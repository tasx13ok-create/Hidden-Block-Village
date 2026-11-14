package village.vale.bridge;
import java.lang.reflect.*;
import net.minecraft.entity.*;import net.minecraft.entity.player.EntityPlayerMP;import net.minecraft.entity.projectile.*;

/** Fixed public ownership contracts only. No guessed NBT owners or configurable method invocation. */
final class BridgeOwnerProof {
    private static boolean attempted;private static Class<?> projectile,flow;private static Field shooter;private static Method user;
    private static synchronized void bind(){
        if(attempted)return;attempted=true;
        try{projectile=Class.forName("net.narutomod.entity.EntityScalableProjectile$Base",false,BridgeOwnerProof.class.getClassLoader());shooter=projectile.getField("shootingEntity");if(!Modifier.isPublic(shooter.getModifiers())||Modifier.isStatic(shooter.getModifiers())||shooter.getType()!=EntityLivingBase.class){projectile=null;shooter=null;}}
        catch(ReflectiveOperationException|RuntimeException|LinkageError absent){projectile=null;shooter=null;}
        try{flow=Class.forName("net.narutomod.entity.EntityChakraFlow$Base",false,BridgeOwnerProof.class.getClassLoader());user=flow.getMethod("getUser");if(!Modifier.isPublic(user.getModifiers())||Modifier.isStatic(user.getModifiers())||user.getReturnType()!=EntityLivingBase.class){flow=null;user=null;}}
        catch(ReflectiveOperationException|RuntimeException|LinkageError absent){flow=null;user=null;}
    }
    static EntityPlayerMP owner(Entity entity,String policy){
        Object result=null;
        try{
            if(policy.equals("thrower")&&entity instanceof EntityThrowable)result=((EntityThrowable)entity).getThrower();
            else if(policy.equals("arrow")&&entity instanceof EntityArrow)result=((EntityArrow)entity).shootingEntity;
            else if(policy.equals("ownable")&&entity instanceof IEntityOwnable)result=((IEntityOwnable)entity).getOwner();
            else if(policy.equals("naruto_projectile")){bind();if(projectile!=null&&projectile.isInstance(entity))result=shooter.get(entity);}
            else if(policy.equals("naruto_flow")){bind();if(flow!=null&&flow.isInstance(entity))result=user.invoke(entity);}
        }catch(ReflectiveOperationException|RuntimeException|LinkageError invalid){return null;}
        return result instanceof EntityPlayerMP?(EntityPlayerMP)result:null;
    }
}
