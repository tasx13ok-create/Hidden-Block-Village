package village.residents;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

public final class ResidentEvents {
    private static final class Budget { int attempts; long nextTick = 100; }
    private final Map<World, Budget> budgets = new WeakHashMap<>();

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote
            || !(event.world instanceof WorldServer) || event.world.provider.getDimension() != 0
            || !ValeResidents.autoProvision) return;
        WorldServer world = (WorldServer) event.world;
        Budget budget = budgets.computeIfAbsent(world, ignored -> new Budget());
        long now = world.getTotalWorldTime();
        if (budget.attempts >= 6 || now < budget.nextTick) return;
        budget.attempts++;
        budget.nextTick = now + 200;
        ResidentsData data = ResidentsData.get(world);
        if (data.slots.isReadOnly() || data.slots.count() >= ResidentSlots.HARD_LIMIT) {
            budget.attempts = 6;
            return;
        }
        ValeResidents.LOG.info(Provisioning.run(world));
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event) { budgets.remove(event.getWorld()); DialogueSessions.unload(event.getWorld()); }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) { DialogueSessions.logout(event.player.getUniqueID()); }
}
