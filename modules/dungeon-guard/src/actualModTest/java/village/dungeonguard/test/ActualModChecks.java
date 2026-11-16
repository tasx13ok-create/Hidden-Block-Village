package village.dungeonguard.test;

import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import village.dungeonguard.GuardRuntime;

/** Private, doubly gated acceptance of pinned real third-party mod releases. */
@Mod(modid = "dungeonguard_actualchecks", name = "Dungeon Guard Actual Mod Checks", version = "1.1.1",
        dependencies = "required-after:dungeonguard;required-after:narutomod;required-after:waystones",
        serverSideOnly = true, acceptableRemoteVersions = "*")
public final class ActualModChecks {
    private final List<Map<String, Object>> checks = new ArrayList<Map<String, Object>>();
    private final Map<String, Object> mods = new LinkedHashMap<String, Object>();
    private final Map<String, Object> groups = new LinkedHashMap<String, Object>();
    private int passed, failed;

    @Mod.EventHandler
    public void started(FMLServerStartedEvent event) {
        if (!Boolean.getBoolean("dungeonguard.runNativeChecks")
                || !new File("DUNGEON_GUARD_DISPOSABLE_TEST_WORLD").isFile()) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        String fatal = null;
        try {
            if (!GuardRuntime.ready() || !server.getPlayerList().getPlayers().isEmpty())
                throw new IllegalStateException("Actual-mod checks require an empty guarded disposable server");
            verifyMod("narutomod", "0.3.1-beta", "6cb62642e995b90f1cf1fc3eecd8b8b68361469460b681384f1fd16d69e378c0");
            verifyMod("waystones", "4.1.0", "5e79f329154d9a6225130f2b29dbcb0f603e2e622637e39ca566f8179b02ca15");
            int p = passed, f = failed;
            RealNarutoNativeChecks.run(server, this::check);
            group("actual_naruto_kamui", p, f);
            p = passed; f = failed;
            RealWaystonesNativeChecks.run(server, this::check);
            group("actual_waystones", p, f);
        } catch (Throwable error) {
            fatal = error.toString(); error.printStackTrace();
            check("actual-mod fixture completes without exception", false);
        } finally {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("suite", "Pinned real Naruto and Waystones releases on a fresh Forge server");
            result.put("success", failed == 0 && passed > 0);
            result.put("passed", passed); result.put("failed", failed);
            result.put("groups", groups); result.put("checks", checks); result.put("mods", mods);
            result.put("fatal", fatal); result.put("java", System.getProperty("java.version"));
            result.put("scope", "Real mod binaries with native server entities and a synthetic connected player; no graphical client, actual saved map, hosted server or live Magma acceptance.");
            try {
                File temporary = new File("actual-mod-result.json.tmp");
                Files.write(temporary.toPath(), new GsonBuilder().setPrettyPrinting().create()
                        .toJson(result).getBytes(StandardCharsets.UTF_8));
                Files.move(temporary.toPath(), new File("actual-mod-result.json").toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable error) { error.printStackTrace(); failed++; }
            System.out.println("DUNGEON_GUARD_ACTUAL_MOD_" + (failed == 0 && passed > 0 ? "PASS" : "FAIL")
                    + " passed=" + passed + " failed=" + failed);
            server.initiateShutdown();
        }
    }

    private void verifyMod(String id, String expectedVersion, String expectedDigest) throws Exception {
        ModContainer mod = Loader.instance().getIndexedModList().get(id);
        if (mod == null) throw new IllegalStateException("Missing real mod " + id);
        String digest = sha256(mod.getSource());
        Map<String, Object> identity = new LinkedHashMap<String, Object>();
        identity.put("version", mod.getVersion()); identity.put("file", mod.getSource().getName());
        identity.put("sha256", digest); mods.put(id, identity);
        if (!expectedVersion.equals(mod.getVersion()) || !expectedDigest.equals(digest))
            throw new IllegalStateException("Unrecognized real mod artifact " + id);
        check("actual mod identity is exact: " + id + " " + expectedVersion, true);
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192]; int size;
            while ((size = stream.read(buffer)) != -1) digest.update(buffer, 0, size);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format("%02x", value & 255));
        return hex.toString();
    }

    private void group(String name, int p, int f) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("passed", passed - p); result.put("failed", failed - f); groups.put(name, result);
    }

    private void check(String name, boolean value) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("name", name); item.put("pass", value); checks.add(item);
        if (value) passed++; else failed++;
        System.out.println("DUNGEON_GUARD_ACTUAL_CHECK " + (value ? "PASS " : "FAIL ") + name);
    }
}
