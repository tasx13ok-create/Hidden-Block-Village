package village.fieldhud;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod(modid=FieldHud.ID, name="Field HUD", version="0.15.0", clientSideOnly=true, acceptableRemoteVersions="*")
public final class FieldHud {
    public static final String ID="fieldhud";
    @Mod.EventHandler public void init(FMLPreInitializationEvent event) {
        if (event.getSide() == Side.CLIENT) ClientHud.start(event.getModConfigurationDirectory());
    }
}
