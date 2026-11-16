package village.dungeonguard.test;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import village.dungeonguard.DungeonManager;
import village.dungeonguard.DungeonSnapshot;
import village.dungeonguard.GuardConfig;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.GuardSelections;
import village.dungeonguard.GuardWand;
import village.dungeonguard.OperatorAccess;

/** Exercises the registered setup commands and real interaction-event pipeline. */
public final class SetupNativeChecks {
    private static final BlockPos SPAWN = new BlockPos(580, 100, 580);
    private static final BlockPos FIRST = new BlockPos(600, 99, 600);
    private static final BlockPos SECOND = new BlockPos(604, 102, 604);
    private SetupNativeChecks() { }

    public static void run(final MinecraftServer server, final BiConsumer<String, Boolean> check) throws Exception {
        if (!Boolean.getBoolean("dungeonguard.runNativeChecks")
                || !new File("DUNGEON_GUARD_DISPOSABLE_TEST_WORLD").isFile()) return;
        if (!GuardRuntime.ready() || !server.getPlayerList().getPlayers().isEmpty())
            throw new IllegalStateException("Setup checks require an empty guarded disposable server");
        final WorldServer world = server.getWorld(0);
        final GuardConfig original = GuardRuntime.settings();
        final BlockPos oldSpawn = world.getSpawnPoint().toImmutable();
        final String oldRadius = world.getGameRules().getString("spawnRadius");
        final GameProfile profile = new GameProfile(UUID.fromString("10000000-0000-0000-0000-000000000121"), "NativeSetup");
        final RecordingPlayer player = new RecordingPlayer(server, world, profile);
        new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), player);
        final DungeonManager manager = GuardRuntime.manager();
        final Set<String> originalNames = names(manager);
        final List<String> created = new ArrayList<String>();
        boolean loggedIn = false;
        try {
            GuardConfig policy = GuardRuntime.settings(); policy.spawn = new GuardConfig.Spawn();
            policy.resetDelayTicks = 0; policy.blocksPerTick = 4; policy.maxSnapshotBlocks = 10000;
            GuardRuntime.saveAndInstall(policy);
            prepare(world);
            player.setPosition(SPAWN.getX() + .5D, SPAWN.getY(), SPAWN.getZ() - 2.5D);
            player.rotationYaw = 0F; player.rotationPitch = 10F;
            server.getPlayerList().playerLoggedIn(player); loggedIn = true;
            player.interactionManager.setGameType(GameType.CREATIVE);

            ICommand primary = server.getCommandManager().getCommands().get("dungeonguard");
            check.accept("setup command registers dungeon and dg aliases for the original command", primary != null
                    && primary == server.getCommandManager().getCommands().get("dg")
                    && primary == server.getCommandManager().getCommands().get("dungeon"));
            player.messages.clear();
            check.accept("non-operator gets the public clickable dungeon menu", command(server, player, "dungeon") == 1
                    && clicks(player).contains("/dungeon spawn") && !clicks(player).contains("/dungeon setup"));
            String protectedSettings = json(GuardRuntime.settings());
            for (String denied : new String[] {"setup", "wand", "setspawn", "create fire Unapproved", "boss Unapproved"})
                check.accept("non-operator cannot use new setup action: " + denied.split(" ")[0],
                        command(server, player, "dungeon " + denied) == 0);
            check.accept("denied setup actions change neither configuration nor inventory", protectedSettings.equals(json(GuardRuntime.settings()))
                    && wandCount(player) == 0 && names(manager).equals(originalNames));

            server.getPlayerList().addOp(profile);
            player.messages.clear();
            check.accept("operator setup presents clickable spawn wand and boss steps", command(server, player, "dungeon setup") == 1
                    && clicks(player).contains("/dungeon setspawn") && clicks(player).contains("/dungeon wand")
                    && clicks(player).contains("/dungeon boss "));
            check.accept("wand command grants a marked vanilla stick in the main hand", command(server, player, "dungeon wand") == 1
                    && GuardWand.isWand(player.getHeldItemMainhand()) && player.getHeldItemMainhand().getItem() == Items.STICK);
            ItemStack wand = player.getHeldItemMainhand().copy();
            check.accept("repeated wand command reuses the existing tool", command(server, player, "dg wand") == 1 && wandCount(player) == 1);
            ItemStack[] inventory = copyInventory(player);
            for (int slot = 0; slot < player.inventory.mainInventory.size(); slot++)
                player.inventory.setInventorySlotContents(slot, new ItemStack(Blocks.STONE, 64));
            ItemStack[] full = copyInventory(player);
            player.inventory.setInventorySlotContents(9, ItemStack.EMPTY);
            check.accept("full creative hotbar grants the wand into a free inventory slot without replacing hotbar items",
                    command(server, player, "dungeon wand") == 1 && GuardWand.isWand(player.inventory.getStackInSlot(9))
                    && wandCount(player) == 1 && sameHotbar(player, full));
            restoreInventory(player, full);
            check.accept("full inventory refuses wand without overwriting held items", command(server, player, "dungeon wand") == 0
                    && sameInventory(player, full));
            restoreInventory(player, inventory); player.setHeldItem(EnumHand.MAIN_HAND, wand.copy());

            command(server, player, "dungeon clear");
            server.getPlayerList().removeOp(profile);
            player.interactionManager.onBlockClicked(FIRST, EnumFacing.UP);
            click(player, world, SECOND, EnumHand.MAIN_HAND);
            check.accept("a copied marked stick gives non-operators no selection or destruction", GuardSelections.get(player) == null
                    && world.getBlockState(FIRST).getBlock() == Blocks.STONE && world.getBlockState(SECOND).getBlock() == Blocks.CHEST);
            server.getPlayerList().addOp(profile);
            player.interactionManager.onBlockClicked(FIRST, EnumFacing.UP);
            check.accept("operator wand left-click selects the exact first block without breaking it", GuardSelections.get(player) != null
                    && FIRST.equals(GuardSelections.get(player).first) && world.getBlockState(FIRST).getBlock() == Blocks.STONE);
            Container beforeContainer = player.openContainer;
            NBTTagCompound chestBefore = world.getTileEntity(SECOND).writeToNBT(new NBTTagCompound());
            click(player, world, SECOND, EnumHand.MAIN_HAND);
            check.accept("operator wand right-click selects without opening or changing a container", GuardSelections.get(player).complete()
                    && SECOND.equals(GuardSelections.get(player).second) && player.openContainer == beforeContainer
                    && chestBefore.equals(world.getTileEntity(SECOND).writeToNBT(new NBTTagCompound())));
            player.setHeldItem(EnumHand.OFF_HAND, new ItemStack(Blocks.COBBLESTONE));
            BlockPos offhand = new BlockPos(606, 99, 600);
            click(player, world, offhand, EnumHand.OFF_HAND);
            check.accept("wand suppresses offhand follow-up placement and corner changes", SECOND.equals(GuardSelections.get(player).second)
                    && world.isAirBlock(offhand.up()));
            check.accept("wand also cancels direct operator harvesting through the break event", !player.interactionManager.tryHarvestBlock(FIRST)
                    && world.getBlockState(FIRST).getBlock() == Blocks.STONE);
            player.messages.clear();
            check.accept("selection command reports inclusive corners and volume", command(server, player, "dungeon selection") == 1
                    && text(player).contains("600 99 600") && text(player).contains("604 102 604") && text(player).contains("100 blocks"));
            check.accept("create refuses an unbound spawn without starting a capture", command(server, player, "dungeon create fire Ember Trial") == 0
                    && names(manager).equals(originalNames) && json(GuardRuntime.settings().dungeonLabels).equals(json(original.dungeonLabels)));

            player.rotationYaw = 0F; player.rotationPitch = 10F;
            check.accept("setspawn links the looked-at Waystone without coordinate arguments", command(server, player, "dungeon setspawn") == 1
                    && GuardRuntime.settings().spawn.bound && GuardRuntime.settings().spawn.x == SPAWN.getX()
                    && GuardRuntime.settings().spawn.y == SPAWN.getY() && world.getSpawnPoint().equals(SPAWN.north())
                    && world.getGameRules().getInt("spawnRadius") == 0);
            server.getPlayerList().removeOp(profile);
            player.setPosition(570.5D, 100D, 580.5D);
            check.accept("ordinary player dungeon spawn command reaches the exact safe bound landing", command(server, player, "dungeon spawn") == 1
                    && player.dimension == 0 && player.posX == SPAWN.getX() + .5D && player.posY == SPAWN.getY()
                    && player.posZ == SPAWN.getZ() - .5D);
            server.getPlayerList().addOp(profile);
            Set<String> beforeCreate = names(manager);
            check.accept("simple create accepts an element and multiword display name", command(server, player, "dungeon create fire Ember Trial") == 1);
            GuardRegion first = added(manager, beforeCreate); created.add(first.name);
            check.accept("wand selection becomes the exact inclusive capture bounds", sameBounds(first, new GuardRegion("unused", 0,
                    FIRST.getX(), FIRST.getY(), FIRST.getZ(), SECOND.getX(), SECOND.getY(), SECOND.getZ())));
            check.accept("create keeps both region and auto label unpublished until capture verifies", "CAPTURING".equals(manager.state(first.name))
                    && !hasRegion(GuardRuntime.settings(), first.name) && !GuardRuntime.settings().dungeonLabels.containsKey(first.name)
                    && manager.isEntryLocked(world, FIRST));
            check.accept("boss creation refuses a parent whose clean copy is still saving", command(server, player, "dungeon boss Premature Boss") == 0
                    && GuardRuntime.settings().bossAreas.size() == original.bossAreas.size());
            player.messages.clear(); await(manager, first.name, "IDLE");
            GuardConfig saved = diskConfig(); GuardConfig.DungeonLabel firstLabel = saved.dungeonLabels.get(first.name);
            check.accept("verified capture persists its region and automatic element label together", hasRegion(saved, first.name)
                    && firstLabel != null && "FIRE".equals(firstLabel.element) && "Ember Trial".equals(firstLabel.displayName));
            DungeonSnapshot snapshot = DungeonSnapshot.load(snapshot(first.name), first, GuardRuntime.settings().maxSnapshotBlocks);
            check.accept("simple create writes a durable complete clean snapshot", snapshot.size() == 100 && snapshot.tileCount() == 1
                    && new File(snapshot(first.name).getPath() + ".sha256").isFile());
            check.accept("capture sends its connected creator one ready notification", occurrences(text(player), "Ready: Ember Trial") == 1);
            for (int i = 0; i < 4; i++) manager.tickResets();
            check.accept("ready notification is not repeated on later ticks", occurrences(text(player), "Ready: Ember Trial") == 1);
            GuardRuntime.reload();
            check.accept("automatic dungeon label survives settings reload", "Ember Trial".equals(GuardRuntime.settings().dungeonLabels.get(first.name).displayName));

            select(server, player, new BlockPos(601, 100, 601), new BlockPos(602, 101, 602));
            int oldBosses = GuardRuntime.settings().bossAreas.size();
            check.accept("simple boss command infers its saved parent from selected bounds", command(server, player, "dungeon boss Pain Deva Path") == 1
                    && GuardRuntime.settings().bossAreas.size() == oldBosses + 1);
            GuardConfig.BossZone boss = lastBoss();
            check.accept("simple boss persists parent identity name and chamber bounds", first.name.equals(boss.dungeon)
                    && "Pain Deva Path".equals(boss.bossName) && boss.bounds.minX == 601 && boss.bounds.maxX == 602
                    && diskConfig().bossAreas.size() == oldBosses + 1);
            String bossId = boss.id;
            check.accept("reusing exact boss corners updates the encounter without duplicates", command(server, player, "dungeon boss Pain Awakened") == 1
                    && GuardRuntime.settings().bossAreas.size() == oldBosses + 1 && bossId.equals(lastBoss().id)
                    && "Pain Awakened".equals(lastBoss().bossName));
            String stable = json(GuardRuntime.settings());
            select(server, player, new BlockPos(602, 100, 602), new BlockPos(603, 101, 603));
            check.accept("partial boss overlap is rejected without changing saved metadata", command(server, player, "dungeon boss Overlap") == 0
                    && stable.equals(json(GuardRuntime.settings())));
            select(server, player, new BlockPos(604, 100, 604), new BlockPos(605, 101, 605));
            check.accept("boss bounds crossing a dungeon boundary are rejected", command(server, player, "dungeon boss Outside") == 0
                    && stable.equals(json(GuardRuntime.settings())));
            select(server, player, FIRST, SECOND);
            check.accept("invalid element cannot start an unlabelled capture", command(server, player, "dungeon create not_an_element Invalid") == 0
                    && stable.equals(json(GuardRuntime.settings())));
            check.accept("overlong display name is rejected before capture or label changes", command(server, player,
                    "dungeon create fire " + repeat('x', 81)) == 0 && stable.equals(json(GuardRuntime.settings())));
            check.accept("invalid boss display characters leave saved encounter unchanged", command(server, player, "dungeon boss Bad\nBoss") == 0
                    && stable.equals(json(GuardRuntime.settings())));
            check.accept("out-of-world corner coordinates cannot replace a valid selection", command(server, player, "dg pos2 604 256 604") == 0
                    && SECOND.equals(GuardSelections.get(player).second));
            select(server, player, new BlockPos(700, 0, 700), new BlockPos(799, 255, 799));
            check.accept("oversized setup selection creates no region or orphan label", command(server, player, "dungeon create earth Too Large") == 0
                    && stable.equals(json(GuardRuntime.settings())));
            select(server, player, SPAWN.down().north(), SPAWN.up());
            check.accept("setup refuses a dungeon containing the spawn Waystone landing", command(server, player, "dungeon create water Spawn Trap") == 0
                    && stable.equals(json(GuardRuntime.settings())));

            labelLifecycle(server, player, manager, world, created, check);
            legacyRoutes(server, player, manager, created, check);

            byte[] cleanCopy = Files.readAllBytes(snapshot(first.name).toPath());
            check.accept("remove clears automatic labels and boss zones but retains clean copy", command(server, player, "dg remove " + first.name) == 1
                    && !GuardRuntime.settings().dungeonLabels.containsKey(first.name) && !containsBossParent(first.name)
                    && snapshot(first.name).isFile());
            select(server, player, new BlockPos(612, 99, 600), new BlockPos(614, 101, 602));
            beforeCreate = names(manager);
            check.accept("same display name can create another dungeon after removal", command(server, player, "dungeon create fire Ember Trial") == 1);
            GuardRegion next = added(manager, beforeCreate); created.add(next.name); await(manager, next.name, "IDLE");
            check.accept("automatic ID skips retained baselines instead of overwriting them", !first.name.equals(next.name)
                    && java.util.Arrays.equals(cleanCopy, Files.readAllBytes(snapshot(first.name).toPath())));
            select(server, player, new BlockPos(618, 99, 600), new BlockPos(620, 101, 602));
            beforeCreate = names(manager);
            check.accept("element-only create supplies a readable default dungeon name", command(server, player, "dungeon create water") == 1);
            GuardRegion water = added(manager, beforeCreate); created.add(water.name); await(manager, water.name, "IDLE");
            check.accept("default dungeon caption and element persist together", "Water Dungeon".equals(GuardRuntime.settings().dungeonLabels.get(water.name).displayName)
                    && "WATER".equals(GuardRuntime.settings().dungeonLabels.get(water.name).element));
            player.messages.clear();
            check.accept("all three registered command routes retain public status access", command(server, player, "dg status") == 1
                    && command(server, player, "dungeonguard list") == 1 && command(server, player, "dungeon info") == 1
                    && text(player).contains("Ready"));
            player.messages.clear();
            check.accept("advanced help retains the existing capture label and boss commands", command(server, player, "dg help advanced") == 1
                    && text(player).contains("/dg capture") && text(player).contains("/dg label") && text(player).contains("/dg bossarea"));

            select(server, player, FIRST, SECOND);
            check.accept("clear removes only session corners and preserves saved dungeons", command(server, player, "dungeon clear") == 1
                    && GuardSelections.get(player) == null && hasRegion(GuardRuntime.settings(), next.name));
            select(server, player, FIRST, SECOND);
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, 0, -1));
            check.accept("registered dimension-change event clears the old selection", GuardSelections.get(player) == null);
            select(server, player, FIRST, SECOND);
            server.getPlayerList().playerLoggedOut(player); loggedIn = false;
            check.accept("actual logout clears selection and revokes connected operator access", GuardSelections.get(player) == null
                    && !OperatorAccess.isOperator(player));
        } finally {
            player.setPosition(SPAWN.getX() + .5D, SPAWN.getY(), SPAWN.getZ() - 2.5D);
            GuardConfig cleanupPolicy = GuardRuntime.settings(); cleanupPolicy.maxSnapshotBlocks = Math.max(10000, original.maxSnapshotBlocks);
            GuardRuntime.installForTests(cleanupPolicy);
            for (String name : created) if (names(manager).contains(name)) {
                if (!"FAILED".equals(manager.state(name))) await(manager, name, "IDLE");
                manager.unregister(name);
            }
            server.getPlayerList().removeOp(profile);
            if (loggedIn) server.getPlayerList().playerLoggedOut(player);
            GuardSelections.forget(profile.getId());
            GuardRuntime.saveAndInstall(original);
            world.setSpawnPoint(oldSpawn); world.getGameRules().setOrCreateGameRule("spawnRadius", oldRadius);
        }
        check.accept("setup fixture leaves no players new arenas or operator permissions", server.getPlayerList().getPlayers().isEmpty()
                && names(manager).equals(originalNames) && !OperatorAccess.allowed(world, FIRST));
    }

    private static void labelLifecycle(MinecraftServer server, RecordingPlayer player, DungeonManager manager, WorldServer world,
            List<String> created, BiConsumer<String, Boolean> check) throws Exception {
        GuardRegion region = new GuardRegion("setup_label_copy", 0, 632, 99, 600, 634, 101, 602);
        GuardConfig.DungeonLabel label = new GuardConfig.DungeonLabel(); label.element = "WIND"; label.displayName = "Original Caption";
        manager.register(region, label, player.getUniqueID()); created.add(region.name);
        label.element = "FIRE"; label.displayName = "Caller Changed Caption"; region.name = "caller_changed_id";
        check.accept("labelled capture does not publish an orphan label while pending", !GuardRuntime.settings().dungeonLabels.containsKey("setup_label_copy"));
        await(manager, "setup_label_copy", "IDLE");
        GuardConfig.DungeonLabel saved = GuardRuntime.settings().dungeonLabels.get("setup_label_copy");
        check.accept("pending capture owns a defensive copy of region and display metadata", "WIND".equals(saved.element)
                && "Original Caption".equals(saved.displayName) && hasRegion(GuardRuntime.settings(), "setup_label_copy"));
        GuardConfig.DungeonLabel invalid = new GuardConfig.DungeonLabel(); invalid.displayName = "Bad\nCaption";
        Set<String> oldNames = names(manager); boolean rejected = false;
        try { manager.register(new GuardRegion("setup_bad_label", 0, 640, 99, 600, 642, 101, 602), invalid); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check.accept("invalid pending metadata is rejected before creating any arena or file", rejected && oldNames.equals(names(manager))
                && !snapshot("setup_bad_label").exists() && !GuardRuntime.settings().dungeonLabels.containsKey("setup_bad_label"));
        GuardRegion failed = new GuardRegion("setup_failed_capture", 0, 648, 99, 600, 654, 102, 606);
        GuardConfig.DungeonLabel failLabel = new GuardConfig.DungeonLabel(); failLabel.element = "EARTH"; failLabel.displayName = "Failed Capture";
        manager.register(failed, failLabel, player.getUniqueID()); created.add(failed.name);
        GuardConfig tighter = GuardRuntime.settings(); tighter.maxSnapshotBlocks = 150; GuardRuntime.saveAndInstall(tighter);
        player.messages.clear(); await(manager, failed.name, "FAILED");
        check.accept("capture validation failure publishes neither a region nor its label", !hasRegion(diskConfig(), failed.name)
                && !diskConfig().dungeonLabels.containsKey(failed.name) && !GuardRuntime.settings().dungeonLabels.containsKey(failed.name));
        check.accept("failed labelled capture keeps its selected terrain locked", manager.isEntryLocked(world, new BlockPos(failed.minX, failed.minY, failed.minZ))
                && !world.setBlockToAir(new BlockPos(failed.minX, failed.minY, failed.minZ)));
        check.accept("failed capture notifies the creator without a false ready message", text(player).contains("Could not finish saving " + failed.name)
                && !text(player).contains("Ready:"));
        tighter = GuardRuntime.settings(); tighter.maxSnapshotBlocks = 10000; GuardRuntime.saveAndInstall(tighter);
    }

    private static void legacyRoutes(MinecraftServer server, RecordingPlayer player, DungeonManager manager,
            List<String> created, BiConsumer<String, Boolean> check) throws Exception {
        select(server, player, new BlockPos(624, 99, 600), new BlockPos(626, 101, 602));
        check.accept("legacy capture still accepts an explicit dungeon ID", command(server, player, "dg capture setup_legacy") == 1);
        created.add("setup_legacy"); await(manager, "setup_legacy", "IDLE");
        check.accept("legacy label command updates the saved element and caption", command(server, player, "dg label setup_legacy wind Legacy Wind Shrine") == 1
                && "WIND".equals(GuardRuntime.settings().dungeonLabels.get("setup_legacy").element)
                && "Legacy Wind Shrine".equals(GuardRuntime.settings().dungeonLabels.get("setup_legacy").displayName));
        select(server, player, new BlockPos(625, 100, 601), new BlockPos(625, 101, 601));
        check.accept("legacy explicit bossarea and bosslabel commands still persist", command(server, player, "dg bossarea setup_legacy legacy_setup_boss Old Boss") == 1
                && command(server, player, "dg bosslabel legacy_setup_boss Old Arena") == 1
                && "Old Arena".equals(lastBoss().areaName));
        check.accept("legacy bossremove still removes the named encounter", command(server, player, "dg bossremove legacy_setup_boss") == 1
                && !containsBossId("legacy_setup_boss"));
        check.accept("legacy reset still queues a real verified restoration", command(server, player, "dg reset setup_legacy") == 1
                && manager.isEntryLocked(player.world, new BlockPos(624, 99, 600)));
        await(manager, "setup_legacy", "IDLE");
        check.accept("legacy remove unregisters metadata while preserving its baseline", command(server, player, "dg remove setup_legacy") == 1
                && !hasRegion(GuardRuntime.settings(), "setup_legacy") && !GuardRuntime.settings().dungeonLabels.containsKey("setup_legacy")
                && snapshot("setup_legacy").isFile());
    }

    private static void prepare(final WorldServer world) {
        GuardRuntime.withBypass(new Runnable() { @Override public void run() {
            for (int x = 577; x <= 583; x++) for (int z = 577; z <= 583; z++) for (int y = 99; y <= 103; y++)
                world.setBlockState(new BlockPos(x, y, z), (y == 99 ? Blocks.STONE : Blocks.AIR).getDefaultState(), 2);
            for (int x = 599; x <= 655; x++) for (int z = 599; z <= 607; z++) for (int y = 99; y <= 103; y++)
                world.setBlockState(new BlockPos(x, y, z), (y == 99 ? Blocks.STONE : Blocks.AIR).getDefaultState(), 2);
            Block waystone = Block.REGISTRY.getObject(new ResourceLocation("waystones", "waystone"));
            world.setBlockState(SPAWN, property(property(waystone.getDefaultState(), "base", true), "facing", EnumFacing.NORTH), 2);
            world.setBlockState(SPAWN.up(), property(property(waystone.getDefaultState(), "base", false), "facing", EnumFacing.NORTH), 2);
            world.setBlockState(SECOND, Blocks.CHEST.getDefaultState(), 2);
            ((TileEntityChest) world.getTileEntity(SECOND)).setInventorySlotContents(0, new ItemStack(Items.DIAMOND, 7));
        } });
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState property(IBlockState state, String name, Comparable value) {
        for (IProperty key : state.getPropertyKeys()) if (name.equals(key.getName())) return state.withProperty(key, value);
        throw new IllegalStateException("Missing fixture block property " + name);
    }
    private static int command(MinecraftServer server, ICommandSender sender, String value) { return server.getCommandManager().executeCommand(sender, value); }
    private static void click(EntityPlayerMP player, WorldServer world, BlockPos pos, EnumHand hand) {
        player.interactionManager.processRightClickBlock(player, world, player.getHeldItem(hand), hand, pos, EnumFacing.UP, .5F, 1F, .5F);
    }
    private static void select(MinecraftServer server, RecordingPlayer player, BlockPos first, BlockPos second) {
        if (command(server, player, "dg pos1 " + xyz(first)) != 1 || command(server, player, "dg pos2 " + xyz(second)) != 1)
            throw new IllegalStateException("Native setup could not select fixture corners");
    }
    private static String xyz(BlockPos pos) { return pos.getX() + " " + pos.getY() + " " + pos.getZ(); }
    private static String json(Object value) { return new Gson().toJson(value); }
    private static GuardConfig diskConfig() throws Exception { return new Gson().fromJson(new String(Files.readAllBytes(GuardRuntime.configFile().toPath()), StandardCharsets.UTF_8), GuardConfig.class); }
    private static File snapshot(String name) { return new File(new File(GuardRuntime.configFile().getParentFile(), "snapshots"), name + ".snapshot.nbt.gz"); }
    private static boolean hasRegion(GuardConfig config, String name) { for (GuardRegion region : config.regions) if (name.equals(region.name)) return true; return false; }
    private static Set<String> names(DungeonManager manager) { Set<String> names = new HashSet<String>(); for (GuardRegion region : manager.regions()) names.add(region.name); return names; }
    private static GuardRegion added(DungeonManager manager, Set<String> before) { for (GuardRegion region : manager.regions()) if (!before.contains(region.name)) return region; throw new IllegalStateException("Setup command created no arena"); }
    private static GuardConfig.BossZone lastBoss() { List<GuardConfig.BossZone> zones = GuardRuntime.settings().bossAreas; return zones.get(zones.size() - 1); }
    private static boolean containsBossParent(String parent) { for (GuardConfig.BossZone zone : GuardRuntime.settings().bossAreas) if (parent.equals(zone.dungeon)) return true; return false; }
    private static boolean containsBossId(String id) { for (GuardConfig.BossZone zone : GuardRuntime.settings().bossAreas) if (id.equals(zone.id)) return true; return false; }
    private static boolean sameBounds(GuardRegion a, GuardRegion b) { return a.dimension == b.dimension && a.minX == b.minX && a.maxX == b.maxX && a.minY == b.minY && a.maxY == b.maxY && a.minZ == b.minZ && a.maxZ == b.maxZ; }
    private static String repeat(char value, int count) { char[] text = new char[count]; java.util.Arrays.fill(text, value); return new String(text); }
    private static int occurrences(String value, String needle) { int count = 0, index = 0; while ((index = value.indexOf(needle, index)) >= 0) { count++; index += needle.length(); } return count; }
    private static int wandCount(EntityPlayerMP player) { int count = 0; for (ItemStack stack : player.inventory.mainInventory) if (GuardWand.isWand(stack)) count += stack.getCount(); return count; }
    private static ItemStack[] copyInventory(EntityPlayerMP player) { ItemStack[] result = new ItemStack[player.inventory.mainInventory.size()]; for (int i = 0; i < result.length; i++) result[i] = player.inventory.getStackInSlot(i).copy(); return result; }
    private static void restoreInventory(EntityPlayerMP player, ItemStack[] items) { for (int i = 0; i < items.length; i++) player.inventory.setInventorySlotContents(i, items[i].copy()); }
    private static boolean sameInventory(EntityPlayerMP player, ItemStack[] items) { for (int i = 0; i < items.length; i++) if (!ItemStack.areItemStacksEqual(items[i], player.inventory.getStackInSlot(i))) return false; return true; }
    private static boolean sameHotbar(EntityPlayerMP player, ItemStack[] items) { for (int i = 0; i < 9; i++) if (!ItemStack.areItemStacksEqual(items[i], player.inventory.getStackInSlot(i))) return false; return true; }
    private static String text(RecordingPlayer player) { StringBuilder result = new StringBuilder(); for (ITextComponent message : player.messages) result.append(message.getUnformattedText()).append('\n'); return result.toString(); }
    private static Set<String> clicks(RecordingPlayer player) {
        Set<String> result = new HashSet<String>();
        for (ITextComponent message : player.messages) for (ITextComponent component : message) {
            ClickEvent click = component.getStyle().getClickEvent();
            if (click != null && (click.getAction() == ClickEvent.Action.RUN_COMMAND || click.getAction() == ClickEvent.Action.SUGGEST_COMMAND)) result.add(click.getValue());
        }
        return result;
    }
    private static void await(DungeonManager manager, String name, String target) throws Exception {
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!target.equals(manager.state(name))) {
            if (System.nanoTime() >= deadline || ("FAILED".equals(manager.state(name)) && !"FAILED".equals(target)))
                throw new IllegalStateException("Setup fixture waiting for " + target + ": " + manager.statusLines());
            manager.tickResets(); Thread.sleep(1L);
        }
    }
    private static final class RecordingPlayer extends EntityPlayerMP {
        final List<ITextComponent> messages = new ArrayList<ITextComponent>();
        RecordingPlayer(MinecraftServer server, WorldServer world, GameProfile profile) { super(server, world, profile, new PlayerInteractionManager(world)); }
        @Override public void sendMessage(ITextComponent message) { if (messages != null) messages.add(message); }
    }
}
