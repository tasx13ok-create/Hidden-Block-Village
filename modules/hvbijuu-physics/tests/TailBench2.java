import java.util.*;
import village.bijuu.TailPhysics;
import village.bijuu.TailProfile;

/** Body and multi-tail bench: tails that try to swing through the body and through each other. */
public final class TailBench2 {
    public static void main(String[] a) {
        int tails = 6, n = 12; double seg = 0.22;
        List<TailPhysics.Box> ground = new ArrayList<>(); ground.add(new TailPhysics.Box(-20, 0, -20, 20, 1, 20));
        double[] radii = new double[n]; Arrays.fill(radii, 0.07);
        TailPhysics[] t = new TailPhysics[tails];
        double[][][] poses = new double[tails][][];
        double worstBody = 0, worstOverlap = 0; int nonfinite = 0;
        for (int tick = 0; tick < 200; tick++) {
            double sweepAng = Math.sin(tick * 0.09) * 2.6;    // poses swing tails forward through the body and across each other
            for (int k = 0; k < tails; k++) {
                double fan = (k - (tails - 1) / 2.0) * 0.08 + sweepAng;   // deliberately crowded
                double[][] pose = new double[n][3];
                for (int i = 0; i < n; i++) { pose[i][0] = Math.sin(fan) * i * seg; pose[i][1] = 1.9 - i * seg * 0.25; pose[i][2] = -Math.cos(fan) * i * seg - 0.0; }
                pose[0] = new double[] {0, 1.9, 0};
                poses[k] = pose;
                if (t[k] == null) t[k] = new TailPhysics(pose, radii);
                t[k].step(pose, ground, TailProfile.of(8), false);
            }
            double cx = 0, cz = 0.17;   // body capsule centre (root at origin, tails point -z)
            for (int k = 0; k < tails; k++) for (int i = 2; i < n; i++) {
                double[] p = t[k].points[i];
                for (double v : p) if (!Double.isFinite(v)) nonfinite++;
                boolean poseOutside = Math.hypot(poses[k][i][0] - cx, poses[k][i][2] - cz) >= 0.37;
                if (poseOutside && p[1] > 1.9 - 1.45 && p[1] < 1.9 + 0.55) worstBody = Math.max(worstBody, 0.3 + 0.07 - Math.hypot(p[0] - cx, p[2] - cz));
                for (int m = k + 1; m < tails; m++) for (int j = 2; j < n; j++) worstOverlap = Math.max(worstOverlap, 0.14 - TailPhysics.distance(p, t[m].points[j]));
            }
        }
        System.out.printf("bodyPenetration=%.3f tailOverlap=%.3f nonfinite=%d%n", Math.max(0, worstBody), Math.max(0, worstOverlap), nonfinite);
    }
}
