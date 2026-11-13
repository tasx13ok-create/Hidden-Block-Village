package village.vale;

/** Pure rules and motion curves for the four-way dodge (no Minecraft classes; unit-tested). */
public final class DodgeMath {
    private DodgeMath() {}
    public static final int FRONT = 0, BACK = 1, LEFT = 2, RIGHT = 3;
    public static final String[] NAMES = {"front", "back", "left", "right"};
    /** Animation length and server recovery (ticks). */
    public static final int ANIM_TICKS = 9, COOLDOWN_TICKS = 16, IFRAME_TICKS = 6, AFTERIMAGES = 5;
    /** Flash-step burst: fast and low to the ground (anime body-flicker), not a hop. */
    public static final double SPEED = 1.55, LIFT = 0.08, BACK_LIFT = 0.14;

    /** Direction from held movement input (moveForward > 0 is W, moveStrafe > 0 is A). No input dodges backwards. */
    public static int direction(float forward, float strafe) {
        if (Math.abs(strafe) > 0.1f && Math.abs(strafe) >= Math.abs(forward)) return strafe > 0 ? LEFT : RIGHT;
        if (forward > 0.1f) return FRONT;
        return BACK;
    }

    public static boolean valid(int dir) { return dir >= 0 && dir < 4; }

    /** World velocity {x, y, z} for a dodge from body yaw (Minecraft degrees). */
    public static double[] velocity(int dir, float yaw) {
        double[] f = JutsuMath.forward(yaw), r = JutsuMath.right(yaw);
        double[] d;
        switch (dir) {
            case FRONT: d = f; break;
            case LEFT: d = new double[] {-r[0], 0, -r[2]}; break;
            case RIGHT: d = r; break;
            default: d = new double[] {-f[0], 0, -f[2]}; break;
        }
        double s = dir == BACK ? SPEED * 0.9 : SPEED;
        return new double[] {d[0] * s, dir == BACK ? BACK_LIFT : LIFT, d[2] * s};
    }

    /** Server gate: alive, cooldown elapsed and (on ground or air dodge still unused). */
    public static boolean allowed(boolean alive, boolean spectator, long now, long last, boolean onGround, boolean airUsed) {
        return alive && !spectator && now - last >= COOLDOWN_TICKS && (onGround || !airUsed);
    }

    /** Eased 0..1 progress of the animation. */
    public static double progress(double age) {
        double t = JutsuMath.easeOut(age / ANIM_TICKS);
        double u = Math.max(0, Math.min(1, age / ANIM_TICKS));
        return 0.35 * u + 0.65 * t;
    }

    /**
     * Body lean in degrees {pitch, roll}: a deep forward dash lean, a backstep lean, and side
     * leans into the step. Snaps in fast, holds, then recovers to upright.
     */
    public static double[] lean(int dir, double age) {
        double k = tuck(age);
        switch (dir) {
            case FRONT: return new double[] {38 * k, 0};
            case BACK: return new double[] {-22 * k, 0};
            case LEFT: return new double[] {8 * k, -28 * k};
            default: return new double[] {8 * k, 28 * k};
        }
    }

    /** Afterimage alpha for ghost i (0 = newest) at the given age; ghosts fade as the step ends. */
    public static double ghostAlpha(int i, double age) {
        double life = 1 - Math.max(0, Math.min(1, (age - ANIM_TICKS * 0.4) / (ANIM_TICKS * 0.8)));
        return Math.max(0, 0.45 * (1 - i / (double) AFTERIMAGES) * life);
    }

    /** Tuck amount 0..1: limbs pull in mid-roll and release at the end. */
    public static double tuck(double age) {
        double u = Math.max(0, Math.min(1, age / ANIM_TICKS));
        double in = Math.min(1, u * 4), out = Math.min(1, (1 - u) * 2.5);
        return Math.sin(Math.PI / 2 * Math.min(in, out));
    }
}
