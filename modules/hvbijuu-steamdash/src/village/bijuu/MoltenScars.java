package village.bijuu;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
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
 * Molten scars: after a dash, the rock lining its tunnel and crater glows as magma, vents steam
 * pillars that pop anyone standing in them into the air, then cools to cobblestone and andesite.
 * Only natural ground is touched, every block asks protection first, and nothing is left molten when
 * a world unloads.
 */
final class MoltenScars {
    private static final class Scar { final WorldServer world; final BlockPos pos; final long coolAt; Scar(WorldServer w, BlockPos p, long c) { world = w; pos = p; coolAt = c; } }
    private static final List<Scar> SCARS = new ArrayList<>();
    private static final Random RAND = new Random();

    /** Turns part of a dash's exposed lining molten. */
    static int scar(WorldServer w, EntityPlayerMP by, Set<BlockPos> lining) {
        if (!SteamDash.moltenScars || lining.isEmpty()) return 0;
        List<BlockPos> exposed = new ArrayList<>();
        for (BlockPos p : lining) if (natural(w.getBlockState(p)) && open(w, p)) exposed.add(p);
        java.util.Collections.shuffle(exposed, RAND);
        long cool = w.getTotalWorldTime() + SteamDash.moltenSeconds * 20L;
        int made = 0;
        for (BlockPos p : exposed) {
            if (made >= SteamDash.moltenMax) break;
            if (RAND.nextFloat() > 0.65f) continue;
            IBlockState state = w.getBlockState(p);
            if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(w, p, state, by))) continue;
            if (!w.setBlockState(p, Blocks.MAGMA.getDefaultState(), 3)) continue;
            // Stagger cooling so the scar fades out patchily instead of all at once.
            SCARS.add(new Scar(w, p, cool + RAND.nextInt(40)));
            made++;
        }
        if (made > 0) w.playSound(null, exposed.get(0), SteamDash.SIZZLE, SoundCategory.BLOCKS, 1.4f, 0.9f);
        return made;
    }

    static int active() { return SCARS.size(); }

    private static boolean open(World w, BlockPos p) {
        for (EnumFacing f : EnumFacing.values()) if (w.isAirBlock(p.offset(f))) return true;
        return false;
    }

    /** Ground that may melt: rock, dirt and sand, never builds, ores or containers. */
    static boolean natural(IBlockState s) {
        Block b = s.getBlock();
        return b == Blocks.STONE || b == Blocks.DIRT || b == Blocks.GRASS || b == Blocks.GRAVEL || b == Blocks.SAND || b == Blocks.COBBLESTONE
                || b == Blocks.SANDSTONE || b == Blocks.CLAY || b == Blocks.NETHERRACK || b == Blocks.MYCELIUM;
    }

    private static IBlockState cooled(BlockPos p) {
        return DashMath.coolTo(p.getX(), p.getY(), p.getZ()) == 0 ? Blocks.COBBLESTONE.getDefaultState()
                : Blocks.STONE.getDefaultState().withProperty(BlockStone.VARIANT, BlockStone.EnumType.ANDESITE);
    }

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.world.isRemote || SCARS.isEmpty()) return;
        WorldServer w = (WorldServer) e.world;
        long now = w.getTotalWorldTime();
        List<Scar> here = new ArrayList<>();
        for (Iterator<Scar> it = SCARS.iterator(); it.hasNext();) {
            Scar s = it.next();
            if (s.world != w) continue;
            if (now >= s.coolAt) { cool(s); it.remove(); continue; }
            here.add(s);
        }
        if (here.isEmpty()) return;
        // Glow: lava drips and embers off the scar every few ticks.
        for (int i = 0; i < Math.min(4, here.size()); i++) {
            Scar s = here.get(RAND.nextInt(here.size()));
            w.spawnParticle(EnumParticleTypes.LAVA, true, s.pos.getX() + 0.5, s.pos.getY() + 1.05, s.pos.getZ() + 0.5, 1, 0.3, 0.0, 0.3, 0.0);
        }
        if (now % 6 == 0) vent(w, here.get(RAND.nextInt(here.size())));
    }

    /** A steam pillar out of one scar block; anyone standing in it is popped into the air. */
    private static void vent(WorldServer w, Scar s) {
        BlockPos up = s.pos.up();
        if (!w.isAirBlock(up)) return;
        double x = s.pos.getX() + 0.5, y = s.pos.getY() + 1.0, z = s.pos.getZ() + 0.5;
        for (int i = 0; i < 14; i++)
            w.spawnParticle(EnumParticleTypes.CLOUD, true, x + (RAND.nextDouble() - 0.5) * 0.4, y + i * 0.12, z + (RAND.nextDouble() - 0.5) * 0.4, 0, (RAND.nextDouble() - 0.5) * 0.05, 0.45 + RAND.nextDouble() * 0.25, (RAND.nextDouble() - 0.5) * 0.05, 1.0);
        w.spawnParticle(EnumParticleTypes.SMOKE_LARGE, true, x, y + 0.2, z, 3, 0.15, 0.2, 0.15, 0.03);
        if (RAND.nextInt(3) == 0) w.playSound(null, up, SteamDash.HISS, SoundCategory.BLOCKS, 0.7f, 0.8f + RAND.nextFloat() * 0.4f);
        for (Entity e : w.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(up).expand(0, 4, 0).grow(0.2))) {
            if (e.noClip || e instanceof net.minecraft.entity.player.EntityPlayer && ((net.minecraft.entity.player.EntityPlayer) e).isSpectator()) continue;
            e.motionY = Math.max(e.motionY, 0.95);
            e.velocityChanged = true;
        }
    }

    private static void cool(Scar s) {
        if (s.world.isBlockLoaded(s.pos) && s.world.getBlockState(s.pos).getBlock() == Blocks.MAGMA) {
            s.world.setBlockState(s.pos, cooled(s.pos), 3);
            s.world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, true, s.pos.getX() + 0.5, s.pos.getY() + 1.0, s.pos.getZ() + 0.5, 4, 0.3, 0.1, 0.3, 0.01);
        }
    }

    /** Nothing stays molten across a world unload or server stop. */
    @SubscribeEvent public void unload(WorldEvent.Unload e) {
        if (e.getWorld().isRemote) return;
        for (Iterator<Scar> it = SCARS.iterator(); it.hasNext();) { Scar s = it.next(); if (s.world == e.getWorld()) { cool(s); it.remove(); } }
    }
}
