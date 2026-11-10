package village.residents;

import java.io.File;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import org.apache.logging.log4j.Logger;

@Mod(modid = ValeResidents.MODID, name = "Vale Residents", version = "0.2.0",
     acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:valeorders@[0.3.0,0.4.0)")
public final class ValeResidents {
    public static final String MODID = "valeresidents";
    @Mod.Instance(MODID) public static ValeResidents INSTANCE;
    @SidedProxy(clientSide = "village.residents.client.ClientProxy", serverSide = "village.residents.CommonProxy")
    public static CommonProxy proxy;
    public static Logger LOG;
    public static boolean autoProvision = true;
    public static int maxResidents = ResidentSlots.HARD_LIMIT;
    public static File authoringDirectory;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        LOG = event.getModLog();
        authoringDirectory = new File(event.getModConfigurationDirectory(), MODID);
        Configuration config = new Configuration(event.getSuggestedConfigurationFile());
        config.load();
        autoProvision = config.getBoolean("autoProvisionOverworld", "residents", true,
            "Provision up to three original residents near safe loaded overworld spawn ground. No legacy NPC data is migrated.");
        maxResidents = config.getInt("maxResidents", "residents", 3, 0, 3,
            "Maximum provisioned residents, hard capped at three. Reserved unloaded/missing UUID slots are never silently replaced.");
        if (config.hasChanged()) config.save();
        EntityRegistry.registerModEntity(new ResourceLocation(MODID, "resident"), EntityResident.class,
            "resident", 0, INSTANCE, 64, 3, true);
        proxy.init();
    }

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new ResidentEvents());
    }

    @Mod.EventHandler public void starting(FMLServerStartingEvent event) {
        LOG.info(DialogueBook.reload());
        event.registerServerCommand(new ResidentsCommand());
        event.registerServerCommand(new ResidentChooseCommand());
    }
    @Mod.EventHandler public void stopping(FMLServerStoppingEvent event) { DialogueSessions.clear(); }
}
