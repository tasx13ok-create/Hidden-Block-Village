package village.vale.arsenal;

import net.minecraft.entity.player.EntityPlayerMP;

/** Package-level entry points the licensed-mod bridge uses to trigger Vale visuals. */
public final class BridgeHooks {
    private BridgeHooks() {}
    public static void weapon(EntityPlayerMP p, ArsenalSpec spec) { WeaponPulse.cast(p, spec, 1f); }
    public static void hand(EntityPlayerMP p, String style) { HandTechnique.visual(p, style, 40); }
}
