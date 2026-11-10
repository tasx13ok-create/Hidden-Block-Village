package village.vale.damagecompat;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Original adapter. It contains no game/server implementation, and leaves ordinary Forge byte-identical. */
public final class DamageCompatTransformer implements IClassTransformer {
    private static final String ENTITY="net/minecraft/entity/EntityLivingBase",SOURCE="net/minecraft/util/DamageSource",HOOK="net/minecraftforge/common/ForgeHooks";
    private static AbstractInsnNode next(AbstractInsnNode node){do{node=node.getNext();}while(node!=null&&node.getOpcode()<0);return node;}
    private static boolean call(AbstractInsnNode node,String owner,String name,String desc){if(!(node instanceof MethodInsnNode))return false;MethodInsnNode m=(MethodInsnNode)node;return m.owner.equals(owner)&&m.name.equals(name)&&m.desc.equals(desc);}
    @Override public byte[] transform(String name,String transformedName,byte[] original){
        if(original==null||!"net.minecraft.entity.EntityLivingBase".equals(transformedName))return original;
        ClassReader reader=new ClassReader(original);ClassNode type=new ClassNode(Opcodes.ASM5);reader.accept(type,0);MethodNode method=null;
        for(MethodNode candidate:type.methods)if(candidate.name.equals("damageEntity_CB")&&candidate.desc.equals("(L"+SOURCE+";F)Z")){if(method!=null)throw new IllegalStateException("Ambiguous hybrid damage method");method=candidate;}
        if(method==null){System.out.println("VALE_DAMAGE_COMPAT_NOT_NEEDED: standard Forge damage path preserved.");return original;}
        int hurt=0,finals=0,hooks=0,anchors=0;AbstractInsnNode anchor=null;
        for(AbstractInsnNode node=method.instructions.getFirst();node!=null;node=node.getNext()){
            if(call(node,HOOK,"onLivingDamage","(L"+ENTITY+";L"+SOURCE+";F)F"))hooks++;
            if(call(node,HOOK,"onLivingHurt","(L"+ENTITY+";L"+SOURCE+";F)F"))hurt++;
            if(call(node,"org/bukkit/event/entity/EntityDamageEvent","getFinalDamage","()D")){
                AbstractInsnNode conversion=next(node),store=conversion==null?null:next(conversion);
                if(conversion!=null&&conversion.getOpcode()==Opcodes.D2F&&store instanceof VarInsnNode&&store.getOpcode()==Opcodes.FSTORE&&((VarInsnNode)store).var==2)finals++;
            }
            if(node instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)node;
                if(m.owner.equals(ENTITY)&&m.desc.equals("(F)V")&&(m.name.equals("func_110149_m")||m.name.equals("setAbsorptionAmount"))){
                    AbstractInsnNode load=next(node),zero=load==null?null:next(load),compare=zero==null?null:next(zero);
                    if(load instanceof VarInsnNode&&load.getOpcode()==Opcodes.FLOAD&&((VarInsnNode)load).var==2&&zero!=null&&zero.getOpcode()==Opcodes.FCONST_0&&compare!=null&&compare.getOpcode()==Opcodes.FCMPL){anchors++;anchor=load;}
                }
            }
        }
        if(hooks>0){System.out.println("VALE_DAMAGE_COMPAT_NOT_NEEDED: hybrid already has its final Forge damage hook.");return original;}
        if(hurt!=1||finals!=1||anchors!=1)throw new IllegalStateException("Unsupported hybrid final-damage layout; no changes applied (hurt="+hurt+", final="+finals+", anchor="+anchors+").");
        // Bukkit cancellation and modifiers, armor durability, shield handling and absorption have already run.
        // No new branches or stack-map types are introduced; the returned final amount replaces only local2.
        InsnList bridge=new InsnList();bridge.add(new VarInsnNode(Opcodes.ALOAD,0));bridge.add(new VarInsnNode(Opcodes.ALOAD,1));bridge.add(new VarInsnNode(Opcodes.FLOAD,2));bridge.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"onLivingDamage","(L"+ENTITY+";L"+SOURCE+";F)F",false));bridge.add(new VarInsnNode(Opcodes.FSTORE,2));method.instructions.insertBefore(anchor,bridge);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);type.accept(writer);System.out.println("VALE_DAMAGE_COMPAT_APPLIED: one missing final Forge hook restored after native modifiers/absorption.");return writer.toByteArray();
    }
}
