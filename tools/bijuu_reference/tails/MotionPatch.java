import java.nio.file.*;
import org.objectweb.asm.*;

/** Bind the existing render hook to the actual wearer and its interpolated animation time. */
public class MotionPatch {
    public static void main(String[] args) throws Exception {
        Path path=Paths.get(args[0]);
        ClassReader reader=new ClassReader(Files.readAllBytes(path));
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        int[] calls={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
            public MethodVisitor visitMethod(int a,String n,String d,String sig,String[] ex) {
                MethodVisitor out=super.visitMethod(a,n,d,sig,ex);
                if(!n.equals("func_78087_a") || !d.equals("(FFFFFFLnet/minecraft/entity/Entity;)V"))return out;
                return new MethodVisitor(Opcodes.ASM9,out) {
                    public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf) {
                        if(op==Opcodes.INVOKESTATIC && owner.equals("village/bijuu/ReferenceTails")
                            && name.equals("pose") && desc.equals("(Ljava/lang/Object;)V")) {
                            mv.visitVarInsn(Opcodes.ALOAD,7);
                            for(int i=1;i<=3;i++)mv.visitVarInsn(Opcodes.FLOAD,i);
                            desc="(Ljava/lang/Object;Lnet/minecraft/entity/Entity;FFF)V";
                            calls[0]++;
                        }
                        super.visitMethodInsn(op,owner,name,desc,itf);
                    }
                };
            }
        },0);
        if(calls[0]==0)throw new IllegalStateException("Missing reference pose hook: "+path);
        Files.write(path,writer.toByteArray());
    }
}
