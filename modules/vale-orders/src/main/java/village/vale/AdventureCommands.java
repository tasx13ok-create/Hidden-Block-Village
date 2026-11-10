package village.vale;

import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.Vec3d;

/** All new player commands validate progression/resources in the same server methods as item use. */
public final class AdventureCommands {
    private AdventureCommands(){}
    public static boolean handle(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{
        if(args.length==0)return false;String verb=args[0];
        if(!java.util.Arrays.asList("arts","art","study","missions","mission","journal","dossier","form","summon","trial","fieldmob","provision","provisions").contains(verb))return false;
        if(verb.equals("fieldmob")&&!sender.canUseCommand(4,"vale"))throw new CommandException("commands.generic.permission");
        EntityPlayerMP p=CommandBase.getCommandSenderAsPlayer(sender);
        if(verb.equals("provisions")&&args.length<=2){Quartermaster.show(p,args.length==2?args[1]:"ward");return true;}
        if(verb.equals("provision")&&args.length==2){Quartermaster.buy(p,args[1]);return true;}
        if(verb.equals("arts")&&args.length==1){Arts.show(p);return true;}
        if(verb.equals("art")){if(args.length==3&&args[1].equals("select"))Arts.select(p,args[2]);else if(args.length==2&&args[1].equals("cast"))Arts.cast(p);else throw new WrongUsageException("/vale art select <key> | /vale art cast");return true;}
        if(verb.equals("study")&&args.length==2){Arts.study(p,args[1]);return true;}
        if((verb.equals("missions")||verb.equals("journal"))&&args.length==1){Missions.show(p);return true;}
        if(verb.equals("dossier")&&args.length==1){Missions.dossier(p);return true;}
        if(verb.equals("mission")){
            if(args.length==3&&args[1].equals("accept"))Missions.accept(p,args[2]);
            else if(args.length==2&&args[1].equals("claim"))Missions.claim(p);
            else if(args.length==2&&args[1].equals("abandon"))Missions.abandon(p);
            else throw new WrongUsageException("/vale mission accept <key> | claim | abandon");return true;
        }
        if(verb.equals("form")&&args.length==2){int i=java.util.Arrays.asList(Forms.KEYS).indexOf(args[1]);if(i<0)throw new WrongUsageException("/vale form bastion|zephyr|oracle|eclipse");Forms.activate(p,i);return true;}
        if(verb.equals("summon")&&args.length==2){if(args[1].equals("dismiss"))Summons.dismiss(p);else{int i=java.util.Arrays.asList(Summons.KEYS).indexOf(args[1]);if(i<0)throw new WrongUsageException("/vale summon reedling|cairnling|lantern|dismiss");Summons.activate(p,i);}return true;}
        if(verb.equals("trial")&&args.length==1){Adventures.trial(p,false);return true;}
        if(verb.equals("fieldmob")&&args.length==2){int kind=CommandBase.parseInt(args[1],0,2);Vec3d at=p.getPositionVector().add(p.getLookVec().scale(4));FieldRogue rogue=new FieldRogue(p.world);rogue.setPosition(at.x,p.posY,at.z);rogue.configure(kind);if(!p.world.getCollisionBoxes(rogue,rogue.getEntityBoundingBox()).isEmpty()||!p.world.spawnEntity(rogue))throw new CommandException("Need clear space.");return true;}
        throw new WrongUsageException("/vale arts | missions | mission accept/claim/abandon | study <order> | form <key> | summon <key> | trial | dossier");
    }
}
