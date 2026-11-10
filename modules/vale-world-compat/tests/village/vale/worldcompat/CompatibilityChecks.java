package village.vale.worldcompat;

import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** Headless checks for the actual save bridge. Does not load a saved world. */
public final class CompatibilityChecks {
    private static int assertions;

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Bootstrap.register();
        TileEntity.register("waystones:waystone", WorldCompat.MarkerTile.class);
        TileEntity.register("variedcommodities:table", WorldCompat.TableTile.class);
        TileEntity.register("variedcommodities:campfire", WorldCompat.FireTile.class);
        TileEntity.register("variedcommodities:trading_block", WorldCompat.BoardTile.class);

        preserved(new WorldCompat.MarkerTile(), "waystones:waystone");
        preserved(new WorldCompat.TableTile(), "variedcommodities:table");
        preserved(new WorldCompat.BoardTile(), "variedcommodities:trading_block");
        cookingPersistence();
        legacyBlockStates();
        restartState();
        System.out.println("World compatibility checks passed: " + assertions
            + "; headless NBT and block-state checks only; no saved-world lifecycle.");
    }

    private static NBTTagCompound saved(String id) {
        NBTTagCompound input = new NBTTagCompound();
        input.setString("id", id);
        input.setInteger("x", -120);
        input.setInteger("y", 64);
        input.setInteger("z", 45);
        input.setString("LegacyCustomName", "Preserved marker");
        NBTTagCompound nested = new NBTTagCompound();
        nested.setString("owner", "fictional-check-owner");
        nested.setLong("balance", 1234567890123L);
        nested.setByteArray("opaque", new byte[] {0, 1, -1, 12});
        input.setTag("LegacyState", nested);
        NBTTagList slots = new NBTTagList();
        NBTTagCompound slot = new NBTTagCompound();
        slot.setString("id", "minecraft:bread");
        slot.setByte("Count", (byte) 2);
        slot.setByte("Slot", (byte) 4);
        slots.appendTag(slot);
        input.setTag("Items", slots);
        return input;
    }

    private static void preserved(WorldCompat.SavedTile tile, String id) {
        NBTTagCompound input = saved(id);
        NBTTagCompound snapshot = input.copy();
        tile.readFromNBT(input);
        require(tile.getPos().equals(new BlockPos(-120, 64, 45)), "Saved coordinates must load");
        input.getCompoundTag("LegacyState").setString("owner", "external mutation");
        require(tile.opaque.getCompoundTag("LegacyState").equals(snapshot.getCompoundTag("LegacyState")),
            "Read must own a deep copy of unknown nested data");
        tile.setPos(new BlockPos(8, 72, -19));
        NBTTagCompound output = tile.writeToNBT(new NBTTagCompound());
        require(output.getString("id").equals(id), "Registered legacy tile identity must survive");
        require(output.getString("LegacyCustomName").equals(snapshot.getString("LegacyCustomName")),
            "Unknown string metadata must survive");
        require(output.getTag("Items").equals(snapshot.getTag("Items")), "Opaque inventory data must survive");
        require(output.getCompoundTag("LegacyState").equals(snapshot.getCompoundTag("LegacyState")),
            "Unknown nested records and byte arrays must survive");
        require(output.getInteger("x") == 8 && output.getInteger("y") == 72 && output.getInteger("z") == -19,
            "Current tile position must replace stale saved coordinates");
        output.getCompoundTag("LegacyState").setLong("balance", 0L);
        NBTTagCompound again = tile.writeToNBT(new NBTTagCompound());
        require(again.getCompoundTag("LegacyState").equals(snapshot.getCompoundTag("LegacyState")),
            "Mutating one export must not corrupt the next save");
    }

    private static void cookingPersistence() {
        NBTTagCompound input = saved("variedcommodities:campfire");
        input.setTag("ValeCookInput", new ItemStack(Items.BEEF, 1).writeToNBT(new NBTTagCompound()));
        input.setInteger("ValeCookTicks", 37);
        WorldCompat.FireTile hearth = new WorldCompat.FireTile();
        hearth.readFromNBT(input);
        require(hearth.pending.getItem() == Items.BEEF && hearth.pending.getCount() == 1,
            "Pending food must load exactly once");
        require(hearth.remaining == 37, "Remaining cook ticks must survive a save");
        NBTTagCompound output = hearth.writeToNBT(new NBTTagCompound());
        WorldCompat.FireTile restored = new WorldCompat.FireTile();
        restored.readFromNBT(output);
        require(restored.pending.getItem() == Items.BEEF && restored.pending.getCount() == 1,
            "A restart must retain the one consumed input");
        require(restored.remaining == 37, "A restart must preserve progress");
        require(output.getTag("LegacyState").equals(input.getTag("LegacyState")),
            "Cooking fields must not erase unknown prior data");
        for (int invalid : new int[] {-1000, 0, 101, Integer.MAX_VALUE}) {
            NBTTagCompound altered = input.copy();
            altered.setInteger("ValeCookTicks", invalid);
            restored.readFromNBT(altered);
            require(restored.remaining == (invalid < 1 ? 1 : 100), "Cook progress must remain bounded");
            require(restored.pending.getCount() == 1, "Bounding progress must not duplicate input");
        }
    }

    private static void legacyBlockStates() {
        String[] ids = {"waystones:waystone", "variedcommodities:table",
            "variedcommodities:campfire", "variedcommodities:trading_block"};
        require(WorldCompat.BLOCKS.length == ids.length, "Expected saved block families");
        for (int i = 0; i < WorldCompat.BLOCKS.length; i++) {
            Block block = WorldCompat.BLOCKS[i];
            require(block.getRegistryName().toString().equals(ids[i]), "Legacy block identity must remain stable");
            for (int metadata = 0; metadata < 16; metadata++) {
                require(block.getMetaFromState(block.getStateFromMeta(metadata)) == metadata,
                    "Every legacy metadata nibble must round-trip for " + ids[i]);
            }
        }
    }

    private static void restartState() {
        UUID owner = UUID.fromString("b9bb232d-8404-43d7-8d21-61c1e1c81d82");
        WorldCompat.SELECTED.put(owner, "fixture destination");
        WorldCompat.COOLDOWNS.put(owner, Long.MAX_VALUE);
        new WorldCompat().stop(null);
        require(WorldCompat.SELECTED.isEmpty(), "Stopped servers must release selection state");
        require(WorldCompat.COOLDOWNS.isEmpty(), "Stopped servers must release monotonic-clock cooldowns");
    }
}
