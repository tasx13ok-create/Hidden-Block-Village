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
        return legacyKind(id) != null;
    }
    private static String legacyKind(String id) {
        if(id==null)return null;
        switch(id.toLowerCase(Locale.ROOT)){
            case "narutodestination:xptoken":return "chakra_xp";
            case "narutodestination:sptoken":return "skill";
            case "narutodestination:jptoken":return "jutsu";
            case "narutodestination:rptoken":return "reset";
            default:return null;
        }
    }

    public static boolean validValue(int value) {
        return value == 1 || value == 5 || value == 10 || value == 50;
    }

    public static ItemStack create(String id, int value, int count) {
        if (count < 1 || count > 64) throw new IllegalArgumentException("Reward count must be 1-64");
        Item original = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if(original!=null)return new ItemStack(original,count,value);
        String kind=legacyKind(id);if(kind==null)return null;
        if (!validValue(value)) throw new IllegalArgumentException("Token value must be 1, 5, 10 or 50");
        ItemStack stack = new ItemStack(Items.PAPER, count);
        NBTTagCompound root = new NBTTagCompound(), reward = new NBTTagCompound(), display = new NBTTagCompound();
        reward.setInteger("version", 1);
        reward.setString("kind", kind);
        reward.setInteger("value", value);
        root.setTag(TAG, reward);
        String name="chakra_xp".equals(kind)?"Chakra Experience":"skill".equals(kind)?"Shinobi Skill":"jutsu".equals(kind)?"Jutsu Knowledge":"Training Reset";
        display.setString("Name", "\u00a76"+name+" Token \u00a7e(" + value + ")");
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString("chakra_xp".equals(kind)?"\u00a77Right-click to gain Naruto battle XP.":"\u00a77Right-click to bank "+kind+" points."));
        lore.appendTag(new NBTTagString("chakra_xp".equals(kind)?"\u00a77Your village rank applies its XP bonus.":"\u00a77Open /shinobitraining to spend them."));
        lore.appendTag(new NBTTagString("\u00a77Sneak + right-click to consume the stack."));
        display.setTag("Lore", lore);
        root.setTag("display", display);
        stack.setTagCompound(root);
        return stack;
    }

    /** Zero means ordinary paper or an invalid token. Never trust display text or client arguments. */
    public static int xpValue(ItemStack stack) {
        return "chakra_xp".equals(kind(stack))?pointValue(stack):0;
    }
    public static int pointValue(ItemStack stack){return kind(stack)==null?0:stack.getTagCompound().getCompoundTag(TAG).getInteger("value");}
    public static String kind(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.PAPER || !stack.hasTagCompound()) return null;
        NBTTagCompound root = stack.getTagCompound();
        if (!root.hasKey(TAG, 10)) return null;
        NBTTagCompound reward = root.getCompoundTag(TAG);
        int value = reward.getInteger("value");
        String kind=reward.getString("kind");boolean recognized="chakra_xp".equals(kind)||"skill".equals(kind)||"jutsu".equals(kind)||"reset".equals(kind);
        return reward.getInteger("version")==1&&recognized&&validValue(value)?kind:null;
    }
}
