package narutodestinationvillages.compat;
import net.minecraft.item.Item;import net.minecraft.util.ResourceLocation;import net.minecraftforge.fml.common.registry.ForgeRegistries;
/** Existing pack coins retain the original five base-nine denomination values. */
public final class AddedItems {
 private static Item item(String name){Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("variedcommodities",name));if(item==null)throw new IllegalStateException("Required village currency missing: "+name);return item;}
 public static final Item RyoBronze=item("coin_bronze"),RyoSilver=item("coin_iron"),RyoGold=item("coin_gold"),RyoDiamond=item("coin_diamond"),RyoEmerald=item("coin_emerald");
}
