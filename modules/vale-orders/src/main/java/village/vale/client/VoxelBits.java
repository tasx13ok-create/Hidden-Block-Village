package village.vale.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Tiny client-only voxel particles (pixel chips, smoke cubes, crumbling afterimage pixels) in the
 * style of vanilla block particles: axis-aligned, face-shaded, sized in 1/16-block pixels, with
 * gravity, ground bounce and shrink-out. Capped so a crowd of dodgers cannot flood a frame.
 */
@SideOnly(Side.CLIENT)
public final class VoxelBits {
    public static final double PX = 1 / 16.0;

    private static final List<Bit> BITS = new ArrayList<>();
    static final Random RNG = new Random();

    private static final class Bit {
        double x, y, z, px, py, pz, vx, vy, vz, size, gravity, drag;
        int rgb, age, life;
        float alpha;
        boolean bounce;
    }

    private VoxelBits() {}
    private static boolean installed;

    /** Registers the tick/render handlers once (shared by dodge, jutsu and weapon effects). */
    public static void install() {
        if (installed) return;
        installed = true;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new VoxelBits());
        VfxBudget.install();
    }

    public static void spawn(double x, double y, double z, double vx, double vy, double vz, double pixels, int rgb, float alpha, int life, double gravity, double drag, boolean bounce) {
        synchronized (BITS) {
            while (BITS.size() >= VfxBudget.particleCap()) BITS.remove(0);
            Bit b = new Bit();
            b.x = b.px = x; b.y = b.py = y; b.z = b.pz = z;
            b.vx = vx; b.vy = vy; b.vz = vz; b.size = pixels * PX; b.rgb = rgb; b.alpha = alpha;
            b.life = life; b.gravity = gravity; b.drag = drag; b.bounce = bounce;
            BITS.add(b);
        }
    }

    /** Map colour of the block under (x,y,z) with slight per-chip variation, or -1 over air. */
    public static int groundColor(Entity e) {
        BlockPos pos = new BlockPos(e.posX, e.posY - 0.2, e.posZ);
        IBlockState state = e.world.getBlockState(pos);
        if (state.getMaterial() == net.minecraft.block.material.Material.AIR) return -1;
        int c = state.getMapColor(e.world, pos).colorValue;
        if (state.getBlock() == net.minecraft.init.Blocks.GRASS) c = 0x6A9E3A;
        return c == 0 ? 0x7F7F7F : c;
    }

    public static int vary(int rgb) { return VfxDraw.shade(rgb, Math.min(1, 0.78 + RNG.nextDouble() * 0.3)); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        synchronized (BITS) {
            if (mc.world == null || mc.isGamePaused()) { if (mc.world == null) BITS.clear(); return; }
            for (java.util.Iterator<Bit> it = BITS.iterator(); it.hasNext(); ) {
                Bit b = it.next();
                b.px = b.x; b.py = b.y; b.pz = b.z;
                b.vy -= b.gravity;
                b.vx *= b.drag; b.vy *= b.drag; b.vz *= b.drag;
                double ny = b.y + b.vy;
                if (b.bounce && b.vy < 0) {
                    BlockPos below = new BlockPos(b.x, ny - b.size / 2, b.z);
                    if (mc.world.getBlockState(below).isFullBlock()) {
                        ny = below.getY() + 1 + b.size / 2;
                        b.vy = -b.vy * 0.3; b.vx *= 0.6; b.vz *= 0.6;
                    }
                }
                b.x += b.vx; b.y = ny; b.z += b.vz;
                if (++b.age >= b.life) it.remove();
            }
        }
    }

    @SubscribeEvent public void render(RenderWorldLastEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        Entity cam = mc.getRenderViewEntity();
        if (cam == null) return;
        float pt = e.getPartialTicks();
        double cx = cam.lastTickPosX + (cam.posX - cam.lastTickPosX) * pt, cy = cam.lastTickPosY + (cam.posY - cam.lastTickPosY) * pt, cz = cam.lastTickPosZ + (cam.posZ - cam.lastTickPosZ) * pt;
        synchronized (BITS) {
            if (BITS.isEmpty()) return;
            VfxDraw.begin(-cx, -cy, -cz);
            try {
                net.minecraft.client.renderer.GlStateManager.depthMask(true);
                for (Bit b : BITS) {
                    double f = (b.age + pt) / b.life;
                    double shrink = f < 0.6 ? 1 : 1 - (f - 0.6) / 0.4;
                    // Size steps in whole pixels as it shrinks, like vanilla particles.
                    double s = Math.max(PX, Math.round(b.size * shrink / PX) * PX);
                    double x = b.px + (b.x - b.px) * pt, y = b.py + (b.y - b.py) * pt, z = b.pz + (b.z - b.pz) * pt;
                    VfxDraw.cube(x, y, z, s, b.rgb, b.alpha);
                }
            } finally {
                VfxDraw.end();
            }
        }
    }
}
