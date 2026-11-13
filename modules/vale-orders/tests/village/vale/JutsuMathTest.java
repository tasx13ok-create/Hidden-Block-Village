package village.vale;

/** Headless checks for hand-technique presentation math (anchors, spiral geometry, bolts, lifecycle). */
public final class JutsuMathTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    private static double dist(double[] a, double[] b) { return len(new double[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]}); }

    public static void main(String[] args) {
        check(JutsuMath.easeOut(-1) == 0 && JutsuMath.easeOut(2) == 1 && JutsuMath.easeOut(Double.NaN) == 0, "ease bounds");
        double prev = -1;
        for (int i = 0; i <= 50; i++) { double v = JutsuMath.easeOut(i / 50.0); check(v >= prev, "ease monotonic"); prev = v; }

        double[] f = JutsuMath.forward(0), r = JutsuMath.right(0);
        check(Math.abs(f[2] - 1) < 1e-9 && Math.abs(r[0] + 1) < 1e-9, "yaw 0 faces +z with right hand at -x");
        for (float yaw = -720; yaw <= 720; yaw += 37) {
            double[] a = JutsuMath.forward(yaw), b = JutsuMath.right(yaw);
            check(Math.abs(len(a) - 1) < 1e-9 && Math.abs(dot(a, b)) < 1e-9, "forward/right orthonormal at " + yaw);
        }

        double[] down = JutsuMath.palm(0, 64, 0, 0, 0, false, true);
        double[] level = JutsuMath.palm(0, 64, 0, 0, Math.PI / 2, false, true);
        double[] sneak = JutsuMath.palm(0, 64, 0, 0, Math.PI / 2, true, true);
        double[] left = JutsuMath.palm(0, 64, 0, 0, Math.PI / 2, false, false);
        check(down[1] < 64.8 && down[1] > 64.5, "hanging palm at hip height: " + down[1]);
        check(Math.abs(level[1] - 65.41) < 0.02 && level[2] > 0.8, "raised palm at shoulder height in front: " + level[1] + "," + level[2]);
        check(level[0] < 0 && left[0] > 0, "right/left hands on correct sides");
        check(sneak[1] < level[1] - 0.15, "sneaking lowers the palm");
        double[] turned = JutsuMath.palm(10, 64, 10, 90, Math.PI / 2, false, true);
        check(turned[0] < 10 - 0.8, "yaw 90 (facing -x) puts the raised palm at -x: " + turned[0]);

        double[] eye = {0, 65.62, 0};
        for (float pitch = -80; pitch <= 80; pitch += 20) {
            double[] fp = JutsuMath.firstPerson(eye[0], eye[1], eye[2], 30, pitch);
            double[] rel = {fp[0] - eye[0], fp[1] - eye[1], fp[2] - eye[2]};
            check(dot(rel, JutsuMath.look(30, pitch)) > 0.55, "first-person anchor in front of camera at pitch " + pitch);
            check(dot(rel, JutsuMath.right(30)) > 0.25, "first-person anchor to the right at pitch " + pitch);
            check(len(rel) < 1.2, "first-person anchor close to the camera");
        }

        for (int band = 0; band < 16; band++) {
            double[] last = null;
            for (int i = 0; i <= 25; i++) {
                double[] p = JutsuMath.spiralBand(band, 16, i / 25.0, 37.5);
                check(Math.abs(len(p) - 1) < 1e-9, "band point on the unit sphere");
                if (last != null) check(dist(p, last) < 0.6, "band is a continuous curve");
                last = p;
            }
            check(dist(JutsuMath.spiralBand(band, 16, 0.3, 10), JutsuMath.spiralBand(band, 16, 0.3, 11)) > 0.05, "band visibly rotates every tick");
        }
        check(dist(JutsuMath.spiralBand(0, 16, 0.4, 5), JutsuMath.spiralBand(1, 16, 0.4, 5)) > 0.1, "bands are distinct");
        // Opposite spin directions: consecutive bands rotate different ways.
        double[] a0 = JutsuMath.spiralBand(0, 16, 0.0, 0), a1 = JutsuMath.spiralBand(0, 16, 0.0, 1);
        double[] b0 = JutsuMath.spiralBand(1, 16, 0.0, 0), b1 = JutsuMath.spiralBand(1, 16, 0.0, 1);
        double spinA = Math.atan2(a1[2], a1[0]) - Math.atan2(a0[2], a0[0]), spinB = Math.atan2(b1[2], b1[0]) - Math.atan2(b0[2], b0[0]);
        check(Math.signum(Math.sin(spinA)) != Math.signum(Math.sin(spinB)), "neighbouring bands counter-rotate");

        for (int i = 0; i < 30; i++) {
            check(len(JutsuMath.mote(i, 12, 0)) > len(JutsuMath.mote(i, 12, 1)) + 1, "charging motes start wide and spiral inward");
        }

        double[][] b = JutsuMath.bolt(42, new double[] {0, 1, 0}, 0.8, 7, 0.05);
        check(b.length == 8 && len(b[0]) == 0 && Math.abs(b[7][1] - 0.8) < 1e-9 && Math.abs(b[7][0]) < 1e-9, "bolt starts at the hand and ends exactly at its reach");
        check(java.util.Arrays.deepEquals(b, JutsuMath.bolt(42, new double[] {0, 1, 0}, 0.8, 7, 0.05)), "bolt deterministic per flicker seed");
        check(!java.util.Arrays.deepEquals(b, JutsuMath.bolt(43, new double[] {0, 1, 0}, 0.8, 7, 0.05)), "new flicker seed re-jags the bolt");
        for (double[] p : b) check(Math.abs(p[0]) <= 0.05 + 1e-9 && Math.abs(p[2]) <= 0.05 + 1e-9, "bolt jitter bounded");

        check(JutsuMath.scale(0, 0) < 0.2 && JutsuMath.scale(0, JutsuMath.CHARGE_TICKS) == 1, "charge grows to full size");
        check(JutsuMath.scale(2, 0) == 1 && JutsuMath.scale(2, JutsuMath.FIZZLE_TICKS) == 0, "fizzle shrinks to nothing");
        check(JutsuMath.scale(1, 5) == 1, "impact keeps full scale (burst handles its own growth)");
        System.out.println("PASS: " + checks + " hand-technique presentation math checks (anchors, spiral bands, motes, bolts, lifecycle).");
    }
}
