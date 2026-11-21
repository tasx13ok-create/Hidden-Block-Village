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
        // 2.0
        check(DashMath.tier(0) == 1 && DashMath.tier(6) == 1 && DashMath.tier(7) == 2 && DashMath.tier(19) == 2 && DashMath.tier(20) == 3 && DashMath.tier(99) == 3, "charge tiers");
        check(DashMath.tierCost(1, 50) == 50 && DashMath.tierCost(3, 50) == 80, "tier costs");
        check(DashMath.tierDamage(3) > DashMath.tierDamage(2) && DashMath.tierDamage(2) > DashMath.tierDamage(1), "tier damage rises");
        check(DashMath.tierMargin(3) == 1.0 && DashMath.tierFreeze(3) == DashMath.FREEZE + 1, "tier 3 bores wider and holds longer");
        double[] rs = DashMath.relaySpot(0, 64, 0, new double[] {0, 0.5, 4});
        check(rs[0] == 0 && rs[2] == 3 && rs[1] > 64, "relay lands ahead of the body");
        double[] rl = DashMath.relayLaunch(new double[] {3, 1, 0}, false);
        check(rl[0] < 0 && rl[1] == DashMath.LAUNCH_LIFT && Math.abs(Math.hypot(rl[0], rl[2]) - DashMath.LAUNCH_SPEED * 0.9) < 1e-9, "relay reverses the flight");
        double[][] stabs = DashMath.pinStabs(10, 64, 10);
        check(stabs.length == 9, "nine tail stabs");
        for (double[] st : stabs) { double r = Math.hypot(st[0] - 10, st[2] - 10); check(r > 1.5 && r < 2.6 && st[1] == 64, "stab ring around the target"); }
        check(Math.abs(DashMath.dollyDistance(4, 70, 70) - 4) < 1e-9 && DashMath.dollyDistance(4, 70, 100) < 4, "dolly zoom closes in as the view widens");
        double size0 = 1 / (4 * Math.tan(Math.toRadians(35))), size1 = 1 / (DashMath.dollyDistance(4, 70, 100) * Math.tan(Math.toRadians(50)));
        check(Math.abs(size0 - size1) < 1e-9, "dolly zoom keeps the subject the same size");
        float[] la = DashMath.lookAngles(0, 0, 0, 0, 0, 5);
        check(Math.abs(la[0]) < 1e-4 && Math.abs(la[1]) < 1e-4, "looking down +Z is yaw 0");
        check(Math.abs(DashMath.lookAngles(0, 0, 0, -5, 0, 0)[0] - 90) < 1e-4, "looking -X is yaw 90");
        check(DashMath.lookAngles(0, 10, 0, 0, 0, 0.001)[1] > 89, "looking straight down is pitch 90");
        double[] ob = DashMath.orbit(0, 0, 0, 4, 0, 1);
        check(Math.abs(ob[2] - 4) < 1e-9 && ob[1] == 1, "orbit starts on +Z");
        double[] ob90 = DashMath.orbit(0, 0, 0, 4, 90, 0);
        float[] back = DashMath.lookAngles(ob90[0], ob90[1], ob90[2], 0, 0, 0);
        check(Math.abs(Math.hypot(ob90[0], ob90[2]) - 4) < 1e-9 && Math.abs(back[1]) < 1e-4, "orbit stays on the circle");
        check(DashMath.ease(0) == 0 && DashMath.ease(1) == 1 && DashMath.ease(0.5) == 0.5 && DashMath.ease(2) == 1, "ease");
        check(DashMath.thunderDelayMs(10) == 0 && DashMath.thunderDelayMs(343) == 1000 && DashMath.thunderDelayMs(100) > 200, "thunder delay by distance");
        check(DashMath.rippleRadius(0) == 0 && DashMath.rippleRadius(500) == 8 && DashMath.rippleRadius(5000) == 14, "ripple front");
        int lets = 0; for (long tk = 0; tk < 40; tk++) if (DashMath.bubbleLets(tk)) lets++;
        check(lets == 10, "bubble runs mobs at a quarter rate");
        check(DashMath.bubbleBlend(0, 4) == 1 && DashMath.bubbleBlend(2, 2) == 2, "bubble keeps a quarter of each step");
        int cobble = 0; for (int x = 0; x < 100; x++) cobble += DashMath.coolTo(x, 64, x * 3) == 0 ? 1 : 0;
        check(cobble > 55 && cobble < 85 && DashMath.coolTo(5, 6, 7) == DashMath.coolTo(5, 6, 7), "scars cool mostly to cobblestone, stably");
        System.out.println("PASS: " + n + " steam dash checks");
    }
}
