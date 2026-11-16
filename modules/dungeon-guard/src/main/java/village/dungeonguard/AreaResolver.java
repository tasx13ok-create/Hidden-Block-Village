package village.dungeonguard;

import java.util.Locale;
import net.minecraft.util.math.BlockPos;

/** Authoritative location labels from the player's current server-side position. */
public final class AreaResolver {
    private AreaResolver() { }
    public static AreaState at(GuardConfig config, int dimension, BlockPos pos) {
        if (config == null || pos == null) return AreaState.EMPTY;
        for (GuardRegion region : config.regions) {
            if (region.dimension != dimension || !region.contains(pos)) continue;
            GuardConfig.DungeonLabel label = config.dungeonLabels.get(region.name);
            String element = label == null ? "GENERAL" : label.element;
            String title = label == null ? readable(region.name) : label.displayName;
            for (GuardConfig.BossZone zone : config.bossAreas) {
                if (region.name.equals(zone.dungeon) && zone.bounds.dimension == dimension && zone.bounds.contains(pos))
                    return new AreaState(region.name + "/" + zone.id, element, zone.areaName, zone.bossName);
            }
            return new AreaState(region.name, element, title, "");
        }
        return AreaState.EMPTY;
    }
    private static String readable(String id) {
        StringBuilder result = new StringBuilder();
        for (String part : id.split("[_-]+")) {
            if (part.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return result.toString();
    }
}
