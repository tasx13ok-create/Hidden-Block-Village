package village.valerack;

import java.util.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.EnumHand;
import village.vale.Gear;
import village.vale.RewardLedger;

/** Real inventory, capability, cursor and death checks in the disposable Forge fixture. */
public final class CombatHotbarNativeChecks {
    private int checks;
    private void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError("Combat hotbar: "+why);}
    private static NBTTagCompound item(ItemStack s){return s.writeToNBT(new NBTTagCompound());}
    private static void advance(EntityPlayerMP p){p.getServer().getWorld(0).getWorldInfo().setWorldTotalTime(RackSessions.now(p)+11);}
    private static String request(EntityPlayerMP p,int op,int slot,int button){advance(p);return RackSessions.request(p,op,slot,RackSessions.current(p).nonce,RackData.of(p).revision,p.inventoryContainer.windowId,button);}
    public static int run(EntityPlayerMP p){return new CombatHotbarNativeChecks().verify(p);}
    private int verify(EntityPlayerMP p){
        RackData rack=RackData.of(p);NBTTagCompound saved=rack.serializeNBT();NBTTagList inv=p.inventory.writeToNBT(new NBTTagList());int selected=p.inventory.currentItem;
        try{
            rack.detach();p.inventory.clear();p.inventory.setItemStack(ItemStack.EMPTY);p.openContainer=p.inventoryContainer;RackSessions.reset(p);
            check(RackPolicy.combatAccepts(p,new ItemStack(Gear.ITEMS.get("travel_ration")))&&RackPolicy.combatAccepts(p,village.vale.culinary.Culinary.stack("resolve_pill_1")),"crafted combat consumables require nonexistent reward receipts");
            check(RackPolicy.combatAccepts(p,new ItemStack(Gear.ITEMS.get("outfit_bracken_coat"))),"freely traded garment requires a nonexistent reward receipt");
            List<NBTTagCompound> normal=new ArrayList<>(),combat=new ArrayList<>();
            for(int i=0;i<9;i++){ItemStack s=new ItemStack(Items.APPLE,i+1);s.setStackDisplayName("Normal "+i);p.inventory.mainInventory.set(i,s);normal.add(item(s));}
            for(int i=0;i<6;i++){ItemStack s=new ItemStack(Items.IRON_SWORD);s.setItemDamage(10+i);s.setStackDisplayName("Combat "+i);rack.raw(i,s);combat.add(item(s));}
            p.inventory.currentItem=8;
            check(request(p,2,255,0).isEmpty()&&rack.combat.active,"whole combat hotbar activation failed");
            for(int i=0;i<6;i++)check(item(p.inventory.mainInventory.get(i)).equals(combat.get(i))&&rack.getStackInSlot(i).isEmpty(),"combat item lost or duplicated "+i);
            for(int i=6;i<9;i++)check(p.inventory.mainInventory.get(i).isEmpty(),"normal-only cell remains active "+i);
            for(int i=0;i<9;i++)check(item(rack.combat.parked(i)).equals(normal.get(i)),"parked normal stack changed "+i);
            check(p.inventory.currentItem==0,"invalid initial combat selection");
            NBTTagCompound active=rack.serializeNBT();RackData reload=new RackData(p);reload.deserializeNBT(active);
            check(!reload.locked&&reload.combat.active&&active.equals(reload.serializeNBT()),"active save round trip lost normal backup");
            rack.deserializeNBT(active); // Continue from restored capability plus vanilla-owned active inventory.
            p.inventory.currentItem=4;p.inventory.mainInventory.get(2).setItemDamage(33);combat.set(2,item(p.inventory.mainInventory.get(2)));
            check(request(p,2,255,0).isEmpty()&&!rack.combat.active&&p.inventory.currentItem==8,"normal hotbar or selected cell not restored");
            for(int i=0;i<9;i++)check(item(p.inventory.mainInventory.get(i)).equals(normal.get(i))&&rack.combat.parked(i).isEmpty(),"normal restoration lost or duplicated stack "+i);
            for(int i=0;i<6;i++)check(item(rack.getStackInSlot(i)).equals(combat.get(i)),"changed combat stack not retained "+i);
            check(request(p,2,255,0).isEmpty()&&p.inventory.currentItem==4,"combat selection not remembered");
            p.inventory.setItemStack(new ItemStack(Items.STICK));NBTTagCompound before=rack.serializeNBT();
            check(!request(p,2,255,0).isEmpty()&&before.equals(rack.serializeNBT()),"switch with occupied cursor mutated loadout");p.inventory.setItemStack(ItemStack.EMPTY);
            check(request(p,2,255,0).isEmpty(),"return from combat failed");
            for(int i=9;i<36;i++)p.inventory.mainInventory.set(i,new ItemStack(Items.STICK));
            check(request(p,2,255,0).isEmpty(),"full main inventory prevented normal bank switching");
            p.inventory.mainInventory.set(6,new ItemStack(Items.STICK,3));
            NBTTagCompound blockedRack=rack.serializeNBT();NBTTagList blockedInv=p.inventory.writeToNBT(new NBTTagList());
            check(!request(p,2,255,0).isEmpty()&&blockedRack.equals(rack.serializeNBT())&&blockedInv.equals(p.inventory.writeToNBT(new NBTTagList())),"full return partially changed or lost an injected item");
            p.inventory.mainInventory.set(15,ItemStack.EMPTY);
            check(request(p,2,255,0).isEmpty()&&p.inventory.mainInventory.get(15).getCount()==3&&item(p.inventory.mainInventory.get(6)).equals(normal.get(6)),"injected item displaced the saved normal hotbar");
            for(int i=9;i<36;i++)p.inventory.mainInventory.set(i,ItemStack.EMPTY);
            rack.raw(0,ItemStack.EMPTY);p.inventory.setItemStack(new ItemStack(Items.ARROW,12));
            check(request(p,3,0,0).isEmpty()&&p.inventory.getItemStack().isEmpty()&&rack.getStackInSlot(0).getCount()==12,"cursor drag deposit failed");
            check(request(p,3,0,1).isEmpty()&&p.inventory.getItemStack().getCount()==6&&rack.getStackInSlot(0).getCount()==6,"right click split failed");
            check(request(p,3,0,0).isEmpty()&&p.inventory.getItemStack().isEmpty()&&rack.getStackInSlot(0).getCount()==12,"cursor merge failed");
            advance(p);long staleRevision=rack.revision;
            check(RackSessions.request(p,3,0,RackSessions.current(p).nonce,staleRevision,p.inventoryContainer.windowId,1).isEmpty(),"valid cursor request failed");
            advance(p);NBTTagCompound afterPickup=rack.serializeNBT();NBTTagCompound cursorBefore=item(p.inventory.getItemStack());
            check(!RackSessions.request(p,3,0,RackSessions.current(p).nonce,staleRevision,p.inventoryContainer.windowId,1).isEmpty()&&afterPickup.equals(rack.serializeNBT())&&cursorBefore.equals(item(p.inventory.getItemStack())),"replayed revision duplicated cursor contents");
            check(request(p,3,0,0).isEmpty(),"cursor cleanup failed");
            advance(p);check(!RackSessions.request(p,3,0,RackSessions.current(p).nonce+1,rack.revision,p.inventoryContainer.windowId,0).isEmpty()&&p.inventory.getItemStack().isEmpty(),"foreign nonce picked up a stack");
            advance(p);long nonce=RackSessions.current(p).nonce,revision=rack.revision;
            check(!RackSessions.request(p,3,0,nonce,revision,p.inventoryContainer.windowId+1,0).isEmpty()&&rack.revision==revision,"foreign window cursor request accepted");
            p.inventory.setItemStack(new ItemStack(Gear.ITEMS.get("technique_spiral_palm")));
            check(!request(p,3,1,0).isEmpty()&&p.inventory.getItemStack().getItem()==Gear.ITEMS.get("technique_spiral_palm"),"unissued ability was admitted or destroyed");p.inventory.setItemStack(ItemStack.EMPTY);
            p.inventory.mainInventory.set(0,new ItemStack(Items.BOW));p.inventory.currentItem=0;p.setActiveHand(EnumHand.MAIN_HAND);
            check(request(p,2,255,0).isEmpty()&&!p.isHandActive(),"switch failed to cancel native held charge");
            NBTTagCompound cloneBank=rack.serializeNBT();
            for(boolean death:new boolean[]{false,true}){
                EntityPlayerMP clone=new EntityPlayerMP(p.getServer(),p.getServerWorld(),p.getGameProfile(),new net.minecraft.server.management.PlayerInteractionManager(p.getServerWorld()));
                clone.inventory.copyInventory(p.inventory);rack.deathSeen=true;rack.deathKeep=true;
                new RackLifecycle().clone(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(clone,p,death));
                RackData copied=RackData.of(clone);
                check(cloneBank.equals(copied.serializeNBT())&&!rack.combat.active&&rack.isEmpty(),"clone failed to transfer bank ownership, death="+death);
                check(copied.combat.toggle(clone,copied).isEmpty()&&clone.inventory.mainInventory.get(0).getItem()==Items.BOW&&copied.getStackInSlot(0).getCount()==12,"clone could not restore parked normal loadout, death="+death);
                clone.inventory.clear();copied.detach();rack.deserializeNBT(cloneBank);RackSessions.reset(p);
            }
            List<EntityItem> drops=new ArrayList<>();rack.deathHandled=false;RackLifecycle.consumeDeath(p,drops);RackLifecycle.consumeDeath(p,drops);
            check(drops.size()==9&&!rack.combat.active&&rack.isEmpty(),"death duplicated or lost the parked nine stacks");
            check(p.inventory.mainInventory.get(0).getCount()==12,"rack death stole active inventory drops from vanilla");
            RackData old=new RackData(p);NBTTagCompound legacy=new NBTTagCompound();legacy.setInteger("Version",1);legacy.setLong("Revision",5);legacy.setTag("Items",new NBTTagList());old.deserializeNBT(legacy);
            check(!old.locked&&!old.combat.active&&old.serializeNBT().getInteger("Version")==2,"schema 1 migration failed");
            NBTTagCompound future=legacy.copy();future.setInteger("Version",99);old.deserializeNBT(future);check(old.locked&&future.equals(old.serializeNBT()),"future schema was rewritten");
            // An acknowledged rejection must never authorize a deferred vanilla cursor click.
            RackSessions.Session session=RackSessions.reset(p);p.inventory.setItemStack(new ItemStack(Items.STICK,3));advance(p);
            RackNetwork.Request rejected=new RackNetwork.Request(3,5,session.nonce,rack.revision-1);rejected.window=p.inventoryContainer.windowId;rejected.sequence=41;
            NBTTagCompound beforeAck=rack.serializeNBT();RackSessions.handle(p,rejected);
            check(session.acknowledged==41&&!session.accepted&&beforeAck.equals(rack.serializeNBT())&&p.inventory.getItemStack().getCount()==3,"stale rejection produced a successful cursor receipt");
            io.netty.buffer.ByteBuf ackWire=io.netty.buffer.Unpooled.buffer();
            try{new RackNetwork.Snapshot(p,session,rack).toBytes(ackWire);RackNetwork.Snapshot read=new RackNetwork.Snapshot();read.fromBytes(ackWire);check(read.valid&&read.acknowledged==41&&!read.accepted,"rejection result did not survive actual snapshot wire");}finally{ackWire.release();}
            advance(p);RackNetwork.Request accepted=new RackNetwork.Request(3,5,session.nonce,rack.revision);accepted.window=p.inventoryContainer.windowId;accepted.sequence=42;RackSessions.handle(p,accepted);
            check(session.acknowledged==42&&session.accepted&&p.inventory.getItemStack().isEmpty(),"successful cursor transaction lacks its exact positive receipt");
            ackWire=io.netty.buffer.Unpooled.buffer();
            try{new RackNetwork.Snapshot(p,session,rack).toBytes(ackWire);RackNetwork.Snapshot read=new RackNetwork.Snapshot();read.fromBytes(ackWire);check(read.valid&&read.acknowledged==42&&read.accepted,"success result did not survive actual snapshot wire");}finally{ackWire.release();}
            return checks;
        }finally{p.resetActiveHand();p.inventory.clear();p.inventory.readFromNBT(inv);p.inventory.setItemStack(ItemStack.EMPTY);p.inventory.currentItem=selected;rack.deserializeNBT(saved);RackSessions.reset(p);}
    }
}
