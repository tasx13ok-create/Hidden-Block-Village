package village.fieldhud;

public final class FableHudMathTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] a) {
        FableHudMath.Spring s = new FableHudMath.Spring(0.35, 0.9, 0);
        s.target = 8;
        double max = 0;
        for (int i = 0; i < 400; i++) { s.step(0.5); max = Math.max(max, s.value); check(Double.isFinite(s.value), "spring finite"); }
        check(Math.abs(s.value - 8) < 1e-3, "spring settles on target");
        check(max > 8 && max < 9.5, "spring overshoots a little (lively but bounded): " + max);
        FableHudMath.Spring f1 = new FableHudMath.Spring(0.35, 0.9, 0), f2 = new FableHudMath.Spring(0.35, 0.9, 0);
        f1.target = f2.target = 5;
        for (int i = 0; i < 40; i++) f1.step(0.25);
        for (int i = 0; i < 10; i++) f2.step(1.0);
        check(Math.abs(f1.value - f2.value) < 1e-9, "frame-rate independent");
        s.target = -3; s.step(500); check(s.value == -3 && s.velocity == 0, "long pause snaps");
        s.step(Double.NaN); check(s.value == -3, "NaN dt ignored");
        check(FableHudMath.backOut(0) < 1e-9 && Math.abs(FableHudMath.backOut(1) - 1) < 1e-9, "backOut ends");
        double peak = 0; for (int i = 0; i <= 100; i++) peak = Math.max(peak, FableHudMath.backOut(i / 100.0));
        check(peak > 1.05 && peak < 1.2, "backOut overshoot");
        check(FableHudMath.pulse(0) == 0 && FableHudMath.pulse(1) == 0 && Math.abs(FableHudMath.pulse(0.15) - 1) < 1e-9, "pulse shape");
        for (double t = 0; t < 50; t += 0.37) { double h = FableHudMath.heartbeat(t, 0.05); check(h >= 0 && h <= 1, "heartbeat bounded"); }
        check(FableHudMath.frost(-0.5) == 1 && FableHudMath.frost(0.8) == 0 && FableHudMath.heat(2.0) == 1 && FableHudMath.heat(0.8) == 0, "climate curves");
        check(FableHudMath.lift(0) == 3 && FableHudMath.lift(2) == 0 && FableHudMath.lift(0.5) > FableHudMath.lift(1), "slot lift");
        double v = 0; for (int i = 0; i < 100; i++) v = FableHudMath.approach(v, 10, 1, 4); check(Math.abs(v - 10) < 1e-3, "approach converges");
        check(FableHudMath.approach(2, 10, 100, 4) == 10, "approach snaps after long gaps");
        double[] k = FableHudMath.shake(2, 3.2), k2 = FableHudMath.shake(2, 3.2);
        check(k[0] == k2[0] && Math.abs(k[0]) <= 2 && Math.abs(k[1]) <= 2, "shake deterministic and bounded");
        check(FableHudMath.snap(2.5) == 3 && FableHudMath.snap(-0.4) == 0, "pixel snap");
        System.out.println("PASS: " + checks + " Fable HUD motion checks (springs, easing, pulses, climate, shake).");
    }
}
