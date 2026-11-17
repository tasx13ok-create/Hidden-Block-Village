package village.dungeonguard.test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;
import village.dungeonguard.core.GuardTransformer;

/** Independent JVM fixtures and optional inspection of a user-supplied Naruto release. */
public final class KamuiTransformerChecks implements Opcodes {
    private static final String KAMUI = "net/narutomod/procedure/ProcedureKamuiTeleportEntity";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String ACCESS = "net/minecraft/world/IBlockAccess";
    private static final String POS = "net/minecraft/util/math/BlockPos";
    private static final String STATE = "net/minecraft/block/state/IBlockState";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String ITEM = "net/minecraft/item/ItemStack";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String DROP = "net/minecraft/entity/item/EntityItem";
    private static final String HOOK = "village/dungeonguard/GuardHooks";
    private static final String DESC = "(" + ref(WORLD) + ref(POS) + "I)I";
    private static final String HOOK_DESC = "(" + ref(WORLD) + ref(POS) + ")Z";
    private static int checks;

    public static int run() {
        checks = 0;
        ClassNode node = read(shell());
        node.methods.remove(method(node)); reject("missing Kamui helper", node);
        node = read(shell()); method(node).desc = "(" + ref(WORLD) + ref(POS) + "I)Z";
        reject("changed Kamui descriptor", node);
        node = read(shell()); method(node).access &= ~ACC_STATIC;
        reject("non-static Kamui helper", node);
        reject("unrecognized independent Kamui body", read(shell()));
        node = read(shell()); method(node).instructions.insert(new LdcInsnNode("unrecognized fixture"));
        reject("unsupported Kamui instruction kind", node);
        node = read(shell());
        org.objectweb.asm.tree.InsnList fake = new org.objectweb.asm.tree.InsnList();
        fake.add(new VarInsnNode(ALOAD, 0)); fake.add(new VarInsnNode(ALOAD, 0));
        fake.add(new MethodInsnNode(INVOKESTATIC, HOOK, "handleKamuiBlock", HOOK_DESC, false));
        fake.add(new InsnNode(POP)); method(node).instructions.insert(fake);
        reject("malformed preexisting Kamui guard", node);
        node = read(shell()); MethodNode target = method(node);
        LabelNode start = new LabelNode(), end = new LabelNode(), handler = new LabelNode();
        target.instructions.insert(start); target.instructions.add(end); target.instructions.add(handler);
        target.instructions.add(new InsnNode(POP)); target.instructions.add(new InsnNode(ICONST_0)); target.instructions.add(new InsnNode(IRETURN));
        target.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Exception"));
        reject("unexpected Kamui exception handler", node);
        return checks;
    }

    /** Actual upstream JAR stays an external test input; no source/assets are copied. */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Supply the original NarutoMod-1.12.2-0.3.1.beta.jar path");
        checks = 0;
        String hash = hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Paths.get(args[0]))));
        check("exact released Naruto artifact", hash.equals("6cb62642e995b90f1cf1fc3eecd8b8b68361469460b681384f1fd16d69e378c0"));
        byte[] actual;
        try (JarFile jar = new JarFile(args[0]); InputStream input = jar.getInputStream(jar.getJarEntry(KAMUI + ".class"))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int length; (length = input.read(buffer)) >= 0;) output.write(buffer, 0, length);
            actual = output.toByteArray();
        }
        // Verify the full release class, then isolate only the static method for
        // JVM execution against instrumented stand-ins. The method is untouched.
        GuardTransformer transformer = new GuardTransformer();
        byte[] patched = transform(transformer, actual);
        check("real Naruto class receives the entry guard", !Arrays.equals(actual, patched));
        check("real Naruto transformation is idempotent", transform(transformer, patched) == patched);
        ClassNode release = read(actual);
        MethodNode target = method(release);
        ClassNode isolated = read(shell());
        isolated.methods.remove(method(isolated));
        isolated.methods.add(target);
        exercise(true, bytes(isolated));
        convertToMcp(method(isolated));
        exercise(false, bytes(isolated));
        negativeCases(actual);
        kgEligibility(args[0]);
        System.out.println("DUNGEON_GUARD_REAL_NARUTO_TRANSFORMER_PASS checks=" + checks + " sha256=" + hash);
    }

    private static void exercise(boolean srg, byte[] input) throws Exception {
        GuardTransformer transformer = new GuardTransformer();
        byte[] patched = transform(transformer, input);
        check("Kamui helper is guarded", !Arrays.equals(input, patched));
        check("Kamui guard is idempotent", transform(transformer, patched) == patched);
        Map<String, byte[]> classes = fixtures(srg);
        classes.put(KAMUI, patched);
        Loader loader = new Loader(classes);
        Class<?> hook = loader.loadClass(HOOK.replace('/', '.'));
        Class<?> worldType = loader.loadClass(WORLD.replace('/', '.'));
        Class<?> posType = loader.loadClass(POS.replace('/', '.'));
        Class<?> blockType = loader.loadClass(BLOCK.replace('/', '.'));
        Class<?> dropType = loader.loadClass(DROP.replace('/', '.'));
        Object world = worldType.newInstance(), pos = posType.newInstance();
        Method call = loader.loadClass(KAMUI.replace('/', '.')).getMethod("eBlock", worldType, posType, int.class);
        hook.getField("deny").setBoolean(null, true);
        check("denied Kamui returns zero emitted stacks", ((Integer) call.invoke(null, world, pos, 17)) == 0);
        check("denied Kamui does not read block drops", blockType.getField("drops").getInt(null) == 0);
        check("denied Kamui does not construct item entities", dropType.getField("created").getInt(null) == 0);
        check("denied Kamui does not spawn item entities", worldType.getField("spawned").getInt(null) == 0);
        check("denied Kamui does not transfer item entities", dropType.getField("transfers").getInt(null) == 0);
        check("denied Kamui does not erase terrain", worldType.getField("erased").getInt(null) == 0);
        check("entry hook receives the original World", hook.getField("world").get(null) == world);
        check("entry hook receives the original BlockPos", hook.getField("pos").get(null) == pos);
        hook.getField("deny").setBoolean(null, false);
        check("allowed Kamui retains its item-stack count", ((Integer) call.invoke(null, world, pos, 17)) == 2);
        check("allowed Kamui computes its drops once", blockType.getField("drops").getInt(null) == 1);
        check("allowed Kamui retains both item entities", dropType.getField("created").getInt(null) == 2);
        check("allowed Kamui retains both spawn calls", worldType.getField("spawned").getInt(null) == 2);
        check("allowed Kamui retains both dimension transfers", dropType.getField("transfers").getInt(null) == 2);
        check("allowed Kamui retains its destination dimension", dropType.getField("dimension").getInt(null) == 17);
        check("allowed Kamui erases exactly one target", worldType.getField("erased").getInt(null) == 1);
    }

    private static void negativeCases(byte[] original) {
        ClassNode node = read(original);
        method(node).desc = "(" + ref(WORLD) + ref(POS) + "I)Z";
        reject("changed Kamui descriptor", node);
        node = read(original); method(node).access &= ~ACC_STATIC;
        reject("non-static Kamui helper", node);
        node = read(original); method(node).instructions.insert(new InsnNode(NOP));
        reject("changed Kamui instruction structure", node);
        node = read(original);
        for (AbstractInsnNode instruction : method(node).instructions.toArray()) {
            if (instruction instanceof VarInsnNode && instruction.getOpcode() == ILOAD && ((VarInsnNode) instruction).var == 2) {
                ((VarInsnNode) instruction).var = 3; break;
            }
        }
        reject("changed Kamui destination operand", node);
        node = read(original);
        for (AbstractInsnNode instruction : method(node).instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && (((MethodInsnNode) instruction).name.equals("spawnEntity") || ((MethodInsnNode) instruction).name.equals("func_72838_d"))) {
                ((MethodInsnNode) instruction).name = "unexpectedSpawn"; break;
            }
        }
        reject("changed early side-effect call", node);
        node = read(transform(new GuardTransformer(), original));
        for (AbstractInsnNode instruction : method(node).instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals("handleKamuiBlock")) {
                AbstractInsnNode argument = instruction.getPrevious();
                while (argument.getOpcode() < 0) argument = argument.getPrevious();
                ((VarInsnNode) argument).var = 0; break;
            }
        }
        reject("malformed existing Kamui guard", node);
        node = read(transform(new GuardTransformer(), original));
        MethodNode target = method(node);
        for (AbstractInsnNode instruction : target.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).name.equals("getDrops")) {
                ((MethodInsnNode) instruction).desc = "()Ljava/util/List;"; break;
            }
        }
        reject("guarded method with a subsequently changed body", node);
    }

    private static void reject(String name, ClassNode node) {
        try { transform(new GuardTransformer(), bytes(node)); throw new AssertionError("Expected rejection: " + name); }
        catch (IllegalStateException expected) { check(name, expected.getMessage().contains("Dungeon Guard cannot safely protect")); }
    }

    /** Original minimal shell; the accepted method body is read only from the external JAR. */
    private static byte[] shell() {
        ClassWriter writer = type(KAMUI, "java/lang/Object", null, false);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "eBlock", DESC, null, null);
        method.visitCode(); method.visitInsn(ICONST_0); method.visitInsn(IRETURN); finish(method);
        return finish(writer);
    }

    private static void convertToMcp(MethodNode method) {
        String[][] aliases = {{"func_180495_p", "getBlockState"}, {"func_72838_d", "spawnEntity"},
                {"func_175698_g", "setBlockToAir"}, {"func_177230_c", "getBlock"},
                {"func_177958_n", "getX"}, {"func_177956_o", "getY"}, {"func_177952_p", "getZ"},
                {"func_174869_p", "setDefaultPickupDelay"}, {"func_184204_a", "changeDimension"}};
        for (AbstractInsnNode node : method.instructions.toArray()) if (node instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode) node;
            for (String[] alias : aliases) if (call.name.equals(alias[0])) { call.name = alias[1]; break; }
        }
    }

    private static Map<String, byte[]> fixtures(boolean srg) {
        Map<String, byte[]> out = new HashMap<String, byte[]>();
        out.put(ACCESS, finish(type(ACCESS, "java/lang/Object", null, true)));
        ClassWriter state = type(STATE, "java/lang/Object", null, true);
        state.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, name(srg, "getBlock", "func_177230_c"), "()" + ref(BLOCK), null, null).visitEnd();
        out.put(STATE, finish(state));
        ClassWriter concrete = type("test/State", "java/lang/Object", new String[] {STATE}, false);
        MethodVisitor m = concrete.visitMethod(ACC_PUBLIC, name(srg, "getBlock", "func_177230_c"), "()" + ref(BLOCK), null, null);
        m.visitCode(); m.visitTypeInsn(NEW, BLOCK); m.visitInsn(DUP); call(m, INVOKESPECIAL, BLOCK, "<init>", "()V", false); m.visitInsn(ARETURN); finish(m);
        out.put("test/State", finish(concrete));
        out.put(ITEM, finish(type(ITEM, "java/lang/Object", null, false)));
        out.put(ENTITY, finish(type(ENTITY, "java/lang/Object", null, false)));
        ClassWriter pos = type(POS, "java/lang/Object", null, false);
        String[] mcp = {"getX", "getY", "getZ"}, aliases = {"func_177958_n", "func_177956_o", "func_177952_p"};
        for (int i = 0; i < 3; i++) { m = pos.visitMethod(ACC_PUBLIC, name(srg, mcp[i], aliases[i]), "()I", null, null); m.visitCode(); m.visitInsn(ICONST_1); m.visitInsn(IRETURN); finish(m); }
        out.put(POS, finish(pos));
        ClassWriter block = type(BLOCK, "java/lang/Object", null, false); field(block, "drops", "I");
        m = block.visitMethod(ACC_PUBLIC, "getDrops", "(" + ref(ACCESS) + ref(POS) + ref(STATE) + "I)Ljava/util/List;", null, null);
        m.visitCode(); increment(m, BLOCK, "drops"); m.visitInsn(ICONST_2); m.visitTypeInsn(ANEWARRAY, ITEM);
        for (int i = 0; i < 2; i++) { m.visitInsn(DUP); m.visitInsn(ICONST_0 + i); m.visitTypeInsn(NEW, ITEM); m.visitInsn(DUP); call(m, INVOKESPECIAL, ITEM, "<init>", "()V", false); m.visitInsn(AASTORE); }
        call(m, INVOKESTATIC, "java/util/Arrays", "asList", "([Ljava/lang/Object;)Ljava/util/List;", false); m.visitInsn(ARETURN); finish(m);
        out.put(BLOCK, finish(block));
        ClassWriter world = type(WORLD, "java/lang/Object", new String[] {ACCESS}, false); field(world, "spawned", "I"); field(world, "erased", "I");
        m = world.visitMethod(ACC_PUBLIC, name(srg, "getBlockState", "func_180495_p"), "(" + ref(POS) + ")" + ref(STATE), null, null);
        m.visitCode(); m.visitTypeInsn(NEW, "test/State"); m.visitInsn(DUP); call(m, INVOKESPECIAL, "test/State", "<init>", "()V", false); m.visitInsn(ARETURN); finish(m);
        m = world.visitMethod(ACC_PUBLIC, name(srg, "spawnEntity", "func_72838_d"), "(" + ref(ENTITY) + ")Z", null, null);
        m.visitCode(); increment(m, WORLD, "spawned"); m.visitInsn(ICONST_1); m.visitInsn(IRETURN); finish(m);
        m = world.visitMethod(ACC_PUBLIC, name(srg, "setBlockToAir", "func_175698_g"), "(" + ref(POS) + ")Z", null, null);
        m.visitCode(); increment(m, WORLD, "erased"); m.visitInsn(ICONST_1); m.visitInsn(IRETURN); finish(m); out.put(WORLD, finish(world));
        ClassWriter drop = type(DROP, ENTITY, null, false); field(drop, "created", "I"); field(drop, "transfers", "I"); field(drop, "dimension", "I");
        m = drop.visitMethod(ACC_PUBLIC, "<init>", "(" + ref(WORLD) + "DDD" + ref(ITEM) + ")V", null, null);
        m.visitCode(); m.visitVarInsn(ALOAD, 0); call(m, INVOKESPECIAL, ENTITY, "<init>", "()V", false); increment(m, DROP, "created"); m.visitInsn(RETURN); finish(m);
        m = drop.visitMethod(ACC_PUBLIC, name(srg, "setDefaultPickupDelay", "func_174869_p"), "()V", null, null); m.visitCode(); m.visitInsn(RETURN); finish(m);
        m = drop.visitMethod(ACC_PUBLIC, name(srg, "changeDimension", "func_184204_a"), "(I)" + ref(ENTITY), null, null);
        m.visitCode(); increment(m, DROP, "transfers"); m.visitVarInsn(ILOAD, 1); m.visitFieldInsn(PUTSTATIC, DROP, "dimension", "I"); m.visitVarInsn(ALOAD, 0); m.visitInsn(ARETURN); finish(m); out.put(DROP, finish(drop));
        ClassWriter hook = type(HOOK, "java/lang/Object", null, false); field(hook, "deny", "Z"); field(hook, "world", ref(WORLD)); field(hook, "pos", ref(POS));
        m = hook.visitMethod(ACC_PUBLIC | ACC_STATIC, "handleKamuiBlock", HOOK_DESC, null, null); m.visitCode();
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(PUTSTATIC, HOOK, "world", ref(WORLD));
        m.visitVarInsn(ALOAD, 1); m.visitFieldInsn(PUTSTATIC, HOOK, "pos", ref(POS));
        m.visitFieldInsn(GETSTATIC, HOOK, "deny", "Z"); m.visitInsn(IRETURN); finish(m); out.put(HOOK, finish(hook));
        return out;
    }

    private static ClassWriter type(String name, String parent, String[] interfaces, boolean itf) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC | (itf ? ACC_INTERFACE | ACC_ABSTRACT : ACC_SUPER), name, null, parent, interfaces);
        if (!itf) { MethodVisitor m = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null); m.visitCode(); m.visitVarInsn(ALOAD, 0); call(m, INVOKESPECIAL, parent, "<init>", "()V", false); m.visitInsn(RETURN); finish(m); }
        return writer;
    }
    /**
     * The kekkei genkai eligibility gate must install on the released class, stay idempotent,
     * ignore Minecraft member renaming (development runtimes use MCP names), and leave an
     * unrecognized class untouched instead of breaking NarutoMod's class loading.
     */
    private static void kgEligibility(String jarPath) throws Exception {
        String kg = "net/narutomod/procedure/ProcedureKGDistribution";
        byte[] raw;
        try (JarFile jar = new JarFile(jarPath); InputStream input = jar.getInputStream(jar.getJarEntry(kg + ".class"))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int length; (length = input.read(buffer)) >= 0;) output.write(buffer, 0, length);
            raw = output.toByteArray();
        }
        GuardTransformer transformer = new GuardTransformer();
        String name = kg.replace('/', '.');
        byte[] patched = transformer.transform(name, name, raw);
        check("real KG distribution receives the eligibility gate", !Arrays.equals(raw, patched));
        check("KG gate is idempotent", transformer.transform(name, name, patched) == patched);
        ClassNode renamed = read(raw);
        for (MethodNode m : renamed.methods) for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (n instanceof MethodInsnNode && ((MethodInsnNode) n).name.startsWith("func_")) ((MethodInsnNode) n).name = "mcp" + ((MethodInsnNode) n).name.substring(4);
            if (n instanceof org.objectweb.asm.tree.FieldInsnNode && ((org.objectweb.asm.tree.FieldInsnNode) n).name.startsWith("field_")) ((org.objectweb.asm.tree.FieldInsnNode) n).name = "mcp" + ((org.objectweb.asm.tree.FieldInsnNode) n).name.substring(5);
        }
        byte[] mcp = bytes(renamed);
        check("KG gate installs when Minecraft members carry development names", !Arrays.equals(mcp, transformer.transform(name, name, mcp)));
        ClassNode changed = read(raw);
        for (MethodNode m : changed.methods) if (m.name.equals("executeProcedure")) m.instructions.insert(new InsnNode(NOP));
        byte[] altered = bytes(changed);
        check("an unrecognized KG class is left untouched rather than failing to load", transformer.transform(name, name, altered) == altered);
    }

    private static void field(ClassWriter writer, String name, String desc) { writer.visitField(ACC_PUBLIC | ACC_STATIC, name, desc, null, null).visitEnd(); }
    private static void increment(MethodVisitor m, String owner, String field) { m.visitFieldInsn(GETSTATIC, owner, field, "I"); m.visitInsn(ICONST_1); m.visitInsn(IADD); m.visitFieldInsn(PUTSTATIC, owner, field, "I"); }
    private static void call(MethodVisitor m, int op, String owner, String name, String desc, boolean itf) { m.visitMethodInsn(op, owner, name, desc, itf); }
    private static void finish(MethodVisitor method) { method.visitMaxs(0, 0); method.visitEnd(); }
    private static byte[] finish(ClassWriter writer) { writer.visitEnd(); return writer.toByteArray(); }
    private static String name(boolean srg, String mcp, String alias) { return srg ? alias : mcp; }
    private static String ref(String type) { return "L" + type + ";"; }
    private static byte[] transform(GuardTransformer transformer, byte[] bytes) { return transformer.transform(KAMUI, KAMUI.replace('/', '.'), bytes); }
    private static ClassNode read(byte[] bytes) { ClassNode n = new ClassNode(ASM5); new ClassReader(bytes).accept(n, ClassReader.EXPAND_FRAMES); return n; }
    private static byte[] bytes(ClassNode node) { ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(w); return w.toByteArray(); }
    private static MethodNode method(ClassNode node) { for (MethodNode m : node.methods) if (m.name.equals("eBlock")) return m; throw new AssertionError("missing fixture method"); }
    private static void check(String name, boolean ok) { if (!ok) throw new AssertionError(name); checks++; }
    private static String hex(byte[] bytes) { StringBuilder s = new StringBuilder(); for (byte b : bytes) { s.append(Character.forDigit((b >>> 4) & 15, 16)); s.append(Character.forDigit(b & 15, 16)); } return s.toString(); }
    private static final class Loader extends ClassLoader {
        private final Map<String, byte[]> definitions;
        Loader(Map<String, byte[]> definitions) { super(null); this.definitions = definitions; }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException { byte[] raw = definitions.get(name.replace('.', '/')); if (raw == null) throw new ClassNotFoundException(name); return defineClass(name, raw, 0, raw.length); }
    }
}
