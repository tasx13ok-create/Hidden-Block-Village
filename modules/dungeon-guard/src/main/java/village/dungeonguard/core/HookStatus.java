package village.dungeonguard.core;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Verifies that the mandatory protection gates were installed before play. */
public final class HookStatus {
    private static final Set<String> INSTALLED = java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> REQUIRED = new TreeSet<String>(Arrays.asList(
            "World.setBlockState", "World.destroyBlock", "World.spawnEntity", "Chunk.setBlockState",
            "Block.dropBlockAsItemWithChance", "Block.spawnAsEntity", "BlockPistonBase.doMove",
            "PlayerInteractionManager.operatorHarvest", "PlayerInteractionManager.operatorUseOn",
            "PlayerInteractionManager.operatorUse", "CommandHandler.operatorEdits",
            "EntityPlayer.forceSpawn", "WorldInfo.forceSpawn", "PlayerList.forceRespawn", "PlayerList.firstJoinSpawn"));
    private HookStatus() { }

    static void installed(String name) { INSTALLED.add(name); }

    public static boolean hasHook(String name) { return INSTALLED.contains(name); }

    private static final java.util.Map<String, String> FAILED = new ConcurrentHashMap<String, String>();
    static void failed(String name, String reason) { FAILED.put(name, reason == null ? "unrecognized target" : reason); }
    /** Why an optional hook was not installed, or null. */
    public static String failure(String name) { return FAILED.get(name); }

    public static void assertInstalled() {
        String[] targets = {"net.minecraft.world.World", "net.minecraft.world.chunk.Chunk",
                "net.minecraft.block.Block", "net.minecraft.block.BlockPistonBase",
                "net.minecraft.server.management.PlayerInteractionManager", "net.minecraft.command.CommandHandler",
                "net.minecraft.entity.player.EntityPlayer", "net.minecraft.world.storage.WorldInfo", "net.minecraft.server.management.PlayerList"};
        try {
            for (String target : targets) Class.forName(target, false, HookStatus.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Dungeon Guard cannot load its mandatory terrain/spawn-protection target.", e);
        }
        Set<String> missing = new TreeSet<String>(REQUIRED);
        missing.removeAll(INSTALLED);
        if (!missing.isEmpty()) throw new IllegalStateException("Dungeon Guard coremod did not install required hooks: "
                + missing + ". Refusing to start without terrain and spawn protection.");
        assertMagmaCommands();
    }

    /** A present Bukkit wrapper must be observed in the transformer chain. */
    private static void assertMagmaCommands() {
        String target = "org.bukkit.craftbukkit.v1_12_R1.command.VanillaCommandWrapper";
        ClassLoader loader = HookStatus.class.getClassLoader();
        if (loader.getResource(target.replace('.', '/') + ".class") == null) return;
        try {
            Class.forName(target, false, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Dungeon Guard found Magma/CraftBukkit's vanilla-command wrapper but could not verify it. Startup stopped.", e);
        } catch (LinkageError e) {
            throw new IllegalStateException("Dungeon Guard could not safely load Magma/CraftBukkit's vanilla-command wrapper. Startup stopped.", e);
        }
        if (!INSTALLED.contains("Magma.vanillaCommands"))
            throw new IllegalStateException("Dungeon Guard found Magma/CraftBukkit's vanilla-command wrapper outside the verified transformer chain. "
                    + "The Magma.vanillaCommands guard is missing; refusing to start without command-edit protection.");
    }

    public static String summary() {
        Set<String> all = new TreeSet<String>(INSTALLED);
        Set<String> core = new TreeSet<String>(all);
        core.retainAll(REQUIRED);
        return "core " + core.size() + "/" + REQUIRED.size() + ": " + all;
    }
}
