package village.vale.culinary;
import java.util.*;import net.minecraft.init.*;import net.minecraft.item.*;
/** Original two/three-input native process graph. Stations intentionally use different transformations. */
public final class KitchenProcesses {
 private KitchenProcesses(){}public static final List<Process> ALL=new ArrayList<>();
 public static final class Process {public final int machine,ticks;public final ItemStack[] inputs;public final ItemStack output;Process(int m,int t,ItemStack out,ItemStack... in){machine=m;ticks=t;output=out;inputs=in;}public boolean matches(ItemStack[] slots){for(int i=0;i<3;i++){if(i>=inputs.length){if(!slots[i].isEmpty())return false;}else if(!ItemStack.areItemsEqual(inputs[i],slots[i])||!ItemStack.areItemStackTagsEqual(inputs[i],slots[i])||slots[i].getCount()<inputs[i].getCount())return false;}return true;}}
 private static ItemStack s(String k){return Culinary.stack(k);}private static ItemStack v(Item i){return new ItemStack(i);}private static ItemStack bowl(){return v(Items.BOWL);}private static void add(int m,String out,ItemStack... in){ALL.add(new Process(m,m==0?100:m==1?120:m==2?160:200,s(out),in));}
 public static void initialize(){if(!ALL.isEmpty())return;
  add(0,"rice_flour",s("rice"));add(0,"wheat_flour",v(Items.WHEAT));add(0,"ground_kelp",s("boiled_kelp"));
  add(1,"noodles",s("wheat_flour"),v(Items.WATER_BUCKET));add(1,"moromi",s("soybean"),s("wheat_flour"));add(1,"mochi",s("rice_flour"),v(Items.SUGAR));add(1,"dango",s("rice_flour"),s("redbean"),v(Items.SUGAR));add(1,"onigiri",s("cooked_rice"),s("ground_kelp"));
  add(2,"cooked_soybean",s("soybean"),v(Items.WATER_BUCKET));add(2,"boiled_kelp",s("kelp"),v(Items.WATER_BUCKET));add(2,"ramen_egg",v(Items.EGG),v(Items.WATER_BUCKET));add(2,"cooked_rice",s("rice"),v(Items.WATER_BUCKET));add(2,"soy_sauce",s("moromi"),v(Items.GLASS_BOTTLE));add(2,"soy_milk",s("cooked_soybean"),v(Items.GLASS_BOTTLE));add(2,"tofu",s("cooked_soybean"),s("soy_sauce"));
  add(3,"suika",v(Items.MELON),v(Items.SUGAR));add(3,"yokan",s("redbean"),v(Items.SUGAR));add(3,"anpan",s("wheat_flour"),s("redbean"));add(3,"daifuku_mochi",s("mochi"),s("redbean"));
  add(2,"okayu",s("cooked_rice"),v(Items.WATER_BUCKET),bowl());add(3,"donburi",s("cooked_rice"),v(Items.COOKED_CHICKEN),bowl());add(3,"tofu_curry",s("tofu"),v(Items.CARROT),bowl());add(2,"miso_soup",s("moromi"),s("tofu"),bowl());add(2,"tofu_soup",s("tofu"),s("ground_kelp"),bowl());add(2,"dashi_soup",s("ground_kelp"),v(Items.FISH),bowl());add(2,"zenzai",s("redbean"),v(Items.SUGAR),bowl());
  add(3,"hotdog",v(Items.BREAD),v(Items.COOKED_PORKCHOP));add(2,"recovery_curry",s("cooked_rice"),v(Items.GOLDEN_CARROT),bowl());add(3,"fortune_fruit",v(Items.APPLE),s("resolve_shard"),v(Items.EMERALD));add(2,"sake",s("cooked_rice"),v(Items.SUGAR),v(Items.GLASS_BOTTLE));add(1,"fish_cake",v(Items.FISH),s("wheat_flour"));add(2,"cooked_noodles",s("noodles"),v(Items.WATER_BUCKET));add(3,"potato_chips",v(Items.POTATO));
  add(3,"okonomiyaki",s("wheat_flour"),v(Items.EGG),v(Items.CARROT));add(3,"yakisoba",s("noodles"),s("soy_sauce"),v(Items.COOKED_PORKCHOP));add(2,"ramen",s("noodles"),s("ramen_egg"),bowl());
 }
 public static Process match(int machine,ItemStack[] slots){initialize();for(Process p:ALL)if(p.machine==machine&&p.matches(slots))return p;return null;}
}
