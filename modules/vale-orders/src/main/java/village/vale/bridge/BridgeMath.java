package village.vale.bridge;

import java.util.Locale;

/** Pure bridge rules (unit-tested): chakra percentage mirror and registry-name style inference. */
public final class BridgeMath {
    private BridgeMath() {}

    /** Chakra as a 0..100 energy percentage. */
    public static int percent(double have, double max) {
        if (!(max > 0) || Double.isNaN(have)) return 0;
        return (int) Math.max(0, Math.min(100, Math.round(have / max * 100)));
    }

    /** Chakra to spend for a drop of {@code points} percent of max, scaled. */
    public static double chakraFor(int points, double max, double scale) {
        if (points <= 0 || !(max > 0)) return 0;
        return points / 100.0 * max * Math.max(0.05, Math.min(10, scale));
    }

    private static final String[][] RULES = {
        // keyword (in the registry path)            style
        {"rasen", "spiral"}, {"spiral", "spiral"}, {"sphere", "spiral"},
        {"chidori", "charge"}, {"raikiri", "charge"}, {"lightning", "charge"}, {"raiton", "charge"}, {"thunder", "charge"},
        {"armor", "lightmode"}, {"cloak", "lightmode"}, {"mode", "lightmode"}, {"sage", "lightmode"},
        {"lava", "lava"}, {"yoton", "lava"}, {"magma", "lava"},
        {"fire", "fireedge"}, {"katon", "fireedge"}, {"flame", "fireedge"}, {"blaze", "fireedge"}, {"ember", "fireedge"},
        {"wind", "windedge"}, {"futon", "windedge"}, {"gale", "windedge"}, {"air", "windedge"},
        {"water", "weapon:mist_censer"}, {"suiton", "weapon:mist_censer"}, {"mist", "weapon:mist_censer"},
        {"earth", "weapon:siege_maul"}, {"doton", "weapon:siege_maul"}, {"stone", "weapon:siege_maul"},
        {"ice", "weapon:rime_pin"}, {"hyoton", "weapon:rime_pin"},
        {"wood", "weapon:thorn_staff"}, {"mokuton", "weapon:thorn_staff"},
        {"sand", "weapon:ward_fan"},
        {"kunai", "weapon:waymark_knife"}, {"shuriken", "weapon:returning_sickle"}, {"senbon", "weapon:venom_needle"}, {"needle", "weapon:venom_needle"},
        {"scythe", "weapon:sweep_glaive"}, {"sickle", "weapon:returning_sickle"}, {"fan", "weapon:ward_fan"},
        {"chain", "weapon:binder_spear"}, {"spear", "weapon:binder_spear"}, {"staff", "weapon:thorn_staff"},
        {"hammer", "weapon:siege_maul"}, {"axe", "weapon:breakaxe"}, {"claw", "weapon:crescent_talons"},
        {"tag", "weapon:seal_charge"}, {"explosive", "weapon:seal_charge"}, {"seal", "weapon:seal_charge"},
        {"bow", "weapon:lens_bow"}, {"puppet", "weapon:needle_spool"},
        {"sword", "weapon:thunder_edge"}, {"katana", "weapon:thunder_edge"}, {"blade", "weapon:thunder_edge"}, {"tanto", "weapon:thunder_edge"},
    };

    /** Style for a licensed registry id, or null when nothing fits. */
    public static String infer(String id) {
        if (id == null) return null;
        int c = id.indexOf(':');
        String path = (c < 0 ? id : id.substring(c + 1)).toLowerCase(Locale.ROOT);
        for (String[] r : RULES) if (path.contains(r[0])) return r[1];
        return null;
    }
}
