package village.dungeonguard.test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.tree.analysis.Frame;
import village.dungeonguard.core.ForcedSpawnTransformer;

/**
 * Explicit, static check of the pinned Magma b4c01d2 PlayerList after official
 * binpatch application and stable-39 SRG remapping. Upstream bytes stay external.
 * This analyzes real release bytecode; it does not start or fully link Magma.
 */
public final class MagmaSpawnReleaseChecks implements Opcodes {
    private static final String LIST = "net/minecraft/server/management/PlayerList";
    private static final String MP = "net/minecraft/entity/player/EntityPlayerMP";
    private static final String POLICY = "village/dungeonguard/ForcedSpawnPolicy";
    private static final String DESC = "(L" + MP + ";IZ)L" + MP + ";";
    private static final String EXPECTED = "4b02590830821bd3495a17e1e006b06e8dc68fa02fa2f35435fc12bdc6c1a96d";
    private static final List<String> PASSED = new ArrayList<String>();

    private MagmaSpawnReleaseChecks() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply the explicitly prepared pinned Magma SRG classes JAR");
        byte[] original;
        try (ZipFile jar = new ZipFile(args[0])) {
            ZipEntry entry = jar.getEntry(LIST + ".class");
            if (entry == null) throw new IllegalArgumentException("Missing actual PlayerList.class");
            try (InputStream in = jar.getInputStream(entry)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                original = out.toByteArray();
            }
        }
        check("exact pinned patched/remapped PlayerList SHA-256", EXPECTED.equals(hash(original)));
        ClassNode before = read(original);
        check("actual SRG PlayerList identity", LIST.equals(before.name));
        MethodNode oldBody = recreation(before);
        check("actual prepared destination field", fields(before).contains("location:Lorg/bukkit/Location;:1:null"));
        byte[] transformed = ForcedSpawnTransformer.apply(LIST.replace('/', '.'), original);
        check("real release class is transformed", !Arrays.equals(original, transformed));
        check("real release second transformation is byte-identical", Arrays.equals(transformed,
                ForcedSpawnTransformer.apply(LIST.replace('/', '.'), transformed)));
        ClassNode after = read(transformed);
        MethodNode body = recreation(after);
        int captured = oldBody.maxLocals;
        check("exactly one additional captured local", body.maxLocals == captured + 1);
        check("class fields and method count preserved", fields(before).equals(fields(after)) && before.methods.size() == after.methods.size());

        List<AbstractInsnNode> code = meaningful(body);
        Set<AbstractInsnNode> injected = Collections.newSetFromMap(new IdentityHashMap<AbstractInsnNode, Boolean>());
        check("destination captured before original body", variable(code.get(0), ALOAD, 0)
                && location(code.get(1), GETFIELD) && variable(code.get(2), ASTORE, captured)
                && variable(code.get(3), ALOAD, captured) && code.get(4).getOpcode() == IFNONNULL);
        check("dimension hook guarded at entry", variable(code.get(5), ALOAD, 1)
                && variable(code.get(6), ILOAD, 2) && policy(code.get(7), "respawnDimension", "(L" + MP + ";I)I")
                && variable(code.get(8), ISTORE, 2));
        check("entry skip targets untouched original start", nextReal(((JumpInsnNode)code.get(4)).label) == code.get(9)
                && variable(code.get(9), ALOAD, 0) && location(code.get(10), GETFIELD));
        injected.addAll(code.subList(0, 9));
        int returns = 0;
        boolean allGated = true;
        for (int i = 0; i < code.size(); ++i) if (code.get(i).getOpcode() == ARETURN) {
            ++returns;
            if (i < 4 || !variable(code.get(i - 4), ALOAD, captured) || code.get(i - 3).getOpcode() != IFNONNULL
                    || !variable(code.get(i - 2), ALOAD, 1)
                    || !policy(code.get(i - 1), "respawnFinished", "(L" + MP + ";L" + MP + ";)L" + MP + ";")
                    || nextReal(((JumpInsnNode)code.get(i - 3)).label) != code.get(i)) allGated = false;
            else injected.addAll(code.subList(i - 4, i));
        }
        check("every actual completed recreation return is guarded", returns > 0 && allGated);
        int locationReads = 0, locationWrites = 0, hookCalls = 0;
        for (AbstractInsnNode instruction : code) {
            if (location(instruction, GETFIELD)) ++locationReads;
            if (location(instruction, PUTFIELD)) ++locationWrites;
            if (instruction instanceof MethodInsnNode && POLICY.equals(((MethodInsnNode)instruction).owner)) ++hookCalls;
        }
        check("only capture read added; Magma destination clear preserved", locationReads == 2 && locationWrites == 1);
        check("exact intended mandatory hook count", hookCalls == returns + 1);
        check("entire original recreation instruction semantics preserved", canonical(oldBody, Collections.<AbstractInsnNode>emptySet()).equals(canonical(body, injected)));
        check("recreation exception-handler ranges and types preserved", handlers(oldBody, Collections.<AbstractInsnNode>emptySet()).equals(handlers(body, injected)));

        boolean untouched = true, signatures = true;
        for (Object entry : before.methods) {
            MethodNode old = (MethodNode)entry; MethodNode updated = find(after, old.name, old.desc);
            signatures &= updated != null && old.access == updated.access && same(old.signature, updated.signature)
                    && old.exceptions.equals(updated.exceptions);
            boolean dataHook = name(old, "readPlayerDataFromFile", "func_72380_a")
                    && ("(L" + MP + ";)Lnet/minecraft/nbt/NBTTagCompound;").equals(old.desc);
            boolean loginHook = name(old, "initializeConnectionToPlayer", "func_72355_a")
                    && ("(Lnet/minecraft/network/NetworkManager;L" + MP + ";Lnet/minecraft/network/NetHandlerPlayServer;)V").equals(old.desc);
            if (updated == null || old == oldBody || dataHook || loginHook) continue;
            untouched &= canonical(old, Collections.<AbstractInsnNode>emptySet()).equals(canonical(updated, Collections.<AbstractInsnNode>emptySet()))
                    && handlers(old, Collections.<AbstractInsnNode>emptySet()).equals(handlers(updated, Collections.<AbstractInsnNode>emptySet()));
        }
        check("all method signatures and declared exceptions preserved", signatures);
        check("all unrelated actual Magma method bodies preserved", untouched);

        Analyzer originalAnalyzer = new Analyzer(new BasicVerifier()); originalAnalyzer.analyze(LIST, oldBody);
        check("original actual recreation passes ASM BasicVerifier", true);
        Analyzer analyzer = new Analyzer(new BasicVerifier()); Frame[] frames = analyzer.analyze(LIST, body);
        check("transformed actual recreation passes ASM BasicVerifier", true);
        int declaredFrames = 0; boolean declared = true, inferred = true;
        AbstractInsnNode[] instructions = body.instructions.toArray();
        for (int i = 0; i < instructions.length; ++i) {
            if (!(instructions[i] instanceof FrameNode)) continue;
            ++declaredFrames; FrameNode frame = (FrameNode)instructions[i];
            int slots = 0;
            for (Object local : frame.local) slots += LONG.equals(local) || DOUBLE.equals(local) ? 2 : 1;
            declared &= frame.type == F_NEW && slots == captured + 1
                    && "java/lang/Object".equals(frame.local.get(frame.local.size() - 1));
            Frame inferredFrame = frames[i];
            if (inferredFrame != null) inferred &= ((BasicValue)inferredFrame.getLocal(captured)).isReference();
        }
        check("all declared stack-map frames retain isolated destination local", declaredFrames > 0 && declared);
        check("dataflow agrees captured destination survives every reachable frame", inferred);
        System.out.println("DUNGEON_GUARD_MAGMA_SPAWN_RELEASE_PASS");
        System.out.println("{\"success\":true,\"passed\":" + PASSED.size() + ",\"failed\":0,\"scope\":\"Pinned real Magma PlayerList transformation and static dataflow; no server startup\",\"player_list_sha256\":\""
                + EXPECTED + "\",\"actual_returns\":" + returns + ",\"guard_count\":" + (returns + 1)
                + ",\"expanded_frames\":" + declaredFrames + ",\"checks\":" + json(PASSED) + "}");
    }

    private static String hash(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes); StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format("%02x", b & 255)); return out.toString();
    }
    private static ClassNode read(byte[] bytes) { ClassNode node = new ClassNode(ASM5); new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES); return node; }
    private static List<String> fields(ClassNode node) {
        List<String> fields = new ArrayList<String>();
        for (Object entry : node.fields) { FieldNode f = (FieldNode)entry; fields.add(f.name + ":" + f.desc + ":" + f.access + ":" + f.value); }
        return fields;
    }
    private static MethodNode recreation(ClassNode node) {
        for (Object entry : node.methods) { MethodNode m = (MethodNode)entry; if (name(m, "recreatePlayerEntity", "func_72368_a") && DESC.equals(m.desc)) return m; }
        throw new IllegalArgumentException("Missing actual recreation method");
    }
    private static MethodNode find(ClassNode node, String name, String desc) {
        for (Object entry : node.methods) { MethodNode m = (MethodNode)entry; if (name.equals(m.name) && desc.equals(m.desc)) return m; } return null;
    }
    private static boolean name(MethodNode m, String mcp, String srg) { return mcp.equals(m.name) || srg.equals(m.name); }
    private static boolean same(Object a, Object b) { return a == null ? b == null : a.equals(b); }
    private static boolean variable(AbstractInsnNode node, int opcode, int local) { return node instanceof VarInsnNode && node.getOpcode() == opcode && ((VarInsnNode)node).var == local; }
    private static boolean location(AbstractInsnNode node, int opcode) {
        if (!(node instanceof FieldInsnNode) || node.getOpcode() != opcode) return false;
        FieldInsnNode f = (FieldInsnNode)node; return LIST.equals(f.owner) && "location".equals(f.name) && "Lorg/bukkit/Location;".equals(f.desc);
    }
    private static boolean policy(AbstractInsnNode node, String name, String desc) {
        if (!(node instanceof MethodInsnNode) || node.getOpcode() != INVOKESTATIC) return false;
        MethodInsnNode m = (MethodInsnNode)node; return POLICY.equals(m.owner) && name.equals(m.name) && desc.equals(m.desc) && !m.itf;
    }
    private static AbstractInsnNode nextReal(AbstractInsnNode node) { while (node != null && node.getOpcode() < 0) node = node.getNext(); return node; }
    private static List<AbstractInsnNode> meaningful(MethodNode method) {
        List<AbstractInsnNode> nodes = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode node : method.instructions.toArray()) if (node.getOpcode() >= 0) nodes.add(node); return nodes;
    }

    /** Compare all original opcodes/operands and symbolic control-flow targets, ignoring only explicit injected nodes and metadata. */
    private static List<String> canonical(MethodNode method, Set<AbstractInsnNode> excluded) {
        Map<AbstractInsnNode, Integer> offsets = offsets(method, excluded); List<String> result = new ArrayList<String>();
        for (AbstractInsnNode n : method.instructions.toArray()) {
            if (n.getOpcode() < 0 || excluded.contains(n)) continue;
            String s = Integer.toString(n.getOpcode());
            if (n instanceof VarInsnNode) s += ":" + ((VarInsnNode)n).var;
            else if (n instanceof IntInsnNode) s += ":" + ((IntInsnNode)n).operand;
            else if (n instanceof TypeInsnNode) s += ":" + ((TypeInsnNode)n).desc;
            else if (n instanceof FieldInsnNode) { FieldInsnNode f = (FieldInsnNode)n; s += ":" + f.owner + ":" + f.name + ":" + f.desc; }
            else if (n instanceof MethodInsnNode) { MethodInsnNode m = (MethodInsnNode)n; s += ":" + m.owner + ":" + m.name + ":" + m.desc + ":" + m.itf; }
            else if (n instanceof JumpInsnNode) s += ":" + offsets.get(((JumpInsnNode)n).label);
            else if (n instanceof LdcInsnNode) s += ":" + ((LdcInsnNode)n).cst;
            else if (n instanceof IincInsnNode) { IincInsnNode i = (IincInsnNode)n; s += ":" + i.var + ":" + i.incr; }
            else if (n instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode t = (TableSwitchInsnNode)n; s += ":" + t.min + ":" + t.max + ":" + offsets.get(t.dflt);
                for (Object label : t.labels) s += ":" + offsets.get(label);
            } else if (n instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode l = (LookupSwitchInsnNode)n; s += ":" + l.keys + ":" + offsets.get(l.dflt);
                for (Object label : l.labels) s += ":" + offsets.get(label);
            } else if (n instanceof MultiANewArrayInsnNode) { MultiANewArrayInsnNode a = (MultiANewArrayInsnNode)n; s += ":" + a.desc + ":" + a.dims; }
            else if (n instanceof InvokeDynamicInsnNode) { InvokeDynamicInsnNode d = (InvokeDynamicInsnNode)n; s += ":" + d.name + ":" + d.desc + ":" + d.bsm + ":" + Arrays.toString(d.bsmArgs); }
            else if (n.getType() != AbstractInsnNode.INSN) throw new IllegalArgumentException("Unsupported comparison instruction " + n.getClass().getName());
            result.add(s);
        }
        return result;
    }
    private static Map<AbstractInsnNode, Integer> offsets(MethodNode method, Set<AbstractInsnNode> excluded) {
        Map<AbstractInsnNode, Integer> offsets = new IdentityHashMap<AbstractInsnNode, Integer>(); int offset = 0;
        for (AbstractInsnNode node : method.instructions.toArray()) { offsets.put(node, offset); if (node.getOpcode() >= 0 && !excluded.contains(node)) ++offset; }
        return offsets;
    }
    private static List<String> handlers(MethodNode method, Set<AbstractInsnNode> excluded) {
        Map<AbstractInsnNode, Integer> offsets = offsets(method, excluded); List<String> result = new ArrayList<String>();
        for (Object entry : method.tryCatchBlocks) { TryCatchBlockNode t = (TryCatchBlockNode)entry; result.add(offsets.get(t.start) + ":" + offsets.get(t.end) + ":" + offsets.get(t.handler) + ":" + t.type); }
        return result;
    }
    private static void check(String name, boolean value) { if (!value) throw new AssertionError(name); PASSED.add(name); }
    private static String json(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (String value : values) { if (out.length() > 1) out.append(','); out.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"'); }
        return out.append(']').toString();
    }
}
