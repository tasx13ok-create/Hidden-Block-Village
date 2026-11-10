package village.vale.arsenal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid="valeorders",value=Side.CLIENT)
public final class ArsenalClient {
    private ArsenalClient() {}
    public static void renderers() {
        RenderingRegistry.registerEntityRenderingHandler(ArsenalShot.class,manager->new Representation<ArsenalShot>(manager));
        RenderingRegistry.registerEntityRenderingHandler(ArsenalField.class,manager->new Representation<ArsenalField>(manager));
    }
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        for(ArsenalBlade blade:Arsenal.items().values())ModelLoader.setCustomModelResourceLocation(blade,0,
                new ModelResourceLocation(blade.getRegistryName(),"inventory"));
    }
    private static final class Representation<T extends ArsenalEntity> extends Render<T> {
        Representation(RenderManager manager) {super(manager);shadowSize=0;}
        @Override protected ResourceLocation getEntityTexture(T entity) {return TextureMap.LOCATION_BLOCKS_TEXTURE;}
        @Override public void doRender(T entity,double x,double y,double z,float yaw,float partial) {
            ArsenalBlade item=Arsenal.item(entity.spec());if(item==null)return;
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(x,y+.12,z);
                boolean field=entity instanceof ArsenalField;
                if(field) {GlStateManager.rotate(90,1,0,0);GlStateManager.rotate((entity.ticksExisted+partial)*2,0,0,1);}
                else {GlStateManager.rotate(-renderManager.playerViewY,0,1,0);GlStateManager.rotate(renderManager.playerViewX,1,0,0);GlStateManager.rotate((entity.ticksExisted+partial)*22,0,0,1);}
                float size=field?.65f:.45f;GlStateManager.scale(size,size,size);
                bindEntityTexture(entity);
                Minecraft.getMinecraft().getRenderItem().renderItem(new ItemStack(item),ItemCameraTransforms.TransformType.GROUND);
            } finally {GlStateManager.popMatrix();}
        }
    }
}
