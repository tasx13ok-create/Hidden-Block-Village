package village.vale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

public final class Starter {
    private Starter() {}
    public static boolean choose(EntityPlayerMP player, Order order) {
        NBTTagCompound state = PlayerState.data(player);
        String chosen = state.getString("starterOrder");
        if (!chosen.isEmpty() && !chosen.equals(order.key)) return false;
        state.setString("starterOrder", order.key);
        // Save the choice before issuance; every piece has a stable retry receipt.
        player.getServer().getWorld(0).getSaveHandler().getPlayerNBTManager().writePlayerData(player);
        RewardLedger ledger = RewardLedger.get(player);
        for (String part : new String[] {"helmet", "chestplate", "leggings", "boots", "saber"})
            ledger.issue(player, order.key + "_" + part, 1, "starter/" + player.getUniqueID() + "/" + part);
        ledger.issue(player, "field_manual", 1, "starter/" + player.getUniqueID() + "/manual");
        Progression.unlock(player);
        HudNetwork.dirty(player);
        return true;
    }
}
