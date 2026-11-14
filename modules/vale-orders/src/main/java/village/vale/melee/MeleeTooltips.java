package village.vale.melee;

import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.vale.Gear;
import village.vale.ValeOrders;

/** Kept separate: ItemTooltipEvent's constructor references a client-only Minecraft class. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid=ValeOrders.ID,value=Side.CLIENT)
public final class MeleeTooltips {
    private MeleeTooltips(){}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){
        if(!(event.getItemStack().getItem() instanceof Gear.WardBlade))return;
        String kind=((Gear.WardBlade)event.getItemStack().getItem()).kind;
        event.getToolTip().add("§bMelee chain: three fully cooled hits on one enemy.");
        event.getToolTip().add("§7Matching issued set required. Finisher recovery: 4s.");
        event.getToolTip().add("§7"+("saber".equals(kind)?"Pressure Cut: stronger contact and brief resistance."
            :"spear".equals(kind)?"Line Break: push your opponent back."
            :"dagger".equals(kind)?"Shadow Flank: strike from behind, then accelerate."
            :"glaive".equals(kind)?"Crescent Sweep: strike two nearby frontal enemies."
            :"staff".equals(kind)?"Chakra Check: weaken a foe and recover chakra."
            :"Splitting Impact: heavy contact and a short slow."));
    }
}
