package village.immersion;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ShinobiImmersion.ID, name = "Shinobi Immersion", version = "0.1.0", acceptedMinecraftVersions = "[1.12.2]", dependencies = "after:valeorders;after:narutomod")
public final class ShinobiImmersion {
    public static final String ID = "shinobiimmersion";
    @SidedProxy(clientSide = "village.immersion.ClientSetup", serverSide = "village.immersion.CommonSetup")
    public static CommonSetup proxy;

    @Mod.EventHandler public void pre(FMLPreInitializationEvent e) {
        Config.load(e.getModConfigurationDirectory());
        MinecraftForge.EVENT_BUS.register(new Movement());
        proxy.client();
    }
}
