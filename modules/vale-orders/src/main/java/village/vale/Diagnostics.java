package village.vale;

import java.util.UUID;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Optional startup checks (-Dvaleorders.selftest=true); never issues player rewards. */
public final class Diagnostics {
    private Diagnostics() {}
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("Vale startup check failed: " + message);
    }
    public static void verify() {
        require(Gear.ITEMS.size() == 152, "expected 152 registered items");
        int armor = 0, weapons = 0;
        for (Item item : Gear.ITEMS.values()) {
            require(item.getRegistryName() != null, "missing item registry name");
            require(ForgeRegistries.ITEMS.getValue(item.getRegistryName()) == item, "item absent from live Forge registry");
            ItemStack stack = new ItemStack(item);
            require(!stack.isEmpty() && stack.getCount() == 1, "invalid item stack");
            require(stack.getMaxStackSize() >= 1 && stack.getMaxStackSize() <= 16, "unexpected reward stack limit");
            if (item instanceof Gear.WardArmor || item instanceof OriginalArmorRegistry.Armor) armor++;
            if (item instanceof Gear.WardBlade) weapons++;
        }
        require(armor == 72 && weapons == 54, "unexpected armor/weapon registrations");
        verifyLedger();
        org.apache.logging.log4j.LogManager.getLogger("ValeOrders").info(
            "VALE_REGISTRY_SELFTEST_PASS: 152 live Forge items, including 72 armor pieces, 54 melee weapons, food and adventure tools.");
    }
    public static void main(String[] args) { verifyLedger(); }
    private static void verifyLedger() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        NBTTagList rows = new NBTTagList();
        for (int i = 0; i < 10000; i++) rows.appendTag(row("history-" + i, first, false));
        rows.appendTag(row("pending-a", first, true));
        rows.appendTag(row("pending-b", first, true));
        rows.appendTag(row("pending-c", second, true));
        NBTTagCompound input = new NBTTagCompound(); input.setTag("grants", rows);
        NBTTagCompound receipts = new NBTTagCompound();
        receipts.setString("opening-1", first + "/ember_saber/1");
        input.setTag("receipts", receipts);
        RewardLedger ledger = new RewardLedger(); ledger.readFromNBT(input);
        require(ledger.pendingCount(first) == 2, "history polluted first player's queue");
        require(ledger.pendingCount(second) == 1, "queues were not separated by owner");
        NBTTagCompound saved = ledger.writeToNBT(new NBTTagCompound());
        require(saved.getTagList("grants", 10).tagCount() == 10003, "grant round trip lost rows");
        require(saved.getCompoundTag("receipts").getString("opening-1").equals(first + "/ember_saber/1"), "receipt round trip changed request");
        RewardLedger restored = new RewardLedger(); restored.readFromNBT(saved);
        require(restored.pendingCount(first) == 2 && restored.pendingCount(second) == 1, "pending index did not rebuild");
        // Duplicate serialized IDs must not leave stale entries in the previous owner's queue.
        NBTTagList duplicate = new NBTTagList();
        duplicate.appendTag(row("same-id", first, true));
        duplicate.appendTag(row("same-id", second, true));
        NBTTagCompound replacement = new NBTTagCompound(); replacement.setTag("grants", duplicate);
        restored.readFromNBT(replacement);
        require(restored.pendingCount(first) == 0 && restored.pendingCount(second) == 1, "duplicate serial left a stale owner index");
        restored.readFromNBT(new NBTTagCompound());
        require(restored.pendingCount(second) == 0, "reload did not clear queues");
        org.apache.logging.log4j.LogManager.getLogger("ValeOrders").info(
            "VALE_LEDGER_SELFTEST_PASS: 10003-row ledger round trip, receipts, owner queues and reload checks.");
    }
    private static NBTTagCompound row(String serial, UUID owner, boolean pending) {
        NBTTagCompound row = new NBTTagCompound();
        row.setString("serial", serial); row.setString("owner", owner.toString());
        row.setString("item", "valeorders:ember_saber"); row.setBoolean("pending", pending);
        return row;
    }
}
