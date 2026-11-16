package narutodestinationvillages.compat;
import java.util.*;import net.minecraft.command.*;import net.minecraft.entity.player.*;import net.minecraft.util.text.TextComponentString;
/** Original replacement for the old dependency's affiliation entry point. */
public final class CommandAffiliation extends LegacyCommand {
 public static final String[] affiliations={"None","Akatsuki","Amegakure","Getsugakure","Hoshigakure","Ishigakure","Iwa","Jomae","Kiri","Konoha","Kumo","Kusagakure","Nadeshiko","Otogakure","RyuchiCave","Shimogakure","Suna","Takigakure","Takumi","Tanigakure","MountMyoboku","Yugagakure","Yukigakure","Yumegakure","Shikotsu"};
 public String getName(){return "villageaffiliation";}public int getRequiredPermissionLevel(){return 0;}
 public String getUsage(ICommandSender sender){return "/villageaffiliation join <village> | set <player> <village> (OP)";}
 private static int find(String name){for(int i=0;i<affiliations.length;i++)if(affiliations[i].equalsIgnoreCase(name))return i;return -1;}
 public void legacyExecute(ICommandSender sender,String[] args){
  EntityPlayerMP target;String village;
  if(args.length==2&&args[0].equalsIgnoreCase("join")&&sender instanceof EntityPlayerMP){target=(EntityPlayerMP)sender;if(ExtendedPlayer.get(target).getAffiliation()!=0)throw new LegacyUsageException("You already belong to a village. An operator must transfer your membership.");village=args[1];}
  else if(args.length==3&&args[0].equalsIgnoreCase("set")&&sender.canUseCommand(2,getName())){target=sender.getServer().getPlayerList().getPlayerByUsername(args[1]);if(target==null)throw new LegacyUsageException("Player is not online");village=args[2];}
  else throw new LegacyUsageException(getUsage(sender));
  int id=find(village);if(id<0)throw new LegacyUsageException("Unknown village. Available: "+String.join(", ",affiliations));
  ExtendedPlayer profile=ExtendedPlayer.get(target);profile.setAffiliation(id);profile.setRank(NarutoRank.VILLAGER.getId());profile.updateRecord();PacketDispatcher.sendTo(new SyncRankMessage(target),target);
  target.sendMessage(new TextComponentString("Village: "+affiliations[id]));if(target!=sender)sender.sendMessage(new TextComponentString("Updated "+target.getName()+" village to "+affiliations[id]));
 }
 public List<String> legacyTab(ICommandSender sender,String[] args){if(args.length==1)return getListOfStringsMatchingLastWord(args,"join","set");if(args.length==2&&args[0].equalsIgnoreCase("join")||args.length==3&&args[0].equalsIgnoreCase("set"))return getListOfStringsMatchingLastWord(args,affiliations);return Collections.emptyList();}
}
