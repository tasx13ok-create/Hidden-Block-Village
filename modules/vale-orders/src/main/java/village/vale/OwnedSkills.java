package village.vale;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;

/** Shared policy used by the user-owned armor's compatibility build. */
public final class OwnedSkills {
    private OwnedSkills() {}
    public static boolean allowed(EntityPlayer player, EntityLivingBase target) {
        if (target == player || !target.isEntityAlive() || player.isOnSameTeam(target)) return false;
        if (target instanceof EntityPlayer) {
            EntityPlayer other=(EntityPlayer)target;
            return ValeOrders.pvpAbilities && player.getServer()!=null && player.getServer().isPVPEnabled() &&
                player.canAttackPlayer(other) && !other.isSpectator() && !other.capabilities.isCreativeMode;
        }
        return Targets.hostile(target);
    }
    public static boolean damage(EntityLivingBase target, DamageSource source, float amount) {
        if (target.world.isRemote || !(source.getTrueSource() instanceof EntityPlayer) ||
                !allowed((EntityPlayer)source.getTrueSource(),target)) return false;
        return Combat.impact(target,source,amount);
    }
    public static void pulse(EntityPlayerMP player,double radius,float amount,boolean fire,boolean debuff) {
        if (player.world.isRemote || !player.isEntityAlive()) return;
        radius=Math.max(0,Math.min(12,radius));
        for (EntityLivingBase target:player.world.getEntitiesWithinAABB(EntityLivingBase.class,player.getEntityBoundingBox().grow(radius))) {
            if (!allowed(player,target) || player.getDistanceSq(target)>radius*radius || !player.canEntityBeSeen(target)) continue;
            // Control pulses deal one raw damage so every status effect passes
            // through the real attack/hurt/damage protection pipeline.
            if (!damage(target,DamageSource.causePlayerDamage(player),Math.max(1,amount))) continue;
            if (fire) target.setFire(3);
            if (debuff) {target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,80,1));target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,80,0));}
        }
    }
}
