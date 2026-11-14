package village.vale;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

public final class ValeCommand extends CommandBase {
    @Override public String getName() { return "vale"; }
    @Override public String getUsage(ICommandSender sender) { return "/vale join <order> | status | arts | missions | provisions | form <key> | summon <key> | trial | dossier | catalog. Admin: boss <sentinel|duelist|sage|colossus>, fieldmob <0-2>, grant <player> <item> [1-16] [receipt], beast <player> <beast> <armor-part> [receipt], standard <player> <village> [receipt], target <entity-uuid> <allow|deny|default>"; }
    @Override public int getRequiredPermissionLevel() { return 0; }
    /** Vanilla 1.12 players can reject even level zero for an unlisted command. */
    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return true; }
    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if(AdventureCommands.handle(server,sender,args))return;
        if(args.length>=3&&args.length<=4&&args[0].equals("standard")){
            if(!sender.canUseCommand(4,getName()))throw new CommandException("commands.generic.permission");
            EntityPlayerMP player=getPlayer(server,sender,args[1]);String receipt=args.length==4?args[3]:"";
            if(!receipt.isEmpty()&&!receipt.matches("[A-Za-z0-9_./:-]{1,128}"))throw new CommandException("Invalid receipt identifier.");
            try{boolean fresh=RewardLedger.get(player).issueStandard(player,args[2],receipt);sender.sendMessage(new TextComponentString((fresh?"Recorded ":"Previously recorded ")+args[2]+" standard; full or dead-player rewards remain queued."));}
            catch(IllegalArgumentException ex){throw new CommandException(ex.getMessage());}return;
        }
        if(args.length>=4&&args.length<=5&&args[0].equals("beast")){
            if(!sender.canUseCommand(4,getName()))throw new CommandException("commands.generic.permission");
            EntityPlayerMP player=getPlayer(server,sender,args[1]);String key=args[2]+"_"+args[3],receipt=args.length==5?args[4]:"";
            if(!receipt.isEmpty()&&!receipt.matches("[A-Za-z0-9_./:-]{1,128}"))throw new CommandException("Invalid receipt identifier.");
            try{boolean fresh=RewardLedger.get(player).issueOwned(player,key,receipt);sender.sendMessage(new TextComponentString((fresh?"Recorded ":"Previously recorded ")+key+"; full or dead-player rewards remain queued."));}
            catch(IllegalArgumentException ex){throw new CommandException(ex.getMessage());}
            return;
        }
        if (args.length == 2 && args[0].equals("join")) {
            Order chosen = null;
            for (Order order : Order.values()) if (order.key.equals(args[1])) chosen = order;
            if (chosen == null) throw new CommandException("Choose ember, current, gale, flint, bloom, rime, storm, dusk or dawn.");
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            if (!Starter.choose(player, chosen)) throw new CommandException("Your starter order is already chosen. Use /vale status.");
            sender.sendMessage(new TextComponentString("Starter recorded for " + chosen.title + ". Equip the matching pieces and saber; right-click for your order skill, sneak + right-click for your weapon technique."));
            return;
        }
        if (args.length == 2 && args[0].equals("boss")) {
            if (!sender.canUseCommand(4, getName())) throw new CommandException("commands.generic.permission");
            int kind = java.util.Arrays.asList(Bosses.KEYS).indexOf(args[1]);
            if (kind < 0) throw new WrongUsageException(getUsage(sender));
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            net.minecraft.util.math.Vec3d at = player.getPositionVector().add(player.getLookVec().scale(4));
            TrialKeeper boss = new TrialKeeper(player.world); boss.setPosition(at.x, player.posY, at.z); boss.configure(kind);
            if (!player.world.getCollisionBoxes(boss, boss.getEntityBoundingBox()).isEmpty() || !player.world.spawnEntity(boss))
                throw new CommandException("Need an open arena to spawn this boss.");
            sender.sendMessage(new TextComponentString(Bosses.NAMES[kind] + " spawned with a 24-block arena boundary."));
            return;
        }
        if (args.length == 1 && args[0].equals("status")) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            sender.sendMessage(new TextComponentString("Shinobi level " + PlayerState.level(player) +
                " | " + village.vale.bridge.ResourceText.balance(player) + " | XP " + PlayerState.data(player).getInteger("xp") +
                " | Starter " + PlayerState.data(player).getString("starterOrder") +
                " | Merit " + PlayerState.data(player).getInteger("merit") + " | Contracts " + PlayerState.data(player).getInteger("missionsCompleted") +
                " | Pending rewards " + RewardLedger.get(player).pendingCount(player.getUniqueID())));
            return;
        }
        if (args.length == 1 && args[0].equals("catalog")) {
            sender.sendMessage(new TextComponentString(String.join(", ", Gear.ITEMS.keySet())));
            return;
        }
        if (args.length == 3 && args[0].equals("target")) {
            if (!sender.canUseCommand(4, getName())) throw new CommandException("commands.generic.permission");
            java.util.UUID uuid;
            try { uuid = java.util.UUID.fromString(args[1]); }
            catch (IllegalArgumentException ex) { throw new CommandException("Invalid entity UUID."); }
            int policy;
            if (args[2].equals("allow")) policy = 1;
            else if (args[2].equals("deny")) policy = -1;
            else if (args[2].equals("default")) policy = 0;
            else throw new WrongUsageException(getUsage(sender));
            for (net.minecraft.world.WorldServer world : net.minecraftforge.common.DimensionManager.getWorlds()) {
                net.minecraft.entity.Entity entity = world.getEntityFromUuid(uuid);
                if (!(entity instanceof net.minecraft.entity.EntityLivingBase)) continue;
                if (entity instanceof net.minecraft.entity.player.EntityPlayer)
                    throw new CommandException("Player targeting is controlled by PvP and team rules.");
                entity.getEntityData().setInteger(Targets.KEY, policy);
                sender.sendMessage(new TextComponentString("Vale targeting for " + entity.getName() + ": " + args[2]));
                return;
            }
            throw new CommandException("Living entity is not loaded. Stand nearby before marking it.");
        }
        if (args.length < 3 || args.length > 5 || !args[0].equals("grant")) throw new WrongUsageException(getUsage(sender));
        if (!sender.canUseCommand(4, getName())) throw new CommandException("commands.generic.permission");
        EntityPlayerMP player = getPlayer(server, sender, args[1]);
        String key = args[2].startsWith(ValeOrders.ID + ":") ? args[2].substring(ValeOrders.ID.length()+1) : args[2];
        if (!Gear.ITEMS.containsKey(key)) throw new CommandException("Unknown Vale item: " + key);
        int count = args.length >= 4 ? parseInt(args[3], 1, 16) : 1;
        String receipt = args.length == 5 ? args[4] : "";
        if (!receipt.isEmpty() && !receipt.matches("[A-Za-z0-9_./:-]{1,128}")) throw new CommandException("Invalid receipt identifier.");
        try {
            boolean fresh = RewardLedger.get(player).issue(player, key, count, receipt);
            sender.sendMessage(new TextComponentString((fresh ? "Recorded " : "Previously recorded ") + count + " " + key +
                " for " + player.getName() + "; inventory-full rewards remain queued."));
        } catch (IllegalArgumentException ex) { throw new CommandException(ex.getMessage()); }
    }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos pos) {
        if(args.length==0)return Collections.emptyList();
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "status", "catalog", "join", "boss", "grant", "beast", "standard", "target", "arts", "art", "study", "missions", "mission", "journal", "dossier", "form", "summon", "trial", "fieldmob", "provision", "provisions");
        if(args.length==2&&args[0].equals("provisions"))return getListOfStringsMatchingLastWord(args,"ward","spirit","tools","ember","current","gale","flint","bloom","rime","storm","dusk","dawn");
        if(args.length==2&&args[0].equals("form"))return getListOfStringsMatchingLastWord(args,Forms.KEYS);
        if(args.length==2&&args[0].equals("summon"))return getListOfStringsMatchingLastWord(args,"reedling","cairnling","lantern","dismiss");
        if(args.length==2&&args[0].equals("mission"))return getListOfStringsMatchingLastWord(args,"accept","claim","abandon");
        if(args.length==3&&args[0].equals("mission")&&args[1].equals("accept"))return getListOfStringsMatchingLastWord(args,Missions.ALL.keySet());
        if(args.length==2&&args[0].equals("art"))return getListOfStringsMatchingLastWord(args,"select","cast");
        if(args.length==3&&args[0].equals("art")&&args[1].equals("select")&&sender instanceof EntityPlayerMP){java.util.List<String> keys=new java.util.ArrayList<>();for(Arts.Art a:Arts.available((EntityPlayerMP)sender))keys.add(a.key);return getListOfStringsMatchingLastWord(args,keys);}
        if(args[0].equals("standard")&&sender.canUseCommand(4,getName())){
            if(args.length==2)return getListOfStringsMatchingLastWord(args,server.getOnlinePlayerNames());
            if(args.length==3)return getListOfStringsMatchingLastWord(args,"stone","sand","mist","leaf","cloud");
        }
        if(args[0].equals("beast")&&sender.canUseCommand(4,getName())){
            if(args.length==2)return getListOfStringsMatchingLastWord(args,server.getOnlinePlayerNames());
            if(args.length==3)return getListOfStringsMatchingLastWord(args,OwnedArmor.beasts());
            if(args.length==4)return getListOfStringsMatchingLastWord(args,"helmet","chestplate","leggings","boots");
        }
        if (args.length == 2 && args[0].equals("join")) return getListOfStringsMatchingLastWord(args, "ember", "current", "gale", "flint", "bloom", "rime", "storm", "dusk", "dawn");
        if (args.length == 2 && args[0].equals("boss") && sender.canUseCommand(4, getName())) return getListOfStringsMatchingLastWord(args, Bosses.KEYS);
        if (args.length == 3 && args[0].equals("target") && sender.canUseCommand(4, getName()))
            return getListOfStringsMatchingLastWord(args, "allow", "deny", "default");
        if (args[0].equals("grant") && sender.canUseCommand(4, getName())) {
            if (args.length == 2) return getListOfStringsMatchingLastWord(args, server.getOnlinePlayerNames());
            if (args.length == 3) return getListOfStringsMatchingLastWord(args, Gear.ITEMS.keySet());
        }
        return Collections.emptyList();
    }
}
