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
 @SubscribeEvent public static void models(ModelRegistryEvent e){for(final WorldCompat.Furnishing b:WorldCompat.BLOCKS){
  if(net.minecraft.block.Block.REGISTRY.getObject(b.getRegistryName())!=b)continue;
  final net.minecraft.util.ResourceLocation model=new net.minecraft.util.ResourceLocation(WorldCompat.ID,b.getRegistryName().getPath());
  ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(b),0,new ModelResourceLocation(model,"inventory"));
  ModelLoader.setCustomStateMapper(b,new net.minecraft.client.renderer.block.statemap.StateMapperBase(){
   @Override protected ModelResourceLocation getModelResourceLocation(net.minecraft.block.state.IBlockState state){return new ModelResourceLocation(model,"legacy="+b.getMetaFromState(state));}
  });
 }}
}
