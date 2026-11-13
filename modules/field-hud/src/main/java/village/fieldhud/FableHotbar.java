package village.fieldhud;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;

/**
 * Animated pixel-art main hotbar (Fable skin). Real vanilla stacks, selection and key bindings;
 * everything else is presentation:
 *  - spring selector that overshoots and leaves a ghost trail when scrolling fast;
 *  - selected slot lifts and neighbours lean, items tilt with the scroll direction;
 *  - pickup pop + sparkle, removal poof, count-change bump, low-durability blink;
 *  - whole bar sways with strafing, bobs with jumps/falls and squashes on landing;
 *  - shake and red edge on damage, heartbeat rim at low health;
 *  - frost rims in cold biomes, heat shimmer/embers in hot ones, rain drops landing and sliding
 *    on the frame, bubbles underwater, glowing rims in darkness, lightning flash;
 *  - XP bar that eases with a glint sweep and a level-up burst; attack-cooldown pixel gauge.
 */
public final class FableHotbar extends Gui {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final int SLOT = 20, STRIDE = 22, COUNT = 9;
    private static final FableHudMath.Spring SELECT = new FableHudMath.Spring(0.32, 0.62, 0);
    private static final FableHudMath.Spring[] LIFT = new FableHudMath.Spring[COUNT], TILT = new FableHudMath.Spring[COUNT], POP = new FableHudMath.Spring[COUNT];
    private static final ItemStack[] SEEN = new ItemStack[COUNT];
    private static final int[] SEEN_COUNT = new int[COUNT];
    private static final double[] PICKED = new double[COUNT], BUMPED = new double[COUNT];
    private static final double[] GHOST = new double[5];
    private static final Random RNG = new Random();
    private static final FableHudMath.Spring XP = new FableHudMath.Spring(0.2, 0.7, 0);
    private static int lastSelected = -1, lastLevel = -1;
    private static double levelAt = -100, scrollDir;
    static {
        for (int i = 0; i < COUNT; i++) { LIFT[i] = new FableHudMath.Spring(0.4, 0.55, 0); TILT[i] = new FableHudMath.Spring(0.25, 0.5, 0); POP[i] = new FableHudMath.Spring(0.45, 0.5, 1); SEEN[i] = ItemStack.EMPTY; }
    }
    private FableHotbar() {}

    public static void draw(ShinobiLayout layout, double time, float partial) {
        if (MC.player == null) return;
        double dt = FableEnv.update(time);
        boolean motion = HudConfig.motion && HudConfig.mode != 2;
        if (!motion) dt = 100; // springs snap: static presentation
        Layout.Box box = layout.hotbar;
        int width = COUNT * STRIDE + 8;
        int baseX = box.x + box.w / 2 - width / 2, baseY = box.y + box.h - SLOT - 6;
        int selected = MC.player.inventory.currentItem;
        if (lastSelected < 0) { SELECT.snap(selected); lastSelected = selected; }
        if (selected != lastSelected) {
            int d = selected - lastSelected;
            if (Math.abs(d) > 4) d = -Integer.signum(d); // wrapped scroll
            scrollDir = Integer.signum(d);
            for (int i = 0; i < COUNT; i++) TILT[i].kick(-scrollDir * 1.6);
            lastSelected = selected;
        }
        SELECT.target = selected;
        SELECT.step(dt);
        for (int i = GHOST.length - 1; i > 0; i--) GHOST[i] = GHOST[i - 1];
        GHOST[0] = SELECT.value;

        // Global body motion and damage shake (snapped to whole pixels).
        double[] shake = FableHudMath.shake(2.2 * FableEnv.hurt, time);
        int ox = FableHudMath.snap((motion ? FableEnv.SWAY.value : 0) + shake[0]);
        int oy = FableHudMath.snap((motion ? FableEnv.BOB.value + FableEnv.SQUASH.value * 1.5 : 0) + shake[1]);
        double squash = motion ? FableHudMath.clamp(FableEnv.SQUASH.value * 0.06, -0.08, 0.1) : 0;
        int x = baseX + ox, y = baseY + oy;

        GlStateManager.pushMatrix();
        try {
            // Landing squash: widen and flatten around the bar's bottom centre.
            GlStateManager.translate(x + width / 2.0, y + SLOT + 4, 0);
            GlStateManager.scale(1 + squash, 1 - squash, 1);
            GlStateManager.translate(-(x + width / 2.0), -(y + SLOT + 4), 0);
            frame(x, y, width, time);
            for (int i = 0; i < COUNT; i++) slot(i, x, y, selected, time, dt);
            selector(x, y, time);
            items(x, y, selected, time, dt);
            keys(x, y, selected);
            xp(x, y, width, time, dt);
            attack(x, y, width, partial);
            FableEnv.weather(x, y, width, SLOT + 8, time, dt, RNG);
        } finally {
            GlStateManager.popMatrix();
        }
        offhand(layout, ox, oy);
        GlStateManager.disableDepth();
        FableEnv.drawParticles();
        GlStateManager.color(1, 1, 1, 1);
    }

    // ---- frame ---------------------------------------------------------------------------------
    private static void frame(int x, int y, int w, double time) {
        int h = SLOT + 8;
        int rim = blend(0xFF3A3F45, 0xFF8FD8FF, FableEnv.frost * 0.7);
        rim = blend(rim, 0xFFB8562A, FableEnv.heat * 0.6);
        rim = blend(rim, 0xFFD8384A, Math.max(FableEnv.hurt, FableEnv.heartbeat(time)));
        rim = blend(rim, 0xFFFFFFFF, FableEnv.flash * 0.8);
        // Outer pixel border with chamfered corners, dark body, bevel highlight/shadow.
        drawRect(x + 1, y - 4, x + w - 1, y + h - 3, 0xFF0E1114);
        drawRect(x, y - 3, x + w, y + h - 4, 0xFF0E1114);
        drawRect(x + 1, y - 3, x + w - 1, y + h - 4, rim);
        drawRect(x + 2, y - 2, x + w - 2, y + h - 5, 0xF01A1E22);
        drawRect(x + 2, y - 2, x + w - 2, y - 1, 0x40FFFFFF);
        drawRect(x + 2, y + h - 6, x + w - 2, y + h - 5, 0x60000000);
        // Rivets at the corners (glow in the dark).
        int rivet = blend(0xFF7A7F86, 0xFFFFE6A0, FableEnv.dark * 0.8);
        for (int cx : new int[] {x + 3, x + w - 5}) for (int cy : new int[] {y - 1, y + h - 8}) drawRect(cx, cy, cx + 2, cy + 2, rivet);
        // Frost crystals creep in from the corners; heat makes the top edge shimmer.
        if (FableEnv.frost > 0.05) {
            int n = (int) (FableEnv.frost * 10);
            for (int i = 0; i < n; i++) {
                int fx = (int) (FableHudMath.noise(5, i) * 14), fy = (int) (FableHudMath.noise(9, i) * 6);
                int c = SkinDraw.alpha(0xE6F8FF, (int) (200 * FableEnv.frost));
                drawRect(x + 2 + fx, y - 2 + fy, x + 3 + fx, y - 1 + fy, c);
                drawRect(x + w - 3 - fx, y + h - 6 - fy, x + w - 2 - fx, y + h - 5 - fy, c);
            }
        }
        if (FableEnv.heat > 0.05 || FableEnv.burning > 0.05) {
            double k = Math.max(FableEnv.heat * 0.6, FableEnv.burning);
            for (int px = x + 3; px < x + w - 3; px += 2) {
                int dy = (int) Math.round(Math.sin(time * 0.35 + px * 0.4) * 1.2);
                drawRect(px, y - 4 + dy, px + 1, y - 3 + dy, SkinDraw.alpha(0xFFB347, (int) (120 * k)));
            }
        }
        if (FableEnv.wet > 0.05) drawRect(x + 2, y - 2, x + w - 2, y - 1, SkinDraw.alpha(0x9CC8F0, (int) (140 * FableEnv.wet)));
    }

    private static int slotX(int x, int i) { return x + 4 + i * STRIDE + 1; }

    private static void slot(int i, int x, int y, int selected, double time, double dt) {
        double dist = i - SELECT.value;
        LIFT[i].target = FableHudMath.lift(dist);
        LIFT[i].step(dt);
        TILT[i].target = 0;
        TILT[i].step(dt);
        int sx = slotX(x, i), sy = y - FableHudMath.snap(LIFT[i].value);
        boolean sel = i == selected;
        int well = blend(0xFF23282D, 0xFF1A2A33, FableEnv.submerged);
        drawRect(sx, sy, sx + SLOT, sy + SLOT, 0xFF0A0C0E);
        drawRect(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, well);
        drawRect(sx + 1, sy + 1, sx + SLOT - 1, sy + 2, 0xFF0E1013);           // inner top shadow
        drawRect(sx + 1, sy + SLOT - 2, sx + SLOT - 1, sy + SLOT - 1, 0xFF3C434A); // inner bottom light
        if (FableEnv.dark > 0.2) drawRect(sx + 1, sy + SLOT - 2, sx + SLOT - 1, sy + SLOT - 1, SkinDraw.alpha(0xFFD27A, (int) (90 * FableEnv.dark)));
        ItemStack stack = MC.player.inventory.mainInventory.get(i);
        if (!stack.isEmpty() && stack.isItemStackDamageable() && stack.getMaxDamage() > 0) {
            double left = 1 - stack.getItemDamage() / (double) stack.getMaxDamage();
            if (left < 0.15 && (int) (time / 6) % 2 == 0) {
                drawRect(sx + SLOT - 4, sy + 1, sx + SLOT - 1, sy + 2, 0xFFE83A3A);
                drawRect(sx + SLOT - 2, sy + 1, sx + SLOT - 1, sy + 4, 0xFFE83A3A);
            }
        }
        if (sel) drawRect(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x22FFE9B0);
    }

    private static void selector(int x, int y, double time) {
        // Ghost trail when the selector moves quickly between slots.
        for (int g = GHOST.length - 1; g >= 1; g--) {
            double d = Math.abs(GHOST[g] - SELECT.value);
            if (d < 0.25) continue;
            int gx = FableHudMath.snap(x + 4 + GHOST[g] * STRIDE + 1);
            outline(gx - 1, y - 4, SLOT + 2, SLOT + 2, SkinDraw.alpha(0xFFE9B0, (int) (90 * (1 - g / (double) GHOST.length))));
        }
        double stretch = Math.abs(SELECT.velocity) * 4;
        int sx = FableHudMath.snap(x + 4 + SELECT.value * STRIDE + 1), sy = y - 4;
        int glow = (int) (140 + 60 * Math.sin(time * 0.15));
        int col = blend(0xFFFFE9B0, 0xFF9DE0FF, FableEnv.frost * 0.5);
        outline(sx - 2 - (int) stretch, sy - 1, SLOT + 4 + 2 * (int) stretch, SLOT + 4, SkinDraw.alpha(col & 0xFFFFFF, glow));
        outline(sx - 1, sy, SLOT + 2, SLOT + 2, col);
        // Pixel corner brackets.
        for (int[] c : new int[][] {{sx - 2, sy - 1}, {sx + SLOT, sy - 1}, {sx - 2, sy + SLOT + 1}, {sx + SLOT, sy + SLOT + 1}}) drawRect(c[0], c[1], c[0] + 3, c[1] + 2, 0xFFFFFFFF);
    }

    private static void items(int x, int y, int selected, double time, double dt) {
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (int i = 0; i < COUNT; i++) {
            ItemStack stack = MC.player.inventory.mainInventory.get(i);
            int count = stack.getCount();
            boolean changedItem = !ItemStack.areItemsEqual(stack, SEEN[i]);
            if (changedItem) {
                if (!stack.isEmpty()) { POP[i].snap(0.2); POP[i].target = 1; PICKED[i] = time; sparkle(slotX(x, i) + 10, y + 10, 0xFFFFF2B0, 10); }
                else if (!SEEN[i].isEmpty()) poof(slotX(x, i) + 10, y + 10);
                SEEN[i] = stack.copy();
            } else if (count != SEEN_COUNT[i] && !stack.isEmpty()) { POP[i].kick(count > SEEN_COUNT[i] ? 0.12 : -0.08); BUMPED[i] = time; }
            SEEN_COUNT[i] = count;
            POP[i].target = 1;
            POP[i].step(dt);
            if (stack.isEmpty()) continue;
            int sx = slotX(x, i), sy = y - FableHudMath.snap(LIFT[i].value);
            double scale = POP[i].value * (i == selected ? 1.12 + 0.03 * Math.sin(time * 0.2) : 1.0);
            double tilt = TILT[i].value * 6 + (i == selected ? Math.sin(time * 0.1) * 2 : 0);
            GlStateManager.pushMatrix();
            GlStateManager.translate(sx + 10, sy + 10, 0);
            GlStateManager.rotate((float) tilt, 0, 0, 1);
            GlStateManager.scale(scale, scale, 1);
            GlStateManager.translate(-8, -8, 0);
            GlStateManager.color(1, 1, 1, 1);
            MC.getRenderItem().renderItemAndEffectIntoGUI(MC.player, stack, 0, 0);
            MC.getRenderItem().renderItemOverlays(MC.fontRenderer, stack, 0, 0);
            GlStateManager.popMatrix();
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
    }

    private static void keys(int x, int y, int selected) {
        for (int i = 0; i < COUNT; i++) {
            String label = GameSettings.getKeyDisplayString(MC.gameSettings.keyBindsHotbar[i].getKeyCode());
            if (label.length() > 2) label = label.substring(0, 2);
            int sx = slotX(x, i), sy = y - FableHudMath.snap(LIFT[i].value);
            GlStateManager.pushMatrix();
            GlStateManager.translate(sx + 2, sy + 2, 300);
            GlStateManager.scale(0.5f, 0.5f, 1);
            MC.fontRenderer.drawString(label, 0, 0, i == selected ? 0xFFFFE9B0 : 0xFF7F8790);
            GlStateManager.popMatrix();
        }
    }

    private static void xp(int x, int y, int w, double time, double dt) {
        if (!MC.playerController.gameIsSurvivalOrAdventure()) return;
        int level = MC.player.experienceLevel;
        if (lastLevel >= 0 && level > lastLevel) { levelAt = time; sparkle(x + w / 2, y - 8, 0xFFB8F060, 18); }
        lastLevel = level;
        XP.target = MC.player.experience;
        XP.step(dt);
        int bx = x + 4, by = y - 9, bw = w - 8;
        drawRect(bx - 1, by - 1, bx + bw + 1, by + 3, 0xFF0A0C0E);
        drawRect(bx, by, bx + bw, by + 2, 0xFF1F2A16);
        int fill = (int) (bw * FableHudMath.clamp(XP.value, 0, 1));
        drawRect(bx, by, bx + fill, by + 2, 0xFF7FCB2A);
        drawRect(bx, by, bx + fill, by + 1, 0xFFB8F060);
        int glint = (int) ((time * 3) % (bw + 40)) - 20;
        if (glint > 0 && glint < fill) drawRect(bx + glint, by, bx + Math.min(fill, glint + 4), by + 2, 0x90FFFFFF);
        for (int n = 1; n < 9; n++) drawRect(bx + n * bw / 9, by, bx + n * bw / 9 + 1, by + 2, 0x60000000);
        if (level > 0) {
            String s = String.valueOf(level);
            double pop = FableHudMath.backOut(FableHudMath.clamp((time - levelAt) / 10, 0, 1));
            double scale = time - levelAt < 10 ? 0.6 + 0.6 * pop : 1;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + w / 2.0, by - 6, 0);
            GlStateManager.scale(scale, scale, 1);
            int sw = MC.fontRenderer.getStringWidth(s);
            MC.fontRenderer.drawString(s, -sw / 2 + 1, -3, 0xFF000000);
            MC.fontRenderer.drawString(s, -sw / 2, -4, 0xFF9CF04A);
            GlStateManager.popMatrix();
        }
    }

    private static void attack(int x, int y, int w, float partial) {
        if (MC.gameSettings.attackIndicator != 2) return;
        float cooled = MC.player.getCooledAttackStrength(partial);
        if (cooled >= 1) return;
        int ax = x + w + 3, ay = y;
        drawRect(ax, ay, ax + 4, ay + SLOT, 0xFF0A0C0E);
        int seg = (int) (cooled * 9);
        for (int s = 0; s < 9; s++) drawRect(ax + 1, ay + SLOT - 2 - s * 2, ax + 3, ay + SLOT - 1 - s * 2, s < seg ? 0xFFFFD27A : 0xFF2A2F34);
    }

    private static void offhand(ShinobiLayout layout, int ox, int oy) {
        ItemStack off = MC.player.getHeldItemOffhand();
        if (off.isEmpty()) return;
        Layout.Box ob = MC.player.getPrimaryHand() == EnumHandSide.RIGHT ? layout.leftHand : layout.rightHand;
        int sx = ob.x + 2 + ox, sy = ob.y + ob.h - SLOT - 3 + oy;
        drawRect(sx - 1, sy - 1, sx + SLOT + 1, sy + SLOT + 1, 0xFF0E1114);
        drawRect(sx, sy, sx + SLOT, sy + SLOT, 0xFF3A3F45);
        drawRect(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0xFF23282D);
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        MC.getRenderItem().renderItemAndEffectIntoGUI(MC.player, off, sx + 2, sy + 2);
        MC.getRenderItem().renderItemOverlays(MC.fontRenderer, off, sx + 2, sy + 2);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
    }

    // ---- helpers ---------------------------------------------------------------------------------
    static void outline(int x, int y, int w, int h, int c) {
        drawRect(x, y, x + w, y + 1, c); drawRect(x, y + h - 1, x + w, y + h, c);
        drawRect(x, y + 1, x + 1, y + h - 1, c); drawRect(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    static int blend(int a, int b, double t) {
        t = FableHudMath.clamp(t, 0, 1);
        int aa = (a >>> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static void sparkle(double x, double y, int color, int n) {
        for (int i = 0; i < n; i++) {
            double a = RNG.nextDouble() * Math.PI * 2, s = 0.6 + RNG.nextDouble() * 1.2;
            FableEnv.emit(x, y, Math.cos(a) * s, Math.sin(a) * s - 0.6, 0.06, 14 + RNG.nextInt(8), i % 3 == 0 ? 0xFFFFFFFF : color, RNG.nextInt(3) == 0 ? 2 : 1);
        }
    }

    static void poof(double x, double y) {
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            FableEnv.emit(x, y, Math.cos(a) * 0.7, Math.sin(a) * 0.7, 0, 10, 0xC0B8B8B8, 2);
        }
    }
}
