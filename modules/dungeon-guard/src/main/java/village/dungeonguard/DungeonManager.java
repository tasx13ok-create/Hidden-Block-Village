package village.dungeonguard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.BlockSnapshot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Server-thread arena state machine. Its worker only writes an immutable captured baseline. */
public final class DungeonManager {
    private static final Logger LOG = LogManager.getLogger("DungeonGuard");
    private static final long WORK_NANOS_PER_TICK = 8_000_000L;
    public enum State { CAPTURING, SAVING, IDLE, ACTIVE, WAITING, VALIDATING, RESTORING_BLOCKS, VERIFYING, RESTORING_TILES, FAILED }

    private static final class Arena {
        final GuardRegion region;
        DungeonSnapshot snapshot;
        State state;
        Set<UUID> occupants = Collections.emptySet();
        Future<DungeonSnapshot> ioJob;
        int cursor;
        int countdown;
        List<Integer> tileIndexes = Collections.emptyList();
        String failure;
        boolean durableMarker;
        GuardConfig.DungeonLabel pendingLabel;
        UUID captureRequester;
        Arena(GuardRegion region) { this.region = DungeonSnapshot.copyRegion(region); }
    }

    private final MinecraftServer server;
    private final File snapshotDirectory;
    private final LinkedHashMap<String, Arena> arenas = new LinkedHashMap<String, Arena>();
    private final ExecutorService io;
    private boolean closed;

    /** dataDirectory is the exact per-save dungeon-guard directory, not the save root. */
    public DungeonManager(MinecraftServer server, File dataDirectory) throws IOException {
        this.server = server;
        this.snapshotDirectory = new File(dataDirectory, "snapshots");
        Files.createDirectories(snapshotDirectory.toPath());
        this.io = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "DungeonGuard-snapshot-io");
                thread.setDaemon(true);
                return thread;
            }
        });
        loadConfiguredRegions();
    }

    public void close() { closed = true; io.shutdown(); }

    public String register(GuardRegion requested) throws IOException {
        return register(requested, null, null);
    }

    public String register(GuardRegion requested, GuardConfig.DungeonLabel label) throws IOException {
        return register(requested, label, null);
    }

    /** The optional display label becomes visible only with a verified, durable baseline. */
    public String register(GuardRegion requested, GuardConfig.DungeonLabel label, UUID notifyPlayer) throws IOException {
        ensureOpen();
        if (requested == null) throw new IllegalArgumentException("Select both dungeon corners first with /dungeon wand.");
        requested = DungeonSnapshot.copyRegion(requested);
        requested.validate();
        GuardConfig config = GuardRuntime.settings();
        if (requested.volume() > config.maxSnapshotBlocks) throw new IllegalArgumentException("Dungeon exceeds maxSnapshotBlocks");
        if (arenas.containsKey(requested.name)) throw new IllegalArgumentException("Dungeon name already exists: " + requested.name);
        for (Arena existing : arenas.values()) if (requested.overlaps(existing.region)) throw new IllegalArgumentException("Dungeon overlaps " + existing.region.name);
        if (hasSavedFiles(requested.name)) {
            throw new IOException("Existing baseline/recovery files for " + requested.name + "; refusing to overwrite them");
        }
        GuardConfig.DungeonLabel pendingLabel = copyLabel(label);
        // Validate the complete future definition before locking the selected area or writing bytes.
        config.regions.add(DungeonSnapshot.copyRegion(requested));
        if (pendingLabel != null) config.dungeonLabels.put(requested.name, copyLabel(pendingLabel));
        config.validate();
        WorldServer world = server.getWorld(requested.dimension);
        if (world == null) throw new IllegalArgumentException("Dungeon dimension is not available: " + requested.dimension);
        Arena arena = new Arena(requested);
        arena.snapshot = new DungeonSnapshot(requested);
        arena.state = State.CAPTURING;
        arena.pendingLabel = pendingLabel;
        arena.captureRequester = notifyPlayer;
        arenas.put(requested.name, arena);
        return "Saving " + requested.name + " (" + requested.volume() + " blocks). Keep this area empty until Ready; check /dungeon list.";
    }

    /** Auto names never reuse an active name or a retained baseline from a removed dungeon. */
    public String nextAvailableName(String base) {
        ensureOpen();
        if (base == null || !base.matches("[a-z0-9][a-z0-9_-]{0,47}"))
            throw new IllegalArgumentException("Use a short name with letters, numbers, underscores or hyphens.");
        if (nameAvailable(base)) return base;
        for (int number = 2; number <= 100000; number++) {
            String suffix = "-" + number;
            String candidate = base.substring(0, Math.min(base.length(), 48 - suffix.length())) + suffix;
            if (nameAvailable(candidate)) return candidate;
        }
        throw new IllegalArgumentException("Too many dungeons use that name. Choose a different name.");
    }

    private boolean nameAvailable(String name) { return !arenas.containsKey(name) && !hasSavedFiles(name); }
    private boolean hasSavedFiles(String name) {
        return snapshotFile(name).exists() || checksumFile(name).exists() || markerFile(name).exists();
    }
    private static GuardConfig.DungeonLabel copyLabel(GuardConfig.DungeonLabel label) {
        if (label == null) return null;
        GuardConfig.DungeonLabel result = new GuardConfig.DungeonLabel();
        result.element = label.element;
        result.displayName = label.displayName;
        return result;
    }

    /** Reconfiguration is explicit and cannot discard an in-progress fight/capture/reset. */
    public void reloadConfiguredRegions() throws IOException {
        for (Arena arena : arenas.values()) {
            if (arena.state != State.IDLE && arena.state != State.FAILED) throw new IOException("Cannot replace dungeon definitions while " + arena.region.name + " is " + arena.state);
        }
        arenas.clear();
        loadConfiguredRegions();
    }

    private void loadConfiguredRegions() {
        GuardConfig config = GuardRuntime.settings();
        for (GuardRegion region : config.regions) {
            Arena arena = new Arena(region);
            arenas.put(region.name, arena);
            try {
                arena.snapshot = DungeonSnapshot.load(snapshotFile(region.name), region, config.maxSnapshotBlocks);
                arena.tileIndexes = arena.snapshot.tileIndexes();
                File marker = markerFile(region.name);
                if (marker.exists()) {
                    String value = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim();
                    if (!value.equals("1\n" + arena.snapshot.checksum())) throw new IOException("Recovery marker does not match baseline");
                    arena.durableMarker = true;
                    arena.state = State.RESTORING_BLOCKS;
                    arena.cursor = 0;
                    LOG.info("Dungeon {} will restore its clean baseline before entry", region.name);
                } else {
                    arena.state = State.IDLE;
                }
            } catch (Exception failure) { fail(arena, failure); }
        }
    }

    public void tick() {
        if (closed) return;
        updateOccupants(scanOccupants());
        tickResets();
    }

    /** Includes spectators and body-box intersections, not just the player's feet. */
    private Map<String, Set<UUID>> scanOccupants() {
        Map<String, Set<UUID>> result = new HashMap<String, Set<UUID>>();
        for (Arena arena : arenas.values()) result.put(arena.region.name, new HashSet<UUID>());
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            for (Arena arena : arenas.values()) {
                if (player.dimension == arena.region.dimension && arena.region.intersects(player.getEntityBoundingBox())) {
                    result.get(arena.region.name).add(player.getUniqueID());
                }
            }
        }
        return result;
    }

    /** Deterministic presence seam; production tick supplies a scan of actual online players. */
    public void updateOccupants(Map<String, Set<UUID>> current) {
        ensureOpen();
        if (current == null) throw new IllegalArgumentException("Occupant map is required");
        for (Arena arena : arenas.values()) {
            Set<UUID> ids = current.get(arena.region.name);
            arena.occupants = ids == null ? Collections.<UUID>emptySet() : new HashSet<UUID>(ids);
            if (arena.occupants.contains(null)) throw new IllegalArgumentException("Null occupant identity");
            if (arena.state == State.IDLE && !arena.occupants.isEmpty()) {
                try {
                    markDirty(arena); // Must be durable before ACTIVE permits the first destructive write.
                    arena.state = State.ACTIVE;
                } catch (IOException failure) { fail(arena, failure); }
            } else if (arena.state == State.ACTIVE && arena.occupants.isEmpty()) {
                arena.countdown = GuardRuntime.settings().resetDelayTicks;
                arena.state = State.WAITING;
            } else if (arena.state == State.WAITING && !arena.occupants.isEmpty()) {
                arena.countdown = 0;
                arena.state = State.ACTIVE;
            }
        }
    }

    /** One shared block budget across every capture/restoration job. */
    public void tickResets() {
        if (closed) return;
        int budget = GuardRuntime.settings().blocksPerTick;
        final long deadline = System.nanoTime() + WORK_NANOS_PER_TICK;
        for (final Arena arena : arenas.values()) {
            try {
                if (arena.state == State.SAVING && arena.ioJob.isDone()) finishCapture(arena);
                if (arena.state == State.VALIDATING && arena.ioJob.isDone()) {
                    arena.snapshot = arena.ioJob.get();
                    arena.tileIndexes = arena.snapshot.tileIndexes();
                    arena.ioJob = null;
                    markDirty(arena);
                    arena.state = State.RESTORING_BLOCKS;
                    arena.failure = null;
                    arena.cursor = 0;
                }
                if (arena.state == State.WAITING && arena.occupants.isEmpty()) {
                    if (arena.countdown > 0) arena.countdown--;
                    if (arena.countdown == 0) beginReset(arena);
                }
                if (budget <= 0 || System.nanoTime() >= deadline || !arena.occupants.isEmpty()) continue;
                final WorldServer world = server.getWorld(arena.region.dimension);
                if (world == null) {
                    if (isWorking(arena.state)) throw new IOException("Dungeon dimension is unavailable: " + arena.region.dimension);
                    continue;
                }
                if (arena.state == State.CAPTURING) {
                    int used = arena.snapshot.capture(world, budget, deadline);
                    budget -= used;
                    if (arena.snapshot.captured() == arena.snapshot.size()) {
                        arena.state = State.SAVING;
                        final int maxBlocks = GuardRuntime.settings().maxSnapshotBlocks;
                        arena.ioJob = io.submit(new Callable<DungeonSnapshot>() {
                            @Override public DungeonSnapshot call() throws Exception {
                                arena.snapshot.save(snapshotFile(arena.region.name));
                                return DungeonSnapshot.load(snapshotFile(arena.region.name), arena.region, maxBlocks);
                            }
                        });
                    }
                } else if (arena.state == State.RESTORING_BLOCKS) {
                    final int end = Math.min(arena.snapshot.size(), arena.cursor + budget);
                    final int start = arena.cursor;
                    final int[] processed = new int[] {start};
                    runRestoreBatch(world, arena.region, new Runnable() {
                        @Override public void run() {
                            while (processed[0] < end && System.nanoTime() < deadline) {
                                restoreBlock(world, arena.snapshot, processed[0]);
                                processed[0]++;
                            }
                        }
                    });
                    budget -= processed[0] - arena.cursor;
                    arena.cursor = processed[0];
                    if (arena.cursor == arena.snapshot.size()) { arena.state = State.VERIFYING; arena.cursor = 0; }
                } else if (arena.state == State.VERIFYING) {
                    int end = Math.min(arena.snapshot.size(), arena.cursor + budget);
                    int start = arena.cursor;
                    while (arena.cursor < end && System.nanoTime() < deadline) {
                        int i = arena.cursor;
                        BlockPos pos = arena.snapshot.positionAt(i);
                        if (!world.getBlockState(pos).equals(arena.snapshot.stateAt(i))) {
                            throw new IOException("Restored block verification failed at " + pos);
                        }
                        arena.cursor++;
                    }
                    budget -= arena.cursor - start;
                    if (arena.cursor == arena.snapshot.size()) { arena.state = State.RESTORING_TILES; arena.cursor = 0; }
                } else if (arena.state == State.RESTORING_TILES) {
                    final int end = Math.min(arena.tileIndexes.size(), arena.cursor + budget);
                    final int start = arena.cursor;
                    final int[] processed = new int[] {start};
                    runRestoreBatch(world, arena.region, new Runnable() {
                        @Override public void run() {
                            while (processed[0] < end && System.nanoTime() < deadline) {
                                restoreTile(world, arena.snapshot, arena.tileIndexes.get(processed[0]));
                                processed[0]++;
                            }
                        }
                    });
                    budget -= processed[0] - arena.cursor;
                    arena.cursor = processed[0];
                    if (arena.cursor == arena.tileIndexes.size()) {
                        arena.state = State.IDLE;
                        arena.cursor = 0;
                        // Keep the recovery marker: async chunk saving cannot make a crash look clean.
                        LOG.info("Dungeon {} reset and block/tile baseline verified", arena.region.name);
                    }
                }
            } catch (Exception failure) { fail(arena, failure); }
        }
    }

    private void finishCapture(Arena arena) throws Exception {
        DungeonSnapshot saved = arena.ioJob.get();
        GuardConfig next = GuardRuntime.settings();
        for (GuardRegion region : next.regions) if (region.name.equals(arena.region.name) || region.overlaps(arena.region)) {
            throw new IOException("Dungeon settings changed during capture; baseline kept without activation");
        }
        next.regions.add(DungeonSnapshot.copyRegion(arena.region));
        if (arena.pendingLabel != null) next.dungeonLabels.put(arena.region.name, copyLabel(arena.pendingLabel));
        GuardRuntime.saveAndInstall(next);
        arena.snapshot = saved;
        arena.tileIndexes = saved.tileIndexes();
        arena.state = State.IDLE;
        arena.ioJob = null;
        LOG.info("Dungeon {} baseline saved and verified: {} blocks, {} tile entities", arena.region.name, saved.size(), saved.tileCount());
        String title = arena.pendingLabel == null ? arena.region.name : arena.pendingLabel.displayName;
        captureNotice(arena, "Ready: " + title + " (" + arena.region.name + "). The dungeon will restore after the last player leaves.");
        arena.pendingLabel = null;
    }

    private void beginReset(final Arena arena) throws IOException {
        if (!arena.occupants.isEmpty()) throw new IOException("Refusing to reset an occupied dungeon");
        // Revalidate durable bytes before destructive restoration, not merely the original capture.
        final int maxBlocks = GuardRuntime.settings().maxSnapshotBlocks;
        arena.ioJob = io.submit(new Callable<DungeonSnapshot>() {
            @Override public DungeonSnapshot call() throws Exception {
                return DungeonSnapshot.load(snapshotFile(arena.region.name), arena.region, maxBlocks);
            }
        });
        arena.state = State.VALIDATING;
        arena.cursor = 0;
    }

    public String forceReset(String name) throws IOException {
        Arena arena = requireArena(name);
        Set<UUID> online = scanOccupants().get(name);
        if (!arena.occupants.isEmpty() || (online != null && !online.isEmpty())) throw new IllegalArgumentException("Dungeon is occupied; reset refused");
        if (arena.state == State.CAPTURING || arena.state == State.SAVING) throw new IllegalArgumentException("Dungeon baseline is still being captured");
        if (isRestoring(arena.state)) throw new IllegalArgumentException("Dungeon reset is already running");
        try { beginReset(arena); }
        catch (IOException failure) { fail(arena, failure); throw failure; }
        return "Reset queued for " + name + "; entry is locked until verification completes.";
    }

    private void markDirty(Arena arena) throws IOException {
        if (arena.snapshot == null || arena.snapshot.checksum() == null) throw new IOException("Dungeon has no verified durable baseline");
        if (arena.durableMarker) return;
        DungeonSnapshot.durableText(markerFile(arena.region.name), "1\n" + arena.snapshot.checksum() + "\n");
        arena.durableMarker = true;
    }

    /** Removes only the definition; baseline and recovery evidence remain on disk. */
    public String unregister(String name) throws IOException {
        Arena arena = requireArena(name);
        Set<UUID> online = scanOccupants().get(name);
        if (!arena.occupants.isEmpty() || (online != null && !online.isEmpty())) throw new IllegalArgumentException("Dungeon is occupied; removal refused");
        if (arena.state != State.IDLE && arena.state != State.FAILED) throw new IllegalArgumentException("Only an IDLE or FAILED dungeon can be removed");
        GuardConfig next = GuardRuntime.settings();
        for (int i = next.regions.size() - 1; i >= 0; i--) if (name.equals(next.regions.get(i).name)) next.regions.remove(i);
        next.dungeonLabels.remove(name);
        for (int i = next.bossAreas.size() - 1; i >= 0; i--) if (name.equals(next.bossAreas.get(i).dungeon)) next.bossAreas.remove(i);
        GuardRuntime.saveAndInstall(next);
        arenas.remove(name);
        return "Unregistered " + name + ". Its baseline and recovery files were preserved; use a new name for a new capture.";
    }

    private void restoreBlock(WorldServer world, DungeonSnapshot snapshot, int index) {
        BlockPos pos = snapshot.positionAt(index);
        IBlockState expected = snapshot.stateAt(index);
        IBlockState old = world.getBlockState(pos);
        boolean hasTile = old.getBlock().hasTileEntity(old) || expected.getBlock().hasTileEntity(expected);
        if (old.equals(expected) && !hasTile) return;
        // Remove the old tile before breakBlock can query an inventory and scatter its contents.
        if (hasTile) world.removeTileEntity(pos);
        Chunk chunk = world.getChunk(pos);
        if (!old.equals(expected)) GuardRuntime.restoreBlockState(world, pos, expected);
        chunk.markDirty();
        if (!old.equals(expected)) {
            world.checkLight(pos);
            world.notifyBlockUpdate(pos, old, expected, 2);
        }
    }

    private void runRestoreBatch(WorldServer world, GuardRegion region, Runnable work) {
        boolean capturing = world.captureBlockSnapshots;
        boolean restoring = world.restoringBlockSnapshots;
        ArrayList<BlockSnapshot> originalSnapshots = world.capturedBlockSnapshots;
        try {
            // Never let reset callbacks enter another Forge interaction's rollback buffer.
            world.capturedBlockSnapshots = new ArrayList<BlockSnapshot>();
            world.captureBlockSnapshots = true;
            world.restoringBlockSnapshots = true;
            GuardRuntime.withRestoration(region, work);
        } finally {
            world.capturedBlockSnapshots = originalSnapshots;
            world.captureBlockSnapshots = capturing;
            world.restoringBlockSnapshots = restoring;
        }
    }

    private void restoreTile(WorldServer world, DungeonSnapshot snapshot, int index) {
        BlockPos pos = snapshot.positionAt(index);
        NBTTagCompound saved = snapshot.tileAt(index);
        world.removeTileEntity(pos);
        TileEntity tile = TileEntity.create(world, saved.copy());
        if (tile == null) throw new IllegalStateException("Cannot reconstruct baseline tile at " + pos);
        world.setTileEntity(pos, tile);
        tile.markDirty();
        NBTTagCompound actual = tile.writeToNBT(new NBTTagCompound());
        if (!actual.equals(saved)) throw new IllegalStateException("Restored tile NBT verification failed at " + pos);
        IBlockState state = snapshot.stateAt(index);
        world.notifyBlockUpdate(pos, state, state, 2);
    }

    public boolean isWritable(World world, BlockPos pos) {
        Arena arena = find(world, pos);
        return arena != null && arena.state == State.ACTIVE && !arena.occupants.isEmpty();
    }
    public boolean isArena(World world, BlockPos pos) { return find(world, pos) != null; }
    public boolean isResetting(World world, BlockPos pos) {
        Arena arena = find(world, pos);
        return arena != null && isRestoring(arena.state);
    }
    public boolean isEntryLocked(World world, BlockPos pos) {
        Arena arena = find(world, pos);
        return arena != null && (arena.state == State.CAPTURING || arena.state == State.SAVING || arena.state == State.FAILED || isRestoring(arena.state));
    }
    public String state(String name) { return requireArena(name).state.name(); }
    public int occupants(String name) { return requireArena(name).occupants.size(); }
    public GuardRegion region(String name) { return DungeonSnapshot.copyRegion(requireArena(name).region); }
    public List<GuardRegion> regions() {
        List<GuardRegion> result = new ArrayList<GuardRegion>();
        for (Arena arena : arenas.values()) result.add(DungeonSnapshot.copyRegion(arena.region));
        return result;
    }
    public List<String> statusLines() {
        List<String> result = new ArrayList<String>();
        GuardConfig config = GuardRuntime.settings();
        for (Arena arena : arenas.values()) {
            GuardConfig.DungeonLabel label = config.dungeonLabels.get(arena.region.name);
            if (label == null) label = arena.pendingLabel;
            String title = label == null ? arena.region.name : label.displayName + " (" + arena.region.name + ")";
            String extra = arena.state == State.WAITING ? "; reset in " + ((arena.countdown + 19) / 20) + "s" : "";
            if (arena.state == State.CAPTURING) extra = "; " + arena.snapshot.captured() + "/" + arena.snapshot.size() + " blocks saved";
            if (arena.failure != null) extra += "; " + arena.failure;
            result.add(title + ": " + friendlyState(arena.state) + " | Players: " + arena.occupants.size() + extra);
        }
        if (result.isEmpty()) result.add("No dungeons yet. World protection is on by default. Start with /dungeon setup.");
        return result;
    }

    private static String friendlyState(State state) {
        switch (state) {
            case CAPTURING: return "Saving original";
            case SAVING: return "Checking saved original";
            case IDLE: return "Ready";
            case ACTIVE: return "In use";
            case WAITING: return "Waiting to reset";
            case FAILED: return "Needs attention (entry locked)";
            default: return "Rebuilding (entry locked)";
        }
    }

    private Arena find(World world, BlockPos pos) {
        if (world == null || world.provider == null || pos == null) return null;
        int dim = world.provider.getDimension();
        for (Arena arena : arenas.values()) if (arena.region.dimension == dim && arena.region.contains(pos)) return arena;
        return null;
    }
    private Arena requireArena(String name) {
        Arena arena = arenas.get(name);
        if (arena == null) throw new IllegalArgumentException("Unknown dungeon: " + name);
        return arena;
    }
    private void fail(Arena arena, Exception error) {
        arena.state = State.FAILED;
        arena.failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        LOG.error("Dungeon {} locked after failure: {}", arena.region.name, arena.failure, error);
        captureNotice(arena, "Could not finish saving " + arena.region.name + ". Entry stays locked: " + arena.failure + ". Check /dungeon status.");
    }
    private void captureNotice(Arena arena, String message) {
        UUID recipient = arena.captureRequester;
        arena.captureRequester = null;
        if (recipient == null) return;
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(recipient);
        if (player != null) player.sendMessage(new TextComponentString("[Dungeon Guard] " + message));
    }
    private void ensureOpen() { if (closed) throw new IllegalStateException("Dungeon manager has stopped"); }
    private static boolean isRestoring(State state) { return state == State.VALIDATING || state == State.RESTORING_BLOCKS || state == State.VERIFYING || state == State.RESTORING_TILES; }
    private static boolean isWorking(State state) { return state == State.CAPTURING || isRestoring(state); }
    private File snapshotFile(String name) { return new File(snapshotDirectory, name + ".snapshot.nbt.gz"); }
    private File checksumFile(String name) { return new File(snapshotDirectory, name + ".snapshot.nbt.gz.sha256"); }
    private File markerFile(String name) { return new File(snapshotDirectory, name + ".dirty"); }
}
