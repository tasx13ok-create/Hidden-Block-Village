package village.vale.culinary;
import java.util.*;import net.minecraft.entity.player.*;import net.minecraft.inventory.*;import net.minecraft.item.*;import net.minecraft.nbt.*;import net.minecraft.tileentity.*;import net.minecraft.util.*;import net.minecraftforge.common.capabilities.Capability;import net.minecraftforge.items.*;import net.minecraftforge.items.wrapper.SidedInvWrapper;
/** Atomic completion: inputs/fuel containers and output capacity are checked before publication. */
public final class KitchenTile extends TileEntityLockableLoot implements ISidedInventory,ITickable {
 private NonNullList<ItemStack> inventory=NonNullList.withSize(9,ItemStack.EMPTY);public int machine,burn,progress;private int recipe=-1;private final Map<EnumFacing,IItemHandler> handlers=new EnumMap<>(EnumFacing.class);
 public KitchenTile(){}public KitchenTile(int m){machine=m;}
 @Override protected NonNullList<ItemStack> getItems(){return inventory;}
 @Override public int getSizeInventory(){return 9;}
 @Override public boolean isEmpty(){for(ItemStack s:inventory)if(!s.isEmpty())return false;return true;}
 @Override public int getInventoryStackLimit(){return 64;}
 @Override public String getName(){return "Kitchen "+Culinary.STATIONS[Math.max(0,Math.min(3,machine))]+" (in1-3 fuel4 out5 return6-9)";}
 @Override public String getGuiID(){return "minecraft:container";}
 @Override public Container createContainer(InventoryPlayer inv,EntityPlayer p){return new KitchenContainer(inv,this);}
 @Override public boolean isItemValidForSlot(int i,ItemStack s){return i>=0&&i<3||i==3&&TileEntityFurnace.getItemBurnTime(s)>0;}
 @Override public int[] getSlotsForFace(EnumFacing side){return side==EnumFacing.DOWN?new int[]{4,5,6,7,8}:side==EnumFacing.UP?new int[]{0,1,2}:new int[]{3};}
 @Override public boolean canInsertItem(int i,ItemStack s,EnumFacing side){return isItemValidForSlot(i,s);}
 @Override public boolean canExtractItem(int i,ItemStack s,EnumFacing side){return i>=4;}
 @Override public <T>T getCapability(Capability<T> cap,EnumFacing side){if(cap==CapabilityItemHandler.ITEM_HANDLER_CAPABILITY){EnumFacing f=side==null?EnumFacing.UP:side;return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handlers.computeIfAbsent(f,k->new SidedInvWrapper(this,k)));}return super.getCapability(cap,side);}
 @Override public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);inventory=NonNullList.withSize(9,ItemStack.EMPTY);ItemStackHelper.loadAllItems(n,inventory);machine=Math.max(0,Math.min(3,n.getInteger("Machine")));burn=Math.max(0,Math.min(20000,n.getInteger("Burn")));progress=Math.max(0,Math.min(200,n.getInteger("Progress")));recipe=n.hasKey("Recipe")?n.getInteger("Recipe"):-1;}
 @Override public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);ItemStackHelper.saveAllItems(n,inventory);n.setInteger("Machine",machine);n.setInteger("Burn",burn);n.setInteger("Progress",progress);n.setInteger("Recipe",recipe);return n;}
 private static boolean merge(NonNullList<ItemStack> slots,int start,int end,ItemStack add){for(int i=start;i<end&&!add.isEmpty();i++){ItemStack s=slots.get(i);int max=Math.min(64,add.getMaxStackSize());if(s.isEmpty()){int take=Math.min(max,add.getCount());ItemStack copy=add.copy();copy.setCount(take);slots.set(i,copy);add.shrink(take);}else if(ItemStack.areItemsEqual(s,add)&&ItemStack.areItemStackTagsEqual(s,add)){int take=Math.min(Math.max(0,max-s.getCount()),add.getCount());s.grow(take);add.shrink(take);}}return add.isEmpty();}
 private NonNullList<ItemStack> after(KitchenProcesses.Process p,boolean includeFuel){NonNullList<ItemStack> copy=NonNullList.withSize(9,ItemStack.EMPTY);for(int i=0;i<9;i++)copy.set(i,inventory.get(i).copy());if(!merge(copy,4,5,p.output.copy()))return null;for(int i=0;i<p.inputs.length;i++){ItemStack one=copy.get(i).copy();one.setCount(1);for(int j=0;j<p.inputs[i].getCount();j++){ItemStack container=one.getItem().getContainerItem(one);if(!container.isEmpty()&&!merge(copy,5,9,container))return null;}copy.get(i).shrink(p.inputs[i].getCount());}if(includeFuel){ItemStack one=copy.get(3).copy();one.setCount(1);ItemStack rest=one.getItem().getContainerItem(one);if(!rest.isEmpty()&&!merge(copy,5,9,rest))return null;copy.get(3).shrink(1);}return copy;}
 @Override public void update(){if(world==null||world.isRemote)return;KitchenProcesses.Process p=KitchenProcesses.match(machine,new ItemStack[]{inventory.get(0),inventory.get(1),inventory.get(2)});int next=p==null?-1:KitchenProcesses.ALL.indexOf(p);if(next!=recipe){recipe=next;progress=0;markDirty();}if(p==null)return;boolean fuel=burn<=0;if(fuel&&TileEntityFurnace.getItemBurnTime(inventory.get(3))<=0)return;NonNullList<ItemStack> completed=after(p,fuel);if(completed==null)return;
  if(fuel){ItemStack one=inventory.get(3).copy();one.setCount(1);burn=TileEntityFurnace.getItemBurnTime(one);ItemStack rest=one.getItem().getContainerItem(one);if(!rest.isEmpty())merge(inventory,5,9,rest);inventory.get(3).shrink(1);}
  burn--;progress++;if(progress>=p.ticks){completed=after(p,false);if(completed!=null){inventory=completed;progress=0;}}markDirty();
 }
}
