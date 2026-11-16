package village.dungeonguard.test;

import com.mojang.authlib.GameProfile;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import village.dungeonguard.DungeonManager;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.OperatorAccess;

/** Tests real online-player identity, native action wrappers and native editing commands. */
@Mod.EventBusSubscriber(modid = "dungeonguard_nativechecks")
public final class OperatorNativeChecks {
    private static ProbeItem ability;
    private static ItemBlock waystoneItem;
    private static CallbackBlock callback;
    private static BlockPos other;
    private static boolean throwItem, throwBlock, itemOtherChanged, abilityChanged, callbackChanged;
    private OperatorNativeChecks() { }

    private static boolean enabled() {
        return Boolean.getBoolean("dungeonguard.runNativeChecks")
                && new File("DUNGEON_GUARD_DISPOSABLE_TEST_WORLD").isFile();
    }

    @SubscribeEvent public static void registerItems(RegistryEvent.Register<Item> event) {
        if (!enabled()) return;
        ability = new ProbeItem();
        ability.setRegistryName(new ResourceLocation("dungeonguard_nativechecks", "operator_ability"));
        event.getRegistry().register(ability);
        Block waystone = Block.REGISTRY.getObject(new ResourceLocation("waystones", "waystone"));
        if (waystone == Blocks.AIR) throw new IllegalStateException("Native operator checks require the Waystone contract fixture");
        waystoneItem = new ItemBlock(waystone);
        waystoneItem.setRegistryName(new ResourceLocation("dungeonguard_nativechecks", "operator_waystone_contract"));
        event.getRegistry().register(waystoneItem);
    }

    @SubscribeEvent public static void registerBlocks(RegistryEvent.Register<Block> event) {
        if (!enabled()) return;
        callback = new CallbackBlock();
        callback.setRegistryName(new ResourceLocation("dungeonguard_nativechecks", "operator_callback"));
        event.getRegistry().register(callback);
    }

    public static void run(final MinecraftServer server, final BiConsumer<String, Boolean> check) throws Exception {
        if (!enabled()) return;
        if (server == null || check == null || !GuardRuntime.ready() || !server.getPlayerList().getPlayers().isEmpty())
            throw new IllegalStateException("Operator checks require an active empty disposable server");
        final WorldServer world = server.getWorld(0);
        final BlockPos target = new BlockPos(400, 100, 400);
        other = target.east(3);
        prepare(world, target);
        final GameProfile profile = new GameProfile(UUID.fromString("10000000-0000-0000-0000-000000000041"), "NativeOperator");
        final EntityPlayerMP player = new EntityPlayerMP(server, world, profile, new PlayerInteractionManager(world));
        new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), player);
        player.setPosition(400.5D, 100D, 399.5D);
        server.getPlayerList().playerLoggedIn(player);
        try {
            player.interactionManager.setGameType(GameType.CREATIVE);
            check.accept("connected creative non-operator has no edit permission", !OperatorAccess.isOperator(player));
            check.accept("non-operator manual break cannot remove protected block", !player.interactionManager.tryHarvestBlock(target)
                    && world.getBlockState(target).getBlock() == Blocks.STONE);
            ItemStack stone = new ItemStack(Blocks.COBBLESTONE);
            player.setHeldItem(EnumHand.MAIN_HAND, stone);
            place(player, world, stone, target.east().down());
            check.accept("non-operator manual placement cannot alter protected air", world.isAirBlock(target.east()));
            check.accept("non-operator native setblock remains permission denied", command(server, player,
                    "setblock " + xyz(target) + " minecraft:air") == 0 && world.getBlockState(target).getBlock() == Blocks.STONE);

            server.getPlayerList().addOp(profile);
            check.accept("connected real operator receives edit permission", OperatorAccess.isOperator(player));
            FakePlayer fake = new FakePlayer(world, profile);
            check.accept("FakePlayer sharing an operator profile receives no permission", !OperatorAccess.isOperator(fake));
            EntityPlayerMP duplicate = new EntityPlayerMP(server, world, profile, new PlayerInteractionManager(world));
            check.accept("unconnected duplicate profile cannot impersonate online operator", !OperatorAccess.isOperator(duplicate));
            try (OperatorAccess.Scope outer = OperatorAccess.beginHarvest(player.interactionManager, target)) {
                check.accept("operator scope allows only its selected cell", OperatorAccess.allowed(world, target)
                        && !OperatorAccess.allowed(world, other));
                check.accept("operator scope is bound to its exact world", !OperatorAccess.allowed(server.getWorld(-1), target));
                try (OperatorAccess.Scope nested = OperatorAccess.beginHarvest(fake.interactionManager, target)) {
                    check.accept("unauthorized nested action shadows operator scope", !OperatorAccess.allowed(world, target));
                }
                check.accept("closing nested action restores outer scope", OperatorAccess.allowed(world, target));
                server.getPlayerList().removeOp(profile);
                check.accept("deop immediately revokes an already open scope", !OperatorAccess.allowed(world, target));
                server.getPlayerList().addOp(profile);
            }
            check.accept("closing operator scope keeps later world writes protected", !OperatorAccess.allowed(world, target)
                    && !world.setBlockToAir(target));
            BlockPos waystoneTarget = target.add(6, 0, 0);
            try (OperatorAccess.Scope scope = OperatorAccess.beginUseOn(player.interactionManager, player, world,
                    new ItemStack(waystoneItem), EnumHand.MAIN_HAND, waystoneTarget.down(), EnumFacing.UP)) {
                check.accept("known Waystone placement permits its two-block contract only", OperatorAccess.allowed(world, waystoneTarget)
                        && OperatorAccess.allowed(world, waystoneTarget.up()) && !OperatorAccess.allowed(world, waystoneTarget.up(2)));
            }
            boolean legacyRejected = false;
            try { GuardRuntime.beginEdit(new GuardRegion("legacy", 0, 399, 99, 399, 405, 105, 405), 30); }
            catch (IllegalStateException expected) { legacyRejected = true; }
            check.accept("legacy global edit window cannot unlock other players or abilities", legacyRejected && !world.setBlockToAir(target));

            player.interactionManager.setGameType(GameType.SURVIVAL);
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            int beforeDrops = drops(world, target);
            check.accept("operator native harvest destroys selected outside block", player.interactionManager.tryHarvestBlock(target)
                    && world.isAirBlock(target));
            check.accept("operator manual harvest retains ordinary block drops", drops(world, target) > beforeDrops);
            check.accept("operator harvest does not authorize a later ability write", !world.destroyBlock(other, true)
                    && world.getBlockState(other).getBlock() == Blocks.STONE);
            stone = new ItemStack(Blocks.COBBLESTONE);
            player.setHeldItem(EnumHand.MAIN_HAND, stone);
            place(player, world, stone, target.down());
            check.accept("operator native placement builds outside dungeons", world.getBlockState(target).getBlock() == Blocks.COBBLESTONE);

            BlockPos door = target.add(0, 0, 3);
            ItemStack doorItem = new ItemStack(Items.OAK_DOOR);
            player.setHeldItem(EnumHand.MAIN_HAND, doorItem);
            place(player, world, doorItem, door.down());
            check.accept("operator door placement includes both intended halves", world.getBlockState(door).getBlock() == Blocks.OAK_DOOR
                    && world.getBlockState(door.up()).getBlock() == Blocks.OAK_DOOR);
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            player.interactionManager.tryHarvestBlock(door.up());
            check.accept("operator door removal removes both intended halves", world.isAirBlock(door) && world.isAirBlock(door.up()));

            BlockPos bed = target.add(3, 0, 3);
            player.rotationYaw = 0F;
            ItemStack bedItem = new ItemStack(Items.BED);
            player.setHeldItem(EnumHand.MAIN_HAND, bedItem);
            place(player, world, bedItem, bed.down());
            check.accept("operator bed placement includes both intended halves", world.getBlockState(bed).getBlock() == Blocks.BED
                    && world.getBlockState(bed.south()).getBlock() == Blocks.BED);
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            player.interactionManager.tryHarvestBlock(bed);
            check.accept("operator bed removal removes both intended halves", world.isAirBlock(bed) && world.isAirBlock(bed.south()));

            constructionChecks(world, player, target, check);

            ItemStack abilityStack = new ItemStack(ability);
            player.setHeldItem(EnumHand.MAIN_HAND, abilityStack);
            abilityChanged = false;
            place(player, world, abilityStack, target);
            check.accept("operator right-click ability receives no global terrain bypass", !abilityChanged
                    && world.getBlockState(target).getBlock() == Blocks.COBBLESTONE && world.getBlockState(other).getBlock() == Blocks.STONE);
            itemOtherChanged = false;
            player.interactionManager.tryHarvestBlock(target);
            check.accept("operator synchronous tool callback cannot destroy neighboring terrain", !itemOtherChanged
                    && world.isAirBlock(target) && world.getBlockState(other).getBlock() == Blocks.STONE);
            set(world, target, Blocks.STONE.getDefaultState());
            throwItem = true;
            boolean itemThrew = false;
            try { player.interactionManager.tryHarvestBlock(target); }
            catch (IllegalStateException expected) { itemThrew = true; }
            finally { throwItem = false; }
            check.accept("native player-action exception closes operator permission", itemThrew && !OperatorAccess.allowed(world, target)
                    && !world.setBlockToAir(target));

            BlockPos water = target.add(0, 0, 7);
            player.setPosition(water.getX() + .5D, water.getY() + 2D, water.getZ() + .5D);
            player.rotationYaw = 0F; player.rotationPitch = 90F;
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            player.interactionManager.processRightClick(player, world, player.getHeldItemMainhand(), EnumHand.MAIN_HAND);
            check.accept("operator native bucket can place water at selected ray target", world.getBlockState(water).getMaterial() == Material.WATER);
            player.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            player.interactionManager.processRightClick(player, world, player.getHeldItemMainhand(), EnumHand.MAIN_HAND);
            check.accept("operator native bucket can collect its selected water source", world.isAirBlock(water));

            commandChecks(server, world, player, target, check);
            lockedChecks(server, world, player, check);
        } finally {
            throwItem = false; throwBlock = false;
            server.getPlayerList().removeOp(profile);
            server.getPlayerList().playerLoggedOut(player);
        }
        check.accept("operator checks leave no online player or leaked edit scope", server.getPlayerList().getPlayers().isEmpty()
                && GuardRuntime.guarded(world, target));
    }

    private static void constructionChecks(WorldServer world, EntityPlayerMP player, BlockPos origin,
            BiConsumer<String, Boolean> check) {
        Item[] items = {Items.SIGN, Items.BANNER, Items.SKULL, Items.FIRE_CHARGE};
        Block[] blocks = {Blocks.STANDING_SIGN, Blocks.STANDING_BANNER, Blocks.SKULL, Blocks.FIRE};
        String[] names = {"sign", "banner", "skull", "fire charge"};
        for (int i = 0; i < items.length; i++) {
            BlockPos pos = origin.add(6, 0, i + 1);
            ItemStack stack = new ItemStack(items[i]);
            player.setHeldItem(EnumHand.MAIN_HAND, stack);
            place(player, world, stack, pos.down());
            check.accept("operator can place a vanilla " + names[i] + " outside dungeons", world.getBlockState(pos).getBlock() == blocks[i]);
            player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
            player.interactionManager.tryHarvestBlock(pos);
            check.accept("operator can remove the selected " + names[i] + " without opening nearby terrain",
                    world.isAirBlock(pos) && GuardRuntime.guarded(world, other) && world.getBlockState(other).getBlock() == Blocks.STONE);
        }

        BlockPos water = origin.add(6, 0, 7);
        set(world, water, Blocks.WATER.getDefaultState());
        player.setPosition(water.getX() + .5D, water.getY() + 2D, water.getZ() + .5D);
        player.rotationYaw = 0F; player.rotationPitch = 90F;
        ItemStack lily = new ItemStack(Blocks.WATERLILY);
        player.setHeldItem(EnumHand.MAIN_HAND, lily);
        try (OperatorAccess.Scope scope = OperatorAccess.beginUse(player.interactionManager, player, world, lily, EnumHand.MAIN_HAND)) {
            check.accept("lily-pad scope includes only the cell above its ray-traced water", OperatorAccess.allowed(world, water.up())
                    && !OperatorAccess.allowed(world, water) && !OperatorAccess.allowed(world, water.up(2)));
        }
        player.interactionManager.processRightClick(player, world, lily, EnumHand.MAIN_HAND);
        check.accept("operator native right-click places a lily pad above water", world.getBlockState(water.up()).getBlock() == Blocks.WATERLILY
                && world.getBlockState(water).getMaterial() == Material.WATER);
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactionManager.tryHarvestBlock(water.up());
        check.accept("operator lily-pad removal preserves the protected water source", world.isAirBlock(water.up())
                && world.getBlockState(water).getMaterial() == Material.WATER);
        set(world, water, Blocks.AIR.getDefaultState());

        BlockPos waystone = origin.add(6, 0, 0);
        player.setPosition(origin.getX() + .5D, origin.getY(), origin.getZ() - .5D);
        player.rotationPitch = 0F;
        ItemStack placed = new ItemStack(waystoneItem);
        player.setHeldItem(EnumHand.MAIN_HAND, placed);
        place(player, world, placed, waystone.down());
        check.accept("operator ItemBlock placement completes both Waystone fixture halves",
                world.getBlockState(waystone).getBlock() == waystoneItem.getBlock()
                && world.getBlockState(waystone.up()).getBlock() == waystoneItem.getBlock());
        try (OperatorAccess.Scope scope = OperatorAccess.beginHarvest(player.interactionManager, waystone.up())) {
            check.accept("upper Waystone harvest scope includes its lower half only", OperatorAccess.allowed(world, waystone)
                    && OperatorAccess.allowed(world, waystone.up()) && !OperatorAccess.allowed(world, waystone.up(2)));
        }
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactionManager.tryHarvestBlock(waystone.up());
        check.accept("harvesting the upper Waystone fixture half removes both halves", world.isAirBlock(waystone) && world.isAirBlock(waystone.up()));
        placed = new ItemStack(waystoneItem);
        player.setHeldItem(EnumHand.MAIN_HAND, placed);
        place(player, world, placed, waystone.down());
        boolean secondPlacementComplete = world.getBlockState(waystone).getBlock() == waystoneItem.getBlock()
                && world.getBlockState(waystone.up()).getBlock() == waystoneItem.getBlock();
        player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactionManager.tryHarvestBlock(waystone);
        check.accept("harvesting the lower Waystone fixture half removes both halves", secondPlacementComplete
                && world.isAirBlock(waystone) && world.isAirBlock(waystone.up()));
    }

    private static void commandChecks(MinecraftServer server, WorldServer world, EntityPlayerMP player,
            BlockPos target, BiConsumer<String, Boolean> check) throws Exception {
        BlockPos fill = target.add(8, 0, 5);
        check.accept("server-console native fill edits only its explicit box", command(server, server,
                "fill " + xyz(fill) + " " + xyz(fill.east()) + " minecraft:gold_block") == 1
                && world.getBlockState(fill).getBlock() == Blocks.GOLD_BLOCK && world.getBlockState(fill.east()).getBlock() == Blocks.GOLD_BLOCK
                && world.isAirBlock(fill.east(2)));
        player.setPosition(402.5D, 100D, 407.5D);
        BlockPos relative = player.getPosition();
        check.accept("operator native setblock preserves relative coordinates", command(server, player, "setblock ~ ~ ~ minecraft:diamond_block") == 1
                && world.getBlockState(relative).getBlock() == Blocks.DIAMOND_BLOCK);
        BlockPos copy = fill.south(2);
        check.accept("operator native clone edits its destination", command(server, player,
                "clone " + xyz(fill) + " " + xyz(fill.east()) + " " + xyz(copy)) == 1
                && world.getBlockState(copy).getBlock() == Blocks.GOLD_BLOCK && world.getBlockState(copy.east()).getBlock() == Blocks.GOLD_BLOCK);
        BlockPos moved = copy.south(2);
        check.accept("operator native clone move permits its explicit source and destination", command(server, player,
                "clone " + xyz(copy) + " " + xyz(copy.east()) + " " + xyz(moved) + " replace move") == 1
                && world.isAirBlock(copy) && world.isAirBlock(copy.east()) && world.getBlockState(moved).getBlock() == Blocks.GOLD_BLOCK);

        ICommandSender untrusted = new UntrustedSender(server, world);
        check.accept("permission-claiming mod command sender cannot bypass protected world", command(server, untrusted,
                "setblock " + xyz(target) + " minecraft:air") == 0 && world.getBlockState(target).getBlock() == Blocks.STONE);
        SpoofFill spoof = new SpoofFill(world, target);
        try (OperatorAccess.Scope outer = OperatorAccess.beginHarvest(player.interactionManager, target)) {
            OperatorAccess.runCommand(spoof, server, player, new String[0]);
            check.accept("custom command named fill cannot inherit an operator edit scope", !spoof.changed && OperatorAccess.allowed(world, target));
        }
        callbackChanged = false;
        BlockPos callbackPos = fill.north(2);
        check.accept("console setblock reaches original mod block callbacks", command(server, server,
                "setblock " + xyz(callbackPos) + " dungeonguard_nativechecks:operator_callback") == 1
                && world.getBlockState(callbackPos).getBlock() == callback);
        check.accept("command callback cannot write beyond declared command bounds", !callbackChanged
                && world.getBlockState(other).getBlock() == Blocks.STONE);
        throwBlock = true;
        try {
            check.accept("native command exception does not report success", command(server, server,
                    "setblock " + xyz(callbackPos.east()) + " dungeonguard_nativechecks:operator_callback") == 0);
        } finally { throwBlock = false; }
        check.accept("native command exception closes operator permission", !OperatorAccess.allowed(world, other)
                && !world.setBlockToAir(other));
    }

    private static void lockedChecks(final MinecraftServer server, final WorldServer world,
            final EntityPlayerMP player, final BiConsumer<String, Boolean> check) throws Exception {
        final BlockPos chest = new BlockPos(432, 100, 432);
        final GuardRegion region = new GuardRegion("operator_locked", 0, 432, 100, 432, 433, 101, 432);
        final DungeonManager manager = GuardRuntime.manager();
        GuardRuntime.withBypass(new Runnable() {
            @Override public void run() {
                for (int x = 431; x <= 434; x++) for (int y = 99; y <= 102; y++)
                    world.setBlockState(new BlockPos(x, y, 432), y == 99 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
                world.setBlockState(chest, Blocks.CHEST.getDefaultState(), 2);
                ((TileEntityChest) world.getTileEntity(chest)).setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 11));
            }
        });
        manager.register(region);
        check.accept("operator cannot manually break a capturing dungeon", !player.interactionManager.tryHarvestBlock(chest)
                && diamonds(world, chest) == 11);
        rejectLockedCommands(server, world, player, chest, "capture", check);
        try (OperatorAccess.Scope scope = OperatorAccess.beginHarvest(player.interactionManager, other)) {
            GuardRuntime.withRestoration(region, new Runnable() {
                @Override public void run() {
                    check.accept("restoration suppresses even an existing operator scope", !OperatorAccess.allowed(world, other)
                            && !world.setBlockToAir(other));
                }
            });
        }
        await(manager, region.name, "IDLE");
        manager.forceReset(region.name);
        rejectLockedCommands(server, world, player, chest, "reset", check);
        await(manager, region.name, "IDLE");
        File snapshot = new File(new File(GuardRuntime.configFile().getParentFile(), "snapshots"), region.name + ".snapshot.nbt.gz");
        byte[] original = Files.readAllBytes(snapshot.toPath());
        try {
            Files.delete(snapshot.toPath());
            manager.forceReset(region.name);
            await(manager, region.name, "FAILED");
            rejectLockedCommands(server, world, player, chest, "failed recovery", check);
        } finally { Files.write(snapshot.toPath(), original); }
        manager.forceReset(region.name);
        await(manager, region.name, "IDLE");
        manager.unregister(region.name);
    }

    private static void rejectLockedCommands(MinecraftServer server, WorldServer world, EntityPlayerMP player,
            BlockPos chest, String phase, BiConsumer<String, Boolean> check) {
        check.accept("operator setblock cannot clear inventory during " + phase,
                command(server, player, "setblock " + xyz(chest) + " minecraft:air") == 0 && diamonds(world, chest) == 11);
        check.accept("console fill preflights entire locked intersection during " + phase,
                command(server, server, "fill " + xyz(chest.west()) + " " + xyz(chest.east()) + " minecraft:stone") == 0
                && world.isAirBlock(chest.west()) && diamonds(world, chest) == 11);
        check.accept("clone move cannot clear a locked source during " + phase,
                command(server, server, "clone " + xyz(chest) + " " + xyz(chest) + " " + xyz(chest.south(4)) + " replace move") == 0
                && diamonds(world, chest) == 11 && world.isAirBlock(chest.south(4)));
        check.accept("ordinary clone may read a locked source without mutating it during " + phase,
                command(server, server, "clone " + xyz(chest) + " " + xyz(chest) + " " + xyz(chest.south(6))) == 1
                && diamonds(world, chest) == 11 && diamonds(world, chest.south(6)) == 11);
    }

    private static int diamonds(World world, BlockPos pos) {
        return world.getTileEntity(pos) instanceof TileEntityChest ? ((TileEntityChest) world.getTileEntity(pos)).getStackInSlot(0).getCount() : -1;
    }
    private static void prepare(final World world, final BlockPos target) {
        GuardRuntime.withBypass(new Runnable() {
            @Override public void run() {
                for (int x = -1; x <= 12; x++) for (int z = -1; z <= 12; z++) for (int y = -1; y <= 4; y++)
                    world.setBlockState(target.add(x, y, z), y == -1 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState(), 2);
                world.setBlockState(target, Blocks.STONE.getDefaultState(), 2);
                world.setBlockState(other, Blocks.STONE.getDefaultState(), 2);
            }
        });
    }
    private static void set(final World world, final BlockPos pos, final IBlockState state) {
        GuardRuntime.withBypass(new Runnable() { @Override public void run() { world.setBlockState(pos, state, 2); } });
    }
    private static EnumActionResult place(EntityPlayerMP player, World world, ItemStack stack, BlockPos clicked) {
        return player.interactionManager.processRightClickBlock(player, world, stack, EnumHand.MAIN_HAND, clicked, EnumFacing.UP, .5F, 1F, .5F);
    }
    private static int command(MinecraftServer server, ICommandSender sender, String command) { return server.getCommandManager().executeCommand(sender, command); }
    private static String xyz(BlockPos pos) { return pos.getX() + " " + pos.getY() + " " + pos.getZ(); }
    private static int drops(World world, BlockPos pos) { return world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos).grow(1.5D)).size(); }
    private static void await(DungeonManager manager, String name, String desired) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!desired.equals(manager.state(name))) {
            if (System.nanoTime() >= deadline || ("FAILED".equals(manager.state(name)) && !"FAILED".equals(desired)))
                throw new IllegalStateException("Operator fixture waiting for " + desired + ": " + manager.statusLines());
            manager.tickResets(); Thread.sleep(1L);
        }
    }
    private static final class ProbeItem extends Item {
        @Override public boolean onBlockStartBreak(ItemStack stack, BlockPos pos, EntityPlayer player) {
            itemOtherChanged = player.world.setBlockToAir(other);
            if (throwItem) throw new IllegalStateException("Expected native fixture action failure");
            return false;
        }
        @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                EnumFacing facing, float hitX, float hitY, float hitZ) {
            abilityChanged = world.setBlockToAir(pos) | world.setBlockToAir(other);
            return EnumActionResult.SUCCESS;
        }
    }
    private static final class CallbackBlock extends Block {
        CallbackBlock() { super(Material.ROCK); }
        @Override public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
            callbackChanged = world.setBlockToAir(other);
            if (throwBlock) throw new IllegalStateException("Expected native fixture command failure");
        }
    }
    private static final class UntrustedSender implements ICommandSender {
        private final MinecraftServer server; private final World world;
        UntrustedSender(MinecraftServer server, World world) { this.server = server; this.world = world; }
        @Override public String getName() { return "ClaimedPermission"; }
        @Override public boolean canUseCommand(int level, String name) { return true; }
        @Override public World getEntityWorld() { return world; }
        @Override public MinecraftServer getServer() { return server; }
    }
    private static final class SpoofFill extends CommandBase {
        private final World world; private final BlockPos pos; boolean changed;
        SpoofFill(World world, BlockPos pos) { this.world = world; this.pos = pos; }
        @Override public String getName() { return "fill"; }
        @Override public String getUsage(ICommandSender sender) { return "fill"; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            changed = world.setBlockToAir(pos);
        }
    }
}
