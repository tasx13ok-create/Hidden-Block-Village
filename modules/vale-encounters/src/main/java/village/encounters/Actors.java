package village.encounters;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.*;
import net.minecraft.init.*;
import net.minecraft.nbt.*;
import net.minecraft.network.datasync.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.*;
import village.vale.ArsenalAccess;

/** Original shared engines: three actual entity classes, persisted profile data. */
public final class Actors {
 private Actors(){}
 public static abstract class Actor extends EntityCreature {
  private static final DataParameter<String> PROFILE=EntityDataManager.createKey(Actor.class,DataSerializers.STRING);
  protected int cycle,cooldown=40,windup,guardTicks,healsLeft=3;protected String active="";protected Vec3d mark=Vec3d.ZERO;
  public Actor(World w){super(w);experienceValue=0;enablePersistence();}
  protected void entityInit(){super.entityInit();dataManager.register(PROFILE,"");}
  protected void applyEntityAttributes(){super.applyEntityAttributes();getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(24);getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(.25);getEntityAttribute(SharedMonsterAttributes.FOLLOW_RANGE).setBaseValue(24);}
  public String kind(){return this instanceof Boss?"boss":this instanceof Companion?"companion":"mob";}
  public Profiles.Profile profile(){Profiles.Profile p=Profiles.get(dataManager.get(PROFILE));return p!=null&&kind().equals(p.kind)?p:Profiles.first(kind());}
  public void configure(String key){Profiles.Profile p=Profiles.get(key);if(p==null||!p.kind.equals(kind()))throw new IllegalArgumentException("Profile kind mismatch");dataManager.set(PROFILE,p.key);getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(p.health);getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(p.speed);setHealth(getMaxHealth());if(this instanceof Boss)getEntityData().setInteger("valeEncounterTier",MathHelper.clamp(1+(int)((p.health-320)/100),1,5));setCustomNameTag(p.title);setAlwaysRenderNameTag(true);}
  public String profileKey(){return profile().key;}
  public int phase(){return this instanceof Boss?(getHealth()<=getMaxHealth()*.30?3:getHealth()<=getMaxHealth()*.65?2:1):1;}
  public boolean isGuarding(){return guardTicks>0;}public int telegraphTicks(){return windup;}
  protected boolean canDespawn(){return false;}protected boolean canDropLoot(){return false;}protected int getExperiencePoints(EntityPlayer p){return 0;}
  public void writeEntityToNBT(NBTTagCompound t){super.writeEntityToNBT(t);t.setInteger("EncounterSchema",1);t.setString("EncounterProfile",profileKey());t.setInteger("EncounterCycle",cycle);t.setInteger("EncounterCooldown",cooldown);t.setInteger("EncounterHeals",healsLeft);}
  public void readEntityFromNBT(NBTTagCompound t){super.readEntityFromNBT(t);float health=getHealth();Profiles.Profile p=Profiles.get(t.getString("EncounterProfile"));configure(p!=null&&kind().equals(p.kind)?p.key:Profiles.first(kind()).key);setHealth(Math.min(getMaxHealth(),Math.max(0,health)));cycle=MathHelper.clamp(t.getInteger("EncounterCycle"),0,1000000);cooldown=MathHelper.clamp(t.getInteger("EncounterCooldown"),30,200);healsLeft=MathHelper.clamp(t.getInteger("EncounterHeals"),0,3);windup=guardTicks=0;active="";}
  public boolean attackEntityFrom(DamageSource source,float amount){return super.attackEntityFrom(source,guardTicks>0?amount*.55F:amount);}
  protected void ring(Vec3d at,double radius,EnumParticleTypes particle){if(!(world instanceof WorldServer))return;for(int i=0;i<16;i++){double a=i*Math.PI/8;((WorldServer)world).spawnParticle(particle,at.x+Math.cos(a)*radius,at.y+.15,at.z+Math.sin(a)*radius,1,0.0,0.0,0.0,0.0);}}
  static boolean canLand(World w,BlockPos p,Entity entity){
   if(p.getY()<1||p.getY()>253||!w.isBlockLoaded(p)||!w.getWorldBorder().contains(p)||!w.getBlockState(p.down()).isSideSolid(w,p.down(),EnumFacing.UP))return false;
   net.minecraft.block.Block floor=w.getBlockState(p.down()).getBlock();if(floor==Blocks.MAGMA||floor==Blocks.CACTUS||floor==Blocks.FIRE)return false;
   AxisAlignedBB box=entity.getEntityBoundingBox().offset(p.getX()+.5-entity.posX,p.getY()-entity.posY,p.getZ()+.5-entity.posZ);
   return w.getCollisionBoxes(entity,box).isEmpty()&&!w.containsAnyLiquid(box)&&w.getBlockState(p).getBlock()!=Blocks.FIRE&&w.getBlockState(p.up()).getBlock()!=Blocks.FIRE;
  }
  static BlockPos landing(World w,BlockPos near,Entity e){for(int dy=1;dy>=-1;dy--)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){BlockPos p=near.add(dx,dy,dz);if(canLand(w,p,e))return p;}return null;}
 }
 public static class Mob extends Actor implements IMob {
  public Mob(World w){super(w);}
  protected void initEntityAI(){tasks.addTask(0,new EntityAISwimming(this));tasks.addTask(3,new EntityAIMoveTowardsTarget(this,.8,24));tasks.addTask(5,new EntityAIWanderAvoidWater(this,.65));tasks.addTask(6,new EntityAIWatchClosest(this,EntityPlayer.class,16));targetTasks.addTask(1,new EntityAINearestAttackableTarget<EntityPlayer>(this,EntityPlayer.class,true));}
  boolean valid(EntityLivingBase target){return target instanceof EntityPlayer&&target.isEntityAlive()&&!((EntityPlayer)target).isSpectator()&&!((EntityPlayer)target).capabilities.isCreativeMode&&target.world==world&&getDistanceSq(target)<=24*24;}
  public void onLivingUpdate(){super.onLivingUpdate();if(world.isRemote||!isEntityAlive())return;if(guardTicks>0)--guardTicks;EntityLivingBase target=getAttackTarget();if(!valid(target)){windup=0;active="";setAttackTarget(null);return;}
   if(windup>0){getNavigator().clearPath();motionX=motionZ=0;if(windup%5==0)ring(mark,active.equals("area")||active.equals("control")?profile().radius:1.1,EnumParticleTypes.SPELL_INSTANT);if(--windup==0){resolveMove(target);cooldown=Math.max(40,profile().cooldown-(phase()-1)*12);}return;}
   if(--cooldown<=0&&canEntityBeSeen(target)){String move=moveFor(cycle++);begin(move,target);}
  }
  public String moveFor(int number){Profiles.Profile p=profile();if(number%3==2)return phase()==3?p.phase3:phase()==2?p.phase2:p.secondary;return p.primary;}
  public void begin(String move,EntityLivingBase target){if(!valid(target))return;active=move;mark=target.getPositionVector();windup=profile().windup;if(move.equals("melee")&&getDistanceSq(target)>16){getNavigator().tryMoveToEntityLiving(target,1);windup=0;cooldown=15;return;}getNavigator().clearPath();ring(mark,profile().radius,EnumParticleTypes.VILLAGER_ANGRY);}
  public void resolveMove(EntityLivingBase target){String move=active;active="";if(!valid(target)||!canEntityBeSeen(target))return;Profiles.Profile p=profile();
   if(move.equals("guard")){guardTicks=40;ring(getPositionVector(),1,EnumParticleTypes.CRIT_MAGIC);return;}
   if(move.equals("heal")){if(healsLeft>0&&getHealth()<getMaxHealth()*.85F){healsLeft--;heal(this instanceof Boss?16:6);ring(getPositionVector(),1,EnumParticleTypes.HEART);}return;}
   if(move.equals("mobility")){Vec3d direction=target.getPositionVector().subtract(getPositionVector());if(direction.lengthSquared()<1)return;Vec3d at=getPositionVector().add(direction.normalize().scale(Math.max(0,Math.min(5,direction.length()-1.5))));BlockPos dest=landing(world,new BlockPos(at),this);if(dest!=null&&getDistanceSq(dest)<=49)setPositionAndUpdate(dest.getX()+.5,dest.getY(),dest.getZ()+.5);return;}
   if(move.equals("melee")){if(getDistanceSq(target)<=9)hurt(target,p.damage,false);return;}
   if(move.equals("ranged")){if(getDistanceSq(target)<=p.range*p.range&&target.getPositionVector().squareDistanceTo(mark)<=2.25)hurt(target,p.damage,false);return;}
   if(move.equals("area")||move.equals("control")){
    int count=0;for(EntityPlayer player:world.getEntitiesWithinAABB(EntityPlayer.class,new AxisAlignedBB(mark.x-p.radius,mark.y-2,mark.z-p.radius,mark.x+p.radius,mark.y+3,mark.z+p.radius)))if(count<6&&valid(player)&&canEntityBeSeen(player)&&player.getPositionVector().squareDistanceTo(mark)<=p.radius*p.radius){hurt(player,move.equals("control")?Math.max(1,p.damage*.5F):p.damage,move.equals("control"));count++;}
   }
  }
  public boolean hurt(EntityLivingBase target,float damage,boolean control){if(!valid(target)||!canEntityBeSeen(target))return false;float before=target.getHealth()+target.getAbsorptionAmount();boolean accepted=target.attackEntityFrom(DamageSource.causeMobDamage(this),Math.min(10,Math.max(1,damage)));boolean hit=accepted&&target.getHealth()+target.getAbsorptionAmount()<before;if(hit&&control)target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,40,0));return hit;}
 }
 public static final class Boss extends Mob {
  final BossInfoServer bar=new BossInfoServer(new TextComponentString("Encounter Warden"),BossInfo.Color.YELLOW,BossInfo.Overlay.PROGRESS);
  final Map<UUID,Float> contributors=new LinkedHashMap<>();final Map<UUID,Long> lastHit=new LinkedHashMap<>();boolean rewarded;
  public Boss(World w){super(w);setSize(.8F,2.2F);}
  public void onLivingUpdate(){super.onLivingUpdate();if(!world.isRemote){bar.setPercent(Math.max(0,Math.min(1,getHealth()/getMaxHealth())));bar.setName(new TextComponentString(profile().title+" | Phase "+phase()));}}
  public boolean attackEntityFrom(DamageSource source,float amount){float before=getHealth();boolean accepted=super.attackEntityFrom(source,amount);float actual=before-getHealth();if(accepted&&actual>0&&source.getTrueSource()instanceof EntityPlayer){UUID id=source.getTrueSource().getUniqueID();if(contributors.containsKey(id)||contributors.size()<64){contributors.put(id,Math.min(100000,contributors.getOrDefault(id,0F)+actual));lastHit.put(id,world.getTotalWorldTime());}}return accepted;}
  boolean award(){if(rewarded)return true;if(!(world instanceof WorldServer))return false;try{rewarded=village.vale.EncounterRewards.award((WorldServer)world,this,profileKey(),MathHelper.clamp(1+(int)((getMaxHealth()-320)/100),1,5),Collections.unmodifiableMap(contributors),Collections.unmodifiableMap(lastHit));return rewarded;}catch(RuntimeException|LinkageError failure){if(ticksExisted%200==0)System.err.println("Encounter reward retry pending for "+getUniqueID()+": "+failure.getClass().getSimpleName());return false;}}
  public void onDeath(DamageSource source){if(!world.isRemote)award();super.onDeath(source);}
  protected void onDeathUpdate(){if(!world.isRemote&&!award())return;super.onDeathUpdate();}
  public Map<UUID,Float> contributions(){return Collections.unmodifiableMap(contributors);}
  public void addTrackingPlayer(EntityPlayerMP p){super.addTrackingPlayer(p);bar.addPlayer(p);}public void removeTrackingPlayer(EntityPlayerMP p){super.removeTrackingPlayer(p);bar.removePlayer(p);}
  public void setDead(){super.setDead();if(bar!=null)bar.setVisible(false);}
  public void writeEntityToNBT(NBTTagCompound t){super.writeEntityToNBT(t);NBTTagList list=new NBTTagList();for(Map.Entry<UUID,Float> row:contributors.entrySet()){NBTTagCompound n=new NBTTagCompound();n.setUniqueId("Player",row.getKey());n.setFloat("ActualDamage",row.getValue());n.setLong("LastHit",lastHit.getOrDefault(row.getKey(),0L));list.appendTag(n);}t.setTag("EncounterContributors",list);t.setBoolean("EncounterRewarded",rewarded);}
  public void readEntityFromNBT(NBTTagCompound t){super.readEntityFromNBT(t);rewarded=t.getBoolean("EncounterRewarded");contributors.clear();lastHit.clear();NBTTagList list=t.getTagList("EncounterContributors",10);for(int i=0;i<Math.min(64,list.tagCount());i++){NBTTagCompound row=list.getCompoundTagAt(i);float damage=row.getFloat("ActualDamage");if(row.hasUniqueId("Player")&&Float.isFinite(damage)&&damage>0){contributors.put(row.getUniqueId("Player"),Math.min(100000,damage));lastHit.put(row.getUniqueId("Player"),Math.max(0,row.getLong("LastHit")));}}}
 }
 public static final class Companion extends Actor {
  UUID owner;long expires;int supportCharges=10;
  public Companion(World w){super(w);}
  protected void initEntityAI(){tasks.addTask(0,new EntityAISwimming(this));tasks.addTask(6,new EntityAIWatchClosest(this,EntityPlayer.class,12));}
  public void bind(EntityPlayerMP player,String key){configure(key);owner=player.getUniqueID();expires=world.getTotalWorldTime()+profile().duration;}
  public UUID ownerId(){return owner;}
  EntityPlayerMP owner(){EntityPlayer p=owner==null?null:world.getPlayerEntityByUUID(owner);return p instanceof EntityPlayerMP?(EntityPlayerMP)p:null;}
  public void onLivingUpdate(){super.onLivingUpdate();if(world.isRemote)return;EntityPlayerMP player=owner();long now=world.getTotalWorldTime();if(player==null||!player.isEntityAlive()||player.isSpectator()||player.world!=world||now>=expires||expires-now>1200){setDead();return;}
   if(getDistanceSq(player)>18*18){BlockPos p=landing(world,player.getPosition().add(2,0,2),this);if(p!=null)setPositionAndUpdate(p.getX()+.5,p.getY(),p.getZ()+.5);}else if(getDistanceSq(player)>9)getNavigator().tryMoveToEntityLiving(player,1);
   if(--cooldown>0)return;cooldown=80;Profiles.Profile p=profile();String role=p.companionRole;
   if(role.equals("mender")){if(supportCharges>0&&player.getHealth()<player.getMaxHealth()){supportCharges--;player.heal(2);ring(player.getPositionVector(),1,EnumParticleTypes.HEART);}return;}
   if(role.equals("warden")){if(supportCharges>0){supportCharges--;player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE,60,0));ring(player.getPositionVector(),1,EnumParticleTypes.CRIT_MAGIC);}return;}
   EntityLivingBase target=null;double distance=144;for(EntityLivingBase candidate:world.getEntitiesWithinAABB(EntityLivingBase.class,player.getEntityBoundingBox().grow(12)))if(candidate instanceof IMob&&ArsenalAccess.allowed(player,candidate)&&canEntityBeSeen(candidate)&&getDistanceSq(candidate)<distance){distance=getDistanceSq(candidate);target=candidate;}
   if(target==null)return;if(role.equals("scout")){if(ArsenalAccess.hit(player,target,1))target.addPotionEffect(new PotionEffect(MobEffects.GLOWING,40,0));return;}
   getNavigator().tryMoveToEntityLiving(target,1);if(getDistanceSq(target)<=9)ArsenalAccess.hit(player,target,Math.min(4,p.damage));else cooldown=20;
  }
  public void writeEntityToNBT(NBTTagCompound t){super.writeEntityToNBT(t);if(owner!=null)t.setUniqueId("EncounterOwner",owner);t.setLong("EncounterExpires",expires);t.setInteger("EncounterSupportCharges",supportCharges);}
  public void readEntityFromNBT(NBTTagCompound t){super.readEntityFromNBT(t);owner=t.hasUniqueId("EncounterOwner")?t.getUniqueId("EncounterOwner"):null;expires=t.getLong("EncounterExpires");supportCharges=MathHelper.clamp(t.getInteger("EncounterSupportCharges"),0,10);}
 }
}
