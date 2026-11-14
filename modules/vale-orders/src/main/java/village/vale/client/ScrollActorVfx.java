package village.vale.client;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.vale.WeaponMath;

/**
 * Blocky procedural shapes for thrown scroll jutsu, replacing the shared glyph sprite for these
 * kinds. Each shape is built from face-shaded voxels (VfxDraw voxel mode) and oriented along the
 * flight path, which is sampled per entity so trails follow real curves:
 *   fireball  layered fire orb, flame tongues and embers
 *   firestream tapering fire chain along the path
 *   vacuum    spinning wind rings
 *   front     wide wind crescent
 *   cannon    water orb with orbiting droplets
 *   waterstream ribbon of water cubes with spray
 *   shark     finned water creature (body, dorsal and tail fins, eye) with a wake
 *   blade     spinning steel crescent
 *   dragon    segmented serpent following its own path
 *   thunder   jagged sky bolt down to the strike point
 * All designs are original. Other kinds keep the glyph.
 */
@SideOnly(Side.CLIENT)
public final class ScrollActorVfx {
    private ScrollActorVfx() {}
    private static final Map<Entity, ArrayDeque<double[]>> PATH = new WeakHashMap<>();

    public static boolean draws(String kind) {
        switch (kind) {
            case "fireball": case "firestream": case "vacuum": case "front": case "cannon":
            case "waterstream": case "shark": case "blade": case "dragon": case "thunder": return true;
            default: return false;
        }
    }

    private static double[] pos(Entity e, float pt) {
        return new double[] {e.lastTickPosX + (e.posX - e.lastTickPosX) * pt, e.lastTickPosY + (e.posY - e.lastTickPosY) * pt, e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt};
    }

    /** Recent positions (newest first), sampled once per tick. */
    private static ArrayDeque<double[]> path(Entity e) {
        ArrayDeque<double[]> q = PATH.computeIfAbsent(e, k -> new ArrayDeque<>());
        double[] last = q.peekFirst();
        if (last == null || (int) last[3] != e.ticksExisted) {
            q.addFirst(new double[] {e.posX, e.posY + e.height / 2, e.posZ, e.ticksExisted});
            while (q.size() > 24) q.removeLast();
        }
        return q;
    }

    /** Unit flight direction from the sampled path (falls back to motion). */
    private static double[] dir(Entity e, ArrayDeque<double[]> q) {
        double dx = e.motionX, dy = e.motionY, dz = e.motionZ;
        if (q.size() >= 2) { java.util.Iterator<double[]> it = q.iterator(); double[] a = it.next(), b = it.next(); dx = a[0] - b[0]; dy = a[1] - b[1]; dz = a[2] - b[2]; }
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return l < 1e-4 ? new double[] {0, 0, 1} : new double[] {dx / l, dy / l, dz / l};
    }

    public static void render(Entity e, String kind, double x, double y, double z, float pt) {
        if (VfxBudget.lod(e) <= 0) return;
        ArrayDeque<double[]> q = path(e);
        double[] at = pos(e, pt), d = dir(e, q);
        double t = e.ticksExisted + pt;
        float yaw = (float) Math.toDegrees(Math.atan2(-d[0], d[2])), pitch = (float) -Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, d[1]))));
        double[][] b = WeaponMath.basis(yaw, pitch);
        VfxDraw.begin(x, y + e.height / 2, z);
        VfxDraw.voxel = true;
        try {
            switch (kind) {
                case "fireball": fireball(t, b, e); break;
                case "firestream": stream(q, at, e, 0xFF8A2A, 0xFFE27A, 0.34, t); break;
                case "waterstream": stream(q, at, e, 0x2F7BFF, 0xCFEFFF, 0.3, t); break;
                case "vacuum": rings(t, b, 0xE8FFF4, 0x9FE0C8); break;
                case "front": crescent(t, b, 0xF2FFF0, 0x9FD97A, 1.4, false); break;
                case "blade": crescent(t, b, 0xF4F8FF, 0x9AA8B8, 0.7, true); break;
                case "cannon": orb(t, 0x2F7BFF, 0xCFEFFF, 0.42); break;
                case "shark": creature(t, b); wake(q, at, e, 0x9FD8FF); break;
                case "dragon": serpent(q, at, e, t); break;
                case "thunder": bolt(e, t); break;
                default: break;
            }
        } finally {
            VfxDraw.voxel = false;
            VfxDraw.end();
        }
    }

    private static double[] w(double[][] b, double x, double y, double z) { return WeaponMath.toWorld(b, x, y, z); }

    private static void fireball(double t, double[][] b, Entity e) {
        double r = 0.42 + 0.04 * Math.sin(t * 0.7);
        VfxDraw.normal();
        VfxDraw.sphere(0, 0, 0, r, 0xC8401A, 0.85f, 8);
        VfxDraw.additive();
        VfxDraw.sphere(0, 0, 0, r * 0.75, 0xFF8A2A, 0.9f, 8);
        VfxDraw.sphere(0, 0, 0, r * 0.45, 0xFFE27A, 1f, 6);
        for (int i = 0; i < 9; i++) {          // flame tongues streaming back
            double a = i * 0.698 + t * 0.4, len = 0.35 + 0.35 * WeaponMath.hash((long) (t / 2), i);
            double[] base = w(b, Math.cos(a) * r * 0.7, Math.sin(a) * r * 0.7, -r * 0.4);
            double[] tip = w(b, Math.cos(a) * r * 0.4, Math.sin(a) * r * 0.4, -r - len);
            VfxDraw.line(new double[][] {base, tip}, i % 2 == 0 ? 0xFFB03A : 0xFF6A1A, 0.95f, 0.2f, 9f);
        }
        if (e.ticksExisted % 2 == 0) {
            java.util.Random rnd = e.world.rand;
            VoxelBits.spawn(e.posX, e.posY + e.height / 2, e.posZ, (rnd.nextDouble() - 0.5) * 0.05, 0.03, (rnd.nextDouble() - 0.5) * 0.05, 1, rnd.nextBoolean() ? 0xFFB03A : 0xFF6A1A, 1f, 12, -0.002, 0.9, false);
        }
    }

    private static void orb(double t, int body, int core, double r) {
        VfxDraw.normal();
        VfxDraw.sphere(0, 0, 0, r * (1 + 0.05 * Math.sin(t * 0.9)), body, 0.8f, 8);
        VfxDraw.additive();
        VfxDraw.sphere(0, 0, 0, r * 0.5, core, 0.9f, 6);
        for (int i = 0; i < 8; i++) {
            double a = t * 0.3 + i * Math.PI / 4;
            VfxDraw.cube(Math.cos(a) * r * 1.3, Math.sin(a * 1.7) * r * 0.5, Math.sin(a) * r * 1.3, 0.08, core, 0.9f);
        }
    }

    /** A trail of cubes along the real path, shrinking toward the tail. */
    private static void stream(ArrayDeque<double[]> q, double[] at, Entity e, int body, int core, double r, double t) {
        int i = 0, n = q.size();
        for (double[] p : q) {
            double f = i / (double) Math.max(1, n - 1), s = r * (1 - f * 0.75);
            double ox = p[0] - at[0], oy = p[1] - (at[1] + e.height / 2), oz = p[2] - at[2];
            double wob = Math.sin(t * 0.6 + i) * 0.06;
            VfxDraw.normal();
            VfxDraw.cube(ox + wob, oy, oz - wob, s, body, 0.85f * (float) (1 - f * 0.6));
            VfxDraw.additive();
            VfxDraw.cube(ox, oy, oz, s * 0.55, core, 0.9f * (float) (1 - f));
            i++;
        }
    }

    private static void wake(ArrayDeque<double[]> q, double[] at, Entity e, int c) {
        int i = 0;
        for (double[] p : q) {
            if (i++ == 0 || i > 12) continue;
            double s = 0.12 * (1 - i / 13.0);
            VfxDraw.additive();
            VfxDraw.cube(p[0] - at[0], p[1] - (at[1] + e.height / 2) - 0.1, p[2] - at[2], s, c, 0.6f * (float) (1 - i / 13.0));
        }
    }

    private static void rings(double t, double[][] b, int core, int glow) {
        for (int k = 0; k < 3; k++) {
            double z = -k * 0.35, r = 0.35 + k * 0.12, spin = t * (0.5 + 0.2 * k) * (k % 2 == 0 ? 1 : -1);
            double[][] ring = new double[17][];
            for (int i = 0; i <= 16; i++) { double a = spin + i * Math.PI / 8; ring[i] = w(b, Math.cos(a) * r, Math.sin(a) * r, z); }
            VfxDraw.normal();
            VfxDraw.line(ring, glow, 0.8f, 0.8f, 10f);
            VfxDraw.additive();
            VfxDraw.line(ring, core, 0.9f, 0.9f, 4f);
        }
    }

    private static void crescent(double t, double[][] b, int core, int glow, double size, boolean spin) {
        GlStateManager.pushMatrix();
        if (spin) {
            double[] axis = b[0];
            GlStateManager.rotate((float) (t * 40 % 360), (float) axis[0], (float) axis[1], (float) axis[2]);
        }
        int n = 14;
        double[][] in = new double[n][], out = new double[n][];
        for (int i = 0; i < n; i++) {
            double f = i / (n - 1.0), a = -1.2 + 2.4 * f, taper = Math.sin(Math.PI * f);
            in[i] = w(b, Math.sin(a) * size * (1 - 0.25 * taper), 0, Math.cos(a) * size * 0.4 * (1 - 0.25 * taper) - size * 0.3);
            out[i] = w(b, Math.sin(a) * size, 0, Math.cos(a) * size * 0.4 - size * 0.3 + 0.08 * taper);
        }
        VfxDraw.normal();
        VfxDraw.ribbon(in, out, glow, 0.85f, 0.85f);
        VfxDraw.additive();
        VfxDraw.line(out, core, 0.95f, 0.95f, 6f);
        GlStateManager.popMatrix();
    }

    private static void creature(double t, double[][] b) {
        int body = 0x2F6FC8, belly = 0xCFE8FF, fin = 0x2558A8;
        double sway = Math.sin(t * 0.8) * 0.12;
        VfxDraw.normal();
        for (int i = 0; i < 7; i++) {          // tapered body from snout (+z) to tail (-z)
            double z = 0.45 - i * 0.15, r = 0.1 + 0.16 * Math.sin(Math.PI * (i + 1) / 8.0);
            double[] c = w(b, sway * (i / 6.0) * (i / 6.0), 0, z);
            VfxDraw.cube(c[0], c[1], c[2], r * 2, body, 0.95f);
            double[] bl = w(b, sway * (i / 6.0) * (i / 6.0), -r * 0.6, z);
            VfxDraw.cube(bl[0], bl[1], bl[2], r * 1.2, belly, 0.95f);
        }
        double[] d0 = w(b, 0, 0.18, 0.1), d1 = w(b, 0, 0.45, -0.05), d2 = w(b, 0, 0.18, -0.15);   // dorsal fin
        VfxDraw.ribbon(new double[][] {d0, d1}, new double[][] {d2, d2}, fin, 0.95f, 0.95f);
        double ts = sway * 2;
        double[] t0 = w(b, ts, 0, -0.55), t1 = w(b, ts + 0.05, 0.3, -0.8), t2 = w(b, ts + 0.05, -0.25, -0.75);   // tail fin
        VfxDraw.ribbon(new double[][] {t0, t1}, new double[][] {t0, t2}, fin, 0.95f, 0.95f);
        for (int side = -1; side <= 1; side += 2) {
            double[] p0 = w(b, side * 0.18, -0.05, 0.15), p1 = w(b, side * 0.42, -0.15, -0.05);   // pectoral fins
            VfxDraw.line(new double[][] {p0, p1}, fin, 0.95f, 0.95f, 10f);
            double[] eye = w(b, side * 0.1, 0.05, 0.4);
            VfxDraw.cube(eye[0], eye[1], eye[2], 0.05, 0x101418, 1f);
        }
    }

    private static void serpent(ArrayDeque<double[]> q, double[] at, Entity e, double t) {
        int i = 0, n = Math.min(q.size(), 18);
        for (double[] p : q) {
            if (i >= n) break;
            double f = i / (double) Math.max(1, n - 1), s = (i == 0 ? 0.46 : 0.34 * (1 - f * 0.6));
            double ox = p[0] - at[0], oy = p[1] - (at[1] + e.height / 2) + Math.sin(t * 0.5 - i * 0.6) * 0.1, oz = p[2] - at[2];
            VfxDraw.normal();
            VfxDraw.cube(ox, oy, oz, s, i % 2 == 0 ? 0x3A8A5A : 0x2E7048, 0.95f);
            VfxDraw.cube(ox, oy - s * 0.35, oz, s * 0.7, 0xD8C890, 0.95f);   // pale underside
            if (i == 0) {
                VfxDraw.additive();
                VfxDraw.cube(ox + 0.12, oy + 0.1, oz, 0.07, 0xFFE27A, 1f);
                VfxDraw.cube(ox - 0.12, oy + 0.1, oz, 0.07, 0xFFE27A, 1f);
                VfxDraw.normal();
                VfxDraw.cube(ox + 0.15, oy + 0.3, oz, 0.08, 0xE8E0C8, 1f);   // horn nubs
                VfxDraw.cube(ox - 0.15, oy + 0.3, oz, 0.08, 0xE8E0C8, 1f);
            } else if (i % 3 == 0) {
                VfxDraw.cube(ox, oy + s * 0.55, oz, s * 0.35, 0x8AE0A0, 0.95f);   // dorsal ridge
            }
            i++;
        }
    }

    private static void bolt(Entity e, double t) {
        long seed = e.getEntityId() * 31L + (long) (t / 2);
        double[][] pts = village.vale.JutsuMath.bolt(seed, new double[] {0, -1, 0}, 6.5, 12, 0.35);
        VfxDraw.normal();
        VfxDraw.line(pts, 0x4F8DFF, 0.8f, 0.8f, 12f);
        VfxDraw.additive();
        VfxDraw.line(pts, 0xF2F8FF, 1f, 1f, 5f);
        VfxDraw.sphere(0, -6.5, 0, 0.5 + 0.2 * Math.sin(t), 0xCFE8FF, 0.6f, 8);
    }
}
