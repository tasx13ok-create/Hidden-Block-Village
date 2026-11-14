package narutodestinationvillages.compat;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;

/** Operator issuer for the same reward stacks used by the PvP pool. */
public final class CommandVillageRewards extends LegacyCommand {
    @Override public String getName() { return "villagereward"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) { return "/villagereward xp <1|5|10|50> [1-64] [player] | reload"; }

    @Override public void legacyExecute(ICommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equals("reload")) {
            NativeXpRewards.reload();
            sender.sendMessage(new TextComponentString("Native village reward settings reloaded."));
            return;
        }
        if (args.length < 2 || args.length > 4 || !args[0].equals("xp")) throw new LegacyUsageException(getUsage(sender));
        int value, count;
        try { value = Integer.parseInt(args[1]); count = args.length >= 3 ? Integer.parseInt(args[2]) : 1; }
        catch (NumberFormatException invalid) { throw new LegacyUsageException(getUsage(sender)); }
        if (!NativeRewardItems.validValue(value) || count < 1 || count > 64) throw new LegacyUsageException(getUsage(sender));
        EntityPlayerMP target = args.length == 4 ? sender.getServer().getPlayerList().getPlayerByUsername(args[3]) : sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
        if (target == null) throw new LegacyUsageException("Choose an online player.");
        ItemStack stack = NativeRewardItems.create("NarutoDestination:xptoken", value, count);
        target.inventory.addItemStackToInventory(stack);
        if (!stack.isEmpty()) target.dropItem(stack, false);
        target.inventory.markDirty();
        target.inventoryContainer.detectAndSendChanges();
        sender.sendMessage(new TextComponentString("Issued " + count + " chakra XP token(s) to " + target.getName() + "."));
    }
}
