package village.vale.client;

import java.util.ArrayList;
import java.util.List;

/** Actual emitted-cube checks. No GL, Minecraft, wall-clock or FPS assumptions. */
public final class VoxelGeometryChecks {
    private static int assertions, groups;
    private static final List<String> failures = new ArrayList<>();
    private static final double[][] LINE = {{0,0,0},{1,0,0},{80,0,0}};
    private static final double[][] INNER = {{0,0,0},{1,0,0},{80,0,0}};
    private static final double[][] OUTER = {{0,0,8},{1,0,8},{80,0,8}};
    private static void check(boolean ok, String label) {
        assertions++;
        if (!ok) failures.add(label);
    }
    private static void run(String name, Runnable body) {
        groups++;
        try { body.run(); } catch (Throwable failure) { failures.add(name + " threw " + failure); }
    }
    private static void bounded(List<VoxelGeometry.Cube> cubes, int budget, String label) {
        check(cubes.size() <= Math.max(0,Math.min(384,budget)), label + " emitted count obeys caller and hard budget");
        for (VoxelGeometry.Cube c : cubes) {
            check(Double.isFinite(c.x) && Double.isFinite(c.y) && Double.isFinite(c.z) && Double.isFinite(c.size)
                  && Float.isFinite(c.alpha), label + " cube is finite");
            check(c.size >= 1/16.0 && c.size <= 16 && c.alpha > .003f && c.alpha <= 1,
                  label + " cube size and opacity fit primitive bounds");
            check(Math.abs(c.x) <= 384 && Math.abs(c.y) <= 384 && Math.abs(c.z) <= 384,
                  label + " cube remains inside supported coordinate/radius envelope");
        }
    }
    private static boolean near(List<VoxelGeometry.Cube> cubes, double x, double y, double z) {
        for (VoxelGeometry.Cube c : cubes) {
            double tolerance = c.size/2 + 1e-9;
            if (Math.abs(c.x-x)<=tolerance && Math.abs(c.y-y)<=tolerance && Math.abs(c.z-z)<=tolerance) return true;
        }
        return false;
    }
    private static boolean samePosition(VoxelGeometry.Cube a, VoxelGeometry.Cube b) {
        return a.x == b.x && a.y == b.y && a.z == b.z;
    }
    private static void unique(List<VoxelGeometry.Cube> cubes, String label) {
        boolean duplicate = false;
        for (int i=0;i<cubes.size();i++) for (int j=i+1;j<cubes.size();j++) {
            if (samePosition(cubes.get(i),cubes.get(j))) duplicate = true;
        }
        check(!duplicate, label + " coalesces geometrically identical grid cells, including signed zero");
    }

    private static void lineSamplesWholePath() {
        List<VoxelGeometry.Cube> cubes = VoxelGeometry.line(LINE,1,1,1,4);
        bounded(cubes,4,"whole line");
        check(near(cubes,0,0,0) && near(cubes,80,0,0), "small line budget includes both whole-path endpoints within quantization tolerance");
        boolean middle = false;
        for (VoxelGeometry.Cube c : cubes) if (c.x>16 && c.x<64) middle = true;
        check(middle, "line sample budget reaches later segment interior instead of clipping at first segment");
        List<VoxelGeometry.Cube> one = VoxelGeometry.line(new double[][]{{0,0,0},{80,0,0}},1,1,1,1);
        bounded(one,1,"one line sample");
        check(one.size()==1 && near(one,40,0,0), "single line sample represents midpoint instead of only the first endpoint");
        List<VoxelGeometry.Cube> corner = VoxelGeometry.line(new double[][]{{0,0,0},{0,0,20},{20,0,20}},1,1,1,3);
        bounded(corner,3,"line corner");
        check(near(corner,0,0,0) && near(corner,0,0,20) && near(corner,20,0,20), "arc-length sampling preserves distant corner and final segment");
        List<VoxelGeometry.Cube> repeated = VoxelGeometry.line(new double[][]{{1,2,3},{1,2,3},{1,2,3}},1,1,1,384);
        bounded(repeated,384,"degenerate line"); unique(repeated,"degenerate line");
        check(repeated.size()==1 && near(repeated,1,2,3), "zero-length repeated segments emit a single finite cell");
        List<VoxelGeometry.Cube> fade = VoxelGeometry.line(new double[][]{{1,2,3},{1,2,3}},0,1,1,256);
        bounded(fade,256,"coincident fading line"); unique(fade,"coincident fading line");
        check(fade.size()==1 && near(fade,1,2,3) && fade.get(0).alpha>=.999f,
              "coincident fade coalesces at maximum visible alpha rather than retaining the first faint sample");
    }

    private static void signedZeroAndDuplicateGridCoalescing() {
        List<VoxelGeometry.Cube> line = VoxelGeometry.line(new double[][]{{-.01,0,0},{.01,0,0}},1,1,1,32);
        bounded(line,32,"signed zero line"); unique(line,"signed zero line");
        check(line.size()==1, "sub-cell line crossing zero emits one geometric cell");
        List<VoxelGeometry.Cube> ribbon = VoxelGeometry.ribbon(new double[][]{{-.01,0,0},{.01,0,0}},
                new double[][]{{-.01,0,.01},{.01,0,.01}},1,1,32);
        bounded(ribbon,32,"signed zero ribbon"); unique(ribbon,"signed zero ribbon");
        check(ribbon.size()==1, "sub-cell ribbon crossing zero emits one geometric cell");
        List<VoxelGeometry.Cube> positive = VoxelGeometry.line(new double[][]{{.001,.001,.001},{.01,.01,.01}},1,1,1,32);
        check(positive.size()==1, "ordinary same-grid line coalesces repeated samples");
    }

    private static void budgetsAndFiniteOutput() {
        int[] budgets = {Integer.MIN_VALUE,-1,0,1,2,3,8,17,64,383,384,385,Integer.MAX_VALUE};
        for (int budget : budgets) {
            List<VoxelGeometry.Cube> line = VoxelGeometry.line(new double[][]{{-256,-256,-256},{0,256,0},{256,-256,256}},-2,2,128,budget);
            List<VoxelGeometry.Cube> sphere = VoxelGeometry.sphere(256,-256,256,128,2,budget);
            List<VoxelGeometry.Cube> ribbon = VoxelGeometry.ribbon(INNER,OUTER,1,1,budget);
            bounded(line,budget,"line budget " + budget);
            bounded(sphere,budget,"sphere budget " + budget);
            bounded(ribbon,budget,"ribbon budget " + budget);
            unique(line,"line budget " + budget); unique(sphere,"sphere budget " + budget); unique(ribbon,"ribbon budget " + budget);
            if (budget<=0) check(line.isEmpty() && sphere.isEmpty() && ribbon.isEmpty(), "nonpositive budgets never emit primitives");
            else check(!line.isEmpty() && !sphere.isEmpty() && !ribbon.isEmpty(), "positive representative budgets emit all primitive types");
        }
    }

    private static void sphereAndRibbonExtent() {
        List<VoxelGeometry.Cube> sphere = VoxelGeometry.sphere(0,0,0,4,1,2);
        bounded(sphere,2,"two-cell sphere");
        check(sphere.size()==2 && near(sphere,-4,0,0) && near(sphere,4,0,0), "sphere budget samples opposite extents rather than first loop cells");
        List<VoxelGeometry.Cube> ribbon = VoxelGeometry.ribbon(INNER,OUTER,1,1,16);
        bounded(ribbon,16,"whole ribbon");
        check(near(ribbon,0,0,0) && near(ribbon,80,0,8), "low ribbon budget reaches both path and width extents");
        List<VoxelGeometry.Cube> four = VoxelGeometry.ribbon(INNER,OUTER,1,1,4);
        bounded(four,4,"four-sample ribbon");
        check(near(four,0,0,0) && near(four,80,0,8), "four-sample ribbon preserves both edge bands within quantization tolerance");
        List<VoxelGeometry.Cube> wide = VoxelGeometry.ribbon(INNER,OUTER,1,1,64);
        bounded(wide,64,"ribbon with width samples");
        check(near(wide,0,0,0) && near(wide,80,0,8), "ribbon with across capacity samples both path and width extents");
        List<VoxelGeometry.Cube> one = VoxelGeometry.ribbon(new double[][]{{0,0,0},{80,0,0}},new double[][]{{0,0,8},{80,0,8}},1,1,1);
        bounded(one,1,"one ribbon sample");
        check(one.size()==1 && near(one,40,0,4), "single ribbon sample represents interior of entire surface");
        double[][] many = new double[256][3];
        for (int i=0;i<many.length;i++) many[i] = new double[]{i,0,0};
        List<VoxelGeometry.Cube> fullPath = VoxelGeometry.line(many,1,1,1,8);
        bounded(fullPath,8,"maximum vertex path");
        check(near(fullPath,0,0,0) && near(fullPath,255,0,0), "maximum accepted path samples final vertex with small budget");
    }

    private static void invalidShapesAndCoordinates() {
        double[][][] invalid = {null, new double[0][], {{0,0,0}}, {{0,0,0},null}, {{0,0,0},{1,2}},
            {{0,0,0},{0,0,0,0}}, {{0,0,0},{Double.NaN,0,0}}, {{0,0,0},{0,Double.POSITIVE_INFINITY,0}},
            {{0,0,0},{0,0,Double.NEGATIVE_INFINITY}}, {{0,0,0},{256.001,0,0}}, {{0,0,0},{-256.001,0,0}}, new double[257][3]};
        for (int i=0;i<invalid.length;i++) {
            check(VoxelGeometry.line(invalid[i],1,1,1,384).isEmpty(), "invalid line shape rejected " + i);
            check(VoxelGeometry.ribbon(invalid[i],OUTER,1,1,384).isEmpty(), "invalid ribbon inner shape rejected " + i);
            check(VoxelGeometry.ribbon(INNER,invalid[i],1,1,384).isEmpty(), "invalid ribbon outer shape rejected " + i);
        }
        check(VoxelGeometry.ribbon(new double[][]{{0,0,0},{1,0,0}},OUTER,1,1,384).isEmpty(), "ribbon mismatched path lengths rejected");
        double[] bad = {Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,256.001,-256.001};
        for (double n : bad) {
            check(VoxelGeometry.sphere(n,0,0,1,1,384).isEmpty(), "invalid sphere X rejected");
            check(VoxelGeometry.sphere(0,n,0,1,1,384).isEmpty(), "invalid sphere Y rejected");
            check(VoxelGeometry.sphere(0,0,n,1,1,384).isEmpty(), "invalid sphere Z rejected");
        }
        double[] badWidth = {Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1,0,128.001};
        for (double width : badWidth) check(VoxelGeometry.line(LINE,1,1,width,384).isEmpty(), "invalid line width rejected");
        double[] badRadius = {Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1,0,.001,128.001};
        for (double radius : badRadius) check(VoxelGeometry.sphere(0,0,0,radius,1,384).isEmpty(), "invalid sphere radius rejected");
        List<VoxelGeometry.Cube> tinyWidth = VoxelGeometry.line(LINE,1,1,Double.MIN_VALUE,16);
        bounded(tinyWidth,16,"tiny positive width");
        check(!tinyWidth.isEmpty(), "tiny finite positive line width remains safely quantized");
    }

    private static void opacityClampsAndInvalidOpacity() {
        double[] absent = {-1,0,.003,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY};
        for (double opacity : absent) {
            check(VoxelGeometry.line(LINE,opacity,opacity,1,32).isEmpty(), "absent line opacity emits no cubes");
            check(VoxelGeometry.ribbon(INNER,OUTER,opacity,opacity,32).isEmpty(), "absent ribbon opacity emits no cubes");
            check(VoxelGeometry.sphere(0,0,0,2,opacity,32).isEmpty(), "absent sphere opacity emits no cubes");
        }
        List<VoxelGeometry.Cube> fadeLine = VoxelGeometry.line(LINE,Double.NaN,8,1,16);
        List<VoxelGeometry.Cube> fadeRibbon = VoxelGeometry.ribbon(INNER,OUTER,Double.NEGATIVE_INFINITY,8,16);
        bounded(fadeLine,16,"sanitized line fade"); bounded(fadeRibbon,16,"sanitized ribbon fade");
        check(!fadeLine.isEmpty() && !fadeRibbon.isEmpty(), "invalid fade endpoint sanitizes to zero without poisoning finite endpoint");
        for (VoxelGeometry.Cube c : VoxelGeometry.sphere(0,0,0,2,8,16)) check(c.alpha==1, "sphere opacity above one clamps before float conversion");
    }

    public static void main(String[] args) {
        run("whole line", VoxelGeometryChecks::lineSamplesWholePath);
        run("signed zero coalescing", VoxelGeometryChecks::signedZeroAndDuplicateGridCoalescing);
        run("budgets", VoxelGeometryChecks::budgetsAndFiniteOutput);
        run("shape extents", VoxelGeometryChecks::sphereAndRibbonExtent);
        run("invalid shapes", VoxelGeometryChecks::invalidShapesAndCoordinates);
        run("opacity", VoxelGeometryChecks::opacityClampsAndInvalidOpacity);
        for (String failure : failures) System.err.println("FAIL: " + failure);
        System.out.println("VoxelGeometryChecks: " + assertions + " assertions, " + groups + " groups, " + failures.size() + " failures");
        if (!failures.isEmpty()) throw new AssertionError("VoxelGeometry failures: " + failures.size());
    }
}
