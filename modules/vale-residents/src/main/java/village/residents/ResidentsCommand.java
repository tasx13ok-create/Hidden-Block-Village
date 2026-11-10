package village.residents;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

public final class ResidentsCommand extends CommandBase {
    @Override public String getName() { return "valeresidents"; }
    @Override public String getUsage(ICommandSender sender) { return "/valeresidents status|provision|reload | title <role> <name> | move <role> <x> <y> <z>"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) throw new WrongUsageException(getUsage(sender));
        WorldServer world = server.getWorld(0);
        if (world == null) throw new CommandException("The overworld is unavailable.");
        if (args[0].equals("provision") && args.length == 1) {
            sender.sendMessage(new TextComponentString(Provisioning.run(world)));
        } else if (args[0].equals("reload") && args.length == 1) {
            sender.sendMessage(new TextComponentString(DialogueBook.reload()));
        } else if (args[0].equals("title") && args.length >= 3) {
            ResidentRole role=ResidentRole.fromId(args[1]);
            if(role==null||!ResidentAuthoring.title(world,role,buildString(args,2)))throw new CommandException("Name rejected or resident is not loaded; saved slots were preserved.");
            sender.sendMessage(new TextComponentString("Resident name saved for "+role.id+"."));
        } else if (args[0].equals("move") && args.length == 5) {
            ResidentRole role=ResidentRole.fromId(args[1]);
            BlockPos feet=parseBlockPos(sender,args,2,false);
            if(role==null||!ResidentAuthoring.move(world,role,feet))throw new CommandException("Move rejected: use safe, empty, already loaded ground for a loaded supported resident.");
            sender.sendMessage(new TextComponentString("Resident and saved home moved for "+role.id+" to "+feet+"."));
        } else if (args[0].equals("status") && args.length == 1) {
            ResidentsData data = ResidentsData.get(world);
            sender.sendMessage(new TextComponentString("Vale Residents 0.2.0 | cap " + ValeResidents.maxResidents
                + " | auto " + ValeResidents.autoProvision + " | saved schema writable " + !data.slots.isReadOnly()));
            for (ResidentRole role : ResidentRole.values()) {
                UUID id = data.slots.uuid(role);
                String status = !data.slots.occupied(role) ? "unassigned" : id == null ? "reserved/malformed/conflict; preserved"
                    : world.getEntityFromUuid(id) == null ? "reserved; entity currently unloaded or missing" : "loaded";
                sender.sendMessage(new TextComponentString(role.title + ": " + status + (id == null ? "" : " | " + id)));
            }
        } else throw new WrongUsageException(getUsage(sender));
    }

    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender,
                                                    String[] args, @Nullable BlockPos pos) {
        if(args.length==1)return getListOfStringsMatchingLastWord(args,Arrays.asList("status","provision","reload","title","move"));
        if(args.length==2&&(args[0].equals("title")||args[0].equals("move")))return getListOfStringsMatchingLastWord(args,Arrays.asList("academy","contracts","quartermaster"));
        return Collections.emptyList();
    }
}
