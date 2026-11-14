package village.vale.bridge;

/**
 * Pure reserve/settle rules for spending chakra (unit-tested without Minecraft).
 *
 * reserve: validates the cost, verifies a real debit postcondition (exact-zero via clear), and
 * returns a Receipt with the pre-balance and actual debit. settle once: commit keeps the debit;
 * refund adds back only the recorded debit (additive, so unrelated spending/regeneration in
 * between is preserved), never above the pre-balance and never into the native overfill zone.
 * A receipt whose pathway identity changed (death, clone, reconnect, dimension) refunds nothing.
 */
public final class ChakraLedger {
    private ChakraLedger() {}
    public static final double EPS = 1e-6;

    public enum Result { OK, ZERO_COST, INVALID_COST, INSUFFICIENT, UNREADY, FAILED, UNKNOWN_STATE }

    public static final class Receipt {
        public final Result result;
        public final Object identity;
        public final double before, debit;
        private boolean settled;
        Receipt(Result r, Object id, double before, double debit) { result = r; identity = id; this.before = before; this.debit = debit; settled = r != Result.OK; }
        public boolean paid() { return result == Result.OK || result == Result.ZERO_COST; }
        public boolean settled() { return settled; }
    }

    static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }

    public static Receipt reserve(ChakraPool pool, double cost) {
        if (!finite(cost) || cost < 0) return new Receipt(Result.INVALID_COST, null, Double.NaN, 0);
        if (cost == 0) return new Receipt(Result.ZERO_COST, pool == null ? null : pool.identity(), Double.NaN, 0);
        if (pool == null) return new Receipt(Result.UNREADY, null, Double.NaN, 0);
        double have, max;
        try { have = pool.amount(); max = pool.max(); } catch (Exception e) { return new Receipt(Result.FAILED, null, Double.NaN, 0); }
        if (!finite(have) || !finite(max) || max <= 0 || have < 0) return new Receipt(Result.UNREADY, pool.identity(), have, 0);
        if (have + EPS < cost) return new Receipt(Result.INSUFFICIENT, pool.identity(), have, 0);
        double after;
        try {
            if (Math.abs(have - cost) <= EPS) pool.clear();          // consume refuses results <= 0
            else pool.consume(cost, false);
            after = pool.amount();
        } catch (Exception e) {
            return new Receipt(Result.UNKNOWN_STATE, pool.identity(), have, 0);   // never retry or fall back after a possible mutation
        }
        double debit = have - after;
        if (!finite(after) || Math.abs(debit - cost) > Math.max(EPS, cost * 1e-9)) {
            // Anything other than the exact requested debit is recorded, not guessed or re-applied.
            return new Receipt(debit > EPS ? Result.UNKNOWN_STATE : Result.FAILED, pool.identity(), have, Math.max(0, debit));
        }
        return new Receipt(Result.OK, pool.identity(), have, debit);
    }

    /** Keeps an accepted debit. Returns false if already settled. */
    public static boolean commit(Receipt r) {
        if (r == null || r.settled) return false;
        r.settled = true;
        return true;
    }

    /** Refunds the recorded debit once on the same pathway. Returns the amount actually restored. */
    public static double refund(Receipt r, ChakraPool pool) {
        if (r == null || r.settled) return 0;
        r.settled = true;
        if (pool == null || r.debit <= 0 || pool.identity() != r.identity) return 0;
        try {
            double now = pool.amount(), max = pool.max();
            if (!finite(now) || !finite(max) || max <= 0) return 0;
            double give = Math.min(r.debit, Math.max(0, r.before - now));   // never exceed the original pre-balance
            give = Math.min(give, Math.max(0, 4 * max - now - EPS));          // stay clear of the native overfill zone
            if (give <= EPS) return 0;
            boolean overCap = now + give > max;                               // restoring a legitimate pre-existing over-cap balance
            pool.consume(-give, overCap);
            double after = pool.amount();
            return Math.max(0, after - now);
        } catch (Exception e) { return 0; }
    }
}
