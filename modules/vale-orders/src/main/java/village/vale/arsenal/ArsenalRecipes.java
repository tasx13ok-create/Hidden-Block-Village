package village.vale.arsenal;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Distinct native recipes with one fresh output, certified after native slot transfer. */
@Mod.EventBusSubscriber(modid="valeorders")
public final class ArsenalRecipes {
    private ArsenalRecipes() {}
    @SubscribeEvent public static void register(RegistryEvent.Register<IRecipe> event) {
        Item[] focus={Items.FEATHER,Items.SHEARS,Items.COMPASS,Items.FISHING_ROD,Items.REDSTONE,
            Items.GLOWSTONE_DUST,Items.BRICK,Items.FLINT,Items.IRON_INGOT,Items.BOW,Items.BLAZE_ROD,
            Items.FIRE_CHARGE,Items.SNOWBALL,Items.STRING,Items.SUGAR,Items.GHAST_TEAR,
            Items.ENDER_PEARL,Items.ARROW,Items.SPIDER_EYE,Items.GLASS_BOTTLE,Items.QUARTZ,Items.SHIELD,
            Items.MAGMA_CREAM,Items.RABBIT_FOOT,Items.PRISMARINE_SHARD,Items.GUNPOWDER};
        ArsenalSpec[] specs=ArsenalSpec.values();
        if(focus.length!=specs.length)throw new IllegalStateException("Specialist recipe catalog mismatch");
        for(int i=0;i<specs.length;i++) {
            ArsenalBlade blade=Arsenal.item(specs[i]);
            if(blade==null)throw new IllegalStateException("Register specialist items before recipes");
            NonNullList<Ingredient> ingredients=NonNullList.create();
            ingredients.add(Ingredient.fromItem(Items.IRON_SWORD));
            ingredients.add(Ingredient.fromItem(Items.DIAMOND));
            ingredients.add(Ingredient.fromItem(focus[i]));
            IRecipe nativeRecipe=new ShapelessRecipes("valeorders_specialists",new ItemStack(blade,1),ingredients);
            event.getRegistry().register(new ArsenalCrafting.Recipe(new ResourceLocation("valeorders",specs[i].key),nativeRecipe));
        }
    }
}
