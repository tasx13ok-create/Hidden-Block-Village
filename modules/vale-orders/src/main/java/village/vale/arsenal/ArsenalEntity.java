package village.vale.arsenal;

import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.world.World;

/** A flying/field representation never contains a pickup or grants another item. */
public abstract class ArsenalEntity extends Entity {
    private static final DataParameter<Integer> SPEC=EntityDataManager.createKey(ArsenalEntity.class,DataSerializers.VARINT);
    private static final DataParameter<Integer> MODE=EntityDataManager.createKey(ArsenalEntity.class,DataSerializers.VARINT);
    private static final DataParameter<Integer> OWNER=EntityDataManager.createKey(ArsenalEntity.class,DataSerializers.VARINT);
    private static final DataParameter<Float> POWER=EntityDataManager.createKey(ArsenalEntity.class,DataSerializers.FLOAT);
    protected UUID ownerId;
    protected ItemStack issued=ItemStack.EMPTY;
    protected int age;
    protected ArsenalEntity(World world) {super(world);setSize(.3f,.3f);}
    protected void initialize(EntityPlayerMP owner,ItemStack stack,ArsenalSpec spec,int mode,float power) {
        ownerId=owner.getUniqueID();issued=stack.copy();issued.setCount(1);
        dataManager.set(SPEC,spec.ordinal());dataManager.set(MODE,mode);dataManager.set(OWNER,owner.getEntityId());
        dataManager.set(POWER,Math.max(0,Math.min(1,power)));
    }
    @Override protected void entityInit() {dataManager.register(SPEC,-1);dataManager.register(MODE,-1);dataManager.register(OWNER,-1);dataManager.register(POWER,0f);}
    public ArsenalSpec spec() {return ArsenalSpec.at(dataManager.get(SPEC));}
    public int mode() {return dataManager.get(MODE);}
    public float power() {float f=dataManager.get(POWER);return Float.isFinite(f)?Math.max(0,Math.min(1,f)):0;}
    public int ownerEntityId() {return dataManager.get(OWNER);}
    public ItemStack proofStack() {return issued;}
    protected EntityPlayerMP owner() {
        net.minecraft.entity.player.EntityPlayer p=ownerId==null?null:world.getPlayerEntityByUUID(ownerId);
        return p instanceof EntityPlayerMP?(EntityPlayerMP)p:null;
    }
    public boolean ownedBy(EntityPlayerMP p) {return p!=null&&p.getUniqueID().equals(ownerId);}
    protected boolean validOwner(EntityPlayerMP p) {return p!=null&&p.world==world&&spec()!=null&&ArsenalLogic.heldProof(p,issued,spec());}
    @Override protected void writeEntityToNBT(NBTTagCompound nbt) {
        if(ownerId!=null)nbt.setUniqueId("owner",ownerId);
        nbt.setTag("issued",issued.writeToNBT(new NBTTagCompound()));nbt.setInteger("spec",dataManager.get(SPEC));
        nbt.setInteger("mode",mode());nbt.setFloat("power",power());nbt.setInteger("age",age);
    }
    @Override protected void readEntityFromNBT(NBTTagCompound nbt) {
        ownerId=nbt.hasUniqueId("owner")?nbt.getUniqueId("owner"):null;
        issued=new ItemStack(nbt.getCompoundTag("issued"));
        ArsenalSpec spec=ArsenalSpec.at(nbt.getInteger("spec"));dataManager.set(SPEC,spec==null?-1:spec.ordinal());
        dataManager.set(MODE,nbt.getInteger("mode"));float power=nbt.getFloat("power");dataManager.set(POWER,Float.isFinite(power)?Math.max(0,Math.min(1,power)):0);
        age=nbt.getInteger("age");
    }
    @Override public boolean canBeCollidedWith() {return false;}
    @Override public boolean canBePushed() {return false;}
}
