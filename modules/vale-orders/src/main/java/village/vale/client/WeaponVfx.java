package village.vale.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.vale.JutsuMath;
import village.vale.WeaponMath;
import village.vale.arsenal.ArsenalField;
import village.vale.arsenal.ArsenalShot;
import village.vale.arsenal.ArsenalSpec;
import village.vale.arsenal.WeaponPulse;

/**
 * Procedural visuals for all arsenal weapons: technique casts (gusts, shockwaves, sound rings,
 * lashes, chain lightning, siphons, shatters, frost, cinder bursts, prism beams), melee slash
 * crescents, timed auras (guards, mends, overdrive, sweep), projectile trails and ground fields.
 * Everything is drawn relative to the pulse/shot entity so nothing lags the camera.
 */
@SideOnly(Side.CLIENT)
public final class WeaponVfx extends Render<WeaponPulse> {
    public WeaponVfx(RenderManager manager) {
        super(manager);
        shadowSize = 0;
        VoxelBits.install();
    }

    private static final java.util.Set<Integer> BURST = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Integer, Boolean>());

    /** One-time pixel debris for a pulse: ground chips, shards, embers, frost, sparks. */
    private static void debris(WeaponPulse e, ArsenalSpec spec, double[] origin, int[] pal) {
        if (!BURST.add(e.getEntityId())) return;
        java.util.Random r = VoxelBits.RNG;
        double ex = e.endX(), ey = e.endY(), ez = e.endZ();
        switch (spec.role) {
            case SLAM: {
                int g = VoxelBits.groundColor(e);
                if (g < 0) g = 0x7F7F7F;
                for (int i = 0; i < 40; i++) {
                    double a = r.nextDouble() * Math.PI * 2, sp = 0.1 + r.nextDouble() * 0.3;
                    VoxelBits.spawn(origin[0] + Math.cos(a) * 0.5, origin[1] + 0.1, origin[2] + Math.sin(a) * 0.5, Math.cos(a) * sp, 0.25 + r.nextDouble() * 0.3, Math.sin(a) * sp,
                            2 + r.nextInt(3), VoxelBits.vary(g), 1f, 24 + r.nextInt(14), 0.045, 0.97, true);
                }
                break;
            }
            case BREAK: case SHOCK: case RIME: case DRAIN: case RESTORE: case FINISH: case BURST: case CHAIN: case EXTEND: {
                int n = spec.role == ArsenalSpec.Role.BREAK ? 22 : 12;
                for (int i = 0; i < n; i++) {
                    double vx = (r.nextDouble() - 0.5) * 0.3, vy = r.nextDouble() * 0.25, vz = (r.nextDouble() - 0.5) * 0.3;
                    int c = spec.role == ArsenalSpec.Role.BREAK && i % 3 == 0 ? 0xD8D8D8 : (i % 2 == 0 ? pal[0] : pal[1]);
                    VoxelBits.spawn(ex, ey, ez, vx, vy, vz, 1 + r.nextInt(2), c, 1f, 14 + r.nextInt(10), spec.role == ArsenalSpec.Role.RIME ? 0.03 : 0.02, 0.95, true);
                }
                break;
            }
            default:
                for (int i = 0; i < 6; i++)
                    VoxelBits.spawn(origin[0], origin[1] - 0.2, origin[2], (r.nextDouble() - 0.5) * 0.12, r.nextDouble() * 0.08, (r.nextDouble() - 0.5) * 0.12, 1, pal[i % 2], 1f, 10 + r.nextInt(6), 0.01, 0.92, false);
        }
    }

    @Override protected ResourceLocation getEntityTexture(WeaponPulse entity) { return null; }

    private static int detail() { return JutsuVfx.detail; }

    private static double[] interp(Entity e, float partial) {
        return new double[] {
            e.lastTickPosX + (e.posX - e.lastTickPosX) * partial,
            e.lastTickPosY + (e.posY - e.lastTickPosY) * partial,
            e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * partial};
    }

    private static double[] sub(double[] a, double[] b) { return new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static double[] add(double[] a, double[] b) { return new double[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    private static double[] mul(double[] a, double s) { return new double[] {a[0] * s, a[1] * s, a[2] * s}; }
    private static double[] lerp(double[] a, double[] b, double f) { return new double[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f}; }
    private static double len(double[] a) { return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); }

    /** Weapon hand of a player in world space (first-person anchor for the local camera). */
    static double[] hand(EntityPlayer p, float partial) {
        Minecraft mc = Minecraft.getMinecraft();
        double[] at = interp(p, partial);
        if (p == mc.getRenderViewEntity() && mc.gameSettings.thirdPersonView == 0) {
            float yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * partial;
            float pitch = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * partial;
            return JutsuMath.firstPerson(at[0], at[1] + p.getEyeHeight(), at[2], yaw, pitch);
        }
        float body = p.prevRenderYawOffset + (p.renderYawOffset - p.prevRenderYawOffset) * partial;
        return JutsuMath.palm(at[0], at[1], at[2], body, 1.25, p.isSneaking(), true);
    }

    private static EntityPlayer player(Entity world, int id) {
        Entity e = world.world.getEntityByID(id);
        return e instanceof EntityPlayer ? (EntityPlayer) e : null;
    }

    // ---- pulses ---------------------------------------------------------------------------------
    @Override public void doRender(WeaponPulse e, double x, double y, double z, float yaw, float partial) {
        ArsenalSpec spec = e.spec();
        if (spec == null) return;
        double age = e.ticksExisted + partial;
        int life = e.life();
        if (age > life + 1) return;
        double[] origin = interp(e, partial);
        EntityPlayer owner = player(e, e.ownerId());
        double[] handRel = owner != null ? sub(hand(owner, partial), origin) : new double[3];
        double[] endRel = sub(new double[] {e.endX(), e.endY(), e.endZ()}, origin);
        int[] pal = WeaponMath.palette(spec.role.name());
        double f = WeaponMath.clamp01(age / life), fade = WeaponMath.fade(age, life);
        if (e.kind() == WeaponPulse.CAST) debris(e, spec, origin, pal);
        VfxDraw.begin(x, y, z);
        VfxDraw.voxel = true;
        try {
            switch (e.kind()) {
                case WeaponPulse.ARC: chainBolt(new double[3], endRel, e.getEntityId() * 31L + (long) (age / 2), pal, fade, 1.0); break;
                case WeaponPulse.SWING: swing(e, owner, origin, partial, f, fade, pal); break;
                case WeaponPulse.AURA: aura(e, spec, owner, origin, partial, age, life, pal); break;
                default: cast(e, spec, owner, origin, handRel, endRel, age, f, fade, pal); break;
            }
        } finally {
            VfxDraw.voxel = false;
            VfxDraw.end();
        }
    }

    private void cast(WeaponPulse e, ArsenalSpec spec, EntityPlayer owner, double[] origin, double[] hand, double[] end, double age, double f, double fade, int[] pal) {
        double[][] b = WeaponMath.basis(e.lookYaw(), e.lookPitch());
        long seed = e.getEntityId() * 7919L;
        switch (spec.role) {
            case WIND: gust(b, hand, f, fade, pal, 7.5); break;
            case SONIC: soundRings(b, hand, f, fade, pal); break;
            case SLAM: shockwave(seed, f, fade, pal); break;
            case EXTEND: lash(b, hand, end, age, f, fade, pal); break;
            case CHAIN: chainBolt(hand, end, seed + (long) (age / 2), pal, fade, 1.4); VfxDraw.additive(); VfxDraw.sphere(end[0], end[1], end[2], 0.25 * fade, pal[0], (float) (0.6 * fade), 8); break;
            case DRAIN: strike(e, b, f, fade, pal); siphon(end, hand, age, f, fade, pal); break;
            case SHOCK: strike(e, b, f, fade, pal); for (int i = 0; i < 5; i++) chainBolt(end, add(end, mul(dir(seed, i), 0.9)), seed + i * 13 + (long) (age / 2), pal, fade, 0.6); break;
            case BREAK: strike(e, b, f, fade, pal); shatter(seed, end, f, fade, pal); break;
            case RESTORE: strike(e, b, f, fade, pal); restoreSparks(seed, hand, age, fade, pal); break;
            case RIME: strike(e, b, f, fade, pal); frost(seed, end, f, fade, pal); break;
            case BURST: cinderBurst(e, b, end, age, f, pal); break;
            case FINISH: prism(hand, end, age, f, fade, pal); break;
            default: muzzle(b, hand, f, fade, pal); break;
        }
    }

    private static double[] dir(long seed, int i) {
        double u = WeaponMath.hash(seed, i * 2) * 2 - 1, a = WeaponMath.hash(seed, i * 2 + 1) * Math.PI * 2, s = Math.sqrt(1 - u * u);
        return new double[] {Math.cos(a) * s, u, Math.sin(a) * s};
    }

    /** Three expanding wind fronts with radial streaks (Ward Fan gust). */
    private void gust(double[][] b, double[] hand, double f, double fade, int[] pal, double range) {
        int fronts = 3, pts = detail() == 0 ? 10 : 18;
        for (int k = 0; k < fronts; k++) {
            double d = 0.4 + WeaponMath.wave(k, fronts, f, range);
            double spread = 0.35 + 0.25 * d / range;
            double[][] line = new double[pts][];
            for (int i = 0; i < pts; i++) {
                double a = -spread + 2 * spread * i / (pts - 1.0);
                double[] w = WeaponMath.toWorld(b, Math.sin(a) * d, Math.sin(a * 3 + k) * 0.12, Math.cos(a) * d);
                line[i] = add(hand, w);
            }
            float alpha = (float) (fade * (1 - 0.25 * k));
            VfxDraw.normal();
            VfxDraw.line(line, pal[1], alpha * 0.7f, alpha * 0.7f, 8f);
            VfxDraw.additive();
            VfxDraw.line(line, pal[0], alpha, alpha, 3f);
        }
        VfxDraw.normal();
        for (int i = 0; i < 9; i++) {
            double a = (WeaponMath.hash(i, 3) - 0.5) * 1.1, h = (WeaponMath.hash(i, 4) - 0.5) * 0.8, d0 = range * f * 0.6, d1 = d0 + 1.2;
            double[] p0 = add(hand, WeaponMath.toWorld(b, Math.sin(a) * d0, h, Math.cos(a) * d0)), p1 = add(hand, WeaponMath.toWorld(b, Math.sin(a) * d1, h, Math.cos(a) * d1));
            VfxDraw.line(new double[][] {p0, p1}, pal[0], 0f, (float) (0.8 * fade), 2f);
        }
    }

    /** Concentric pressure rings travelling forward (Echo Gauntlet). */
    private void soundRings(double[][] b, double[] hand, double f, double fade, int[] pal) {
        int rings = 4, n = 28;
        for (int k = 0; k < rings; k++) {
            double d = 0.3 + WeaponMath.wave(k, rings, f, 9), r = 0.25 + d * 0.22;
            double[][] pts = new double[n + 1][];
            for (int i = 0; i <= n; i++) { double a = i * Math.PI * 2 / n; pts[i] = add(hand, WeaponMath.toWorld(b, Math.cos(a) * r, Math.sin(a) * r, d)); }
            float alpha = (float) (fade * (1 - k * 0.18));
            VfxDraw.normal();
            VfxDraw.line(pts, pal[1], alpha * 0.75f, alpha * 0.75f, 7f);
            VfxDraw.additive();
            VfxDraw.line(pts, pal[0], alpha, alpha, 2.5f);
        }
    }

    /** Ground shock rings, rising rubble and dust (Siege Maul). Anchored at the caster's feet. */
    private void shockwave(long seed, double f, double fade, int[] pal) {
        int rings = 3;
        for (int k = 0; k < rings; k++) {
            double r = 0.3 + WeaponMath.wave(k, rings, f, 4.2);
            float alpha = (float) (fade * (1 - 0.25 * k));
            VfxDraw.normal();
            VfxDraw.annulus(0, 0.06 + k * 0.01, 0, Math.max(0, r - 0.32), r, pal[1], alpha * 0.55f, 36);
            VfxDraw.additive();
            VfxDraw.line(WeaponMath.ring(r, 36, 0.08), pal[0], alpha, alpha, 3f);
        }
        VfxDraw.normal();
        int shards = detail() == 0 ? 8 : 16;
        double r0 = 0.3 + WeaponMath.wave(0, rings, f, 4.2);
        for (int i = 0; i < shards; i++) {
            double a = i * Math.PI * 2 / shards + WeaponMath.hash(seed, i) * 0.3, h = (0.35 + 0.5 * WeaponMath.hash(seed, i + 50)) * (1 - f) * 1.6;
            double rr = r0 * (0.85 + 0.15 * WeaponMath.hash(seed, i + 9));
            double[] base = {Math.cos(a) * rr, 0.05, Math.sin(a) * rr};
            double[] tip = {Math.cos(a) * (rr + 0.1), 0.05 + h, Math.sin(a) * (rr + 0.1)};
            VfxDraw.segment(base, tip, 0x5A3E22, (float) fade, 6f);
            VfxDraw.segment(base, tip, pal[1], (float) (0.8 * fade), 3f);
        }
        VfxDraw.sphere(0, 0.15, 0, 0.6 + 1.6 * f, 0xB9A38A, (float) (0.18 * fade), 10);
    }

    /** Growing, whipping vine from the staff to the target, with thorns (Thorn Staff). */
    private void lash(double[][] b, double[] hand, double[] end, double age, double f, double fade, int[] pal) {
        int n = 26;
        double grow = JutsuMath.easeOut(Math.min(1, f * 2.2));
        double[][] pts = new double[n][];
        for (int i = 0; i < n; i++) {
            double s = grow * i / (n - 1.0), w = Math.sin(s * Math.PI) * 0.35 * (1 - f * 0.6);
            double[] side = WeaponMath.toWorld(b, Math.sin(s * 9 - age * 0.8) * w, Math.cos(s * 7 - age * 0.6) * w * 0.6, 0);
            pts[i] = add(lerp(hand, end, s), side);
        }
        VfxDraw.normal();
        VfxDraw.line(pts, 0x2D5A1E, (float) fade, (float) fade, 9f);
        VfxDraw.line(pts, pal[1], (float) fade, (float) fade, 5f);
        VfxDraw.additive();
        VfxDraw.line(pts, pal[0], (float) (0.5 * fade), (float) (0.8 * fade), 1.5f);
        VfxDraw.normal();
        for (int i = 2; i < n - 1; i += 3) {
            double[] d = sub(pts[i + 1], pts[i - 1]);
            double[] thorn = WeaponMath.toWorld(b, (i % 2 == 0 ? 1 : -1) * 0.14, 0.1, 0);
            VfxDraw.segment(pts[i], add(pts[i], add(thorn, mul(d, 0.3))), 0xD8E8A0, (float) fade, 2.5f);
        }
        if (grow > 0.98) { VfxDraw.additive(); VfxDraw.sphere(end[0], end[1], end[2], 0.3 * fade, pal[0], (float) (0.5 * fade), 8); }
    }

    /** Jagged lightning between two points (Storm Pair chain, Thunder Edge sparks). */
    private void chainBolt(double[] a, double[] b, long seed, int[] pal, double fade, double width) {
        double[] d = sub(b, a);
        double length = len(d);
        if (length < 0.05) return;
        int segs = Math.max(4, Math.min(14, (int) (length * 2.5)));
        double[][] pts = JutsuMath.bolt(seed, d, length, segs, Math.min(0.35, 0.06 + length * 0.04));
        pts = VfxDraw.offset(pts, a[0], a[1], a[2]);
        VfxDraw.normal();
        VfxDraw.line(pts, pal[1], (float) (0.75 * fade), (float) (0.75 * fade), (float) (7 * width));
        VfxDraw.additive();
        VfxDraw.line(pts, pal[0], (float) fade, (float) fade, (float) (2.5 * width));
        if (detail() > 0 && length > 1.5) {
            int fork = segs / 2;
            double[][] branch = JutsuMath.bolt(seed + 5, add(mul(JutsuMath.normalize(d), 0.6), dir(seed, 1)), length * 0.3, 4, 0.08);
            VfxDraw.line(VfxDraw.offset(branch, pts[fork][0], pts[fork][1], pts[fork][2]), pal[0], (float) fade, 0f, (float) (1.8 * width));
        }
    }

    /** Weapon slash crescent sweeping in front of the caster (strike roles). */
    private void strike(WeaponPulse e, double[][] look, double f, double fade, int[] pal) {
        EntityPlayer owner = player(e, e.ownerId());
        double[] chest = owner == null ? new double[] {0, -0.3, 0} : sub(add(interp(owner, Minecraft.getMinecraft().getRenderPartialTicks()), new double[] {0, 1.15, 0}), interp(e, Minecraft.getMinecraft().getRenderPartialTicks()));
        double[][] b = WeaponMath.basis(e.lookYaw(), e.lookPitch() * 0.5f);
        crescent(b, chest, 1.7, 2.6, 0.42, Math.min(1, f * 2.2), (e.getEntityId() % 2 == 0 ? 0.95 : Math.PI - 0.95), pal, Math.min(1, fade * 1.2));
    }

    private void crescent(double[][] b, double[] at, double radius, double arc, double width, double progress, double tilt, int[] pal, double alpha) {
        int n = detail() == 0 ? 12 : 22;
        double[][][] c = WeaponMath.crescent(n, radius, arc, width, progress, tilt);
        double[][] in = new double[n][], out = new double[n][], mid = new double[n][];
        for (int i = 0; i < n; i++) {
            in[i] = add(at, WeaponMath.toWorld(b, c[0][i][0], c[0][i][1], c[0][i][2]));
            out[i] = add(at, WeaponMath.toWorld(b, c[1][i][0], c[1][i][1], c[1][i][2]));
            mid[i] = lerp(in[i], out[i], 0.75);
        }
        VfxDraw.normal();
        VfxDraw.ribbon(in, out, pal[1], (float) (0.1 * alpha), (float) (0.75 * alpha));
        VfxDraw.additive();
        VfxDraw.ribbon(mid, out, pal[0], (float) (0.05 * alpha), (float) (0.9 * alpha));
        VfxDraw.line(out, 0xFFFFFF, 0f, (float) alpha, 2f);
    }

    /** Crimson tendrils pulled from the struck body back into the blade (Siphon Hook). */
    private void siphon(double[] from, double[] to, double age, double f, double fade, int[] pal) {
        int tendrils = detail() == 0 ? 3 : 5;
        for (int k = 0; k < tendrils; k++) {
            double[][] pts = new double[14][];
            for (int i = 0; i < pts.length; i++) {
                double s = i / 13.0, w = Math.sin(s * Math.PI) * 0.3;
                double a = k * 2.1 + age * 0.4 + s * 5;
                pts[i] = add(lerp(from, to, s), new double[] {Math.cos(a) * w, Math.sin(a * 1.3) * w, Math.sin(a) * w});
            }
            VfxDraw.normal();
            VfxDraw.line(pts, pal[1], (float) (0.8 * fade), (float) (0.3 * fade), 5f);
            VfxDraw.additive();
            VfxDraw.line(pts, pal[0], (float) (0.6 * fade), (float) (0.2 * fade), 1.5f);
            double s = (f * 2.5 + k / (double) tendrils) % 1.0;
            double[] mote = pts[(int) (s * 13)];
            VfxDraw.billboard(Minecraft.getMinecraft().getRenderManager(), mote[0], mote[1], mote[2], 0.07, pal[0], (float) fade);
        }
    }

    /** Guard-breaking shards flying out of the impact (Breakaxe). */
    private void shatter(long seed, double[] at, double f, double fade, int[] pal) {
        VfxDraw.additive();
        VfxDraw.sphere(at[0], at[1], at[2], 0.2 + 0.4 * f, pal[0], (float) (0.5 * (1 - f)), 8);
        VfxDraw.normal();
        for (int i = 0; i < 12; i++) {
            double[] d = dir(seed, i);
            double[] p = add(at, mul(d, 0.2 + 1.3 * JutsuMath.easeOut(f)));
            double[] q = add(p, mul(dir(seed, i + 40), 0.16));
            double[] r = add(p, mul(dir(seed, i + 80), 0.12));
            VfxDraw.ribbon(new double[][] {p, q}, new double[][] {r, r}, i % 3 == 0 ? 0xF0F0F0 : pal[1], (float) fade, (float) fade);
        }
    }

    /** Gold sparks spiralling up the blade as it re-tempers (Tempered Cleaver). */
    private void restoreSparks(long seed, double[] hand, double age, double fade, int[] pal) {
        VfxDraw.additive();
        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        for (int i = 0; i < 12; i++) {
            double a = i * 0.52 + age * 0.45, h = ((age * 0.05 + i / 12.0) % 1.0) * 0.9, r = 0.22 * (1 - h * 0.5);
            VfxDraw.billboard(rm, hand[0] + Math.cos(a) * r, hand[1] + h - 0.2, hand[2] + Math.sin(a) * r, 0.035, i % 2 == 0 ? pal[0] : pal[1], (float) fade);
        }
    }

    /** Ice crystals bursting out of the pinned target (Rime Pin). */
    private void frost(long seed, double[] at, double f, double fade, int[] pal) {
        VfxDraw.normal();
        VfxDraw.sphere(at[0], at[1], at[2], 0.45, pal[1], (float) (0.22 * fade), 10);
        double grow = JutsuMath.easeOut(Math.min(1, f * 2.5));
        for (int i = 0; i < 9; i++) {
            double[] d = dir(seed, i);
            double[] tip = add(at, mul(d, (0.4 + 0.5 * WeaponMath.hash(seed, i + 30)) * grow));
            VfxDraw.segment(at, tip, pal[1], (float) fade, 6f);
            VfxDraw.additive();
            VfxDraw.segment(at, tip, pal[0], (float) fade, 2f);
            VfxDraw.normal();
        }
    }

    /** Strike, then a delayed cinder detonation on the target (Cinder Burst). */
    private void cinderBurst(WeaponPulse e, double[][] b, double[] at, double age, double f, int[] pal) {
        if (f < 0.4) strike(e, b, f / 0.4, 1, pal);
        double fuse = WeaponMath.clamp01(f / 0.55);
        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        if (f < 0.55) {
            VfxDraw.additive();
            for (int i = 0; i < 6; i++) {
                double a = i * 1.047 + age * 0.6;
                VfxDraw.billboard(rm, at[0] + Math.cos(a) * 0.35, at[1] + Math.sin(age * 0.7 + i) * 0.15, at[2] + Math.sin(a) * 0.35, 0.05 + 0.04 * fuse, pal[0], 0.9f);
            }
            return;
        }
        double g = (f - 0.55) / 0.45, fade = 1 - g;
        VfxDraw.normal();
        VfxDraw.sphere(at[0], at[1], at[2], 0.4 + 1.3 * JutsuMath.easeOut(g), pal[1], (float) (0.5 * fade), 12);
        VfxDraw.additive();
        VfxDraw.sphere(at[0], at[1], at[2], 0.25 + 0.6 * JutsuMath.easeOut(g), pal[0], (float) (0.8 * fade), 10);
        VfxDraw.line(VfxDraw.offset(WeaponMath.ring(0.5 + 2.2 * JutsuMath.easeOut(g), 32, 0), at[0], at[1] - 0.5, at[2]), pal[0], (float) fade, (float) fade, 3f);
    }

    /** A prism beam with a rotating spectral fringe (Prism Lance finisher). */
    private void prism(double[] hand, double[] end, double age, double f, double fade, int[] pal) {
        double[] d = sub(end, hand);
        double length = len(d);
        if (length < 0.1) return;
        double reach = JutsuMath.easeOut(Math.min(1, f * 3));
        double[] tip = add(hand, mul(d, reach));
        double[] n = JutsuMath.normalize(d);
        double[] side = JutsuMath.normalize(Math.abs(n[1]) > 0.9 ? new double[] {1, 0, 0} : new double[] {-n[2], 0, n[0]});
        double[] up = {n[1] * side[2] - n[2] * side[1], n[2] * side[0] - n[0] * side[2], n[0] * side[1] - n[1] * side[0]};
        int[] spectrum = {0xFF4A4A, 0xFFA23A, 0xFFE84A, 0x5AE07A, 0x4AA8FF, 0xA36BFF};
        VfxDraw.normal();
        for (int i = 0; i < spectrum.length; i++) {
            double a = age * 0.5 + i * Math.PI / 3, r = 0.12 * (1 - f * 0.5);
            double[] off = add(mul(side, Math.cos(a) * r), mul(up, Math.sin(a) * r));
            VfxDraw.segment(add(hand, mul(off, 1.6)), add(tip, mul(off, 1.6)), spectrum[i], (float) (0.9 * fade), 6f);
        }
        VfxDraw.additive();
        VfxDraw.segment(hand, tip, pal[1], (float) (0.6 * fade), 12f);
        VfxDraw.segment(hand, tip, 0xFFFFFF, (float) fade, 4f);
        VfxDraw.sphere(tip[0], tip[1], tip[2], 0.18 + 0.35 * f, 0xFFFFFF, (float) (0.8 * fade), 8);
        VfxDraw.sphere(hand[0], hand[1], hand[2], 0.12, pal[0], (float) fade, 6);
    }

    /** Short release flash and ring at the weapon for throws, shots and field placements. */
    private void muzzle(double[][] b, double[] hand, double f, double fade, int[] pal) {
        double r = 0.15 + 0.45 * JutsuMath.easeOut(f);
        double[][] ring = new double[25][];
        for (int i = 0; i <= 24; i++) { double a = i * Math.PI / 12; ring[i] = add(hand, WeaponMath.toWorld(b, Math.cos(a) * r, Math.sin(a) * r, 0.3 + f * 0.4)); }
        VfxDraw.normal();
        VfxDraw.line(ring, pal[1], (float) (0.8 * (1 - f)), (float) (0.8 * (1 - f)), 6f);
        VfxDraw.additive();
        VfxDraw.line(ring, pal[0], (float) (1 - f), (float) (1 - f), 2f);
        VfxDraw.sphere(hand[0], hand[1], hand[2], 0.16 * (1 - f), pal[0], (float) (0.8 * (1 - f)), 6);
    }

    // ---- melee slashes --------------------------------------------------------------------------
    private void swing(WeaponPulse e, EntityPlayer owner, double[] origin, float partial, double f, double fade, int[] pal) {
        double[] chest = owner == null ? new double[] {0, -0.4, 0} : sub(add(interp(owner, partial), new double[] {0, 1.1, 0}), origin);
        double[][] b = WeaponMath.basis(e.lookYaw(), e.lookPitch() * 0.4f);
        boolean wide = e.alt();
        // Alternating diagonal cuts (never flat, so a level camera always sees the blade face).
        double[] tilts = {0.95, Math.PI - 0.8, 0.6, Math.PI - 1.1};
        double tilt = wide ? 0.5 : tilts[Math.floorMod(e.getEntityId(), tilts.length)];
        ArsenalSpec spec = e.spec();
        if (spec != null && spec.role == ArsenalSpec.Role.TALONS) {
            // Three parallel claw rakes.
            for (int k = -1; k <= 1; k++)
                crescent(b, add(chest, WeaponMath.toWorld(b, 0, k * 0.16, 0)), 1.3 + k * 0.04, 1.9, 0.12, Math.min(1, f * 1.15 + k * 0.05), tilt, pal, fade);
            return;
        }
        crescent(b, chest, wide ? 2.3 : 1.45, wide ? 3.4 : 2.3, wide ? 0.55 : 0.34, f, tilt, pal, fade);
    }

    // ---- auras -----------------------------------------------------------------------------------
    private void aura(WeaponPulse e, ArsenalSpec spec, EntityPlayer owner, double[] origin, float partial, double age, int life, int[] pal) {
        double[] feet = owner == null ? new double[] {0, -1.6, 0} : sub(interp(owner, partial), origin);
        double in = WeaponMath.clamp01(age / 4), out = WeaponMath.clamp01((life - age) / 8), a = Math.min(in, out);
        if (a <= 0) return;
        float bodyYaw = owner == null ? e.lookYaw() : owner.prevRenderYawOffset + (owner.renderYawOffset - owner.prevRenderYawOffset) * partial;
        RenderManager rm = Minecraft.getMinecraft().getRenderManager();
        switch (spec.role) {
            case GUARD: shield(WeaponMath.basis(bodyYaw, 0), add(feet, new double[] {0, 1.05, 0}), age, a, pal); break;
            case MEND: {
                VfxDraw.normal();
                VfxDraw.sphere(feet[0], feet[1] + 0.95, feet[2], 1.05, pal[1], (float) (0.14 * a), 12);
                VfxDraw.annulus(feet[0], feet[1] + 0.05, feet[2], 0.85, 1.05, pal[1], (float) (0.5 * a), 32);
                VfxDraw.additive();
                for (int i = 0; i < 14; i++) {
                    double ang = i * 0.449 + age * 0.08, h = ((age * 0.03 + WeaponMath.hash(i, 7)) % 1.0) * 2.0;
                    VfxDraw.billboard(rm, feet[0] + Math.cos(ang) * 0.75, feet[1] + h, feet[2] + Math.sin(ang) * 0.75, 0.05, pal[0], (float) (a * (1 - h / 2.2)));
                }
                shield(WeaponMath.basis(bodyYaw, 0), add(feet, new double[] {0, 1.05, 0}), age, a * 0.6, pal);
                break;
            }
            case WIND: {
                for (int k = 0; k < 4; k++) {
                    double[][] ring = new double[33][];
                    double tilt = 0.5 + k * 0.4, spin = age * (0.35 + 0.08 * k) * (k % 2 == 0 ? 1 : -1);
                    for (int i = 0; i <= 32; i++) {
                        double ang = i * Math.PI / 16 + spin, r = 0.85 + 0.1 * k;
                        double yy = Math.sin(ang) * r * Math.sin(tilt);
                        ring[i] = new double[] {feet[0] + Math.cos(ang) * r, feet[1] + 1.0 + yy * 0.4 + k * 0.12, feet[2] + Math.sin(ang) * r * Math.cos(tilt)};
                    }
                    VfxDraw.normal();
                    VfxDraw.line(ring, pal[1], (float) (0.0), (float) (0.7 * a), 6f);
                    VfxDraw.additive();
                    VfxDraw.line(ring, pal[0], 0f, (float) a, 2f);
                }
                break;
            }
            case OVERDRIVE: {
                long flicker = (long) (age / 2);
                VfxDraw.normal();
                VfxDraw.annulus(feet[0], feet[1] + 0.05, feet[2], 0.55, 0.85, pal[1], (float) (0.45 * a), 28);
                int tongues = detail() == 0 ? 8 : 16;
                for (int i = 0; i < tongues; i++) {
                    double ang = i * Math.PI * 2 / tongues + WeaponMath.hash(flicker, i) * 0.4, r = 0.42 + 0.08 * WeaponMath.hash(flicker, i + 20);
                    double h = 0.7 + 1.2 * WeaponMath.hash(flicker, i + 40);
                    double[] base = {feet[0] + Math.cos(ang) * r, feet[1] + 0.1, feet[2] + Math.sin(ang) * r};
                    double[] mid = {feet[0] + Math.cos(ang) * r * 0.9, feet[1] + h * 0.55, feet[2] + Math.sin(ang) * r * 0.9};
                    double[] tip = {feet[0] + Math.cos(ang + 0.2) * r * 0.6, feet[1] + h, feet[2] + Math.sin(ang + 0.2) * r * 0.6};
                    VfxDraw.normal();
                    VfxDraw.line(new double[][] {base, mid, tip}, pal[1], (float) (0.8 * a), 0f, 7f);
                    VfxDraw.additive();
                    VfxDraw.line(new double[][] {base, mid, tip}, pal[0], (float) a, 0f, 2.5f);
                }
                break;
            }
            case SWEEP: {
                double spin = age * 0.3;
                VfxDraw.normal();
                double[][] ring = WeaponMath.ring(1.15, 40, 0);
                VfxDraw.line(VfxDraw.offset(ring, feet[0], feet[1] + 0.95, feet[2]), pal[1], (float) (0.6 * a), (float) (0.6 * a), 5f);
                VfxDraw.additive();
                VfxDraw.line(VfxDraw.offset(ring, feet[0], feet[1] + 0.95, feet[2]), pal[0], (float) (0.8 * a), (float) (0.8 * a), 1.5f);
                for (int i = 0; i < 6; i++) {
                    double ang = spin + i * Math.PI / 3;
                    double[] c = {feet[0] + Math.cos(ang) * 1.15, feet[1] + 0.95 + Math.sin(age * 0.2 + i) * 0.08, feet[2] + Math.sin(ang) * 1.15};
                    double[] t0 = {c[0] - Math.sin(ang) * 0.22, c[1], c[2] + Math.cos(ang) * 0.22};
                    double[] t1 = {c[0] + Math.sin(ang) * 0.12, c[1] + 0.12, c[2] - Math.cos(ang) * 0.12};
                    VfxDraw.normal();
                    VfxDraw.segment(t0, t1, pal[1], (float) a, 6f);
                    VfxDraw.additive();
                    VfxDraw.segment(t0, t1, pal[0], (float) a, 2f);
                }
                break;
            }
            default: break;
        }
    }

    /** Curved translucent crescent shield in front of the body with travelling ripples. */
    private void shield(double[][] b, double[] center, double age, double a, int[] pal) {
        int cols = 14, rows = 7;
        double arc = 2.0, r = 0.85;
        for (int j = 0; j < rows; j++) {
            double y0 = -0.95 + 1.9 * j / rows, y1 = -0.95 + 1.9 * (j + 1) / rows;
            double[][] lo = new double[cols + 1][], hi = new double[cols + 1][];
            for (int i = 0; i <= cols; i++) {
                double ang = -arc / 2 + arc * i / cols;
                double bulge0 = Math.cos(y0 * 1.4) * 0.2, bulge1 = Math.cos(y1 * 1.4) * 0.2;
                lo[i] = add(center, WeaponMath.toWorld(b, Math.sin(ang) * (r + bulge0), y0, Math.cos(ang) * (r + bulge0)));
                hi[i] = add(center, WeaponMath.toWorld(b, Math.sin(ang) * (r + bulge1), y1, Math.cos(ang) * (r + bulge1)));
            }
            double ripple = 0.5 + 0.5 * Math.sin(age * 0.5 - j * 0.9);
            VfxDraw.normal();
            VfxDraw.ribbon(lo, hi, pal[1], (float) (a * (0.16 + 0.12 * ripple)), (float) (a * (0.16 + 0.12 * ripple)));
        }
        double[][] top = new double[cols + 1][], bottom = new double[cols + 1][];
        for (int i = 0; i <= cols; i++) {
            double ang = -arc / 2 + arc * i / cols;
            top[i] = add(center, WeaponMath.toWorld(b, Math.sin(ang) * r, 0.95, Math.cos(ang) * r));
            bottom[i] = add(center, WeaponMath.toWorld(b, Math.sin(ang) * r, -0.95, Math.cos(ang) * r));
        }
        VfxDraw.additive();
        VfxDraw.line(top, pal[0], (float) a, (float) a, 2.5f);
        VfxDraw.line(bottom, pal[0], (float) a, (float) a, 2.5f);
    }

    // ---- projectiles and fields (called from ArsenalClient after the weapon model) ---------------
    public static void shot(ArsenalShot s, double x, double y, double z, float partial, RenderManager rm) {
        ArsenalSpec spec = s.spec();
        if (spec == null) return;
        int[] pal = WeaponMath.palette(spec.role.name());
        double t = s.ticksExisted + partial;
        double[] v = {s.motionX, s.motionY, s.motionZ};
        boolean still = len(v) < 1e-3;
        if (still) v = new double[] {0, 0, 1};
        VfxDraw.begin(x, y + 0.12, z);
        VfxDraw.voxel = true;
        try {
            int mode = s.mode();
            EntityPlayer owner = player(s, s.ownerEntityId());
            double[] origin = interp(s, partial);
            switch (mode) {
                case ArsenalShot.BIND: case ArsenalShot.GRAPPLE:
                    if (owner != null) {
                        double[] h = sub(hand(owner, partial), add(origin, new double[] {0, 0.12, 0}));
                        double[][] c = WeaponMath.chain(h, new double[3], 18, mode == ArsenalShot.BIND ? 0.35 : 0.15);
                        VfxDraw.normal();
                        VfxDraw.line(c, mode == ArsenalShot.BIND ? 0x2E2E34 : 0x8A7050, 1f, 1f, 8f);
                        VfxDraw.line(c, pal[1], 0.95f, 0.95f, 3.5f);
                        if (mode == ArsenalShot.BIND) for (int i = 1; i < c.length - 1; i++) {
                            VfxDraw.normal();
                            VfxDraw.sphere(c[i][0], c[i][1], c[i][2], 0.045, 0x9A9AA6, 1f, 4);
                        }
                    }
                    trail(v, t, pal, 5, 0.18, 0.02, 4f, 0.6f);
                    break;
                case ArsenalShot.RETURN:
                    spinDisc(rm, t, pal);
                    trail(v, t, pal, 8, 0.3, 0.18, 5f, 0.6f);
                    break;
                case ArsenalShot.MARK:
                    trail(v, t, pal, 9, 0.25, 0.05, 4f, 0.8f);
                    VfxDraw.additive();
                    VfxDraw.billboard(rm, 0, 0, 0, 0.12 + 0.03 * Math.sin(t), pal[0], 0.6f);
                    break;
                case ArsenalShot.DRAIN:
                    orb(rm, t, pal, 0.13);
                    trail(v, t, pal, 12, 0.22, 0.08, 6f, 0.9f);
                    break;
                case ArsenalShot.GUIDED:
                    orb(rm, t, pal, 0.2);
                    trail(v, t, pal, 16, 0.25, 0.15, 7f, 0.9f);
                    trail(v, t + 3.1, pal, 12, 0.25, 0.25, 2f, 0.7f);
                    break;
                case ArsenalShot.VENOM: {
                    trail(v, t, pal, 10, 0.2, 0.04, 6f, 1f);
                    VfxDraw.normal();
                    for (int i = 0; i < 6; i++) {
                        double[] p = WeaponMath.trail(v, i * 2 + 1, 0.2, 0.05, t);
                        VfxDraw.sphere(p[0], p[1] - ((t * 0.04 + i * 0.13) % 0.5), p[2], 0.05, pal[1], 0.9f, 5);
                    }
                    VfxDraw.sphere(0, 0, 0, 0.07, 0x3A6A14, 0.9f, 5);
                    break;
                }
                case ArsenalShot.MIST:
                    VfxDraw.normal();
                    for (int i = 0; i < 7; i++) {
                        double[] p = WeaponMath.trail(v, i, 0.3, 0.3, t * 0.5);
                        VfxDraw.billboard(rm, p[0], p[1], p[2], 0.12 + i * 0.04, pal[1], 0.35f * (1 - i / 8f));
                    }
                    break;
                case ArsenalShot.FIRE:
                    if (s.ticksExisted % 2 == 0) { double[] o = interp(s, partial); VoxelBits.spawn(o[0], o[1] + 0.12, o[2], (VoxelBits.RNG.nextDouble() - 0.5) * 0.03, 0.03, (VoxelBits.RNG.nextDouble() - 0.5) * 0.03, 1, VoxelBits.RNG.nextBoolean() ? 0xFFB03A : 0xFF6A1A, 1f, 10, -0.002, 0.9, false); }
                    VfxDraw.normal();
                    for (int i = 0; i < 9; i++) {
                        double[] p = WeaponMath.trail(v, i, 0.16, 0.06, t * 2);
                        VfxDraw.sphere(p[0], p[1], p[2], 0.17 * (1 - i / 10.0), i < 3 ? 0xFFB03A : pal[1], 0.75f * (1 - i / 10f), 6);
                    }
                    VfxDraw.additive();
                    VfxDraw.sphere(0, 0, 0, 0.11, pal[0], 0.95f, 6);
                    break;
                case ArsenalShot.NEEDLE:
                    VfxDraw.normal();
                    VfxDraw.segment(new double[3], WeaponMath.trail(v, 1, 1.1, 0, t), pal[1], 0.85f, 7f);
                    VfxDraw.additive();
                    VfxDraw.segment(new double[3], WeaponMath.trail(v, 1, 1.3, 0, t), 0xF4FCFF, 1f, 2.5f);
                    break;
                default: break;
            }
        } finally {
            VfxDraw.voxel = false;
            VfxDraw.end();
        }
    }

    private static void trail(double[] v, double t, int[] pal, int n, double spacing, double wobble, float width, float alpha) {
        double[][] pts = new double[n][];
        for (int i = 0; i < n; i++) pts[i] = WeaponMath.trail(v, i, spacing, wobble, t);
        VfxDraw.normal();
        VfxDraw.line(pts, pal[1], alpha * 0.8f, 0f, width * 1.8f);
        VfxDraw.additive();
        VfxDraw.line(pts, pal[0], alpha, 0f, width * 0.6f);
    }

    private static void orb(RenderManager rm, double t, int[] pal, double r) {
        VfxDraw.normal();
        VfxDraw.sphere(0, 0, 0, r * (1 + 0.08 * Math.sin(t * 1.3)), pal[1], 0.55f, 8);
        VfxDraw.additive();
        VfxDraw.sphere(0, 0, 0, r * 0.55, pal[0], 0.9f, 6);
        VfxDraw.billboard(rm, 0, 0, 0, r * 2.2, pal[1], 0.18f);
    }

    /** Motion-blur disc for a spinning thrown blade. */
    private static void spinDisc(RenderManager rm, double t, int[] pal) {
        double[][] f = VfxDraw.facing(0, 0, 0);
        int n = 24;
        for (int k = 0; k < 3; k++) {
            double start = t * 0.9 + k * 2.1;
            double[][] in = new double[n][], out = new double[n][];
            for (int i = 0; i < n; i++) {
                double a = start + i * 0.09;
                in[i] = disc(f, a, 0.22);
                out[i] = disc(f, a, 0.4);
            }
            VfxDraw.normal();
            VfxDraw.ribbon(in, out, pal[1], 0f, 0.5f);
            VfxDraw.additive();
            VfxDraw.line(out, pal[0], 0f, 0.9f, 2f);
        }
    }

    private static double[] disc(double[][] f, double a, double r) {
        double c = Math.cos(a) * r, s = Math.sin(a) * r;
        return new double[] {f[0][0] * c + f[1][0] * s, f[0][1] * c + f[1][1] * s, f[0][2] * c + f[1][2] * s};
    }

    public static void field(ArsenalField f, double x, double y, double z, float partial, RenderManager rm) {
        ArsenalSpec spec = f.spec();
        if (spec == null) return;
        int[] pal = WeaponMath.palette(spec.role.name());
        double t = f.ticksExisted + partial;
        VfxDraw.begin(x, y, z);
        VfxDraw.voxel = true;
        try {
            switch (f.mode()) {
                case ArsenalField.EMITTER: {
                    double spin = t * 0.25;
                    for (int i = 0; i < 10; i++) {
                        double a = spin + i * Math.PI / 5, r = 0.55;
                        double[] p = {Math.cos(a) * r, Math.sin(t * 0.3 + i) * 0.06, Math.sin(a) * r};
                        double[] q = {Math.cos(a) * (r + 0.32), p[1], Math.sin(a) * (r + 0.32)};
                        VfxDraw.normal();
                        VfxDraw.segment(p, q, pal[1], 0.95f, 8f);
                        VfxDraw.additive();
                        VfxDraw.segment(p, q, pal[0], 1f, 3f);
                    }
                    VfxDraw.normal();
                    VfxDraw.line(WeaponMath.ring(0.5, 30, 0), pal[1], 0.8f, 0.8f, 6f);
                    VfxDraw.sphere(0, 0, 0, 0.18, pal[1], 0.35f, 8);
                    break;
                }
                case ArsenalField.TRAP: {
                    boolean armed = f.ticksExisted >= 10;
                    float pulse = armed ? (float) (0.65 + 0.35 * Math.sin(t * 0.5)) : 0.45f;
                    VfxDraw.normal();
                    VfxDraw.annulus(0, 0.02, 0, 0.0, 1.0, 0xF2E6C8, 0.18f * pulse, 32);
                    VfxDraw.line(WeaponMath.ring(1.0, 40, 0.03), pal[1], pulse, pulse, 4f);
                    VfxDraw.line(WeaponMath.ring(0.78, 40, 0.03), pal[1], pulse, pulse, 2.5f);
                    VfxDraw.line(VfxDraw.offset(WeaponMath.sigil(7, 3, 0.76, t * 0.02), 0, 0.03, 0), pal[1], pulse, pulse, 3f);
                    VfxDraw.additive();
                    VfxDraw.line(VfxDraw.offset(WeaponMath.sigil(7, 3, 0.76, t * 0.02), 0, 0.035, 0), pal[0], pulse * 0.7f, pulse * 0.7f, 1.2f);
                    if (armed) VfxDraw.billboard(rm, 0, 0.2, 0, 0.09, pal[0], pulse);
                    break;
                }
                case ArsenalField.SNARE: {
                    double grow = JutsuMath.easeOut(Math.min(1, t / 12.0));
                    VfxDraw.normal();
                    VfxDraw.annulus(0, 0.03, 0, 2.3, 2.6, pal[1], 0.4f, 40);
                    int spikes = JutsuVfx.detail == 0 ? 12 : 22;
                    for (int i = 0; i < spikes; i++) {
                        double a = i * Math.PI * 2 / spikes + WeaponMath.hash(f.getEntityId(), i) * 0.2;
                        double h = (0.7 + 0.6 * WeaponMath.hash(f.getEntityId(), i + 50)) * grow, sway = Math.sin(t * 0.15 + i) * 0.08;
                        double[] base = {Math.cos(a) * 2.45, 0.03, Math.sin(a) * 2.45};
                        double[] mid = {Math.cos(a) * 2.35 + sway, h * 0.55, Math.sin(a) * 2.35};
                        double[] tip = {Math.cos(a) * 2.15, h, Math.sin(a) * 2.15 + sway};
                        VfxDraw.line(new double[][] {base, mid, tip}, 0x2D5A1E, 1f, 1f, 6f);
                        VfxDraw.line(new double[][] {base, mid, tip}, pal[1], 1f, 0.6f, 3f);
                        VfxDraw.segment(mid, new double[] {mid[0] * 0.93, mid[1] + 0.1, mid[2] * 0.93}, 0xD8E8A0, 1f, 2f);
                    }
                    break;
                }
                case ArsenalField.MIST: {
                    double life = WeaponMath.clamp01(Math.min(t / 8.0, (70 - t) / 12.0));
                    VfxDraw.normal();
                    for (int i = 0; i < 7; i++) {
                        double a = i * 0.9 + t * 0.03, r = 0.6 + 1.2 * WeaponMath.hash(f.getEntityId(), i);
                        VfxDraw.sphere(Math.cos(a) * r, 0.5 + 0.4 * WeaponMath.hash(f.getEntityId(), i + 9), Math.sin(a) * r, 1.0 + 0.5 * WeaponMath.hash(f.getEntityId(), i + 3), pal[1], (float) (0.13 * life), 8);
                    }
                    VfxDraw.sphere(0, 0.6, 0, 2.4, 0x4A4458, (float) (0.10 * life), 12);
                    break;
                }
                default: break;
            }
        } finally {
            VfxDraw.voxel = false;
            VfxDraw.end();
        }
    }
}
