package village.vale.arsenal;

import java.util.*;
import village.vale.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.*;

/** One persisted, bounded actor family. No vanilla attack AI bypasses ArsenalAccess. */
public final class ScrollActor extends EntityCreature {
 private static final DataParameter<Integer> MIRROR=EntityDataManager.createKey(ScrollActor.class,DataSerializers.VARINT);
 private static final DataParameter<String> KIND=EntityDataManager.createKey(ScrollActor.class,DataSerializers.STRING);
 public String kind="",sourceSerial="";UUID owner,target;long expires;float power;int strikes;private double anchorX,anchorY,anchorZ;
 public ScrollActor(World w){super(w);setSize(.65f,.8f);enablePersistence();}
 @Override protected void entityInit(){super.entityInit();dataManager.register(KIND,"");dataManager.register(MIRROR,-1);}
 @Override protected void initEntityAI(){}
 @Override protected void applyEntityAttributes(){super.applyEntityAttributes();getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30);getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(.25);}
 @Override public boolean canDespawn(){return false;}
 @Override public boolean canBeLeashedTo(EntityPlayer p){return false;}
 boolean owned(EntityPlayerMP p){return owner!=null&&owner.equals(p.getUniqueID());}
 static ScrollActor spawn(EntityPlayerMP p,String kind,Vec3d at,EntityLivingBase t,int life,float power){
  if(!p.world.getWorldBorder().contains(new BlockPos(at))||!p.world.isBlockLoaded(new BlockPos(at)))return null;
  ScrollActor a=new ScrollActor(p.world);a.owner=p.getUniqueID();a.kind=kind;a.dataManager.set(KIND,kind);a.target=t==null?null:t.getUniqueID();if(t!=null)a.dataManager.set(MIRROR,t.getEntityId());a.power=power;a.expires=p.world.getTotalWorldTime()+life;a.anchorX=at.x;a.anchorY=at.y;a.anchorZ=at.z;a.setPosition(at.x,at.y,at.z);a.setCustomNameTag(title(kind));
  if(kind.equals("wall")){a.setSize(3,3);a.setHealth(30);}else if(kind.startsWith("puppet")){a.setHealth(30);}else if(kind.equals("golem")){a.setSize(1.2f,2.4f);}
  return p.world.spawnEntity(a)?a:null;
 }
 static String title(String k){return "Vale "+k.replace('_',' ');}
 static boolean exists(EntityPlayerMP p,String k){for(Entity e:p.world.loadedEntityList)if(e instanceof ScrollActor&&((ScrollActor)e).owned(p)&&((ScrollActor)e).kind.equals(k)&&!e.isDead)return true;return false;}
 static boolean dismiss(EntityPlayerMP p,String k){boolean any=false;for(Entity e:new ArrayList<Entity>(p.world.loadedEntityList))if(e instanceof ScrollActor&&((ScrollActor)e).owned(p)&&((ScrollActor)e).kind.equals(k)&&!e.isDead){e.setDead();any=true;}return any;}
 static ScrollActor find(EntityPlayerMP p,UUID id){for(Entity e:p.world.loadedEntityList)if(e instanceof ScrollActor&&e.getUniqueID().equals(id)&&((ScrollActor)e).owned(p)&&!e.isDead)return(ScrollActor)e;return null;}
 EntityLivingBase target(){if(target==null)return null;for(Entity e:world.loadedEntityList)if(e.getUniqueID().equals(target)&&e instanceof EntityLivingBase&&!e.isDead)return(EntityLivingBase)e;return null;}
 @Override public AxisAlignedBB getCollisionBoundingBox(){return kind.equals("wall")?getEntityBoundingBox():null;}
 @Override public boolean attackEntityFrom(DamageSource source,float amount){if(world.isRemote)return false;Entity attacker=source.getTrueSource();EntityPlayerMP p=owner==null?null:world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);if(p==null||!(attacker instanceof EntityLivingBase)||!(attacker instanceof EntityPlayerMP?ArsenalAccess.allowed((EntityPlayerMP)attacker,p):ArsenalAccess.allowed(p,(EntityLivingBase)attacker)))return false;return super.attackEntityFrom(source,amount);}
 void recordCompanion(boolean closed){if(world.isRemote||sourceSerial.isEmpty()||owner==null)return;EntityPlayerMP p=world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);if(p==null)return;ScrollJournal j=ScrollJournal.get(p);NBTTagCompound n=j.companion(sourceSerial);if(n.hasUniqueId("actor")&&!getUniqueID().equals(n.getUniqueId("actor")))return;n.setFloat("health",Math.max(0,getHealth()));if(closed)n.setBoolean("deployed",false);j.markDirty();if(closed)j.flush(p);}
 @Override public void onDeath(DamageSource source){recordCompanion(true);super.onDeath(source);}
 @Override public void onLivingUpdate(){
  super.onLivingUpdate();if(world.isRemote){kind=dataManager.get(KIND);return;}
  if(owner==null||world.getTotalWorldTime()>=expires){recordCompanion(true);setDead();return;}if(ticksExisted%20==0)recordCompanion(false);
  EntityPlayerMP p=world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);if(p==null||p.world!=world||!p.isEntityAlive()){motionX=motionY=motionZ=0;return;}
  if(getDistanceSq(p)>4096){setDead();return;}
  EntityLivingBase t=target();
  if(kind.equals("wall")){motionX=motionY=motionZ=0;setPosition(anchorX,anchorY,anchorZ);return;}
  if(kind.equals("disguise")){setPosition(p.posX,p.posY,p.posZ);p.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY,25,0));if(t!=null){setCustomNameTag(t.getName());dataManager.set(MIRROR,t.getEntityId());}return;}
  if(kind.equals("titan")){setPosition(p.posX,p.posY,p.posZ);if(ticksExisted%20==0)for(EntityLivingBase e:ArsenalAccess.targets(p,3,false,3))if(ScrollEffects.hit(p,e,2,null,0,0))ScrollEffects.push(p,e,.45,.3);return;}
  if(kind.equals("refuge")){setPosition(anchorX,anchorY,anchorZ);if(p.getDistanceSq(this)<4)p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE,25,1));return;}
  if(kind.equals("golem")||kind.equals("clone")||kind.startsWith("puppet")||kind.startsWith("summon_")){
   if(!sourceSerial.isEmpty()&&ticksExisted<30)return;
   if(kind.equals("summon_1")&&getDistanceSq(p)<25)p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE,25,0));
   if(kind.equals("summon_3")){getNavigator().tryMoveToEntityLiving(p,1);if(ticksExisted%40==0&&getDistanceSq(p)<9&&p.getHealth()<p.getMaxHealth())p.heal(1);return;}
   if(t==null){List<EntityLivingBase> near=ArsenalAccess.targets(p,10,false,1);for(EntityLivingBase candidate:near)if(!(candidate instanceof ScrollActor)){t=candidate;target=t.getUniqueID();break;}}
   EntityLivingBase follow=t==null?p:t;if(t!=null&&!ArsenalAccess.allowed(p,t)){target=null;return;}
   getNavigator().tryMoveToEntityLiving(follow,kind.equals("puppet_sentinel")?.7:1.1);
   if(t!=null&&ticksExisted%20==0&&getDistanceSq(t)<((kind.equals("puppet_spindle")||kind.equals("summon_2"))?100:9)){
    float amount=kind.equals("puppet_sentinel")?2:kind.equals("puppet_spindle")?3:kind.equals("puppet_furnace")?4:power;
    if(ScrollEffects.hit(p,t,amount,kind.equals("puppet_wisp")?MobEffects.POISON:null,60,0)){if(kind.equals("puppet_furnace"))t.setFire(2);if(kind.equals("puppet_sentinel"))ScrollEffects.push(p,t,.5,.2);if(kind.equals("summon_2"))t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,40,1));}
   }return;
  }
  if(kind.equals("mind")||kind.equals("shadow")||kind.equals("chains")||kind.equals("prison")||kind.equals("swarm")){
   if(t==null||!ArsenalAccess.allowed(p,t)||p.getDistanceSq(t)>576){setDead();return;}setPosition(t.posX,t.posY,t.posZ);
   if(ticksExisted%20==0&&ScrollEffects.hit(p,t,Math.max(1,power),null,0,0)){
    if(kind.equals("swarm")){PlayerState.setResolve(p,Math.min(100,PlayerState.resolve(p)+1));}
    else{t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,25,kind.equals("chains")?2:4));if(kind.equals("mind"))t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,25,2));if(kind.equals("prison"))t.motionY=Math.min(0,t.motionY);if(kind.equals("shadow"))t.rotationYaw=p.rotationYaw;}
   }return;
  }
  if(Arrays.asList("ash","mist","poison","ring","shockwave","ritual","clasp","thorns","swamp").contains(kind)){
   motionX=motionY=motionZ=0;setPosition(anchorX,anchorY,anchorZ);if(ticksExisted%20!=0)return;
   if(kind.equals("ash")||kind.equals("mist"))for(EntityPlayer ally:world.playerEntities)if((ally==p||p.isOnSameTeam(ally))&&getDistanceSq(ally)<25)ally.addPotionEffect(new PotionEffect(kind.equals("ash")?MobEffects.INVISIBILITY:MobEffects.RESISTANCE,25,0));
   for(EntityLivingBase e:world.getEntitiesWithinAABB(EntityLivingBase.class,getEntityBoundingBox().grow(kind.equals("ring")?4:3)))if(!(e instanceof ScrollActor)&&ArsenalAccess.allowed(p,e)){
    if(ScrollEffects.hit(p,e,Math.max(1,power),null,0,0)){
     if(kind.equals("poison"))e.addPotionEffect(new PotionEffect(MobEffects.POISON,60,0));
     if(kind.equals("mist"))e.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS,30,0));
     if(kind.equals("ring"))e.setFire(2);
     if(kind.equals("clasp")||kind.equals("swamp")||kind.equals("ritual"))e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,30,kind.equals("clasp")?4:2));
     if(kind.equals("ritual"))e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,40,1));
     if(kind.equals("thorns"))e.addVelocity(0,.35,0);
     if(kind.equals("shockwave"))ScrollEffects.push(p,e,.65,.1);
    }
   }return;
  }
  // Targeted moving actors collide along the complete step, so fast darts cannot tunnel.
  if(t==null||!ArsenalAccess.allowed(p,t)){setDead();return;}
  if(kind.equals("thunder")&&ticksExisted<25){motionX=motionY=motionZ=0;return;}
  Vec3d from=getPositionVector(),delta=t.getPositionEyes(1).subtract(from);double speed=kind.equals("blade")?1.5:kind.equals("shark")?.45:kind.equals("dragon")?.6:.8;Vec3d step=delta.normalize().scale(Math.min(speed,delta.length()));Vec3d to=from.add(step);
  RayTraceResult block=world.rayTraceBlocks(from,to,false,true,false);if(block!=null){setDead();return;}
  if(t.getEntityBoundingBox().grow(.6).calculateIntercept(from,to)!=null||delta.length()<1.1){
   if(ScrollEffects.hit(p,t,power,null,0,0)){
    strikes++;if(kind.equals("fireball")||kind.equals("front")||kind.equals("firestream"))t.setFire(kind.equals("front")?4:2);
    if(kind.equals("cannon"))ScrollEffects.push(p,t,.85,.2);
    if(kind.equals("waterstream")){t.extinguish();ScrollEffects.push(p,t,.15,0);}
    if(kind.equals("shark")){t.setAir(Math.max(0,t.getAir()-60));t.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,60,0));}
    if(kind.equals("panther")){Vec3d behind=t.getPositionVector().subtract(t.getLookVec().scale(1.5));if(ScrollEffects.safe(p,behind))p.connection.setPlayerLocation(behind.x,behind.y,behind.z,p.rotationYaw,p.rotationPitch);}
    if(kind.equals("dragon"))t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,60,1));
    if(kind.equals("hound")||kind.equals("panther")||kind.equals("thunder"))t.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,40,2));
    if(kind.equals("vacuum")){Vec3d pull=p.getPositionVector().subtract(t.getPositionVector()).normalize();t.addVelocity(pull.x*.4,.1,pull.z*.4);}
    if(kind.equals("front"))for(EntityLivingBase e:ArsenalAccess.targets(p,12,true,8))if(e!=t)ScrollEffects.hit(p,e,4,null,0,0);
   }
   if(!(kind.equals("firestream")||kind.equals("waterstream"))||strikes>=4){setDead();return;}
   if(ticksExisted%10!=0)return;
  }
  setPosition(to.x,to.y,to.z);motionX=motionY=motionZ=0;
 }
 @Override public void writeEntityToNBT(NBTTagCompound n){super.writeEntityToNBT(n);if(owner!=null)n.setUniqueId("owner",owner);if(target!=null)n.setUniqueId("target",target);n.setString("kind",kind);n.setString("sourceSerial",sourceSerial);n.setLong("expires",expires);n.setFloat("power",power);n.setInteger("strikes",strikes);n.setDouble("ax",anchorX);n.setDouble("ay",anchorY);n.setDouble("az",anchorZ);}
 @Override public void readEntityFromNBT(NBTTagCompound n){super.readEntityFromNBT(n);owner=n.hasUniqueId("owner")?n.getUniqueId("owner"):null;target=n.hasUniqueId("target")?n.getUniqueId("target"):null;kind=n.getString("kind");sourceSerial=n.getString("sourceSerial");dataManager.set(KIND,kind);expires=n.getLong("expires");power=Math.max(0,Math.min(16,n.getFloat("power")));strikes=n.getInteger("strikes");anchorX=n.getDouble("ax");anchorY=n.getDouble("ay");anchorZ=n.getDouble("az");if(kind.equals("wall"))setSize(3,3);else if(kind.equals("golem"))setSize(1.2f,2.4f);}
 @SideOnly(Side.CLIENT) static void registerRenderer(){net.minecraftforge.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(ScrollActor.class,manager->new net.minecraft.client.renderer.entity.RenderSnowball<ScrollActor>(manager,Gear.ITEMS.get("scroll_actor_glyph"),net.minecraft.client.Minecraft.getMinecraft().getRenderItem()){
  @Override public void doRender(ScrollActor a,double x,double y,double z,float yaw,float partial){
   if(a.dataManager.get(KIND).equals("disguise")){Entity mirrored=a.world.getEntityByID(a.dataManager.get(MIRROR));if(mirrored instanceof EntityLivingBase&&!(mirrored instanceof ScrollActor)){manager.renderEntity(mirrored,x,y,z,yaw,partial,false);return;}}
   super.doRender(a,x,y,z,yaw,partial);
  }
 });}
}
