package village.vale.arsenal;

import java.util.*;
import village.vale.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;

/** Original mechanics. Temporary geometry lives in owned actors; world blocks are never replaced. */
final class ScrollEffects {
 static boolean buff(EntityPlayerMP p,Potion potion,int ticks,int strength){
  if(p.isPotionActive(potion))return false;
  PotionEffect effect=new PotionEffect(potion,ticks,strength);p.addPotionEffect(effect);
  // Native PotionApplicableEvent can deny or replace the attempted addition.
  // Charge only for this actual application, not an unrelated existing effect.
  return p.getActivePotionEffect(potion)==effect&&effect.getDuration()>0;
 }
 static boolean heal(EntityPlayerMP p){
  EntityLivingBase t=friendlyAim(p,3);if(t==null)t=p;
  if(!t.isEntityAlive()||(t instanceof EntityPlayer&&((EntityPlayer)t).isSpectator())||(t!=p&&!p.isOnSameTeam(t)))return false;
  float before=t.getHealth();if(!Float.isFinite(before)||before>=t.getMaxHealth())return false;
  t.heal(6);float after=t.getHealth();return Float.isFinite(after)&&after>before;
 }
 static boolean hit(EntityPlayerMP p,EntityLivingBase t,float amount,Potion status,int ticks,int amp){if(t==null||!ArsenalAccess.hit(p,t,amount))return false;if(status!=null)t.addPotionEffect(new PotionEffect(status,ticks,amp));return true;}
 static Vec3d ground(EntityPlayerMP p,int range){Vec3d eye=p.getPositionEyes(1);RayTraceResult r=p.world.rayTraceBlocks(eye,eye.add(p.getLookVec().scale(range)),false,true,false);return r!=null&&r.typeOfHit==RayTraceResult.Type.BLOCK&&r.sideHit==EnumFacing.UP?r.hitVec.add(0,.1,0):null;}
 static boolean spawn(EntityPlayerMP p,String kind,Vec3d at,EntityLivingBase target,int life,float power){return ScrollActor.spawn(p,kind,at,target,life,power)!=null;}
 static boolean mode(EntityPlayerMP p,String kind,int ticks){NBTTagCompound s=Scrolls.state(p);String current=s.getString("mode");long now=ArsenalAccess.now(p);if(!current.isEmpty()&&s.getLong("modeUntil")>now){s.setString("mode","");s.setLong("modeUntil",0);ArsenalAccess.save(p);return true;}s.setString("mode",kind);s.setLong("modeUntil",now+ticks);return true;}
 static boolean cast(EntityPlayerMP p,Scrolls.Lesson l){
  EntityLivingBase t;Vec3d at;NBTTagCompound s=Scrolls.state(p);String k=l.kind;
  switch(k){
   case "replacement":s.setBoolean("replacement",!s.getBoolean("replacement"));s.setLong("replacementUntil",ArsenalAccess.now(p)+240);return true;
   case "regen":return buff(p,MobEffects.REGENERATION,200,0);
   case "strength":return buff(p,MobEffects.STRENGTH,160,1);
   case "heal":return heal(p);
   case "camo":return buff(p,MobEffects.INVISIBILITY,200,0);
   case "rock":if(!p.onGround)return false;return spawn(p,"refuge",p.getPositionVector(),null,160,0);
   case "teleport":return teleport(p,s);
   case "charge":case "fireedge":case "windedge":case "spiral":case "lava":case "lightmode":return mode(p,k,200);
   case "size":if(p.isPotionActive(MobEffects.HEALTH_BOOST)||!spawn(p,"titan",p.getPositionVector(),null,200,2))return false;buff(p,MobEffects.HEALTH_BOOST,200,1);p.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,200,0));return true;
   case "disguise":t=ArsenalAccess.aimedTarget(p,20);if(t==null||!spawn(p,"disguise",p.getPositionVector(),t,240,0))return false;s.setString("disguiseName",t.getName());return true;
   case "puppetcontrol":for(ScrollActor a:p.world.getEntitiesWithinAABB(ScrollActor.class,p.getEntityBoundingBox().grow(5)))if(a.owned(p)&&a.kind.startsWith("puppet")){t=ArsenalAccess.aimedTarget(p,20);return a.command(p,t);}return false;
   case "golem":case "clone":if(ScrollActor.exists(p,k))return false;return spawn(p,k,p.getPositionVector().add(p.getLookVec().scale(2)),null,k.equals("clone")?400:600,k.equals("clone")?2:5);
   case "wall":at=ground(p,16);return at!=null&&spawn(p,"wall",at,null,200,0);
   case "ritual":
    at=ground(p,10);if(at==null)return false;BlockPos b=new BlockPos(at);for(EnumFacing f:EnumFacing.HORIZONTALS)if(!p.world.getBlockState(b.offset(f,2).down()).isFullBlock())return false;
    return spawn(p,"ritual",at,null,240,0);
   case "clasp":case "thorns":case "swamp":at=ground(p,24);if(at==null)return false;return spawn(p,k,at,null,k.equals("clasp")?80:160,k.equals("thorns")?5:2);
   case "ash":case "mist":case "poison":case "ring":case "shockwave":
    if(ScrollActor.dismiss(p,k))return true;return spawn(p,k,p.getPositionVector(),null,160,k.equals("poison")?2:k.equals("ring")?4:1);
   case "illusion":t=ArsenalAccess.aimedTarget(p,16);return hit(p,t,1,MobEffects.BLINDNESS,80,0)&&slow(t,80,2);
   case "mind":t=ArsenalAccess.aimedTarget(p,20);if(t==null||!spawn(p,k,t.getPositionVector(),t,100,0))return false;p.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,100,3));return true;
   case "chains":if(ScrollActor.dismiss(p,k))return true;t=ArsenalAccess.aimedTarget(p,24);return t!=null&&spawn(p,k,t.getPositionVector(),t,120,1);
   case "shadow":t=ArsenalAccess.aimedTarget(p,20);return t!=null&&spawn(p,k,t.getPositionVector(),t,100,1);
   case "prison":t=ArsenalAccess.aimedTarget(p,16);if(t==null||ScrollActor.exists(p,k))return false;return spawn(p,k,t.getPositionVector(),t,100,2);
   case "swarm":t=ArsenalAccess.aimedTarget(p,24);return t!=null&&!ScrollActor.exists(p,k)&&spawn(p,k,t.getPositionVector(),t,140,2);
   case "thunder":t=ArsenalAccess.aimedTarget(p,24);if(t==null)return false;if(!spawn(p,k,t.getPositionVector().add(0,6,0),t,60,12))return false;p.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,300,0));return true;
   case "hound":case "panther":t=ArsenalAccess.aimedTarget(p,24);return t!=null&&spawn(p,k,p.getPositionVector(),t,100,k.equals("hound")?5:9);
   case "gust":{int n=0;for(EntityLivingBase e:ArsenalAccess.targets(p,6,true,6))if(hit(p,e,2,null,0,0)){push(p,e,.8,.25);n++;}return n>0;}
   case "fork":t=ArsenalAccess.aimedTarget(p,20);if(!hit(p,t,7,MobEffects.SLOWNESS,30,1))return false;for(EntityLivingBase e:ArsenalAccess.targets(p,8,true,3))if(e!=t)hit(p,e,3,null,0,0);return true;
   case "wave":{int n=0;for(EntityLivingBase e:ArsenalAccess.targets(p,10,true,8))if(hit(p,e,5,null,0,0)){push(p,e,.35,.1);n++;}return n>0;}
   case "dragon":if(!p.onGround)return false;t=ArsenalAccess.aimedTarget(p,24);return t!=null&&spawn(p,k,p.getPositionVector().add(0,1,0),t,100,10);
   case "front":return projectile(p,k,18,10,60);
   case "firestream":return projectile(p,k,16,3,80);
   case "vacuum":return projectile(p,k,24,6,60);
   case "fireball":return projectile(p,k,20,8,60);
   case "cannon":return projectile(p,k,20,7,60);
   case "shark":return projectile(p,k,24,8,100);
   case "waterstream":return projectile(p,k,20,2,100);
   case "blade":return projectile(p,k,24,9,60);
   default:return false;
  }
 }
 static EntityLivingBase friendlyAim(EntityPlayerMP p,int range){Vec3d eye=p.getPositionEyes(1),end=eye.add(p.getLookVec().scale(range));EntityLivingBase best=null;double near=range*range;for(EntityLivingBase e:p.world.getEntitiesWithinAABB(EntityLivingBase.class,p.getEntityBoundingBox().grow(range)))if(e!=p&&p.isOnSameTeam(e)&&e.getEntityBoundingBox().grow(.2).calculateIntercept(eye,end)!=null&&p.getDistanceSq(e)<near&&p.canEntityBeSeen(e)){near=p.getDistanceSq(e);best=e;}return best;}
 static boolean projectile(EntityPlayerMP p,String k,int range,float power,int life){EntityLivingBase t=ArsenalAccess.aimedTarget(p,range);return t!=null&&spawn(p,k,p.getPositionEyes(1).add(p.getLookVec()),t,life,power);}
 static boolean slow(EntityLivingBase t,int ticks,int amp){t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,ticks,amp));return true;}
 static void push(EntityPlayerMP p,EntityLivingBase t,double force,double lift){Vec3d d=t.getPositionVector().subtract(p.getPositionVector()).normalize();t.addVelocity(d.x*force,lift,d.z*force);t.velocityChanged=true;}
 static boolean teleport(EntityPlayerMP p,NBTTagCompound state){
  Vec3d origin=p.getPositionVector();int dimension=p.dimension;
  if(!state.hasKey("markDim")){
   if(!p.onGround||!safe(p,origin))return false;
   state.setInteger("markDim",dimension);state.setDouble("markX",origin.x);state.setDouble("markY",origin.y);state.setDouble("markZ",origin.z);return true;
  }
  if(!state.hasKey("markDim",3)||state.getInteger("markDim")!=dimension
     ||!state.hasKey("markX",6)||!state.hasKey("markY",6)||!state.hasKey("markZ",6))return false;
  Vec3d mark=new Vec3d(state.getDouble("markX"),state.getDouble("markY"),state.getDouble("markZ"));
  if(!safe(p,mark))return false;
  EnderTeleportEvent event=new EnderTeleportEvent(p,mark.x,mark.y,mark.z,0);
  if(MinecraftForge.EVENT_BUS.post(event))return false;
  Vec3d destination=new Vec3d(event.getTargetX(),event.getTargetY(),event.getTargetZ());
  if(!Scrolls.castStillValid(p)||p.getPositionVector().squareDistanceTo(origin)!=0
     ||!sameMark(state,dimension,mark)||!safe(p,destination))return false;
  // The final collision query itself invokes Forge subscribers. Check again
  // after it, before moving, so their authority/state/position changes survive.
  if(!Scrolls.castStillValid(p)||p.getPositionVector().squareDistanceTo(origin)!=0||!sameMark(state,dimension,mark))return false;
  net.minecraft.world.World world=p.world;boolean returned=false,applied=false;
  try{
   p.connection.setPlayerLocation(destination.x,destination.y,destination.z,p.rotationYaw,p.rotationPitch);returned=true;
  }finally{
   boolean reached=p.world==world&&p.dimension==dimension&&p.getPositionVector().squareDistanceTo(destination)<=1.0e-8;
   boolean moved=p.world!=world||p.dimension!=dimension||p.posX!=origin.x||p.posY!=origin.y||p.posZ!=origin.z;
   // Preserve observed movement even when the connection throws afterwards.
   // A throwing no-move callback remains an unapplied, refundable attempt.
   if(moved||(returned&&reached)){
    applied=true;Scrolls.recordApplied(p);
    if(reached)p.fallDistance=0;
    // Consume only the marker actually authorized; a callback's new marker or
    // replacement player state is independent and is never restored/overwritten.
    if(sameMark(state,dimension,mark)){state.removeTag("markDim");state.removeTag("markX");state.removeTag("markY");state.removeTag("markZ");}
   }
  }
  return applied;
 }
 private static boolean sameMark(NBTTagCompound state,int dimension,Vec3d mark){
  return state.hasKey("markDim",3)&&state.getInteger("markDim")==dimension
    &&state.hasKey("markX",6)&&state.hasKey("markY",6)&&state.hasKey("markZ",6)
    &&state.getDouble("markX")==mark.x&&state.getDouble("markY")==mark.y&&state.getDouble("markZ")==mark.z;
 }
 static boolean safe(EntityPlayerMP p,Vec3d at){
  if(at==null||!Double.isFinite(at.x)||!Double.isFinite(at.y)||!Double.isFinite(at.z)
     ||at.y<1||at.y>p.world.getActualHeight()-2||at.squareDistanceTo(p.getPositionVector())>4096)return false;
  AxisAlignedBB box=p.getEntityBoundingBox().offset(at.subtract(p.getPositionVector()));BlockPos floor=new BlockPos(at).down();
  if(!p.world.isAreaLoaded(new BlockPos(box.minX,box.minY-1,box.minZ),new BlockPos(box.maxX,box.maxY,box.maxZ))
     ||box.minX<p.world.getWorldBorder().minX()||box.maxX>p.world.getWorldBorder().maxX()
     ||box.minZ<p.world.getWorldBorder().minZ()||box.maxZ>p.world.getWorldBorder().maxZ())return false;
  return p.world.getCollisionBoxes(p,box).isEmpty()&&!p.world.containsAnyLiquid(box)
    &&p.world.getBlockState(floor).isSideSolid(p.world,floor,EnumFacing.UP);
 }
}
