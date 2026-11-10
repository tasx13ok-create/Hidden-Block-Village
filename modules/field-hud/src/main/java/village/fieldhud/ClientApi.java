package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Stable optional entry points for the separate Settings hub. Own config stays here. */
@SideOnly(Side.CLIENT)
public final class ClientApi {
    private ClientApi() {}
    /** True consumes the existing cast binding for the focused rail, even if blocked. */
    public static boolean castFocusedJutsu() { return JutsuInput.castFocused(); }
    public static void openSettings(GuiScreen parent) {
        Minecraft mc=Minecraft.getMinecraft();Runnable open=()->mc.displayGuiScreen(new HudScreen(parent));
        if(mc.isCallingFromMinecraftThread())open.run();else mc.addScheduledTask(open);
    }
    public static boolean openLayout(GuiScreen parent) {
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null||mc.world==null)return false;
        Runnable open=()->mc.displayGuiScreen(new HudLayoutScreen(parent));
        if(mc.isCallingFromMinecraftThread())open.run();else mc.addScheduledTask(open);return true;
    }
}
