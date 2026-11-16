package village.immersion;

import net.minecraftforge.common.MinecraftForge;

public final class ClientSetup extends CommonSetup {
    @Override public void client() {
        MinecraftForge.EVENT_BUS.register(new SoundFx());
        MinecraftForge.EVENT_BUS.register(new CompassBar());
        MinecraftForge.EVENT_BUS.register(new MovementFx());
    }
}
