package village.vale;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/** Optional integration with the armor project the user created; no bundled foreign classes. */
public final class OwnedArmor {
    private static Method full;
    private static Method ledgerGet;
    private static Field entries;
    private static boolean ready;
    private static final Order[] ORDERS = {Order.FLINT,Order.RIME,Order.CURRENT,Order.EMBER,Order.GALE,Order.BLOOM,Order.DAWN,Order.DUSK,Order.STORM};
    private static final String[] BEASTS = {"shukaku","matatabi","isobu","son_goku","kokuo","saiken","chomei","gyuki","kurama"};
    public static final String[] TITLES={"Dune Guardian","Azure Lynx","Tide Shell","Cinder Ape","Ivory Runner","Mire Warden","Glimmer Moth","Deep Colossus","Storm Fox"};
    private OwnedArmor() {}
    public static Order affinity(String itemKey) { for(int i=0;i<BEASTS.length;i++)if(itemKey.startsWith(BEASTS[i]+"_"))return ORDERS[i];return null; }
    public static void initialize() {
        if (ready) return; ready = true;
        if (!Loader.isModLoaded("hvbijuu")) return;
        try {
            Class<?> armor=Class.forName("village.bijuu.BijuuArmor");
            full = armor.getMethod("full", EntityPlayer.class);
            Class<?> ledger=Class.forName("village.bijuu.BijuuArmor$Ledger");
            ledgerGet=ledger.getDeclaredMethod("get",net.minecraft.world.World.class);ledgerGet.setAccessible(true);
            entries=ledger.getDeclaredField("entries");entries.setAccessible(true);
            if(!Loader.isModLoaded("narutomod"))armor.getField("useNarutoChakra").setBoolean(null,false);
        }
        catch (ReflectiveOperationException ex) { full=null;ledgerGet=null;entries=null;org.apache.logging.log4j.LogManager.getLogger("ValeOrders").warn("Owned armor API unavailable; optional powers remain disabled.", ex); }
    }
    public static String[] beasts(){return BEASTS.clone();}
    public static boolean rewardKey(String key) {
        initialize();if(full==null||ledgerGet==null||entries==null)return false;
        for(String beast:BEASTS)for(String part:new String[]{"helmet","chestplate","leggings","boots"})if(key.equals(beast+"_"+part))return true;
        return false;
    }
    public static void registerReward(net.minecraft.world.WorldServer world,String key,String serial) {
        if(!rewardKey(key))throw new IllegalArgumentException("Owned armor is unavailable or the item is unknown.");
        int split=key.lastIndexOf('_');String beast=key.substring(0,split),part=key.substring(split+1);
        int slot=java.util.Arrays.asList("helmet","chestplate","leggings","boots").indexOf(part);
        try {
            Object ledger=ledgerGet.invoke(null,world.getMinecraftServer().getWorld(0));
            ((net.minecraft.nbt.NBTTagCompound)entries.get(ledger)).setString(serial,beast+"/"+slot);
            ((net.minecraft.world.storage.WorldSavedData)ledger).markDirty();
        }catch(ReflectiveOperationException ex){throw new IllegalStateException("Cannot register owned armor reward",ex);}
    }
    public static boolean wearing(EntityPlayer player) {
        ItemStack stack = player.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        return !stack.isEmpty() && stack.getItem().getRegistryName() != null && "hvbijuu".equals(stack.getItem().getRegistryName().getNamespace());
    }
    public static Order order(EntityPlayer player) {
        initialize(); if (full == null || !wearing(player)) return null;
        // Require the original issued marker in addition to the owner's own full-set validation.
        for (EntityEquipmentSlot slot : new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET}) {
            ItemStack piece = player.getItemStackFromSlot(slot);
            if (!piece.hasTagCompound() || piece.getTagCompound().getString("hvReward").isEmpty()) return null;
        }
        try { int kind = ((Number)full.invoke(null, player)).intValue(); return kind >= 0 && kind < ORDERS.length ? ORDERS[kind] : null; }
        catch (ReflectiveOperationException ex) { full = null; org.apache.logging.log4j.LogManager.getLogger("ValeOrders").error("Owned armor integration failed; optional powers disabled.", ex); return null; }
    }
}
