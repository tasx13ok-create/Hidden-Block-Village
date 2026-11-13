package noppes.npcs.client.gui.player;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
/** Original constructor-thread fixture; no third-party GUI implementation. */
public final class GuiFaction extends GuiScreen {
    public static int constructions;
    public static Thread constructedOn;
    public GuiFaction(){
        if(!Minecraft.getMinecraft().isCallingFromMinecraftThread())throw new AssertionError("GUI constructor on worker");
        constructions++;constructedOn=Thread.currentThread();
    }
}
