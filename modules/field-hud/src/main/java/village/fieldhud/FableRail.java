package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/**
 * Animated pixel-art six-slot jutsu bar on the right (Fable skin). Uses the existing rail model
 * and input (scroll focus, slot keys, server activation); this class only presents it:
 *  - slots slide in one after another the first time the bar appears;
 *  - a spring selector, the selected slot slides out toward the screen centre;
 *  - pixel perimeter cooldown that fills clockwise, then a ready flash and sparkle burst;
 *  - cast kick: the slot recoils and flashes the moment its cooldown starts;
 *  - unusable slots dim behind pixel chains; focus mode widens the rail and lights its rim;
 *  - the rail counter-sways with the player's motion and shares weather/damage reactions.
 */
public final class FableRail extends Gui {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int N = JutsuLoadout.SIZE;
    private static final FableHudMath.Spring SELECT = new FableHudMath.Spring(0.3, 0.6, 0), FOCUS = new FableHudMath.Spring(0.25, 0.7, 0);
    private static final FableHudMath.Spring[] OUT = new FableHudMath.Spring[N], RECOIL = new FableHudMath.Spring[N];
    private static final double[] READY_AT = new double[N], CAST_AT = new double[N];
    private static final boolean[] WAS_READY = new boolean[N];
    private static final String[] IDS = new String[N];
    private static double shownAt = -1, lastTime = -1;
    static { for (int i = 0; i < N; i++) { OUT[i] = new FableHudMath.Spring(0.35, 0.55, 0); RECOIL[i] = new FableHudMath.Spring(0.45, 0.5, 0); READY_AT[i] = CAST_AT[i] = -100; } }
    private FableRail() {}

    public static void draw(HudLayers.Frame frame) {
        if (!JutsuRail.visible() || !JutsuAccess.MODEL.initialized()) { shownAt = -1; return; }
        JutsuRailLayout rail = JutsuRail.layout(frame.resolution.getScaledWidth(), frame.resolution.getScaledHeight());
        if (rail.box == null) return;
        double time = frame.time;
        boolean motion = HudConfig.motion && HudConfig.mode != 2;
        double dt = lastTime < 0 || time < lastTime ? 0 : Math.min(5, time - lastTime);
        lastTime = time;
        if (!motion) dt = 100;
        if (shownAt < 0) { shownAt = time; SELECT.snap(JutsuAccess.MODEL.index()); }
        JutsuRailModel model = JutsuAccess.MODEL;
        boolean fresh = JutsuAccess.fresh();
        SELECT.target = model.index(); SELECT.step(dt);
        FOCUS.target = model.focused() ? 1 : 0; FOCUS.step(dt);
        Layout.Box b = rail.box;
        int size = Math.min(22, rail.slotHeight - 2);
        int ox = FableHudMath.snap(motion ? -FableEnv.SWAY.value * 0.5 : 0), oy = FableHudMath.snap(motion ? -FableEnv.BOB.value * 0.4 : 0);
        double[] shake = FableHudMath.shake(1.5 * FableEnv.hurt, time + 7);
        ox += FableHudMath.snap(shake[0]); oy += FableHudMath.snap(shake[1]);
        int cx = b.x + b.w / 2 + ox;
        double elapsed = model.elapsed(time);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        try {
            // Spine of the rail: widens with focus.
            int spineW = 4 + FableHudMath.snap(FOCUS.value * 4);
            int top = rail.slotY(0) + oy - 3, bottom = rail.slotY(N - 1) + rail.slotHeight + oy + 1;
            int rim = FableHotbar.blend(0xFF3A3F45, 0xFF9DDDDC, FOCUS.value);
            rim = FableHotbar.blend(rim, 0xFF8FD8FF, FableEnv.frost * 0.6);
            rim = FableHotbar.blend(rim, 0xFFD8384A, Math.max(FableEnv.hurt, FableEnv.heartbeat(time)));
            drawRect(cx - spineW / 2 - 1, top, cx + spineW / 2 + 1, bottom, 0xFF0E1114);
            drawRect(cx - spineW / 2, top + 1, cx + spineW / 2, bottom - 1, rim);
            for (int row = 0; row < N; row++) {
                // Staggered slide-in from the right edge on first appearance.
                double enter = motion ? FableHudMath.backOut(FableHudMath.clamp((time - shownAt - row * 2.5) / 9, 0, 1)) : 1;
                if (enter <= 0.001) continue;
                JutsuEntry entry = model.display(row);
                boolean selected = row == model.index();
                boolean enabled = fresh && entry != null && entry.enabled;
                double fraction = entry == null ? 0 : entry.fraction(elapsed);
                boolean ready = entry != null && fresh && entry.canCast && fraction <= 0;
                String id = entry == null ? null : entry.id;
                if (id == null ? IDS[row] != null : !id.equals(IDS[row])) { IDS[row] = id; WAS_READY[row] = ready; }
                if (ready && !WAS_READY[row]) { READY_AT[row] = time; }
                if (!ready && WAS_READY[row] && fraction > 0) { CAST_AT[row] = time; RECOIL[row].kick(2.2); }
                WAS_READY[row] = ready;
                OUT[row].target = selected ? 5 + FOCUS.value * 3 : FOCUS.value * 1.5;
                OUT[row].step(dt);
                RECOIL[row].target = 0; RECOIL[row].step(dt);
                int slide = FableHudMath.snap((1 - enter) * 40);
                int sx = cx - size / 2 - FableHudMath.snap(OUT[row].value) + FableHudMath.snap(RECOIL[row].value) + slide;
                int sy = rail.slotY(row) + oy + (rail.slotHeight - size) / 2;
                slot(sx, sy, size, selected, enabled, entry, fraction, time, row);
                if (time - READY_AT[row] < 1.2 && time - READY_AT[row] >= 0) FableHotbar.sparkle(sx + size / 2.0, sy + size / 2.0, 0xFF9DDDDC, 8);
            }
            // Selector bracket springs between rows.
            int selY = FableHudMath.snap(rail.slotY(0) + oy + (rail.slotHeight - size) / 2 + SELECT.value * rail.slotHeight);
            int selX = cx - size / 2 - FableHudMath.snap(OUT[Math.max(0, Math.min(N - 1, model.index()))].value);
            int col = model.focused() ? 0xFF9DDDDC : 0xFFFFE9B0;
            int stretch = FableHudMath.snap(Math.abs(SELECT.velocity) * 5);
            FableHotbar.outline(selX - 2, selY - 2 - stretch, size + 4, size + 4 + stretch * 2, col);
            for (int[] c : new int[][] {{selX - 3, selY - 3}, {selX + size, selY - 3}, {selX - 3, selY + size + 1}, {selX + size, selY + size + 1}}) drawRect(c[0], c[1], c[0] + 3, c[1] + 2, 0xFFFFFFFF);
            if (FOCUS.value > 0.05) FableHotbar.outline(selX - 4, selY - 4, size + 8, size + 8, SkinDraw.alpha(0x9DDDDC, (int) (120 * FOCUS.value * (0.7 + 0.3 * Math.sin(time * 0.3)))));
            FableEnv.weather(cx - size, top, size * 2, bottom - top, time, dt * 0.5, new java.util.Random((long) (time * 13)));
        } finally {
            if (alphaTest) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private static void slot(int x, int y, int s, boolean selected, boolean enabled, JutsuEntry entry, double fraction, double time, int row) {
        double castFlash = FableHudMath.pulse((time - CAST_AT[row]) / 8), readyFlash = FableHudMath.pulse((time - READY_AT[row]) / 10);
        drawRect(x - 1, y - 1, x + s + 1, y + s + 1, 0xFF0A0C0E);
        int frame = selected ? 0xFF5A5040 : 0xFF3A3F45;
        frame = FableHotbar.blend(frame, 0xFFFFFFFF, castFlash * 0.8);
        frame = FableHotbar.blend(frame, 0xFF9DDDDC, readyFlash);
        drawRect(x, y, x + s, y + s, frame);
        drawRect(x + 1, y + 1, x + s - 1, y + s - 1, FableHotbar.blend(0xFF23282D, 0xFF1A2A33, FableEnv.submerged));
        drawRect(x + 1, y + 1, x + s - 1, y + 2, 0xFF0E1013);
        // Row number badge.
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 2, y + 2, 300);
        GlStateManager.scale(0.5f, 0.5f, 1);
        MC.fontRenderer.drawString(String.valueOf(row + 1), 0, 0, selected ? 0xFFFFE9B0 : 0xFF7F8790);
        GlStateManager.popMatrix();
        if (entry == null) {
            drawRect(x + s / 2 - 2, y + s / 2, x + s / 2 + 2, y + s / 2 + 1, 0xFF4A5056);
            return;
        }
        int ix = x + (s - 16) / 2, iy = y + (s - 16) / 2;
        JutsuRail.icon(entry, ix, iy, enabled ? 255 : 110, readyFlash);
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        if (fraction > 0) {
            // Dark veil drains from the top; a pixel perimeter fills clockwise as it recharges.
            int veil = (int) Math.ceil((s - 2) * fraction);
            drawRect(x + 1, y + 1, x + s - 1, y + 1 + veil, 0xA0081016);
            perimeter(x, y, s, 1 - fraction, 0xFFD9BE85);
        }
        if (!enabled) {
            for (int k = 0; k < s - 2; k += 3) {
                drawRect(x + 1 + k, y + 1 + k, x + 3 + k, y + 2 + k, 0xC0888E94);
                drawRect(x + s - 3 - k, y + 1 + k, x + s - 1 - k, y + 2 + k, 0xC0888E94);
            }
        }
        if (castFlash > 0) FableHotbar.outline(x - 2, y - 2, s + 4, s + 4, SkinDraw.alpha(0xFFFFFF, (int) (220 * castFlash)));
        if (readyFlash > 0) {
            int grow = FableHudMath.snap(readyFlash * 4);
            FableHotbar.outline(x - 1 - grow, y - 1 - grow, s + 2 + grow * 2, s + 2 + grow * 2, SkinDraw.alpha(0x9DDDDC, (int) (230 * readyFlash)));
        }
    }

    /** Fills the slot border clockwise from top-centre, one pixel at a time. */
    private static void perimeter(int x, int y, int s, double f, int color) {
        int total = 4 * (s - 1), lit = (int) (total * FableHudMath.clamp(f, 0, 1));
        int start = (s - 1) / 2;
        for (int i = 0; i < lit; i++) {
            int p = (start + i) % total, px, py;
            if (p < s - 1) { px = x + p; py = y; }
            else if (p < 2 * (s - 1)) { px = x + s - 1; py = y + p - (s - 1); }
            else if (p < 3 * (s - 1)) { px = x + s - 1 - (p - 2 * (s - 1)); py = y + s - 1; }
            else { px = x; py = y + s - 1 - (p - 3 * (s - 1)); }
            drawRect(px, py, px + 1, py + 1, color);
        }
    }
}
