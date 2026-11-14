package village.vale.client;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.vale.JutsuMath;

/**
 * Chest power core for Hidden Village Bijuu Armor chestplates (detected by reflection; no compile
 * dependency). Drawn on the body bone so it follows every pose, in stacked 3D layers:
 *   1. recessed dark socket and bevelled frame plates (face-shaded voxels)
 *   2. eight rune studs around the frame that light up in sequence
 *   3. energy veins running to the shoulders and down the ribs, with pulses travelling outward
 *   4. a tumbling emissive core cube inside a counter-rotating inner shell
 *   5. two orbit rings of voxel motes on tilted planes
 *   6. additive bloom (soft halo + hard core glow) and flaring light shafts
 *   7. a light pool on the ground and nearby surfaces that pulses with the core
 * "Power" drives brightness, pulse rate, ring speed, shaft length and sparks. It rises with
 * sprinting, attacking, taking damage and low health, and decays smoothly at rest.
 * Colour comes from the beast slot (original palette, colour only).
 */
@SideOnly(Side.CLIENT)
public final class ArmorCoreFx implements LayerRenderer<AbstractClientPlayer> {
    private static final String ARMOR_CLASS = "village.bijuu.BijuuArmor$Armor";
    private static final int[][] PALETTE = {
        {0xFFE7A8, 0xD89A3A}, {0xCDEBFF, 0x2F7BFF}, {0xE0FFF8, 0x3FC8C0}, {0xFFE0C8, 0xE8301A}, {0xFFF4F0, 0xE8C8F0},
        {0xF0FFD0, 0x58D83A}, {0xF8FFE0, 0xB8E05A}, {0xF2E6FF, 0x8A3AF0}, {0xFFF0D0, 0xFF7A1A}};
    private static Field beastField;
    private static final Set<RenderPlayer> HOOKED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final java.util.Map<Integer, double[]> POWER = new java.util.HashMap<>(); // id -> {power, lastTime, lastHurt, lastSwing}
    private final RenderPlayer renderer;

    private ArmorCoreFx(RenderPlayer r) { renderer = r; }

    public static void initialize() { MinecraftForge.EVENT_BUS.register(new Hooks()); VoxelBits.install(); }

    public static final class Hooks {
        @SubscribeEvent public void attach(RenderPlayerEvent.Pre e) {
            RenderPlayer r = e.getRenderer();
            if (HOOKED.add(r)) r.addLayer(new ArmorCoreFx(r));
        }
        @SubscribeEvent public void ground(RenderWorldLastEvent e) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.world == null) return;
            float pt = e.getPartialTicks();
            net.minecraft.entity.Entity cam = mc.getRenderViewEntity();
            if (cam == null) return;
            double cx = cam.lastTickPosX + (cam.posX - cam.lastTickPosX) * pt, cy = cam.lastTickPosY + (cam.posY - cam.lastTickPosY) * pt, cz = cam.lastTickPosZ + (cam.posZ - cam.lastTickPosZ) * pt;
            for (EntityPlayer p : mc.world.playerEntities) {
                int beast = beast(p);
                if (beast < 0 || p.getDistanceSq(cam) > 48 * 48) continue;
                double power = power(p, p.ticksExisted + pt);
                double pulse = 0.5 + 0.5 * Math.sin((p.ticksExisted + pt) * (0.12 + 0.25 * power));
                double x = p.lastTickPosX + (p.posX - p.lastTickPosX) * pt, y = p.lastTickPosY + (p.posY - p.lastTickPosY) * pt, z = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * pt;
                int c = PALETTE[beast][1];
                // Light pool on the floor under the wearer (brighter and wider with power).
                double groundY = Math.floor(y + 0.01) + 0.02 - y;
                VfxDraw.begin(x - cx, y - cy, z - cz);
                try {
                    VfxDraw.additive();
                    double r = 1.3 + 1.2 * power + 0.25 * pulse;
                    VfxDraw.annulus(0, groundY, 0, 0, r * 0.35, c, (float) (0.28 + 0.25 * power), 24);
                    VfxDraw.annulus(0, groundY, 0, r * 0.35, r * 0.7, c, (float) (0.16 + 0.16 * power), 24);
                    VfxDraw.annulus(0, groundY, 0, r * 0.7, r, c, (float) (0.06 + 0.08 * power), 24);
                } finally { VfxDraw.end(); }
            }
        }
    }

    static int beast(EntityPlayer p) {
        ItemStack s = p.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (s.isEmpty() || !ARMOR_CLASS.equals(s.getItem().getClass().getName())) return -1;
        try {
            if (beastField == null) { beastField = s.getItem().getClass().getField("beast"); }
            int b = beastField.getInt(s.getItem());
            return b >= 0 && b < PALETTE.length ? b : -1;
        } catch (ReflectiveOperationException | RuntimeException e) { return -1; }
    }

    /** Smoothed 0..1 power from sprinting, swinging, recent damage and low health. */
    static double power(EntityPlayer p, double time) {
        double[] st = POWER.computeIfAbsent(p.getEntityId(), k -> new double[] {0, time, -100, -100});
        double dt = Math.max(0, Math.min(5, time - st[1]));
        st[1] = time;
        if (p.hurtTime > 0 && p.hurtTime >= p.maxHurtTime - 1) st[2] = time;
        if (p.isSwingInProgress) st[3] = time;
        double target = 0.15;
        if (p.isSprinting()) target = Math.max(target, 0.55);
        target = Math.max(target, 0.9 * Math.max(0, 1 - (time - st[3]) / 20));
        target = Math.max(target, 1.0 * Math.max(0, 1 - (time - st[2]) / 30));
        if (p.getMaxHealth() > 0) target = Math.max(target, 0.4 + 0.6 * Math.max(0, (0.35 - p.getHealth() / p.getMaxHealth()) / 0.35));
        double rate = target > st[0] ? 0.35 : 0.04;
        st[0] += (target - st[0]) * (1 - Math.pow(1 - rate, dt));
        return st[0];
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer p, float limb, float limbAmount, float partial, float age, float yaw, float pitch, float scale) {
        int beast = beast(p);
        if (beast < 0 || p.isInvisible()) return;
        int core = PALETTE[beast][0], glow = PALETTE[beast][1];
        double t = p.ticksExisted + partial, power = power(p, t);
        double pulse = 0.5 + 0.5 * Math.sin(t * (0.12 + 0.25 * power));
        GlStateManager.pushMatrix();
        renderer.getMainModel().bipedBody.postRender(scale);
        VfxDraw.begin();
        try {
            if (p.isSneaking()) GlStateManager.translate(0, 0.2, 0);
            // Chest front plane: x right(-) / left(+), y down, z front = -0.135 (just proud of the armour).
            GlStateManager.translate(0, 0.26, -0.15);
            double px = 1 / 16.0;
            // 1. socket + bevelled frame (dark plates, face-shaded)
            VfxDraw.normal();
            VfxDraw.cube(0, 0, 0.012, px * 5.2, 0x15171C, 1f);
            int frame = VfxDraw.shade(glow, 0.32);
            for (int i = -2; i <= 2; i++) {
                VfxDraw.cube(i * px, -2.6 * px, -0.004, px * 1.05, frame, 1f);
                VfxDraw.cube(i * px, 2.6 * px, -0.004, px * 1.05, frame, 1f);
                VfxDraw.cube(-2.6 * px, i * px, -0.004, px * 1.05, frame, 1f);
                VfxDraw.cube(2.6 * px, i * px, -0.004, px * 1.05, frame, 1f);
            }
            for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) VfxDraw.cube(c[0] * 2.6 * px, c[1] * 2.6 * px, -0.012, px * 1.3, VfxDraw.shade(glow, 0.5), 1f);
            // 2. rune studs light in a rotating sequence
            VfxDraw.additive();
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4, lit = 0.5 + 0.5 * Math.cos(a - t * (0.1 + 0.2 * power));
                VfxDraw.cube(Math.cos(a) * 3.6 * px, Math.sin(a) * 3.6 * px, -0.01, px * 0.7, glow, (float) (0.25 + 0.75 * lit * (0.4 + 0.6 * power)));
            }
            // 3. energy veins to the shoulders and ribs with travelling pulses
            double[][] veins = {{-1, -1, -3.9, -4.4}, {1, -1, 3.9, -4.4}, {-1, 1, -3.6, 5.4}, {1, 1, 3.6, 5.4}, {0, 1, 0, 6.2}};
            for (int v = 0; v < veins.length; v++) {
                double x0 = veins[v][0] * 2.4 * px, y0 = veins[v][1] * 2.4 * px, x1 = veins[v][2] * px, y1 = veins[v][3] * px;
                int steps = 9;
                for (int s = 0; s <= steps; s++) {
                    double f = s / (double) steps, x = x0 + (x1 - x0) * f, y = y0 + (y1 - y0) * f;
                    double wave = Math.max(0, Math.cos((f - ((t * (0.04 + 0.08 * power) + v * 0.2) % 1.0)) * Math.PI * 3));
                    VfxDraw.cube(VfxDraw.pixel(x) , VfxDraw.pixel(y), -0.004, px * 0.55, glow, (float) ((0.18 + 0.82 * wave) * (0.35 + 0.65 * power) * (1 - f * 0.4)));
                }
            }
            // 4. inner shell + tumbling core cube
            GlStateManager.pushMatrix();
            GlStateManager.translate(0, 0, -0.03);
            GlStateManager.rotate((float) (t * (2 + 6 * power)), 0, 0, 1);
            VfxDraw.normal();
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                VfxDraw.cube(Math.cos(a) * 1.6 * px, Math.sin(a) * 1.6 * px, 0, px * 0.8, VfxDraw.shade(glow, 0.75), 1f);
            }
            GlStateManager.rotate((float) (t * (5 + 12 * power)), 1, 1, 0);
            VfxDraw.additive();
            double cs = px * (1.5 + 0.35 * pulse + 0.6 * power);
            VfxDraw.cube(0, 0, 0, cs, glow, 0.95f);
            VfxDraw.cube(0, 0, 0, cs * 0.6, core, 1f);
            GlStateManager.popMatrix();
            // 5. two orbit rings of voxel motes on tilted planes
            for (int ring = 0; ring < 2; ring++) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(0, 0, -0.03);
                GlStateManager.rotate(ring == 0 ? 25 : -35, 1, 0, 0);
                GlStateManager.rotate(ring == 0 ? 15 : -20, 0, 1, 0);
                double spin = t * (0.06 + 0.12 * power) * (ring == 0 ? 1 : -1);
                for (int i = 0; i < 6; i++) {
                    double a = spin + i * Math.PI / 3, r = (ring == 0 ? 3.0 : 3.8) * px;
                    VfxDraw.cube(Math.cos(a) * r, Math.sin(a) * r, 0, px * (ring == 0 ? 0.45 : 0.35), ring == 0 ? core : glow, (float) (0.55 + 0.45 * power));
                }
                GlStateManager.popMatrix();
            }
            // 6. bloom: soft halo + hard glow, and flaring light shafts
            VfxDraw.additive();
            VfxDraw.sphere(0, 0, -0.03, px * (3.2 + 2.5 * power + pulse), glow, (float) (0.06 + 0.10 * power), 10);
            VfxDraw.sphere(0, 0, -0.03, px * (1.9 + 1.0 * power), core, (float) (0.18 + 0.2 * power), 8);
            if (power > 0.25) {
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3 + t * 0.03, len = px * (4 + 10 * (power - 0.25) * (0.6 + 0.4 * Math.sin(t * 0.3 + i)));
                    double[][] in = {{0, 0, -0.04}, {Math.cos(a) * len, Math.sin(a) * len, -0.06}};
                    double[][] out = {{Math.cos(a + 0.5) * px * 0.8, Math.sin(a + 0.5) * px * 0.8, -0.04}, {Math.cos(a + 0.12) * len, Math.sin(a + 0.12) * len, -0.06}};
                    VfxDraw.ribbon(in, out, glow, (float) (0.35 * power), 0f);
                }
            }
        } finally {
            VfxDraw.end();
            GlStateManager.popMatrix();
        }
        // Sparks: voxel bits shed from the core when powered.
        if (power > 0.45 && p.ticksExisted % 3 == 0 && partial < 0.34 && p.world.rand.nextDouble() < power) {
            float body = p.renderYawOffset;
            double[] f = JutsuMath.forward(body);
            VoxelBits.spawn(p.posX + f[0] * 0.3, p.posY + (p.isSneaking() ? 1.0 : 1.2), p.posZ + f[2] * 0.3,
                    f[0] * 0.05 + (p.world.rand.nextDouble() - 0.5) * 0.06, 0.03 + p.world.rand.nextDouble() * 0.04, f[2] * 0.05 + (p.world.rand.nextDouble() - 0.5) * 0.06,
                    1, p.world.rand.nextBoolean() ? core : glow, 1f, 14 + p.world.rand.nextInt(8), -0.002, 0.93, false);
        }
    }

    @Override public boolean shouldCombineTextures() { return false; }
}
