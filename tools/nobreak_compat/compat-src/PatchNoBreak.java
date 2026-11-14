import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;

/** Original single-call adapter; contains no third-party method implementation. */
public final class PatchNoBreak {
    public static void main(String[] args)throws Exception {
        byte[] input=Files.readAllBytes(Paths.get(args[0]));ClassReader reader=new ClassReader(input);
        ClassWriter writer=new ClassWriter(reader,0);final int[] changes={0},fields={0};
        reader.accept(new ClassVisitor(Opcodes.ASM5,writer){
            @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions) {
                MethodVisitor delegate=super.visitMethod(access,name,descriptor,signature,exceptions);
                final boolean interact=name.equals("onInteract")&&descriptor.equals("(Lorg/bukkit/event/player/PlayerInteractEvent;)V");
                final boolean farmland=name.equals("entityInteract")&&descriptor.equals("(Lorg/bukkit/event/entity/EntityInteractEvent;)V");
                if(!interact&&!farmland)return delegate;
                return new MethodVisitor(Opcodes.ASM5,delegate){
                    @Override public void visitMethodInsn(int opcode,String owner,String name,String desc,boolean itf) {
                        if(interact&&opcode==Opcodes.INVOKEVIRTUAL&&owner.equals("org/bukkit/Material")&&name.equals("isInteractable")&&desc.equals("()Z")) {
                            changes[0]++;super.visitMethodInsn(Opcodes.INVOKESTATIC,"village/vale/nobreakcompat/InteractionCompat","isInteractable","(Lorg/bukkit/Material;)Z",false);
                        } else super.visitMethodInsn(opcode,owner,name,desc,itf);
                    }
                    @Override public void visitFieldInsn(int opcode,String owner,String name,String desc) {
                        if(farmland&&opcode==Opcodes.GETSTATIC&&owner.equals("org/bukkit/Material")&&name.equals("FARMLAND")&&desc.equals("Lorg/bukkit/Material;")) {
                            fields[0]++;super.visitFieldInsn(opcode,owner,"SOIL",desc);
                        } else super.visitFieldInsn(opcode,owner,name,desc);
                    }
                };
            }
        },0);
        if(changes[0]!=1)throw new IllegalArgumentException("Expected exactly one pinned Material.isInteractable call, got "+changes[0]);
        if(fields[0]!=1)throw new IllegalArgumentException("Expected exactly one pinned FARMLAND field, got "+fields[0]);
        Files.write(Paths.get(args[1]),writer.toByteArray(),StandardOpenOption.CREATE_NEW);
        System.out.println("patched_calls="+changes[0]+" field_aliases="+fields[0]);
    }
}
