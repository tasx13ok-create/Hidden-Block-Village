package village.vale;

/**
 * Pure presentation math for hand-held technique effects (no Minecraft classes, unit-testable).
 * Coordinates are world-space doubles; yaw/pitch are Minecraft degrees.
 */
public final class JutsuMath {
    private JutsuMath() {}

    public static final int CHARGE_TICKS = 16, IMPACT_TICKS = 14, FIZZLE_TICKS = 8;

    /** Smooth 0..1 ease-out, clamped. */
    public static double easeOut(double t) {
        if (!(t > 0)) return 0;
        if (t >= 1) return 1;
        double u = 1 - t;
        return 1 - u * u * u;
    }

    /** Horizontal forward unit vector for a body yaw (degrees). */
    public static double[] forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new double[] {-Math.sin(r), 0, Math.cos(r)};
    }

    /** Horizontal right-hand unit vector for a body yaw (degrees). */
    public static double[] right(float yaw) {
        double r = Math.toRadians(yaw);
        return new double[] {-Math.cos(r), 0, -Math.sin(r)};
    }

    /** Full look vector for yaw/pitch (degrees). */
    public static double[] look(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new double[] {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
    }

    /**
     * Palm position of the right arm raised to {@code armPitch} radians (0 = hanging, PI/2 = level
     * forward) for a standing or sneaking player whose feet are at (x,y,z). Matches the vanilla
     * biped proportions (shoulder 1.37 above feet, 0.33 to the side, arm 0.70 long).
     */
    public static double[] palm(double x, double y, double z, float bodyYaw, double armPitch, boolean sneaking, boolean rightHand) {
        double[] f = forward(bodyYaw), r = right(bodyYaw);
        double side = rightHand ? 0.33 : -0.33, shoulder = sneaking ? 1.17 : 1.37, arm = 0.70;
        double out = Math.sin(armPitch) * arm + 0.14, down = Math.cos(armPitch) * arm;
        return new double[] {
            x + r[0] * side + f[0] * out,
            y + shoulder - down + 0.04,
            z + r[2] * side + f[2] * out};
    }

    /** First-person anchor in front of the camera, lower right of view. */
    public static double[] firstPerson(double eyeX, double eyeY, double eyeZ, float yaw, float pitch) {
        double[] l = look(yaw, pitch), r = right(yaw);
        double[] up = cross(r, l);
        return new double[] {
            eyeX + l[0] * 0.95 + r[0] * 0.36 + up[0] * -0.30,
            eyeY + l[1] * 0.95 + r[1] * 0.36 + up[1] * -0.30,
            eyeZ + l[2] * 0.95 + r[2] * 0.36 + up[2] * -0.30};
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /**
     * Point on spiral band {@code band} at parameter s (0..1) of a sphere of radius 1, at time t
     * (ticks). Each band is a tilted loop that precesses on its own axis, so the set rotates in
     * many directions at once (the signature look of a compressed rotating sphere).
     */
    public static double[] spiralBand(int band, int bands, double s, double t) {
        double phase = band * (Math.PI * 2 / bands);
        double tilt = 0.35 + 1.1 * ((band * 0.618034) % 1.0);
        double spin = t * (0.85 + 0.23 * (band % 3)) * (band % 2 == 0 ? 1 : -1) + phase;
        double a = s * Math.PI * 2 * 0.82 + spin;
        double lat = Math.sin(s * Math.PI * 3 + phase + t * 0.31) * 0.42;
        double cx = Math.cos(a) * Math.cos(lat), cy = Math.sin(lat), cz = Math.sin(a) * Math.cos(lat);
        double precess = t * 0.19 * (band % 2 == 0 ? 1 : -1) + phase;
        double y1 = cy * Math.cos(tilt) - cz * Math.sin(tilt), z1 = cy * Math.sin(tilt) + cz * Math.cos(tilt);
        double x2 = cx * Math.cos(precess) + z1 * Math.sin(precess), z2 = -cx * Math.sin(precess) + z1 * Math.cos(precess);
        return new double[] {x2, y1, z2};
    }

    /** Orbiting mote positions (unit sphere scale) with an inward spiral while charging. */
    public static double[] mote(int i, double t, double charge) {
        double phase = i * 2.399963;
        double baseR = 1.15 + 0.35 * Math.sin(i * 1.7 + t * 0.21);
        double r = baseR + (1 - charge) * 2.2;
        double a = phase + t * (0.6 + 0.05 * (i % 7)) + (1 - charge) * 6;
        double y = Math.sin(phase * 1.3 + t * 0.4) * 0.6;
        return new double[] {Math.cos(a) * r, y * r * 0.6, Math.sin(a) * r};
    }

    /** Deterministic jagged lightning polyline from origin along dir, re-seeded per flicker. */
    public static double[][] bolt(long seed, double[] dir, double length, int segments, double jitter) {
        long state = seed * 0x5DEECE66DL + 11;
        double[][] pts = new double[segments + 1][];
        double[] d = normalize(dir);
        for (int i = 0; i <= segments; i++) {
            double f = (double) i / segments;
            double jx = 0, jy = 0, jz = 0;
            if (i > 0 && i < segments) {
                state = state * 0x5DEECE66DL + 11;
                jx = (((state >>> 17) & 0xFFFF) / 65535.0 - 0.5) * 2 * jitter;
                state = state * 0x5DEECE66DL + 11;
                jy = (((state >>> 17) & 0xFFFF) / 65535.0 - 0.5) * 2 * jitter;
                state = state * 0x5DEECE66DL + 11;
                jz = (((state >>> 17) & 0xFFFF) / 65535.0 - 0.5) * 2 * jitter;
            }
            pts[i] = new double[] {d[0] * length * f + jx, d[1] * length * f + jy, d[2] * length * f + jz};
        }
        return pts;
    }

    public static double[] normalize(double[] v) {
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return n < 1e-9 ? new double[] {0, 1, 0} : new double[] {v[0] / n, v[1] / n, v[2] / n};
    }

    /** Visual radius multiplier for a hand technique's lifecycle state. */
    public static double scale(int state, int ageInState) {
        if (state == 0) return 0.08 + 0.92 * easeOut(ageInState / (double) CHARGE_TICKS);
        if (state == 2) return Math.max(0, 1 - ageInState / (double) FIZZLE_TICKS);
        return 1;
    }
}
