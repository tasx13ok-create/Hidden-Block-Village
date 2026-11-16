package village.dungeonguard.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.dungeonguard.AreaState;

/** Client artwork stays separate from protection. All labels are supplied by the server. */
@SideOnly(Side.CLIENT)
public final class AreaBannerHud {
    private static final ResourceLocation TEXTURE = new ResourceLocation("dungeonguard", "textures/gui/area_banner.png");
    private AreaState displayed = AreaState.EMPTY;
    private boolean wanted;
    private int remainingTicks;
    private float opacity;
    private final AreaBannerLayout.BossStack bosses = new AreaBannerLayout.BossStack();
    private final FieldHudReservations fieldHud = new FieldHudReservations();
    private Boolean immersionPresent;
    public boolean enabled = true;

    public void accept(AreaState incoming) {
        wanted = !incoming.isEmpty();
        remainingTicks = wanted ? 200 : 0;
        if (wanted && !incoming.equals(displayed)) { displayed = incoming; opacity = 0; }
    }
    public void clear() { displayed = AreaState.EMPTY; wanted = false; remainingTicks = 0; opacity = 0; bosses.reset(); }
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void beginFrame(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) bosses.reset();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void bossBar(RenderGameOverlayEvent.BossInfo event) {
        bosses.observe(event.getY(), event.getIncrement(), event.isCanceled());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) { clear(); return; }
        if (remainingTicks > 0 && --remainingTicks == 0) wanted = false;
        opacity = wanted ? Math.min(1.0F, opacity + 0.1F) : Math.max(0.0F, opacity - 0.1F);
        if (!wanted && opacity <= 0) displayed = AreaState.EMPTY;
    }
    @SubscribeEvent public void render(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || !enabled || displayed.isEmpty() || opacity <= 0) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings.hideGUI || minecraft.gameSettings.showDebugInfo || minecraft.currentScreen != null
                || minecraft.world == null || minecraft.player == null || minecraft.player.isSpectator() || !minecraft.player.isEntityAlive()) return;
        int screenWidth = event.getResolution().getScaledWidth(), screenHeight = event.getResolution().getScaledHeight();
        if (immersionPresent == null) immersionPresent = Loader.isModLoaded("shinobiimmersion");
        // Its compass settings are private; retain this small reserve even when the compass is disabled.
        int reservedTop = Math.max(bosses.bottom(), immersionPresent ? 32 : 0);
        AreaBannerLayout.Box[] reserved = fieldHud.bounds(minecraft, screenWidth, screenHeight);
        AreaBannerLayout.Box box = AreaBannerLayout.place(screenWidth, screenHeight, reservedTop, reserved);
        boolean compact = box == null;
        if (compact) box = AreaBannerLayout.compact(screenWidth, screenHeight, reservedTop, displayed.isBoss() ? 3 : 2, reserved);
        if (box == null) return;
        int width = box.width, height = box.height, x = box.x, y = box.y;
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        try {
            int center = x + width / 2;
            if (compact) {
                Gui.drawRect(x, y, x + width, y + height, (int) (180 * opacity) << 24 | 0x171B23);
                Gui.drawRect(x, y, x + width, y + 1, (int) (230 * opacity) << 24 | 0xD4AC60);
                GlStateManager.enableBlend();
                line(minecraft.fontRenderer, displayed.heading(), center, y + 4, width - 12, 0xF0CF88, opacity);
                line(minecraft.fontRenderer, "\u00a7l" + displayed.title, center, y + 15, width - 12, 0xFFF0D6, opacity);
                if (displayed.isBoss()) line(minecraft.fontRenderer, "Boss: " + displayed.boss, center, y + 26, width - 12, 0xFFB6A1, opacity);
            } else {
                GlStateManager.color(1, 1, 1, opacity);
                minecraft.getTextureManager().bindTexture(TEXTURE);
                Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 2172, 724, width, height, 2172, 724);
                int maxTextWidth = (int) (width * 0.64F);
                if (displayed.isBoss()) {
                    line(minecraft.fontRenderer, displayed.heading(), center, y + height * 0.53F, maxTextWidth, elementColor(displayed.element), opacity);
                    line(minecraft.fontRenderer, "\u00a7l" + displayed.title, center, y + height * 0.64F, maxTextWidth, 0x392515, opacity);
                    line(minecraft.fontRenderer, "Boss: " + displayed.boss, center, y + height * 0.75F, maxTextWidth, 0x832E20, opacity);
                } else {
                    line(minecraft.fontRenderer, displayed.heading(), center, y + height * 0.56F, maxTextWidth, elementColor(displayed.element), opacity);
                    line(minecraft.fontRenderer, "\u00a7l" + displayed.title, center, y + height * 0.71F, maxTextWidth, 0x392515, opacity);
                }
            }
        } finally {
            GlStateManager.color(1, 1, 1, 1);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }
    private static void line(FontRenderer font, String text, int center, float y, int maxWidth, int rgb, float opacity) {
        int fullWidth = font.getStringWidth(text);
        if (fullWidth == 0) return;
        if (fullWidth > maxWidth / 0.75F) {
            text = font.trimStringToWidth(text, (int) (maxWidth / 0.75F) - font.getStringWidth("...")) + "...";
            fullWidth = font.getStringWidth(text);
        }
        float scale = Math.min(1.0F, (float) maxWidth / fullWidth);
        int alpha = Math.max(5, (int) (255 * opacity));
        GlStateManager.pushMatrix();
        GlStateManager.translate(center - fullWidth * scale / 2.0F, y, 0);
        GlStateManager.scale(scale, scale, 1);
        font.drawString(text, 0, 0, alpha << 24 | rgb, false);
        GlStateManager.popMatrix();
    }
    private static int elementColor(String element) {
        if (element.equals("FIRE")) return 0xA22A1C;
        if (element.equals("WATER")) return 0x174B91;
        if (element.equals("WIND")) return 0x216D59;
        if (element.equals("EARTH")) return 0x73502C;
        if (element.equals("LIGHTNING")) return 0x825611;
        if (element.equals("SAND")) return 0x8A5D25;
        if (element.equals("ICE")) return 0x276B85;
        return 0x73502C;
    }
}
