package village.vale.armor;

/** Stable identities for the independent ward roster. Ordinals have no gameplay meaning. */
public enum ArmorProfile {
    CAIRNROOT("cairnroot", "Cairnroot", "flint", Role.ANCHOR, 48),
    GLASSCHIME("glasschime", "Glasschime", "rime", Role.REACTIVE, 36),
    SHOALGLASS("shoalglass", "Shoalglass", "current", Role.UNDERTOW, 40),
    KILNBACK("kilnback", "Kilnback", "ember", Role.HEAT, 40),
    REEDRUNNER("reedrunner", "Reedrunner", "gale", Role.MOMENTUM, 36),
    BELLBLOOM("bellbloom", "Bellbloom", "bloom", Role.CLEANSE, 40),
    AURIC_SKIMMER("auric_skimmer", "Auric Skimmer", "dawn", Role.FLIGHT, 32),
    HUSHVAULT("hushvault", "Hushvault", "dusk", Role.VEIL, 40),
    STORMLOOM("stormloom", "Stormloom", "storm", Role.COMPANION, 40);

    public enum Role { ANCHOR, REACTIVE, UNDERTOW, HEAT, MOMENTUM, CLEANSE, FLIGHT, VEIL, COMPANION }
    // New stable keys within Core let the existing authoritative reward outbox work unchanged.
    public static final String NAMESPACE = "valeorders";
    public final String key, title, ward;
    public final Role role;
    public final int maximumHealth;

    ArmorProfile(String key, String title, String ward, Role role, int maximumHealth) {
        this.key=key; this.title=title; this.ward=ward; this.role=role; this.maximumHealth=maximumHealth;
    }

    public static ArmorProfile byKey(String key) {
        for (ArmorProfile profile:values()) if (profile.key.equals(key)) return profile;
        return null;
    }

    public String item(ArmorMesh.Slot slot) {
        String suffix;
        switch(slot) {
            case HEAD: suffix="helmet"; break;
            case CHEST: suffix="chestplate"; break;
            case LEGS: suffix="leggings"; break;
            case FEET: suffix="boots"; break;
            default: throw new IllegalArgumentException("Unknown equipment slot");
        }
        return NAMESPACE+":"+key+"_"+suffix;
    }
}
