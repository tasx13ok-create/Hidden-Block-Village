package village.encounters;
import java.util.*;
import net.minecraft.command.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.registry.EntityRegistry;
@Mod(modid=Encounters.ID,name="Vale Original Encounters",version="0.3.6",dependencies="required-after:valeorders@[0.3.6,)",acceptedMinecraftVersions="[1.12.2]")
public final class Encounters {
 public static final String ID="valeencounters";
 @SidedProxy(clientSide="village.encounters.ClientProxy",serverSide="village.encounters.CommonProxy")public static CommonProxy proxy;
 @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){Profiles.load();EntityRegistry.registerModEntity(new ResourceLocation(ID,"mob"),Actors.Mob.class,"original_mob",1,this,64,3,true);EntityRegistry.registerModEntity(new ResourceLocation(ID,"boss"),Actors.Boss.class,"original_boss",2,this,80,3,true);EntityRegistry.registerModEntity(new ResourceLocation(ID,"companion"),Actors.Companion.class,"original_companion",3,this,64,3,true);proxy.renderers();}
 @Mod.EventHandler public void starting(FMLServerStartingEvent event){event.registerServerCommand(new Command());}
 public static Actors.Actor create(net.minecraft.world.World world,String key){Profiles.Profile p=Profiles.get(key);if(p==null)throw new IllegalArgumentException("Unknown original profile");Actors.Actor actor=p.kind.equals("boss")?new Actors.Boss(world):p.kind.equals("companion")?new Actors.Companion(world):new Actors.Mob(world);actor.configure(key);return actor;}
 /** Server-owned hook for a future audited core ability; callers enforce lesson/cost/cooldown. */
 public static Actors.Companion summon(EntityPlayerMP player,String key){Profiles.Profile p=Profiles.get(key);if(p==null||!p.kind.equals("companion")||!player.isEntityAlive()||player.isSpectator()||player.world.isRemote)return null;for(Entity e:player.world.loadedEntityList)if(e instanceof Actors.Companion&&player.getUniqueID().equals(((Actors.Companion)e).ownerId())&&e.isEntityAlive())return null;Actors.Companion companion=(Actors.Companion)create(player.world,key);BlockPos at=Actors.Actor.landing(player.world,player.getPosition().add(2,0,2),companion);if(at==null)return null;companion.bind(player,key);companion.setPosition(at.getX()+.5,at.getY(),at.getZ()+.5);return player.world.spawnEntity(companion)?companion:null;}
 public static void show(EntityPlayerMP player){showList(player,1);}
 public static void showList(ICommandSender sender,int page){int i=0;for(Profiles.Profile p:Profiles.ALL.values())if(i++/8==page-1)sender.sendMessage(new TextComponentString(p.key+" | "+p.title+" | "+p.kind));sender.sendMessage(new TextComponentString("Roster "+page+"/17 | /valeencounter list <page> | /valeencounter info <profile>"));}
 public static boolean info(ICommandSender sender,String key){Profiles.Profile p=Profiles.get(key);if(p==null)return false;sender.sendMessage(new TextComponentString(p.title+" | "+p.kind+" | Health "+p.health));sender.sendMessage(new TextComponentString(p.kind.equals("companion")?"Support role: "+p.companionRole+" | Duration "+(p.duration/20)+"s":"Combat: "+p.primary+", "+p.secondary+" | Telegraph "+p.windup+" ticks"));if(p.kind.equals("boss"))sender.sendMessage(new TextComponentString("Changes approach at 65% and 30% health. Watch the marked attack area."));return true;}
 public static final class Command extends CommandBase {
  public String getName(){return "valeencounter";}public String getUsage(ICommandSender sender){return "/valeencounter list [page] | info <profile> | spawn <profile>";}public int getRequiredPermissionLevel(){return 0;}public boolean checkPermission(MinecraftServer server,ICommandSender sender){return true;}
  public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{
   if(args.length>=1&&args[0].equals("list")){int page=args.length==2?parseInt(args[1],1,17):1;showList(sender,page);return;}
   if(args.length==2&&args[0].equals("info")){if(!info(sender,args[1]))throw new CommandException("Unknown original profile.");return;}
   if(args.length!=2||!args[0].equals("spawn"))throw new WrongUsageException(getUsage(sender));if(!sender.canUseCommand(4,"valeencounter"))throw new CommandException("Operator level4 is required to spawn encounters.");EntityPlayerMP player=getCommandSenderAsPlayer(sender);Profiles.Profile p=Profiles.get(args[1]);if(p==null)throw new CommandException("Unknown original profile.");
   if(p.kind.equals("companion")){if(summon(player,p.key)==null)throw new CommandException("Need clear ground and no existing owned companion.");return;}
   int loaded=0;for(Entity e:player.world.loadedEntityList)if(e instanceof Actors.Actor&&e.isEntityAlive())loaded++;if(loaded>=128)throw new CommandException("Encounter population limit reached.");Actors.Actor actor=create(player.world,p.key);Vec3d front=player.getPositionVector().add(player.getLookVec().scale(5));BlockPos at=Actors.Actor.landing(player.world,new BlockPos(front),actor);if(at==null)throw new CommandException("Need clear loaded solid ground.");actor.setPosition(at.getX()+.5,at.getY(),at.getZ()+.5);if(!player.world.spawnEntity(actor))throw new CommandException("Spawn rejected by server.");sender.sendMessage(new TextComponentString("Spawned "+p.title+"."));
  }
 }
}
