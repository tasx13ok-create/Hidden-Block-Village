package village.vale.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import village.vale.Dodge;
import village.vale.DodgeMath;

/**
 * Dodge key (default V, rebindable under Controls > Vale Orders) plus the four animations:
 * an anime-style flash step: a low burst dash with a deep lean, afterimages left along the path
 * and speed lines. Front = arms-back dash, back = guarded backstep, left/right = mirrored side
 * steps. The lean is applied in RenderPlayerEvent; limb poses come from the biped pose hook. First person gets a short FOV kick instead.
 */
@SideOnly(Side.CLIENT)
public final class DodgeClient {
    public static final KeyBinding DODGE = new KeyBinding("key.valeorders.dodge", KeyConflictContext.IN_GAME, Keyboard.KEY_V, "key.categories.valeorders");
    private static final Map<Integer, double[]> ACTIVE = new ConcurrentHashMap<>(); // id -> {dir, startTick}
    private static long localCooldownUntil;
    private static final java.util.Set<Integer> PUSHED = ConcurrentHashMap.newKeySet();

    private DodgeClient() {}

    public static void initialize() {
        ClientRegistry.registerKeyBinding(DODGE);
        MinecraftForge.EVENT_BUS.register(new DodgeClient());
        VoxelBits.install();
    }

    private static double now(float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.world == null ? 0 : mc.world.getTotalWorldTime() + partial;
    }

    /** Server confirmed a dodge for entity {@code id}. */
    public static void start(int id, int dir) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (Minecraft.getMinecraft().world == null) return;
            ACTIVE.put(id, new double[] {dir, now(0)});
            Entity e = Minecraft.getMinecraft().world.getEntityByID(id);
            if (e != null) { java.util.ArrayDeque<double[]> t = new java.util.ArrayDeque<>(); t.add(new double[] {e.posX, e.posY, e.posZ}); TRAIL.put(id, t); }
            if (e != null) takeoff(e, dir);
        });
    }

    /** Shunshin-style take-off: ground chips burst out and a ring of smoke cubes puffs up. */
    private static void takeoff(Entity e, int dir) {
        java.util.Random r = VoxelBits.RNG;
        int ground = VoxelBits.groundColor(e);
        double[] v = DodgeMath.velocity(dir, e.rotationYaw);
        if (ground >= 0) for (int i = 0; i < 16; i++) {
            double a = r.nextDouble() * Math.PI * 2, sp = 0.08 + r.nextDouble() * 0.14;
            VoxelBits.spawn(e.posX + Math.cos(a) * 0.3, e.posY + 0.08, e.posZ + Math.sin(a) * 0.3,
                    Math.cos(a) * sp - v[0] * 0.12, 0.18 + r.nextDouble() * 0.2, Math.sin(a) * sp - v[2] * 0.12,
                    1 + r.nextInt(3), VoxelBits.vary(ground), 1f, 20 + r.nextInt(12), 0.04, 0.97, true);
        }
        for (int i = 0; i < 14; i++) {
            double a = i * Math.PI * 2 / 14 + r.nextDouble() * 0.3, rad = 0.35 + r.nextDouble() * 0.2;
            int grey = r.nextInt(3) == 0 ? 0xBDBDBD : r.nextBoolean() ? 0xE6E6E6 : 0xF7F7F7;
            VoxelBits.spawn(e.posX + Math.cos(a) * rad, e.posY + 0.2 + r.nextDouble() * 1.2, e.posZ + Math.sin(a) * rad,
                    Math.cos(a) * 0.05, 0.02 + r.nextDouble() * 0.03, Math.sin(a) * 0.05,
                    2 + r.nextInt(2), grey, 0.85f, 12 + r.nextInt(8), -0.003, 0.9, false);
        }
    }

    /** Skid stop: a low puff of smoke cubes and a few chips at the feet. */
    private static void landing(Entity e) {
        java.util.Random r = VoxelBits.RNG;
        int ground = VoxelBits.groundColor(e);
        for (int i = 0; i < 8; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            VoxelBits.spawn(e.posX + Math.cos(a) * 0.4, e.posY + 0.1, e.posZ + Math.sin(a) * 0.4, Math.cos(a) * 0.06, 0.01, Math.sin(a) * 0.06,
                    2 + r.nextInt(3), r.nextBoolean() ? 0xE6E6E6 : 0xC8C8C8, 0.8f, 10 + r.nextInt(6), -0.002, 0.88, false);
            if (ground >= 0) VoxelBits.spawn(e.posX, e.posY + 0.06, e.posZ, Math.cos(a) * 0.1, 0.12, Math.sin(a) * 0.1, 1, VoxelBits.vary(ground), 1f, 14, 0.04, 0.97, true);
        }
    }

    /** Age in ticks of an active dodge, or -1. */
    static double age(Entity e, float partial) {
        double[] a = ACTIVE.get(e.getEntityId());
        if (a == null) return -1;
        double age = now(partial) - a[1];
        if (age > DodgeMath.ANIM_TICKS + 1 || age < -1) { ACTIVE.remove(e.getEntityId()); return -1; }
        return Math.max(0, age);
    }

    static int dir(Entity e) { double[] a = ACTIVE.get(e.getEntityId()); return a == null ? -1 : (int) a[0]; }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.currentScreen != null) return;
        while (DODGE.isPressed()) {
            long t = mc.world.getTotalWorldTime();
            if (t < localCooldownUntil) continue;
            localCooldownUntil = t + DodgeMath.COOLDOWN_TICKS;
            Dodge.request(DodgeMath.direction(mc.player.movementInput.moveForward, mc.player.movementInput.moveStrafe));
        }
        for (Integer id : ACTIVE.keySet()) {
            Entity en = mc.world.getEntityByID(id);
            if (en == null) continue;
            java.util.ArrayDeque<double[]> t = TRAIL.computeIfAbsent(id, k -> new java.util.ArrayDeque<>());
            t.addLast(new double[] {en.posX, en.posY, en.posZ});
            while (t.size() > DodgeMath.AFTERIMAGES + 3) t.removeFirst();
        }
        TRAIL.keySet().retainAll(ACTIVE.keySet());
        // Ground chips skidding off the feet, and afterimages crumbling into pixels.
        for (Map.Entry<Integer, double[]> a : ACTIVE.entrySet()) {
            Entity en = mc.world.getEntityByID(a.getKey());
            if (en == null) continue;
            double age = mc.world.getTotalWorldTime() - a.getValue()[1];
            if (age < 0 || age > DodgeMath.ANIM_TICKS) continue;
            java.util.Random r = VoxelBits.RNG;
            int ground = VoxelBits.groundColor(en);
            if (ground >= 0 && age < DodgeMath.ANIM_TICKS * 0.8) for (int i = 0; i < 3; i++)
                VoxelBits.spawn(en.posX + (r.nextDouble() - 0.5) * 0.5, en.posY + 0.06, en.posZ + (r.nextDouble() - 0.5) * 0.5,
                        -en.motionX * 0.25 + (r.nextDouble() - 0.5) * 0.06, 0.08 + r.nextDouble() * 0.12, -en.motionZ * 0.25 + (r.nextDouble() - 0.5) * 0.06,
                        1 + r.nextInt(2), VoxelBits.vary(ground), 1f, 14 + r.nextInt(8), 0.035, 0.96, true);
            java.util.ArrayDeque<double[]> t = TRAIL.get(a.getKey());
            if (t != null && t.size() > 2) {
                double[] g = t.peekFirst();
                for (int i = 0; i < 4; i++)
                    VoxelBits.spawn(g[0] + (r.nextDouble() - 0.5) * 0.6, g[1] + r.nextDouble() * 1.8, g[2] + (r.nextDouble() - 0.5) * 0.6,
                            (r.nextDouble() - 0.5) * 0.02, 0.01 + r.nextDouble() * 0.03, (r.nextDouble() - 0.5) * 0.02,
                            1 + r.nextInt(2), r.nextBoolean() ? 0x8CD2FF : 0xD8F0FF, 0.75f, 10 + r.nextInt(8), -0.002, 0.95, false);
            }
            if ((long) age == DodgeMath.ANIM_TICKS - 1) landing(en);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void pre(RenderPlayerEvent.Pre e) {
        EntityPlayer p = e.getEntityPlayer();
        double age = age(p, e.getPartialRenderTick());
        if (age < 0) return;
        int dir = dir(p);
        float body = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * e.getPartialRenderTick();
        double[] lean = DodgeMath.lean(dir, age);
        double dip = 0.35 * DodgeMath.tuck(age); // stay low to the ground
        GlStateManager.pushMatrix();
        PUSHED.add(p.getEntityId());
        GlStateManager.translate(e.getX(), e.getY() - dip, e.getZ());
        GlStateManager.rotate(-body, 0, 1, 0);
        GlStateManager.rotate((float) lean[0], 1, 0, 0);
        GlStateManager.rotate((float) lean[1], 0, 0, 1);
        GlStateManager.rotate(body, 0, 1, 0);
        GlStateManager.translate(-e.getX(), -e.getY(), -e.getZ());
    }

    private static boolean ghosting;
    private static final Map<Integer, java.util.ArrayDeque<double[]>> TRAIL = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true) public void post(RenderPlayerEvent.Post e) {
        EntityPlayer p = e.getEntityPlayer();
        if (PUSHED.remove(p.getEntityId())) GlStateManager.popMatrix();
        if (ghosting) return;
        float partial = e.getPartialRenderTick();
        double age = age(p, partial);
        java.util.ArrayDeque<double[]> trail = TRAIL.get(p.getEntityId());
        if (age < 0 || trail == null) return;
        double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * partial, py = p.lastTickPosY + (p.posY - p.lastTickPosY) * partial, pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partial;
        float yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * partial;
        // Afterimages: translucent blue copies of the body left along the path.
        ghosting = true;
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.depthMask(false);
        try {
            int i = 0;
            for (java.util.Iterator<double[]> it = trail.descendingIterator(); it.hasNext() && i < DodgeMath.AFTERIMAGES; ) {
                double[] g = it.next();
                double dx = g[0] - px, dy = g[1] - py, dz = g[2] - pz;
                if (dx * dx + dz * dz < 0.36) continue;
                float a = (float) DodgeMath.ghostAlpha(i, age);
                if (a > 0.01) {
                    GlStateManager.color(0.55f, 0.82f, 1f, a);
                    e.getRenderer().doRender((net.minecraft.client.entity.AbstractClientPlayer) p, e.getX() + dx, e.getY() + dy, e.getZ() + dz, yaw, partial);
                }
                i++;
            }
        } finally {
            ghosting = false;
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
        }
        // Speed lines streaming back from the body.
        if (age > DodgeMath.ANIM_TICKS * 0.75) return;
        double[] last = trail.peekFirst();
        double vx = px - last[0], vz = pz - last[2], n = Math.sqrt(vx * vx + vz * vz);
        if (n < 0.3) return;
        vx /= n; vz /= n;
        float fade = (float) (1 - age / (DodgeMath.ANIM_TICKS * 0.75));
        VfxDraw.begin(e.getX(), e.getY(), e.getZ());
        try {
            net.minecraft.client.renderer.GlStateManager.depthMask(true);
            for (int k = 0; k < 9; k++) {
                double side = (village.vale.WeaponMath.hash(p.getEntityId(), k) - 0.5) * 1.1, h = 0.15 + 1.6 * village.vale.WeaponMath.hash(p.getEntityId(), k + 20);
                double len = 1.2 + 1.8 * village.vale.WeaponMath.hash(p.getEntityId(), k + 40), back = 0.3 + 0.4 * village.vale.WeaponMath.hash((long) age, k);
                double ox = -vz * side, oz = vx * side;
                // A streak is a row of single-pixel cubes snapped to the pixel grid, fading toward its tail.
                int steps = (int) (len / (VoxelBits.PX * 3));
                for (int q = 0; q < steps; q++) {
                    double d = back + q * VoxelBits.PX * 3, f = 1 - q / (double) steps;
                    double cx = VfxDraw.pixel(ox - vx * d), cy = VfxDraw.pixel(h), cz = VfxDraw.pixel(oz - vz * d);
                    VfxDraw.cube(cx, cy, cz, VoxelBits.PX * (q < steps / 3 ? 2 : 1), q % 4 == 0 ? 0x9DD6FF : 0xFFFFFF, (float) (fade * f * 0.9));
                }
            }
        } finally {
            VfxDraw.end();
        }
    }

    @SubscribeEvent public void fov(FOVUpdateEvent e) {
        double age = age(e.getEntity(), Minecraft.getMinecraft().getRenderPartialTicks());
        if (age >= 0) e.setNewfov((float) (e.getNewfov() * (1 + 0.12 * DodgeMath.tuck(age))));
    }

    /** Limb tucks per direction (called from the biped pose hook after vanilla angles). */
    public static void pose(ModelBiped m, Entity entity, float ageInTicks) {
        if (!(entity instanceof EntityPlayer) || ageInTicks == 0) return;
        double age = age(entity, Minecraft.getMinecraft().getRenderPartialTicks());
        if (age < 0) return;
        int dir = dir(entity);
        float k = (float) DodgeMath.tuck(age);
        switch (dir) {
            case DodgeMath.FRONT: // dash: arms swept straight back, long stride, eyes forward
                m.bipedHead.rotateAngleX = lerp(m.bipedHead.rotateAngleX, -0.6f, k);
                m.bipedRightArm.rotateAngleX = lerp(m.bipedRightArm.rotateAngleX, 1.35f, k);
                m.bipedLeftArm.rotateAngleX = lerp(m.bipedLeftArm.rotateAngleX, 1.35f, k);
                m.bipedRightArm.rotateAngleZ = lerp(m.bipedRightArm.rotateAngleZ, 0.25f, k);
                m.bipedLeftArm.rotateAngleZ = lerp(m.bipedLeftArm.rotateAngleZ, -0.25f, k);
                m.bipedRightLeg.rotateAngleX = lerp(m.bipedRightLeg.rotateAngleX, -1.0f, k);
                m.bipedLeftLeg.rotateAngleX = lerp(m.bipedLeftLeg.rotateAngleX, 0.8f, k);
                break;
            case DodgeMath.BACK: // backstep: guard up, front foot skidding, low stance
                m.bipedHead.rotateAngleX = lerp(m.bipedHead.rotateAngleX, 0.35f, k);
                m.bipedRightArm.rotateAngleX = lerp(m.bipedRightArm.rotateAngleX, -1.45f, k);
                m.bipedLeftArm.rotateAngleX = lerp(m.bipedLeftArm.rotateAngleX, -1.2f, k);
                m.bipedRightArm.rotateAngleY = lerp(m.bipedRightArm.rotateAngleY, -0.45f, k);
                m.bipedLeftArm.rotateAngleY = lerp(m.bipedLeftArm.rotateAngleY, 0.45f, k);
                m.bipedRightLeg.rotateAngleX = lerp(m.bipedRightLeg.rotateAngleX, -0.9f, k);
                m.bipedLeftLeg.rotateAngleX = lerp(m.bipedLeftLeg.rotateAngleX, 0.55f, k);
                break;
            default: { // side step: lead hand trailing low, other arm guarding, legs split
                boolean left = dir == DodgeMath.LEFT;
                ModelRendererPair lead = left ? new ModelRendererPair(m.bipedRightArm, m.bipedLeftArm) : new ModelRendererPair(m.bipedLeftArm, m.bipedRightArm);
                float s = left ? 1 : -1;
                lead.trail.rotateAngleZ = lerp(lead.trail.rotateAngleZ, 1.25f * s, k);
                lead.trail.rotateAngleX = lerp(lead.trail.rotateAngleX, 0.3f, k);
                lead.guard.rotateAngleX = lerp(lead.guard.rotateAngleX, -1.35f, k);
                lead.guard.rotateAngleY = lerp(lead.guard.rotateAngleY, 0.6f * s, k);
                m.bipedRightLeg.rotateAngleZ = lerp(m.bipedRightLeg.rotateAngleZ, 0.6f, k);
                m.bipedLeftLeg.rotateAngleZ = lerp(m.bipedLeftLeg.rotateAngleZ, -0.6f, k);
                m.bipedRightLeg.rotateAngleX = lerp(m.bipedRightLeg.rotateAngleX, 0, k);
                m.bipedLeftLeg.rotateAngleX = lerp(m.bipedLeftLeg.rotateAngleX, 0, k);
                m.bipedHead.rotateAngleZ = lerp(m.bipedHead.rotateAngleZ, -0.35f * s, k);
                break;
            }
        }
        m.bipedHeadwear.rotateAngleX = m.bipedHead.rotateAngleX;
        m.bipedHeadwear.rotateAngleZ = m.bipedHead.rotateAngleZ;
    }

    private static final class ModelRendererPair {
        final net.minecraft.client.model.ModelRenderer trail, guard;
        ModelRendererPair(net.minecraft.client.model.ModelRenderer trail, net.minecraft.client.model.ModelRenderer guard) { this.trail = trail; this.guard = guard; }
    }

    private static float lerp(float a, float b, float k) { return a + (b - a) * k; }
}
