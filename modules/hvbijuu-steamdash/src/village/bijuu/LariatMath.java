package village.bijuu;

/**
 * Pure timing and geometry for the Gyuki Lariat (no Minecraft types; checked headlessly).
 *
 * Four beats, from the key press:
 *   1 anticipation  ticks 1-6    crouch, crimson flare, ribbons whip, the ground cracks; you are rooted
 *   2 blink         tick 7       a zero-frame vanish along your look (up to RANGE blocks)
 *   3 hit-stop      4 ticks      both fighters frozen; inverted impact frames, lightning web, Dutch roll
 *   4 release       12 ticks     the victim rockets away gouging a trench; you skid 1.5 blocks, arm still out
 */
public final class LariatMath {
    private LariatMath() {}

    public static final int ANTICIPATION = 6, HITSTOP = 4, RELEASE = 12, ARM_HOLD = 8, AFTERIMAGES = 6, RIBBONS = 8, RIBBON_SEGMENTS = 12;
    public static final double RANGE = 18, STEP = 0.5, HIT_RADIUS = 1.6, LAUNCH_H = 3.4, LAUNCH_V = 0.85, SKID = 1.5, SKID_FRICTION = 0.72;
    public static final double MAX_PITCH = 0.35;   // the blink stays near-horizontal: |dir.y| <= 0.35
    public static final double LEVEL_PITCH = 0.2;  // a look within ~11.5 degrees of level runs dead flat
    public static final int REGEN_TICKS = 400;

    /** The blink direction: the look, run flat when near level, its vertical part clamped otherwise, normalised. */
    public static double[] blinkDir(double lx, double ly, double lz) {
        double y = Math.abs(ly) < LEVEL_PITCH ? 0 : Math.max(-MAX_PITCH, Math.min(MAX_PITCH, ly));
        double h = Math.sqrt(lx * lx + lz * lz);
        if (h < 1e-6) { lx = 0; lz = 1; h = 1; }
        double hs = Math.sqrt(Math.max(0, 1 - y * y));
        return new double[] {lx / h * hs, y, lz / h * hs};
    }

    /** Sample points along the blink, every STEP blocks up to `range`. */
    public static double[][] path(double x, double y, double z, double[] dir, double range) {
        int n = (int) Math.floor(range / STEP);
        double[][] out = new double[n][];
        for (int i = 1; i <= n; i++) out[i - 1] = new double[] {x + dir[0] * STEP * i, y + dir[1] * STEP * i, z + dir[2] * STEP * i};
        return out;
    }

    /** Where the caster lands on a hit: one block short of the victim, on the blink line. */
    public static double[] landing(double[] hitPoint, double[] dir) {
        return new double[] {hitPoint[0] - dir[0], hitPoint[1] - dir[1], hitPoint[2] - dir[2]};
    }

    /** Launch velocity: 3.4 along the horizontal blink direction, 0.85 up; bosses take half. */
    public static double[] launch(double[] dir, boolean boss) {
        double h = Math.sqrt(dir[0] * dir[0] + dir[2] * dir[2]);
        if (h < 1e-6) return new double[] {0, LAUNCH_V, LAUNCH_H};
        double m = boss ? 0.5 : 1;
        return new double[] {dir[0] / h * LAUNCH_H * m, LAUNCH_V * m, dir[2] / h * LAUNCH_H * m};
    }

    /** Initial skid speed so friction (per tick) carries the caster SKID blocks over ARM_HOLD ticks. */
    public static double skidStart() { return SKID * (1 - SKID_FRICTION) / (1 - Math.pow(SKID_FRICTION, ARM_HOLD)); }

    /** Distance skidded after `ticks`. */
    public static double skidAfter(int ticks) {
        double v = skidStart(), d = 0;
        for (int i = 0; i < Math.min(ticks, ARM_HOLD); i++) { d += v; v *= SKID_FRICTION; }
        return d;
    }

    /** Dutch-angle roll (degrees) during the hit-stop: snaps to 14, holds, eases out over 300 ms after it. */
    public static double dutch(double msSinceImpact, double holdMs) {
        if (msSinceImpact < 0) return 0;
        if (msSinceImpact < 40) return 14 * msSinceImpact / 40;
        if (msSinceImpact < holdMs) return 14;
        double u = (msSinceImpact - holdMs) / 300.0;
        return u >= 1 ? 0 : 14 * (1 - u) * (1 - u);
    }

    /** Field of view: tighten 12% over the anticipation, snap 45% wide on the blink, ease back. */
    public static double fov(double msSinceStart, double blinkMs) {
        if (msSinceStart < 0) return 1;
        if (msSinceStart < blinkMs) return 1 - 0.12 * msSinceStart / blinkMs;
        double u = msSinceStart - blinkMs;
        if (u < 50) return 0.88 + (1.45 - 0.88) * u / 50;
        if (u < 700) return 1.45 - 0.45 * (u - 50) / 650;
        return 1;
    }

    /** The six afterimages sit along the blink at 1/7 ... 6/7; later ones (nearer the hit) fade later. */
    public static double afterimageAt(int k) { return (k + 1) / (double) (AFTERIMAGES + 1); }
    public static float afterimageAlpha(int k, double msSinceBlink) {
        double life = 260 + 50 * k, u = msSinceBlink / life;
        return u < 0 || u >= 1 ? 0f : (float) (0.6 * (1 - u) * (1 - u));
    }

    /**
     * One ribbon segment's bend (radians) for ribbon i, segment s at `age` ticks: violent whipping in the
     * anticipation (`whip` 1), trailing nearly straight behind during and after the blink (`whip` small).
     */
    public static double ribbonBend(int i, int s, double age, double whip) {
        return Math.sin(age * 0.45 + i * 0.8 + s * 0.5) * (0.08 + 0.22 * whip) * (0.4 + s / (double) RIBBON_SEGMENTS);
    }

    /** The fan angle (degrees, around the body's back) where ribbon i leaves the tail base. */
    public static double ribbonFan(int i) { return (i - (RIBBONS - 1) / 2.0) * 21; }

    /** A seeded jagged bolt from a to b (n points, both ends fixed), offsets up to `jag` blocks. */
    public static double[][] bolt(double[] a, double[] b, int n, double jag, long seed) {
        java.util.Random r = new java.util.Random(seed);
        double[][] out = new double[n][];
        for (int i = 0; i < n; i++) {
            double u = i / (double) (n - 1), env = Math.sin(Math.PI * u);
            out[i] = new double[] {a[0] + (b[0] - a[0]) * u + (r.nextDouble() - 0.5) * 2 * jag * env,
                                   a[1] + (b[1] - a[1]) * u + (r.nextDouble() - 0.5) * 2 * jag * env,
                                   a[2] + (b[2] - a[2]) * u + (r.nextDouble() - 0.5) * 2 * jag * env};
        }
        out[0] = a.clone(); out[n - 1] = b.clone();
        return out;
    }

    /**
     * The screen-space lightning web for the impact frame: `arms` jagged arms out from (cx, cy), each with
     * one or two side branches. Returns polylines as flat {x0, y0, x1, y1, ...} arrays in pixels.
     */
    public static java.util.List<double[]> web(double cx, double cy, double reach, int arms, long seed) {
        java.util.Random r = new java.util.Random(seed);
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (int a = 0; a < arms; a++) {
            double ang = a * 2 * Math.PI / arms + (r.nextDouble() - 0.5) * 0.5, len = reach * (0.6 + r.nextDouble() * 0.4);
            int n = 9;
            double[] line = new double[n * 2];
            double x = cx, y = cy;
            for (int i = 0; i < n; i++) {
                line[i * 2] = x; line[i * 2 + 1] = y;
                double step = len / (n - 1), wob = (r.nextDouble() - 0.5) * 0.9;
                x += Math.cos(ang + wob) * step; y += Math.sin(ang + wob) * step;
            }
            out.add(line);
            for (int b = 0; b < 1 + r.nextInt(2); b++) {
                int from = 2 + r.nextInt(n - 4);
                double bx = line[from * 2], by = line[from * 2 + 1], bang = ang + (r.nextBoolean() ? 1 : -1) * (0.5 + r.nextDouble() * 0.6), blen = len * 0.35;
                double[] br = new double[5 * 2];
                for (int i = 0; i < 5; i++) {
                    br[i * 2] = bx; br[i * 2 + 1] = by;
                    double wob = (r.nextDouble() - 0.5) * 0.9;
                    bx += Math.cos(bang + wob) * blen / 4; by += Math.sin(bang + wob) * blen / 4;
                }
                out.add(br);
            }
        }
        return out;
    }

    /** The impact frame during the hit-stop: inverted black-and-white for the first two ticks, then normal. */
    public static boolean invertFrame(double ticksSinceImpact) { return ticksSinceImpact >= 0 && ticksSinceImpact < 2; }

    /** How far back (degrees) the victim's spine bends: snaps to 28 on contact, holds, recovers after the release. */
    public static double spineBend(double msSinceImpact, double holdMs) {
        if (msSinceImpact < 0) return 0;
        if (msSinceImpact < 50) return 28 * msSinceImpact / 50;
        if (msSinceImpact < holdMs + 150) return 28;
        double u = (msSinceImpact - holdMs - 150) / 350.0;
        return u >= 1 ? 0 : 28 * (1 - u);
    }
}
