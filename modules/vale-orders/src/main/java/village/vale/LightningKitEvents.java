package village.vale;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.*;

/** Native mode lifecycle, protected trial actors and deferred accepted-contact counter. */
public final class LightningKitEvents {
    private static final Map<UUID,CounterHit> HITS=new HashMap<>();
    private static final Map<UUID,LivingDeathEvent> DEATHS=new HashMap<>();
    private static final class CounterHit {
        final EntityPlayerMP owner;final EntityLivingBase attacker;final DamageSource source;final float before;final LivingAttackEvent attack;final long at;final net.minecraft.world.World world;final int dimension;LivingHurtEvent hurt;LivingDamageEvent damage;
        CounterHit(EntityPlayerMP p,EntityLivingBase attacker,LivingAttackEvent e){owner=p;this.attacker=attacker;source=e.getSource();before=p.getHealth()+p.getAbsorptionAmount();attack=e;at=Adventures.now(p);world=p.world;dimension=p.dimension;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void attack(LivingAttackEvent e){
        EntityLivingBase entity=e.getEntityLiving();if(entity.world.isRemote)return;
        if(entity.getEntityData().hasUniqueId(LightningTrial.OWNER)){
            Entity attacker=e.getSource().getTrueSource();if(!(attacker instanceof EntityPlayerMP)||!attacker.getUniqueID().equals(entity.getEntityData().getUniqueId(LightningTrial.OWNER)))e.setCanceled(true);
        }
        if(entity instanceof EntityPlayerMP&&!e.isCanceled()){
            EntityPlayerMP p=(EntityPlayerMP)entity;Entity source=e.getSource().getTrueSource();
            if(LightningKit.active(p)&&LightningKit.state(p).getLong("counterUntil")>Adventures.now(p)&&source instanceof EntityLivingBase&&e.getSource().getImmediateSource()==source&&!e.getSource().isProjectile()&&OwnedSkills.allowed(p,(EntityLivingBase)source)&&p.canEntityBeSeen(source)&&p.getDistanceSq(source)<=16){
                if(HITS.size()>=256)HITS.clear();HITS.put(p.getUniqueID(),new CounterHit(p,(EntityLivingBase)source,e));
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void hurt(LivingHurtEvent e){CounterHit hit=HITS.get(e.getEntityLiving().getUniqueID());if(hit!=null&&hit.owner==e.getEntityLiving()&&hit.source==e.getSource())hit.hurt=e;if(!e.isCanceled()&&e.getEntityLiving() instanceof EntityPlayerMP&&!e.getEntityLiving().world.isRemote&&LightningKit.active((EntityPlayerMP)e.getEntityLiving()))e.setAmount(e.getAmount()*.80F);}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void damage(LivingDamageEvent e){CounterHit hit=HITS.get(e.getEntityLiving().getUniqueID());if(hit!=null&&hit.owner==e.getEntityLiving()&&hit.source==e.getSource())hit.damage=e;}
    @SubscribeEvent public void server(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)flush();}
    public static void flush(){
        for(LivingDeathEvent e:new ArrayList<>(DEATHS.values())){DEATHS.remove(e.getEntityLiving().getUniqueID(),e);if(!e.isCanceled()&&e.getEntityLiving().getHealth()<=0)close((EntityPlayerMP)e.getEntityLiving());}
        for(CounterHit hit:new ArrayList<>(HITS.values())){
            HITS.remove(hit.owner.getUniqueID(),hit);EntityPlayerMP p=hit.owner;long now=Adventures.now(p);NBTTagCompound s=LightningKit.state(p);
            if(now<hit.at||now-hit.at>3||p.world!=hit.world||p.dimension!=hit.dimension||hit.attacker.world!=hit.world||hit.attack.isCanceled()||hit.hurt==null||hit.hurt.isCanceled()||hit.damage==null||hit.damage.isCanceled()||hit.damage.getAmount()<0||p.getHealth()+p.getAbsorptionAmount()>=hit.before||!LightningKit.active(p)||s.getLong("counterUntil")<now||!OwnedSkills.allowed(p,hit.attacker)||!p.canEntityBeSeen(hit.attacker)||p.getDistanceSq(hit.attacker)>16)continue;
            s.removeTag("counterUntil");
            if(Combat.impact(hit.attacker,DamageSource.causePlayerDamage(p),8)){Arts.push(p,hit.attacker,.8);LightningKit.burst(p,18);Adventures.save(p);}
        }
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&!e.player.world.isRemote&&e.player instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)e.player;LightningKit.tick(p);if(p.ticksExisted%10==0)LightningTrial.tick(p);}}
    @SubscribeEvent public void actor(LivingEvent.LivingUpdateEvent e){EntityLivingBase actor=e.getEntityLiving();if(!actor.world.isRemote&&actor.ticksExisted%20==0&&!LightningTrial.actorValid(actor)){actor.setDead();actor.world.removeEntity(actor);}}
    @SubscribeEvent(priority=EventPriority.LOWEST)public void target(LivingSetAttackTargetEvent e){if(e.getTarget()!=null&&e.getEntityLiving() instanceof EntityLiving&&e.getEntityLiving().getEntityData().hasUniqueId(LightningTrial.OWNER)&&!e.getTarget().getUniqueID().equals(e.getEntityLiving().getEntityData().getUniqueId(LightningTrial.OWNER)))((EntityLiving)e.getEntityLiving()).setAttackTarget(null);}
    @SubscribeEvent public void fall(LivingFallEvent e){if(e.getEntityLiving() instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)e.getEntityLiving();if(LightningKit.active(p)&&LightningKit.state(p).getLong("slamUntil")>=Adventures.now(p))e.setDistance(0);}}
    private static void close(EntityPlayerMP p){HITS.remove(p.getUniqueID());LightningKit.end(p);LightningTrial.clear(p);Adventures.save(p);}
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.player instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)e.player;LightningKit.end(p);}}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.player instanceof EntityPlayerMP)close((EntityPlayerMP)e.player);}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.player instanceof EntityPlayerMP)close((EntityPlayerMP)e.player);}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void death(LivingDeathEvent e){if(!e.getEntityLiving().world.isRemote&&e.getEntityLiving() instanceof EntityPlayerMP){if(DEATHS.size()>=256)DEATHS.clear();DEATHS.put(e.getEntityLiving().getUniqueID(),e);}}
}
