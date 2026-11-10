package village.residents;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import village.vale.Arts;
import village.vale.Missions;
import village.vale.Quartermaster;

public final class RoleMenus {
    private RoleMenus() {}

    public static void open(ResidentRole role, EntityPlayerMP player) {
        switch (role) {
            case ACADEMY: Arts.show(player); break;
            case CONTRACTS: Missions.show(player); break;
            case QUARTERMASTER:
                Quartermaster.show(player, "ward");
                link(player, "[Tools]", "/vale provisions tools");
                link(player, "[Spirit armor]", "/vale provisions spirit");
                break;
            default: throw new IllegalArgumentException("Unsupported resident role");
        }
    }

    private static void link(EntityPlayerMP player, String text, String command) {
        TextComponentString line = new TextComponentString(text);
        line.getStyle().setColor(TextFormatting.AQUA)
            .setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        player.sendMessage(line);
    }
}
