package village.immersion;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Server rules for combat feel. Everything here is a light layer on normal combat:
 *  - Hit-stop: heavy or finishing blows dealt by a player cue a brief camera hold and kick.
 *  - Guard break: five blocked hits within three seconds break a raised shield briefly.
 *  - Stagger: four player hits on a mob within two seconds stagger it (bosses need eight).
 *  - Chakra exhaustion: below 5% chakra (Core's real reading) you move a little slower until 15%.
 *  - Landing roll: sneaking as you land a 3.5-24 block fall cuts the fall damage.
 */
final class Feel {
    private static final Map<UUID, long[]> GUARD = new HashMap<>();      // {count, lastAt}
    private static final Map<Integer, long[]> STAGGER = new HashMap<>();  // entityId -> {count, lastAt, cooldownUntil}
    private static final Map<UUID, Boolean> TIRED = new HashMap<>();
    private static Method view;
    private static Field amount, maximum;
    private static boolean triedView;

    // ---- hit-stop (post-armor damage so the strength matches what landed) ----
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void landed(LivingDamageEvent e) {
        if (!Config.hitStop || e.getAmount() <= 0) return;
        Entity src = e.getSource().getTrueSource();
        EntityLivingBase t = e.getEntityLiving();
        boolean finisher = e.getAmount() >= t.getHealth();
        float s = ImmersionMath.impact(e.getAmount(), Config.hitStopDamage, finisher);
        if (s <= 0) return;
        if (src instanceof EntityPlayerMP && src != t) Net.cue((EntityPlayerMP) src, Net.IMPACT, s);
        if (t instanceof EntityPlayerMP) Net.cue((EntityPlayerMP) t, Net.IMPACT, s * 0.6f);
    }

    // ---- guard break ----
    @SubscribeEvent(priority = EventPriority.LOW)
    public void blocked(LivingAttackEvent e) {
        if (!Config.guardBreak || !(e.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        if (!p.isActiveItemStackBlocking() || e.getSource().isUnblockable()) return;
        Vec3d from = e.getSource().getDamageLocation();
        if (from == null) return;
        Vec3d to = from.subtract(p.getPositionVector()).normalize(), look = p.getLook(1f);
        if (to.x * look.x + to.z * look.z <= 0) return;   // only hits the shield actually faces
        long now = p.world.getTotalWorldTime();
        long[] g = GUARD.computeIfAbsent(p.getUniqueID(), k -> new long[] {0, -1});
        g[0] = ImmersionMath.pressure((int) g[0], g[1], now, ImmersionMath.GUARD_WINDOW); g[1] = now;
        if (g[0] < ImmersionMath.GUARD_BREAK) return;
        g[0] = 0;
        ItemStack shield = p.getActiveItemStack();
        if (!shield.isEmpty()) p.getCooldownTracker().setCooldown(shield.getItem(), 60);
        p.resetActiveHand();
        p.world.setEntityState(p, (byte) 30);   // vanilla shield-break sound for everyone nearby
        p.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 20, 1, false, false));
        Net.cue(p, Net.GUARD_BREAK, 0.7f);
    }

    // ---- stagger ----
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void pressured(LivingDamageEvent e) {
        if (!Config.stagger || !(e.getEntityLiving() instanceof EntityLiving) || !(e.getSource().getTrueSource() instanceof EntityPlayer)) return;
        EntityLiving m = (EntityLiving) e.getEntityLiving();
        if (e.getAmount() >= m.getHealth() || !(m.world instanceof WorldServer)) return;
        long now = m.world.getTotalWorldTime();
        long[] st = STAGGER.computeIfAbsent(m.getEntityId(), k -> new long[] {0, -1, 0});
        if (now < st[2]) return;
        st[0] = ImmersionMath.pressure((int) st[0], st[1], now, ImmersionMath.STAGGER_WINDOW); st[1] = now;
        boolean boss = !m.isNonBoss();
        if (st[0] < ImmersionMath.staggerHits(boss)) return;
        st[0] = 0; st[2] = now + ImmersionMath.STAGGER_COOLDOWN;
        int ticks = boss ? 10 : 24;
        m.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, ticks, 4, false, false));
        m.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, ticks, 1, false, false));
        m.motionX *= 0.2; m.motionZ *= 0.2;
        WorldServer w = (WorldServer) m.world;
        w.spawnParticle(EnumParticleTypes.CRIT, m.posX, m.posY + m.height + 0.3, m.posZ, 10, 0.3, 0.1, 0.3, 0.1);
        w.playSound(null, m.posX, m.posY, m.posZ, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.6f, 0.6f);
        if (e.getSource().getTrueSource() instanceof EntityPlayerMP) Net.cue((EntityPlayerMP) e.getSource().getTrueSource(), Net.STAGGER, 0.5f);
    }

    // ---- chakra exhaustion ----
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e) {
        if (!Config.exhaustion || e.phase != TickEvent.Phase.END || !(e.player instanceof EntityPlayerMP) || e.player.world.getTotalWorldTime() % 20 != 0) return;
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        if (p.isCreative() || p.isSpectator() || !p.isEntityAlive()) { TIRED.remove(p.getUniqueID()); return; }
        double[] c = chakra(p);
        boolean was = TIRED.getOrDefault(p.getUniqueID(), false);
        boolean now = c != null && ImmersionMath.exhausted(was, c[0], c[1]);
        TIRED.put(p.getUniqueID(), now);
        if (!now) return;
        p.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 30, 0, false, false));
        if (p.world.getTotalWorldTime() % 40 == 0) Net.cue(p, Net.EXHAUSTED, 1);
    }

    /** {amount, maximum} from Core's MoveCost.view, or null when Core or a reading is unavailable. */
    private static double[] chakra(EntityPlayerMP p) {
        if (!triedView) {
            triedView = true;
            try {
                view = Class.forName("village.vale.bridge.MoveCost").getMethod("view", EntityPlayer.class);
                Class<?> v = view.getReturnType();
                amount = v.getField("amount"); maximum = v.getField("maximum");
            } catch (ReflectiveOperationException | LinkageError absent) { view = null; }
        }
        if (view == null) return null;
        try {
            Object v = view.invoke(null, p);
            if (v == null || !Boolean.TRUE.equals(v.getClass().getMethod("ready").invoke(v))) return null;
            return new double[] {amount.getDouble(v), maximum.getDouble(v)};
        } catch (ReflectiveOperationException | RuntimeException failed) { return null; }
    }

    // ---- landing roll (damage; the client plays the roll itself) ----
    @SubscribeEvent public void fall(LivingFallEvent e) {
        if (!Config.landingRoll || !(e.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.getEntityLiving();
        if (!ImmersionMath.roll(p.isSneaking(), e.getDistance())) return;
        e.setDamageMultiplier(e.getDamageMultiplier() * ImmersionMath.rollDamage(e.getDistance()));
        p.world.playSound(null, p.posX, p.posY, p.posZ, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.7f, 0.8f);
    }

    @SubscribeEvent public void cleanup(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (++sweep % 600 != 0) return;
        net.minecraft.world.World w = net.minecraftforge.common.DimensionManager.getWorld(0);
        if (w == null) return;
        long now = w.getTotalWorldTime();
        STAGGER.values().removeIf(v -> v[1] < now - 400 && v[2] < now);
        GUARD.values().removeIf(v -> v[1] < now - 400);
    }
    private int sweep;
}
