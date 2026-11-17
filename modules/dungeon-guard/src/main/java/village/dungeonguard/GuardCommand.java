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
import village.dungeonguard.AreaState;
import village.dungeonguard.DungeonWaystones;
import village.dungeonguard.GuardConfig;
import village.dungeonguard.GuardMenus;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.GuardSelections;
import village.dungeonguard.GuardWand;
import village.dungeonguard.NarutoGameplayCompat;
import village.dungeonguard.OperatorAccess;
import village.dungeonguard.core.HookStatus;

public final class GuardCommand
extends CommandBase {
    public String getName() {
        return "dungeonguard";
    }

    public List<String> getAliases() {
        return Arrays.asList("dg", "dungeon");
    }

    public int getRequiredPermissionLevel() {
        return 0;
    }

    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender != null;
    }

    public String getUsage(ICommandSender sender) {
        return "/dungeon";
    }

    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        String sub;
        if (!GuardRuntime.ready()) {
            throw new CommandException("Dungeon Guard has no active world.", new Object[0]);
        }
        String string = sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("menu")) {
            if (args.length > 1) {
                throw new WrongUsageException("/dungeon", new Object[0]);
            }
            GuardMenus.main(sender, GuardCommand.isAdmin(sender));
            return;
        }
        if (sub.equals("spawn")) {
            if (args.length != 1) {
                throw new WrongUsageException("/dungeon spawn", new Object[0]);
            }
            GuardRuntime.spawn().teleportToSpawn(GuardCommand.getCommandSenderAsPlayer((ICommandSender)sender));
            GuardRuntime.manager().refreshPresence();
            GuardMenus.feedback(sender, "Returned to spawn.");
            return;
        }
        if (sub.equals("help")) {
            if (args.length > 2 || args.length == 2 && !args[1].equalsIgnoreCase("advanced")) {
                throw new WrongUsageException("/dungeon help [advanced]", new Object[0]);
            }
            GuardCommand.help(sender, args.length == 2);
            return;
        }
        if (sub.equals("status") || sub.equals("list") || sub.equals("info")) {
            if (args.length > 2) {
                throw new WrongUsageException("/dg list [page]", new Object[0]);
            }
            GuardCommand.status(sender, args.length == 2 ? GuardCommand.parseInt((String)args[1], (int)1) : 1);
            return;
        }
        if (!GuardCommand.isAdmin(sender)) {
            throw new CommandException("Only connected server operators can configure dungeons. Use /dungeon for spawn and information.", new Object[0]);
        }
        try {
            if (sub.equals("setup")) {
                GuardCommand.exact(args, 1, "/dungeon setup");
                GuardMenus.setup(GuardCommand.operator(sender));
                return;
            }
            if (sub.equals("dungeon") || sub.equals("waystone")) {
                GuardMenus.feedback(sender, DungeonWaystones.create(GuardCommand.operator(sender), args.length > 1 ? GuardCommand.join(args, 1) : ""));
                return;
            }
            if (sub.equals("puppets")) {
                GuardCommand.exact(args, 1, "/dg puppets");
                NarutoGameplayCompat.givePuppetKit(GuardCommand.operator(sender));
                GuardMenus.feedback(sender, "Puppet Jutsu, Karasu and Sanshouo scrolls added.");
                return;
            }
            if (sub.equals("xp")) {
                if (args.length == 1) {
                    GuardCommand.say(sender, "Ninja XP multiplier: " + NarutoGameplayCompat.nativeXpMultiplier() + ". Set with /dg xp <multiplier>.");
                    return;
                }
                GuardCommand.exact(args, 2, "/dg xp <multiplier|native>");
                double multiplier = args[1].equalsIgnoreCase("native") ? -1.0 : GuardCommand.parseDouble((String)args[1], (double)0.0, (double)2000.0);
                NarutoGameplayCompat.nativeXpMultiplier();
                GuardConfig next = GuardRuntime.settings();
                next.ninjaXpGainMultiplier = multiplier;
                GuardRuntime.saveAndInstall(next);
                NarutoGameplayCompat.applySettings(server);
                GuardMenus.feedback(sender, "Ninja XP multiplier: " + NarutoGameplayCompat.nativeXpMultiplier());
                return;
            }
            if (sub.equals("wand")) {
                GuardCommand.exact(args, 1, "/dungeon wand");
                GuardWand.give(GuardCommand.operator(sender));
                return;
            }
            if (sub.equals("selection")) {
                GuardCommand.exact(args, 1, "/dungeon selection");
                GuardMenus.feedback(sender, GuardSelections.describe(GuardCommand.operator(sender)));
                return;
            }
            if (sub.equals("clear")) {
                GuardCommand.exact(args, 1, "/dungeon clear");
                GuardSelections.forget(GuardCommand.operator(sender).getUniqueID());
                GuardMenus.feedback(sender, "Selection cleared.");
                return;
            }
            if (sub.equals("setspawn") || sub.equals("bindspawn")) {
                BlockPos target;
                EntityPlayerMP player = GuardCommand.operator(sender);
                if (sub.equals("bindspawn") && args.length == 4) {
                    target = GuardCommand.parseBlockPos((ICommandSender)sender, (String[])args, (int)1, (boolean)false);
                } else if (args.length == 1) {
                    Vec3d end;
                    Vec3d eyes = new Vec3d(player.posX, player.posY + (double)player.getEyeHeight(), player.posZ);
                    RayTraceResult hit = player.world.rayTraceBlocks(eyes, end = eyes.add(player.getLookVec().scale(8.0)), false, false, true);
                    if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
                        throw new CommandException("Look directly at the spawn Waystone, then click Set spawn or use /dungeon setspawn.", new Object[0]);
                    }
                    target = hit.getBlockPos();
                } else {
                    throw new WrongUsageException(sub.equals("setspawn") ? "/dungeon setspawn" : "/dg bindspawn [x y z]", new Object[0]);
                }
                GuardRuntime.spawn().bind(player, target);
                GuardMenus.feedback(sender, "Spawn linked. Next: look at a dungeon Waystone and use /dg dungeon <name>.");
                return;
            }
            if (sub.equals("pos1") || sub.equals("pos2")) {
                EntityPlayerMP player = GuardCommand.operator(sender);
                if (args.length != 1 && args.length != 4) {
                    throw new WrongUsageException("/dg " + sub + " [x y z]", new Object[0]);
                }
                BlockPos pos = args.length == 4 ? GuardCommand.parseBlockPos((ICommandSender)sender, (String[])args, (int)1, (boolean)false) : player.getPosition();
                GuardSelections.select(player, pos, sub.equals("pos1"));
                GuardMenus.feedback(sender, GuardSelections.describe(player));
                return;
            }
            if (sub.equals("create") || sub.equals("save")) {
                if (args.length < 2) {
                    throw new WrongUsageException("/dungeon create <element> [display name...]", new Object[0]);
                }
                EntityPlayerMP player = GuardCommand.operator(sender);
                GuardConfig.DungeonLabel label = new GuardConfig.DungeonLabel();
                label.element = AreaState.normalizeElement(args[1]);
                label.displayName = args.length > 2 ? GuardCommand.join(args, 2) : GuardMenus.title(label.element) + " Dungeon";
                AreaState.requireText(label.displayName, 80, false, "dungeon name");
                GuardSelections.Selection selection = GuardSelections.require(player);
                String id = GuardRuntime.manager().nextAvailableName(GuardCommand.safeId(label.displayName, label.element.toLowerCase(Locale.ROOT) + "_dungeon"));
                GuardRegion region = selection.region(id);
                GuardCommand.validateCapture(region);
                GuardRuntime.manager().register(region, label, player.getUniqueID());
                GuardMenus.feedback(sender, "Saving " + label.displayName + ". One Ready notice will follow.");
                return;
            }
            if (sub.equals("capture")) {
                GuardCommand.exact(args, 2, "/dg capture <name>");
                EntityPlayerMP player = GuardCommand.operator(sender);
                GuardRegion region = GuardSelections.require(player).region(args[1]);
                GuardCommand.validateCapture(region);
                GuardRuntime.manager().register(region, null, player.getUniqueID());
                GuardMenus.feedback(sender, "Saving the clean dungeon. One Ready notice will follow.");
                return;
            }
            if (sub.equals("boss")) {
                if (args.length < 2) {
                    throw new WrongUsageException("/dungeon boss <boss name...>", new Object[0]);
                }
                GuardCommand.saveEasyBoss(GuardCommand.operator(sender), GuardCommand.join(args, 1));
                return;
            }
            if (sub.equals("reset")) {
                GuardCommand.exact(args, 2, "/dg reset <name>");
                GuardCommand.say(sender, GuardRuntime.manager().forceReset(args[1]));
                return;
            }
            if (sub.equals("remove")) {
                GuardCommand.exact(args, 2, "/dg remove <name>");
                GuardCommand.say(sender, GuardRuntime.manager().unregister(args[1]));
                return;
            }
            if (sub.equals("reload")) {
                GuardCommand.exact(args, 1, "/dg reload");
                GuardRuntime.reload();
                NarutoGameplayCompat.applySettings(server);
                GuardMenus.feedback(sender, "Settings reloaded.");
                return;
            }
            if (sub.equals("label")) {
                if (args.length < 4) {
                    throw new WrongUsageException("/dg label <dungeon> <element> <display name...>", new Object[0]);
                }
                GuardConfig next = GuardRuntime.settings();
                GuardCommand.requireDungeon(next, args[1]);
                GuardConfig.DungeonLabel label = new GuardConfig.DungeonLabel();
                label.element = AreaState.normalizeElement(args[2]);
                label.displayName = GuardCommand.join(args, 3);
                next.dungeonLabels.put(args[1], label);
                GuardRuntime.saveAndInstall(next);
                GuardMenus.feedback(sender, "Dungeon name: " + label.displayName);
                return;
            }
            if (sub.equals("bossarea")) {
                if (args.length < 4) {
                    throw new WrongUsageException("/dg bossarea <dungeon> <area-id> <boss name...>", new Object[0]);
                }
                GuardConfig next = GuardRuntime.settings();
                GuardCommand.requireDungeon(next, args[1]);
                GuardConfig.BossZone zone = new GuardConfig.BossZone();
                zone.id = args[2];
                zone.dungeon = args[1];
                zone.bossName = GuardCommand.join(args, 3);
                zone.bounds = GuardSelections.require(GuardCommand.operator(sender)).region(zone.id);
                for (int i = next.bossAreas.size() - 1; i >= 0; --i) {
                    GuardConfig.BossZone old = next.bossAreas.get(i);
                    if (!zone.id.equals(old.id)) continue;
                    if (!zone.dungeon.equals(old.dungeon)) {
                        throw new CommandException("That boss area ID belongs to a different dungeon.", new Object[0]);
                    }
                    zone.areaName = old.areaName;
                    next.bossAreas.remove(i);
                }
                next.bossAreas.add(zone);
                GuardRuntime.saveAndInstall(next);
                GuardMenus.feedback(sender, "Boss area saved: " + zone.bossName);
                return;
            }
            if (sub.equals("bosslabel") || sub.equals("bossremove")) {
                if (sub.equals("bosslabel") && args.length < 3 || sub.equals("bossremove") && args.length != 2) {
                    throw new WrongUsageException(sub.equals("bosslabel") ? "/dg bosslabel <area-id> <area name...>" : "/dg bossremove <area-id>", new Object[0]);
                }
                GuardConfig next = GuardRuntime.settings();
                GuardConfig.BossZone found = null;
                for (GuardConfig.BossZone zone : next.bossAreas) {
                    if (!zone.id.equals(args[1])) continue;
                    found = zone;
                    break;
                }
                if (found == null) {
                    throw new CommandException("No boss area named " + args[1] + ".", new Object[0]);
                }
                if (sub.equals("bosslabel")) {
                    found.areaName = GuardCommand.join(args, 2);
                } else {
                    next.bossAreas.remove(found);
                }
                GuardRuntime.saveAndInstall(next);
                GuardCommand.say(sender, "Boss area " + args[1] + (sub.equals("bosslabel") ? " renamed." : " removed."));
                return;
            }
            if (sub.equals("edit")) {
                GuardCommand.say(sender, "Operators can mine and build directly. Other players and ability damage remain protected outside active dungeons.");
                return;
            }
            throw new WrongUsageException("Unknown subcommand. Open /dungeon or use /dungeon help.", new Object[0]);
        }
        catch (CommandException e) {
            throw e;
        }
        catch (Exception e) {
            throw new CommandException("Dungeon Guard: " + e.getMessage(), new Object[0]);
        }
    }

    private static void validateCapture(GuardRegion region) throws CommandException {
        region.validate();
        if (!GuardRuntime.spawn().isBound()) {
            throw new CommandException("Link spawn first: look at the spawn Waystone and use /dungeon setspawn.", new Object[0]);
        }
        GuardConfig settings = GuardRuntime.settings();
        if (region.volume() > (long)settings.maxSnapshotBlocks) {
            throw new CommandException("That selection contains " + region.volume() + " blocks; the limit is " + settings.maxSnapshotBlocks + ". Choose closer corners.", new Object[0]);
        }
        GuardConfig.Spawn spawn = settings.spawn;
        BlockPos landing = new BlockPos(spawn.landingX, spawn.landingY, spawn.landingZ);
        if (region.dimension == spawn.dimension && (region.contains(spawn.x, spawn.y, spawn.z) || region.contains(spawn.x, spawn.y + 1, spawn.z) || region.contains(landing) || region.contains(landing.up()) || region.contains(landing.down()))) {
            throw new CommandException("Keep the spawn Waystone and its landing area outside your dungeon selection.", new Object[0]);
        }
    }

    private static void saveEasyBoss(EntityPlayerMP player, String bossName) throws Exception {
        boolean bl;
        AreaState.requireText(bossName, 80, false, "boss name");
        GuardRegion selected = GuardSelections.require(player).region("boss_selection");
        selected.validate();
        GuardConfig next = GuardRuntime.settings();
        GuardRegion parent = null;
        for (GuardRegion guardRegion : next.regions) {
            if (guardRegion.dimension != selected.dimension || !guardRegion.contains(selected.minX, selected.minY, selected.minZ) || !guardRegion.contains(selected.maxX, selected.maxY, selected.maxZ)) continue;
            if (parent != null) {
                throw new CommandException("This chamber matches more than one dungeon; check the saved dungeon bounds.", new Object[0]);
            }
            parent = guardRegion;
        }
        if (parent == null) {
            throw new CommandException("Select a boss chamber completely inside one saved dungeon. If its clean copy is still saving, wait for Ready.", new Object[0]);
        }
        GuardConfig.BossZone match = null;
        for (GuardConfig.BossZone zone : next.bossAreas) {
            if (GuardCommand.sameBounds(zone.bounds, selected) && zone.dungeon.equals(parent.name)) {
                match = zone;
                continue;
            }
            if (!zone.bounds.overlaps(selected)) continue;
            throw new CommandException("That selection overlaps boss area " + zone.id + ". Select its exact corners to rename its boss, or remove it with /dungeon bossremove " + zone.id + ".", new Object[0]);
        }
        boolean bl2 = bl = match != null;
        if (match == null) {
            match = new GuardConfig.BossZone();
            match.id = GuardCommand.bossId(next, GuardCommand.safeId(bossName, "boss_area"));
            match.dungeon = parent.name;
            selected.name = match.id;
            match.bounds = selected;
            next.bossAreas.add(match);
        }
        match.bossName = bossName;
        GuardRuntime.saveAndInstall(next);
        GuardConfig.DungeonLabel label = next.dungeonLabels.get(parent.name);
        GuardMenus.feedback((ICommandSender)player, (bl ? "Boss updated: " : "Boss area saved: ") + bossName);
    }

    private static boolean sameBounds(GuardRegion a, GuardRegion b) {
        return a.dimension == b.dimension && a.minX == b.minX && a.minY == b.minY && a.minZ == b.minZ && a.maxX == b.maxX && a.maxY == b.maxY && a.maxZ == b.maxZ;
    }

    private static String safeId(String text, String fallback) {
        String id = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (id.isEmpty()) {
            id = fallback;
        }
        return id.length() > 48 ? id.substring(0, 48) : id;
    }

    private static String bossId(GuardConfig config, String base) {
        for (int suffix = 1; suffix < Integer.MAX_VALUE; ++suffix) {
            String tail = suffix == 1 ? "" : "-" + suffix;
            String candidate = base.substring(0, Math.min(base.length(), 48 - tail.length())) + tail;
            boolean exists = false;
            for (GuardConfig.BossZone zone : config.bossAreas) {
                if (!zone.id.equals(candidate)) continue;
                exists = true;
                break;
            }
            if (exists) continue;
            return candidate;
        }
        throw new IllegalStateException("No free boss area ID is available.");
    }

    private static void status(ICommandSender sender, int page) {
        List<String> lines = GuardRuntime.manager().statusLines();
        int pages = Math.max(1, (lines.size() + 4) / 5);
        page = Math.min(page, pages);
        GuardMenus.heading(sender, "Dungeons " + page + "/" + pages);
        for (int i = (page - 1) * 5; i < Math.min(lines.size(), page * 5); ++i) {
            GuardCommand.say(sender, lines.get(i));
        }
        if (page < pages) {
            GuardMenus.buttonLine(sender, "", "Next", "/dg list " + (page + 1), "Next five dungeons.", false);
        }
    }

    private static void help(ICommandSender sender, boolean advanced) {
        GuardMenus.main(sender, GuardCommand.isAdmin(sender));
        GuardCommand.say(sender, "/dungeon spawn - return to the linked spawn Waystone");
        GuardCommand.say(sender, "/dungeon list - see clean-copy progress and dungeon activity");
        if (!GuardCommand.isAdmin(sender)) {
            return;
        }
        if (!advanced) {
            GuardCommand.say(sender, "/dg setspawn - look at the spawn Waystone; /dg dungeon Cloud Temple - look at the dungeon Waystone");
            GuardCommand.say(sender, "/dg boss Gaara - name a selected boss chamber; /dg xp 2 - set the native XP multiplier");
            GuardCommand.say(sender, "/dg puppets - operator puppet-scroll kit; /dg wand - select a boss chamber or custom region");
            GuardMenus.buttonLine(sender, "", "Advanced commands", "/dungeon help advanced", "Coordinates, explicit IDs, reset, removal and configuration.", false);
            return;
        }
        GuardCommand.say(sender, "/dg bindspawn [x y z] - bind the targeted or specified spawn Waystone");
        GuardCommand.say(sender, "/dg pos1 [x y z] ; /dg pos2 [x y z] - set inclusive selection corners");
        GuardCommand.say(sender, "/dg capture <id> - save a new clean baseline with an explicit ID");
        GuardCommand.say(sender, "/dg reset <id> ; /dg remove <id> - reset or unregister an empty dungeon; clean copies are retained");
        GuardCommand.say(sender, "/dg label <dungeon> <element> <display name...> - update its banner");
        GuardCommand.say(sender, "/dg bossarea <dungeon> <area-id> <boss name...> - assign the selected boss bounds explicitly");
        GuardCommand.say(sender, "/dg bosslabel <area-id> <area name...> ; /dg bossremove <area-id> - rename/remove a boss area");
        GuardCommand.say(sender, "/dg reload - reload non-region settings. Client banner toggle: /dghud [on|off]");
        GuardCommand.say(sender, "Technical protection check: " + HookStatus.summary());
    }

    private static EntityPlayerMP operator(ICommandSender sender) throws CommandException {
        if (!(sender instanceof EntityPlayerMP) || !OperatorAccess.isOperator((EntityPlayer)((EntityPlayerMP)sender))) {
            throw new CommandException("Use this setup action in-game as a connected server operator.", new Object[0]);
        }
        return (EntityPlayerMP)sender;
    }

    private static boolean isAdmin(ICommandSender sender) {
        if (sender instanceof EntityPlayer) {
            return OperatorAccess.isOperator((EntityPlayer)sender);
        }
        return sender != null && sender.getServer() != null && sender == sender.getServer();
    }

    private static void requireDungeon(GuardConfig config, String name) throws CommandException {
        for (GuardRegion region : config.regions) {
            if (!region.name.equals(name)) continue;
            return;
        }
        throw new CommandException("No saved dungeon named " + name + ". Save its clean copy and wait for Ready first.", new Object[0]);
    }

    private static void exact(String[] args, int count, String usage) throws WrongUsageException {
        if (args.length != count) {
            throw new WrongUsageException(usage, new Object[0]);
        }
    }

    private static String join(String[] values, int first) {
        StringBuilder result = new StringBuilder();
        for (int i = first; i < values.length; ++i) {
            if (i > first) {
                result.append(' ');
            }
            result.append(values[i]);
        }
        return result.toString();
    }

    private static void say(ICommandSender sender, String message) {
        GuardMenus.say(sender, message);
    }

    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos target) {
        if (args.length == 1) {
            if (!GuardCommand.isAdmin(sender)) {
                return GuardCommand.getListOfStringsMatchingLastWord((String[])args, (String[])new String[]{"menu", "help", "spawn", "status", "list", "info"});
            }
            return GuardCommand.getListOfStringsMatchingLastWord((String[])args, (String[])new String[]{"menu", "setup", "wand", "setspawn", "dungeon", "waystone", "save", "create", "boss", "xp", "puppets", "selection", "clear", "help", "spawn", "status", "list", "info", "bindspawn", "pos1", "pos2", "capture", "reset", "remove", "label", "bossarea", "bosslabel", "bossremove", "reload", "edit"});
        }
        if (args.length == 0 || !GuardCommand.isAdmin(sender)) {
            return Collections.emptyList();
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("create") || sub.equals("save")) || args.length == 3 && sub.equals("label")) {
            ArrayList<String> elements = new ArrayList<String>();
            for (String element : AreaState.ELEMENTS) {
                elements.add(element.toLowerCase(Locale.ROOT));
            }
            return GuardCommand.getListOfStringsMatchingLastWord((String[])args, elements);
        }
        if (args.length == 2 && (sub.equals("reset") || sub.equals("remove") || sub.equals("label") || sub.equals("bossarea"))) {
            ArrayList<String> names = new ArrayList<String>();
            for (GuardRegion region : GuardRuntime.manager().regions()) {
                names.add(region.name);
            }
            return GuardCommand.getListOfStringsMatchingLastWord((String[])args, names);
        }
        if (args.length == 2 && (sub.equals("bosslabel") || sub.equals("bossremove"))) {
            ArrayList<String> names = new ArrayList<String>();
            for (GuardConfig.BossZone zone : GuardRuntime.settings().bossAreas) {
                names.add(zone.id);
            }
            return GuardCommand.getListOfStringsMatchingLastWord((String[])args, names);
        }
        if (args.length == 2 && sub.equals("help")) {
            return GuardCommand.getListOfStringsMatchingLastWord((String[])args, (String[])new String[]{"advanced"});
        }
        return Collections.emptyList();
    }
}

