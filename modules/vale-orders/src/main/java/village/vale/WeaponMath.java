package village.vale;

/**
 * Pure presentation math for arsenal weapon effects (slash crescents, chains, shockwaves, seals,
 * trails and per-role palettes). No Minecraft classes, so every shape is unit-testable headless.
 * Local frames use x = right, y = up, z = forward.
 */
public final class WeaponMath {
    private WeaponMath() {}

    /** Ticks each one-shot effect stays visible. */
    public static final int CAST_TICKS = 14, BURST_TICKS = 26, MUZZLE_TICKS = 8, ARC_TICKS = 8, SWING_TICKS = 7;

    /** Role keys in ArsenalSpec.Role declaration order (kept as strings so this class stays pure). */
    public static final String[] ROLES = {"WIND", "RETURN", "MARK", "DRAIN", "CHAIN", "SHOCK", "SLAM", "BREAK", "RESTORE",
        "DRAIN_BOLT", "FIRE", "BURST", "RIME", "BIND", "EXTEND", "MEND", "GUIDED", "EMITTER", "VENOM", "MIST", "FINISH",
        "GUARD", "OVERDRIVE", "SONIC", "SWEEP", "TRAP", "TALONS"};

    /** {core, glow} colours per role: core is the bright centre, glow the saturated body colour. */
    public static int[] palette(String role) {
        switch (role == null ? "" : role) {
            case "WIND": return new int[] {0xF2FFF0, 0x9FD97A};
            case "RETURN": return new int[] {0xF4FFF4, 0x7FC98A};
            case "MARK": return new int[] {0xF6ECFF, 0x9A6BE0};
            case "DRAIN": return new int[] {0xFFD6DE, 0xB0173A};
            case "CHAIN": return new int[] {0xF4F0FF, 0x8F7BFF};
            case "SHOCK": return new int[] {0xF2F8FF, 0x4F8DFF};
            case "SLAM": return new int[] {0xFFE3B8, 0x9A6A3A};
            case "BREAK": return new int[] {0xFFEBD0, 0xC2702E};
            case "RESTORE": return new int[] {0xFFF6D0, 0xE0B040};
            case "DRAIN_BOLT": return new int[] {0xFFF4D8, 0xD9902A};
            case "FIRE": return new int[] {0xFFE27A, 0xFF6A1A};
            case "BURST": return new int[] {0xFFD08A, 0xE8411A};
            case "RIME": return new int[] {0xF0FCFF, 0x6CC8EE};
            case "BIND": return new int[] {0xE8FFE8, 0x4FA860};
            case "EXTEND": return new int[] {0xE8FFD8, 0x3E9A3A};
            case "MEND": return new int[] {0xEFFFF0, 0x5ED27E};
            case "GUIDED": return new int[] {0xEFFBFF, 0x34C8FF};
            case "EMITTER": return new int[] {0xEFFFFD, 0x2FB5B5};
            case "VENOM": return new int[] {0xF0FFD0, 0x6FB82A};
            case "MIST": return new int[] {0xE8E4F0, 0x6E6684};
            case "FINISH": return new int[] {0xFFFFFF, 0xFFE89A};
            case "GUARD": return new int[] {0xEFFFFF, 0x3FB8C8};
            case "OVERDRIVE": return new int[] {0xFFE0C0, 0xFF3A1A};
            case "SONIC": return new int[] {0xF4FFF8, 0x8FE0B0};
            case "SWEEP": return new int[] {0xF4FDFF, 0x7FC8E8};
            case "TRAP": return new int[] {0xFFF0E0, 0xD2452A};
            case "TALONS": return new int[] {0xF0E8FF, 0x6A4C9A};
            default: return new int[] {0xFFFFFF, 0xAAAAAA};
        }
    }

    public static double clamp01(double v) { return v > 0 ? (v < 1 ? v : 1) : 0; }

    /** Orthonormal {forward, right, up} basis for a yaw/pitch look (Minecraft degrees). */
    public static double[][] basis(float yaw, float pitch) {
        double[] f = JutsuMath.look(yaw, pitch), r = JutsuMath.right(yaw);
        double[] u = JutsuMath.cross(r, f);
        return new double[][] {f, r, u};
    }

    /** Converts a local (right, up, forward) offset into world space using {@link #basis}. */
    public static double[] toWorld(double[][] b, double x, double y, double z) {
        return new double[] {
            b[1][0] * x + b[2][0] * y + b[0][0] * z,
            b[1][1] * x + b[2][1] * y + b[0][1] * z,
            b[1][2] * x + b[2][2] * y + b[0][2] * z};
    }

    /**
     * Slash crescent sweeping across the front of the body. Returns {inner, outer} point arrays
     * (local frame). The visible blade is the trailing window behind the leading edge at
     * {@code progress}; it tapers at both ends. {@code tilt} rolls the slash plane (radians).
     */
    public static double[][][] crescent(int n, double radius, double arc, double width, double progress, double tilt) {
        double lead = clamp01(progress) * 1.35, tail = lead - 0.55;
        double[][] in = new double[n][], out = new double[n][];
        for (int i = 0; i < n; i++) {
            double s = tail + (lead - tail) * i / (n - 1.0), c = clamp01(s);
            double a = -arc / 2 + arc * c;
            double taper = Math.sin(Math.PI * clamp01((s - tail) / (lead - tail)));
            double w = width * taper * (s < 0 || s > 1 ? 0 : 1);
            double x = Math.sin(a), z = Math.cos(a);
            in[i] = roll(x * (radius - w), 0, z * (radius - w), tilt);
            out[i] = roll(x * (radius + w * 0.35), 0, z * (radius + w * 0.35), tilt);
        }
        return new double[][][] {in, out};
    }

    static double[] roll(double x, double y, double z, double tilt) {
        return new double[] {x * Math.cos(tilt) - y * Math.sin(tilt), x * Math.sin(tilt) + y * Math.cos(tilt), z};
    }

    /** Chain/tether between two points with a gravity sag at the middle (world units). */
    public static double[][] chain(double[] a, double[] b, int links, double sag) {
        double[][] pts = new double[links + 1][];
        for (int i = 0; i <= links; i++) {
            double f = (double) i / links;
            pts[i] = new double[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f - sag * 4 * f * (1 - f), a[2] + (b[2] - a[2]) * f};
        }
        return pts;
    }

    /** Radius of shockwave ring {@code i} (of {@code rings}) at progress f; rings start staggered. */
    public static double wave(int i, int rings, double f, double range) {
        double start = Math.min(0.6, i * 0.18), local = clamp01((f - start) / (1 - start));
        return range * JutsuMath.easeOut(local);
    }

    /** Star-polygon seal (points connected every {@code skip}) on a circle of radius r in the XZ plane. */
    public static double[][] sigil(int points, int skip, double r, double spin) {
        double[][] pts = new double[points + 1][];
        for (int i = 0; i <= points; i++) {
            double a = spin + (i * skip % points) * Math.PI * 2 / points;
            pts[i] = new double[] {Math.cos(a) * r, 0, Math.sin(a) * r};
        }
        return pts;
    }

    /** Points of a horizontal circle (closed). */
    public static double[][] ring(double r, int n, double y) {
        double[][] pts = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double a = i * Math.PI * 2 / n; pts[i] = new double[] {Math.cos(a) * r, y, Math.sin(a) * r}; }
        return pts;
    }

    /** Trail sample i behind a moving body: offsets back along -velocity with slight spiral wobble. */
    public static double[] trail(double[] velocity, int i, double spacing, double wobble, double t) {
        double[] d = JutsuMath.normalize(velocity);
        double[] side = Math.abs(d[1]) > 0.9 ? new double[] {1, 0, 0} : JutsuMath.normalize(JutsuMath.cross(d, new double[] {0, 1, 0}));
        double[] up = JutsuMath.cross(side, d);
        double back = i * spacing, a = t * 0.7 + i * 0.9, w = wobble * i / (i + 3.0);
        return new double[] {
            -d[0] * back + (side[0] * Math.cos(a) + up[0] * Math.sin(a)) * w,
            -d[1] * back + (side[1] * Math.cos(a) + up[1] * Math.sin(a)) * w,
            -d[2] * back + (side[2] * Math.cos(a) + up[2] * Math.sin(a)) * w};
    }

    /** Deterministic pseudo-random 0..1 for (seed, i) so effects don't flicker between frames. */
    public static double hash(long seed, int i) {
        long x = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31; x *= 0x94D049BB133111EBL; x ^= x >>> 29;
        return ((x >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    /** Fraction 0..1 of an effect's life, with a fade that is 1 for the first part then eases to 0. */
    public static double fade(double age, int life) {
        double f = clamp01(age / life);
        return f < 0.45 ? 1 : 1 - (f - 0.45) / 0.55;
    }
}
