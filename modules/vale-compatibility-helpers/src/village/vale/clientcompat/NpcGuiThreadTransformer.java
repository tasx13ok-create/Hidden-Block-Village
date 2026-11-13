package village.vale.clientcompat;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import org.objectweb.asm.*;

/** Original narrow hook. No client game classes are resolved by this transformer. */
public final class NpcGuiThreadTransformer implements IClassTransformer {
    static final String TARGET = "micdoodle8.mods.galacticraft.api.client.tabs.InventoryTabQuests";
    static final String METHOD = "onTabClicked";
    static final String SCHEDULER = "noppes/npcs/util/CustomNPCsScheduler";
    static final String RUNNABLE = "(Ljava/lang/Runnable;)V";
    static final String HELPER = "village/vale/clientcompat/NpcGuiThreadDispatch";

    @Override public byte[] transform(String name, String transformedName, byte[] original) {
        if (original == null || !TARGET.equals(transformedName)) return original;
        if (!FMLLaunchHandler.side().isClient()) return original;
        SupportedNpcSource.verify(Launch.classLoader.getResource(TARGET.replace('.', '/') + ".class"));
        return rewrite(original);
    }

    // Package-private fixture seam; production always passes the side/source checks above.
    static byte[] rewrite(byte[] original) {
        ClassReader reader = new ClassReader(original);
        if (!TARGET.replace('.', '/').equals(reader.getClassName()))
            throw new IllegalStateException("Unexpected CustomNPCs GUI class identity");
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        final int[] methods = {0}, calls = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM5, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor next = super.visitMethod(access, name, desc, signature, exceptions);
                if (!METHOD.equals(name) || !"()V".equals(desc)) return next;
                if ((access & Opcodes.ACC_PUBLIC) == 0 || (access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0)
                    throw new IllegalStateException("Unexpected CustomNPCs quest-tab callback flags");
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM5, next) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean itf) {
                        if (opcode == Opcodes.INVOKESTATIC && !itf && SCHEDULER.equals(owner)
                                && "runTack".equals(method) && RUNNABLE.equals(descriptor)) {
                            // Identical (Runnable)V ABI. The complete original lambda stays untouched.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HELPER, "run", RUNNABLE, false);
                            calls[0]++;
                        } else super.visitMethodInsn(opcode, owner, method, descriptor, itf);
                    }
                };
            }
        }, 0);
        if (methods[0] != 1 || calls[0] != 1)
            throw new IllegalStateException("Unsupported CustomNPCs quest-tab layout: " + methods[0] + " callbacks, " + calls[0] + " scheduler calls");
        System.out.println("VALE_NPC_GUI_THREAD_HOOK: one supported quest-tab Runnable dispatched entirely to the client thread.");
        return writer.toByteArray();
    }
}
