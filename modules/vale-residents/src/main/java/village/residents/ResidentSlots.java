package village.residents;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Own world data only. Occupied, malformed and future slots are never reset. */
public final class ResidentSlots {
    public static final int SCHEMA = 1;
    public static final int HARD_LIMIT = 3;
    private final NBTTagCompound raw;
    private final boolean readOnly;

    public ResidentSlots() {
        raw = new NBTTagCompound();
        raw.setInteger("Schema", SCHEMA);
        raw.setTag("Slots", new NBTTagCompound());
        readOnly = false;
    }

    private ResidentSlots(NBTTagCompound input) {
        raw = input.copy();
        readOnly = !raw.hasKey("Schema", 3) || raw.getInteger("Schema") != SCHEMA
            || !raw.hasKey("Slots", 10);
    }

    public static ResidentSlots read(NBTTagCompound input) { return new ResidentSlots(input); }
    public NBTTagCompound write() { return raw.copy(); }
    public boolean isReadOnly() { return readOnly; }
    public int count() { return raw.getCompoundTag("Slots").getSize(); }
    public boolean occupied(ResidentRole role) { return raw.getCompoundTag("Slots").hasKey(role.id); }

    public UUID uuid(ResidentRole role) {
        NBTTagCompound slot = raw.getCompoundTag("Slots").getCompoundTag(role.id);
        return slot.hasUniqueId("Uuid") ? slot.getUniqueId("Uuid") : null;
    }

    public int occupancyIncludingLoaded(Collection<UUID> loaded) {
        Set<UUID> ids = new HashSet<>(loaded);
        int unparseable = 0;
        NBTTagCompound slots = raw.getCompoundTag("Slots");
        for (String key : slots.getKeySet()) {
            if (slots.hasKey(key, 10) && slots.getCompoundTag(key).hasUniqueId("Uuid"))
                ids.add(slots.getCompoundTag(key).getUniqueId("Uuid"));
            else {
                NBTTagCompound slot = slots.getCompoundTag(key);
                unparseable += slot.getBoolean("Conflict")
                    ? Math.max(1, Math.min(1024, slot.getInteger("ObservedResidents"))) : 1;
            }
        }
        return ids.size() + unparseable;
    }

    public boolean mayReserve(ResidentRole role, int configuredLimit, Collection<UUID> loaded) {
        int limit = Math.max(0, Math.min(HARD_LIMIT, configuredLimit));
        return !readOnly && !occupied(role) && occupancyIncludingLoaded(loaded) < limit;
    }

    public boolean reserve(ResidentRole role, UUID id, int dimension, BlockPos home,
                           int configuredLimit, Collection<UUID> loaded) {
        if (id == null || !mayReserve(role, configuredLimit, loaded)) return false;
        NBTTagCompound slot = new NBTTagCompound();
        slot.setUniqueId("Uuid", id);
        slot.setInteger("Dimension", dimension);
        slot.setInteger("HomeX", home.getX());
        slot.setInteger("HomeY", home.getY());
        slot.setInteger("HomeZ", home.getZ());
        raw.getCompoundTag("Slots").setTag(role.id, slot);
        return true;
    }

    /** Roll back only a newly rejected spawn with the exact reservation UUID. */
    public boolean cancelRejected(ResidentRole role, UUID id) {
        if (readOnly || id == null || !id.equals(uuid(role))) return false;
        raw.getCompoundTag("Slots").removeTag(role.id);
        return true;
    }

    public boolean recordConflict(ResidentRole role, int count) {
        if (readOnly || occupied(role) || count < 2) return false;
        NBTTagCompound slot = new NBTTagCompound();
        slot.setBoolean("Conflict", true);
        slot.setInteger("ObservedResidents", count);
        raw.getCompoundTag("Slots").setTag(role.id, slot);
        return true;
    }

    /** Author relocation of an existing matching UUID; preserve all unrelated slot data. */
    public boolean relocate(ResidentRole role, UUID id, int dimension, BlockPos home) {
        if (readOnly || id == null || !id.equals(uuid(role))) return false;
        NBTTagCompound slot = raw.getCompoundTag("Slots").getCompoundTag(role.id);
        slot.setInteger("Dimension", dimension);slot.setInteger("HomeX", home.getX());
        slot.setInteger("HomeY", home.getY());slot.setInteger("HomeZ", home.getZ());
        return true;
    }
}
