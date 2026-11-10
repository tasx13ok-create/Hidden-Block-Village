package village.vale;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.block.Block;
import net.minecraft.item.crafting.IRecipe;
@Mod.EventBusSubscriber(modid=ValeOrders.ID)
public final class ContentRegistries {
    private ContentRegistries(){}
    @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){village.vale.culinary.Culinary.registerBlocks(e);}
    @SubscribeEvent public static void recipes(RegistryEvent.Register<IRecipe> e){village.vale.culinary.Culinary.registerRecipes(e);}
}
