package village.vale.bridge;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * /narutobridge status | reload | map <style> | unmap | test <style>
 * Lets the server owner tune the licensed-mod bridge while playing: map the held item to a move
 * style, preview a style, see the chakra link and how many IDs discovery has recorded.
 */
public final class BridgeCommand extends CommandBase {
    static final List<String> STYLES = Arrays.asList("spiral", "charge", "fireedge", "windedge", "lava", "lightmode", "none");

    @Override public String getName() { return "narutobridge"; }
    @Override public String getUsage(ICommandSender s) { return "/narutobridge status | reload | map <style|weapon:key> | unmap | test <style|weapon:key>"; }
    @Override public int getRequiredPermissionLevel() { return 2; }

    @Override public void execute(MinecraftServer server, ICommandSender sender, String[] a) throws CommandException {
        if (a.length == 0) throw new WrongUsageException(getUsage(sender));
        switch (a[0]) {
            case "status":
                tell(sender, TextFormatting.GOLD + "Chakra: " + TextFormatting.RESET + ChakraLink.status);
                tell(sender, TextFormatting.GOLD + "Mappings: " + TextFormatting.RESET + Bridge.mappingCount() + " explicit, auto-map " + (Bridge.autoMap ? "on" : "off") + "; " + Bridge.seenCount() + " IDs discovered");
                if (sender instanceof EntityPlayerMP) {
                    String id = held((EntityPlayerMP) sender);
                    if (id != null) tell(sender, TextFormatting.GOLD + "Held: " + TextFormatting.RESET + id + " -> " + String.valueOf(Bridge.style(id)));
                }
                return;
            case "reload": Bridge.reload(); tell(sender, "Reloaded bridge-moves.json (" + Bridge.mappingCount() + " mappings)."); return;
            case "map": case "unmap": {
                EntityPlayerMP p = getCommandSenderAsPlayer(sender);
                String id = held(p);
                if (id == null) throw new CommandException("Hold the item to map.");
                String style = a[0].equals("unmap") ? null : a.length > 1 ? a[1] : null;
                if (a[0].equals("map") && (style == null || !valid(style))) throw new CommandException("Style must be one of " + STYLES + " or weapon:<arsenal key>.");
                Bridge.setMapping(id, style);
                tell(sender, a[0].equals("map") ? "Mapped " + id + " -> " + style : "Removed mapping for " + id);
                return;
            }
            case "test": {
                EntityPlayerMP p = getCommandSenderAsPlayer(sender);
                if (a.length < 2 || !valid(a[1])) throw new CommandException("Style must be one of " + STYLES + " or weapon:<arsenal key>.");
                MoveVisuals.play(p, a[1]);
                return;
            }
            default: throw new WrongUsageException(getUsage(sender));
        }
    }

    static boolean valid(String style) {
        if (STYLES.contains(style)) return true;
        return style.startsWith("weapon:") && village.vale.arsenal.ArsenalSpec.byKey(style.substring(7)) != null;
    }

    private static String held(EntityPlayerMP p) {
        ItemStack s = p.getHeldItemMainhand();
        return s.isEmpty() || s.getItem().getRegistryName() == null ? null : s.getItem().getRegistryName().toString();
    }

    private static void tell(ICommandSender s, String text) { s.sendMessage(new TextComponentString(text)); }

    @Override public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] a, BlockPos pos) {
        if (a.length == 1) return getListOfStringsMatchingLastWord(a, "status", "reload", "map", "unmap", "test");
        if (a.length == 2 && (a[0].equals("map") || a[0].equals("test"))) {
            List<String> all = new java.util.ArrayList<>(STYLES);
            for (village.vale.arsenal.ArsenalSpec s : village.vale.arsenal.ArsenalSpec.values()) all.add("weapon:" + s.key);
            return getListOfStringsMatchingLastWord(a, all);
        }
        return Collections.emptyList();
    }
}
