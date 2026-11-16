package village.dungeonguard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import village.dungeonguard.core.HookStatus;

/** Readable setup commands with backward-compatible advanced administration. */
public final class GuardCommand extends CommandBase {
    @Override public String getName() { return "dungeonguard"; }
    @Override public List<String> getAliases() { return Arrays.asList("dg", "dungeon"); }
    @Override public int getRequiredPermissionLevel() { return 0; }
    // Native players reject unknown command names even at level zero. Public
    // dispatch is intentional; each setup action checks real operator identity.
    @Override public boolean checkPermission(MinecraftServer server, ICommandSender sender) { return sender != null; }
    @Override public String getUsage(ICommandSender sender) { return "/dungeon"; }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!GuardRuntime.ready()) throw new CommandException("Dungeon Guard has no active world.");
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("menu")) {
            if (args.length > 1) throw new WrongUsageException("/dungeon");
            GuardMenus.main(sender, isAdmin(sender)); return;
        }
        if (sub.equals("spawn")) {
            if (args.length != 1) throw new WrongUsageException("/dungeon spawn");
            GuardRuntime.spawn().teleportToSpawn(getCommandSenderAsPlayer(sender));
            say(sender, "Returned to the spawn Waystone."); return;
        }
        if (sub.equals("help")) {
            if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("advanced")))
                throw new WrongUsageException("/dungeon help [advanced]");
            help(sender, args.length == 2); return;
        }
        if (sub.equals("status") || sub.equals("list") || sub.equals("info")) {
            if (args.length != 1) throw new WrongUsageException("/dungeon " + sub);
            status(sender); return;
        }
        if (!isAdmin(sender)) throw new CommandException("Only connected server operators can configure dungeons. Use /dungeon for spawn and information.");
        try {
            if (sub.equals("setup")) {
                exact(args, 1, "/dungeon setup"); GuardMenus.setup(operator(sender)); return;
            }
            if (sub.equals("wand")) {
                exact(args, 1, "/dungeon wand"); GuardWand.give(operator(sender)); return;
            }
            if (sub.equals("selection")) {
                exact(args, 1, "/dungeon selection"); say(sender, GuardSelections.describe(operator(sender))); return;
            }
            if (sub.equals("clear")) {
                exact(args, 1, "/dungeon clear"); GuardSelections.forget(operator(sender).getUniqueID());
                say(sender, "Selection cleared. Saved dungeons and clean copies are unchanged."); return;
            }
            if (sub.equals("setspawn") || sub.equals("bindspawn")) {
                EntityPlayerMP player = operator(sender); BlockPos target;
                if (sub.equals("bindspawn") && args.length == 4) target = parseBlockPos(sender, args, 1, false);
                else if (args.length == 1) {
                    Vec3d eyes = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
                    Vec3d end = eyes.add(player.getLookVec().scale(8.0D));
                    RayTraceResult hit = player.world.rayTraceBlocks(eyes, end, false, false, true);
                    if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK)
                        throw new CommandException("Look directly at the spawn Waystone, then click Set spawn or use /dungeon setspawn.");
                    target = hit.getBlockPos();
                } else throw new WrongUsageException(sub.equals("setspawn") ? "/dungeon setspawn" : "/dg bindspawn [x y z]");
                GuardRuntime.spawn().bind(player, target);
                say(sender, "World spawn is linked to this Waystone. Players can return with /dungeon spawn.");
                GuardMenus.buttonLine(sender, "Next: ", "Get selection wand", "/dungeon wand", "Select the full dungeon's opposite corners.", false); return;
            }
            if (sub.equals("pos1") || sub.equals("pos2")) {
                EntityPlayerMP player = operator(sender);
                if (args.length != 1 && args.length != 4) throw new WrongUsageException("/dg " + sub + " [x y z]");
                BlockPos pos = args.length == 4 ? parseBlockPos(sender, args, 1, false) : player.getPosition();
                GuardSelections.select(player, pos, sub.equals("pos1"));
                say(sender, (sub.equals("pos1") ? "Corner 1" : "Corner 2") + " = " + GuardSelections.coordinates(pos) + " in dimension " + player.dimension + ".");
                say(sender, GuardSelections.describe(player)); return;
            }
            if (sub.equals("create")) {
                if (args.length < 2) throw new WrongUsageException("/dungeon create <element> [display name...]");
                EntityPlayerMP player = operator(sender);
                GuardConfig.DungeonLabel label = new GuardConfig.DungeonLabel();
                label.element = AreaState.normalizeElement(args[1]);
                label.displayName = args.length > 2 ? join(args, 2) : GuardMenus.title(label.element) + " Dungeon";
                AreaState.requireText(label.displayName, 80, false, "dungeon name");
                GuardSelections.Selection selection = GuardSelections.require(player);
                String id = GuardRuntime.manager().nextAvailableName(safeId(label.displayName, label.element.toLowerCase(Locale.ROOT) + "_dungeon"));
                GuardRegion region = selection.region(id); validateCapture(region);
                say(sender, GuardRuntime.manager().register(region, label, player.getUniqueID()));
                say(sender, "Name: " + label.displayName + " | Element: " + GuardMenus.title(label.element) + ". You will be told when it is Ready.");
                say(sender, "Keep everyone outside while the clean copy saves. Anyone inside returns to spawn."); return;
            }
            if (sub.equals("capture")) {
                exact(args, 2, "/dg capture <name>"); EntityPlayerMP player = operator(sender);
                GuardRegion region = GuardSelections.require(player).region(args[1]); validateCapture(region);
                say(sender, GuardRuntime.manager().register(region, null, player.getUniqueID()));
                say(sender, "The clean copy is saving. Keep the area empty; you will be told when it is Ready."); return;
            }
            if (sub.equals("boss")) {
                if (args.length < 2) throw new WrongUsageException("/dungeon boss <boss name...>");
                saveEasyBoss(operator(sender), join(args, 1)); return;
            }
            if (sub.equals("reset")) {
                exact(args, 2, "/dg reset <name>"); say(sender, GuardRuntime.manager().forceReset(args[1])); return;
            }
            if (sub.equals("remove")) {
                exact(args, 2, "/dg remove <name>"); say(sender, GuardRuntime.manager().unregister(args[1])); return;
            }
            if (sub.equals("reload")) {
                exact(args, 1, "/dg reload"); GuardRuntime.reload(); say(sender, "Settings reloaded. Region definitions need a restart if changed by hand."); return;
            }
            if (sub.equals("label")) {
                if (args.length < 4) throw new WrongUsageException("/dg label <dungeon> <element> <display name...>");
                GuardConfig next = GuardRuntime.settings(); requireDungeon(next, args[1]);
                GuardConfig.DungeonLabel label = new GuardConfig.DungeonLabel();
                label.element = AreaState.normalizeElement(args[2]); label.displayName = join(args, 3);
                next.dungeonLabels.put(args[1], label); GuardRuntime.saveAndInstall(next);
                say(sender, "Dungeon banner: " + GuardMenus.title(label.element) + " / " + label.displayName + "."); return;
            }
            if (sub.equals("bossarea")) {
                if (args.length < 4) throw new WrongUsageException("/dg bossarea <dungeon> <area-id> <boss name...>");
                GuardConfig next = GuardRuntime.settings(); requireDungeon(next, args[1]);
                GuardConfig.BossZone zone = new GuardConfig.BossZone();
                zone.id = args[2]; zone.dungeon = args[1]; zone.bossName = join(args, 3);
                zone.bounds = GuardSelections.require(operator(sender)).region(zone.id);
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
                say(sender, "Operators can mine and build directly. Other players and ability damage remain protected outside active dungeons."); return;
            }
            throw new WrongUsageException("Unknown subcommand. Open /dungeon or use /dungeon help.");
        } catch (CommandException e) { throw e; }
        catch (Exception e) { throw new CommandException("Dungeon Guard: " + e.getMessage()); }
    }

    private static void validateCapture(GuardRegion region) throws CommandException {
        region.validate();
        if (!GuardRuntime.spawn().isBound())
            throw new CommandException("Link spawn first: look at the spawn Waystone and use /dungeon setspawn.");
        GuardConfig settings = GuardRuntime.settings();
        if (region.volume() > settings.maxSnapshotBlocks)
            throw new CommandException("That selection contains " + region.volume() + " blocks; the limit is " + settings.maxSnapshotBlocks + ". Choose closer corners.");
        GuardConfig.Spawn spawn = settings.spawn;
        BlockPos landing = new BlockPos(spawn.landingX, spawn.landingY, spawn.landingZ);
        if (region.dimension == spawn.dimension && (region.contains(spawn.x, spawn.y, spawn.z)
                || region.contains(spawn.x, spawn.y + 1, spawn.z) || region.contains(landing)
                || region.contains(landing.up()) || region.contains(landing.down())))
            throw new CommandException("Keep the spawn Waystone and its landing area outside your dungeon selection.");
    }
    private static void saveEasyBoss(EntityPlayerMP player, String bossName) throws Exception {
        AreaState.requireText(bossName, 80, false, "boss name");
        GuardRegion selected = GuardSelections.require(player).region("boss_selection"); selected.validate();
        GuardConfig next = GuardRuntime.settings(); GuardRegion parent = null;
        for (GuardRegion region : next.regions) if (region.dimension == selected.dimension
                && region.contains(selected.minX, selected.minY, selected.minZ) && region.contains(selected.maxX, selected.maxY, selected.maxZ)) {
            if (parent != null) throw new CommandException("This chamber matches more than one dungeon; check the saved dungeon bounds.");
            parent = region;
        }
        if (parent == null) throw new CommandException("Select a boss chamber completely inside one saved dungeon. If its clean copy is still saving, wait for Ready.");
        GuardConfig.BossZone match = null;
        for (GuardConfig.BossZone zone : next.bossAreas) {
            if (sameBounds(zone.bounds, selected) && zone.dungeon.equals(parent.name)) match = zone;
            else if (zone.bounds.overlaps(selected))
                throw new CommandException("That selection overlaps boss area " + zone.id + ". Select its exact corners to rename its boss, or remove it with /dungeon bossremove " + zone.id + ".");
        }
        boolean updated = match != null;
        if (match == null) {
            match = new GuardConfig.BossZone(); match.id = bossId(next, safeId(bossName, "boss_area")); match.dungeon = parent.name;
            selected.name = match.id; match.bounds = selected; next.bossAreas.add(match);
        }
        match.bossName = bossName; GuardRuntime.saveAndInstall(next);
        GuardConfig.DungeonLabel label = next.dungeonLabels.get(parent.name);
        say(player, (updated ? "Boss updated: " : "Boss area saved: ") + bossName + " in " + (label == null ? parent.name : label.displayName) + ".");
        say(player, "Area ID: " + match.id + ". Its element follows the dungeon.");
        GuardMenus.buttonLine(player, "Optional area title: ", "Name room...", "/dungeon bosslabel " + match.id + " ", "Type an area title such as Gaara's Chamber.", true);
    }
    private static boolean sameBounds(GuardRegion a, GuardRegion b) {
        return a.dimension == b.dimension && a.minX == b.minX && a.minY == b.minY && a.minZ == b.minZ
                && a.maxX == b.maxX && a.maxY == b.maxY && a.maxZ == b.maxZ;
    }
    private static String safeId(String text, String fallback) {
        String id = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (id.isEmpty()) id = fallback;
        return id.length() > 48 ? id.substring(0, 48) : id;
    }
    private static String bossId(GuardConfig config, String base) {
        for (int suffix = 1; suffix < Integer.MAX_VALUE; suffix++) {
            String tail = suffix == 1 ? "" : "-" + suffix;
            String candidate = base.substring(0, Math.min(base.length(), 48 - tail.length())) + tail;
            boolean exists = false; for (GuardConfig.BossZone zone : config.bossAreas) if (zone.id.equals(candidate)) { exists = true; break; }
            if (!exists) return candidate;
        }
        throw new IllegalStateException("No free boss area ID is available.");
    }
    private static void status(ICommandSender sender) {
        GuardMenus.heading(sender, "Dungeon information");
        say(sender, GuardRuntime.spawn().describe());
        say(sender, "World protection: " + (GuardRuntime.policy().enabled && GuardRuntime.policy().lockOutsideDungeons
                ? "outside terrain is protected; operators can build" : "custom settings"));
        if (!isAdmin(sender) && GuardRuntime.manager().regions().isEmpty()) say(sender, "No dungeons are registered yet.");
        else for (String line : GuardRuntime.manager().statusLines()) say(sender, line);
        GuardConfig settings = GuardRuntime.settings();
        if (!settings.bossAreas.isEmpty()) for (GuardConfig.BossZone zone : settings.bossAreas)
            say(sender, "Boss: " + zone.bossName + " | " + zone.areaName + " | " + zone.dungeon + (isAdmin(sender) ? " [" + zone.id + "]" : ""));
        if (isAdmin(sender)) {
            for (GuardRegion region : settings.regions) {
                GuardConfig.DungeonLabel label = settings.dungeonLabels.get(region.name);
                String element = label == null ? "general" : label.element.toLowerCase(Locale.ROOT);
                ITextComponent actions = new TextComponentString((label == null ? region.name : label.displayName) + ": ");
                actions.appendSibling(GuardMenus.button("Rename...", "/dungeon label " + region.name + " " + element + " ", "Type the display name; the clean copy stays unchanged.", true));
                actions.appendText("  ").appendSibling(GuardMenus.button("Reset...", "/dungeon reset " + region.name, "Review and send to restore this empty dungeon.", true));
                actions.appendText("  ").appendSibling(GuardMenus.button("Unregister...", "/dungeon remove " + region.name, "Review and send to unregister this empty dungeon. Its saved clean copy is retained.", true));
                sender.sendMessage(actions);
            }
            GuardMenus.buttonLine(sender, "", "Guided setup", "/dungeon setup", "Create another dungeon or mark a boss room.", false);
        }
    }
    private static void help(ICommandSender sender, boolean advanced) {
        GuardMenus.main(sender, isAdmin(sender));
        say(sender, "/dungeon spawn - return to the linked spawn Waystone");
        say(sender, "/dungeon list - see clean-copy progress and dungeon activity");
        if (!isAdmin(sender)) return;
        if (!advanced) {
            say(sender, "/dungeon setup - clickable guide for spawn, corners, elements and boss rooms");
            say(sender, "/dungeon create fire Fire Temple - save the selected clean dungeon and its banner together");
            say(sender, "/dungeon boss Gaara - mark the selected chamber and name its boss");
            say(sender, "Elements: general, fire, water, wind, earth, lightning, sand, ice. Existing /dg commands still work.");
            GuardMenus.buttonLine(sender, "", "Advanced commands", "/dungeon help advanced", "Coordinates, explicit IDs, reset, removal and configuration.", false); return;
        }
        say(sender, "/dg bindspawn [x y z] - bind the targeted or specified spawn Waystone");
        say(sender, "/dg pos1 [x y z] ; /dg pos2 [x y z] - set inclusive selection corners");
        say(sender, "/dg capture <id> - save a new clean baseline with an explicit ID");
        say(sender, "/dg reset <id> ; /dg remove <id> - reset or unregister an empty dungeon; clean copies are retained");
        say(sender, "/dg label <dungeon> <element> <display name...> - update its banner");
        say(sender, "/dg bossarea <dungeon> <area-id> <boss name...> - assign the selected boss bounds explicitly");
        say(sender, "/dg bosslabel <area-id> <area name...> ; /dg bossremove <area-id> - rename/remove a boss area");
        say(sender, "/dg reload - reload non-region settings. Client banner toggle: /dghud [on|off]");
        say(sender, "Technical protection check: " + HookStatus.summary());
    }
    private static EntityPlayerMP operator(ICommandSender sender) throws CommandException {
        if (!(sender instanceof EntityPlayerMP) || !OperatorAccess.isOperator((EntityPlayerMP) sender))
            throw new CommandException("Use this setup action in-game as a connected server operator.");
        return (EntityPlayerMP) sender;
    }
    private static boolean isAdmin(ICommandSender sender) {
        if (sender instanceof EntityPlayer) return OperatorAccess.isOperator((EntityPlayer) sender);
        return sender != null && sender.getServer() != null && sender == sender.getServer();
    }
    private static void requireDungeon(GuardConfig config, String name) throws CommandException {
        for (GuardRegion region : config.regions) if (region.name.equals(name)) return;
        throw new CommandException("No saved dungeon named " + name + ". Save its clean copy and wait for Ready first.");
    }
    private static void exact(String[] args, int count, String usage) throws WrongUsageException { if (args.length != count) throw new WrongUsageException(usage); }
    private static String join(String[] values, int first) {
        StringBuilder result = new StringBuilder();
        for (int i = first; i < values.length; i++) { if (i > first) result.append(' '); result.append(values[i]); }
        return result.toString();
    }
    private static void say(ICommandSender sender, String message) { GuardMenus.say(sender, message); }
    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos target) {
        if (args.length == 1) {
            if (!isAdmin(sender)) return getListOfStringsMatchingLastWord(args, "menu", "help", "spawn", "status", "list", "info");
            return getListOfStringsMatchingLastWord(args, "menu", "setup", "wand", "setspawn", "create", "boss", "selection", "clear", "help", "spawn", "status", "list", "info",
                    "bindspawn", "pos1", "pos2", "capture", "reset", "remove", "label", "bossarea", "bosslabel", "bossremove", "reload", "edit");
        }
        if (args.length == 0 || !isAdmin(sender)) return Collections.emptyList();
        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((args.length == 2 && sub.equals("create")) || (args.length == 3 && sub.equals("label"))) {
            List<String> elements = new ArrayList<String>(); for (String element : AreaState.ELEMENTS) elements.add(element.toLowerCase(Locale.ROOT));
            return getListOfStringsMatchingLastWord(args, elements);
        }
        if (args.length == 2 && (sub.equals("reset") || sub.equals("remove") || sub.equals("label") || sub.equals("bossarea"))) {
            List<String> names = new ArrayList<String>(); for (GuardRegion region : GuardRuntime.manager().regions()) names.add(region.name);
            return getListOfStringsMatchingLastWord(args, names);
        }
        if (args.length == 2 && (sub.equals("bosslabel") || sub.equals("bossremove"))) {
            List<String> names = new ArrayList<String>(); for (GuardConfig.BossZone zone : GuardRuntime.settings().bossAreas) names.add(zone.id);
            return getListOfStringsMatchingLastWord(args, names);
        }
        if (args.length == 2 && sub.equals("help")) return getListOfStringsMatchingLastWord(args, "advanced");
        return Collections.emptyList();
    }
}
