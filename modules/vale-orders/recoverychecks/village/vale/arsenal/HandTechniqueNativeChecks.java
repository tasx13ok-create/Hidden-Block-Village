package village.vale.arsenal;

import java.io.File;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.*;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.eventhandler.*;
import village.vale.*;

/** Actual server-side casts, native damage/absorption and callbacks; disposable fixture only. */
public final class HandTechniqueNativeChecks {
 private static final String[] KEYS={"spiral_palm","storm_lance","cinder_edge","gale_edge","molten_mantle","storm_stride"};
 private static final String[] STYLES={"spiral","charge","fireedge","windedge","lava","lightmode"};
 private final EntityPlayerMP p;private final WorldServer world;private final Gate gate=new Gate();
 private final NBTTagCompound saved;private final NBTTagList inventory;private final Vec3d position;private final float health,absorption;
 private final List<PotionEffect> potions=new ArrayList<>();private final List<Entity> actors=new ArrayList<>();
 private int assertions,receipt;private ItemStack item;private Scrolls.Lesson lesson;private EntityZombie target;private long clock;
 private HandTechniqueNativeChecks(EntityPlayerMP p){this.p=p;world=p.getServerWorld();saved=PlayerState.data(p).copy();inventory=p.inventory.writeToNBT(new NBTTagList());position=p.getPositionVector();health=p.getHealth();absorption=p.getAbsorptionAmount();for(PotionEffect e:p.getActivePotionEffects())potions.add(new PotionEffect(e));clock=Adventures.now(p)+500;gate.rig=this;}
 public static int run(EntityPlayerMP p){
  if(!Boolean.getBoolean("valeorders.recoverycheck")||!"vale-recovery-test".equals(p.getServer().getFolderName())||!new File(".vale-recovery-test").isFile())throw new IllegalStateException("Private hand-technique fixture required");
  HandTechniqueNativeChecks c=new HandTechniqueNativeChecks(p);MinecraftForge.EVENT_BUS.register(c.gate);
  try{c.setup();c.casts();c.contacts();c.cancelled();c.observations();c.callbackIsolation();return c.assertions;}
  finally{MinecraftForge.EVENT_BUS.unregister(c.gate);c.close();}
 }
 private void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError("Hand technique native: "+message);}
 private void setup(){for(int x=764;x<=776;x++)for(int z=764;z<=778;z++)for(int y=89;y<=95;y++){BlockPos b=new BlockPos(x,y,z);world.getChunk(b);world.setBlockState(b,y==89?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}}
 private void advance(int ticks){clock+=ticks;world.getWorldInfo().setWorldTotalTime(clock);}
 private void erase(){ScrollEvents.flushContacts();for(Entity e:new ArrayList<>(actors)){e.world=world;world.removeEntityDangerously(e);}actors.clear();for(HandTechnique h:new ArrayList<>(world.getEntities(HandTechnique.class,h->h.ownerId()==p.getEntityId())))world.removeEntityDangerously(h);}
 private void reset(int index){
  erase();gate.reset();advance(400);p.clearActivePotions();p.setHealth(p.getMaxHealth());p.setAbsorptionAmount(0);p.setSneaking(false);p.setPositionAndRotation(768.5,90,768.5,0,0);p.motionX=p.motionY=p.motionZ=0;
  for(int i=0;i<p.inventory.getSizeInventory();i++)p.inventory.setInventorySlotContents(i,ItemStack.EMPTY);p.inventory.setItemStack(ItemStack.EMPTY);p.inventory.currentItem=0;
  PlayerState.data(p).setInteger("xp",1000000);PlayerState.data(p).removeTag("form");PlayerState.data(p).setTag("OriginalScrolls",new NBTTagCompound());PlayerState.setResolve(p,100);
  lesson=AbilityCarriers036.lesson(KEYS[index]);check(lesson!=null,"Lesson key absent "+KEYS[index]);NBTTagCompound row=Scrolls.row(p,lesson.key);row.setBoolean("learned",true);row.setInteger("practice",60);Scrolls.row(p,"breeze_burst").setBoolean("learned",true);
  RewardLedger.get(p).issue(p,"technique_"+lesson.key,1,"native/hand/"+(receipt++));
  item=ItemStack.EMPTY;for(int i=0;i<p.inventory.getSizeInventory();i++){ItemStack s=p.inventory.getStackInSlot(i);if(s.getItem()==Gear.ITEMS.get("technique_"+lesson.key)){item=s;p.inventory.setInventorySlotContents(i,ItemStack.EMPTY);break;}}
  check(!item.isEmpty(),"Issued technique missing");p.setHeldItem(EnumHand.MAIN_HAND,item);p.getCooldownTracker().setCooldown(item.getItem(),0);
  target=new EntityZombie(world);target.setNoAI(true);target.setSilent(true);target.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(100);target.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0);target.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0);target.setHealth(100);target.setPosition(768.5,90,771.5);target.motionX=target.motionY=target.motionZ=0;check(world.spawnEntity(target),"Target spawn failed");actors.add(target);
 }
 private boolean use(){return item.getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS;}
 private HandTechnique arm(){check(use(),"Native technique activation failed "+lesson.key);HandTechnique h=HandTechnique.find(p);if(!gate.cancelSpawn&&!gate.throwSpawn)check(h!=null,"Live carrier missing");return h;}
 private void hit(DamageSource source,float amount){target.hurtResistantTime=0;target.attackEntityFrom(source,amount);}
 private boolean armed(){return lesson.kind.equals(Scrolls.state(p).getString("mode"));}
 private void casts(){
  for(int i=0;i<KEYS.length;i++){
   reset(i);HandTechnique h=arm();check(h!=null&&h.style().equals(STYLES[i])&&h.ownerId()==p.getEntityId(),"Server style/owner mismatch");
   check(PlayerState.resolve(p)==100-lesson.cost&&Scrolls.row(p,lesson.key).getLong("next")==clock+lesson.cooldown,"Mode reservation/deadline incorrect");
   check(!h.writeToNBTOptional(new NBTTagCompound()),"Carrier was persisted");h.onUpdate();check(h.state()==HandTechnique.HELD,"Valid owner carrier fizzled");
   advance(201);h.onUpdate();check(h.state()==HandTechnique.FIZZLE,"Expired mode carrier stayed held");for(int n=0;n<8;n++)h.onUpdate();check(h.isDead,"Fizzle carrier did not die");
  }
  reset(0);gate.cancelSpawn=true;check(use()&&armed()&&PlayerState.resolve(p)==76&&HandTechnique.find(p)==null,"Cosmetic spawn rejection blocked or refunded valid mode");
  reset(0);gate.throwSpawn=true;check(use()&&armed()&&PlayerState.resolve(p)==76&&Scrolls.row(p,lesson.key).getLong("next")>clock,"Cosmetic spawn exception granted a free/failed mode");
  reset(0);gate.reenter=true;arm();check(gate.rejected&&gate.observedResolve==76&&PlayerState.resolve(p)==76,"Spawn callback recursively activated or observed unreserved chakra");
  reset(0);HandTechnique h=arm();Scrolls.state(p).setString("mode","");h.onUpdate();check(h.state()==HandTechnique.FIZZLE,"Removed mode leaked carrier");
  reset(0);h=arm();HandTechnique.CLIENT_BY_OWNER.put(19001,h);HandTechnique.CLIENT_BY_OWNER.put(19002,h);HandTechnique.clearClientWorld(world);check(!HandTechnique.CLIENT_BY_OWNER.containsKey(19001)&&!HandTechnique.CLIENT_BY_OWNER.containsKey(19002),"World cache cleanup retained stale owner IDs");
  HandTechnique loaded=new HandTechnique(world);loaded.readEntityFromNBT(new NBTTagCompound());check(loaded.isDead,"Reloaded decorative entity resurrected");
  reset(0);h=arm();ScrollEvents.cleanup(p,false);check(!armed()&&h.state()==HandTechnique.FIZZLE,"Logout/death cleanup retained hand mode/carrier");
 }
 private void contacts(){
  float[] extra={8,6,3,4,3,2};
  for(int i=0;i<KEYS.length;i++){
   reset(i);HandTechnique h=arm();hit(DamageSource.causePlayerDamage(p),2);check(Math.abs(target.getHealth()-(98-extra[i]))<.001,"Native bonus damage wrong "+lesson.key);check(h.state()==HandTechnique.HELD,"Impact announced before final native settlement");ScrollEvents.flushContacts();
   if(i<=1)check(!armed()&&h.state()==HandTechnique.IMPACT,"Accepted single contact did not consume/burst "+lesson.key);else check(armed()&&h.state()==HandTechnique.HELD,"Persistent mode was consumed "+lesson.key);
   if(i==2||i==4)check(target.isBurning(),"Accepted fire aftermath missing");if(i==1||i==5)check(target.isPotionActive(MobEffects.SLOWNESS),"Accepted lightning slow missing");
  }
  reset(0);HandTechnique h=arm();target.setAbsorptionAmount(40);hit(DamageSource.causePlayerDamage(p),2);check(target.getHealth()==100&&target.getAbsorptionAmount()<40&&gate.lastDamage==0,"Fixture did not create real absorption-only native contact");ScrollEvents.flushContacts();check(!armed()&&h.state()==HandTechnique.IMPACT,"Fully absorbed contact did not consume/burst");
 }
 private void cancelled(){
  for(int index=0;index<KEYS.length;index++)for(int stage=1;stage<=3;stage++){
   reset(index);HandTechnique h=arm();gate.stage=stage;hit(DamageSource.causePlayerDamage(p),2);ScrollEvents.flushContacts();
   check(target.getHealth()==100&&armed()&&h.state()==HandTechnique.HELD,"Canceled mode damage/consume at stage "+stage+" key "+lesson.key);
   check(!target.isBurning()&&!target.isPotionActive(MobEffects.SLOWNESS),"Canceled contact applied aftermath");
  }
  reset(0);HandTechnique h=arm();EntityTippedArrow arrow=new EntityTippedArrow(world);arrow.shootingEntity=p;hit(DamageSource.causeArrowDamage(arrow,p),2);ScrollEvents.flushContacts();check(target.getHealth()==98&&armed()&&h.state()==HandTechnique.HELD,"Arrow triggered hand technique");
  reset(0);h=arm();target.setPosition(768.5,90,775.5);hit(DamageSource.causePlayerDamage(p),2);ScrollEvents.flushContacts();check(target.getHealth()==98&&armed(),"Distant direct source triggered melee bonus");
  reset(0);h=arm();for(int y=90;y<=93;y++)world.setBlockState(new BlockPos(768,y,770),Blocks.STONE.getDefaultState(),2);hit(DamageSource.causePlayerDamage(p),2);ScrollEvents.flushContacts();check(target.getHealth()==98&&armed(),"Occluded hand contact triggered bonus");for(int y=90;y<=93;y++)world.setBlockToAir(new BlockPos(768,y,770));
 }
 private void observations(){
  for(int stage=2;stage<=3;stage++){
   reset(0);HandTechnique h=arm();hit(DamageSource.causePlayerDamage(p),2);float hp=target.getHealth();gate.stage=stage;hit(DamageSource.GENERIC,3);check(target.getHealth()==hp,"Later cancellation fixture damaged target");ScrollEvents.flushContacts();check(!armed()&&h.state()==HandTechnique.IMPACT,"Later canceled source erased accepted receipt stage "+stage);
  }
  reset(0);HandTechnique h=arm();hit(DamageSource.causePlayerDamage(p),2);target.heal(10);check(target.getHealth()==100,"Post-contact heal fixture failed");ScrollEvents.flushContacts();check(!armed()&&h.state()==HandTechnique.IMPACT,"Post-contact native healing erased accepted receipt");
  reset(0);h=arm();hit(DamageSource.causePlayerDamage(p),2);ScrollEvents.beforeHealthReset(target);target.setHealth(100);ScrollEvents.flushContacts();check(!armed()&&h.state()==HandTechnique.IMPACT,"Observed academy-style direct health reset erased accepted receipt");
  reset(0);h=arm();gate.zeroDamage=true;hit(DamageSource.causePlayerDamage(p),2);gate.zeroDamage=false;hit(DamageSource.GENERIC,3);ScrollEvents.flushContacts();check(armed()&&h.state()==HandTechnique.HELD,"Zero final damage plus unrelated damage consumed mode");
  reset(0);h=arm();DamageSource fake=DamageSource.causePlayerDamage(p);MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target,fake,2));MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target,fake,10));ScrollEvents.flushContacts();check(armed()&&h.state()==HandTechnique.HELD&&target.getHealth()==100,"Synthetic events announced accepted contact");
 }
 private void callbackIsolation(){
  reset(1);HandTechnique old=arm();hit(DamageSource.causePlayerDamage(p),2);gate.rearm=true;ScrollEvents.flushContacts();HandTechnique next=HandTechnique.find(p);check(next!=null&&next!=old&&next.state()==HandTechnique.HELD&&next.style().equals("fireedge")&&Scrolls.state(p).getString("mode").equals("fireedge"),"Old slow callback burst/consumed a rearmed carrier");check(old.state()==HandTechnique.IMPACT,"Old accepted carrier did not burst before callback");
  reset(0);old=arm();hit(DamageSource.causePlayerDamage(p),2);Scrolls.state(p).removeTag("mode");ScrollEffects.mode(p,"windedge",200);next=HandTechnique.find(p);ScrollEvents.flushContacts();check(next!=null&&next.state()==HandTechnique.HELD&&Scrolls.state(p).getString("mode").equals("windedge"),"Old pending contact consumed replacement activation");
 }
 private void close(){try{erase();p.inventory.readFromNBT(inventory);p.clearActivePotions();for(PotionEffect e:potions)p.addPotionEffect(e);p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).setTag("ValeOrders",saved);p.setPosition(position.x,position.y,position.z);p.setHealth(Math.min(health,p.getMaxHealth()));p.setAbsorptionAmount(absorption);HandTechnique.clearClientWorld(world);}finally{gate.reset();}}
 public static final class Gate {
  HandTechniqueNativeChecks rig;int stage,observedResolve;boolean cancelSpawn,throwSpawn,reenter,rejected,rearm,zeroDamage;float lastDamage=-1;
  void reset(){stage=0;cancelSpawn=throwSpawn=reenter=rejected=rearm=zeroDamage=false;lastDamage=-1;}
  @SubscribeEvent(priority=EventPriority.LOWEST)public void spawn(EntityJoinWorldEvent e){if(!(e.getEntity() instanceof HandTechnique))return;observedResolve=PlayerState.resolve(rig.p);if(reenter){reenter=false;rejected=!rig.use();}if(cancelSpawn)e.setCanceled(true);if(throwSpawn)throw new IllegalStateException("Expected hand carrier decorative spawn failure");}
  @SubscribeEvent(priority=EventPriority.LOWEST)public void attack(LivingAttackEvent e){if(stage==1&&e.getEntityLiving()==rig.target)e.setCanceled(true);}
  @SubscribeEvent(priority=EventPriority.LOWEST)public void hurt(LivingHurtEvent e){if(stage==2&&e.getEntityLiving()==rig.target)e.setCanceled(true);}
  @SubscribeEvent(priority=EventPriority.LOWEST)public void damage(LivingDamageEvent e){if(e.getEntityLiving()!=rig.target)return;lastDamage=e.getAmount();if(stage==3)e.setCanceled(true);if(zeroDamage)e.setAmount(0);}
  @SubscribeEvent(priority=EventPriority.LOWEST)public void potion(PotionEvent.PotionApplicableEvent e){if(rearm&&e.getEntityLiving()==rig.target&&e.getPotionEffect().getPotion()==MobEffects.SLOWNESS){rearm=false;ScrollEffects.mode(rig.p,"fireedge",200);}}
 }
}
