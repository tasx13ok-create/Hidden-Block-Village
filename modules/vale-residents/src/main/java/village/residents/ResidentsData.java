package village.residents;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

public final class ResidentsData extends WorldSavedData {
    public static final String KEY = "vale_residents";
    public ResidentSlots slots = new ResidentSlots();

    public ResidentsData() { super(KEY); }
    public ResidentsData(String name) { super(name); }

    public static ResidentsData get(WorldServer world) {
        MapStorage storage = world.getPerWorldStorage();
        ResidentsData result = (ResidentsData) storage.getOrLoadData(ResidentsData.class, KEY);
        if (result == null) {
            result = new ResidentsData();
            storage.setData(KEY, result);
            result.markDirty();
        }
        return result;
    }

    @Override public void readFromNBT(NBTTagCompound tag) { slots = ResidentSlots.read(tag); }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound tag) { return slots.write(); }
}
