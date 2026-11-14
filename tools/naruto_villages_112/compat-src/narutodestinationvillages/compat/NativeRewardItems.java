package narutodestinationvillages.compat;

import java.util.Locale;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Original server-owned reward stacks using a vanilla model and ordinary inventory synchronization. */
public final class NativeRewardItems {
    public static final String TAG = "NarutoVillagesReward";
    private NativeRewardItems() {}

    public static boolean supports(String id) {
        return id != null && "narutodestination:xptoken".equals(id.toLowerCase(Locale.ROOT));
    }

    public static boolean validValue(int value) {
        return value == 1 || value == 5 || value == 10 || value == 50;
    }

    public static ItemStack create(String id, int value, int count) {
        if (count < 1 || count > 64) throw new IllegalArgumentException("Reward count must be 1-64");
        if (!supports(id)) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            return item == null ? null : new ItemStack(item, count, value);
        }
        if (!validValue(value)) throw new IllegalArgumentException("XP token value must be 1, 5, 10 or 50");
        ItemStack stack = new ItemStack(Items.PAPER, count);
        NBTTagCompound root = new NBTTagCompound(), reward = new NBTTagCompound(), display = new NBTTagCompound();
        reward.setInteger("version", 1);
        reward.setString("kind", "chakra_xp");
        reward.setInteger("value", value);
        root.setTag(TAG, reward);
        display.setString("Name", "\u00a76Chakra Experience Token \u00a7e(" + value + ")");
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString("\u00a77Right-click to gain Naruto battle XP."));
        lore.appendTag(new NBTTagString("\u00a77Your village rank applies its XP bonus."));
        lore.appendTag(new NBTTagString("\u00a77Sneak + right-click to consume the stack."));
        display.setTag("Lore", lore);
        root.setTag("display", display);
        stack.setTagCompound(root);
        return stack;
    }

    /** Zero means ordinary paper or an invalid token. Never trust display text or client arguments. */
    public static int xpValue(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.PAPER || !stack.hasTagCompound()) return 0;
        NBTTagCompound root = stack.getTagCompound();
        if (!root.hasKey(TAG, 10)) return 0;
        NBTTagCompound reward = root.getCompoundTag(TAG);
        int value = reward.getInteger("value");
        return reward.getInteger("version") == 1 && "chakra_xp".equals(reward.getString("kind")) && validValue(value) ? value : 0;
    }
}
