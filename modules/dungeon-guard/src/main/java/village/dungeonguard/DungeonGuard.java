package village.dungeonguard;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.command.ICommand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
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
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import village.dungeonguard.AreaNetwork;
import village.dungeonguard.AreaTracker;
import village.dungeonguard.CommonProxy;
import village.dungeonguard.DungeonManager;
import village.dungeonguard.GuardCommand;
import village.dungeonguard.GuardHooks;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.GuardSelections;
import village.dungeonguard.GuardWand;
import village.dungeonguard.NarutoGameplayCompat;
import village.dungeonguard.OperatorAccess;
import village.dungeonguard.core.HookStatus;

@Mod(modid="dungeonguard", name="Dungeon Guard", version=DungeonGuard.VERSION, acceptedMinecraftVersions="[1.12.2]", acceptableRemoteVersions="*")
public final class DungeonGuard {
    public static final String ID = "dungeonguard";
    public static final String VERSION = "1.4.2";
    @SidedProxy(clientSide="village.dungeonguard.client.ClientProxy", serverSide="village.dungeonguard.CommonProxy")
    public static CommonProxy proxy;
    private static final Logger LOG;
    private MinecraftServer server;
    private int ticks;
    private final Map<UUID, Long> noticeTimes = new HashMap<UUID, Long>();
    private final AreaTracker areaTracker = new AreaTracker();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register((Object)this);
        AreaNetwork.init();
        proxy.preInit(event.getModConfigurationDirectory());
        NarutoGameplayCompat.register();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        this.server = event.getServer();
        this.ticks = 0;
        this.noticeTimes.clear();
        this.areaTracker.clear();
        GuardSelections.clear();
        HookStatus.assertInstalled();
        try {
            File root = this.server.getWorld(0).getSaveHandler().getWorldDirectory();
            File data = new File(root, "dungeon-guard");
            if (!this.server.isDedicatedServer() && !new File(data, "settings.json").isFile()) {
                GuardRuntime.stop();
                LOG.info("Dungeon Guard HUD loaded; this unrelated singleplayer save has no protection configuration.");
                return;
            }
            GuardRuntime.start(this.server, data);
            NarutoGameplayCompat.started(this.server);
        }
        catch (Exception e) {
            throw new IllegalStateException("Dungeon Guard could not load this world's protection. Startup stopped.", e);
        }
        event.registerServerCommand((ICommand)new GuardCommand());
        LOG.info("Dungeon Guard started: operator-scoped outside editing, durable dungeon baselines and optional area HUD. {}", (Object)HookStatus.summary());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        try {
            NarutoGameplayCompat.stopped();
        }
        finally {
            GuardRuntime.stop();
            this.server = null;
            this.noticeTimes.clear();
            this.areaTracker.clear();
            GuardSelections.clear();
        }
    }

    @SubscribeEvent
    public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.server == null || !GuardRuntime.ready()) {
            return;
        }
        DungeonManager manager = GuardRuntime.manager();
        for (EntityPlayerMP player : this.server.getPlayerList().getPlayers()) {
            if (!this.entryLocked(manager, player)) continue;
            try {
                GuardRuntime.spawn().teleportToSpawn(player);
                this.notify(player, "This dungeon is rebuilding. Returned you to the spawn Waystone.");
            }
            catch (Exception e) {
                this.notify(player, "Dungeon entry is locked; spawn Waystone needs attention. " + e.getMessage());
            }
        }
        manager.tick();
        if (++this.ticks % 20 == 0) {
            GuardRuntime.spawn().sync();
        }
        this.areaTracker.tick(this.server, this.ticks);
    }

    private boolean entryLocked(DungeonManager manager, EntityPlayerMP p) {
        for (GuardRegion region : manager.regions()) {
            if (region.dimension != p.dimension || !region.intersects(p.getEntityBoundingBox()) || !manager.isEntryLocked(p.world, new BlockPos(region.minX, region.minY, region.minZ))) continue;
            return true;
        }
        return manager.isEntryLocked(p.world, p.getPosition());
    }

    @SubscribeEvent
    public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || !GuardRuntime.ready()) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP)event.player;
        for (GuardRegion region : GuardRuntime.manager().regions()) {
            if (region.dimension != player.dimension || !region.intersects(player.getEntityBoundingBox())) continue;
            try {
                GuardRuntime.spawn().teleportToSpawn(player);
                this.notify(player, "Returned to spawn after reconnecting inside a dungeon.");
            }
            catch (Exception failure) {
                this.notify(player, "Your saved location is inside a dungeon; the spawn Waystone needs attention: " + failure.getMessage());
            }
            return;
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        this.areaTracker.forget(event.player.getUniqueID());
        this.noticeTimes.remove(event.player.getUniqueID());
        GuardSelections.forget(event.player.getUniqueID());
    }

    @SubscribeEvent
    public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        GuardSelections.forget(event.player.getUniqueID());
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void explosion(ExplosionEvent.Detonate event) {
        if (event.getWorld().isRemote) {
            return;
        }
        Iterator cells = event.getAffectedBlocks().iterator();
        while (cells.hasNext()) {
            if (!GuardHooks.blockDestroy(event.getWorld(), (BlockPos)cells.next())) continue;
            cells.remove();
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void breaking(BlockEvent.BreakEvent event) {
        boolean arena;
        if (GuardWand.breaking(event)) {
            return;
        }
        if (event.getWorld().isRemote || this.playerEditing(event.getPlayer(), event.getWorld(), event.getPos())) {
            return;
        }
        boolean bl = arena = GuardRuntime.manager() != null && GuardRuntime.manager().isArena(event.getWorld(), event.getPos());
        if (GuardRuntime.guarded(event.getWorld(), event.getPos()) || arena && !this.miningAllowed(event.getWorld(), event.getPos())) {
            event.setCanceled(true);
            event.setExpToDrop(0);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void placing(BlockEvent.PlaceEvent event) {
        boolean arena;
        if (event.getWorld().isRemote) {
            return;
        }
        boolean bl = arena = GuardRuntime.manager() != null && GuardRuntime.manager().isArena(event.getWorld(), event.getPos());
        if (!this.playerEditing(event.getPlayer(), event.getWorld(), event.getPos()) && (GuardRuntime.guarded(event.getWorld(), event.getPos()) || arena && !this.buildingAllowed(event.getWorld(), event.getPos()))) {
            event.setCanceled(true);
        }
        if (event instanceof BlockEvent.MultiPlaceEvent) {
            for (BlockSnapshot snapshot : ((BlockEvent.MultiPlaceEvent)event).getReplacedBlockSnapshots()) {
                if (this.playerEditing(event.getPlayer(), event.getWorld(), snapshot.getPos()) || !GuardRuntime.guarded(event.getWorld(), snapshot.getPos()) && (GuardRuntime.manager() == null || !GuardRuntime.manager().isArena(event.getWorld(), snapshot.getPos()) || this.buildingAllowed(event.getWorld(), snapshot.getPos()))) continue;
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void leftClick(PlayerInteractEvent.LeftClickBlock event) {
        boolean arena;
        if (GuardWand.leftClick(event)) {
            return;
        }
        if (event.getWorld().isRemote || this.playerEditing(event.getEntityPlayer(), event.getWorld(), event.getPos())) {
            return;
        }
        boolean bl = arena = GuardRuntime.manager() != null && GuardRuntime.manager().isArena(event.getWorld(), event.getPos());
        if (GuardRuntime.guarded(event.getWorld(), event.getPos()) || arena && !this.miningAllowed(event.getWorld(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (GuardWand.rightClick(event)) {
            return;
        }
        DungeonManager manager = GuardRuntime.manager();
        if (!event.getWorld().isRemote && manager != null && manager.isEntryLocked(event.getWorld(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void drops(BlockEvent.HarvestDropsEvent event) {
        if (GuardHooks.blockDrop(event.getWorld(), event.getPos())) {
            event.getDrops().clear();
            event.setDropChance(0.0f);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void griefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity != null && GuardRuntime.guarded(entity.world, entity.getPosition())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void spawning(EntityJoinWorldEvent event) {
        if (GuardHooks.blockSpawn(event.getWorld(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private boolean playerEditing(EntityPlayer player, World w, BlockPos p) {
        if (!GuardRuntime.ready() || !GuardRuntime.policy().enabled || GuardRuntime.bypassed()) {
            return true;
        }
        DungeonManager manager = GuardRuntime.manager();
        return OperatorAccess.isOperator(player) && (manager == null || !manager.isEntryLocked(w, p));
    }

    private boolean miningAllowed(World world, BlockPos pos) {
        return GuardRuntime.policy().allowPlayerMiningInDungeons || GuardRuntime.manager().isWaystoneZone(world, pos);
    }

    private boolean buildingAllowed(World world, BlockPos pos) {
        return GuardRuntime.policy().allowPlayerBuildingInDungeons || GuardRuntime.manager().isWaystoneZone(world, pos);
    }

    private void notify(EntityPlayerMP player, String message) {
        long now = System.nanoTime();
        Long last = this.noticeTimes.get(player.getUniqueID());
        if (last == null || now - last > 5000000000L) {
            player.sendStatusMessage((ITextComponent)new TextComponentString(message), true);
            this.noticeTimes.put(player.getUniqueID(), now);
        }
    }

    static {
        LOG = LogManager.getLogger((String)"Dungeon Guard");
    }
}

