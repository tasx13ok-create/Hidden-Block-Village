package village.fieldhud;

import java.util.*;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.IClientCommand;

public final class HudCommand extends CommandBase implements IClientCommand {
    public String getName() { return "fieldhud"; }
    public String getUsage(ICommandSender sender) { return "/fieldhud [mode|reload|mark <build> <pasteX> <pasteY> <pasteZ>|remove <build>|list]"; }
    public int getRequiredPermissionLevel() { return 0; }
    // Local presentation controls and personal zone labels are public to every player.
    @Override public boolean checkPermission(MinecraftServer server,ICommandSender sender) { return true; }
    public boolean allowUsageWithoutPrefix(ICommandSender sender,String message) { return false; }
    public void execute(MinecraftServer server,ICommandSender sender,String[] args) throws CommandException {
        if(args.length==0) { ClientHud.MC.addScheduledTask(()->ClientHud.MC.displayGuiScreen(new HudScreen())); return; }
        if(args[0].equals("mode")&&args.length==1) {
            HudConfig.mode=(HudConfig.mode+1)%3; HudConfig.save(); say(sender,"Field HUD: "+HudConfig.MODES[HudConfig.mode]); return;
        }
        if(args[0].equals("reload")&&args.length==1) {
            Regions.reload(); say(sender,Regions.error.isEmpty()?"Build zones reloaded.":Regions.error); return;
        }
        if(args[0].equals("list")&&args.length==1) {
            say(sender,"Build templates: "+String.join(", ",Regions.TEMPLATES.keySet()));
            String scope=Regions.scope(ClientHud.MC);
            for(Regions.Zone z:Regions.data.zones) if(z.world.equals(scope)) say(sender,z.id+" | dimension "+z.dimension+" | "+Arrays.toString(z.min)+" to "+Arrays.toString(z.max));
            return;
        }
        if(args[0].equals("mark")&&args.length==5) {
            if(!Regions.TEMPLATES.containsKey(args[1])) throw new CommandException("Unknown build. Use /fieldhud list.");
            int x=parseInt(args[2],-29999000,29999000),y=parseInt(args[3],-1024,2048),z=parseInt(args[4],-29999000,29999000);
            try {
                Regions.mark(ClientHud.MC,args[1],new BlockPos(x,y,z));
                say(sender,"Saved "+args[1]+" at unrotated WorldEdit paste anchor "+x+", "+y+", "+z+". Client location label only.");
            } catch(java.io.IOException e) { throw new CommandException("Could not save build zone; previous zones preserved."); }
            return;
        }
        if(args[0].equals("remove")&&args.length==2) {
            List<Regions.Zone> old=new ArrayList<>(Regions.data.zones);
            Regions.data.zones.removeIf(v->v.id.equals(args[1])&&v.world.equals(Regions.scope(ClientHud.MC))&&v.dimension==ClientHud.MC.player.dimension);
            try { Regions.save(); say(sender,"Removed matching zone in this world/dimension."); }
            catch(java.io.IOException e) { Regions.data.zones=old; throw new CommandException("Could not save zones."); }
            return;
        }
        throw new WrongUsageException(getUsage(sender));
    }
    private void say(ICommandSender sender,String text) { sender.sendMessage(new TextComponentString(text)); }
    public List<String> getTabCompletions(MinecraftServer server,ICommandSender sender,String[] args,BlockPos target) {
        if(args.length==1) return getListOfStringsMatchingLastWord(args,"mode","reload","mark","remove","list");
        if(args.length==2&&(args[0].equals("mark")||args[0].equals("remove"))) return getListOfStringsMatchingLastWord(args,Regions.TEMPLATES.keySet());
        return Collections.emptyList();
    }
}
