package village.bijuu;

/** Headless checks for the Gyuki Lariat rules. */
public final class LariatChecks {
    static int n;
    static void check(boolean ok, String what) { n++; if (!ok) throw new AssertionError(what); }
    static double len(double[] a) { return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); }

    public static void main(String[] a) {
        double[] d = LariatMath.blinkDir(0, 0, 1);
        check(d[2] == 1 && Math.abs(len(d) - 1) < 1e-9, "level look");
        double[] up = LariatMath.blinkDir(0, 0.9, 0.44);
        check(Math.abs(up[1] - LariatMath.MAX_PITCH) < 1e-9 && Math.abs(len(up) - 1) < 1e-9, "steep looks are clamped near level");
        check(Math.abs(len(LariatMath.blinkDir(0, -1, 0)) - 1) < 1e-9, "straight down still gives a unit direction");
        check(LariatMath.blinkDir(0, -0.035, 1)[1] == 0 && LariatMath.blinkDir(0.3, 0.15, 0.94)[1] == 0, "a look near level runs dead flat");
        check(LariatMath.blinkDir(0, -0.3, 0.95)[1] < -0.25, "a deliberate downward aim still dives");
        double[][] p = LariatMath.path(0, 64, 0, d, 18);
        check(p.length == 36 && p[0][2] == 0.5 && p[35][2] == 18, "path samples every half block to 18");
        double[] land = LariatMath.landing(new double[] {0, 64, 10}, d);
        check(land[2] == 9, "the caster lands a block short of the victim");
        double[] v = LariatMath.launch(new double[] {0.6, 0.3, 0.8}, false);
        check(Math.abs(Math.hypot(v[0], v[2]) - 3.4) < 1e-9 && v[1] == 0.85, "launch 3.4 out, 0.85 up");
        check(Math.hypot(LariatMath.launch(d, true)[0], LariatMath.launch(d, true)[2]) < 1.8, "bosses take half");
        check(Math.abs(LariatMath.skidAfter(LariatMath.ARM_HOLD) - 1.5) < 1e-9 && LariatMath.skidAfter(1) > 0.3, "the skid covers 1.5 blocks over the arm hold");
        check(LariatMath.dutch(0, 200) == 0 && LariatMath.dutch(100, 200) == 14 && LariatMath.dutch(600, 200) == 0 && LariatMath.dutch(300, 200) < 14, "Dutch roll snaps, holds, eases out");
        check(Math.abs(LariatMath.fov(300, 300) - 0.88) < 1e-9 && Math.abs(LariatMath.fov(350, 300) - 1.45) < 1e-9 && LariatMath.fov(1100, 300) == 1, "FOV tightens 12% then snaps 45% wide");
        for (int k = 0; k < LariatMath.AFTERIMAGES; k++) {
            check(LariatMath.afterimageAt(k) > 0 && LariatMath.afterimageAt(k) < 1, "afterimage on the path");
            check(LariatMath.afterimageAlpha(k, 0) == 0.6f && LariatMath.afterimageAlpha(k, 1000) == 0, "afterimage fades");
        }
        check(LariatMath.afterimageAlpha(5, 200) > LariatMath.afterimageAlpha(0, 200), "nearer the hit fades later");
        check(Math.abs(LariatMath.ribbonBend(0, 11, 3, 1)) > Math.abs(LariatMath.ribbonBend(0, 11, 3, 0)) || Math.abs(LariatMath.ribbonBend(0, 11, 3, 0)) < 0.12, "ribbons whip harder in the anticipation");
        check(LariatMath.ribbonFan(0) == -LariatMath.ribbonFan(7), "ribbons fan symmetrically");
        double[][] b = LariatMath.bolt(new double[] {0, 0, 0}, new double[] {1, 0, 0}, 8, 0.1, 42);
        check(b.length == 8 && b[0][0] == 0 && b[7][0] == 1 && b[7][1] == 0, "bolt ends are fixed");
        java.util.List<double[]> web = LariatMath.web(640, 360, 500, 7, 3);
        check(web.size() >= 14, "web has arms and branches");
        check(web.get(0)[0] == 640 && web.get(0)[1] == 360, "web starts at the contact point");
        check(LariatMath.invertFrame(0) && LariatMath.invertFrame(1.9) && !LariatMath.invertFrame(2) && !LariatMath.invertFrame(-1), "two inverted frames");
        check(LariatMath.spineBend(100, 200) == 28 && LariatMath.spineBend(1000, 200) == 0, "spine bends back and recovers");
        System.out.println("PASS: " + n + " lariat checks");
    }
}
