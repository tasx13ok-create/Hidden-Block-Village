package village.standards;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Optional one-shot runner in a private helper, never in the production mod. */
@Mod.EventBusSubscriber(modid = "valestandardschecks")
public final class StandardsNativeRunner {
    private static boolean started;
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || started || !Boolean.getBoolean("valestandards.nativecheck.standalone")) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getWorld(0) == null) return;
        started = true;
        if (!Boolean.getBoolean("valestandards.nativecheck") || !"vale-recovery-test".equals(server.getFolderName())
                || !new File(".vale-recovery-test").isFile() || !server.getPlayerList().getPlayers().isEmpty())
            throw new IllegalStateException("Standalone Standards checks require explicit flags, a marked disposable world and no connected players");
        Map<String, Object> result = new LinkedHashMap<>(); result.put("suite", "Village Standards 0.3.1");
        result.put("passed", false); result.put("started_epoch_ms", System.currentTimeMillis());
        try {
            List<Map<String, Object>> artifacts = new ArrayList<>();
            for (ModContainer mod : Loader.instance().getModList()) {
                File file = mod.getSource(); if (file == null || !file.isFile()) continue;
                Map<String, Object> row = new LinkedHashMap<>(); row.put("mod_id", mod.getModId()); row.put("version", mod.getVersion());
                row.put("file", file.getName()); row.put("bytes", file.length()); row.put("sha256", sha(file)); artifacts.add(row);
            }
            result.put("loaded_artifacts", artifacts);
            FakePlayer player = new FakePlayer(server.getWorld(0), new GameProfile(UUID.fromString("2c2b2f69-16c6-4984-9cb4-31f5f0bef394"), "StandardsNative"));
            result.put("assertions", StandardsNativeChecks.run(player)); result.put("passed", true);
        } catch (Throwable failure) {
            result.put("error", failure.toString()); failure.printStackTrace();
        } finally {
            result.put("finished_epoch_ms", System.currentTimeMillis());
            try {
                Files.write(new File("vale-standards-native-result.json").toPath(),
                    (new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n").getBytes(StandardCharsets.UTF_8));
            } catch (Exception failure) { throw new IllegalStateException("Could not record private native check result", failure); }
            finally { server.initiateShutdown(); }
        }
    }
    private static String sha(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder value = new StringBuilder();
        for (byte part : digest.digest()) value.append(String.format(java.util.Locale.ROOT, "%02x", part & 255));
        return value.toString();
    }
}
