package village.immersion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Server side of the wall movement system. The owning client moves itself (as vanilla does) and
 * reports each wall state and wall jump; the server checks there really is a wall beside the
 * player, takes the chakra through Core's MoveCost (food without Core), keeps fall distance fair
 * while the player is on a wall, and relays the state to nearby players for their effects.
 * When chakra runs out the client is told to drop off the wall.
 */
final class Parkour {
    private static final class Srv { int state; int nx, nz; long since, lastMsg, lastPay; int msgs; long msgWindow; }
    private static final Map<UUID, Srv> SERVER = new HashMap<>();
    /** The local player's state on this client (set by ParkourClient); remote players come from WallFx. */
    static volatile int localState = ParkourMath.NONE;
    static final Map<Integer, int[]> REMOTE = new HashMap<>();   // entityId -> {state, nx, nz, sinceTick}

    /** Current wall state of a player on whichever side asks. */
    static int state(EntityPlayer p) {
        if (!p.world.isRemote) { Srv s = SERVER.get(p.getUniqueID()); return s == null ? ParkourMath.NONE : s.state; }
        if (ClientInput.local(p)) return localState;
        int[] r = REMOTE.get(p.getEntityId());
        return r == null ? ParkourMath.NONE : r[0];
    }

    static void handle(EntityPlayerMP p, int state, int nx, int nz, int event) {
        if (!ParkourMath.valid(state) || Math.abs(nx) + Math.abs(nz) > 1 || !p.isEntityAlive() || p.isSpectator()) return;
        Srv s = SERVER.computeIfAbsent(p.getUniqueID(), k -> new Srv());
        long now = p.world.getTotalWorldTime();
        if (now - s.msgWindow >= 20) { s.msgWindow = now; s.msgs = 0; }
        if (++s.msgs > 12) return;   // a client sending more than this is not moving honestly
        s.lastMsg = now;
        if (event == 2) {   // still on the wall: keep the fall protection, no new entry cost
            if (s.state == state && state != ParkourMath.NONE && !wallNear(p)) deny(p, s);
            return;
        }
        if (event == 1) {   // wall jump
            if (!wallNear(p) || !ChakraCost.pay(p, Config.wallJumpCost)) { deny(p, s); return; }
            p.fallDistance = 0;
            set(p, s, ParkourMath.NONE, nx, nz, 1, now);
            return;
        }
        if (state == ParkourMath.NONE) { set(p, s, state, nx, nz, 0, now); return; }
        int cost = state == ParkourMath.RUN_UP ? Config.wallCost : state == ParkourMath.RUN_SIDE ? Config.sideRunCost : 0;
        if (!wallNear(p) || cost > 0 && !ChakraCost.pay(p, cost)) { deny(p, s); return; }
        s.lastPay = now;
        set(p, s, state, nx, nz, 0, now);
    }

    private static void set(EntityPlayerMP p, Srv s, int state, int nx, int nz, int event, long now) {
        if (s.state != state) s.since = now;
        s.state = state; s.nx = nx; s.nz = nz;
        if (Net.CH != null && p.world instanceof WorldServer)
            for (EntityPlayer o : ((WorldServer) p.world).getEntityTracker().getTrackingPlayers(p))
                if (o instanceof EntityPlayerMP) Net.CH.sendTo(new Net.WallFx(p.getEntityId(), state, nx, nz, event), (EntityPlayerMP) o);
    }

    private static void deny(EntityPlayerMP p, Srv s) {
        set(p, s, ParkourMath.NONE, s.nx, s.nz, 0, p.world.getTotalWorldTime());
        Net.cue(p, Net.PARKOUR_DENY, 0);
    }

    /** A solid surface within reach of the player's body on any side (a little looser than the client's probe). */
    static boolean wallNear(EntityPlayer p) {
        AxisAlignedBB b = p.getEntityBoundingBox();
        AxisAlignedBB probe = new AxisAlignedBB(b.minX - 0.6, b.minY + 0.2, b.minZ - 0.6, b.maxX + 0.6, b.maxY, b.maxZ + 0.6);
        return !p.world.getCollisionBoxes(p, probe).isEmpty();
    }

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof EntityPlayerMP)) return;
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        Srv s = SERVER.get(p.getUniqueID());
        if (s == null || s.state == ParkourMath.NONE) return;
        long now = p.world.getTotalWorldTime();
        if (now - s.lastMsg > 60 || p.onGround && now - s.since > 5 || p.isInWater() || p.isRiding()) { set(p, s, ParkourMath.NONE, s.nx, s.nz, 0, now); return; }
        if (s.state == ParkourMath.SLIDE) p.fallDistance = Math.min(p.fallDistance, 2f);
        else p.fallDistance = 0;
        if (s.state == ParkourMath.RUN_SIDE && now - s.lastPay >= 20) {   // a long run along a wall keeps costing
            s.lastPay = now;
            if (!ChakraCost.pay(p, 1)) deny(p, s);
        }
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) { SERVER.remove(e.player.getUniqueID()); }
}
