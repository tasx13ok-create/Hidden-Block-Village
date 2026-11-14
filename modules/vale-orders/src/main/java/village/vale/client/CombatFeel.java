package village.vale.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-only combat feedback (no gameplay effect):
 *  - taking damage: a decaying camera shake scaled by the health lost, biased away from the attacker;
 *  - heavy landings: a short vertical jolt scaled by fall distance;
 *  - landing a melee hit: a quick camera punch toward the swing plus white voxel sparks at the
 *    target (more on a critical hit, detected from falling + not on ground).
 * Shake uses smooth noise and an exponential decay so it never jitters at high frame rates.
 * Config: screenShake (on/off) and shakeIntensity (0..2) in valeorders-client.cfg.
 */
@SideOnly(Side.CLIENT)
public final class CombatFeel {
    public static boolean enabled = true;
    public static float intensity = 1f;
    private static double trauma, punch, bias, lastHealth = -1;
    private static float lastFall;
    private static boolean wasGround = true;
    private static long seed = 1;

    private CombatFeel() {}

    public static void initialize(java.io.File folder) {
        net.minecraftforge.common.config.Configuration c = new net.minecraftforge.common.config.Configuration(new java.io.File(folder, "valeorders-client.cfg"));
        c.load();
        enabled = c.getBoolean("screenShake", "combat", true, "Camera shake on taking damage and heavy landings, and a small punch when your melee hit lands. Cosmetic only.");
        intensity = c.getFloat("shakeIntensity", "combat", 1f, 0f, 2f, "Strength of combat camera feedback.");
        if (c.hasChanged()) c.save();
        MinecraftForge.EVENT_BUS.register(new CombatFeel.Hooks());
        VoxelBits.install();
    }

    static double noise(double t, long s) {   // smooth value noise in [-1,1]
        long i = (long) Math.floor(t); double f = t - i, u = f * f * (3 - 2 * f);
        double a = village.vale.WeaponMath.hash(s, (int) i) * 2 - 1, b = village.vale.WeaponMath.hash(s, (int) (i + 1)) * 2 - 1;
        return a + (b - a) * u;
    }

    public static final class Hooks {
        @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getMinecraft();
            EntityPlayer p = mc.player;
            if (p == null || mc.isGamePaused()) { lastHealth = -1; return; }
            if (lastHealth >= 0 && p.getHealth() < lastHealth - 0.01) {
                double lost = lastHealth - p.getHealth();
                trauma = Math.min(1, trauma + 0.25 + lost / Math.max(1, p.getMaxHealth()) * 2.5);
                Entity src = p.getAttackingEntity() != null ? p.getAttackingEntity() : null;
                if (src != null) { double ang = Math.atan2(src.posZ - p.posZ, src.posX - p.posX) - Math.toRadians(p.rotationYaw + 90); bias = Math.sin(ang); }
                else bias = 0;
            }
            lastHealth = p.getHealth();
            if (!p.onGround) lastFall = Math.max(lastFall, p.fallDistance);
            if (p.onGround && !wasGround && lastFall > 3.5) trauma = Math.min(1, trauma + Math.min(0.7, (lastFall - 3) * 0.08));
            if (p.onGround) lastFall = 0;
            wasGround = p.onGround;
            trauma *= 0.86;
            punch *= 0.6;
            if (trauma < 0.002) trauma = 0;
            if (punch < 0.002) punch = 0;
        }

        @SubscribeEvent public void hit(AttackEntityEvent e) {
            Minecraft mc = Minecraft.getMinecraft();
            if (!e.getEntityPlayer().world.isRemote || e.getEntityPlayer() != mc.player || !(e.getTarget() instanceof EntityLivingBase)) return;
            EntityPlayer p = e.getEntityPlayer();
            boolean crit = p.fallDistance > 0 && !p.onGround && !p.isOnLadder() && !p.isInWater() && p.getCooledAttackStrength(0.5f) > 0.9f;
            punch = Math.min(1, punch + (crit ? 0.9 : 0.5) * p.getCooledAttackStrength(0.5f));
            Entity t = e.getTarget();
            java.util.Random r = p.world.rand;
            double tx = t.posX, ty = t.posY + t.height * 0.6, tz = t.posZ;
            int n = crit ? 16 : 8;
            for (int i = 0; i < n; i++)
                VoxelBits.spawn(tx, ty, tz, (r.nextDouble() - 0.5) * 0.35, r.nextDouble() * 0.25, (r.nextDouble() - 0.5) * 0.35, 1, i % 4 == 0 ? 0xFFE27A : 0xFFFFFF, 1f, 8 + r.nextInt(6), 0.03, 0.9, true);
        }

        @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup e) {
            if (!enabled || intensity <= 0 || !village.vale.client.MotionGate.allowed()) return;
            double t = (Minecraft.getMinecraft().player == null ? 0 : Minecraft.getMinecraft().player.ticksExisted) + e.getRenderPartialTicks();
            double k = trauma * trauma * intensity;   // squared trauma: small hits stay subtle
            if (k > 0) {
                e.setYaw((float) (e.getYaw() + (noise(t * 0.9, seed) * 2.2 + bias * 1.2) * k));
                e.setPitch((float) (e.getPitch() + noise(t * 0.9, seed + 7) * 2.0 * k));
                e.setRoll((float) (e.getRoll() + noise(t * 0.7, seed + 13) * 3.0 * k));
            }
            if (punch > 0) e.setPitch((float) (e.getPitch() + punch * 1.4 * intensity));
        }
    }
}
