package village.vale;

import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class Combat {
    private static final ThreadLocal<SkillImpact> IMPACT = new ThreadLocal<>();
    private static final class SkillImpact {
        final EntityLivingBase target;
        final DamageSource source;
        LivingAttackEvent attack;
        LivingHurtEvent hurt;
        LivingDamageEvent damage;
        SkillImpact(EntityLivingBase target, DamageSource source) { this.target = target; this.source = source; }
        boolean canceled() {
            return (attack != null && attack.isCanceled()) || (hurt != null && hurt.isCanceled()) ||
                (damage != null && damage.isCanceled());
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void observeAttack(LivingAttackEvent event) {
        SkillImpact impact = IMPACT.get();
        if (impact != null && impact.target == event.getEntityLiving() && impact.source == event.getSource()) impact.attack = event;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void observeHurt(LivingHurtEvent event) {
        SkillImpact impact = IMPACT.get();
        if (impact != null && impact.target == event.getEntityLiving() && impact.source == event.getSource()) impact.hurt = event;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void observeDamage(LivingDamageEvent event) {
        SkillImpact impact = IMPACT.get();
        if (impact != null && impact.target == event.getEntityLiving() && impact.source == event.getSource()) impact.damage = event;
    }
    private static final EntityEquipmentSlot[] SLOTS = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST,
        EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET};

    static long now(EntityPlayer player) { return player.getServer().getWorld(0).getTotalWorldTime(); }

    /** Final Forge cancellation states matter even when vanilla returns true. */
    static boolean impact(EntityLivingBase target, DamageSource source, float damage) {
        float before = target.getHealth() + target.getAbsorptionAmount();
        SkillImpact hit = new SkillImpact(target, source), previous = IMPACT.get();
        boolean accepted;
        int immunityBefore = target.hurtResistantTime;
        IMPACT.set(hit);
        try { accepted = target.attackEntityFrom(source, damage); }
        finally { if (previous == null) IMPACT.remove(); else IMPACT.set(previous); }
        boolean applied = accepted && !hit.canceled() && target.getHealth() + target.getAbsorptionAmount() < before;
        if (!applied && Boolean.getBoolean("valeorders.selftest"))
            org.apache.logging.log4j.LogManager.getLogger("ValeImpact").info("Rejected impact: target=" + target.getName() + " hpBefore=" + before + " hpAfter=" + (target.getHealth()+target.getAbsorptionAmount()) + " raw=" + damage + " immunityBefore=" + immunityBefore + " accepted=" + accepted + " canceled=" + hit.canceled());
        return applied;
    }

    public static Order fullSet(EntityPlayer player) {
        if (OriginalArmorRegistry.wearing(player)) return OriginalArmorRegistry.order(OriginalArmorRegistry.full(player));
        if (OwnedArmor.wearing(player)) return OwnedArmor.order(player);
        Order found = null;
        RewardLedger ledger = RewardLedger.get(player);
        for (EntityEquipmentSlot slot : SLOTS) {
            ItemStack piece = player.getItemStackFromSlot(slot);
            if (!(piece.getItem() instanceof Gear.WardArmor) || !ledger.valid(player, piece)) return null;
            Order order = ((Gear.Ordered)piece.getItem()).order();
            if (found != null && found != order) return null;
            found = order;
        }
        return found;
    }

    private static boolean issuedWeapon(EntityPlayer player, ItemStack stack, Order order) {
        return stack.getItem() instanceof Gear.WardBlade && ((Gear.Ordered)stack.getItem()).order() == order
            && RewardLedger.get(player).valid(player, stack);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void hurt(LivingHurtEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        if (event.getSource().getTrueSource() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)event.getSource().getTrueSource();
            PlayerState.data(player).setLong("lastCombat", now(player));
            Order order = fullSet(player);
            if (order != null && event.getSource().getImmediateSource() == player &&
                    issuedWeapon(player, player.getHeldItemMainhand(), order)) {
                event.setAmount(event.getAmount() * 1.10F);
            }
        }
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer)event.getEntityLiving();
            PlayerState.data(player).setLong("lastCombat", now(player));
            Order order = fullSet(player);
            if (order != null && stateGuard(player) && !event.getSource().isUnblockable()) {
                event.setAmount(event.getAmount() * .45F);
                PlayerState.data(player).setLong("guardUntil", 0);
                player.sendStatusMessage(new TextComponentString("Guard absorbed the strike."), true);
            }
            if (order == Order.EMBER && event.getSource().isFireDamage()) event.setAmount(event.getAmount() * .7F);
            if (order == Order.GALE && "fall".equals(event.getSource().damageType)) event.setAmount(event.getAmount() * .6F);
        }
    }

    private static boolean stateGuard(EntityPlayer player) {
        return PlayerState.data(player).getLong("guardUntil") > now(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void death(LivingDeathEvent event) {
        // Adventures confirms the final cancellation state and actual death next tick
        // before granting progression, journal entries or contract credit.
    }

    @SubscribeEvent
    public void clone(PlayerEvent.Clone event) {
        NBTTagCompound original = PlayerState.data(event.getOriginal()).copy();
        PlayerState.data(event.getEntityPlayer());
        event.getEntityPlayer().getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).setTag("ValeOrders", original);
    }

    @SubscribeEvent
    public void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote || event.player.ticksExisted % 20 != 0) return;
        EntityPlayerMP player = (EntityPlayerMP)event.player;
        if (player.isDead || player.getHealth() <= 0) return;
        RewardLedger.get(player).deliver(player);
        NBTTagCompound state = PlayerState.data(player);
        long time = now(player);
        boolean fighting = state.getLong("lastCombat") > 0 && time - state.getLong("lastCombat") < 100;
        PlayerState.setResolve(player, PlayerState.resolve(player) + (fighting ? 2 : 5));
        Order order = fullSet(player);
        java.util.UUID healthId = java.util.UUID.fromString("db20c5a6-7fb4-4abf-b1c7-f86c717ef140");
        net.minecraft.entity.ai.attributes.IAttributeInstance health = player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MAX_HEALTH);
        double bonus = order == null || OwnedArmor.wearing(player) || OriginalArmorRegistry.wearing(player) ? 0 : 4 + Math.min(4, (PlayerState.level(player) - 1) / 4);
        net.minecraft.entity.ai.attributes.AttributeModifier old = health.getModifier(healthId);
        if ((old == null ? 0 : old.getAmount()) != bonus) {
            if (old != null) health.removeModifier(old);
            if (bonus > 0) health.applyModifier(new net.minecraft.entity.ai.attributes.AttributeModifier(healthId, "Vale set vitality", bonus, 0).setSaved(false));
            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        }
        if (order != null) {
            long seconds = Rules.cooldownSeconds(time, state.getLong("nextSkill"));
            player.sendStatusMessage(new TextComponentString(order.title + " | Resolve " + PlayerState.resolve(player) +
                "/100 | " + order.skill + (seconds > 0 ? " " + seconds + "s" : " ready") +
                " | Technique " + (Rules.cooldownSeconds(time, state.getLong("nextTechnique")) > 0 ?
                    Rules.cooldownSeconds(time, state.getLong("nextTechnique")) + "s" : "ready")), true);
        }
    }

    public static boolean activate(EntityPlayer player, ItemStack weapon, Order order) {
        if (player.world.isRemote || player.isDead || player.isSpectator()) return false;
        if (fullSet(player) != order || !issuedWeapon(player, weapon, order))
            return tell(player, "Equip four matching server-issued armor pieces and an issued matching weapon.");
        NBTTagCompound state = PlayerState.data(player);
        long time = now(player);
        if (time < state.getLong("nextSkill")) return tell(player, "That order skill is cooling down.");
        int remaining = Rules.spendResolve(PlayerState.resolve(player), ValeOrders.skillCost);
        if (remaining < 0) return tell(player, "Not enough resolve.");
        EntityLivingBase target = null;
        if (order == Order.EMBER || order == Order.RIME || order == Order.STORM) {
            target = aimedTarget(player);
            if (target == null) return tell(player, "Aim at a visible permitted target within twelve blocks.");
            float damage = order == Order.STORM ? 8.0F : order == Order.EMBER ? 6.0F : 4.0F;
            // Forge can return true after LivingHurtEvent was canceled. Require an
            // actual impact and inspect the final event cancellation states. Absorption
            // may already be consumed before a later LivingDamageEvent is canceled.
            if (!impact(target, DamageSource.causePlayerDamage(player), damage))
                return tell(player, "The target blocked or rejected the skill.");
        }
        if (order == Order.BLOOM && player.getHealth() >= player.getMaxHealth()) return tell(player, "Health is already full.");
        switch (order) {
            case EMBER: target.setFire(3); break;
            case CURRENT: player.addPotionEffect(new PotionEffect(MobEffects.SPEED, 100, 1)); break;
            case GALE:
                Vec3d direction = player.getLookVec();
                player.addVelocity(direction.x * .7, .2, direction.z * .7);
                player.velocityChanged = true;
                break;
            case FLINT: player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 100, 0)); break;
            case BLOOM: player.heal(6.0F); break;
            case RIME: target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 80, 1)); break;
            case STORM: break;
            case DUSK: player.addPotionEffect(new PotionEffect(MobEffects.INVISIBILITY, 80, 0)); break;
            case DAWN: player.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 120, 0)); break;
        }
        PlayerState.setResolve(player, remaining);
        state.setLong("nextSkill", time + ValeOrders.skillCooldown * 20L);
        state.setLong("lastCombat", time);
        player.sendStatusMessage(new TextComponentString(order.skill + " activated."), true);
        return true;
    }

    static EntityLivingBase aimedTarget(EntityPlayer player) { return aimedTarget(player, 12); }
    static EntityLivingBase aimedTarget(EntityPlayer player, double range) {
        Vec3d start = player.getPositionEyes(1.0F);
        Vec3d end = start.add(player.getLookVec().scale(range));
        RayTraceResult wall = player.world.rayTraceBlocks(start, end, false, true, false);
        double best = wall == null ? range : start.distanceTo(wall.hitVec);
        EntityLivingBase result = null;
        List<EntityLivingBase> candidates = player.world.getEntitiesWithinAABB(EntityLivingBase.class,
            player.getEntityBoundingBox().grow(range));
        for (EntityLivingBase target : candidates) {
            if (target == player || !target.isEntityAlive() || !target.canBeCollidedWith() || player.isOnSameTeam(target)) continue;
            if (target instanceof EntityPlayer) {
                EntityPlayer other = (EntityPlayer)target;
                if (!ValeOrders.pvpAbilities || !player.getServer().isPVPEnabled() ||
                    !player.canAttackPlayer(other) || other.isSpectator() || other.capabilities.isCreativeMode) continue;
            } else if (!Targets.hostile(target)) {
                // Custom enemies must be explicitly marked; friendly NPCs/pets remain excluded.
                continue;
            }
            RayTraceResult hit = target.getEntityBoundingBox().grow(.2).calculateIntercept(start, end);
            if (hit != null && start.distanceTo(hit.hitVec) < best) {
                best = start.distanceTo(hit.hitVec); result = target;
            }
        }
        return result;
    }

    private static boolean tell(EntityPlayer player, String text) {
        player.sendStatusMessage(new TextComponentString(text), true);
        return false;
    }
}
