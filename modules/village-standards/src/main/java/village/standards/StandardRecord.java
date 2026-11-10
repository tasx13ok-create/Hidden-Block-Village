package village.standards;

import java.util.UUID;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/** Versioned new save data, with opaque old fields retained through item/entity cycles. */
public final class StandardRecord {
    public static final String HISTORY = "ValeStandardHistory";
    public static final String VARIANT = "ValeStandardVariant";
    private static final String STACK = "ValeStandardItem";
    private NBTTagCompound opaque = new NBTTagCompound();
    private NBTTagCompound item = new NBTTagCompound();
    private StandardVariant variant;
    private UUID owner;
    private UUID origin = UUID.randomUUID();

    public static StandardRecord read(NBTTagCompound source) {
        StandardRecord record = new StandardRecord();
        record.opaque = source.copy();
        // No ordinal or guessed legacy key mapping: the previous custom schema
        // has not been recovered. Unknown data remains explicitly unassigned.
        record.variant = StandardVariant.byKey(source.getString(VARIANT));
        if (source.hasUniqueId("ValeStandardOrigin")) record.origin = source.getUniqueId("ValeStandardOrigin");
        else if (source.hasUniqueId("UUID")) record.origin = source.getUniqueId("UUID");
        if (source.hasKey(STACK, 10)) record.item = cleanItem(source.getCompoundTag(STACK));
        if (source.hasUniqueId("ValeStandardOwner")) record.owner = source.getUniqueId("ValeStandardOwner");
        return record;
    }

    public static StandardRecord placed(StandardVariant variant, ItemStack stack, UUID owner) {
        NBTTagCompound tag = stack.getTagCompound();
        StandardRecord record = tag != null && tag.hasKey(HISTORY, 10)
            ? read(tag.getCompoundTag(HISTORY)) : new StandardRecord();
        record.variant = variant;
        record.owner = owner;
        ItemStack one = stack.copy(); one.setCount(1);
        record.item = cleanItem(one.writeToNBT(new NBTTagCompound()));
        return record;
    }

    static NBTTagCompound cleanItem(NBTTagCompound input) {
        NBTTagCompound result = input.copy();
        result.setByte("Count", (byte) 1);
        if (result.hasKey("tag", 10)) {
            NBTTagCompound tag = result.getCompoundTag("tag");
            tag.removeTag(HISTORY);
            if (tag.getKeySet().isEmpty()) result.removeTag("tag");
        }
        return result;
    }

    public StandardVariant variant() { return variant; }
    public UUID origin() { return origin; }
    public void assign(StandardVariant value) {
        if (value == null) throw new IllegalArgumentException("Choose a known village");
        variant = value;
    }
    public NBTTagCompound itemData() { return item.copy(); }
    public NBTTagCompound write(NBTTagCompound destination) {
        for (String key : opaque.getKeySet()) {
            if (!destination.hasKey(key)) destination.setTag(key, opaque.getTag(key).copy());
        }
        destination.setInteger("ValeStandardSchema", 1);
        destination.setString(VARIANT, variant == null ? "" : variant.key);
        destination.setBoolean("ValeStandardUnassigned", variant == null);
        destination.setUniqueId("ValeStandardOrigin", origin);
        if (!item.getKeySet().isEmpty()) destination.setTag(STACK, item.copy());
        if (owner != null) destination.setUniqueId("ValeStandardOwner", owner);
        return destination;
    }
    public ItemStack drop() {
        ItemStack result = new ItemStack(StandardsMod.item(variant));
        if (item.hasKey("tag", 10)) result.setTagCompound(item.getCompoundTag("tag").copy());
        NBTTagCompound tag = result.hasTagCompound() ? result.getTagCompound() : new NBTTagCompound();
        tag.setTag(HISTORY, write(new NBTTagCompound()));
        result.setTagCompound(tag);
        result.setCount(1);
        return result;
    }
}
