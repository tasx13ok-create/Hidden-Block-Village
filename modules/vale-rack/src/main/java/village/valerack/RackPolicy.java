package village.valerack;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.ResourceLocation;
import village.vale.Gear;
import village.vale.RewardLedger;

public final class RackPolicy {
    private RackPolicy(){}
    public static boolean bounded(ItemStack stack){return stack!=null&&(stack.isEmpty()||stack.getCount()>0&&stack.getCount()<=Math.min(64,stack.getMaxStackSize())&&(!stack.hasTagCompound()||stack.getTagCompound().toString().getBytes(StandardCharsets.UTF_8).length<=65536));}
    /** Physical combat loadout supports scrolls, codices, consumables and ordinary items. */
    public static boolean combatType(ItemStack stack){
        if(stack==null||stack.isEmpty())return false;Item item=stack.getItem();ResourceLocation id=item.getRegistryName();
        return id!=null&&Item.REGISTRY.containsKey(id)&&Item.REGISTRY.getObject(id)==item&&(!id.getNamespace().equals("valeorders")||Gear.ITEMS.get(id.getPath())==item);
    }
    public static boolean combatAccepts(EntityPlayer owner,ItemStack stack){
        if(!combatType(stack)||!bounded(stack))return false;
        Item item=stack.getItem();
        // Crafted garments, meals, kitchen supplies and tonics are intentionally tradeable;
        // their ordinary native use path does not require a reward receipt.
        return owner.world.isRemote||!item.getRegistryName().getNamespace().equals("valeorders")
            ||item instanceof village.vale.OriginalOutfits.Garment||item instanceof village.vale.FieldItems.Meal||village.vale.culinary.Culinary.ITEMS.containsValue(item)
            ||RewardLedger.get(owner).valid(owner,stack);
    }
    public static boolean type(ItemStack stack){
        if(stack==null||stack.isEmpty())return false;
        Item item=stack.getItem();ResourceLocation id=item.getRegistryName();
        if(id==null)return false;
        if(id.getNamespace().equals("valeorders")){
            // A matching name alone is insufficient: both Core's map and native registry
            // must point to this exact registered item object.
            boolean registered=Gear.ITEMS.get(id.getPath())==item&&Item.REGISTRY.containsKey(id)&&Item.REGISTRY.getObject(id)==item;
            return registered&&(item instanceof ItemSword||Arrays.asList("needle_fan","snap_disc","smoke_capsule").contains(id.getPath()));
        }
        return item instanceof ItemSword||item instanceof ItemAxe||item instanceof ItemBow||item instanceof ItemShield;
    }
    public static boolean accepts(EntityPlayer owner,ItemStack stack){
        if(!type(stack)||stack.getCount()<1||stack.getCount()>Math.min(64,stack.getMaxStackSize()))return false;
        if(stack.hasTagCompound()&&stack.getTagCompound().toString().getBytes(StandardCharsets.UTF_8).length>65536)return false;
        ResourceLocation id=stack.getItem().getRegistryName();
        return owner.world.isRemote||!id.getNamespace().equals("valeorders")||RewardLedger.get(owner).valid(owner,stack);
    }
}
