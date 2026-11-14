package village.vale.arsenal;
import net.minecraft.entity.player.EntityPlayerMP;

/** Isolated cosmetic entry points; an actual earned hand carrier is never replaced or fizzled. */
public final class BridgeHooks {
    private BridgeHooks(){}
    public static void weapon(EntityPlayerMP player,ArsenalSpec spec,village.vale.bridge.BridgeReceipts.Context lease){if(village.vale.bridge.Bridge.current(player)&&spec!=null)WeaponPulse.visual(player,spec,lease);}
    public static void hand(EntityPlayerMP player,String style,village.vale.bridge.BridgeReceipts.Context lease){if(village.vale.bridge.Bridge.current(player))HandTechnique.visual(player,style,20,lease);}
    public static void close(EntityPlayerMP player){if(player!=null){HandTechnique.endVisual(player);WeaponPulse.closeVisual(player);}}
}
