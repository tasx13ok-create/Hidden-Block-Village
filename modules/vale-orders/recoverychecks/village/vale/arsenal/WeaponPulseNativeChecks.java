package village.vale.arsenal;

import java.io.File;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import village.vale.*;

/** Actual cosmetic entity lifecycles and real issued-weapon settlement with failed presentation. */
public final class WeaponPulseNativeChecks {
    private final EntityPlayerMP p;private final WorldServer world;private final NBTTagCompound account;
    private final NBTTagList inventory;private final int selected;private final Vec3d position;private final float yaw,pitch;
    private final Gate gate=new Gate();private int assertions,receipt;
    private WeaponPulseNativeChecks(EntityPlayerMP p){this.p=p;world=p.getServerWorld();account=PlayerState.data(p).copy();inventory=p.inventory.writeToNBT(new NBTTagList());selected=p.inventory.currentItem;position=p.getPositionVector();yaw=p.rotationYaw;pitch=p.rotationPitch;}
    public static int run(EntityPlayerMP p){
        if(!Boolean.getBoolean("valeorders.recoverycheck")||!"vale-recovery-test".equals(p.getServer().getFolderName())||!new File(".vale-recovery-test").isFile())throw new IllegalStateException("Private weapon VFX fixture required");
        WeaponPulseNativeChecks c=new WeaponPulseNativeChecks(p);MinecraftForge.EVENT_BUS.register(c.gate);
        try{c.exercise();return c.assertions;}finally{MinecraftForge.EVENT_BUS.unregister(c.gate);c.close();}
    }
    private void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError("Weapon pulse native: "+message);}
    private List<WeaponPulse> live(){return world.getEntities(WeaponPulse.class,w->!w.isDead);}
    private void erase(){for(WeaponPulse pulse:new ArrayList<>(world.getEntities(WeaponPulse.class,w->true)))world.removeEntityDangerously(pulse);}
    private void exercise(){
        for(int cx=47;cx<=51;cx++)for(int cz=47;cz<=51;cz++)world.getChunk(cx,cz);
        p.setPositionAndRotation(784.5,90,784.5,0,0);p.setSneaking(false);p.setHealth(p.getMaxHealth());
        for(ArsenalSpec spec:ArsenalSpec.values()){
            erase();WeaponPulse.cast(p,spec,.65f);List<WeaponPulse> entities=live();
            check(entities.size()==1,"Exactly one carrier for "+spec.key);WeaponPulse pulse=entities.get(0);
            check(pulse.spec()==spec&&pulse.ownerId()==p.getEntityId()&&pulse.power()>=0&&pulse.power()<=1,"Bound spec/owner/power for "+spec.key);
            check(!pulse.canBeCollidedWith()&&!pulse.canBePushed(),"Non-colliding carrier "+spec.key);
            check(!pulse.writeToNBTOptional(new NBTTagCompound()),"Carrier must not persist "+spec.key);
            int life=pulse.life();for(int tick=0;tick<life+3;tick++)world.updateEntityWithOptionalForce(pulse,true);check(pulse.isDead,"Bounded expiry for "+spec.key);
        }
        erase();WeaponPulse.arc(p,ArsenalSpec.STORM_PAIR,p.getPositionVector(),p.getPositionVector().add(2,1,0));
        check(live().size()==1&&live().get(0).kind()==WeaponPulse.ARC,"Native secondary arc");
        erase();WeaponPulse.swing(p,ArsenalSpec.CRESCENT_TALONS,false);check(live().size()==1&&live().get(0).kind()==WeaponPulse.SWING,"Native accepted-hit slash carrier");
        erase();for(int i=0;i<40;i++)WeaponPulse.swing(p,ArsenalSpec.SWEEP_GLAIVE,true);check(live().size()==32,"Per-owner cosmetic budget bounded");
        erase();WeaponPulse.arc(p,ArsenalSpec.STORM_PAIR,new Vec3d(Double.NaN,0,0),p.getPositionVector());check(live().isEmpty(),"Nonfinite cosmetic input refused");
        for(int mode=1;mode<=2;mode++){
            erase();gate.mode=mode;ArsenalSpec spec=ArsenalSpec.CRESCENT_GUARD;ItemStack weapon=equip(spec);
            ArsenalLogic.state(p).removeTag("cooldowns");ArsenalLogic.state(p).setLong("ready",0);ArsenalLogic.state(p).removeTag("busy");
            for(ArsenalBlade blade:Arsenal.items().values())p.getCooldownTracker().setCooldown(blade,0);
            PlayerState.setResolve(p,100);check(ArsenalLogic.ready(p,weapon,spec,false),"Real issued guard weapon ready before cosmetic failure");
            ArsenalLogic.release(p,weapon,spec,40);
            check(PlayerState.resolve(p)==100-spec.cost,"Cosmetic refusal/exception does not refund real guard cost");
            check(ArsenalLogic.state(p).getLong("guardUntil")>ArsenalAccess.now(p),"Accepted real guard retained after failed cosmetic");
            check(ArsenalCooldowns.readyAt(ArsenalLogic.state(p),spec)>ArsenalAccess.now(p),"Real cooldown retained after failed cosmetic");
            check(!ArsenalLogic.state(p).getBoolean("busy")&&live().isEmpty(),"No stuck reservation or orphan after cosmetic failure");
            gate.mode=0;
        }
    }
    private ItemStack issue(String key){RewardLedger.get(p).issue(p,key,1,"native/weapon-vfx/"+(receipt++));for(int i=0;i<p.inventory.mainInventory.size();i++){ItemStack item=p.inventory.mainInventory.get(i);if(item.getItem()==Gear.ITEMS.get(key)&&RewardLedger.get(p).valid(p,item)){p.inventory.mainInventory.set(i,ItemStack.EMPTY);return item;}}throw new AssertionError("Missing issued "+key);}
    private ItemStack equip(ArsenalSpec spec){
        for(int i=0;i<p.inventory.getSizeInventory();i++)p.inventory.setInventorySlotContents(i,ItemStack.EMPTY);p.inventory.currentItem=0;
        String[] parts={"helmet","chestplate","leggings","boots"};EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
        for(int i=0;i<4;i++)p.setItemStackToSlot(slots[i],issue(spec.order.key+"_"+parts[i]));ItemStack weapon=issue(spec.key);p.setHeldItem(EnumHand.MAIN_HAND,weapon);return weapon;
    }
    private void close(){erase();p.inventory.readFromNBT(inventory);p.inventory.currentItem=selected;p.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).setTag("ValeOrders",account);p.setPositionAndRotation(position.x,position.y,position.z,yaw,pitch);for(ArsenalBlade blade:Arsenal.items().values())p.getCooldownTracker().setCooldown(blade,0);}
    public static final class Gate {
        int mode;
        @SubscribeEvent(priority=EventPriority.LOWEST)public void spawn(EntityJoinWorldEvent event){if(event.getEntity() instanceof WeaponPulse){if(mode==1)event.setCanceled(true);if(mode==2)throw new IllegalStateException("Injected cosmetic-only weapon spawn failure");}}
    }
}
