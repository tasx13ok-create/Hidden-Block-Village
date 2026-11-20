import java.nio.file.*;
import org.objectweb.asm.*;
/** Add two calls to the existing model without changing its normal pose logic. */
public class TailPatch {
    public static void main(String[] args) throws Exception {
        Path file=Paths.get(args[0]);
        ClassReader reader=new ClassReader(Files.readAllBytes(file));
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
            public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex) {
                MethodVisitor method=super.visitMethod(access,name,desc,sig,ex);
                if(name.equals("renderedTailRoots") && desc.equals("()I")) {
                    method.visitCode();
                    method.visitVarInsn(Opcodes.ALOAD,0);
                    method.visitMethodInsn(Opcodes.INVOKESTATIC,"village/bijuu/ReferenceTails","visibleRoots","(Ljava/lang/Object;)I",false);
                    method.visitInsn(Opcodes.IRETURN);
                    method.visitMaxs(1,1);
                    method.visitEnd();
                    return null;
                }
                boolean init=name.equals("<init>");
                if (!init && !name.equals("func_78087_a")) return method;
                return new MethodVisitor(Opcodes.ASM9,method) {
                    public void visitInsn(int op) {
                        if(op==Opcodes.RETURN) {
                            visitVarInsn(Opcodes.ALOAD,0);
                            if(init) {
                                int index=Integer.parseInt(args[1]);
                                if(index<0) visitVarInsn(Opcodes.ILOAD,1); else visitLdcInsn(index);
                                visitVarInsn(Opcodes.ALOAD,index<0?2:1);
                                visitMethodInsn(Opcodes.INVOKESTATIC,"village/bijuu/ReferenceTails","install","(Ljava/lang/Object;ILjava/lang/Object;)V",false);
                            } else visitMethodInsn(Opcodes.INVOKESTATIC,"village/bijuu/ReferenceTails","pose","(Ljava/lang/Object;)V",false);
                        }
                        super.visitInsn(op);
                    }
                };
            }
        },0);
        Files.write(file,writer.toByteArray());
    }
}
