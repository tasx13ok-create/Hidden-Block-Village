package village.vale.culinary;
import net.minecraft.block.BlockCrops;import net.minecraft.item.Item;
/** Vanilla farmland, light, hydration, bonemeal, mature harvest and seed drops. */
public final class KitchenCrop extends BlockCrops {
 private final String crop;public KitchenCrop(String c){crop=c;}
 @Override protected Item getSeed(){return Culinary.item(crop+"_seed");}
 @Override protected Item getCrop(){return Culinary.item(crop);}
}
