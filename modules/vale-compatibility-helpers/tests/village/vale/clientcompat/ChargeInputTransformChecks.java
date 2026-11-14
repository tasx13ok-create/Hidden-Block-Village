package village.vale.clientcompat;
import java.io.*;import java.lang.reflect.*;import java.net.*;import java.util.*;import java.util.zip.*;
import net.minecraft.launchwrapper.*;import net.minecraftforge.fml.relauncher.*;
import org.objectweb.asm.*;import org.objectweb.asm.tree.*;import org.objectweb.asm.util.CheckClassAdapter;

/** Exact-source prefix/layout checks only; native bodies are not emitted or packaged. */
public final class ChargeInputTransformChecks {
 private static int checks;private static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 private static ClassNode parse(byte[] bytes){ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);return c;}
 private static List<String> code(MethodNode m){List<String> out=new ArrayList<>();for(AbstractInsnNode i:m.instructions.toArray()){
  if(i.getOpcode()<0)continue;String token=""+i.getOpcode();
  if(i instanceof MethodInsnNode){MethodInsnNode v=(MethodInsnNode)i;token+="/"+v.owner+"/"+v.name+v.desc;}
  else if(i instanceof FieldInsnNode){FieldInsnNode v=(FieldInsnNode)i;token+="/"+v.owner+"/"+v.name+v.desc;}
  else if(i instanceof VarInsnNode)token+="/"+((VarInsnNode)i).var;
  else if(i instanceof TypeInsnNode)token+="/"+((TypeInsnNode)i).desc;
  else if(i instanceof IntInsnNode)token+="/"+((IntInsnNode)i).operand;
  else if(i instanceof LdcInsnNode)token+="/"+((LdcInsnNode)i).cst;
  out.add(token);
 }return out;}
 private static void compare(byte[] original,byte[] changed){
  ClassNode a=parse(original),b=parse(changed);check(a.fields.size()==b.fields.size()&&a.methods.size()==b.methods.size(),"native fields/methods preserved");int routed=0;
  for(Object row:a.methods){MethodNode before=(MethodNode)row,after=null;for(Object other:b.methods){MethodNode m=(MethodNode)other;if(m.name.equals(before.name)&&m.desc.equals(before.desc)){after=m;break;}}
   check(after!=null&&after.access==before.access,"native method identity/access retained");List<String> old=code(before),now=code(after);
   if(before.name.equals("onKeyInput")){routed++;check(now.size()==old.size()+5,"one five-instruction prefix only");check(now.subList(5,now.size()).equals(old),"original native first-press body/instructions retained");check(now.get(2).contains("village/vale/clientcompat/ChargeInputClient/allow(Ljava/lang/Object;)Z"),"only original observation gate added");}
   else check(old.equals(now),"unrelated native method unchanged");
  }
  check(routed==1,"exactly one native input method patched");new ClassReader(changed).accept(new CheckClassAdapter(new ClassWriter(0),false),0);checks++;
 }
 public static void main(String[]args)throws Exception{
  Side side=Side.valueOf(args[1]);Field f=FMLLaunchHandler.class.getDeclaredField("side");f.setAccessible(true);f.set(null,side);
  File source=new File(args[0]);Launch.classLoader=new LaunchClassLoader(new URL[]{source.toURI().toURL()});ChargeInputTransformer transformer=new ChargeInputTransformer();
  check(transformer.transform("anything","anything",null)==null,"null class remains absent");byte[] other={1,2};check(transformer.transform("anything","anything",other)==other,"missing addon/unrelated class passes untouched");
  byte[] raw;try(ZipFile jar=new ZipFile(source);InputStream in=jar.getInputStream(jar.getEntry(ChargeInputGuard.TARGET.replace('.','/')+".class"));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] data=new byte[65536];int n;while((n=in.read(data))!=-1)out.write(data,0,n);raw=out.toByteArray();}
  compare(raw,transformer.transform(ChargeInputGuard.TARGET,ChargeInputGuard.TARGET,raw));
  byte[] sided=new net.minecraftforge.fml.common.asm.transformers.SideTransformer().transform(ChargeInputGuard.TARGET,ChargeInputGuard.TARGET,raw);
  String actual=ChargeInputGuard.hash(new ByteArrayInputStream(sided));check(actual.equals(side==Side.CLIENT?ChargeInputGuard.CLIENT:ChargeInputGuard.SERVER),"official exact side input matched reviewed pin");
  byte[] result=transformer.transform(ChargeInputGuard.TARGET,ChargeInputGuard.TARGET,sided);
  if(side==Side.CLIENT)compare(sided,result);else check(result==sided,"dedicated server returns original bytes without a client helper call");
  byte[] changed=raw.clone();changed[changed.length-1]^=1;boolean failed=false;try{transformer.transform(ChargeInputGuard.TARGET,ChargeInputGuard.TARGET,changed);}catch(IllegalStateException expected){failed=true;}check(failed,"unknown upstream bytes refused before patching");
  System.out.println("CHARGE_INPUT_TRANSFORM_CHECKS_PASS side="+side+" assertions="+checks+" actual_client_endpoint=false");
 }
}
