package village.immersion;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ShinobiImmersion.ID, name = "Shinobi Immersion", version = "0.5.0", acceptedMinecraftVersions = "[1.12.2]", dependencies = "after:valeorders;after:narutomod")
public final class ShinobiImmersion {
    public static final String ID = "shinobiimmersion";
    @SidedProxy(clientSide = "village.immersion.ClientSetup", serverSide = "village.immersion.CommonSetup")
    public static CommonSetup proxy;

    @Mod.EventHandler public void serverStart(net.minecraftforge.fml.common.event.FMLServerStartingEvent e) {
        e.registerServerCommand(new Squad.Command());
        e.registerServerCommand(new Spar.Command());
    }

    @Mod.EventHandler public void pre(FMLPreInitializationEvent e) {
        Config.load(e.getModConfigurationDirectory());
        MinecraftForge.EVENT_BUS.register(new Movement());
        MinecraftForge.EVENT_BUS.register(new Combat());
        MinecraftForge.EVENT_BUS.register(new Feel());
        MinecraftForge.EVENT_BUS.register(new Squad());
        MinecraftForge.EVENT_BUS.register(new Spar());
        MinecraftForge.EVENT_BUS.register(new Parkour());
        Net.register();
        proxy.client();
    }
}
