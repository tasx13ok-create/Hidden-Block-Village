package village.dungeonguard.test;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import village.dungeonguard.DungeonManager;
import village.dungeonguard.GuardConfig;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.OperatorAccess;
import village.dungeonguard.SpawnBinding;

/** Uses the unmodified Waystones 4.1.0 block, item, tile and teleport service. */
public final class RealWaystonesNativeChecks {
    private RealWaystonesNativeChecks() { }

    public static void run(MinecraftServer server, BiConsumer<String, Boolean> check) throws Exception {
        if (!Boolean.getBoolean("dungeonguard.runNativeChecks")
                || !new File("DUNGEON_GUARD_DISPOSABLE_TEST_WORLD").isFile()) return;
        if (!GuardRuntime.ready() || !server.getPlayerList().getPlayers().isEmpty())
            throw new IllegalStateException("Real Waystones checks require an empty guarded disposable server");
        final WorldServer world = server.getWorld(0);
        Block block = Block.REGISTRY.getObject(new ResourceLocation("waystones", "waystone"));
        if (!block.getClass().getName().equals("net.blay09.mods.waystones.block.BlockWaystone"))
            throw new IllegalStateException("Expected the actual upstream Waystones block");
        Item item = Item.getItemFromBlock(block);
        check.accept("real Waystones block and ItemBlock come from the installed release", item instanceof ItemBlock);
        final BlockPos base = new BlockPos(880, 100, 880);
        final BlockPos inner = new BlockPos(902, 100, 882);
        final GuardRegion arena = new GuardRegion("real_waystone", 0, 900, 99, 880, 904, 103, 884);
        final GuardConfig original = new Gson().fromJson(new Gson().toJson(GuardRuntime.settings()), GuardConfig.class);
        final BlockPos oldSpawn = world.getSpawnPoint().toImmutable();
        final String oldRadius = world.getGameRules().getString("spawnRadius");
        final GameProfile profile = new GameProfile(UUID.fromString("10000000-0000-0000-0000-000000000081"), "NativeWaystones");
        final EntityPlayerMP player = new EntityPlayerMP(server, world, profile, new PlayerInteractionManager(world));
        NetworkManager network = new NetworkManager(EnumPacketDirection.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(network);
        network.setConnectionState(EnumConnectionState.PLAY);
        new NetHandlerPlayServer(server, network, player);
        NetworkDispatcher.allocAndSet(network, server.getPlayerList());
        DungeonManager manager = GuardRuntime.manager();
        boolean registered = false;
        try {
            GuardRuntime.withBypass(new Runnable() {
                @Override public void run() {
                    for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
                        for (int y = 99; y <= 103; y++) world.setBlockState(new BlockPos(base.getX() + dx, y, base.getZ() + dz),
                                (y == 99 ? Blocks.STONE : Blocks.AIR).getDefaultState(), 2);
                    for (int x = arena.minX; x <= arena.maxX; x++) for (int z = arena.minZ; z <= arena.maxZ; z++)
                        for (int y = arena.minY; y <= arena.maxY; y++) world.setBlockState(new BlockPos(x, y, z),
                                (y == 99 ? Blocks.STONE : Blocks.AIR).getDefaultState(), 2);
                }
            });
            player.setPosition(base.getX() + .5D, base.getY(), base.getZ() - 2.5D);
            player.rotationYaw = 0F;
            server.getPlayerList().playerLoggedIn(player);
            player.interactionManager.setGameType(GameType.CREATIVE);
            place(player, world, item, base);
            check.accept("non-operator cannot place the actual Waystones item outside a dungeon", world.isAirBlock(base) && world.isAirBlock(base.up()));
            server.getPlayerList().addOp(profile);
            check.accept("actual Waystones builder has connected operator permission", OperatorAccess.isOperator(player));
            place(player, world, item, base);
            check.accept("operator placement creates both actual Waystone halves", complete(world, block, base));
            TileEntity spawnTile = world.getTileEntity(base);
            if (!complete(world, block, base) || spawnTile == null) throw new IllegalStateException("Actual Waystone placement failed");
            check.accept("actual Waystone base owns its real TileWaystone", spawnTile.getClass().getName().equals("net.blay09.mods.waystones.block.TileWaystone")
                    && Boolean.FALSE.equals(call(spawnTile, "isDummy")));
            check.accept("actual Waystone upper half has its dummy tile", world.getTileEntity(base.up()) != null
                    && Boolean.TRUE.equals(call(world.getTileEntity(base.up()), "isDummy")));
            check.accept("actual Waystone placement records the operator as owner", Boolean.TRUE.equals(spawnTile.getClass()
                    .getMethod("isOwner", EntityPlayer.class).invoke(spawnTile, player)));
            spawnTile.getClass().getMethod("setWaystoneName", String.class).invoke(spawnTile, "Acceptance Spawn");
            NBTTagCompound spawnNbt = spawnTile.writeToNBT(new NBTTagCompound());
            server.getPlayerList().removeOp(profile);
            check.accept("non-operator cannot harvest either actual Waystone half", !player.interactionManager.tryHarvestBlock(base.up())
                    && !player.interactionManager.tryHarvestBlock(base) && complete(world, block, base));
            check.accept("blocked actual Waystone harvesting preserves exact tile data", spawnNbt.equals(world.getTileEntity(base).writeToNBT(new NBTTagCompound())));
            check.accept("unattributed world writes cannot erase the actual spawn Waystone", !world.setBlockToAir(base)
                    && !world.setBlockToAir(base.up()) && complete(world, block, base));
            server.getPlayerList().addOp(profile);
            SpawnBinding binding = GuardRuntime.spawn();
            binding.bind(player, base);
            GuardConfig.Spawn bound = GuardRuntime.settings().spawn;
            BlockPos landing = new BlockPos(bound.landingX, bound.landingY, bound.landingZ);
            check.accept("real Waystone binding selects its facing-side safe landing", landing.equals(base.offset(facing(world.getBlockState(base)))));
            check.accept("real Waystone binding sets the actual world spawn and zero radius", world.getSpawnPoint().equals(landing)
                    && world.getGameRules().getInt("spawnRadius") == 0);
            binding.bind(player, base.up());
            check.accept("actual upper Waystone target normalizes to the real base", GuardRuntime.settings().spawn.x == base.getX()
                    && GuardRuntime.settings().spawn.y == base.getY() && GuardRuntime.settings().spawn.z == base.getZ());
            GuardConfig saved = new Gson().fromJson(new String(Files.readAllBytes(GuardRuntime.configFile().toPath()), StandardCharsets.UTF_8), GuardConfig.class);
            check.accept("real Waystone world-spawn binding persists to settings", saved.spawn.bound && saved.spawn.x == base.getX()
                    && saved.spawn.landingX == bound.landingX && saved.spawn.landingZ == bound.landingZ);
            world.setSpawnPoint(base.east(12)); binding.sync();
            check.accept("real Waystone synchronization repairs displaced world spawn", world.getSpawnPoint().equals(landing));
            player.setPosition(860.5D, 100D, 880.5D); player.fallDistance = 30F; player.setFire(20);
            binding.teleportToSpawn(player);
            check.accept("spawn service reaches the actual Waystone landing", at(player, landing));
            check.accept("real spawn travel clears fall damage and burning", player.fallDistance == 0F && !player.isBurning());

            player.setPosition(inner.getX() + .5D, inner.getY(), inner.getZ() - 1.5D);
            place(player, world, item, inner);
            check.accept("real Waystone can be built inside an uncaptured future dungeon", complete(world, block, inner));
            TileEntity innerTile = world.getTileEntity(inner);
            innerTile.getClass().getMethod("setWaystoneName", String.class).invoke(innerTile, "Acceptance Dungeon");
            NBTTagCompound innerNbt = innerTile.writeToNBT(new NBTTagCompound());
            player.setPosition(base.getX() + .5D, base.getY(), base.getZ() - 2.5D);
            GuardConfig activePolicy = GuardRuntime.settings();
            activePolicy.resetDelayTicks = 0;
            GuardRuntime.installForTests(activePolicy);
            manager.register(arena); registered = true; await(manager, arena.name, "IDLE");
            player.setPosition(901.5D, 100D, 881.5D); manager.tick();
            check.accept("actual-mod dungeon becomes active for its connected occupant", "ACTIVE".equals(manager.state(arena.name)) && manager.occupants(arena.name) == 1);
            BlockPos floor = new BlockPos(901, 99, 881);
            check.accept("combat can erase ordinary terrain beside an actual Waystone", world.setBlockToAir(floor));
            check.accept("active-dungeon protection keeps actual Waystone tiles intact", !world.destroyBlock(inner, true)
                    && !world.setBlockToAir(inner.up()) && complete(world, block, inner));
            Class<?> tileClass = Class.forName("net.blay09.mods.waystones.block.TileWaystone");
            Class<?> entryClass = Class.forName("net.blay09.mods.waystones.util.WaystoneEntry");
            Object entry = entryClass.getConstructor(tileClass).newInstance(spawnTile);
            Class<?> waystoneManager = Class.forName("net.blay09.mods.waystones.WaystoneManager");
            // The real service accepts only destinations discovered by this player.
            // Owning a newly placed stone alone does not activate it for travel.
            waystoneManager.getMethod("addPlayerWaystone", EntityPlayer.class, entryClass).invoke(null, player, entry);
            check.accept("actual Waystones destination is recorded as discovered before travel", Boolean.TRUE.equals(
                    waystoneManager.getMethod("checkAndUpdateWaystone", EntityPlayer.class, entryClass).invoke(null, player, entry)));
            Method teleport = waystoneManager.getMethod("teleportToWaystone", EntityPlayer.class, entryClass);
            boolean moved = Boolean.TRUE.equals(teleport.invoke(null, player, entry));
            check.accept("the actual Waystones teleport service moves the player to its spawn landing", moved
                    && player.dimension == 0 && player.posX == landing.getX() + .5D
                    && player.posY == landing.getY() + .5D && player.posZ == landing.getZ() + .5D);
            manager.tick();
            check.accept("actual Waystones travel empties and locks the old dungeon", manager.occupants(arena.name) == 0 && !manager.isWritable(world, floor));
            await(manager, arena.name, "IDLE");
            check.accept("actual Waystones departure triggers clean dungeon terrain restoration", world.getBlockState(floor).getBlock() == Blocks.STONE);
            check.accept("reset preserves both actual Waystone halves and exact base tile data", complete(world, block, inner)
                    && innerNbt.equals(world.getTileEntity(inner).writeToNBT(new NBTTagCompound())));
            manager.unregister(arena.name); registered = false;
            check.accept("actual spawn Waystone stays unchanged during the remote dungeon reset", complete(world, block, base)
                    && spawnNbt.equals(world.getTileEntity(base).writeToNBT(new NBTTagCompound())));

            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            player.interactionManager.tryHarvestBlock(base.up());
            check.accept("operator harvesting the real upper Waystone removes both halves", world.isAirBlock(base) && world.isAirBlock(base.up()));
            boolean refused = false;
            try { binding.teleportToSpawn(player); } catch (CommandException expected) { refused = true; }
            check.accept("spawn service refuses a deleted actual bound Waystone", refused);
            place(player, world, item, base);
            check.accept("operator can repair the same actual bound Waystone", complete(world, block, base));
            binding.sync(); binding.teleportToSpawn(player);
            check.accept("repair resumes the saved actual Waystone spawn binding", at(player, landing) && world.getSpawnPoint().equals(landing));
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            player.interactionManager.tryHarvestBlock(base);
            check.accept("operator harvesting the real lower Waystone removes both halves", world.isAirBlock(base) && world.isAirBlock(base.up()));
        } finally {
            player.setPosition(860.5D, 100D, 880.5D);
            if (registered) { manager.tick(); await(manager, arena.name, "IDLE"); manager.unregister(arena.name); }
            server.getPlayerList().removeOp(profile);
            if (server.getPlayerList().getPlayers().contains(player)) server.getPlayerList().playerLoggedOut(player);
            channel.finishAndReleaseAll();
            GuardRuntime.saveAndInstall(original);
            world.setSpawnPoint(oldSpawn); world.getGameRules().setOrCreateGameRule("spawnRadius", oldRadius);
        }
        check.accept("actual Waystones fixture leaves no player or leaked operator scope", server.getPlayerList().getPlayers().isEmpty()
                && !OperatorAccess.allowed(world, base));
    }

    private static Object call(Object target, String name) throws Exception { return target.getClass().getMethod(name).invoke(target); }
    private static void place(EntityPlayerMP player, WorldServer world, Item item, BlockPos pos) {
        ItemStack stack = new ItemStack(item); player.setHeldItem(EnumHand.MAIN_HAND, stack);
        player.interactionManager.processRightClickBlock(player, world, stack, EnumHand.MAIN_HAND, pos.down(), EnumFacing.UP, .5F, 1F, .5F);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object property(IBlockState state, String name) {
        for (IProperty key : state.getPropertyKeys()) if (key.getName().equals(name)) return state.getValue(key);
        return null;
    }
    private static EnumFacing facing(IBlockState state) { return (EnumFacing) property(state, "facing"); }
    private static boolean complete(WorldServer world, Block block, BlockPos pos) {
        return world.getBlockState(pos).getBlock() == block && world.getBlockState(pos.up()).getBlock() == block
                && Boolean.TRUE.equals(property(world.getBlockState(pos), "base")) && Boolean.FALSE.equals(property(world.getBlockState(pos.up()), "base"));
    }
    private static boolean at(EntityPlayerMP player, BlockPos pos) {
        return player.dimension == 0 && player.posX == pos.getX() + .5D && player.posY == pos.getY() && player.posZ == pos.getZ() + .5D;
    }
    private static void await(DungeonManager manager, String name, String state) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!state.equals(manager.state(name))) {
            if (System.nanoTime() >= deadline || "FAILED".equals(manager.state(name)))
                throw new IllegalStateException("Actual Waystone fixture waiting for " + state + ": " + manager.statusLines());
            manager.tickResets(); Thread.sleep(1L);
        }
    }
}
