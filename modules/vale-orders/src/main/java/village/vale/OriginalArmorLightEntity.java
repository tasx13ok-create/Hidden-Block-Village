package village.vale;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.EntityRegistry;

/** Registered on both sides; instantiated only by the bounded client light manager. Never writes blocks. */
public final class OriginalArmorLightEntity extends Entity {
    public OriginalArmorLightEntity(World world){super(world);setSize(.01f,.01f);noClip=true;isImmuneToFire=true;setInvisible(true);}
    public static void register(Object mod){EntityRegistry.registerModEntity(new ResourceLocation("valeorders","armor_light"),OriginalArmorLightEntity.class,"armor_light",201,mod,32,20,false);}
    @Override protected void entityInit(){}
    @Override protected void readEntityFromNBT(NBTTagCompound data){}
    @Override protected void writeEntityToNBT(NBTTagCompound data){}
    @Override public boolean writeToNBTOptional(NBTTagCompound data){return false;}
    @Override public boolean canBeCollidedWith(){return false;}
    @Override public boolean canBePushed(){return false;}
    @Override public void onUpdate(){if(!world.isRemote){setDead();return;}prevPosX=posX;prevPosY=posY;prevPosZ=posZ;ticksExisted++;}
}
