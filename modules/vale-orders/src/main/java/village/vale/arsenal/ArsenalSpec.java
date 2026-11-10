package village.vale.arsenal;

import village.vale.Order;

/** Original specialist identities. Values are bounded gameplay parameters, not legacy balance. */
public enum ArsenalSpec {
    WARD_FAN("ward_fan", "Ward Fan", Order.GALE, 4, 1.4f, 20, 80, 8, Role.WIND),
    RETURNING_SICKLE("returning_sickle", "Returning Sickle", Order.GALE, 6, 1.1f, 16, 70, 6, Role.RETURN),
    WAYMARK_KNIFE("waymark_knife", "Waymark Knife", Order.DUSK, 4, 2f, 12, 40, 6, Role.MARK),
    SIPHON_HOOK("siphon_hook", "Siphon Hook", Order.DUSK, 6, 1.3f, 20, 100, 0, Role.DRAIN),
    STORM_PAIR("storm_pair", "Storm Pair", Order.STORM, 6, 1.8f, 22, 90, 12, Role.CHAIN),
    THUNDER_EDGE("thunder_edge", "Thunder Edge", Order.STORM, 7, 1.5f, 20, 80, 8, Role.SHOCK),
    SIEGE_MAUL("siege_maul", "Siege Maul", Order.FLINT, 9, .85f, 26, 110, 16, Role.SLAM),
    BREAKAXE("breakaxe", "Breakaxe", Order.FLINT, 8, 1f, 20, 90, 10, Role.BREAK),
    TEMPERED_CLEAVER("tempered_cleaver", "Tempered Cleaver", Order.FLINT, 8, 1f, 18, 80, 0, Role.RESTORE),
    LENS_BOW("lens_bow", "Lens Bow", Order.DAWN, 4, 1.3f, 20, 70, 8, Role.DRAIN_BOLT),
    EMBERBRAND("emberbrand", "Emberbrand", Order.EMBER, 6, 1.5f, 24, 100, 10, Role.FIRE),
    CINDER_BURST("cinder_burst", "Cinder Burst", Order.EMBER, 7, 1f, 25, 100, 12, Role.BURST),
    RIME_PIN("rime_pin", "Rime Pin", Order.RIME, 5, 1.9f, 16, 70, 6, Role.RIME),
    BINDER_SPEAR("binder_spear", "Binder Spear", Order.BLOOM, 6, 1.3f, 24, 100, 12, Role.BIND),
    THORN_STAFF("thorn_staff", "Thorn Staff", Order.BLOOM, 5, 1.2f, 25, 120, 12, Role.EXTEND),
    MENDGUARD_STAFF("mendguard_staff", "Mendguard Staff", Order.DAWN, 4, 1.2f, 20, 100, 0, Role.MEND),
    FLUX_CANNON("flux_cannon", "Flux Cannon", Order.STORM, 4, 1f, 28, 110, 16, Role.GUIDED),
    NEEDLE_SPOOL("needle_spool", "Needle Spool", Order.CURRENT, 4, 1.4f, 24, 100, 10, Role.EMITTER),
    VENOM_NEEDLE("venom_needle", "Venom Needle", Order.DUSK, 4, 1.7f, 18, 90, 6, Role.VENOM),
    MIST_CENSER("mist_censer", "Mist Censer", Order.CURRENT, 3, 1.3f, 20, 110, 6, Role.MIST),
    PRISM_LANCE("prism_lance", "Prism Lance", Order.DAWN, 7, 1f, 30, 140, 20, Role.FINISH),
    CRESCENT_GUARD("crescent_guard", "Crescent Guard", Order.CURRENT, 5, 1.3f, 18, 80, 0, Role.GUARD),
    OVERDRIVE_BLADE("overdrive_blade", "Overdrive Blade", Order.EMBER, 6, 1.7f, 28, 160, 12, Role.OVERDRIVE),
    ECHO_GAUNTLET("echo_gauntlet", "Echo Gauntlet", Order.GALE, 5, 1.6f, 20, 90, 10, Role.SONIC),
    SWEEP_GLAIVE("sweep_glaive", "Sweep Glaive", Order.RIME, 7, 1.1f, 24, 120, 12, Role.SWEEP),
    SEAL_CHARGE("seal_charge", "Seal Charge", Order.FLINT, 4, 1.2f, 20, 120, 6, Role.TRAP);

    public enum Role { WIND, RETURN, MARK, DRAIN, CHAIN, SHOCK, SLAM, BREAK, RESTORE, DRAIN_BOLT,
        FIRE, BURST, RIME, BIND, EXTEND, MEND, GUIDED, EMITTER, VENOM, MIST, FINISH, GUARD,
        OVERDRIVE, SONIC, SWEEP, TRAP }
    public final String key, title;
    public final Order order;
    public final float damage, speed;
    public final int cost, cooldown, charge;
    public final Role role;
    ArsenalSpec(String key, String title, Order order, float damage, float speed, int cost, int cooldown, int charge, Role role) {
        this.key=key;this.title=title;this.order=order;this.damage=damage;this.speed=speed;
        this.cost=cost;this.cooldown=cooldown;this.charge=charge;this.role=role;
    }
    public int minimumLevel() { return 5+cost/5; }
    public int meritPrice() { return 4+(cost+5)/6; }
    public static ArsenalSpec byKey(String key) {
        if(key==null)return null;
        if(key.startsWith("valeorders:"))key=key.substring(11);
        for(ArsenalSpec spec:values())if(spec.key.equals(key))return spec;
        return null;
    }
    public boolean shield() { return role==Role.GUARD||role==Role.MEND||role==Role.WIND||role==Role.RESTORE; }
    public static ArsenalSpec at(int value) { return value>=0&&value<values().length?values()[value]:null; }
}
