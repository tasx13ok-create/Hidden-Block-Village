package village.fieldhud;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.GuiIngameForge;

/** Observe the armor mod's own visible HUD state, preserving all its callbacks. */
public final class ArmorHudBridge {
    private static boolean armor,wings,attempted;
    private static Method visual;
    private static Field kind;
    private ArmorHudBridge() {}
    public static void tick() {
        Minecraft mc=Minecraft.getMinecraft();armor=false;wings=false;
        if(mc.player==null||mc.world==null||!mc.player.isEntityAlive()||mc.player.isSpectator())return;
        for(ItemStack stack:mc.player.inventory.armorInventory) {
            if(!stack.isEmpty()&&stack.getItem().getRegistryName()!=null&&stack.getItem().getRegistryName().toString().startsWith("hvbijuu:")) { armor=true;break; }
        }
        if(!attempted) {
            attempted=true;
            try { visual=Class.forName("village.bijuu.ResonanceVisuals").getMethod("get",Entity.class); }
            catch(ReflectiveOperationException|LinkageError absent) { visual=null; }
        }
        if(visual==null||mc.player.capabilities.isCreativeMode)return;
        try {
            Object value=visual.invoke(null,mc.player);
            if(value!=null) { if(kind==null)kind=value.getClass().getField("kind");wings=kind.getInt(value)==6; }
        } catch(ReflectiveOperationException|RuntimeException|LinkageError invalid) { visual=null;kind=null; }
    }
    public static Layout.Box[] bounds(int w,int h) {
        return ArmorReservations.bounds(w,h,armor,wings,Math.max(GuiIngameForge.left_height,GuiIngameForge.right_height));
    }
}
