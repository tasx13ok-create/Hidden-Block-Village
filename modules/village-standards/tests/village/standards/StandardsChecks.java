package village.standards;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/** Geometry and actual NBT persistence checks; full placement runs in the native helper. */
public final class StandardsChecks {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static NBTTagCompound reload(NBTTagCompound data) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(data, output);
        return CompressedStreamTools.readCompressed(new ByteArrayInputStream(output.toByteArray()));
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            BlockPos anchor = new BlockPos(-17, 74, 28);
            List<BlockPos> back = StandardGeometry.backingCells(anchor, side);
            List<BlockPos> display = StandardGeometry.displayCells(anchor, side);
            check(back.size() == 25 && new HashSet<>(back).size() == 25, "Backing must cover exactly 25 distinct blocks");
            check(display.size() == 25 && new HashSet<>(display).size() == 25, "Front must cover exactly 25 distinct blocks");
            check(back.contains(anchor.offset(side.getOpposite())), "Clicked backing is the panel center");
            for (BlockPos position : back) {
                check(display.contains(position.offset(side)), "Every support has exactly its outward display cell");
                check(position.getY() >= 72 && position.getY() <= 76, "Panel must be exactly five blocks tall");
                check(side.getAxis() == EnumFacing.Axis.X ? position.getX() == anchor.getX() - side.getXOffset()
                    : position.getZ() == anchor.getZ() - side.getZOffset(), "Backing must stay on one wall plane");
            }
        }
        check(!StandardGeometry.horizontal(EnumFacing.UP) && !StandardGeometry.horizontal(EnumFacing.DOWN), "Ceiling/floor placement must fail");
        for (StandardVariant variant : StandardVariant.values()) {
            check(StandardVariant.byKey(variant.key) == variant, "Known names round-trip without numeric mapping");
            check(variant.texture().toString().equals("hvdungeonpictures:textures/pictures/" + variant.key + ".png"), "Reviewed texture path must stay stable");
        }
        check(StandardVariant.byKey("unknown") == null && StandardVariant.byKey("0") == null, "Unknown/ordinal data must not silently choose a village");

        NBTTagCompound old = new NBTTagCompound();
        old.setInteger("TileX", 4); old.setInteger("TileY", 80); old.setInteger("TileZ", 11);
        old.setString("UnknownPictureKey", "legacy-private-variant");
        old.setByteArray("UnknownBytes", new byte[] {4, -1, 17});
        NBTTagCompound nested = new NBTTagCompound(); nested.setLong("credit", 1234567890123L); old.setTag("LegacyNested", nested);
        UUID original = UUID.fromString("167a8824-95d1-4c38-9665-ac189ce3a977"); old.setUniqueId("UUID", original);
        StandardRecord record = StandardRecord.read(old);
        check(record.variant() == null, "Unknown custom save format must remain unassigned");
        check(record.origin().equals(original), "Legacy entity UUID identifies its one recovery record");
        old.getCompoundTag("LegacyNested").setLong("credit", 0);
        NBTTagCompound saved = record.write(new NBTTagCompound());
        check(saved.getCompoundTag("LegacyNested").getLong("credit") == 1234567890123L, "Read must copy opaque NBT deeply");
        saved.setInteger("TileX", 100);
        NBTTagCompound current = new NBTTagCompound(); current.setInteger("TileX", -50);
        check(record.write(current).getInteger("TileX") == -50, "Current native position must override old opaque coordinates");
        record = StandardRecord.read(reload(record.write(new NBTTagCompound())));
        record.assign(StandardVariant.MIST);
        NBTTagCompound assigned = reload(record.write(new NBTTagCompound()));
        check(StandardRecord.read(assigned).variant() == StandardVariant.MIST, "Explicit assignment must survive compressed persistence");
        check(assigned.getString("UnknownPictureKey").equals("legacy-private-variant"), "Assignment must retain unknown fields");
        check(assigned.getByteArray("UnknownBytes")[1] == -1, "Assignment must retain opaque bytes");
        check(StandardRecord.read(assigned).origin().equals(original), "Resolution must retain the original recovery identity");

        NBTTagCompound stack = new NBTTagCompound(); stack.setString("id", "hvdungeonpictures:leaf_picture"); stack.setByte("Count", (byte) 8);
        NBTTagCompound itemTag = new NBTTagCompound(); itemTag.setString("valeSerial", "synthetic-reward");
        itemTag.setTag(StandardRecord.HISTORY, assigned); stack.setTag("tag", itemTag);
        NBTTagCompound cleaned = StandardRecord.cleanItem(stack);
        check(cleaned.getByte("Count") == 1, "One placed object must store only one inventory item");
        check(!cleaned.getCompoundTag("tag").hasKey(StandardRecord.HISTORY), "Item history cannot recursively contain itself");
        check(cleaned.getCompoundTag("tag").getString("valeSerial").equals("synthetic-reward"), "Reward receipt must remain attached");
        check(stack.getCompoundTag("tag").hasKey(StandardRecord.HISTORY) && stack.getByte("Count") == 8, "Cleaning cannot mutate held source stack data");
        StandardsArchive archive = new StandardsArchive();
        check(archive.keep(original, assigned), "First unresolved record must be retained");
        check(!archive.keep(original, cleaned) && archive.size() == 1, "Repeated break/place cannot grow or overwrite the original archive");
        StandardsArchive restored = new StandardsArchive(); restored.readFromNBT(reload(archive.writeToNBT(new NBTTagCompound())));
        check(restored.size() == 1 && restored.copy(original).equals(assigned), "Opaque archive must survive compressed save/reload");
        restored.copy(original).setString("UnknownPictureKey", "changed");
        check(restored.copy(original).getString("UnknownPictureKey").equals("legacy-private-variant"), "Archive reads must be isolated copies");
        NBTTagCompound duplicateArchive = archive.writeToNBT(new NBTTagCompound());
        NBTTagList rows = duplicateArchive.getTagList("records", 10);
        NBTTagCompound duplicate = rows.getCompoundTagAt(0).copy(); duplicate.setTag("data", cleaned); rows.appendTag(duplicate);
        restored.readFromNBT(duplicateArchive);
        check(restored.size() == 1 && restored.copy(original).equals(assigned), "Duplicate saved archive keys keep the first original");
        System.out.println("PASS: " + checks + " Village Standards geometry and persistence checks; actual placement/rendering are separate.");
    }
}
