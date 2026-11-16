package village.dungeonguard.test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import village.dungeonguard.core.GuardTransformer;

/** JVM-loadable synthetic cases exercise injected branches and stack-map frames. */
public final class TransformerChecks implements Opcodes {
    private static final String WORLD = "net/minecraft/world/World";
    private static final String CHUNK = "net/minecraft/world/chunk/Chunk";
    private static final String BLOCK = "net/minecraft/block/Block";
    private static final String PISTON = "net/minecraft/block/BlockPistonBase";
    private static final String POS = "net/minecraft/util/math/BlockPos";
    private static final String STATE = "net/minecraft/block/state/IBlockState";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String ITEM_ENTITY = "net/minecraft/entity/item/EntityItem";
    private static final String ITEM = "net/minecraft/item/ItemStack";
    private static final String FACING = "net/minecraft/util/EnumFacing";
    private static final String HOOK = "village/dungeonguard/GuardHooks";
    private static final String UTILS = "net/narutomod/procedure/ProcedureUtils";
    private static final String PORTAL = "net/narutomod/procedure/ProcedureYomotsuHirasaka";
    private static final String WRITE = "(" + ref(POS) + ref(STATE) + "I)Z";
    private static final String BREAK = "(" + ref(WORLD) + ref(POS) + "FFFZ)" + ref(ITEM_ENTITY);
    private static int checks;

    public static void main(String[] ignored) throws Exception {
        GuardTransformer transformer = new GuardTransformer();
        byte[] arbitrary = {1, 2, 3};
        check("unrelated bytes preserved by identity", transformer.transform("other", "other", arbitrary) == arbitrary);
        check("null bytes accepted", transformer.transform(WORLD, WORLD.replace('/', '.'), null) == null);
        check("null name leaves bytes unchanged", transformer.transform("other", null, arbitrary) == arbitrary);
        exercise(false);
        exercise(true);
        unsupportedCases();
        checks += OperatorTransformerChecks.run();
        checks += KamuiTransformerChecks.run();
        checks += MagmaCommandTransformerChecks.run();
        System.out.println("DUNGEON_GUARD_TRANSFORMER_PASS checks=" + checks);
    }

    private static void exercise(boolean srg) throws Exception {
        Map<String, byte[]> classes = fixtures(srg, 4, false);
        GuardTransformer transformer = new GuardTransformer();
        for (String owner : new String[] {WORLD, CHUNK, BLOCK, PISTON, UTILS, PORTAL}) {
            byte[] before = classes.get(owner), after = transform(transformer, owner, before);
            check(owner + " was instrumented", !Arrays.equals(before, after));
            check(owner + " transformation is idempotent", transform(transformer, owner, after) == after);
            classes.put(owner, after);
        }
        Loader loader = new Loader(classes);
        Class<?> hooks = loader.loadClass(HOOK.replace('/', '.'));
        Object world = instance(loader, WORLD), pos = instance(loader, POS), state = instance(loader, STATE);
        Object entity = instance(loader, ENTITY), item = instance(loader, ITEM), facing = instance(loader, FACING);
        Class<?> wc = world.getClass();

        checkGate(hooks, wc, world, srg ? "func_180501_a" : "setBlockState",
                new Class<?>[] {pos.getClass(), state.getClass(), int.class}, new Object[] {pos, state, 3}, Boolean.FALSE, Boolean.TRUE);
        checkGate(hooks, wc, world, srg ? "func_175655_b" : "destroyBlock",
                new Class<?>[] {pos.getClass(), boolean.class}, new Object[] {pos, true}, Boolean.FALSE, Boolean.TRUE);
        checkGate(hooks, wc, world, srg ? "func_72838_d" : "spawnEntity",
                new Class<?>[] {entity.getClass()}, new Object[] {entity}, Boolean.FALSE, Boolean.TRUE);
        Object chunk = instance(loader, CHUNK);
        checkGate(hooks, chunk.getClass(), chunk, srg ? "func_177436_a" : "setBlockState",
                new Class<?>[] {pos.getClass(), state.getClass()}, new Object[] {pos, state}, null, state);
        Object block = instance(loader, BLOCK);
        checkGate(hooks, block.getClass(), block, srg ? "func_180653_a" : "dropBlockAsItemWithChance",
                new Class<?>[] {wc, pos.getClass(), state.getClass(), float.class, int.class}, new Object[] {world, pos, state, 0.5F, 0}, null, null);
        checkGate(hooks, block.getClass(), null, srg ? "func_180635_a" : "spawnAsEntity",
                new Class<?>[] {wc, pos.getClass(), item.getClass()}, new Object[] {world, pos, item}, null, null);
        Object piston = instance(loader, PISTON);
        checkGate(hooks, piston.getClass(), piston, srg ? "func_176319_a" : "doMove",
                new Class<?>[] {wc, pos.getClass(), facing.getClass(), boolean.class}, new Object[] {world, pos, facing, true}, Boolean.FALSE, Boolean.TRUE);

        Class<?> utils = loader.loadClass(UTILS.replace('/', '.'));
        Method breaker = utils.getMethod("breakBlockAndDropWithChance", wc, pos.getClass(), float.class, float.class, float.class, boolean.class);
        hooks.getField("deny").setBoolean(null, true);
        utils.getField("calls").setInt(null, 0);
        hooks.getField("filters").setInt(null, 0);
        check("AHZNB helper blocked before destruction", breaker.invoke(null, world, pos, 3F, 1F, 0.6F, true) == null);
        check("AHZNB helper blocked before side effects", utils.getField("calls").getInt(null) == 0);
        check("AHZNB denied helper does not filter", hooks.getField("filters").getInt(null) == 0);
        hooks.getField("deny").setBoolean(null, false);
        hooks.getField("dropFilter").setFloat(null, -1F);
        check("AHZNB allowed helper still returns its item", breaker.invoke(null, world, pos, 3F, 1F, 0.6F, true) != null);
        check("AHZNB helper runs exactly once", utils.getField("calls").getInt(null) == 1);
        check("AHZNB manual drop chance becomes negative", utils.getField("observedChance").getFloat(null) == -1F);
        check("AHZNB drop filter receives original chance", hooks.getField("inputChance").getFloat(null) == 0.6F);
        check("AHZNB drop filter invoked exactly once", hooks.getField("filters").getInt(null) == 1);

        Class<?> portal = loader.loadClass(PORTAL.replace('/', '.'));
        Method execute = portal.getMethod("executeProcedure", Map.class);
        Object destination = instance(loader, POS);
        Map<String, Object> dependencies = new HashMap<String, Object>();
        dependencies.put("world", world); dependencies.put("source", pos);
        dependencies.put("destination", destination); dependencies.put("state", state);
        wc.getField("calls").setInt(null, 0);
        hooks.getField("deny").setBoolean(null, true);
        execute.invoke(null, dependencies);
        check("portal denied before any of four writes", wc.getField("calls").getInt(null) == 0);
        check("portal preflight receives correct World", hooks.getField("portalWorld").get(null) == world);
        check("portal preflight receives source target", hooks.getField("portalA").get(null) == pos);
        check("portal preflight receives destination target", hooks.getField("portalB").get(null) == destination);
        hooks.getField("deny").setBoolean(null, false);
        execute.invoke(null, dependencies);
        check("allowed portal executes all four original writes", wc.getField("calls").getInt(null) == 4);
        check("wide locals survive portal continuation frame", portal.getField("observedDouble").getDouble(null) == 7.25D);
    }

    private static void checkGate(Class<?> hooks, Class<?> owner, Object receiver, String name,
                                  Class<?>[] parameters, Object[] args, Object denied, Object allowed) throws Exception {
        Method method = owner.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        owner.getField("calls").setInt(null, 0);
        hooks.getField("deny").setBoolean(null, true);
        check(name + " returns the denied value", equal(method.invoke(receiver, args), denied));
        check(name + " blocks side effects", owner.getField("calls").getInt(null) == 0);
        hooks.getField("deny").setBoolean(null, false);
        check(name + " retains allowed return", equal(method.invoke(receiver, args), allowed));
        check(name + " retains one allowed side effect", owner.getField("calls").getInt(null) == 1);
    }

    private static void unsupportedCases() {
        final GuardTransformer transformer = new GuardTransformer();
        Map<String, byte[]> classes = fixtures(false, 4, false);
        ClassNode missing = read(classes.get(WORLD));
        missing.methods.remove(find(missing, "destroyBlock"));
        reject("missing required vanilla method", transformer, WORLD, bytes(missing));
        ClassNode wrong = read(classes.get(WORLD));
        find(wrong, "destroyBlock").desc = "(" + ref(POS) + "I)Z";
        reject("wrong descriptor is never matched by name alone", transformer, WORLD, bytes(wrong));
        ClassNode ambiguous = read(classes.get(WORLD));
        MethodNode alias = new MethodNode(ASM5, ACC_PUBLIC, "func_175655_b", "(" + ref(POS) + "Z)Z", null, null);
        alias.instructions.add(new org.objectweb.asm.tree.InsnNode(ICONST_1));
        alias.instructions.add(new org.objectweb.asm.tree.InsnNode(IRETURN));
        alias.maxLocals = 3; alias.maxStack = 1;
        ambiguous.methods.add(alias);
        reject("ambiguous MCP and SRG duplicates", transformer, WORLD, bytes(ambiguous));
        reject("wrong class owner", transformer, WORLD, simple("unrelated/World"));
        reject("three-write portal is unsupported", transformer, PORTAL, fixtures(false, 3, false).get(PORTAL));
        reject("portal destination initialized too late", transformer, PORTAL, fixtures(false, 4, true).get(PORTAL));
        ClassNode helper = read(classes.get(UTILS));
        find(helper, "breakBlockAndDropWithChance").access &= ~ACC_STATIC;
        reject("non-static AHZNB helper", transformer, UTILS, bytes(helper));
        ClassNode irregular = read(classes.get(WORLD));
        MethodNode setter = find(irregular, "setBlockState");
        org.objectweb.asm.tree.InsnList bad = new org.objectweb.asm.tree.InsnList();
        bad.add(new org.objectweb.asm.tree.InsnNode(ACONST_NULL));
        bad.add(new org.objectweb.asm.tree.InsnNode(ACONST_NULL));
        bad.add(new org.objectweb.asm.tree.InsnNode(ACONST_NULL));
        bad.add(new MethodInsnNode(INVOKESTATIC, HOOK, "blockChange", "(" + ref(WORLD) + ref(POS) + ref(STATE) + ")Z", false));
        bad.add(new org.objectweb.asm.tree.InsnNode(POP));
        setter.instructions.insert(bad);
        reject("unrecognized preexisting guard cannot masquerade as installed", transformer, WORLD, bytes(irregular));
    }

    private static void reject(String description, GuardTransformer transformer, String owner, byte[] raw) {
        try {
            transform(transformer, owner, raw);
            throw new AssertionError("Expected unsupported bytecode rejection: " + description);
        } catch (IllegalStateException expected) {
            check(description, expected.getMessage().contains("Dungeon Guard cannot safely protect"));
        }
    }

    private static Map<String, byte[]> fixtures(boolean srg, int portalWrites, boolean lateDestination) {
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
        for (String owner : new String[] {POS, STATE, ENTITY, ITEM_ENTITY, ITEM, FACING}) result.put(owner, simple(owner));
        ClassWriter world = type(WORLD);
        effect(world, WORLD, ACC_PUBLIC, srg ? "func_180501_a" : "setBlockState", WRITE, 0);
        effect(world, WORLD, ACC_PUBLIC, srg ? "func_175655_b" : "destroyBlock", "(" + ref(POS) + "Z)Z", 0);
        effect(world, WORLD, ACC_PUBLIC, srg ? "func_72838_d" : "spawnEntity", "(" + ref(ENTITY) + ")Z", 0);
        result.put(WORLD, finish(world));
        ClassWriter chunk = type(CHUNK);
        effect(chunk, CHUNK, ACC_PUBLIC, srg ? "func_177436_a" : "setBlockState", "(" + ref(POS) + ref(STATE) + ")" + ref(STATE), 2);
        result.put(CHUNK, finish(chunk));
        ClassWriter block = type(BLOCK);
        effect(block, BLOCK, ACC_PUBLIC, srg ? "func_180653_a" : "dropBlockAsItemWithChance", "(" + ref(WORLD) + ref(POS) + ref(STATE) + "FI)V", 0);
        effect(block, BLOCK, ACC_PUBLIC | ACC_STATIC, srg ? "func_180635_a" : "spawnAsEntity", "(" + ref(WORLD) + ref(POS) + ref(ITEM) + ")V", 0);
        result.put(BLOCK, finish(block));
        ClassWriter piston = type(PISTON);
        effect(piston, PISTON, ACC_PRIVATE, srg ? "func_176319_a" : "doMove", "(" + ref(WORLD) + ref(POS) + ref(FACING) + "Z)Z", 0);
        result.put(PISTON, finish(piston));
        result.put(HOOK, hooks());
        result.put(UTILS, helper());
        result.put(PORTAL, portal(srg, portalWrites, lateDestination));
        return result;
    }

    private static byte[] hooks() {
        ClassWriter writer = type(HOOK);
        field(writer, "deny", "Z"); field(writer, "dropFilter", "F"); field(writer, "inputChance", "F");
        field(writer, "filters", "I"); field(writer, "portalWorld", "Ljava/lang/Object;");
        field(writer, "portalA", "Ljava/lang/Object;"); field(writer, "portalB", "Ljava/lang/Object;");
        String[][] names = {
            {"blockChange", "(" + ref(WORLD) + ref(POS) + ref(STATE) + ")Z"},
            {"blockDestroy", "(" + ref(WORLD) + ref(POS) + ")Z"},
            {"blockSpawn", "(" + ref(WORLD) + ref(ENTITY) + ")Z"},
            {"blockChunkChange", "(" + ref(CHUNK) + ref(POS) + ref(STATE) + ")Z"},
            {"blockDrop", "(" + ref(WORLD) + ref(POS) + ")Z"},
            {"blockPiston", "(" + ref(WORLD) + ref(POS) + ref(FACING) + "Z)Z"},
            {"blockPortal", "(" + ref(WORLD) + ref(POS) + ref(POS) + ")Z"}};
        for (String[] definition : names) {
            MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, definition[0], definition[1], null, null);
            method.visitCode();
            if (definition[0].equals("blockPortal")) {
                String[] fields = {"portalWorld", "portalA", "portalB"};
                for (int i = 0; i < fields.length; i++) {
                    method.visitVarInsn(ALOAD, i);
                    method.visitFieldInsn(PUTSTATIC, HOOK, fields[i], "Ljava/lang/Object;");
                }
            }
            method.visitFieldInsn(GETSTATIC, HOOK, "deny", "Z");
            method.visitInsn(IRETURN); method.visitMaxs(0, 0); method.visitEnd();
        }
        MethodVisitor filter = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "filterDropChance", "(" + ref(WORLD) + ref(POS) + "F)F", null, null);
        filter.visitCode();
        filter.visitVarInsn(FLOAD, 2); filter.visitFieldInsn(PUTSTATIC, HOOK, "inputChance", "F");
        filter.visitFieldInsn(GETSTATIC, HOOK, "filters", "I"); filter.visitInsn(ICONST_1); filter.visitInsn(IADD);
        filter.visitFieldInsn(PUTSTATIC, HOOK, "filters", "I");
        filter.visitFieldInsn(GETSTATIC, HOOK, "dropFilter", "F"); filter.visitInsn(FRETURN);
        filter.visitMaxs(0, 0); filter.visitEnd();
        return finish(writer);
    }

    private static byte[] helper() {
        ClassWriter writer = type(UTILS);
        field(writer, "observedChance", "F");
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "breakBlockAndDropWithChance", BREAK, null, null);
        method.visitCode(); increment(method, UTILS);
        method.visitVarInsn(FLOAD, 4); method.visitFieldInsn(PUTSTATIC, UTILS, "observedChance", "F");
        method.visitTypeInsn(NEW, ITEM_ENTITY); method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, ITEM_ENTITY, "<init>", "()V", false);
        method.visitInsn(ARETURN); method.visitMaxs(0, 0); method.visitEnd();
        return finish(writer);
    }

    private static byte[] portal(boolean srg, int writes, boolean late) {
        ClassWriter writer = type(PORTAL);
        field(writer, "observedDouble", "D");
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "executeProcedure", "(Ljava/util/Map;)V", null, null);
        method.visitCode();
        org.objectweb.asm.Label notEmpty = new org.objectweb.asm.Label();
        method.visitVarInsn(ALOAD, 0); method.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "isEmpty", "()Z", true);
        method.visitJumpInsn(IFEQ, notEmpty); method.visitInsn(RETURN); method.visitLabel(notEmpty);
        fromMap(method, "world", WORLD, 1); fromMap(method, "source", POS, 2);
        if (!late) fromMap(method, "destination", POS, 3);
        fromMap(method, "state", STATE, 4);
        method.visitLdcInsn(7.25D); method.visitVarInsn(DSTORE, 5);
        for (int i = 0; i < writes; i++) {
            if (late && i == 2) fromMap(method, "destination", POS, 3);
            method.visitVarInsn(ALOAD, 1); method.visitVarInsn(ALOAD, i < 2 ? 2 : 3);
            method.visitVarInsn(ALOAD, 4); method.visitInsn(ICONST_3);
            method.visitMethodInsn(INVOKEVIRTUAL, WORLD, srg ? "func_180501_a" : "setBlockState", WRITE, false);
            method.visitInsn(POP);
        }
        method.visitVarInsn(DLOAD, 5); method.visitFieldInsn(PUTSTATIC, PORTAL, "observedDouble", "D");
        method.visitInsn(RETURN); method.visitMaxs(0, 0); method.visitEnd();
        return finish(writer);
    }

    private static void fromMap(MethodVisitor method, String key, String type, int local) {
        method.visitVarInsn(ALOAD, 0); method.visitLdcInsn(key);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        method.visitTypeInsn(CHECKCAST, type); method.visitVarInsn(ASTORE, local);
    }
    private static ClassWriter type(String owner) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
        };
        writer.visit(V1_8, ACC_PUBLIC, owner, null, "java/lang/Object", null);
        field(writer, "calls", "I");
        MethodVisitor ctor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN); ctor.visitMaxs(0, 0); ctor.visitEnd();
        return writer;
    }
    private static void field(ClassWriter writer, String name, String desc) { writer.visitField(ACC_PUBLIC | ACC_STATIC, name, desc, null, null).visitEnd(); }
    private static byte[] simple(String owner) { return finish(type(owner)); }
    private static byte[] finish(ClassWriter writer) { writer.visitEnd(); return writer.toByteArray(); }
    private static void increment(MethodVisitor method, String owner) {
        method.visitFieldInsn(GETSTATIC, owner, "calls", "I"); method.visitInsn(ICONST_1); method.visitInsn(IADD);
        method.visitFieldInsn(PUTSTATIC, owner, "calls", "I");
    }
    private static void effect(ClassWriter writer, String owner, int access, String name, String desc, int objectReturn) {
        MethodVisitor method = writer.visitMethod(access, name, desc, null, null);
        method.visitCode(); increment(method, owner);
        int sort = Type.getReturnType(desc).getSort();
        if (sort == Type.VOID) method.visitInsn(RETURN);
        else if (sort == Type.BOOLEAN) { method.visitInsn(ICONST_1); method.visitInsn(IRETURN); }
        else { method.visitVarInsn(ALOAD, objectReturn); method.visitInsn(ARETURN); }
        method.visitMaxs(0, 0); method.visitEnd();
    }
    private static ClassNode read(byte[] bytes) { ClassNode node = new ClassNode(ASM5); new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES); return node; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static MethodNode find(ClassNode node, String name) {
        for (MethodNode method : node.methods) if (method.name.equals(name)) return method;
        throw new AssertionError("Missing synthetic method " + name);
    }
    private static byte[] transform(GuardTransformer transformer, String owner, byte[] raw) {
        return transformer.transform(owner.replace('/', '.'), owner.replace('/', '.'), raw);
    }
    private static String ref(String owner) { return "L" + owner + ";"; }
    private static boolean equal(Object a, Object b) { return a == b || a != null && a.equals(b); }
    private static Object instance(ClassLoader loader, String owner) throws Exception { return loader.loadClass(owner.replace('/', '.')).newInstance(); }
    private static void check(String description, boolean value) { if (!value) throw new AssertionError(description); checks++; }

    private static final class Loader extends ClassLoader {
        private final Map<String, byte[]> classes;
        Loader(Map<String, byte[]> classes) { super(TransformerChecks.class.getClassLoader()); this.classes = classes; }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                byte[] raw = classes.get(name.replace('.', '/'));
                if (loaded == null) loaded = raw == null ? super.loadClass(name, false) : defineClass(name, raw, 0, raw.length);
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        }
    }
}
