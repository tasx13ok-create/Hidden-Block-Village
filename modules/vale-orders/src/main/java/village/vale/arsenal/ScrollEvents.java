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
 private static final Map<UUID,EscapeAttempt> ESCAPES=new ConcurrentHashMap<>();
 private static final java.util.List<ModeContact> CONTACTS=new java.util.ArrayList<>();
 @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void preserveContact(LivingAttackEvent e){
  // A later attempt (even one canceled in hurt/damage) can overwrite vanilla's
  // last source. Preserve the earlier native observation before that happens.
  for(ModeContact c:CONTACTS)if(c.target==e.getEntityLiving())c.observeNativeLoss();
 }
 @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true) public static void preserveHealedContact(LivingHealEvent e){for(ModeContact c:CONTACTS)if(c.target==e.getEntityLiving())c.observeNativeLoss();}
 /** Academy constructs refill directly rather than emitting LivingHealEvent. Preserve their real completed contact first. */
 public static void beforeHealthReset(EntityLivingBase target){village.vale.FocusTransactions.beforeHealthReset(target);for(ModeContact c:CONTACTS)if(c.target==target)c.observeNativeLoss();}
 /** Add through the native attack, never recursively attack inside LivingDamageEvent. */
 @SubscribeEvent(priority=EventPriority.HIGH) public static void modeBonus(LivingHurtEvent e){
  if(e.isCanceled()||!Float.isFinite(e.getAmount())||e.getAmount()<=0||e.getEntityLiving().world.isRemote||CONTACTS.size()>=512)return;
  Entity source=e.getSource().getTrueSource();if(!(source instanceof EntityPlayerMP)||e.getSource().getImmediateSource()!=source||!"player".equals(e.getSource().getDamageType()))return;
  EntityPlayerMP p=(EntityPlayerMP)source;EntityLivingBase target=e.getEntityLiving();
  if(!permittedContact(p,target))return;
  NBTTagCompound account=PlayerState.data(p),state=Scrolls.state(p);String mode=state.getString("mode");long until=state.getLong("modeUntil");
  if(until<ArsenalAccess.now(p))return;
  float extra=mode.equals("spiral")?8:mode.equals("charge")?6:mode.equals("fireedge")?3:mode.equals("windedge")?4:mode.equals("lightmode")?2:mode.equals("lava")?3:0;
  if(extra==0)return;
  long version=state.getLong("modeVersion");
  if(mode.equals("spiral")||mode.equals("charge"))for(ModeContact pending:CONTACTS)if(pending.player==p&&pending.state==state&&pending.version==version)return;
  ModeContact contact=new ModeContact(p,target,account,state,mode,until,version,e);
  if(!Float.isFinite(contact.before))return;CONTACTS.add(contact);e.setAmount(e.getAmount()+extra);
 }
 @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void damage(LivingDamageEvent e){
  for(int i=CONTACTS.size()-1;i>=0;i--){ModeContact c=CONTACTS.get(i);if(c.target==e.getEntityLiving()&&c.hurt.getSource()==e.getSource()&&c.damage==null){c.damage=e;c.absorbed=Math.max(0,c.absorptionBefore-c.target.getAbsorptionAmount());break;}}
 }
 private static boolean permittedContact(EntityPlayerMP p,EntityLivingBase target){
  if(!ArsenalAccess.allowed(p,target)||p.getDistanceSq(target)>16)return false;
  net.minecraft.util.math.Vec3d a=p.getPositionEyes(1),b=target.getPositionEyes(1);
  net.minecraft.util.math.BlockPos lo=new net.minecraft.util.math.BlockPos(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z)),hi=new net.minecraft.util.math.BlockPos(Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z));
  return p.world.isAreaLoaded(lo,hi)&&p.world.getWorldBorder().contains(new net.minecraft.util.math.BlockPos(b))&&p.canEntityBeSeen(target);
 }
 private static final class ModeContact{
  final EntityPlayerMP player;final EntityLivingBase target;final UUID owner;final net.minecraft.world.World world;final NBTTagCompound account,state;
  final String mode;final long until,version;final LivingHurtEvent hurt;final float before,absorptionBefore;LivingDamageEvent damage;boolean observedLoss;float absorbed;
  ModeContact(EntityPlayerMP p,EntityLivingBase t,NBTTagCompound a,NBTTagCompound s,String m,long u,long v,LivingHurtEvent h){player=p;target=t;owner=p.getUniqueID();world=p.world;account=a;state=s;mode=m;until=u;version=v;hurt=h;before=t.getHealth()+t.getAbsorptionAmount();absorptionBefore=t.getAbsorptionAmount();}
  boolean activation(){return owner.equals(player.getUniqueID())&&player.world==world&&PlayerState.data(player)==account&&Scrolls.state(player)==state&&mode.equals(state.getString("mode"))&&state.getLong("modeUntil")==until&&state.getLong("modeVersion")==version&&player.isEntityAlive()&&!player.isSpectator();}
  void observeNativeLoss(){if(target.getLastDamageSource()==hurt.getSource()&&target.getHealth()+target.getAbsorptionAmount()<before)observedLoss=true;}
  boolean accepted(){observeNativeLoss();return !hurt.isCanceled()&&Float.isFinite(hurt.getAmount())&&hurt.getAmount()>0&&damage!=null&&!damage.isCanceled()&&Float.isFinite(damage.getAmount())&&damage.getAmount()>=0&&(damage.getAmount()>0||absorbed>0)&&observedLoss;}
 }
 @SubscribeEvent public static void contacts(net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent e){if(e.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END)flushContacts();}
 @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload e){CONTACTS.removeIf(c->c.world==e.getWorld());}
 /** Package access for the private native harness; production calls once at server END. */
 static void flushContacts(){
  java.util.List<ModeContact> contacts=new java.util.ArrayList<>(CONTACTS);CONTACTS.clear();
  for(ModeContact c:contacts){
   if(!c.accepted()||!c.activation())continue;
   EntityPlayerMP p=c.player;boolean single=c.mode.equals("spiral")||c.mode.equals("charge");
   // Consume the captured activation before any cosmetic/potion callbacks can install another.
   if(single){c.state.removeTag("mode");c.state.removeTag("modeUntil");c.state.setLong("modeVersion",c.version+1);}
   try{
    boolean permitted=permittedContact(p,c.target);
    if(single&&c.target.world==c.world&&(permitted||!c.target.isEntityAlive()))HandTechnique.impact(p,c.target);
    permitted=permittedContact(p,c.target);
    if(permitted){
     if(c.mode.equals("fireedge")||c.mode.equals("lava"))c.target.setFire(3);
     if(c.mode.equals("windedge"))ScrollEffects.push(p,c.target,.65,.15);
     if(c.mode.equals("charge")||c.mode.equals("lightmode"))c.target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,40,2));
     if(c.mode.equals("spiral"))ScrollEffects.push(p,c.target,1,.4);
    }
   }catch(RuntimeException error){org.apache.logging.log4j.LogManager.getLogger("ValeJutsu").warn("Accepted hand contact presentation/status failed; activation settlement retained",error);}
   finally{ArsenalAccess.save(p);}
  }
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
  ScrollSupport.closeOwner(p);CONTACTS.removeIf(c->c.player==p);HandTechnique.end(p);NBTTagCompound state=Scrolls.state(p);state.removeTag("mode");state.removeTag("modeUntil");state.setBoolean("replacement",false);state.removeTag("replacementUntil");
  if(changedDimension){state.removeTag("markDim");state.removeTag("markX");state.removeTag("markY");state.removeTag("markZ");}
  ArsenalAccess.save(p);
 }
 @SubscribeEvent public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e){if(e.player instanceof EntityPlayerMP)cleanup((EntityPlayerMP)e.player,false);}
 @SubscribeEvent public static void dimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent e){if(e.player instanceof EntityPlayerMP)cleanup((EntityPlayerMP)e.player,true);}
 @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getEntityPlayer().world.isRemote)return;/* Clone confirms death after every death-event canceller has run. */if(e.isWasDeath()&&e.getOriginal() instanceof EntityPlayerMP)cleanup((EntityPlayerMP)e.getOriginal(),false);NBTTagCompound old=PlayerState.data(e.getOriginal());if(old.hasKey("OriginalScrolls",10))PlayerState.data(e.getEntityPlayer()).setTag("OriginalScrolls",old.getCompoundTag("OriginalScrolls").copy());}
 @SubscribeEvent public static void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent e){if(!(e.player instanceof EntityPlayerMP))return;EntityPlayerMP p=(EntityPlayerMP)e.player;String team=Scrolls.state(p).getString("party");if(team.startsWith("vs")&&p.getTeam()==null&&p.world.getScoreboard().getTeam(team)!=null)p.world.getScoreboard().addPlayerToTeam(p.getName(),team);}
}
