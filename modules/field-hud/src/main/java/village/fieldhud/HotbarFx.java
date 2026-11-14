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


    // ---- anime art layers -----------------------------------------------------------------------
    private static double splashAt = -100, splashX, splashY;
    private static int splashFrom = -1;

    /** Current chakra colour: blue at rest, shifting with danger, fire, cold and focus. */
    public static int chakra(double time) {
        int c = AnimeDraw.mix(0x3FA8FF, 0x7FE8FF, 0.5 + 0.5 * Math.sin(time * 0.05));
        c = AnimeDraw.mix(c, 0xFF3A2A, Math.max(FableEnv.hurt, FableHudMath.clamp((0.3 - FableEnv.health) / 0.3, 0, 1) * 0.8));
        c = AnimeDraw.mix(c, 0xFF8A2A, FableEnv.burning * 0.8);
        c = AnimeDraw.mix(c, 0xCFF4FF, FableEnv.frost * 0.5);
        return c;
    }

    /** Behind the scroll: breathing multi-layer chakra aura and a soft drop shadow. */
    public static void under(Layout.Box b, double time) {
        int c = chakra(time);
        double breathe = 0.75 + 0.25 * Math.sin(time * 0.08);
        double cx = b.x + b.w / 2.0, cy = b.y + 17;
        AnimeDraw.glow(cx, cy + 14, b.w * 0.55, 10, 0x000000, 0.55, false);                  // drop shadow
        AnimeDraw.glow(cx, cy, b.w * 0.68, 34, c, 0.32 * breathe, true);                     // wide aura
        AnimeDraw.glow(cx, cy, b.w * 0.56, 24, c, 0.38 * breathe, true);
        AnimeDraw.glow(cx, cy - 2, b.w * 0.40, 10, AnimeDraw.mix(c, 0xFFFFFF, 0.5), 0.18 * breathe, true);
        // Chakra wisps curling up off the top edge.
        for (int k = 0; k < 7; k++) {
            double px = b.x + 24 + k * (b.w - 48) / 6.0 + Math.sin(time * 0.07 + k) * 6;
            double h = 12 + 10 * (0.5 + 0.5 * Math.sin(time * 0.11 + k * 1.7));
            AnimeDraw.flame(px, b.y + 4, 9, h, time * 0.2 + k, c, 0xFFFFFF, 0.5 * breathe, true);
        }
    }

    /** Between the slot wells and the items: paper fibres, ink wash, gloss, selected seal + flames, kunai, splash, chakra stream, cap seals. */
    public static void over(Layout.Box b, double time, double selector, int selected) {
        int x = b.x, y = b.y, c = chakra(time);
        // Washi fibres in the parchment border.
        for (int i = 0; i < 70; i++) {
            double fx = x + 10 + FableHudMath.noise(41, i) * (b.w - 20), fy = y + 1 + (i % 2 == 0 ? FableHudMath.noise(43, i) * 4 : 26 + FableHudMath.noise(47, i) * 4);
            double len = 2 + FableHudMath.noise(53, i) * 5, ang = (FableHudMath.noise(59, i) - 0.5) * 0.8;
            SkinDraw.line(fx, fy, fx + Math.cos(ang) * len, fy + Math.sin(ang) * len, .35, SkinDraw.alpha(i % 3 == 0 ? 0xFFF6E2 : 0x8A6E4A, 70));
        }
        // Sumi-e ink wash across the well band, darker toward the bottom.
        AnimeDraw.vertical(x + 17, y + 6, b.w - 34, 25, AnimeDraw.argb(0x0A1A2A, 0.0), AnimeDraw.argb(0x000000, 0.35), false);
        for (int i = 0; i < 9; i++) {
            int sx = ScrollGeometry.slotX(b, i), sy = ScrollGeometry.slotY(b, i, HudConfig.curve) - lift(i);
            AnimeDraw.vertical(sx + 1, sy + 1, 17, 9, AnimeDraw.argb(0xFFFFFF, 0.14), AnimeDraw.argb(0xFFFFFF, 0.0), false); // gloss
            AnimeDraw.vertical(sx + 1, sy + 14, 17, 7, AnimeDraw.argb(c, 0.0), AnimeDraw.argb(c, 0.16), true);           // chakra underlight
            SkinDraw.line(sx + 2, sy + 1.2, sx + 9, sy + 1.2, .5, SkinDraw.alpha(0xFFFFFF, 90));
        }
        // Selected slot: rotating swirl seal, chakra flames and kunai brackets.
        double sx = x + 22 + selector * ScrollGeometry.STRIDE + 9.5, sy = y + 7 + ScrollGeometry.curve(selector, HudConfig.curve) - FableHudMath.lift(0) + 11;
        AnimeDraw.glow(sx, sy, 15, 15, c, 0.45, true);
        AnimeDraw.spiral(sx, sy, 9, 2.6, 1.6, AnimeDraw.mix(c, 0xFFFFFF, 0.3), 0.55, -time * 0.12, true);
        AnimeDraw.spiral(sx, sy, 9, 2.6, 0.8, 0xFFFFFF, 0.35, -time * 0.12 + Math.PI, true);
        AnimeDraw.halo(sx, sy, 11 + Math.sin(time * 0.2), 1.6, c, 0.5, true);
        for (int k = 0; k < 6; k++) {
            double fx = sx - 8 + k * 3.2, flick = FableHudMath.noise(k, (long) (time / 2));
            AnimeDraw.flame(fx, sy + 11, 4.5, 9 + flick * 9, time * 0.4 + k, c, 0xE8FBFF, 0.55, true);
        }
        AnimeDraw.kunai(sx - 13, sy, 1, 7, 0.95);
        AnimeDraw.kunai(sx + 13, sy, -1, 7, 0.95);
        // Ink splash where the selection lands.
        if (selected != splashFrom) { if (splashFrom >= 0) { splashAt = time; splashX = sx; splashY = sy; } splashFrom = selected; }
        double sp = (time - splashAt) / 12;
        if (sp >= 0 && sp < 1) for (int i = 0; i < 9; i++) {
            double a = i * 0.698 + FableHudMath.noise(7, i), d = 4 + 14 * FableHudMath.backOut(sp) * (0.6 + 0.4 * FableHudMath.noise(11, i));
            AnimeDraw.blob(splashX + Math.cos(a) * d, splashY + Math.sin(a) * d * 0.6, 1.2 + 2.2 * (1 - sp) * FableHudMath.noise(13, i), i, i % 3 == 0 ? 0x8A1A1A : 0x101418, 0.45 * (1 - sp));
        }
        // Flowing chakra stream along the top and bottom of the scroll, with travelling beads.
        for (int edge = 0; edge < 2; edge++) {
            int n = 48;
            double[] xs = new double[n], ys = new double[n], ws = new double[n];
            int[] cs = new int[n];
            for (int i = 0; i < n; i++) {
                double f = i / (n - 1.0);
                xs[i] = x + 17 + f * (b.w - 34);
                ys[i] = (edge == 0 ? y + 2.5 : y + 30) + Math.sin(f * 18 - time * 0.25 + edge * 2) * 0.9;
                double wave = 0.5 + 0.5 * Math.sin(f * 9 - time * 0.18 + edge);
                ws[i] = 0.8 + 1.4 * wave;
                cs[i] = AnimeDraw.argb(AnimeDraw.mix(c, 0xFFFFFF, wave * 0.6), 0.25 + 0.45 * wave);
            }
            AnimeDraw.ribbon(xs, ys, ws, cs, true);
            for (int k = 0; k < 2; k++) {
                double f = ((time * 0.012 + k * 0.5 + edge * 0.25) % 1.0);
                AnimeDraw.glow(x + 17 + f * (b.w - 34), edge == 0 ? y + 2.5 : y + 30, 5, 2.2, 0xFFFFFF, 0.7, true);
            }
        }
        // Spinning seals over both end caps.
        for (double cxp : new double[] {x + 9.5, x + b.w - 9.5}) {
            AnimeDraw.glow(cxp, y + 15.5, 8, 8, c, 0.35, true);
            AnimeDraw.spiral(cxp, y + 15.5, 4.2, 2.2, 1.0, 0xFFFFFF, 0.6, time * 0.09, true);
        }
    }

    private static double castAt = -100;
    /** A licensed jutsu or weapon was used: the selected slot flares with a chakra burst. */
    public static void castPulse() { castAt = MC.player == null ? -100 : MC.player.ticksExisted; }

    /** Above the items: chakra motes drifting up from the selected slot and an edge light sweep. */
    public static void top(Layout.Box b, double time, double selector) {
        int c = chakra(time);
        double cast = FableHudMath.pulse((time - castAt) / 12);
        if (cast > 0) {
            double cx = b.x + 22 + selector * ScrollGeometry.STRIDE + 9.5, cy = b.y + 18;
            AnimeDraw.glow(cx, cy, 30 * cast + 10, 24 * cast + 8, c, 0.6 * cast, true);
            railBurst(cx, cy, 22, cast, AnimeDraw.mix(c, 0xFFFFFF, 0.3));
            if (time - castAt < 1) sparkle(cx, cy, 0xFF000000 | c, 14);
        }
        double sx = b.x + 22 + selector * ScrollGeometry.STRIDE + 9.5;
        if (RNG.nextDouble() < 0.5 * Math.min(1, dt)) FableEnv.emit(sx + (RNG.nextDouble() - 0.5) * 14, b.y + 26, (RNG.nextDouble() - 0.5) * 0.2, -0.3 - RNG.nextDouble() * 0.3, -0.004, 24, 0xFF000000 | AnimeDraw.mix(c, 0xFFFFFF, 0.4), 1);
        double sweep = ((time * 0.01) % 1.4) - 0.2;
        if (sweep > 0 && sweep < 1) AnimeDraw.gradient(b.x + 17 + sweep * (b.w - 34) - 8, b.y + 6, 16, 25, AnimeDraw.argb(0xFFFFFF, 0), AnimeDraw.argb(0xFFFFFF, 0.12), AnimeDraw.argb(0xFFFFFF, 0.12), AnimeDraw.argb(0xFFFFFF, 0), true);
    }

    /** Element colour for a jutsu school/id. */
    public static int element(JutsuEntry e) {
        String k = ((e.school == null ? "" : e.school) + " " + e.id).toLowerCase(java.util.Locale.ROOT);
        if (k.contains("ember") || k.contains("fire") || k.contains("katon")) return 0xFF6A1A;
        if (k.contains("current") || k.contains("water") || k.contains("suiton")) return 0x2F8CFF;
        if (k.contains("gale") || k.contains("wind") || k.contains("futon")) return 0x7FE08A;
        if (k.contains("storm") || k.contains("lightning") || k.contains("raiton")) return 0xC8B8FF;
        if (k.contains("flint") || k.contains("earth") || k.contains("doton")) return 0xC89A5A;
        if (k.contains("rime") || k.contains("ice")) return 0x9FE8FF;
        if (k.contains("bloom") || k.contains("wood")) return 0x5ED27E;
        if (k.contains("dusk") || k.contains("shadow")) return 0x8A6BC8;
        if (k.contains("dawn") || k.contains("light")) return 0xFFE08A;
        return 0x3FA8FF;
    }

    /** Behind a rail slot's icon: element gradient, inner glow, rotating seal ring, selected flames and kunai. */
    public static void railUnder(double x, double y, double w, double h, JutsuEntry e, boolean selected, boolean enabled, double time, int row) {
        if (e == null) return;
        int el = element(e);
        double cx = x + w / 2, cy = y + h / 2, k = enabled ? 1 : 0.35;
        AnimeDraw.vertical(x + 1, y + 1, w - 2, h - 2, AnimeDraw.argb(AnimeDraw.mix(el, 0x000000, 0.35), 0.55 * k), AnimeDraw.argb(0x05080C, 0.7 * k), false);
        AnimeDraw.glow(cx, cy, w * 0.45, h * 0.45, el, 0.45 * k, true);
        AnimeDraw.vertical(x + 1, y + 1, w - 2, h * 0.35, AnimeDraw.argb(0xFFFFFF, 0.16), AnimeDraw.argb(0xFFFFFF, 0), false);
        AnimeDraw.spiral(cx, cy, Math.min(w, h) * 0.42, 1.8, 0.9, el, 0.45 * k, time * (0.05 + row * 0.01), true);
        if (selected) {
            AnimeDraw.glow(cx, cy, w * 0.9, h * 0.7, el, 0.35, true);
            AnimeDraw.halo(cx, cy, Math.min(w, h) * 0.55 + Math.sin(time * 0.25), 1.4, AnimeDraw.mix(el, 0xFFFFFF, 0.4), 0.6, true);
            for (int f = 0; f < 4; f++) AnimeDraw.flame(x + 3 + f * (w - 6) / 3.0, y + h - 1, 4, 6 + 6 * FableHudMath.noise(f + row * 9, (long) (time / 2)), time * 0.4 + f, el, 0xFFFFFF, 0.5, true);
            AnimeDraw.kunai(x - 3, cy, 1, 6, 0.9);
        }
    }

    /** Ready burst: radial chakra rays and an expanding halo. */
    public static void railBurst(double cx, double cy, double size, double f, int rgb) {
        if (f <= 0) return;
        AnimeDraw.halo(cx, cy, size * (0.5 + 0.8 * (1 - f)), 2, rgb, f, true);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6, r0 = size * 0.4, r1 = size * (0.6 + 0.7 * (1 - f));
            AnimeDraw.ribbon(new double[] {cx + Math.cos(a) * r0, cx + Math.cos(a) * r1}, new double[] {cy + Math.sin(a) * r0, cy + Math.sin(a) * r1}, new double[] {1.4, 0.2}, new int[] {AnimeDraw.argb(0xFFFFFF, f), AnimeDraw.argb(rgb, 0)}, true);
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
