package narutodestinationvillages.compat;
import net.minecraft.item.Item;
public final class LegacyGameData {
 private static final Items ITEMS=new Items();public static Items getItemRegistry(){return ITEMS;}
 public static final class Items {public String func_148750_c(Object item){return item instanceof Item&&((Item)item).getRegistryName()!=null?((Item)item).getRegistryName().toString():null;}}
}
