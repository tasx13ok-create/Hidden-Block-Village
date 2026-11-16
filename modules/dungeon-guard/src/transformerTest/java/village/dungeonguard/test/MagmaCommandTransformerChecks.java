package village.dungeonguard.test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import village.dungeonguard.core.GuardTransformer;

/** Independent dispatch fixtures plus optional exact-Magma bytecode inspection. */
public final class MagmaCommandTransformerChecks implements Opcodes {
    private static final String WRAPPER = "org/bukkit/craftbukkit/v1_12_R1/command/VanillaCommandWrapper";
    private static final String BUKKIT_SENDER = "org/bukkit/command/CommandSender";
    private static final String SERVER = "net/minecraft/server/MinecraftServer";
    private static final String SENDER = "net/minecraft/command/ICommandSender";
    private static final String COMMAND = "net/minecraft/command/ICommand";
    private static final String BASE = "net/minecraft/command/CommandBase";
    private static final String ACCESS = "village/dungeonguard/OperatorAccess";
    private static final String EXEC = "(" + ref(SERVER) + ref(SENDER) + "[Ljava/lang/String;)V";
    private static final String RUN = "(" + ref(COMMAND) + EXEC.substring(1);
    private static final String DISPATCH = "(" + ref(BUKKIT_SENDER) + ref(SENDER) + "[Ljava/lang/String;)I";
    private static int checks;

    public static int run() throws Exception {
        checks = 0; exercise(false); exercise(true); negatives(); startupCases(); return checks;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Supply exact Magma b4c01d2 JAR, official joined.srg and Minecraft 1.12.2 server JAR paths");
        checks = 0;
        String hash = hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Paths.get(args[0]))));
        check("exact inspected Magma artifact", hash.equals("2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce"));
        try (JarFile jar = new JarFile(args[0]); JarFile minecraft = new JarFile(args[2])) {
            byte[] original = readEntry(jar, WRAPPER + ".class");
            ClassWriter named = new ClassWriter(0);
            new ClassReader(original).accept(new ClassRemapper(named, new SrgNames(jar, minecraft, args[1])), 0);
            byte[] input = named.toByteArray();
            check("real Magma has exactly two native bypass calls", calls(read(input), BASE, "func_184881_a", EXEC) == 2);
            check("real Bukkit player listener is a native EntityPlayerMP", calls(read(input),
                    "org/bukkit/craftbukkit/v1_12_R1/entity/CraftPlayer", "getHandle", "()Lnet/minecraft/entity/player/EntityPlayerMP;") == 1);
            check("real local-console listener is the native server", calls(read(input),
                    "org/bukkit/craftbukkit/v1_12_R1/CraftServer", "getServer", "()" + ref(SERVER)) == 1);
            check("real command-block listener remains a separate native sender", calls(read(input),
                    "org/bukkit/craftbukkit/v1_12_R1/command/CraftBlockCommandSender", "getTileEntity", "()" + ref(SENDER)) == 1);
            GuardTransformer transformer = new GuardTransformer();
            byte[] after = transform(transformer, input);
            check("real Magma wrapper receives both guarded dispatches", calls(read(after), ACCESS, "runCommand", RUN) == 2);
            check("real Magma leaves no direct execute bypass", calls(read(after), BASE, "func_184881_a", EXEC) == 0);
            check("real Magma transformation is idempotent", transform(transformer, after) == after);
            ClassNode changed = read(input);
            for (AbstractInsnNode node : dispatch(changed).instructions.toArray()) if (node instanceof MethodInsnNode
                    && ((MethodInsnNode) node).owner.equals(BASE) && ((MethodInsnNode) node).desc.equals(EXEC)) {
                AbstractInsnNode arg = previous(node); ((VarInsnNode) previous(arg)).var = 1; break;
            }
            reject("real Magma changed sender operand is rejected", changed);
        }
        System.out.println("DUNGEON_GUARD_REAL_MAGMA_TRANSFORMER_PASS checks=" + checks + " sha256=" + hash
                + " scope=static-bytecode-no-live-Magma-acceptance");
    }

    private static void exercise(boolean srg) throws Exception {
        Map<String, byte[]> definitions = fixtures(srg);
        byte[] before = definitions.get(WRAPPER), after = transform(new GuardTransformer(), before);
        check("Magma wrapper is transformed", !Arrays.equals(before, after));
        check("Magma wrapper transformation is idempotent", transform(new GuardTransformer(), after) == after);
        definitions.put(WRAPPER, after);
        Loader loader = new Loader(definitions);
        Class<?> access = loader.loadClass(ACCESS.replace('/', '.'));
        Class<?> base = loader.loadClass(BASE.replace('/', '.'));
        Class<?> sender = loader.loadClass(SENDER.replace('/', '.'));
        Class<?> bukkit = loader.loadClass(BUKKIT_SENDER.replace('/', '.'));
        Object nativeSender = loader.loadClass("test.NativeSender").newInstance();
        Object wrapper = loader.loadClass(WRAPPER.replace('/', '.')).newInstance();
        Method invoke = wrapper.getClass().getMethod("dispatchVanillaCommand", bukkit, sender, String[].class);
        for (String[] arguments : new String[][] {new String[0], new String[] {"selector"}}) {
            access.getField("deny").setBoolean(null, false);
            base.getField("calls").setInt(null, 0); access.getField("calls").setInt(null, 0);
            check("Magma dispatch retains its result", ((Integer) invoke.invoke(wrapper, null, nativeSender, arguments)) == 1);
            check("Magma dispatch enters the scoped command helper once", access.getField("calls").getInt(null) == 1);
            check("Magma dispatch keeps its original command once", base.getField("calls").getInt(null) == 1);
            check("Magma dispatch passes the actual native sender", access.getField("sender").get(null) == nativeSender);
            check("Magma dispatch preserves the original argument array", access.getField("args").get(null) == arguments);
            access.getField("deny").setBoolean(null, true); base.getField("calls").setInt(null, 0);
            try { invoke.invoke(wrapper, null, nativeSender, arguments); throw new AssertionError("Expected dispatch denial"); }
            catch (InvocationTargetException expected) { check("Magma helper exception follows the dispatch exception path", expected.getCause() instanceof IllegalStateException); }
            check("Magma denied dispatch has no command side effects", base.getField("calls").getInt(null) == 0);
        }
    }

    private static void negatives() {
        ClassNode node = read(fixtures(false).get(WRAPPER)); dispatch(node).desc = "()I";
        reject("changed Magma dispatch descriptor", node);
        node = read(fixtures(false).get(WRAPPER)); dispatch(node).access |= ACC_STATIC;
        reject("static Magma dispatch", node);
        node = read(fixtures(false).get(WRAPPER));
        MethodInsnNode call = commandCalls(dispatch(node)).get(0); call.name = "unexpectedExecute";
        reject("missing Magma execute site", node);
        node = read(fixtures(false).get(WRAPPER));
        call = commandCalls(dispatch(node)).get(0); ((VarInsnNode) previous(call)).var = 2;
        reject("changed Magma arguments operand", node);
        node = read(fixtures(false).get(WRAPPER));
        call = commandCalls(dispatch(node)).get(0);
        AbstractInsnNode field = previous(previous(previous(previous(call)))); ((FieldInsnNode) field).name = "otherCommand";
        reject("changed Magma command field", node);
        node = read(transform(new GuardTransformer(), fixtures(false).get(WRAPPER)));
        for (AbstractInsnNode instruction : dispatch(node).instructions.toArray()) if (instruction instanceof MethodInsnNode
                && ((MethodInsnNode) instruction).owner.equals(ACCESS)) { ((VarInsnNode) previous(instruction)).var = 2; break; }
        reject("malformed existing Magma dispatch guard", node);
    }

    private static Map<String, byte[]> fixtures(boolean srg) {
        Map<String, byte[]> out = new HashMap<String, byte[]>();
        out.put(BUKKIT_SENDER, finish(type(BUKKIT_SENDER, "java/lang/Object", null, true)));
        out.put(SENDER, finish(type(SENDER, "java/lang/Object", null, true)));
        out.put("test/NativeSender", finish(type("test/NativeSender", "java/lang/Object", new String[] {SENDER}, false)));
        String execute = srg ? "func_184881_a" : "execute";
        ClassWriter command = type(COMMAND, "java/lang/Object", null, true);
        command.visitMethod(ACC_PUBLIC | ACC_ABSTRACT, execute, EXEC, null, null).visitEnd(); out.put(COMMAND, finish(command));
        ClassWriter server = type(SERVER, "java/lang/Object", null, false);
        MethodVisitor m = server.visitMethod(ACC_PUBLIC | ACC_STATIC, "getServerInst", "()" + ref(SERVER), null, null);
        m.visitCode(); m.visitTypeInsn(NEW, SERVER); m.visitInsn(DUP); m.visitMethodInsn(INVOKESPECIAL, SERVER, "<init>", "()V", false); m.visitInsn(ARETURN); finish(m); out.put(SERVER, finish(server));
        ClassWriter base = type(BASE, "java/lang/Object", new String[] {COMMAND}, false); field(base, "calls", "I");
        m = base.visitMethod(ACC_PUBLIC, execute, EXEC, null, null); m.visitCode(); increment(m, BASE); m.visitInsn(RETURN); finish(m); out.put(BASE, finish(base));
        ClassWriter access = type(ACCESS, "java/lang/Object", null, false); field(access, "calls", "I"); field(access, "deny", "Z"); field(access, "sender", ref(SENDER)); field(access, "args", "[Ljava/lang/String;");
        m = access.visitMethod(ACC_PUBLIC | ACC_STATIC, "runCommand", RUN, null, null); m.visitCode(); increment(m, ACCESS);
        m.visitVarInsn(ALOAD, 2); m.visitFieldInsn(PUTSTATIC, ACCESS, "sender", ref(SENDER)); m.visitVarInsn(ALOAD, 3); m.visitFieldInsn(PUTSTATIC, ACCESS, "args", "[Ljava/lang/String;");
        m.visitFieldInsn(GETSTATIC, ACCESS, "deny", "Z"); Label permit = new Label(); m.visitJumpInsn(IFEQ, permit);
        m.visitTypeInsn(NEW, "java/lang/IllegalStateException"); m.visitInsn(DUP); m.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "()V", false); m.visitInsn(ATHROW); m.visitLabel(permit);
        for (int i = 0; i < 4; i++) m.visitVarInsn(ALOAD, i);
        m.visitMethodInsn(INVOKEINTERFACE, COMMAND, execute, EXEC, true); m.visitInsn(RETURN); finish(m); out.put(ACCESS, finish(access));
        ClassWriter wrapper = type(WRAPPER, "java/lang/Object", null, false);
        wrapper.visitField(ACC_PUBLIC, "vanillaCommand", ref(BASE), null, null).visitEnd();
        m = wrapper.visitMethod(ACC_PUBLIC | ACC_FINAL, "dispatchVanillaCommand", DISPATCH, null, null); m.visitCode();
        m.visitVarInsn(ALOAD, 0); m.visitTypeInsn(NEW, BASE); m.visitInsn(DUP); m.visitMethodInsn(INVOKESPECIAL, BASE, "<init>", "()V", false); m.visitFieldInsn(PUTFIELD, WRAPPER, "vanillaCommand", ref(BASE));
        m.visitMethodInsn(INVOKESTATIC, SERVER, "getServerInst", "()" + ref(SERVER), false); m.visitVarInsn(ASTORE, 6);
        m.visitVarInsn(ALOAD, 3); m.visitInsn(ARRAYLENGTH); Label other = new Label(), end = new Label(); m.visitJumpInsn(IFEQ, other);
        dispatchCall(m, execute); m.visitJumpInsn(GOTO, end); m.visitLabel(other); dispatchCall(m, execute); m.visitLabel(end);
        m.visitInsn(ICONST_1); m.visitInsn(IRETURN); finish(m); out.put(WRAPPER, finish(wrapper)); return out;
    }
    private static void dispatchCall(MethodVisitor m, String name) {
        m.visitVarInsn(ALOAD, 0); m.visitFieldInsn(GETFIELD, WRAPPER, "vanillaCommand", ref(BASE));
        m.visitVarInsn(ALOAD, 6); m.visitVarInsn(ALOAD, 2); m.visitVarInsn(ALOAD, 3); m.visitMethodInsn(INVOKEVIRTUAL, BASE, name, EXEC, false);
    }

    private static void startupCases() throws Exception {
        String statusName = "village/dungeonguard/core/HookStatus";
        byte[] statusBytes;
        try (InputStream input = MagmaCommandTransformerChecks.class.getResourceAsStream("/" + statusName + ".class")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096];
            for (int n; (n = input.read(buffer)) >= 0;) out.write(buffer, 0, n);
            statusBytes = out.toByteArray();
        }
        for (int scenario = 0; scenario < 4; scenario++) {
            Map<String, byte[]> definitions = fixtures(false);
            definitions.put(statusName, statusBytes);
            if (scenario == 3) definitions.remove(WRAPPER);
            Loader loader = new Loader(definitions, scenario != 0);
            Class<?> status = loader.loadClass(statusName.replace('/', '.'));
            if (scenario == 2) {
                Method installed = status.getDeclaredMethod("installed", String.class); installed.setAccessible(true);
                installed.invoke(null, "Magma.vanillaCommands");
            }
            Method assertion = status.getDeclaredMethod("assertMagmaCommands"); assertion.setAccessible(true);
            try {
                assertion.invoke(null);
                check(scenario == 0 ? "normal Forge skips absent Bukkit compatibility cleanly" : "observed Magma wrapper hook permits startup",
                        scenario == 0 || scenario == 2);
            } catch (InvocationTargetException failure) {
                check(scenario == 1 ? "present untransformed Bukkit wrapper fails startup" : "present unloadable Bukkit wrapper fails startup",
                        (scenario == 1 || scenario == 3) && failure.getCause() instanceof IllegalStateException
                        && failure.getCause().getMessage().contains("Dungeon Guard"));
            }
        }
    }

    /** Reads official mappings; follows real superclass/interface declarations for inherited names. */
    private static final class SrgNames extends Remapper {
        final JarFile jar, minecraft;
        final Map<String, String> classes = new HashMap<String, String>(), methods = new HashMap<String, String>(), fields = new HashMap<String, String>();
        final Map<String, String[]> inheritance = new HashMap<String, String[]>();
        SrgNames(JarFile jar, JarFile minecraft, String path) throws Exception {
            this.jar = jar; this.minecraft = minecraft;
            for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
                String[] parts = line.trim().split(" +");
                if (parts.length == 3 && parts[0].equals("CL:")) classes.put(parts[1], parts[2]);
                else if (parts.length == 3 && parts[0].equals("FD:")) fields.put(parts[1], tail(parts[2]));
                else if (parts.length == 5 && parts[0].equals("MD:")) methods.put(parts[1] + " " + parts[2], tail(parts[3]));
            }
        }
        @Override public String map(String owner) { String named = classes.get(owner); return named == null ? owner : named; }
        @Override public String mapMethodName(String owner, String name, String desc) {
            String mapped = inherited(owner, name, " " + desc, methods, new HashSet<String>()); return mapped == null ? name : mapped;
        }
        @Override public String mapFieldName(String owner, String name, String desc) {
            String mapped = inherited(owner, name, "", fields, new HashSet<String>()); return mapped == null ? name : mapped;
        }
        String inherited(String owner, String name, String suffix, Map<String, String> mapping, Set<String> visited) {
            if (!visited.add(owner)) return null;
            String result = mapping.get(owner + "/" + name + suffix); if (result != null) return result;
            String[] parents = inheritance.get(owner);
            if (parents == null) {
                List<String> found = new ArrayList<String>();
                JarFile container = jar.getJarEntry(owner + ".class") == null ? minecraft : jar;
                JarEntry entry = container.getJarEntry(owner + ".class");
                if (entry != null) try {
                    ClassReader reader = new ClassReader(readEntry(container, owner + ".class"));
                    if (reader.getSuperName() != null) found.add(reader.getSuperName()); found.addAll(Arrays.asList(reader.getInterfaces()));
                } catch (Exception failure) { throw new IllegalStateException("Cannot inspect mapping inheritance for " + owner, failure); }
                parents = found.toArray(new String[found.size()]); inheritance.put(owner, parents);
            }
            for (String parent : parents) { result = inherited(parent, name, suffix, mapping, visited); if (result != null) return result; }
            return null;
        }
        static String tail(String value) { return value.substring(value.lastIndexOf('/') + 1); }
    }
    private static byte[] readEntry(JarFile jar, String name) throws Exception {
        try (InputStream input = jar.getInputStream(jar.getJarEntry(name))) { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[8192]; for (int n; (n = input.read(b)) >= 0;) out.write(b, 0, n); return out.toByteArray(); }
    }
    private static ClassWriter type(String name, String parent, String[] interfaces, boolean itf) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); writer.visit(V1_8, ACC_PUBLIC | (itf ? ACC_INTERFACE | ACC_ABSTRACT : ACC_SUPER), name, null, parent, interfaces);
        if (!itf) { MethodVisitor m = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null); m.visitCode(); m.visitVarInsn(ALOAD, 0); m.visitMethodInsn(INVOKESPECIAL, parent, "<init>", "()V", false); m.visitInsn(RETURN); finish(m); } return writer;
    }
    private static void field(ClassWriter w, String name, String desc) { w.visitField(ACC_PUBLIC | ACC_STATIC, name, desc, null, null).visitEnd(); }
    private static void increment(MethodVisitor m, String owner) { m.visitFieldInsn(GETSTATIC, owner, "calls", "I"); m.visitInsn(ICONST_1); m.visitInsn(IADD); m.visitFieldInsn(PUTSTATIC, owner, "calls", "I"); }
    private static void finish(MethodVisitor m) { m.visitMaxs(0, 0); m.visitEnd(); }
    private static byte[] finish(ClassWriter w) { w.visitEnd(); return w.toByteArray(); }
    private static String ref(String owner) { return "L" + owner + ";"; }
    private static byte[] transform(GuardTransformer t, byte[] b) { return t.transform(WRAPPER, WRAPPER.replace('/', '.'), b); }
    private static ClassNode read(byte[] b) { ClassNode n = new ClassNode(ASM5); new ClassReader(b).accept(n, ClassReader.EXPAND_FRAMES); return n; }
    private static byte[] bytes(ClassNode n) { ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS); n.accept(w); return w.toByteArray(); }
    private static MethodNode dispatch(ClassNode n) { for (MethodNode m : n.methods) if (m.name.equals("dispatchVanillaCommand")) return m; throw new AssertionError("fixture dispatch missing"); }
    private static List<MethodInsnNode> commandCalls(MethodNode m) { List<MethodInsnNode> out = new ArrayList<MethodInsnNode>(); for (AbstractInsnNode n : m.instructions.toArray()) if (n instanceof MethodInsnNode && ((MethodInsnNode) n).owner.equals(BASE) && ((MethodInsnNode) n).desc.equals(EXEC)) out.add((MethodInsnNode) n); return out; }
    private static int calls(ClassNode n, String owner, String name, String desc) { int count = 0; for (MethodNode m : n.methods) for (AbstractInsnNode i : m.instructions.toArray()) if (i instanceof MethodInsnNode) { MethodInsnNode c = (MethodInsnNode) i; if (c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc)) count++; } return count; }
    private static AbstractInsnNode previous(AbstractInsnNode n) { do { n = n.getPrevious(); } while (n != null && n.getOpcode() < 0); return n; }
    private static void reject(String message, ClassNode n) { try { transform(new GuardTransformer(), bytes(n)); throw new AssertionError("Expected rejection: " + message); } catch (IllegalStateException expected) { check(message, expected.getMessage().contains("Dungeon Guard cannot safely protect")); } }
    private static void check(String name, boolean ok) { if (!ok) throw new AssertionError(name); checks++; }
    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) { s.append(Character.forDigit((x >>> 4) & 15, 16)); s.append(Character.forDigit(x & 15, 16)); } return s.toString(); }
    private static final class Loader extends ClassLoader {
        final Map<String, byte[]> definitions;
        final boolean wrapperResource;
        Loader(Map<String, byte[]> definitions) { this(definitions, false); }
        Loader(Map<String, byte[]> definitions, boolean wrapperResource) { super(null); this.definitions = definitions; this.wrapperResource = wrapperResource; }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = definitions.get(name.replace('.', '/'));
            if (bytes == null) throw new ClassNotFoundException(name);
            return defineClass(name, bytes, 0, bytes.length);
        }
        @Override protected java.net.URL findResource(String name) {
            if (!wrapperResource || !name.equals(WRAPPER + ".class")) return null;
            try { return new java.net.URL("file:/dungeonguard-test-presence-only.class"); }
            catch (java.net.MalformedURLException impossible) { throw new AssertionError(impossible); }
        }
    }
}
