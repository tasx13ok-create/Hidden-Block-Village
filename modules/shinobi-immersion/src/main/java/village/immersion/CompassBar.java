package village.immersion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A slim compass strip at the top centre: cardinal letters, 15-degree ticks, a spawn marker and
 * markers for other players within 128 blocks (with distance). Hidden in menus, with F1, in
 * spectator and while the debug screen is open, so it never fights other HUDs.
 */
@SideOnly(Side.CLIENT)
final class CompassBar {
    private static final String[] CARDINAL = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    @SubscribeEvent public void draw(RenderGameOverlayEvent.Post e) {
        if (!Config.compass || e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.player;
        if (p == null || mc.gameSettings.hideGUI || mc.gameSettings.showDebugInfo || mc.currentScreen != null || p.isSpectator()) return;
        ScaledResolution r = e.getResolution();
        int cx = r.getScaledWidth() / 2, y = 4, half = 90;
        double field = 160, yaw = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * e.getPartialTicks();
        FontRenderer f = mc.fontRenderer;
        Gui.drawRect(cx - half - 2, y - 1, cx + half + 2, y + 11, 0x7010141A);
        Gui.drawRect(cx - half - 2, y + 11, cx + half + 2, y + 12, 0x90C0A779);
        for (int deg = -180; deg < 180; deg += 15) {
            double x = ImmersionMath.compassX(deg, yaw, half, field);
            if (Double.isNaN(x)) continue;
            int px = cx + (int) Math.round(x);
            if (deg % 45 == 0) {
                String label = CARDINAL[Math.floorMod((deg + 180) / 45, 8)];
                int col = label.equals("N") ? 0xFFE8C77F : 0xFFE1D1AF;
                f.drawStringWithShadow(label, px - f.getStringWidth(label) / 2f, y + 1, col);
            } else Gui.drawRect(px, y + 6, px + 1, y + 10, 0x90E1D1AF);
        }
        BlockPos spawn = p.getBedLocation(p.dimension) != null ? p.getBedLocation(p.dimension) : mc.world.getSpawnPoint();
        marker(f, cx, y, half, field, yaw, p, spawn.getX() + 0.5, spawn.getZ() + 0.5, 0xFF9DDDDC, "⌂");
        for (EntityPlayer o : mc.world.playerEntities) {
            if (o == p || o.isInvisibleToPlayer(p) || o.getDistanceSq(p) > 128 * 128) continue;
            marker(f, cx, y, half, field, yaw, p, o.posX, o.posZ, 0xFF7FD8FF, "▲");
        }
        Gui.drawRect(cx, y - 1, cx + 1, y + 12, 0xFFFFFFFF);
    }

    private static void marker(FontRenderer f, int cx, int y, int half, double field, double yaw, EntityPlayer p, double tx, double tz, int color, String glyph) {
        double x = ImmersionMath.compassX(ImmersionMath.bearing(p.posX, p.posZ, tx, tz), yaw, half, field);
        if (Double.isNaN(x)) return;
        int px = cx + (int) Math.round(x);
        f.drawStringWithShadow(glyph, px - f.getStringWidth(glyph) / 2f, y + 13, color);
        int dist = (int) Math.round(Math.sqrt((tx - p.posX) * (tx - p.posX) + (tz - p.posZ) * (tz - p.posZ)));
        String d = dist + "m";
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(px, y + 23, 0);
        net.minecraft.client.renderer.GlStateManager.scale(0.5f, 0.5f, 1);
        f.drawStringWithShadow(d, -f.getStringWidth(d) / 2f, 0, color);
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }
}
