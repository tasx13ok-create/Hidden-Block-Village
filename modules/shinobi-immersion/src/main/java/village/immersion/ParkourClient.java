package village.immersion;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Local wall movement controller. One state at a time: run up a wall you face (with an automatic
 * ledge pull-up at the top), run along a wall beside you, or slide down a wall you press into.
 * Jump off any of them to wall-jump; chain up to {@code maxWallJumps} jumps between walls before
 * landing. A jump pressed just before touching a wall, or a dodge into it, counts as the start.
 * A wall can't be run twice in one airtime, so there is no infinite climbing.
 *
 * Also detects Core's dodge (its speed burst) for the dodge feel: a dodge cancels a wall state,
 * flows into a wall run, and a jump right after a ground dodge keeps its momentum (dodge hop).
 */
@SideOnly(Side.CLIENT)
final class ParkourClient {
    private static int state = ParkourMath.NONE, stateTicks, wallJumps, lastState;
    private static int nx, nz, lastNx, lastNz, usedNx, usedNz;
    private static boolean usedWall, endedByJump, lastJumpDown, dodgeGround;
    private static long lastKeepAlive, tick, jumpPressAt = -100, dodgeAt = -100, lastEndAt = -100, lastWallJumpAt = -100, deniedUntil, endedAt = -100, startedAt = -100;
    private static double lastSpeed, dodgePeak, hopTarget;
    private static boolean hopPending;
    private static int side;

    // ---- read by ParkourFx ----
    static int state() { return state; }
    static int side() { return side; }
    static int ticksIn() { return (int) (tick - startedAt); }
    static int sinceEnd() { return state != ParkourMath.NONE ? -1 : (int) (tick - endedAt); }
    static long dodgeTick() { return dodgeAt; }
    static long now() { return tick; }

    static void deny() {
        Minecraft mc = Minecraft.getMinecraft();
        if (state != ParkourMath.NONE) end(false);
        deniedUntil = tick + 20;
        if (mc.player != null) mc.player.sendStatusMessage(new TextComponentString("§cNot enough chakra"), true);
    }

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !e.player.world.isRemote || !ClientInput.local(e.player)) return;
        EntityPlayerSP p = (EntityPlayerSP) e.player;
        Minecraft mc = Minecraft.getMinecraft();
        tick++;
        boolean jumpDown = mc.gameSettings.keyBindJump.isKeyDown() && mc.currentScreen == null;
        boolean jumpPressed = jumpDown && !lastJumpDown;
        lastJumpDown = jumpDown;
        if (jumpPressed) jumpPressAt = tick;

        double speed = Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ);
        if (hopPending && !p.onGround) {
            hopPending = false;
            if (speed > 1e-4 && speed < hopTarget) { double k = hopTarget / speed; p.motionX *= k; p.motionZ *= k; speed = hopTarget; }
        } else if (hopPending && tick - dodgeAt > ParkourMath.HOP_WINDOW + 2) hopPending = false;
        if (state != ParkourMath.NONE && tick - lastKeepAlive >= 20) { lastKeepAlive = tick; send(state, nx, nz, 2); }

        if (p.onGround || p.isInWater() || p.isInLava() || p.capabilities.isFlying || p.isRiding() || p.isElytraFlying() || p.isOnLadder() || p.isSpectator()) {
            if (state != ParkourMath.NONE) end(false);
            wallJumps = 0; usedWall = false;
            return;
        }

        double yaw = Math.toRadians(p.rotationYaw), lx = -Math.sin(yaw), lz = Math.cos(yaw);
        boolean forward = p.movementInput.moveForward > 0.1f;
        int[] w = wall(p, lx, lz);

        if (state == ParkourMath.NONE) {
            // Wall jump just after leaving a wall (coyote time).
            if (jumpPressed && Config.wallJump && !endedByJump && tick - lastEndAt <= ParkourMath.COYOTE && lastState != ParkourMath.NONE && wallJumps < Config.maxWallJumps) {
                wallJump(p, lastState, lastNx, lastNz, lx, lz);
                return;
            }
            if (w == null || tick < deniedUntil || tick - lastWallJumpAt < ParkourMath.REENTRY) return;
            boolean same = usedWall && ParkourMath.sameWall(w[0], w[1], usedNx, usedNz);
            double facing = ParkourMath.facing(lx, lz, w[0], w[1]);
            double along = ParkourMath.along(p.motionX, p.motionZ, w[0], w[1]);
            boolean recentDodge = tick - dodgeAt <= 4;
            boolean trigger = tick - jumpPressAt <= ParkourMath.BUFFER || recentDodge;
            boolean momentum = p.isSprinting() || recentDodge || speed > 0.2;
            int s = ParkourMath.choose(true, forward, jumpDown, trigger, facing, along, p.motionY < -0.1, forward && facing > 0.3);
            if (s == ParkourMath.RUN_UP && (!Config.wallRun || !momentum || same)) s = ParkourMath.NONE;
            if (s == ParkourMath.RUN_SIDE && (!Config.wallSideRun || !momentum || same)) s = ParkourMath.NONE;
            if (s == ParkourMath.SLIDE && !Config.wallSlide) s = ParkourMath.NONE;
            if (s != ParkourMath.NONE) begin(p, s, w, lx, lz);
            return;
        }

        stateTicks++;
        if (jumpPressed && Config.wallJump && wallJumps < Config.maxWallJumps && stateTicks > 1) { wallJump(p, state, nx, nz, lx, lz); return; }
        boolean onSameWall = w != null && ParkourMath.sameWall(w[0], w[1], nx, nz);
        switch (state) {
            case ParkourMath.RUN_UP: {
                double top = Movement.ledgeTop(p);
                if (!Double.isNaN(top) && top - p.posY > 0.2 && top - p.posY <= 2.6) { ledge(p, top); return; }
                if (!onSameWall || !forward || !jumpDown || stateTicks >= ParkourMath.RUN_UP_TICKS) { end(false); return; }
                p.motionY = Math.max(p.motionY, ParkourMath.runUpLift(stateTicks));
                p.motionX = -nx * 0.06; p.motionZ = -nz * 0.06;   // hug the wall
                break;
            }
            case ParkourMath.RUN_SIDE: {
                if (!onSameWall || !forward || p.isSneaking() || stateTicks >= ParkourMath.RUN_SIDE_TICKS) { end(false); return; }
                double[] v = ParkourMath.alongWall(p.motionX, p.motionZ, nx, nz, lx, lz, 0.28);
                p.motionX = v[0]; p.motionZ = v[1];
                p.motionY = Math.max(p.motionY, ParkourMath.sideFloor(stateTicks));
                break;
            }
            default: {   // SLIDE
                if (!onSameWall || !(forward && ParkourMath.facing(lx, lz, nx, nz) > 0.2) && p.movementInput.moveForward <= 0) { end(false); return; }
                p.motionY = Math.max(p.motionY, ParkourMath.SLIDE_FALL);
            }
        }
        p.fallDistance = 0;
    }

    /**
     * Core's dodge arrives as a velocity packet; read it before movement and friction run, so the
     * burst is seen at full strength (after a ground tick it has already lost almost half).
     */
    @SubscribeEvent public void before(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.START || !e.player.world.isRemote || !ClientInput.local(e.player)) return;
        EntityPlayerSP p = (EntityPlayerSP) e.player;
        double speed = Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ);
        if (ImmersionMath.dodgeBurst(speed, lastSpeed)) {
            dodgeAt = tick; dodgePeak = speed; dodgeGround = p.onGround;
            DodgeFx.confirmed(p);
            if (state != ParkourMath.NONE) end(false);
        }
        lastSpeed = speed;
    }

    /** Jump right after a ground dodge keeps the dodge's momentum. */
    @SubscribeEvent public void jump(LivingEvent.LivingJumpEvent e) {
        if (!Config.dodgeHop || !(e.getEntity() instanceof EntityPlayerSP) || !ClientInput.local((EntityPlayerSP) e.getEntity())) return;
        if (!dodgeGround || tick - dodgeAt > ParkourMath.HOP_WINDOW) return;
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        double h = Math.sqrt(p.motionX * p.motionX + p.motionZ * p.motionZ);
        if (h < 1e-4) return;
        // Ground friction still runs on the jump tick, so the speed is set on the first airborne tick.
        hopTarget = ParkourMath.hopSpeed(dodgePeak, h);
        hopPending = true;
        dodgeGround = false;
        DodgeFx.hop(p);
    }

    private static void begin(EntityPlayerSP p, int s, int[] w, double lx, double lz) {
        state = s; stateTicks = 0; nx = w[0]; nz = w[1]; startedAt = tick;
        usedWall = true; usedNx = nx; usedNz = nz;
        side = s == ParkourMath.RUN_SIDE ? ParkourMath.side(lx, lz, nx, nz) : 0;
        Parkour.localState = s;
        if (s == ParkourMath.RUN_UP) p.motionY = Math.max(p.motionY, ParkourMath.runUpLift(0));
        if (s == ParkourMath.RUN_SIDE) p.motionY = Math.max(p.motionY, ParkourMath.sideFloor(0));
        if (s == ParkourMath.SLIDE) p.motionY = Math.max(p.motionY, ParkourMath.SLIDE_FALL);
        lastKeepAlive = tick;
        p.fallDistance = 0;
        send(s, nx, nz, 0);
        ParkourFx.contact(p, s, nx, nz);
    }

    private static void end(boolean byJump) {
        lastState = state; lastNx = nx; lastNz = nz; lastEndAt = tick; endedByJump = byJump; endedAt = tick;
        state = ParkourMath.NONE; stateTicks = 0;
        Parkour.localState = ParkourMath.NONE;
        if (!byJump) send(ParkourMath.NONE, nx, nz, 0);
    }

    private static void wallJump(EntityPlayerSP p, int from, int wx, int wz, double lx, double lz) {
        double[] v = ParkourMath.wallJump(from, wx, wz, lx, lz);
        p.motionX = v[0]; p.motionY = v[1]; p.motionZ = v[2];
        p.fallDistance = 0;
        wallJumps++;
        lastWallJumpAt = tick;
        if (state != ParkourMath.NONE) end(true); else endedByJump = true;
        send(from, wx, wz, 1);
        ParkourFx.wallJump(p, wx, wz);
    }

    private static void ledge(EntityPlayerSP p, double top) {
        p.motionY = ImmersionMath.ledgeLift(Math.max(1.2, top - p.posY));
        p.motionX = -nx * 0.16; p.motionZ = -nz * 0.16;
        p.fallDistance = 0;
        end(false);
        ParkourFx.ledge(p);
    }

    private static void send(int s, int x, int z, int event) { if (Net.CH != null) Net.CH.sendToServer(new Net.Wall(s, x, z, event)); }

    /**
     * The wall touching the player's body: {nx, nz} pointing out of the wall, or null. When two
     * sides touch (a corner) the one the player faces most wins.
     */
    static int[] wall(EntityPlayerSP p, double lx, double lz) {
        AxisAlignedBB b = p.getEntityBoundingBox();
        AxisAlignedBB body = new AxisAlignedBB(b.minX, b.minY + 0.4, b.minZ, b.maxX, b.maxY - 0.2, b.maxZ);
        int[] best = null; double bestScore = -9;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            if (p.world.getCollisionBoxes(p, body.offset(d[0] * 0.3, 0, d[1] * 0.3)).isEmpty()) continue;
            int wx = -d[0], wz = -d[1];
            double score = ParkourMath.facing(lx, lz, wx, wz);
            if (score > bestScore) { bestScore = score; best = new int[] {wx, wz}; }
        }
        return best;
    }

    /** The block of the wall the player is on, for its dust and step sounds. */
    static IBlockState wallBlock(net.minecraft.entity.player.EntityPlayer p, int wx, int wz) {
        return p.world.getBlockState(new BlockPos(p.posX - wx * 0.8, p.posY + 1, p.posZ - wz * 0.8));
    }
}
