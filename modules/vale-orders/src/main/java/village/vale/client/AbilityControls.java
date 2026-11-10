package village.vale.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.*;
import village.vale.*;

/** Return value acknowledges local transport only; the server alone confirms effects. */
@SideOnly(Side.CLIENT)
public final class AbilityControls {
    private AbilityControls(){}
    public static boolean requestSelect(String id){return send(id,false);}
    public static boolean requestActivate(String id){return send(id,true);}
    public static boolean requestEquip(String id){
        Minecraft mc=Minecraft.getMinecraft();AbilityLoadoutSnapshot value=AbilitySnapshots.getLoadout();
        if(value==null||!AbilitySnapshot.validId(id)||mc.currentScreen!=null)return false;
        AbilitySnapshot.Entry entry=value.find(id);if(entry==null||!entry.kind.equals("weapon")||!entry.enabled)return false;
        return AbilityNetwork.requestEquip(id,value.dimension);
    }
    private static boolean send(String id,boolean activate){
        Minecraft mc=Minecraft.getMinecraft();if(mc.currentScreen!=null||!AbilitySnapshot.validId(id))return false;
        AbilityLoadoutSnapshot modern=AbilitySnapshots.getLoadout();AbilitySnapshot legacy=modern==null?AbilitySnapshots.getLocal():null;
        if(available(modern,legacy,id,activate)==null)return false;
        return AbilityNetwork.request(id,modern!=null?modern.dimension:legacy.dimension,activate);
    }
    /** Getters above already enforce live owner, world, connection and five-second TTL. */
    static AbilitySnapshot.Entry available(AbilityLoadoutSnapshot modern,AbilitySnapshot legacy,String id,boolean activate){
        if(!AbilitySnapshot.validId(id))return null;
        AbilitySnapshot.Entry entry=modern!=null?modern.find(id):legacy==null?null:legacy.find(id);
        return entry==null||entry.kind.equals("weapon")||activate&&!entry.enabled?null:entry;
    }
}
