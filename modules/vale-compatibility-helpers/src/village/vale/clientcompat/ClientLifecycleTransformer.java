package village.vale.clientcompat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/** Fix only the observed1.12 integrated-stop race; no world, network or game data is rewritten. */
public final class ClientLifecycleTransformer implements IClassTransformer {
    @Override public byte[] transform(String name,String transformedName,byte[] original){
        if(original==null||!"net.minecraft.server.integrated.IntegratedServer".equals(transformedName))return original;
        ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);int[] hooks={0};
        reader.accept(new ClassVisitor(Opcodes.ASM5,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                MethodVisitor base=super.visitMethod(access,name,desc,signature,exceptions);if(!desc.equals("()V")||!name.equals("func_71263_m")&&!name.equals("initiateShutdown"))return base;
                return new MethodVisitor(Opcodes.ASM5,base){
                    @Override public void visitMethodInsn(int opcode,String owner,String method,String descriptor,boolean itf){
                        if(opcode==Opcodes.INVOKESTATIC&&owner.equals("com/google/common/util/concurrent/Futures")&&method.equals("getUnchecked")&&descriptor.equals("(Ljava/util/concurrent/Future;)Ljava/lang/Object;")){
                            super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,"village/vale/clientcompat/SafeShutdown","await","(Ljava/util/concurrent/Future;Lnet/minecraft/server/MinecraftServer;)Ljava/lang/Object;",false);hooks[0]++;
                        }else super.visitMethodInsn(opcode,owner,method,descriptor,itf);
                    }
                };
            }
        },0);
        if(hooks[0]!=1)throw new IllegalStateException("Unsupported integrated shutdown layout; expected one wait, got "+hooks[0]);
        System.out.println("VALE_CLIENT_SHUTDOWN_HOOK: one integrated wait guarded against a fully ended server thread.");return writer.toByteArray();
    }
}
