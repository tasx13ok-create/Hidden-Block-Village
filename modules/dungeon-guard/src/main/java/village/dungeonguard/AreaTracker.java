package village.dungeonguard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

/** Runs on the logical server; teleports, walking and dimension changes use the same position scan. */
public final class AreaTracker {
    private final Map<UUID, AreaState> previous = new HashMap<UUID, AreaState>();
    public void tick(MinecraftServer server, int ticks) {
        if (ticks % 10 != 0) return;
        GuardConfig config = GuardRuntime.policy();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            AreaState state = AreaResolver.at(config, player.dimension, player.getPosition());
            AreaState old = previous.put(player.getUniqueID(), state);
            boolean changed = !state.equals(old);
            if (changed || ticks % 100 == 0) AreaNetwork.send(player, state, changed);
        }
    }
    public void forget(UUID player) { previous.remove(player); }
    public void clear() { previous.clear(); }
}
