package village.vale;

import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.*;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Temporary ground effect, never places blocks or loads chunks. */
public final class RuneField extends Entity {
    private static final DataParameter<Integer> KIND=EntityDataManager.createKey(RuneField.class,DataSerializers.VARINT);
    private UUID owner;private long expires;
    public RuneField(World w){super(w);setSize(.1F,.1F);noClip=true;}
    @Override protected void entityInit(){dataManager.register(KIND,0);}
    public void configure(EntityPlayerMP p,int kind,Vec3d at){owner=p.getUniqueID();dataManager.set(KIND,MathHelper.clamp(kind,0,8));expires=Adventures.now(p)+100;setPosition(at.x,at.y+.1,at.z);}
    @Override public void onUpdate(){super.onUpdate();if(world.isRemote)return;
        EntityPlayerMP p=owner==null?null:world.getMinecraftServer().getPlayerList().getPlayerByUUID(owner);
        if(p==null||!p.isEntityAlive()||p.dimension!=dimension||Adventures.now(p)>=expires||p.getDistanceSq(this)>24*24){setDead();return;}
        if(ticksExisted%10==0)for(int i=0;i<16;i++){double a=i*Math.PI/8;((WorldServer)world).spawnParticle(EnumParticleTypes.END_ROD,posX+Math.cos(a)*3,posY,posZ+Math.sin(a)*3,1,0.0,0.0,0.0,0.0);}
        if(ticksExisted%20==0){int hits=0;for(EntityLivingBase e:world.getEntitiesWithinAABB(EntityLivingBase.class,getEntityBoundingBox().grow(3,2,3))){
            if(e!=p&&e.isEntityAlive()&&getDistanceSq(e)<3.5*3.5&&p.canEntityBeSeen(e)&&Arts.hit(p,e,Order.values()[dataManager.get(KIND)],2,new net.minecraft.util.EntityDamageSourceIndirect("vale_seal",this,p))&&++hits>=4)break;
        }}
    }
    @Override protected void readEntityFromNBT(NBTTagCompound n){owner=n.hasUniqueId("owner")?n.getUniqueId("owner"):null;expires=n.getLong("expires");dataManager.set(KIND,MathHelper.clamp(n.getInteger("kind"),0,8));}
    @Override protected void writeEntityToNBT(NBTTagCompound n){if(owner!=null)n.setUniqueId("owner",owner);n.setLong("expires",expires);n.setInteger("kind",dataManager.get(KIND));}
}
