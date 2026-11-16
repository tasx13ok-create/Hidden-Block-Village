package village.dungeonguard;

import java.util.Locale;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

/** Small vanilla chat menus; every clicked command rechecks server-side authority. */
public final class GuardMenus {
    private GuardMenus() { }

    public static void main(ICommandSender sender, boolean admin) {
        heading(sender, "Dungeons");
        say(sender, "Return to spawn or check the dungeon information.");
        ITextComponent actions = new TextComponentString("");
        actions.appendSibling(button("Spawn", "/dungeon spawn", "Return to the linked spawn Waystone.", false));
        actions.appendText("  ").appendSibling(button("Dungeon info", "/dungeon status", "See which dungeons are ready, in use or rebuilding.", false));
        sender.sendMessage(actions);
        if (admin) {
            buttonLine(sender, "Operator tools: ", "Guided setup", "/dungeon setup", "Link spawn, select a dungeon and save its clean copy.", false);
            buttonLine(sender, "", "Command reference", "/dungeon help", "Read the short command guide and advanced options.", false);
        }
    }
    public static void setup(EntityPlayerMP player) {
        heading(player, "Dungeon setup");
        say(player, "1. Spawn: " + (GuardRuntime.spawn().isBound() ? "linked to a Waystone." : "look at your Overworld spawn Waystone."));
        buttonLine(player, "   ", "Set spawn", "/dungeon setspawn", "Link the Waystone you are looking at to world spawn.", false);
        say(player, "2. Select the full dungeon, including its floor, walls and ceiling.");
        ITextComponent selection = new TextComponentString("   ");
        selection.appendSibling(button("Get wand", "/dungeon wand", "Left-click one corner; right-click the opposite corner. Blocks stay unchanged.", false));
        selection.appendText("  ").appendSibling(button("Show selection", "/dungeon selection", "Show your selected corners and block count.", false));
        selection.appendText("  ").appendSibling(button("Clear", "/dungeon clear", "Clear only your selected corners; saved dungeons stay registered.", false));
        player.sendMessage(selection); say(player, GuardSelections.describe(player));
        GuardSelections.Selection chosen = GuardSelections.get(player);
        if (GuardRuntime.spawn().isBound() && chosen != null && chosen.complete()
                && chosen.region("selection").volume() <= GuardRuntime.settings().maxSnapshotBlocks) {
            say(player, "3. Click an element to SAVE the selected dungeon's clean copy:");
            ITextComponent elements = new TextComponentString("   ");
            for (String element : AreaState.ELEMENTS) {
                if (!elements.getSiblings().isEmpty()) elements.appendText(" ");
                elements.appendSibling(button(title(element), "/dungeon create " + element.toLowerCase(Locale.ROOT),
                        "Save as " + title(element) + " Dungeon. Optional name: /dungeon create " + element.toLowerCase(Locale.ROOT) + " <name>.", false));
            }
            player.sendMessage(elements);
            buttonLine(player, "   Or choose a name: ", "Named dungeon...", "/dungeon create fire ", "Change the element and type your dungeon name before sending.", true);
        } else say(player, "3. Link spawn and select two valid corners; then return here to choose an element and save.");
        say(player, "4. Optional boss room: use the wand inside a saved dungeon, then name its boss.");
        buttonLine(player, "   ", "Name boss...", "/dungeon boss ", "Type the boss name. The selected chamber's parent dungeon is found automatically.", true);
        buttonLine(player, "", "Dungeon list", "/dungeon list", "Check clean-copy progress and saved dungeons.", false);
    }
    public static void heading(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString("[Dungeon Guard] " + message).setStyle(new Style().setColor(TextFormatting.GOLD).setBold(true)));
    }
    public static void say(ICommandSender sender, String message) {
        if (sender != null) sender.sendMessage(new TextComponentString("[Dungeon Guard] " + message));
    }
    public static void buttonLine(ICommandSender sender, String prefix, String label, String command, String tooltip, boolean suggest) {
        sender.sendMessage(new TextComponentString(prefix).appendSibling(button(label, command, tooltip, suggest)));
    }
    public static ITextComponent button(String label, String command, String tooltip, boolean suggest) {
        return new TextComponentString("[" + label + "]").setStyle(new Style().setColor(TextFormatting.AQUA)
                .setClickEvent(new ClickEvent(suggest ? ClickEvent.Action.SUGGEST_COMMAND : ClickEvent.Action.RUN_COMMAND, command))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(tooltip))));
    }
    public static String title(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
