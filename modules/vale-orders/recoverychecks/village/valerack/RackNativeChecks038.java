package village.valerack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import village.vale.Gear;
import village.vale.RewardLedger;

/** Runs only from the guarded, disposable recovery mod with the real Rack loaded. */
public final class RackNativeChecks038 {
    private int checks;
    private void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    public static int run(EntityPlayerMP p){return new RackNativeChecks038().verify(p);}
    private static NBTTagCompound item(ItemStack stack){return stack.writeToNBT(new NBTTagCompound());}
    private static void advance(EntityPlayerMP p){p.getServer().getWorld(0).getWorldInfo().setWorldTotalTime(RackSessions.now(p)+3);}
    private static String request(EntityPlayerMP p,int slot){
        advance(p);RackSessions.Session session=RackSessions.current(p);
        return RackSessions.request(p,0,slot,session.nonce,RackData.of(p).revision);
    }
    private static void empty(EntityPlayerMP p,RackData rack){
        p.resetActiveHand();p.inventory.clear();p.inventory.setItemStack(ItemStack.EMPTY);
        p.inventory.currentItem=0;p.openContainer=p.inventoryContainer;rack.clear();RackSessions.reset(p);
        // Earlier fixture phases intentionally fire a death clone on this same
        // synthetic player. Let the normal alive tick reset death bookkeeping
        // before starting another independent rack interaction/death scenario.
        new RackLifecycle().tick(new net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent(
            net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,p));
    }
    private int verify(EntityPlayerMP p){
        RackData rack=RackData.of(p);empty(p,rack);
        try {
            for(int i=0;i<36;i++)p.inventory.mainInventory.set(i,new ItemStack(Blocks.COBBLESTONE,64));
            for(int slot=0;slot<6;slot++){
                ItemStack held=new ItemStack(Items.IRON_SWORD);held.setItemDamage(slot+3);
                held.setStackDisplayName("Fixture blade "+slot);held.addEnchantment(Enchantments.UNBREAKING,2);
                NBTTagCompound expected=item(held);p.inventory.mainInventory.set(0,held);
                long revision=rack.revision;
                check(request(p,slot).isEmpty(),"Full-inventory quick stow refused rack cell "+slot);
                check(p.getHeldItemMainhand().isEmpty()&&expected.equals(item(rack.getStackInSlot(slot))),"Quick stow changed or duplicated the physical stack");
                check(rack.revision==revision+1,"Quick stow must publish exactly one rack revision");
            }
            for(int i=1;i<36;i++)check(p.inventory.mainInventory.get(i).getCount()==64,"Quick stow touched an unrelated full inventory cell");
            NBTTagCompound six=rack.serializeNBT();RackData restored=new RackData(p);restored.deserializeNBT(six);
            check(six.equals(restored.serializeNBT()),"Six physical rack stacks lost NBT through save/reload");

            ItemStack bow=new ItemStack(Items.BOW);bow.setItemDamage(13);bow.addEnchantment(Enchantments.POWER,3);
            NBTTagCompound bowBefore=item(bow),bladeBefore=item(rack.getStackInSlot(0));p.inventory.mainInventory.set(0,bow);
            p.setActiveHand(EnumHand.MAIN_HAND);check(p.isHandActive(),"Native bow charge fixture did not start");
            long oldRevision=rack.revision;long nonce=RackSessions.current(p).nonce;
            check(request(p,0).isEmpty(),"Full-inventory weapon swap failed");
            check(!p.isHandActive(),"Rack swap left the removed main-hand charge active");
            check(bladeBefore.equals(item(p.getHeldItemMainhand()))&&bowBefore.equals(item(rack.getStackInSlot(0))),"Full-inventory swap lost damage, enchantment or stack identity data");
            NBTTagCompound rackAfter=rack.serializeNBT(),handAfter=item(p.getHeldItemMainhand());
            advance(p);
            check(!RackSessions.request(p,0,0,nonce,oldRevision).isEmpty(),"Replayed old rack revision was accepted");
            check(rackAfter.equals(rack.serializeNBT())&&handAfter.equals(item(p.getHeldItemMainhand())),"Rejected revision replay mutated items");
            advance(p);
            check(!RackSessions.request(p,0,0,nonce+1,rack.revision).isEmpty(),"Foreign rack session nonce was accepted");

            p.inventory.mainInventory.set(0,new ItemStack(Items.APPLE,64));
            NBTTagCompound apples=item(p.getHeldItemMainhand());rackAfter=rack.serializeNBT();
            check(!request(p,0).isEmpty(),"Full-inventory nonweapon was overwritten by quick equip");
            check(apples.equals(item(p.getHeldItemMainhand()))&&rackAfter.equals(rack.serializeNBT()),"Rejected full-inventory equip lost an item");
            p.inventory.mainInventory.set(17,ItemStack.EMPTY);
            check(request(p,0).isEmpty(),"Quick equip could not relocate held food into a free main inventory cell");
            check(apples.equals(item(p.inventory.mainInventory.get(17)))&&bowBefore.equals(item(p.getHeldItemMainhand()))&&rack.getStackInSlot(0).isEmpty(),"Quick equip relocation lost or duplicated a stack");
            p.setActiveHand(EnumHand.MAIN_HAND);
            check(request(p,0).isEmpty()&&!p.isHandActive()&&p.getHeldItemMainhand().isEmpty(),"Quick stow did not cancel the removed native bow charge");

            p.inventory.mainInventory.set(0,new ItemStack(Items.DIAMOND_SWORD));
            p.inventory.offHandInventory.set(0,new ItemStack(Items.SHIELD));p.setActiveHand(EnumHand.OFF_HAND);
            check(p.isHandActive()&&request(p,0).isEmpty()&&p.isHandActive()&&p.getActiveHand()==EnumHand.OFF_HAND,"Main-hand swap interrupted unrelated offhand shield use");
            p.resetActiveHand();
            p.inventory.setItemStack(new ItemStack(Items.STICK));rackAfter=rack.serializeNBT();handAfter=item(p.getHeldItemMainhand());
            check(!request(p,0).isEmpty()&&rackAfter.equals(rack.serializeNBT())&&handAfter.equals(item(p.getHeldItemMainhand())),"Rack mutated while a cursor item was held");
            p.inventory.setItemStack(ItemStack.EMPTY);

            empty(p,rack);
            RewardLedger ledger=RewardLedger.get(p);ledger.issue(p,"crescent_talons",1,"native038/rack/talons");
            ItemStack talons=ItemStack.EMPTY;
            for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==Gear.ITEMS.get("crescent_talons"))talons=stack;
            check(!talons.isEmpty()&&ledger.valid(p,talons),"Issued claw fixture was not delivered");
            for(int i=0;i<36;i++)if(p.inventory.mainInventory.get(i)==talons)p.inventory.mainInventory.set(i,ItemStack.EMPTY);
            p.inventory.mainInventory.set(0,talons);NBTTagCompound talonsBefore=item(talons);
            check(request(p,5).isEmpty()&&ledger.valid(p,rack.getStackInSlot(5)),"New issued claw could not enter the sixth rack cell");
            check(request(p,5).isEmpty()&&talonsBefore.equals(item(p.getHeldItemMainhand()))&&rack.getStackInSlot(5).isEmpty(),"New claw quick equip changed its ownership serial");
            ItemStack unissued=new ItemStack(Gear.ITEMS.get("crescent_talons"));p.inventory.mainInventory.set(0,unissued);
            check(!request(p,5).isEmpty()&&rack.getStackInSlot(5).isEmpty()&&p.getHeldItemMainhand()==unissued,"Unissued claw entered an owned rack cell");

            empty(p,rack);
            ItemStack normal=new ItemStack(Items.IRON_SWORD),vanishing=new ItemStack(Items.BOW);vanishing.addEnchantment(Enchantments.VANISHING_CURSE,1);
            check(EnchantmentHelper.hasVanishingCurse(vanishing),"Native vanishing fixture lacks its enchantment");
            rack.raw(0,normal);rack.raw(1,vanishing);List<EntityItem> drops=new ArrayList<>();
            RackLifecycle.consumeDeath(p,drops);RackLifecycle.consumeDeath(p,drops);
            check(drops.size()==1&&item(normal).equals(item(drops.get(0).getItem()))&&rack.isEmpty(),"Rack death transaction duplicated a drop or retained a vanished weapon");

            ByteBuf wire=Unpooled.buffer();
            try{
                new RackNetwork.Request(0,5,1234,7).toBytes(wire);check(wire.readableBytes()==18,"Rack wire request changed length");
                RackNetwork.Request decoded=new RackNetwork.Request();decoded.fromBytes(wire);
                check(decoded.valid&&decoded.index==5&&decoded.nonce==1234&&decoded.revision==7,"Sixth rack cell request failed wire round-trip");
                wire.clear();wire.writeZero(19);decoded=new RackNetwork.Request();decoded.fromBytes(wire);check(!decoded.valid,"Rack accepted trailing request bytes");
                wire.clear();wire.writeZero(17);decoded=new RackNetwork.Request();decoded.fromBytes(wire);check(!decoded.valid,"Rack accepted a truncated request");
            }finally{wire.release();}
            return checks;
        }finally{empty(p,rack);}
    }
}
