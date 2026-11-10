package village.standards;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;

/** First-seen opaque data survives even if an unassigned recovery item is lost. */
public final class StandardsArchive extends WorldSavedData {
    private static final String NAME = "vale_standards_recovery";
    private final Map<UUID, NBTTagCompound> records = new LinkedHashMap<>();
    public StandardsArchive() { super(NAME); }
    public StandardsArchive(String name) { super(name); }
    public static StandardsArchive get(WorldServer world) {
        WorldServer store = world.getMinecraftServer().getWorld(0);
        StandardsArchive archive = (StandardsArchive) store.getMapStorage().getOrLoadData(StandardsArchive.class, NAME);
        if (archive == null) { archive = new StandardsArchive(); store.getMapStorage().setData(NAME, archive); }
        return archive;
    }
    public boolean keep(UUID origin, NBTTagCompound data) {
        if (records.containsKey(origin)) return false;
        records.put(origin, data.copy()); markDirty(); return true;
    }
    public int size() { return records.size(); }
    public List<UUID> origins(int start, int limit) {
        List<UUID> result = new ArrayList<>();
        if (start < 0 || limit < 1) return result;
        int index = 0;
        for (UUID origin : records.keySet()) {
            if (index++ < start) continue;
            result.add(origin);
            if (result.size() == limit) break;
        }
        return result;
    }
    public NBTTagCompound copy(UUID origin) {
        NBTTagCompound data = records.get(origin);
        return data == null ? null : data.copy();
    }
    @Override public void readFromNBT(NBTTagCompound input) {
        records.clear();
        NBTTagList list = input.getTagList("records", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound row = list.getCompoundTagAt(i);
            if (row.hasUniqueId("origin") && row.hasKey("data", 10))
                records.putIfAbsent(row.getUniqueId("origin"), row.getCompoundTag("data").copy());
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound output) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, NBTTagCompound> entry : records.entrySet()) {
            NBTTagCompound row = new NBTTagCompound(); row.setUniqueId("origin", entry.getKey());
            row.setTag("data", entry.getValue().copy()); list.appendTag(row);
        }
        output.setTag("records", list); return output;
    }
}
