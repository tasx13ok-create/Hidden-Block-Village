package village.immersion;

/** Pure rules for immersion features (unit-tested without Minecraft). */
public final class ImmersionMath {
    private ImmersionMath() {}

    /** Ticks between chakra payments while water-walking. */
    public static final int WATER_PAY_EVERY = 20;

    /** Water-walk: allowed only when not sneaking (sneak to dive), not flying, with the feet at the surface. */
    public static boolean waterWalk(boolean enabled, boolean sneaking, boolean flying, boolean feetInWater, boolean headInWater, double motionY) {
        return enabled && !sneaking && !flying && feetInWater && !headInWater && motionY <= 0.1;
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

    // ---- hit-stop ----
    /** Impact strength 0..1 for a hit: 0 below the heavy threshold, full at 2x it or on a finishing blow. */
    public static float impact(float amount, float threshold, boolean finisher) {
        if (!(threshold > 0) || !(amount > 0)) return 0;
        if (finisher) return 1;
        if (amount < threshold) return 0;
        return Math.min(1f, 0.5f + 0.5f * (amount - threshold) / threshold);
    }
    /** Milliseconds the camera holds still: 2-3 frames at 60 fps. */
    public static long holdMs(float strength) { return strength <= 0 ? 0 : 30 + Math.round(20 * Math.min(1f, strength)); }
    /** Camera kick in degrees after the hold, decaying linearly over 160 ms. */
    public static double kick(float strength, long sinceMs, long holdMs) {
        long t = sinceMs - holdMs;
        if (strength <= 0 || t < 0 || t > 160) return 0;
        return 1.6 * strength * (1 - t / 160.0);
    }

    // ---- pressure: guard break and stagger ----
    /** Hits within {@code window} ticks of the previous one keep building; a gap resets to 1. */
    public static int pressure(int count, long lastAt, long now, int window) { return lastAt >= 0 && now - lastAt <= window ? count + 1 : 1; }
    public static final int GUARD_WINDOW = 60, GUARD_BREAK = 5, STAGGER_WINDOW = 40, STAGGER_HITS = 4, STAGGER_HITS_BOSS = 8, STAGGER_COOLDOWN = 100;
    public static int staggerHits(boolean boss) { return boss ? STAGGER_HITS_BOSS : STAGGER_HITS; }

    // ---- low health ----
    /** 0 at or above 25% health, rising to 1 at zero. */
    public static float danger(float health, float max) {
        if (!(max > 0) || health <= 0) return 0;
        float f = health / max;
        return f >= 0.25f ? 0 : Math.min(1f, (0.25f - f) / 0.25f * 0.8f + 0.2f);
    }
    /** Ticks between heartbeats: 30 at the threshold, 16 near death. */
    public static int heartbeatGap(float danger) { return danger <= 0 ? 0 : Math.round(30 - 14 * Math.min(1f, danger)); }
    /** World sound volume multiplier while in danger (a gentle tunnelling, never silent). */
    public static float dangerMuffle(float danger) { return 1f - 0.3f * Math.max(0f, Math.min(1f, danger)); }

    // ---- chakra exhaustion ----
    /** Hysteresis: exhausted below 5% and stays exhausted until above 15%. */
    public static boolean exhausted(boolean was, double amount, double max) {
        if (!(max > 0) || Double.isNaN(amount)) return false;
        double f = amount / max;
        return was ? f < 0.15 : f < 0.05;
    }

    // ---- ledge grab ----
    /** Ledge height above the feet that a grab will climb: out of normal jump reach but not a wall. */
    public static boolean ledgeReach(double ledgeTopAboveFeet) { return ledgeTopAboveFeet > 1.15 && ledgeTopAboveFeet <= 2.6; }
    /** Upward motion that lifts the player onto a ledge this far above the feet. */
    public static double ledgeLift(double ledgeTopAboveFeet) { return Math.max(0.5, Math.min(0.8, 0.3 + 0.19 * ledgeTopAboveFeet)); }

    // ---- landing roll ----
    /** Whether a fall qualifies for a roll: sneaking on landing, more than 3.5 blocks, and survivable. */
    public static boolean roll(boolean sneaking, float distance) { return sneaking && distance > 3.5f && distance < 24f; }
    /** Fall damage multiplier after a roll: 40% of normal, fading back to 70% for long drops. */
    public static float rollDamage(float distance) { return distance <= 10f ? 0.4f : Math.min(0.7f, 0.4f + (distance - 10f) * 0.03f); }

    // ---- canopy and roof running ----
    public static final double CANOPY_BOOST = 0.15;

    // ---- weather ----
    /** Player-marker range on the compass: shorter in rain, shortest in storms. */
    public static int compassRange(boolean raining, boolean thundering) { return thundering ? 32 : raining ? 64 : 128; }
    /** Lightning chain {radius, max targets}: wider when the target stands in rain. */
    public static double[] chain(boolean rain) { return rain ? new double[] {6, 5} : new double[] {4, 3}; }
    /** Wind direction in radians, turning slowly over the day so dust drift stays consistent. */
    public static double windAngle(long worldTime) { return (worldTime % 24000L) / 24000.0 * Math.PI * 2 * 0.25 + 0.7; }

    // ---- footsteps ----
    /** Step volume multiplier by sound path: soft ground quieter, hard ground unchanged. */
    public static float stepVolume(String path) {
        String p = path == null ? "" : path;
        if (p.contains("grass") || p.contains("cloth") || p.contains("snow") || p.contains("sand")) return 0.8f;
        return 1f;
    }
    public static boolean isStep(String path) { return path != null && path.endsWith(".step"); }

    // ---- village life ----
    /** Village cue kind: 0 none, 1 chatter, 2 smithing, 3 door. Only by day with at least two residents near. */
    public static int village(int residents, boolean day, double roll) {
        if (!day || residents < 2) return 0;
        return roll < 0.55 ? 1 : roll < 0.8 ? 2 : 3;
    }
    public static int villageGap(int residents) { return residents < 2 ? 0 : Math.max(5, 14 - residents); }

    // ---- squad ----
    public static final int SQUAD_MAX = 6;
    /** Health pips 0..5 for a squad member; a member with any health left shows at least one. */
    public static int pips(float health, float max) { if (!(max > 0) || health <= 0) return 0; return Math.max(1, Math.min(5, (int) Math.ceil(health / max * 5))); }
    /** Downed: at or below 20% health (pulses on the HUD). */
    public static boolean downed(float health, float max) { return max > 0 && health > 0 && health / max <= 0.2f; }

    // ---- sparring ----
    public static final int SPAR_COUNTDOWN = 60, SPAR_INVITE = 600, SPAR_LIMIT = 6000, SPAR_RANGE = 48;
    /** Final damage allowed from a sparring partner: never below 1 health. Returns {amount, ended(1/0)}. */
    public static float[] sparCap(float amount, float health) {
        if (amount < health - 1f) return new float[] {amount, 0};
        return new float[] {Math.max(0f, health - 1f), 1};
    }
}
