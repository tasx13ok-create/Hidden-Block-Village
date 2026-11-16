package village.dungeonguard;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBed;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBlockSpecial;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemRedstone;
import net.minecraft.item.ItemLilyPad;
import net.minecraft.item.ItemSeedFood;
import net.minecraft.item.ItemSeeds;
import net.minecraft.item.ItemSnow;
import net.minecraft.item.ItemSign;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * An operator may edit only the cells selected by a current, synchronous build
 * action. Being near an operator, firing their projectile, or running later on
 * the same thread never grants permission. Every scope is closed in finally by
 * the core transformer (or the command helper below).
 */
public final class OperatorAccess {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<Scope>();
    private static final net.minecraft.util.ResourceLocation WAYSTONE = new net.minecraft.util.ResourceLocation("waystones", "waystone");
    private OperatorAccess() { }

    /** Creative mode and mod-owned FakePlayers do not confer operator access. */
    public static boolean isOperator(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || player instanceof FakePlayer
                || player.world == null || player.world.isRemote) return false;
        MinecraftServer server = player.getServer();
        return server != null && server.getPlayerList() != null
                && server.getPlayerList().getPlayerByUUID(player.getUniqueID()) == player
                && server.getPlayerList().canSendCommands(player.getGameProfile());
    }

    public static boolean allowed(World world, BlockPos pos) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.closed || scope.world != world || pos == null
                || world == null || world.isRemote || GuardRuntime.isRestoring()
                || !scope.authorized() || !scope.bounds.contains(pos)) return false;
        DungeonManager manager = GuardRuntime.manager();
        return manager == null || !manager.isEntryLocked(world, pos);
    }

    static void clear() { CURRENT.remove(); }

    /** Opaque capability: creation is restricted to checked build-action entry points. */
    public static final class Scope implements AutoCloseable {
        private final Scope previous;
        private final Thread thread;
        private final World world;
        private final MinecraftServer server;
        private final EntityPlayer player;
        private final Bounds bounds;
        private final boolean console;
        private boolean closed;

        private Scope(World world, EntityPlayer player, MinecraftServer console, Bounds bounds) {
            this.previous = CURRENT.get(); this.thread = Thread.currentThread();
            this.world = world; this.player = player; this.console = console != null;
            this.server = console != null ? console : player == null ? null : player.getServer();
            this.bounds = bounds == null ? EMPTY : bounds;
            CURRENT.set(this); // An unprivileged nested action shadows the outer action.
        }

        private boolean authorized() {
            return Thread.currentThread() == thread && server != null && server.isCallingFromMinecraftThread()
                    && world != null && world.getMinecraftServer() == server
                    && (console || (player.world == world && isOperator(player)));
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (Thread.currentThread() != thread) return;
            if (CURRENT.get() != this) { CURRENT.remove(); return; }
            if (previous == null || previous.closed) CURRENT.remove(); else CURRENT.set(previous);
        }
    }

    public static Scope beginHarvest(PlayerInteractionManager manager, BlockPos pos) {
        if (!valid(manager, manager == null ? null : manager.player, manager == null ? null : manager.world)
                || pos == null) return denied();
        Set<BlockPos> cells = new HashSet<BlockPos>();
        add(cells, pos);
        companion(cells, manager.world, pos);
        return new Scope(manager.world, manager.player, null, new Cells(cells));
    }

    public static Scope beginUseOn(PlayerInteractionManager manager, EntityPlayer player, World world,
            ItemStack stack, EnumHand hand, BlockPos clicked, EnumFacing face) {
        if (!valid(manager, player, world) || clicked == null || face == null || stack == null) return denied();
        Item item = stack.isEmpty() ? null : stack.getItem();
        // Ability items receive no edit capability. Ordinary mod building blocks
        // still work through the standard ItemBlock/ItemDoor/etc. contracts.
        if (item != null && !buildingItem(item)) return denied();
        Set<BlockPos> cells = new HashSet<BlockPos>();
        add(cells, clicked);
        companion(cells, world, clicked);
        if (item != null) {
            BlockPos target = world.getBlockState(clicked).getBlock().isReplaceable(world, clicked)
                    ? clicked : clicked.offset(face);
            add(cells, target);
            // Flint and steel always uses the adjacent face, even on replaceable blocks.
            if (item instanceof ItemFlintAndSteel || item instanceof ItemFireball) add(cells, clicked.offset(face));
            if (item instanceof ItemDoor) add(cells, target.up());
            if (item instanceof ItemBlock) {
                net.minecraft.block.Block placed = ((ItemBlock) item).getBlock();
                if (placed instanceof BlockDoublePlant
                        || WAYSTONE.equals(placed.getRegistryName()))
                    add(cells, target.up());
            }
            if (item instanceof ItemBed) add(cells, target.offset(player.getHorizontalFacing()));
        }
        return new Scope(world, player, null, new Cells(cells));
    }

    public static Scope beginUse(PlayerInteractionManager manager, EntityPlayer player, World world,
            ItemStack stack, EnumHand hand) {
        if (!valid(manager, player, world) || stack == null || stack.isEmpty()) return denied();
        boolean lily = stack.getItem() instanceof ItemLilyPad;
        if (!(stack.getItem() instanceof ItemBucket) && !lily) return denied();
        Vec3d eyes = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        double reach = manager.getBlockReachDistance();
        boolean collectLiquid = lily || stack.getItem() == net.minecraft.init.Items.BUCKET;
        RayTraceResult hit = world.rayTraceBlocks(eyes, eyes.add(look.scale(reach)), collectLiquid, !collectLiquid, false);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || hit.sideHit == null) return denied();
        Set<BlockPos> cells = new HashSet<BlockPos>();
        if (lily) add(cells, hit.getBlockPos().up());
        else { add(cells, hit.getBlockPos()); add(cells, hit.getBlockPos().offset(hit.sideHit)); }
        return new Scope(world, player, null, new Cells(cells));
    }

    private static boolean valid(PlayerInteractionManager manager, EntityPlayer player, World world) {
        return manager != null && manager.player == player && manager.world == world
                && player instanceof EntityPlayerMP && ((EntityPlayerMP) player).interactionManager == manager
                && isOperator(player);
    }

    private static boolean buildingItem(Item item) {
        return item instanceof ItemBlock || item instanceof ItemBlockSpecial || item instanceof ItemDoor
                || item instanceof ItemBed || item instanceof ItemFlintAndSteel || item instanceof ItemHoe
                || item instanceof ItemSpade || item instanceof ItemRedstone
                || item instanceof ItemSign || item instanceof ItemSkull || item instanceof ItemFireball
                || item instanceof ItemSeeds || item instanceof ItemSeedFood || item instanceof ItemSnow;
    }

    private static void companion(Set<BlockPos> cells, World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof BlockDoor)
            add(cells, state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.LOWER ? pos.up() : pos.down());
        else if (state.getBlock() instanceof BlockBed) {
            EnumFacing facing = state.getValue(BlockBed.FACING);
            add(cells, pos.offset(state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT ? facing : facing.getOpposite()));
        } else if (state.getBlock() instanceof BlockDoublePlant)
            add(cells, state.getValue(BlockDoublePlant.HALF) == BlockDoublePlant.EnumBlockHalf.LOWER ? pos.up() : pos.down());
        else if (WAYSTONE.equals(state.getBlock().getRegistryName())) {
            for (IProperty<?> property : state.getPropertyKeys()) {
                if (!"base".equals(property.getName())) continue;
                Object base = state.getValue(property);
                if (base instanceof Boolean) {
                    BlockPos other = (Boolean) base ? pos.up() : pos.down();
                    if (WAYSTONE.equals(world.getBlockState(other).getBlock().getRegistryName())) add(cells, other);
                }
                break;
            }
        }
    }

    private static void add(Set<BlockPos> cells, BlockPos pos) { cells.add(pos.toImmutable()); }
    private static Scope denied() { return new Scope(null, null, null, EMPTY); }

    /** Replaces only ICommand.execute inside the real CommandHandler. */
    public static void runCommand(ICommand command, MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        Scope scope = commandScope(command, server, sender, args);
        try { command.execute(server, sender, args); }
        finally { scope.close(); }
    }

    private static Scope commandScope(ICommand command, MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (!GuardRuntime.ready() || !GuardRuntime.policy().enabled || command == null) return denied();
        // Exact classes prevent a mod command called "fill" from inheriting access.
        String type = command.getClass().getName();
        boolean set = type.equals("net.minecraft.command.server.CommandSetBlock");
        boolean fill = type.equals("net.minecraft.command.CommandFill");
        boolean clone = type.equals("net.minecraft.command.CommandClone");
        if (!set && !fill && !clone) return denied();
        EntityPlayer player = sender instanceof EntityPlayer ? (EntityPlayer) sender : null;
        if (server == null || sender == null || command == null || args == null
                || !(sender == server || isOperator(player)) || !command.checkPermission(server, sender))
            throw new CommandException("Dungeon Guard: protected world edits require a connected operator or the server console.");
        World world = sender.getEntityWorld();
        if (world == null || world.isRemote || world.getMinecraftServer() != server)
            throw new CommandException("Dungeon Guard: this edit has no verified server world.");
        Bounds bounds;
        try {
            if (set && args.length >= 4) {
                BlockPos pos = CommandBase.parseBlockPos(sender, args, 0, false);
                bounds = new Cells(Collections.singleton(pos));
            } else if (fill && args.length >= 7) {
                bounds = new Box(CommandBase.parseBlockPos(sender, args, 0, false),
                        CommandBase.parseBlockPos(sender, args, 3, false));
            } else if (clone && args.length >= 9) {
                BlockPos a = CommandBase.parseBlockPos(sender, args, 0, false);
                BlockPos b = CommandBase.parseBlockPos(sender, args, 3, false);
                BlockPos destination = CommandBase.parseBlockPos(sender, args, 6, false);
                Box source = new Box(a, b);
                Box target = new Box(destination, destination.add(source.maxX - source.minX,
                        source.maxY - source.minY, source.maxZ - source.minZ));
                bounds = args.length > 10 && "move".equals(args[10]) ? new Union(source, target) : target;
            } else return denied();
        } catch (CommandException e) {
            return denied(); // The original command produces its own normal syntax error.
        }
        // Vanilla edits clear inventories before calling World.setBlockState.
        // Reject the whole operation before those side effects, not only writes.
        if (GuardRuntime.isRestoring())
            throw new CommandException("Dungeon Guard: commands cannot edit terrain during restoration.");
        DungeonManager manager = GuardRuntime.manager();
        if (manager != null) for (GuardRegion region : manager.regions()) {
            if (region.dimension == world.provider.getDimension() && bounds.intersects(region)
                    && manager.isEntryLocked(world, new BlockPos(region.minX, region.minY, region.minZ)))
                throw new CommandException("Dungeon Guard: " + region.name + " is locked for capture, reset or recovery; this edit was not applied.");
        }
        return new Scope(world, player, sender == server ? server : null, bounds);
    }

    private interface Bounds {
        boolean contains(BlockPos pos);
        boolean intersects(GuardRegion region);
    }
    private static final Bounds EMPTY = new Bounds() {
        @Override public boolean contains(BlockPos pos) { return false; }
        @Override public boolean intersects(GuardRegion region) { return false; }
    };
    private static final class Cells implements Bounds {
        private final Set<BlockPos> cells;
        Cells(Set<BlockPos> cells) { this.cells = Collections.unmodifiableSet(new HashSet<BlockPos>(cells)); }
        @Override public boolean contains(BlockPos pos) { return cells.contains(pos); }
        @Override public boolean intersects(GuardRegion region) {
            for (BlockPos pos : cells) if (region.contains(pos)) return true;
            return false;
        }
    }
    private static final class Box implements Bounds {
        final int minX, minY, minZ, maxX, maxY, maxZ;
        Box(BlockPos a, BlockPos b) {
            minX = Math.min(a.getX(), b.getX()); minY = Math.min(a.getY(), b.getY()); minZ = Math.min(a.getZ(), b.getZ());
            maxX = Math.max(a.getX(), b.getX()); maxY = Math.max(a.getY(), b.getY()); maxZ = Math.max(a.getZ(), b.getZ());
        }
        @Override public boolean contains(BlockPos p) {
            return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY
                    && p.getZ() >= minZ && p.getZ() <= maxZ;
        }
        @Override public boolean intersects(GuardRegion region) {
            return minX <= region.maxX && maxX >= region.minX && minY <= region.maxY && maxY >= region.minY
                    && minZ <= region.maxZ && maxZ >= region.minZ;
        }
    }
    private static final class Union implements Bounds {
        private final Bounds first, second;
        Union(Bounds first, Bounds second) { this.first = first; this.second = second; }
        @Override public boolean contains(BlockPos p) { return first.contains(p) || second.contains(p); }
        @Override public boolean intersects(GuardRegion region) { return first.intersects(region) || second.intersects(region); }
    }
}
