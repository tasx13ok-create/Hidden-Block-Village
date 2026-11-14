package village.vale;

import village.vale.bridge.ChakraLedger;
import village.vale.bridge.ChakraLedger.Receipt;
import village.vale.bridge.ChakraLedger.Result;
import village.vale.bridge.ChakraPool;

/** Adversarial checks for the chakra reserve/settle rules against a fake pool with the observed semantics. */
public final class ChakraLedgerTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }

    /** Fake with the observed public semantics: absolute double delta; refuses results <= 0; over-cap flag limits refunds above max. */
    static final class Fake implements ChakraPool {
        double amount, max; final Object id = new Object(); boolean throwAfter, stuck;
        Fake(double a, double m) { amount = a; max = m; }
        public double amount() { return amount; }
        public double max() { return max; }
        public boolean consume(double d, boolean over) throws Exception {
            if (stuck) return false;
            double next = amount - d;
            if (d > 0 && next <= 0) return false;
            if (d < 0 && !over && next > max) next = Math.max(amount, max);
            boolean changed = next != amount; amount = next;
            if (throwAfter) throw new IllegalStateException("callback threw after debit");
            return changed;
        }
        public void clear() { amount = 0; }
        public Object identity() { return id; }
    }

    static double near(double a) { return Math.round(a * 1e6) / 1e6; }

    public static void main(String[] args) {
        Fake f = new Fake(100, 200);
        Receipt r = ChakraLedger.reserve(f, 30);
        check(r.result == Result.OK && near(f.amount) == 70 && near(r.debit) == 30 && r.before == 100, "positive absolute debit");
        check(ChakraLedger.commit(r) && !ChakraLedger.commit(r), "commit settles exactly once");
        check(ChakraLedger.refund(r, f) == 0 && near(f.amount) == 70, "no refund after commit");

        f = new Fake(25, 200); r = ChakraLedger.reserve(f, 25);
        check(r.result == Result.OK && f.amount == 0, "exact balance spends via clear to verified zero");
        check(near(ChakraLedger.refund(r, f)) == 25 && near(f.amount) == 25, "exact-zero spend refunds its debit");

        f = new Fake(10, 200);
        check(ChakraLedger.reserve(f, 11).result == Result.INSUFFICIENT && f.amount == 10, "insufficient spends nothing");
        check(ChakraLedger.reserve(f, 0).result == Result.ZERO_COST && f.amount == 10, "zero cost is a legitimate no-op");
        for (double bad : new double[] {-5, Double.NaN, Double.POSITIVE_INFINITY}) check(ChakraLedger.reserve(f, bad).result == Result.INVALID_COST && f.amount == 10, "rejects cost " + bad);
        check(ChakraLedger.reserve(null, 5).result == Result.UNREADY, "no pool (non-ninja/unready) fails closed");
        check(ChakraLedger.reserve(new Fake(Double.NaN, 100), 5).result == Result.UNREADY && ChakraLedger.reserve(new Fake(50, 0), 5).result == Result.UNREADY, "bad live values fail closed");

        f = new Fake(100, 200); r = ChakraLedger.reserve(f, 40);
        f.amount -= 20;                                              // another native cast spent meanwhile
        check(near(ChakraLedger.refund(r, f)) == 40 && near(f.amount) == 80, "refund is additive and preserves independent spending");

        f = new Fake(100, 200); r = ChakraLedger.reserve(f, 40);
        f.amount += 30;                                              // native regeneration meanwhile
        check(near(ChakraLedger.refund(r, f)) == 10 && near(f.amount) == 100, "refund never exceeds the pre-balance");

        f = new Fake(110, 100); r = ChakraLedger.reserve(f, 10);
        check(r.result == Result.OK && near(f.amount) == 100, "debit from an over-cap balance");
        check(near(ChakraLedger.refund(r, f)) == 10 && near(f.amount) == 110, "over-cap refund restores exactly the recorded debit");

        f = new Fake(399, 100); r = ChakraLedger.reserve(f, 5);
        check(r.result == Result.OK, "debit near overfill");
        double back = ChakraLedger.refund(r, f);
        check(f.amount < 400 && back <= 5, "refund stays below the native overfill zone");

        f = new Fake(100, 200); r = ChakraLedger.reserve(f, 20);
        Fake newBody = new Fake(f.amount, 200);
        check(ChakraLedger.refund(r, newBody) == 0 && near(newBody.amount) == 80, "replaced body/pathway forfeits the receipt");

        f = new Fake(100, 200); f.throwAfter = true; r = ChakraLedger.reserve(f, 20);
        check(r.result == Result.UNKNOWN_STATE && !r.paid() && near(f.amount) == 80, "throw after debit is recorded as unknown, not retried");

        f = new Fake(100, 200); f.stuck = true; r = ChakraLedger.reserve(f, 20);
        check(r.result == Result.FAILED && f.amount == 100, "a call that changes nothing is a failure, not a payment");

        f = new Fake(50, 200); r = ChakraLedger.reserve(f, 10);
        check(near(ChakraLedger.refund(r, f)) == 10 && ChakraLedger.refund(r, f) == 0 && near(f.amount) == 50, "refund settles exactly once");
        check(ChakraLedger.refund(null, f) == 0 && !ChakraLedger.commit(null), "null receipts are inert");
        System.out.println("PASS: " + checks + " chakra ledger checks (debit, exact zero, insufficient, zero/invalid cost, additive/bounded/over-cap refunds, body replacement, throw-after-debit, settle-once).");
    }
}
