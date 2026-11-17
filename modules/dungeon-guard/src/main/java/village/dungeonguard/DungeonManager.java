package village.dungeonguard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.BitSet;
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
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import village.dungeonguard.DungeonSnapshot;
import village.dungeonguard.GuardConfig;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.GuardRuntime;

public final class DungeonManager {
    private static final Logger LOG = LogManager.getLogger((String)"DungeonGuard");
    private static final long WORK_NANOS_PER_TICK = 8000000L;
    private final MinecraftServer server;
    private final File snapshotDirectory;
    private final LinkedHashMap<String, Arena> arenas = new LinkedHashMap();
    private final ExecutorService io;
    private final List<PendingRegistration> pendingRegistrations = new ArrayList<PendingRegistration>();
    private boolean closed;

    public DungeonManager(MinecraftServer server, File dataDirectory) throws IOException {
        this.server = server;
        this.snapshotDirectory = new File(dataDirectory, "snapshots");
        Files.createDirectories(this.snapshotDirectory.toPath(), new FileAttribute[0]);
        this.io = Executors.newSingleThreadExecutor(new ThreadFactory(){

            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "DungeonGuard-snapshot-io");
                thread.setDaemon(true);
                return thread;
            }
        });
        this.loadConfiguredRegions();
    }

    public void close() {
        this.closed = true;
        this.io.shutdown();
    }

    public String register(GuardRegion requested) throws IOException {
        return this.register(requested, null, null);
    }

    public String register(GuardRegion requested, GuardConfig.DungeonLabel label) throws IOException {
        return this.register(requested, label, null);
    }

    public String register(GuardRegion requested, GuardConfig.DungeonLabel label, UUID notifyPlayer) throws IOException {
        return this.register(requested, label, notifyPlayer, null, null);
    }

    public String registerWaystone(GuardRegion requested, GuardConfig.DungeonLabel label, UUID notifyPlayer, GuardConfig.WaystoneZone waystone, String replaces) throws IOException {
        if (waystone == null) {
            throw new IllegalArgumentException("A dungeon Waystone is required.");
        }
        return this.register(requested, label, notifyPlayer, waystone, replaces);
    }

    public String queueWaystoneRegistration(GuardRegion region, GuardConfig.DungeonLabel label, UUID requester, GuardConfig.WaystoneZone stone, String replaces) throws IOException {
        EntityPlayerMP player;
        PendingRegistration pending2;
        this.ensureOpen();
        if (replaces == null) {
            return this.registerWaystone(region, label, requester, stone, null);
        }
        Arena old = this.requireArena(replaces);
        for (PendingRegistration queued : this.pendingRegistrations) {
            if (!replaces.equals(queued.replaces) && !region.overlaps(queued.region)) continue;
            throw new IllegalArgumentException("This dungeon upgrade is already being prepared.");
        }
        for (UUID occupant : this.scanOccupants().get(replaces)) {
            if (occupant.equals(requester)) continue;
            throw new IllegalArgumentException("Wait for the other players to leave this dungeon before expanding it.");
        }
        if (old.state != State.IDLE && old.state != State.ACTIVE && old.state != State.WAITING) {
            throw new IllegalArgumentException("Wait until the old dungeon is Ready before expanding it.");
        }
        EntityPlayerMP entityPlayerMP = player = requester == null ? null : this.server.getPlayerList().getPlayerByUUID(requester);
        if (player != null && player.dimension == old.region.dimension && old.region.intersects(player.getEntityBoundingBox())) {
            try {
                GuardRuntime.spawn().teleportToSpawn(player);
            }
            catch (Exception failure) {
                throw new IOException("Could not move you to spawn before preparing the dungeon.", failure);
            }
        }
        this.refreshPresence();
        this.forceReset(replaces);
        pending2 = new PendingRegistration();
        pending2.region = DungeonSnapshot.copyRegion(region);
        pending2.label = DungeonManager.copyLabel(label);
        pending2.stone = DungeonManager.copyWaystone(stone);
        pending2.requester = requester;
        pending2.replaces = replaces;
        this.pendingRegistrations.add(pending2);
        return "Preparing the 300 x 250 x 300 zone. The old clean dungeon is restored first; one Ready notice follows.";
    }

    private String register(GuardRegion requested, GuardConfig.DungeonLabel label, UUID notifyPlayer, GuardConfig.WaystoneZone waystone, String replaces) throws IOException {
        Arena prior;
        this.ensureOpen();
        if (requested == null) {
            throw new IllegalArgumentException("Select both dungeon corners first with /dungeon wand.");
        }
        requested = DungeonSnapshot.copyRegion(requested);
        requested.validate();
        GuardConfig config = GuardRuntime.settings();
        if (requested.volume() > (long)config.maxSnapshotBlocks) {
            throw new IllegalArgumentException("Dungeon exceeds maxSnapshotBlocks");
        }
        if (this.arenas.containsKey(requested.name)) {
            throw new IllegalArgumentException("Dungeon name already exists: " + requested.name);
        }
        Arena arena = prior = replaces == null ? null : this.requireArena(replaces);
        if (prior != null) {
            if (prior.state != State.IDLE || !prior.occupants.isEmpty()) {
                throw new IllegalArgumentException("The old dungeon must be Ready and empty before expanding it.");
            }
            if (prior.region.dimension != requested.dimension || !requested.contains(prior.region.minX, prior.region.minY, prior.region.minZ) || !requested.contains(prior.region.maxX, prior.region.maxY, prior.region.maxZ)) {
                throw new IllegalArgumentException("The new Waystone zone must contain the complete old dungeon.");
            }
        }
        for (Arena existing : this.arenas.values()) {
            if (existing == prior || !requested.overlaps(existing.region)) continue;
            throw new IllegalArgumentException("Dungeon overlaps " + existing.region.name);
        }
        if (this.hasSavedFiles(requested.name)) {
            throw new IOException("Existing baseline/recovery files for " + requested.name + "; refusing to overwrite them");
        }
        GuardConfig.DungeonLabel pendingLabel = DungeonManager.copyLabel(label);
        if (prior != null) {
            DungeonManager.removeDefinition(config, prior.region.name, requested.name);
        }
        config.regions.add(DungeonSnapshot.copyRegion(requested));
        if (pendingLabel != null) {
            config.dungeonLabels.put(requested.name, DungeonManager.copyLabel(pendingLabel));
        }
        if (waystone != null) {
            config.dungeonWaystones.put(requested.name, DungeonManager.copyWaystone(waystone));
        }
        config.validate();
        WorldServer world = this.server.getWorld(requested.dimension);
        if (world == null) {
            throw new IllegalArgumentException("Dungeon dimension is not available: " + requested.dimension);
        }
        Arena arena2 = new Arena(requested);
        arena2.snapshot = new DungeonSnapshot(requested);
        arena2.state = State.CAPTURING;
        arena2.pendingLabel = pendingLabel;
        arena2.captureRequester = notifyPlayer;
        arena2.pendingWaystone = DungeonManager.copyWaystone(waystone);
        arena2.replaces = replaces;
        if (prior != null) {
            this.arenas.remove(prior.region.name);
        }
        this.arenas.put(requested.name, arena2);
        return "Saving " + requested.name + " (" + requested.volume() + " blocks). Keep this area empty until Ready; check /dungeon list.";
    }

    public String nextAvailableName(String base) {
        this.ensureOpen();
        if (base == null || !base.matches("[a-z0-9][a-z0-9_-]{0,47}")) {
            throw new IllegalArgumentException("Use a short name with letters, numbers, underscores or hyphens.");
        }
        if (this.nameAvailable(base)) {
            return base;
        }
        for (int number = 2; number <= 100000; ++number) {
            String suffix = "-" + number;
            String candidate = base.substring(0, Math.min(base.length(), 48 - suffix.length())) + suffix;
            if (!this.nameAvailable(candidate)) continue;
            return candidate;
        }
        throw new IllegalArgumentException("Too many dungeons use that name. Choose a different name.");
    }

    private boolean nameAvailable(String name) {
        return !this.arenas.containsKey(name) && !this.hasSavedFiles(name);
    }

    private boolean hasSavedFiles(String name) {
        return this.snapshotFile(name).exists() || this.checksumFile(name).exists() || this.markerFile(name).exists();
    }

    private static GuardConfig.DungeonLabel copyLabel(GuardConfig.DungeonLabel label) {
        if (label == null) {
            return null;
        }
        GuardConfig.DungeonLabel result = new GuardConfig.DungeonLabel();
        result.element = label.element;
        result.displayName = label.displayName;
        return result;
    }

    private static GuardConfig.WaystoneZone copyWaystone(GuardConfig.WaystoneZone source) {
        if (source == null) {
            return null;
        }
        GuardConfig.WaystoneZone value = new GuardConfig.WaystoneZone();
        value.dimension = source.dimension;
        value.x = source.x;
        value.y = source.y;
        value.z = source.z;
        return value;
    }

    private static void removeDefinition(GuardConfig config, String oldName, String replacement) {
        int i;
        for (i = config.regions.size() - 1; i >= 0; --i) {
            if (!oldName.equals(config.regions.get((int)i).name)) continue;
            config.regions.remove(i);
        }
        config.dungeonLabels.remove(oldName);
        config.dungeonWaystones.remove(oldName);
        for (i = config.bossAreas.size() - 1; i >= 0; --i) {
            GuardConfig.BossZone boss = config.bossAreas.get(i);
            if (!oldName.equals(boss.dungeon)) continue;
            if (replacement == null) {
                config.bossAreas.remove(i);
                continue;
            }
            boss.dungeon = replacement;
        }
    }

    public void reloadConfiguredRegions() throws IOException {
        if (!this.pendingRegistrations.isEmpty()) {
            throw new IOException("A Waystone dungeon is still being prepared.");
        }
        for (Arena arena : this.arenas.values()) {
            if (arena.state == State.IDLE || arena.state == State.FAILED) continue;
            throw new IOException("Cannot replace dungeon definitions while " + arena.region.name + " is " + (Object)((Object)arena.state));
        }
        this.arenas.clear();
        this.loadConfiguredRegions();
    }

    private void loadConfiguredRegions() {
        GuardConfig config = GuardRuntime.settings();
        for (GuardRegion region : config.regions) {
            Arena arena = new Arena(region);
            this.arenas.put(region.name, arena);
            try {
                arena.snapshot = DungeonSnapshot.load(this.snapshotFile(region.name), region, config.maxSnapshotBlocks);
                arena.tileIndexes = arena.snapshot.tileIndexes();
                File marker = this.markerFile(region.name);
                if (marker.exists()) {
                    String value = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8).trim();
                    if (!value.equals("1\n" + arena.snapshot.checksum())) {
                        throw new IOException("Recovery marker does not match baseline");
                    }
                    arena.durableMarker = true;
                    arena.state = State.RESTORING_BLOCKS;
                    arena.cursor = 0;
                    LOG.info("Dungeon {} will restore its clean baseline before entry", (Object)region.name);
                    continue;
                }
                arena.state = State.IDLE;
            }
            catch (Exception failure) {
                this.fail(arena, failure);
            }
        }
    }

    public void tick() {
        if (this.closed) {
            return;
        }
        this.updateOccupants(this.scanOccupants());
        this.tickResets();
        this.tickPendingRegistrations();
    }

    private void tickPendingRegistrations() {
        for (int i = this.pendingRegistrations.size() - 1; i >= 0; --i) {
            PendingRegistration pending = this.pendingRegistrations.get(i);
            Arena old = this.arenas.get(pending.replaces);
            if (old != null && old.state != State.IDLE && old.state != State.FAILED || old != null && !old.occupants.isEmpty()) continue;
            this.pendingRegistrations.remove(i);
            try {
                if (old == null || old.state == State.FAILED) {
                    throw new IOException("The old dungeon could not be restored; its baseline is preserved.");
                }
                this.registerWaystone(pending.region, pending.label, pending.requester, pending.stone, pending.replaces);
                continue;
            }
            catch (Exception failure) {
                LOG.error("Waystone dungeon preparation failed", (Throwable)failure);
                EntityPlayerMP player = this.server.getPlayerList().getPlayerByUUID(pending.requester);
                if (player == null) continue;
                player.sendMessage((ITextComponent)new TextComponentString("[Dungeon Guard] Could not prepare the dungeon: " + failure.getMessage()));
            }
        }
    }

    public void refreshPresence() {
        this.updateOccupants(this.scanOccupants());
    }

    public void arrivedThroughWaystone(EntityPlayerMP player, int dimension, BlockPos destination) throws IOException {
        if (player == null || destination == null || player.dimension != dimension) {
            return;
        }
        for (Arena arena : this.arenas.values()) {
            GuardConfig.WaystoneZone stone = this.waystone(arena);
            if (stone == null || stone.dimension != dimension || stone.x != destination.getX() || stone.y != destination.getY() || stone.z != destination.getZ() || !arena.region.intersects(player.getEntityBoundingBox())) continue;
            if (this.isEntryLocked(player.world, destination)) {
                throw new IOException("Dungeon is not ready for entry.");
            }
            try {
                this.markDirty(arena);
                arena.state = State.ACTIVE;
                arena.countdown = 0;
                arena.occupants = new HashSet<UUID>(arena.occupants);
                arena.occupants.add(player.getUniqueID());
            }
            catch (IOException failure) {
                this.fail(arena, failure);
                throw failure;
            }
        }
        this.refreshPresence();
    }

    public void failWaystoneArrival(int dimension, BlockPos destination, Exception failure) {
        for (Arena arena : this.arenas.values()) {
            if (!DungeonManager.sameStone(this.waystone(arena), dimension, destination)) continue;
            this.fail(arena, failure);
        }
    }

    private Map<String, Set<UUID>> scanOccupants() {
        HashMap<String, Set<UUID>> result = new HashMap<String, Set<UUID>>();
        for (Arena arena : this.arenas.values()) {
            result.put(arena.region.name, new HashSet());
        }
        for (EntityPlayerMP player : this.server.getPlayerList().getPlayers()) {
            for (Arena arena : this.arenas.values()) {
                if (player.dimension != arena.region.dimension || !arena.region.intersects(player.getEntityBoundingBox())) continue;
                ((Set)result.get(arena.region.name)).add(player.getUniqueID());
            }
        }
        return result;
    }

    public void updateOccupants(Map<String, Set<UUID>> current) {
        this.ensureOpen();
        if (current == null) {
            throw new IllegalArgumentException("Occupant map is required");
        }
        for (Arena arena : this.arenas.values()) {
            Set<UUID> ids = current.get(arena.region.name);
            arena.occupants = ids == null ? Collections.<UUID>emptySet() : new HashSet<UUID>(ids);
            if (arena.occupants.contains(null)) {
                throw new IllegalArgumentException("Null occupant identity");
            }
            if (arena.state == State.IDLE && !arena.occupants.isEmpty() && this.waystone(arena) == null) {
                try {
                    this.markDirty(arena);
                    arena.state = State.ACTIVE;
                }
                catch (IOException failure) {
                    this.fail(arena, failure);
                }
                continue;
            }
            if (arena.state == State.ACTIVE && arena.occupants.isEmpty()) {
                arena.countdown = this.waystone(arena) == null ? GuardRuntime.policy().resetDelayTicks : 0;
                arena.state = State.WAITING;
                continue;
            }
            if (arena.state != State.WAITING || arena.occupants.isEmpty()) continue;
            arena.countdown = 0;
            arena.state = State.ACTIVE;
        }
    }

    public void tickResets() {
        if (this.closed) {
            return;
        }
        int budget = GuardRuntime.settings().blocksPerTick;
        final long deadline = System.nanoTime() + 8000000L;
        for (final Arena arena : this.arenas.values()) {
            try {
                if (arena.state == State.SAVING && arena.ioJob.isDone()) {
                    this.finishCapture(arena);
                }
                if (arena.state == State.VALIDATING && arena.ioJob.isDone()) {
                    arena.snapshot = arena.ioJob.get();
                    arena.tileIndexes = arena.snapshot.tileIndexes();
                    arena.ioJob = null;
                    this.markDirty(arena);
                    arena.state = State.RESTORING_BLOCKS;
                    arena.failure = null;
                    arena.cursor = 0;
                    for (Integer tile : arena.tileIndexes) {
                        arena.changed.set(tile);
                    }
                }
                if (arena.state == State.WAITING && arena.occupants.isEmpty()) {
                    if (arena.countdown > 0) {
                        --arena.countdown;
                    }
                    if (arena.countdown == 0) {
                        this.beginReset(arena);
                    }
                }
                if (budget <= 0 || System.nanoTime() >= deadline || !arena.occupants.isEmpty()) continue;
                final WorldServer world = this.server.getWorld(arena.region.dimension);
                if (world == null) {
                    if (!DungeonManager.isWorking(arena.state)) continue;
                    throw new IOException("Dungeon dimension is unavailable: " + arena.region.dimension);
                }
                if (arena.state == State.CAPTURING) {
                    int used = arena.snapshot.capture((World)world, budget, deadline);
                    budget -= used;
                    if (arena.snapshot.captured() != arena.snapshot.size()) continue;
                    arena.state = State.SAVING;
                    final int maxBlocks = GuardRuntime.settings().maxSnapshotBlocks;
                    arena.ioJob = this.io.submit(new Callable<DungeonSnapshot>(){

                        @Override
                        public DungeonSnapshot call() throws Exception {
                            arena.snapshot.save(DungeonManager.this.snapshotFile(arena.region.name));
                            return DungeonSnapshot.load(DungeonManager.this.snapshotFile(arena.region.name), arena.region, maxBlocks);
                        }
                    });
                    continue;
                }
                if (arena.state == State.RESTORING_BLOCKS) {
                    final int allowance = budget;
                    final int[] processed = new int[]{0};
                    this.runRestoreBatch(world, arena.region, new Runnable(){

                        @Override
                        public void run() {
                            int index = DungeonManager.nextResetIndex(arena, arena.cursor);
                            while (index < arena.snapshot.size() && processed[0] < allowance && System.nanoTime() < deadline) {
                                DungeonManager.this.restoreBlock(world, arena.snapshot, index);
                                processed[0] = processed[0] + 1;
                                arena.cursor = index + 1;
                                index = DungeonManager.nextResetIndex(arena, arena.cursor);
                            }
                            if (index >= arena.snapshot.size()) {
                                arena.cursor = arena.snapshot.size();
                            }
                        }
                    });
                    budget -= processed[0];
                    if (arena.cursor != arena.snapshot.size()) continue;
                    arena.state = State.VERIFYING;
                    arena.cursor = 0;
                    continue;
                }
                if (arena.state == State.VERIFYING) {
                    int checked;
                    int i = DungeonManager.nextResetIndex(arena, arena.cursor);
                    for (checked = 0; i < arena.snapshot.size() && checked < budget && System.nanoTime() < deadline; ++checked) {
                        BlockPos pos = arena.snapshot.positionAt(i);
                        if (!world.getBlockState(pos).equals(arena.snapshot.stateAt(i))) {
                            throw new IOException("Restored block verification failed at " + pos);
                        }
                        arena.cursor = i + 1;
                        i = DungeonManager.nextResetIndex(arena, arena.cursor);
                    }
                    if (i >= arena.snapshot.size()) {
                        arena.cursor = arena.snapshot.size();
                    }
                    budget -= checked;
                    if (arena.cursor != arena.snapshot.size()) continue;
                    arena.state = State.RESTORING_TILES;
                    arena.cursor = 0;
                    continue;
                }
                if (arena.state != State.RESTORING_TILES) continue;
                final int end = Math.min(arena.tileIndexes.size(), arena.cursor + budget);
                int start = arena.cursor;
                final int[] processed = new int[]{start};
                this.runRestoreBatch(world, arena.region, new Runnable(){

                    @Override
                    public void run() {
                        while (processed[0] < end && System.nanoTime() < deadline) {
                            DungeonManager.this.restoreTile(world, arena.snapshot, arena.tileIndexes.get(processed[0]));
                            processed[0] = processed[0] + 1;
                        }
                    }
                });
                budget -= processed[0] - arena.cursor;
                arena.cursor = processed[0];
                if (arena.cursor != arena.tileIndexes.size()) continue;
                arena.state = State.IDLE;
                arena.cursor = 0;
                arena.changed.clear();
                arena.fullReset = false;
                LOG.info("Dungeon {} reset and block/tile baseline verified", (Object)arena.region.name);
            }
            catch (Exception failure) {
                this.fail(arena, failure);
            }
        }
    }

    private void finishCapture(Arena arena) throws Exception {
        DungeonSnapshot saved = arena.ioJob.get();
        GuardConfig next = GuardRuntime.settings();
        if (arena.replaces != null) {
            DungeonManager.removeDefinition(next, arena.replaces, arena.region.name);
        }
        for (GuardRegion region : next.regions) {
            if (!region.name.equals(arena.region.name) && !region.overlaps(arena.region)) continue;
            throw new IOException("Dungeon settings changed during capture; baseline kept without activation");
        }
        next.regions.add(DungeonSnapshot.copyRegion(arena.region));
        if (arena.pendingLabel != null) {
            next.dungeonLabels.put(arena.region.name, DungeonManager.copyLabel(arena.pendingLabel));
        }
        if (arena.pendingWaystone != null) {
            next.dungeonWaystones.put(arena.region.name, DungeonManager.copyWaystone(arena.pendingWaystone));
        }
        GuardRuntime.saveAndInstall(next);
        arena.snapshot = saved;
        arena.tileIndexes = saved.tileIndexes();
        arena.state = State.IDLE;
        arena.fullReset = false;
        arena.ioJob = null;
        LOG.info("Dungeon {} baseline saved and verified: {} blocks, {} tile entities", (Object)arena.region.name, (Object)saved.size(), (Object)saved.tileCount());
        String title = arena.pendingLabel == null ? arena.region.name : arena.pendingLabel.displayName;
        this.captureNotice(arena, "Ready: " + title + " (" + arena.region.name + "). The dungeon will restore after the last player leaves.");
        arena.pendingLabel = null;
        arena.pendingWaystone = null;
        arena.replaces = null;
    }

    private void beginReset(final Arena arena) throws IOException {
        if (!arena.occupants.isEmpty()) {
            throw new IOException("Refusing to reset an occupied dungeon");
        }
        final int maxBlocks = GuardRuntime.settings().maxSnapshotBlocks;
        arena.ioJob = this.io.submit(new Callable<DungeonSnapshot>(){

            @Override
            public DungeonSnapshot call() throws Exception {
                return DungeonSnapshot.load(DungeonManager.this.snapshotFile(arena.region.name), arena.region, maxBlocks);
            }
        });
        arena.state = State.VALIDATING;
        arena.cursor = 0;
    }

    public String forceReset(String name) throws IOException {
        Arena arena = this.requireArena(name);
        Set<UUID> online = this.scanOccupants().get(name);
        if (!arena.occupants.isEmpty() || online != null && !online.isEmpty()) {
            throw new IllegalArgumentException("Dungeon is occupied; reset refused");
        }
        if (arena.state == State.CAPTURING || arena.state == State.SAVING) {
            throw new IllegalArgumentException("Dungeon baseline is still being captured");
        }
        if (DungeonManager.isRestoring(arena.state)) {
            throw new IllegalArgumentException("Dungeon reset is already running");
        }
        arena.fullReset = true;
        try {
            this.beginReset(arena);
        }
        catch (IOException failure) {
            this.fail(arena, failure);
            throw failure;
        }
        return "Reset queued for " + name + "; entry is locked until verification completes.";
    }

    private void markDirty(Arena arena) throws IOException {
        if (arena.snapshot == null || arena.snapshot.checksum() == null) {
            throw new IOException("Dungeon has no verified durable baseline");
        }
        if (arena.durableMarker) {
            return;
        }
        DungeonSnapshot.durableText(this.markerFile(arena.region.name), "1\n" + arena.snapshot.checksum() + "\n");
        arena.durableMarker = true;
    }

    public String unregister(String name) throws IOException {
        for (PendingRegistration pending : this.pendingRegistrations) {
            if (!name.equals(pending.replaces)) continue;
            throw new IllegalArgumentException("This dungeon is being prepared for a Waystone zone.");
        }
        Arena arena = this.requireArena(name);
        Set<UUID> online = this.scanOccupants().get(name);
        if (!arena.occupants.isEmpty() || online != null && !online.isEmpty()) {
            throw new IllegalArgumentException("Dungeon is occupied; removal refused");
        }
        if (arena.state != State.IDLE && arena.state != State.FAILED) {
            throw new IllegalArgumentException("Only an IDLE or FAILED dungeon can be removed");
        }
        GuardConfig next = GuardRuntime.settings();
        DungeonManager.removeDefinition(next, name, null);
        GuardRuntime.saveAndInstall(next);
        this.arenas.remove(name);
        return "Unregistered " + name + ". Its baseline and recovery files were preserved; use a new name for a new capture.";
    }

    private void restoreBlock(WorldServer world, DungeonSnapshot snapshot, int index) {
        boolean hasTile;
        BlockPos pos = snapshot.positionAt(index);
        IBlockState expected = snapshot.stateAt(index);
        IBlockState old = world.getBlockState(pos);
        boolean bl = hasTile = old.getBlock().hasTileEntity(old) || expected.getBlock().hasTileEntity(expected);
        if (old.equals(expected) && !hasTile) {
            return;
        }
        if (hasTile) {
            world.removeTileEntity(pos);
        }
        Chunk chunk = world.getChunk(pos);
        if (!old.equals(expected)) {
            GuardRuntime.restoreBlockState((World)world, pos, expected);
        }
        chunk.markDirty();
        if (!old.equals(expected)) {
            world.checkLight(pos);
            world.notifyBlockUpdate(pos, old, expected, 2);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void runRestoreBatch(WorldServer world, GuardRegion region, Runnable work) {
        boolean capturing = world.captureBlockSnapshots;
        boolean restoring = world.restoringBlockSnapshots;
        ArrayList originalSnapshots = world.capturedBlockSnapshots;
        try {
            world.capturedBlockSnapshots = new ArrayList();
            world.captureBlockSnapshots = true;
            world.restoringBlockSnapshots = true;
            GuardRuntime.withRestoration(region, work);
        }
        finally {
            world.capturedBlockSnapshots = originalSnapshots;
            world.captureBlockSnapshots = capturing;
            world.restoringBlockSnapshots = restoring;
        }
    }

    private void restoreTile(WorldServer world, DungeonSnapshot snapshot, int index) {
        BlockPos pos = snapshot.positionAt(index);
        NBTTagCompound saved = snapshot.tileAt(index);
        world.removeTileEntity(pos);
        TileEntity tile = TileEntity.create((World)world, (NBTTagCompound)saved.copy());
        if (tile == null) {
            throw new IllegalStateException("Cannot reconstruct baseline tile at " + pos);
        }
        world.setTileEntity(pos, tile);
        tile.markDirty();
        NBTTagCompound actual = tile.writeToNBT(new NBTTagCompound());
        if (!actual.equals((Object)saved)) {
            throw new IllegalStateException("Restored tile NBT verification failed at " + pos);
        }
        IBlockState state = snapshot.stateAt(index);
        world.notifyBlockUpdate(pos, state, state, 2);
    }

    public boolean isWritable(World world, BlockPos pos) {
        Arena arena = this.find(world, pos);
        return arena != null && arena.state == State.ACTIVE && !arena.occupants.isEmpty();
    }

    public boolean isWaystoneZone(World world, BlockPos pos) {
        Arena arena = this.find(world, pos);
        return arena != null && this.waystone(arena) != null;
    }

    public boolean protectsWaystone(World world, BlockPos pos) {
        Arena arena = this.find(world, pos);
        GuardConfig.WaystoneZone stone = arena == null ? null : this.waystone(arena);
        return stone != null && Math.abs((long)pos.getX() - (long)stone.x) <= 2L && Math.abs((long)pos.getZ() - (long)stone.z) <= 2L && pos.getY() >= stone.y - 1 && pos.getY() <= stone.y + 2;
    }

    public void recordChange(World world, BlockPos pos) {
        Arena arena = this.find(world, pos);
        if (arena != null && arena.snapshot != null && arena.state != State.CAPTURING && arena.state != State.SAVING && arena.state != State.FAILED && !DungeonManager.isRestoring(arena.state)) {
            arena.changed.set(arena.snapshot.indexAt(pos));
        }
    }

    public int changedBlocks(String name) {
        return this.requireArena((String)name).changed.cardinality();
    }

    public void invalidateChangeTracking() {
        for (Arena arena : this.arenas.values()) {
            arena.fullReset = true;
        }
    }

    private GuardConfig.WaystoneZone waystone(Arena arena) {
        return arena.pendingWaystone != null ? arena.pendingWaystone : GuardRuntime.policy().dungeonWaystones.get(arena.region.name);
    }

    private static int nextResetIndex(Arena arena, int from) {
        if (arena.fullReset) {
            return from;
        }
        int index = arena.changed.nextSetBit(from);
        return index < 0 ? arena.snapshot.size() : index;
    }

    public boolean isArena(World world, BlockPos pos) {
        return this.find(world, pos) != null;
    }

    public boolean isResetting(World world, BlockPos pos) {
        Arena arena = this.find(world, pos);
        return arena != null && DungeonManager.isRestoring(arena.state);
    }

    public boolean isEntryLocked(World world, BlockPos pos) {
        Arena arena = this.find(world, pos);
        return arena != null && (arena.state == State.CAPTURING || arena.state == State.SAVING || arena.state == State.FAILED || DungeonManager.isRestoring(arena.state));
    }

    public String state(String name) {
        for (PendingRegistration pending : this.pendingRegistrations) {
            if (!pending.region.name.equals(name)) continue;
            return "PREPARING";
        }
        return this.requireArena((String)name).state.name();
    }

    public String boundWaystoneName(int dimension, BlockPos base) {
        for (PendingRegistration pending : this.pendingRegistrations) {
            if (!DungeonManager.sameStone(pending.stone, dimension, base)) continue;
            return pending.region.name;
        }
        for (Arena arena : this.arenas.values()) {
            if (!DungeonManager.sameStone(this.waystone(arena), dimension, base)) continue;
            return arena.region.name;
        }
        return null;
    }

    private static boolean sameStone(GuardConfig.WaystoneZone stone, int dimension, BlockPos pos) {
        return stone != null && pos != null && stone.dimension == dimension && stone.x == pos.getX() && stone.y == pos.getY() && stone.z == pos.getZ();
    }

    public int occupants(String name) {
        return this.requireArena((String)name).occupants.size();
    }

    public GuardRegion region(String name) {
        return DungeonSnapshot.copyRegion(this.requireArena((String)name).region);
    }

    public List<GuardRegion> regions() {
        ArrayList<GuardRegion> result = new ArrayList<GuardRegion>();
        for (Arena arena : this.arenas.values()) {
            result.add(DungeonSnapshot.copyRegion(arena.region));
        }
        return result;
    }

    public List<String> statusLines() {
        ArrayList<String> result = new ArrayList<String>();
        GuardConfig config = GuardRuntime.settings();
        for (Arena arena : this.arenas.values()) {
            String extra;
            GuardConfig.DungeonLabel label = config.dungeonLabels.get(arena.region.name);
            if (label == null) {
                label = arena.pendingLabel;
            }
            String title = label == null ? arena.region.name : label.displayName + " (" + arena.region.name + ")";
            String string = extra = arena.state == State.WAITING ? "; reset in " + (arena.countdown + 19) / 20 + "s" : "";
            if (arena.state == State.CAPTURING) {
                extra = "; " + arena.snapshot.captured() + "/" + arena.snapshot.size() + " blocks saved";
            }
            if (arena.failure != null) {
                extra = extra + "; " + arena.failure;
            }
            result.add(title + ": " + DungeonManager.friendlyState(arena.state) + " | Players: " + arena.occupants.size() + extra);
        }
        if (result.isEmpty()) {
            result.add("No dungeons yet. World protection is on by default. Start with /dungeon setup.");
        }
        return result;
    }

    private static String friendlyState(State state) {
        switch (state) {
            case CAPTURING: {
                return "Saving original";
            }
            case SAVING: {
                return "Checking saved original";
            }
            case IDLE: {
                return "Ready";
            }
            case ACTIVE: {
                return "In use";
            }
            case WAITING: {
                return "Waiting to reset";
            }
            case FAILED: {
                return "Needs attention (entry locked)";
            }
        }
        return "Rebuilding (entry locked)";
    }

    private Arena find(World world, BlockPos pos) {
        if (world == null || world.provider == null || pos == null) {
            return null;
        }
        int dim = world.provider.getDimension();
        for (Arena arena : this.arenas.values()) {
            if (arena.region.dimension != dim || !arena.region.contains(pos)) continue;
            return arena;
        }
        return null;
    }

    private Arena requireArena(String name) {
        Arena arena = this.arenas.get(name);
        if (arena == null) {
            throw new IllegalArgumentException("Unknown dungeon: " + name);
        }
        return arena;
    }

    private void fail(Arena arena, Exception error) {
        arena.state = State.FAILED;
        arena.failure = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        LOG.error("Dungeon {} locked after failure: {}", (Object)arena.region.name, (Object)arena.failure, (Object)error);
        this.captureNotice(arena, "Could not finish saving " + arena.region.name + ". Entry stays locked: " + arena.failure + ". Check /dungeon status.");
    }

    private void captureNotice(Arena arena, String message) {
        UUID recipient = arena.captureRequester;
        arena.captureRequester = null;
        if (recipient == null) {
            return;
        }
        EntityPlayerMP player = this.server.getPlayerList().getPlayerByUUID(recipient);
        if (player != null) {
            player.sendMessage((ITextComponent)new TextComponentString("[Dungeon Guard] " + message));
        }
    }

    private void ensureOpen() {
        if (this.closed) {
            throw new IllegalStateException("Dungeon manager has stopped");
        }
    }

    private static boolean isRestoring(State state) {
        return state == State.VALIDATING || state == State.RESTORING_BLOCKS || state == State.VERIFYING || state == State.RESTORING_TILES;
    }

    private static boolean isWorking(State state) {
        return state == State.CAPTURING || DungeonManager.isRestoring(state);
    }

    private File snapshotFile(String name) {
        return new File(this.snapshotDirectory, name + ".snapshot.nbt.gz");
    }

    private File checksumFile(String name) {
        return new File(this.snapshotDirectory, name + ".snapshot.nbt.gz.sha256");
    }

    private File markerFile(String name) {
        return new File(this.snapshotDirectory, name + ".dirty");
    }

    private static final class PendingRegistration {
        GuardRegion region;
        GuardConfig.DungeonLabel label;
        GuardConfig.WaystoneZone stone;
        UUID requester;
        String replaces;

        private PendingRegistration() {
        }
    }

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
        GuardConfig.WaystoneZone pendingWaystone;
        String replaces;
        final BitSet changed = new BitSet();
        boolean fullReset = true;

        Arena(GuardRegion region) {
            this.region = DungeonSnapshot.copyRegion(region);
        }
    }

    public static enum State {
        CAPTURING,
        SAVING,
        IDLE,
        ACTIVE,
        WAITING,
        VALIDATING,
        RESTORING_BLOCKS,
        VERIFYING,
        RESTORING_TILES,
        FAILED;

    }
}

