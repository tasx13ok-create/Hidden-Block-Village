package village.vale.arsenal;

import java.io.File;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.eventhandler.*;
import village.vale.*;
import village.vale.bridge.*;

/** PRIVATE actual native paired receipt checks; no registry entries, caller supplied fresh fixture only. */
public final class PairedAbsorptionNativeChecks {
 private final EntityPlayerMP p;private final WorldServer world;private final Gate gate=new Gate();private final List<EntityZombie> actors=new ArrayList<>();private int checks;
 private PairedAbsorptionNativeChecks(EntityPlayerMP p){this.p=p;world=p.getServerWorld();}
 public static int run(EntityPlayerMP p)throws Exception{
  if(p==null||p.getServer()==null||!p.getServer().isCallingFromMinecraftThread()||p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())!=p)throw new IllegalStateException("Disposable real current MP required");
  boolean presence=Boolean.getBoolean("vale.bridge.nativeFixture")&&"bridge-native-flat".equals(p.getServer().getFolderName())&&"ValeBridgeNative".equals(p.getName())&&new File("PRIVATE-DISPOSABLE-BRIDGE-NATIVE.json").isFile();
  boolean absent=Boolean.getBoolean("valeorders.recoverycheck")&&"vale-recovery-test".equals(p.getServer().getFolderName())&&new File(".vale-recovery-test").isFile()&&"SharedCost0316".equals(p.getName())&&UUID.fromString("43ec7807-821a-4a56-9c03-031600000001").equals(p.getUniqueID())&&!MoveCost.external();
  if(!presence&&!absent)throw new IllegalStateException("Approved isolated presence/absent native fixture required");
  PairedAbsorptionNativeChecks c=new PairedAbsorptionNativeChecks(p);c.exercise();return c.checks;
 }
 private void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError("Paired absorption: "+message+" trace="+gate.trace());}
 private EntityZombie target(){EntityZombie t=new EntityZombie(world);t.setNoAI(true);t.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(200);t.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0);t.setHealth(200);t.setAbsorptionAmount(100);t.setPosition(p.posX,p.posY,p.posZ+2);t.motionX=t.motionY=t.motionZ=0;check(world.spawnEntity(t),"native zombie admission");actors.add(t);gate.target=t;gate.hurt=null;gate.damage=null;return t;}
 private ItemStack issue(String key){RewardLedger.get(p).issue(p,key,1,"paired-abs/"+UUID.randomUUID()+"/"+key);for(ItemStack s:p.inventory.mainInventory)if(Gear.key(s).equals("valeorders:"+key)&&RewardLedger.get(p).valid(p,s))return s;throw new AssertionError("Issued stack missing "+key);}
 private void arm(){NBTTagCompound state=Scrolls.state(p);state.setString("mode","spiral");state.setLong("modeUntil",ArsenalAccess.now(p)+200);state.setLong("modeVersion",state.getLong("modeVersion")+1);}
 private boolean armed(){return "spiral".equals(Scrolls.state(p).getString("mode"));}
 private void hit(EntityZombie t,DamageSource source){t.hurtResistantTime=0;t.attackEntityFrom(source,8);}
 private void exercise(){
  NBTTagCompound original=p.getEntityData().copy();NBTTagList inventory=p.inventory.writeToNBT(new NBTTagList());int selected=p.inventory.currentItem;
  Set<Entity> before=Collections.newSetFromMap(new IdentityHashMap<Entity,Boolean>());before.addAll(world.loadedEntityList);MinecraftForge.EVENT_BUS.register(gate);
  try{
   check(MoveCost.view(p).ready(),"provider ready");p.inventory.clear();PlayerState.data(p).setInteger("xp",4900);
   ItemStack technique=issue("technique_spiral_palm");for(int i=0;i<36;i++)if(p.inventory.mainInventory.get(i)==technique)p.inventory.mainInventory.set(i,ItemStack.EMPTY);p.inventory.mainInventory.set(0,technique);p.inventory.currentItem=0;
   // Activation cost is covered by ArsenalResourceNativeChecks. Arrange the already-armed
   // mode here to isolate actual damage receipt settlement, not progression or a cast bypass.
   arm();EntityZombie t=target();float hp=t.getHealth();hit(t,DamageSource.causePlayerDamage(p));
   check(t.getHealth()==hp&&t.getAbsorptionAmount()<100,"actual fully absorbed mode hit");check(gate.damage!=null&&!gate.damage.isCanceled()&&gate.damage.getAmount()==0,"real uncanceled final Damage0 event");
   ScrollEvents.flushContacts();check(!armed(),"absorbed accepted contact consumes single-use mode");
   arm();t=target();gate.cancelDamage=true;hit(t,DamageSource.causePlayerDamage(p));gate.cancelDamage=false;
   check(gate.damage!=null&&gate.damage.isCanceled(),"actual final damage cancellation reached");ScrollEvents.flushContacts();check(armed(),"late-canceled final event keeps single-use mode");
   arm();t=target();hp=t.getHealth();hit(t,DamageSource.causePlayerDamage(p));check(t.getHealth()==hp&&t.getAbsorptionAmount()<100,"accepted first hit before later cancel");
   gate.cancelHurt=true;hit(t,DamageSource.GENERIC);gate.cancelHurt=false;ScrollEvents.flushContacts();check(!armed(),"later canceled unrelated source cannot invalidate earlier accepted receipt");
   arm();t=target();t.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE,200,4));hp=t.getHealth();hit(t,DamageSource.causePlayerDamage(p));
   check(t.getHealth()==hp&&t.getAbsorptionAmount()==100,"first native100percent-resistance hit has zero loss");check(gate.damage!=null&&!gate.damage.isCanceled()&&gate.damage.getAmount()==0,"resistance path emitted real uncanceled Damage0");
   t.removePotionEffect(MobEffects.RESISTANCE);hit(t,DamageSource.GENERIC);check(t.getAbsorptionAmount()<100,"separate later native damage removes absorption");ScrollEvents.flushContacts();check(armed(),"zero-loss first receipt cannot inherit later unrelated absorption loss");
  }finally{
   gate.cancelDamage=gate.cancelHurt=false;ScrollEvents.flushContacts();MinecraftForge.EVENT_BUS.unregister(gate);
   for(Entity e:new ArrayList<>(world.loadedEntityList))if(!before.contains(e)&&(actors.contains(e)||e instanceof HandTechnique)){e.setDead();world.removeEntity(e);}
   p.inventory.readFromNBT(inventory);p.inventory.currentItem=selected;for(String k:new HashSet<>(p.getEntityData().getKeySet()))p.getEntityData().removeTag(k);for(String k:original.getKeySet())p.getEntityData().setTag(k,original.getTag(k).copy());
  }
 }
 public static final class Gate {
  EntityZombie target;LivingHurtEvent hurt;LivingDamageEvent damage;float absorptionAtDamage;boolean cancelDamage,cancelHurt;
  String trace(){return "hurt="+(hurt==null?"none":hurt.getAmount()+"/"+hurt.isCanceled())+",damage="+(damage==null?"none":damage.getAmount()+"/"+damage.isCanceled())+",absAtDamage="+absorptionAtDamage+",absNow="+(target==null?-1:target.getAbsorptionAmount());}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void hurt(LivingHurtEvent e){if(e.getEntityLiving()==target){hurt=e;if(cancelHurt)e.setCanceled(true);}}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void damage(LivingDamageEvent e){if(e.getEntityLiving()==target){damage=e;absorptionAtDamage=target.getAbsorptionAmount();if(cancelDamage)e.setCanceled(true);}}
 }
}
