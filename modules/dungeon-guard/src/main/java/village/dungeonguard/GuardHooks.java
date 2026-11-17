package village.dungeonguard;

import net.minecraft.block.state.BlockPistonStructureHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import village.dungeonguard.DungeonManager;
import village.dungeonguard.GuardRuntime;

public final class GuardHooks {
    private GuardHooks() {
    }

    private static boolean active(World w) {
        return GuardRuntime.ready() && GuardRuntime.policy().enabled && w != null && !w.isRemote;
    }

    public static boolean blockChange(World w, BlockPos p, IBlockState next) {
        IBlockState old;
        if (GuardRuntime.isRestoring() && w != null && !w.isRemote) {
            return true;
        }
        if (!GuardHooks.active(w) || p == null || next == null || GuardRuntime.bypassed()) {
            return false;
        }
        boolean protectedCell = GuardRuntime.guarded(w, p);
        if (!protectedCell) {
            boolean blocked = GuardHooks.protectedTile(w, p, next);
            if (!blocked && GuardRuntime.manager() != null) {
                GuardRuntime.manager().recordChange(w, p);
            }
            return blocked;
        }
        DungeonManager manager = GuardRuntime.manager();
        if (manager != null && manager.isEntryLocked(w, p)) {
            return true;
        }
        if (w.isBlockLoaded(p) && ((old = w.getBlockState(p)) == next || GuardRuntime.policy().allowStateChangesInProtectedWorld && old.getBlock() == next.getBlock())) {
            if (manager != null) {
                manager.recordChange(w, p);
            }
            return false;
        }
        return !GuardRuntime.policy().allowWorldGenerationEdits || manager == null || manager.isArena(w, p) || !GuardHooks.generatingNewTerrain();
    }

    private static boolean protectedTile(World w, BlockPos p, IBlockState next) {
        DungeonManager m = GuardRuntime.manager();
        if (!GuardRuntime.policy().protectDungeonTileEntities || m == null || !m.isArena(w, p) || GuardRuntime.editing(w, p) || !w.isBlockLoaded(p)) {
            return false;
        }
        if (w.restoringBlockSnapshots && m.isWritable(w, p)) {
            return false;
        }
        IBlockState old = w.getBlockState(p);
        return old.getBlock() != next.getBlock() && old.getBlock().hasTileEntity(old);
    }

    public static boolean blockDestroy(World w, BlockPos p) {
        boolean blocked;
        if (GuardRuntime.isRestoring() && w != null && !w.isRemote) {
            return true;
        }
        if (!GuardHooks.active(w) || p == null || GuardRuntime.bypassed()) {
            return false;
        }
        boolean bl = blocked = GuardRuntime.guarded(w, p) || GuardHooks.protectedTile(w, p, Blocks.AIR.getDefaultState());
        if (!blocked && GuardRuntime.manager() != null) {
            GuardRuntime.manager().recordChange(w, p);
        }
        return blocked;
    }

    public static boolean blockChunkChange(Chunk chunk, BlockPos p, IBlockState next) {
        if (GuardRuntime.isRestoring() && chunk != null && !chunk.getWorld().isRemote) {
            return !GuardRuntime.consumeRestorationWrite(chunk.getWorld(), p);
        }
        return chunk != null && GuardHooks.blockChange(chunk.getWorld(), p, next);
    }

    public static boolean blockDrop(World w, BlockPos p) {
        if (GuardRuntime.isRestoring()) {
            return w != null && !w.isRemote;
        }
        if (!GuardHooks.active(w) || p == null || GuardRuntime.bypassed() || GuardRuntime.editing(w, p)) {
            return false;
        }
        DungeonManager m = GuardRuntime.manager();
        return GuardRuntime.guarded(w, p) || GuardRuntime.policy().suppressDungeonBlockDrops && m != null && m.isArena(w, p);
    }

    public static float filterDropChance(World w, BlockPos p, float chance) {
        return GuardHooks.blockDrop(w, p) ? -1.0f : chance;
    }

    public static boolean handleKamuiBlock(World w, BlockPos p) {
        if (GuardHooks.blockDestroy(w, p)) {
            return true;
        }
        if (GuardHooks.blockDrop(w, p)) {
            w.setBlockToAir(p);
            return true;
        }
        return false;
    }

    public static boolean blockSpawn(World w, Entity e) {
        if (w == null || w.isRemote || e == null) {
            return false;
        }
        if (GuardRuntime.isRestoring() && (e instanceof EntityItem || e instanceof EntityXPOrb || e instanceof EntityFallingBlock || e instanceof EntityTNTPrimed || e instanceof IProjectile)) {
            return true;
        }
        if (!GuardHooks.active(w) || GuardRuntime.bypassed()) {
            return false;
        }
        if (e instanceof EntityFallingBlock) {
            BlockPos source = new BlockPos(e.posX, e.posY, e.posZ);
            DungeonManager manager = GuardRuntime.manager();
            if (GuardRuntime.policy().suppressDungeonBlockDrops && manager != null && manager.isArena(w, source)) {
                ((EntityFallingBlock)e).shouldDropItem = false;
            }
            return GuardRuntime.guarded(w, source);
        }
        return false;
    }

    public static boolean blockPiston(World w, BlockPos p, EnumFacing direction, boolean extending) {
        if (GuardRuntime.isRestoring()) {
            return true;
        }
        if (!GuardHooks.active(w) || GuardRuntime.bypassed()) {
            return false;
        }
        if (GuardRuntime.guarded(w, p) || GuardRuntime.guarded(w, p.offset(direction))) {
            return true;
        }
        BlockPistonStructureHelper helper = new BlockPistonStructureHelper(w, p, direction, extending);
        if (!helper.canMove()) {
            return false;
        }
        EnumFacing movement = extending ? direction : direction.getOpposite();
        for (BlockPos source : helper.getBlocksToMove()) {
            if (!GuardRuntime.guarded(w, source) && !GuardRuntime.guarded(w, source.offset(movement))) continue;
            return true;
        }
        for (BlockPos destroyed : helper.getBlocksToDestroy()) {
            if (!GuardRuntime.guarded(w, destroyed)) continue;
            return true;
        }
        return false;
    }

    public static boolean blockPortal(World w, BlockPos first, BlockPos second) {
        if (!GuardHooks.active(w) || GuardRuntime.bypassed()) {
            return false;
        }
        return GuardHooks.blockDestroy(w, first) || GuardHooks.blockDestroy(w, first.down()) || GuardHooks.blockDestroy(w, second) || GuardHooks.blockDestroy(w, second.down());
    }

    private static boolean generatingNewTerrain() {
        for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
            String c = f.getClassName();
            String m = f.getMethodName();
            if (c.equals("net.minecraftforge.fml.common.registry.GameRegistry") && m.equals("generateWorld")) {
                return true;
            }
            if (!c.startsWith("net.minecraft.world.gen.ChunkGenerator") || !m.equals("populate") && !m.equals("func_185931_b")) continue;
            return true;
        }
        return false;
    }
}

