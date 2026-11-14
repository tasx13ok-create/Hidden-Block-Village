package narutodestinationvillages.compat;
import net.minecraft.inventory.IInventory;import net.minecraft.item.ItemStack;import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.*;
/** Original nonnull Minecraft inventory surface around unchanged legacy storage logic. */
public interface LegacyInventory extends IInventory {
 ItemStack legacyStack(int slot);ItemStack legacyDecr(int slot,int count);ItemStack legacyRemove(int slot);
 void legacySet(int slot,ItemStack stack);void legacyOpen();void legacyClose();boolean legacyValid(int slot,ItemStack stack);
 default ItemStack getStackInSlot(int i){return Legacy112.present(legacyStack(i));}
 default ItemStack decrStackSize(int i,int n){return Legacy112.present(legacyDecr(i,n));}
 default ItemStack removeStackFromSlot(int i){return Legacy112.present(legacyRemove(i));}
 default void setInventorySlotContents(int i,ItemStack s){legacySet(i,Legacy112.nullable(s));}
 default void openInventory(EntityPlayer p){legacyOpen();}default void closeInventory(EntityPlayer p){legacyClose();}
 default void openInventory(){legacyOpen();}default void closeInventory(){legacyClose();}
 default boolean isItemValidForSlot(int i,ItemStack s){return legacyValid(i,Legacy112.nullable(s));}
 default boolean isEmpty(){for(int i=0;i<getSizeInventory();i++)if(!getStackInSlot(i).isEmpty())return false;return true;}
 default int getField(int id){return 0;}default void setField(int id,int value){}default int getFieldCount(){return 0;}
 default void clear(){for(int i=0;i<getSizeInventory();i++)legacySet(i,null);}
 default ITextComponent getDisplayName(){return new TextComponentString(getName());}
}
