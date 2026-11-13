package village.vale;

/** Pure rules and motion curves for the four-way dodge (no Minecraft classes; unit-tested). */
public final class DodgeMath {
    private DodgeMath() {}
    public static final int FRONT = 0, BACK = 1, LEFT = 2, RIGHT = 3;
    public static final String[] NAMES = {"front", "back", "left", "right"};
    /** Animation length and server recovery (ticks). */
    public static final int ANIM_TICKS = 12, COOLDOWN_TICKS = 16, IFRAME_TICKS = 6;
    public static final double SPEED = 1.05, LIFT = 0.22, BACK_LIFT = 0.34;

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

    /** Whole-body rotation in degrees: front roll +360, back flip -360, side rolls +/-360 about the forward axis. */
    public static double spin(int dir, double age) {
        double p = progress(age);
        switch (dir) {
            case FRONT: return 360 * p;
            case BACK: return -360 * p;
            case LEFT: return -360 * p;
            default: return 360 * p;
        }
    }

    /** Tuck amount 0..1: limbs pull in mid-roll and release at the end. */
    public static double tuck(double age) {
        double u = Math.max(0, Math.min(1, age / ANIM_TICKS));
        return Math.sin(Math.PI * Math.min(1, u * 1.15));
    }
}
