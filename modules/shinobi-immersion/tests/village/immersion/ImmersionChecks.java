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
        check(ImmersionMath.impact(3, 6, false) == 0 && ImmersionMath.impact(6, 6, false) == 0.5f && ImmersionMath.impact(30, 6, false) == 1f && ImmersionMath.impact(1, 6, true) == 1f, "impact strength");
        check(ImmersionMath.holdMs(0) == 0 && ImmersionMath.holdMs(0.5f) >= 30 && ImmersionMath.holdMs(1f) <= 50, "hold is 2-3 frames");
        check(ImmersionMath.kick(1f, 40, 50) == 0 && ImmersionMath.kick(1f, 50, 50) > 1.5 && ImmersionMath.kick(1f, 300, 50) == 0, "kick after hold then decays");
        check(ImmersionMath.pressure(3, 100, 130, 60) == 4 && ImmersionMath.pressure(3, 100, 200, 60) == 1 && ImmersionMath.pressure(0, -1, 5, 60) == 1, "pressure builds and resets");
        check(ImmersionMath.staggerHits(true) > ImmersionMath.staggerHits(false), "bosses resist stagger");
        check(ImmersionMath.danger(20, 20) == 0 && ImmersionMath.danger(5, 20) == 0 && ImmersionMath.danger(4.9f, 20) > 0 && ImmersionMath.danger(0.5f, 20) > 0.9f && ImmersionMath.danger(0, 20) == 0, "danger range");
        check(ImmersionMath.heartbeatGap(0) == 0 && ImmersionMath.heartbeatGap(0.2f) > ImmersionMath.heartbeatGap(1f) && ImmersionMath.heartbeatGap(1f) == 16, "heartbeat speeds up");
        check(ImmersionMath.dangerMuffle(0) == 1f && ImmersionMath.dangerMuffle(1) >= 0.7f, "muffle gentle");
        check(!ImmersionMath.exhausted(false, 10, 100) && ImmersionMath.exhausted(false, 4, 100) && ImmersionMath.exhausted(true, 10, 100) && !ImmersionMath.exhausted(true, 16, 100), "exhaustion hysteresis");
        check(!ImmersionMath.exhausted(false, Double.NaN, 100) && !ImmersionMath.exhausted(false, 0, 0), "no chakra data is not exhaustion");
        check(!ImmersionMath.ledgeReach(1.0) && ImmersionMath.ledgeReach(2.0) && !ImmersionMath.ledgeReach(3.0), "ledge reach");
        for (double h = 1.2; h <= 2.6; h += 0.2) { double v = ImmersionMath.ledgeLift(h), rise = 0; for (double m = v; m > 0; m = (m - 0.08) * 0.98) rise += m; check(rise >= h + 0.1, "ledge lift clears " + h); check(v <= 0.8, "ledge lift bounded"); }
        check(ImmersionMath.roll(true, 6) && !ImmersionMath.roll(false, 6) && !ImmersionMath.roll(true, 3) && !ImmersionMath.roll(true, 40), "roll window");
        check(ImmersionMath.rollDamage(5) == 0.4f && ImmersionMath.rollDamage(20) <= 0.7f && ImmersionMath.rollDamage(20) > 0.4f, "roll damage");
        check(ImmersionMath.compassRange(false, false) == 128 && ImmersionMath.compassRange(true, false) == 64 && ImmersionMath.compassRange(true, true) == 32, "fog shortens compass");
        check(ImmersionMath.chain(true)[0] > ImmersionMath.chain(false)[0] && ImmersionMath.chain(true)[1] > ImmersionMath.chain(false)[1], "rain widens chains");
        check(Math.abs(ImmersionMath.windAngle(100) - ImmersionMath.windAngle(120)) < 0.01, "wind turns slowly");
        check(ImmersionMath.isStep("block.grass.step") && !ImmersionMath.isStep("block.grass.break"), "step detection");
        check(ImmersionMath.stepVolume("block.grass.step") < 1 && ImmersionMath.stepVolume("block.stone.step") == 1, "soft ground quieter");
        check(ImmersionMath.village(1, true, 0.1) == 0 && ImmersionMath.village(3, false, 0.1) == 0 && ImmersionMath.village(3, true, 0.1) == 1 && ImmersionMath.village(3, true, 0.7) == 2 && ImmersionMath.village(3, true, 0.9) == 3, "village cues");
        check(ImmersionMath.villageGap(1) == 0 && ImmersionMath.villageGap(20) >= 5, "village sparse");
        check(ImmersionMath.pips(20, 20) == 5 && ImmersionMath.pips(0.5f, 20) == 1 && ImmersionMath.pips(0, 20) == 0, "squad pips");
        check(ImmersionMath.downed(3, 20) && !ImmersionMath.downed(10, 20) && !ImmersionMath.downed(0, 20), "downed state");
        float[] cap = ImmersionMath.sparCap(30, 12); check(cap[0] == 11 && cap[1] == 1, "spar stops at one health");
        cap = ImmersionMath.sparCap(4, 12); check(cap[0] == 4 && cap[1] == 0, "spar normal hit");
        cap = ImmersionMath.sparCap(4, 0.8f); check(cap[0] == 0 && cap[1] == 1, "spar never kills a nearly downed player");
        // ---- wall movement and dodge feel ----
        check(Math.abs(ParkourMath.facing(0, 1, 0, -1) - 1) < 1e-9 && Math.abs(ParkourMath.facing(1, 0, 0, -1)) < 1e-9, "facing into vs along a wall");
        check(ParkourMath.side(0, 1, 1, 0) == 1 && ParkourMath.side(0, 1, -1, 0) == -1 && ParkourMath.side(0, 1, 0, -1) == 0, "wall side from look and normal");
        check(Math.abs(ParkourMath.along(0.3, 0.4, 1, 0) - 0.4) < 1e-9, "along-wall speed drops the normal part");
        check(ParkourMath.choose(true, true, true, false, 0.9, 0, false, false) == ParkourMath.RUN_UP, "facing a wall and jumping runs up it");
        check(ParkourMath.choose(true, true, false, true, 0.1, 0.25, false, false) == ParkourMath.RUN_SIDE, "a buffered jump alongside a wall runs along it");
        check(ParkourMath.choose(true, true, true, false, 0.1, 0.05, false, false) == ParkourMath.NONE, "too slow along the wall does not side-run");
        check(ParkourMath.choose(true, false, false, false, 0.9, 0, true, true) == ParkourMath.SLIDE, "falling while pressing into a wall slides");
        check(ParkourMath.choose(false, true, true, true, 0.9, 0.3, false, true) == ParkourMath.NONE, "nothing starts on the ground");
        check(ParkourMath.choose(true, true, false, false, 0.9, 0.3, false, false) == ParkourMath.NONE, "no jump intent, no wall run");
        double prevLift = 1;
        for (int t = 0; t < ParkourMath.RUN_UP_TICKS; t++) { double l = ParkourMath.runUpLift(t); check(l <= prevLift && l > 0.15, "run-up lift eases but keeps climbing"); prevLift = l; }
        check(ParkourMath.runUpLift(ParkourMath.RUN_UP_TICKS) == 0, "run-up ends");
        double climb = 0; for (int t = 0; t < ParkourMath.RUN_UP_TICKS; t++) climb += ParkourMath.runUpLift(t);
        check(climb > 4 && climb < 7, "a full run-up climbs about five blocks (" + climb + ")");
        check(ParkourMath.sideFloor(0) > 0 && ParkourMath.sideFloor(20) < 0 && ParkourMath.sideFloor(ParkourMath.RUN_SIDE_TICKS) >= -0.1, "side run rises a little then sinks gently");
        double[] g = ParkourMath.alongWall(0.1, 0.05, 1, 0, 0, 1, 0.3);
        check(Math.abs(g[1]) >= 0.3 - 1e-9 && g[0] < 0, "side run keeps at least the minimum along-wall speed and grips the wall");
        double[] g0 = ParkourMath.alongWall(0, 0, 1, 0, 0, 1, 0.3);
        check(Math.abs(g0[1] - 0.3) < 1e-9, "a standing start runs the way the player looks");
        double[] j = ParkourMath.wallJump(ParkourMath.RUN_SIDE, 1, 0, 0, 1);
        check(j[0] > 0.3 && j[1] > 0.4 && j[2] > 0.2, "wall jump pushes away, up and onward");
        check(ParkourMath.wallJump(ParkourMath.RUN_UP, 0, -1, 0, 1)[2] < -0.4, "run-up wall jump kicks straight back off the wall");
        check(ParkourMath.sameWall(1, 0, 0.95, 0.31) && !ParkourMath.sameWall(1, 0, -1, 0), "same-wall detection");
        check(ParkourMath.tilt(1, 0, -1) == 0 && ParkourMath.tilt(1, 5, -1) == 8 && ParkourMath.tilt(-1, 10, -1) == -8 && ParkourMath.tilt(1, 10, 3) == 4 && ParkourMath.tilt(1, 10, 6) == 0, "camera tilt eases in and out");
        check(ParkourMath.fov(0, false) > 1.08f && ParkourMath.fov(300, false) == 1f && ParkourMath.fov(-1, true) == 1.05f, "fov punch and side-run widening");
        check(ParkourMath.dodgeDirection(1, 0) == 0 && ParkourMath.dodgeDirection(0, 0) == 1 && ParkourMath.dodgeDirection(0, 1) == 2 && ParkourMath.dodgeDirection(0.5f, -1) == 3, "dodge direction matches Core");
        check(ParkourMath.dodgeRoll(2, 110) < -4 && ParkourMath.dodgeRoll(3, 110) > 4 && ParkourMath.dodgeRoll(0, 110) == 0 && ParkourMath.dodgeRoll(2, 300) == 0, "dodge roll leans into side dodges only");
        check(ParkourMath.hopSpeed(1.55, 0.2) > 1.0 && ParkourMath.hopSpeed(1.55, 1.2) == 1.2 && ParkourMath.hopSpeed(3, 0) == 1.1, "dodge hop keeps bounded momentum");
        System.out.println("PASS: " + checks + " immersion checks (movement, sound, combat, squad, sparring).");
    }
}
