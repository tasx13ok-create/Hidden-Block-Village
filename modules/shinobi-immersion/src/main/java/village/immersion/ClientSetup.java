package village.immersion;

import net.minecraftforge.common.MinecraftForge;

public final class ClientSetup extends CommonSetup {
    @Override public void cue(int kind, float value) {
        net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
            switch (kind) {
                case Net.PERFECT: CueFx.perfect(); break;
                case Net.IMPACT: ImpactFx.impact(value, true); break;
                case Net.GUARD_BREAK: case Net.STAGGER: ImpactFx.impact(value, false); break;
                case Net.EXHAUSTED: ImpactFx.exhausted(); break;
                case Net.PARKOUR_DENY: ParkourClient.deny(); break;
                default: break;
            }
        });
    }
    @Override public void wallFx(int entity, int state, int nx, int nz, int event) { net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> ParkourFx.remote(entity, state, nx, nz, event)); }
    @Override public void squad(java.util.List<Net.Member> members) { net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> SquadHud.update(members)); }
    @Override public void client() {
        MinecraftForge.EVENT_BUS.register(new SoundFx());
        MinecraftForge.EVENT_BUS.register(new CompassBar());
        MinecraftForge.EVENT_BUS.register(new MovementFx());
        MinecraftForge.EVENT_BUS.register(new AmbienceFx());
        MinecraftForge.EVENT_BUS.register(new CueFx());
        MinecraftForge.EVENT_BUS.register(new ImpactFx());
        MinecraftForge.EVENT_BUS.register(new WeatherFx());
        MinecraftForge.EVENT_BUS.register(new SquadHud());
        MinecraftForge.EVENT_BUS.register(new ParkourClient());
        MinecraftForge.EVENT_BUS.register(new ParkourFx());
        MinecraftForge.EVENT_BUS.register(new DodgeFx());
    }
}
