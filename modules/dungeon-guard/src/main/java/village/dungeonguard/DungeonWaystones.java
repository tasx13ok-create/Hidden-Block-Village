package village.dungeonguard;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import village.dungeonguard.AreaNetwork;
import village.dungeonguard.AreaResolver;
import village.dungeonguard.AreaState;
import village.dungeonguard.DungeonManager;
import village.dungeonguard.GuardConfig;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.OperatorAccess;
import village.dungeonguard.SpawnBinding;
import village.dungeonguard.WaystoneBounds;
import village.dungeonguard.core.HookStatus;

public final class DungeonWaystones {
    private static final Logger LOG = LogManager.getLogger((String)"DungeonGuard/Waystones");
    private static final String MANAGER = "net.blay09.mods.waystones.WaystoneManager";
    private static final String ENTRY = "net.blay09.mods.waystones.util.WaystoneEntry";
    private static final String TILE = "net.blay09.mods.waystones.block.TileWaystone";
    private static final Map<EntityPlayerMP, Integer> lastNotice = new WeakHashMap<EntityPlayerMP, Integer>();
    private static boolean reflectionWarning;

    private DungeonWaystones() {
    }

    public static boolean allowTravel(EntityPlayer player, Object entry) {
        if (!DungeonWaystones.active() || player == null || player.world == null || player.world.isRemote) {
            return true;
        }
        EntityPlayerMP connected = DungeonWaystones.online(player);
        if (connected == null) {
            return false;
        }
        try {
            Destination destination = DungeonWaystones.destination(entry);
            DungeonManager manager = GuardRuntime.manager();
            String bound = manager.boundWaystoneName(destination.dimension, destination.pos);
            if (bound != null && !DungeonWaystones.entryReady(manager.state(bound))) {
                DungeonWaystones.notice(connected, "Dungeon is rebuilding. Try again when it is ready.");
                return false;
            }
            for (GuardRegion region : manager.regions()) {
                if (region.dimension != destination.dimension || !DungeonWaystones.nearLanding(region, destination.pos) || DungeonWaystones.entryReady(manager.state(region.name))) continue;
                DungeonWaystones.notice(connected, "Dungeon is not ready for entry.");
                return false;
            }
            return true;
        }
        catch (Exception failure) {
            if (!reflectionWarning) {
                reflectionWarning = true;
                LOG.warn("Waystone destination could not be verified; travel stays blocked: {}", (Object)DungeonWaystones.concise(failure));
            }
            DungeonWaystones.notice(connected, "Dungeon travel is unavailable. Ask an operator to check it.");
            return false;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static boolean travelFinished(boolean success, EntityPlayer player, Object entry) {
        if (!success || !DungeonWaystones.active()) {
            return success;
        }
        EntityPlayerMP connected = DungeonWaystones.online(player);
        if (connected == null) {
            return success;
        }
        DungeonManager manager = GuardRuntime.manager();
        Destination arrived = null;
        try {
            arrived = DungeonWaystones.destination(entry);
            String bound = manager.boundWaystoneName(arrived.dimension, arrived.pos);
            manager.arrivedThroughWaystone(connected, arrived.dimension, arrived.pos);
            if (bound != null && !"ACTIVE".equals(manager.state(bound))) {
                throw new IOException("The dungeon could not enter its verified active state.");
            }
        }
        catch (Exception failure) {
            if (arrived != null) {
                try {
                    manager.failWaystoneArrival(arrived.dimension, arrived.pos, failure);
                }
                catch (RuntimeException locking) {
                    LOG.error("Could not finish locking the failed dungeon arrival: {}", (Object)DungeonWaystones.concise(locking));
                }
            }
            LOG.warn("Dungeon activation failed after Waystone travel; returning player to spawn: {}", (Object)DungeonWaystones.concise(failure));
            try {
                SpawnBinding spawn = GuardRuntime.spawn();
                if (spawn != null && spawn.isBound()) {
                    spawn.teleportToSpawn(connected);
                }
            }
            catch (Exception evacuation) {
                LOG.warn("Could not evacuate the failed Waystone arrival: {}", (Object)DungeonWaystones.concise(evacuation));
            }
            DungeonWaystones.notice(connected, "Dungeon entry could not start. An operator needs to check it.");
        }
        finally {
            try {
                manager.refreshPresence();
            }
            catch (RuntimeException failure) {
                LOG.warn("Could not refresh dungeon occupants after travel: {}", (Object)DungeonWaystones.concise(failure));
            }
            try {
                AreaNetwork.send(connected, AreaResolver.at(GuardRuntime.policy(), connected.dimension, connected.getPosition()), true);
            }
            catch (RuntimeException failure) {
                LOG.warn("Could not refresh dungeon header after travel: {}", (Object)DungeonWaystones.concise(failure));
            }
        }
        return success;
    }

    public static String create(EntityPlayerMP player, String displayName) throws CommandException {
        Vec3d end;
        Vec3d eye;
        if (DungeonWaystones.online((EntityPlayer)player) == null || !OperatorAccess.isOperator((EntityPlayer)player)) {
            throw new CommandException("Only an online operator can set up a dungeon Waystone.", new Object[0]);
        }
        if (!DungeonWaystones.active()) {
            throw new CommandException("Dungeon Guard is not active for this world.", new Object[0]);
        }
        DungeonWaystones.ensureTravelHook();
        SpawnBinding spawnBinding = GuardRuntime.spawn();
        if (spawnBinding == null || !spawnBinding.isBound()) {
            throw new CommandException("Look at the spawn Waystone and use /dg setspawn first.", new Object[0]);
        }
        WorldServer world = player.getServerWorld();
        RayTraceResult hit = world.rayTraceBlocks(eye = new Vec3d(player.posX, player.posY + (double)player.getEyeHeight(), player.posZ), end = eye.add(player.getLookVec().scale(8.0)), false, false, true);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            throw new CommandException("Look directly at the dungeon Waystone within eight blocks.", new Object[0]);
        }
        BlockPos base = SpawnBinding.resolveBase(world, hit.getBlockPos());
        DungeonManager manager = GuardRuntime.manager();
        String already = manager.boundWaystoneName(player.dimension, base);
        if (already != null) {
            return "Dungeon " + already + ": " + DungeonWaystones.readableState(manager.state(already)) + ".";
        }
        try {
            String title = displayName == null || displayName.trim().isEmpty() ? DungeonWaystones.stoneName(world, base) : displayName.trim();
            AreaState.requireText(title, 80, false, "dungeon name");
            String id = manager.nextAvailableName(DungeonWaystones.slug(title));
            GuardRegion candidate = WaystoneBounds.around(id, player.dimension, base.getX(), base.getY(), base.getZ());
            GuardConfig config = GuardRuntime.settings();
            if (candidate.volume() > (long)config.maxSnapshotBlocks) {
                throw new CommandException("A dungeon Waystone needs a 22,500,000-block snapshot limit.", new Object[0]);
            }
            if ((double)candidate.minX < world.getWorldBorder().minX() || (double)candidate.maxX + 1.0 > world.getWorldBorder().maxX() || (double)candidate.minZ < world.getWorldBorder().minZ() || (double)candidate.maxZ + 1.0 > world.getWorldBorder().maxZ()) {
                throw new CommandException("The full 300 x 250 x 300 dungeon must fit inside the world border.", new Object[0]);
            }
            DungeonWaystones.validateSpawnOutside(candidate, config.spawn);
            GuardRegion prior = null;
            for (GuardRegion existing : manager.regions()) {
                if (!candidate.overlaps(existing)) continue;
                if (!(prior == null && existing.contains(base) && candidate.contains(existing.minX, existing.minY, existing.minZ) && candidate.contains(existing.maxX, existing.maxY, existing.maxZ))) {
                    throw new CommandException("This zone overlaps " + existing.name + ". Its whole old dungeon must fit inside this one zone.", new Object[0]);
                }
                if (config.dungeonWaystones.containsKey(existing.name)) {
                    throw new CommandException("That dungeon already uses a different Waystone.", new Object[0]);
                }
                prior = existing;
            }
            GuardConfig.DungeonLabel label = new GuardConfig.DungeonLabel();
            label.displayName = title;
            if (prior != null && config.dungeonLabels.containsKey(prior.name)) {
                label.element = config.dungeonLabels.get((Object)prior.name).element;
            }
            GuardConfig.WaystoneZone stone = new GuardConfig.WaystoneZone();
            stone.dimension = player.dimension;
            stone.x = base.getX();
            stone.y = base.getY();
            stone.z = base.getZ();
            return manager.queueWaystoneRegistration(candidate, label, player.getUniqueID(), stone, prior == null ? null : prior.name);
        }
        catch (CommandException failure) {
            throw failure;
        }
        catch (Exception failure) {
            throw new CommandException("Could not prepare dungeon: " + DungeonWaystones.concise(failure), new Object[0]);
        }
    }

    private static boolean active() {
        return GuardRuntime.ready() && GuardRuntime.manager() != null && GuardRuntime.policy().enabled;
    }

    private static EntityPlayerMP online(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || player instanceof FakePlayer || player.world == null || player.world.isRemote) {
            return null;
        }
        EntityPlayerMP result = (EntityPlayerMP)player;
        MinecraftServer server = result.getServer();
        return server != null && server.isCallingFromMinecraftThread() && result.connection != null && server.getPlayerList().getPlayerByUUID(result.getUniqueID()) == result ? result : null;
    }

    private static Destination destination(Object entry) throws ReflectiveOperationException {
        if (entry == null || !ENTRY.equals(entry.getClass().getName())) {
            throw new IllegalArgumentException("Unsupported Waystone destination type");
        }
        Object dimension = entry.getClass().getMethod("getDimensionId", new Class[0]).invoke(entry, new Object[0]);
        Object pos = entry.getClass().getMethod("getPos", new Class[0]).invoke(entry, new Object[0]);
        if (!(dimension instanceof Integer) || !(pos instanceof BlockPos)) {
            throw new IllegalArgumentException("Invalid Waystone destination");
        }
        return new Destination((Integer)dimension, ((BlockPos)pos).toImmutable());
    }

    private static String stoneName(WorldServer world, BlockPos base) throws ReflectiveOperationException {
        TileEntity tile = world.getTileEntity(base);
        if (tile == null || !TILE.equals(tile.getClass().getName())) {
            throw new IllegalArgumentException("Unsupported Waystone tile");
        }
        Method getName = tile.getClass().getMethod("getWaystoneName", new Class[0]);
        Object name = getName.invoke((Object)tile, new Object[0]);
        if (!(name instanceof String) || ((String)name).trim().isEmpty()) {
            throw new IllegalArgumentException("Name this Waystone first, or provide a name after /dg dungeon");
        }
        return ((String)name).trim();
    }

    private static void ensureTravelHook() throws CommandException {
        try {
            Class.forName(MANAGER, false, DungeonWaystones.class.getClassLoader());
        }
        catch (ClassNotFoundException | LinkageError failure) {
            throw new CommandException("The supported Waystones mod is required for this setup.", new Object[0]);
        }
        if (!HookStatus.hasHook("Waystones.teleportToWaystone")) {
            throw new CommandException("Waystones travel protection is not installed. Check the server log before registering this dungeon.", new Object[0]);
        }
    }

    private static boolean entryReady(String state) {
        return "IDLE".equals(state) || "ACTIVE".equals(state) || "WAITING".equals(state);
    }

    private static boolean nearLanding(GuardRegion region, BlockPos pos) {
        return (long)pos.getX() + 1L >= (long)region.minX && (long)pos.getX() - 1L <= (long)region.maxX && (long)pos.getZ() + 1L >= (long)region.minZ && (long)pos.getZ() - 1L <= (long)region.maxZ && (long)pos.getY() + 1L >= (long)region.minY && pos.getY() <= region.maxY;
    }

    private static void validateSpawnOutside(GuardRegion candidate, GuardConfig.Spawn spawn) throws CommandException {
        if (spawn == null || !spawn.bound) {
            throw new CommandException("Bind the spawn Waystone first with /dg setspawn.", new Object[0]);
        }
        if (candidate.dimension != spawn.dimension) {
            return;
        }
        BlockPos landing = new BlockPos(spawn.landingX, spawn.landingY, spawn.landingZ);
        if (candidate.contains(spawn.x, spawn.y, spawn.z) || candidate.contains(spawn.x, spawn.y + 1, spawn.z) || candidate.contains(landing) || candidate.contains(landing.up()) || candidate.contains(landing.down())) {
            throw new CommandException("Keep the spawn Waystone and its landing outside the complete dungeon zone.", new Object[0]);
        }
    }

    private static String slug(String title) {
        String name = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (name.isEmpty()) {
            name = "dungeon";
        }
        return name.substring(0, Math.min(48, name.length()));
    }

    private static void notice(EntityPlayerMP player, String message) {
        Integer previous = lastNotice.get(player);
        if (previous != null && player.ticksExisted >= previous && player.ticksExisted - previous < 40) {
            return;
        }
        lastNotice.put(player, player.ticksExisted);
        player.sendStatusMessage((ITextComponent)new TextComponentString(message), true);
    }

    private static String readableState(String state) {
        if ("IDLE".equals(state)) {
            return "Ready";
        }
        if ("ACTIVE".equals(state)) {
            return "In use";
        }
        if ("FAILED".equals(state)) {
            return "Needs attention";
        }
        return state.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String concise(Throwable failure) {
        while (failure.getCause() != null && failure.getCause() != failure) {
            failure = failure.getCause();
        }
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private static final class Destination {
        final int dimension;
        final BlockPos pos;

        Destination(int dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }
    }
}

