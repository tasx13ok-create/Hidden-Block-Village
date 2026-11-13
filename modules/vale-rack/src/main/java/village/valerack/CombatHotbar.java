package village.valerack;

import java.util.Arrays;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumHand;

/** One physical active hotbar. The inactive normal nine slots are parked, never duplicated. */
public final class CombatHotbar {
    private final ItemStack[] normal=new ItemStack[9];
    public boolean active;
    public int normalSelected,combatSelected;
    public CombatHotbar(){clear();}
    public void clear(){Arrays.fill(normal,ItemStack.EMPTY);active=false;normalSelected=combatSelected=0;}
    public ItemStack parked(int i){return normal[i];}
    public ItemStack combat(EntityPlayer p,RackData rack,int i){return active?p.inventory.mainInventory.get(i):rack.getStackInSlot(i);}
    public void setCombat(EntityPlayer p,RackData rack,int i,ItemStack stack){
        if(active){if(p.inventory.currentItem==i&&p.isHandActive()&&p.getActiveHand()==EnumHand.MAIN_HAND)p.resetActiveHand();p.inventory.mainInventory.set(i,stack);}
        else rack.raw(i,stack);
    }
    public String toggle(EntityPlayerMP p,RackData rack){
        if(p.inventory.currentItem<0||p.inventory.currentItem>=9)return "Native hand selection is invalid.";
        // All failure gates precede assignments. Leaving combat always returns the original normal loadout.
        if(!active){
            for(int i=0;i<6;i++){ItemStack stack=rack.getStackInSlot(i);if(!stack.isEmpty()&&!RackPolicy.combatAccepts(p,stack))return "A combat slot contains an unavailable or unowned item.";}
            for(int i=0;i<9;i++)if(!RackPolicy.bounded(p.inventory.mainInventory.get(i)))return "A hotbar item is too large to switch safely.";
        }
        if(!active){
            if(p.isHandActive()&&p.getActiveHand()==EnumHand.MAIN_HAND)p.resetActiveHand();
            normalSelected=p.inventory.currentItem;
            for(int i=0;i<9;i++){normal[i]=p.inventory.mainInventory.get(i);p.inventory.mainInventory.set(i,i<6?rack.getStackInSlot(i):ItemStack.EMPTY);}
            for(int i=0;i<6;i++)rack.raw(i,ItemStack.EMPTY);
            active=true;p.inventory.currentItem=Math.max(0,Math.min(5,combatSelected));
        }else{
            // Slots 7-9 are kept empty by the normal input path. If another mod placed
            // items there, move the additions to free main-inventory cells so the
            // original normal hotbar is restored. Refuse before mutation if full.
            int[] free=new int[3];Arrays.fill(free,-1);boolean[] used=new boolean[36];
            for(int i=6;i<9;i++)if(!p.inventory.mainInventory.get(i).isEmpty()&&!normal[i].isEmpty()){
                for(int n=9;n<36;n++)if(!used[n]&&p.inventory.mainInventory.get(n).isEmpty()){free[i-6]=n;used[n]=true;break;}
                if(free[i-6]<0)return "Free an inventory slot for an item added to the inactive hotbar.";
            }
            if(p.isHandActive()&&p.getActiveHand()==EnumHand.MAIN_HAND)p.resetActiveHand();
            combatSelected=Math.max(0,Math.min(5,p.inventory.currentItem));
            for(int i=0;i<6;i++)rack.raw(i,p.inventory.mainInventory.get(i));
            for(int i=0;i<9;i++){
                if(i>=6&&!p.inventory.mainInventory.get(i).isEmpty()){
                    if(free[i-6]>=0)p.inventory.mainInventory.set(free[i-6],p.inventory.mainInventory.get(i));
                    else {normal[i]=p.inventory.mainInventory.get(i);}
                }
                p.inventory.mainInventory.set(i,normal[i]);normal[i]=ItemStack.EMPTY;
            }
            active=false;p.inventory.currentItem=Math.max(0,Math.min(8,normalSelected));
        }
        rack.markDirty();p.inventory.markDirty();return "";
    }
    public NBTTagCompound save(){
        NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("Active",active);tag.setInteger("NormalSelected",normalSelected);tag.setInteger("CombatSelected",combatSelected);
        NBTTagList list=new NBTTagList();for(int i=0;i<9;i++)if(!normal[i].isEmpty()){NBTTagCompound row=new NBTTagCompound();row.setInteger("Slot",i);row.setTag("Stack",normal[i].writeToNBT(new NBTTagCompound()));list.appendTag(row);}tag.setTag("Normal",list);return tag;
    }
    public boolean load(NBTTagCompound tag){
        clear();if(!tag.hasKey("Active",1)||!tag.hasKey("NormalSelected",3)||!tag.hasKey("CombatSelected",3)||!tag.hasKey("Normal",9))return false;
        active=tag.getBoolean("Active");normalSelected=tag.getInteger("NormalSelected");combatSelected=tag.getInteger("CombatSelected");
        if(normalSelected<0||normalSelected>8||combatSelected<0||combatSelected>5)return false;
        NBTTagList list=tag.getTagList("Normal",10);if(((NBTTagList)tag.getTag("Normal")).tagCount()!=list.tagCount()||list.tagCount()>9)return false;
        for(int n=0;n<list.tagCount();n++){NBTTagCompound row=list.getCompoundTagAt(n);int i=row.hasKey("Slot",3)?row.getInteger("Slot"):-1;if(i<0||i>8||!normal[i].isEmpty()||!row.hasKey("Stack",10))return false;ItemStack stack=new ItemStack(row.getCompoundTag("Stack"));if(stack.isEmpty()||!RackPolicy.bounded(stack))return false;normal[i]=stack;}
        return active||list.tagCount()==0;
    }
    public void dropParked(EntityPlayer p,java.util.Collection<EntityItem> drops){
        for(ItemStack stack:normal)if(!stack.isEmpty()&&!EnchantmentHelper.hasVanishingCurse(stack)){EntityItem item=new EntityItem(p.world,p.posX,p.posY,p.posZ,stack);item.setDefaultPickupDelay();drops.add(item);}clear();
    }
}
