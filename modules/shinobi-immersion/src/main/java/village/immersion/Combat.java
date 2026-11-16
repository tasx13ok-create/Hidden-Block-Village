package village.immersion;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Server-side combat immersion that layers on top of Core and NarutoMod without replacing them:
 *  - Perfect dodge: a hit cancelled within the first ticks of a dodge burst slows the attacker,
 *    hastes the dodger, refunds chakra through Core's MoveCost.restore (reflection) and cues a flash.
 *  - Elemental reactions: fire on wet = steam (extinguish, weaker hit), lightning on wet = chain to
 *    nearby wet mobs, wind on burning = fire spreads to neighbours, earth on burning = snuffed.
 *  - Bosses (non-"isNonBoss" living entities): a red warning ring when an attack swing starts, and
 *    an arena pulse (roar, harmless knockback shockwave, dust) at the 65% and 30% health phases.
 * No blocks are changed; reaction damage goes through normal damage so protection mods still apply.
 */
final class Combat {
    private static final Map<UUID, double[]> DODGE = new HashMap<>();   // {lastSpeed, dodgeAt}
    private static final Map<Integer, int[]> BOSS = new HashMap<>();     // entityId -> {phase, swinging}
    private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> false);
    private static Method restore;
    private static boolean triedRestore;

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.world.isRemote) return;
        EntityPlayer p = e.player;
        double[] st = DODGE.computeIfAbsent(p.getUniqueID(), k -> new double[] {0, -1});
        double speed = Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ);
        if (ImmersionMath.dodgeBurst(speed, st[0])) st[1] = p.world.getTotalWorldTime();
        st[0] = speed;
    }

    // ---- perfect dodge ----
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void dodged(LivingAttackEvent e) {
        if (!Config.perfectDodge || !(e.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        double[] st = DODGE.get(p.getUniqueID());
        long now = p.world.getTotalWorldTime();
        if (st == null || !ImmersionMath.perfect(now, (long) st[1], e.isCanceled())) return;
        st[1] = -1;   // once per dodge
        Entity attacker = e.getSource().getTrueSource();
        if (attacker instanceof EntityLivingBase && attacker != p) ((EntityLivingBase) attacker).addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 3, false, true));
        p.addPotionEffect(new PotionEffect(MobEffects.SPEED, 30, 1, false, false));
        refund(p, Config.perfectRefund);
        p.world.playSound(null, p.posX, p.posY, p.posZ, SoundEvents.BLOCK_NOTE_CHIME, SoundCategory.PLAYERS, 0.8f, 1.6f);
        if (p.world instanceof WorldServer) ((WorldServer) p.world).spawnParticle(EnumParticleTypes.END_ROD, p.posX, p.posY + 1, p.posZ, 14, 0.4, 0.6, 0.4, 0.04);
        Net.perfect(p);
    }

    private static void refund(EntityPlayerMP p, int units) {
        if (units <= 0) return;
        if (!triedRestore) {
            triedRestore = true;
            try { restore = Class.forName("village.vale.bridge.MoveCost").getMethod("restore", EntityPlayerMP.class, int.class); }
            catch (ReflectiveOperationException | LinkageError absent) { restore = null; }
        }
        if (restore != null) { try { restore.invoke(null, p, units); return; } catch (ReflectiveOperationException | RuntimeException ignored) {} }
        p.getFoodStats().addStats(1, 0.2f);
    }

    // ---- elemental reactions ----
    @SubscribeEvent(priority = EventPriority.LOW)
    public void element(LivingHurtEvent e) {
        if (!Config.reactions || CHAINING.get() || e.getEntity().world.isRemote) return;
        EntityLivingBase t = e.getEntityLiving();
        DamageSource src = e.getSource();
        Entity imm = src.getImmediateSource();
        ResourceLocation id = imm == null ? null : EntityList.getKey(imm);
        String sourceId = (id == null ? "" : id.toString()) + " " + (imm == null ? "" : imm.getClass().getSimpleName());
        int el = ImmersionMath.element(src.getDamageType(), sourceId, src.isFireDamage());
        boolean wet = t.isWet();
        int r = ImmersionMath.reaction(el, wet, t.isBurning());
        if (r == ImmersionMath.R_NONE || !(t.world instanceof WorldServer)) return;
        WorldServer w = (WorldServer) t.world;
        e.setAmount(e.getAmount() * ImmersionMath.reactionDamage(r));
        double x = t.posX, y = t.posY + t.height * 0.6, z = t.posZ;
        switch (r) {
            case ImmersionMath.R_STEAM:
                t.extinguish();
                w.spawnParticle(EnumParticleTypes.CLOUD, x, y, z, 20, 0.5, 0.5, 0.5, 0.05);
                w.playSound(null, x, y, z, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.8f, 1.2f);
                break;
            case ImmersionMath.R_CHAIN: {
                List<EntityLivingBase> near = w.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(x - 4, y - 3, z - 4, x + 4, y + 3, z + 4), o -> o != t && o != src.getTrueSource() && o.isWet() && o.isEntityAlive() && !(o instanceof EntityPlayer && src.getTrueSource() instanceof EntityPlayer && !((EntityPlayer) src.getTrueSource()).canAttackPlayer((EntityPlayer) o)));
                CHAINING.set(true);
                try {
                    int n = 0;
                    for (EntityLivingBase o : near) {
                        if (n++ >= 3) break;
                        o.attackEntityFrom(src, e.getAmount() * 0.3f);
                        for (int i = 0; i <= 8; i++) { double f = i / 8.0; w.spawnParticle(EnumParticleTypes.FIREWORKS_SPARK, x + (o.posX - x) * f, y + (o.posY + o.height * 0.5 - y) * f, z + (o.posZ - z) * f, 1, 0.05, 0.05, 0.05, 0); }
                    }
                } finally { CHAINING.set(false); }
                w.playSound(null, x, y, z, SoundEvents.ENTITY_LIGHTNING_IMPACT, SoundCategory.PLAYERS, 0.5f, 1.8f);
                break;
            }
            case ImmersionMath.R_SPREAD:
                for (EntityLivingBase o : w.getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(x - 3, y - 2, z - 3, x + 3, y + 2, z + 3), o -> o != t && o != src.getTrueSource() && !o.isWet() && o.isEntityAlive() && !(o instanceof EntityPlayer))) o.setFire(3);
                w.spawnParticle(EnumParticleTypes.FLAME, x, y, z, 24, 1.2, 0.4, 1.2, 0.03);
                w.playSound(null, x, y, z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.6f, 0.9f);
                break;
            case ImmersionMath.R_SNUFF:
                t.extinguish();
                w.spawnParticle(EnumParticleTypes.BLOCK_DUST, x, y, z, 18, 0.4, 0.4, 0.4, 0.05, net.minecraft.block.Block.getStateId(net.minecraft.init.Blocks.DIRT.getDefaultState()));
                w.playSound(null, x, y, z, SoundEvents.BLOCK_GRAVEL_BREAK, SoundCategory.PLAYERS, 0.7f, 0.8f);
                break;
            default: break;
        }
    }

    // ---- bosses ----
    @SubscribeEvent public void bosses(TickEvent.WorldTickEvent e) {
        if (!Config.bossCues || e.phase != TickEvent.Phase.END || !(e.world instanceof WorldServer) || e.world.getTotalWorldTime() % 2 != 0) return;
        WorldServer w = (WorldServer) e.world;
        for (EntityPlayer p : w.playerEntities) {
            for (EntityLiving b : w.getEntitiesWithinAABB(EntityLiving.class, p.getEntityBoundingBox().grow(32), b -> !b.isNonBoss() && b.isEntityAlive())) {
                int[] st = BOSS.computeIfAbsent(b.getEntityId(), k -> new int[] {ImmersionMath.phase(b.getHealth(), b.getMaxHealth()), 0});
                int ph = ImmersionMath.phase(b.getHealth(), b.getMaxHealth());
                if (ph > st[0]) { st[0] = ph; arena(w, b); }
                boolean swinging = b.isSwingInProgress;
                if (swinging && st[1] == 0) {   // attack tell: red ring at the boss's feet
                    for (int i = 0; i < 24; i++) { double a = i * Math.PI / 12; w.spawnParticle(EnumParticleTypes.REDSTONE, b.posX + Math.cos(a) * 1.6, b.posY + 0.1, b.posZ + Math.sin(a) * 1.6, 0, 1.0, 0.0, 0.0, 1.0); }
                }
                st[1] = swinging ? 1 : 0;
            }
        }
        if (w.getTotalWorldTime() % 200 == 0) BOSS.keySet().removeIf(id -> w.getEntityByID(id) == null);
    }

    private static void arena(WorldServer w, EntityLiving b) {
        w.playSound(null, b.posX, b.posY, b.posZ, SoundEvents.ENTITY_ENDERDRAGON_GROWL, SoundCategory.HOSTILE, 1.2f, 0.7f);
        for (int ring = 1; ring <= 3; ring++)
            for (int i = 0; i < 36; i++) { double a = i * Math.PI / 18, rr = ring * 2.0; w.spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, b.posX + Math.cos(a) * rr, b.posY + 0.2, b.posZ + Math.sin(a) * rr, 1, 0, 0.05, 0, 0.02); }
        for (EntityPlayer p : w.getEntitiesWithinAABB(EntityPlayer.class, b.getEntityBoundingBox().grow(6))) {
            double dx = p.posX - b.posX, dz = p.posZ - b.posZ, d = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
            p.addVelocity(dx / d * 0.9, 0.35, dz / d * 0.9);
            p.velocityChanged = true;
        }
    }
}
