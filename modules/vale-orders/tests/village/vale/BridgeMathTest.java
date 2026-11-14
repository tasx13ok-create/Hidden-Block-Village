package village.vale;

import village.vale.bridge.BridgeMath;

public final class BridgeMathTest {
    private static int checks;
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] a) {
        check(BridgeMath.percent(50, 200) == 25 && BridgeMath.percent(0, 100) == 0 && BridgeMath.percent(300, 100) == 100, "percent bounds");
        check(BridgeMath.percent(5, 0) == 0 && BridgeMath.percent(Double.NaN, 10) == 0, "percent safe on bad input");
        check(Math.abs(BridgeMath.chakraFor(20, 500, 1) - 100) < 1e-9, "20% of 500 chakra");
        check(BridgeMath.chakraFor(-3, 500, 1) == 0 && BridgeMath.chakraFor(5, 0, 1) == 0, "no spend for refunds or zero max");
        check(Math.abs(BridgeMath.chakraFor(10, 100, 99) - 100) < 1e-9, "scale clamped");
        for (int have = 0; have <= 1000; have += 37) {
            int p = BridgeMath.percent(have, 1000);
            check(p >= 0 && p <= 100, "mirror in range");
        }
        String[][] cases = {{"narutomod:rasengan", "spiral"}, {"narutomod:chidori", "charge"}, {"narutomod:katon_fireball", "fireedge"},
            {"narutomod:futon_vacuum", "windedge"}, {"narutomod:yoton_lava", "lava"}, {"narutomod:raiton_armor", "charge"},
            {"narutomod:kunai", "weapon:waymark_knife"}, {"ahznbswordsplus:great_katana", "weapon:thunder_edge"},
            {"narutomod:explosive_tag", "weapon:seal_charge"}, {"narutomod:doton_wall", "weapon:siege_maul"}, {"narutomod:sage_mode", "lightmode"}};
        for (String[] c : cases) check(c[1].equals(BridgeMath.infer(c[0])), c[0] + " -> " + BridgeMath.infer(c[0]));
        check(BridgeMath.infer("narutomod:ramen") == null && BridgeMath.infer(null) == null, "unrelated ids stay unmapped");
        System.out.println("PASS: " + checks + " bridge checks (chakra mirror, cost scaling, style inference).");
    }
}
