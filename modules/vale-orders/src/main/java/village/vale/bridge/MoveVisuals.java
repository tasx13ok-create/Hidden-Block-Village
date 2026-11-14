package village.vale.bridge;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import village.vale.arsenal.*;

/** Presentation only. This router never invokes native cast, damage, XP, resource or cooldown APIs. */
public final class MoveVisuals {
    private MoveVisuals(){}
    public static Set<String> weaponKeys(){Set<String> result=new TreeSet<>();for(ArsenalSpec spec:ArsenalSpec.values())result.add(spec.key);return Collections.unmodifiableSet(result);}
    public static List<String> styles(){return BridgeMappings.styles(weaponKeys());}
    static void accepted(EntityPlayerMP player,String style,BridgeReceipts.Context lease){
        if(lease==null||!lease.valid()||!Bridge.current(player)||!BridgeMappings.validStyle(style,weaponKeys())||style.equals("none")||style.equals("dodge"))return;
        if(style.startsWith("weapon:"))BridgeHooks.weapon(player,ArsenalSpec.byKey(style.substring(7)),lease);
        else BridgeHooks.hand(player,style,lease);
    }
    public static boolean preview(EntityPlayerMP player,String style){
        return Bridge.preview(player,style);
    }
}
