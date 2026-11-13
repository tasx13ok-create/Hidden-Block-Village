package village.vale;

import java.util.*;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

/** Small local witnessed reactions to settled actions; never reads hidden inventory or changes reputation globally. */
public final class WorldReactions {
    private WorldReactions(){}
    public static int power(EntityPlayerMP player,String kind,int intensity){
        if(player.world.isRemote||!player.isEntityAlive()||player.isSpectator()||intensity<1||intensity>3||!Arrays.asList("lightning_armor","lariat","guillotine_drop").contains(kind))return 0;
        long now=Adventures.now(player);List<EntityVillager> villagers=player.world.getEntitiesWithinAABB(EntityVillager.class,player.getEntityBoundingBox().grow(16));
        villagers.sort(Comparator.comparingDouble(player::getDistanceSq));int reacted=0,spoken=0;
        boolean training=player.dimension==0&&AcademyCourse.get((WorldServer)player.world).near(player,0,16,48);
        for(EntityVillager villager:villagers){
            if(reacted>=8)break;if(!villager.isEntityAlive()||player.getDistanceSq(villager)>256)continue;
            BlockPos a=player.getPosition(),b=villager.getPosition();if(!player.world.isAreaLoaded(new BlockPos(Math.min(a.getX(),b.getX()),Math.min(a.getY(),b.getY()),Math.min(a.getZ(),b.getZ())),new BlockPos(Math.max(a.getX(),b.getX()),Math.max(a.getY(),b.getY())+2,Math.max(a.getZ(),b.getZ())))||!villager.canEntityBeSeen(player))continue;
            NBTTagCompound data=villager.getEntityData(),memory=data.getCompoundTag("ValePowerReaction");
            if(data.hasKey("ValePowerReaction",10)&&memory.getInteger("schema")!=1)continue;
            if(now<memory.getLong("ready"))continue;
            memory.setInteger("schema",1);memory.setString("kind",kind);memory.setLong("ready",now+200);memory.setLong("expires",now+1200);memory.setInteger("intensity",intensity);memory.setUniqueId("witnessedPlayer",player.getUniqueID());data.setTag("ValePowerReaction",memory);
            villager.getLookHelper().setLookPositionWithEntity(player,30,30);reacted++;
            if(!training&&!villager.isAIDisabled()){
                Vec3d away=villager.getPositionVector().subtract(player.getPositionVector());away=new Vec3d(away.x,0,away.z);if(away.lengthSquared()<.01)away=new Vec3d(1,0,0);away=away.normalize().scale(4);
                BlockPos destination=new BlockPos(villager.posX+away.x,villager.posY,villager.posZ+away.z);
                if(player.world.isBlockLoaded(destination)&&player.world.getWorldBorder().contains(destination)&&player.world.getBlockState(destination.down()).getMaterial().isSolid()&&player.world.getCollisionBoxes(villager,villager.getEntityBoundingBox().offset(away)).isEmpty())villager.getNavigator().tryMoveToXYZ(destination.getX()+.5,destination.getY(),destination.getZ()+.5,1.15);
            }
            if(spoken++<2){
                String name=villager.hasCustomName()?villager.getCustomNameTag():"Villager";
                player.sendMessage(new TextComponentString(name+": "+(training?"Keep that chakra controlled. Show me a clean technique!":"That chakra... give them room!")));
                player.world.playSound(null,villager.posX,villager.posY,villager.posZ,training?SoundEvents.ENTITY_VILLAGER_YES:SoundEvents.ENTITY_VILLAGER_NO,SoundCategory.NEUTRAL,.55F,training?1.1F:.85F);
            }
        }
        return reacted;
    }
}
