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
        check(ImmersionMath.ambience(true, true, true, true, 40) == 3, "underground wins");
        check(ImmersionMath.ambience(false, false, false, true, 130) == 5, "high wind");
        check(ImmersionMath.ambience(false, true, true, true, 64) == 4, "water near shore");
        check(ImmersionMath.ambience(false, false, true, true, 64) == 1 && ImmersionMath.ambience(false, false, true, false, 64) == 2, "forest day/night");
        check(ImmersionMath.ambience(false, false, false, true, 64) == 0, "plain daytime is quiet");
        for (int l = 1; l <= 5; l++) check(ImmersionMath.ambienceGap(l) >= 5, "ambience is sparse");
        for (double sd = 0; sd < 1; sd += 0.1) { float v = ImmersionMath.vary(1f, sd); check(v >= 0.94f && v <= 1.06f, "pitch variation bounded"); }
        check(ImmersionMath.heavy(1f) && !ImmersionMath.heavy(0.3f), "heavy threshold");
        check(ImmersionMath.dodgeBurst(1.55, 0.2) && !ImmersionMath.dodgeBurst(0.28, 0.2) && !ImmersionMath.dodgeBurst(1.5, 1.4), "dodge burst detection");
        check(ImmersionMath.perfect(105, 100, true) && !ImmersionMath.perfect(107, 100, true) && !ImmersionMath.perfect(103, 100, false) && !ImmersionMath.perfect(103, -1, true), "perfect window");
        check(ImmersionMath.element("onFire", "", true) == ImmersionMath.FIRE, "fire damage");
        check(ImmersionMath.element("ninjutsu", "narutomod:chidori", false) == ImmersionMath.LIGHTNING, "lightning id");
        check(ImmersionMath.element("ninjutsu", "narutomod:suiton_shark", false) == ImmersionMath.WATER, "water id");
        check(ImmersionMath.element("ninjutsu", "narutomod:futon_vacuum", false) == ImmersionMath.WIND, "wind id");
        check(ImmersionMath.element("ninjutsu", "narutomod:doton_wall", false) == ImmersionMath.EARTH, "earth id");
        check(ImmersionMath.element("player", "minecraft:zombie", false) == ImmersionMath.NONE, "plain hit has no element");
        check(ImmersionMath.reaction(ImmersionMath.FIRE, true, false) == ImmersionMath.R_STEAM, "fire+wet steam");
        check(ImmersionMath.reaction(ImmersionMath.LIGHTNING, true, false) == ImmersionMath.R_CHAIN, "lightning+wet chain");
        check(ImmersionMath.reaction(ImmersionMath.WIND, false, true) == ImmersionMath.R_SPREAD, "wind+burning spread");
        check(ImmersionMath.reaction(ImmersionMath.EARTH, false, true) == ImmersionMath.R_SNUFF, "earth+burning snuff");
        check(ImmersionMath.reaction(ImmersionMath.FIRE, false, false) == ImmersionMath.R_NONE, "no reaction on dry target");
        check(ImmersionMath.reactionDamage(ImmersionMath.R_STEAM) < 1 && ImmersionMath.reactionDamage(ImmersionMath.R_CHAIN) > 1, "reaction damage");
        check(ImmersionMath.phase(100, 100) == 0 && ImmersionMath.phase(50, 100) == 1 && ImmersionMath.phase(20, 100) == 2 && ImmersionMath.phase(5, 0) == 0, "boss phases");
        System.out.println("PASS: " + checks + " immersion checks (water-walk, wall-run, sound, compass).");
    }
}
