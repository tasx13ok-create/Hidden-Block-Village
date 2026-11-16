package village.dungeonguard;

import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.SPacketSpawnPosition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Binds the ordinary Overworld spawn to a real, existing Waystones 1.12 block.
 * No Waystones classes are linked at compile time and no blocks are generated.
 */
public final class SpawnBinding {
    private static final Logger LOG = LogManager.getLogger("DungeonGuard/Spawn");
    private static final String WAYSTONE_ID = "waystones:waystone";
    private static final String TILE_CLASS = "net.blay09.mods.waystones.block.TileWaystone";

    private final MinecraftServer server;
    private String attemptedLoadKey;
    private String lastSyncFailure;

    public SpawnBinding(MinecraftServer server) {
        this.server = server;
    }

    public boolean isBound() {
        GuardConfig settings = GuardRuntime.settings();
        return settings != null && settings.spawn != null && settings.spawn.bound;
    }

    /** Target may be either half of the two-block Waystone. */
    public void bind(EntityPlayerMP player, BlockPos target) throws CommandException {
        if (player.dimension != 0) {
            throw problem("World spawn must be bound to a Waystone in the Overworld (dimension 0).");
        }
        WorldServer world = server.getWorld(0);
        if (world == null) {
            throw problem("The Overworld is not available; spawn was not changed.");
        }
        BlockPos base = resolveBase(world, target);
        if (inArena(world, base) || inArena(world, base.up())) {
            throw problem("The spawn Waystone must be outside every dungeon arena.");
        }
        EnumFacing facing = facingOf(world.getBlockState(base));
        BlockPos landing = chooseLanding(world, base, facing);
        if (landing == null) {
            throw problem("No safe landing beside this Waystone. Clear two blocks of air above a solid, non-hazardous floor outside all arenas, then bind again.");
        }

        GuardConfig settings = GuardRuntime.settings();
        if (settings == null) {
            throw problem("Dungeon Guard settings are not available; spawn was not changed.");
        }
        if (settings.spawn == null) {
            settings.spawn = new GuardConfig.Spawn();
        }
        GuardConfig.Spawn spawn = settings.spawn;
        spawn.bound = true;
        spawn.dimension = 0;
        spawn.x = base.getX();
        spawn.y = base.getY();
        spawn.z = base.getZ();
        spawn.blockId = WAYSTONE_ID;
        spawn.landingX = landing.getX() + 0.5D;
        spawn.landingY = landing.getY();
        spawn.landingZ = landing.getZ() + 0.5D;
        spawn.yaw = facing.getHorizontalAngle();

        // Persist the binding before changing the world's spawn metadata.
        try {
            GuardRuntime.saveAndInstall(settings);
        } catch (Exception exception) {
            throw problem("Could not save the Waystone spawn binding; world spawn was not changed: " + concise(exception));
        }
        attemptedLoadKey = key(spawn);
        lastSyncFailure = null;
        try {
            applyWorldSpawn(world, landing);
        } catch (RuntimeException exception) {
            throw problem("The binding was saved, but world spawn could not be applied. Check the server log and run the binding command again: " + concise(exception));
        }
        LOG.info("Bound world spawn to Waystone at {}, landing at {}", base, landing);
    }

    /**
     * Called at startup and periodically. Existing chunks are loaded at most
     * once per binding identity; later checks never repeatedly load chunks.
     */
    public void sync() {
        GuardConfig settings = GuardRuntime.settings();
        if (settings == null || settings.spawn == null || !settings.spawn.bound) {
            lastSyncFailure = null;
            return;
        }
        GuardConfig.Spawn spawn = settings.spawn;
        try {
            WorldServer world = boundWorld(spawn);
            String loadKey = key(spawn);
            if (!loadKey.equals(attemptedLoadKey)) {
                attemptedLoadKey = loadKey;
                loadExistingChunks(world, spawn);
            }
            BlockPos landing = validateBinding(world, spawn);
            applyWorldSpawn(world, landing);
            if (lastSyncFailure != null) {
                LOG.info("The bound Waystone and its safe spawn landing are available again.");
                lastSyncFailure = null;
            }
        } catch (Exception exception) {
            String failure = concise(exception);
            if (!failure.equals(lastSyncFailure)) {
                LOG.warn("Waystone spawn binding is unavailable: {} No new spawn was chosen; repair the Waystone/landing or rebind.", failure);
                lastSyncFailure = failure;
            }
        }
    }

    /** Teleports without creating a Nether portal or altering any blocks. */
    public void teleportToSpawn(EntityPlayerMP player) throws CommandException {
        if (player == null || player.connection == null) {
            throw problem("A connected player is required to teleport to spawn.");
        }
        GuardConfig settings = GuardRuntime.settings();
        if (settings == null || settings.spawn == null || !settings.spawn.bound) {
            throw problem("No spawn Waystone is bound yet.");
        }
        GuardConfig.Spawn spawn = settings.spawn;
        WorldServer world = boundWorld(spawn);
        loadExistingChunks(world, spawn);
        BlockPos landing = validateBinding(world, spawn);
        final double x = landing.getX() + 0.5D;
        final double y = landing.getY();
        final double z = landing.getZ() + 0.5D;
        final float yaw = spawn.yaw;

        player.dismountRidingEntity();
        player.removePassengers();
        if (player.dimension != 0) {
            server.getPlayerList().transferPlayerToDimension(player, 0,
                    new SpawnTeleporter(world, x, y, z, yaw));
        }
        if (player.dimension != 0 || player.getServerWorld() != world) {
            throw problem("Another server component prevented the dimension transfer; no spawn coordinates were applied.");
        }
        player.connection.setPlayerLocation(x, y, z, yaw, 0F);
        player.fallDistance = 0F;
        player.motionX = 0D;
        player.motionY = 0D;
        player.motionZ = 0D;
        player.extinguish();
    }

    public String describe() {
        GuardConfig settings = GuardRuntime.settings();
        if (settings == null || settings.spawn == null || !settings.spawn.bound) {
            return "No spawn Waystone is bound.";
        }
        GuardConfig.Spawn spawn = settings.spawn;
        return "Spawn Waystone: dimension " + spawn.dimension + " at " + spawn.x + " "
                + spawn.y + " " + spawn.z + "; landing " + spawn.landingX + " "
                + spawn.landingY + " " + spawn.landingZ
                + (lastSyncFailure == null ? "." : "; unavailable: " + lastSyncFailure);
    }

    private WorldServer boundWorld(GuardConfig.Spawn spawn) throws CommandException {
        if (spawn.dimension != 0 || !WAYSTONE_ID.equals(spawn.blockId)) {
            throw problem("Invalid saved Waystone spawn binding; rebind an Overworld waystones:waystone.");
        }
        WorldServer world = server.getWorld(0);
        if (world == null) {
            throw problem("The Overworld is not available.");
        }
        return world;
    }

    private BlockPos validateBinding(WorldServer world, GuardConfig.Spawn spawn) throws CommandException {
        BlockPos recordedBase = new BlockPos(spawn.x, spawn.y, spawn.z);
        BlockPos actualBase = resolveBase(world, recordedBase);
        if (!recordedBase.equals(actualBase)) {
            throw problem("The saved Waystone base no longer matches the actual lower half.");
        }
        if (inArena(world, actualBase) || inArena(world, actualBase.up())) {
            throw problem("The bound Waystone is now inside a dungeon arena.");
        }
        if (!finite(spawn.landingX) || !finite(spawn.landingY) || !finite(spawn.landingZ)
                || Float.isNaN(spawn.yaw) || Float.isInfinite(spawn.yaw)) {
            throw problem("The saved spawn landing contains invalid coordinates.");
        }
        BlockPos landing = new BlockPos(spawn.landingX, spawn.landingY, spawn.landingZ);
        long dx = (long) landing.getX() - actualBase.getX();
        long dz = (long) landing.getZ() - actualBase.getZ();
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1 || (dx == 0 && dz == 0)
                || landing.getY() != actualBase.getY()
                || spawn.landingX != landing.getX() + 0.5D
                || spawn.landingY != landing.getY()
                || spawn.landingZ != landing.getZ() + 0.5D) {
            throw problem("The saved spawn landing is not immediately beside the Waystone; rebind it.");
        }
        if (!safeLanding(world, landing)) {
            throw problem("The bound landing is unloaded, obstructed, hazardous, or inside a dungeon arena.");
        }
        return landing;
    }

    private static BlockPos resolveBase(WorldServer world, BlockPos target) throws CommandException {
        if (target == null || target.getY() < 0 || target.getY() > 255 || !world.isBlockLoaded(target)) {
            throw problem("The targeted Waystone chunk is not loaded.");
        }
        IBlockState targetState = world.getBlockState(target);
        if (!isWaystone(targetState)) {
            throw problem("The target is not a waystones:waystone block; point at the actual spawn Waystone.");
        }
        Boolean targetBase = baseValue(targetState);
        BlockPos base;
        if (targetBase != null) {
            base = targetBase ? target : target.down();
        } else {
            base = reflectedParent(world, target);
            if (base == null) {
                throw problem("This Waystone has an unsupported block layout; its lower half could not be verified.");
            }
        }
        if (base.getY() < 0 || base.getY() >= 255 || !world.isBlockLoaded(base)
                || !world.isBlockLoaded(base.up())) {
            throw problem("The Waystone's two-block structure is not available.");
        }
        IBlockState bottom = world.getBlockState(base);
        IBlockState top = world.getBlockState(base.up());
        if (!isWaystone(bottom) || !isWaystone(top)
                || Boolean.FALSE.equals(baseValue(bottom)) || Boolean.TRUE.equals(baseValue(top))) {
            throw problem("The Waystone is missing a valid lower or upper half; repair it before binding.");
        }
        return base.toImmutable();
    }

    private static BlockPos reflectedParent(WorldServer world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null || !TILE_CLASS.equals(tile.getClass().getName())) {
            return null;
        }
        try {
            Method method = tile.getClass().getMethod("getParent");
            Object parent = method.invoke(tile);
            if (parent instanceof TileEntity) {
                TileEntity parentTile = (TileEntity) parent;
                if (parentTile.getWorld() == world) {
                    BlockPos parentPos = parentTile.getPos();
                    if (parentPos.equals(pos) || parentPos.equals(pos.down())) {
                        return parentPos.toImmutable();
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // A fork with a different API must not silently bind an arbitrary block.
        }
        return null;
    }

    private static Boolean baseValue(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if ("base".equals(property.getName())) {
                Object value = state.getValue(property);
                return value instanceof Boolean ? (Boolean) value : null;
            }
        }
        return null;
    }

    private static EnumFacing facingOf(IBlockState state) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if ("facing".equals(property.getName())) {
                Object value = state.getValue(property);
                if (value instanceof EnumFacing && ((EnumFacing) value).getAxis().isHorizontal()) {
                    return (EnumFacing) value;
                }
            }
        }
        return EnumFacing.NORTH;
    }

    private static boolean isWaystone(IBlockState state) {
        ResourceLocation registryName = state.getBlock().getRegistryName();
        return registryName != null && WAYSTONE_ID.equals(registryName.toString());
    }

    private BlockPos chooseLanding(WorldServer world, BlockPos base, EnumFacing facing) {
        Set<BlockPos> candidates = new LinkedHashSet<BlockPos>();
        candidates.add(base.offset(facing));
        candidates.add(base.offset(facing.rotateY()));
        candidates.add(base.offset(facing.rotateYCCW()));
        candidates.add(base.offset(facing.getOpposite()));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 && dz != 0) {
                    candidates.add(base.add(dx, 0, dz));
                }
            }
        }
        for (BlockPos candidate : candidates) {
            if (safeLanding(world, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean safeLanding(WorldServer world, BlockPos feet) {
        if (feet.getY() < 1 || feet.getY() > 254 || !world.isBlockLoaded(feet)
                || !world.isBlockLoaded(feet.up()) || !world.isBlockLoaded(feet.down())
                || !world.getWorldBorder().contains(feet)
                || inArena(world, feet) || inArena(world, feet.up()) || inArena(world, feet.down())) {
            return false;
        }
        if (!world.isAirBlock(feet) || !world.isAirBlock(feet.up())) {
            return false;
        }
        // WorldBorder.contains(BlockPos) tests overlap, not full containment.
        // Keep the entire normal player body inside the border and clear of
        // collision shapes that may protrude from neighbouring blocks.
        AxisAlignedBB body = new AxisAlignedBB(feet.getX() + 0.2D, feet.getY(), feet.getZ() + 0.2D,
                feet.getX() + 0.8D, feet.getY() + 1.8D, feet.getZ() + 0.8D);
        if (body.minX < world.getWorldBorder().minX() || body.maxX > world.getWorldBorder().maxX()
                || body.minZ < world.getWorldBorder().minZ() || body.maxZ > world.getWorldBorder().maxZ()
                || !world.getCollisionBoxes(null, body).isEmpty()) {
            return false;
        }
        IBlockState floor = world.getBlockState(feet.down());
        return floor.isSideSolid(world, feet.down(), EnumFacing.UP)
                && !floor.getMaterial().isLiquid()
                && floor.getBlock() != Blocks.MAGMA
                && floor.getBlock() != Blocks.CACTUS;
    }

    private boolean inArena(WorldServer world, BlockPos pos) {
        return GuardRuntime.manager() != null && GuardRuntime.manager().isArena(world, pos);
    }

    private static void loadExistingChunks(WorldServer world, GuardConfig.Spawn spawn) {
        BlockPos base = new BlockPos(spawn.x, spawn.y, spawn.z);
        if (!world.isBlockLoaded(base)) {
            world.getChunkProvider().loadChunk(base.getX() >> 4, base.getZ() >> 4);
        }
        if (finite(spawn.landingX) && finite(spawn.landingY) && finite(spawn.landingZ)) {
            BlockPos landing = new BlockPos(spawn.landingX, spawn.landingY, spawn.landingZ);
            if (!world.isBlockLoaded(landing)) {
                world.getChunkProvider().loadChunk(landing.getX() >> 4, landing.getZ() >> 4);
            }
        }
    }

    private static void applyWorldSpawn(final WorldServer world, final BlockPos landing) {
        GuardRuntime.withBypass(new Runnable() {
            @Override
            public void run() {
                if (!world.getSpawnPoint().equals(landing)) {
                    world.setSpawnPoint(landing);
                    if (!world.getSpawnPoint().equals(landing)) {
                        throw new IllegalStateException("Another server component prevented the world-spawn change.");
                    }
                    MinecraftServer server = world.getMinecraftServer();
                    if (server != null) {
                        server.getPlayerList().sendPacketToAllPlayers(new SPacketSpawnPosition(landing));
                    }
                }
                if (world.getGameRules().getInt("spawnRadius") != 0) {
                    world.getGameRules().setOrCreateGameRule("spawnRadius", "0");
                    if (world.getGameRules().getInt("spawnRadius") != 0) {
                        throw new IllegalStateException("Another server component prevented spawnRadius from being set to zero.");
                    }
                }
            }
        });
    }

    private static String key(GuardConfig.Spawn spawn) {
        return spawn.dimension + ":" + spawn.x + ":" + spawn.y + ":" + spawn.z
                + ":" + spawn.landingX + ":" + spawn.landingY + ":" + spawn.landingZ;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static CommandException problem(String message) {
        return new CommandException(message);
    }

    private static String concise(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static final class SpawnTeleporter extends Teleporter {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;

        SpawnTeleporter(WorldServer world, double x, double y, double z, float yaw) {
            super(world);
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }

        @Override
        public void placeInPortal(Entity entity, float rotationYaw) {
            entity.setLocationAndAngles(x, y, z, yaw, 0F);
        }

        @Override
        public boolean placeInExistingPortal(Entity entity, float rotationYaw) {
            placeInPortal(entity, rotationYaw);
            return true;
        }

        @Override
        public boolean makePortal(Entity entity) {
            return false;
        }

        @Override
        public void removeStalePortalLocations(long worldTime) {
            // No portal cache exists for a direct spawn transfer.
        }
    }
}
