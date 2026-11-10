package village.vale;

import java.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.item.crafting.*;
import net.minecraft.util.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistryModifiable;

/** Consolidate overlapping utility combat props without deleting saved registry identities. */
@Mod.EventBusSubscriber(modid=ValeOrders.ID)
public final class CanonicalCatalog {
    private static final Set<Item> BLOCKED=new LinkedHashSet<>();private static boolean collected;
    public static final List<String> removedRecipes=new ArrayList<>(),adaptedRecipes=new ArrayList<>(),unresolvedRecipes=new ArrayList<>();
    private CanonicalCatalog(){}
    private static void collect(){
        if(collected)return;collected=true;if(!ValeOrders.canonicalEquipmentCatalog)return;
        for(Item item:ForgeRegistries.ITEMS.getValuesCollection()){
            ResourceLocation id=item.getRegistryName();if(id==null||!Arrays.asList("variedcommodities","customnpcs").contains(id.getNamespace()))continue;
            String path=id.getPath().toLowerCase(Locale.ROOT);
            if(path.contains("wand")||path.contains("cloner")||path.contains("scripter")||path.contains("scripted")||path.contains("soulstone")||path.contains("pather"))continue;
            boolean combat=item instanceof ItemSword||item instanceof ItemArmor||item instanceof ItemBow||item instanceof ItemShield||path.matches(".*(sword|dagger|spear|scythe|halberd|battleaxe|broadsword|katana|kunai|shuriken|musket|pistol|glaive|warhammer|trident|shield).*" );
            if(combat)BLOCKED.add(item);
        }
    }
    public static boolean blocked(Item item){return BLOCKED.contains(item);}
    public static Collection<Item> items(){return Collections.unmodifiableSet(BLOCKED);}
    public static void finish(){collect();for(Item item:BLOCKED)item.setCreativeTab(null);org.apache.logging.log4j.LogManager.getLogger("ValeCatalog").info("VALE_CATALOG_POLICY: "+BLOCKED.size()+" overlapping utility combat props quarantined; "+removedRecipes.size()+" recipes removed, "+adaptedRecipes.size()+" adapted, "+unresolvedRecipes.size()+" custom dependent recipes require review. Registry IDs retained.");}
    private static Item canonical(Item item){if(item instanceof ItemArmor){switch(((ItemArmor)item).armorType){case HEAD:return Items.IRON_HELMET;case CHEST:return Items.IRON_CHESTPLATE;case LEGS:return Items.IRON_LEGGINGS;default:return Items.IRON_BOOTS;}}if(item instanceof ItemBow)return Items.BOW;if(item instanceof ItemShield)return Items.SHIELD;return Items.IRON_SWORD;}
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void recipes(RegistryEvent.Register<IRecipe> event){
        collect();if(BLOCKED.isEmpty())return;IForgeRegistryModifiable<IRecipe> registry=(IForgeRegistryModifiable<IRecipe>)event.getRegistry();
        for(IRecipe recipe:new ArrayList<>(registry.getValuesCollection())){
            ItemStack output=recipe.getRecipeOutput();if(!output.isEmpty()&&blocked(output.getItem())){registry.remove(recipe.getRegistryName());removedRecipes.add(recipe.getRegistryName().toString());continue;}
            NonNullList<Ingredient> ingredients=NonNullList.create();boolean changed=false;
            for(Ingredient ingredient:recipe.getIngredients()){
                List<ItemStack> options=new ArrayList<>();boolean replace=false;
                for(ItemStack option:ingredient.getMatchingStacks()){if(blocked(option.getItem())){options.add(new ItemStack(canonical(option.getItem())));replace=true;}else options.add(option.copy());}
                ingredients.add(replace?Ingredient.fromStacks(options.toArray(new ItemStack[0])):ingredient);changed|=replace;
            }
            if(!changed)continue;String name=recipe.getClass().getName();IRecipe replacement=null;
            if((name.equals("net.minecraft.item.crafting.ShapedRecipes")||name.equals("net.minecraftforge.oredict.ShapedOreRecipe"))&&recipe instanceof IShapedRecipe){IShapedRecipe shape=(IShapedRecipe)recipe;replacement=new ShapedRecipes(recipe.getGroup(),shape.getRecipeWidth(),shape.getRecipeHeight(),ingredients,output.copy());}
            if(name.equals("net.minecraft.item.crafting.ShapelessRecipes")||name.equals("net.minecraftforge.oredict.ShapelessOreRecipe"))replacement=new ShapelessRecipes(recipe.getGroup(),output.copy(),ingredients);
            if(replacement==null){unresolvedRecipes.add(recipe.getRegistryName().toString());continue;}
            ResourceLocation id=recipe.getRegistryName();registry.remove(id);registry.register(replacement.setRegistryName(id));adaptedRecipes.add(id.toString());
        }
    }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event){if(blocked(event.getItemStack().getItem())){event.setCanceled(true);message(event.getEntityPlayer());}}
    @SubscribeEvent public static void attack(AttackEntityEvent event){if(blocked(event.getEntityPlayer().getHeldItemMainhand().getItem())){event.setCanceled(true);message(event.getEntityPlayer());}}
    private static void message(EntityPlayer p){if(!p.world.isRemote)p.sendStatusMessage(new TextComponentString("This legacy combat prop is retired. Use Vale equipment; /vale provisions."),true);}
}
