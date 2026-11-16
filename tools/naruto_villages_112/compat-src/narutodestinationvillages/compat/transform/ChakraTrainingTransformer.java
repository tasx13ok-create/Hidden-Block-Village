package narutodestinationvillages.compat.transform;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

/** Original additive hook at the provider's own maximum calculation and synchronization boundary. */
public final class ChakraTrainingTransformer implements IClassTransformer {
 private static final String TARGET="net/narutomod/Chakra$PathwayPlayer";
 private static final String BRIDGE="narutodestinationvillages/compat/NativeTraining";
 public byte[] transform(String name,String transformedName,byte[] bytes){
  if(bytes==null||!TARGET.replace('/','.').equals(transformedName))return bytes;
  try{
   ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
   if(!TARGET.equals(node.name))throw new IllegalStateException("Unexpected chakra class identity");
   List<MethodInsnNode> sites=new ArrayList<>();List<MethodNode> methods=new ArrayList<>();int prior=0,canonicalPrior=0;
   for(Object entry:node.methods){MethodNode method=(MethodNode)entry;
    if(!"()V".equals(method.desc)||!("onUpdate".equals(method.name)||"resetMax".equals(method.name)))continue;
    for(AbstractInsnNode instruction=method.instructions.getFirst();instruction!=null;instruction=instruction.getNext())if(instruction instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)instruction;
     if(BRIDGE.equals(call.owner)&&"chakraMaximum".equals(call.name)){prior++;AbstractInsnNode next=call.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();if(next!=null&&(("onUpdate".equals(method.name)&&next.getOpcode()==Opcodes.DSTORE)||("resetMax".equals(method.name)&&next instanceof MethodInsnNode&&"setMax".equals(((MethodInsnNode)next).name))))canonicalPrior++;}
     if(call.getOpcode()==Opcodes.INVOKEVIRTUAL&&call.owner.startsWith("net/narutomod/Chakra$Pathway")&&"setMax".equals(call.name)&&"(D)Lnet/narutomod/Chakra$Pathway;".equals(call.desc)){sites.add(call);methods.add(method);}
    }
   }
   if(prior==2&&canonicalPrior==2&&sites.size()==2){System.setProperty("narutovillages.chakraHook","2");return bytes;}
   if(prior!=0||sites.size()!=2||methods.get(0)==methods.get(1))throw new IllegalStateException("Expected one maximum setter in each provider calculation");
   for(int i=0;i<sites.size();i++){
    MethodNode method=methods.get(i);Analyzer<SourceValue> analyzer=new Analyzer<>(new SourceInterpreter());Frame<SourceValue>[] frames=analyzer.analyze(node.name,method);
    Frame<SourceValue> frame=frames[method.instructions.indexOf(sites.get(i))];SourceValue receiver=frame==null||frame.getStackSize()<2?null:frame.getStack(frame.getStackSize()-2);
    if(receiver==null||receiver.insns.size()!=1||!(receiver.insns.iterator().next() instanceof VarInsnNode)||((VarInsnNode)receiver.insns.iterator().next()).var!=0||receiver.insns.iterator().next().getOpcode()!=Opcodes.ALOAD)throw new IllegalStateException("Maximum setter receiver is not the current pathway");
    AbstractInsnNode insertion=sites.get(i);
    if("onUpdate".equals(method.name)){
     SourceValue desired=frame.getStack(frame.getStackSize()-1);if(desired.insns.size()!=1||!(desired.insns.iterator().next() instanceof VarInsnNode)||desired.insns.iterator().next().getOpcode()!=Opcodes.DLOAD)throw new IllegalStateException("Canonical desired maximum is not a local value");
     int slot=((VarInsnNode)desired.insns.iterator().next()).var;int stores=0;for(AbstractInsnNode n=method.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof VarInsnNode&&n.getOpcode()==Opcodes.DSTORE&&((VarInsnNode)n).var==slot){insertion=n;stores++;}
     if(stores!=1)throw new IllegalStateException("Canonical desired maximum has ambiguous assignments");
    }
    InsnList extra=new InsnList();extra.add(new VarInsnNode(Opcodes.ALOAD,0));
    extra.add(new FieldInsnNode(Opcodes.GETFIELD,"net/narutomod/Chakra$Pathway","user","Lnet/minecraft/entity/EntityLivingBase;"));
    extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"chakraMaximum","(DLnet/minecraft/entity/EntityLivingBase;)D",false));
    methods.get(i).instructions.insertBefore(insertion,extra);
   }
   ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);
   System.setProperty("narutovillages.chakraHook","2");System.out.println("NARUTO_TRAINING_CHAKRA_HOOK: two canonical maximum calculations adapted");return writer.toByteArray();
  }catch(RuntimeException|AnalyzerException failure){System.setProperty("narutovillages.chakraHook","unsupported");System.err.println("NARUTO_TRAINING_CHAKRA_HOOK_UNSUPPORTED: "+failure);return bytes;}
 }
}
