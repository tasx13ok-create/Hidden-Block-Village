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
            double[] start = DodgeMath.lean(d, 0), mid = DodgeMath.lean(d, DodgeMath.ANIM_TICKS * 0.4), end = DodgeMath.lean(d, DodgeMath.ANIM_TICKS);
            check(Math.abs(start[0]) + Math.abs(start[1]) < 1e-9 && Math.abs(end[0]) + Math.abs(end[1]) < 1e-9, "upright before and after " + d);
            check(Math.abs(mid[0]) + Math.abs(mid[1]) > 20, "strong lean mid-step " + d);
            check(Math.abs(mid[0]) < 90 && Math.abs(mid[1]) < 90, "never flips " + d);
        }
        check(DodgeMath.lean(DodgeMath.FRONT, 4)[0] > 0 && DodgeMath.lean(DodgeMath.BACK, 4)[0] < 0, "front leans in, back leans away");
        check(DodgeMath.lean(DodgeMath.LEFT, 4)[1] == -DodgeMath.lean(DodgeMath.RIGHT, 4)[1], "side leans mirror");
        check(DodgeMath.tuck(0) < 1e-9 && DodgeMath.tuck(DodgeMath.ANIM_TICKS * 0.4) > 0.99 && DodgeMath.tuck(DodgeMath.ANIM_TICKS) < 1e-9, "pose snaps in, holds, recovers");
        for (int i = 0; i < DodgeMath.AFTERIMAGES; i++) {
            check(DodgeMath.ghostAlpha(i, 2) > DodgeMath.ghostAlpha(i + 1, 2), "older afterimages are fainter");
            check(DodgeMath.ghostAlpha(i, DodgeMath.ANIM_TICKS * 1.3) == 0, "afterimages gone after the step");
        }
        System.out.println("PASS: " + checks + " dodge checks (directions, velocity, gating, rotation, tuck).");
    }
}
