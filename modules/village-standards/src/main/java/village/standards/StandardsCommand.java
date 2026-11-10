package village.standards;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

/** Explicit operator restoration when old custom variant data is unknown. */
public final class StandardsCommand extends CommandBase {
    @Override public String getName() { return "standards"; }
    @Override public int getRequiredPermissionLevel() { return 2; }
    @Override public String getUsage(ICommandSender sender) { return "/standards inspect | archive [page] | resolve <entity-uuid> <stone|sand|mist|leaf|cloud> | recover <archive-uuid>"; }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 1 && args[0].equals("inspect")) {
            int known = 0, unresolved = 0;
            for (WorldServer world : server.worlds) for (Entity entity : world.loadedEntityList) {
                if (!(entity instanceof EntityStandard)) continue;
                EntityStandard standard = (EntityStandard) entity;
                if (standard.variant() != null) known++;
                else {
                    unresolved++;
                    if (unresolved <= 20) sender.sendMessage(new TextComponentString("Unassigned " + standard.getUniqueID()
                        + " in dimension " + world.provider.getDimension() + " at " + standard.anchor()));
                }
            }
            sender.sendMessage(new TextComponentString("Loaded village standards: " + known + " assigned, " + unresolved + " unassigned. Unloaded chunks were not searched."));
            sender.sendMessage(new TextComponentString("First-seen unresolved records in the saved recovery archive: " + StandardsArchive.get(server.getWorld(0)).size() + ". List their IDs with /standards archive."));
            return;
        }
        if ((args.length == 1 || args.length == 2) && args[0].equals("archive")) {
            StandardsArchive archive = StandardsArchive.get(server.getWorld(0));
            int pages = Math.max(1, (archive.size() + 19) / 20);
            int page = args.length == 2 ? parseInt(args[1], 1, pages) : 1;
            sender.sendMessage(new TextComponentString("Standard recovery archive page " + page + "/" + pages + " (" + archive.size() + " originals):"));
            for (UUID origin : archive.origins((page - 1) * 20, 20)) {
                NBTTagCompound data = archive.copy(origin);
                sender.sendMessage(new TextComponentString(origin + " at original block " + data.getInteger("TileX") + ","
                    + data.getInteger("TileY") + "," + data.getInteger("TileZ") + "; /standards recover " + origin));
            }
            return;
        }
        if (args.length == 2 && args[0].equals("recover")) {
            UUID origin;
            try { origin = UUID.fromString(args[1]); } catch (IllegalArgumentException invalid) { throw new CommandException("Invalid archive UUID"); }
            NBTTagCompound data = StandardsArchive.get(server.getWorld(0)).copy(origin);
            if (data == null) throw new CommandException("That recovery record is not in the archive");
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            ItemStack recovered = StandardRecord.read(data).drop();
            if (player.inventory.getFirstEmptyStack() < 0) throw new CommandException("Free one inventory slot before recovering this standard");
            if (!player.inventory.addItemStackToInventory(recovered)) throw new CommandException("The recovery item could not be inserted");
            player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges();
            sender.sendMessage(new TextComponentString("Created one operator recovery copy for " + origin + ". The archived original is retained; this does not remove other copies."));
            return;
        }
        if (args.length != 3 || !args[0].equals("resolve")) throw new WrongUsageException(getUsage(sender));
        UUID id;
        try { id = UUID.fromString(args[1]); } catch (IllegalArgumentException invalid) { throw new CommandException("Invalid entity UUID"); }
        StandardVariant choice = StandardVariant.byKey(args[2]);
        if (choice == null) throw new WrongUsageException(getUsage(sender));
        for (WorldServer world : server.worlds) {
            Entity entity = world.getEntityFromUuid(id);
            if (entity instanceof EntityStandard && !entity.isDead) {
                EntityStandard standard = (EntityStandard) entity;
                if (standard.variant() != null) throw new CommandException("This standard already has an assigned village");
                standard.assign(choice);
                sender.sendMessage(new TextComponentString("Assigned " + choice.key + " to " + id + "; retained its original saved metadata."));
                return;
            }
        }
        throw new CommandException("That standard is not loaded");
    }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos target) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "inspect", "archive", "resolve", "recover");
        if (args.length == 2 && args[0].equals("recover")) {
            List<String> ids = new ArrayList<>();
            for (UUID id : StandardsArchive.get(server.getWorld(0)).origins(0, 100)) ids.add(id.toString());
            return getListOfStringsMatchingLastWord(args, ids);
        }
        if (args.length == 3 && args[0].equals("resolve")) return getListOfStringsMatchingLastWord(args, "stone", "sand", "mist", "leaf", "cloud");
        if (args.length == 2 && args[0].equals("resolve")) {
            List<String> ids = new ArrayList<>();
            for (WorldServer world : server.worlds) for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityStandard && ((EntityStandard) entity).variant() == null && !entity.isDead && ids.size() < 100) ids.add(entity.getUniqueID().toString());
            }
            return getListOfStringsMatchingLastWord(args, ids);
        }
        return Collections.emptyList();
    }
}
