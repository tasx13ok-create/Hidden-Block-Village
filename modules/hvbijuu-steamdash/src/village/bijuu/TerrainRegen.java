package village.bijuu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockPane;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Temporary terrain damage: a block is recorded with its exact state, changed (to air or a cracked
 * look), and put back after `regenSeconds`. Protection is asked first (Forge BreakEvent, then the
 * setBlockState result), containers and unbreakable blocks are never touched, a block is never
 * restored into an entity standing in its space, and everything is restored at once when a world
 * unloads or the server stops.
 */
final class TerrainRegen {
    private static final class Entry { final WorldServer world; final BlockPos pos; final IBlockState original, placed; long restoreAt;
        Entry(WorldServer w, BlockPos p, IBlockState o, IBlockState n, long at) { world = w; pos = p; original = o; placed = n; restoreAt = at; } }
    private static final Map<World, Map<BlockPos, Entry>> QUEUE = new HashMap<>();

    /** Glass, panes, leaves, plants, vines, snow, cobwebs and fences: things a body crashes straight through. */
    static boolean soft(IBlockState s) {
        Material m = s.getMaterial();
        Block b = s.getBlock();
        return m == Material.GLASS || m == Material.LEAVES || m == Material.PLANTS || m == Material.VINE || m == Material.SNOW
                || m == Material.CRAFTED_SNOW || m == Material.WEB || b instanceof BlockFence || b instanceof BlockPane || b instanceof BlockFenceGate;
    }

    /** Loose ground a skidding body gouges: grass, dirt, sand, gravel, farmland, mycelium, snow. */
    static boolean gougeable(IBlockState s) {
        Block b = s.getBlock();
        return b == Blocks.GRASS || b == Blocks.DIRT || b == Blocks.SAND || b == Blocks.GRAVEL || b == Blocks.FARMLAND
                || b == Blocks.MYCELIUM || b == Blocks.SNOW || b == Blocks.GRASS_PATH;
    }

    /** Breaks a block for REGEN ticks. Returns true when the space is passable afterwards. */
    static boolean breakTemporarily(WorldServer w, BlockPos pos, EntityPlayerMP by) { return replace(w, pos, Blocks.AIR.getDefaultState(), by); }

    /** The cracked look of a block (for the ground under a crouch), or null when it has none. */
    static IBlockState cracked(IBlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.STONE) return Blocks.COBBLESTONE.getDefaultState();
        if (b == Blocks.GRASS || b == Blocks.DIRT && s.getValue(BlockDirt.VARIANT) == BlockDirt.DirtType.DIRT)
            return Blocks.DIRT.getDefaultState().withProperty(BlockDirt.VARIANT, BlockDirt.DirtType.COARSE_DIRT);
        if (b == Blocks.SANDSTONE) return Blocks.SAND.getDefaultState();
        return null;
    }

    static boolean replace(WorldServer w, BlockPos pos, IBlockState with, EntityPlayerMP by) {
        if (!w.isBlockLoaded(pos)) return false;
        IBlockState state = w.getBlockState(pos);
        if (state == with) return true;
        if (state.getBlock().isAir(state, w, pos)) return with.getBlock() == Blocks.AIR;
        if (state.getBlock().hasTileEntity(state) || state.getMaterial().isLiquid()) return false;
        if (state.getBlockHardness(w, pos) < 0 || state.getBlock() == Blocks.OBSIDIAN) return false;
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(w, pos, state, by))) return false;
        if (!w.setBlockState(pos, with, 3)) return false;   // a claim or Dungeon Guard refused
        Map<BlockPos, Entry> q = QUEUE.computeIfAbsent(w, k -> new HashMap<>());
        Entry old = q.get(pos);
        long at = w.getTotalWorldTime() + SteamDash.regenSeconds * 20L;
        q.put(pos.toImmutable(), new Entry(w, pos.toImmutable(), old != null ? old.original : state, with, at));
        return true;
    }

    static int pending() { int n = 0; for (Map<BlockPos, Entry> q : QUEUE.values()) n += q.size(); return n; }

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote) return;
        Map<BlockPos, Entry> q = QUEUE.get(e.world);
        if (q == null || q.isEmpty()) return;
        long now = e.world.getTotalWorldTime();
        List<Entry> due = new ArrayList<>();
        for (Entry en : q.values()) if (now >= en.restoreAt) due.add(en);
        for (Entry en : due) {
            if (!en.world.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class, new AxisAlignedBB(en.pos)).isEmpty()) { en.restoreAt = now + 20; continue; }
            restore(en, true);
            q.remove(en.pos);
        }
    }

    private static void restore(Entry en, boolean fx) {
        if (!en.world.isBlockLoaded(en.pos)) return;
        IBlockState now = en.world.getBlockState(en.pos);
        if (now != en.placed) return;   // somebody built or changed it since: leave it
        en.world.setBlockState(en.pos, en.original, 3);
        if (fx) en.world.spawnParticle(EnumParticleTypes.BLOCK_DUST, en.pos.getX() + 0.5, en.pos.getY() + 0.5, en.pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02, Block.getStateId(en.original));
    }

    @SubscribeEvent public void unload(WorldEvent.Unload e) {
        Map<BlockPos, Entry> q = QUEUE.remove(e.getWorld());
        if (q != null) for (Entry en : q.values()) restore(en, false);
    }

    /** Restores everything now (server stopping). */
    static void restoreAll() {
        for (Iterator<Map<BlockPos, Entry>> it = QUEUE.values().iterator(); it.hasNext();) {
            for (Entry en : it.next().values()) restore(en, false);
            it.remove();
        }
    }
}
