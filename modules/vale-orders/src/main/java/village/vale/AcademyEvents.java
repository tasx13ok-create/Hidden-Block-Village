package village.vale;

import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class AcademyEvents {
    @SubscribeEvent public void actor(LivingEvent.LivingUpdateEvent e){
        EntityLivingBase actor=e.getEntityLiving();if(actor.world.isRemote||actor.ticksExisted%20!=0||!actor.getEntityData().hasUniqueId(AcademyCampaign.ACTOR_OWNER))return;
        EntityPlayerMP owner=actor.getServer().getPlayerList().getPlayerByUUID(actor.getEntityData().getUniqueId(AcademyCampaign.ACTOR_OWNER));
        boolean valid=owner!=null&&owner.dimension==0&&AcademyCampaign.eligible(owner);String kind=actor.getEntityData().getString(AcademyCampaign.ACTOR_KIND);
        if(valid){net.minecraft.nbt.NBTTagCompound s=AcademyCampaign.state(owner);valid=false;
            for(String key:kind.equals("training")?new String[]{"target"}:new String[]{"ambush0","ambush1"})if(s.hasUniqueId(key)&&s.getUniqueId(key).equals(actor.getUniqueID()))valid=true;
            if(kind.equals("ambush"))valid=valid&&"academy_ambush".equals(PlayerState.data(owner).getString("mission"))&&AcademyCampaign.ambushMatches(owner,actor)&&AcademyCampaign.course(owner).near(owner,12,34,80);
        }
        if(!valid){actor.setDead();actor.world.removeEntity(actor);}
    }
    @SubscribeEvent public void interact(PlayerInteractEvent.EntityInteract e){
        if(e.getWorld().isRemote||e.getHand()!=EnumHand.MAIN_HAND||!(e.getEntityPlayer() instanceof EntityPlayerMP))return;
        EntityPlayerMP p=(EntityPlayerMP)e.getEntityPlayer();
        if(AcademyCampaign.course(p).role(e.getTarget())!=null){e.setCanceled(true);AcademyCampaign.pickupDispatch(p,e.getTarget());AcademyCampaign.open(p,e.getTarget());}
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){
        if(e.phase==TickEvent.Phase.END&&!e.player.world.isRemote&&e.player instanceof EntityPlayerMP&&e.player.ticksExisted%2==0)AcademyCampaign.tick((EntityPlayerMP)e.player);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){
        if(e.player instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)e.player;if(p.dimension==0&&AcademyCampaign.course(p).active){AcademyCampaign.settle(p);AcademyCampaign.show(p);}}
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.player instanceof EntityPlayerMP&&e.player.dimension==0)AcademyCampaign.logout((EntityPlayerMP)e.player);}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.player instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)e.player;AcademyCampaign.clearSession(p);}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void protect(LivingAttackEvent e){
        if(e.getEntityLiving().world.isRemote)return;EntityLivingBase target=e.getEntityLiving();
        if(target.world.provider.getDimension()==0&&AcademyCourse.get((net.minecraft.world.WorldServer)target.world).role(target)!=null){e.setCanceled(true);return;}
        if(target.getEntityData().hasUniqueId(AcademyCampaign.ACTOR_OWNER)){
            Entity source=e.getSource().getTrueSource();
            if(!(source instanceof EntityPlayerMP)||!source.getUniqueID().equals(target.getEntityData().getUniqueId(AcademyCampaign.ACTOR_OWNER)))e.setCanceled(true);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void safeTraining(LivingDamageEvent e){
        if(e.getEntityLiving().world.isRemote||e.isCanceled())return;EntityLivingBase target=e.getEntityLiving();
        if("training".equals(target.getEntityData().getString(AcademyCampaign.ACTOR_KIND)))e.setAmount(Math.max(0,Math.min(e.getAmount(),target.getHealth()-2)));
        if(target instanceof EntityPlayerMP){Entity source=e.getSource().getTrueSource();if(source!=null&&"training".equals(source.getEntityData().getString(AcademyCampaign.ACTOR_KIND))&&source.getEntityData().hasUniqueId(AcademyCampaign.ACTOR_OWNER)&&source.getEntityData().getUniqueId(AcademyCampaign.ACTOR_OWNER).equals(target.getUniqueID()))e.setAmount(Math.max(0,Math.min(e.getAmount(),target.getHealth()-2)));}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void target(LivingSetAttackTargetEvent e){
        if(e.getTarget()!=null&&e.getEntityLiving() instanceof EntityLiving&&e.getEntityLiving().getEntityData().hasUniqueId(AcademyCampaign.ACTOR_OWNER)&&!e.getTarget().getUniqueID().equals(e.getEntityLiving().getEntityData().getUniqueId(AcademyCampaign.ACTOR_OWNER)))((EntityLiving)e.getEntityLiving()).setAttackTarget(null);
    }
}
