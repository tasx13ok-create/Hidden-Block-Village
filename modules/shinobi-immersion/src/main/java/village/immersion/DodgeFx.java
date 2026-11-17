package village.immersion;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Makes Core's dodge feel instant without changing Core. Core waits for the server before it
 * moves you, so on the same tick you press its dodge key this answers with the feel of the dodge:
 * a lean into side dodges, a field-of-view punch and a whoosh. The real push still comes from
 * Core after the server accepts it (chakra and cooldown unchanged). When it lands, an afterimage
 * trail follows you and the landing kicks up dust. If Core refuses, the feel simply fades.
 * Core's key is read through its public binding() accessor; without Core this stays idle.
 */
@SideOnly(Side.CLIENT)
final class DodgeFx {
    private static KeyBinding binding;
    private static boolean looked, wasDown;
    private static long pressMs = -1, confirmedTick = -100, lastConfirm = -100, tick;
    private static int direction;
    private static boolean airborneTrail;
    private static double[][] trail = new double[6][];
    private static int trailN;

    /** Milliseconds since the dodge feel started, or -1. */
    static double sinceMs() { return pressMs < 0 ? -1 : System.nanoTime() / 1e6 - pressMs; }

    private static KeyBinding binding() {
        if (!looked) {
            looked = true;
            try { Method m = Class.forName("village.vale.client.DodgeClient").getMethod("binding"); binding = (KeyBinding) m.invoke(null); }
            catch (ReflectiveOperationException | LinkageError | ClassCastException absent) { binding = null; }
        }
        return binding;
    }

    @SubscribeEvent public void input(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !Config.dodgeFeel) return;
        tick++;
        Minecraft mc = Minecraft.getMinecraft();
        KeyBinding k = binding();
        EntityPlayerSP p = mc.player;
        boolean down = k != null && k.getKeyCode() != 0 && k.isKeyDown() && mc.currentScreen == null && mc.inGameHasFocus;
        boolean pressed = down && !wasDown;
        wasDown = down;
        if (pressed && p != null && p.isEntityAlive() && !p.isSpectator() && !p.isRiding() && !p.capabilities.isFlying && !p.isElytraFlying()
                && tick - lastConfirm > 16) {   // Core's cooldown: don't promise a dodge it will refuse
            pressMs = (long) (System.nanoTime() / 1e6);
            direction = ParkourMath.dodgeDirection(p.movementInput.moveForward, p.movementInput.moveStrafe);
            p.world.playSound(p.posX, p.posY, p.posZ, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.22f, 1.75f, false);
        }
        // Refused (no burst within ~6 ticks): let the feel end.
        if (pressMs >= 0 && sinceMs() > 300 && confirmedTick < tick - 8) pressMs = -1;
    }

    /** Called by ParkourClient on the tick Core's dodge burst arrives. */
    static void confirmed(EntityPlayerSP p) {
        if (!Config.dodgeFeel) return;
        confirmedTick = tick; lastConfirm = tick;
        if (pressMs < 0 || sinceMs() > 400) {   // pressed through a path we did not see: start the feel now
            pressMs = (long) (System.nanoTime() / 1e6);
            direction = ParkourMath.dodgeDirection(p.movementInput.moveForward, p.movementInput.moveStrafe);
        }
        trailN = 0; airborneTrail = true;
    }

    static void hop(EntityPlayerSP p) {
        p.world.playSound(p.posX, p.posY, p.posZ, SoundEvents.ENTITY_PLAYER_SMALL_FALL, SoundCategory.PLAYERS, 0.4f, 1.4f, false);
    }

    @SubscribeEvent public void trail(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Config.dodgeFeel) return;
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (p == null || confirmedTick < 0) return;
        long age = tick - confirmedTick;
        if (age >= 0 && age < 6) {   // afterimage: soft puffs along the dodge path
            trail[trailN % trail.length] = new double[] {p.posX, p.posY, p.posZ};
            trailN++;
            for (int i = 0; i < Math.min(trailN, trail.length); i++) {
                double[] q = trail[i];
                if (q != null && p.world.rand.nextInt(2) == 0) p.world.spawnParticle(EnumParticleTypes.CLOUD, q[0], q[1] + 0.2 + p.world.rand.nextDouble() * 1.4, q[2], 0, 0.005, 0);
            }
        }
        if (airborneTrail && age > 1 && age < 20 && p.onGround) {   // landing dust
            airborneTrail = false;
            net.minecraft.block.state.IBlockState g = p.world.getBlockState(new BlockPos(p.posX, p.posY - 0.2, p.posZ));
            if (g.getMaterial() != net.minecraft.block.material.Material.AIR) {
                int id = net.minecraft.block.Block.getStateId(g);
                for (int i = 0; i < 10; i++) p.world.spawnParticle(EnumParticleTypes.BLOCK_DUST, p.posX, p.posY + 0.1, p.posZ, (p.world.rand.nextDouble() - 0.5) * 0.3, 0.05, (p.world.rand.nextDouble() - 0.5) * 0.3, id);
            }
        }
        if (age >= 20) airborneTrail = false;
    }

    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup e) {
        if (!Config.dodgeFeel || !Config.cameraTilt || pressMs < 0) return;
        double r = ParkourMath.dodgeRoll(direction, sinceMs());
        if (r != 0) e.setRoll((float) (e.getRoll() + r));
    }
}
