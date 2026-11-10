package village.fieldhud;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;

/** Optional read-only adapter to the installed NarutoMod 0.3.1 client/item synchronization. */
public final class JutsuSignals {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static final CastReceipt RECEIPT=new CastReceipt();
    private static Class<?> base;
    private static Method current,mangekyo,blinded,byakugan;
    private static boolean attempted,unavailable,offhand;
    private static Object trackedPlayer;
    public static boolean casting,eye,whiteEye;
    public static String name="";
    public static int color=0x24B5FF;
    public static double confirmedAt=-1000;
    private JutsuSignals() {}
    public static void reset() { RECEIPT.reset();casting=eye=whiteEye=false;name="";confirmedAt=-1000;offhand=false; }
    private static void ensure() {
        if(attempted)return;attempted=true;
        try {
            base=Class.forName("net.narutomod.item.ItemJutsu$Base");
            current=Class.forName("net.narutomod.item.ItemJutsu").getMethod("getCurrentJutsu",ItemStack.class);
            Class<?> sharingan=Class.forName("net.narutomod.item.ItemSharingan");
            mangekyo=sharingan.getMethod("isWearingMangekyo",EntityLivingBase.class);blinded=sharingan.getMethod("isBlinded",ItemStack.class);
            byakugan=Class.forName("net.narutomod.item.ItemByakugan").getMethod("wearingAny",EntityLivingBase.class);
            System.out.println("FIELD_HUD_SIGNALS: NarutoMod 0.3.1 cast/optics adapter ready");
        } catch(ReflectiveOperationException|LinkageError e) { unavailable=true; }
    }
    public static void tick() {
        if(MC.player!=trackedPlayer) { trackedPlayer=MC.player;reset();ShinobiHud.resetWorld();HudSoundPulse.reset(); }
        if(MC.player==null||MC.world==null||!MC.player.isEntityAlive()) { reset();return; }
        ensure();if(unavailable)return;
        try {
            ItemStack head=MC.player.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
            eye=(Boolean)mangekyo.invoke(null,MC.player)&&!(Boolean)blinded.invoke(null,head);
            whiteEye=(Boolean)byakugan.invoke(null,MC.player);
            casting=MC.player.isHandActive()&&base.isInstance(MC.player.getActiveItemStack().getItem());
            if(casting)offhand=MC.player.getActiveHand()==EnumHand.OFF_HAND;
            ItemStack stack=offhand?MC.player.getHeldItemOffhand():MC.player.getHeldItemMainhand();
            ChakraBridge.Snapshot resource=ChakraBridge.current();
            if(stack.isEmpty()||!base.isInstance(stack.getItem())||!stack.hasTagCompound()) {
                RECEIPT.update("",-1,Double.NaN,false,MC.player.ticksExisted);casting=false;return;
            }
            NBTTagCompound tag=stack.getTagCompound();int index=tag.getInteger("JutsuIndexKey");int[] xp=tag.getIntArray("JutsuExperienceMapKey");
            String key=(offhand?"off":String.valueOf(MC.player.inventory.currentItem))+":"+stack.getItem().getRegistryName()+":"+index+":"+tag.getLong("OwnerIdKeyMost")+":"+tag.getLong("OwnerIdKeyLeast");
            Object jutsu=current.invoke(null,stack);name=(String)jutsu.getClass().getMethod("getName").invoke(jutsu);
            String type=String.valueOf(jutsu.getClass().getMethod("getType").invoke(jutsu));
            color=type.contains("KATON")?0xFF773C:type.contains("IRYO")?0x82E8B2:type.contains("FUTON")?0xB3E9DA:type.contains("DOTON")?0xD4AD6C:0x24B5FF;
            int experience=index>=0&&index<xp.length?xp[index]:-1;
            if(RECEIPT.update(key,experience,resource==null?Double.NaN:resource.amount,casting,MC.player.ticksExisted)) {
                confirmedAt=MC.player.ticksExisted;
                System.out.println("FIELD_HUD_CAST_CONFIRMED: "+name+" xp="+experience+" chakra="+resource.amount);
            }
        } catch(ReflectiveOperationException|RuntimeException|LinkageError error) {
            unavailable=true;reset();System.out.println("FIELD_HUD_SIGNALS_DISABLED: "+error.getClass().getSimpleName());
        }
    }
}
