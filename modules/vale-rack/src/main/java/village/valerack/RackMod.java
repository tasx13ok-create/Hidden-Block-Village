package village.valerack;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;

@Mod(modid=RackMod.ID,name="Vale Rack",version=RackMod.VERSION,dependencies="required-after:valeorders",acceptableRemoteVersions="[0.1.1]")
public final class RackMod {
    public static final String ID="valerack",VERSION="0.1.1";
    @Mod.Instance(ID) public static RackMod INSTANCE;
    @SidedProxy(clientSide="village.valerack.client.ClientProxy",serverSide="village.valerack.CommonProxy") public static CommonProxy PROXY;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){
        RackData.register();RackNetwork.initialize();MinecraftForge.EVENT_BUS.register(new RackLifecycle());
        NetworkRegistry.INSTANCE.registerGuiHandler(INSTANCE,new RackGuiHandler());PROXY.initialize();
    }
}
