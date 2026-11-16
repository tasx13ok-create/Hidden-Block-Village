package village.dungeonguard;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import village.dungeonguard.core.HookStatus;

@Mod(modid = DungeonGuard.ID, name = "Dungeon Guard", version = DungeonGuard.VERSION, acceptedMinecraftVersions = "[1.12.2]",
     acceptableRemoteVersions = "*")
public final class DungeonGuard {
    public static final String ID = "dungeonguard";
    public static final String VERSION = "1.1.1";
    @SidedProxy(clientSide = "village.dungeonguard.client.ClientProxy", serverSide = "village.dungeonguard.CommonProxy")
    public static CommonProxy proxy;
    private static final Logger LOG = LogManager.getLogger("Dungeon Guard");
    private MinecraftServer server;
    private int ticks;
    private final Map<UUID, Long> noticeTimes = new HashMap<UUID, Long>();
    private final AreaTracker areaTracker = new AreaTracker();

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this); AreaNetwork.init(); proxy.preInit(event.getModConfigurationDirectory());
    }
    @Mod.EventHandler public void serverStarting(FMLServerStartingEvent event) {
        server = event.getServer(); ticks = 0; noticeTimes.clear(); areaTracker.clear();
        HookStatus.assertInstalled();
        try {
            File root = server.getWorld(0).getSaveHandler().getWorldDirectory();
            File data = new File(root, "dungeon-guard");
            if (!server.isDedicatedServer() && !new File(data, "settings.json").isFile()) {
                GuardRuntime.stop();
                LOG.info("Dungeon Guard HUD loaded; this unrelated singleplayer save has no protection configuration.");
                return;
            }
            GuardRuntime.start(server, data);
        } catch (Exception e) { throw new IllegalStateException("Dungeon Guard could not load this world's protection. Startup stopped.", e); }
        event.registerServerCommand(new GuardCommand());
        LOG.info("Dungeon Guard started: operator-scoped outside editing, durable dungeon baselines and optional area HUD. {}", HookStatus.summary());
    }
    @Mod.EventHandler public void serverStopped(FMLServerStoppedEvent event) {
        GuardRuntime.stop(); server = null; noticeTimes.clear(); areaTracker.clear();
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || !GuardRuntime.ready()) return;
        DungeonManager manager = GuardRuntime.manager();
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            if (entryLocked(manager, player)) {
                try { GuardRuntime.spawn().teleportToSpawn(player); notify(player, "This dungeon is rebuilding. Returned you to the spawn Waystone."); }
                catch (Exception e) { notify(player, "Dungeon entry is locked; spawn Waystone needs attention. " + e.getMessage()); }
            }
        }
        manager.tick();
        if (++ticks % 20 == 0) GuardRuntime.spawn().sync();
        areaTracker.tick(server, ticks);
    }
    private boolean entryLocked(DungeonManager manager, EntityPlayerMP p) {
        for (GuardRegion region : manager.regions())
            if (region.dimension == p.dimension && region.intersects(p.getEntityBoundingBox())
                    && manager.isEntryLocked(p.world, new BlockPos(region.minX, region.minY, region.minZ))) return true;
        // Capturing regions are deliberately not persisted until a complete baseline exists.
        return manager.isEntryLocked(p.world, p.getPosition());
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || !GuardRuntime.ready()) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        for (GuardRegion region : GuardRuntime.manager().regions()) {
            if (region.dimension == player.dimension && region.intersects(player.getEntityBoundingBox())) {
                try { GuardRuntime.spawn().teleportToSpawn(player); notify(player, "Returned to spawn after reconnecting inside a dungeon."); }
                catch (Exception failure) { notify(player, "Your saved location is inside a dungeon; the spawn Waystone needs attention: " + failure.getMessage()); }
                return;
            }
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        areaTracker.forget(event.player.getUniqueID()); noticeTimes.remove(event.player.getUniqueID());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void explosion(ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote) return;
        Iterator<BlockPos> cells = event.getAffectedBlocks().iterator();
        while (cells.hasNext()) if (GuardHooks.blockDestroy(event.getWorld(), cells.next())) cells.remove();
        // Entity list is intentionally preserved: damage and knockback still run.
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void breaking(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote || playerEditing(event.getPlayer(), event.getWorld(), event.getPos())) return;
        boolean arena = GuardRuntime.manager() != null && GuardRuntime.manager().isArena(event.getWorld(), event.getPos());
        if (GuardRuntime.guarded(event.getWorld(), event.getPos()) || (arena && !GuardRuntime.policy().allowPlayerMiningInDungeons)) {
            event.setCanceled(true); event.setExpToDrop(0);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void placing(BlockEvent.PlaceEvent event) {
        if (event.getWorld().isRemote) return;
        boolean arena = GuardRuntime.manager() != null && GuardRuntime.manager().isArena(event.getWorld(), event.getPos());
        if (!playerEditing(event.getPlayer(), event.getWorld(), event.getPos())
                && (GuardRuntime.guarded(event.getWorld(), event.getPos()) || (arena && !GuardRuntime.policy().allowPlayerBuildingInDungeons))) event.setCanceled(true);
        if (event instanceof BlockEvent.MultiPlaceEvent) {
            for (net.minecraftforge.common.util.BlockSnapshot snapshot : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots())
                if (!playerEditing(event.getPlayer(), event.getWorld(), snapshot.getPos())
                        && (GuardRuntime.guarded(event.getWorld(), snapshot.getPos())
                        || (GuardRuntime.manager() != null && GuardRuntime.manager().isArena(event.getWorld(), snapshot.getPos())
                        && !GuardRuntime.policy().allowPlayerBuildingInDungeons))) event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getWorld().isRemote || playerEditing(event.getEntityPlayer(), event.getWorld(), event.getPos())) return;
        boolean arena = GuardRuntime.manager() != null && GuardRuntime.manager().isArena(event.getWorld(), event.getPos());
        if (GuardRuntime.guarded(event.getWorld(), event.getPos()) || (arena && !GuardRuntime.policy().allowPlayerMiningInDungeons)) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void rightClick(PlayerInteractEvent.RightClickBlock event) {
        DungeonManager manager = GuardRuntime.manager();
        if (!event.getWorld().isRemote && manager != null && manager.isEntryLocked(event.getWorld(), event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void drops(BlockEvent.HarvestDropsEvent event) {
        if (GuardHooks.blockDrop(event.getWorld(), event.getPos())) { event.getDrops().clear(); event.setDropChance(0.0F); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void griefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity != null && GuardRuntime.guarded(entity.world, entity.getPosition())) event.setResult(Event.Result.DENY);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public void spawning(EntityJoinWorldEvent event) {
        if (GuardHooks.blockSpawn(event.getWorld(), event.getEntity())) event.setCanceled(true);
    }
    private boolean playerEditing(EntityPlayer player, net.minecraft.world.World w, BlockPos p) {
        if (!GuardRuntime.ready() || !GuardRuntime.policy().enabled || GuardRuntime.bypassed()) return true;
        DungeonManager manager = GuardRuntime.manager();
        return OperatorAccess.isOperator(player) && (manager == null || !manager.isEntryLocked(w, p));
    }
    private void notify(EntityPlayerMP player, String message) {
        long now = System.nanoTime(); Long last = noticeTimes.get(player.getUniqueID());
        if (last == null || now - last > 5000000000L) { player.sendMessage(new TextComponentString("[Dungeon Guard] " + message)); noticeTimes.put(player.getUniqueID(), now); }
    }
}
