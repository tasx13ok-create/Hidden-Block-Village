package village.vale.arsenal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import village.vale.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.*;

public final class ScrollEvents {
 private static final ThreadLocal<Boolean> EXTRA=new ThreadLocal<>();
 private static final Map<UUID,EscapeAttempt> ESCAPES=new ConcurrentHashMap<>();
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent e){
  if(e.isCanceled()||e.getAmount()<=0||Boolean.TRUE.equals(EXTRA.get())||e.getEntityLiving().world.isRemote)return;
  Entity attacker=e.getSource().getTrueSource();if(!(attacker instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)attacker;NBTTagCompound s=Scrolls.state(p);String mode=s.getString("mode");if(s.getLong("modeUntil")<ArsenalAccess.now(p)||!ArsenalAccess.allowed(p,e.getEntityLiving()))return;
  float extra=mode.equals("spiral")?8:mode.equals("charge")?6:mode.equals("fireedge")?3:mode.equals("windedge")?4:mode.equals("lightmode")?2:mode.equals("lava")?3:0;if(extra==0)return;
  EXTRA.set(true);try{if(ArsenalAccess.hit(p,e.getEntityLiving(),extra)){
   if(mode.equals("fireedge")||mode.equals("lava"))e.getEntityLiving().setFire(3);
   if(mode.equals("windedge"))ScrollEffects.push(p,e.getEntityLiving(),.65,.15);
   if(mode.equals("charge")||mode.equals("lightmode"))e.getEntityLiving().addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,40,2));
   if(mode.equals("spiral")){ScrollEffects.push(p,e.getEntityLiving(),1,.4);HandTechnique.impact(p,e.getEntityLiving());s.removeTag("mode");}
   if(mode.equals("charge")){HandTechnique.impact(p,e.getEntityLiving());s.removeTag("mode");}ArsenalAccess.save(p);
  }}finally{EXTRA.remove();}
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void hurt(LivingHurtEvent e){
  if(e.isCanceled()||!Float.isFinite(e.getAmount())||e.getAmount()<=0||!(e.getEntityLiving() instanceof EntityPlayerMP))return;
  EntityPlayerMP p=(EntityPlayerMP)e.getEntityLiving();if(p.world.isRemote||!p.isEntityAlive()||p.isSpectator()||p instanceof net.minecraftforge.common.util.FakePlayer)return;
  net.minecraft.world.World world=p.world;int dimension=p.dimension;NBTTagCompound s=currentScrolls(p);
  if(s.getBoolean("replacement")&&s.getLong("replacementUntil")>=ArsenalAccess.now(p)&&escape(p,e,s))return;
  if(e.isCanceled()||!Float.isFinite(e.getAmount())||e.getAmount()<=0||p.world!=world||p.dimension!=dimension||!p.isEntityAlive()||p.isSpectator())return;
  // A rejected escape may still have changed the canonical state in a callback.
  // Never apply a defensive mode from the displaced compound.
  s=currentScrolls(p);String mode=s.getString("mode");if(s.getLong("modeUntil")>=ArsenalAccess.now(p)){if(mode.equals("lava"))e.setAmount(e.getAmount()*.7f);if(mode.equals("lightmode")){p.addPotionEffect(new PotionEffect(MobEffects.SPEED,30,1));e.setAmount(e.getAmount()*.85f);}}
 }
 private static NBTTagCompound currentAccount(EntityPlayerMP p){
  return p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag("ValeOrders");
 }
 private static NBTTagCompound currentScrolls(EntityPlayerMP p){return currentAccount(p).getCompoundTag("OriginalScrolls");}
 private static boolean escape(EntityPlayerMP p,LivingHurtEvent hurt,NBTTagCompound state){
  // Entity UUID is mutable during native callbacks; keep the live player
  // instance locked as well as the owner whose activation was captured.
  for(EscapeAttempt active:ESCAPES.values())if(active.player==p)return false;
  EscapeAttempt attempt=new EscapeAttempt(p,hurt,state);
  if(ESCAPES.putIfAbsent(attempt.owner,attempt)!=null)return false;
  ScrollActor clone=null;boolean escaped=false;
  try{
   if(!attempt.valid())return false;
   Vec3d destination=null;
   for(int i=2;i<=5;i++){
    Vec3d candidate=attempt.origin.subtract(p.getLookVec().scale(i));boolean safe=attempt.safe(candidate);
    if(!attempt.valid())return false;
    if(safe){destination=candidate;break;}
   }
   if(destination==null)return false;
   EnderTeleportEvent teleport=new EnderTeleportEvent(p,destination.x,destination.y,destination.z,0);
   if(net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(teleport)||!attempt.valid())return false;
   destination=new Vec3d(teleport.getTargetX(),teleport.getTargetY(),teleport.getTargetZ());
   if(!attempt.safe(destination)||!attempt.valid())return false;
   clone=ScrollActor.spawn(p,"clone",attempt.origin,null,60,0);
   if(clone==null||!attempt.valid()||!clone.isEntityAlive()||clone.world!=attempt.world||!clone.owned(p)
      ||!clone.kind.equals("clone")||!clone.sourceSerial.isEmpty())return false;
   // Spawn subscribers can change geometry as well as the player/activation.
   if(!attempt.safe(destination)||!attempt.valid())return false;
   try{p.connection.setPlayerLocation(destination.x,destination.y,destination.z,p.rotationYaw,p.rotationPitch);}
   finally{
    if(attempt.moved()){
     escaped=true;
     // Movement already happened. Consume only the captured activation, never
     // a new one installed by a connection callback, and preserve its position.
     attempt.consume();hurt.setCanceled(true);
     if(p.world==attempt.world&&p.dimension==attempt.dimension&&p.getPositionVector().squareDistanceTo(destination)<=1.0e-8)p.fallDistance=0;
     ArsenalAccess.save(p);
    }
   }
   return escaped;
  }finally{
   try{if(!escaped&&clone!=null){clone.setDead();clone.world.removeEntity(clone);}}
   finally{ESCAPES.remove(attempt.owner,attempt);}
  }
 }
 private static final class EscapeAttempt{
  final EntityPlayerMP player;final UUID owner;final LivingHurtEvent hurt;final NBTTagCompound account,state;
  final net.minecraft.world.World world;final int dimension;final Vec3d origin;final long until;
  EscapeAttempt(EntityPlayerMP player,LivingHurtEvent hurt,NBTTagCompound state){
   this.player=player;owner=player.getUniqueID();this.hurt=hurt;this.state=state;account=currentAccount(player);world=player.world;
   dimension=player.dimension;origin=player.getPositionVector();until=state.getLong("replacementUntil");
  }
  boolean valid(){
   return hurt.getEntityLiving()==player&&owner.equals(player.getUniqueID())&&!hurt.isCanceled()&&Float.isFinite(hurt.getAmount())&&hurt.getAmount()>0
     &&player.world==world&&player.dimension==dimension&&!world.isRemote&&player.isEntityAlive()&&!player.isSpectator()
     &&currentAccount(player)==account&&account.getCompoundTag("OriginalScrolls")==state
     &&state.getBoolean("replacement")&&state.getLong("replacementUntil")==until&&until>=ArsenalAccess.now(player)
     &&player.posX==origin.x&&player.posY==origin.y&&player.posZ==origin.z;
  }
  boolean safe(Vec3d destination){
   double distance=destination.squareDistanceTo(origin);
   return Double.isFinite(distance)&&distance>1.0e-8&&distance<=25&&ScrollEffects.safe(player,destination);
  }
  boolean moved(){return player.world!=world||player.dimension!=dimension||player.posX!=origin.x||player.posY!=origin.y||player.posZ!=origin.z;}
  void consume(){if(state.getBoolean("replacement")&&state.getLong("replacementUntil")==until){state.setBoolean("replacement",false);state.removeTag("replacementUntil");}}
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e){if(!(e.getEntityLiving() instanceof EntityPlayerMP)||e.isCanceled())return;EntityPlayerMP p=(EntityPlayerMP)e.getEntityLiving();boolean slate=false,cloud=false;
  for(int i=0;i<p.inventory.getSizeInventory();i++){ItemStack s=p.inventory.getStackInSlot(i);if(!(s.getItem() instanceof ScrollSupport.Support)||!ArsenalAccess.issued(p,s))continue;String k=((ScrollSupport.Support)s.getItem()).key;if(k.equals("exam_slate")&&!slate){s.shrink(1);slate=true;}else if(k.equals("exam_cloud")&&!cloud){ItemStack drop=s.copy();drop.setCount(1);s.shrink(1);p.entityDropItem(drop,0);cloud=true;}}
 }
 static void cleanup(EntityPlayerMP p,boolean changedDimension){
  ScrollSupport.closeOwner(p);NBTTagCompound state=Scrolls.state(p);state.removeTag("mode");state.removeTag("modeUntil");state.setBoolean("replacement",false);state.removeTag("replacementUntil");
  if(changedDimension){state.removeTag("markDim");state.removeTag("markX");state.removeTag("markY");state.removeTag("markZ");}
  ArsenalAccess.save(p);
 }
 @SubscribeEvent public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e){if(e.player instanceof EntityPlayerMP)cleanup((EntityPlayerMP)e.player,false);}
 @SubscribeEvent public static void dimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent e){if(e.player instanceof EntityPlayerMP)cleanup((EntityPlayerMP)e.player,true);}
 @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getEntityPlayer().world.isRemote)return;/* Clone confirms death after every death-event canceller has run. */if(e.isWasDeath()&&e.getOriginal() instanceof EntityPlayerMP)cleanup((EntityPlayerMP)e.getOriginal(),false);NBTTagCompound old=PlayerState.data(e.getOriginal());if(old.hasKey("OriginalScrolls",10))PlayerState.data(e.getEntityPlayer()).setTag("OriginalScrolls",old.getCompoundTag("OriginalScrolls").copy());}
 @SubscribeEvent public static void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent e){if(!(e.player instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)e.player;String team=Scrolls.state(p).getString("party");if(team.startsWith("vs")&&p.getTeam()==null&&p.world.getScoreboard().getTeam(team)!=null)p.world.getScoreboard().addPlayerToTeam(p.getName(),team);}
}
