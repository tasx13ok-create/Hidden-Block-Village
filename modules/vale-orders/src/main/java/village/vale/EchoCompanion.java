package village.vale;

import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.*;
import net.minecraft.util.*;
import net.minecraft.world.World;

/** One leased companion per owner. Unloaded older generations expire when reloaded. */
public final class EchoCompanion extends EntityCreature {
    private static final DataParameter<Integer> KIND=EntityDataManager.createKey(EchoCompanion.class,DataSerializers.VARINT);
    private UUID owner;private long generation,expires;private int strike,attackAnimation;
    public EchoCompanion(World w){super(w);setSize(.7F,1.1F);enablePersistence();experienceValue=0;}
    @Override public void notifyDataManagerChange(DataParameter<?> key){super.notifyDataManagerChange(key);if(KIND.equals(key))setSize(kind()==1?.9F:.7F,kind()==1?1.45F:kind()==2?.85F:1.1F);}
    @Override protected void entityInit(){super.entityInit();dataManager.register(KIND,0);}
    @Override protected void initEntityAI(){tasks.addTask(0,new EntityAISwimming(this));}
    @Override protected void applyEntityAttributes(){super.applyEntityAttributes();getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(30);getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(.30);}
    public int kind(){return dataManager.get(KIND);}
    public UUID ownerId(){return owner;}
    public void configure(EntityPlayerMP p,int kind,long lease,long end){owner=p.getUniqueID();generation=lease;expires=end;dataManager.set(KIND,net.minecraft.util.math.MathHelper.clamp(kind,0,3));getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(kind==1?50:kind==3?12:30);setHealth(getMaxHealth());setCustomNameTag(kind==3?"Echo Double":new String[]{"Reedling Scout","Cairnling Guard","Lantern Attendant"}[kind]);}
    @Override protected boolean canDespawn(){return false;}
    @Override public boolean canBeLeashedTo(net.minecraft.entity.player.EntityPlayer p){return false;}
    @Override public boolean attackEntityFrom(DamageSource source,float amount){if(owner!=null&&source.getTrueSource()!=null&&owner.equals(source.getTrueSource().getUniqueID()))return false;return super.attackEntityFrom(source,amount);}
    @Override public void onLivingUpdate(){
        super.onLivingUpdate();if(attackAnimation>0)attackAnimation--;if(world.isRemote||!isEntityAlive())return;
        EntityPlayerMP p=owner==null?null:world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
        if(p==null||!p.isEntityAlive()||p.dimension!=dimension||generation!=PlayerState.data(p).getLong("summonGeneration")||!PlayerState.data(p).hasUniqueId("summonId")||!getUniqueID().equals(PlayerState.data(p).getUniqueId("summonId"))||Adventures.now(p)>=expires||getDistanceSq(p)>32*32){setDead();return;}
        if(kind()==3){if(ticksExisted%20==0)for(EntityLivingBase e:Arts.near(p,8,false,3))if(e instanceof EntityLiving&&Combat.impact(e,DamageSource.causePlayerDamage(p),1))((EntityLiving)e).setAttackTarget(this);return;}
        if(ticksExisted%10==0){
            EntityLivingBase target=p.getLastAttackedEntity();
            if(target==null||!target.isEntityAlive()||p.getDistanceSq(target)>16*16||!OwnedSkills.allowed(p,target))target=p.getRevengeTarget();
            if(target==null||!target.isEntityAlive()||p.getDistanceSq(target)>16*16||!OwnedSkills.allowed(p,target))target=null;
            setAttackTarget(target);
            if(target!=null)getNavigator().tryMoveToEntityLiving(target,kind()==0?1.25:1);
            else if(getDistanceSq(p)>3*3)getNavigator().tryMoveToEntityLiving(p,1.15);
            else getNavigator().clearPath();
        }
        EntityLivingBase target=getAttackTarget();if(strike>0)strike--;
        if(target!=null&&strike==0&&getDistanceSq(target)<(kind()==2?7*7:2.5*2.5)&&canEntityBeSeen(target)&&OwnedSkills.allowed(p,target)){
            if(Combat.impact(target,new net.minecraft.util.EntityDamageSourceIndirect("vale_companion",this,p),kind()==1?4:3)){attackAnimation=8;world.setEntityState(this,(byte)4);if(kind()==2&&p.getHealth()<p.getMaxHealth())p.heal(1);}
            strike=kind()==0?24:40;
        }
    }
    public float attackPose(float partial){return Math.max(0,attackAnimation-partial)/8F;}
    @Override public void handleStatusUpdate(byte id){if(id==4)attackAnimation=8;else super.handleStatusUpdate(id);}
    @Override public void writeEntityToNBT(NBTTagCompound n){super.writeEntityToNBT(n);if(owner!=null)n.setUniqueId("ValeOwner",owner);n.setLong("ValeGeneration",generation);n.setLong("ValeExpires",expires);n.setInteger("ValeKind",kind());}
    @Override public void readEntityFromNBT(NBTTagCompound n){super.readEntityFromNBT(n);owner=n.hasUniqueId("ValeOwner")?n.getUniqueId("ValeOwner"):null;generation=n.getLong("ValeGeneration");expires=n.getLong("ValeExpires");dataManager.set(KIND,net.minecraft.util.math.MathHelper.clamp(n.getInteger("ValeKind"),0,3));}
}
