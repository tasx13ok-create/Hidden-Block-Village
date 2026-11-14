import java.util.*;
import village.bijuu.TailPhysics;
import village.bijuu.TailProfile;

/** Headless bench for a TailPhysics implementation: same scripted motion, measured metrics. */
public final class TailBench {
    static double[][] pose(double rx, double ry, double rz, int n, double seg, double droop) {
        double[][] p = new double[n][3];
        for (int i = 0; i < n; i++) { p[i][0] = rx; p[i][1] = ry - i * seg * droop; p[i][2] = rz - i * seg * Math.sqrt(1 - droop * droop); }
        return p;
    }
    public static void main(String[] a) {
        int n = 12; double seg = 0.22;
        double[] radii = new double[n]; Arrays.fill(radii, 0.06);
        List<TailPhysics.Box> ground = new ArrayList<>();
        ground.add(new TailPhysics.Box(-20, 0, -20, 20, 1, 20));          // floor top at y=1
        ground.add(new TailPhysics.Box(-0.5, 1, -3.5, 0.5, 2.0, -2.5));  // a block behind the player
        TailProfile prof = TailProfile.of(Integer.parseInt(a.length > 0 ? a[0] : "8"));
        double[][] start = pose(0, 1.9, 0, n, seg, 0.35);
        TailPhysics t = new TailPhysics(start, radii);
        double maxStretch = 0, penetration = 0, tipLagSum = 0, settleMotion = 0, maxSpeed = 0, folds = 0, slide = 0;
        double[] tipAtRest = null;
        int ticks = 260;
        for (int tick = 0; tick < ticks; tick++) {
            double rz = 0, ry = 1.9, rx = 0;
            if (tick >= 20 && tick < 70) rz = (tick - 20) * 0.28;              // run forward
            else if (tick >= 70) rz = 50 * 0.28;
            if (tick >= 70 && tick < 110) rx = Math.sin((tick - 70) * 0.25) * 1.2; // weave
            if (tick >= 120 && tick < 136) { double u = (tick - 120) / 16.0; ry = 1.9 + 4 * 1.25 * u * (1 - u); } // jump
            double[][] target = pose(rx, ry, rz, n, seg, 0.35);
            double[] before = t.points[n - 1].clone();
            t.step(target, ground, prof, false);
            double tipMove = TailPhysics.distance(before, t.points[n - 1]);
            maxSpeed = Math.max(maxSpeed, tipMove);
            for (int i = 1; i < n; i++) {
                double d = TailPhysics.distance(t.points[i], t.points[i - 1]);
                maxStretch = Math.max(maxStretch, Math.abs(d - seg) / seg);
                double[] p = t.points[i];
                for (TailPhysics.Box b : ground) {
                    double depth = Double.MAX_VALUE; boolean in = true;
                    for (int k = 0; k < 3; k++) { if (p[k] <= b.lo[k] || p[k] >= b.hi[k]) { in = false; break; } depth = Math.min(depth, Math.min(p[k] - b.lo[k], b.hi[k] - p[k])); }
                    if (in) penetration = Math.max(penetration, depth);
                }
                if (i >= 2 && TailPhysics.distance(t.points[i], t.points[i - 2]) < 0.4 * seg) folds++;
                for (int k = 0; k < 3; k++) if (!Double.isFinite(p[k])) { System.out.println("NONFINITE"); return; }
            }
            if (tick >= 20 && tick < 70) tipLagSum += target[n - 1][2] - t.points[n - 1][2];
            if (tick >= 230) settleMotion += tipMove;
            if (tick == 240) tipAtRest = t.points[n - 1].clone();
            if (tick == 259 && tipAtRest != null) slide = TailPhysics.distance(tipAtRest, t.points[n - 1]);
        }
        System.out.printf("stretch=%.3f penetration=%.4f folds=%.0f tipLag=%.2f peakTipSpeed=%.3f settleMotion=%.4f restSlide=%.4f%n",
            maxStretch, penetration, folds, tipLagSum / 50, maxSpeed, settleMotion, slide);
    }
}
