package village.vale;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = ValeOrders.ID, value = Side.CLIENT)
public final class ClientModels {
    @SubscribeEvent public static void tooltip(net.minecraftforge.event.entity.player.ItemTooltipEvent event) {
        net.minecraft.util.ResourceLocation id=event.getItemStack().getItem().getRegistryName();
        if(id!=null&&id.getNamespace().equals("hvbijuu")){
            Order order=OwnedArmor.affinity(id.getPath());
            if(order!=null)event.getToolTip().add("Vale affinity: "+order.title+". Use a matching issued Vale weapon.");
        }
        if(id!=null&&id.getNamespace().equals(ValeOrders.ID)){
            String key=id.getPath();
            if(event.getItemStack().getItem() instanceof FieldItems.Meal){FieldItems.Meal meal=(FieldItems.Meal)event.getItemStack().getItem();event.getToolTip().add("Food: "+FieldItems.HUNGER[meal.kind]+" hunger. Recovery bonuses share a 30s cooldown.");if(meal.bowl())event.getToolTip().add("Returns its bowl after eating.");}
            if(key.equals("technique_codex"))event.getToolTip().add("Use: cast selected art. Sneak + use: cycle. /vale arts shows study.");
            if(key.equals("mission_journal"))event.getToolTip().add("Use: contracts and merit. /vale mission claim finishes your current contract.");
            if(key.startsWith("form_")){int i=java.util.Arrays.asList(Forms.KEYS).indexOf(key.substring(5));if(i>=0)event.getToolTip().add("Level "+Forms.LEVELS[i]+" | 60 resolve + 3/s | 20s form | 120s cooldown.");}
            if(key.startsWith("summon_")){int i=java.util.Arrays.asList(Summons.KEYS).indexOf(key.substring(7));if(i>=0)event.getToolTip().add("Level "+Summons.LEVELS[i]+" | 35 resolve + 1/s | 60s companion. Use again: dismiss.");}
            if(key.equals("trial_writ"))event.getToolTip().add("Use: challenge. Sneak + use: choose. One merit; levels 5/7/9/12.");
        }
    }
    @SubscribeEvent public static void register(ModelRegistryEvent event) {
        for (Item item : Gear.ITEMS.values()) {
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
