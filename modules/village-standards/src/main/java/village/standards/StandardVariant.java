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
}
