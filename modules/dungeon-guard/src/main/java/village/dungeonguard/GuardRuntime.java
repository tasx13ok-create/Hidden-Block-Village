package village.dungeonguard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Single logical-server runtime, including integrated-server world changes. */
public final class GuardRuntime {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile GuardConfig config = new GuardConfig();
    private static volatile DungeonManager manager;
    private static volatile SpawnBinding spawnBinding;
    private static volatile boolean ready;
    private static File configFile;
    private static final ThreadLocal<Integer> BYPASS = new ThreadLocal<Integer>() {
        @Override protected Integer initialValue() { return 0; }
    };
    private static final ThreadLocal<GuardRegion> RESTORATION = new ThreadLocal<GuardRegion>();
    private static final class RestoreWrite {
        final World world;
        final BlockPos pos;
        boolean used;
        RestoreWrite(World world, BlockPos pos) { this.world = world; this.pos = pos.toImmutable(); }
    }
    private static final ThreadLocal<RestoreWrite> RESTORE_WRITE = new ThreadLocal<RestoreWrite>();
    private GuardRuntime() { }

    public static synchronized void start(MinecraftServer server, File dataDirectory) throws IOException {
        stop();
        Files.createDirectories(dataDirectory.toPath());
        configFile = new File(dataDirectory, "settings.json");
        if (configFile.isFile()) config = readConfig(configFile); else saveAndInstall(new GuardConfig());
        manager = new DungeonManager(server, dataDirectory);
        spawnBinding = new SpawnBinding(server);
        ready = true;
        spawnBinding.sync();
    }
    public static synchronized void stop() {
        ready = false;
        if (manager != null) manager.close();
        manager = null; spawnBinding = null;
        OperatorAccess.clear();
        RESTORATION.remove(); RESTORE_WRITE.remove(); BYPASS.remove();
    }
    public static GuardConfig settings() { return copy(config); }
    static GuardConfig policy() { return config; }
    public static File configFile() { return configFile; }
    public static DungeonManager manager() { return manager; }
    public static SpawnBinding spawn() { return spawnBinding; }
    public static boolean ready() { return ready; }
    public static boolean bypassed() { return BYPASS.get() > 0; }
    public static void withBypass(Runnable action) {
        int prior = BYPASS.get(); BYPASS.set(prior + 1);
        try { action.run(); } finally { if (prior == 0) BYPASS.remove(); else BYPASS.set(prior); }
    }
    public static boolean isRestoring() { return RESTORATION.get() != null; }
    public static boolean restorationAllows(World world, BlockPos pos) {
        GuardRegion region = RESTORATION.get();
        return region != null && world != null && world.provider != null && pos != null
                && region.dimension == world.provider.getDimension() && region.contains(pos);
    }
    public static void withRestoration(GuardRegion region, Runnable action) {
        if (region == null) throw new IllegalArgumentException("Restoration must have explicit dungeon bounds.");
        GuardRegion prior = RESTORATION.get(); RESTORATION.set(region);
        try { action.run(); } finally { if (prior == null) RESTORATION.remove(); else RESTORATION.set(prior); }
    }
    /** Authorizes exactly the primary Chunk write, never a recursive block callback. */
    public static IBlockState restoreBlockState(World world, BlockPos pos, IBlockState state) {
        if (!restorationAllows(world, pos) || RESTORE_WRITE.get() != null)
            throw new IllegalStateException("A restore write must be an unnested cell in its dungeon.");
        RESTORE_WRITE.set(new RestoreWrite(world, pos));
        try { return world.getChunk(pos).setBlockState(pos, state); }
        finally { RESTORE_WRITE.remove(); }
    }
    static boolean consumeRestorationWrite(World world, BlockPos pos) {
        RestoreWrite permit = RESTORE_WRITE.get();
        if (permit == null || permit.used || permit.world != world || !permit.pos.equals(pos)) return false;
        permit.used = true; return true;
    }
    public static synchronized void installForTests(GuardConfig value) {
        value.validate(); config = copy(value);
    }
    public static synchronized void saveAndInstall(GuardConfig value) throws IOException {
        value.validate(); GuardConfig next = copy(value);
        if (configFile == null) throw new IOException("Dungeon Guard has no active world configuration.");
        Path target = configFile.toPath(); Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), "settings-", ".tmp");
        try {
            Files.write(tmp, (JSON.toJson(next) + "\n").getBytes(StandardCharsets.UTF_8));
            try { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
        config = next;
    }
    public static synchronized void reload() throws IOException {
        GuardConfig next = readConfig(configFile);
        // Never replace a running arena's definition while a capture/reset or fight is in progress.
        if (!JSON.toJson(next.regions).equals(JSON.toJson(config.regions)))
            throw new IOException("Region changes require a server restart. Existing active definitions were kept.");
        config = next;
    }
    private static GuardConfig readConfig(File file) throws IOException {
        try {
            GuardConfig value = JSON.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), GuardConfig.class);
            if (value == null) throw new IllegalArgumentException("Empty settings file.");
            value.validate(); return value;
        } catch (RuntimeException e) { throw new IOException("Invalid Dungeon Guard settings; refusing to unlock terrain: " + e.getMessage(), e); }
    }
    private static GuardConfig copy(GuardConfig v) { return JSON.fromJson(JSON.toJson(v), GuardConfig.class); }
    /** Global edit windows were removed: they also unlocked terrain for other players and abilities. */
    @Deprecated public static void beginEdit(GuardRegion zone, int seconds) {
        throw new IllegalStateException("Global edit windows are disabled. Operators can build directly or use /setblock, /fill and /clone.");
    }
    @Deprecated public static void endEdit() { }
    public static boolean editing(World world, BlockPos p) {
        return OperatorAccess.allowed(world, p);
    }
    public static boolean guarded(World w, BlockPos p) {
        if (w == null || w.isRemote || w.provider == null || p == null) return false;
        if (isRestoring()) return true;
        if (!ready || !config.enabled || bypassed()) return false;
        DungeonManager m = manager;
        if (m != null && m.isEntryLocked(w, p)) return true;
        if (editing(w, p)) return false;
        if (m == null) return config.lockOutsideDungeons;
        return !m.isWritable(w, p) && (config.lockOutsideDungeons || m.isArena(w, p));
    }
}
