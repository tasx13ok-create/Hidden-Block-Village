package village.residents;

import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;

/** Native clickable chat uses an opaque server token and an authored choice ID only. */
public final class ResidentChooseCommand extends CommandBase {
    @Override public String getName(){return "residentchoose";}
    @Override public String getUsage(ICommandSender sender){return "/residentchoose <conversation> <choice>";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{
        if(args.length!=2||args[0].length()>36||args[1].length()>48)throw new WrongUsageException(getUsage(sender));
        DialogueSessions.choose(getCommandSenderAsPlayer(sender),args[0],args[1]);
    }
}
