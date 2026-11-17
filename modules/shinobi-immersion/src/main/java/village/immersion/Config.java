package village.immersion;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

final class Config {
    private Config() {}
    static boolean waterWalk = true, wallRun = true, underwaterSound = true, caveEcho = true, compass = true, ambience = true, combatLayers = true;
    static float ambienceVolume = 0.5f;
    static boolean perfectDodge = true, reactions = true, bossCues = true;
    static int perfectRefund = 3;
    static boolean hitStop = true, guardBreak = true, stagger = true, lowHealth = true, exhaustion = true;
    static boolean ledgeGrab = true, landingRoll = true, canopyRun = true, weather = true, footsteps = true, villageLife = true, squad = true, sparring = true;
    static float hitStopDamage = 6f;
    static int ledgeCost = 1;
    static int waterCost = 1, wallCost = 2, sideRunCost = 2, wallJumpCost = 1, maxWallJumps = 3;
    static boolean wallSideRun = true, wallJump = true, wallSlide = true, cameraTilt = true, dodgeFeel = true, dodgeHop = true;

    static void load(File dir) {
        Configuration c = new Configuration(new File(dir, "shinobi-immersion.cfg"));
        c.load();
        waterWalk = c.getBoolean("waterWalk", "movement", true, "Walk on water while not sneaking (sneak to dive). Spends chakra each second.");
        waterCost = c.getInt("waterWalkCost", "movement", 1, 0, 20, "Chakra units per second of water-walking.");
        wallRun = c.getBoolean("wallRun", "movement", true, "Run up a wall you face: sprint or dodge into it and hold jump. Pulls you onto the ledge at the top.");
        wallCost = c.getInt("wallRunCost", "movement", 2, 0, 20, "Chakra units per run up a wall.");
        wallSideRun = c.getBoolean("wallSideRun", "movement", true, "Run along a wall beside you: jump toward it at speed while holding forward. Lasts up to 2 seconds.");
        sideRunCost = c.getInt("wallSideRunCost", "movement", 2, 0, 20, "Chakra units to start a run along a wall, plus 1 per second.");
        wallJump = c.getBoolean("wallJump", "movement", true, "Press jump on a wall (or just after leaving it) to kick off it.");
        wallJumpCost = c.getInt("wallJumpCost", "movement", 1, 0, 20, "Chakra units per wall jump.");
        maxWallJumps = c.getInt("maxWallJumps", "movement", 3, 0, 10, "Wall jumps allowed before you land again.");
        wallSlide = c.getBoolean("wallSlide", "movement", true, "Falling while pressing into a wall slows you to a slide and limits fall damage.");
        underwaterSound = c.getBoolean("underwaterSound", "sound", true, "Muffle and lower sounds while your head is underwater.");
        caveEcho = c.getBoolean("caveEcho", "sound", true, "A soft delayed echo on sounds in enclosed spaces.");
        ambience = c.getBoolean("ambience", "sound", true, "Quiet biome and time-of-day ambience: forest birds, night rustle, cave drips, water, high wind.");
        ambienceVolume = c.getFloat("ambienceVolume", "sound", 0.5f, 0f, 1f, "Ambience loudness.");
        combatLayers = c.getBoolean("combatLayers", "sound", true, "Vary the pitch of NarutoMod and Core combat sounds and add a soft low tail to loud ones.");
        perfectDodge = c.getBoolean("perfectDodge", "combat", true, "A hit avoided at the start of a dodge slows the attacker, hastes you and refunds chakra.");
        perfectRefund = c.getInt("perfectDodgeRefund", "combat", 3, 0, 20, "Chakra units refunded on a perfect dodge.");
        reactions = c.getBoolean("elementalReactions", "combat", true, "Fire+wet steam, lightning+wet chains, wind+burning spreads fire, earth+burning snuffs it.");
        bossCues = c.getBoolean("bossCues", "combat", true, "Red warning ring when a boss attacks; roar and harmless shockwave at 65% and 30% health.");
        hitStop = c.getBoolean("hitStop", "combat", true, "Heavy and finishing blows briefly hold the camera (2-3 frames) and kick it.");
        hitStopDamage = c.getFloat("hitStopDamage", "combat", 6f, 1f, 100f, "Damage (after armor) that counts as a heavy blow.");
        guardBreak = c.getBoolean("guardBreak", "combat", true, "Five blocked hits within three seconds break a raised shield for three seconds.");
        stagger = c.getBoolean("stagger", "combat", true, "Four hits on a mob within two seconds stagger it briefly (bosses need eight).");
        lowHealth = c.getBoolean("lowHealth", "combat", true, "Below 25% health: a quickening heartbeat, a dark edge and slightly muffled world sound.");
        exhaustion = c.getBoolean("chakraExhaustion", "combat", true, "Below 5% chakra you move a little slower and breathe hard until you recover to 15%.");
        ledgeGrab = c.getBoolean("ledgeGrab", "movement", true, "Jump at a ledge just out of reach and hold jump to pull up onto it.");
        ledgeCost = c.getInt("ledgeGrabCost", "movement", 1, 0, 20, "Chakra units per ledge grab.");
        landingRoll = c.getBoolean("landingRoll", "movement", true, "Sneak as you land a 3.5-24 block fall to roll forward and take much less fall damage.");
        canopyRun = c.getBoolean("canopyRun", "movement", true, "Sprinting on leaves or rooftops (stairs and slabs) is 15% faster.");
        weather = c.getBoolean("weather", "world", true, "Rain widens lightning chains, wind carries dust, and rain or storms shorten compass player range.");
        footsteps = c.getBoolean("footsteps", "sound", true, "Softer steps on grass, sand and snow; gravel crunch; splashes in shallow water.");
        villageLife = c.getBoolean("villageLife", "sound", true, "Distant chatter, smithing and doors by day near villagers or NPCs.");
        squad = c.getBoolean("squads", "social", true, "/squad: teammates on the compass at any distance and a squad health panel.");
        sparring = c.getBoolean("sparring", "social", true, "/spar: agreed duels where nobody drops below one health. Needs PvP on.");
        cameraTilt = c.getBoolean("cameraTilt", "feel", true, "Lean the camera away from a wall you run along, and into side dodges.");
        dodgeFeel = c.getBoolean("dodgeFeel", "feel", true, "Instant feel when you press Core's dodge key (lean, view punch, whoosh) and an afterimage trail once it lands.");
        dodgeHop = c.getBoolean("dodgeHop", "feel", true, "Jumping right after a ground dodge keeps the dodge's momentum.");
        compass = c.getBoolean("compassBar", "hud", true, "Compass strip at the top of the screen with directions, spawn and nearby players.");
        if (c.hasChanged()) c.save();
    }
}
