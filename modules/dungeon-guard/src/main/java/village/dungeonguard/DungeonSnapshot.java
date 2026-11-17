package village.dungeonguard;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** A bounded arena baseline using registry names, exact properties and full tile NBT. */
public final class DungeonSnapshot {
    private static final int SCHEMA = 2;   // 2: packed palette indexes (1 = legacy int array, still readable)
    private static final long MAX_COMPRESSED_BYTES = 256L * 1024L * 1024L;
    private static final long MAX_NBT_BYTES = 768L * 1024L * 1024L;
    private final GuardRegion region;
    private final PackedBlockStates states;
    private final List<IBlockState> palette;
    private final Map<IBlockState, Integer> paletteLookup;
    private final LinkedHashMap<Integer, NBTTagCompound> tiles;
    private int captured;
    private String checksum;

    public DungeonSnapshot(GuardRegion region) {
        region.validate();
        if (region.volume() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Snapshot exceeds the supported array size");
        }
        this.region = copyRegion(region);
        this.states = new PackedBlockStates((int) region.volume());
        this.palette = new ArrayList<IBlockState>();
        this.paletteLookup = new HashMap<IBlockState, Integer>();
        this.tiles = new LinkedHashMap<Integer, NBTTagCompound>();
    }

    private DungeonSnapshot(GuardRegion region, PackedBlockStates states, List<IBlockState> palette,
            LinkedHashMap<Integer, NBTTagCompound> tiles, String checksum) {
        this.region = copyRegion(region);
        this.states = states;
        this.palette = palette;
        this.paletteLookup = new HashMap<IBlockState, Integer>();
        this.tiles = tiles;
        this.captured = states.length();
        this.checksum = checksum;
    }

    public GuardRegion region() { return copyRegion(region); }
    public int size() { return states.length(); }
    public int captured() { return captured; }
    public String checksum() { return checksum; }
    public IBlockState stateAt(int index) { return palette.get(states.get(index)); }
    public int tileCount() { return tiles.size(); }
    public List<Integer> tileIndexes() { return new ArrayList<Integer>(tiles.keySet()); }
    public NBTTagCompound tileAt(int index) {
        NBTTagCompound nbt = tiles.get(index);
        return nbt == null ? null : nbt.copy();
    }

    /** Snapshot index of a position inside the region (inverse of {@link #positionAt}). */
    public int indexAt(BlockPos pos) {
        if (pos == null || !region.contains(pos)) throw new IllegalArgumentException("Position outside snapshot bounds");
        long width = (long) region.maxX - region.minX + 1, depth = (long) region.maxZ - region.minZ + 1;
        return (int) (((long) pos.getY() - region.minY) * width * depth + ((long) pos.getZ() - region.minZ) * width + (long) pos.getX() - region.minX);
    }

    /** X changes fastest, then Z, then Y; restoration therefore proceeds bottom-up. */
    public BlockPos positionAt(int index) {
        if (index < 0 || index >= states.length()) throw new IndexOutOfBoundsException();
        int width = region.maxX - region.minX + 1;
        int depth = region.maxZ - region.minZ + 1;
        int layer = width * depth;
        return new BlockPos(region.minX + index % width,
                region.minY + index / layer, region.minZ + index / width % depth);
    }

    public int capture(World world, int budget) {
        return capture(world, budget, Long.MAX_VALUE);
    }

    int capture(World world, int budget, long deadlineNanos) {
        requireWorld(world);
        int used = 0;
        while (captured < states.length() && used < budget && System.nanoTime() < deadlineNanos) {
            BlockPos pos = positionAt(captured);
            IBlockState state = world.getBlockState(pos);
            Integer entry = paletteLookup.get(state);
            if (entry == null) {
                entry = palette.size();
                paletteLookup.put(state, entry);
                palette.add(state);
            }
            states.set(captured, entry);
            TileEntity tile = world.getTileEntity(pos);
            if (tile != null) {
                NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
                validateTilePosition(nbt, pos);
                tiles.put(captured, nbt.copy());
            }
            captured++;
            used++;
        }
        return used;
    }

    /** Called on the snapshot I/O worker only after capture is complete. */
    public String save(File file) throws IOException {
        if (captured != states.length()) throw new IOException("Cannot save an incomplete baseline");
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("Schema", SCHEMA);
        root.setTag("Region", regionTag(region));
        NBTTagList paletteTag = new NBTTagList();
        for (IBlockState state : palette) paletteTag.appendTag(NBTUtil.writeBlockState(new NBTTagCompound(), state));
        root.setTag("Palette", paletteTag);
        root.setInteger("BlockCount", states.length());
        root.setInteger("BlockIndexBytes", states.indexBytes());
        root.setByteArray("Blocks", states.bytesForSerialization());
        NBTTagList tileTags = new NBTTagList();
        for (Map.Entry<Integer, NBTTagCompound> tile : tiles.entrySet()) {
            NBTTagCompound item = new NBTTagCompound();
            item.setInteger("Index", tile.getKey());
            item.setTag("Data", tile.getValue().copy());
            tileTags.appendTag(item);
        }
        root.setTag("Tiles", tileTags);
        Path target = file.toPath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "snapshot-", ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                CompressedStreamTools.writeCompressed(root, output);
            }
            // writeCompressed closes its wrapper; sync through a fresh descriptor afterwards.
            try (FileOutputStream sync = new FileOutputStream(temporary.toFile(), true)) {
                sync.getFD().sync();
            }
            String digest = digest(temporary.toFile());
            atomicMove(temporary, target);
            durableText(new File(file.getPath() + ".sha256"), digest + "\n");
            checksum = digest;
            return digest;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static DungeonSnapshot load(File file, GuardRegion expected, int maxBlocks) throws IOException {
        if (!file.isFile() || !new File(file.getPath() + ".sha256").isFile()) {
            throw new IOException("Baseline or checksum is missing for " + expected.name);
        }
        if (file.length() > MAX_COMPRESSED_BYTES) throw new IOException("Baseline file is unexpectedly large");
        String claimed = new String(Files.readAllBytes(new File(file.getPath() + ".sha256").toPath()), StandardCharsets.US_ASCII).trim();
        if (!claimed.matches("[0-9a-f]{64}")) throw new IOException("Malformed baseline checksum");
        String actual = digest(file);
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII), claimed.getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("Baseline checksum mismatch for " + expected.name);
        }
        NBTTagCompound root;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(file))))) {
            root = CompressedStreamTools.read(input, new NBTSizeTracker(MAX_NBT_BYTES));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid baseline NBT", invalid);
        }
        int schema = root.getInteger("Schema");
        boolean blockFormat = schema == 1 ? root.hasKey("Blocks", 11)
                : schema == 2 && root.hasKey("Blocks", 7) && root.hasKey("BlockCount", 3) && root.hasKey("BlockIndexBytes", 3);
        if (!blockFormat || !root.hasKey("Region", 10) || !root.hasKey("Palette", 9) || !root.hasKey("Tiles", 9)) {
            throw new IOException("Unsupported or incomplete baseline format");
        }
        GuardRegion region = readRegion(root.getCompoundTag("Region"));
        if (!regionTag(region).equals(regionTag(expected))) throw new IOException("Baseline bounds differ from configured dungeon " + expected.name);
        if (region.volume() > maxBlocks || region.volume() > Integer.MAX_VALUE) throw new IOException("Baseline exceeds configured maximum block count");
        int blockCount = (int) region.volume();
        if (schema == 1 && root.getIntArray("Blocks").length != blockCount || schema == 2 && root.getInteger("BlockCount") != blockCount)
            throw new IOException("Baseline block count does not match bounds");
        NBTTagList savedPalette = root.getTagList("Palette", 10);
        if (savedPalette.tagCount() == 0 || savedPalette.tagCount() > blockCount) throw new IOException("Invalid baseline palette length");
        List<IBlockState> palette = new ArrayList<IBlockState>();
        for (int i = 0; i < savedPalette.tagCount(); i++) {
            NBTTagCompound saved = savedPalette.getCompoundTagAt(i);
            IBlockState decoded = NBTUtil.readBlockState(saved);
            // NBTUtil alone silently substitutes unknown blocks/properties; that is unsafe here.
            if (!NBTUtil.writeBlockState(new NBTTagCompound(), decoded).equals(saved)) {
                throw new IOException("A baseline block or property is no longer registered: " + saved);
            }
            palette.add(decoded);
        }
        PackedBlockStates states;
        try {
            states = schema == 1 ? PackedBlockStates.fromLegacy(root.getIntArray("Blocks"), palette.size())
                    : PackedBlockStates.takeBytes(blockCount, root.getInteger("BlockIndexBytes"), root.getByteArray("Blocks"), palette.size());
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid baseline packed block data", invalid);
        }
        root.removeTag("Blocks");   // release the large array before tiles are decoded
        LinkedHashMap<Integer, NBTTagCompound> tiles = new LinkedHashMap<Integer, NBTTagCompound>();
        DungeonSnapshot snapshot = new DungeonSnapshot(region, states, palette, tiles, actual);
        NBTTagList savedTiles = root.getTagList("Tiles", 10);
        if (savedTiles.tagCount() > states.length()) throw new IOException("Too many baseline tile entities");
        for (int i = 0; i < savedTiles.tagCount(); i++) {
            NBTTagCompound entry = savedTiles.getCompoundTagAt(i);
            int index = entry.getInteger("Index");
            if (index < 0 || index >= states.length() || tiles.containsKey(index) || !entry.hasKey("Data", 10)) {
                throw new IOException("Invalid or duplicated baseline tile index");
            }
            NBTTagCompound data = entry.getCompoundTag("Data");
            try { validateTilePosition(data, snapshot.positionAt(index)); }
            catch (IllegalArgumentException invalid) { throw new IOException("Invalid baseline tile position", invalid); }
            if (!snapshot.stateAt(index).getBlock().hasTileEntity(snapshot.stateAt(index))) {
                throw new IOException("Baseline tile entity has no corresponding tile block");
            }
            tiles.put(index, data.copy());
        }
        return snapshot;
    }

    private void requireWorld(World world) {
        if (world == null || world.isRemote || world.provider == null || world.provider.getDimension() != region.dimension) {
            throw new IllegalArgumentException("Baseline world/dimension mismatch");
        }
    }

    private static void validateTilePosition(NBTTagCompound tile, BlockPos pos) {
        if (!tile.hasKey("id", 8) || tile.getString("id").isEmpty() || !tile.hasKey("x", 3)
                || !tile.hasKey("y", 3) || !tile.hasKey("z", 3)
                || tile.getInteger("x") != pos.getX() || tile.getInteger("y") != pos.getY() || tile.getInteger("z") != pos.getZ()) {
            throw new IllegalArgumentException("Tile entity has invalid identity or coordinates at " + pos);
        }
    }

    static GuardRegion copyRegion(GuardRegion r) {
        return new GuardRegion(r.name, r.dimension, r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ);
    }

    static NBTTagCompound regionTag(GuardRegion r) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", r.name);
        tag.setInteger("Dimension", r.dimension);
        tag.setIntArray("Bounds", new int[] {r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ});
        return tag;
    }

    private static GuardRegion readRegion(NBTTagCompound tag) throws IOException {
        int[] b = tag.getIntArray("Bounds");
        if (b.length != 6 || !tag.hasKey("Name", 8) || !tag.hasKey("Dimension", 3)) throw new IOException("Invalid baseline region metadata");
        GuardRegion r = new GuardRegion(tag.getString("Name"), tag.getInteger("Dimension"), b[0], b[1], b[2], b[3], b[4], b[5]);
        try { r.validate(); } catch (IllegalArgumentException invalid) { throw new IOException("Invalid baseline bounds", invalid); }
        if (!Arrays.equals(b, new int[] {r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ})) throw new IOException("Reversed baseline bounds");
        return r;
    }

    static void durableText(File target, String text) throws IOException {
        Path path = target.toPath();
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "marker-", ".tmp");
        try {
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
                output.flush();
                output.getFD().sync();
            }
            atomicMove(temporary, path);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void atomicMove(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static String digest(File file) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest.digest()) result.append(String.format("%02x", value & 255));
        return result.toString();
    }
}
