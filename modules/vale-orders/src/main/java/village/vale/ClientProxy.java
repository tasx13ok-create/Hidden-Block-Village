package village.vale;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
public final class ClientProxy extends CommonProxy {
    @Override public void abilityLoadout(AbilityLoadoutSnapshot snapshot){net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(()->village.vale.client.AbilitySnapshots.acceptLoadout(snapshot));}
    @Override public void abilitySnapshot(AbilitySnapshot snapshot){net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(()->village.vale.client.AbilitySnapshots.accept(snapshot));}
    @Override public void hudSnapshot(HudSnapshot snapshot){net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(()->village.vale.client.HudSnapshots.accept(snapshot));}
    @Override public void dodgeAnim(int entity, int dir){village.vale.client.DodgeClient.start(entity,dir);}
    @Override public void preInit() { RunPoses.configure(net.minecraftforge.fml.common.Loader.instance().getConfigDir());village.vale.client.JutsuVfx.configure(net.minecraftforge.fml.common.Loader.instance().getConfigDir());RenderingRegistry.registerEntityRenderingHandler(TrialKeeper.class, TrialRender::new);FieldRenderers.register();village.vale.arsenal.Arsenal.registerRenderers();
village.vale.arsenal.Scrolls.registerRenderers();village.vale.client.DodgeClient.initialize();village.vale.armor.client.OriginalArmorLights.initialize(); }
}
