package village.bijuu;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerArrow;
import net.minecraft.client.renderer.entity.layers.LayerEntityOnShoulder;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/**
 * The cut-scene side of the Steam Dash Punch: camera (third-person cut, FOV pull and snap, shake,
 * letterbox, speed lines), impact frames (white flash, ink silhouette shader), the white-hot
 * overheat pass, golden afterimage clones, the fist mach cone, sonic rings, the Kurama tail whip and
 * client-only flying rubble. Nothing here changes the world.
 */
public final class DashClient {
    static KeyBinding KEY;
    private static final ResourceLocation INK = new ResourceLocation(BijuuArmor.ID, "shaders/post/inkframe.json");
    private static final Random RAND = new Random();
    private static final int CONSTANT_COLOR = 0x8001;

    public static final class Proxy extends SteamDash.Proxy {
        @Override public void preInit() {
            KEY = new KeyBinding("key.hvbijuu.steamdash", Keyboard.KEY_B, "key.categories.hvbijuu");
            ClientRegistry.registerKeyBinding(KEY);
            MinecraftForge.EVENT_BUS.register(new DashClient());
            MinecraftForge.EVENT_BUS.register(new DashCamera());
            RenderingRegistry.registerEntityRenderingHandler(Rubble.class, RubbleRender::new);
        }
        @Override public void fx(DashNet.Fx m) { Minecraft.getMinecraft().addScheduledTask(() -> handle(m)); }
    }

    // ---- state ----

    /** One attacker's sequence as seen by this client; times are system milliseconds, -1 until reached. */
    static final class Cue {
        final int attacker, target;
        final long start = now();
        long blink = -1, impact = -1, release = -1, spike = -1, end = -1;
        long barrage = -1, launch = -1, relay = -1, pin = -1, spikeFall = -1, crater = -1, splatter = -1;
        int telegraph = DashMath.TELEGRAPH, tier = 1, relays;
        double[] dir = {0, 0, 1}, flight = {0, 0, 1}, splatNormal = {0, 0, 1};
        double hx, hy, hz, sx, sy, sz, cx, cy, cz, groundY;
        int[] line = new int[0];
        long jab = -1;   // last small hit (clone jab, pierce pass, relay): a brief flash
        int whiteDrawn, inkDrawn;
        final ArrayDeque<Pose> trail = new ArrayDeque<>();
        Cue(int attacker, int target) { this.attacker = attacker; this.target = target; }
    }

    /** A frozen copy of a player's pose for the afterimage clones. */
    static final class Pose {
        double x, y, z; float yawOffset, yawHead, pitch, limbSwing, limbAmount, swing, lean; double[] leanDir; boolean sneak; long at;
    }

    /** A sonic ring: an expanding, fading annulus. */
    static final class Boom {
        double x, y, z; double[] n; long born; int life; double r0, r1; float red, green, blue, alpha;
    }

    /** A ghost that is not tied to the trail: the blink-off point, a springboard, the spike teleport. */
    static final class Ghost { int entity; Pose pose; long born; double tx, ty, tz; long lunge; }

    static final Map<Integer, Cue> CUES = new HashMap<>();
    private static final List<Boom> BOOMS = new ArrayList<>();
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<long[]> SHAKES = new ArrayList<>();   // {startMs, strength x100}
    private static final List<double[]> BUBBLES = new ArrayList<>();   // {x, y, z, radius, bornMs, untilMs}
    private static final List<double[]> THUNDER = new ArrayList<>();   // {dueMs, x, y, z, volume, pitch}
    private static final List<double[]> RIPPLES = new ArrayList<>();   // {x, y, z, maxRadius, bornMs, lastRadius}
    private static long springKick = -1;
    static boolean inkOn, cinematic, forcedThirdPerson;
    static int whiteFrames, inkFrames;   // frames actually drawn (diagnostics)
    private static Cue lastFrameCue;
    private static long cinematicUntil = -1;
    private static boolean rendering;   // re-entrancy guard for the tinted passes

    /** Charging players seen by this client: entity id -> {start ms, tier}. */
    static final Map<Integer, long[]> CHARGES = new HashMap<>();
    private static boolean keyWasDown;

    // The effect clock follows game time, not the wall clock: if the game runs slower (a heavy frame,
    // or a slow-motion capture), every ring, flash and camera move slows with it and stays in sync.
    private static long virtual, lastReal = -1;
    private static Field timerField, tickLengthField;

    static long now() {
        long real = Minecraft.getSystemTime();
        if (lastReal < 0) { lastReal = real; virtual = real; }
        virtual += Math.round((real - lastReal) * (50.0 / tickLength()));
        lastReal = real;
        return virtual;
    }

    private static float tickLength() {
        try {
            if (tickLengthField == null) {
                timerField = field(Minecraft.class, "field_71428_T", "timer");
                tickLengthField = field(net.minecraft.util.Timer.class, "field_194149_e", "tickLength");
            }
            float l = tickLengthField.getFloat(timerField.get(Minecraft.getMinecraft()));
            return l > 1 ? l : 50f;
        } catch (ReflectiveOperationException e) { return 50f; }
    }
    private static Minecraft mc() { return Minecraft.getMinecraft(); }
    private static boolean local(int id) { return mc().player != null && mc().player.getEntityId() == id; }

    // ---- cues ----

    static void handle(DashNet.Fx m) {
        Minecraft mc = mc();
        if (mc.world == null) return;
        Entity a = mc.world.getEntityByID(m.attacker);
        double[] d = {m.dx, m.dy, m.dz};
        Cue c = CUES.get(m.attacker);
        long t = now();
        switch (m.kind) {
            case DashNet.START:
                c = new Cue(m.attacker, m.target);
                if (m.data.length > 0) c.telegraph = m.data[0];
                if (m.data.length > 1) c.tier = m.data[1];
                if (m.data.length > 2) c.line = java.util.Arrays.copyOfRange(m.data, 2, m.data.length);
                c.dir = DashMath.norm(d);
                CHARGES.remove(m.attacker);
                CUES.put(m.attacker, c);
                if (local(m.attacker)) beginCinematic();
                break;
            case DashNet.BLINK:
                if (c == null) break;
                c.blink = t; c.dir = DashMath.norm(d);
                if (a instanceof AbstractClientPlayer) ghost(m.attacker, pose((AbstractClientPlayer) a, 1f, m.x, m.y, m.z));
                boom(m.x, m.y + 1.0, m.z, d, 0.4, 3.6, 380, 1f, 1f, 1f, 0.75f);
                boom(m.x, m.y + 1.0, m.z, d, 0.2, 2.2, 260, 1f, 0.72f, 0.2f, 0.8f);
                shake(t, 35);
                break;
            case DashNet.IMPACT:
                if (c == null) break;
                c.impact = t; c.hx = m.x; c.hy = m.y; c.hz = m.z;
                if (m.data.length > 0) c.tier = m.data[0];
                tailStrike(m.attacker, m.target);
                boom(m.x, m.y, m.z, d, 0.2, 1.6, 150, 1f, 1f, 1f, 0.9f);
                break;
            case DashNet.RELEASE: {
                if (c == null) break;
                boolean relayLaunch = m.data.length > 1;
                if (!relayLaunch) c.release = t;
                c.launch = t; c.flight = d;
                double hy = c.hy + 0.6;
                boom(c.hx, hy, c.hz, d, 0.5, 6.5 + c.tier, 520, 1f, 1f, 1f, 0.85f);
                boom(c.hx, hy, c.hz, new double[] {0, 1, 0}, 0.5, 5.0 + c.tier, 600, 1f, 0.7f, 0.15f, 0.6f);
                shake(t, relayLaunch ? 110 : 150);
                if (!relayLaunch) {
                    thunder(c.hx, c.hy, c.hz, 2.0f, 0.75f);
                    ripple(c.hx, c.hy - 1, c.hz, 6 + c.tier * 2);
                    if (c.tier >= 2) skyFlash();
                }
                break;
            }
            case DashNet.BARRAGE: {
                if (c == null) break;
                int i = m.data.length > 0 ? m.data[0] : 1;
                if (c.barrage < 0) { c.barrage = t; c.hx = m.x; c.hy = m.y; c.hz = m.z; }
                lunge(c, a, i, m.x, m.y, m.z);
                boom(m.x, m.y, m.z, d, 0.15, 1.1 + i * 0.2, 160, 1f, 0.85f, 0.45f, 0.8f);
                c.jab = t;
                shake(t, 30 + i * 10);
                break;
            }
            case DashNet.PASS:
                if (c == null) break;
                boom(m.x, m.y, m.z, d, 0.2, 2.4, 220, 1f, 1f, 1f, 0.9f);
                boom(m.x, m.y, m.z, new double[] {0, 1, 0}, 0.2, 2.0, 260, 1f, 0.7f, 0.2f, 0.7f);
                c.jab = t;
                shake(t, 80);
                break;
            case DashNet.RELAY:
                if (c == null) break;
                c.relay = t; c.relays = m.data.length > 0 ? m.data[0] : c.relays + 1;
                c.hx = m.x; c.hy = m.y; c.hz = m.z;
                if (a instanceof AbstractClientPlayer) ghost(m.attacker, pose((AbstractClientPlayer) a, 1f, m.dx, m.dy, m.dz));
                boom(m.x, m.y, m.z, c.flight, 0.3, 4.0, 300, 1f, 1f, 1f, 0.9f);
                boom(m.x, m.y, m.z, new double[] {0, 1, 0}, 0.3, 3.0, 360, 1f, 0.75f, 0.2f, 0.7f);
                tailStrike(m.attacker, m.target);
                c.jab = t;
                shake(t, 120);
                break;
            case DashNet.PIN:
                if (c == null) break;
                if (m.data.length > 0 && m.data[0] == 1) { c.spikeFall = t; shake(t, 120); break; }   // the slam itself
                c.pin = t; c.hx = m.x; c.hy = m.y; c.hz = m.z; c.flight = d;
                c.groundY = mc.world.getHeight(new BlockPos(m.x, 0, m.z)).getY();
                tailStrikeDown(m.attacker);
                boom(m.x, m.y + 1, m.z, new double[] {0, 1, 0}, 0.4, 3.2, 400, 1f, 0.8f, 0.3f, 0.7f);
                break;
            case DashNet.STAB:
                for (int i = 0; i + 1 < m.data.length; i += 2) {
                    double sx = m.data[i] / 8.0, sz = m.data[i + 1] / 8.0;
                    boom(sx, m.y + 0.1, sz, new double[] {0, 1, 0}, 0.1, 1.5, 260, 1f, 0.9f, 0.6f, 0.85f);
                    IBlockState g = mc.world.getBlockState(new BlockPos(sx, m.y - 0.5, sz));
                    for (int k = 0; k < 2; k++) rubble(sx, m.y + 0.3, sz, g, (RAND.nextDouble() - 0.5) * 0.3, 0.45 + RAND.nextDouble() * 0.3, (RAND.nextDouble() - 0.5) * 0.3);
                }
                shake(t, 45);
                break;
            case DashNet.CHARGE: {
                int tier = m.data.length > 0 ? m.data[0] : 1;
                long[] ch = CHARGES.get(m.attacker);
                CHARGES.put(m.attacker, new long[] {ch != null ? ch[0] : t, tier});
                if (tier > 1) {
                    boom(m.x, m.y + 0.1, m.z, new double[] {0, 1, 0}, 0.4, 1.2 + tier * 1.2, 360, 1f, 0.85f, 0.5f, 0.8f);
                    if (local(m.attacker)) shake(t, 30 * tier);
                }
                break;
            }
            case DashNet.CHARGE_END:
                CHARGES.remove(m.attacker);
                break;
            case DashNet.BUBBLE:
                if (SteamDash.timeBubble) BUBBLES.add(new double[] {m.x, m.y, m.z, m.dx, t, t + (long) (m.dy * 50)});
                break;
            case DashNet.DEBRIS:
                for (int i = 0; i + 3 < m.data.length; i += 4) {
                    double[] look = DashMath.norm(d);
                    rubble(m.data[i] + 0.5, m.data[i + 1] + 0.5, m.data[i + 2] + 0.5, Block.getStateById(m.data[i + 3]),
                            -look[0] * 0.8 + (RAND.nextDouble() - 0.5) * 0.7, 0.2 + RAND.nextDouble() * 0.45, -look[2] * 0.8 + (RAND.nextDouble() - 0.5) * 0.7);
                }
                break;
            case DashNet.SPIKE:
                if (c != null) c.spike = t;
                if (a instanceof AbstractClientPlayer) ghost(m.attacker, pose((AbstractClientPlayer) a, 1f, m.x, m.y, m.z));
                if (m.data.length >= 3) {
                    double tx = m.data[0] / 8.0, ty = m.data[1] / 8.0, tz = m.data[2] / 8.0;
                    if (c != null) { c.hx = tx; c.hy = ty + 1; c.hz = tz; }
                    mc.world.addWeatherEffect(new EntityLightningBolt(mc.world, tx, ty, tz, true));
                    boom(tx, ty + 1, tz, new double[] {0, 1, 0}, 0.3, 3.2, 300, 1f, 0.85f, 0.4f, 0.8f);
                }
                tailStrikeDown(m.attacker);
                shake(t, 90);
                break;
            case DashNet.CRATER: {
                double r = Math.max(4, m.dx);
                if (c != null) { c.crater = t; c.cx = m.x; c.cy = m.y; c.cz = m.z; }
                thunder(m.x, m.y, m.z, 2.0f, 0.6f);
                ripple(m.x, m.y - 1, m.z, r + 6);
                skyFlash();
                boom(m.x, m.y + 0.2, m.z, new double[] {0, 1, 0}, 1.0, r * 1.35, 700, 1f, 1f, 1f, 0.8f);
                boom(m.x, m.y + 0.4, m.z, new double[] {0, 1, 0}, 0.5, r, 900, 1f, 0.65f, 0.15f, 0.6f);
                for (int i = 0; i + 3 < m.data.length; i += 4) {
                    double ox = m.data[i] + 0.5 - m.x, oz = m.data[i + 2] + 0.5 - m.z, l = Math.max(0.1, Math.sqrt(ox * ox + oz * oz));
                    IBlockState s = Block.getStateById(m.data[i + 3]);
                    for (int k = 0; k < 2; k++)
                        rubble(m.data[i] + 0.5, m.data[i + 1] + 1.2 + k * 0.6, m.data[i + 2] + 0.5, s, ox / l * (0.35 + RAND.nextDouble() * 0.3), 0.55 + RAND.nextDouble() * 0.45, oz / l * (0.35 + RAND.nextDouble() * 0.3)).spinSpeed *= 1.6f;
                }
                shake(t, 200);
                break;
            }
            case DashNet.SPLATTER: {
                double[] n = DashMath.norm(d);
                if (c != null) { c.splatter = t; c.sx = m.x; c.sy = m.y; c.sz = m.z; c.splatNormal = n; }
                boom(m.x - n[0] * 0.4, m.y, m.z - n[2] * 0.4, n, 0.4, 4.5, 420, 1f, 1f, 1f, 0.8f);
                for (int i = 0; i < 12; i++)
                    rubble(m.x - n[0] * 0.8, m.y + (RAND.nextDouble() - 0.5) * 2, m.z - n[2] * 0.8, i % 3 == 0 ? Blocks.STONE.getDefaultState() : Blocks.COBBLESTONE.getDefaultState(),
                            -n[0] * (0.4 + RAND.nextDouble() * 0.5) + (RAND.nextDouble() - 0.5) * 0.5, 0.2 + RAND.nextDouble() * 0.5, -n[2] * (0.4 + RAND.nextDouble() * 0.5) + (RAND.nextDouble() - 0.5) * 0.5);
                shake(t, 120);
                break;
            }
            case DashNet.SPRING: {
                double[] n = m.data.length >= 2 ? new double[] {m.data[0], 0, m.data[1]} : new double[] {0, 1, 0};
                boom(m.x - n[0] * 0.3, m.y + 0.8, m.z - n[2] * 0.3, n, 0.3, 2.4, 300, 1f, 1f, 1f, 0.8f);
                if (a instanceof AbstractClientPlayer) ghost(m.attacker, pose((AbstractClientPlayer) a, 1f, m.x, m.y, m.z));
                if (local(m.attacker)) springKick = t;
                break;
            }
            case DashNet.END:
                if (c != null) c.end = t;
                if (local(m.attacker)) cinematicUntil = t + 700;
                break;
            default: break;
        }
    }

    private static void tailStrike(int attacker, int target) {
        Entity a = mc().world.getEntityByID(attacker), b = mc().world.getEntityByID(target);
        if (a == null || b == null) return;
        double dx = b.posX - a.posX, dz = b.posZ - a.posZ, dy = b.posY + b.height * 0.5 - (a.posY + a.height * 0.5);
        try { KuramaTailAnimator.strike(attacker, (float) Math.toDegrees(Math.atan2(-dx, dz)), (float) Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))); } catch (Throwable ignored) {}
    }

    private static void tailStrikeDown(int attacker) {
        Entity a = mc().world.getEntityByID(attacker);
        if (a == null) return;
        try { KuramaTailAnimator.strike(attacker, a.rotationYaw, -1.1f); } catch (Throwable ignored) {}
    }

    private static void boom(double x, double y, double z, double[] n, double r0, double r1, int life, float r, float g, float b, float alpha) {
        Boom o = new Boom();
        o.x = x; o.y = y; o.z = z; o.n = DashMath.norm(n); o.born = now(); o.life = life; o.r0 = r0; o.r1 = r1; o.red = r; o.green = g; o.blue = b; o.alpha = alpha;
        BOOMS.add(o);
        if (BOOMS.size() > 64) BOOMS.remove(0);
    }

    static void ghost(int entity, Pose p) {
        Ghost g = new Ghost();
        g.entity = entity; g.pose = p; g.born = now();
        GHOSTS.add(g);
        if (GHOSTS.size() > 16) GHOSTS.remove(0);
    }

    private static void shake(long at, int strength) {
        SHAKES.add(new long[] {at, strength});
        if (SHAKES.size() > 12) SHAKES.remove(0);
    }

    /** One afterimage clone lunges from its trail pose into the target and punches. */
    private static void lunge(Cue c, Entity a, int i, double tx, double ty, double tz) {
        if (!(a instanceof AbstractClientPlayer)) return;
        Pose[] trail = c.trail.toArray(new Pose[0]);
        Pose from = trail.length > 0 ? trail[Math.max(0, Math.min(trail.length - 1, trail.length - i))] : pose((AbstractClientPlayer) a, 1f, a.posX, a.posY, a.posZ);
        Pose p = new Pose();
        p.x = from.x; p.y = from.y; p.z = from.z; p.yawOffset = p.yawHead = DashMath.lookAngles(from.x, 0, from.z, tx, 0, tz)[0];
        p.pitch = 0; p.limbSwing = from.limbSwing; p.limbAmount = 0.6f; p.swing = 0.45f; p.lean = 24; p.leanDir = c.dir;
        Ghost g = new Ghost();
        g.entity = c.attacker; g.pose = p; g.born = now(); g.lunge = 90;
        // Each clone strikes from a slightly different side.
        double side = (i % 2 == 0 ? 1 : -1) * 0.5 * ((i + 1) / 2);
        g.tx = tx - c.dir[0] * 0.9 - c.dir[2] * side; g.ty = from.y; g.tz = tz - c.dir[2] * 0.9 + c.dir[0] * side;
        GHOSTS.add(g);
        if (GHOSTS.size() > 16) GHOSTS.remove(0);
    }

    /** Thunder for the listener, late by the distance sound travels when they are far away. */
    private static void thunder(double x, double y, double z, float volume, float pitch) {
        if (!SteamDash.delayedThunder) return;
        Entity l = mc().getRenderViewEntity();
        double dist = l == null ? 0 : Math.sqrt(l.getDistanceSq(x, y, z));
        THUNDER.add(new double[] {now() + DashMath.thunderDelayMs(dist), x, y, z, volume, pitch});
    }

    private static void ripple(double x, double y, double z, double max) {
        if (SteamDash.groundRipple) RIPPLES.add(new double[] {x, y, z, Math.min(14, max), now(), 0});
    }

    private static void skyFlash() { if (mc().world != null) mc().world.setLastLightningBolt(2); }

    private static Rubble rubble(double x, double y, double z, IBlockState s, double mx, double my, double mz) {
        Rubble r = new Rubble(mc().world, x, y, z, s == null || s.getBlock() == Blocks.AIR ? Blocks.COBBLESTONE.getDefaultState() : s);
        r.motionX = mx; r.motionY = my; r.motionZ = mz;
        if (Rubble.alive < 160) { mc().world.spawnEntity(r); Rubble.alive++; }
        return r;
    }

    private static void beginCinematic() {
        cinematic = true; cinematicUntil = -1;
        if (SteamDash.cameraEffects && !SteamDash.cinematicCamera && mc().gameSettings.thirdPersonView == 0) { mc().gameSettings.thirdPersonView = 1; forcedThirdPerson = true; }
    }

    private static void endCinematic() {
        cinematic = false; cinematicUntil = -1;
        if (forcedThirdPerson && mc().gameSettings.thirdPersonView == 1) mc().gameSettings.thirdPersonView = 0;
        forcedThirdPerson = false;
    }

    /** The cue this client is in as attacker (or null). */
    private static Cue mine() { return mc().player == null ? null : CUES.get(mc().player.getEntityId()); }

    /** The cue this client is the victim of (or null). */
    private static Cue against() {
        if (mc().player == null) return null;
        int me = mc().player.getEntityId();
        for (Cue c : CUES.values()) if (c.target == me) return c;
        return null;
    }

    private static boolean dashing(Cue c) { return c.blink >= 0 && c.barrage < 0 && c.impact < 0 && c.end < 0; }
    private static boolean held(Cue c, long t) { long s = c.barrage >= 0 ? c.barrage : c.impact; return s >= 0 && c.release < 0 && c.end < 0 && t - s < 1200; }

    // ---- ticking ----

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = mc();
        if (mc.world == null || mc.player == null) { CUES.clear(); BOOMS.clear(); GHOSTS.clear(); stopInk(); if (cinematic) endCinematic(); return; }
        // The key sends PRESS when it goes down and RELEASE when it comes up; holding it overcharges.
        boolean down = KEY.isKeyDown() && mc.currentScreen == null;
        boolean tapped = false;
        while (KEY.isPressed()) tapped = true;
        if (down && !keyWasDown || tapped && !keyWasDown) SteamDash.NET.sendToServer(new DashNet.Request(DashNet.Request.PRESS));
        if (!down && (keyWasDown || tapped)) SteamDash.NET.sendToServer(new DashNet.Request(DashNet.Request.RELEASE));
        keyWasDown = down;
        long t = now();
        if (inkGroup == null && !inkFailed && mc.player.ticksExisted >= 100) prepareInk();
        for (Iterator<Cue> it = CUES.values().iterator(); it.hasNext();) {
            Cue c = it.next();
            Entity a = mc.world.getEntityByID(c.attacker);
            if (dashing(c) && a instanceof AbstractClientPlayer) {
                c.trail.addFirst(pose((AbstractClientPlayer) a, 1f, a.posX, a.posY, a.posZ));
                while (c.trail.size() > DashMath.GHOSTS) c.trail.removeLast();
                // A pressure ring peeling off the fist every tick of the dash.
                double[] f = fist(a, c, 1f);
                boom(f[0], f[1], f[2], c.dir, 0.25, 1.7, 220, 1f, 0.9f, 0.6f, 0.55f);
            } else if (!c.trail.isEmpty() && t - c.impact > 200) c.trail.removeLast();
            if (c.end >= 0 && t - c.end > 1500 || t - c.start > 9000) it.remove();
        }
        BOOMS.removeIf(b -> t - b.born > b.life);
        GHOSTS.removeIf(g -> t - g.born > 400);
        SHAKES.removeIf(s -> t - s[0] > 450);
        BUBBLES.removeIf(b -> t > b[5] + 400);
        CHARGES.keySet().removeIf(id -> mc.world.getEntityByID(id) == null);
        for (Iterator<double[]> it = THUNDER.iterator(); it.hasNext();) {
            double[] th = it.next();
            if (t < th[0]) continue;
            mc.world.playSound(th[1], th[2], th[3], net.minecraft.init.SoundEvents.ENTITY_LIGHTNING_THUNDER, net.minecraft.util.SoundCategory.WEATHER, (float) th[4], (float) th[5], false);
            it.remove();
        }
        for (Iterator<double[]> it = RIPPLES.iterator(); it.hasNext();) {
            double[] rp = it.next();
            double r = Math.min(rp[3], DashMath.rippleRadius(t - rp[4]));
            if (r >= rp[5] + 0.8) { rippleRing(rp[0], rp[1], rp[2], r); rp[5] = r; }
            if (r >= rp[3]) it.remove();
        }
        if (cinematic && (cinematicUntil >= 0 && t > cinematicUntil || mine() == null && cinematicUntil < 0 && t - lastSeenMine > 500)) endCinematic();
        if (mine() != null) lastSeenMine = t;
    }
    private static long lastSeenMine;

    /** One ring of the shock ripple: the ground blocks on the circle hop up and settle back. */
    private static void rippleRing(double x, double y, double z, double r) {
        Minecraft mc = mc();
        int n = Math.min(44, (int) (2 * Math.PI * r * 1.1));
        for (int i = 0; i < n && Rubble.ripples < 520; i++) {
            double a = i * 2 * Math.PI / n;
            BlockPos top = mc.world.getHeight(new BlockPos(x + Math.cos(a) * r, 0, z + Math.sin(a) * r));
            if (Math.abs(top.getY() - 1 - y) > 6) continue;
            BlockPos ground = top.down();
            IBlockState st = mc.world.getBlockState(ground);
            if (st.getRenderType() != net.minecraft.util.EnumBlockRenderType.MODEL || st.getMaterial().isLiquid()) continue;
            Rubble b = new Rubble(mc.world, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, st);
            b.ripple(ground.getY(), 0.35 + Math.min(0.5, 2.5 / Math.max(1, r)));
            mc.world.spawnEntity(b);
            Rubble.ripples++;
        }
    }

    @SubscribeEvent public void unload(WorldEvent.Unload e) {
        if (e.getWorld().isRemote) { CUES.clear(); BOOMS.clear(); GHOSTS.clear(); stopInk(); Rubble.alive = 0; }
    }

    /** Lock movement while rooted in the telegraph, flying the dash or held in the hit-stop. */
    @SubscribeEvent public void input(InputUpdateEvent e) {
        long t = now();
        Cue c = mine(), v = against();
        boolean locked = c != null && c.end < 0 && (c.release < 0 || c.spike >= 0 && t - c.spike < 250) || v != null && held(v, t);
        if (!locked) return;
        e.getMovementInput().moveForward = 0; e.getMovementInput().moveStrafe = 0;
        e.getMovementInput().jump = false; e.getMovementInput().sneak = false;
        e.getMovementInput().forwardKeyDown = e.getMovementInput().backKeyDown = e.getMovementInput().leftKeyDown = e.getMovementInput().rightKeyDown = false;
    }

    // ---- camera ----

    @SubscribeEvent public void fov(EntityViewRenderEvent.FOVModifier e) {
        if (!SteamDash.cameraEffects || DashCamera.active()) return;
        long t = now();
        Cue c = mine();
        float k = 1f;
        if (c != null) {
            double sinceStart = t - c.start, blinkAt = c.telegraph * 50.0;
            k = DashMath.fov(sinceStart * (200.0 / Math.max(50, blinkAt)) * (sinceStart < blinkAt ? 1 : 0) + (sinceStart >= blinkAt ? 200 + (sinceStart - blinkAt) : 0));
            if (held(c, t)) k = Math.min(k, 0.94f);   // tight on the contact frame
            if (c.release >= 0) k *= 1f + 0.1f * (float) Math.max(0, 1 - (t - c.release) / 350.0);
        }
        if (springKick >= 0 && t - springKick < 400) k *= 1f + 0.12f * (float) (1 - (t - springKick) / 400.0);
        if (k != 1f) e.setFOV(e.getFOV() * k);
    }

    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup e) {
        if (!SteamDash.cameraEffects || mc().player == null) return;
        long t = now();
        pullBack(t, (float) e.getRenderPartialTicks());
        if (SHAKES.isEmpty()) return;
        double amp = 0;
        for (long[] s : SHAKES) amp = Math.max(amp, DashMath.shake(t - s[0]) * s[1] / 100.0);
        Cue c = mine(), v = against();
        if (c == null && v == null) amp *= 0.5;   // bystanders feel it less
        if (amp <= 0) return;
        e.setYaw(e.getYaw() + (float) (amp * Math.sin(t * 0.093)));
        e.setPitch(e.getPitch() + (float) (amp * Math.sin(t * 0.121 + 1.3)));
        e.setRoll(e.getRoll() + (float) (amp * 0.7 * Math.sin(t * 0.077 + 2.1)));
    }

    /**
     * Cut-scene framing: the stock third-person camera sits inside a nine-tail rig, so pull it back and a
     * little over the shoulder while the move plays, never through walls.
     */
    private static void pullBack(long t, float pt) {
        Minecraft mc = mc();
        if (!forcedThirdPerson || mc.gameSettings.thirdPersonView != 1 || mc.getRenderViewEntity() != mc.player) return;
        float k = 1f;   // hard cut to the wide shot; only the return eases
        if (cinematicUntil >= 0) k = Math.min(k, Math.max(0f, (cinematicUntil - t) / 700f));
        if (k <= 0) return;
        k = k * k * (3 - 2 * k);
        double extra = 5.0 * k, side = 1.2 * k, up = 0.5 * k;
        net.minecraft.util.math.Vec3d eye = mc.player.getPositionEyes(pt), look = mc.player.getLook(pt);
        net.minecraft.util.math.RayTraceResult hit = mc.world.rayTraceBlocks(eye, eye.subtract(look.scale(4 + extra + 0.6)), false, true, false);
        if (hit != null && hit.hitVec != null) extra = Math.max(0, Math.min(extra, hit.hitVec.distanceTo(eye) - 4 - 0.6));
        GlStateManager.translate(-side, -up, -extra);
    }

    /** Ink silhouette shader on the hold frames; switched by frame so it never outlives the hit-stop. */
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent e) {
        Minecraft mc = mc();
        if (mc.world == null || mc.player == null) return;
        long t = now();
        if (e.phase == TickEvent.Phase.START) {
            warmInk();
            int f = impactFrame(t);
            if (f == 2) { if (!inkInstalled) startInk(); }   // retried every ink frame until it is really in place
            else if (inkOn || inkInstalled) stopInk();
            return;
        }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof net.minecraft.client.gui.GuiChat)) return;
        mc.entityRenderer.setupOverlayRendering();
        ScaledResolution sr = new ScaledResolution(mc);
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        Cue c = mine();
        // Speed lines toward the centre while you fly.
        if (c != null && SteamDash.cameraEffects && (dashing(c) || c.release >= 0 && t - c.release < 350)) speedLines(w, h, dashing(c) ? 0.42f : 0.3f * (1 - (t - c.release) / 350f));
        // Letterbox bars.
        if (SteamDash.letterbox && (c != null || cinematic)) {
            float in = c == null ? 1 : Math.min(1, (t - c.start) / 160f), out = c != null && c.end >= 0 ? Math.max(0, 1 - (t - c.end) / 320f) : cinematic && cinematicUntil >= 0 ? Math.max(0, (cinematicUntil - t) / 700f) : 1;
            int bar = (int) (h * 0.11f * Math.min(in, out));
            if (bar > 0) { Gui.drawRect(0, 0, w, bar, 0xFF000000); Gui.drawRect(0, h - bar, w, h, 0xFF000000); }
        }
        // Time bubble: a cold, slightly drained tint while you stand inside one.
        Entity view = mc.getRenderViewEntity();
        for (double[] b : BUBBLES) {
            if (view == null || t < b[4] || view.getDistanceSq(b[0], b[1], b[2]) > b[3] * b[3]) continue;
            float k = (float) Math.min(1, Math.min((t - b[4]) / 150.0, Math.max(0, (b[5] + 300 - t) / 300.0)));
            if (k > 0) Gui.drawRect(0, 0, w, h, ((int) (0x38 * k) << 24) | 0x5A7FA8);
            break;
        }
        // A short white tick for each small hit (clone jabs, pierce passes, relays) you are part of.
        for (Cue jc : CUES.values()) if (jc.jab >= 0 && t - jc.jab < 45 && (local(jc.attacker) || local(jc.target))) { Gui.drawRect(0, 0, w, h, 0x50FFFFFF); break; }
        int frame = impactFrame(t);
        if (frame == 1) { Gui.drawRect(0, 0, w, h, 0xF8FFFFFF); whiteFrames++; if (lastFrameCue != null) lastFrameCue.whiteDrawn++; }
        if (frame == 2) { if (inkInstalled && inkActive()) inkFrames++; if (lastFrameCue != null) lastFrameCue.inkDrawn++; }
        if (frame == 2 && !inkInstalled) Gui.drawRect(0, 0, w, h, 0x99000000);   // no shader support: a dark hold instead
    }

    /** The impact frame for this client: attackers, victims and anyone within 32 blocks see it. */
    private static int impactFrame(long t) {
        if (!SteamDash.impactFrames || mc().player == null) return 0;
        for (Cue c : CUES.values()) {
            if (c.impact < 0) continue;
            boolean involved = local(c.attacker) || local(c.target) || mc().player.getDistanceSq(c.hx, c.hy, c.hz) < 32 * 32;
            if (!involved) continue;
            // Timed from contact only, and every impact gets at least one white and one ink frame even at a low frame rate.
            long since = t - c.impact;
            int f = DashMath.impactFrame(since / 50.0);
            if (c.whiteDrawn == 0 && since < 400) f = 1;
            else if (f == 0 && c.inkDrawn == 0 && since < 450) f = 2;
            if (f != 0) { lastFrameCue = c; return f; }
        }
        return 0;
    }

    // The ink frame is a post shader. It is compiled once ahead of time and swapped into the renderer for
    // the hold frames, so the impact never waits on a shader compile (and is never deleted mid-fight).
    static net.minecraft.client.shader.ShaderGroup inkGroup;
    static boolean inkFailed, inkInstalled;
    private static int inkW, inkH;
    private static Field rendererShader, rendererUse;

    static void prepareInk() {
        Minecraft mc = mc();
        if (inkGroup != null || inkFailed || !OpenGlHelper.shadersSupported || !SteamDash.impactFrames) return;
        try {
            rendererShader = field(net.minecraft.client.renderer.EntityRenderer.class, "field_147707_d", "shaderGroup");
            rendererUse = field(net.minecraft.client.renderer.EntityRenderer.class, "field_175083_ad", "useShader");
            inkGroup = new net.minecraft.client.shader.ShaderGroup(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), INK);
            inkGroup.createBindFramebuffers(mc.displayWidth, mc.displayHeight);
            inkW = mc.displayWidth; inkH = mc.displayHeight;
        } catch (Exception | LinkageError failed) {
            inkFailed = true; inkGroup = null;
            org.apache.logging.log4j.LogManager.getLogger("SteamDash").warn("Ink impact frame unavailable, using the dark hold frame instead", failed);
        }
    }

    private static boolean inkWarm;

    /** Draws the ink pass once before the world renders (the frame clears it), so its first real use is instant. */
    private static void warmInk() {
        if (inkWarm || inkGroup == null) return;
        inkWarm = true;
        try {
            GlStateManager.matrixMode(GL11.GL_TEXTURE); GlStateManager.pushMatrix(); GlStateManager.loadIdentity();
            inkGroup.render(0f);
            GlStateManager.popMatrix(); GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            mc().getFramebuffer().bindFramebuffer(true);
        } catch (RuntimeException e) { inkFailed = true; inkGroup = null; }
    }

    private static Field field(Class<?> c, String srg, String mcp) throws NoSuchFieldException {
        Field f;
        try { f = c.getDeclaredField(srg); } catch (NoSuchFieldException dev) { f = c.getDeclaredField(mcp); }
        f.setAccessible(true);
        return f;
    }

    private static void startInk() {
        inkOn = true;
        Minecraft mc = mc();
        prepareInk();
        if (inkGroup == null) { inkSkips[0]++; return; }
        try {
            Object current = rendererShader.get(mc.entityRenderer);
            if (current != null && current != inkGroup) { inkSkips[1]++; return; }   // never replace someone else's shader
            if (inkW != mc.displayWidth || inkH != mc.displayHeight) { inkGroup.createBindFramebuffers(mc.displayWidth, mc.displayHeight); inkW = mc.displayWidth; inkH = mc.displayHeight; }
            rendererShader.set(mc.entityRenderer, inkGroup);
            rendererUse.setBoolean(mc.entityRenderer, true);
            inkInstalled = true;
        } catch (IllegalAccessException e) { inkFailed = true; }
    }

    static final int[] inkSkips = new int[2];   // diagnostics: {no shader group, another shader active}

    private static boolean inkActive() {
        try { return rendererShader != null && rendererShader.get(mc().entityRenderer) == inkGroup && rendererUse.getBoolean(mc().entityRenderer); }
        catch (IllegalAccessException e) { return false; }
    }

    private static void stopInk() {
        if (inkInstalled) {
            try { if (rendererShader.get(mc().entityRenderer) == inkGroup) rendererShader.set(mc().entityRenderer, null); } catch (IllegalAccessException ignored) {}
            inkInstalled = false;
        }
        inkOn = false;
    }

    private static void speedLines(int w, int h, float alpha) {
        if (alpha <= 0) return;
        GlStateManager.disableTexture2D(); GlStateManager.enableBlend(); GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        Tessellator ts = Tessellator.getInstance(); BufferBuilder b = ts.getBuffer();
        b.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        double cx = w / 2.0, cy = h / 2.0, far = Math.hypot(w, h) * 0.6;
        Random r = new Random(now() / 33);
        for (int i = 0; i < 46; i++) {
            double ang = r.nextDouble() * Math.PI * 2, inner = far * (0.38 + r.nextDouble() * 0.3), width = 0.004 + r.nextDouble() * 0.01;
            double ox = Math.cos(ang), oy = Math.sin(ang);
            b.pos(cx + Math.cos(ang - width) * far, cy + Math.sin(ang - width) * far, 0).color(1f, 1f, 1f, alpha).endVertex();
            b.pos(cx + Math.cos(ang + width) * far, cy + Math.sin(ang + width) * far, 0).color(1f, 1f, 1f, alpha).endVertex();
            b.pos(cx + ox * inner, cy + oy * inner, 0).color(1f, 0.95f, 0.85f, 0f).endVertex();
        }
        ts.draw();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableAlpha(); GlStateManager.disableBlend(); GlStateManager.enableTexture2D();
    }

    // ---- body pose and the white-hot pass ----

    private static final Map<Integer, double[]> RESTORE = new HashMap<>();

    /** Lean and punch pose, applied around the real render; also keeps the tail physics from reading the dash as a teleport. */
    @SubscribeEvent(priority = EventPriority.LOWEST) public void prePlayer(RenderPlayerEvent.Pre e) {
        if (rendering) return;
        EntityPlayer p = e.getEntityPlayer();
        Cue c = CUES.get(p.getEntityId());
        if (c == null && !CHARGES.containsKey(p.getEntityId())) return;
        long t = now();
        double[] save = {p.prevPosX, p.prevPosY, p.prevPosZ, p.swingProgress, p.prevSwingProgress};
        if (c == null) { RESTORE.put(p.getEntityId(), save); GlStateManager.pushMatrix(); return; }
        RESTORE.put(p.getEntityId(), save);
        double dx = p.posX - p.prevPosX, dy = p.posY - p.prevPosY, dz = p.posZ - p.prevPosZ;
        if (dx * dx + dy * dy + dz * dz > 15) {   // the tails reset on >4 blocks a tick; show them streaming back instead
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz) / 3.6;
            p.prevPosX = p.posX - dx / l; p.prevPosY = p.posY - dy / l; p.prevPosZ = p.posZ - dz / l;
        }
        if (dashing(c) || held(c, t)) { p.swingProgress = 0.32f; p.prevSwingProgress = 0.32f; }
        GlStateManager.pushMatrix();
        lean(c, t, e.getX(), e.getY(), e.getZ());
    }

    @SubscribeEvent public void postPlayer(RenderPlayerEvent.Post e) {
        if (rendering) return;
        EntityPlayer p = e.getEntityPlayer();
        double[] save = RESTORE.remove(p.getEntityId());
        if (save == null) return;
        Cue c = CUES.get(p.getEntityId());
        long t = now();
        long[] ch = CHARGES.get(p.getEntityId());
        if (c == null && ch != null && p instanceof AbstractClientPlayer) {
            // Overcharge: the armor and tails flare white-hot, pulsing faster as the tier climbs.
            float k = (float) Math.min(1, (t - ch[0]) / 1000.0);
            float heat = 0.12f + 0.45f * k + 0.08f * (float) Math.sin(t * (0.012 + 0.008 * ch[1]));
            Pose pose = pose((AbstractClientPlayer) p, e.getPartialRenderTick(), 0, 0, 0);
            pose.x = e.getX(); pose.y = e.getY(); pose.z = e.getZ(); pose.lean = 0;
            tinted((AbstractClientPlayer) p, e.getRenderer(), pose, 0, 0, 0, 1.0f, 0.85f, 0.75f, heat, e.getPartialRenderTick());
        }
        if (c != null && p instanceof AbstractClientPlayer) {
            float heat = 0;
            if (c.blink < 0) heat = Math.min(1f, (t - c.start) / (c.telegraph * 50f)) * 0.72f;
            else if (t - c.blink < 160) heat = 0.75f * (1 - (t - c.blink) / 160f) + 0.15f;
            else if (dashing(c)) heat = 0.15f;
            if (heat > 0.01f) {
                Pose pose = pose((AbstractClientPlayer) p, e.getPartialRenderTick(), 0, 0, 0);
                pose.x = e.getX(); pose.y = e.getY(); pose.z = e.getZ(); pose.lean = 0;
                tinted((AbstractClientPlayer) p, e.getRenderer(), pose, 0, 0, 0, 1.0f, 0.85f, 0.75f, heat, e.getPartialRenderTick());
            }
        }
        GlStateManager.popMatrix();
        p.prevPosX = save[0]; p.prevPosY = save[1]; p.prevPosZ = save[2]; p.swingProgress = (float) save[3]; p.prevSwingProgress = (float) save[4];
    }

    /** Lean angle (degrees) over the sequence, about the hips, toward the dash direction. */
    private static float leanAngle(Cue c, long t) {
        if (c.spike >= 0 && t - c.spike < 700) return 62f * (float) Math.min(1, (t - c.spike) / 80.0) * (float) Math.min(1, (700 - (t - c.spike)) / 250.0);
        if (c.blink < 0) return 16f * Math.min(1f, (t - c.start) / (c.telegraph * 50f));
        if (dashing(c)) return 38f;
        if (held(c, t)) return 30f;
        if (c.release >= 0 && t - c.release < 300) return 30f * (1 - (t - c.release) / 300f);
        return 0;
    }

    private static void lean(Cue c, long t, double x, double y, double z) {
        float ang = leanAngle(c, t);
        if (ang <= 0.01f) return;
        double[] d = c.spike >= 0 && t - c.spike < 700 ? spikeLeanDir(c) : c.dir;
        double hx = d[0], hz = d[2], l = Math.sqrt(hx * hx + hz * hz);
        if (l < 1e-4) return;
        // Axis = up x heading, so a positive turn tips the head toward the heading.
        double ax = hz / l, az = -hx / l;
        GlStateManager.translate(x, y + 0.9, z);
        GlStateManager.rotate(ang, (float) ax, 0, (float) az);
        GlStateManager.translate(-x, -(y + 0.9), -z);
    }

    private static double[] spikeLeanDir(Cue c) {
        Entity a = mc().world.getEntityByID(c.attacker);
        if (a == null) return c.dir;
        double yaw = Math.toRadians(a.rotationYaw);
        return new double[] {-Math.sin(yaw), 0, Math.cos(yaw)};
    }

    static Pose pose(AbstractClientPlayer e, float pt, double x, double y, double z) {
        Pose p = new Pose();
        p.x = x; p.y = y; p.z = z;
        p.yawOffset = interp(e.prevRenderYawOffset, e.renderYawOffset, pt);
        p.yawHead = interp(e.prevRotationYawHead, e.rotationYawHead, pt);
        p.pitch = e.prevRotationPitch + (e.rotationPitch - e.prevRotationPitch) * pt;
        p.limbAmount = e.prevLimbSwingAmount + (e.limbSwingAmount - e.prevLimbSwingAmount) * pt;
        p.limbSwing = e.limbSwing - e.limbSwingAmount * (1 - pt);
        p.swing = e.getSwingProgress(pt);
        p.sneak = e.isSneaking();
        p.at = now();
        Cue c = CUES.get(e.getEntityId());
        if (c != null) { p.lean = leanAngle(c, now()); p.leanDir = c.dir; if (dashing(c)) p.swing = 0.32f; }
        return p;
    }

    private static float interp(float a, float b, float t) {
        float d = MathHelper.wrapDegrees(b - a);
        return a + t * d;
    }

    private static Field layerField;

    @SuppressWarnings("unchecked")
    private static List<LayerRenderer<AbstractClientPlayer>> layers(RenderLivingBase<?> r) {
        try {
            if (layerField == null) {
                for (String n : new String[] {"field_177097_h", "layerRenderers"}) {
                    try { layerField = RenderLivingBase.class.getDeclaredField(n); layerField.setAccessible(true); break; } catch (NoSuchFieldException ignored) {}
                }
            }
            return layerField == null ? java.util.Collections.emptyList() : (List<LayerRenderer<AbstractClientPlayer>>) layerField.get(r);
        } catch (IllegalAccessException e) { return java.util.Collections.emptyList(); }
    }

    /**
     * Renders a player's model and armor layers as a flat additive colour: src * (r, g, b) * alpha + dst.
     * The blend colour is a GL constant, so the colour calls the armor layers make cannot undo it.
     * (camX, camY, camZ) is subtracted from the pose position.
     */
    static void tinted(AbstractClientPlayer e, RenderPlayer r, Pose pose, double camX, double camY, double camZ, float red, float green, float blue, float alpha, float pt) {
        if (rendering || alpha <= 0.005f) return;
        rendering = true;
        float[] saved = {e.renderYawOffset, e.prevRenderYawOffset, e.rotationYawHead, e.prevRotationYawHead, e.rotationPitch, e.prevRotationPitch, e.limbSwing, e.limbSwingAmount, e.prevLimbSwingAmount, e.swingProgress, e.prevSwingProgress};
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        try {
            e.renderYawOffset = e.prevRenderYawOffset = pose.yawOffset;
            e.rotationYawHead = e.prevRotationYawHead = pose.yawHead;
            e.rotationPitch = e.prevRotationPitch = pose.pitch;
            e.limbSwing = pose.limbSwing; e.limbSwingAmount = e.prevLimbSwingAmount = pose.limbAmount;
            e.swingProgress = e.prevSwingProgress = pose.swing;
            float age = e.ticksExisted + pt;
            float headYaw = pose.yawHead - pose.yawOffset, limb = pose.limbSwing, amount = pose.limbAmount;
            GlStateManager.pushMatrix();
            double x = pose.x - camX, y = pose.y - camY, z = pose.z - camZ;
            if (pose.lean > 0.01f && pose.leanDir != null) {
                double hx = pose.leanDir[0], hz = pose.leanDir[2], l = Math.sqrt(hx * hx + hz * hz);
                if (l > 1e-4) {
                    GlStateManager.translate(x, y + 0.9, z);
                    GlStateManager.rotate(pose.lean, (float) (hz / l), 0, (float) (-hx / l));
                    GlStateManager.translate(-x, -(y + 0.9), -z);
                }
            }
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(180f - pose.yawOffset, 0, 1, 0);
            GlStateManager.enableRescaleNormal();
            GlStateManager.scale(-1f, -1f, 1f);
            GlStateManager.scale(0.9375f, 0.9375f, 0.9375f);
            GlStateManager.translate(0f, -1.501f, 0f);
            net.minecraft.client.model.ModelPlayer model = r.getMainModel();
            model.swingProgress = pose.swing; model.isRiding = false; model.isChild = false; model.isSneak = pose.sneak;
            model.setLivingAnimations(e, limb, amount, pt);
            model.setRotationAngles(limb, amount, age, headYaw, pose.pitch, 0.0625f, e);
            GlStateManager.disableLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
            GlStateManager.depthMask(false);
            tint(red, green, blue, alpha);
            mc().getTextureManager().bindTexture(e.getLocationSkin());
            model.render(e, limb, amount, age, headYaw, pose.pitch, 0.0625f);
            for (LayerRenderer<AbstractClientPlayer> layer : layers(r)) {
                Object kind = layer;
                if (kind instanceof LayerArrow || kind instanceof LayerEntityOnShoulder) continue;
                tint(red, green, blue, alpha);
                try { layer.doRenderLayer(e, limb, amount, pt, age, headYaw, pose.pitch, 0.0625f); } catch (RuntimeException ignored) {}
            }
            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix();
        } finally {
            GL14.glBlendColor(0f, 0f, 0f, 0f);
            GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            if (!blend) GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            if (lighting) GlStateManager.enableLighting();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
            GlStateManager.color(1f, 1f, 1f, 1f);
            e.renderYawOffset = saved[0]; e.prevRenderYawOffset = saved[1]; e.rotationYawHead = saved[2]; e.prevRotationYawHead = saved[3];
            e.rotationPitch = saved[4]; e.prevRotationPitch = saved[5]; e.limbSwing = saved[6]; e.limbSwingAmount = saved[7]; e.prevLimbSwingAmount = saved[8];
            e.swingProgress = saved[9]; e.prevSwingProgress = saved[10];
            rendering = false;
        }
    }

    private static void tint(float r, float g, float b, float a) {
        GlStateManager.enableBlend();
        GL14.glBlendColor(r * a, g * a, b * a, 1f);
        GlStateManager.blendFunc(CONSTANT_COLOR, GL11.GL_ONE);
        GlStateManager.depthMask(false);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);   // transparent texels never glow
    }

    // ---- world-space effects ----

    /** Where the punching fist is: chest height, right shoulder, reaching forward along the dash. */
    private static double[] fist(Entity a, Cue c, float pt) {
        double x = a.prevPosX + (a.posX - a.prevPosX) * pt, y = a.prevPosY + (a.posY - a.prevPosY) * pt, z = a.prevPosZ + (a.posZ - a.prevPosZ) * pt;
        double[] d = c.dir;
        Entity t = mc().world.getEntityByID(c.target);
        if (t != null) d = DashMath.direction(x, y + 1.2, z, t.posX, t.posY + t.height / 2, t.posZ);
        double hx = d[0], hz = d[2], l = Math.max(1e-4, Math.sqrt(hx * hx + hz * hz));
        double rx = -hz / l, rz = hx / l;   // right of the heading
        return new double[] {x + rx * 0.33 + d[0] * 0.75, y + 1.25 + d[1] * 0.75, z + rz * 0.33 + d[2] * 0.75, d[0], d[1], d[2]};
    }

    @SubscribeEvent public void world(RenderWorldLastEvent e) {
        Minecraft mc = mc();
        if (mc.world == null || CUES.isEmpty() && BOOMS.isEmpty() && GHOSTS.isEmpty() && BUBBLES.isEmpty()) return;
        RenderManager rm = mc.getRenderManager();
        double cx = rm.viewerPosX, cy = rm.viewerPosY, cz = rm.viewerPosZ;
        float pt = e.getPartialTicks();
        long t = now();
        // Golden afterimage clones: the dash trail, and the poses left behind at blink, spring and spike.
        for (Cue c : CUES.values()) {
            Entity a = mc.world.getEntityByID(c.attacker);
            if (!(a instanceof AbstractClientPlayer)) continue;
            Object r = rm.getEntityRenderObject(a);
            if (!(r instanceof RenderPlayer)) continue;
            int i = 0;
            for (Pose p : c.trail) {
                if (nearOwnCamera(a, p, cx, cy, cz)) { i++; continue; }
                float alpha = DashMath.trailAlpha(i++) * (c.impact >= 0 ? Math.max(0, 1 - (t - c.impact) / 300f) : 1);
                double cd = Math.sqrt((p.x - cx) * (p.x - cx) + (p.y + 1 - cy) * (p.y + 1 - cy) + (p.z - cz) * (p.z - cz));
                alpha *= (float) Math.max(0, Math.min(1, (cd - 2.2) / 4.0));   // a clone close to the lens fades instead of blooming
                tinted((AbstractClientPlayer) a, (RenderPlayer) r, p, cx, cy, cz, 1.0f, 0.6f, 0.1f, alpha, pt);
            }
        }
        for (Ghost g : GHOSTS) {
            Entity a = mc.world.getEntityByID(g.entity);
            if (!(a instanceof AbstractClientPlayer)) continue;
            Object r = rm.getEntityRenderObject(a);
            if (!(r instanceof RenderPlayer) || nearOwnCamera(a, g.pose, cx, cy, cz)) continue;
            float alpha = DashMath.ghostAlpha((t - g.born) / 50.0);
            Pose p = g.pose;
            if (g.lunge > 0) {   // a barrage clone: dart in, punch, fade
                double u = DashMath.ease((t - g.born) / (double) g.lunge);
                Pose q = new Pose();
                q.x = p.x + (g.tx - p.x) * u; q.y = p.y + (g.ty - p.y) * u; q.z = p.z + (g.tz - p.z) * u;
                q.yawOffset = p.yawOffset; q.yawHead = p.yawHead; q.pitch = p.pitch; q.limbSwing = p.limbSwing; q.limbAmount = p.limbAmount;
                q.swing = (float) (0.15 + 0.5 * u); q.lean = p.lean; q.leanDir = p.leanDir;
                p = q;
                alpha = 0.42f * (float) Math.max(0, 1 - Math.max(0, t - g.born - g.lunge) / 220.0);
            }
            tinted((AbstractClientPlayer) a, (RenderPlayer) r, p, cx, cy, cz, 1.0f, 0.6f, 0.1f, alpha, pt);
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(-cx, -cy, -cz);
        GlStateManager.disableTexture2D(); GlStateManager.disableLighting(); GlStateManager.disableCull(); GlStateManager.disableAlpha();
        GlStateManager.enableBlend(); GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false); GlStateManager.shadeModel(GL11.GL_SMOOTH);
        float lx = OpenGlHelper.lastBrightnessX, ly = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        // Mach cone over the punching arm.
        for (Cue c : CUES.values()) {
            float k = dashing(c) ? 1f : c.impact >= 0 && c.release < 0 ? Math.max(0, 1 - (t - c.impact) / 150f) : 0;
            if (k <= 0) continue;
            Entity a = mc.world.getEntityByID(c.attacker);
            if (a == null) continue;
            double[] f = fist(a, c, pt);
            double[] d = {f[3], f[4], f[5]};
            double spin = t * 0.012;
            cone(f[0], f[1], f[2], d, 0.95, 1.7, 1.15, spin, 1f, 0.78f, 0.25f, 0.55f * k);
            cone(f[0], f[1], f[2], d, 0.75, 1.3, 0.75, -spin * 1.4, 1f, 1f, 1f, 0.7f * k);
        }
        for (double[] bb : BUBBLES) {   // the time bubble: a faint shell of latitude and longitude rings
            if (t < bb[4]) continue;
            double grow = DashMath.ease((t - bb[4]) / 220.0), fade = Math.max(0, Math.min(1, (bb[5] + 300 - t) / 300.0));
            double rr = bb[3] * grow;
            float al = (float) (0.22 * fade);
            for (int k = -2; k <= 2; k++) {
                double yy = rr * k / 3.0, rad = Math.sqrt(Math.max(0, rr * rr - yy * yy));
                ring(bb[0], bb[1] + yy, bb[2], new double[] {0, 1, 0}, rad, 0.12, 0.75f, 0.88f, 1f, al);
            }
            double spin = t * 0.0006;
            for (int k = 0; k < 3; k++) {
                double a = spin + k * Math.PI / 3;
                ring(bb[0], bb[1], bb[2], new double[] {Math.cos(a), 0, Math.sin(a)}, rr, 0.12, 0.75f, 0.88f, 1f, al);
            }
            double pulse = ((t - bb[4]) % 600) / 600.0;   // slow pulses travelling outward
            ring(bb[0], bb[1], bb[2], new double[] {0, 1, 0}, rr * pulse, 0.3, 0.85f, 0.95f, 1f, (float) (0.25 * (1 - pulse) * fade));
        }
        for (Boom b : BOOMS) {
            double u = (t - b.born) / (double) b.life;
            if (u < 0 || u > 1) continue;
            double ease = 1 - Math.pow(1 - u, 3);
            ring(b.x, b.y, b.z, b.n, b.r0 + (b.r1 - b.r0) * ease, 0.18 + 0.35 * (1 - u), b.red, b.green, b.blue, b.alpha * (float) (1 - u));
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx, ly);
        GlStateManager.shadeModel(GL11.GL_FLAT); GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend(); GlStateManager.enableAlpha(); GlStateManager.enableCull(); GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    /** An afterimage right at the lens would just fill the screen (first person, or a cut-scene shot); skip it there. */
    private static boolean nearOwnCamera(Entity a, Pose p, double cx, double cy, double cz) {
        double dx = p.x - cx, dy = p.y + 1.0 - cy, dz = p.z - cz, d2 = dx * dx + dy * dy + dz * dz;
        if (a == mc().getRenderViewEntity() && mc().gameSettings.thirdPersonView == 0) return d2 < 3.0 * 3.0;
        return d2 < 2.2 * 2.2;
    }

    /** A rotating, striped pressure cone: apex ahead of the fist, opening backward along the arm. */
    private static void cone(double x, double y, double z, double[] d, double ahead, double back, double radius, double spin, float r, float g, float b, float a) {
        double[][] base = DashMath.ring(d, 1, 24);
        Tessellator ts = Tessellator.getInstance(); BufferBuilder buf = ts.getBuffer();
        buf.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        double ax = x + d[0] * ahead, ay = y + d[1] * ahead, az = z + d[2] * ahead;
        double bx = x - d[0] * back, by = y - d[1] * back, bz = z - d[2] * back;
        for (int i = 0; i <= 24; i++) {
            double[] p = base[i % 24];
            double s = 0.55 + 0.45 * Math.sin(i * Math.PI * 6 / 24 + spin);   // stripes that turn
            buf.pos(ax + p[0] * 0.06, ay + p[1] * 0.06, az + p[2] * 0.06).color(r, g, b, (float) (a * s)).endVertex();
            buf.pos(bx + p[0] * radius, by + p[1] * radius, bz + p[2] * radius).color(r, g, b, 0f).endVertex();
        }
        ts.draw();
    }

    private static void ring(double x, double y, double z, double[] n, double radius, double width, float r, float g, float b, float a) {
        if (a <= 0.003f) return;
        double[][] pts = DashMath.ring(n, 1, 48);
        Tessellator ts = Tessellator.getInstance(); BufferBuilder buf = ts.getBuffer();
        buf.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        double inner = Math.max(0, radius - width);
        for (int i = 0; i <= 48; i++) {
            double[] p = pts[i % 48];
            buf.pos(x + p[0] * radius, y + p[1] * radius, z + p[2] * radius).color(r, g, b, a).endVertex();
            buf.pos(x + p[0] * inner, y + p[1] * inner, z + p[2] * inner).color(r, g, b, 0f).endVertex();
        }
        ts.draw();
    }

    // ---- rubble ----

    /** Client-only flying block chunk: tumbles, bounces and is gone in two seconds. Never placed, never dropped. */
    public static final class Rubble extends EntityFallingBlock {
        static int alive;
        float spin, prevSpin, spinSpeed, scale;
        float axisX, axisZ;
        int life, maxLife;

        public Rubble(World w) { super(w); }

        Rubble(World w, double x, double y, double z, IBlockState s) {
            super(w, x, y, z, s);
            fallTime = 1; shouldDropItem = false;
            spinSpeed = 14f + RAND.nextFloat() * 22f;
            double ang = RAND.nextDouble() * Math.PI * 2;
            axisX = (float) Math.cos(ang); axisZ = (float) Math.sin(ang);
            scale = 0.45f + RAND.nextFloat() * 0.45f;
            maxLife = 34 + RAND.nextInt(14);
        }

        static int ripples;
        boolean ripple; double baseY, height;

        /** Ripple mode: no flight, just a hop of `h` blocks over 8 ticks, exactly over its own ground block. */
        void ripple(double y, double h) {
            ripple = true; baseY = y; height = h; scale = 1.003f; spinSpeed = 0; maxLife = 8;
            setPosition(posX, y, posZ); prevPosY = y;
        }

        float lift(float pt) { return ripple ? (float) (Math.sin(Math.PI * Math.min(1, (life + pt) / maxLife)) * height) : 0f; }

        @Override public void onUpdate() {
            if (ripple) {
                prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
                if (++life > maxLife) setDead();
                return;
            }
            prevPosX = posX; prevPosY = posY; prevPosZ = posZ; prevSpin = spin;
            spin += spinSpeed;
            motionY -= 0.045;
            move(MoverType.SELF, motionX, motionY, motionZ);
            motionX *= 0.97; motionY *= 0.98; motionZ *= 0.97;
            if (onGround) { motionX *= 0.55; motionZ *= 0.55; motionY *= -0.35; spinSpeed *= 0.5f; }
            if (++life > maxLife) setDead();
        }
        @Override public void setDead() { if (!isDead) { if (ripple) ripples = Math.max(0, ripples - 1); else alive = Math.max(0, alive - 1); } super.setDead(); }
        @Override public boolean canBeCollidedWith() { return false; }
        @Override public boolean canBePushed() { return false; }
        @Override public boolean canBeAttackedWithItem() { return false; }
    }

    static final class RubbleRender extends Render<Rubble> {
        RubbleRender(RenderManager m) { super(m); shadowSize = 0.3f; }

        @Override public void doRender(Rubble e, double x, double y, double z, float yaw, float pt) {
            IBlockState s = e.getBlock();
            if (s == null || s.getRenderType() != net.minecraft.util.EnumBlockRenderType.MODEL) return;
            bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            GlStateManager.pushMatrix();
            GlStateManager.disableLighting();
            float fade = e.ripple ? 1f : e.life > e.maxLife - 6 ? Math.max(0.05f, (e.maxLife - e.life) / 6f) : 1f;
            float lift = e.lift(pt);
            if (e.ripple && lift < 0.04f) { GlStateManager.enableLighting(); GlStateManager.popMatrix(); return; }   // at rest it would just overlap its own block
            BlockPos at = new BlockPos(e.posX, e.posY, e.posZ);
            if (e.ripple) at = at.up();   // light a lifted ground block from the air above, not from inside the ground
            GlStateManager.translate(x, y + 0.5 * e.scale + lift, z);
            GlStateManager.rotate(e.prevSpin + (e.spin - e.prevSpin) * pt, e.axisX, 0.35f, e.axisZ);
            GlStateManager.scale(e.scale * fade, e.scale * fade, e.scale * fade);
            GlStateManager.translate(-at.getX() - 0.5, -at.getY() - 0.5, -at.getZ() - 0.5);
            Tessellator ts = Tessellator.getInstance(); BufferBuilder b = ts.getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            BlockRendererDispatcher d = Minecraft.getMinecraft().getBlockRendererDispatcher();
            d.getBlockModelRenderer().renderModel(e.world, d.getModelForState(s), s, at, b, false, MathHelper.getPositionRandom(e.getOrigin()));
            ts.draw();
            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
            super.doRender(e, x, y, z, yaw, pt);
        }

        @Override protected ResourceLocation getEntityTexture(Rubble e) { return TextureMap.LOCATION_BLOCKS_TEXTURE; }
    }
}
