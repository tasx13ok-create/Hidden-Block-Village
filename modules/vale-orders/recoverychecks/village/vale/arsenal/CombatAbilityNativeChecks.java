package village.vale.arsenal;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import village.vale.*;
import village.valerack.*;

/** All six Claude hand styles cast from the server-switched combat inventory. */
public final class CombatAbilityNativeChecks {
    public static int run(EntityPlayerMP p){
        String[] keys={"spiral_palm","storm_lance","cinder_edge","gale_edge","molten_mantle","storm_stride"};
        String[] styles={"spiral","charge","fireedge","windedge","lava","lightmode"};
        RackData rack=RackData.of(p);NBTTagCompound savedRack=rack.serializeNBT(),savedState=PlayerState.data(p).copy();NBTTagList inv=p.inventory.writeToNBT(new NBTTagList());int selected=p.inventory.currentItem,checks=0;boolean sneaking=p.isSneaking();
        try{
            rack.detach();p.inventory.clear();p.inventory.setItemStack(ItemStack.EMPTY);p.openContainer=p.inventoryContainer;RackSessions.reset(p);p.setSneaking(false);
            PlayerState.data(p).setInteger("xp",1000000);PlayerState.data(p).removeTag("form");PlayerState.data(p).setTag("OriginalScrolls",new NBTTagCompound());
            for(int i=0;i<keys.length;i++){
                Scrolls.Lesson lesson=AbilityCarriers036.lesson(keys[i]);Scrolls.row(p,keys[i]).setBoolean("learned",true);Scrolls.row(p,keys[i]).setInteger("practice",60);
                if(!lesson.prerequisite.isEmpty())Scrolls.row(p,lesson.prerequisite).setBoolean("learned",true);
                RewardLedger.get(p).issue(p,"technique_"+keys[i],1,"native/combat-bar/"+keys[i]);
                for(int n=0;n<36;n++)if(p.inventory.mainInventory.get(n).getItem()==Gear.ITEMS.get("technique_"+keys[i])){rack.raw(i,p.inventory.mainInventory.get(n));p.inventory.mainInventory.set(n,ItemStack.EMPTY);break;}
                if(rack.getStackInSlot(i).isEmpty()||!RackPolicy.combatAccepts(p,rack.getStackInSlot(i)))throw new AssertionError("Combat technique admission failed: "+keys[i]);checks++;
            }
            if(!rack.combat.toggle(p,rack).isEmpty())throw new AssertionError("Combat technique bank could not activate");checks++;
            for(int i=0;i<keys.length;i++){
                p.getServerWorld().getWorldInfo().setWorldTotalTime(ArsenalAccess.now(p)+400);
                p.inventory.currentItem=i;PlayerState.setResolve(p,100);Scrolls.Lesson lesson=AbilityCarriers036.lesson(keys[i]);ItemStack held=p.getHeldItemMainhand();
                if(held.getItem().onItemRightClick(p.world,p,EnumHand.MAIN_HAND).getType()!=EnumActionResult.SUCCESS)throw new AssertionError("Native combat-slot cast failed: "+keys[i]);checks++;
                if(PlayerState.resolve(p)!=100-lesson.cost||!styles[i].equals(Scrolls.state(p).getString("mode")))throw new AssertionError("Combat-slot cost/mode mismatch: "+keys[i]);checks++;
                HandTechnique carrier=HandTechnique.find(p);if(carrier==null||!styles[i].equals(carrier.style()))throw new AssertionError("Combat-slot Claude effect absent: "+keys[i]);checks++;
                int paid=PlayerState.resolve(p);long version=Scrolls.state(p).getLong("modeVersion");
                if(held.getItem().onItemRightClick(p.world,p,EnumHand.MAIN_HAND).getType()!=EnumActionResult.FAIL||PlayerState.resolve(p)!=paid||Scrolls.state(p).getLong("modeVersion")!=version)throw new AssertionError("Combat-slot cooldown bypassed: "+keys[i]);checks++;
            }
            Scrolls.Lesson last=AbilityCarriers036.lesson(keys[5]);p.getServer().getWorld(0).getWorldInfo().setWorldTotalTime(ArsenalAccess.now(p)+last.cooldown+1);
            // Recast a still-active same mode after the item's cooldown is cleared.
            Scrolls.state(p).setLong("modeUntil",ArsenalAccess.now(p)+200);p.getCooldownTracker().setCooldown(p.getHeldItemMainhand().getItem(),0);PlayerState.setResolve(p,100);
            if(p.getHeldItemMainhand().getItem().onItemRightClick(p.world,p,EnumHand.MAIN_HAND).getType()!=EnumActionResult.SUCCESS||!Scrolls.state(p).getString("mode").isEmpty()||HandTechnique.find(p)!=null)throw new AssertionError("Same hand technique no longer toggles off");checks++;
            return checks;
        }finally{
            for(HandTechnique h:new java.util.ArrayList<>(p.world.getEntities(HandTechnique.class,h->h.ownerId()==p.getEntityId())))p.world.removeEntityDangerously(h);
            p.getEntityData().getCompoundTag(net.minecraft.entity.player.EntityPlayer.PERSISTED_NBT_TAG).setTag("ValeOrders",savedState);p.inventory.clear();p.inventory.readFromNBT(inv);p.inventory.currentItem=selected;p.inventory.setItemStack(ItemStack.EMPTY);p.setSneaking(sneaking);rack.deserializeNBT(savedRack);RackSessions.reset(p);
        }
    }
}
