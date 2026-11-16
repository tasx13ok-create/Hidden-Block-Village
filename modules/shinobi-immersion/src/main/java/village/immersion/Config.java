package village.immersion;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

final class Config {
    private Config() {}
    static boolean waterWalk = true, wallRun = true, underwaterSound = true, caveEcho = true, compass = true, ambience = true, combatLayers = true;
    static float ambienceVolume = 0.5f;
    static int waterCost = 1, wallCost = 2;

    static void load(File dir) {
        Configuration c = new Configuration(new File(dir, "shinobi-immersion.cfg"));
        c.load();
        waterWalk = c.getBoolean("waterWalk", "movement", true, "Walk on water while not sneaking (sneak to dive). Spends chakra each second.");
        waterCost = c.getInt("waterWalkCost", "movement", 1, 0, 20, "Chakra units per second of water-walking.");
        wallRun = c.getBoolean("wallRun", "movement", true, "Sprint-jump into a wall and hold jump to run up it briefly.");
        wallCost = c.getInt("wallRunCost", "movement", 2, 0, 20, "Chakra units per wall-run.");
        underwaterSound = c.getBoolean("underwaterSound", "sound", true, "Muffle and lower sounds while your head is underwater.");
        caveEcho = c.getBoolean("caveEcho", "sound", true, "A soft delayed echo on sounds in enclosed spaces.");
        ambience = c.getBoolean("ambience", "sound", true, "Quiet biome and time-of-day ambience: forest birds, night rustle, cave drips, water, high wind.");
        ambienceVolume = c.getFloat("ambienceVolume", "sound", 0.5f, 0f, 1f, "Ambience loudness.");
        combatLayers = c.getBoolean("combatLayers", "sound", true, "Vary the pitch of NarutoMod and Core combat sounds and add a soft low tail to loud ones.");
        compass = c.getBoolean("compassBar", "hud", true, "Compass strip at the top of the screen with directions, spawn and nearby players.");
        if (c.hasChanged()) c.save();
    }
}
