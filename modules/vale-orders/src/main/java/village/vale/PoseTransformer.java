package village.vale;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/** Apply after the vanilla biped pose so player skin, clothing and armor use the same eased arms. */
public final class PoseTransformer implements IClassTransformer {
    @Override public byte[] transform(String name,String transformedName,byte[] original){
        if(original==null||!"net.minecraft.client.model.ModelBiped".equals(transformedName))return original;
        ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);final int[] methods={0},returns={0};
        reader.accept(new ClassVisitor(Opcodes.ASM5,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                MethodVisitor next=super.visitMethod(access,name,desc,signature,exceptions);
                if(!(name.equals("setRotationAngles")||name.equals("func_78087_a")||name.equals("a"))||!desc.startsWith("(FFFFFFL")||!desc.endsWith(";)V"))return next;
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM5,next){
                    @Override public void visitCode(){super.visitCode();super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,7);super.visitVarInsn(Opcodes.FLOAD,2);super.visitVarInsn(Opcodes.FLOAD,3);super.visitMethodInsn(Opcodes.INVOKESTATIC,"village/vale/RunPoses","capture","(Lnet/minecraft/client/model/ModelBiped;Lnet/minecraft/entity/Entity;FF)V",false);}
                    @Override public void visitInsn(int opcode){
                        // The production Mojang stack-map frames discard dead argument slots at the
                        // return. Capture them at entry; only 'this' is guaranteed live here.
                        if(opcode==Opcodes.RETURN){super.visitVarInsn(Opcodes.ALOAD,0);super.visitMethodInsn(Opcodes.INVOKESTATIC,"village/vale/RunPoses","finish","(Lnet/minecraft/client/model/ModelBiped;)V",false);returns[0]++;}
                        super.visitInsn(opcode);
                    }
                };
            }
        },0);
        if(methods[0]!=1||returns[0]<1)throw new IllegalStateException("Vale 1.12.2 run-pose hook did not match the biped model: "+methods[0]+" methods, "+returns[0]+" exits");
        System.out.println("VALE_RUNPOSE_HOOK_PASS: one biped animation method hooked for player, clothing and armor poses.");return writer.toByteArray();
    }
}
