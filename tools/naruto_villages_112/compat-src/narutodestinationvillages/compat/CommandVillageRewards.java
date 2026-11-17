package narutodestinationvillages.compat;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;

/** Operator issuer for the same reward stacks used by the PvP pool. */
public final class CommandVillageRewards extends LegacyCommand {
    @Override public String getName() { return "villagereward"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) { return "/villagereward <xp|sp|jp|reset> <1|5|10|50> [1-64] [player] | <dna|kg|nature|clan|sage> <form, 0=random> [1-64] [player] | reload"; }

    @Override public void legacyExecute(ICommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equals("reload")) {
            NativeXpRewards.reload();
            sender.sendMessage(new TextComponentString("Native village reward settings reloaded."));
            return;
        }
        boolean randomizer = args.length >= 2 && NativeRandomizers.isKind(args[0]);
        if (args.length < 2 || args.length > 4 || !randomizer && !java.util.Arrays.asList("xp","sp","jp","reset").contains(args[0])) throw new LegacyUsageException(getUsage(sender));
        int value, count;
        try { value = Integer.parseInt(args[1]); count = args.length >= 3 ? Integer.parseInt(args[2]) : 1; }
        catch (NumberFormatException invalid) { throw new LegacyUsageException(getUsage(sender)); }
        if ((randomizer ? !NativeRandomizers.validForm(args[0], value) : !NativeRewardItems.validValue(value)) || count < 1 || count > 64) throw new LegacyUsageException(getUsage(sender));
        EntityPlayerMP target = args.length == 4 ? sender.getServer().getPlayerList().getPlayerByUsername(args[3]) : sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
        if (target == null) throw new LegacyUsageException("Choose an online player.");
        String id = randomizer ? (args[0].equals("dna") ? "unidentifieddna" : args[0] + "randomizer") : args[0].equals("reset") ? "rpToken" : args[0] + "token";
        ItemStack stack = NativeRewardItems.create("NarutoDestination:" + id, value, count);
        target.inventory.addItemStackToInventory(stack);
        if (!stack.isEmpty()) target.dropItem(stack, false);
        target.inventory.markDirty();
        target.inventoryContainer.detectAndSendChanges();
        sender.sendMessage(new TextComponentString("Issued " + count + " "+args[0]+" token(s) to " + target.getName() + "."));
    }
}
