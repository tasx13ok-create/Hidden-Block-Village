package village.vale.arsenal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import village.vale.ArsenalAccess;
import village.vale.PlayerState;

/** Server-only, transient locks. A lock never freezes or steers its victim. */
public final class ClawCombat {
    private static final ArsenalSpec SPEC=ArsenalSpec.CRESCENT_TALONS;
    private static final Map<UUID,Lock> LOCKS=new HashMap<>();
    private static final ThreadLocal<PotionAttempt> POTION=new ThreadLocal<>();
    private static final Map<UUID,Map<UUID,Bleed>> BLEEDS=new HashMap<>();
    private ClawCombat() {}

    /** Shared native-use and optional middle-click action. Clients supply no target. */
    public static boolean use(EntityPlayerMP p,ItemStack stack) {
        if(!held(p,stack))return false;
        if(p.isSneaking()) {
            boolean removed=LOCKS.remove(p.getUniqueID())!=null;
            if(removed)tell(p,"Crescent lock released.");
            return removed;
        }
        Lock lock=LOCKS.get(p.getUniqueID());
        if(lock!=null) {
            if(validLock(p,lock)&&lock.winding)return false;
            if(validLock(p,lock)&&ClawRules.lockOpen(ArsenalAccess.now(p),lock.created))return start(p,lock);
            LOCKS.remove(p.getUniqueID());
        }
        return acquire(p,stack);
    }

    /** Native left-click becomes the locked lunge; ordinary melee stays native otherwise. */
    static boolean attack(EntityPlayerMP p,ItemStack stack,Entity target) {
        Lock lock=LOCKS.get(p.getUniqueID());
        if(lock==null)return false;
        if(!validLock(p,lock)||(!lock.winding&&!ClawRules.lockOpen(ArsenalAccess.now(p),lock.created))) {
            LOCKS.remove(p.getUniqueID());return false;
        }
        if(lock.winding)return true;
        if(target.getUniqueID().equals(lock.target)) {start(p,lock);return true;}
        return false;
    }

    private static boolean acquire(EntityPlayerMP p,ItemStack stack) {
        if(!ArsenalLogic.ready(p,stack,SPEC,true))return false;
        EntityLivingBase target=ArsenalAccess.aimedTarget(p,ClawRules.RANGE);
        if(!targetable(p,target)) {tell(p,"Aim at a visible permitted target within five blocks.");return false;}
        boolean rear=rear(p,target),used=false,reserved=false;
        ArsenalLogic.state(p).setBoolean("busy",true);
        try {
            if(MinecraftForge.EVENT_BUS.post(new ClawLockEvent(p,target,rear))||!held(p,stack)||!targetable(p,target))return false;
            if(!held(p,stack)||!targetable(p,target)||PlayerState.resolve(p)<SPEC.cost)return false;
            PlayerState.setResolve(p,PlayerState.resolve(p)-SPEC.cost);reserved=true;
            // One native application, and its final applicability result, authorizes
            // the rear effect. Existing blindness must not hide a new DENY result.
            if(rear&&!blind(target))return false;
            if(!held(p,stack)||!targetable(p,target))return false;
            LOCKS.put(p.getUniqueID(),new Lock(target,stack,p.dimension,ArsenalAccess.now(p),rear));
            used=true;ArsenalLogic.commit(p,stack,SPEC);
            cue(p,target,EnumParticleTypes.SPELL_WITCH,8);
            tell(p,(rear?"Rear lock: blinded. ":"Target locked. ")+"Use or attack within five seconds; the lunge has a one-second tell.");
            return true;
        } finally {
            ArsenalLogic.state(p).removeTag("busy");
            if(reserved&&!used)PlayerState.setResolve(p,Math.min(100,PlayerState.resolve(p)+SPEC.cost));
        }
    }

    private static boolean blind(EntityLivingBase target) {
        PotionEffect effect=new PotionEffect(MobEffects.BLINDNESS,ClawRules.BLIND_TICKS,0);
        PotionAttempt attempt=new PotionAttempt(target,effect),previous=POTION.get();
        POTION.set(attempt);
        try {target.addPotionEffect(effect);}
        finally {if(previous==null)POTION.remove();else POTION.set(previous);}
        PotionEffect applied=target.getActivePotionEffect(MobEffects.BLINDNESS);
        return attempt.event!=null&&attempt.event.getResult()!=Event.Result.DENY
                &&applied!=null&&applied.getDuration()>=ClawRules.BLIND_TICKS;
    }
    static void observe(PotionEvent.PotionApplicableEvent event) {
        PotionAttempt attempt=POTION.get();
        if(attempt!=null&&attempt.target==event.getEntityLiving()&&attempt.effect==event.getPotionEffect())attempt.event=event;
    }
    private static boolean start(EntityPlayerMP p,Lock lock) {
        if(lock.winding)return false;
        EntityLivingBase target=target(p,lock.target);
        if(!validLock(p,lock)||!ClawRules.lockOpen(ArsenalAccess.now(p),lock.created)||!targetable(p,target)) {
            LOCKS.remove(p.getUniqueID());return false;
        }
        lock.winding=true;lock.started=ArsenalAccess.now(p);lock.strike=target.getPositionVector();
        cue(p,target,EnumParticleTypes.CRIT_MAGIC,12);
        p.world.playSound(null,target.posX,target.posY,target.posZ,SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,SoundCategory.PLAYERS,.7f,.65f);
        tell(p,"Crescent lunge committed: impact in one second.");
        return true;
    }

    static void tick(EntityPlayerMP p) {
        long now=ArsenalAccess.now(p);
        Lock lock=LOCKS.get(p.getUniqueID());
        if(lock!=null) {
            EntityLivingBase target=target(p,lock.target);
            if(!validLock(p,lock)||!targetable(p,target)||now<lock.created
                    ||(!lock.winding&&!ClawRules.lockOpen(now,lock.created))
                    ||(lock.winding&&(now<lock.started||now-lock.started>ClawRules.WINDUP_TICKS+2)))LOCKS.remove(p.getUniqueID());
            else if(lock.winding) {
                if(ClawRules.windupDue(now,lock.started)) {
                    LOCKS.remove(p.getUniqueID()); // Consume before native move/damage callbacks.
                    strike(p,lock,target);
                } else if((now-lock.started)%5==0)cue(p,target,EnumParticleTypes.CRIT,3);
            }
        }
        Map<UUID,Bleed> bleeds=BLEEDS.get(p.getUniqueID());
        if(bleeds==null)return;
        // Snapshot iteration tolerates a protection callback clearing the owner state.
        for(Bleed bleed:new ArrayList<>(bleeds.values())) {
            EntityLivingBase target=target(p,bleed.target);
            if(!held(p,bleed.stack)||p.dimension!=bleed.dimension||!ArsenalAccess.allowed(p,target)
                    ||target.isEntityInvulnerable(DamageSource.causePlayerDamage(p))||p.getDistanceSq(target)>24*24
                    ||now<bleed.lastHit||now-bleed.lastHit>ClawRules.BLEED_TICKS) {
                bleeds.remove(bleed.target);continue;
            }
            if(bleed.stacks>0&&now>bleed.until)bleed.stacks=0;
            if(bleed.stacks>0&&now>=bleed.next) {
                bleed.next=now+20;
                if(!ArsenalAccess.hit(p,target,bleed.stacks))bleeds.remove(bleed.target);
                else cue(p,target,EnumParticleTypes.DAMAGE_INDICATOR,Math.min(3,bleed.stacks));
            }
        }
        if(bleeds.isEmpty())BLEEDS.remove(p.getUniqueID(),bleeds);
    }

    private static void strike(EntityPlayerMP p,Lock lock,EntityLivingBase target) {
        if(!held(p,lock.stack)||!targetable(p,target)||ClawRules.dodged(target.getPositionVector().squareDistanceTo(lock.strike))) {
            tell(p,"Crescent lunge missed.");return;
        }
        Vec3d delta=lock.strike.subtract(p.getPositionVector());
        double horizontal=Math.sqrt(delta.x*delta.x+delta.z*delta.z);
        if(!Double.isFinite(horizontal)||Math.abs(delta.y)>1.25)return;
        double distance=Math.max(0,Math.min(3.5,horizontal-1.5));
        if(distance>0) {
            Vec3d step=new Vec3d(delta.x/horizontal*distance,0,delta.z/horizontal*distance);
            Vec3d destination=p.getPositionVector().add(step);
            AxisAlignedBB swept=p.getEntityBoundingBox().expand(step.x,0,step.z);
            if(!p.world.isBlockLoaded(new BlockPos(destination))||!p.world.getWorldBorder().contains(new BlockPos(destination))
                    ||!p.world.getCollisionBoxes(p,swept).isEmpty()||p.world.containsAnyLiquid(swept))return;
            p.move(MoverType.SELF,step.x,0,step.z);
            if(p.connection!=null)p.connection.setPlayerLocation(p.posX,p.posY,p.posZ,p.rotationYaw,p.rotationPitch);
        }
        if(!held(p,lock.stack)||!targetable(p,target)||p.getDistanceSq(target)>3*3)return;
        float amount=ClawRules.rearDamage(8,lock.rear&&rear(p,target));
        if(ArsenalAccess.hit(p,target,amount)) {
            confirmedHit(p,lock.stack,target);
            cue(p,target,EnumParticleTypes.CRIT,10);
            p.world.playSound(null,target.posX,target.posY,target.posZ,SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,SoundCategory.PLAYERS,.8f,1.2f);
        }
    }

    /** Called only after the observed native damage transaction has succeeded. */
    static void confirmedHit(EntityPlayerMP p,ItemStack stack,EntityLivingBase target) {
        if(!held(p,stack)||!ArsenalAccess.allowed(p,target))return;
        long now=ArsenalAccess.now(p);
        Map<UUID,Bleed> bleeds=BLEEDS.computeIfAbsent(p.getUniqueID(),key->new LinkedHashMap<>());
        Bleed bleed=bleeds.get(target.getUniqueID());
        if(bleed==null||bleed.stack!=stack||now<bleed.lastHit||now-bleed.lastHit>ClawRules.BLEED_TICKS) {
            if(bleed==null&&bleeds.size()>=ClawRules.MAX_TARGETS)bleeds.remove(bleeds.keySet().iterator().next());
            bleed=new Bleed(target,stack,p.dimension);bleeds.put(bleed.target,bleed);
        }
        bleed.lastHit=now;
        if(++bleed.hits>=ClawRules.HITS_PER_STACK) {
            bleed.hits=0;
            if(now>bleed.until)bleed.stacks=0;
            bleed.stacks=Math.min(ClawRules.MAX_STACKS,bleed.stacks+1);
            bleed.until=now+ClawRules.BLEED_TICKS;bleed.next=now+20;
            cue(p,target,EnumParticleTypes.DAMAGE_INDICATOR,4);
            tell(p,"Crescent bleed: "+bleed.stacks+" / "+ClawRules.MAX_STACKS+" stacks for seventeen seconds.");
        }
    }

    static boolean rear(EntityPlayerMP p,EntityLivingBase target) {
        Vec3d look=target.getLookVec();
        return ClawRules.rear(look.x,look.z,p.posX-target.posX,p.posZ-target.posZ);
    }
    private static boolean held(EntityPlayerMP p,ItemStack stack) {
        return p!=null&&p.getHeldItemMainhand()==stack&&ArsenalLogic.proof(p,stack,SPEC);
    }
    private static boolean validLock(EntityPlayerMP p,Lock lock) {return p.dimension==lock.dimension&&held(p,lock.stack);}
    private static EntityLivingBase target(EntityPlayerMP p,UUID id) {
        Entity entity=((WorldServer)p.world).getEntityFromUuid(id);
        return entity instanceof EntityLivingBase?(EntityLivingBase)entity:null;
    }
    private static boolean targetable(EntityPlayerMP p,EntityLivingBase target) {
        return ArsenalAccess.allowed(p,target)&&ClawRules.inRange(p.getDistanceSq(target))&&p.canEntityBeSeen(target)
                &&!target.isEntityInvulnerable(DamageSource.causePlayerDamage(p));
    }
    private static void cue(EntityPlayerMP p,EntityLivingBase target,EnumParticleTypes particle,int count) {
        ArsenalLogic.particles(p,target.getPositionVector().add(0,target.height*.6,0),particle,count);
    }
    private static void tell(EntityPlayerMP p,String text) {p.sendStatusMessage(new TextComponentString(text),true);}
    static void clear(EntityPlayerMP p) {LOCKS.remove(p.getUniqueID());BLEEDS.remove(p.getUniqueID());ClawNetwork.clear(p.getUniqueID());}
    static boolean locked(EntityPlayerMP p) {return LOCKS.containsKey(p.getUniqueID());}
    static int stacks(EntityPlayerMP p,UUID target) {
        Map<UUID,Bleed> map=BLEEDS.get(p.getUniqueID());Bleed bleed=map==null?null:map.get(target);return bleed==null?0:bleed.stacks;
    }
    static int hits(EntityPlayerMP p,UUID target) {
        Map<UUID,Bleed> map=BLEEDS.get(p.getUniqueID());Bleed bleed=map==null?null:map.get(target);return bleed==null?0:bleed.hits;
    }
    private static final class PotionAttempt {
        final EntityLivingBase target;final PotionEffect effect;PotionEvent.PotionApplicableEvent event;
        PotionAttempt(EntityLivingBase target,PotionEffect effect) {this.target=target;this.effect=effect;}
    }
    private static final class Lock {
        final UUID target;final ItemStack stack;final int dimension;final long created;final boolean rear;
        boolean winding;long started;Vec3d strike;
        Lock(EntityLivingBase target,ItemStack stack,int dimension,long created,boolean rear) {
            this.target=target.getUniqueID();this.stack=stack;this.dimension=dimension;this.created=created;this.rear=rear;
        }
    }
    private static final class Bleed {
        final UUID target;final ItemStack stack;final int dimension;
        int hits,stacks;long lastHit,until,next;
        Bleed(EntityLivingBase target,ItemStack stack,int dimension) {this.target=target.getUniqueID();this.stack=stack;this.dimension=dimension;}
    }
}
