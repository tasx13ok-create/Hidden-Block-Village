package village.bijuu;

/** Headless checks for the Steam Dash Punch rules. */
public final class DashChecks {
    static int n;
    static void check(boolean ok, String what) { n++; if (!ok) throw new AssertionError(what); }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    public static void main(String[] a) {
        double[] d = DashMath.direction(0, 64, 0, 10, 64, 0);
        check(Math.abs(d[0] - 1) < 1e-9 && len(d) > 0.999, "direction unit");
        check(DashMath.direction(1, 1, 1, 1, 1, 1)[2] == 1, "coincident fallback");
        check(DashMath.dashStep(30) == DashMath.DASH_SPEED && DashMath.dashStep(3) < 3 && DashMath.dashStep(0.5) == 0, "dash never overshoots the target");
        check(DashMath.range() == 35, "range from dash speed and ticks");
        double[] l = DashMath.launch(new double[] {0.6, -0.8, 0}, false);
        check(Math.abs(l[0] - 2.52) < 1e-9 && Math.abs(l[1] - (-3.36 + 1.1)) < 1e-9 && l[2] == 0, "launch = look x 4.2 + 1.1 up");
        check(Math.abs(DashMath.launch(d, false)[0] - 4.2) < 1e-9 && DashMath.launch(d, false)[1] == 1.1, "level launch");
        check(Math.hypot(DashMath.launch(d, true)[0], DashMath.launch(d, true)[2]) < 2.2, "bosses fly half as far");
        check(DashMath.smashing(4) && DashMath.smashing(1.25) && !DashMath.smashing(1.2) && !DashMath.smashing(0.5), "smashing above 1.2");
        double[] sv = DashMath.spikeVelocity(new double[] {0, 0.3, 2});
        check(sv[1] == -3.8 && sv[0] == 0 && Math.abs(Math.toDegrees(Math.atan2(-sv[1], sv[2])) - 75) < 1e-6, "spike slams at 75 degrees");
        check(DashMath.spikeVelocity(new double[] {0, 1, 0})[1] == -3.8, "vertical spike fallback");
        for (double[] look : new double[][] {{-1, 0, 0}, {0, 0, 1}, {-0.7, 0.2, 0.7}, {1, 0, 0}, {0, -1, 0}}) {
            double[] v = DashMath.springboard(new double[] {1, 0, 0}, look);
            check(Math.abs(len(v) - 1.68) < 1e-9, "springboard speed is 1.4x");
            check(v[0] > 0.2, "springboard always leaves the wall");
            check(v[1] > 0, "springboard lifts");
        }
        check(DashMath.springboard(new double[] {1, 0, 0}, new double[] {0, 0, 1})[2] > 0.5, "springboard keeps the look heading along the wall");
        check(DashMath.box(new double[] {1, 0, 0}, 2).length == 4 && DashMath.box(new double[] {1, 0, 0}, 3).length == 9, "rubble box sizes");
        for (int[] c : DashMath.box(new double[] {0, 0, 1}, 2)) check(c[2] == 0, "2x2 box is perpendicular");
        int core = 0, spoke = 0, outside = 0;
        for (int x = -6; x <= 6; x++) for (int y = -6; y <= 6; y++) {
            int k = DashMath.splatter(x, y, 42);
            if (Math.sqrt(x * x + y * y) > 4.01) { if (k != 0) outside++; }
            else if (Math.sqrt(x * x + y * y) <= 1.5) { if (k == 1) core++; }
            else if (k == 2) spoke++;
            check(DashMath.splatter(x, y, 42) == k, "splatter is deterministic");
        }
        check(outside == 0 && core == 9 && spoke >= 10, "splatter: solid core, spokes, nothing past 4 (" + core + "/" + spoke + ")");
        check(DashMath.impactFrame(0) == 1 && DashMath.impactFrame(0.9) == 1 && DashMath.impactFrame(1.5) == 2 && DashMath.impactFrame(2.9) == 2 && DashMath.impactFrame(3) == 0 && DashMath.impactFrame(-1) == 0, "white flash then ink until release");
        check(DashMath.trailAlpha(0) == 0.55f && DashMath.trailAlpha(3) > 0 && DashMath.trailAlpha(4) == 0, "trail clones fade");
        for (double[] dir : new double[][] {{1, 0, 0}, {0, 1, 0}, {0.3, 0.4, -0.8}}) {
            double[][] r = DashMath.ring(dir, 1.5, 24);
            check(r.length == 24, "ring count");
            for (double[] p : r) { check(Math.abs(len(p) - 1.5) < 1e-6, "ring radius"); check(Math.abs(dot(p, DashMath.norm(dir))) < 1e-6, "ring perpendicular to travel"); }
            int[][] t = DashMath.tunnel(dir);
            check(t.length == 9, "tunnel is 3x3");
            java.util.Set<String> cells = new java.util.HashSet<>();
            for (int[] c : t) { cells.add(c[0] + "," + c[1] + "," + c[2]); check(Math.abs(c[0]) <= 1 && Math.abs(c[1]) <= 1 && Math.abs(c[2]) <= 1, "tunnel cells adjacent"); }
            check(cells.contains("0,0,0"), "tunnel includes the centre");
        }
        check(DashMath.tunnel(new double[] {1, 0, 0}).length == 9 && new java.util.HashSet<>(java.util.Arrays.asList(java.util.Arrays.stream(DashMath.tunnel(new double[] {1, 0, 0})).map(java.util.Arrays::toString).toArray())).size() == 9, "axis tunnel has 9 distinct cells");
        check(DashMath.craterDepth(0, 8) == 3 && DashMath.craterDepth(7.9, 8) == 0 && DashMath.craterDepth(8, 8) == 0 && DashMath.craterDepth(4, 8) == 2, "crater bowl");
        check(DashMath.breakable(1.5f, 3) && DashMath.breakable(3f, 3) && !DashMath.breakable(50f, 3) && !DashMath.breakable(-1f, 3), "hardness rule");
        check(DashMath.fov(0) == 1f && Math.abs(DashMath.fov(200) - 0.92f) < 1e-6 && Math.abs(DashMath.fov(260) - 1.25f) < 1e-6 && DashMath.fov(1300) == 1f, "fov pull, snap, ease");
        check(DashMath.shake(0) == 1.5 && DashMath.shake(450) == 0 && DashMath.shake(200) < 1.5, "shake decays");
        check(Math.abs(DashMath.ghostAlpha(0) - 0.55f) < 1e-6 && DashMath.ghostAlpha(8) == 0 && DashMath.ghostAlpha(4) < 0.55f, "afterimage fades");
        double[] s = DashMath.spikeSpot(10, 70, 10, new double[] {1, 0, 0});
        check(s[0] == 8 && s[1] == 72 && s[2] == 10, "spike lands 2 behind and 2 above");
        double[] cp = DashMath.closest(0, 0, 0, 10, 0, 0, 5, 1, 0);
        check(cp[0] == 5 && cp[1] == 0, "closest point on the flown path");
        check(DashMath.closest(0, 0, 0, 10, 0, 0, -3, 0, 0)[0] == 0 && DashMath.closest(0, 0, 0, 10, 0, 0, 14, 0, 0)[0] == 10, "closest point clamps to the segment");
        check(DashMath.closest(1, 1, 1, 1, 1, 1, 5, 5, 5)[0] == 1, "zero-length path");
        double flown = 0; for (int i = 0; i < DashMath.LAUNCH; i++) flown += DashMath.boilSpeed(i, false);
        check(flown > 45 && flown < 60, "the boil carries a body about 50 blocks (" + flown + ")");
        check(DashMath.boilSpeed(0, false) == DashMath.LAUNCH_SPEED && DashMath.boilSpeed(10, true) < DashMath.boilSpeed(10, false), "boil decays and bosses fly less");
        System.out.println("PASS: " + n + " steam dash checks");
    }
}
