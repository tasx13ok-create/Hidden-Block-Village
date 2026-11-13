package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.relauncher.Side;

/** Client-only registration; never replaces another mod's inventory subclass or hotbar. */
@Mod.EventBusSubscriber(modid=FieldHud.ID,value=Side.CLIENT)
public final class NarutoMenus {
    private static final int OPEN=19631;
    private static final java.util.Set<GuiButton> OWN=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<GuiButton,Boolean>());
    private NarutoMenus(){}
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void open(GuiOpenEvent e){
        Minecraft mc=Minecraft.getMinecraft();
        if(HudConfig.narutoInventory&&e.getGui()!=null&&e.getGui().getClass()==GuiInventory.class&&mc.player!=null&&mc.playerController!=null&&!mc.playerController.isInCreativeMode())e.setGui(new NarutoInventoryScreen(mc.player));
    }
    @SubscribeEvent public static void init(GuiScreenEvent.InitGuiEvent.Post e){
        if(e.getGui() instanceof GuiIngameMenu){GuiButton button=new NarutoInk.Button(OPEN,new Layout.Box(Math.max(4,e.getGui().width-116),6,110,18),"Naruto settings");OWN.add(button);e.getButtonList().add(button);}
    }
    @SubscribeEvent public static void action(GuiScreenEvent.ActionPerformedEvent.Pre e){
        if(e.getGui() instanceof GuiIngameMenu&&OWN.contains(e.getButton())){e.setCanceled(true);Minecraft.getMinecraft().displayGuiScreen(new HudScreen(e.getGui()));}
    }
}
