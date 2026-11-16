package village.dungeonguard.test;

import village.dungeonguard.client.AreaBannerLayout;
import village.dungeonguard.client.AreaBannerLayout.Box;
import village.dungeonguard.client.AreaBannerLayout.BossStack;

/** Real geometry regressions, independent of graphical launch and screenshot claims. */
public final class AreaLayoutChecks {
    private static int checks;
    public static void main(String[] args) {
        BossStack stack = new BossStack();
        Box ordinary = AreaBannerLayout.place(640, 360, stack.bottom());
        check("normal display keeps the top-centered full-size banner", ordinary != null && ordinary.x == 155
                && ordinary.y == 5 && ordinary.width == 330 && ordinary.height == 110);
        Box compass = AreaBannerLayout.place(320, 240, 32);
        check("installed Immersion compass leaves its 32-pixel top band clear", compass != null && compass.y >= 38
                && compass.y + compass.height <= 240 - AreaBannerLayout.BOTTOM_RESERVE);
        Box compactCompass = AreaBannerLayout.compact(200, 240, 32, 3);
        check("compact text also stays below Immersion compass markers", compactCompass != null && compactCompass.y >= 38);
        stack.observe(12, 19, false);
        check("one boss reserves its rendered slot", stack.bottom() == 31);
        Box one = AreaBannerLayout.place(640, 360, stack.bottom());
        check("banner stays below first boss bar and its name", one != null && one.y >= 37);
        stack.observe(31, 19, false); stack.observe(50, 19, false);
        check("multiple boss bars reserve the full stack", stack.bottom() == 69);
        Box many = AreaBannerLayout.place(640, 360, stack.bottom());
        check("banner stays below the third boss bar", many != null && many.y >= 75);
        Box sharedTop = AreaBannerLayout.place(640, 360, Math.max(32, stack.bottom()));
        check("compass and boss reservations combine by maximum without double spacing", sharedTop != null && sharedTop.y == many.y);
        stack.observe(1000, 19, true);
        check("cancelled boss bars do not displace the banner", stack.bottom() == 69);
        Box small = AreaBannerLayout.place(320, 240, stack.bottom());
        check("small GUI shrinks readable artwork to fit below three bosses", small != null && small.width == 260 && small.y >= 75);
        check("small GUI still protects hotbar and readiness space", small != null && small.y + small.height <= 240 - AreaBannerLayout.BOTTOM_RESERVE);
        stack.observe(69, 19, false);
        check("crowded small GUI hides artwork instead of covering combat controls", AreaBannerLayout.place(320, 240, stack.bottom()) == null);
        Box fallback = AreaBannerLayout.compact(320, 240, stack.bottom(), 3);
        check("crowded small GUI retains boss information in a compact text panel", fallback != null && fallback.height == 39
                && fallback.y >= 94 && fallback.y + fallback.height <= 166);
        stack.reset();
        check("next frame clears vanished boss reservations", stack.bottom() == 0 && AreaBannerLayout.place(640, 360, stack.bottom()).y == 5);
        stack.observe(12, -10, false);
        check("invalid negative increment still reserves the visible five-pixel bar", stack.bottom() == 17);
        stack.observe(Integer.MAX_VALUE, Integer.MAX_VALUE, false);
        check("oversized boss geometry cannot wrap into the visible area", stack.bottom() == Integer.MAX_VALUE
                && AreaBannerLayout.place(640, 360, stack.bottom()) == null);
        check("too narrow GUI does not squeeze unreadable labels", AreaBannerLayout.place(275, 240, 0) == null);
        check("too short GUI does not overlap bottom controls", AreaBannerLayout.place(640, 150, 0) == null);

        // Public Field HUD 0.20.1 source at e4c6daa: status, map, contextual card and rail.
        Box[] field = {new Box(8, 8, 238, 120), new Box(484, 44, 148, 160),
                new Box(8, 134, 196, 30), new Box(572, 210, 28, 168)};
        Box compatible = AreaBannerLayout.place(640, 360, 31, field);
        check("Field HUD reservations still allow a usable banner", compatible != null);
        clear("banner avoids Field HUD status, minimap, contextual card and rail", compatible, field);
        Box[] moved = {new Box(240, 8, 238, 120), new Box(484, 44, 148, 160)};
        Box movedFit = AreaBannerLayout.place(640, 360, 0, moved);
        check("moved status plate changes banner placement", movedFit != null && (movedFit.x != ordinary.x || movedFit.y != ordinary.y));
        clear("banner avoids moved Field HUD status and minimap", movedFit, moved);
        check("fully occupied safe area suppresses artwork", AreaBannerLayout.place(640, 360, 0, new Box(0, 0, 640, 286)) == null);
        check("fully occupied safe area also suppresses text instead of overlapping", AreaBannerLayout.compact(640, 360, 0, 3, new Box(0, 0, 640, 286)) == null);
        check("narrow window retains legible compact labels", AreaBannerLayout.compact(200, 240, 0, 3) != null);
        check("extremely narrow window suppresses all overflowing text", AreaBannerLayout.compact(130, 240, 0, 3) == null);
        check("null optional reservations are harmless", AreaBannerLayout.place(640, 360, 0, (Box[]) null) != null);

        for (int[] screen : new int[][] {{1920, 1080}, {854, 480}, {640, 360}, {427, 240}, {320, 240}}) {
            for (int boss : new int[] {0, 31, 69}) {
                Box fit = AreaBannerLayout.place(screen[0], screen[1], boss);
                check("readable in-bounds banner at " + screen[0] + "x" + screen[1] + " bossBottom=" + boss,
                        fit != null && fit.width >= 260 && fit.height == fit.width / 3 && fit.x >= 8
                        && fit.x + fit.width <= screen[0] - 8 && fit.y >= (boss == 0 ? 5 : boss + 6)
                        && fit.y + fit.height <= screen[1] - AreaBannerLayout.BOTTOM_RESERVE);
            }
        }
        System.out.println("DUNGEON_GUARD_AREA_LAYOUT_PASS checks=" + checks);
    }
    private static void clear(String name, Box candidate, Box[] occupied) {
        boolean pass = candidate != null;
        if (candidate != null) for (Box box : occupied) pass &= !candidate.overlaps(box);
        check(name, pass);
    }
    private static void check(String name, boolean pass) {
        if (!pass) throw new AssertionError(name);
        checks++;
    }
}
