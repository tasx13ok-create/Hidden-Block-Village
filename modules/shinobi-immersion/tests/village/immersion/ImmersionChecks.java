package village.immersion;

public final class ImmersionChecks {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] a) {
        check(ImmersionMath.waterWalk(true, false, false, true, false, -0.1), "walks on the surface");
        check(!ImmersionMath.waterWalk(true, true, false, true, false, -0.1), "sneak dives");
        check(!ImmersionMath.waterWalk(true, false, false, true, true, -0.1), "not when submerged");
        check(!ImmersionMath.waterWalk(true, false, false, true, false, 0.4), "not while jumping up");
        check(!ImmersionMath.waterWalk(false, false, false, true, false, -0.1), "config off");
        check(ImmersionMath.wallRun(true, true, false, true, 0, true), "wall-run starts");
        check(!ImmersionMath.wallRun(true, false, false, true, 0, true), "needs sprint");
        check(!ImmersionMath.wallRun(true, true, true, true, 0, true), "needs to be airborne");
        check(!ImmersionMath.wallRun(true, true, false, true, ImmersionMath.WALL_RUN_TICKS, true), "bounded duration");
        double prev = 1;
        for (int t = 0; t <= ImmersionMath.WALL_RUN_TICKS; t++) { double l = ImmersionMath.wallLift(t); check(l <= prev && l >= 0, "lift eases off"); prev = l; }
        float[] u = ImmersionMath.underwater(1, 1);
        check(u[0] < 0.6 && u[1] < 0.8 && u[1] >= 0.5, "underwater muffles");
        check(ImmersionMath.echoVolume(1, 0) == 0 && ImmersionMath.echoVolume(1, 1) < 0.35, "echo bounded");
        check(ImmersionMath.echoDelay(0) == 4 && ImmersionMath.echoDelay(1) == 10, "echo delay range");
        check(Math.abs(ImmersionMath.compassX(0, 0, 90, 160)) < 1e-9, "facing bearing is centre");
        check(Double.isNaN(ImmersionMath.compassX(180, 0, 90, 160)), "behind is off the bar");
        check(ImmersionMath.compassX(40, 0, 90, 160) > 0 && ImmersionMath.compassX(-40, 0, 90, 160) < 0, "left/right sides");
        check(Math.abs(ImmersionMath.wrap(370) - 10) < 1e-9 && Math.abs(ImmersionMath.wrap(-190) - 170) < 1e-9, "wrap");
        check(Math.abs(ImmersionMath.bearing(0, 0, 0, 10)) < 1e-9, "south (+z) is yaw 0");
        check(Math.abs(Math.abs(ImmersionMath.bearing(0, 0, 0, -10)) - 180) < 1e-9, "north is yaw 180");
        check(Math.abs(ImmersionMath.bearing(0, 0, -10, 0) - 90) < 1e-9, "west (-x) is yaw 90");
        System.out.println("PASS: " + checks + " immersion checks (water-walk, wall-run, sound, compass).");
    }
}
