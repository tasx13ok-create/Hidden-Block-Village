package village.vale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import java.io.InputStream;

/** Disposable native fixture only; checks actual registered getters, not a duplicate path implementation. */
public final class WornArmorNativeChecks {
    private static int checks;
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static void binding(EntityPlayerMP player,String key,EntityEquipmentSlot slot,String expected)throws Exception{
        Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("valeorders",key));
        require(item instanceof ItemArmor,"Missing native armor "+key);
        String actual=((ItemArmor)item).getArmorTexture(new ItemStack(item),player,slot,null);
        require(expected.equals(actual),"Native armor getter "+key+": "+actual);
        try(InputStream in=ValeOrders.class.getResourceAsStream("/assets/"+actual.replace(':','/'))){require(in!=null,"Packaged worn resource missing "+actual);}
    }
    public static int run(EntityPlayerMP player)throws Exception{
        if(!Boolean.getBoolean("valeorders.recoverycheck")||player==null||player.world.isRemote||!"vale-recovery-test".equals(player.getServer().getFolderName()))throw new IllegalStateException("Disposable native test context required");
        checks=0;require(LightningKit.EFFECT!=null,"Lightning effect registered");PotionEffect old=player.getActivePotionEffect(LightningKit.EFFECT);old=old==null?null:new PotionEffect(old);
        try{
            player.removePotionEffect(LightningKit.EFFECT);
            String[] wards={"ember","current","gale","flint","bloom","rime","storm","dusk","dawn"};
            String[] pieces={"helmet","chestplate","leggings","boots"};EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
            for(String ward:wards)for(int n=0;n<4;n++)binding(player,ward+"_"+pieces[n],slots[n],ArmorSkinPaths.texture(ward,n==2,false));
            player.addPotionEffect(new PotionEffect(LightningKit.EFFECT,200,0,false,false));require(player.isPotionActive(LightningKit.EFFECT),"Native active lightning state");
            for(String ward:wards)for(int n=0;n<4;n++)binding(player,ward+"_"+pieces[n],slots[n],ArmorSkinPaths.texture(ward,n==2,true));
            for(OriginalFocusGear.Spec spec:OriginalFocusGear.SPECS)binding(player,spec.key,spec.slot,ArmorSkinPaths.garment("focus",spec.key,spec.slot==EntityEquipmentSlot.LEGS));
            for(OriginalOutfits.Spec spec:OriginalOutfits.SPECS)binding(player,spec.key,spec.slot,ArmorSkinPaths.garment("outfits",spec.key,spec.slot==EntityEquipmentSlot.LEGS));
            return checks;
        }finally{player.removePotionEffect(LightningKit.EFFECT);if(old!=null)player.addPotionEffect(old);}
    }
}
