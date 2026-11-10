package village.vale;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

public final class PlayerState {
    private PlayerState() {}
    public static NBTTagCompound data(EntityPlayer player) {
        NBTTagCompound root = player.getEntityData();
        if (!root.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10))
            root.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        NBTTagCompound persisted = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (!persisted.hasKey("ValeOrders", 10)) {
            NBTTagCompound state = new NBTTagCompound();
            state.setInteger("resolve", 100);
            persisted.setTag("ValeOrders", state);
        }
        return persisted.getCompoundTag("ValeOrders");
    }
    public static int resolve(EntityPlayer player) { return Rules.clampResolve(data(player).getInteger("resolve")); }
    public static void setResolve(EntityPlayer player, int value) { data(player).setInteger("resolve", Rules.clampResolve(value));HudNetwork.dirty(player); }
    public static int level(EntityPlayer player) { return levelForXp(data(player).getInteger("xp")); }
    public static int levelForXp(int xp) { return Rules.levelForXp(xp); }
    public static void addXp(EntityPlayer player, int value) {
        NBTTagCompound state = data(player);
        state.setInteger("xp", (int)Math.min(1000000L, Math.max(0L, (long)state.getInteger("xp") + value)));
        HudNetwork.dirty(player);
    }
}
