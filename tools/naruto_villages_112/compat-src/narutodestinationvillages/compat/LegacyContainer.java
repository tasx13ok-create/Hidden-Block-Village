package narutodestinationvillages.compat;
import net.minecraft.inventory.*;import net.minecraft.entity.player.EntityPlayer;import net.minecraft.item.ItemStack;
/** Original click-protocol boundary. The seven vanilla click ordinals retain 1.7 meanings. */
public abstract class LegacyContainer extends Container {
 @Override public final ItemStack slotClick(int slot,int button,ClickType type,EntityPlayer player){return Legacy112.present(legacySlotClick(slot,button,type.ordinal(),player));}
 protected ItemStack legacySlotClick(int slot,int button,int mode,EntityPlayer player){
  if(mode<0||mode>=ClickType.values().length)return null;
  return Legacy112.nullable(super.slotClick(slot,button,ClickType.values()[mode],player));
 }
 @Override public final ItemStack transferStackInSlot(EntityPlayer player,int index){return Legacy112.present(legacyTransfer(player,index));}
 public ItemStack legacyTransfer(EntityPlayer player,int index){return null;}
}
