package village.vale;

import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.vale.armor.*;

/** Source integration inside Core: new item keys, the same RewardLedger and issued-serial authority. */
public final class OriginalArmorRegistry {
    static final EntityEquipmentSlot[] SLOTS={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
    private OriginalArmorRegistry(){}
    public static void register(RegistryEvent.Register<Item> event){
        for(ArmorProfile profile:ArmorProfile.values()){
            ItemArmor.ArmorMaterial material=EnumHelper.addArmorMaterial("VALE_ORIGINAL_"+profile.key.toUpperCase(Locale.ROOT),"valearmor:"+profile.key,40,new int[]{3,6,8,3},12,SoundEvents.ITEM_ARMOR_EQUIP_IRON,2);
            material.setRepairItem(new ItemStack(net.minecraft.init.Items.IRON_INGOT));
            for(EntityEquipmentSlot slot:SLOTS){
                Armor item=new Armor(profile,material,slot);String key=profile.item(convert(slot)).substring("valeorders:".length());
                Gear.add(event,key,item);
            }
        }
    }
    public static Order order(ArmorProfile profile){return profile==null?null:Order.valueOf(profile.ward.toUpperCase(Locale.ROOT));}
    public static boolean wearing(EntityPlayer p){return p.getItemStackFromSlot(EntityEquipmentSlot.CHEST).getItem() instanceof Armor;}
    public static ArmorProfile visual(EntityPlayer player){
        ArmorProfile profile=null;
        for(EntityEquipmentSlot slot:SLOTS){ItemStack stack=player.getItemStackFromSlot(slot);if(!(stack.getItem() instanceof Armor))return null;Armor a=(Armor)stack.getItem();if(a.armorType!=slot||profile!=null&&profile!=a.profile)return null;profile=a.profile;}
        return profile;
    }
    public static ArmorProfile full(EntityPlayer player){
        if(player==null||player.world.isRemote||player.getServer()==null)return null;
        final Map<String,ItemStack> bySerial=new HashMap<>();EquipmentGate.Piece[] pieces=new EquipmentGate.Piece[4];
        int i=0;
        for(EntityEquipmentSlot slot:SLOTS){
            ItemStack stack=player.getItemStackFromSlot(slot);if(!(stack.getItem() instanceof Armor)||!stack.hasTagCompound())return null;
            Armor armor=(Armor)stack.getItem();if(armor.armorType!=slot)return null;String serial=stack.getTagCompound().getString("valeSerial");
            pieces[i++]=new EquipmentGate.Piece(armor.profile,convert(slot),Gear.key(stack),serial);bySerial.put(serial,stack);
        }
        final RewardLedger ledger=RewardLedger.get(player);
        return EquipmentGate.resolve(player.getUniqueID(),pieces,(owner,item,serial,slot)->owner.equals(player.getUniqueID())&&ledger.valid(player,bySerial.get(serial)));
    }
    public static int price(EntityPlayer player,ArmorProfile profile){return PlayerState.level(player)>=8?8:-1;}
    public static ArmorMesh.Slot convert(EntityEquipmentSlot slot){switch(slot){case HEAD:return ArmorMesh.Slot.HEAD;case CHEST:return ArmorMesh.Slot.CHEST;case LEGS:return ArmorMesh.Slot.LEGS;case FEET:return ArmorMesh.Slot.FEET;default:throw new IllegalArgumentException("Armor slot required");}}
    public static final class Armor extends ItemArmor implements Gear.Ordered{
        public final ArmorProfile profile;
        Armor(ArmorProfile profile,ArmorMaterial material,EntityEquipmentSlot slot){super(material,0,slot);this.profile=profile;setCreativeTab(net.minecraft.creativetab.CreativeTabs.COMBAT);}
        @Override public Order order(){return OriginalArmorRegistry.order(profile);}
        @Override public String getArmorTexture(ItemStack stack,Entity entity,EntityEquipmentSlot slot,String type){return "valearmor:textures/armor/"+profile.key+".png";}
        @Override public boolean hasEffect(ItemStack stack){return false;}
        @SideOnly(Side.CLIENT) @Override public net.minecraft.client.model.ModelBiped getArmorModel(EntityLivingBase entity,ItemStack stack,EntityEquipmentSlot slot,net.minecraft.client.model.ModelBiped original){return village.vale.armor.client.OriginalArmorModels.get(profile,slot,original);}
        @SideOnly(Side.CLIENT) @Override public void addInformation(ItemStack stack,net.minecraft.world.World world,java.util.List<String> lines,net.minecraft.client.util.ITooltipFlag flag){
            lines.add(profile.title+" | "+order().title);lines.add("Four matching issued pieces unlock "+profile.role.name().toLowerCase(Locale.ROOT)+" resonance.");
            if(profile.role==ArmorProfile.Role.FLIGHT)lines.add("Double-tap jump: stamina flight. Rest on the ground to recharge.");
            else lines.add("Resonance follows your current permitted opponent.");
            lines.add("Ordinary armor still protects; powers require your issued serials.");
        }
    }
}
