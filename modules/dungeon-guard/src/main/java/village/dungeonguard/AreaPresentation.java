package village.dungeonguard;

import village.dungeonguard.AreaState;

public final class AreaPresentation {
    private AreaPresentation() {
    }

    public static String name(AreaState area) {
        if (area == null || area.isEmpty()) {
            return "";
        }
        return area.isBoss() ? area.boss : area.title;
    }
}

