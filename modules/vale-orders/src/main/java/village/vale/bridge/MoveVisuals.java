package village.vale.bridge;

import net.minecraft.entity.player.EntityPlayerMP;
import village.vale.arsenal.ArsenalSpec;

/** Plays a Vale presentation style for a licensed move (visual/audio only; no damage or cost). */
public final class MoveVisuals {
    private MoveVisuals() {}
    public static void play(EntityPlayerMP p, String style) {
        if (p == null || style == null) return;
        if (style.startsWith("weapon:")) {
            ArsenalSpec spec = ArsenalSpec.byKey(style.substring(7));
            if (spec != null) village.vale.arsenal.BridgeHooks.weapon(p, spec);
            return;
        }
        if (style.equals("dodge")) return;
        village.vale.arsenal.BridgeHooks.hand(p, style);
    }
}
