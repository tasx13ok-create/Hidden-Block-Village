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

public final class GuardMenus {
    private GuardMenus() {
    }

    public static void main(ICommandSender sender, boolean admin) {
        TextComponentString actions = new TextComponentString("Dungeons  ");
        actions.appendSibling(GuardMenus.button("Spawn", "/dg spawn", "Return to the spawn Waystone.", false));
        actions.appendText("  ").appendSibling(GuardMenus.button("List", "/dg list", "Five dungeons per page.", false));
        if (admin) {
            actions.appendText("  ").appendSibling(GuardMenus.button("Setup", "/dg setup", "Two Waystone setup steps.", false));
        }
        actions.appendText("  ").appendSibling(GuardMenus.button("Help", "/dg help", "Short command guide.", false));
        sender.sendMessage((ITextComponent)actions);
    }

    public static void setup(EntityPlayerMP player) {
        GuardMenus.buttonLine((ICommandSender)player, "1. Look at the spawn Waystone: ", "Set spawn", "/dg setspawn", "Link world spawn to this Waystone.", false);
        GuardMenus.buttonLine((ICommandSender)player, "2. Look at a dungeon Waystone: ", "Save dungeon", "/dg dungeon", "Save its clean 300 x 250 x 300 zone using the Waystone name. Wait for Ready.", false);
        TextComponentString boss = new TextComponentString("Optional boss room: ");
        boss.appendSibling(GuardMenus.button("Wand", "/dg wand", "Select the boss chamber's two opposite corners.", false));
        boss.appendText("  ").appendSibling(GuardMenus.button("Boss name...", "/dg boss ", "Type the boss name for the selected chamber.", true));
        player.sendMessage((ITextComponent)boss);
    }

    public static void feedback(ICommandSender sender, String message) {
        if (sender instanceof EntityPlayerMP) {
            ((EntityPlayerMP)sender).sendStatusMessage((ITextComponent)new TextComponentString(message), true);
        } else {
            GuardMenus.say(sender, message);
        }
    }

    public static void heading(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString("[Dungeon Guard] " + message).setStyle(new Style().setColor(TextFormatting.GOLD).setBold(Boolean.valueOf(true))));
    }

    public static void say(ICommandSender sender, String message) {
        if (sender != null) {
            sender.sendMessage((ITextComponent)new TextComponentString("[Dungeon Guard] " + message));
        }
    }

    public static void buttonLine(ICommandSender sender, String prefix, String label, String command, String tooltip, boolean suggest) {
        sender.sendMessage(new TextComponentString(prefix).appendSibling(GuardMenus.button(label, command, tooltip, suggest)));
    }

    public static ITextComponent button(String label, String command, String tooltip, boolean suggest) {
        return new TextComponentString("[" + label + "]").setStyle(new Style().setColor(TextFormatting.AQUA).setClickEvent(new ClickEvent(suggest ? ClickEvent.Action.SUGGEST_COMMAND : ClickEvent.Action.RUN_COMMAND, command)).setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, (ITextComponent)new TextComponentString(tooltip))));
    }

    public static String title(String text) {
        String value = text.toLowerCase(Locale.ROOT);
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}

