package narutodestinationvillages.compat;
import java.util.*;import net.minecraft.command.*;import net.minecraft.server.MinecraftServer;import net.minecraft.util.math.BlockPos;
/** Original command-signature bridge; native permission checks still run. */
public abstract class LegacyCommand extends CommandBase {
 public abstract void legacyExecute(ICommandSender sender,String[] args);
 public List<String> legacyTab(ICommandSender sender,String[] args){return Collections.emptyList();}
 public boolean legacyPermission(ICommandSender sender){return sender.canUseCommand(getRequiredPermissionLevel(),getName());}
 @Override public final void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{
  try{legacyExecute(sender,args);}catch(LegacyUsageException usage){throw new WrongUsageException(usage.getMessage(),usage.arguments);}
 }
 @Override public final boolean checkPermission(MinecraftServer server,ICommandSender sender){return legacyPermission(sender);}
 @Override public final List<String> getTabCompletions(MinecraftServer server,ICommandSender sender,String[] args,BlockPos pos){return legacyTab(sender,args);}
}
