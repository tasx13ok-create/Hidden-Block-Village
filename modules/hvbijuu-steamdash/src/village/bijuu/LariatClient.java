package village.bijuu;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

/**
 * The Gyuki Lariat, client side: the four beats as an animator would stage them.
 *
 *   anticipation  the body sinks and leans, a crimson aura ramps up, eight energy ribbons whip out of
 *                 the tail base and the view tightens
 *   blink         a vacuum ring collapses where you stood, a mach cone tears open along the path, six
 *                 crimson afterimages hang in the air behind you, lightning crawls along the forearm
 *   hit-stop      two inverted frames, a lightning web out of the contact point, the victim's spine
 *                 bends back, Dutch roll and shake
 *   release       rings and a ground ripple roll out, distant thunder; the ribbons trail and settle
 * Nothing here changes the world. Times are the shared effect clock (game time), like the Steam Dash.
 */
public final class LariatClient {
    static final class C {
        final int caster; int victim = -1; final long start = DashClient.now();
        long blink = -1, impact = -1, release = -1, end = -1;
        double[] dir = {0, 0, 1};
        double sx, sy, sz, ex, ey, ez, cx, cy, cz;
        C(int caster) { this.caster = caster; }
    }

    static final Map<Integer, C> CUES = new HashMap<>();
    static int webFrames, ribbonFrames, afterimageFrames;   // diagnostics for QA
    private static final Map<Integer, Boolean> PUSHED = new HashMap<>();
    private static final Set<Integer> BENT = new HashSet<>();
    private static boolean rendering;

    private static Minecraft mc() { return Minecraft.getMinecraft(); }
    private static boolean local(int id) { return mc().player != null && mc().player.getEntityId() == id; }
    static C mine() { return mc().player == null ? null : CUES.get(mc().player.getEntityId()); }

    static void handle(DashNet.Fx m) {
        Minecraft mc = mc();
        if (mc.world == null) return;
        long t = DashClient.now();
        C c = CUES.get(m.attacker);
        switch (m.kind) {
            case DashNet.LARIAT_START:
                c = new C(m.attacker);
                c.dir = new double[] {m.dx, m.dy, m.dz};
                c.sx = m.x; c.sy = m.y; c.sz = m.z;
                CUES.put(m.attacker, c);
                DashClient.shake(t, 25);
                break;
            case DashNet.LARIAT_BLINK:
                if (c == null) break;
                c.blink = t;
                c.sx = m.x; c.sy = m.y; c.sz = m.z; c.ex = m.dx; c.ey = m.dy; c.ez = m.dz;
                if (m.data.length > 0) c.victim = m.data[0];
                DashClient.boom(c.sx, c.sy + 0.1, c.sz, new double[] {0, 1, 0}, 0.4, 3.4, 420, 1f, 0.92f, 0.9f, 0.6f);
                DashClient.boom(c.sx, c.sy + 1.0, c.sz, c.dir, 0.3, 2.6, 300, 0.95f, 0.88f, 0.92f, 0.55f);
                DashClient.shake(t, 60);
                break;
            case DashNet.LARIAT_IMPACT:
                if (c == null) break;
                c.impact = t; c.cx = m.x; c.cy = m.y; c.cz = m.z;
                DashClient.boom(c.cx, c.cy, c.cz, c.dir, 0.15, 2.2, 200, 0.85f, 0.97f, 1f, 0.95f);
                DashClient.shake(t, 160);
                try { KuramaTailAnimator.strike(c.caster, (float) Math.toDegrees(Math.atan2(-c.dir[0], c.dir[2])), 0.1f); } catch (Throwable ignored) {}
                break;
            case DashNet.LARIAT_RELEASE:
                if (c == null) break;
                c.release = t;
                DashClient.boom(c.cx, c.cy, c.cz, c.dir, 0.4, 5.5, 520, 1f, 1f, 1f, 0.85f);
                DashClient.boom(c.cx, c.cy - 0.6, c.cz, new double[] {0, 1, 0}, 0.4, 4.5, 600, 0.9f, 0.86f, 0.82f, 0.5f);
                DashClient.ripple(c.cx, c.cy - 1.4, c.cz, 9);
                DashClient.thunder(c.cx, c.cy, c.cz, 1.6f, 0.9f);
                DashClient.shake(t, 120);
                break;
            case DashNet.LARIAT_END:
                if (c != null) c.end = t;
                break;
            default: break;
        }
    }

    static boolean involved(C c) {
        if (local(c.caster) || local(c.victim)) return true;
        return mc().player != null && mc().player.getDistanceSq(c.cx, c.cy, c.cz) < 32 * 32;
    }

    /** Two inverted frames from contact, for anyone involved or close. */
    static boolean invertWanted(long t) {
        if (!SteamDash.impactFrames) return false;
        for (C c : CUES.values()) if (c.impact >= 0 && LariatMath.invertFrame((t - c.impact) / 50.0) && involved(c)) return true;
        return false;
    }

    private static boolean held(C c, long t) { return c.impact >= 0 && (c.release < 0 || t < c.release) && c.end < 0; }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (mc().world == null) { CUES.clear(); return; }
        long t = DashClient.now();
        for (Iterator<C> it = CUES.values().iterator(); it.hasNext();) { C c = it.next(); if (c.end >= 0 && t - c.end > 1500 || t - c.start > 10000) it.remove(); }
    }

    // ---- camera: FOV, Dutch roll, shake (the director adds its own shots on top) ----

    @SubscribeEvent public void fov(EntityViewRenderEvent.FOVModifier e) {
        C c = mine();
        if (c == null || !SteamDash.cameraEffects || DashCamera.active()) return;
        double blinkMs = LariatMath.ANTICIPATION * 50.0, since = DashClient.now() - c.start;
        if (c.blink >= 0) since = blinkMs + (DashClient.now() - c.blink);
        e.setFOV((float) (e.getFOV() * LariatMath.fov(since, blinkMs)));
    }

    @SubscribeEvent(priority = EventPriority.LOW) public void camera(EntityViewRenderEvent.CameraSetup e) {
        if (!SteamDash.cameraEffects) return;
        long t = DashClient.now();
        for (C c : CUES.values()) {
            if (c.impact < 0 || !involved(c)) continue;
            double roll = LariatMath.dutch(t - c.impact, LariatMath.HITSTOP * 50.0);
            if (roll > 0.01) e.setRoll(e.getRoll() + (float) (roll * (c.caster % 2 == 0 ? 1 : -1)));
        }
    }

    // ---- the body: crouch and lean, the arm held out, the crimson pass; the victim's spine ----

    @SubscribeEvent(priority = EventPriority.LOWEST) public void prePlayer(RenderPlayerEvent.Pre e) {
        if (rendering) return;
        EntityPlayer p = e.getEntityPlayer();
        C c = CUES.get(p.getEntityId());
        if (c == null) return;
        long t = DashClient.now();
        double[] save = {p.swingProgress, p.prevSwingProgress};
        RESTORE.put(p.getEntityId(), save);
        if (c.blink >= 0 && (c.release < 0 || t - c.release < LariatMath.ARM_HOLD * 50)) { p.swingProgress = p.prevSwingProgress = 0.42f; }   // clothesline arm out
        GlStateManager.pushMatrix();
        PUSHED.put(p.getEntityId(), true);
        double x = e.getX(), y = e.getY(), z = e.getZ();
        float lean; double sink = 0;
        if (c.blink < 0) { double u = Math.min(1, (t - c.start) / (LariatMath.ANTICIPATION * 50.0)); lean = (float) (22 * u); sink = 0.18 * u; }
        else if (held(c, t) || c.release >= 0 && t - c.release < 250) lean = 30;
        else lean = (float) Math.max(0, 30 - (t - Math.max(c.blink, c.release)) * 0.08);
        if (lean > 0.1f) {
            double hx = c.dir[0], hz = c.dir[2], l = Math.max(1e-4, Math.sqrt(hx * hx + hz * hz));
            GlStateManager.translate(x, y + 0.9 - sink, z);
            GlStateManager.rotate(lean, (float) (hz / l), 0, (float) (-hx / l));
            GlStateManager.translate(-x, -(y + 0.9), -z);
        }
    }

    private static final Map<Integer, double[]> RESTORE = new HashMap<>();

    @SubscribeEvent public void postPlayer(RenderPlayerEvent.Post e) {
        if (rendering) return;
        EntityPlayer p = e.getEntityPlayer();
        if (PUSHED.remove(p.getEntityId()) == null) return;
        C c = CUES.get(p.getEntityId());
        long t = DashClient.now();
        if (c != null && p instanceof AbstractClientPlayer) {
            float heat;
            // A glaze over the armor, never a silhouette: the owner's Gyuki armor has to read through it.
            if (c.blink < 0) heat = 0.22f * (float) Math.min(1, (t - c.start) / (LariatMath.ANTICIPATION * 50.0)) + 0.04f * (float) Math.sin(t * 0.05);
            else if (held(c, t)) heat = 0.28f;
            else heat = (float) Math.max(0, 0.22 - (t - Math.max(c.blink, c.release)) / 2500.0);
            if (heat > 0.01f) {
                DashClient.Pose pose = DashClient.pose((AbstractClientPlayer) p, e.getPartialRenderTick(), 0, 0, 0);
                pose.x = e.getX(); pose.y = e.getY(); pose.z = e.getZ(); pose.lean = 0;
                rendering = true;
                try { DashClient.tinted((AbstractClientPlayer) p, e.getRenderer(), pose, 0, 0, 0, 1.0f, 0.13f, 0.07f, heat, e.getPartialRenderTick()); }
                finally { rendering = false; }
            }
        }
        GlStateManager.popMatrix();
        double[] save = RESTORE.remove(p.getEntityId());
        if (save != null) { p.swingProgress = (float) save[0]; p.prevSwingProgress = (float) save[1]; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void preLiving(RenderLivingEvent.Pre<EntityLivingBase> e) {
        EntityLivingBase v = e.getEntity();
        for (C c : CUES.values()) {
            if (c.victim != v.getEntityId() || c.impact < 0) continue;
            double bend = LariatMath.spineBend(DashClient.now() - c.impact, LariatMath.HITSTOP * 50.0);
            if (bend <= 0.1) return;
            double hx = c.dir[0], hz = c.dir[2], l = Math.max(1e-4, Math.sqrt(hx * hx + hz * hz));
            GlStateManager.pushMatrix();
            BENT.add(v.getEntityId());
            double x = e.getX(), y = e.getY() + v.height * 0.45, z = e.getZ();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate((float) bend, (float) (hz / l), 0, (float) (-hx / l));   // top of the body thrown along the blow
            GlStateManager.translate(-x, -y, -z);
            return;
        }
    }

    @SubscribeEvent public void postLiving(RenderLivingEvent.Post<EntityLivingBase> e) {
        if (BENT.remove(e.getEntity().getEntityId())) GlStateManager.popMatrix();
    }

    // ---- world-space: ribbons, forearm lightning, vacuum ring, mach cones, afterimages ----

    @SubscribeEvent public void world(RenderWorldLastEvent e) {
        Minecraft mc = mc();
        if (mc.world == null || CUES.isEmpty()) return;
        RenderManager rm = mc.getRenderManager();
        double vx = rm.viewerPosX, vy = rm.viewerPosY, vz = rm.viewerPosZ;
        float pt = e.getPartialTicks();
        long t = DashClient.now();
        // Afterimages first (they use the model renderer and its own state).
        rendering = true;
        try { for (C c : CUES.values()) {
            if (c.blink < 0) continue;
            Entity a = mc.world.getEntityByID(c.caster);
            if (!(a instanceof AbstractClientPlayer)) continue;
            Object r = rm.getEntityRenderObject(a);
            if (!(r instanceof RenderPlayer)) continue;
            float yaw = DashMath.lookAngles(0, 0, 0, c.dir[0], 0, c.dir[2])[0];
            for (int k = 0; k < LariatMath.AFTERIMAGES; k++) {
                float alpha = LariatMath.afterimageAlpha(k, t - c.blink);
                if (alpha <= 0.01f) continue;
                double u = LariatMath.afterimageAt(k);
                DashClient.Pose p = DashClient.pose((AbstractClientPlayer) a, pt, c.sx + (c.ex - c.sx) * u, c.sy + (c.ey - c.sy) * u, c.sz + (c.ez - c.sz) * u);
                p.yawOffset = p.yawHead = yaw; p.pitch = 0; p.limbAmount = 0.9f; p.limbSwing = (float) (k * 1.3); p.swing = 0.42f; p.lean = 30; p.leanDir = c.dir;
                double dx = p.x - vx, dy = p.y + 1 - vy, dz = p.z - vz;
                if (dx * dx + dy * dy + dz * dz < 2.2 * 2.2) continue;   // never bloom across the lens
                DashClient.tinted((AbstractClientPlayer) a, (RenderPlayer) r, p, vx, vy, vz, 1.0f, 0.12f, 0.06f, alpha, pt);
                afterimageFrames++;
            }
        } } finally { rendering = false; }
        GlStateManager.pushMatrix();
        GlStateManager.translate(-vx, -vy, -vz);
        GlStateManager.disableTexture2D(); GlStateManager.disableLighting(); GlStateManager.disableCull(); GlStateManager.disableAlpha();
        GlStateManager.enableBlend(); GlStateManager.depthMask(false); GlStateManager.shadeModel(GL11.GL_SMOOTH);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        for (C c : CUES.values()) {
            Entity a = mc.world.getEntityByID(c.caster);
            if (a == null) continue;
            double ax = a.prevPosX + (a.posX - a.prevPosX) * pt, ay = a.prevPosY + (a.posY - a.prevPosY) * pt, az = a.prevPosZ + (a.posZ - a.prevPosZ) * pt;
            float bodyYaw = a instanceof EntityLivingBase ? ((EntityLivingBase) a).prevRenderYawOffset + (((EntityLivingBase) a).renderYawOffset - ((EntityLivingBase) a).prevRenderYawOffset) * pt : a.rotationYaw;
            double fade = c.end >= 0 ? Math.max(0, 1 - (t - c.end) / 300.0) : 1;
            if (c.blink >= 0) fade *= Math.max(0.3, 1 - (t - c.blink) / 700.0);   // after the blink they settle and thin out
            if (fade > 0) { ribbons(c, ax, ay, az, bodyYaw, t, pt, fade, vx, vy, vz); ribbonFrames++; }
            if (c.blink >= 0 && (c.release < 0 && c.end < 0 || c.release >= 0 && t - c.release < 450)) armLightning(c, ax, ay, az, bodyYaw, t);
            if (c.blink >= 0) {
                double u = (t - c.blink) / 250.0;   // the vacuum ring collapsing where you stood
                if (u < 1) {
                    GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    DashClient.ring(c.sx, c.sy + 1.0, c.sz, c.dir, 2.8 * (1 - u) + 0.1, 0.25, 0.95f, 0.97f, 1f, (float) (0.75 * (1 - u)));
                }
                machCone(c.sx, c.sy + 1.0, c.sz, c.dir, (t - c.blink) / 400.0);
            }
            if (c.impact >= 0) machCone(c.cx - c.dir[0] * 0.4, c.cy, c.cz - c.dir[2] * 0.4, c.dir, (t - c.impact) / 400.0);
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GlStateManager.shadeModel(GL11.GL_FLAT); GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend(); GlStateManager.enableAlpha(); GlStateManager.enableCull(); GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    /**
     * Eight energy ribbons out of the tail base. In the anticipation they flare up and out and whip hard;
     * from the blink on they stream back along the path and settle. Each is drawn twice: a wide additive
     * crimson glow, then a thin dark core, so they read as dense energy rather than sparkles.
     */
    private static void ribbons(C c, double ax, double ay, double az, float bodyYaw, long t, float pt, double fade, double vx, double vy, double vz) {
        double yaw = Math.toRadians(bodyYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        boolean flare = c.blink < 0;
        double reach = flare ? 0.3 : 0.18;   // streaming back they are shorter, so they never sweep across a trailing camera
        double whip = flare ? 1.0 : 0.22;
        double age = (t - c.start) / 50.0;
        double bx = ax - fx * 0.28, by = ay + 0.85, bz = az - fz * 0.28;
        double[] back = flare ? new double[] {-fx, 0, -fz} : DashMath.norm(new double[] {-c.dir[0], 0, -c.dir[2]});
        Tessellator ts = Tessellator.getInstance(); BufferBuilder buf = ts.getBuffer();
        for (int pass = 0; pass < 2; pass++) {
            // Alpha-blended, not additive: crimson added onto grass reads orange and onto sky reads pink.
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            for (int i = 0; i < LariatMath.RIBBONS; i++) {
                double fan = Math.toRadians(LariatMath.ribbonFan(i) * (flare ? 1.0 : 0.3));
                double lift = Math.toRadians(flare ? 28 + (i % 2) * 14 : 6);
                double hx = back[0] * Math.cos(fan) - back[2] * Math.sin(fan), hz = back[0] * Math.sin(fan) + back[2] * Math.cos(fan);
                double[] d = DashMath.norm(new double[] {hx * Math.cos(lift), Math.sin(lift), hz * Math.cos(lift)});
                double px = bx, py = by, pz = bz;
                buf.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
                for (int s = 0; s <= LariatMath.RIBBON_SEGMENTS; s++) {
                    double w = (0.17 - 0.0125 * s) * (pass == 0 ? 1.7 : 0.6);
                    double[] toCam = DashMath.norm(new double[] {vx - px, vy - py, vz - pz});
                    double[] side = DashMath.norm(DashMath.cross(d, toCam));
                    float alpha = (float) (fade * (pass == 0 ? 0.42 : 0.85) * (1 - s / (double) (LariatMath.RIBBON_SEGMENTS + 2)));
                    float r = pass == 0 ? 0.92f : 0.3f, g = pass == 0 ? 0.08f : 0.0f, b = pass == 0 ? 0.1f : 0.03f;
                    buf.pos(px + side[0] * w, py + side[1] * w, pz + side[2] * w).color(r, g, b, alpha).endVertex();
                    buf.pos(px - side[0] * w, py - side[1] * w, pz - side[2] * w).color(r, g, b, alpha).endVertex();
                    // Bend the next segment: yaw and pitch waves, stronger toward the tip.
                    double by1 = LariatMath.ribbonBend(i, s, age, whip), bp = LariatMath.ribbonBend(i + 3, s, age * 1.1, whip) * 0.8;
                    double cy = Math.cos(by1), sy = Math.sin(by1);
                    double nx = d[0] * cy - d[2] * sy, nz = d[0] * sy + d[2] * cy, ny = d[1] + bp;
                    d = DashMath.norm(new double[] {nx, ny, nz});
                    px += d[0] * reach; py += d[1] * reach; pz += d[2] * reach;
                }
                Tessellator.getInstance().draw();
            }
        }
    }

    /** Jagged white-cyan arcs crawling along the extended right forearm. */
    private static void armLightning(C c, double ax, double ay, double az, float bodyYaw, long t) {
        double yaw = Math.toRadians(bodyYaw);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double[] shoulder = {ax + rx * 0.37, ay + 1.38, az + rz * 0.37};
        double[] arm = DashMath.norm(new double[] {fx * 0.5 + rx * 0.85, -0.05, fz * 0.5 + rz * 0.85});
        double[] hand = {shoulder[0] + arm[0] * 0.8, shoulder[1] + arm[1] * 0.8, shoulder[2] + arm[2] * 0.8};
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GL11.glLineWidth(2.5f);
        Tessellator ts = Tessellator.getInstance(); BufferBuilder buf = ts.getBuffer();
        long seed = t / 40;
        for (int k = 0; k < 4; k++) {
            double[][] bolt = LariatMath.bolt(shoulder, k < 3 ? hand : new double[] {hand[0] + arm[0] * 0.5 + (k - 3) * 0.2, hand[1] + 0.25, hand[2] + arm[2] * 0.5}, 9, 0.13, seed * 31 + k);
            buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
            for (double[] q : bolt) buf.pos(q[0], q[1], q[2]).color(0.82f, 0.96f, 1f, 0.9f).endVertex();
            ts.draw();
        }
        GL11.glLineWidth(1f);
    }

    /** The supersonic cone: tip forward along the path, widening and fading over its life (0..1). */
    private static void machCone(double x, double y, double z, double[] d, double progress) {
        if (progress < 0 || progress >= 1) return;
        double radius = 0.5 + progress * 3.8, length = 2.8 - progress * 1.2;
        float alpha = (float) ((1 - progress) * 0.75);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        double[][] ring = DashMath.ring(d, 1, 24);
        Tessellator ts = Tessellator.getInstance(); BufferBuilder buf = ts.getBuffer();
        buf.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        double tx = x + d[0] * length, ty = y + d[1] * length, tz = z + d[2] * length;
        for (int i = 0; i <= 24; i++) {
            double[] p = ring[i % 24];
            buf.pos(tx, ty, tz).color(0.85f, 0.98f, 1f, alpha).endVertex();
            buf.pos(x + p[0] * radius, y + p[1] * radius, z + p[2] * radius).color(0.75f, 0.05f, 0.08f, 0f).endVertex();
        }
        ts.draw();
    }

    // ---- screen: the lightning web and speed lines on the held frames ----

    @SubscribeEvent public void overlay(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || mc().world == null || mc().player == null) return;
        long t = DashClient.now();
        for (C c : CUES.values()) {
            if (c.impact < 0 || !involved(c)) continue;
            long since = t - c.impact;
            if (since < 0 || since > LariatMath.HITSTOP * 50 + 60) continue;
            mc().entityRenderer.setupOverlayRendering();
            ScaledResolution sr = new ScaledResolution(mc());
            int w = sr.getScaledWidth(), h = sr.getScaledHeight();
            DashClient.speedLines(w, h, 0.45f);
            GlStateManager.disableTexture2D(); GlStateManager.enableBlend(); GlStateManager.disableAlpha();
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            GL11.glLineWidth(3f);
            List<double[]> web = LariatMath.web(w / 2.0, h / 2.0, Math.hypot(w, h) * 0.55, 7, c.caster * 131L + since / 50);
            Tessellator ts = Tessellator.getInstance(); BufferBuilder buf = ts.getBuffer();
            float a = (float) Math.max(0.2, 1 - since / (double) (LariatMath.HITSTOP * 50 + 60));
            for (double[] line : web) {
                buf.begin(GL11.GL_LINE_STRIP, DefaultVertexFormats.POSITION_COLOR);
                for (int i = 0; i + 1 < line.length; i += 2) buf.pos(line[i], line[i + 1], 0).color(0.85f, 0.97f, 1f, a).endVertex();
                ts.draw();
            }
            GL11.glLineWidth(1f);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableAlpha(); GlStateManager.disableBlend(); GlStateManager.enableTexture2D();
            webFrames++;
            break;
        }
    }
}
