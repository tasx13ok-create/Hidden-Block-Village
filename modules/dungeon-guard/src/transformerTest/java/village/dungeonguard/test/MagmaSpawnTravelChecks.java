package village.dungeonguard.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import village.dungeonguard.core.ForcedSpawnTransformer;

/**
 * Original synthetic JVM regressions for Magma's prepared-location recreation
 * contract. No Minecraft, Bukkit, Forge or upstream implementation is bundled.
 * Generated Java-8 classes are loaded and executed, so invalid stack-map frames
 * fail verification instead of passing a structural bytecode inspection.
 *
 * Run with --expect-bug and the uploaded 1.4.2 transformer first on the classpath
 * to reproduce its unconditional hub redirect. A normal run requires the fix.
 * This is not an actual-server, graphical-client or owner-world acceptance test.
 */
public final class MagmaSpawnTravelChecks implements Opcodes {
    private static final String LIST = "net/minecraft/server/management/PlayerList";
    private static final String MP = "net/minecraft/entity/player/EntityPlayerMP";
    private static final String POLICY = "village/dungeonguard/ForcedSpawnPolicy";
    private static final String LOC = "org/bukkit/Location";
    private static final String NBT = "net/minecraft/nbt/NBTTagCompound";
    private static final String NETWORK = "net/minecraft/network/NetworkManager";
    private static final String HANDLER = "net/minecraft/network/NetHandlerPlayServer";
    private static final String RECREATE = "(L" + MP + ";IZ)L" + MP + ";";
    private static final List<String> PASSED = new ArrayList<String>();

    private MagmaSpawnTravelChecks() { }

    public static void main(String[] args) throws Exception {
        boolean expectBug = args.length == 1 && "--expect-bug".equals(args[0]);
        if (args.length > (expectBug ? 1 : 0)) throw new IllegalArgumentException("Only --expect-bug is supported");
        byte[] source = playerList(true);
        byte[] patched = ForcedSpawnTransformer.apply(LIST.replace('/', '.'), source);
        check("second application is byte-identical", Arrays.equals(patched,
                ForcedSpawnTransformer.apply(LIST.replace('/', '.'), patched)));

        travel(patched, "Nether entry", -1, true, 41, 0, expectBug);
        travel(patched, "Nether return", 0, true, 42, 0, expectBug);
        travel(patched, "plugin travel with false flag", 7, false, 43, 0, expectBug);
        travel(patched, "plugin travel with true flag", 9, true, 44, 0, expectBug);
        travel(patched, "early return", -1, true, 45, 2, expectBug);
        travel(patched, "caught exception return", -1, true, 46, 5, expectBug);
        respawn(patched, "death", -1, false, 0);
        respawn(patched, "End return", 0, true, 0);
        respawn(patched, "early-return death", 7, false, 2);
        login(patched);
        byte[] forge = ForcedSpawnTransformer.apply(LIST.replace('/', '.'), playerList(false));
        respawn(forge, "ordinary Forge death", -1, false, 0);
        respawn(forge, "ordinary Forge End return", 0, true, 0);

        if (!expectBug) {
            exceptionalTransfer(patched);
            nestedTransfer(patched);
            malformed(source, patched);
        }
        System.out.println(expectBug ? "DUNGEON_GUARD_MAGMA_SPAWN_BUG_REPRODUCED" : "DUNGEON_GUARD_MAGMA_SPAWN_FIX_PASS");
        System.out.println("{\"success\":true,\"passed\":" + PASSED.size()
                + ",\"failed\":0,\"old_bug_reproduction\":" + expectBug
                + ",\"scope\":\"Synthetic JVM execution; not actual Magma or the owner server\",\"checks\":" + json(PASSED) + "}");
    }

    private static void travel(byte[] code, String name, int dimension, boolean flag,
                               int tag, int mode, boolean expectBug) throws Exception {
        Fixture f = new Fixture(code);
        f.prepare(dimension, tag, flag);
        f.listType.getField("mode").setInt(f.list, mode);
        Object player = f.player();
        Object result = f.recreate(player, dimension, flag);
        check(name + ": retains player identity", result == player);
        check(name + ": destination consumed normally", f.listType.getField("location").get(f.list) == null);
        check(name + ": original target dimension reaches body", f.listType.getField("seenDimension").getInt(f.list) == (expectBug ? 0 : dimension));
        check(name + ": final destination", f.playerType.getField("dimension").getInt(result) == (expectBug ? 0 : dimension)
                && f.playerType.getField("tag").getInt(result) == (expectBug ? 999 : tag + (mode == 5 ? 1000 : 0)));
        check(name + ": mandatory spawn hook count", f.count("prefixCount") == (expectBug ? 1 : 0)
                && f.count("finishCount") == (expectBug ? 1 : 0));
    }

    private static void respawn(byte[] code, String name, int dimension, boolean flag, int mode) throws Exception {
        Fixture f = new Fixture(code);
        f.listType.getField("mode").setInt(f.list, mode);
        Object player = f.player();
        Object result = f.recreate(player, dimension, flag);
        check(name + ": hub dimension and landing remain enforced", result == player
                && f.playerType.getField("dimension").getInt(result) == 0
                && f.playerType.getField("tag").getInt(result) == 999);
        check(name + ": both mandatory hooks execute once", f.count("prefixCount") == 1 && f.count("finishCount") == 1);
    }

    private static void login(byte[] code) throws Exception {
        Fixture f = new Fixture(code);
        Object player = f.player();
        Class<?> nbt = f.loader.loadClass(NBT.replace('/', '.'));
        f.listType.getMethod("readPlayerDataFromFile", f.playerType).invoke(f.list, player);
        f.listType.getMethod("initializeConnectionToPlayer", f.loader.loadClass(NETWORK.replace('/', '.')),
                f.playerType, f.loader.loadClass(HANDLER.replace('/', '.'))).invoke(f.list, null, player, null);
        check("login and player-data hooks remain installed", f.count("loginCount") == 1 && f.count("loadCount") == 1
                && nbt != null);
    }

    private static void exceptionalTransfer(byte[] code) throws Exception {
        Fixture f = new Fixture(code);
        f.prepare(-1, 80, true);
        f.listType.getField("mode").setInt(f.list, 3);
        boolean thrown = false;
        try { f.recreate(f.player(), -1, true); }
        catch (InvocationTargetException failure) { thrown = failure.getCause() instanceof IllegalStateException; }
        check("failed prepared transfer preserves original exception", thrown);
        check("failed prepared transfer never invokes mandatory spawn", f.count("prefixCount") == 0 && f.count("finishCount") == 0);
        f.listType.getField("mode").setInt(f.list, 0);
        Object result = f.recreate(f.player(), -1, false);
        check("exception leaves no state that suppresses next death spawn", f.count("prefixCount") == 1
                && f.count("finishCount") == 1 && f.playerType.getField("tag").getInt(result) == 999);
        f.prepare(5, 81, false);
        result = f.recreate(f.player(), 5, false);
        check("later transfer remains independent of failed invocation", f.count("prefixCount") == 1
                && f.count("finishCount") == 1 && f.playerType.getField("tag").getInt(result) == 81);
    }

    private static void nestedTransfer(byte[] code) throws Exception {
        Fixture f = new Fixture(code);
        f.prepare(-1, 91, true);
        f.listType.getField("mode").setInt(f.list, 4);
        Object result = f.recreate(f.player(), -1, true);
        check("nested genuine respawn is not suppressed by outer travel", f.count("prefixCount") == 1
                && f.count("finishCount") == 1 && f.listType.getField("nestedTag").getInt(f.list) == 999);
        check("outer prepared destination survives nested recreation", f.playerType.getField("dimension").getInt(result) == -1
                && f.playerType.getField("tag").getInt(result) == 91 && f.listType.getField("bodyCalls").getInt(f.list) == 2);
    }

    private static void malformed(byte[] source, byte[] patched) {
        ClassNode n = node(source);
        for (Object entry : n.fields) { FieldNode field = (FieldNode)entry; if (field.name.equals("location")) field.access |= ACC_STATIC; }
        rejected("static location marker rejected", bytes(n));
        n = node(source);
        for (int i = n.methods.size() - 1; i >= 0; i--) if (((MethodNode)n.methods.get(i)).name.equals("prepareRecreatePlayerEntity")) n.methods.remove(i);
        rejected("missing prepare method rejected", bytes(n));
        n = node(source);
        for (AbstractInsnNode i : method(n, "prepareRecreatePlayerEntity").instructions.toArray())
            if (i instanceof FieldInsnNode) { ((FieldInsnNode)i).name = "unexpected"; break; }
        rejected("wrong prepared destination write rejected", bytes(n));
        n = node(source);
        for (AbstractInsnNode i : method(n, "recreatePlayerEntity").instructions.toArray())
            if (i.getOpcode() == PUTFIELD) { ((FieldInsnNode)i).name = "unexpected"; break; }
        rejected("wrong recreation destination clear rejected", bytes(n));
        n = node(patched);
        for (AbstractInsnNode i : method(n, "recreatePlayerEntity").instructions.toArray())
            if (i.getOpcode() == ASTORE) { ((VarInsnNode)i).var--; break; }
        rejected("tampered captured local rejected", bytes(n));
        n = node(patched);
        MethodNode recreation = method(n, "recreatePlayerEntity");
        for (AbstractInsnNode i : recreation.instructions.toArray()) if (i.getOpcode() == IFNONNULL) {
            ((JumpInsnNode)i).label = new LabelNode(); recreation.instructions.add(((JumpInsnNode)i).label); break;
        }
        rejected("tampered entry skip target rejected", bytes(n));
        n = node(patched);
        recreation = method(n, "recreatePlayerEntity");
        for (AbstractInsnNode i : recreation.instructions.toArray()) if (i instanceof MethodInsnNode
                && ((MethodInsnNode)i).name.equals("respawnFinished")) { recreation.instructions.remove(i); break; }
        rejected("missing completion hook rejected", bytes(n));
        n = node(patched);
        for (AbstractInsnNode i : method(n, "recreatePlayerEntity").instructions.toArray()) if (i instanceof FrameNode) {
            FrameNode frame = (FrameNode)i;
            if (frame.local != null && !frame.local.isEmpty()) { frame.local.set(frame.local.size() - 1, INTEGER); break; }
        }
        rejected("tampered captured-local frame rejected", bytes(n));

        n = node(source);
        recreation = method(n, "recreatePlayerEntity");
        org.objectweb.asm.tree.InsnList oldPrefix = new org.objectweb.asm.tree.InsnList();
        oldPrefix.add(new VarInsnNode(ALOAD, 1)); oldPrefix.add(new VarInsnNode(ILOAD, 2));
        oldPrefix.add(new MethodInsnNode(INVOKESTATIC, POLICY, "respawnDimension", "(L" + MP + ";I)I", false));
        oldPrefix.add(new VarInsnNode(ISTORE, 2)); recreation.instructions.insert(oldPrefix);
        rejected("legacy partial unconditional hook rejected", bytes(n));
    }

    private static void rejected(String name, byte[] input) {
        boolean rejected = false;
        try { ForcedSpawnTransformer.apply(LIST.replace('/', '.'), input); }
        catch (IllegalStateException expected) { rejected = true; }
        check(name, rejected);
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode(ASM5); new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES); return node;
    }
    private static byte[] bytes(ClassNode node) { ClassWriter out = new ClassWriter(0); node.accept(out); return out.toByteArray(); }
    private static MethodNode method(ClassNode node, String name) {
        for (Object entry : node.methods) { MethodNode method = (MethodNode)entry; if (method.name.equals(name)) return method; }
        throw new IllegalStateException("Missing fixture method " + name);
    }
    private static void check(String name, boolean value) {
        if (!value) throw new AssertionError(name);
        PASSED.add(name);
    }
    private static String json(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (String value : values) { if (out.length() > 1) out.append(','); out.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"'); }
        return out.append(']').toString();
    }

    private static final class Fixture {
        final FixtureLoader loader;
        final Class<?> listType, playerType, policyType, locationType;
        final Object list;
        Fixture(byte[] code) throws Exception {
            Map<String, byte[]> definitions = new HashMap<String, byte[]>();
            definitions.put(LIST, code); definitions.put(MP, record(MP, "dimension", "tag"));
            definitions.put(LOC, record(LOC, "dimension", "tag")); definitions.put(POLICY, policy());
            for (String type : new String[]{NBT, NETWORK, HANDLER}) definitions.put(type, record(type));
            loader = new FixtureLoader(definitions);
            listType = loader.loadClass(LIST.replace('/', '.')); playerType = loader.loadClass(MP.replace('/', '.'));
            policyType = loader.loadClass(POLICY.replace('/', '.')); locationType = loader.loadClass(LOC.replace('/', '.'));
            list = listType.newInstance();
        }
        Object player() throws Exception { return playerType.newInstance(); }
        int count(String field) throws Exception { return policyType.getField(field).getInt(null); }
        void prepare(int dimension, int tag, boolean flag) throws Exception {
            Object location = locationType.newInstance(); locationType.getField("dimension").setInt(location, dimension);
            locationType.getField("tag").setInt(location, tag);
            listType.getMethod("prepareRecreatePlayerEntity", locationType, boolean.class).invoke(list, location, flag);
        }
        Object recreate(Object player, int dimension, boolean flag) throws Exception {
            return listType.getMethod("recreatePlayerEntity", playerType, int.class, boolean.class).invoke(list, player, dimension, flag);
        }
    }

    private static final class FixtureLoader extends ClassLoader {
        private final Map<String, byte[]> definitions;
        FixtureLoader(Map<String, byte[]> definitions) { super(MagmaSpawnTravelChecks.class.getClassLoader()); this.definitions = definitions; }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            byte[] definition = definitions.get(name.replace('.', '/'));
            if (definition == null) return super.loadClass(name, resolve);
            Class<?> result = findLoadedClass(name);
            if (result == null) result = defineClass(name, definition, 0, definition.length);
            if (resolve) resolveClass(result);
            return result;
        }
    }

    private static ClassWriter writer(String name) {
        ClassWriter out = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) { return a.equals(b) ? a : "java/lang/Object"; }
        };
        out.visit(V1_8, ACC_PUBLIC, name, null, "java/lang/Object", null);
        MethodVisitor ctor = out.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(ALOAD, 0); ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd(); return out;
    }
    private static byte[] record(String name, String... fields) {
        ClassWriter out = writer(name);
        for (String field : fields) out.visitField(ACC_PUBLIC, field, "I", null, null).visitEnd();
        out.visitEnd(); return out.toByteArray();
    }
    private static void increment(MethodVisitor m, String owner, String field, boolean isStatic) {
        if (isStatic) { m.visitFieldInsn(GETSTATIC, owner, field, "I"); m.visitInsn(ICONST_1); m.visitInsn(IADD); m.visitFieldInsn(PUTSTATIC, owner, field, "I"); }
        else { m.visitVarInsn(ALOAD, 0); m.visitInsn(DUP); m.visitFieldInsn(GETFIELD, owner, field, "I"); m.visitInsn(ICONST_1); m.visitInsn(IADD); m.visitFieldInsn(PUTFIELD, owner, field, "I"); }
    }
    private static byte[] policy() {
        ClassWriter out = writer(POLICY);
        for (String field : new String[]{"prefixCount", "finishCount", "loadCount", "loginCount"}) out.visitField(ACC_PUBLIC | ACC_STATIC, field, "I", null, null).visitEnd();
        MethodVisitor m = out.visitMethod(ACC_PUBLIC | ACC_STATIC, "respawnDimension", "(L" + MP + ";I)I", null, null);
        m.visitCode(); increment(m, POLICY, "prefixCount", true); m.visitInsn(ICONST_0); m.visitInsn(IRETURN); m.visitMaxs(0, 0); m.visitEnd();
        m = out.visitMethod(ACC_PUBLIC | ACC_STATIC, "respawnFinished", "(L" + MP + ";L" + MP + ";)L" + MP + ";", null, null);
        m.visitCode(); increment(m, POLICY, "finishCount", true);
        m.visitVarInsn(ALOAD, 0); m.visitInsn(ICONST_0); m.visitFieldInsn(PUTFIELD, MP, "dimension", "I");
        m.visitVarInsn(ALOAD, 0); m.visitIntInsn(SIPUSH, 999); m.visitFieldInsn(PUTFIELD, MP, "tag", "I");
        m.visitVarInsn(ALOAD, 0); m.visitInsn(ARETURN); m.visitMaxs(0, 0); m.visitEnd();
        m = out.visitMethod(ACC_PUBLIC | ACC_STATIC, "playerDataLoaded", "(L" + NBT + ";L" + MP + ";)L" + NBT + ";", null, null);
        m.visitCode(); increment(m, POLICY, "loadCount", true); m.visitVarInsn(ALOAD, 0); m.visitInsn(ARETURN); m.visitMaxs(0, 0); m.visitEnd();
        m = out.visitMethod(ACC_PUBLIC | ACC_STATIC, "loginFinished", "(L" + MP + ";)V", null, null);
        m.visitCode(); increment(m, POLICY, "loginCount", true); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        out.visitEnd(); return out.toByteArray();
    }

    private static byte[] playerList(boolean magma) {
        ClassWriter out = writer(LIST);
        for (String field : new String[]{"seenDimension", "bodyCalls", "mode", "nestedTag"}) out.visitField(ACC_PUBLIC, field, "I", null, null).visitEnd();
        if (magma) {
            out.visitField(ACC_PUBLIC, "location", "L" + LOC + ";", null, null).visitEnd();
            MethodVisitor m = out.visitMethod(ACC_PUBLIC, "prepareRecreatePlayerEntity", "(L" + LOC + ";Z)V", null, null);
            m.visitCode(); m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(PUTFIELD, LIST, "location", "L" + LOC + ";");
            m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        }
        MethodVisitor m = out.visitMethod(ACC_PUBLIC, "readPlayerDataFromFile", "(L" + MP + ";)L" + NBT + ";", null, null);
        m.visitCode(); m.visitInsn(ACONST_NULL); m.visitInsn(ARETURN); m.visitMaxs(0, 0); m.visitEnd();
        m = out.visitMethod(ACC_PUBLIC, "initializeConnectionToPlayer", "(L" + NETWORK + ";L" + MP + ";L" + HANDLER + ";)V", null, null);
        m.visitCode(); m.visitInsn(RETURN); m.visitMaxs(0, 0); m.visitEnd();
        m = out.visitMethod(ACC_PUBLIC, "recreatePlayerEntity", RECREATE, null, null); m.visitCode();
        if (magma) {
            m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, LIST, "location", "L" + LOC + ";"); m.visitVarInsn(ASTORE, 4);
            m.visitVarInsn(ALOAD, 0); m.visitInsn(ACONST_NULL); m.visitFieldInsn(PUTFIELD, LIST, "location", "L" + LOC + ";");
        } else { m.visitInsn(ACONST_NULL); m.visitVarInsn(ASTORE, 4); }
        m.visitLdcInsn(123456789L); m.visitVarInsn(LSTORE, 5); m.visitLdcInsn(2.5D); m.visitVarInsn(DSTORE, 7);
        increment(m, LIST, "bodyCalls", false);
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ILOAD, 2); m.visitFieldInsn(PUTFIELD, LIST, "seenDimension", "I");
        Label noThrow = new Label(); modeJump(m, 3, noThrow);
        throwException(m, "java/lang/IllegalStateException", "fixture rejected"); m.visitLabel(noThrow);
        Label notNested = new Label(); modeJump(m, 4, notNested);
        m.visitVarInsn(ALOAD, 0); m.visitInsn(ICONST_0); m.visitFieldInsn(PUTFIELD, LIST, "mode", "I");
        m.visitVarInsn(ALOAD, 0); m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(NEW, MP); m.visitInsn(DUP);
        m.visitMethodInsn(INVOKESPECIAL, MP, "<init>", "()V", false); m.visitIntInsn(BIPUSH, -9); m.visitInsn(ICONST_0);
        m.visitMethodInsn(INVOKEVIRTUAL, LIST, "recreatePlayerEntity", RECREATE, false); m.visitFieldInsn(GETFIELD, MP, "tag", "I");
        m.visitFieldInsn(PUTFIELD, LIST, "nestedTag", "I"); m.visitLabel(notNested);
        Label prepared = new Label(), placed = new Label(); m.visitVarInsn(ALOAD, 4); m.visitJumpInsn(IFNONNULL, prepared);
        m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ILOAD, 2); m.visitFieldInsn(PUTFIELD, MP, "dimension", "I");
        m.visitVarInsn(ALOAD, 1); m.visitInsn(ICONST_M1); m.visitFieldInsn(PUTFIELD, MP, "tag", "I"); m.visitJumpInsn(GOTO, placed);
        m.visitLabel(prepared);
        for (String field : new String[]{"dimension", "tag"}) {
            m.visitVarInsn(ALOAD, 1); m.visitVarInsn(ALOAD, 4); m.visitFieldInsn(GETFIELD, LOC, field, "I"); m.visitFieldInsn(PUTFIELD, MP, field, "I");
        }
        m.visitLabel(placed);
        Label notEarly = new Label(); modeJump(m, 2, notEarly); m.visitVarInsn(ALOAD, 1); m.visitInsn(ARETURN); m.visitLabel(notEarly);
        Label start = new Label(), end = new Label(), handler = new Label(), notCaught = new Label();
        m.visitTryCatchBlock(start, end, handler, "java/lang/IllegalArgumentException"); m.visitLabel(start);
        modeJump(m, 5, notCaught); throwException(m, "java/lang/IllegalArgumentException", "recoverable fixture path"); m.visitLabel(notCaught);
        // Force wide locals to stay live across branching and exception frames.
        m.visitVarInsn(LLOAD, 5); m.visitInsn(POP2); m.visitVarInsn(DLOAD, 7); m.visitInsn(POP2);
        m.visitVarInsn(ALOAD, 1); m.visitLabel(end); m.visitInsn(ARETURN);
        m.visitLabel(handler); m.visitVarInsn(ASTORE, 9);
        m.visitVarInsn(LLOAD, 5); m.visitInsn(POP2); m.visitVarInsn(DLOAD, 7); m.visitInsn(POP2);
        m.visitVarInsn(ALOAD, 1); m.visitInsn(DUP); m.visitFieldInsn(GETFIELD, MP, "tag", "I"); m.visitIntInsn(SIPUSH, 1000);
        m.visitInsn(IADD); m.visitFieldInsn(PUTFIELD, MP, "tag", "I"); m.visitVarInsn(ALOAD, 1); m.visitInsn(ARETURN);
        m.visitMaxs(0, 0); m.visitEnd(); out.visitEnd(); return out.toByteArray();
    }
    private static void modeJump(MethodVisitor m, int mode, Label whenDifferent) {
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, LIST, "mode", "I"); m.visitIntInsn(BIPUSH, mode); m.visitJumpInsn(IF_ICMPNE, whenDifferent);
    }
    private static void throwException(MethodVisitor m, String type, String message) {
        m.visitTypeInsn(NEW, type); m.visitInsn(DUP); m.visitLdcInsn(message);
        m.visitMethodInsn(INVOKESPECIAL, type, "<init>", "(Ljava/lang/String;)V", false); m.visitInsn(ATHROW);
    }
}
