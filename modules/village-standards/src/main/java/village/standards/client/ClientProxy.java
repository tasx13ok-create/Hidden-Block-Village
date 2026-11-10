package village.standards.client;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.standards.CommonProxy;
import village.standards.EntityStandard;
import village.standards.ItemStandard;
import village.standards.StandardsMod;

@SideOnly(Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    @Override public void initialize() {
        MinecraftForge.EVENT_BUS.register(this);
        RenderingRegistry.registerEntityRenderingHandler(EntityStandard.class, StandardRenderer::new);
    }
    @SubscribeEvent public void models(ModelRegistryEvent event) {
        for (ItemStandard item : StandardsMod.items().values()) register(item);
        register(StandardsMod.item(null));
    }
    private static void register(ItemStandard item) {
        ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
