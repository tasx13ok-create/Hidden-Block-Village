package village.immersion;

/** Pure rules for immersion features (unit-tested without Minecraft). */
public final class ImmersionMath {
    private ImmersionMath() {}

    /** Ticks between chakra payments while water-walking. */
    public static final int WATER_PAY_EVERY = 20;
    /** Maximum continuous wall-run time (ticks) and upward speed. */
    public static final int WALL_RUN_TICKS = 18;
    public static final double WALL_RUN_LIFT = 0.32;

    /** Water-walk: allowed only when not sneaking (sneak to dive), not flying, with the feet at the surface. */
    public static boolean waterWalk(boolean enabled, boolean sneaking, boolean flying, boolean feetInWater, boolean headInWater, double motionY) {
        return enabled && !sneaking && !flying && feetInWater && !headInWater && motionY <= 0.1;
    }

    /** Wall-run starts when sprinting, airborne, jumping and pressing into a wall; lasts a bounded time. */
    public static boolean wallRun(boolean enabled, boolean sprinting, boolean onGround, boolean collidedHorizontally, int ticksRunning, boolean jumpHeld) {
        return enabled && sprinting && !onGround && collidedHorizontally && jumpHeld && ticksRunning < WALL_RUN_TICKS;
    }

    /** Upward speed during a wall-run, easing off toward the end. */
    public static double wallLift(int ticksRunning) {
        double u = Math.max(0, Math.min(1, ticksRunning / (double) WALL_RUN_TICKS));
        return WALL_RUN_LIFT * (1 - u * u);
    }

    /** Underwater muffle: volume and pitch multipliers. */
    public static float[] underwater(float volume, float pitch) {
        return new float[] {Math.max(0, volume) * 0.45f, Math.max(0.5f, pitch * 0.72f)};
    }

    /** Cave echo: delay in ticks and echo volume for a given enclosure 0..1 (share of sky blocked). */
    public static int echoDelay(double enclosure) { return 4 + (int) Math.round(6 * clamp(enclosure)); }
    public static float echoVolume(float volume, double enclosure) { return (float) (Math.max(0, volume) * 0.28 * clamp(enclosure)); }

    /** Compass: screen x offset (pixels) for a world bearing (degrees) seen from yaw, or NaN when off the bar. */
    public static double compassX(double bearing, double yaw, double halfWidth, double fieldDegrees) {
        double d = wrap(bearing - yaw);
        if (Math.abs(d) > fieldDegrees / 2) return Double.NaN;
        return d / (fieldDegrees / 2) * halfWidth;
    }

    /** Bearing in Minecraft yaw degrees from (fx, fz) toward (tx, tz). */
    public static double bearing(double fx, double fz, double tx, double tz) {
        return Math.toDegrees(Math.atan2(-(tx - fx), tz - fz));
    }

    public static double wrap(double deg) { deg %= 360; if (deg >= 180) deg -= 360; if (deg < -180) deg += 360; return deg; }
    static double clamp(double v) { return v < 0 ? 0 : v > 1 ? 1 : v; }
}
