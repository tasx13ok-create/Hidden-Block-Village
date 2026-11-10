package village.vale;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.inventory.Container;
import net.minecraft.item.*;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Optional JEI bridge uses its installed public API reflectively; no bundled dependency. */
@Mod.EventBusSubscriber(modid=ValeOrders.ID,value=Side.CLIENT)
public final class ClientCatalogHide {
    public static boolean jeiComplete;public static String jeiFailure="";
    private static int ticks;private static boolean attempted;
    private ClientCatalogHide(){}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||attempted||++ticks%20!=0)return;
        if(!Loader.isModLoaded("jei")){jeiComplete=true;attempted=true;return;}
        try{
            Class<?> internal=Class.forName("mezz.jei.Internal");if(internal.getMethod("getRuntime").invoke(null)==null)return;Object registry=internal.getMethod("getIngredientRegistry").invoke(null);if(registry==null)return;
            Class<?> api=Class.forName("mezz.jei.api.ingredients.IIngredientRegistry");Collection<?> all=(Collection<?>)api.getMethod("getAllIngredients",Class.class).invoke(registry,ItemStack.class);List<ItemStack> hidden=new ArrayList<>();for(Object o:all)if(o instanceof ItemStack&&CanonicalCatalog.blocked(((ItemStack)o).getItem()))hidden.add((ItemStack)o);
            api.getMethod("removeIngredientsAtRuntime",Class.class,Collection.class).invoke(registry,ItemStack.class,hidden);jeiComplete=true;attempted=true;
            org.apache.logging.log4j.LogManager.getLogger("ValeCatalog").info("VALE_JEI_CATALOG_PASS: "+hidden.size()+" overlapping combat-prop variants removed from JEI.");
        }catch(Exception e){jeiFailure=e.toString();attempted=true;org.apache.logging.log4j.LogManager.getLogger("ValeCatalog").warn("Optional JEI catalog bridge failed; common recipe/use policy remains active.",e);}
    }
    @SubscribeEvent public static void creative(GuiScreenEvent.DrawScreenEvent.Pre event){
        if(!(event.getGui() instanceof GuiContainerCreative))return;Container container=((GuiContainerCreative)event.getGui()).inventorySlots;
        if(container instanceof GuiContainerCreative.ContainerCreative){GuiContainerCreative.ContainerCreative creative=(GuiContainerCreative.ContainerCreative)container;int before=creative.itemList.size();creative.itemList.removeIf(stack->CanonicalCatalog.blocked(stack.getItem()));if(creative.itemList.size()!=before)creative.scrollTo(0);}
    }
}
