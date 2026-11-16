package village.dungeonguard;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import village.dungeonguard.core.HookStatus;

public final class GuardCommand extends CommandBase {
    private static final class Selection { int dimension; BlockPos first, second; }
    private final Map<UUID, Selection> selections = new HashMap<UUID, Selection>();
    @Override public String getName() { return "dungeonguard"; }
    @Override public List<String> getAliases() { return Collections.singletonList("dg"); }
    @Override public int getRequiredPermissionLevel() { return 0; }
    @Override public String getUsage(ICommandSender sender) { return "/dg help"; }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!GuardRuntime.ready()) throw new CommandException("Dungeon Guard has no active world.");
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("spawn")) {
            if (args.length != 1) throw new WrongUsageException("/dg spawn");
            GuardRuntime.spawn().teleportToSpawn(getCommandSenderAsPlayer(sender));
            say(sender, "Returned to the spawn Waystone."); return;
        }
        if (sub.equals("help")) { help(sender); return; }
        if (sub.equals("status") || sub.equals("list")) {
            say(sender, GuardRuntime.spawn().describe());
            say(sender, "World protection: " + (GuardRuntime.policy().enabled && GuardRuntime.policy().lockOutsideDungeons ? "LOCKED outside active dungeons; operator building only" : "custom settings"));
            for (String line : GuardRuntime.manager().statusLines()) say(sender, line);
            if (isAdmin(sender)) say(sender, HookStatus.summary());
            return;
        }
        if (!isAdmin(sender)) throw new CommandException("Only server operators can configure dungeon protection.");
        try {
            if (sub.equals("bindspawn")) {
                EntityPlayerMP player = getCommandSenderAsPlayer(sender); BlockPos target;
                if (args.length == 4) target = parseBlockPos(sender, args, 1, false);
                else if (args.length == 1) {
                    RayTraceResult hit = player.rayTrace(8.0D, 1.0F);
                    if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) throw new CommandException("Look directly at the spawn Waystone, then run /dg bindspawn.");
                    target = hit.getBlockPos();
                } else throw new WrongUsageException("/dg bindspawn [x y z]");
                GuardRuntime.spawn().bind(player, target); say(sender, GuardRuntime.spawn().describe()); return;
            }
            if (sub.equals("pos1") || sub.equals("pos2")) {
                EntityPlayerMP player = getCommandSenderAsPlayer(sender);
                BlockPos pos = args.length == 4 ? parseBlockPos(sender, args, 1, false) : player.getPosition();
                if (args.length != 1 && args.length != 4) throw new WrongUsageException("/dg " + sub + " [x y z]");
                Selection s = selections.get(player.getUniqueID());
                if (s == null || s.dimension != player.dimension) { s = new Selection(); s.dimension = player.dimension; selections.put(player.getUniqueID(), s); }
                if (sub.equals("pos1")) s.first = pos; else s.second = pos;
                say(sender, sub + " = " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " in dimension " + player.dimension); return;
            }
            if (sub.equals("capture")) {
                if (args.length != 2) throw new WrongUsageException("/dg capture <name>");
                if (!GuardRuntime.spawn().isBound()) throw new CommandException("Bind the spawn Waystone first with /dg bindspawn; rebuilding dungeons need a safe exit.");
                EntityPlayerMP player = getCommandSenderAsPlayer(sender); Selection s = selections.get(player.getUniqueID());
                if (s == null || s.first == null || s.second == null || s.dimension != player.dimension)
                    throw new CommandException("Set both inclusive corners with /dg pos1 and /dg pos2 in this dimension.");
                GuardRegion region = new GuardRegion(args[1], s.dimension, s.first.getX(), s.first.getY(), s.first.getZ(), s.second.getX(), s.second.getY(), s.second.getZ());
                region.validate();
                GuardConfig.Spawn spawn = GuardRuntime.settings().spawn;
                BlockPos landing = new BlockPos(spawn.landingX, spawn.landingY, spawn.landingZ);
                if (region.dimension == spawn.dimension && (region.contains(spawn.x, spawn.y, spawn.z)
                        || region.contains(spawn.x, spawn.y + 1, spawn.z) || region.contains(landing)
                        || region.contains(landing.up()) || region.contains(landing.down())))
                    throw new CommandException("A dungeon cannot include the spawn Waystone or its landing area.");
                say(sender, GuardRuntime.manager().register(region));
                say(sender, "Saving the clean baseline. Entry stays locked until capture finishes; check /dg status."); return;
            }
            if (sub.equals("reset")) {
                if (args.length != 2) throw new WrongUsageException("/dg reset <name>");
                say(sender, GuardRuntime.manager().forceReset(args[1])); return;
            }
            if (sub.equals("remove")) {
                if (args.length != 2) throw new WrongUsageException("/dg remove <name>");
                say(sender, GuardRuntime.manager().unregister(args[1])); return;
            }
            if (sub.equals("reload")) { GuardRuntime.reload(); say(sender, "Settings reloaded. Region definitions need a restart if changed by hand."); return; }
            if (sub.equals("label")) {
                if (args.length < 4) throw new WrongUsageException("/dg label <dungeon> <element> <display name...>");
                GuardConfig next = GuardRuntime.settings(); requireDungeon(next, args[1]);
                GuardConfig.DungeonLabel label = new GuardConfig.DungeonLabel();
                label.element = AreaState.normalizeElement(args[2]); label.displayName = join(args, 3);
                next.dungeonLabels.put(args[1], label); GuardRuntime.saveAndInstall(next);
                say(sender, "Dungeon banner: " + label.element + " / " + label.displayName + "."); return;
            }
            if (sub.equals("bossarea")) {
                if (args.length < 4) throw new WrongUsageException("/dg bossarea <dungeon> <area-id> <boss name...>");
                GuardConfig next = GuardRuntime.settings(); requireDungeon(next, args[1]);
                EntityPlayerMP player = getCommandSenderAsPlayer(sender); Selection selection = selections.get(player.getUniqueID());
                if (selection == null || selection.first == null || selection.second == null || selection.dimension != player.dimension)
                    throw new CommandException("Select the boss area's opposite inclusive corners with /dg pos1 and /dg pos2 first.");
                GuardConfig.BossZone zone = new GuardConfig.BossZone();
                zone.id = args[2]; zone.dungeon = args[1]; zone.bossName = join(args, 3);
                zone.bounds = new GuardRegion(zone.id, selection.dimension, selection.first.getX(), selection.first.getY(), selection.first.getZ(),
                        selection.second.getX(), selection.second.getY(), selection.second.getZ());
                for (int i = next.bossAreas.size() - 1; i >= 0; i--) {
                    GuardConfig.BossZone old = next.bossAreas.get(i);
                    if (zone.id.equals(old.id)) {
                        if (!zone.dungeon.equals(old.dungeon)) throw new CommandException("That boss area ID belongs to a different dungeon.");
                        zone.areaName = old.areaName; next.bossAreas.remove(i);
                    }
                }
                next.bossAreas.add(zone); GuardRuntime.saveAndInstall(next);
                say(sender, "Boss area " + zone.id + " saved inside " + zone.dungeon + "; Boss: " + zone.bossName + ".");
                say(sender, "Optional area title: /dg bosslabel " + zone.id + " <area name...>"); return;
            }
            if (sub.equals("bosslabel") || sub.equals("bossremove")) {
                if ((sub.equals("bosslabel") && args.length < 3) || (sub.equals("bossremove") && args.length != 2))
                    throw new WrongUsageException(sub.equals("bosslabel") ? "/dg bosslabel <area-id> <area name...>" : "/dg bossremove <area-id>");
                GuardConfig next = GuardRuntime.settings(); GuardConfig.BossZone found = null;
                for (GuardConfig.BossZone zone : next.bossAreas) if (zone.id.equals(args[1])) { found = zone; break; }
                if (found == null) throw new CommandException("No boss area named " + args[1] + ".");
                if (sub.equals("bosslabel")) found.areaName = join(args, 2); else next.bossAreas.remove(found);
                GuardRuntime.saveAndInstall(next); say(sender, "Boss area " + args[1] + (sub.equals("bosslabel") ? " renamed." : " removed.")); return;
            }
            if (sub.equals("edit")) {
                say(sender, "Global edit windows were removed. Real server operators can mine and build directly; other players stay protected."); return;
            }
            throw new WrongUsageException("Unknown subcommand. Use /dg help.");
        } catch (CommandException e) { throw e; }
        catch (Exception e) { throw new CommandException("Dungeon Guard: " + e.getMessage()); }
    }
    private void help(ICommandSender sender) {
        say(sender, "/dg spawn — return to the bound spawn Waystone");
        say(sender, "/dg status — protection, clean-copy progress, occupants and reset state");
        if (isAdmin(sender)) {
            say(sender, "/dg bindspawn [x y z] — bind the Waystone you are looking at, or its coordinates");
            say(sender, "/dg pos1 [x y z] ; /dg pos2 [x y z] — select opposite inclusive dungeon corners");
            say(sender, "/dg capture <name> — save a new clean baseline and register the dungeon");
            say(sender, "/dg reset <name> — restore an empty dungeon; normally automatic");
            say(sender, "/dg remove <name> — unregister an empty dungeon; saved baseline is retained");
            say(sender, "/dg label <dungeon> <element> <display name...> — set the element and dungeon banner");
            say(sender, "/dg bossarea <dungeon> <area-id> <boss name...> — use selected corners for a boss area");
            say(sender, "/dg bosslabel <area-id> <area name...> ; /dg bossremove <area-id> — rename/remove a boss area");
            say(sender, "Operators can build directly. Client banner toggle: /dghud [on|off]");
            say(sender, "/dg reload — reload non-region settings");
        }
    }
    private static boolean isAdmin(ICommandSender sender) {
        if (sender instanceof EntityPlayer) return OperatorAccess.isOperator((EntityPlayer) sender);
        return sender.canUseCommand(3, "dungeonguard");
    }
    private static void requireDungeon(GuardConfig config, String name) throws CommandException {
        for (GuardRegion region : config.regions) if (region.name.equals(name)) return;
        throw new CommandException("No saved dungeon named " + name + ". Capture it and wait for IDLE first.");
    }
    private static String join(String[] values, int first) {
        StringBuilder result = new StringBuilder();
        for (int i = first; i < values.length; i++) { if (i > first) result.append(' '); result.append(values[i]); }
        return result.toString();
    }
    private static void say(ICommandSender sender, String message) { sender.sendMessage(new TextComponentString("[Dungeon Guard] " + message)); }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos target) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "help", "spawn", "status", "list", "bindspawn", "pos1", "pos2", "capture", "reset", "remove", "label", "bossarea", "bosslabel", "bossremove", "reload");
        if (args.length == 2 && (args[0].equals("reset") || args[0].equals("remove") || args[0].equals("label") || args[0].equals("bossarea"))) {
            java.util.ArrayList<String> names = new java.util.ArrayList<String>();
            for (GuardRegion r : GuardRuntime.settings().regions) names.add(r.name);
            return getListOfStringsMatchingLastWord(args, names);
        }
        if (args.length == 3 && args[0].equals("label")) return getListOfStringsMatchingLastWord(args, AreaState.ELEMENTS);
        if (args.length == 2 && (args[0].equals("bosslabel") || args[0].equals("bossremove"))) {
            java.util.ArrayList<String> names = new java.util.ArrayList<String>();
            for (GuardConfig.BossZone zone : GuardRuntime.settings().bossAreas) names.add(zone.id);
            return getListOfStringsMatchingLastWord(args, names);
        }
        return Collections.emptyList();
    }
}
