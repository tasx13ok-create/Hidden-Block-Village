package village.vale;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;

/** Six weapon roles using the same two mouse buttons as ordinary equipment. */
public final class Techniques {
    private Techniques() {}
    public static String name(String kind) {
        switch (kind) {
            case "saber": return "Patient Guard";
            case "spear": return "Long Thrust";
            case "dagger": return "Sidestep";
            case "glaive": return "Fan Cut";
            case "staff": return "Centering Breath";
            default: return "Crushing Strike";
        }
    }
    public static boolean activate(EntityPlayer player, Gear.WardBlade blade) {
        if (player.world.isRemote || !player.isEntityAlive() || player.isSpectator()) return false;
        if (Combat.fullSet(player) != blade.order() || !RewardLedger.get(player).valid(player, player.getHeldItemMainhand()))
            return tell(player, "Equip your issued matching armor and weapon.");
        NBTTagCompound state = PlayerState.data(player);
        long time = Combat.now(player);
        if (time < state.getLong("nextTechnique")) return tell(player, "Weapon technique is cooling down.");
        if (PlayerState.resolve(player) < 20) return tell(player, "Need 20 resolve.");
        switch (blade.kind) {
            case "saber": state.setLong("guardUntil", time + 50); break;
            case "dagger":
                Vec3d look = player.getLookVec();
                player.addVelocity(look.z * .65, .12, -look.x * .65);
                player.velocityChanged = true;
                player.addPotionEffect(new PotionEffect(MobEffects.SPEED, 40, 0));
                break;
            case "staff":
                if (player.getHealth() >= player.getMaxHealth() && !player.isPotionActive(MobEffects.SLOWNESS) && !player.isPotionActive(MobEffects.WEAKNESS))
                    return tell(player, "Already centered: health is full and no slowing or weakness.");
                player.heal(2); player.removePotionEffect(MobEffects.SLOWNESS); player.removePotionEffect(MobEffects.WEAKNESS);
                break;
            case "glaive":
                List<EntityLivingBase> candidates = new ArrayList<>(player.world.getEntitiesWithinAABB(EntityLivingBase.class, player.getEntityBoundingBox().grow(3.75)));
                candidates.sort(Comparator.comparingDouble(player::getDistanceSq));
                int hits = 0;
                for (EntityLivingBase target : candidates) {
                    if (hits >= 3) break;
                    if (!target.isEntityAlive() || !Targets.hostile(target) || player.isOnSameTeam(target) || player.getDistanceSq(target) > 3.75 * 3.75) continue;
                    Vec3d delta = target.getPositionVector().subtract(player.getPositionVector()).normalize();
                    if (player.getLookVec().dotProduct(delta) < .15 || !player.canEntityBeSeen(target)) continue;
                    if (Combat.impact(target, DamageSource.causePlayerDamage(player), 6)) hits++;
                }
                if (hits == 0) return tell(player, "No permitted enemies were hit in front of you.");
                break;
            default:
                EntityLivingBase target = Combat.aimedTarget(player, blade.kind.equals("spear") ? 6 : 4);
                if (target == null || !Combat.impact(target, DamageSource.causePlayerDamage(player), blade.kind.equals("spear") ? 8 : 10))
                    return tell(player, "Aim at an unblocked permitted enemy in weapon range.");
                break;
        }
        PlayerState.setResolve(player, PlayerState.resolve(player) - 20);
        state.setLong("nextTechnique", time + 120);
        state.setLong("lastCombat", time);
        player.sendStatusMessage(new TextComponentString(name(blade.kind) + " activated."), true);
        return true;
    }
    private static boolean tell(EntityPlayer player, String text) { player.sendStatusMessage(new TextComponentString(text), true); return false; }
}
