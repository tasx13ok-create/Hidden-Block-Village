package village.vale.culinary;
import java.util.*;
import net.minecraft.block.*;import net.minecraft.block.material.Material;import net.minecraft.creativetab.CreativeTabs;import net.minecraft.init.*;import net.minecraft.item.*;import net.minecraft.item.crafting.*;import net.minecraft.util.*;import net.minecraftforge.event.RegistryEvent;import net.minecraftforge.fml.common.registry.GameRegistry;import village.vale.Gear;
/** Explicit Core integration entry points; food is ordinary freely tradeable inventory. */
public final class Culinary {
 private Culinary(){}
 public static final Map<String,Item> ITEMS=new LinkedHashMap<>();public static final Map<String,Block> BLOCKS=new LinkedHashMap<>();
 public static final String[] RAW={"rice","rice_flour","wheat_flour","soybean","redbean","ground_kelp","noodles","moromi","resolve_shard"};
 public static final String[] FOOD={"cooked_soybean","boiled_kelp","ramen_egg","soy_sauce","tofu","cooked_rice","dango","okonomiyaki","yakisoba","mochi","suika","yokan","anpan","daifuku_mochi","okayu","onigiri","donburi","tofu_curry","miso_soup","tofu_soup","dashi_soup","zenzai","ramen","soy_milk","hotdog","recovery_curry","fortune_fruit","sake","fish_cake","cooked_noodles","potato_chips"};
 public static final String[] CROPS={"rice","soybean","redbean"};public static final String[] STATIONS={"mill","roller","boiler","steamer"};
 public static Item item(String key){return ITEMS.get(key);}public static ItemStack stack(String key){return new ItemStack(item(key));}
 private static <T extends Block>T block(RegistryEvent.Register<Block> e,String key,T b){b.setRegistryName("valeorders","kitchen_"+key);b.setTranslationKey("valeorders.kitchen_"+key);BLOCKS.put(key,b);e.getRegistry().register(b);return b;}
 public static void registerBlocks(RegistryEvent.Register<Block> e){for(String c:CROPS)block(e,c+"_crop",new KitchenCrop(c));for(int i=0;i<4;i++)block(e,STATIONS[i],new KitchenBlock(i));block(e,"kelp",new BlockReed(){@Override public Item getItemDropped(net.minecraft.block.state.IBlockState s,Random r,int f){return item("kelp");}@Override public ItemStack getItem(net.minecraft.world.World w,net.minecraft.util.math.BlockPos p,net.minecraft.block.state.IBlockState s){return stack("kelp");}}.setCreativeTab(CreativeTabs.DECORATIONS));}
 private static void add(RegistryEvent.Register<Item> e,String key,Item i){if(Gear.ITEMS.containsKey("kitchen_"+key))throw new IllegalStateException("Duplicate culinary key "+key);i.setRegistryName("valeorders","kitchen_"+key);i.setTranslationKey("valeorders.kitchen_"+key);i.setCreativeTab(CreativeTabs.FOOD);ITEMS.put(key,i);Gear.ITEMS.put("kitchen_"+key,i);e.getRegistry().register(i);}
 public static void registerItems(RegistryEvent.Register<Item> e){for(String c:CROPS)add(e,c+"_seed",new ItemSeeds(BLOCKS.get(c+"_crop"),Blocks.FARMLAND));for(String k:RAW)add(e,k,k.equals("resolve_shard")?new Item(){@Override public int getItemBurnTime(ItemStack stack){return 400;}}:new Item());for(int i=0;i<FOOD.length;i++)add(e,FOOD[i],new KitchenFood(FOOD[i],i));for(int i=1;i<=3;i++)add(e,"resolve_pill_"+i,new KitchenTonic(i));add(e,"experience_draught",new KitchenTonic(4));add(e,"antidote",new KitchenTonic(5));for(String s:STATIONS)add(e,s,new ItemBlock(BLOCKS.get(s)));add(e,"kelp",new ItemBlock(BLOCKS.get("kelp")));}
 public static void registerTiles(){net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(KitchenEvents.class);GameRegistry.registerTileEntity(KitchenTile.class,new ResourceLocation("valeorders","kitchen_station"));}
 /** Uses the built-in chest-window protocol and texture; no custom packets or GUI registration. */
 public static void registerGui(Object mod){}
 public static void registerRecipes(RegistryEvent.Register<IRecipe> e){KitchenProcesses.initialize();int id=0;
  for(String c:CROPS)recipe(e,"seed_"+c,stack(c+"_seed"),new ItemStack(Items.WHEAT_SEEDS),new ItemStack(c.equals("rice")?Items.SUGAR:c.equals("soybean")?Items.POTATO:Items.BEETROOT));
  recipe(e,"kelp",stack("kelp"),new ItemStack(Items.WHEAT_SEEDS),new ItemStack(Items.DYE,1,2));
  for(int i=0;i<4;i++)recipe(e,"station_"+STATIONS[i],stack(STATIONS[i]),new ItemStack(Blocks.FURNACE),new ItemStack(Items.IRON_INGOT),new ItemStack(new Item[]{Items.FLINT,Items.STICK,Items.BUCKET,Items.BOWL}[i]));
  recipe(e,"shard",new ItemStack(item("resolve_shard"),2),new ItemStack(Items.QUARTZ),new ItemStack(Items.GLOWSTONE_DUST),new ItemStack(Items.SUGAR));
  for(int i=1;i<=3;i++)recipe(e,"pill_"+i,stack("resolve_pill_"+i),stack("resolve_shard"),stack(i==1?"rice_flour":i==2?"cooked_soybean":"moromi"),new ItemStack(i==1?Items.SUGAR:i==2?Items.REDSTONE:Items.GHAST_TEAR));
  recipe(e,"antidote",stack("antidote"),new ItemStack(Items.GLASS_BOTTLE),new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.SUGAR));
  recipe(e,"draught",stack("experience_draught"),new ItemStack(Items.GLASS_BOTTLE),new ItemStack(Items.EMERALD),stack("resolve_shard"),stack("soy_milk"));
 }
 private static void recipe(RegistryEvent.Register<IRecipe> e,String id,ItemStack out,ItemStack... inputs){NonNullList<Ingredient> list=NonNullList.create();for(ItemStack s:inputs)list.add(Ingredient.fromStacks(s));e.getRegistry().register(new ShapelessRecipes("valeorders_kitchen",out,list).setRegistryName("valeorders","kitchen_"+id));}
}
