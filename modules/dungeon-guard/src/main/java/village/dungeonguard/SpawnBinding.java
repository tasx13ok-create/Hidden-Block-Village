package village.dungeonguard;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketSpawnPosition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import village.dungeonguard.AreaNetwork;
import village.dungeonguard.AreaResolver;
import village.dungeonguard.GuardConfig;
import village.dungeonguard.GuardRuntime;

public final class SpawnBinding {
    private static final Logger LOG = LogManager.getLogger((String)"DungeonGuard/Spawn");
    private static final String WAYSTONE_ID = "waystones:waystone";
    private static final String TILE_CLASS = "net.blay09.mods.waystones.block.TileWaystone";
    private static final String PENDING_HUB_SPAWN = "DungeonGuardHubSpawnPending";
    private final MinecraftServer server;
    private final Map<EntityPlayerMP, Boolean> firstLogins = new WeakHashMap<EntityPlayerMP, Boolean>();
    private String attemptedLoadKey;
    private String lastSyncFailure;
    private boolean spawnPacketDirty;

    public SpawnBinding(MinecraftServer server) {
        this.server = server;
    }

    public boolean isBound() {
        GuardConfig settings = GuardRuntime.policy();
        return settings != null && settings.spawn != null && settings.spawn.bound;
    }

    boolean owns(EntityPlayer player) {
        return player.getServer() == this.server && this.server.isCallingFromMinecraftThread();
    }

    boolean owns(WorldInfo info) {
        WorldServer world = this.server.getWorld(0);
        return this.server.isCallingFromMinecraftThread() && world != null && world.getWorldInfo() == info;
    }

    void correctSpawnPacket() {
        this.spawnPacketDirty = true;
    }

    BlockPos recordedLanding() {
        GuardConfig.Spawn spawn = GuardRuntime.policy().spawn;
        if (!(spawn != null && spawn.bound && spawn.dimension == 0 && WAYSTONE_ID.equals(spawn.blockId) && SpawnBinding.finite(spawn.landingX) && SpawnBinding.finite(spawn.landingY) && SpawnBinding.finite(spawn.landingZ))) {
            throw new IllegalStateException("Invalid trading-hub spawn binding");
        }
        return new BlockPos(spawn.landingX, spawn.landingY, spawn.landingZ);
    }

    void pinPersonalSpawn(final EntityPlayer player) {
        final BlockPos landing = this.recordedLanding();
        GuardRuntime.withBypass(new Runnable(){

            @Override
            public void run() {
                player.setSpawnChunk(landing, true, 0);
                player.setSpawnDimension(Integer.valueOf(0));
            }
        });
    }

    void playerDataLoaded(EntityPlayerMP player, boolean firstLogin) {
        this.firstLogins.put(player, firstLogin);
        this.pinPersonalSpawn((EntityPlayer)player);
    }

    void loginFinished(EntityPlayerMP player) {
        boolean firstLogin = Boolean.TRUE.equals(this.firstLogins.remove(player));
        this.pinPersonalSpawn((EntityPlayer)player);
        if (firstLogin || SpawnBinding.pendingHubSpawn((EntityPlayer)player)) {
            this.finishAtHub(player, null);
        }
    }

    void respawnFinished(EntityPlayerMP player, EntityPlayerMP previous) {
        this.pinPersonalSpawn((EntityPlayer)player);
        this.finishAtHub(player, previous);
    }

    private void finishAtHub(EntityPlayerMP player, EntityPlayerMP previous) {
        SpawnBinding.setPendingHubSpawn((EntityPlayer)player, true);
        try {
            if (player.connection == null || this.server.getPlayerList().getPlayerByUUID(player.getUniqueID()) != player) {
                throw SpawnBinding.problem("The joining or respawning player is not fully connected.");
            }
            if (previous != null) {
                if (previous.connection != player.connection || !previous.getUniqueID().equals(player.getUniqueID()) || player.connection.player == null || !player.connection.player.getUniqueID().equals(player.getUniqueID())) {
                    throw SpawnBinding.problem("The respawn connection no longer belongs to this player.");
                }
                player.connection.player = player;
            } else if (player.connection.player != player) {
                throw SpawnBinding.problem("The login connection no longer belongs to this player.");
            }
            this.teleportToSpawn(player);
            SpawnBinding.setPendingHubSpawn((EntityPlayer)player, false);
        }
        catch (Exception failure) {
            LOG.error("Could not complete the mandatory trading-hub spawn for {}: {}", (Object)player.getUniqueID(), (Object)SpawnBinding.concise(failure));
            if (player.connection != null && player.connection.player != null && player.connection.player.getUniqueID().equals(player.getUniqueID())) {
                player.connection.disconnect((ITextComponent)new TextComponentString("The trading-hub spawn is unavailable. Ask an operator to repair its Waystone and safe landing."));
            }
            return;
        }
        try {
            if (GuardRuntime.manager() != null) {
                GuardRuntime.manager().refreshPresence();
            }
        }
        catch (RuntimeException failure) {
            LOG.warn("Could not refresh dungeon occupants after hub spawn: {}", (Object)SpawnBinding.concise(failure));
        }
        try {
            AreaNetwork.send(player, AreaResolver.at(GuardRuntime.policy(), player.dimension, player.getPosition()), true);
        }
        catch (RuntimeException failure) {
            LOG.warn("Could not refresh dungeon header after hub spawn: {}", (Object)SpawnBinding.concise(failure));
        }
    }

    private static boolean pendingHubSpawn(EntityPlayer player) {
        return player.getEntityData().getCompoundTag("PlayerPersisted").getBoolean(PENDING_HUB_SPAWN);
    }

    private static void setPendingHubSpawn(EntityPlayer player, boolean pending) {
        NBTTagCompound persisted = player.getEntityData().getCompoundTag("PlayerPersisted");
        if (pending) {
            persisted.setBoolean(PENDING_HUB_SPAWN, true);
        } else {
            persisted.removeTag(PENDING_HUB_SPAWN);
        }
        player.getEntityData().setTag("PlayerPersisted", (NBTBase)persisted);
    }

    public void bind(EntityPlayerMP player, BlockPos target) throws CommandException {
        if (player.dimension != 0) {
            throw SpawnBinding.problem("World spawn must be bound to a Waystone in the Overworld (dimension 0).");
        }
        WorldServer world = this.server.getWorld(0);
        if (world == null) {
            throw SpawnBinding.problem("The Overworld is not available; spawn was not changed.");
        }
        BlockPos base = SpawnBinding.resolveBase(world, target);
        if (this.inArena(world, base) || this.inArena(world, base.up())) {
            throw SpawnBinding.problem("The spawn Waystone must be outside every dungeon arena.");
        }
        EnumFacing facing = SpawnBinding.facingOf(world.getBlockState(base));
        BlockPos landing = this.chooseLanding(world, base, facing);
        if (landing == null) {
            throw SpawnBinding.problem("No safe landing beside this Waystone. Clear two blocks of air above a solid, non-hazardous floor outside all arenas, then bind again.");
        }
        GuardConfig settings = GuardRuntime.settings();
        if (settings == null) {
            throw SpawnBinding.problem("Dungeon Guard settings are not available; spawn was not changed.");
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
        spawn.landingX = (double)landing.getX() + 0.5;
        spawn.landingY = landing.getY();
        spawn.landingZ = (double)landing.getZ() + 0.5;
        spawn.yaw = facing.getHorizontalAngle();
        try {
            GuardRuntime.saveAndInstall(settings);
        }
        catch (Exception exception) {
            throw SpawnBinding.problem("Could not save the Waystone spawn binding; world spawn was not changed: " + SpawnBinding.concise(exception));
        }
        this.attemptedLoadKey = SpawnBinding.key(spawn);
        this.lastSyncFailure = null;
        try {
            this.applyWorldSpawn(world, landing);
        }
        catch (RuntimeException exception) {
            throw SpawnBinding.problem("The binding was saved, but world spawn could not be applied. Check the server log and run the binding command again: " + SpawnBinding.concise(exception));
        }
        LOG.info("Bound world spawn to Waystone at {}, landing at {}", (Object)base, (Object)landing);
    }

    public void sync() {
        block5: {
            GuardConfig settings = GuardRuntime.settings();
            if (settings == null || settings.spawn == null || !settings.spawn.bound) {
                this.lastSyncFailure = null;
                return;
            }
            GuardConfig.Spawn spawn = settings.spawn;
            try {
                WorldServer world = this.boundWorld(spawn);
                String loadKey = SpawnBinding.key(spawn);
                if (!loadKey.equals(this.attemptedLoadKey)) {
                    this.attemptedLoadKey = loadKey;
                    SpawnBinding.loadExistingChunks(world, spawn);
                }
                BlockPos landing = this.validateBinding(world, spawn);
                this.applyWorldSpawn(world, landing);
                if (this.lastSyncFailure != null) {
                    LOG.info("The bound Waystone and its safe spawn landing are available again.");
                    this.lastSyncFailure = null;
                }
            }
            catch (Exception exception) {
                String failure = SpawnBinding.concise(exception);
                if (failure.equals(this.lastSyncFailure)) break block5;
                LOG.warn("Waystone spawn binding is unavailable: {} No new spawn was chosen; repair the Waystone/landing or rebind.", (Object)failure);
                this.lastSyncFailure = failure;
            }
        }
    }

    public void teleportToSpawn(EntityPlayerMP player) throws CommandException {
        if (player == null || player.connection == null) {
            throw SpawnBinding.problem("A connected player is required to teleport to spawn.");
        }
        GuardConfig settings = GuardRuntime.settings();
        if (settings == null || settings.spawn == null || !settings.spawn.bound) {
            throw SpawnBinding.problem("No spawn Waystone is bound yet.");
        }
        GuardConfig.Spawn spawn = settings.spawn;
        WorldServer world = this.boundWorld(spawn);
        SpawnBinding.loadExistingChunks(world, spawn);
        BlockPos landing = this.validateBinding(world, spawn);
        double x = (double)landing.getX() + 0.5;
        double y = landing.getY();
        double z = (double)landing.getZ() + 0.5;
        float yaw = spawn.yaw;
        player.dismountRidingEntity();
        player.removePassengers();
        if (player.dimension != 0) {
            this.server.getPlayerList().transferPlayerToDimension(player, 0, (Teleporter)new SpawnTeleporter(world, x, y, z, yaw));
        }
        if (player.dimension != 0 || player.getServerWorld() != world) {
            throw SpawnBinding.problem("Another server component prevented the dimension transfer; no spawn coordinates were applied.");
        }
        player.connection.setPlayerLocation(x, y, z, yaw, 0.0f);
        player.fallDistance = 0.0f;
        player.motionX = 0.0;
        player.motionY = 0.0;
        player.motionZ = 0.0;
        player.extinguish();
    }

    public String describe() {
        GuardConfig settings = GuardRuntime.settings();
        if (settings == null || settings.spawn == null || !settings.spawn.bound) {
            return "No spawn Waystone is bound.";
        }
        GuardConfig.Spawn spawn = settings.spawn;
        return "Spawn Waystone: dimension " + spawn.dimension + " at " + spawn.x + " " + spawn.y + " " + spawn.z + "; landing " + spawn.landingX + " " + spawn.landingY + " " + spawn.landingZ + (this.lastSyncFailure == null ? "." : "; unavailable: " + this.lastSyncFailure);
    }

    private WorldServer boundWorld(GuardConfig.Spawn spawn) throws CommandException {
        if (spawn.dimension != 0 || !WAYSTONE_ID.equals(spawn.blockId)) {
            throw SpawnBinding.problem("Invalid saved Waystone spawn binding; rebind an Overworld waystones:waystone.");
        }
        WorldServer world = this.server.getWorld(0);
        if (world == null) {
            throw SpawnBinding.problem("The Overworld is not available.");
        }
        return world;
    }

    private BlockPos validateBinding(WorldServer world, GuardConfig.Spawn spawn) throws CommandException {
        BlockPos recordedBase = new BlockPos(spawn.x, spawn.y, spawn.z);
        BlockPos actualBase = SpawnBinding.resolveBase(world, recordedBase);
        if (!recordedBase.equals((Object)actualBase)) {
            throw SpawnBinding.problem("The saved Waystone base no longer matches the actual lower half.");
        }
        if (this.inArena(world, actualBase) || this.inArena(world, actualBase.up())) {
            throw SpawnBinding.problem("The bound Waystone is now inside a dungeon arena.");
        }
        if (!SpawnBinding.finite(spawn.landingX) || !SpawnBinding.finite(spawn.landingY) || !SpawnBinding.finite(spawn.landingZ) || Float.isNaN(spawn.yaw) || Float.isInfinite(spawn.yaw)) {
            throw SpawnBinding.problem("The saved spawn landing contains invalid coordinates.");
        }
        BlockPos landing = new BlockPos(spawn.landingX, spawn.landingY, spawn.landingZ);
        long dx = (long)landing.getX() - (long)actualBase.getX();
        long dz = (long)landing.getZ() - (long)actualBase.getZ();
        if (Math.abs(dx) > 1L || Math.abs(dz) > 1L || dx == 0L && dz == 0L || landing.getY() != actualBase.getY() || spawn.landingX != (double)landing.getX() + 0.5 || spawn.landingY != (double)landing.getY() || spawn.landingZ != (double)landing.getZ() + 0.5) {
            throw SpawnBinding.problem("The saved spawn landing is not immediately beside the Waystone; rebind it.");
        }
        if (!this.safeLanding(world, landing)) {
            throw SpawnBinding.problem("The bound landing is unloaded, obstructed, hazardous, or inside a dungeon arena.");
        }
        return landing;
    }

    static BlockPos resolveBase(WorldServer world, BlockPos target) throws CommandException {
        BlockPos base;
        if (target == null || target.getY() < 0 || target.getY() > 255 || !world.isBlockLoaded(target)) {
            throw SpawnBinding.problem("The targeted Waystone chunk is not loaded.");
        }
        IBlockState targetState = world.getBlockState(target);
        if (!SpawnBinding.isWaystone(targetState)) {
            throw SpawnBinding.problem("The target is not a waystones:waystone block; point at the actual spawn Waystone.");
        }
        Boolean targetBase = SpawnBinding.baseValue(targetState);
        if (targetBase != null) {
            base = targetBase != false ? target : target.down();
        } else {
            base = SpawnBinding.reflectedParent(world, target);
            if (base == null) {
                throw SpawnBinding.problem("This Waystone has an unsupported block layout; its lower half could not be verified.");
            }
        }
        if (base.getY() < 0 || base.getY() >= 255 || !world.isBlockLoaded(base) || !world.isBlockLoaded(base.up())) {
            throw SpawnBinding.problem("The Waystone's two-block structure is not available.");
        }
        IBlockState bottom = world.getBlockState(base);
        IBlockState top = world.getBlockState(base.up());
        if (!SpawnBinding.isWaystone(bottom) || !SpawnBinding.isWaystone(top) || Boolean.FALSE.equals(SpawnBinding.baseValue(bottom)) || Boolean.TRUE.equals(SpawnBinding.baseValue(top))) {
            throw SpawnBinding.problem("The Waystone is missing a valid lower or upper half; repair it before binding.");
        }
        return base.toImmutable();
    }

    private static BlockPos reflectedParent(WorldServer world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null || !TILE_CLASS.equals(tile.getClass().getName())) {
            return null;
        }
        try {
            BlockPos parentPos;
            TileEntity parentTile;
            Method method = tile.getClass().getMethod("getParent", new Class[0]);
            Object parent = method.invoke((Object)tile, new Object[0]);
            if (parent instanceof TileEntity && (parentTile = (TileEntity)parent).getWorld() == world && ((parentPos = parentTile.getPos()).equals((Object)pos) || parentPos.equals((Object)pos.down()))) {
                return parentPos.toImmutable();
            }
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        return null;
    }

    private static Boolean baseValue(IBlockState state) {
        for (IProperty property : state.getPropertyKeys()) {
            if (!"base".equals(property.getName())) continue;
            Comparable value = state.getValue(property);
            return value instanceof Boolean ? (Boolean)value : null;
        }
        return null;
    }

    private static EnumFacing facingOf(IBlockState state) {
        for (IProperty property : state.getPropertyKeys()) {
            Comparable value;
            if (!"facing".equals(property.getName()) || !((value = state.getValue(property)) instanceof EnumFacing) || !((EnumFacing)value).getAxis().isHorizontal()) continue;
            return (EnumFacing)value;
        }
        return EnumFacing.NORTH;
    }

    private static boolean isWaystone(IBlockState state) {
        ResourceLocation registryName = state.getBlock().getRegistryName();
        return registryName != null && WAYSTONE_ID.equals(registryName.toString());
    }

    private BlockPos chooseLanding(WorldServer world, BlockPos base, EnumFacing facing) {
        LinkedHashSet<BlockPos> candidates = new LinkedHashSet<BlockPos>();
        candidates.add(base.offset(facing));
        candidates.add(base.offset(facing.rotateY()));
        candidates.add(base.offset(facing.rotateYCCW()));
        candidates.add(base.offset(facing.getOpposite()));
        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                if (dx == 0 || dz == 0) continue;
                candidates.add(base.add(dx, 0, dz));
            }
        }
        for (BlockPos candidate : candidates) {
            if (!this.safeLanding(world, candidate)) continue;
            return candidate;
        }
        return null;
    }

    private boolean safeLanding(WorldServer world, BlockPos feet) {
        if (feet.getY() < 1 || feet.getY() > 254 || !world.isBlockLoaded(feet) || !world.isBlockLoaded(feet.up()) || !world.isBlockLoaded(feet.down()) || !world.getWorldBorder().contains(feet) || this.inArena(world, feet) || this.inArena(world, feet.up()) || this.inArena(world, feet.down())) {
            return false;
        }
        if (!world.isAirBlock(feet) || !world.isAirBlock(feet.up())) {
            return false;
        }
        AxisAlignedBB body = new AxisAlignedBB((double)feet.getX() + 0.2, (double)feet.getY(), (double)feet.getZ() + 0.2, (double)feet.getX() + 0.8, (double)feet.getY() + 1.8, (double)feet.getZ() + 0.8);
        if (body.minX < world.getWorldBorder().minX() || body.maxX > world.getWorldBorder().maxX() || body.minZ < world.getWorldBorder().minZ() || body.maxZ > world.getWorldBorder().maxZ() || !world.getCollisionBoxes(null, body).isEmpty()) {
            return false;
        }
        IBlockState floor = world.getBlockState(feet.down());
        return floor.isSideSolid((IBlockAccess)world, feet.down(), EnumFacing.UP) && !floor.getMaterial().isLiquid() && floor.getBlock() != Blocks.MAGMA && floor.getBlock() != Blocks.CACTUS;
    }

    private boolean inArena(WorldServer world, BlockPos pos) {
        return GuardRuntime.manager() != null && GuardRuntime.manager().isArena((World)world, pos);
    }

    private static void loadExistingChunks(WorldServer world, GuardConfig.Spawn spawn) {
        BlockPos landing;
        BlockPos base = new BlockPos(spawn.x, spawn.y, spawn.z);
        if (!world.isBlockLoaded(base)) {
            world.getChunkProvider().loadChunk(base.getX() >> 4, base.getZ() >> 4);
        }
        if (SpawnBinding.finite(spawn.landingX) && SpawnBinding.finite(spawn.landingY) && SpawnBinding.finite(spawn.landingZ) && !world.isBlockLoaded(landing = new BlockPos(spawn.landingX, spawn.landingY, spawn.landingZ))) {
            world.getChunkProvider().loadChunk(landing.getX() >> 4, landing.getZ() >> 4);
        }
    }

    private void applyWorldSpawn(final WorldServer world, final BlockPos landing) {
        GuardRuntime.withBypass(new Runnable(){

            @Override
            public void run() {
                MinecraftServer owner;
                if (!world.getSpawnPoint().equals((Object)landing)) {
                    world.setSpawnPoint(landing);
                    if (!world.getSpawnPoint().equals((Object)landing)) {
                        throw new IllegalStateException("Another server component prevented the world-spawn change.");
                    }
                    SpawnBinding.this.spawnPacketDirty = true;
                }
                if (SpawnBinding.this.spawnPacketDirty && (owner = world.getMinecraftServer()) != null) {
                    owner.getPlayerList().sendPacketToAllPlayers((Packet)new SPacketSpawnPosition(landing));
                    SpawnBinding.this.spawnPacketDirty = false;
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
        return spawn.dimension + ":" + spawn.x + ":" + spawn.y + ":" + spawn.z + ":" + spawn.landingX + ":" + spawn.landingY + ":" + spawn.landingZ;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static CommandException problem(String message) {
        return new CommandException(message, new Object[0]);
    }

    private static String concise(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static final class SpawnTeleporter
    extends Teleporter {
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

        public void placeInPortal(Entity entity, float rotationYaw) {
            entity.setLocationAndAngles(this.x, this.y, this.z, this.yaw, 0.0f);
        }

        public boolean placeInExistingPortal(Entity entity, float rotationYaw) {
            this.placeInPortal(entity, rotationYaw);
            return true;
        }

        public boolean makePortal(Entity entity) {
            return false;
        }

        public void removeStalePortalLocations(long worldTime) {
        }
    }
}

