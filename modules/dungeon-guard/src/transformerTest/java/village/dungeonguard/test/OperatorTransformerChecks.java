package village.dungeonguard.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import village.dungeonguard.core.GuardTransformer;

/** Executes both naming variants in the Java 8 verifier, including exception cleanup. */
public final class OperatorTransformerChecks implements Opcodes {
    private static final String MANAGER = "net/minecraft/server/management/PlayerInteractionManager";
    private static final String HANDLER = "net/minecraft/command/CommandHandler";
    private static final String OP = "village/dungeonguard/OperatorAccess";
    private static final String SCOPE = OP + "$Scope";
    private static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String POS = "net/minecraft/util/math/BlockPos";
    private static final String ITEM = "net/minecraft/item/ItemStack";
    private static final String HAND = "net/minecraft/util/EnumHand";
    private static final String FACE = "net/minecraft/util/EnumFacing";
    private static final String RESULT = "net/minecraft/util/EnumActionResult";
    private static final String COMMAND = "net/minecraft/command/ICommand";
    private static final String SERVER = "net/minecraft/server/MinecraftServer";
    private static final String SENDER = "net/minecraft/command/ICommandSender";
    private static final String IMPL = "fixture/Command";
    private static final String EXEC = "(" + ref(SERVER) + ref(SENDER) + "[Ljava/lang/String;)V";
    private static final String RUN = "(" + ref(COMMAND) + EXEC.substring(1);
    private static int checks;

    public static int run() throws Exception {
        checks = 0;
        exercise(false); exercise(true);
        rejectCases();
        return checks;
    }

    private static void exercise(boolean srg) throws Exception {
        Map<String, byte[]> classes = fixtures(srg);
        GuardTransformer transformer = new GuardTransformer();
        for (String owner : new String[] {MANAGER, HANDLER}) {
            byte[] before = classes.get(owner), after = transform(transformer, owner, before);
            check(owner + " operator scope injected", !Arrays.equals(before, after));
            check(owner + " operator scope idempotent", transform(transformer, owner, after) == after);
            classes.put(owner, after);
        }
        Loader loader = new Loader(classes);
        Class<?> operator = loader.loadClass(OP.replace('/', '.'));
        Object manager = instance(loader, MANAGER);
        Object player = instance(loader, PLAYER), world = instance(loader, WORLD), item = instance(loader, ITEM);
        Object hand = instance(loader, HAND), pos = instance(loader, POS), face = instance(loader, FACE);
        String[] names = {srg ? "func_180237_b" : "tryHarvestBlock", srg ? "func_187251_a" : "processRightClickBlock",
                srg ? "func_187250_a" : "processRightClick"};
        Class<?>[][] parameters = {{pos.getClass()},
                {player.getClass(), world.getClass(), item.getClass(), hand.getClass(), pos.getClass(), face.getClass(), float.class, float.class, float.class},
                {player.getClass(), world.getClass(), item.getClass(), hand.getClass()}};
        Object[][] args = {{pos}, {player, world, item, hand, pos, face, .2F, .3F, .4F}, {player, world, item, hand}};
        for (int i = 0; i < names.length; i++) {
            Method method = manager.getClass().getMethod(names[i], parameters[i]);
            manager.getClass().getField("fail").setBoolean(null, false);
            int before = operator.getField("closes").getInt(null);
            Object result = method.invoke(manager, args[i]);
            check(names[i] + " retains original result", i == 0 ? Boolean.TRUE.equals(result) : result != null && result.getClass().getName().equals(RESULT.replace('/', '.')));
            check(names[i] + " original executes with one active scope", manager.getClass().getField("observed").getInt(null) == 1);
            check(names[i] + " closes on return exactly once", operator.getField("closes").getInt(null) == before + 1
                    && operator.getField("active").getInt(null) == 0);
            manager.getClass().getField("fail").setBoolean(null, true);
            boolean threw = false;
            try { method.invoke(manager, args[i]); }
            catch (InvocationTargetException e) { threw = e.getCause() instanceof IllegalStateException; }
            check(names[i] + " original exception retained", threw);
            check(names[i] + " closes on throw exactly once", operator.getField("closes").getInt(null) == before + 2
                    && operator.getField("active").getInt(null) == 0);
        }
        Object handler = instance(loader, HANDLER), command = instance(loader, IMPL);
        Object server = instance(loader, SERVER), sender = instance(loader, SENDER);
        String[] strings = {"3", "4", "5"};
        Method execute = handler.getClass().getMethod("dispatch", loader.loadClass(COMMAND.replace('/', '.')),
                server.getClass(), sender.getClass(), String[].class);
        execute.invoke(handler, command, server, sender, strings);
        check("command receiver preserved", operator.getField("command").get(null) == command);
        check("command server preserved", operator.getField("server").get(null) == server);
        check("command sender preserved", operator.getField("sender").get(null) == sender);
        check("command argument array preserved", operator.getField("arguments").get(null) == strings);
        check("command executes once through helper", command.getClass().getField("calls").getInt(null) == 1);
    }

    private static void rejectCases() {
        Map<String, byte[]> fixtures = fixtures(false);
        ClassNode missing = read(fixtures.get(MANAGER));
        missing.methods.remove(find(missing, "processRightClick"));
        reject("missing player action", MANAGER, bytes(missing));
        ClassNode collision = read(fixtures.get(MANAGER));
        find(collision, "processRightClick").name = "dungeonguard$original$tryHarvestBlock";
        reject("operator helper collision", MANAGER, bytes(collision));
        byte[] guarded = transform(new GuardTransformer(), MANAGER, fixtures.get(MANAGER));
        ClassNode brokenFinally = read(guarded);
        find(brokenFinally, "tryHarvestBlock").tryCatchBlocks.clear();
        reject("tampered finally wrapper", MANAGER, bytes(brokenFinally));
        ClassNode noCall = read(fixtures.get(HANDLER));
        MethodNode dispatch = find(noCall, "dispatch");
        for (org.objectweb.asm.tree.AbstractInsnNode n = dispatch.instructions.getFirst(); n != null; n = n.getNext())
            if (n instanceof MethodInsnNode) ((MethodInsnNode) n).name = "wrongExecute";
        reject("unsupported command execution site", HANDLER, bytes(noCall));
    }

    private static Map<String, byte[]> fixtures(boolean srg) {
        Map<String, byte[]> result = new LinkedHashMap<String, byte[]>();
        for (String type : new String[] {PLAYER, WORLD, POS, ITEM, HAND, FACE, RESULT, SERVER, SENDER}) result.put(type, finish(type(type)));
        ClassWriter manager = type(MANAGER);
        field(manager, "fail", "Z"); field(manager, "observed", "I");
        action(manager, srg ? "func_180237_b" : "tryHarvestBlock", "(" + ref(POS) + ")Z");
        action(manager, srg ? "func_187251_a" : "processRightClickBlock",
                "(" + ref(PLAYER) + ref(WORLD) + ref(ITEM) + ref(HAND) + ref(POS) + ref(FACE) + "FFF)" + ref(RESULT));
        action(manager, srg ? "func_187250_a" : "processRightClick",
                "(" + ref(PLAYER) + ref(WORLD) + ref(ITEM) + ref(HAND) + ")" + ref(RESULT));
        result.put(MANAGER, finish(manager));
        result.put(OP, operator(srg)); result.put(SCOPE, scope());
        ClassWriter command = new ClassWriter(0);
        command.visit(V1_8, ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE, COMMAND, null, "java/lang/Object", null);
        command.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, srg ? "func_184881_a" : "execute", EXEC, null, null).visitEnd();
        result.put(COMMAND, finish(command));
        ClassWriter impl = type(IMPL, new String[] {COMMAND});
        field(impl, "calls", "I");
        MethodVisitor execute = impl.visitMethod(ACC_PUBLIC, srg ? "func_184881_a" : "execute", EXEC, null, null);
        execute.visitCode(); increment(execute, IMPL, "calls", 1); execute.visitInsn(RETURN); end(execute);
        result.put(IMPL, finish(impl));
        ClassWriter handler = type(HANDLER);
        MethodVisitor dispatch = handler.visitMethod(ACC_PUBLIC, "dispatch", RUN, null, null);
        dispatch.visitCode();
        for (int i = 1; i <= 4; i++) dispatch.visitVarInsn(ALOAD, i);
        dispatch.visitMethodInsn(INVOKEINTERFACE, COMMAND, srg ? "func_184881_a" : "execute", EXEC, true);
        dispatch.visitInsn(RETURN); end(dispatch);
        result.put(HANDLER, finish(handler));
        return result;
    }

    private static void action(ClassWriter writer, String name, String desc) {
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, name, desc, null, null);
        method.visitCode();
        method.visitFieldInsn(GETSTATIC, OP, "active", "I"); method.visitFieldInsn(PUTSTATIC, MANAGER, "observed", "I");
        Label normal = new Label();
        method.visitFieldInsn(GETSTATIC, MANAGER, "fail", "Z"); method.visitJumpInsn(IFEQ, normal);
        method.visitTypeInsn(NEW, "java/lang/IllegalStateException"); method.visitInsn(DUP);
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false); method.visitInsn(ATHROW);
        method.visitLabel(normal);
        if (Type.getReturnType(desc).equals(Type.BOOLEAN_TYPE)) { method.visitInsn(ICONST_1); method.visitInsn(IRETURN); }
        else {
            method.visitTypeInsn(NEW, RESULT); method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESPECIAL, RESULT, "<init>", "()V", false); method.visitInsn(ARETURN);
        }
        end(method);
    }

    private static byte[] operator(boolean srg) {
        ClassWriter writer = type(OP);
        field(writer, "active", "I"); field(writer, "closes", "I");
        for (String name : new String[] {"command", "server", "sender", "arguments"}) field(writer, name, "Ljava/lang/Object;");
        String[][] definitions = {{"beginHarvest", "(" + ref(MANAGER) + ref(POS) + ")" + ref(SCOPE)},
                {"beginUseOn", "(" + ref(MANAGER) + ref(PLAYER) + ref(WORLD) + ref(ITEM) + ref(HAND) + ref(POS) + ref(FACE) + ")" + ref(SCOPE)},
                {"beginUse", "(" + ref(MANAGER) + ref(PLAYER) + ref(WORLD) + ref(ITEM) + ref(HAND) + ")" + ref(SCOPE)}};
        for (String[] definition : definitions) {
            MethodVisitor method = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, definition[0], definition[1], null, null);
            method.visitCode(); increment(method, OP, "active", 1);
            method.visitTypeInsn(NEW, SCOPE); method.visitInsn(DUP);
            method.visitMethodInsn(INVOKESPECIAL, SCOPE, "<init>", "()V", false); method.visitInsn(ARETURN); end(method);
        }
        MethodVisitor run = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "runCommand", RUN, null, null);
        run.visitCode(); String[] fields = {"command", "server", "sender", "arguments"};
        for (int i = 0; i < fields.length; i++) { run.visitVarInsn(ALOAD, i); run.visitFieldInsn(PUTSTATIC, OP, fields[i], "Ljava/lang/Object;"); }
        for (int i = 0; i < fields.length; i++) run.visitVarInsn(ALOAD, i);
        run.visitMethodInsn(INVOKEINTERFACE, COMMAND, srg ? "func_184881_a" : "execute", EXEC, true);
        run.visitInsn(RETURN); end(run);
        return finish(writer);
    }

    private static byte[] scope() {
        ClassWriter writer = type(SCOPE);
        MethodVisitor method = writer.visitMethod(ACC_PUBLIC, "close", "()V", null, null);
        method.visitCode(); increment(method, OP, "active", -1); increment(method, OP, "closes", 1);
        method.visitInsn(RETURN); end(method);
        return finish(writer);
    }

    private static void increment(MethodVisitor method, String owner, String field, int amount) {
        method.visitFieldInsn(GETSTATIC, owner, field, "I"); method.visitInsn(amount < 0 ? ICONST_M1 : ICONST_1);
        method.visitInsn(IADD); method.visitFieldInsn(PUTSTATIC, owner, field, "I");
    }
    private static ClassWriter type(String owner) { return type(owner, null); }
    private static ClassWriter type(String owner, String[] interfaces) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override protected String getCommonSuperClass(String a, String b) { return "java/lang/Object"; }
        };
        writer.visit(V1_8, ACC_PUBLIC, owner, null, "java/lang/Object", interfaces);
        MethodVisitor ctor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode(); ctor.visitVarInsn(ALOAD, 0); ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN); end(ctor); return writer;
    }
    private static void end(MethodVisitor method) { method.visitMaxs(0, 0); method.visitEnd(); }
    private static void field(ClassWriter writer, String name, String desc) { writer.visitField(ACC_PUBLIC | ACC_STATIC, name, desc, null, null).visitEnd(); }
    private static String ref(String type) { return "L" + type + ";"; }
    private static byte[] finish(ClassWriter writer) { writer.visitEnd(); return writer.toByteArray(); }
    private static ClassNode read(byte[] raw) { ClassNode node = new ClassNode(ASM5); new ClassReader(raw).accept(node, ClassReader.EXPAND_FRAMES); return node; }
    private static byte[] bytes(ClassNode node) { ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray(); }
    private static MethodNode find(ClassNode owner, String name) {
        for (MethodNode method : owner.methods) if (method.name.equals(name)) return method;
        throw new AssertionError(name);
    }
    private static byte[] transform(GuardTransformer transformer, String owner, byte[] raw) { return transformer.transform(owner, owner.replace('/', '.'), raw); }
    private static Object instance(Loader loader, String name) throws Exception { return loader.loadClass(name.replace('/', '.')).newInstance(); }
    private static void check(String message, boolean value) { if (!value) throw new AssertionError(message); checks++; }
    private static void reject(String message, String owner, byte[] raw) {
        boolean rejected = false;
        try { transform(new GuardTransformer(), owner, raw); } catch (IllegalStateException expected) { rejected = true; }
        check(message, rejected);
    }
    private static final class Loader extends ClassLoader {
        private final Map<String, byte[]> classes;
        Loader(Map<String, byte[]> classes) { super(null); this.classes = classes; }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] raw = classes.get(name.replace('.', '/'));
            if (raw == null) throw new ClassNotFoundException(name);
            return defineClass(name, raw, 0, raw.length);
        }
    }
}
