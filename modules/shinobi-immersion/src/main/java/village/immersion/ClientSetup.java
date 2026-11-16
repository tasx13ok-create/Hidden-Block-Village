package village.immersion;

import net.minecraftforge.common.MinecraftForge;

public final class ClientSetup extends CommonSetup {
    @Override public void cue(int kind) { net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> CueFx.perfect()); }
    @Override public void client() {
        MinecraftForge.EVENT_BUS.register(new SoundFx());
        MinecraftForge.EVENT_BUS.register(new CompassBar());
        MinecraftForge.EVENT_BUS.register(new MovementFx());
        MinecraftForge.EVENT_BUS.register(new AmbienceFx());
        MinecraftForge.EVENT_BUS.register(new CueFx());
    }
}
