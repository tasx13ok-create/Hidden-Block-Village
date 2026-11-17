package village.immersion;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Squad panel on the left edge: each teammate's name, five health pips and distance (or the
 * dimension if they're elsewhere). A downed teammate (20% health or less) pulses red. Data comes
 * from the server twice a second and is hidden if it goes stale.
 */
@SideOnly(Side.CLIENT)
final class SquadHud {
    private static volatile List<Net.Member> members = new ArrayList<>();
    private static volatile long updatedAt;

    static void update(List<Net.Member> list) { members = new ArrayList<>(list); updatedAt = System.currentTimeMillis(); }
    static List<Net.Member> current() { return System.currentTimeMillis() - updatedAt > 3000 ? new ArrayList<>() : members; }
    static boolean isMate(String name) { for (Net.Member m : current()) if (m.name.equals(name)) return true; return false; }

    @SubscribeEvent public void draw(RenderGameOverlayEvent.Post e) {
        if (!Config.squad || e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.gameSettings.hideGUI || mc.gameSettings.showDebugInfo || mc.currentScreen != null) return;
        List<Net.Member> list = current();
        if (list.isEmpty()) return;
        ScaledResolution r = e.getResolution();
        FontRenderer f = mc.fontRenderer;
        int x = 4, y = r.getScaledHeight() / 3, rowH = 13, w = 92;
        Gui.drawRect(x - 2, y - 3, x + w, y + list.size() * rowH, 0x6010141A);
        double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 160.0);
        for (Net.Member m : list) {
            boolean down = ImmersionMath.downed(m.health, m.max), here = m.dim == mc.player.dimension;
            int nameColor = down ? (0xFF000000 | ((int) (180 + 75 * pulse) << 16) | 0x5050) : 0xFFE6F2E8;
            String name = f.trimStringToWidth(m.name, 52);
            f.drawStringWithShadow(name, x, y, nameColor);
            String where = here ? Math.round(Math.sqrt((m.x - mc.player.posX) * (m.x - mc.player.posX) + (m.z - mc.player.posZ) * (m.z - mc.player.posZ))) + "m" : "away";
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.translate(x + w - 3 - f.getStringWidth(where) * 0.5f, y + 1, 0);
            net.minecraft.client.renderer.GlStateManager.scale(0.5f, 0.5f, 1);
            f.drawStringWithShadow(where, 0, 0, 0xFFB8C8BE);
            net.minecraft.client.renderer.GlStateManager.popMatrix();
            int pips = ImmersionMath.pips(m.health, m.max);
            for (int i = 0; i < 5; i++) {
                int px = x + i * 9, py = y + 9;
                int c = i < pips ? (down ? 0xFFE05050 : 0xFF6FD08C) : 0x60FFFFFF;
                Gui.drawRect(px, py, px + 7, py + 2, c);
            }
            y += rowH;
        }
    }
}
