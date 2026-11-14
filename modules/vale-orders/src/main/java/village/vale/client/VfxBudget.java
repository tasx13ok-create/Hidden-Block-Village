package village.vale.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Frame budget and distance level-of-detail for all procedural effects (voxel cubes, particles).
 * Scales with the client "jutsuDetail" setting (0 low, 1 normal, 2 high) so big fights stay smooth:
 *   - each frame may emit at most {@link #cubeBudget()} voxel cubes; extra cubes are skipped
 *     (effects degrade by dropping fine detail, never by stalling the frame);
 *   - effects beyond {@link #FAR} blocks are not drawn, and past {@link #MID} they use reduced detail;
 *   - the voxel particle cap follows the same setting.
 * Counters are exposed for diagnostics (last frame's cube count and skipped cubes).
 */
@SideOnly(Side.CLIENT)
public final class VfxBudget {
    private VfxBudget() {}
    public static final double MID = 24, FAR = 48;
    private static int used, skipped;
    public static int lastUsed, lastSkipped;
    private static boolean installed;

    public static void install() {
        if (installed) return;
        installed = true;
        MinecraftForge.EVENT_BUS.register(new VfxBudget.Hooks());
    }

    public static final class Hooks {
        @SubscribeEvent public void frame(TickEvent.RenderTickEvent e) {
            if (e.phase != TickEvent.Phase.START) return;
            lastUsed = used; lastSkipped = skipped; used = 0; skipped = 0;
        }
    }

    public static int cubeBudget() { int d = JutsuVfx.detail; return d <= 0 ? 1200 : d == 1 ? 3000 : 9000; }
    public static int particleCap() { int d = JutsuVfx.detail; return d <= 0 ? 200 : d == 1 ? 600 : 1200; }

    /** Reserves one cube draw this frame; false when the frame budget is spent. */
    static boolean take() {
        if (used >= cubeBudget()) { skipped++; return false; }
        used++;
        return true;
    }

    /** Detail factor for an effect at this entity: 1 near, 0.5 mid range, 0 when too far to draw. */
    public static double lod(Entity e) {
        Entity cam = Minecraft.getMinecraft().getRenderViewEntity();
        if (cam == null || e == null) return 1;
        double d2 = cam.getDistanceSq(e);
        if (d2 > FAR * FAR) return 0;
        return d2 > MID * MID ? 0.5 : 1;
    }
}
