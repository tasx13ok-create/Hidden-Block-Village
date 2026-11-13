package village.vale;

/** Headless checks for dodge direction, velocity, gating and animation curves. */
public final class DodgeMathTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    public static void main(String[] args) {
        check(DodgeMath.direction(1, 0) == DodgeMath.FRONT, "W is front");
        check(DodgeMath.direction(-1, 0) == DodgeMath.BACK, "S is back");
        check(DodgeMath.direction(0, 1) == DodgeMath.LEFT, "A is left");
        check(DodgeMath.direction(0, -1) == DodgeMath.RIGHT, "D is right");
        check(DodgeMath.direction(0, 0) == DodgeMath.BACK, "no input dodges back");
        check(DodgeMath.direction(1, 1) == DodgeMath.LEFT && DodgeMath.direction(0.3f, -1) == DodgeMath.RIGHT, "diagonals favour the side step");
        for (float yaw = -720; yaw <= 720; yaw += 15) {
            double[] f = DodgeMath.velocity(DodgeMath.FRONT, yaw), b = DodgeMath.velocity(DodgeMath.BACK, yaw);
            double[] l = DodgeMath.velocity(DodgeMath.LEFT, yaw), r = DodgeMath.velocity(DodgeMath.RIGHT, yaw);
            double[] fwd = JutsuMath.forward(yaw), right = JutsuMath.right(yaw);
            check(f[0] * fwd[0] + f[2] * fwd[2] > 1, "front goes forward");
            check(b[0] * fwd[0] + b[2] * fwd[2] < -0.9, "back goes backward");
            check(r[0] * right[0] + r[2] * right[2] > 1 && l[0] * right[0] + l[2] * right[2] < -1, "sides go sideways");
            check(Math.abs(l[0] * fwd[0] + l[2] * fwd[2]) < 1e-9, "side dodge has no forward drift");
            check(b[1] > f[1] && f[1] > 0, "every dodge lifts, the back flip most");
        }
        check(DodgeMath.allowed(true, false, 100, 0, true, true), "ground dodge after cooldown");
        check(!DodgeMath.allowed(true, false, 100, 90, true, false), "cooldown blocks spam");
        check(DodgeMath.allowed(true, false, 100, 0, false, false) && !DodgeMath.allowed(true, false, 100, 0, false, true), "one air dodge until landing");
        check(!DodgeMath.allowed(false, false, 100, 0, true, false) && !DodgeMath.allowed(true, true, 100, 0, true, false), "dead/spectator cannot dodge");
        double prev = -1;
        for (int i = 0; i <= 40; i++) { double p = DodgeMath.progress(i * DodgeMath.ANIM_TICKS / 40.0); check(p >= prev && p <= 1, "progress monotonic"); prev = p; }
        for (int d = 0; d < 4; d++) {
            check(DodgeMath.spin(d, 0) == 0 && Math.abs(Math.abs(DodgeMath.spin(d, DodgeMath.ANIM_TICKS)) - 360) < 1e-9, "full rotation ends upright " + d);
            check(DodgeMath.spin(d, 30) == DodgeMath.spin(d, DodgeMath.ANIM_TICKS), "spin holds after the end");
        }
        check(Math.signum(DodgeMath.spin(DodgeMath.FRONT, 5)) != Math.signum(DodgeMath.spin(DodgeMath.BACK, 5)), "front roll and back flip turn opposite ways");
        check(Math.signum(DodgeMath.spin(DodgeMath.LEFT, 5)) != Math.signum(DodgeMath.spin(DodgeMath.RIGHT, 5)), "left and right rolls mirror");
        check(DodgeMath.tuck(0) < 1e-9 && DodgeMath.tuck(DodgeMath.ANIM_TICKS * 0.45) > 0.9 && DodgeMath.tuck(DodgeMath.ANIM_TICKS) < 0.5, "tuck peaks mid-roll");
        System.out.println("PASS: " + checks + " dodge checks (directions, velocity, gating, rotation, tuck).");
    }
}
