package village.vale.bridge;
import java.io.IOException;import java.util.*;
import net.minecraft.command.*;import net.minecraft.entity.player.EntityPlayerMP;import net.minecraft.server.MinecraftServer;import net.minecraft.util.math.BlockPos;import net.minecraft.util.text.TextComponentString;

/** Permissioned mapping/discovery and clearly labeled cosmetic preview, never a native cast command. */
public final class BridgeCommand extends CommandBase {
    @Override public String getName(){return "narutobridge";}
    @Override public int getRequiredPermissionLevel(){return 2;}
    @Override public String getUsage(ICommandSender sender){return "/narutobridge status|reload|discover|map <style>|mapentity <id> <style> <owner>|mapdamage <id> <style>|unmap [kind when id]|test <style>";}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{
        if(!sender.canUseCommand(2,getName())||server==null||!server.isCallingFromMinecraftThread())throw new CommandException("Permission level2 on the server thread is required");
        if(args.length<1)throw new WrongUsageException(getUsage(sender));
        try{
            switch(args[0]){
                case "status":
                    if(args.length!=1)throw new WrongUsageException(getUsage(sender));tell(sender,"Naruto bridge: "+Bridge.mappingCount()+" exact mappings; "+Bridge.seenCount()+" observed IDs; dropped="+Bridge.discoveryDropped());
                    if(!Bridge.problem().isEmpty())tell(sender,Bridge.problem());
                    if(sender instanceof EntityPlayerMP){EntityPlayerMP p=(EntityPlayerMP)sender;ChakraLink.View view=MoveCost.view(p);tell(sender,"Provider="+view.provider+" state="+view.state+"; held="+Bridge.heldId(p));}return;
                case "reload":if(args.length!=1)throw new WrongUsageException(getUsage(sender));Bridge.reload();tell(sender,"Validated and reloaded all "+Bridge.mappingCount()+" mappings atomically");return;
                case "discover":if(args.length!=1)throw new WrongUsageException(getUsage(sender));tell(sender,"Exported "+Bridge.exportRegistry()+" exact registry IDs; this is discovery, not cast or mapping verification");return;
                case "map":{
                    if(args.length!=2)throw new WrongUsageException(getUsage(sender));EntityPlayerMP p=getCommandSenderAsPlayer(sender);String id=Bridge.heldId(p);if(id==null)throw new CommandException("Hold a registered item");
                    Bridge.mapping(new BridgeMappings.Row("item",id,"melee","direct_player",args[1]),null);tell(sender,"Mapped "+id+" for accepted direct melee only; use mapentity for admitted native jutsu effects");return;
                }
                case "mapentity":if(args.length!=4)throw new WrongUsageException(getUsage(sender));Bridge.mapping(new BridgeMappings.Row("entity",args[1],"spawn",args[3],args[2]),null);tell(sender,"Mapped admitted entity "+args[1]+" with exact owner policy "+args[3]);return;
                case "mapdamage":if(args.length!=3)throw new WrongUsageException(getUsage(sender));Bridge.mapping(new BridgeMappings.Row("entity",args[1],"damage","damage_source",args[2]),null);tell(sender,"Mapped accepted native damage from "+args[1]);return;
                case "unmap":{
                    String kind,when,id;if(args.length==1){kind="item";when="melee";id=Bridge.heldId(getCommandSenderAsPlayer(sender));if(id==null)throw new CommandException("Hold a registered item");}
                    else if(args.length==4){kind=args[1];when=args[2];id=args[3];if(!(kind.equals("entity")&&(when.equals("spawn")||when.equals("damage"))||kind.equals("item")&&when.equals("melee"))||!BridgeMappings.validId(id))throw new CommandException("Exact kind, evidence and registry ID required");}
                    else throw new WrongUsageException(getUsage(sender));Bridge.mapping(null,kind+"/"+when+"/"+id);tell(sender,"Removed exact mapping "+kind+"/"+when+"/"+id);return;
                }
                case "test":if(args.length!=2)throw new WrongUsageException(getUsage(sender));if(!MoveVisuals.preview(getCommandSenderAsPlayer(sender),args[1]))throw new CommandException("Unknown style, invalid context or bounded cosmetic preview cooldown");return;
                default:throw new WrongUsageException(getUsage(sender));
            }
        }catch(IOException|IllegalStateException invalid){throw new CommandException("Bridge configuration unchanged: "+invalid.getMessage());}
    }
    private static void tell(ICommandSender sender,String text){sender.sendMessage(new TextComponentString(text));}
    @Override public List<String> getTabCompletions(MinecraftServer server,ICommandSender sender,String[] args,BlockPos pos){
        if(!sender.canUseCommand(2,getName()))return Collections.emptyList();
        if(args.length==1)return getListOfStringsMatchingLastWord(args,"status","reload","discover","map","mapentity","mapdamage","unmap","test");
        if(args.length==2&&(args[0].equals("map")||args[0].equals("test")))return getListOfStringsMatchingLastWord(args,MoveVisuals.styles());
        if(args.length==3&&(args[0].equals("mapentity")||args[0].equals("mapdamage")))return getListOfStringsMatchingLastWord(args,MoveVisuals.styles());
        if(args.length==4&&args[0].equals("mapentity"))return getListOfStringsMatchingLastWord(args,"thrower","arrow","ownable","naruto_projectile","naruto_flow");
        return Collections.emptyList();
    }
}
