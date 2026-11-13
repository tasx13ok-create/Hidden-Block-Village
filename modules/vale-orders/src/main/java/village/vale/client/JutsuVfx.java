package village.vale.client;

import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import village.vale.JutsuMath;
import village.vale.arsenal.HandTechnique;

/**
 * Original procedural visuals for hand-held techniques. Drawn every frame at the caster's animated
 * palm (or first-person anchor), so the effect never lags the body. No textures or shaders are
 * required; additive full-bright geometry stays readable without OptiFine. {@link #detail}
 * (client config) scales band/mote/bolt counts for low-end machines and reduced effects.
 */
@SideOnly(Side.CLIENT)
public final class JutsuVfx extends Render<HandTechnique> {
    /** 0 = reduced, 1 = normal, 2 = high. */
    public static int detail = 1;

    public JutsuVfx(RenderManager manager) {
        super(manager);
        shadowSize = 0;
    }

    public static void configure(File folder) {
        Configuration c = new Configuration(new File(folder, "valeorders-client.cfg"));
        c.load();
        detail = c.getInt("jutsuDetail", "effects", 1, 0, 2,
            "Hand-technique effect density: 0 reduced (fewer bands/motes, no flicker), 1 normal, 2 high.");
        if (c.hasChanged()) c.save();
    }

    @Override protected ResourceLocation getEntityTexture(HandTechnique entity) { return null; }

    @Override public boolean shouldRender(HandTechnique e, ICamera camera, double x, double y, double z) { return true; }

    /** Arm pitch (radians from hanging) used for the pose and the palm anchor. */
    public static double armPitch(String style) {
        switch (style) {
            case "spiral": return 1.45;
            case "charge": return 0.95;
            case "fireedge": case "windedge": return 1.15;
            default: return 0.18;
        }
    }

    private static boolean posed(String style) {
        return style.equals("spiral") || style.equals("charge") || style.equals("fireedge") || style.equals("windedge");
    }

    /** Called from RunPoses.finish after vanilla/run poses; keeps swings and item use intact. */
    public static void pose(ModelBiped model, Entity entity, float age) {
        if (!(entity instanceof EntityPlayer) || !entity.world.isRemote) return;
        EntityPlayer p = (EntityPlayer) entity;
        if (Math.abs(age - p.ticksExisted) > 2) return; // first-person hand pass
        HandTechnique h = HandTechnique.CLIENT_BY_OWNER.get(p.getEntityId());
        if (h == null || h.isDead || !posed(h.style()) || p.isHandActive() || p.isRiding()) return;
        float blend = (float) JutsuMath.easeOut(Math.min(1, (h.stateAge() + 1) / 6.0));
        float swing = p.swingProgress > 0 ? 0.4f : 1f; // let attack swings read through
        float target = (float) -armPitch(h.style());
        model.bipedRightArm.rotateAngleX += (target - model.bipedRightArm.rotateAngleX) * blend * swing;
        model.bipedRightArm.rotateAngleY += (-0.12f - model.bipedRightArm.rotateAngleY) * blend * swing;
        model.bipedRightArm.rotateAngleZ *= 1 - blend;
        if (h.style().equals("charge")) {
            // Left hand grips the right wrist to brace the committed thrust.
            model.bipedLeftArm.rotateAngleX += (target - model.bipedLeftArm.rotateAngleX) * blend;
            model.bipedLeftArm.rotateAngleY += (0.62f - model.bipedLeftArm.rotateAngleY) * blend;
            model.bipedLeftArm.rotateAngleZ *= 1 - blend;
        }
    }

    // ---------------------------------------------------------------------------------------
    @Override public void doRender(HandTechnique e, double x, double y, double z, float yaw, float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        double camX = e.lastTickPosX + (e.posX - e.lastTickPosX) * partial - x;
        double camY = e.lastTickPosY + (e.posY - e.lastTickPosY) * partial - y;
        double camZ = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partial - z;
        String style = e.style();
        byte state = e.state();
        double t = e.ticksExisted + partial, age = e.stateAge() + partial;
        double[] at;
        Entity ownerEntity = e.world.getEntityByID(e.ownerId());
        EntityPlayer owner = ownerEntity instanceof EntityPlayer ? (EntityPlayer) ownerEntity : null;
        if (state == HandTechnique.IMPACT || owner == null) {
            at = new double[] {camX + x, camY + y, camZ + z};
        } else {
            at = anchor(owner, style, partial, mc);
        }
        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        try {
            GlStateManager.translate(at[0] - camX, at[1] - camY, at[2] - camZ);
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            GlStateManager.depthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            double scale = JutsuMath.scale(state, (int) age);
            switch (style) {
                case "spiral": spiral(state, t, age, scale); break;
                case "charge": lightning(e, state, t, age, scale, 7, 0.75, false); break;
                case "lightmode": if (state == HandTechnique.IMPACT) lightning(e, state, t, age, 1, 6, 1.2, false); else bodyArcs(e, owner, t, scale); break;
                case "fireedge": flame(state, t, age, scale, 0xFF9A2E, 0xFFE27A); break;
                case "lava": flame(state, t, age, scale, 0xE8411A, 0xFF8A2A); lavaGlobs(t, scale); break;
                case "windedge": wind(state, t, age, scale); break;
                default: break;
            }
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
            GL11.glPopAttrib();
            GlStateManager.depthMask(true);
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private double[] anchor(EntityPlayer p, String style, float partial, Minecraft mc) {
        double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * partial;
        double py = p.lastTickPosY + (p.posY - p.lastTickPosY) * partial;
        double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * partial;
        if (style.equals("lightmode")) return new double[] {px, py + 0.95, pz};
        boolean self = p == mc.getRenderViewEntity() && mc.gameSettings.thirdPersonView == 0;
        if (self) {
            float yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * partial;
            float pitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * partial;
            return JutsuMath.firstPerson(px, py + p.getEyeHeight(), pz, yaw, pitch);
        }
        float body = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * partial;
        return JutsuMath.palm(px, py, pz, body, armPitch(style), p.isSneaking(), true);
    }

    // ---- primitives --------------------------------------------------------------------------
    private static void color(BufferBuilder b, double x, double y, double z, int rgb, float a) {
        b.pos(x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) (Math.max(0, Math.min(1, a)) * 255)).endVertex();
    }

    private static void sphere(double r, int rgb, float alpha, int steps) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * i / steps - Math.PI / 2, a1 = Math.PI * (i + 1) / steps - Math.PI / 2;
            for (int j = 0; j < steps * 2; j++) {
                double o0 = Math.PI * j / steps, o1 = Math.PI * (j + 1) / steps;
                color(b, r * Math.cos(a0) * Math.cos(o0), r * Math.sin(a0), r * Math.cos(a0) * Math.sin(o0), rgb, alpha);
                color(b, r * Math.cos(a0) * Math.cos(o1), r * Math.sin(a0), r * Math.cos(a0) * Math.sin(o1), rgb, alpha);
                color(b, r * Math.cos(a1) * Math.cos(o1), r * Math.sin(a1), r * Math.cos(a1) * Math.sin(o1), rgb, alpha);
                color(b, r * Math.cos(a1) * Math.cos(o0), r * Math.sin(a1), r * Math.cos(a1) * Math.sin(o0), rgb, alpha);
            }
        }
        t.draw();
    }

    private void billboard(double x, double y, double z, double size, int rgb, float alpha) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(-renderManager.playerViewY, 0, 1, 0);
        GlStateManager.rotate((renderManager.options.thirdPersonView == 2 ? -1 : 1) * renderManager.playerViewX, 1, 0, 0);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        color(b, -size, -size, 0, rgb, alpha); color(b, size, -size, 0, rgb, alpha);
        color(b, size, size, 0, rgb, alpha); color(b, -size, size, 0, rgb, alpha);
        t.draw();
        GlStateManager.popMatrix();
    }

    private static void line(double[][] pts, double scale, int rgb, float a0, float a1, float width) {
        GL11.glLineWidth(width);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < pts.length; i++) {
            float f = pts.length < 2 ? 0 : i / (float) (pts.length - 1);
            color(b, pts[i][0] * scale, pts[i][1] * scale, pts[i][2] * scale, rgb, a0 + (a1 - a0) * f);
        }
        t.draw();
    }

    // ---- Spiral Palm: compressed rotating sphere ----------------------------------------------
    private void spiral(byte state, double t, double age, double scale) {
        double r = 0.34;
        if (state == HandTechnique.IMPACT) { spiralBurst(t, age); return; }
        int bands = detail == 0 ? 6 : detail == 1 ? 11 : 16, motes = detail == 0 ? 8 : detail == 1 ? 18 : 30;
        double charge = state == HandTechnique.HELD ? JutsuMath.easeOut(age / JutsuMath.CHARGE_TICKS) : 1;
        double pulse = 1 + 0.04 * Math.sin(t * 1.7);
        double R = r * scale * pulse;
        if (R <= 0.002) return;
        // Outer wind envelope, translucent shell, bright core.
        sphere(R * 1.28, 0x5AA8FF, 0.08f, 10);
        sphere(R, 0x7FC8FF, 0.22f, 12);
        sphere(R * 0.62, 0xBFE8FF, 0.35f, 10);
        sphere(R * 0.34, 0xFFFFFF, 0.85f, 8);
        // Many spiral bands precessing on different axes = visible multi-directional rotation.
        for (int band = 0; band < bands; band++) {
            double[][] pts = new double[26][];
            for (int i = 0; i < pts.length; i++) pts[i] = JutsuMath.spiralBand(band, bands, i / 25.0, t);
            int col = band % 3 == 0 ? 0xFFFFFF : band % 3 == 1 ? 0x9BD7FF : 0x4C9DFF;
            line(pts, R * (0.82 + 0.16 * (band % 4) / 3.0), col, 0.0f, 0.85f, detail == 0 ? 1.5f : 2.5f);
        }
        // Wind motes orbit (and spiral inward while charging).
        for (int i = 0; i < motes; i++) {
            double[] m = JutsuMath.mote(i, t, charge);
            billboard(m[0] * R, m[1] * R, m[2] * R, 0.018 + 0.012 * (i % 3), i % 4 == 0 ? 0xFFFFFF : 0x8CCBFF, 0.55f);
        }
        // Thin precessing rings sell the spin from any camera angle.
        for (int ring = 0; ring < 2; ring++) {
            double[][] pts = new double[33][];
            double tilt = t * (ring == 0 ? 0.37 : -0.29) + ring * 1.2;
            for (int i = 0; i <= 32; i++) {
                double a = i * Math.PI / 16;
                double cx = Math.cos(a) * 1.12, cz = Math.sin(a) * 1.12;
                pts[i] = new double[] {cx, cz * Math.sin(tilt), cz * Math.cos(tilt)};
            }
            line(pts, R, 0xD6F0FF, 0.35f, 0.35f, 1.5f);
        }
    }

    private void spiralBurst(double t, double age) {
        double f = Math.min(1, age / JutsuMath.IMPACT_TICKS), fade = 1 - f;
        double radius = 0.4 + 2.9 * JutsuMath.easeOut(f);
        sphere(0.55 * (1 - f) + 0.05, 0xFFFFFF, (float) (0.9 * fade), 10);
        sphere(0.9 * JutsuMath.easeOut(f * 1.6) * fade + 0.05, 0x8CCBFF, (float) (0.3 * fade), 10);
        int arms = detail == 0 ? 6 : 10;
        for (int arm = 0; arm < arms; arm++) {
            double[][] pts = new double[20][];
            for (int i = 0; i < 20; i++) {
                double s = i / 19.0, a = arm * Math.PI * 2 / arms + s * 2.6 + age * 0.55;
                pts[i] = new double[] {Math.cos(a) * s * radius, (s - 0.5) * 0.25 * fade, Math.sin(a) * s * radius};
            }
            line(pts, 1, arm % 2 == 0 ? 0xFFFFFF : 0x7FC8FF, (float) (0.9 * fade), 0f, 3f);
        }
        double[][] ring = new double[41][];
        for (int i = 0; i <= 40; i++) { double a = i * Math.PI / 20; ring[i] = new double[] {Math.cos(a) * radius, 0, Math.sin(a) * radius}; }
        line(ring, 1, 0xD6F0FF, (float) (0.7 * fade), (float) (0.7 * fade), 2.5f);
    }

    // ---- Storm Lance / Storm Stride: crackling lightning --------------------------------------
    private void lightning(HandTechnique e, byte state, double t, double age, double scale, int bolts, double reach, boolean body) {
        boolean impact = state == HandTechnique.IMPACT;
        double fade = impact ? 1 - Math.min(1, age / JutsuMath.IMPACT_TICKS) : 1;
        long flicker = detail == 0 ? (long) (t / 4) : (long) (t / 2);
        sphere(0.11 * scale * (impact ? 2.5 * fade : 1), 0xFFFFFF, (float) (0.9 * fade), 8);
        sphere(0.22 * scale * (impact ? 2 : 1), 0x6FB8FF, (float) (0.25 * fade), 8);
        int n = detail == 0 ? Math.max(3, bolts / 2) : detail == 2 ? bolts + 4 : bolts;
        double len = reach * scale * (impact ? 3.2 : 1);
        for (int i = 0; i < n; i++) {
            long seed = e.getEntityId() * 31L + i * 977L + flicker * 7919L;
            double u = ((seed * 0x9E3779B97F4A7C15L) >>> 40) / (double) (1L << 24);
            double v = ((seed * 0xC2B2AE3D27D4EB4FL) >>> 40) / (double) (1L << 24);
            double theta = u * Math.PI * 2, phi = Math.acos(2 * v - 1);
            double[] dir = {Math.sin(phi) * Math.cos(theta), Math.cos(phi), Math.sin(phi) * Math.sin(theta)};
            double[][] pts = JutsuMath.bolt(seed, dir, len * (0.55 + 0.45 * u), 7, 0.07 * len);
            line(pts, 1, 0xEAF6FF, (float) fade, (float) (0.2 * fade), 2.5f);
            line(pts, 1, 0x4FA3FF, (float) (0.5 * fade), 0f, 5f);
        }
    }

    private void bodyArcs(HandTechnique e, EntityPlayer owner, double t, double scale) {
        long flicker = detail == 0 ? (long) (t / 4) : (long) (t / 2);
        int n = detail == 0 ? 3 : detail == 2 ? 9 : 6;
        for (int i = 0; i < n; i++) {
            long seed = e.getEntityId() * 131L + i * 7L + flicker * 104729L;
            double u = ((seed * 0x9E3779B97F4A7C15L) >>> 40) / (double) (1L << 24);
            double v = ((seed * 0xC2B2AE3D27D4EB4FL) >>> 40) / (double) (1L << 24);
            double a = u * Math.PI * 2, h = (v - 0.5) * 1.6;
            double[] dir = {Math.cos(a + 1.4), (v - 0.5) * 0.6, Math.sin(a + 1.4)};
            double[][] pts = JutsuMath.bolt(seed, dir, 0.38 * scale, 5, 0.05);
            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.cos(a) * 0.36, h, Math.sin(a) * 0.36);
            line(pts, 1, 0xEAF6FF, 0.9f, 0.1f, 2f);
            GlStateManager.popMatrix();
        }
    }

    // ---- Cinder Edge / Molten Mantle: flame tongues -------------------------------------------
    private void flame(byte state, double t, double age, double scale, int base, int tip) {
        boolean impact = state == HandTechnique.IMPACT;
        double fade = impact ? 1 - Math.min(1, age / JutsuMath.IMPACT_TICKS) : 1;
        double grow = impact ? 1 + 2.5 * JutsuMath.easeOut(age / JutsuMath.IMPACT_TICKS) : scale;
        int tongues = detail == 0 ? 6 : detail == 2 ? 14 : 10;
        Tessellator tes = Tessellator.getInstance();
        BufferBuilder b = tes.getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < tongues; i++) {
            double a = i * Math.PI * 2 / tongues + t * 0.08;
            double flick = detail == 0 ? 1 : 0.75 + 0.35 * Math.sin(t * 0.9 + i * 2.1);
            double h = (0.22 + 0.16 * ((i * 7) % 5) / 4.0) * flick * grow, w = 0.06 * grow, rr = 0.1 * grow;
            double bx = Math.cos(a) * rr, bz = Math.sin(a) * rr, sx = -Math.sin(a) * w, sz = Math.cos(a) * w;
            double sway = Math.sin(t * 0.5 + i) * 0.04 * grow;
            color(b, bx - sx, -0.05, bz - sz, base, (float) (0.75 * fade));
            color(b, bx + sx, -0.05, bz + sz, base, (float) (0.75 * fade));
            color(b, bx * 0.4 + sway, h, bz * 0.4, tip, 0f);
        }
        tes.draw();
        sphere(0.12 * grow, tip, (float) (0.35 * fade), 8);
    }

    private void lavaGlobs(double t, double scale) {
        int globs = detail == 0 ? 3 : 6;
        for (int i = 0; i < globs; i++) {
            double a = t * 0.12 + i * Math.PI * 2 / globs, r = 0.24 * scale;
            GlStateManager.pushMatrix();
            GlStateManager.translate(Math.cos(a) * r, 0.05 * Math.sin(t * 0.3 + i), Math.sin(a) * r);
            sphere(0.035 + 0.01 * (i % 2), i % 2 == 0 ? 0xFF5A1A : 0xFFB03A, 0.85f, 5);
            GlStateManager.popMatrix();
        }
    }

    // ---- Gale Edge: spinning wind crescents ---------------------------------------------------
    private void wind(byte state, double t, double age, double scale) {
        boolean impact = state == HandTechnique.IMPACT;
        double fade = impact ? 1 - Math.min(1, age / JutsuMath.IMPACT_TICKS) : 1;
        double radius = (impact ? 0.4 + 2.2 * JutsuMath.easeOut(age / JutsuMath.IMPACT_TICKS) : 0.36) * (impact ? 1 : scale);
        int blades = detail == 0 ? 2 : 3;
        for (int k = 0; k < blades; k++) {
            double start = t * 0.75 + k * Math.PI * 2 / blades;
            double[][] pts = new double[14][];
            for (int i = 0; i < 14; i++) {
                double a = start + i * 0.17, rr = radius * (0.75 + 0.25 * Math.sin(i / 13.0 * Math.PI));
                pts[i] = new double[] {Math.cos(a) * rr, (k - 1) * 0.07, Math.sin(a) * rr};
            }
            line(pts, 1, 0xE8FFF4, 0f, (float) (0.9 * fade), 3f);
            line(pts, 1, 0x9DFFD5, 0f, (float) (0.35 * fade), 6f);
        }
    }
}
