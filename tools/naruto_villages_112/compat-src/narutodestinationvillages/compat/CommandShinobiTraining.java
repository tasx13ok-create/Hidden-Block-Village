package narutodestinationvillages.compat;
import java.util.*;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

public final class CommandShinobiTraining extends LegacyCommand {
 public String getName(){return "shinobitraining";}public int getRequiredPermissionLevel(){return 0;}
 public boolean legacyPermission(ICommandSender sender){return sender instanceof EntityPlayerMP;}
 public String getUsage(ICommandSender sender){return "/shinobitraining [status | spend <ninjutsu|taijutsu|kenjutsu|speed> <points> | reset | techniques | learn <number>]";}
 public void legacyExecute(ICommandSender sender,String[] args){
  if(!(sender instanceof EntityPlayerMP))throw new LegacyUsageException("Only players have a training menu.");EntityPlayerMP player=(EntityPlayerMP)sender;
  if(args.length==0){NativeTrainingMenu.open(player);return;}
  if(args.length==1&&args[0].equals("status")){sender.sendMessage(new TextComponentString("SP "+NativeTraining.balance(player,"skill")+" | JP "+NativeTraining.balance(player,"jutsu")+" | Reset credits "+NativeTraining.balance(player,"reset")));for(NativeTraining.Stat stat:NativeTraining.Stat.values())sender.sendMessage(new TextComponentString(stat.title+": "+NativeTraining.points(player,stat)));return;}
  if(args.length==3&&args[0].equals("spend")){int amount;try{amount=Integer.parseInt(args[2]);}catch(NumberFormatException invalid){throw new LegacyUsageException(getUsage(sender));}NativeTraining.Stat stat=NativeTraining.Stat.byKey(args[1]);if(!NativeTraining.spend(player,stat,amount))throw new LegacyUsageException("Not enough SP, invalid amount, training cap reached, or chakra integration unavailable.");sender.sendMessage(new TextComponentString("Training increased: "+stat.title+" +"+amount));return;}
  if(args.length==1&&args[0].equals("reset")){if(!NativeTraining.reset(player))throw new LegacyUsageException("Allocate training points and obtain a reset credit first.");sender.sendMessage(new TextComponentString("Training points reclaimed. Chakra emptied for retraining."));return;}
  if(args.length==1&&args[0].equals("techniques")){List<NativeLearning.Choice> choices=NativeLearning.choices(player);if(choices.isEmpty())throw new LegacyUsageException("Hold your own Naruto jutsu scroll.");for(int i=0;i<choices.size();i++){NativeLearning.Choice c=choices.get(i);sender.sendMessage(new TextComponentString((i+1)+". "+c.technique.getName()+" — "+c.cost+" JP"+(NativeLearning.learned(player.getHeldItemMainhand(),c)?" (learned)":"")));}return;}
  if(args.length==2&&args[0].equals("learn")){int index;try{index=Integer.parseInt(args[1])-1;}catch(NumberFormatException invalid){throw new LegacyUsageException(getUsage(sender));}List<NativeLearning.Choice> choices=NativeLearning.choices(player);if(index<0||index>=choices.size()||!NativeLearning.learn(player,choices.get(index)))throw new LegacyUsageException("Hold your own scroll, choose an unlearned technique, and obtain enough JP.");sender.sendMessage(new TextComponentString("Learned "+choices.get(index).technique.getName()+". Native chakra and mode requirements still apply."));return;}
  throw new LegacyUsageException(getUsage(sender));
 }
 public List<String> legacyTab(ICommandSender sender,String[] args){if(args.length==1)return getListOfStringsMatchingLastWord(args,"status","spend","reset","techniques","learn");if(args.length==2&&args[0].equals("spend"))return getListOfStringsMatchingLastWord(args,"ninjutsu","taijutsu","kenjutsu","speed");return Collections.emptyList();}
}
