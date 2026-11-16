package village.dungeonguard.test;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockPistonExtension;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.narutomod.guardfixture.FakeAbility;
import village.dungeonguard.DungeonManager;
import village.dungeonguard.DungeonSnapshot;
import village.dungeonguard.GuardConfig;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.GuardRuntime;

/** Real Forge block/tile/ability checks, restricted to a disposable server world. */
@Mod(modid = "dungeonguard_nativechecks", name = "Dungeon Guard Native Checks",
        version = "1.2.0", serverSideOnly = true, acceptableRemoteVersions = "*")
public final class NativeChecks {
    private static final String MARKER = "DUNGEON_GUARD_DISPOSABLE_TEST_WORLD";
    private static final UUID ONE = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TWO = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private final List<Map<String, Object>> checks = new ArrayList<Map<String, Object>>();
    private int passed;
    private int failed;

    @Mod.EventHandler
    public void onStarted(FMLServerStartedEvent event) {
        // Missing either gate is inert, including no server shutdown.
        if (!Boolean.getBoolean("dungeonguard.runNativeChecks") || !new File(MARKER).isFile()) return;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        String fatal = null;
        try {
            if (!GuardRuntime.ready()) throw new IllegalStateException("Guard runtime is not ready");
            if (!server.getPlayerList().getPlayers().isEmpty()) throw new IllegalStateException("Native checks require an empty disposable server");
            runChecks(server);
        } catch (Throwable error) {
            fatal = error.toString();
            error.printStackTrace();
            check("fixture completes without exception", false);
        } finally {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("suite", "Dungeon Guard real Forge arena lifecycle");
            result.put("passed", passed);
            result.put("failed", failed);
            result.put("success", failed == 0 && passed > 0);
            result.put("checks", checks);
            result.put("fatal", fatal);
            result.put("java", System.getProperty("java.version"));
            result.put("scope", "Real Forge with synthetic ability caller and simulated occupants; not acceptance of the user's Naruto or Magma binaries");
            try {
                File temporary = new File("native-result.json.tmp");
                Files.write(temporary.toPath(), new GsonBuilder().setPrettyPrinting().create()
                        .toJson(result).getBytes(StandardCharsets.UTF_8));
                Files.move(temporary.toPath(), new File("native-result.json").toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable error) { error.printStackTrace(); failed++; }
            System.out.println("DUNGEON_GUARD_NATIVE_" + (failed == 0 && passed > 0 ? "PASS" : "FAIL")
                    + " passed=" + passed + " failed=" + failed);
            server.initiateShutdown();
        }
    }

    private void runChecks(final MinecraftServer server) throws Exception {
        final WorldServer world = server.getWorld(0);
        final File dataDirectory = GuardRuntime.configFile().getParentFile();
        GuardConfig config = new GuardConfig();
        config.resetDelayTicks = 3;
        config.blocksPerTick = 4;
        config.maxSnapshotBlocks = 10000;
        GuardRuntime.saveAndInstall(config);
        DungeonManager manager = GuardRuntime.manager();
        if (!manager.regions().isEmpty()) throw new IllegalStateException("Disposable fixture contains existing dungeons");

        final GuardRegion a = new GuardRegion("native_a", 0, 256, 99, 256, 260, 102, 258);
        final GuardRegion b = new GuardRegion("native_b", 0, 272, 99, 256, 274, 102, 258);
        final BlockPos floor = new BlockPos(256, 99, 256);
        final BlockPos chunkFloor = new BlockPos(260, 99, 258);
        final BlockPos air = new BlockPos(260, 101, 258);
        final BlockPos chestPos = new BlockPos(257, 100, 257);
        final BlockPos signPos = new BlockPos(258, 100, 257);
        final BlockPos stairPos = new BlockPos(259, 100, 257);
        final BlockPos pistonHead = new BlockPos(256, 100, 258);
        final BlockPos outsidePiston = pistonHead.west();
        final BlockPos furnacePos = new BlockPos(260, 100, 257);
        final IBlockState furnaceState = Blocks.FURNACE.getDefaultState().withProperty(BlockFurnace.FACING, EnumFacing.EAST);
        final IBlockState extendedPiston = Blocks.PISTON.getDefaultState()
                .withProperty(BlockPistonBase.FACING, EnumFacing.EAST).withProperty(BlockPistonBase.EXTENDED, true);
        final IBlockState stair = Blocks.QUARTZ_STAIRS.getDefaultState()
                .withProperty(BlockStairs.FACING, EnumFacing.WEST).withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP);
        GuardRuntime.withBypass(new Runnable() {
            @Override public void run() {
                buildArena(world, a);
                buildArena(world, b);
                world.setBlockState(chestPos, Blocks.CHEST.getDefaultState(), 2);
                TileEntityChest chest = (TileEntityChest) world.getTileEntity(chestPos);
                chest.setCustomName("Baseline chest");
                chest.setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 7));
                world.setBlockState(signPos, Blocks.STANDING_SIGN.getDefaultState(), 2);
                TileEntitySign sign = (TileEntitySign) world.getTileEntity(signPos);
                sign.signText[0] = new TextComponentString("Clean dungeon");
                sign.signText[1] = new TextComponentString("Exact tile restore");
                world.setBlockState(stairPos, stair, 2);
                world.setBlockState(new BlockPos(255, 100, 256), Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(outsidePiston, extendedPiston, 2);
                world.setBlockState(furnacePos.east(), Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(furnacePos, furnaceState, 2);
                // Deliberately preserve a valid facing that vanilla onBlockAdded would rotate.
                world.setBlockState(furnacePos, furnaceState, 2);
            }
        });
        final NBTTagCompound chestNbt = world.getTileEntity(chestPos).writeToNBT(new NBTTagCompound());
        final NBTTagCompound signNbt = world.getTileEntity(signPos).writeToNBT(new NBTTagCompound());
        final NBTTagCompound furnaceNbt = world.getTileEntity(furnacePos).writeToNBT(new NBTTagCompound());

        BlockPos outside = new BlockPos(255, 100, 256);
        check("outside direct World erasure is blocked", !world.setBlockToAir(outside));
        check("outside synthetic ability erasure is blocked", !FakeAbility.erase(world, outside));
        check("outside direct Chunk write is blocked", FakeAbility.chunkSet(world, outside, Blocks.AIR.getDefaultState()) == null);
        check("outside protected block survives all paths", world.getBlockState(outside).getBlock() == Blocks.STONE);

        manager.register(a);
        check("registration queues capture rather than unlocking", "CAPTURING".equals(manager.state(a.name)) && !manager.isWritable(world, floor));
        check("capture blocks entry and mutations", manager.isEntryLocked(world, floor) && !FakeAbility.erase(world, floor));
        check("region is not persisted before capture completes", GuardRuntime.settings().regions.isEmpty());
        manager.tickResets();
        check("capture honors per-tick budget", "CAPTURING".equals(manager.state(a.name)));
        await(manager, a.name, "IDLE");
        manager.register(b);
        await(manager, b.name, "IDLE");
        check("completed captures persist both region definitions", GuardRuntime.settings().regions.size() == 2);
        check("empty captured dungeon remains protected", !manager.isWritable(world, floor) && !world.setBlockToAir(floor));
        expectFailure("overlapping dungeon registration is rejected", new Action() {
            @Override public void run() throws Exception { GuardRuntime.manager().register(new GuardRegion("overlap", 0, 260, 100, 258, 262, 101, 260)); }
        });
        expectFailure("oversized dungeon registration is rejected", new Action() {
            @Override public void run() throws Exception { GuardRuntime.manager().register(new GuardRegion("oversize", 0, 400, 1, 400, 500, 100, 500)); }
        });

        File baseline = new File(new File(dataDirectory, "snapshots"), "native_a.snapshot.nbt.gz");
        check("baseline gzip and checksum exist", baseline.isFile() && new File(baseline.getPath() + ".sha256").isFile());
        DungeonSnapshot loaded = DungeonSnapshot.load(baseline, a, config.maxSnapshotBlocks);
        check("baseline persists complete bounded volume including air", loaded.size() == a.volume() && loaded.tileCount() == 3);
        final GuardRegion wrongDimension = new GuardRegion(a.name, -1, a.minX, a.minY, a.minZ, a.maxX, a.maxY, a.maxZ);
        expectFailure("snapshot rejects dimension mismatch", new Action() {
            @Override public void run() throws Exception {
                DungeonSnapshot.load(new File(new File(dataDirectory, "snapshots"), "native_a.snapshot.nbt.gz"), wrongDimension, 10000);
            }
        });

        manager.updateOccupants(presence(a.name, ONE, TWO));
        check("two occupants activate the dungeon", "ACTIVE".equals(manager.state(a.name)) && manager.occupants(a.name) == 2);
        check("durable recovery marker precedes writable arena", new File(new File(dataDirectory, "snapshots"), "native_a.dirty").isFile() && manager.isWritable(world, floor));
        int drops = itemCount(world, floor);
        check("ability destroyBlock works during fight", FakeAbility.destroy(world, floor, true));
        check("fight destruction actually removes terrain", world.isAirBlock(floor));
        check("destroyed dungeon terrain produces no farmable drops", itemCount(world, floor) == drops);
        check("direct Chunk setter works in occupied dungeon", FakeAbility.chunkSet(world, chunkFloor, Blocks.AIR.getDefaultState()) != null && world.isAirBlock(chunkFloor));
        check("ability can place temporary terrain inside active arena", FakeAbility.set(world, air, Blocks.DIRT.getDefaultState()));
        check("neighboring empty dungeon remains locked", !world.setBlockToAir(new BlockPos(272, 99, 256)));
        check("outside boundary remains locked while arena active", !world.setBlockToAir(outside));
        check("minimum corner is included", manager.isWritable(world, new BlockPos(256, 99, 256)));
        check("maximum corner is included", manager.isWritable(world, new BlockPos(260, 102, 258)));
        check("one block beyond X is outside arena", !manager.isArena(world, new BlockPos(261, 100, 257)));
        check("one block below Y is outside arena", !manager.isArena(world, new BlockPos(258, 98, 257)));
        check("one block beyond Z is outside arena", !manager.isArena(world, new BlockPos(258, 100, 259)));
        WorldServer nether = server.getWorld(-1);
        check("same coordinates in another dimension are outside arena", nether != null && !manager.isArena(nether, floor));
        check("container block survives active fight by default", !FakeAbility.destroy(world, chestPos, true));
        ((TileEntityChest) world.getTileEntity(chestPos)).setInventorySlotContents(0, new ItemStack(Blocks.DIRT, 2));
        ((TileEntitySign) world.getTileEntity(signPos)).signText[0] = new TextComponentString("Combat changed this");
        check("block-state property damage is allowed during combat", world.setBlockState(stairPos, stair.withProperty(BlockStairs.FACING, EnumFacing.EAST), 2));
        combatChecks(world, outside);
        check("active arena accepts boundary piston head", world.setBlockState(pistonHead,
                Blocks.PISTON_HEAD.getDefaultState().withProperty(BlockPistonExtension.FACING, EnumFacing.EAST), 2));
        GuardConfig vulnerableTiles = GuardRuntime.settings();
        vulnerableTiles.protectDungeonTileEntities = false;
        GuardRuntime.installForTests(vulnerableTiles);
        check("opt-in vulnerable tile setting allows furnace removal", FakeAbility.erase(world, furnacePos));
        vulnerableTiles.protectDungeonTileEntities = true;
        GuardRuntime.installForTests(vulnerableTiles);
        final ArrayList<BlockSnapshot> originalCaptureBuffer = world.capturedBlockSnapshots;
        final BlockSnapshot sentinel = BlockSnapshot.getBlockSnapshot(world, chestPos, 2);
        final NBTTagCompound sentinelData = new NBTTagCompound();
        sentinel.writeToNBT(sentinelData);
        final int originalCaptureSize = originalCaptureBuffer.size();
        originalCaptureBuffer.add(sentinel);

        manager.updateOccupants(presence(a.name, TWO));
        for (int i = 0; i < 8; i++) manager.tickResets();
        check("first player's exit does not reset while second remains", "ACTIVE".equals(manager.state(a.name)) && world.isAirBlock(floor));
        manager.updateOccupants(Collections.<String, Set<UUID>>emptyMap());
        check("last exit enters delayed reset", "WAITING".equals(manager.state(a.name)) && !manager.isWritable(world, floor));
        manager.tickResets();
        manager.updateOccupants(presence(a.name, ONE));
        check("reentry during grace period cancels reset", "ACTIVE".equals(manager.state(a.name)) && world.isAirBlock(floor));
        manager.updateOccupants(Collections.<String, Set<UUID>>emptyMap());
        await(manager, a.name, "RESTORING_BLOCKS");
        check("reset locks entry and all terrain writes", manager.isEntryLocked(world, floor) && !FakeAbility.set(world, air, Blocks.STONE.getDefaultState()));
        manager.updateOccupants(presence(a.name, ONE));
        String pausedProgress = manager.statusLines().toString();
        manager.tickResets();
        check("unexpected occupant pauses in-progress restoration", pausedProgress.equals(manager.statusLines().toString()));
        manager.updateOccupants(Collections.<String, Set<UUID>>emptyMap());
        await(manager, a.name, "IDLE");
        checkBaseline(world, floor, chunkFloor, air, stairPos, stair, chestPos, chestNbt, signPos, signNbt);
        check("reset removes temporary piston head inside arena", world.isAirBlock(pistonHead));
        check("restoration callbacks cannot remove outside piston base", world.getBlockState(outsidePiston).equals(extendedPiston));
        check("reset restores exact furnace facing despite vanilla placement callback", world.getBlockState(furnacePos).equals(furnaceState));
        check("reset restores exact furnace tile NBT", world.getTileEntity(furnacePos) != null
                && world.getTileEntity(furnacePos).writeToNBT(new NBTTagCompound()).equals(furnaceNbt));
        check("reset preserves existing Forge snapshot buffer reference and entries", world.capturedBlockSnapshots == originalCaptureBuffer
                && originalCaptureBuffer.size() == originalCaptureSize + 1 && originalCaptureBuffer.get(originalCaptureSize) == sentinel);
        NBTTagCompound sentinelAfter = new NBTTagCompound();
        sentinel.writeToNBT(sentinelAfter);
        check("reset preserves preexisting Forge snapshot NBT exactly", sentinelData.equals(sentinelAfter));
        if (originalCaptureBuffer.size() == originalCaptureSize + 1 && originalCaptureBuffer.get(originalCaptureSize) == sentinel) {
            originalCaptureBuffer.remove(originalCaptureSize);
        }
        check("reset keeps dungeon locked until next occupant", !manager.isWritable(world, floor));
        manager.forceReset(a.name);
        await(manager, a.name, "IDLE");
        check("repeated reset is idempotent", world.getTileEntity(chestPos).writeToNBT(new NBTTagCompound()).equals(chestNbt));

        realPresenceChecks(server, world, manager, a, b);

        manager.updateOccupants(presence(a.name, ONE));
        check("pre-restart fight mutates a block", FakeAbility.erase(world, floor));
        GuardRuntime.start(server, dataDirectory);
        manager = GuardRuntime.manager();
        check("dirty restart schedules locked recovery", manager.isEntryLocked(world, floor) && "RESTORING_BLOCKS".equals(manager.state(a.name)));
        check("dirty restart does not expose damaged arena", !manager.isWritable(world, floor));
        await(manager, a.name, "IDLE");
        await(manager, b.name, "IDLE");
        check("restart recovery restores clean terrain", world.getBlockState(floor).getBlock() == Blocks.STONE);
        check("restart reload preserves exact chest inventory NBT", world.getTileEntity(chestPos).writeToNBT(new NBTTagCompound()).equals(chestNbt));

        byte[] valid = Files.readAllBytes(baseline.toPath());
        try {
            byte[] corrupt = Arrays.copyOf(valid, valid.length);
            corrupt[corrupt.length / 2] ^= 0x40;
            Files.write(baseline.toPath(), corrupt);
            GuardRuntime.start(server, dataDirectory);
            manager = GuardRuntime.manager();
            check("corrupt baseline fails closed on restart", "FAILED".equals(manager.state(a.name)) && manager.isEntryLocked(world, floor));
            check("corrupt baseline never unlocks destructive writes", !FakeAbility.erase(world, floor));
        } finally {
            Files.write(baseline.toPath(), valid);
        }
        manager.forceReset(a.name);
        await(manager, a.name, "IDLE");
        await(manager, b.name, "IDLE");
        check("restoring valid baseline permits explicit recovery", world.getBlockState(floor).getBlock() == Blocks.STONE);
        try {
            Files.delete(baseline.toPath());
            GuardRuntime.start(server, dataDirectory);
            manager = GuardRuntime.manager();
            check("missing baseline fails closed instead of capturing damage", "FAILED".equals(manager.state(a.name)) && manager.isEntryLocked(world, floor));
        } finally { Files.write(baseline.toPath(), valid); }
        manager.forceReset(a.name);
        await(manager, a.name, "IDLE");
        await(manager, b.name, "IDLE");
        manager.unregister(a.name);
        check("unregister persists removal and retains original snapshot", GuardRuntime.settings().regions.size() == 1 && baseline.isFile());
        expectFailure("register refuses overwrite of preserved baseline", new Action() {
            @Override public void run() throws Exception { GuardRuntime.manager().register(a); }
        });
        SpawnNativeChecks.run(server, this::check);
        village.dungeonguard.AreaNativeChecks.run(server, this::check);
        OperatorNativeChecks.run(server, this::check);
        SetupNativeChecks.run(server, this::check);
    }

    private void realPresenceChecks(MinecraftServer server, WorldServer world, DungeonManager manager,
            GuardRegion a, GuardRegion b) throws Exception {
        FakePlayer first = FakePlayerFactory.get(world, new GameProfile(ONE, "NativePresence1"));
        FakePlayer second = FakePlayerFactory.get(world, new GameProfile(TWO, "NativePresence2"));
        try {
            first.setPosition(257.5, 100.0, 256.5);
            second.setPosition(258.5, 100.0, 256.5);
            first.interactionManager.setGameType(GameType.SPECTATOR);
            server.getPlayerList().getPlayers().add(first);
            server.getPlayerList().getPlayers().add(second);
            manager.tick();
            check("production online-player scan counts both occupants", manager.occupants(a.name) == 2);
            check("spectator presence is counted", first.isSpectator() && manager.occupants(a.name) == 2);
            expectFailure("manual reset refuses a currently occupied arena", new Action() {
                @Override public void run() throws Exception { GuardRuntime.manager().forceReset("native_a"); }
            });
            first.setPosition(0.5, 100.0, 0.5);
            manager.tick();
            check("teleport to spawn leaves second fighter active", manager.occupants(a.name) == 1 && "ACTIVE".equals(manager.state(a.name)));
            second.setPosition(273.5, 100.0, 257.5);
            manager.tick();
            check("teleport into different dungeon exits old and enters new", manager.occupants(a.name) == 0 && manager.occupants(b.name) == 1 && "ACTIVE".equals(manager.state(b.name)));
            server.getPlayerList().getPlayers().remove(second);
            manager.tick();
            check("disconnect triggers empty-dungeon reset path", manager.occupants(b.name) == 0 && !manager.isWritable(world, new BlockPos(272, 99, 256)));
            first.setPosition(261.2, 100.0, 257.5);
            manager.tick();
            check("body overlapping boundary still counts as occupied", manager.occupants(a.name) == 1);
            first.setPosition(261.4, 100.0, 257.5);
            manager.tick();
            check("whole body beyond boundary leaves the dungeon", manager.occupants(a.name) == 0);
        } finally {
            server.getPlayerList().getPlayers().remove(first);
            server.getPlayerList().getPlayers().remove(second);
            manager.updateOccupants(Collections.<String, Set<UUID>>emptyMap());
        }
        await(manager, a.name, "IDLE");
        await(manager, b.name, "IDLE");
    }

    private void combatChecks(final WorldServer world, BlockPos outside) {
        BlockPos breakable = new BlockPos(257, 100, 256);
        check("active arena accepts explosion test block", world.setBlockState(breakable, Blocks.GLASS.getDefaultState(), 2));
        EntityPig pig = new EntityPig(world);
        pig.setPosition(256.5, 101.0, 257.5);
        world.spawnEntity(pig);
        float health = pig.getHealth();
        world.newExplosion(null, 256.5, 100.5, 256.5, 2.0F, false, true);
        check("explosion destroys terrain inside active dungeon", world.isAirBlock(breakable));
        check("same explosion cannot damage outside world", world.getBlockState(outside).getBlock() == Blocks.STONE);
        check("explosion still damages combat entity", pig.getHealth() < health || pig.isDead);
        check("explosion still applies knockback", Math.abs(pig.motionX) + Math.abs(pig.motionY) + Math.abs(pig.motionZ) > 0.0);
        pig.setDead();
        EntityPig direct = new EntityPig(world);
        direct.setPosition(258.5, 101.0, 256.5);
        world.spawnEntity(direct);
        float before = direct.getHealth();
        check("synthetic ability direct combat damage remains functional", FakeAbility.damage(direct, 3.0F) && direct.getHealth() < before);
        direct.setDead();
    }

    private void checkBaseline(World world, BlockPos floor, BlockPos chunkFloor, BlockPos air,
            BlockPos stairPos, IBlockState stair, BlockPos chestPos, NBTTagCompound chest, BlockPos signPos, NBTTagCompound sign) {
        check("reset restores World-deleted floor", world.getBlockState(floor).getBlock() == Blocks.STONE);
        check("reset restores directly changed chunk block", world.getBlockState(chunkFloor).getBlock() == Blocks.STONE);
        check("reset removes placed terrain by restoring baseline air", world.isAirBlock(air));
        check("reset restores exact stair orientation and half", world.getBlockState(stairPos).equals(stair));
        check("reset restores complete chest inventory and custom name NBT", world.getTileEntity(chestPos) != null && world.getTileEntity(chestPos).writeToNBT(new NBTTagCompound()).equals(chest));
        check("reset restores complete sign text NBT", world.getTileEntity(signPos) != null && world.getTileEntity(signPos).writeToNBT(new NBTTagCompound()).equals(sign));
    }

    private static void buildArena(World world, GuardRegion region) {
        for (int y = region.minY; y <= region.maxY; y++) {
            for (int z = region.minZ; z <= region.maxZ; z++) {
                for (int x = region.minX; x <= region.maxX; x++) {
                    world.setBlockState(new BlockPos(x, y, z), y == region.minY ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
                }
            }
        }
    }

    private void await(DungeonManager manager, String name, String desired) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!desired.equals(manager.state(name))) {
            if ("FAILED".equals(manager.state(name))) throw new IllegalStateException(manager.statusLines().toString());
            if (System.nanoTime() >= deadline) throw new IllegalStateException("Timed out waiting for " + name + " -> " + desired + ": " + manager.statusLines());
            manager.tickResets();
            Thread.sleep(1L); // Allow immutable snapshot I/O jobs to run; never busy-spin.
        }
    }

    private static Map<String, Set<UUID>> presence(String name, UUID... occupants) {
        Map<String, Set<UUID>> map = new HashMap<String, Set<UUID>>();
        map.put(name, new HashSet<UUID>(Arrays.asList(occupants)));
        return map;
    }
    private static int itemCount(World world, BlockPos pos) {
        return world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(2.0)).size();
    }
    private interface Action { void run() throws Exception; }
    private void expectFailure(String name, Action action) throws Exception {
        try { action.run(); check(name, false); }
        catch (IllegalArgumentException expected) { check(name, true); }
        catch (IOException expected) { check(name, true); }
    }
    private void check(String name, boolean pass) {
        Map<String, Object> record = new LinkedHashMap<String, Object>();
        record.put("name", name); record.put("pass", pass); checks.add(record);
        if (pass) passed++; else failed++;
        System.out.println("DUNGEON_GUARD_CHECK " + (pass ? "PASS " : "FAIL ") + name);
    }
}
