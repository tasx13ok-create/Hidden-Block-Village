package village.immersion;

/**
 * Pure rules for the wall movement system and dodge feel. No Minecraft types, so every rule is
 * checked headlessly. Vectors are horizontal (x, z); a wall normal points out of the wall, toward
 * the player.
 */
public final class ParkourMath {
    private ParkourMath() {}

    public static final int NONE = 0, RUN_UP = 1, RUN_SIDE = 2, SLIDE = 3;
    /** Longest run up a wall and along a wall, in ticks. */
    public static final int RUN_UP_TICKS = 16, RUN_SIDE_TICKS = 40;
    /** A jump pressed this many ticks before touching a wall still counts; a wall jump stays possible this long after leaving it. */
    public static final int BUFFER = 4, COYOTE = 4;
    /** Ticks after a wall jump before a new wall state can start. */
    public static final int REENTRY = 5;
    public static final double SLIDE_FALL = -0.12, SIDE_MIN_SPEED = 0.12;

    public static boolean valid(int state) { return state >= NONE && state <= SLIDE; }

    /** 1 when looking straight into the wall, 0 when parallel, -1 when looking away. */
    public static double facing(double lookX, double lookZ, double nx, double nz) { return -(lookX * nx + lookZ * nz); }

    /** Speed along the wall surface (the velocity with its normal component removed). */
    public static double along(double vx, double vz, double nx, double nz) {
        double n = vx * nx + vz * nz, tx = vx - n * nx, tz = vz - n * nz;
        return Math.sqrt(tx * tx + tz * tz);
    }

    /** +1 when the wall is on the player's right, -1 on the left, 0 when ahead or behind. */
    public static int side(double lookX, double lookZ, double nx, double nz) {
        double cross = lookX * nz - lookZ * nx;   // y of look x normal
        return Math.abs(cross) < 0.3 ? 0 : cross > 0 ? -1 : 1;
    }

    /**
     * Which wall state to enter. {@code trigger} is a buffered jump press or a recent dodge;
     * {@code holding} is the jump key held now.
     */
    public static int choose(boolean airborne, boolean forward, boolean holding, boolean trigger, double facing, double alongSpeed, boolean falling, boolean pressingInto) {
        if (!airborne) return NONE;
        boolean go = forward && (holding || trigger);
        if (go && facing > 0.75) return RUN_UP;
        if (go && facing > -0.3 && facing <= 0.75 && alongSpeed >= SIDE_MIN_SPEED) return RUN_SIDE;
        if (falling && pressingInto) return SLIDE;
        return NONE;
    }

    /** Upward speed while running up a wall: strong at first, easing off, never a hover. */
    public static double runUpLift(int t) {
        if (t < 0 || t >= RUN_UP_TICKS) return 0;
        double u = t / (double) RUN_UP_TICKS;
        return 0.36 - 0.18 * u * u;
    }

    /** Lowest vertical speed while running along a wall: a small rise, then a gentle sink. */
    public static double sideFloor(int t) {
        if (t < 0) return 0;
        if (t < 8) return 0.10 * (1 - t / 8.0);
        double u = Math.min(1, (t - 8) / (double) (RUN_SIDE_TICKS - 8));
        return -0.02 - 0.08 * u;
    }

    /** Keeps the along-wall part of the velocity (at least {@code min}) and drops any push into the wall. */
    public static double[] alongWall(double vx, double vz, double nx, double nz, double lookX, double lookZ, double min) {
        double n = vx * nx + vz * nz, tx = vx - n * nx, tz = vz - n * nz;
        double len = Math.sqrt(tx * tx + tz * tz);
        if (len < 1e-6) {   // no along speed yet: run the way the player looks
            double ln = lookX * nx + lookZ * nz; tx = lookX - ln * nx; tz = lookZ - ln * nz; len = Math.sqrt(tx * tx + tz * tz);
            if (len < 1e-6) return new double[] {0, 0};
            return new double[] {tx / len * min, tz / len * min};
        }
        double s = Math.max(len, min) / len;
        return new double[] {tx * s - nx * 0.02, tz * s - nz * 0.02};   // a hair of grip into the wall
    }

    /** Launch velocity {x, y, z} for a wall jump: away from the wall, up, and along the look direction. */
    public static double[] wallJump(int state, double nx, double nz, double lookX, double lookZ) {
        double away = state == RUN_UP ? 0.48 : 0.40, up = state == SLIDE ? 0.46 : 0.52, carry = state == RUN_SIDE ? 0.28 : 0.10;
        double ln = lookX * nx + lookZ * nz, tx = lookX - ln * nx, tz = lookZ - ln * nz, tl = Math.sqrt(tx * tx + tz * tz);
        if (tl > 1e-6) { tx /= tl; tz /= tl; } else { tx = 0; tz = 0; }
        return new double[] {nx * away + tx * carry, up, nz * away + tz * carry};
    }

    /** The same wall: normals within ~25 degrees. A wall can't be run again until the player lands. */
    public static boolean sameWall(double ax, double az, double bx, double bz) { return ax * bx + az * bz > 0.9; }

    /** Camera roll in degrees during a side run: eases in over 5 ticks and back out over 6. */
    public static double tilt(int side, int ticksIn, int ticksSinceEnd) {
        if (side == 0) return 0;
        double k = ticksSinceEnd < 0 ? Math.min(1, Math.max(0, ticksIn) / 5.0) : Math.max(0, 1 - ticksSinceEnd / 6.0);
        return 8 * side * k;
    }

    /** Field-of-view multiplier: a short punch on dodge, a slight widening during a side run. */
    public static float fov(double sinceDodgeMs, boolean sideRun) {
        float punch = sinceDodgeMs < 0 || sinceDodgeMs > 260 ? 0 : (float) (0.09 * (1 - sinceDodgeMs / 260.0));
        return 1f + punch + (sideRun ? 0.05f : 0f);
    }

    /** Dodge direction from the movement keys, matching Core's own choice (0 front, 1 back, 2 left, 3 right). */
    public static int dodgeDirection(float forward, float strafe) {
        if (!Float.isFinite(forward) || !Float.isFinite(strafe)) return 1;
        if (Math.abs(strafe) > .1f && Math.abs(strafe) >= Math.abs(forward)) return strafe > 0 ? 2 : 3;
        return forward > .1f ? 0 : 1;
    }

    /** Camera roll in degrees right after a dodge press: leans into the dodge, gone in 220 ms. */
    public static double dodgeRoll(int direction, double sinceMs) {
        if (sinceMs < 0 || sinceMs > 220) return 0;
        double k = Math.sin(Math.PI * sinceMs / 220.0) * 5;
        return direction == 2 ? -k : direction == 3 ? k : 0;
    }

    /** Dodge-hop: horizontal speed kept when jumping within the window after a ground dodge. */
    public static final int HOP_WINDOW = 6;
    public static double hopSpeed(double peak, double current) { return Math.max(current, Math.min(1.1, peak * 0.7)); }
}
