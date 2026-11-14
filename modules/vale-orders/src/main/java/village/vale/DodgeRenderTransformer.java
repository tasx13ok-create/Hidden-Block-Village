package village.vale;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/** Presentation-only hook inside vanilla's already-scoped body matrix, never a render-event push/pop. */
public final class DodgeRenderTransformer implements IClassTransformer {
    @Override public byte[] transform(String name,String transformedName,byte[] original){
        if(original==null||!"net.minecraft.client.renderer.entity.RenderLivingBase".equals(transformedName))return original;
        ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);final int[] hooked={0};
        reader.accept(new ClassVisitor(Opcodes.ASM5,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                MethodVisitor next=super.visitMethod(access,method,descriptor,signature,exceptions);
                if((access&(Opcodes.ACC_BRIDGE|Opcodes.ACC_SYNTHETIC))!=0||!(method.equals("applyRotations")||method.equals("func_77043_a")||method.equals("a"))||!descriptor.startsWith("(L")||!descriptor.endsWith(";FFF)V"))return next;
                hooked[0]++;return new MethodVisitor(Opcodes.ASM5,next){@Override public void visitCode(){super.visitCode();super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.FLOAD,3);super.visitVarInsn(Opcodes.FLOAD,4);super.visitMethodInsn(Opcodes.INVOKESTATIC,"village/vale/client/DodgeClient","rotate","(Lnet/minecraft/entity/Entity;FF)V",false);}};
            }
        },0);
        if(hooked[0]!=1)throw new IllegalStateException("Vale dodge body rotation hook matched "+hooked[0]+" methods");
        System.out.println("VALE_DODGE_RENDER_HOOK_PASS: native scoped body rotation.");return writer.toByteArray();
    }
}
