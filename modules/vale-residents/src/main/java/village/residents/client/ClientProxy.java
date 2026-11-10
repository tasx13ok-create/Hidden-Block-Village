package village.residents.client;

import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.residents.CommonProxy;
import village.residents.EntityResident;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    @Override public void init() {
        RenderingRegistry.registerEntityRenderingHandler(EntityResident.class, ResidentRenderer::new);
    }
}
