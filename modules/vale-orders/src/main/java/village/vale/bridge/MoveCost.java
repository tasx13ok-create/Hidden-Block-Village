package village.vale.bridge;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import village.vale.PlayerState;

/**
 * Facade for paying add-on move costs.
 *   Ticket t = MoveCost.reserve(player, cost);   // null = cannot pay (or busy); nothing spent
 *   ... run the move ...
 *   t.commit();  // accepted irreversible gameplay
 *   t.refund();  // canceled/rejected attempt (refunds only this receipt's debit, once)
 *   t.close();   // safety: refunds if neither was called
 * With NarutoMod READY, cost units become absolute chakra via {@code chakraPerUnit}. With NarutoMod
 * genuinely ABSENT, the Vale resolve fallback is used. UNSUPPORTED or a non-ninja fails closed.
 * Native NarutoMod casts pay their own costs and must never go through here.
 */
public final class MoveCost {
    private MoveCost() {}
    private static final Set<UUID> BUSY = Collections.synchronizedSet(new java.util.HashSet<>());

    public static final class Ticket implements AutoCloseable {
        private final EntityPlayerMP player;
        private final ChakraLedger.Receipt receipt;
        private final int resolveDebit;
        private boolean settled;
        Ticket(EntityPlayerMP p, ChakraLedger.Receipt r, int resolve) { player = p; receipt = r; resolveDebit = resolve; }
        public boolean chakra() { return receipt != null; }
        public double debit() { return receipt != null ? receipt.debit : resolveDebit; }
        public void commit() {
            if (settled) return;
            settled = true;
            if (receipt != null) ChakraLedger.commit(receipt);
            BUSY.remove(player.getUniqueID());
        }
        public void refund() {
            if (settled) return;
            settled = true;
            try {
                if (receipt != null) {
                    // Only the same live body/pathway can be refunded; a replaced body forfeits the receipt.
                    ChakraPool pool = player.isEntityAlive() && player.connection != null ? ChakraLink.pool(player) : null;
                    ChakraLedger.refund(receipt, pool);
                } else if (resolveDebit > 0) {
                    PlayerState.setResolve(player, Math.min(100, PlayerState.resolve(player) + resolveDebit));
                }
            } finally { BUSY.remove(player.getUniqueID()); }
        }
        @Override public void close() { if (!settled) refund(); }
    }

    /** Converts an add-on cost to absolute chakra (explicit scale; NarutoMod is not 0..100). */
    public static double chakraFor(int cost) { return cost <= 0 ? 0 : cost * Bridge.chakraPerUnit; }

    public static Ticket reserve(EntityPlayerMP p, int cost) {
        if (p == null || cost < 0 || p.world.isRemote) return null;
        UUID id = p.getUniqueID();
        if (!BUSY.add(id)) return null;                         // bounded per-player reentry guard
        boolean handedOut = false;
        try {
            if (cost == 0) { handedOut = true; return new Ticket(p, null, 0); }   // free moves never depend on a provider
            ChakraLink.Status s = ChakraLink.state();
            if (s == ChakraLink.Status.READY) {
                ChakraLedger.Receipt r = ChakraLedger.reserve(ChakraLink.pool(p), chakraFor(cost));
                if (r.result != ChakraLedger.Result.OK) {
                    if (r.result == ChakraLedger.Result.UNKNOWN_STATE) System.out.println("[naruto bridge] chakra debit for " + p.getName() + " ended in an unknown state (before " + r.before + ", observed debit " + r.debit + "); not retried");
                    return null;
                }
                handedOut = true;
                return new Ticket(p, r, 0);
            }
            if (s != ChakraLink.Status.ABSENT) return null;     // installed but unsupported: fail closed
            int have = PlayerState.resolve(p);
            if (have < cost) return null;
            if (cost > 0) PlayerState.setResolve(p, have - cost);
            handedOut = true;
            return new Ticket(p, null, cost);
        } finally { if (!handedOut) BUSY.remove(id); }
    }

    /** Compatibility shortcut for already irreversible actions: reserve and commit at once. */
    public static boolean pay(EntityPlayerMP p, int cost) {
        Ticket t = reserve(p, cost);
        if (t == null) return false;
        t.commit();
        return true;
    }

    /** Units the player could spend now (for HUD/affordability), or -1 when unknown. */
    public static double availableUnits(EntityPlayerMP p) {
        if (ChakraLink.state() == ChakraLink.Status.READY) {
            double[] v = ChakraLink.view(p);
            return v == null || Bridge.chakraPerUnit <= 0 ? -1 : v[0] / Bridge.chakraPerUnit;
        }
        return ChakraLink.absent() ? PlayerState.resolve(p) : -1;
    }
}
