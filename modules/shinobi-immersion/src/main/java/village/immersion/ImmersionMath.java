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

    /** Ambience layer for a listener: 0 none, 1 forest birds (day), 2 night insects-like rustle, 3 cave drips/bats, 4 water lapping, 5 high wind. */
    public static int ambience(boolean underground, boolean nearWater, boolean forest, boolean day, double height) {
        if (underground) return 3;
        if (height > 110) return 5;
        if (nearWater) return 4;
        if (forest) return day ? 1 : 2;
        return day ? 0 : 2;
    }

    /** Seconds between ambience cues for a layer (randomised by the caller within +-50%). */
    public static int ambienceGap(int layer) { return layer == 0 ? 0 : layer == 5 ? 6 : layer == 3 ? 9 : 7; }

    /** Small pitch variation for repeated combat sounds, deterministic from a seed in [0,1). */
    public static float vary(float pitch, double seed) { return pitch * (float) (0.94 + 0.12 * seed); }

    /** Whether a sound is loud enough to earn a low "weight" tail. */
    public static boolean heavy(float volume) { return volume >= 0.6f; }

    // ---- perfect dodge ----
    /** A horizontal speed this high in one tick means a dodge burst (Core dodge speed is ~1.55). */
    public static boolean dodgeBurst(double speedXZ, double previousSpeedXZ) { return speedXZ > 1.0 && speedXZ - previousSpeedXZ > 0.6; }
    /** Ticks after a dodge starts during which a cancelled hit counts as a perfect dodge (Core grace is 6). */
    public static final int PERFECT_WINDOW = 6;
    public static boolean perfect(long now, long dodgeAt, boolean cancelled) { return cancelled && dodgeAt >= 0 && now - dodgeAt >= 0 && now - dodgeAt <= PERFECT_WINDOW; }

    // ---- elements ----
    public static final int NONE = 0, FIRE = 1, WATER = 2, LIGHTNING = 3, WIND = 4, EARTH = 5;
    /** Element from a damage type and the attacking entity's registry id/class name (keywords, lower case). */
    public static int element(String damageType, String sourceId, boolean fireDamage) {
        String k = ((damageType == null ? "" : damageType) + " " + (sourceId == null ? "" : sourceId)).toLowerCase(java.util.Locale.ROOT);
        if (k.contains("lightning") || k.contains("raiton") || k.contains("chidori") || k.contains("thunder") || k.contains("storm")) return LIGHTNING;
        if (fireDamage || k.contains("fire") || k.contains("katon") || k.contains("flame") || k.contains("ember") || k.contains("lava")) return FIRE;
        if (k.contains("water") || k.contains("suiton") || k.contains("mist") || k.contains("bubble") || k.contains("shark")) return WATER;
        if (k.contains("wind") || k.contains("futon") || k.contains("gale") || k.contains("vacuum")) return WIND;
        if (k.contains("earth") || k.contains("doton") || k.contains("stone") || k.contains("rock") || k.contains("flint")) return EARTH;
        return NONE;
    }
    public static final int R_NONE = 0, R_STEAM = 1, R_CHAIN = 2, R_SPREAD = 3, R_SNUFF = 4;
    /** Reaction for an element hitting a target that is wet and/or burning. */
    public static int reaction(int element, boolean wet, boolean burning) {
        if (element == FIRE && wet) return R_STEAM;
        if (element == LIGHTNING && wet) return R_CHAIN;
        if (element == WIND && burning) return R_SPREAD;
        if (element == EARTH && burning) return R_SNUFF;
        return R_NONE;
    }
    /** Damage multiplier for the triggering hit. */
    public static float reactionDamage(int reaction) { return reaction == R_STEAM ? 0.7f : reaction == R_CHAIN ? 1.1f : 1.0f; }

    // ---- bosses ----
    /** Health phase: 0 above 65%, 1 between 30% and 65%, 2 below 30%. */
    public static int phase(float health, float max) { if (!(max > 0)) return 0; float f = health / max; return f > 0.65f ? 0 : f > 0.30f ? 1 : 2; }
}
