package village.immersion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Perfect-dodge screen cue: a brief cool-white edge flash that fades over half a second. */
@SideOnly(Side.CLIENT)
final class CueFx {
    private static long at = -100;
    static void perfect() { Minecraft mc = Minecraft.getMinecraft(); at = mc.world == null ? -100 : mc.world.getTotalWorldTime(); }

    @SubscribeEvent public void draw(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) return;
        double age = mc.world.getTotalWorldTime() + e.getPartialTicks() - at;
        if (age < 0 || age > 10) return;
        int a = (int) (110 * (1 - age / 10));
        ScaledResolution r = e.getResolution();
        int w = r.getScaledWidth(), h = r.getScaledHeight(), band = 18;
        int c = (a << 24) | 0xDDF2FF, clear = 0x00DDF2FF;
        Gui.drawRect(0, 0, w, 2, c); Gui.drawRect(0, h - 2, w, h, c); Gui.drawRect(0, 0, 2, h, c); Gui.drawRect(w - 2, 0, w, h, c);
        for (int i = 0; i < band; i++) {
            int ai = (int) (a * (1 - i / (double) band) * 0.5);
            int ci = (ai << 24) | 0xDDF2FF;
            Gui.drawRect(i, i, w - i, i + 1, ci); Gui.drawRect(i, h - i - 1, w - i, h - i, ci);
        }
    }
}
