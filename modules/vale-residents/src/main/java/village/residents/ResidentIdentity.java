package village.residents;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

/** Preserve the entire owned entity tag, even if a future schema changes its type. */
public final class ResidentIdentity {
    public static final String KEY = "ValeResident";
    private final NBTBase raw;
    private ResidentIdentity(NBTBase raw) { this.raw = raw == null ? null : raw.copy(); }
    public static ResidentIdentity empty() { return new ResidentIdentity(null); }

    public static ResidentIdentity forRole(ResidentRole role) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Schema", 1);
        tag.setString("Role", role.id);
        return new ResidentIdentity(tag);
    }

    public static ResidentIdentity readEntity(NBTTagCompound entity) {
        return new ResidentIdentity(entity.getTag(KEY));
    }

    public void writeEntity(NBTTagCompound entity) {
        if (raw == null) entity.removeTag(KEY);
        else entity.setTag(KEY, raw.copy());
    }

    public ResidentRole displayRole() {
        return raw instanceof NBTTagCompound
            ? ResidentRole.fromId(((NBTTagCompound) raw).getString("Role")) : null;
    }

    public ResidentRole interactiveRole() {
        if (!(raw instanceof NBTTagCompound)) return null;
        NBTTagCompound tag = (NBTTagCompound) raw;
        return tag.hasKey("Schema", 3) && tag.getInteger("Schema") == 1 ? displayRole() : null;
    }
}
