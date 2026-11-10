package village.vale;

import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.*;

public final class FieldProjectile extends EntityThrowable {
    private static final DataParameter<Integer> KIND=EntityDataManager.createKey(FieldProjectile.class,DataSerializers.VARINT);
    public FieldProjectile(World w){super(w);}
    public FieldProjectile(World w,EntityPlayerMP p,int kind){super(w,p);dataManager.set(KIND,kind);}
    @Override protected void entityInit(){super.entityInit();dataManager.register(KIND,0);}
    public int kind(){return dataManager.get(KIND);}
    @Override public void onUpdate(){super.onUpdate();if(!world.isRemote&&ticksExisted>80)setDead();}
    @Override protected void onImpact(RayTraceResult result){
        if(world.isRemote)return;EntityLivingBase owner=getThrower();if(!(owner instanceof EntityPlayerMP)||!owner.isEntityAlive()){setDead();return;}EntityPlayerMP p=(EntityPlayerMP)owner;
        if(kind()==2){
            for(EntityLivingBase e:world.getEntitiesWithinAABB(EntityLivingBase.class,getEntityBoundingBox().grow(3)))if(OwnedSkills.allowed(p,e)&&p.canEntityBeSeen(e)&&Combat.impact(e,DamageSource.causeThrownDamage(this,p),1)){e.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,60,0));e.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,40,0));}
            ((WorldServer)world).spawnParticle(EnumParticleTypes.SMOKE_LARGE,posX,posY,posZ,20,1,.5,1,.02);
        }else if(result.entityHit instanceof EntityLivingBase&&OwnedSkills.allowed(p,(EntityLivingBase)result.entityHit))Combat.impact((EntityLivingBase)result.entityHit,DamageSource.causeThrownDamage(this,p),kind()==0?3:6);
        setDead();
    }
    @Override public void writeEntityToNBT(NBTTagCompound n){super.writeEntityToNBT(n);n.setInteger("ValeKind",kind());}
    @Override public void readEntityFromNBT(NBTTagCompound n){super.readEntityFromNBT(n);dataManager.set(KIND,net.minecraft.util.math.MathHelper.clamp(n.getInteger("ValeKind"),0,2));}
}
