package village.vale.clientcompat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;

/** Prefixes one native input method; original first-press behavior/body and timing remain intact. */
public final class ChargeInputTransformer implements IClassTransformer {
    private static final String METHOD="onKeyInput",DESC="(Lnet/minecraftforge/fml/common/gameevent/InputEvent$KeyInputEvent;)V";
    public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null||!ChargeInputGuard.TARGET.equals(transformedName))return bytes;
        final String identity;
        try{ChargeInputGuard.source();identity=ChargeInputGuard.input(bytes);}catch(Exception failure){throw new IllegalStateException("Naruto charge compatibility refused unknown input",failure);}
        ClassReader reader=new ClassReader(bytes);if(!reader.getClassName().replace('/','.').equals(transformedName))throw new IllegalStateException("Native charge class identity changed");
        final int[] found={0},fields={0};ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM5,writer){
            public FieldVisitor visitField(int access,String name,String desc,String signature,Object value){if(name.equals("keys")&&desc.equals("Lnet/minecraft/client/settings/KeyBinding;")&&(access&Opcodes.ACC_STATIC)==0)fields[0]++;return super.visitField(access,name,desc,signature,value);}
            public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions){
                MethodVisitor next=super.visitMethod(access,name,desc,signature,exceptions);
                if(!name.equals(METHOD)||!desc.equals(DESC))return next;
                if((access&(Opcodes.ACC_STATIC|Opcodes.ACC_NATIVE|Opcodes.ACC_ABSTRACT))!=0||++found[0]!=1)throw new IllegalStateException("Unsupported native charge input method");
                return new MethodVisitor(Opcodes.ASM5,next){public void visitCode(){
                    super.visitCode();Label nativeBody=new Label();super.visitVarInsn(Opcodes.ALOAD,0);
                    super.visitFieldInsn(Opcodes.GETFIELD,"narutomodaddon/keybind/KeyBindingChakraChargeButton","keys","Lnet/minecraft/client/settings/KeyBinding;");
                    super.visitMethodInsn(Opcodes.INVOKESTATIC,"village/vale/clientcompat/ChargeInputClient","allow","(Ljava/lang/Object;)Z",false);
                    super.visitJumpInsn(Opcodes.IFNE,nativeBody);super.visitInsn(Opcodes.RETURN);super.visitLabel(nativeBody);super.visitFrame(Opcodes.F_SAME,0,null,0,null);
                }};
            }
        },0);
        if(identity.equals(ChargeInputGuard.SERVER)){
            if(found[0]!=0)throw new IllegalStateException("Unexpected server-side charge input method");
            return bytes; // Dedicated side has no input method; never loads the client helper.
        }
        if(found[0]!=1||fields[0]!=1)throw new IllegalStateException("Incomplete exact native charge input layout");
        return writer.toByteArray();
    }
}
