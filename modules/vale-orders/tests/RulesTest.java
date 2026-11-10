package village.vale;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RulesTest {
    private static int checks;
    private static void equal(long expected, long actual) {
        checks++; if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        equal(0, Rules.clampResolve(-50)); equal(100, Rules.clampResolve(500));
        equal(1, Rules.levelForXp(-1)); equal(1, Rules.levelForXp(99));
        equal(2, Rules.levelForXp(100)); equal(2, Rules.levelForXp(399));
        equal(3, Rules.levelForXp(400)); equal(20, Rules.levelForXp(Integer.MAX_VALUE));
        // An exact-cost skill succeeds; insufficient resolve is never converted into free activation.
        equal(0, Rules.spendResolve(25, 25)); equal(-1, Rules.spendResolve(24, 25));
        equal(75, Rules.spendResolve(500, 25));
        boolean invalid=false;try {Rules.spendResolve(100,0);}catch(IllegalArgumentException ex){invalid=true;}
        equal(1, invalid?1:0);
        equal(1, Rules.cooldownSeconds(100,101)); equal(1, Rules.cooldownSeconds(100,120));
        equal(2, Rules.cooldownSeconds(100,121)); equal(0, Rules.cooldownSeconds(121,121));
        equal(0, Rules.cooldownSeconds(500,100));
        Map<String,String> receipts=new LinkedHashMap<>();
        equal(1,Rules.recordReceipt(receipts,"opening-1","player-a/ember_saber/1")?1:0);
        equal(0,Rules.recordReceipt(receipts,"opening-1","player-a/ember_saber/1")?1:0);
        Map<String,String> restored=new LinkedHashMap<>(receipts);
        equal(0,Rules.recordReceipt(restored,"opening-1","player-a/ember_saber/1")?1:0);
        invalid=false;
        try {Rules.recordReceipt(restored,"opening-1","player-b/ember_saber/1");}
        catch(IllegalArgumentException ex){invalid=true;}
        equal(1,invalid?1:0);equal(1,restored.size());
        equal(1,Rules.recordReceipt(restored,"opening-2","player-a/ember_saber/1")?1:0);
        System.out.println("PASS: " + checks + " rule and receipt checks. Not a Forge runtime test.");
    }
}
