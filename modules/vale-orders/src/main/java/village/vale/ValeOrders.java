package village.vale;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = ValeOrders.ID, name = "Vale Orders", version = "0.3.10", acceptedMinecraftVersions = "[1.12.2]")
public final class ValeOrders {
    @net.minecraftforge.fml.common.SidedProxy(clientSide="village.vale.ClientProxy", serverSide="village.vale.CommonProxy")
    public static CommonProxy proxy;
    public static final String ID = "valeorders";
    public static int skillCooldown = 12;
    public static int skillCost = 25;
    public static boolean pvpAbilities = false;
    public static boolean fieldRogueSpawns = true;
    public static boolean canonicalEquipmentCatalog = true;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Configuration cfg = new Configuration(event.getSuggestedConfigurationFile());
        cfg.load();
        skillCooldown = cfg.getInt("skillCooldownSeconds", "combat", 12, 3, 120, "Shared order skill cooldown; saved across relogging and death.");
        skillCost = cfg.getInt("skillResolveCost", "combat", 25, 1, 100, "Resolve cost for an order skill.");
        pvpAbilities = cfg.getBoolean("allowAbilityPvP", "combat", false, "Ability attacks must also pass server PvP and team checks.");
        fieldRogueSpawns = cfg.getBoolean("fieldRogueSpawns", "adventure", true, "Rare vanilla-rule night patrols in forests, hills and swamps; no structures or block changes.");
        canonicalEquipmentCatalog = cfg.getBoolean("canonicalEquipmentCatalog", "adventure", true, "Retire overlapping VariedCommodities/CustomNPC combat props from recipes, creative/JEI and player combat. Keep their registry IDs for legacy data. Restart to change.");
        if (cfg.hasChanged()) cfg.save();
        village.vale.culinary.Culinary.registerTiles();
        MinecraftForge.EVENT_BUS.register(new Combat());
        MinecraftForge.EVENT_BUS.register(new Progression());
        MinecraftForge.EVENT_BUS.register(new Adventures());
        MinecraftForge.EVENT_BUS.register(new AcademyEvents());
        Bosses.register(this);
        Adventures.register(this);
        village.vale.arsenal.Arsenal.registerEntities(this);
        MinecraftForge.EVENT_BUS.register(new village.vale.arsenal.ArsenalCrafting());
        MinecraftForge.EVENT_BUS.register(new MigrationClaims());
        village.vale.arsenal.Scrolls.registerEntities(this);
        HudNetwork.register();
        AbilityNetwork.register();
        OriginalArmorRuntime.initialize();
        OriginalArmorLightEntity.register(this);
        proxy.preInit();
    }

    @Mod.EventHandler public void init(net.minecraftforge.fml.common.event.FMLInitializationEvent event) { Adventures.spawns(); }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        CanonicalCatalog.finish();
        OwnedArmor.initialize();
        if (Boolean.getBoolean("valeorders.selftest")) Diagnostics.verify();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new ValeCommand());
        event.registerServerCommand(new AcademyCommand());
        event.registerServerCommand(new MigrationLoot.Command());
    }
}
