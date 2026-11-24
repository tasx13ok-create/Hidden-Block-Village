package village.bijuu;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * The cut-scene director: while the local player performs the move, the view is handed to a free
 * camera that cuts between composed shots, each driven by the move's own cues.
 *
 *   charge / telegraph  low front three-quarter angle, pushing in
 *   blink / dash        behind the shoulder with a dolly zoom (the view widens while the camera closes in)
 *   barrage / hit-stop  a whip orbit around the frozen fighters
 *   launch              chase camera riding behind the body as it tears through the terrain
 *   relay               a quick orbit around the relay hit, then back to the chase
 *   splatter            facing the cracked wall
 *   pin / spike         a low angle looking up, then straight down onto the crater as it forms
 *
 * Positions never pass through walls, the HUD is hidden while it runs, and the player's own view is
 * restored when the move ends. Nothing here touches the world or the player.
 */
final class DashCamera {
    private static Cam cam;
    private static boolean active, hudWasHidden;
    private static int thirdWas;
    private static float fovMult = 1f;
    private static Field viewField;
    static String shot = "";   // diagnostics: the current shot

    /** A camera that exists only on this client; it is never added to the world. */
    static final class Cam extends EntityLivingBase {
        Cam(World w) { super(w); setSize(0.1f, 0.1f); }
        @Override public float getEyeHeight() { return 0f; }
        @Override public Iterable<ItemStack> getArmorInventoryList() { return java.util.Collections.emptyList(); }
        @Override public ItemStack getItemStackFromSlot(EntityEquipmentSlot s) { return ItemStack.EMPTY; }
        @Override public void setItemStackToSlot(EntityEquipmentSlot s, ItemStack i) {}
        @Override public EnumHandSide getPrimaryHand() { return EnumHandSide.RIGHT; }
        @Override public boolean isInvisible() { return true; }
    }

    private static final class Shot {
        final String name; final Vec3d pos; final float yaw, pitch, fov;
        Shot(String n, Vec3d p, float y, float pi, float f) { name = n; pos = p; yaw = y; pitch = pi; fov = f; }
    }

    static boolean active() { return active; }
    static float fovMult() { return active ? fovMult : 1f; }

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    /** Runs before each frame: picks the shot for this instant and places the camera. */
    @SubscribeEvent(priority = EventPriority.HIGH) public void frame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = mc();
        if (mc.world == null || mc.player == null || !SteamDash.cinematicCamera || !SteamDash.cameraEffects) { stop(); return; }
        Shot s = choose(DashClient.now(), e.renderTickTime);
        if (s == null) { stop(); return; }
        if (!active) start();
        if (cam.world != mc.world) cam = new Cam(mc.world);
        Vec3d p = s.pos;
        cam.setPosition(p.x, p.y, p.z);
        cam.prevPosX = cam.lastTickPosX = p.x; cam.prevPosY = cam.lastTickPosY = p.y; cam.prevPosZ = cam.lastTickPosZ = p.z;
        cam.rotationYaw = cam.prevRotationYaw = cam.rotationYawHead = cam.prevRotationYawHead = s.yaw;
        cam.rotationPitch = cam.prevRotationPitch = s.pitch;
        fovMult = s.fov;
        shot = s.name;
    }

    /**
     * Vanilla only sends your position and reads your movement keys while you are the view entity, so
     * during client ticks the view goes back to you; the free camera has it for every rendered frame.
     * Without this the server would not see a skid or a step taken while a shot is up.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void tickView(TickEvent.ClientTickEvent e) {
        if (!active || cam == null || mc().player == null) return;
        setView(e.phase == TickEvent.Phase.START ? mc().player : cam);
    }

    @SubscribeEvent(priority = EventPriority.LOW) public void fov(EntityViewRenderEvent.FOVModifier e) {
        if (active) e.setFOV(e.getFOV() * fovMult);
    }

    private static void start() {
        Minecraft mc = mc();
        if (cam == null || cam.world != mc.world) cam = new Cam(mc.world);
        hudWasHidden = mc.gameSettings.hideGUI;
        thirdWas = mc.gameSettings.thirdPersonView;
        if (SteamDash.hideHudInCutscene) mc.gameSettings.hideGUI = true;
        mc.gameSettings.thirdPersonView = 0;
        setView(cam);
        active = true;
    }

    static void stop() {
        if (!active) return;
        Minecraft mc = mc();
        setView(mc.player);
        mc.gameSettings.hideGUI = hudWasHidden;
        mc.gameSettings.thirdPersonView = thirdWas;
        active = false; fovMult = 1f; shot = "";
    }

    /** Sets the view entity without Minecraft.setRenderViewEntity, which would reload (and delete) post shaders. */
    private static void setView(Entity e) {
        try {
            if (viewField == null) {
                try { viewField = Minecraft.class.getDeclaredField("field_175622_Z"); } catch (NoSuchFieldException dev) { viewField = Minecraft.class.getDeclaredField("renderViewEntity"); }
                viewField.setAccessible(true);
            }
            viewField.set(mc(), e);
        } catch (ReflectiveOperationException failed) { mc().setRenderViewEntity(e); }
    }

    // ---- shots ----

    private static Shot choose(long t, float pt) {
        Minecraft mc = mc();
        int me = mc.player.getEntityId();
        LariatClient.C lar = LariatClient.CUES.get(me);
        if (lar != null) return lariat(lar, t, pt);
        DashClient.Cue c = DashClient.CUES.get(me);
        long[] charge = DashClient.CHARGES.get(me);
        if (c == null) {
            if (charge != null && t - charge[0] > 150) return front("charge", mc.player, pt, 6.0 - Math.min(1, (t - charge[0]) / 1000.0) * 1.6, (t - charge[0]) * 0.012);
            return null;
        }
        if (c.end >= 0 && t - c.end > tailMs(c)) return null;
        Entity a = mc.world.getEntityByID(c.attacker), target = mc.world.getEntityByID(c.target);
        if (a == null) return null;
        if (c.crater >= 0) return overhead(c, t, pt, target);
        if (c.spikeFall >= 0) return overhead(c, t, pt, target);
        if (c.pin >= 0) return lowPin(c, t, pt, target);
        if (c.splatter >= 0 && t - c.splatter < 900) return splat(c, t);
        if (c.relay >= 0 && (c.launch < c.relay || t - c.relay < 320)) return orbit("relay-orbit", c.hx, c.hy, c.hz, c.dir, t - c.relay, 120, 320, 6.0);
        if (c.launch >= 0 && t - c.launch > 110 && target != null) return chase(c, t, pt, target);
        if (c.barrage >= 0 || c.impact >= 0) {
            long since = t - (c.barrage >= 0 ? c.barrage : c.impact);
            return orbit("hit-orbit", c.hx, c.hy, c.hz, c.dir, since, 100, 650, 6.5);
        }
        if (c.blink >= 0) return dolly(c, t, pt, a);
        return front("telegraph", a, pt, 4.6 - Math.min(1, (t - c.start) / 250.0) * 0.6, 0);
    }

    /**
     * The Lariat's four beats: a low crouch shot, a wide side-on frame for the blink (the whole path in
     * view), a tight orbit through the hit-stop, then a low trailing shot behind the caster as the
     * victim flies off and the caster skids.
     */
    private static Shot lariat(LariatClient.C c, long t, float pt) {
        Minecraft mc = mc();
        if (c.end >= 0 && t - c.end > 300) return null;
        Entity a = mc.world.getEntityByID(c.caster), v = c.victim >= 0 ? mc.world.getEntityByID(c.victim) : null;
        if (a == null) return null;
        double[] d = c.dir;
        double hx = d[0], hz = d[2], l = Math.max(1e-4, Math.sqrt(hx * hx + hz * hz)); hx /= l; hz /= l;
        if (c.release >= 0 || c.blink >= 0 && c.impact < 0 && t - c.blink > 320) {
            // Over the shoulder at head height: the caster skidding in the foreground, the body flying off beyond.
            Vec3d p = at(a, pt);
            Vec3d ahead = p.add(hx * 4, 1.1, hz * 4);
            Vec3d focus = v != null ? ahead.add(at(v, pt).add(0, v.height * 0.5, 0).subtract(ahead).scale(0.3)) : ahead;
            Vec3d pos = p.add(-hx * 5.2 - hz * 1.6, 1.7, -hz * 5.2 + hx * 1.6);
            return look("lariat-trail", pos, focus, 1.05f);
        }
        if (c.blink >= 0 && t - c.blink < 90) return blinkShot(c, hx, hz);   // holds through the blink and the first inverted frame
        if (c.impact >= 0) return orbit("lariat-hit", c.cx, c.cy, c.cz, d, t - c.impact, 70, LariatMath.HITSTOP * 50 + 250, 4.2);
        if (c.blink >= 0) return blinkShot(c, hx, hz);
        Vec3d p = at(a, pt);
        double u = Math.min(1, (t - c.start) / (LariatMath.ANTICIPATION * 50.0));
        float yaw = DashMath.lookAngles(0, 0, 0, hx, 0, hz)[0];
        double[] o = DashMath.orbit(p.x, p.y, p.z, 4.4 - 0.8 * u, yaw + 30, 0.25);
        Shot s = look("lariat-crouch", new Vec3d(o[0], o[1], o[2]), p.add(0, 1.0, 0), 0.88f);
        return s;
    }

    /** Wide and side-on, the whole blink path in frame. */
    private static Shot blinkShot(LariatClient.C c, double hx, double hz) {
        double mx = (c.sx + c.ex) / 2, my = (c.sy + c.ey) / 2 + 1.0, mz = (c.sz + c.ez) / 2;
        double len = Math.sqrt((c.ex - c.sx) * (c.ex - c.sx) + (c.ez - c.sz) * (c.ez - c.sz));
        double side = Math.max(7, len * 0.75);
        return look("lariat-blink", new Vec3d(mx + hz * side, my + 1.6, mz - hx * side), new Vec3d(mx, my, mz), 1.05f);
    }

    /** How long to keep the last shot after the move ends: craters get a beat to settle. */
    private static long tailMs(DashClient.Cue c) { return c.crater >= 0 ? 1400 : c.splatter >= 0 ? 700 : 500; }

    private static Vec3d at(Entity e, float pt) {
        return new Vec3d(e.prevPosX + (e.posX - e.prevPosX) * pt, e.prevPosY + (e.posY - e.prevPosY) * pt, e.prevPosZ + (e.posZ - e.prevPosZ) * pt);
    }

    /** Low front three-quarter angle on a fighter, slowly circling by `drift` degrees. */
    private static Shot front(String name, Entity a, float pt, double dist, double drift) {
        Vec3d p = at(a, pt);
        float yaw = a instanceof EntityLivingBase ? ((EntityLivingBase) a).renderYawOffset : a.rotationYaw;
        double[] o = DashMath.orbit(p.x, p.y, p.z, dist, yaw + 35 + drift, 0.55);
        return look(name, new Vec3d(o[0], o[1], o[2]), p.add(0, 1.15, 0), 1.0f);
    }

    /** Behind the shoulder; the field of view widens 70 -> 105 while the camera closes in, so the dasher holds size. */
    private static Shot dolly(DashClient.Cue c, long t, float pt, Entity a) {
        Vec3d p = at(a, pt).add(0, 1.0, 0);
        double u = DashMath.ease((t - c.blink) / 300.0);
        double fov = 70 + 35 * u, d = DashMath.dollyDistance(5.0, 70, fov);
        double[] dir = c.dir;
        double hx = dir[0], hz = dir[2], l = Math.max(1e-4, Math.sqrt(hx * hx + hz * hz));
        Vec3d pos = p.add(-hx / l * d - hz / l * 1.3, 0.8, -hz / l * d + hx / l * 1.3);
        return look("dash-dolly", pos, p.add(dir[0] * 3, dir[1] * 3, dir[2] * 3), (float) (fov / 70));
    }

    /** A whip orbit around a point: starts side-on and sweeps `sweep` degrees, fast then settling. */
    private static Shot orbit(String name, double x, double y, double z, double[] dir, long since, double sweep, double ms, double radius) {
        float base = DashMath.lookAngles(0, 0, 0, dir[0], 0, dir[2])[0];
        double ang = base + 90 + sweep * (1 - Math.pow(1 - Math.min(1, since / ms), 3));
        double[] o = DashMath.orbit(x, y, z, radius, ang, 0.7);
        return look(name, new Vec3d(o[0], o[1], o[2]), new Vec3d(x, y, z), 0.85f);
    }

    /** Rides behind the launched body, looking past it down the tunnel it is cutting. */
    private static Shot chase(DashClient.Cue c, long t, float pt, Entity target) {
        Vec3d p = at(target, pt).add(0, target.height * 0.6, 0);
        double[] f = DashMath.norm(new double[] {c.flight[0], Math.max(-0.3, Math.min(0.3, c.flight[1])), c.flight[2]});
        // Behind and off to the side, so the camera never flies through the body's own steam trail.
        Vec3d pos = p.add(-f[0] * 4.5 - f[2] * 3.5, 2.3, -f[2] * 4.5 + f[0] * 3.5);
        return look("chase", pos, p.add(f[0] * 2.5, -0.7, f[2] * 2.5), 1.12f);
    }

    private static Shot splat(DashClient.Cue c, long t) {
        double[] n = c.splatNormal;
        Vec3d wall = new Vec3d(c.sx - n[0] * 1.6, c.sy, c.sz - n[2] * 1.6);   // aim just off the wall face, not inside the rock
        Vec3d pos = wall.add(-n[0] * 11 + n[2] * 5.5, 2.4, -n[2] * 11 - n[0] * 5.5);
        return look("splatter", pos, wall, 0.95f);
    }

    /** Ground level, looking up at the pinned body hanging above nine tail stabs. */
    private static Shot lowPin(DashClient.Cue c, long t, float pt, Entity target) {
        Vec3d p = target != null ? at(target, pt) : new Vec3d(c.hx, c.hy, c.hz);
        double[] f = c.flight;
        double hx = f[0], hz = f[2], l = Math.max(1e-4, Math.sqrt(hx * hx + hz * hz));
        Vec3d pos = new Vec3d(p.x - hz / l * 6.5, c.groundY + 0.4, p.z + hx / l * 6.5);
        return look("pin-low", pos, p.add(0, 1.4, 0), 1.0f);
    }

    /** Straight down onto the slam and the crater, turning slowly. */
    private static Shot overhead(DashClient.Cue c, long t, float pt, Entity target) {
        Vec3d p = c.crater >= 0 ? new Vec3d(c.cx, c.cy, c.cz) : target != null ? at(target, pt) : new Vec3d(c.hx, c.hy, c.hz);
        long since = t - (c.spikeFall >= 0 ? c.spikeFall : c.crater);
        double h = 15 + Math.min(4, since / 400.0);
        Vec3d pos = new Vec3d(p.x + 0.01, p.y + h, p.z + 0.01);
        float yaw = (float) (since * 0.02);
        return new Shot("overhead", clear(p.add(0, 1, 0), pos), yaw, 89.5f, 1.0f);
    }

    private static Shot look(String name, Vec3d pos, Vec3d focus, float fov) {
        Vec3d safe = clear(focus, pos);
        float[] a = DashMath.lookAngles(safe.x, safe.y, safe.z, focus.x, focus.y, focus.z);
        return new Shot(name, safe, a[0], a[1], fov);
    }

    /** Pulls a camera position in front of any wall between it and what it is looking at. */
    private static Vec3d clear(Vec3d focus, Vec3d pos) {
        Vec3d d = pos.subtract(focus);
        double len = d.length();
        if (len < 1e-6) return pos;
        // Start the check a little out from the focus, so a focus touching a block face never pins the camera to it.
        Vec3d from = focus.add(d.scale(Math.min(0.6, len) / len));
        RayTraceResult hit = mc().world.rayTraceBlocks(from, pos, false, true, false);
        if (hit == null || hit.hitVec == null) return pos;
        double keep = Math.max(0.6, hit.hitVec.distanceTo(focus) - 0.35);
        return focus.add(d.scale(keep / len));
    }
}
