package village.residents;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Small server-side menu throttle; it does not decide or charge gameplay costs. */
public final class InteractionWindow {
    private static final int LIMIT = 64;
    private static final long INTERVAL = 10;
    private final Map<UUID, Long> deadlines = new LinkedHashMap<UUID, Long>() {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Long> entry) {
            return size() > LIMIT;
        }
    };

    public boolean allow(UUID player, long tick) {
        Long end = deadlines.get(player);
        if (end != null && tick < end && tick >= end - INTERVAL) return false;
        deadlines.put(player, tick + INTERVAL);
        return true;
    }

    public int size() { return deadlines.size(); }
}
