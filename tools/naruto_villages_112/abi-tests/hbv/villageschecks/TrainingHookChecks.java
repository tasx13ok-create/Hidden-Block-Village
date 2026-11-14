package hbv.villageschecks;
import java.nio.file.*;import java.util.*;import java.util.zip.*;
import narutodestinationvillages.compat.transform.ChakraTrainingTransformer;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;

/** Checks real private input bytes and rejects incompatible synthetic mutations; no game is launched here. */
public final class TrainingHookChecks {
 private static final String TARGET="net.narutomod.Chakra$PathwayPlayer";
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);System.out.println("PASS "+label);}
 private static ClassNode read(byte[] input){ClassNode node=new ClassNode();new ClassReader(input).accept(node,0);return node;}
 private static byte[] write(ClassNode node){ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();}
 private static int hooks(byte[] bytes){int n=0;for(Object entry:read(bytes).methods){MethodNode method=(MethodNode)entry;for(AbstractInsnNode i=method.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals("narutodestinationvillages/compat/NativeTraining")&&((MethodInsnNode)i).name.equals("chakraMaximum"))n++;}return n;}
 public static void main(String[] args)throws Exception{
  byte[] input;try(ZipFile jar=new ZipFile(Paths.get(System.getProperty("narutovillages.nativeJar")).toFile())){java.io.InputStream stream=jar.getInputStream(jar.getEntry("net/narutomod/Chakra$PathwayPlayer.class"));java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];for(int n;(n=stream.read(buffer))!=-1;)out.write(buffer,0,n);input=out.toByteArray();stream.close();}
  ChakraTrainingTransformer transformer=new ChakraTrainingTransformer();byte[] patched=transformer.transform(TARGET,TARGET,input);
  check(!Arrays.equals(input,patched)&&hooks(patched)==2&&read(input).methods.size()==read(patched).methods.size(),"real provider gets two additive hooks without replacing methods");
  check(transformer.transform(TARGET,TARGET,patched)==patched,"repeated transformation is idempotent");
  check(transformer.transform("net.minecraft.entity.EntityLivingBase","net.minecraft.entity.EntityLivingBase",input)==input,"unrelated classes are returned unchanged");
  ClassNode missing=read(input);missing.methods.removeIf(entry->"onUpdate".equals(((MethodNode)entry).name));byte[] incompatible=write(missing);check(transformer.transform(TARGET,TARGET,incompatible)==incompatible&&"unsupported".equals(System.getProperty("narutovillages.chakraHook")),"missing provider calculation fails closed");
  ClassNode receiver=read(input);for(Object entry:receiver.methods){MethodNode method=(MethodNode)entry;if(!"resetMax".equals(method.name))continue;for(AbstractInsnNode i=method.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof VarInsnNode&&i.getOpcode()==Opcodes.ALOAD){((VarInsnNode)i).var=1;break;}}
  incompatible=write(receiver);check(transformer.transform(TARGET,TARGET,incompatible)==incompatible&&"unsupported".equals(System.getProperty("narutovillages.chakraHook")),"noncurrent pathway receiver fails closed");
 }
}
