package village.vale.expedition;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.*;
import village.vale.ValeOrders;

/** Common-side only: defer observations until all native cancellation listeners finish. */
@Mod.EventBusSubscriber(modid=ValeOrders.ID)
public final class GateEvents {
    private GateEvents(){}
    private static final Map<UUID,PlayerInteractEvent.RightClickBlock> MARKERS=new HashMap<>();
    private static final class Death {final LivingDeathEvent event;int ticks;Death(LivingDeathEvent event){this.event=event;}}
    private static final Map<UUID,Death> DEATHS=new HashMap<>();
    private static boolean tagged(Entity entity){return entity!=null&&entity.getEntityData().getInteger("valeGateActorSchema")==1&&entity.getEntityData().hasUniqueId(GateRuntime.OWNER);}
    private static EntityPlayerMP owner(Entity entity){return entity.getServer().getPlayerList().getPlayerByUUID(entity.getEntityData().getUniqueId(GateRuntime.OWNER));}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public static void marker(PlayerInteractEvent.RightClickBlock event){
        if(event.getEntityPlayer() instanceof EntityPlayerMP&&event.getHand()==net.minecraft.util.EnumHand.MAIN_HAND){
            UUID id=event.getEntityPlayer().getUniqueID();if(MARKERS.size()<256||MARKERS.containsKey(id))MARKERS.putIfAbsent(id,event);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public static void death(LivingDeathEvent event){
        if(!event.getEntityLiving().world.isRemote&&(tagged(event.getEntityLiving())||event.getEntityLiving() instanceof EntityPlayerMP)&&DEATHS.size()<512)DEATHS.putIfAbsent(event.getEntityLiving().getUniqueID(),new Death(event));
    }
    /** Mission couriers are protected actors, never random vanilla merchants. */
    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true)public static void interact(PlayerInteractEvent.EntityInteract event){
        Entity actor=event.getTarget();
        if(!actor.world.isRemote&&actor instanceof net.minecraft.entity.passive.EntityVillager&&tagged(actor)&&actor.getEntityData().getBoolean("valeGateFriendly"))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true)public static void attack(LivingAttackEvent event){
        if(event.getEntityLiving().world.isRemote)return;Entity victim=event.getEntityLiving(),source=event.getSource().getTrueSource();
        if(tagged(source)){
            EntityPlayerMP owner=owner(source);boolean ownVictim=owner!=null&&(victim==owner||tagged(victim)&&GateRuntime.owned(owner,victim));
            if(!ownVictim||owner==null||!GateRuntime.readable(owner)||!GateRuntime.owned(owner,source))event.setCanceled(true);
        }
        if(tagged(victim)){
            EntityPlayerMP owner=owner(victim);boolean friendly=victim.getEntityData().getBoolean("valeGateFriendly");
            boolean permitted=owner!=null&&GateRuntime.readable(owner)&&GateRuntime.owned(owner,victim)
                &&(friendly?(source==null||tagged(source)&&GateRuntime.owned(owner,source)):source==owner);
            if(!permitted)event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void target(LivingSetAttackTargetEvent event){
        if(tagged(event.getEntityLiving())&&event.getEntityLiving() instanceof EntityLiving){EntityPlayerMP owner=owner(event.getEntityLiving());
            if(GateRules.clearTarget(event.getTarget()!=null,owner!=null,owner!=null&&GateRuntime.readable(owner)&&GateRuntime.owned(owner,event.getEntityLiving()),event.getTarget()==owner))((EntityLiving)event.getEntityLiving()).setAttackTarget(null);}
    }
    @SubscribeEvent public static void actorTick(LivingEvent.LivingUpdateEvent event){
        EntityLivingBase actor=event.getEntityLiving();if(actor.world.isRemote||!tagged(actor)||actor.ticksExisted%20!=0)return;
        EntityPlayerMP owner=owner(actor);if(owner==null||!owner.isEntityAlive()||!GateRuntime.readable(owner)||!GateRuntime.owned(owner,actor)){actor.setDead();actor.world.removeEntity(actor);}
    }
    @SubscribeEvent public static void teleport(EnderTeleportEvent event){if(event.getEntityLiving() instanceof EntityPlayerMP&&!event.isCanceled())GateRuntime.teleported((EntityPlayerMP)event.getEntityLiving());}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&event.player instanceof EntityPlayerMP&&!event.player.world.isRemote)GateRuntime.tick((EntityPlayerMP)event.player);}
    @SubscribeEvent public static void server(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END)flush();}
    public static void flush(){
        for(PlayerInteractEvent.RightClickBlock event:new ArrayList<>(MARKERS.values())){
            MARKERS.remove(event.getEntityPlayer().getUniqueID(),event);
            if(!event.isCanceled()&&event.getUseBlock()!=Event.Result.DENY&&event.getUseItem()!=Event.Result.DENY&&event.getEntityPlayer() instanceof EntityPlayerMP&&event.getEntityPlayer().world==event.getWorld())GateRuntime.marker((EntityPlayerMP)event.getEntityPlayer(),event.getPos());
        }
        for(Death pending:new ArrayList<>(DEATHS.values())){
            LivingDeathEvent event=pending.event;EntityLivingBase actor=event.getEntityLiving();
            if(!event.isCanceled()&&actor.getHealth()<=0&&!(actor instanceof EntityPlayerMP)&&!actor.isDead&&actor.deathTime==0&&++pending.ticks<3)continue;
            DEATHS.remove(actor.getUniqueID(),pending);if(event.isCanceled()||actor.getHealth()>0)continue;
            if(actor instanceof EntityPlayerMP){GateRuntime.suspend((EntityPlayerMP)actor);continue;}
            if(tagged(actor)&&event.getSource().getTrueSource() instanceof EntityPlayerMP)GateRuntime.kill((EntityPlayerMP)event.getSource().getTrueSource(),actor,false,actor.isDead||actor.deathTime>0);
        }
    }
    static boolean pendingDeath(UUID actor){return DEATHS.containsKey(actor);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.player instanceof EntityPlayerMP){MARKERS.remove(event.player.getUniqueID());GateRuntime.suspend((EntityPlayerMP)event.player);}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.player instanceof EntityPlayerMP)GateRuntime.suspend((EntityPlayerMP)event.player);}
}
