package village.vale;

import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

public final class AcademyCommand extends CommandBase {
    @Override public String getName(){return "valeacademy";}
    @Override public int getRequiredPermissionLevel(){return 0;}
    @Override public boolean checkPermission(MinecraftServer server,ICommandSender sender){return true;}
    @Override public String getUsage(ICommandSender sender){return "/valeacademy [journal] | choose <conversation> <choice>. Operator setup: /valeacademy build <x> <y> <z>";}
    @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{
        if(args.length>0&&args[0].equals("build")){
            if(!sender.canUseCommand(4,getName()))throw new CommandException("commands.generic.permission");
            if(args.length!=4)throw new WrongUsageException(getUsage(sender));
            BlockPos p=new BlockPos(parseInt(args[1],-29999800,29999800),parseInt(args[2],2,230),parseInt(args[3],-29999800,29999800));
            // The operator explicitly requests this bounded footprint. Load its
            // chunks before checking emptiness; never clear existing terrain.
            for(int x=(p.getX()-32)>>4;x<=((p.getX()+32)>>4);x++)for(int z=(p.getZ()-16)>>4;z<=((p.getZ()+48)>>4);z++)server.getWorld(0).getChunk(x,z);
            AcademyCourse course=AcademyCourse.get(server.getWorld(0));
            if(!course.build(server.getWorld(0),p))throw new CommandException("Course placement refused: "+course.problem+". Use an entirely empty 65x65x10 volume with no entities and no existing course.");
            sender.sendMessage(new net.minecraft.util.text.TextComponentString("Academy placed. Arrival guide is at "+p.add(-24,1,0)+". Players use the residents and their journal; no OP is required for progression."));return;
        }
        if(args.length==0||args.length==1&&args[0].equals("journal")){AcademyCampaign.show(getCommandSenderAsPlayer(sender));return;}
        if(args.length==3&&args[0].equals("choose")&&args[1].length()==36&&args[2].matches("[a-z]{1,16}")){
            if(!AcademyCampaign.choose(getCommandSenderAsPlayer(sender),args[1],args[2]))throw new CommandException("Conversation expired, player moved, or objective is not ready. Speak to the guide again.");return;
        }
        throw new WrongUsageException(getUsage(sender));
    }
}
