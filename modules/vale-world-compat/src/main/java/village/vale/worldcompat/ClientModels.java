package village.vale.worldcompat;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
@Mod.EventBusSubscriber(modid=WorldCompat.ID,value=Side.CLIENT)
public final class ClientModels {
 @SubscribeEvent public static void models(ModelRegistryEvent e){for(WorldCompat.Furnishing b:WorldCompat.BLOCKS)ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b),0,new ModelResourceLocation(b.getRegistryName(),"inventory"));}
}
