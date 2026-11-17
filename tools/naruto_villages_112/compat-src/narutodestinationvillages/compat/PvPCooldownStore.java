package narutodestinationvillages.compat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Keeps the PvP anti-farm kill history across server restarts. The addon tracks recent kills in
 * memory only, so a restart used to clear every cooldown and let the same victim be farmed for
 * loot again. Kill times are wall-clock milliseconds, so restoring them resumes each cooldown
 * exactly. Stored per world in the save folder; written atomically on world save and once a
 * minute when it changed.
 */
public final class PvPCooldownStore {
    private static final Logger LOG = LogManager.getLogger("NarutoVillagesPvP");
    private static final String FILE = "narutovillages_pvp_cooldowns.json";
    private static final Type SHAPE = new TypeToken<Map<String, Map<String, List<Long>>>>() {}.getType();
    private static boolean registered;
    private static File file;
    private static int lastHash;
    private static int ticks;

    private PvPCooldownStore() {}

    static void register() {
        if (registered) return;
        MinecraftForge.EVENT_BUS.register(new PvPCooldownStore.Events());
        registered = true;
    }

    public static final class Events {
        @SubscribeEvent public void load(WorldEvent.Load e) {
            World w = e.getWorld();
            if (w.isRemote || w.provider.getDimension() != 0) return;
            file = new File(w.getSaveHandler().getWorldDirectory(), FILE);
            restore();
        }
        @SubscribeEvent public void save(WorldEvent.Save e) {
            if (!e.getWorld().isRemote && e.getWorld().provider.getDimension() == 0) write(true);
        }
        @SubscribeEvent public void unload(WorldEvent.Unload e) {
            if (!e.getWorld().isRemote && e.getWorld().provider.getDimension() == 0) { write(true); file = null; }
        }
        @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
            if (e.phase == TickEvent.Phase.END && ++ticks % 1200 == 0) write(false);
        }
    }

    /** The live kill-history map inside the addon's PvP loot manager, or null if it changed shape. */
    @SuppressWarnings("unchecked")
    static Map<String, Map<String, List<Long>>> live() {
        try {
            Class<?> type = Class.forName("narutodestinationvillages.pvp.PvPLootManager");
            Method get = type.getMethod("getInstance");
            Object manager = get.invoke(null);
            Field f = type.getDeclaredField("killHistory");
            f.setAccessible(true);
            Object map = f.get(manager);
            return map instanceof Map ? (Map<String, Map<String, List<Long>>>) map : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    static void restore() {
        Map<String, Map<String, List<Long>>> history = live();
        if (history == null || file == null) return;
        synchronized (history) {
            history.clear();   // a different world may have been open before
            if (!file.isFile()) { lastHash = history.hashCode(); return; }
            try (Reader in = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                Map<String, Map<String, List<Long>>> saved = new Gson().fromJson(in, SHAPE);
                if (saved != null) for (Map.Entry<String, Map<String, List<Long>>> killer : saved.entrySet()) {
                    if (killer.getKey() == null || killer.getValue() == null) continue;
                    Map<String, List<Long>> victims = new HashMap<>();
                    for (Map.Entry<String, List<Long>> v : killer.getValue().entrySet()) {
                        if (v.getKey() == null || v.getValue() == null) continue;
                        List<Long> times = new ArrayList<>();
                        for (Long t : v.getValue()) if (t != null && t > 0) times.add(t);
                        if (!times.isEmpty()) victims.put(v.getKey(), times);
                    }
                    if (!victims.isEmpty()) history.put(killer.getKey(), victims);
                }
                LOG.info("Restored PvP anti-farm history for {} killer(s)", history.size());
            } catch (IOException | RuntimeException bad) {
                LOG.warn("PvP cooldown file could not be read; starting with no cooldowns: {}", bad.toString());
            }
            lastHash = history.hashCode();
        }
    }

    static void write(boolean force) {
        Map<String, Map<String, List<Long>>> history = live();
        if (history == null || file == null) return;
        String json; int hash;
        synchronized (history) {
            hash = history.hashCode();
            if (!force && hash == lastHash) return;
            json = new Gson().toJson(history, SHAPE);
        }
        File tmp = new File(file.getParentFile(), FILE + ".tmp");
        try {
            try (Writer out = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) { out.write(json); }
            try { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException plain) { Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING); }
            lastHash = hash;
        } catch (IOException failure) {
            LOG.warn("PvP cooldown file could not be written: {}", failure.toString());
        }
    }
}
