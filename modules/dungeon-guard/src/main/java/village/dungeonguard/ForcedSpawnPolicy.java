package village.dungeonguard;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.FakePlayer;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.SpawnBinding;

public final class ForcedSpawnPolicy {
    private ForcedSpawnPolicy() {
    }

    private static SpawnBinding binding() {
        if (!GuardRuntime.ready() || GuardRuntime.bypassed() || !GuardRuntime.policy().enabled) {
            return null;
        }
        SpawnBinding spawn = GuardRuntime.spawn();
        return spawn != null && spawn.isBound() ? spawn : null;
    }

    private static SpawnBinding binding(EntityPlayer player) {
        SpawnBinding spawn = ForcedSpawnPolicy.binding();
        return spawn != null && player instanceof EntityPlayerMP && !(player instanceof FakePlayer) && player.world != null && !player.world.isRemote && spawn.owns(player) ? spawn : null;
    }

    public static boolean assignPersonalSpawn(EntityPlayer player) {
        SpawnBinding spawn = ForcedSpawnPolicy.binding(player);
        if (spawn == null) {
            return false;
        }
        spawn.pinPersonalSpawn(player);
        return true;
    }

    public static int personalDimension(int original, EntityPlayer player) {
        return ForcedSpawnPolicy.binding(player) == null ? original : 0;
    }

    public static BlockPos personalLocation(BlockPos original, EntityPlayer player, int dimension) {
        SpawnBinding spawn = ForcedSpawnPolicy.binding(player);
        return spawn == null ? original : (dimension == 0 ? spawn.recordedLanding() : null);
    }

    public static boolean personalForced(boolean original, EntityPlayer player, int dimension) {
        return ForcedSpawnPolicy.binding(player) == null ? original : dimension == 0;
    }

    public static NBTTagCompound playerDataLoaded(NBTTagCompound saved, EntityPlayerMP player) {
        SpawnBinding spawn = ForcedSpawnPolicy.binding((EntityPlayer)player);
        if (spawn != null) {
            spawn.playerDataLoaded(player, saved == null);
        }
        return saved;
    }

    public static void loginFinished(EntityPlayerMP player) {
        SpawnBinding spawn = ForcedSpawnPolicy.binding((EntityPlayer)player);
        if (spawn != null) {
            spawn.loginFinished(player);
        }
    }

    public static int respawnDimension(EntityPlayerMP player, int original) {
        SpawnBinding spawn = ForcedSpawnPolicy.binding((EntityPlayer)player);
        if (spawn == null) {
            return original;
        }
        spawn.pinPersonalSpawn((EntityPlayer)player);
        return 0;
    }

    public static EntityPlayerMP respawnFinished(EntityPlayerMP recreated, EntityPlayerMP previous) {
        SpawnBinding spawn = ForcedSpawnPolicy.binding((EntityPlayer)recreated);
        if (spawn != null) {
            spawn.respawnFinished(recreated, previous);
        }
        return recreated;
    }

    public static BlockPos worldSpawn(WorldInfo info, BlockPos original) {
        SpawnBinding spawn = ForcedSpawnPolicy.binding();
        if (spawn == null || !spawn.owns(info)) {
            return original;
        }
        BlockPos landing = spawn.recordedLanding();
        if (!landing.equals((Object)original)) {
            spawn.correctSpawnPacket();
        }
        return landing;
    }

    public static int worldCoordinate(WorldInfo info, int original, int axis) {
        int coordinate;
        SpawnBinding spawn = ForcedSpawnPolicy.binding();
        if (spawn == null || !spawn.owns(info)) {
            return original;
        }
        BlockPos landing = spawn.recordedLanding();
        if (axis == 0) {
            coordinate = landing.getX();
        } else if (axis == 1) {
            coordinate = landing.getY();
        } else if (axis == 2) {
            coordinate = landing.getZ();
        } else {
            throw new IllegalArgumentException("Unknown spawn coordinate axis");
        }
        if (coordinate != original) {
            spawn.correctSpawnPacket();
        }
        return coordinate;
    }
}

