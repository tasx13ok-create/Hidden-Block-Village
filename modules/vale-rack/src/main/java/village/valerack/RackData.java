package village.valerack;

import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.text.*;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.INBTSerializable;

/** Six owned physical slots, independent of InventoryPlayer's thirty-six cells. */
public final class RackData implements IInventory,INBTSerializable<NBTTagCompound> {
    public static final int SIZE=6,VERSION=2;
    public final CombatHotbar combat=new CombatHotbar();
    @CapabilityInject(RackData.class) public static final Capability<RackData> CAP=null;
    private final ItemStack[] stacks=new ItemStack[SIZE];
    private final NBTTagCompound[] envelopes=new NBTTagCompound[SIZE];
    private final List<NBTTagCompound> opaque=new ArrayList<>();
    private NBTTagCompound original=new NBTTagCompound();
    private final EntityPlayer owner;
    public long revision;public boolean locked,deathHandled,deathSeen,deathKeep;public boolean dirty=true;
    public RackData(EntityPlayer owner){this.owner=owner;Arrays.fill(stacks,ItemStack.EMPTY);}
    public static void register(){CapabilityManager.INSTANCE.register(RackData.class,new Capability.IStorage<RackData>(){
        @Override public NBTBase writeNBT(Capability<RackData> cap,RackData data,EnumFacing side){return data.serializeNBT();}
        @Override public void readNBT(Capability<RackData> cap,RackData data,EnumFacing side,NBTBase nbt){if(nbt instanceof NBTTagCompound)data.deserializeNBT((NBTTagCompound)nbt);}
    },()->new RackData(null));}
    public static RackData of(EntityPlayer player){RackData rack=player.getCapability(CAP,null);if(rack==null)throw new IllegalStateException("Rack capability missing");return rack;}
    @Override public NBTTagCompound serializeNBT(){
        if(locked)return original.copy();NBTTagCompound root=original.copy();root.setInteger("Version",VERSION);root.setLong("Revision",revision);
        NBTTagList items=new NBTTagList();for(int i=0;i<SIZE;i++)if(!stacks[i].isEmpty()){
            NBTTagCompound entry=envelopes[i]==null?new NBTTagCompound():envelopes[i].copy();entry.setInteger("Slot",i);entry.setTag("Stack",stacks[i].writeToNBT(new NBTTagCompound()));items.appendTag(entry);
        }for(NBTTagCompound entry:opaque)items.appendTag(entry.copy());root.setTag("Items",items);root.setTag("CombatHotbar",combat.save());return root;
    }
    @Override public void deserializeNBT(NBTTagCompound nbt){
        original=nbt.copy();combat.clear();Arrays.fill(stacks,ItemStack.EMPTY);Arrays.fill(envelopes,null);opaque.clear();locked=nbt.hasKey("Version")&&(!nbt.hasKey("Version",3)||(nbt.getInteger("Version")!=VERSION&&nbt.getInteger("Version")!=1))||nbt.hasKey("Items")&&(!nbt.hasKey("Items",9)||((NBTTagList)nbt.getTag("Items")).tagCount()>0&&((NBTTagList)nbt.getTag("Items")).getTagType()!=10)||nbt.hasKey("Revision")&&!nbt.hasKey("Revision",4);revision=Math.max(0,nbt.getLong("Revision"));
        if(!locked&&nbt.hasKey("CombatHotbar"))locked=!nbt.hasKey("CombatHotbar",10)||!combat.load(nbt.getCompoundTag("CombatHotbar"));
        if(!locked){NBTTagList list=nbt.getTagList("Items",10);for(int i=0;i<list.tagCount();i++){
            NBTTagCompound entry=list.getCompoundTagAt(i);int index=entry.hasKey("Slot",3)?entry.getInteger("Slot"):-1;ItemStack stack=entry.hasKey("Stack",10)?new ItemStack(entry.getCompoundTag("Stack")):ItemStack.EMPTY;
            if(index<0||index>=SIZE||!stacks[index].isEmpty()||stack.isEmpty()||!RackPolicy.combatType(stack)||!RackPolicy.bounded(stack))opaque.add(entry.copy());
            else{stacks[index]=stack;envelopes[index]=entry.copy();}
        }}if(combat.active)for(ItemStack stack:stacks)if(!stack.isEmpty())locked=true;dirty=true;
    }
    /** Moves ownership during clone; the detached old entity cannot hold a second live copy. */
    public void detach(){Arrays.fill(stacks,ItemStack.EMPTY);Arrays.fill(envelopes,null);opaque.clear();original=new NBTTagCompound();combat.clear();locked=false;markDirty();}
    public void raw(int slot,ItemStack stack){stacks[slot]=stack.isEmpty()?ItemStack.EMPTY:stack;}
    @Override public int getSizeInventory(){return SIZE;}
    @Override public boolean isEmpty(){for(ItemStack stack:stacks)if(!stack.isEmpty())return false;return true;}
    @Override public ItemStack getStackInSlot(int index){return index>=0&&index<SIZE?stacks[index]:ItemStack.EMPTY;}
    @Override public ItemStack decrStackSize(int index,int amount){if(locked||index<0||index>=SIZE||amount<=0)return ItemStack.EMPTY;ItemStack result=stacks[index].splitStack(amount);if(stacks[index].isEmpty())stacks[index]=ItemStack.EMPTY;markDirty();return result;}
    @Override public ItemStack removeStackFromSlot(int index){if(locked||index<0||index>=SIZE)return ItemStack.EMPTY;ItemStack result=stacks[index];stacks[index]=ItemStack.EMPTY;markDirty();return result;}
    @Override public void setInventorySlotContents(int index,ItemStack stack){if(index<0||index>=SIZE||locked)return;stacks[index]=stack;markDirty();}
    @Override public int getInventoryStackLimit(){return 64;}
    @Override public void markDirty(){revision=revision==Long.MAX_VALUE?0:revision+1;dirty=true;}
    @Override public boolean isUsableByPlayer(EntityPlayer p){return owner==p&&!p.isDead&&p.isEntityAlive()&&!p.isSpectator();}
    @Override public void openInventory(EntityPlayer p){}
    @Override public void closeInventory(EntityPlayer p){}
    @Override public boolean isItemValidForSlot(int index,ItemStack stack){return !locked&&!combat.active&&owner!=null&&index>=0&&index<SIZE&&RackPolicy.combatAccepts(owner,stack);}
    @Override public int getField(int id){return 0;}
    @Override public void setField(int id,int value){}
    @Override public int getFieldCount(){return 0;}
    @Override public void clear(){if(!locked){Arrays.fill(stacks,ItemStack.EMPTY);markDirty();}}
    @Override public String getName(){return "Combat hotbar";}
    @Override public boolean hasCustomName(){return false;}
    @Override public ITextComponent getDisplayName(){return new TextComponentString(getName());}
    public static final class Provider implements ICapabilitySerializable<NBTTagCompound>{
        final RackData data;public Provider(EntityPlayer p){data=new RackData(p);}
        @Override public boolean hasCapability(Capability<?> capability,@Nullable EnumFacing side){return capability==CAP;}
        @Override @Nullable public <T>T getCapability(Capability<T> capability,@Nullable EnumFacing side){return capability==CAP?CAP.cast(data):null;}
        @Override public NBTTagCompound serializeNBT(){return data.serializeNBT();}
        @Override public void deserializeNBT(NBTTagCompound tag){data.deserializeNBT(tag);}
    }
}
