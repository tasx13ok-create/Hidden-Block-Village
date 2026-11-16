package village.immersion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Water-walking and wall-running. Runs on both sides with the same rules so client prediction and
 * the server agree; chakra is paid on the server only. Movement is the player's own motion, so
 * anti-cheat and other mods see normal velocity.
 */
final class Movement {
    private static final Map<UUID, int[]> STATE = new HashMap<>();   // {waterTicks, wallTicks, wallArmed, paidWall}

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayer p = e.player;
        if (p.isSpectator() || p.isRiding() || p.isElytraFlying()) return;
        int[] st = STATE.computeIfAbsent(p.getUniqueID(), k -> new int[4]);
        boolean server = !p.world.isRemote;

        // Water-walking: hold the surface while chakra lasts.
        BlockPos feet = new BlockPos(p.posX, p.posY + 0.05, p.posZ);
        boolean feetWater = p.world.getBlockState(feet).getMaterial() == Material.WATER;
        boolean headWater = p.isInsideOfMaterial(Material.WATER);
        if (ImmersionMath.waterWalk(Config.waterWalk, p.isSneaking(), p.capabilities.isFlying, feetWater, headWater, p.motionY)) {
            boolean paid = true;
            if (server && st[0] % ImmersionMath.WATER_PAY_EVERY == 0) paid = ChakraCost.pay(p, Config.waterCost);
            if (paid) {
                double surface = Math.floor(p.posY + 0.05) + 0.9;
                if (p.posY < surface) { p.motionY = Math.min(0.12, (surface - p.posY) * 0.6 + 0.02); }
                else p.motionY = Math.max(p.motionY, 0);
                p.fallDistance = 0;
                p.onGround = true;
                st[0]++;
            } else st[0] = 0;
        } else st[0] = 0;

        // Wall-running: sprint + airborne + pressing into a wall + holding jump.
        if (p.onGround) { st[1] = 0; st[2] = 1; st[3] = 0; }
        boolean jump = jumpHeld(p);
        if (st[2] == 1 && ImmersionMath.wallRun(Config.wallRun, p.isSprinting(), p.onGround, p.collidedHorizontally, st[1], jump)) {
            if (server && st[3] == 0) { if (!ChakraCost.pay(p, Config.wallCost)) { st[2] = 0; return; } st[3] = 1; }
            p.motionY = Math.max(p.motionY, ImmersionMath.wallLift(st[1]));
            p.fallDistance = 0;
            st[1]++;
        } else if (st[1] > 0 && !p.collidedHorizontally) {
            st[2] = 0;   // one run per jump
        }
    }

    /** Jump input on the client; on the server, a rising player counts as holding jump. */
    private static boolean jumpHeld(EntityPlayer p) {
        if (p.world.isRemote) return ClientInput.jump();
        return p.motionY > -0.2;
    }

    static int wallTicks(EntityPlayer p) { int[] st = STATE.get(p.getUniqueID()); return st == null ? 0 : st[1]; }
    static int waterTicks(EntityPlayer p) { int[] st = STATE.get(p.getUniqueID()); return st == null ? 0 : st[0]; }
}
