package village.vale.client;

import java.util.ArrayList;
import java.util.List;

/** Isolated Java 8 checks of actual scope, native-tick and body-identity behavior. */
public final class VoxelRulesChecks {
    private static int assertions, groups;
    private static final List<String> failures = new ArrayList<>();
    private static void check(boolean ok, String label) {
        assertions++;
        if (!ok) failures.add(label);
    }
    private static void run(String name, Runnable body) {
        groups++;
        try { body.run(); } catch (Throwable failure) { failures.add(name + " threw " + failure); }
    }
    private static final class EqualBody {
        final int number;
        EqualBody(int number) { this.number = number; }
        @Override public boolean equals(Object other) { return other instanceof EqualBody; }
        @Override public int hashCode() { return 1; }
    }

    private static void framesRestoreNestedScope() {
        VoxelRules.Frames f = new VoxelRules.Frames();
        check(!f.voxel() && f.remaining() == 0, "fresh frame has no mode or budget");
        f.voxel(true);
        check(!f.voxel() && !f.use(0) && !f.use(1), "outside-scope mutations cannot admit drawing");
        f.pop();
        f.push(13); f.voxel(true);
        check(f.use(5) && f.remaining() == 8, "outer scope spends its own budget");
        f.push(4);
        check(!f.voxel() && f.remaining() == 4, "nested scope starts with independent mode and budget");
        f.voxel(true);
        check(f.use(4) && !f.use(1), "inner scope exhausts independently");
        f.push(3); f.voxel(false); f.use(2);
        check(!f.voxel() && f.remaining() == 1, "third scope remains independent");
        f.pop();
        check(f.voxel() && f.remaining() == 0, "third pop restores inner mode and depleted budget");
        f.pop();
        check(f.voxel() && f.remaining() == 8, "inner pop restores outer budget without leaking its spend");
        check(!f.use(-1) && !f.use(9) && f.remaining() == 8, "invalid spends cannot corrupt outer budget");
        check(f.use(0) && f.use(8) && f.remaining() == 0, "outer remaining budget is usable exactly once");
        f.pop(); f.pop(); f.voxel(true);
        check(!f.voxel() && f.remaining() == 0 && !f.use(0), "scope exit removes all mode/budget state");
        f.push(Integer.MIN_VALUE);
        check(f.remaining() == 0 && !f.use(1), "negative frame request cannot create capacity");
        f.pop(); f.push(Integer.MAX_VALUE);
        check(!f.use(2049) && f.use(2048) && !f.use(1), "oversized frame request obeys hard draw cap");
        f.pop(); f.push(7); f.voxel(true);
        try { f.push(2); f.voxel(false); throw new IllegalStateException("synthetic render unwind"); }
        catch (IllegalStateException expected) { /* caller finally must unwind the nested frame */ }
        finally { f.pop(); }
        check(f.voxel() && f.remaining() == 7, "finally-style nested unwind restores parent scope");
        f.pop();
    }

    private static void epochQuotasFollowNativeTicks() {
        VoxelRules.Epoch e = new VoxelRules.Epoch();
        Object w = new Object(), b = new Object(), c = new Object();
        check(!e.admit(true) && !e.admit(false), "unprepared epoch cannot admit either particle lane");
        check(e.prepare(w,b,c,50) && e.context(w,b,c) && e.delta() == 1, "first native tick establishes identity and one update");
        long v = e.version();
        for (int i=0;i<16;i++) check(e.admit(true), "armor lane admits native-tick emitter " + i);
        check(!e.admit(true), "seventeenth armor emitter rejected");
        for (int i=0;i<64;i++) check(e.admit(false), "weapon lane independent native-tick emitter " + i);
        check(!e.admit(false), "sixty-fifth weapon emitter rejected");
        check(e.prepare(w,b,c,50) && e.version() == v && !e.admit(true) && !e.admit(false),
              "extra render frame at same native tick cannot replenish either lane");
        check(e.prepare(w,b,c,51) && e.delta() == 1 && e.version() == v, "next native tick advances without creating a context");
        check(e.admit(true) && e.admit(false), "next native tick replenishes both lanes");
        check(e.prepare(w,b,c,53) && e.delta() == 2, "two elapsed native ticks retain bounded catchup");
        check(e.prepare(w,b,c,Integer.MAX_VALUE) && e.delta() == 4, "large native-tick gap catches up at most four updates without overflow");
        check(e.prepare(w,b,c,Integer.MAX_VALUE) && e.delta() == 4, "same tick does not recalculate or replenish catchup");
        long beforeRewind = e.version();
        check(e.prepare(w,b,c,3) && e.delta() == 1 && e.version() == beforeRewind + 1,
              "native tick rewind creates a clean epoch with no negative catchup");
        check(e.admit(true) && e.admit(false), "rewind resets both admission lanes");
    }

    private static void epochContextIdentityAndInvalidation() {
        VoxelRules.Epoch e = new VoxelRules.Epoch();
        Object[] context = {new EqualBody(1), new EqualBody(2), new EqualBody(3)};
        check(e.prepare(context[0],context[1],context[2],8), "initial equal-valued objects establish context");
        for (int slot=0;slot<3;slot++) {
            Object replacement = new EqualBody(10+slot);
            check(context[slot].equals(replacement) && context[slot] != replacement, "fixture has equal but distinct context objects");
            for (int i=0;i<16;i++) e.admit(true);
            for (int i=0;i<64;i++) e.admit(false);
            Object[] changed = context.clone(); changed[slot] = replacement;
            check(!e.context(changed[0],changed[1],changed[2]), "world/body/connection identity mismatch detected at slot " + slot);
            long version = e.version();
            check(e.prepare(changed[0],changed[1],changed[2],8) && e.version() == version+1 && e.delta() == 1,
                  "direct context replacement starts new epoch at slot " + slot);
            check(e.admit(true) && e.admit(false), "context replacement resets admission at slot " + slot);
            context = changed;
        }
        for (int slot=0;slot<3;slot++) {
            Object[] absent = context.clone(); absent[slot] = null;
            long version = e.version();
            check(!e.prepare(absent[0],absent[1],absent[2],9), "missing world/body/connection refuses prepare at slot " + slot);
            check(e.version() == version+1 && e.delta() == 0 && !e.context(context[0],context[1],context[2]),
                  "missing context clears previous identity and catchup at slot " + slot);
            check(!e.admit(true) && !e.admit(false), "missing context blocks both lanes at slot " + slot);
            check(!e.prepare(absent[0],absent[1],absent[2],9) && e.version() == version+1,
                  "repeated absent context does not repeatedly version-bump at slot " + slot);
            check(e.prepare(context[0],context[1],context[2],9) && e.delta() == 1,
                  "restored context starts clean at slot " + slot);
        }
        check(!e.prepare(context[0],context[1],context[2],-1) && !e.admit(false) && e.delta() == 0,
              "negative native tick invalidates context without admission");
    }

    private static void receiptsIdentityCapPruneAndReuse() {
        VoxelRules.Receipts<EqualBody> r = new VoxelRules.Receipts<>();
        EqualBody first = new EqualBody(0), equal = new EqualBody(1);
        check(!r.take(null) && r.size() == 0, "null body creates no receipt");
        check(first.equals(equal) && first != equal, "receipt equality fixture is intentionally distinct");
        check(r.take(first) && !r.take(first) && r.take(equal) && r.size() == 2,
              "same identity cannot repeat; equal-valued different bodies can each receive");
        EqualBody absent = new EqualBody(2);
        check(r.contains(first) && r.contains(equal) && !r.contains(absent) && !r.contains(null) && r.size()==2,
              "readonly contains preserves body identity and cannot admit an absent equal-valued body");
        r.clear();
        EqualBody[] bodies = new EqualBody[513];
        for (int i=0;i<bodies.length;i++) bodies[i] = new EqualBody(i);
        for (int i=0;i<512;i++) check(r.take(bodies[i]), "distinct body admitted before receipt cap " + i);
        check(!r.take(bodies[512]) && r.size() == 512, "capacity rejects new body without replacing old receipts");
        check(!r.take(bodies[0]) && r.size() == 512, "full capacity preserves existing identity receipt");
        r.retain(body -> body.number % 2 == 0);
        check(r.size() == 256 && !r.take(bodies[0]), "prune retains valid identity receipts");
        check(r.contains(bodies[0]) && !r.contains(bodies[1]) && !r.contains(bodies[512]) && r.size()==256,
              "readonly membership reflects pruning without spending freed capacity");
        for (int i=1;i<512;i+=2) check(r.take(bodies[i]), "pruned body identity can be reused " + i);
        check(r.size() == 512 && !r.take(bodies[512]), "reuse remains bounded after pruning");
        r.retain(body -> false);
        check(r.size() == 0 && r.take(bodies[512]), "prune frees capacity for previously rejected body");
        r.clear();
        check(r.size() == 0 && r.take(first) && r.take(equal), "disconnect-style clear permits fresh body receipts");
    }

    public static void main(String[] args) {
        run("frames", VoxelRulesChecks::framesRestoreNestedScope);
        run("native ticks", VoxelRulesChecks::epochQuotasFollowNativeTicks);
        run("contexts", VoxelRulesChecks::epochContextIdentityAndInvalidation);
        run("receipts", VoxelRulesChecks::receiptsIdentityCapPruneAndReuse);
        for (String failure : failures) System.err.println("FAIL: " + failure);
        System.out.println("VoxelRulesChecks: " + assertions + " assertions, " + groups + " groups, " + failures.size() + " failures");
        if (!failures.isEmpty()) throw new AssertionError("VoxelRules failures: " + failures.size());
    }
}
