package village.bijuu;

import java.io.File;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Steam Dash Punch: wear the full Kurama set while carrying a crate-issued Kokuo piece (or the
 * reverse) and press the dash key at an enemy. A short steam telegraph, a blink-speed dash that
 * tunnels through soft terrain, a hit-stop impact, then a ragdoll launch that smashes through walls.
 * A second press while they fly spikes them into the ground. Lives in the Bijuu Armor jar and uses
 * its resonance energy, cooldown rules and team/PvP checks.
 */
@Mod(modid = SteamDash.ID, name = "Bijuu Steam Dash", version = SteamDash.VERSION, acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:hvbijuu")
public final class SteamDash {
    public static final String ID = "hvbijuu_steamdash", VERSION = "2.0.0";

    @SidedProxy(clientSide = "village.bijuu.DashClient$Proxy", serverSide = "village.bijuu.SteamDash$Proxy")
    public static Proxy proxy;
    static SimpleNetworkWrapper NET;

    // Settings (hvbijuu_steamdash.cfg).
    static int cost = 50, spikeCost = 20, springCost = 10, springCooldownTicks = 20, cooldownSeconds = 25, blockBudget = 450, craterRadius = 8;
    static int relayCost = 15, moltenSeconds = 8, moltenMax = 160;
    static float punchDamage = 16, spikeDamage = 10, craterDamage = 8, wallDamage = 6, maxHardness = 3.0f;
    static float barrageDamage = 2, relayDamage = 6, pierceDamageShare = 0.75f;
    static boolean breakBlocks = true, dropBlocks = false, crackWalls = true, letterbox = true, cameraEffects = true, impactFrames = true;
    static boolean overcharge = true, afterimageBarrage = true, juggle = true, moltenScars = true, timeBubble = true, delayedThunder = true;
    static boolean cinematicCamera = true, hideHudInCutscene = true, groundRipple = true;

    static SoundEvent CHARGE, BLINK, IMPACT, LAUNCH, CRASH, HISS, JAB, TIMEWARP, SIZZLE, STAB, RELAY;

    public static class Proxy {
        public void preInit() {}
        public void fx(DashNet.Fx m) {}
    }

    @Mod.EventHandler public void pre(FMLPreInitializationEvent e) {
        config(new File(e.getModConfigurationDirectory(), "hvbijuu_steamdash.cfg"));
        NET = NetworkRegistry.INSTANCE.newSimpleChannel("hvbsteamdash");
        NET.registerMessage(DashNet.RequestHandler.class, DashNet.Request.class, 0, Side.SERVER);
        NET.registerMessage(DashNet.FxHandler.class, DashNet.Fx.class, 1, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new DashServer());
        MinecraftForge.EVENT_BUS.register(new MoltenScars());
        MinecraftForge.EVENT_BUS.register(new TimeBubble());
        proxy.preInit();
    }

    private static void config(File file) {
        Configuration c = new Configuration(file);
        c.load();
        cost = c.getInt("resonanceCost", "dash", 50, 0, 100, "Bijuu resonance spent per Steam Dash Punch.");
        spikeCost = c.getInt("spikeResonanceCost", "dash", 20, 0, 100, "Extra resonance for the follow-up spike.");
        springCost = c.getInt("springboardResonanceCost", "dash", 10, 0, 100, "Resonance for a springboard off a wall (dash key while wall-running with no target in sight).");
        springCooldownTicks = c.getInt("springboardCooldownTicks", "dash", 20, 0, 1200, "Ticks between springboards.");
        cooldownSeconds = c.getInt("cooldownSeconds", "dash", 25, 0, 600, "Cooldown between dashes.");
        punchDamage = c.getFloat("punchDamage", "dash", 16, 0, 200, "Damage of the dash punch.");
        spikeDamage = c.getFloat("spikeDamage", "dash", 10, 0, 200, "Damage of the follow-up spike.");
        craterDamage = c.getFloat("craterDamage", "dash", 8, 0, 200, "Damage when the spiked target hits the ground.");
        wallDamage = c.getFloat("wallDamage", "dash", 6, 0, 200, "Damage when a launched target hits terrain it can't break.");
        breakBlocks = c.getBoolean("breakBlocks", "terrain", true, "Tunnel and crater through soft terrain. Claim mods and Dungeon Guard are asked for every block; protected blocks act as walls.");
        dropBlocks = c.getBoolean("dropBlocks", "terrain", false, "Drop smashed blocks as items (off avoids turning the move into a mining tool).");
        crackWalls = c.getBoolean("crackWalls", "terrain", true, "Springboards and wall splatters crack stone into cobblestone and cracked stone bricks (permanent).");
        maxHardness = c.getFloat("maxHardness", "terrain", 3.0f, 0f, 50f, "Hardest block that can be smashed (stone 1.5, ores 3, obsidian 50).");
        blockBudget = c.getInt("blockBudget", "terrain", 450, 0, 5000, "Most blocks one dash may smash (tunnel + crater).");
        craterRadius = c.getInt("craterRadius", "terrain", 8, 0, 12, "Spike crater radius in blocks.");
        letterbox = c.getBoolean("letterbox", "client", true, "Cinematic black bars during the move.");
        cameraEffects = c.getBoolean("cameraEffects", "client", true, "Field-of-view snap and impact camera shake.");
        relayCost = c.getInt("relayResonanceCost", "combo", 15, 0, 100, "Resonance for each juggle relay (press again while they fly; sneak + press to pin and spike instead).");
        barrageDamage = c.getFloat("barrageDamage", "combo", 2, 0, 50, "Damage of each of the four afterimage clone punches.");
        relayDamage = c.getFloat("relayDamage", "combo", 6, 0, 100, "Damage of each juggle relay punch.");
        pierceDamageShare = c.getFloat("pierceDamageShare", "combo", 0.75f, 0, 2, "Share of punch damage dealt to each body a full-charge dash passes through.");
        overcharge = c.getBoolean("overcharge", "combo", true, "Hold the key to charge: tier 2 at 0.35 s, tier 3 at 1 s (pierces a whole line, bores 5x5).");
        afterimageBarrage = c.getBoolean("afterimageBarrage", "combo", true, "The four golden trail clones each punch before the real hit.");
        juggle = c.getBoolean("juggle", "combo", true, "Press again while the target flies to blink ahead and punch them back (twice), then pin and spike.");
        moltenScars = c.getBoolean("moltenScars", "terrain", true, "Tunnels and craters glow as magma, vent steam that pops people into the air, then cool to cobblestone and andesite.");
        moltenSeconds = c.getInt("moltenSeconds", "terrain", 8, 1, 120, "How long scars stay molten.");
        moltenMax = c.getInt("moltenMax", "terrain", 160, 0, 1000, "Most blocks one dash can turn molten.");
        timeBubble = c.getBoolean("timeBubble", "world", true, "Bullet time: for about a second around the hit, nearby mobs and projectiles run at quarter speed (never players).");
        delayedThunder = c.getBoolean("delayedThunder", "client", true, "Far-away players hear the thunder late, by the speed of sound.");
        cinematicCamera = c.getBoolean("cinematicCamera", "client", true, "Cut-scene camera shots for the attacker: front angle, dolly zoom, hit-stop orbit, chase cam, overhead crater shot.");
        hideHudInCutscene = c.getBoolean("hideHudInCutscene", "client", true, "Hide the HUD while the cut-scene camera runs.");
        groundRipple = c.getBoolean("groundRipple", "client", true, "A shock ripple of rising and falling blocks rolls out from big impacts (visual only).");
        impactFrames = c.getBoolean("impactFrames", "client", true, "White flash and ink-silhouette frames on contact (turn off if flashing images bother you).");
        if (c.hasChanged()) c.save();
    }

    @SubscribeEvent public void sounds(RegistryEvent.Register<SoundEvent> e) {
        CHARGE = sound(e, "charge"); BLINK = sound(e, "blink"); IMPACT = sound(e, "impact");
        LAUNCH = sound(e, "launch"); CRASH = sound(e, "crash"); HISS = sound(e, "hiss");
        JAB = sound(e, "jab"); TIMEWARP = sound(e, "timewarp"); SIZZLE = sound(e, "sizzle"); STAB = sound(e, "stab"); RELAY = sound(e, "relay");
    }

    private static SoundEvent sound(RegistryEvent.Register<SoundEvent> e, String name) {
        ResourceLocation id = new ResourceLocation(BijuuArmor.ID, "steamdash." + name);
        SoundEvent s = new SoundEvent(id).setRegistryName(id);
        e.getRegistry().register(s);
        return s;
    }
}
