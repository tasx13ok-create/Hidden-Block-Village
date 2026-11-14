package village.fieldhud;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/**
 * Shared live state for the Fable hotbars: body motion springs (sway, bob, landing squash),
 * damage shake, and smoothed environment readings (cold, heat, rain, underwater, darkness,
 * burning, low health, lightning). Also owns a small 2D pixel-particle field for HUD weather.
 */
public final class FableEnv {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private FableEnv() {}

    public static final FableHudMath.Spring SWAY = new FableHudMath.Spring(0.18, 0.55, 0);
    public static final FableHudMath.Spring BOB = new FableHudMath.Spring(0.22, 0.6, 0);
    public static final FableHudMath.Spring SQUASH = new FableHudMath.Spring(0.3, 0.55, 0);
    public static double frost, heat, wet, submerged, dark, burning, sprint, health = 1, flash, hurt;
    private static double last = -1, hurtAt = -100, lightningAt = -100;
    private static boolean wasGround = true;
    private static float lastFall;
    private static int lastHurt;
    private static Object world,body,connection;
    private static int dimension,tick=Integer.MIN_VALUE,emitted;
    private static boolean installed;
    private static double renderStamp,drawStamp=Double.NaN;
    private static final FableHudMath.TickGate CLOCK=new FableHudMath.TickGate();
    private static final FableHudMath.Budget EMIT=new FableHudMath.Budget(32);
    public static void initialize(){if(!installed){installed=true;net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new Lifecycle());}}
    public static final class Lifecycle{
        @net.minecraftforge.fml.common.eventhandler.SubscribeEvent public void tick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent e){if(e.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END)context();}
    }
    private static void context(){
        Object next=MC.getConnection();int dim=MC.player==null?0:MC.player.dimension;
        if(world!=MC.world||body!=MC.player||connection!=next||dimension!=dim){
            world=MC.world;body=MC.player;connection=next;dimension=dim;reset();HotbarFx.reset();
        }
    }
    private static void reset(){last=-1;hurtAt=lightningAt=-100;wasGround=true;lastFall=0;lastHurt=0;tick=Integer.MIN_VALUE;emitted=0;accumulator=0;
        frost=heat=wet=submerged=dark=burning=sprint=flash=hurt=0;health=1;SWAY.snap(0);BOB.snap(0);SQUASH.snap(0);PARTICLES.clear();CLOCK.reset();EMIT.reset();drawStamp=Double.NaN;
    }
    static void clearParticles(){PARTICLES.clear();accumulator=0;drawStamp=Double.NaN;}
    public static int tick(){return MC.player==null?Integer.MIN_VALUE:MC.player.ticksExisted;}


    /** Updates every frame from the real client player and world. Returns frame delta in ticks. */
    public static double update(double time) {
        initialize();context();renderStamp=time;
        EntityPlayer p = MC.player;
        if(p==null||MC.world==null||MC.getConnection()==null)return 0;
        double dt=CLOCK.step(p.ticksExisted);if(dt<=0)return 0;
        if(tick!=p.ticksExisted){tick=p.ticksExisted;emitted=0;}
        last=time;
        // Motion relative to the view: strafing sways the bar, vertical speed bobs it.
        double yaw = Math.toRadians(p.rotationYaw);
        double side = p.motionX * Math.cos(yaw) + p.motionZ * Math.sin(yaw);
        SWAY.target = FableHudMath.clamp(side * 14, -4, 4);
        BOB.target = FableHudMath.clamp(-p.motionY * 5, -3, 4);
        if (!p.onGround) lastFall = Math.max(lastFall, p.fallDistance);
        if (p.onGround && !wasGround) { SQUASH.kick(FableHudMath.clamp(0.25 + lastFall * 0.18, 0.2, 1.6)); BOB.kick(FableHudMath.clamp(lastFall * 0.5, 0.3, 3)); lastFall = 0; }
        wasGround = p.onGround;
        SWAY.step(dt); BOB.step(dt); SQUASH.step(dt);
        if (p.hurtTime > lastHurt && p.hurtTime > 0) hurtAt = time;
        lastHurt = p.hurtTime;
        hurt = FableHudMath.pulse((time - hurtAt) / 10);
        BlockPos pos = new BlockPos(p.posX, p.posY + 0.5, p.posZ);
        double temp = MC.world.getBiome(pos).getTemperature(pos);
        frost = FableHudMath.approach(frost, FableHudMath.frost(temp), dt, 30);
        heat = FableHudMath.approach(heat, Math.max(FableHudMath.heat(temp), MC.world.provider.isNether() ? 1 : 0), dt, 30);
        boolean raining = MC.world.isRainingAt(new BlockPos(p.posX, p.posY + 1.8, p.posZ));
        submerged = FableHudMath.approach(submerged, p.isInsideOfMaterial(Material.WATER) ? 1 : 0, dt, 4);
        wet = FableHudMath.approach(wet, raining || submerged > 0.5 ? 1 : 0, dt, raining ? 12 : 60);
        int light = MC.world.getLightFromNeighbors(pos);
        dark = FableHudMath.approach(dark, FableHudMath.clamp((7 - light) / 7.0, 0, 1), dt, 15);
        burning = FableHudMath.approach(burning, p.isBurning() || p.isInsideOfMaterial(Material.LAVA) ? 1 : 0, dt, 5);
        sprint = FableHudMath.approach(sprint, p.isSprinting() ? 1 : 0, dt, 4);
        health = FableHudMath.approach(health, p.getMaxHealth() <= 0 ? 1 : p.getHealth() / p.getMaxHealth(), dt, 3);
        if (MC.world.getLastLightningBolt() > 0 && time - lightningAt > 4) lightningAt = time;
        flash = FableHudMath.pulse((time - lightningAt) / 8);
        stepParticles(dt);
        return dt;
    }

    /** Low-health heartbeat strength 0..1 (only below 30% health). */
    public static double heartbeat(double time) {
        double danger = FableHudMath.clamp((0.3 - health) / 0.3, 0, 1);
        return danger * FableHudMath.heartbeat(time, 0.04 + 0.04 * danger);
    }

    // ---- 2D pixel particles ------------------------------------------------------------------
    static final class Bit { double x, y, vx, vy, gravity; int age, life, color, size; boolean stick; double stickY; }
    private static final List<Bit> PARTICLES = new ArrayList<>();
    private static double accumulator;

    public static void emit(double x, double y, double vx, double vy, double gravity, int life, int color, int size) {
        if(!HudConfig.motion||HudConfig.mode==2||MC.player==null||MC.world==null||MC.getConnection()==null||life<=0||life>300||size<1||size>4||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(vx)||!Double.isFinite(vy)||!Double.isFinite(gravity))return;
        if(EMIT.take(tick(),1)==0)return;emitted++;
        if (PARTICLES.size() >= 260) PARTICLES.remove(0);
        Bit b = new Bit(); b.x = x; b.y = y; b.vx = vx; b.vy = vy; b.gravity = gravity; b.life = life; b.color = color; b.size = size;
        PARTICLES.add(b);
    }

    /** A drop that lands on a frame edge at {@code edgeY} and then slides/drips down it. */
    public static void drop(double x, double edgeY, double startY) {
        int before=emitted;emit(x, startY, 0, 1.6, 0.08, 40, 0xCC9CC8F0, 1);
        if(before==emitted||PARTICLES.isEmpty())return;
        Bit b = PARTICLES.get(PARTICLES.size() - 1); b.stick = true; b.stickY = edgeY;
    }

    private static void stepParticles(double dt) {
        accumulator += dt;
        while (accumulator >= 1) {
            accumulator -= 1;
            for (Iterator<Bit> it = PARTICLES.iterator(); it.hasNext(); ) {
                Bit b = it.next();
                b.vy += b.gravity; b.x += b.vx; b.y += b.vy;
                if (b.stick && b.y >= b.stickY && b.vy > 0) { b.y = b.stickY; b.vy = 0.05; b.gravity = 0.0; b.vx = 0; b.stick = false; b.color = 0xDDB4DCFF; }
                if (++b.age >= b.life) it.remove();
            }
        }
    }

    public static void drawParticles() {
        if(!HudConfig.motion||HudConfig.mode==2||drawStamp==renderStamp)return;drawStamp=renderStamp;
        for (Bit b : PARTICLES) {
            double f = b.age / (double) b.life;
            int alpha = (int) (((b.color >>> 24) & 255) * (f < 0.7 ? 1 : (1 - f) / 0.3));
            int c = (alpha << 24) | (b.color & 0xFFFFFF);
            int x = FableHudMath.snap(b.x), y = FableHudMath.snap(b.y);
            Gui.drawRect(x, y, x + b.size, y + b.size, c);
        }
    }

    /** Spawns ambient weather particles around a HUD rectangle for this frame. */
    public static void weather(double x, double y, double w, double h, double time, double dt, java.util.Random r) {
        double chance = dt;
        if (wet > 0.3 && submerged < 0.5 && r.nextDouble() < 0.5 * wet * chance) drop(x + r.nextDouble() * w, y, y - 14 - r.nextDouble() * 10);
        if (submerged > 0.5 && r.nextDouble() < 0.4 * chance) emit(x + r.nextDouble() * w, y + h, (r.nextDouble() - 0.5) * 0.1, -0.35 - r.nextDouble() * 0.3, -0.004, 30, 0xB0D8F4FF, r.nextInt(3) == 0 ? 2 : 1);
        if ((heat > 0.4 || burning > 0.3) && r.nextDouble() < (0.15 * heat + 0.6 * burning) * chance)
            emit(x + r.nextDouble() * w, y + h * 0.4, (r.nextDouble() - 0.5) * 0.2, -0.25 - r.nextDouble() * 0.35, -0.005, 26, r.nextBoolean() ? 0xFFFFB347 : 0xFFFF6A1A, 1);
        if (frost > 0.5 && r.nextDouble() < 0.12 * frost * chance) emit(x + r.nextDouble() * w, y - 10, (r.nextDouble() - 0.5) * 0.25, 0.25 + r.nextDouble() * 0.15, 0, 50, 0xEEFFFFFF, 1);
    }
}
