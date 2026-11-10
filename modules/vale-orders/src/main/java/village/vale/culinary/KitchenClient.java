package village.vale.culinary;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;import net.minecraftforge.client.event.ModelRegistryEvent;import net.minecraftforge.client.model.ModelLoader;import net.minecraftforge.fml.common.Mod;import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;import net.minecraftforge.fml.relauncher.*;
@SideOnly(Side.CLIENT) @Mod.EventBusSubscriber(modid="valeorders",value=Side.CLIENT)
public final class KitchenClient {
 private KitchenClient(){}
 @SubscribeEvent public static void registerModels(ModelRegistryEvent event){for(net.minecraft.item.Item i:Culinary.ITEMS.values())ModelLoader.setCustomModelResourceLocation(i,0,new ModelResourceLocation(i.getRegistryName(),"inventory"));}
}
