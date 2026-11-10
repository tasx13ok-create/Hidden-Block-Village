package village.vale;

import net.minecraft.entity.*;
import net.minecraft.entity.ai.*;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.init.MobEffects;
import net.minecraft.util.DamageSource;
import net.minecraft.world.*;

/** Original rare night patrols. Three archetypes; vanilla hostile spawn and damage hooks. */
public final class FieldRogue extends EntityMob {
    private static final DataParameter<Integer> KIND=EntityDataManager.createKey(FieldRogue.class,DataSerializers.VARINT);
    private static final DataParameter<Integer> CAST=EntityDataManager.createKey(FieldRogue.class,DataSerializers.VARINT);
    private int cast=100,windup;private double markX,markY,markZ;
    public FieldRogue(World w){super(w);experienceValue=8;}
    @Override protected void entityInit(){super.entityInit();dataManager.register(KIND,0);dataManager.register(CAST,0);}
    @Override protected void initEntityAI(){tasks.addTask(0,new EntityAISwimming(this));tasks.addTask(2,new EntityAIAttackMelee(this,1,false));tasks.addTask(5,new EntityAIWanderAvoidWater(this,.8));tasks.addTask(6,new EntityAIWatchClosest(this,EntityPlayer.class,12));targetTasks.addTask(1,new EntityAIHurtByTarget(this,false));targetTasks.addTask(2,new EntityAINearestAttackableTarget<>(this,EntityPlayer.class,true));}
    @Override protected void applyEntityAttributes(){super.applyEntityAttributes();getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(28);getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(3);getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(.25);getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(4);}
    public int kind(){return dataManager.get(KIND);}
    @Override public boolean getCanSpawnHere(){return world.provider.getDimension()==0&&super.getCanSpawnHere();}
    public void configure(int kind){dataManager.set(KIND,net.minecraft.util.math.MathHelper.clamp(kind,0,2));getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(28+8*kind());getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(3+kind());getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(4+2*kind());setHealth(getMaxHealth());setCustomNameTag(new String[]{"Road Skirmisher","Marsh Binder","Ash Lookout"}[kind()]);}
    @Override public IEntityLivingData onInitialSpawn(DifficultyInstance d,IEntityLivingData n){IEntityLivingData result=super.onInitialSpawn(d,n);configure(rand.nextInt(3));return result;}
    public float castPose(){int ticks=dataManager.get(CAST);return ticks>0?1-ticks/16F:0;}
    public boolean isCasting(){return dataManager.get(CAST)>0;}
    public void beginCast(EntityLivingBase target){setAttackTarget(target);markX=target.posX;markY=target.posY;markZ=target.posZ;windup=16;cast=100;dataManager.set(CAST,windup);}
    @Override public boolean attackEntityAsMob(Entity target){return windup==0&&super.attackEntityAsMob(target);}
    @Override public void onLivingUpdate(){super.onLivingUpdate();if(world.isRemote||!isEntityAlive())return;EntityLivingBase target=getAttackTarget();
        if(windup>0){
            dataManager.set(CAST,windup);getNavigator().clearPath();motionX=motionZ=0;
            if(windup%4==0)for(int i=0;i<12;i++){double a=i*Math.PI/6;((WorldServer)world).spawnParticle(net.minecraft.util.EnumParticleTypes.VILLAGER_ANGRY,markX+Math.cos(a)*1.5,markY+.1,markZ+Math.sin(a)*1.5,1,0.0,0.0,0.0,0.0);}
            if(--windup==0){dataManager.set(CAST,0);if(target!=null&&target.isEntityAlive()&&canEntityBeSeen(target)&&Math.abs(target.posY-markY)<3&&(target.posX-markX)*(target.posX-markX)+(target.posZ-markZ)*(target.posZ-markZ)<2.25&&(!(target instanceof EntityPlayer)||!((EntityPlayer)target).isSpectator()&&!((EntityPlayer)target).capabilities.isCreativeMode)&&Combat.impact(target,DamageSource.causeMobDamage(this),kind()==2?5:3)&&kind()==1)target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,40,0));}
            return;
        }
        if(target!=null&&target.isEntityAlive()&&canEntityBeSeen(target)&&getDistanceSq(target)<8*8&&--cast<=0)beginCast(target);
    }
    @Override public void writeEntityToNBT(NBTTagCompound n){super.writeEntityToNBT(n);n.setInteger("ValeKind",kind());}
    @Override public void readEntityFromNBT(NBTTagCompound n){super.readEntityFromNBT(n);dataManager.set(KIND,net.minecraft.util.math.MathHelper.clamp(n.getInteger("ValeKind"),0,2));}
}
