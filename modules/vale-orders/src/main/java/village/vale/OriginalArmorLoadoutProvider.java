package village.vale;

import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import village.vale.armor.api.ArmorSnapshot;

/** Source-integration example for Core032 only; leave the legacy031 catalog/dispatch unchanged. */
public final class OriginalArmorLoadoutProvider {
    private OriginalArmorLoadoutProvider(){}
    public static void append(EntityPlayerMP player,List<AbilitySnapshot.Entry> loadout){
        if(!OriginalArmorApi.isAvailable())return;
        Set<String> seen=new HashSet<>();for(AbilitySnapshot.Entry e:loadout)seen.add(e.id);
        for(ArmorSnapshot.Action a:OriginalArmorApi.getSnapshot(player).actions){
            if(loadout.size()>=AbilityLoadoutSnapshot.MAX_ENTRIES)return;
            if(!seen.add(a.id))continue;
            loadout.add(new AbilitySnapshot.Entry(a.id,a.title,a.kind,a.schoolKey,a.iconItemId,a.iconMeta,a.requiredLevel,a.resolveCost,0,a.enabled,a.reason,a.cooldownRemainingTicks,a.cooldownTotalTicks,a.cooldownFraction));
        }
    }
    public static boolean handles(String id){return id!=null&&id.length()<=96&&id.startsWith("order/original_armor_");}
    /** Call before the generic order/* branch; the authenticated request's server player is the actor. */
    public static boolean activate(EntityPlayerMP player,String id){if(!handles(id))return false;village.vale.armor.api.ArmorActionResult result=OriginalArmorApi.activate(player,id);if(!result.accepted)Adventures.tell(player,result.message);return result.accepted;}
}
