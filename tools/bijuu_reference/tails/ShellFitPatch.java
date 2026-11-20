import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.objectweb.asm.*;

/** Adjust only the main shells created in the hash-pinned armor model. */
public final class ShellFitPatch {
    public static void main(String[] args) throws Exception {
        Path path=Paths.get(args[0]);
        ClassReader reader=new ClassReader(Files.readAllBytes(path));
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        final int[] edits={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
            public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] ex) {
                MethodVisitor mv=super.visitMethod(access,name,desc,sig,ex);
                if(!name.equals("box") || !desc.contains("FFFIIIII)"))return mv;
                return new MethodVisitor(Opcodes.ASM9,mv){
                    public void visitLdcInsn(Object constant){
                        if(Float.valueOf(.16f).equals(constant)){
                            edits[0]++;
                            visitVarInsn(Opcodes.ILOAD,5);
                            visitVarInsn(Opcodes.ILOAD,6);
                            visitMethodInsn(Opcodes.INVOKESTATIC,"village/bijuu/ShellClearance","amount","(II)F",false);
                        } else super.visitLdcInsn(constant);
                    }
                };
            }
        },0);
        if(edits[0]!=1)throw new IllegalArgumentException("Expected exactly one base-shell inflation");
        Files.write(path,writer.toByteArray());
    }
}
