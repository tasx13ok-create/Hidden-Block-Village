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
import village.dungeonguard.AreaPresentation;
import village.dungeonguard.AreaState;
import village.dungeonguard.client.AreaBannerLayout;
import village.dungeonguard.client.AreaBannerState;
import village.dungeonguard.client.FieldHudReservations;

@SideOnly(value=Side.CLIENT)
public final class AreaBannerHud {
    private static final ResourceLocation TEXTURE = new ResourceLocation("dungeonguard", "textures/gui/area_banner.png");
    private final AreaBannerState state = new AreaBannerState();
    private Object displayedWorld;
    private Object displayedPlayer;
    private final AreaBannerLayout.BossStack bosses = new AreaBannerLayout.BossStack();
    private final FieldHudReservations fieldHud = new FieldHudReservations();
    private Boolean immersionPresent;
    public boolean enabled = true;

    public void accept(AreaState incoming) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            this.clear();
            return;
        }
        this.synchronizeWorld(minecraft);
        this.state.accept(incoming);
    }

    public void clear() {
        this.state.clear();
        this.bosses.reset();
        this.displayedWorld = null;
        this.displayedPlayer = null;
    }

    private void synchronizeWorld(Minecraft minecraft) {
        if (this.displayedWorld != minecraft.world || this.displayedPlayer != minecraft.player) {
            this.state.clear();
            this.bosses.reset();
            this.displayedWorld = minecraft.world;
            this.displayedPlayer = minecraft.player;
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST, receiveCanceled=true)
    public void beginFrame(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            this.bosses.reset();
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST, receiveCanceled=true)
    public void bossBar(RenderGameOverlayEvent.BossInfo event) {
        this.bosses.observe(event.getY(), event.getIncrement(), event.isCanceled());
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || minecraft.player == null) {
            this.clear();
            return;
        }
        this.synchronizeWorld(minecraft);
        this.state.tick();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        boolean compact;
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || !this.enabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.gameSettings.hideGUI || minecraft.gameSettings.showDebugInfo || minecraft.currentScreen != null || minecraft.world == null || minecraft.player == null || minecraft.player.isSpectator() || !minecraft.player.isEntityAlive()) {
            return;
        }
        this.synchronizeWorld(minecraft);
        AreaState displayed = this.state.area();
        float opacity = this.state.opacity();
        if (displayed.isEmpty() || opacity <= 0.0f) {
            return;
        }
        int screenWidth = event.getResolution().getScaledWidth();
        int screenHeight = event.getResolution().getScaledHeight();
        if (this.immersionPresent == null) {
            this.immersionPresent = Loader.isModLoaded((String)"shinobiimmersion");
        }
        int reservedTop = Math.max(this.bosses.bottom(), this.immersionPresent != false ? 32 : 0);
        AreaBannerLayout.Box[] reserved = this.fieldHud.bounds(minecraft, screenWidth, screenHeight);
        AreaBannerLayout.Box box = AreaBannerLayout.place(screenWidth, screenHeight, reservedTop, reserved);
        boolean bl = compact = box == null;
        if (compact) {
            box = AreaBannerLayout.compact(screenWidth, screenHeight, reservedTop, 1, reserved);
        }
        if (box == null) {
            return;
        }
        int width = box.width;
        int height = box.height;
        int x = box.x;
        int y = box.y;
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate((GlStateManager.SourceFactor)GlStateManager.SourceFactor.SRC_ALPHA, (GlStateManager.DestFactor)GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, (GlStateManager.SourceFactor)GlStateManager.SourceFactor.ONE, (GlStateManager.DestFactor)GlStateManager.DestFactor.ZERO);
        try {
            int center = x + width / 2;
            if (compact) {
                Gui.drawRect((int)x, (int)y, (int)(x + width), (int)(y + height), (int)((int)(180.0f * opacity) << 24 | 0x171B23));
                Gui.drawRect((int)x, (int)y, (int)(x + width), (int)(y + 1), (int)((int)(230.0f * opacity) << 24 | 0xD4AC60));
                GlStateManager.enableBlend();
                AreaBannerHud.line(minecraft.fontRenderer, "\u00a7l" + AreaPresentation.name(displayed), center, y + 4, width - 12, displayed.isBoss() ? 16758433 : 16773334, opacity);
            } else {
                GlStateManager.color((float)1.0f, (float)1.0f, (float)1.0f, (float)opacity);
                minecraft.getTextureManager().bindTexture(TEXTURE);
                Gui.drawScaledCustomSizeModalRect((int)x, (int)y, (float)0.0f, (float)0.0f, (int)2172, (int)724, (int)width, (int)height, (float)2172.0f, (float)724.0f);
                int maxTextWidth = (int)((float)width * 0.64f);
                AreaBannerHud.line(minecraft.fontRenderer, "\u00a7l" + AreaPresentation.name(displayed), center, (float)y + (float)height * 0.65f, maxTextWidth, displayed.isBoss() ? 8597024 : 3745045, opacity);
            }
        }
        finally {
            GlStateManager.color((float)1.0f, (float)1.0f, (float)1.0f, (float)1.0f);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private static void line(FontRenderer font, String text, int center, float y, int maxWidth, int rgb, float opacity) {
        int fullWidth = font.getStringWidth(text);
        if (fullWidth == 0) {
            return;
        }
        if ((float)fullWidth > (float)maxWidth / 0.75f) {
            text = font.trimStringToWidth(text, (int)((float)maxWidth / 0.75f) - font.getStringWidth("...")) + "...";
            fullWidth = font.getStringWidth(text);
        }
        float scale = Math.min(1.0f, (float)maxWidth / (float)fullWidth);
        int alpha = Math.max(5, (int)(255.0f * opacity));
        GlStateManager.pushMatrix();
        GlStateManager.translate((float)((float)center - (float)fullWidth * scale / 2.0f), (float)y, (float)0.0f);
        GlStateManager.scale((float)scale, (float)scale, (float)1.0f);
        font.drawString(text, 0.0f, 0.0f, alpha << 24 | rgb, false);
        GlStateManager.popMatrix();
    }
}

