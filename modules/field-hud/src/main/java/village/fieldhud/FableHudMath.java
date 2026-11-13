package village.fieldhud;

/**
 * Pure motion math for the animated Fable hotbars (no Minecraft classes, unit-tested): damped
 * springs, overshoot easing, pixel snapping, deterministic noise and environment curves.
 */
public final class FableHudMath {
    private FableHudMath() {}

    /** Critically-tunable damped spring integrated with fixed sub-steps (frame-rate independent). */
    public static final class Spring {
        public double value, velocity, target;
        public final double stiffness, damping;
        public Spring(double stiffness, double damping, double start) { this.stiffness = stiffness; this.damping = damping; value = target = start; }
        /** Advances by {@code dt} ticks; large gaps (pause, lag) snap instead of exploding. */
        public double step(double dt) {
            if (!(dt > 0)) return value;
            if (dt > 20) { value = target; velocity = 0; return value; }
            int n = Math.max(1, (int) Math.ceil(dt / 0.25));
            double h = dt / n;
            for (int i = 0; i < n; i++) {
                double a = -stiffness * (value - target) - damping * velocity;
                velocity += a * h;
                value += velocity * h;
            }
            if (Math.abs(value - target) < 1e-4 && Math.abs(velocity) < 1e-4) { value = target; velocity = 0; }
            return value;
        }
        public void kick(double impulse) { velocity += impulse; }
        public void snap(double v) { value = target = v; velocity = 0; }
    }

    public static double clamp(double v, double lo, double hi) { return v < lo ? lo : v > hi ? hi : v; }

    /** 0..1 -> 0..1 with a small overshoot ("back out"), for pop-ins. */
    public static double backOut(double t) {
        t = clamp(t, 0, 1);
        double c = 1.70158, u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    /** Pulse that rises fast and decays: 0 at t<=0, peak 1 near t=0.15, 0 at t>=1. */
    public static double pulse(double t) {
        if (!(t > 0) || t >= 1) return 0;
        return t < 0.15 ? t / 0.15 : Math.pow(1 - (t - 0.15) / 0.85, 2);
    }

    /** Two-beat heartbeat 0..1 at a given beats-per-tick rate. */
    public static double heartbeat(double time, double rate) {
        double p = (time * rate) % 1.0;
        return Math.max(pulse(p / 0.3), 0.6 * pulse((p - 0.18) / 0.3));
    }

    public static int snap(double v) { return (int) Math.floor(v + 0.5); }

    /** Deterministic hash noise in [0,1). */
    public static double noise(long seed, long i) {
        long x = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31; x *= 0x94D049BB133111EBL; x ^= x >>> 29;
        return ((x >>> 11) & ((1L << 53) - 1)) / (double) (1L << 53);
    }

    /** Biome temperature (vanilla scale) to frost 0..1 (cold) and heat 0..1 (hot). */
    public static double frost(double temperature) { return clamp((0.2 - temperature) / 0.5, 0, 1); }
    public static double heat(double temperature) { return clamp((temperature - 1.2) / 0.8, 0, 1); }

    /** Approaches target exponentially with a half-life in ticks. */
    public static double approach(double current, double target, double dt, double halfLife) {
        if (!(dt > 0)) return current;
        if (dt > 40) return target;
        return target + (current - target) * Math.pow(0.5, dt / halfLife);
    }

    /** Selected slot lift in pixels for distance from the selector (0 = selected). */
    public static double lift(double distance) { return 3.0 * Math.max(0, 1 - Math.abs(distance) / 1.4); }

    /** Pixel offset (x, y) for a shake of given strength at a time, deterministic per tick. */
    public static double[] shake(double strength, double time) {
        long t = (long) Math.floor(time * 2);
        return new double[] {(noise(17, t) - 0.5) * 2 * strength, (noise(31, t) - 0.5) * 2 * strength};
    }
}
