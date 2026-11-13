package village.vale;

/** Headless checks for arsenal weapon presentation math (palettes, slashes, chains, waves, seals, trails). */
public final class WeaponMathTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    private static double len(double[] a) { return Math.sqrt(dot(a, a)); }

    public static void main(String[] args) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String role : WeaponMath.ROLES) {
            int[] p = WeaponMath.palette(role);
            check(p.length == 2 && p[0] != 0xAAAAAA && p[1] != 0xAAAAAA, "palette for " + role);
            check((p[0] >>> 24) == 0 && (p[1] >>> 24) == 0, "palette is plain RGB for " + role);
            check(seen.add(Integer.toHexString(p[1]) + role), "distinct palette entry " + role);
        }
        check(WeaponMath.ROLES.length == 27, "one palette per arsenal role");
        check(WeaponMath.palette(null)[1] == 0xAAAAAA && WeaponMath.palette("NOPE")[1] == 0xAAAAAA, "unknown role falls back safely");

        for (float yaw = -360; yaw <= 360; yaw += 45) for (float pitch = -80; pitch <= 80; pitch += 40) {
            double[][] b = WeaponMath.basis(yaw, pitch);
            for (int i = 0; i < 3; i++) check(Math.abs(len(b[i]) - 1) < 1e-9, "basis unit " + i);
            check(Math.abs(dot(b[0], b[1])) < 1e-9 && Math.abs(dot(b[0], b[2])) < 1e-9 && Math.abs(dot(b[1], b[2])) < 1e-9, "basis orthogonal");
            double[] fwd = WeaponMath.toWorld(b, 0, 0, 2);
            check(Math.abs(dot(fwd, b[0]) - 2) < 1e-9, "local +z maps to look");
            check(b[2][1] > -1e-9, "up vector never points down");
        }

        for (double p = 0; p <= 1.0001; p += 0.05) {
            double[][][] c = WeaponMath.crescent(17, 1.4, 2.4, 0.3, p, 0.4);
            check(c[0].length == 17 && c[1].length == 17, "crescent sample count");
            for (int i = 0; i < 17; i++) {
                double ri = len(c[0][i]), ro = len(c[1][i]);
                check(ro >= ri - 1e-9, "outer edge outside inner edge");
                check(ro <= 1.4 + 0.3 * 0.35 + 1e-9 && ri >= 1.4 - 0.3 - 1e-9, "crescent stays near its radius");
                check(c[0][i][2] >= -1e-9 || Math.abs(len(c[0][i])) < 1e-9, "slash stays in front of the body");
            }
            check(len(new double[] {c[1][0][0] - c[0][0][0], c[1][0][1] - c[0][0][1], c[1][0][2] - c[0][0][2]}) < 1e-6, "crescent tip tapers to a point");
        }

        double[] a = {0, 65, 0}, b2 = {6, 66, 2};
        double[][] ch = WeaponMath.chain(a, b2, 12, 0.5);
        check(ch.length == 13 && java.util.Arrays.equals(ch[0], a) && java.util.Arrays.equals(ch[12], b2), "chain ends exactly at both anchors");
        check(ch[6][1] < (a[1] + b2[1]) / 2 - 0.45, "chain sags at the middle");

        for (int i = 0; i < 3; i++) {
            double prev = -1;
            for (double f = 0; f <= 1.0001; f += 0.02) { double r = WeaponMath.wave(i, 3, f, 4); check(r >= prev - 1e-12 && r <= 4 + 1e-9, "wave grows monotonically within range"); prev = r; }
            check(Math.abs(WeaponMath.wave(i, 3, 1, 4) - 4) < 1e-9, "every ring reaches full range");
        }
        check(WeaponMath.wave(2, 3, 0.2, 4) < WeaponMath.wave(0, 3, 0.2, 4), "later rings lag the first");

        double[][] s = WeaponMath.sigil(7, 3, 1, 0.3);
        check(s.length == 8 && Math.abs(s[0][0] - s[7][0]) < 1e-9 && Math.abs(s[0][2] - s[7][2]) < 1e-9, "seal star closes");
        for (double[] q : s) check(Math.abs(len(q) - 1) < 1e-9 && q[1] == 0, "seal points on the ground circle");
        double[][] r = WeaponMath.ring(2, 24, 0.1);
        check(r.length == 25 && Math.abs(r[0][0] - r[24][0]) < 1e-9, "ring closes");

        double[] v = {0.3, -0.2, 1.1};
        for (int i = 1; i < 10; i++) {
            double[] p0 = WeaponMath.trail(v, i - 1, 0.25, 0.1, 5), p1 = WeaponMath.trail(v, i, 0.25, 0.1, 5);
            check(dot(p1, JutsuMath.normalize(v)) < dot(p0, JutsuMath.normalize(v)), "trail extends behind the motion");
        }
        check(len(WeaponMath.trail(v, 0, 0.25, 0.1, 5)) < 1e-9, "trail starts at the body");
        check(len(WeaponMath.trail(new double[] {0, -1, 0}, 3, 0.25, 0.1, 5)) > 0.7, "vertical trail has a valid side vector");

        for (int i = 0; i < 200; i++) { double h = WeaponMath.hash(99, i); check(h >= 0 && h < 1, "hash in range"); }
        check(WeaponMath.hash(5, 3) == WeaponMath.hash(5, 3) && WeaponMath.hash(5, 3) != WeaponMath.hash(5, 4), "hash deterministic and varied");
        check(WeaponMath.fade(0, 10) == 1 && WeaponMath.fade(10, 10) == 0 && WeaponMath.fade(20, 10) == 0, "fade bounds");
        System.out.println("PASS: " + checks + " weapon presentation math checks (palettes, slashes, chains, waves, seals, trails).");
    }
}
