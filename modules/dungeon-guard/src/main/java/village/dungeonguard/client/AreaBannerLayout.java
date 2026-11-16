package village.dungeonguard.client;

import java.util.LinkedHashSet;
import java.util.Set;

/** GUI-pixel geometry only; safe to verify without Minecraft or an OpenGL context. */
public final class AreaBannerLayout {
    public static final int MIN_WIDTH = 260;
    public static final int BOTTOM_RESERVE = 74;
    private static final int GAP = 6;
    private AreaBannerLayout() { }

    public static final class Box {
        public final int x, y, width, height;
        public Box(int x, int y, int width, int height) {
            this.x = x; this.y = y; this.width = width; this.height = height;
        }
        public boolean overlaps(Box other) {
            return width > 0 && height > 0 && other.width > 0 && other.height > 0
                    && (long) x < (long) other.x + other.width && (long) x + width > other.x
                    && (long) y < (long) other.y + other.height && (long) y + height > other.y;
        }
    }

    /** Reset for each overlay frame; cancelled boss bars consume no space. */
    public static final class BossStack {
        private int bottom;
        public void reset() { bottom = 0; }
        public void observe(int y, int increment, boolean cancelled) {
            if (cancelled) return;
            long end = (long) y + Math.max(5, increment);
            bottom = Math.max(bottom, (int) Math.max(0L, Math.min(Integer.MAX_VALUE, end)));
        }
        public int bottom() { return bottom; }
    }

    /** Return no banner when readable artwork cannot fit above the combat controls. */
    public static Box place(int screenWidth, int screenHeight, int bossBottom, Box... reserved) {
        if (screenWidth < MIN_WIDTH + 16) return null;
        int maximum = Math.min(330, screenWidth - 16);
        Set<Integer> widths = new LinkedHashSet<Integer>();
        widths.add(maximum);
        for (int width : new int[] {300, 280, MIN_WIDTH}) if (width <= maximum) widths.add(width);
        return find(screenWidth, screenHeight, bossBottom, widths, -1, reserved);
    }
    /** Preserve area information on crowded GUIs without painting over combat controls. */
    public static Box compact(int screenWidth, int screenHeight, int bossBottom, int rows, Box... reserved) {
        if (screenWidth < 136) return null;
        int maximum = Math.min(220, screenWidth - 16);
        Set<Integer> widths = new LinkedHashSet<Integer>();
        widths.add(maximum);
        for (int width : new int[] {180, 140, 120}) if (width <= maximum) widths.add(width);
        return find(screenWidth, screenHeight, bossBottom, widths, Math.max(1, Math.min(3, rows)) * 11 + 6, reserved);
    }
    private static Box find(int screenWidth, int screenHeight, int bossBottom, Set<Integer> widths, int fixedHeight, Box[] reserved) {
        if (screenHeight < 1 || bossBottom < 0) return null;
        long firstY = bossBottom == 0 ? 5 : (long) bossBottom + GAP;
        int bottom = screenHeight - BOTTOM_RESERVE;
        if (firstY > bottom) return null;
        int top = (int) firstY;
        int count = reserved == null ? 0 : Math.min(32, reserved.length);
        for (int width : widths) {
            int height = fixedHeight < 0 ? width / 3 : fixedHeight;
            if (height > bottom - top) continue;
            int center = (screenWidth - width) / 2;
            Set<Integer> xs = new LinkedHashSet<Integer>(), ys = new LinkedHashSet<Integer>();
            xs.add(center); xs.add(8); xs.add(screenWidth - 8 - width);
            ys.add(top); ys.add(bottom - height);
            for (int i = 0; i < count; i++) {
                Box box = reserved[i];
                if (box == null || box.width <= 0 || box.height <= 0) continue;
                add(xs, (long) box.x - width - GAP); add(xs, (long) box.x + box.width + GAP);
                add(ys, (long) box.y - height - GAP); add(ys, (long) box.y + box.height + GAP);
            }
            Box best = null; long distance = Long.MAX_VALUE;
            for (int x : xs) for (int y : ys) {
                if (x < 8 || y < top || (long) x + width > screenWidth - 8 || (long) y + height > bottom) continue;
                Box candidate = new Box(x, y, width, height);
                boolean clear = true;
                for (int i = 0; i < count; i++) if (reserved[i] != null && candidate.overlaps(reserved[i])) { clear = false; break; }
                long dx = (long) x - center, dy = (long) y - top, score = dx * dx + dy * dy;
                if (clear && score < distance) { best = candidate; distance = score; }
            }
            if (best != null) return best;
        }
        return null;
    }
    private static void add(Set<Integer> values, long value) {
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) values.add((int) value);
    }
}
