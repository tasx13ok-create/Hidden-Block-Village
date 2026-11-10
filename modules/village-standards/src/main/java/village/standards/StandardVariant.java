package village.standards;

import net.minecraft.util.ResourceLocation;

/** The five reviewed existing village pictures; order is never a saved-data contract. */
public enum StandardVariant {
    STONE("stone"), SAND("sand"), MIST("mist"), LEAF("leaf"), CLOUD("cloud");
    public final String key;
    StandardVariant(String key) { this.key = key; }
    public String itemKey() { return key + "_picture"; }
    public ResourceLocation texture() {
        return new ResourceLocation(StandardsMod.ID, "textures/pictures/" + key + ".png");
    }
    public static StandardVariant byKey(String key) {
        for (StandardVariant value : values()) if (value.key.equals(key)) return value;
        return null;
    }
    /** Exact 0.2.0 NBT contract; deliberately independent of this enum's order. */
    public static StandardVariant byLegacyVillage(int value) {
        switch (value) {
            case 0: return MIST;
            case 1: return LEAF;
            case 2: return SAND;
            case 3: return CLOUD;
            case 4: return STONE;
            default: return null;
        }
    }
}
