package village.fieldhud;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Immersion for the screens players don't think about. Every vanilla and modded screen keeps its
 * layout and controls; this only layers presentation on top:
 *  - pause / options / settings: drifting ink-and-chakra backdrop, a slow seal watermark, wisps
 *    and falling leaves tinted by the world's time of day, weather and the player's health;
 *  - opening any screen: a brush-stroke ink reveal instead of a hard cut;
 *  - buttons: hover makes a brush underline sweep in with a kunai marker and a faint tick;
 *    clicking throws an ink splash and a wooden clack (pitch varies so it never feels canned);
 *  - title screen: petals and chakra drifting over the panorama;
 *  - death screen: red ink bleeds in from the edges and drips, the heartbeat slows;
 *  - world loading: a spinning seal and a gathering-chakra line;
 *  - inventories and containers: an edge vignette and ambient motes that stay off the slots;
 *  - chat: an ink band under the input line;
 *  - tooltips: brush-ink colours by rarity and by Vale order, with kunai corner marks.
 * Motion off (or Quiet HUD mode) keeps only the static colouring; menuEffects=false disables it.
 */
public final class MenuFx {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final Random RNG = new Random();
    private static GuiScreen screen;
    private static double openedAt, lastFrame;
    private static GuiButton hovered;
    private static double hoverAt;
    private static Field buttonField;
    private static final MenuFxRules.Clock CLOCK=new MenuFxRules.Clock();
    private static MenuFxRules.Mask mask;private static double hoverSoundAt=-1000,clickSoundAt=-1000;
    private static int screenWidth,screenHeight;
    private static Object world,body,connection;
    private static void context(){Object next=MC.getConnection();if(world!=MC.world||body!=MC.player||connection!=next){world=MC.world;body=MC.player;connection=next;reset(MC.currentScreen);}}
    private static boolean live(GuiScreen g){if(g==null||MC.currentScreen!=g||!MC.isCallingFromMinecraftThread())return false;context();return true;}
    private static boolean sounds(){return MenuFxRules.audible(on(),HudConfig.motion,HudConfig.menuSounds,HudConfig.mode);}
    private static void reset(GuiScreen next){screen=next;openedAt=lastFrame=now();hovered=null;BITS.clear();CLOCK.reset();lastBeat=-100;mask=null;screenWidth=next==null?0:next.width;screenHeight=next==null?0:next.height;}
    private static MenuFxRules.Mask mask(GuiContainer g){
        MenuFxRules.Mask result=new MenuFxRules.Mask(g.width,g.height,g.getGuiLeft(),g.getGuiTop(),g.getXSize(),g.getYSize());
        if(g.inventorySlots==null||g.inventorySlots.inventorySlots.size()>512){result.block();return result;}
        for(net.minecraft.inventory.Slot slot:g.inventorySlots.inventorySlots)if(slot!=null&&slot.isEnabled())result.include((double)g.getGuiLeft()+slot.xPos,(double)g.getGuiTop()+slot.yPos,16,16);
        return result;
    }

    private static double now() { return Minecraft.getSystemTime() / 50.0; }
    private static boolean on() { return HudConfig.menuEffects; }
    private static boolean motion() { return MenuFxRules.animated(on(),HudConfig.motion,HudConfig.mode); }

    // ---- particles (screen space, independent of world pause) ------------------------------------
    private static final class Bit { double x, y, vx, vy, rot, spin, size, age, life; int color; int kind; }
    private static final List<Bit> BITS = new ArrayList<>();
    private static final int LEAF = 0, MOTE = 1, INK = 2, PETAL = 3, DRIP = 4;
    private static void emit(int kind, double x, double y, double vx, double vy, double size, double life, int color) {
        if(!motion()||!CLOCK.emit(now())||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(vx)||!Double.isFinite(vy)||!Double.isFinite(size)||!Double.isFinite(life)||size<=0||size>12||life<1||life>400)return;
        if(BITS.size()>=220)BITS.remove(0);
        Bit b = new Bit(); b.kind = kind; b.x = x; b.y = y; b.vx = vx; b.vy = vy; b.size = size; b.life = life; b.color = color;
        b.rot = RNG.nextDouble() * 360; b.spin = (RNG.nextDouble() - 0.5) * 8; BITS.add(b);
    }
    private static void step(double dt) {
        for (Iterator<Bit> it = BITS.iterator(); it.hasNext(); ) {
            Bit b = it.next();
            b.age += dt;
            if (b.kind == LEAF || b.kind == PETAL) { b.vx += Math.sin((b.age + b.rot) * 0.08) * 0.02 * dt; b.vy = Math.min(b.vy + 0.004 * dt, 0.9); }
            if (b.kind == INK) { b.vx *= Math.pow(0.86, dt); b.vy *= Math.pow(0.86, dt); }
            if (b.kind == DRIP) b.vy = Math.min(b.vy + 0.01 * dt, 0.5);
            b.x += b.vx * dt; b.y += b.vy * dt; b.rot += b.spin * dt;
            if (b.age >= b.life) it.remove();
        }
    }
    private static void drawBits() {
        for (Bit b : BITS) {
            if(mask!=null&&!mask.allows(b.x,b.y,b.size*(b.kind==DRIP?8:3)))continue;
            double f = b.age / b.life, a = f < 0.15 ? f / 0.15 : f > 0.75 ? (1 - f) / 0.25 : 1;
            switch (b.kind) {
                case LEAF: case PETAL: {
                    GlStateManager.pushMatrix();
                    try{GlStateManager.translate(b.x,b.y,0);
                    GlStateManager.rotate((float) b.rot, 0, 0, 1);
                    double s = b.size, squeeze = 0.55 + 0.45 * Math.abs(Math.cos(Math.toRadians(b.rot * 1.7)));
                    int c = b.color;
                    AnimeDraw.ribbon(new double[] {-s, 0, s}, new double[] {0, 0, 0}, new double[] {0.2, s * squeeze, 0.2},
                            new int[] {AnimeDraw.argb(AnimeDraw.mix(c, 0x000000, 0.25), a * 0.9), AnimeDraw.argb(c, a * 0.95), AnimeDraw.argb(AnimeDraw.mix(c, 0xFFFFFF, 0.3), a * 0.9)}, false);
                    if (b.kind == LEAF) SkinDraw.line(-s, 0, s, 0, .35, SkinDraw.alpha(AnimeDraw.mix(c, 0x000000, 0.45), (int) (a * 200)));
                    }finally{GlStateManager.popMatrix();}
                    break;
                }
                case MOTE: AnimeDraw.glow(b.x, b.y, b.size * 2.4, b.size * 2.4, b.color, 0.55 * a, true); AnimeDraw.glow(b.x, b.y, b.size, b.size, 0xFFFFFF, 0.6 * a, true); break;
                case INK: AnimeDraw.blob(b.x, b.y, b.size * (1 - f * 0.3), (long) b.rot, b.color, 0.8 * a); break;
                case DRIP: AnimeDraw.vertical(b.x - b.size / 2, b.y - b.size * 6, b.size, b.size * 6, AnimeDraw.argb(b.color, 0), AnimeDraw.argb(b.color, 0.85 * a), false); AnimeDraw.blob(b.x, b.y, b.size * 0.9, 3, b.color, 0.85 * a); break;
                default: break;
            }
        }
    }

    // ---- mood ----------------------------------------------------------------------------------
    /** Ambient tint from the world: dusk orange, night indigo, rain grey, danger red. */
    private static int mood() {
        int c = 0x3FA8FF;
        if (MC.world != null) {
            long t = MC.world.getWorldTime() % 24000;
            double night = t > 13000 && t < 23000 ? 1 : 0, dusk = Math.max(0, 1 - Math.abs(t - 12500) / 1200.0) + Math.max(0, 1 - Math.abs(t - 23300) / 900.0);
            c = AnimeDraw.mix(c, 0x5A4AC8, night * 0.7);
            c = AnimeDraw.mix(c, 0xFF8A4A, Math.min(1, dusk) * 0.7);
            if (MC.world.isRaining()) c = AnimeDraw.mix(c, 0x8A98A8, 0.5);
        }
        if (MC.player != null && MC.player.getMaxHealth() > 0) c = AnimeDraw.mix(c, 0xFF3A2A, Math.max(0, (0.35 - MC.player.getHealth() / MC.player.getMaxHealth()) / 0.35));
        return c;
    }

    // ---- events ------------------------------------------------------------------------------
    @SubscribeEvent public void opened(GuiScreenEvent.InitGuiEvent.Post e) {
        if(live(e.getGui())&&(e.getGui()!=screen||screenWidth!=e.getGui().width||screenHeight!=e.getGui().height))reset(e.getGui());
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;context();if(screen!=MC.currentScreen||!on())reset(MC.currentScreen);if(!motion()){BITS.clear();CLOCK.reset();}}

    @SubscribeEvent(priority = EventPriority.LOW)
    public void background(GuiScreenEvent.BackgroundDrawnEvent e) {
        if(!on()||!live(e.getGui()))return;
        try(AnimeDraw.Scope state=new AnimeDraw.Scope()){
        GuiScreen g=e.getGui();if(screen!=g||screenWidth!=g.width||screenHeight!=g.height)reset(g);
        int w = g.width, h = g.height;
        boolean move=motion();double t=move?now():openedAt;
        int c=mood();
        if (g instanceof GuiGameOver) return;
        if(g instanceof GuiContainer){mask=mask((GuiContainer)g);container(w,h,t,c,move);return;}mask=null;
        if (g instanceof GuiChat) return;
        // Pause, options and any other in-world menu: layered ink-and-chakra backdrop.
        AnimeDraw.vertical(0, 0, w, h * 0.5, AnimeDraw.argb(0x05080C, 0.35), AnimeDraw.argb(0x05080C, 0.0), false);
        AnimeDraw.vertical(0, h * 0.55, w, h * 0.45, AnimeDraw.argb(c, 0.0), AnimeDraw.argb(c, 0.22), true);
        AnimeDraw.glow(w / 2.0, h * 0.42, w * 0.55, h * 0.45, c, 0.12 + (move ? 0.04 * Math.sin(t * 0.04) : 0), true);
        double rot = move ? t * 0.004 : 0;
        AnimeDraw.spiral(w / 2.0, h * 0.42, Math.min(w, h) * 0.42, 3.2, 6, c, 0.10, rot, true);
        AnimeDraw.spiral(w / 2.0, h * 0.42, Math.min(w, h) * 0.42, 3.2, 2, 0xFFFFFF, 0.06, rot + Math.PI, true);
        AnimeDraw.halo(w / 2.0, h * 0.42, Math.min(w, h) * 0.46, 3, c, 0.10, true);
        // Brush-stroke frame top and bottom.
        brushBand(0, 0, w, 10, 0x0A0C10, 0.75, t, move);
        brushBand(0, h - 12, w, 12, 0x0A0C10, 0.75, t + 40, move);
        for (int k = 0; k < 9; k++) {
            double px = (k + 0.5) * w / 9.0 + (move ? Math.sin(t * 0.03 + k) * 10 : 0), hh = 18 + 16 * (0.5 + 0.5 * Math.sin(t * 0.05 + k * 1.3));
            AnimeDraw.flame(px, h - 8, 22, move ? hh : 20, t * 0.08 + k, c, 0xFFFFFF, 0.16, true);
        }
        if(g instanceof GuiMainMenu||g instanceof GuiDownloadTerrain)return;
        if (move) {
            double dt=CLOCK.step(now());
            if (RNG.nextDouble() < 0.10 * dt) emit(LEAF, RNG.nextDouble() * w, -6, 0.3 + RNG.nextDouble() * 0.4, 0.25 + RNG.nextDouble() * 0.3, 3 + RNG.nextDouble() * 2.5, 260, RNG.nextBoolean() ? 0x6E9E3A : 0xB8862A);
            if (RNG.nextDouble() < 0.25 * dt) emit(MOTE, RNG.nextDouble() * w, h + 4, (RNG.nextDouble() - 0.5) * 0.2, -0.3 - RNG.nextDouble() * 0.4, 1 + RNG.nextDouble() * 1.4, 160, c);
            step(dt);
        }
        drawBits();
        GlStateManager.color(1, 1, 1, 1);
            }
    }

    private static void brushBand(double x, double y, double w, double h, int rgb, double alpha, double t, boolean move) {
        int n = 64;
        double[] xs = new double[n], ys = new double[n], ws = new double[n];
        int[] cs = new int[n];
        for (int i = 0; i < n; i++) {
            double f = i / (n - 1.0);
            xs[i] = x + f * w; ys[i] = y + h / 2 + Math.sin(f * 13 + (move ? t * 0.01 : 0)) * 1.2;
            ws[i] = h * (0.75 + 0.35 * FableHudMath.noise(91, i));
            cs[i] = AnimeDraw.argb(rgb, alpha * (0.8 + 0.2 * FableHudMath.noise(97, i)));
        }
        AnimeDraw.ribbon(xs, ys, ws, cs, false);
    }

    private static void death(int w, int h, double t, boolean move) {
        double age=Math.max(0,t-openedAt),bleed=move?Math.min(1,age/40):1;
        int red = 0x8A0A0A;
        AnimeDraw.vertical(0, 0, w, h * 0.35 * bleed, AnimeDraw.argb(red, 0.75), AnimeDraw.argb(red, 0), false);
        AnimeDraw.vertical(0, h - h * 0.35 * bleed, w, h * 0.35 * bleed, AnimeDraw.argb(red, 0), AnimeDraw.argb(red, 0.75), false);
        AnimeDraw.gradient(0, 0, w * 0.3 * bleed, h, AnimeDraw.argb(red, 0.6), AnimeDraw.argb(red, 0), AnimeDraw.argb(red, 0), AnimeDraw.argb(red, 0.6), false);
        AnimeDraw.gradient(w - w * 0.3 * bleed, 0, w * 0.3 * bleed, h, AnimeDraw.argb(red, 0), AnimeDraw.argb(red, 0.6), AnimeDraw.argb(red, 0.6), AnimeDraw.argb(red, 0), false);
        double beat = FableHudMath.heartbeat(t, 0.025);
        AnimeDraw.halo(w / 2.0, h / 2.0, Math.max(w, h) * 0.62, Math.max(w, h) * 0.18, 0xFF2A1A, 0.10 + 0.14 * beat, true);
        if (move) {
            double dt=CLOCK.step(now());
            if (RNG.nextDouble() < 0.12 * dt) emit(DRIP, RNG.nextDouble() * w, h * 0.3 * bleed * RNG.nextDouble(), 0, 0.05, 1.5 + RNG.nextDouble() * 2, 120, 0x6A0606);
            step(dt);
        }
        drawBits();
        if(move&&sounds()&&beat>0.6&&age-lastBeat>20) { lastBeat = age; MC.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.BLOCK_NOTE_BASEDRUM, 0.5f)); }
        GlStateManager.color(1, 1, 1, 1);
    }
    private static double lastBeat = -100;

    private static void container(int w, int h, double t, int c, boolean move) {
        double left=mask==null?0:mask.leftMargin(),right=mask==null?0:mask.rightMargin(),bottom=mask==null?h:mask.bottomStart();
        AnimeDraw.gradient(0,0,left,h, AnimeDraw.argb(0x000000, 0.45), AnimeDraw.argb(0x000000, 0), AnimeDraw.argb(0x000000, 0), AnimeDraw.argb(0x000000, 0.45), false);
        AnimeDraw.gradient(w-right,0,right,h, AnimeDraw.argb(0x000000, 0), AnimeDraw.argb(0x000000, 0.45), AnimeDraw.argb(0x000000, 0.45), AnimeDraw.argb(0x000000, 0), false);
        AnimeDraw.vertical(0,bottom,w,h-bottom, AnimeDraw.argb(c, 0), AnimeDraw.argb(c, 0.18), true);
        if (move) {
            double dt=CLOCK.step(now());
            // Motes rise only in the outer margins so they never cover slots.
            if (RNG.nextDouble() < 0.15 * dt) { double x=RNG.nextBoolean()?RNG.nextDouble()*left:w-RNG.nextDouble()*right;emit(MOTE, x, h + 4, (RNG.nextDouble() - 0.5) * 0.15, -0.25 - RNG.nextDouble() * 0.3, 1 + RNG.nextDouble(), 180, c); }
            step(dt);
        }
        drawBits();
        GlStateManager.color(1, 1, 1, 1);
    }

    @SuppressWarnings("unchecked")
    private static List<GuiButton> buttons(GuiScreen g) {
        try {
            if (buttonField == null) {
                for (String n : new String[] {"buttonList", "field_146292_n"}) {
                    try { buttonField = GuiScreen.class.getDeclaredField(n); buttonField.setAccessible(true); break; } catch (NoSuchFieldException ignored) {}
                }
            }
            List<GuiButton> result=buttonField==null?java.util.Collections.emptyList():(List<GuiButton>)buttonField.get(g);return result.size()<=512?result:java.util.Collections.emptyList();
        } catch (IllegalAccessException e) { return java.util.Collections.emptyList(); }
    }

    /** The input font is drawn later by native chat, so this band cannot darken typed text. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void chat(GuiScreenEvent.DrawScreenEvent.Pre e){
        if(!on()||!(e.getGui() instanceof GuiChat)||!live(e.getGui()))return;
        GuiScreen g=e.getGui();if(screen!=g||screenWidth!=g.width||screenHeight!=g.height)reset(g);
        try(AnimeDraw.Scope state=new AnimeDraw.Scope()){
            boolean move=motion();double t=move?now():openedAt;
            brushBand(0,g.height-15,g.width,16,0x05080C,.45,t,move);
            SkinDraw.line(2,g.height-2.5,g.width-2,g.height-2.5,.6,SkinDraw.alpha(mood(),160));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void drawn(GuiScreenEvent.DrawScreenEvent.Post e) {
        if(!on()||!live(e.getGui()))return;
        try(AnimeDraw.Scope state=new AnimeDraw.Scope()){
        GuiScreen g=e.getGui();if(screen!=g||screenWidth!=g.width||screenHeight!=g.height)reset(g);
        boolean move=motion();double t=move?now():openedAt;
        int c = mood();
        GlStateManager.disableDepth();
        if (g instanceof GuiMainMenu) title(g.width, g.height, t, move);
        if (g instanceof GuiGameOver) death(g.width, g.height, t, move);
        if (g instanceof GuiDownloadTerrain) loading(g.width, g.height, t);
        // Button reactions.
        GuiButton over = null;
        for(GuiButton b:buttons(g))if(b!=null&&b.visible&&b.enabled&&b.width>0&&b.height>0){if(b.isMouseOver())over=b;else if(over==null&&b instanceof NarutoInk.Button&&((NarutoInk.Button)b).focused)over=b;}
        if (over != hovered) {
            hovered = over; hoverAt = t;
            if(over!=null&&move&&sounds()&&now()-hoverSoundAt>=8){hoverSoundAt=now();MC.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.BLOCK_NOTE_HAT, 1.7f+RNG.nextFloat()*.2f));}
        }
        if (hovered != null && safeButton(g,hovered)) {
            GuiButton b = hovered;
            double p = move ? FableHudMath.backOut(Math.min(1, (t - hoverAt) / 5)) : 1;
            double y = b.y + b.height - 1.5, x0 = b.x + 3, len = (b.width - 6) * p;
            int n = 24; double[] xs = new double[n], ys = new double[n], ws = new double[n]; int[] cs = new int[n];
            for (int i = 0; i < n; i++) { double f = i / (n - 1.0); xs[i] = x0 + f * len; ys[i] = y + Math.sin(f * 7) * 0.4; ws[i] = 1.8 * Math.sin(Math.PI * (0.1 + 0.9 * f)) + 0.4; cs[i] = AnimeDraw.argb(AnimeDraw.mix(c, 0xFFFFFF, 0.35), 0.9); }
            AnimeDraw.ribbon(xs, ys, ws, cs, true);
            AnimeDraw.glow(b.x + b.width / 2.0, b.y + b.height / 2.0, b.width * 0.6, b.height, c, 0.18 * p, true);
            AnimeDraw.kunai(b.x - 2 - (1 - p) * 6, b.y + b.height / 2.0, 1, 7, p);
        }
        // Screen-open brush reveal (ink sweeps off to the right).
        double open = (t - openedAt) / 7.0;
        if (move && open < 1 && !(g instanceof GuiChat) && !(g instanceof GuiContainer) && !(g instanceof GuiGameOver)) {
            double edge = g.width * FableHudMath.backOut(open) * 1.1;
            AnimeDraw.gradient(edge - 40, 0, 40, g.height, AnimeDraw.argb(0x05080C, 0.9), AnimeDraw.argb(0x05080C, 0), AnimeDraw.argb(0x05080C, 0), AnimeDraw.argb(0x05080C, 0.9), false);
            AnimeDraw.gradient(edge, 0, Math.max(0, g.width - edge), g.height, AnimeDraw.argb(0x05080C, 0.9), AnimeDraw.argb(0x05080C, 0.9), AnimeDraw.argb(0x05080C, 0.9), AnimeDraw.argb(0x05080C, 0.9), false);
        }
        GlStateManager.enableDepth();
        GlStateManager.color(1, 1, 1, 1);
            }
    }

    @SubscribeEvent public void clicked(GuiScreenEvent.ActionPerformedEvent.Post e) {
        if(!on()||!live(e.getGui())||e.getButton()==null||!e.getButton().visible||!e.getButton().enabled)return;
        GuiButton b = e.getButton();
        if(sounds()&&now()-clickSoundAt>=5){clickSoundAt=now();MC.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.BLOCK_WOOD_BUTTON_CLICK_ON,.9f+RNG.nextFloat()*.25f));}
        if (!motion()||!safeButton(e.getGui(),b)) return;
        for (int i = 0; i < 10; i++) {
            double a = RNG.nextDouble() * Math.PI * 2, s = 0.6 + RNG.nextDouble() * 1.6;
            emit(INK, b.x + b.width / 2.0, b.y + b.height / 2.0, Math.cos(a) * s * 2, Math.sin(a) * s, 1.2 + RNG.nextDouble() * 2.2, 14 + RNG.nextDouble() * 8, i % 3 == 0 ? 0x8A1A1A : 0x0A0C10);
        }
    }

    private static boolean safeButton(GuiScreen g,GuiButton button){
        if(!(g instanceof GuiContainer))return true;
        MenuFxRules.Mask slots=mask((GuiContainer)g);
        double mx=Math.max(24,button.width*.12),my=Math.max(12,button.height*.6);
        return slots.allowsRectangle(button.x-mx,button.y-my,button.width+2*mx,button.height+2*my);
    }

    private static void title(int w, int h, double t, boolean move) {
        AnimeDraw.vertical(0, h * 0.6, w, h * 0.4, AnimeDraw.argb(0x3FA8FF, 0), AnimeDraw.argb(0x3FA8FF, 0.16), true);
        if (move) {
            double dt=CLOCK.step(now());
            if (RNG.nextDouble() < 0.18 * dt) emit(PETAL, RNG.nextDouble() * w * 1.2 - w * 0.1, -6, 0.4 + RNG.nextDouble() * 0.5, 0.3 + RNG.nextDouble() * 0.3, 2.2 + RNG.nextDouble() * 1.8, 300, RNG.nextBoolean() ? 0xF4B8C8 : 0xFFD8E2);
            if (RNG.nextDouble() < 0.2 * dt) emit(MOTE, RNG.nextDouble() * w, h + 4, (RNG.nextDouble() - 0.5) * 0.2, -0.3 - RNG.nextDouble() * 0.3, 1 + RNG.nextDouble(), 200, 0x7FD8FF);
            step(dt);
        }
        drawBits();
    }

    private static void loading(int w, int h, double t) {
        double cx = w / 2.0, cy = h / 2.0 + 30;
        AnimeDraw.glow(cx, cy, 40, 40, 0x3FA8FF, 0.35, true);
        AnimeDraw.spiral(cx, cy, 18, 2.6, 2.4, 0x7FD8FF, 0.8, -t * 0.15, true);
        AnimeDraw.spiral(cx, cy, 18, 2.6, 1.0, 0xFFFFFF, 0.6, -t * 0.15 + Math.PI, true);
        AnimeDraw.halo(cx, cy, 22 + Math.sin(t * 0.2) * 1.5, 2, 0x3FA8FF, 0.6, true);
        String line = "Gathering chakra" + "...".substring(0, (int) (t / 8) % 4);
        MC.fontRenderer.drawStringWithShadow(line, (float) (cx - MC.fontRenderer.getStringWidth("Gathering chakra...") / 2.0), (float) (cy + 30), 0xFFDCEBFF);
    }

    // Confirmed cast FX use HotbarFx owner receipts, never RightClickItem intent.

    // ---- tooltips -------------------------------------------------------------------------------
    @SubscribeEvent public void tooltipColor(RenderTooltipEvent.Color e) {
        if(!on()||!MC.isCallingFromMinecraftThread()||e.isCancelable()&&e.isCanceled())return;
        ItemStack s=e.getStack();
        int accent = accent(s);
        e.setBackground(0xF00A0C10);
        e.setBorderStart(0xFF000000 | accent);
        e.setBorderEnd(0xFF000000 | AnimeDraw.mix(accent, 0x000000, 0.55));
    }

    @SubscribeEvent public void tooltipPost(RenderTooltipEvent.PostBackground e){
        if(!on()||!MC.isCallingFromMinecraftThread()||e.isCancelable()&&e.isCanceled()||e.getWidth()<1||e.getHeight()<1||e.getWidth()>16384||e.getHeight()>16384)return;
        try(AnimeDraw.Scope state=new AnimeDraw.Scope()){
            int x=e.getX()-3,y=e.getY()-3,w=e.getWidth()+6,h=e.getHeight()+6,c=accent(e.getStack());
            GlStateManager.pushMatrix();try{
                GlStateManager.translate(0,0,300);AnimeDraw.vertical(x+1,y+1,w-2,Math.min(8,h-2),AnimeDraw.argb(c,.25),AnimeDraw.argb(c,0),true);
                for(int[] k:new int[][]{{x,y,1},{x+w,y,-1}}){SkinDraw.line(k[0],k[1],k[0]+k[2]*5,k[1],.8,0xFFFFFFFF);SkinDraw.line(k[0],k[1],k[0],k[1]+5,.8,0xFFFFFFFF);}
            }finally{GlStateManager.popMatrix();}
        }
    }

    private static int accent(ItemStack s) {
        if (s == null || s.isEmpty()) return 0x9DDDDC;
        if (s.getItem().getRegistryName() != null && "valeorders".equals(s.getItem().getRegistryName().getNamespace())) return 0xE9BB79;
        EnumRarity r = s.getRarity();
        return r == EnumRarity.EPIC ? 0xC88AFF : r == EnumRarity.RARE ? 0x7FD8FF : r == EnumRarity.UNCOMMON ? 0xFFE08A : 0x9DDDDC;
    }
}
