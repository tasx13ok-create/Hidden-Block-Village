package village.dungeonguard.test;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import village.dungeonguard.GuardConfig;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.SpawnBinding;

/** Exercises the real spawn service against a deliberately small Waystone-shaped fixture.
 * This fixture verifies the registry/property contract, not the third-party Waystones binary.
 */
@Mod.EventBusSubscriber(modid = "dungeonguard_nativechecks")
public final class SpawnNativeChecks {
    private static final String MARKER = "DUNGEON_GUARD_DISPOSABLE_TEST_WORLD";
    private static final ResourceLocation WAYSTONE_ID = new ResourceLocation("waystones", "waystone");
    private static WaystoneFixture fixture;
    private SpawnNativeChecks() { }

    private static boolean enabled() {
        return Boolean.getBoolean("dungeonguard.runNativeChecks") && new File(MARKER).isFile();
    }

    @SubscribeEvent
    public static void registerFixture(RegistryEvent.Register<Block> event) {
        if (!enabled()) return;
        if (event.getRegistry().containsKey(WAYSTONE_ID)) {
            throw new IllegalStateException("Native spawn checks require a disposable server without an installed Waystones block");
        }
        fixture = new WaystoneFixture();
        fixture.setRegistryName(WAYSTONE_ID);
        event.getRegistry().register(fixture);
    }

    /** Called only by the doubly gated native harness; unexpected exceptions reach its failure record. */
    public static void run(final MinecraftServer server, final BiConsumer<String, Boolean> check) throws Exception {
        if (!enabled()) return;
        if (server == null || check == null || !GuardRuntime.ready()
                || !server.getPlayerList().getPlayers().isEmpty()) {
            throw new IllegalStateException("Spawn checks require an active, empty disposable server");
        }
        check.accept("spawn fixture registers the exact Waystones block identity",
                fixture != null && Block.REGISTRY.getObject(WAYSTONE_ID) == fixture);
        if (fixture == null) throw new IllegalStateException("Waystone fixture was not registered");

        final WorldServer world = server.getWorld(0);
        final SpawnBinding binding = GuardRuntime.spawn();
        final GuardConfig originalSettings = GuardRuntime.settings();
        final BlockPos originalSpawn = world.getSpawnPoint().toImmutable();
        final String originalRadius = world.getGameRules().getString("spawnRadius");
        final BlockPos base = new BlockPos(320, 100, 320);
        final BlockPos unsafe = new BlockPos(336, 100, 320);
        final BlockPos landing = base.north();
        final FakePlayer player = new FakePlayer(world, new GameProfile(
                UUID.fromString("10000000-0000-0000-0000-000000000031"), "NativeSpawn"));
        new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), player);
        try {
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() {
                    buildFixture(world, base, Blocks.STONE.getDefaultState(), false);
                    buildFixture(world, unsafe, Blocks.MAGMA.getDefaultState(), false);
                    world.getGameRules().setOrCreateGameRule("spawnRadius", "17");
                }
            });

            binding.bind(player, base);
            GuardConfig.Spawn saved = GuardRuntime.settings().spawn;
            check.accept("lower Waystone binding records its real base and Overworld", saved.bound
                    && saved.dimension == 0 && saved.x == base.getX() && saved.y == base.getY()
                    && saved.z == base.getZ() && WAYSTONE_ID.toString().equals(saved.blockId));
            check.accept("Waystone landing uses the safe facing-side block", saved.landingX == landing.getX() + 0.5D
                    && saved.landingY == landing.getY() && saved.landingZ == landing.getZ() + 0.5D
                    && world.isAirBlock(landing) && world.isAirBlock(landing.up())
                    && world.getBlockState(landing.down()).getBlock() == Blocks.STONE);
            check.accept("binding moves actual world spawn to the Waystone landing", world.getSpawnPoint().equals(landing));
            check.accept("binding removes randomized spawn radius", world.getGameRules().getInt("spawnRadius") == 0);
            boolean exactProviderSpawn = true;
            for (int i = 0; i < 16; i++) exactProviderSpawn &= world.provider.getRandomizedSpawnPoint().equals(landing);
            check.accept("Forge spawn provider repeatedly chooses the exact bound landing", exactProviderSpawn);

            GuardConfig disk = new Gson().fromJson(new String(Files.readAllBytes(GuardRuntime.configFile().toPath()),
                    StandardCharsets.UTF_8), GuardConfig.class);
            check.accept("Waystone binding persists base and centered landing to settings", sameBinding(saved, disk.spawn));
            GuardConfig unboundMemory = GuardRuntime.settings();
            unboundMemory.spawn.bound = false;
            GuardRuntime.installForTests(unboundMemory);
            GuardRuntime.reload();
            check.accept("saved Waystone binding survives configuration reload", binding.isBound()
                    && sameBinding(saved, GuardRuntime.settings().spawn));

            binding.bind(player, base.up());
            check.accept("targeting the upper Waystone half normalizes to the lower base",
                    sameBinding(saved, GuardRuntime.settings().spawn) && world.getSpawnPoint().equals(landing));

            FakePlayer newPlayer = new FakePlayer(world, new GameProfile(
                    UUID.fromString("10000000-0000-0000-0000-000000000032"), "NativeNewSpawn"));
            check.accept("new player constructor starts at the bound Waystone landing", at(newPlayer, landing));
            final Map<BlockPos, IBlockState> beforeTeleport = blockStates(world, base, 2);
            player.setPosition(base.getX() + 8.5D, 120D, base.getZ() + 8.5D);
            player.motionX = 2D; player.motionY = -4D; player.motionZ = 3D;
            player.fallDistance = 20F; player.setFire(10);
            binding.teleportToSpawn(player);
            check.accept("spawn command uses the real network handler to move the player", at(player, landing)
                    && player.dimension == 0 && player.getServerWorld() == world);
            check.accept("spawn teleport clears fall damage, motion and burning", player.fallDistance == 0F
                    && player.motionX == 0D && player.motionY == 0D && player.motionZ == 0D && !player.isBurning());
            check.accept("spawn teleport creates no portal or landing terrain", beforeTeleport.equals(blockStates(world, base, 2)));

            reject(check, "binding rejects an ordinary block", new CheckedAction() {
                @Override public void run() throws Exception { binding.bind(player, base.down()); }
            });
            check.accept("rejected binding preserves previous saved spawn", sameBinding(saved, GuardRuntime.settings().spawn)
                    && world.getSpawnPoint().equals(landing));
            reject(check, "binding rejects hazardous magma around every landing", new CheckedAction() {
                @Override public void run() throws Exception { binding.bind(player, unsafe); }
            });
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() { buildFixture(world, unsafe, Blocks.STONE.getDefaultState(), true); }
            });
            reject(check, "binding rejects landings without headroom", new CheckedAction() {
                @Override public void run() throws Exception { binding.bind(player, unsafe); }
            });
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() {
                    world.setBlockToAir(base.up());
                    // Recreate only the base: the fixture now removes its paired half on break.
                    world.setBlockState(base, fixture.getDefaultState().withProperty(WaystoneFixture.BASE, true), 2);
                }
            });
            reject(check, "binding rejects a Waystone with its upper half missing", new CheckedAction() {
                @Override public void run() throws Exception { binding.bind(player, base); }
            });
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() { placeStone(world, base); }
            });

            final int originalDimension = player.dimension;
            try {
                player.dimension = -1;
                reject(check, "binding rejects a player outside the Overworld", new CheckedAction() {
                    @Override public void run() throws Exception { binding.bind(player, base); }
                });
            } finally { player.dimension = originalDimension; }
            rejectArenaBinding(world, binding, player, check);

            final BlockPos displaced = base.add(16, 5, 16);
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() {
                    world.setSpawnPoint(displaced);
                    world.getGameRules().setOrCreateGameRule("spawnRadius", "9");
                }
            });
            binding.sync();
            check.accept("periodic synchronization repairs a displaced world spawn", world.getSpawnPoint().equals(landing));
            check.accept("periodic synchronization restores zero spawn radius", world.getGameRules().getInt("spawnRadius") == 0);

            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() {
                    world.setBlockState(landing, Blocks.STONE.getDefaultState(), 2);
                    world.setSpawnPoint(displaced);
                }
            });
            binding.sync();
            check.accept("blocked landing does not silently select another world spawn", world.getSpawnPoint().equals(displaced));
            player.setPosition(350.5D, 120D, 350.5D);
            reject(check, "spawn command refuses an obstructed landing", new CheckedAction() {
                @Override public void run() throws Exception { binding.teleportToSpawn(player); }
            });
            check.accept("refused unsafe teleport leaves player coordinates unchanged", player.posX == 350.5D
                    && player.posY == 120D && player.posZ == 350.5D);
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() {
                    world.setBlockToAir(landing);
                    world.setBlockToAir(base);
                    world.setBlockToAir(base.up());
                }
            });
            binding.sync();
            check.accept("missing Waystone does not invent a replacement spawn", world.getSpawnPoint().equals(displaced)
                    && binding.describe().contains("unavailable"));
            reject(check, "spawn command refuses a missing Waystone", new CheckedAction() {
                @Override public void run() throws Exception { binding.teleportToSpawn(player); }
            });
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() { placeStone(world, base); }
            });
            binding.sync();
            check.accept("repairing the Waystone resumes the same persisted binding", world.getSpawnPoint().equals(landing)
                    && sameBinding(saved, GuardRuntime.settings().spawn) && !binding.describe().contains("unavailable"));
        } finally {
            GuardRuntime.saveAndInstall(originalSettings);
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() {
                    world.setSpawnPoint(originalSpawn);
                    world.getGameRules().setOrCreateGameRule("spawnRadius", originalRadius);
                }
            });
        }
    }

    private static void rejectArenaBinding(final WorldServer world, final SpawnBinding binding,
            final FakePlayer player, BiConsumer<String, Boolean> check) throws Exception {
        BlockPos chosen = null;
        for (GuardRegion region : GuardRuntime.manager().regions()) {
            if (region.dimension != 0) continue;
            for (int y = region.minY; y < region.maxY && chosen == null; y++) {
                BlockPos candidate = new BlockPos(region.minX, y, region.minZ);
                if (world.isAirBlock(candidate) && world.isAirBlock(candidate.up())) chosen = candidate;
            }
        }
        check.accept("spawn rejection fixture has an existing dungeon with two air blocks", chosen != null);
        if (chosen == null) return;
        final BlockPos target = chosen;
        final IBlockState lower = world.getBlockState(target), upper = world.getBlockState(target.up());
        try {
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() { placeStone(world, target); }
            });
            reject(check, "binding rejects a Waystone inside an existing dungeon", new CheckedAction() {
                @Override public void run() throws Exception { binding.bind(player, target); }
            });
        } finally {
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() {
                    world.setBlockState(target, lower, 2);
                    world.setBlockState(target.up(), upper, 2);
                }
            });
        }
    }

    private static void buildFixture(WorldServer world, BlockPos base, IBlockState floor, boolean obstruct) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            world.setBlockState(base.add(dx, -1, dz), floor, 2);
            for (int dy = 0; dy <= 2; dy++) world.setBlockState(base.add(dx, dy, dz), Blocks.AIR.getDefaultState(), 2);
            if (obstruct) world.setBlockState(base.add(dx, 1, dz), Blocks.STONE.getDefaultState(), 2);
        }
        placeStone(world, base);
    }

    private static void placeStone(WorldServer world, BlockPos base) {
        world.setBlockState(base, fixture.getDefaultState().withProperty(WaystoneFixture.BASE, true), 2);
        world.setBlockState(base.up(), fixture.getDefaultState().withProperty(WaystoneFixture.BASE, false), 2);
    }

    private static Map<BlockPos, IBlockState> blockStates(WorldServer world, BlockPos base, int radius) {
        Map<BlockPos, IBlockState> states = new LinkedHashMap<BlockPos, IBlockState>();
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++)
            for (int dy = -1; dy <= 2; dy++) {
                BlockPos pos = base.add(dx, dy, dz);
                states.put(pos, world.getBlockState(pos));
            }
        return states;
    }

    private static boolean at(FakePlayer player, BlockPos feet) {
        return player.posX == feet.getX() + 0.5D && player.posY == feet.getY() && player.posZ == feet.getZ() + 0.5D;
    }

    private static boolean sameBinding(GuardConfig.Spawn a, GuardConfig.Spawn b) {
        return a != null && b != null && a.bound == b.bound && a.dimension == b.dimension
                && a.x == b.x && a.y == b.y && a.z == b.z && a.blockId.equals(b.blockId)
                && a.landingX == b.landingX && a.landingY == b.landingY && a.landingZ == b.landingZ && a.yaw == b.yaw;
    }

    private interface CheckedAction { void run() throws Exception; }
    private static void reject(BiConsumer<String, Boolean> check, String name, CheckedAction action) throws Exception {
        boolean rejected = false;
        try { action.run(); }
        catch (CommandException expected) { rejected = true; }
        check.accept(name, rejected);
    }

    /** Mirrors the official 1.12 registry ID, base property, facing and metadata layout only. */
    private static final class WaystoneFixture extends Block {
        static final PropertyBool BASE = PropertyBool.create("base");
        static final PropertyDirection FACING = BlockHorizontal.FACING;
        WaystoneFixture() {
            super(Material.ROCK);
            setDefaultState(blockState.getBaseState().withProperty(BASE, true).withProperty(FACING, EnumFacing.NORTH));
        }
        @Override protected BlockStateContainer createBlockState() { return new BlockStateContainer(this, BASE, FACING); }
        @Override public IBlockState getStateFromMeta(int meta) {
            return getDefaultState().withProperty(BASE, (meta & 8) != 0).withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3));
        }
        @Override public int getMetaFromState(IBlockState state) {
            return (state.getValue(BASE) ? 8 : 0) | state.getValue(FACING).getHorizontalIndex();
        }
        @Override public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand) {
            // ItemBlock's default metadata is zero, which encodes the saved upper half.
            // A player's newly placed Waystone must always start as its lower half.
            return getDefaultState().withProperty(BASE, true).withProperty(FACING, placer.getHorizontalFacing().getOpposite());
        }
        /** Two-half construction/removal contract, for the operator integration checks. */
        @Override public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
            if (state.getValue(BASE)) world.setBlockState(pos.up(), state.withProperty(BASE, false), 3);
        }
        @Override public void breakBlock(World world, BlockPos pos, IBlockState state) {
            super.breakBlock(world, pos, state);
            BlockPos other = state.getValue(BASE) ? pos.up() : pos.down();
            if (world.getBlockState(other).getBlock() == this) world.setBlockToAir(other);
        }
    }
}
