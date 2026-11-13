package village.valerack;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class RackContainer extends Container {
    public final RackData rack;private final EntityPlayer owner;private final World world;private final int dimension;private final long nonce;
    public RackContainer(EntityPlayer player,RackData rack){
        this.owner=player;this.world=player.world;this.dimension=player.dimension;this.rack=rack;RackSessions.Session session=player instanceof net.minecraft.entity.player.EntityPlayerMP?RackSessions.current((net.minecraft.entity.player.EntityPlayerMP)player):null;nonce=session==null?0:session.nonce;
        for(int i=0;i<6;i++)addSlotToContainer(new Slot(rack,i,43+i*18,36){
            @Override public boolean isItemValid(ItemStack stack){return rack.isItemValidForSlot(getSlotIndex(),stack);}
            @Override public boolean canTakeStack(EntityPlayer p){return canInteractWith(p)&&!rack.locked;}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlotToContainer(new Slot(player.inventory,9+row*9+col,16+col*18,88+row*18));
        for(int col=0;col<9;col++)addSlotToContainer(new Slot(player.inventory,col,16+col*18,146));
    }
    @Override public boolean canInteractWith(EntityPlayer p){if(p!=owner||p.world!=world||p.dimension!=dimension||!rack.isUsableByPlayer(p)||rack.combat.active)return false;if(p.world.isRemote)return true;RackSessions.Session session=p instanceof net.minecraft.entity.player.EntityPlayerMP?RackSessions.current((net.minecraft.entity.player.EntityPlayerMP)p):null;return RackData.of(p)==rack&&session!=null&&session.nonce==nonce;}
    @Override public ItemStack slotClick(int id,int button,ClickType type,EntityPlayer p){return !canInteractWith(p)||rack.locked||type==ClickType.CLONE||type==ClickType.QUICK_CRAFT&&((button>>2)&3)==2?ItemStack.EMPTY:super.slotClick(id,button,type,p);}
    @Override public ItemStack transferStackInSlot(EntityPlayer p,int index){
        if(!canInteractWith(p)||rack.locked||index<0||index>=42)return ItemStack.EMPTY;Slot slot=inventorySlots.get(index);if(!slot.getHasStack()||!slot.canTakeStack(p))return ItemStack.EMPTY;
        ItemStack held=slot.getStack(),before=held.copy();
        if(index<6){if(!mergeItemStack(held,6,42,false))return ItemStack.EMPTY;}
        else{if(!RackPolicy.combatAccepts(p,held)||!mergeItemStack(held,0,6,false))return ItemStack.EMPTY;}
        if(held.isEmpty())slot.putStack(ItemStack.EMPTY);else slot.onSlotChanged();slot.onTake(p,held);rack.markDirty();return before;
    }
}
