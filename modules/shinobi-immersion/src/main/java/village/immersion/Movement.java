package village.immersion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.EnumFacing;
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
    // Separate client and server state: in single player both sides tick the same player UUID.
    private static final Map<UUID, int[]> SERVER = new HashMap<>(), CLIENT = new HashMap<>();
    private static Map<UUID, int[]> state(EntityPlayer p) { return p.world.isRemote ? CLIENT : SERVER; }   // {waterTicks, wallTicks, wallArmed, paidWall, ledgeArmed, ledgeAge}
    private static final UUID CANOPY = UUID.fromString("6f1c2b6e-3d1a-4c55-9b7e-5a8e0c4f2d10");

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        EntityPlayer p = e.player;
        if (p.isSpectator() || p.isRiding() || p.isElytraFlying()) return;
        int[] st = state(p).computeIfAbsent(p.getUniqueID(), k -> new int[6]);
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

        // Everything below reads the jump key, so on a client only the local player runs it.
        if (p.world.isRemote && !ClientInput.local(p)) return;

        // Wall-running: sprint + airborne + pressing into a wall + holding jump.
        if (p.onGround) { st[1] = 0; st[2] = 1; st[3] = 0; st[4] = 1; }
        if (st[5] > 0) st[5]--;
        canopy(p);
        boolean jump = jumpHeld(p);

        // Ledge grab: airborne against a wall whose top is just out of jump reach, holding jump.
        if (Config.ledgeGrab && st[4] == 1 && !p.onGround && p.collidedHorizontally && jump && p.motionY < 0.25 && !p.capabilities.isFlying && !p.isInWater()) {
            double top = ledgeTop(p);
            if (!Double.isNaN(top) && ImmersionMath.ledgeReach(top - p.posY)) {
                st[4] = 0;
                if (server && !ChakraCost.pay(p, Config.ledgeCost)) return;
                p.motionY = ImmersionMath.ledgeLift(top - p.posY);
                EnumFacing f = p.getHorizontalFacing();
                p.motionX += f.getXOffset() * 0.12; p.motionZ += f.getZOffset() * 0.12;
                p.fallDistance = 0;
                st[5] = 10;
                return;
            }
        }
        if (st[2] == 1 && ImmersionMath.wallRun(Config.wallRun, p.isSprinting(), p.onGround, p.collidedHorizontally, st[1], jump)) {
            if (server && st[3] == 0) { if (!ChakraCost.pay(p, Config.wallCost)) { st[2] = 0; return; } st[3] = 1; }
            p.motionY = Math.max(p.motionY, ImmersionMath.wallLift(st[1]));
            p.fallDistance = 0;
            st[1]++;
        } else if (st[1] > 0 && !p.collidedHorizontally) {
            st[2] = 0;   // one run per jump
        }
    }

    /** Top of a climbable ledge in front of the player (world Y), or NaN. Needs two free blocks above it. */
    private static double ledgeTop(EntityPlayer p) {
        EnumFacing f = p.getHorizontalFacing();
        BlockPos front = new BlockPos(p.posX + f.getXOffset() * 0.8, p.posY, p.posZ + f.getZOffset() * 0.8);
        BlockPos head = new BlockPos(p.posX, p.posY, p.posZ);
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos b = front.up(dy);
            IBlockState s = p.world.getBlockState(b);
            if (!s.getMaterial().blocksMovement()) continue;
            if (p.world.getBlockState(b.up()).getMaterial().blocksMovement() || p.world.getBlockState(b.up(2)).getMaterial().blocksMovement()) continue;
            for (int h = 2; h <= dy + 2; h++) if (p.world.getBlockState(head.up(h)).getMaterial().blocksMovement()) return Double.NaN;
            net.minecraft.util.math.AxisAlignedBB box = s.getCollisionBoundingBox(p.world, b);
            return b.getY() + (box == null ? 1 : box.maxY);
        }
        return Double.NaN;
    }

    /** Small sprint boost on leaves and rooftops (stairs/slabs), as a non-saved attribute modifier. */
    private static void canopy(EntityPlayer p) {
        IAttributeInstance speed = p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean on = false;
        if (Config.canopyRun && p.isSprinting() && p.onGround && !p.isInWater()) {
            IBlockState below = p.world.getBlockState(new BlockPos(p.posX, p.posY - 0.2, p.posZ));
            on = below.getMaterial() == Material.LEAVES || below.getBlock() instanceof BlockStairs || below.getBlock() instanceof BlockSlab;
        }
        boolean has = speed.getModifier(CANOPY) != null;
        if (on && !has) speed.applyModifier(new AttributeModifier(CANOPY, "Shinobi canopy run", ImmersionMath.CANOPY_BOOST, 2).setSaved(false));
        else if (!on && has) speed.removeModifier(CANOPY);
    }

    static int ledgeAge(EntityPlayer p) { int[] st = state(p).get(p.getUniqueID()); return st == null ? 0 : st[5]; }

    /** Jump input on the client; on the server, a rising player counts as holding jump. */
    private static boolean jumpHeld(EntityPlayer p) {
        if (p.world.isRemote) return ClientInput.jump();
        return p.motionY > -0.2;
    }

    static int wallTicks(EntityPlayer p) { int[] st = state(p).get(p.getUniqueID()); return st == null ? 0 : st[1]; }
    static int waterTicks(EntityPlayer p) { int[] st = state(p).get(p.getUniqueID()); return st == null ? 0 : st[0]; }
}
