package village.fieldhud;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;

/**
 * Motion and environment reactions layered onto the existing scroll hotbar and jutsu rail.
 * Their art and layout stay as they are; this adds body sway/bob/landing squash, damage shake,
 * per-slot lift, item pop/tilt/sparkle, selector ghosts, climate and weather tints, rail
 * slide-out, cooldown border fill, ready/cast flashes and HUD pixel weather.
 */
public final class HotbarFx {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final Random RNG = new Random();
    private HotbarFx() {}

    // ---- shared body motion ----------------------------------------------------------------------
    private static double dt, barLast = -1;
    /** Hotbar per-frame step (environment springs are shared; slot springs use the hotbar's own clock). */
    public static double frame(double time, boolean motion) {
        FableEnv.update(time);
        dt = barLast < 0 || time < barLast ? 0 : Math.min(5, time - barLast);
        barLast = time;
        if (!motion) dt = 100;
        return dt;
    }

    /** Pushes a matrix that sways, bobs, squashes and shakes the hotbar around its bottom centre. */
    public static void pushBody(Layout.Box b, double time, boolean motion, double scale) {
        GlStateManager.pushMatrix();
        double[] shake = FableHudMath.shake(2.2 * FableEnv.hurt, time);
        double ox = (motion ? FableEnv.SWAY.value : 0) * scale + shake[0], oy = (motion ? FableEnv.BOB.value + FableEnv.SQUASH.value * 1.5 : 0) * scale + shake[1];
        GlStateManager.translate(FableHudMath.snap(ox), FableHudMath.snap(oy), 0);
        double sq = motion && scale > 0.9 ? FableHudMath.clamp(FableEnv.SQUASH.value * 0.06, -0.08, 0.1) : 0;
        if (sq != 0) {
            double cx = b.x + b.w / 2.0, cy = b.y + b.h;
            GlStateManager.translate(cx, cy, 0); GlStateManager.scale(1 + sq, 1 - sq, 1); GlStateManager.translate(-cx, -cy, 0);
        }
    }

    public static void pop() { GlStateManager.popMatrix(); }

    /** Climate / damage / heartbeat / lightning tint over a frame rectangle (drawn after the art). */
    public static void tint(double x, double y, double w, double h, double time) {
        double hurt = Math.max(FableEnv.hurt, FableEnv.heartbeat(time));
        if (hurt > 0.02) SkinDraw.outline(x, y, w, h, 3, 1.2, SkinDraw.alpha(0xE0384A, (int) (220 * hurt)));
        if (FableEnv.flash > 0.02) SkinDraw.rounded(x, y, w, h, 3, SkinDraw.alpha(0xFFFFFF, (int) (90 * FableEnv.flash)));
        if (FableEnv.frost > 0.05) {
            SkinDraw.outline(x + .5, y + .5, w - 1, h - 1, 3, .8, SkinDraw.alpha(0xCDEFFF, (int) (150 * FableEnv.frost)));
            int n = (int) (FableEnv.frost * 12);
            for (int i = 0; i < n; i++) {
                double fx = FableHudMath.noise(5, i) * 16, fy = FableHudMath.noise(9, i) * 7;
                SkinDraw.rounded(x + 2 + fx, y + 2 + fy, 1, 1, 0, SkinDraw.alpha(0xF0FBFF, (int) (210 * FableEnv.frost)));
                SkinDraw.rounded(x + w - 3 - fx, y + h - 3 - fy, 1, 1, 0, SkinDraw.alpha(0xF0FBFF, (int) (210 * FableEnv.frost)));
            }
        }
        double hot = Math.max(FableEnv.heat * 0.6, FableEnv.burning);
        if (hot > 0.05) for (double px = x + 4; px < x + w - 4; px += 2) {
            double dy = Math.round(Math.sin(time * 0.35 + px * 0.4) * 1.2);
            SkinDraw.rounded(px, y - 1 + dy, 1, 1, 0, SkinDraw.alpha(0xFFB347, (int) (150 * hot)));
        }
        if (FableEnv.wet > 0.05) SkinDraw.line(x + 4, y + 1.5, x + w - 4, y + 1.5, .6, SkinDraw.alpha(0x9CC8F0, (int) (160 * FableEnv.wet)));
        if (FableEnv.submerged > 0.05) SkinDraw.rounded(x, y, w, h, 3, SkinDraw.alpha(0x2A6AA0, (int) (45 * FableEnv.submerged)));
        if (FableEnv.dark > 0.2) SkinDraw.outline(x + 1, y + 1, w - 2, h - 2, 3, .5, SkinDraw.alpha(0xFFD27A, (int) (80 * FableEnv.dark)));
    }

    // ---- hotbar slots ------------------------------------------------------------------------------
    private static final int SLOTS = 9;
    private static final FableHudMath.Spring[] LIFT = new FableHudMath.Spring[SLOTS], TILT = new FableHudMath.Spring[SLOTS], POP = new FableHudMath.Spring[SLOTS];
    private static final ItemStack[] SEEN = new ItemStack[SLOTS];
    private static final int[] COUNT = new int[SLOTS];
    private static final double[] GHOST = new double[5];
    private static int lastSelected = -1;
    static { for (int i = 0; i < SLOTS; i++) { LIFT[i] = new FableHudMath.Spring(0.4, 0.55, 0); TILT[i] = new FableHudMath.Spring(0.25, 0.5, 0); POP[i] = new FableHudMath.Spring(0.45, 0.5, 1); SEEN[i] = ItemStack.EMPTY; } }

    /** Tracks selection changes and per-slot springs; {@code selector} is the eased focus position. */
    public static void slots(int selected, double selector) {
        if (lastSelected >= 0 && selected != lastSelected) {
            int d = selected - lastSelected;
            if (Math.abs(d) > 4) d = -Integer.signum(d);
            for (int i = 0; i < SLOTS; i++) TILT[i].kick(-Integer.signum(d) * 1.6);
        }
        lastSelected = selected;
        for (int i = GHOST.length - 1; i > 0; i--) GHOST[i] = GHOST[i - 1];
        GHOST[0] = selector;
        for (int i = 0; i < SLOTS; i++) {
            LIFT[i].target = FableHudMath.lift(i - selector); LIFT[i].step(dt);
            TILT[i].target = 0; TILT[i].step(dt);
            POP[i].target = 1; POP[i].step(dt);
        }
    }

    /** Pixel lift of slot i (draw the slot well and item this many pixels higher). */
    public static int lift(int i) { return FableHudMath.snap(LIFT[i].value); }

    /** Fading outlines where the selector just was (fast scrolls leave a trail). */
    public static void ghosts(Layout.Box b, double selector, int color) {
        for (int g = GHOST.length - 1; g >= 1; g--) {
            if (Math.abs(GHOST[g] - selector) < 0.25) continue;
            double gx = b.x + 22 + GHOST[g] * ScrollGeometry.STRIDE, gy = b.y + 7 + ScrollGeometry.curve(GHOST[g], HudConfig.curve);
            SkinDraw.outline(gx, gy, 19, 22, 1.5, .6, SkinDraw.alpha(color, (int) (110 * (1 - g / (double) GHOST.length))));
        }
    }

    /** Draws an item with pickup pop, count bump, scroll tilt, selected bob and pickup sparkles. */
    public static void item(int i, ItemStack stack, int x, int y, boolean selected, double time) {
        boolean changed = !ItemStack.areItemsEqual(stack, SEEN[i]);
        if (changed) {
            if (!stack.isEmpty()) { POP[i].snap(0.2); POP[i].target = 1; sparkle(x + 8, y + 8, 0xFFF4E3BF, 10); }
            else if (!SEEN[i].isEmpty()) poof(x + 8, y + 8);
            SEEN[i] = stack.copy();
        } else if (!stack.isEmpty() && stack.getCount() != COUNT[i]) POP[i].kick(stack.getCount() > COUNT[i] ? 0.12 : -0.08);
        COUNT[i] = stack.getCount();
        if (stack.isEmpty()) return;
        double scale = POP[i].value * (selected ? 1.1 + 0.03 * Math.sin(time * 0.2) : 1);
        double tilt = TILT[i].value * 6 + (selected ? Math.sin(time * 0.1) * 2 : 0);
        int ly = lift(i);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 8, y + 8 - ly, 0);
        GlStateManager.rotate((float) tilt, 0, 0, 1);
        GlStateManager.scale(scale, scale, 1);
        GlStateManager.translate(-8, -8, 0);
        GlStateManager.color(1, 1, 1, 1);
        MC.getRenderItem().renderItemAndEffectIntoGUI(MC.player, stack, 0, 0);
        MC.getRenderItem().renderItemOverlays(MC.fontRenderer, stack, 0, 0);
        GlStateManager.popMatrix();
        if (stack.isItemStackDamageable() && stack.getMaxDamage() > 0 && 1 - stack.getItemDamage() / (double) stack.getMaxDamage() < 0.15 && (int) (time / 6) % 2 == 0) {
            GlStateManager.disableDepth();
            Gui.drawRect(x + 13, y - 1 - ly, x + 17, y - ly, 0xFFE83A3A);
            Gui.drawRect(x + 16, y - 1 - ly, x + 17, y + 3 - ly, 0xFFE83A3A);
            GlStateManager.enableDepth();
        }
    }

    // ---- rail ---------------------------------------------------------------------------------------
    private static final int ROWS = JutsuLoadout.SIZE;
    private static final FableHudMath.Spring[] OUT = new FableHudMath.Spring[ROWS], RECOIL = new FableHudMath.Spring[ROWS];
    private static final double[] READY_AT = new double[ROWS], CAST_AT = new double[ROWS];
    private static final boolean[] WAS_READY = new boolean[ROWS];
    private static final String[] IDS = new String[ROWS];
    private static double railShownAt = -1, railLast = -1, railDt;
    static { for (int i = 0; i < ROWS; i++) { OUT[i] = new FableHudMath.Spring(0.35, 0.55, 0); RECOIL[i] = new FableHudMath.Spring(0.45, 0.5, 0); READY_AT[i] = CAST_AT[i] = -100; } }

    public static void railFrame(double time, boolean motion, boolean visible) {
        if (!visible) { railShownAt = -1; return; }
        railDt = railLast < 0 || time < railLast ? 0 : Math.min(5, time - railLast);
        railLast = time;
        if (!motion) railDt = 100;
        if (railShownAt < 0) railShownAt = time;
    }

    /** Pushes the per-row offset: staggered entry, selected slide-out toward screen centre, cast recoil, counter-sway. */
    public static void pushRow(int row, boolean selected, double focus, double time, boolean motion) {
        OUT[row].target = selected ? 4 + focus * 3 : focus * 1.5; OUT[row].step(railDt);
        RECOIL[row].target = 0; RECOIL[row].step(railDt);
        double enter = motion ? FableHudMath.backOut(FableHudMath.clamp((time - railShownAt - row * 2.5) / 9, 0, 1)) : 1;
        double[] shake = FableHudMath.shake(1.5 * FableEnv.hurt, time + 7);
        double dx = -OUT[row].value + RECOIL[row].value + (1 - enter) * 40 + (motion ? -FableEnv.SWAY.value * 0.5 : 0) + shake[0];
        double dy = (motion ? -FableEnv.BOB.value * 0.4 : 0) + shake[1];
        GlStateManager.pushMatrix();
        GlStateManager.translate(FableHudMath.snap(dx), FableHudMath.snap(dy), 0);
    }

    /** Detects ready/cast transitions for a row; returns {readyFlash, castFlash}. */
    public static double[] railState(int row, JutsuEntry entry, boolean ready, double fraction, double time) {
        String id = entry == null ? null : entry.id;
        if (id == null ? IDS[row] != null : !id.equals(IDS[row])) { IDS[row] = id; WAS_READY[row] = ready; }
        if (ready && !WAS_READY[row]) READY_AT[row] = time;
        if (!ready && WAS_READY[row] && fraction > 0) { CAST_AT[row] = time; RECOIL[row].kick(2.2); }
        WAS_READY[row] = ready;
        return new double[] {FableHudMath.pulse((time - READY_AT[row]) / 10), FableHudMath.pulse((time - CAST_AT[row]) / 8)};
    }

    public static boolean justReady(int row, double time) { double a = time - READY_AT[row]; return a >= 0 && a < 1.2; }

    /** Row x offset currently applied (so the selector can follow the slid-out slot). */
    public static double rowOut(int row) { return -OUT[row].value + RECOIL[row].value; }

    /** Fills a slot border clockwise from top-centre with 1px dots (recharge progress). */
    public static void perimeter(double x, double y, double w, double h, double f, int color) {
        int iw = (int) w, ih = (int) h, total = 2 * (iw + ih) - 4, lit = (int) (total * FableHudMath.clamp(f, 0, 1)), start = iw / 2;
        for (int i = 0; i < lit; i++) {
            int p = (start + i) % total; double px, py;
            if (p < iw - 1) { px = x + p; py = y; }
            else if (p < iw + ih - 2) { px = x + iw - 1; py = y + p - (iw - 1); }
            else if (p < 2 * iw + ih - 3) { px = x + iw - 1 - (p - (iw + ih - 2)); py = y + ih - 1; }
            else { px = x; py = y + ih - 1 - (p - (2 * iw + ih - 3)); }
            SkinDraw.rounded(px, py, 1, 1, 0, color);
        }
    }

    // ---- particles -----------------------------------------------------------------------------------
    public static void weather(double x, double y, double w, double h, double time) { FableEnv.weather(x, y, w, h, time, Math.min(dt, 5), RNG); }
    public static void particles() { GlStateManager.disableDepth(); FableEnv.drawParticles(); }

    public static void sparkle(double x, double y, int color, int n) {
        for (int i = 0; i < n; i++) {
            double a = RNG.nextDouble() * Math.PI * 2, s = 0.6 + RNG.nextDouble() * 1.2;
            FableEnv.emit(x, y, Math.cos(a) * s, Math.sin(a) * s - 0.6, 0.06, 14 + RNG.nextInt(8), i % 3 == 0 ? 0xFFFFFFFF : color, RNG.nextInt(3) == 0 ? 2 : 1);
        }
    }

    public static void poof(double x, double y) {
        for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; FableEnv.emit(x, y, Math.cos(a) * 0.7, Math.sin(a) * 0.7, 0, 10, 0xC0B8B8B8, 2); }
    }
}
