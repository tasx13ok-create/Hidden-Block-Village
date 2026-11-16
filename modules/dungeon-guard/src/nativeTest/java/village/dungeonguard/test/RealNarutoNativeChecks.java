package village.dungeonguard.test;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import village.dungeonguard.DungeonManager;
import village.dungeonguard.GuardConfig;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.core.HookStatus;

/** Optional acceptance callback requiring the original AHZNB 0.3.1-beta mod. */
public final class RealNarutoNativeChecks {
    private static final UUID OCCUPANT = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private RealNarutoNativeChecks() { }

    public static void run(final MinecraftServer server, BiConsumer<String, Boolean> check) throws Exception {
        if (!GuardRuntime.ready() || !server.getPlayerList().getPlayers().isEmpty())
            throw new IllegalStateException("Real Naruto checks require an empty disposable guarded server");
        final Class<?> procedure = Class.forName("net.narutomod.procedure.ProcedureKamuiTeleportEntity");
        Method eBlock = procedure.getMethod("eBlock", World.class, BlockPos.class, int.class);
        check.accept("real Naruto Kamui helper installs its compatibility gate", HookStatus.summary().contains("AHZNB.kamuiBlock"));
        final GuardConfig original = GuardRuntime.settings();
        final File data = GuardRuntime.configFile().getParentFile();
        final WorldServer world = server.getWorld(0);
        final GuardRegion region = new GuardRegion("real_kamui_native", 0, 1536, 100, 1536, 1539, 102, 1538);
        final BlockPos outside = new BlockPos(1535, 101, 1536);
        final BlockPos outsideChest = new BlockPos(1533, 101, 1536);
        final BlockPos first = new BlockPos(1536, 100, 1536);
        final BlockPos second = new BlockPos(1537, 100, 1536);
        final BlockPos waiting = new BlockPos(1538, 100, 1536);
        final BlockPos chest = new BlockPos(1537, 101, 1537);
        ItemProbe probe = new ItemProbe();
        boolean registered = false;
        MinecraftForge.EVENT_BUS.register(probe);
        try {
            GuardConfig policy = GuardRuntime.settings();
            policy.enabled = true; policy.lockOutsideDungeons = true;
            policy.suppressDungeonBlockDrops = true; policy.protectDungeonTileEntities = true;
            policy.resetDelayTicks = 2; policy.blocksPerTick = 4;
            GuardRuntime.saveAndInstall(policy);
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() {
                    for (int y = region.minY; y <= region.maxY; y++)
                        for (int z = region.minZ; z <= region.maxZ; z++)
                            for (int x = region.minX; x <= region.maxX; x++)
                                world.setBlockState(new BlockPos(x, y, z), y == region.minY ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
                    world.setBlockState(outside, Blocks.STONE.getDefaultState(), 2);
                    seedChest(world, outsideChest); seedChest(world, chest);
                }
            });
            NBTTagCompound outsideNbt = tile(world, outsideChest), arenaNbt = tile(world, chest);
            boolean repeated = true;
            for (int i = 0; i < 3; i++) repeated &= probe.invoke(eBlock, world, outside).empty();
            check.accept("real Kamui repeated protected calls emit no items or dimension transfers", repeated);
            check.accept("real Kamui leaves protected outside stone intact", world.getBlockState(outside).getBlock() == Blocks.STONE);
            check.accept("real Kamui cannot emit a protected container as an item", probe.invoke(eBlock, world, outsideChest).empty());
            check.accept("real Kamui preserves outside container contents exactly", outsideNbt.equals(tile(world, outsideChest)));

            DungeonManager manager = GuardRuntime.manager();
            manager.register(region); registered = true;
            check.accept("real Kamui does nothing while baseline capture is locked", probe.invoke(eBlock, world, first).empty()
                    && world.getBlockState(first).getBlock() == Blocks.STONE);
            await(manager, region.name, "IDLE");
            check.accept("real Kamui cannot destroy or loot an empty captured dungeon", probe.invoke(eBlock, world, first).empty()
                    && world.getBlockState(first).getBlock() == Blocks.STONE);
            manager.updateOccupants(Collections.singletonMap(region.name, Collections.singleton(OCCUPANT)));
            check.accept("real Kamui test arena becomes writable with an occupant", manager.isWritable(world, first));
            Result active = probe.invoke(eBlock, world, first);
            check.accept("real Kamui removes permitted terrain in an active dungeon", world.isAirBlock(first));
            check.accept("real Kamui no-loot result counts zero emitted stacks", active.returned == 0);
            check.accept("real Kamui no-loot destruction creates no item entities", active.spawned == 0);
            check.accept("real Kamui no-loot destruction makes no dimension transfers", active.transferred == 0);
            check.accept("real Kamui respects protected active-dungeon tile entities", probe.invoke(eBlock, world, chest).empty()
                    && arenaNbt.equals(tile(world, chest)));
            check.accept("real Kamui remains contained at the active dungeon boundary", probe.invoke(eBlock, world, outside).empty()
                    && world.getBlockState(outside).getBlock() == Blocks.STONE);

            policy = GuardRuntime.settings(); policy.suppressDungeonBlockDrops = false;
            GuardRuntime.installForTests(policy);
            Result optedIn = probe.invoke(eBlock, world, second);
            check.accept("real Kamui retains explicitly enabled dungeon drops", optedIn.returned == 1 && optedIn.spawned == 1 && optedIn.transferred == 1);
            check.accept("real Kamui original allowed path still erases its target", world.isAirBlock(second));
            policy.suppressDungeonBlockDrops = true; GuardRuntime.installForTests(policy);
            manager.updateOccupants(Collections.<String, Set<UUID>>emptyMap());
            check.accept("real Kamui cannot loot terrain during the departure grace period", probe.invoke(eBlock, world, waiting).empty()
                    && world.getBlockState(waiting).getBlock() == Blocks.STONE);
            await(manager, region.name, "RESTORING_BLOCKS");
            check.accept("real Kamui cannot mutate or loot a resetting dungeon", probe.invoke(eBlock, world, waiting).empty()
                    && world.getBlockState(waiting).getBlock() == Blocks.STONE);
            await(manager, region.name, "IDLE");
            check.accept("real Kamui damage is replaced by the captured baseline after departure", world.getBlockState(first).getBlock() == Blocks.STONE
                    && world.getBlockState(second).getBlock() == Blocks.STONE);
            check.accept("real Kamui compatibility leaves baseline container data intact", arenaNbt.equals(tile(world, chest)));

            policy = GuardRuntime.settings(); policy.enabled = false; GuardRuntime.installForTests(policy);
            Result disabled = probe.invoke(eBlock, world, outside);
            check.accept("real Kamui retains original return and item behavior when policy is disabled", disabled.returned == 1 && disabled.spawned == 1 && disabled.transferred == 1);
            check.accept("real Kamui retains original terrain behavior when policy is disabled", world.isAirBlock(outside));
            world.setBlockState(outside, Blocks.STONE.getDefaultState(), 2);
            // An unrelated integrated world does not start GuardRuntime. Exercise
            // that exact inactive path without launching a second Minecraft JVM.
            GuardRuntime.stop();
            Result inactive = probe.invoke(eBlock, world, outside);
            check.accept("real Kamui retains original item behavior with no guard runtime", inactive.returned == 1 && inactive.spawned == 1 && inactive.transferred == 1);
            check.accept("real Kamui retains original terrain behavior with no guard runtime", world.isAirBlock(outside));
            GuardRuntime.start(server, data);
            check.accept("real Naruto fixture restores the active guard runtime", GuardRuntime.ready() && GuardRuntime.settings().enabled);
        } finally {
            MinecraftForge.EVENT_BUS.unregister(probe);
            probe.cleanup();
            if (!GuardRuntime.ready()) GuardRuntime.start(server, data);
            if (registered) {
                DungeonManager manager = GuardRuntime.manager();
                manager.updateOccupants(Collections.<String, Set<UUID>>emptyMap());
                if (!"IDLE".equals(manager.state(region.name)) && !"FAILED".equals(manager.state(region.name))) await(manager, region.name, "IDLE");
                manager.unregister(region.name);
            }
            GuardRuntime.saveAndInstall(original);
        }
    }

    private static void seedChest(World world, BlockPos pos) {
        world.setBlockState(pos, Blocks.CHEST.getDefaultState(), 2);
        TileEntityChest chest = (TileEntityChest) world.getTileEntity(pos);
        chest.setCustomName("Kamui protected fixture");
        chest.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 5));
    }
    private static NBTTagCompound tile(World world, BlockPos pos) {
        return world.getTileEntity(pos) == null ? null : world.getTileEntity(pos).writeToNBT(new NBTTagCompound());
    }
    private static void await(DungeonManager manager, String name, String desired) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!desired.equals(manager.state(name))) {
            if ("FAILED".equals(manager.state(name)) || System.nanoTime() >= deadline)
                throw new IllegalStateException("Real Naruto fixture cannot reach " + desired + ": " + manager.statusLines());
            manager.tickResets(); Thread.sleep(1L);
        }
    }
    private static final class Result {
        final int returned, spawned, transferred;
        Result(int returned, int spawned, int transferred) { this.returned = returned; this.spawned = spawned; this.transferred = transferred; }
        boolean empty() { return returned == 0 && spawned == 0 && transferred == 0; }
    }
    /** Records entry into both early effect paths; cancels transfer after recording to avoid unrelated portal generation. */
    public static final class ItemProbe {
        private final List<EntityItem> created = new ArrayList<EntityItem>();
        private boolean recording;
        private int spawned, transferred;
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void spawned(EntityJoinWorldEvent event) {
            if (recording && event.getEntity() instanceof EntityItem) { spawned++; created.add((EntityItem) event.getEntity()); }
        }
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void transferred(EntityTravelToDimensionEvent event) {
            if (recording && event.getEntity() instanceof EntityItem) { transferred++; event.setCanceled(true); }
        }
        Result invoke(Method method, World world, BlockPos pos) throws Exception {
            spawned = transferred = 0; recording = true;
            try { return new Result((Integer) method.invoke(null, world, pos, -1), spawned, transferred); }
            catch (InvocationTargetException failure) {
                Throwable cause = failure.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw failure;
            } finally { recording = false; }
        }
        void cleanup() {
            for (EntityItem entity : created) { entity.setDead(); entity.world.removeEntityDangerously(entity); }
            created.clear();
        }
    }
}
