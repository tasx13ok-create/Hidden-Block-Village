package village.vale.expedition;

import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;

/** No earn, spawn, accept or operator shortcut: only a previously issued nonce. */
public final class GateChoiceCommand extends CommandBase {
    public String getName(){return "valegatechoose";}
    public String getUsage(ICommandSender sender){return "/valegatechoose <conversation token> <offered choice>";}
    public int getRequiredPermissionLevel(){return 0;}
    public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{
        if(args.length!=2||!args[0].matches("[0-9a-f-]{36}")||!args[1].matches("[a-z0-9_]{1,64}"))throw new WrongUsageException(getUsage(sender));
        if(!GateRuntime.choose(getCommandSenderAsPlayer(sender),args[0],args[1]))throw new CommandException("This gate conversation expired. Speak to its keeper again.");
    }
}
