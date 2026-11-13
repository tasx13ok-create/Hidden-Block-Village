package micdoodle8.mods.galacticraft.api.client.tabs;
import net.minecraft.client.Minecraft;
import noppes.npcs.client.gui.player.GuiQuestLog;
import noppes.npcs.util.CustomNPCsScheduler;
/** Original synthetic Java8 callback fixture, not copied third-party source. */
public final class InventoryTabQuests {
    public void onTabClicked(){CustomNPCsScheduler.runTack(()->Minecraft.getMinecraft().displayGuiScreen(new GuiQuestLog()));}
    public void untouchedScheduler(){CustomNPCsScheduler.runTack(()->{});}
}
