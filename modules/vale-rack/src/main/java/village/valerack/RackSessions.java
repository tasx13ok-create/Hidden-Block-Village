package village.valerack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.entity.player.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;

public final class RackSessions {
    public static final Map<UUID,Session> SESSIONS=new ConcurrentHashMap<>();
    private RackSessions(){}
    public static final class Session{
        public final EntityPlayerMP owner;public final net.minecraft.world.World world;public final int dimension;public final long nonce=UUID.randomUUID().getLeastSignificantBits();
        public final AtomicBoolean pending=new AtomicBoolean();public long nextRequest,nextSync,nextFeedback;
        public ItemStack[] observedCombat=new ItemStack[6];
        public int acknowledged;
        Session(EntityPlayerMP p){owner=p;world=p.world;dimension=p.dimension;}
    }
    public static Session reset(EntityPlayerMP p){Session s=new Session(p);SESSIONS.put(p.getUniqueID(),s);return s;}
    public static boolean context(EntityPlayerMP p){return p!=null&&p.getServer()!=null&&p.getServer().isCallingFromMinecraftThread()&&!p.world.isRemote;}
    public static Session current(EntityPlayerMP p){Session s=SESSIONS.get(p.getUniqueID());return s!=null&&s.owner==p&&s.world==p.world&&s.dimension==p.dimension?s:null;}
    public static long now(EntityPlayerMP p){return p.getServer().getWorld(0).getTotalWorldTime();}
    public static void sync(EntityPlayerMP p){Session s=current(p);if(s==null)return;RackData r=RackData.of(p);RackNetwork.CHANNEL.sendTo(new RackNetwork.Snapshot(p,s,r),p);r.dirty=false;s.nextSync=now(p)+20;}
    public static String request(EntityPlayerMP p,int operation,int index,long nonce,long revision){return request(p,operation,index,nonce,revision,-1,0);}
    public static String request(EntityPlayerMP p,int operation,int index,long nonce,long revision,int window,int button){
        if(!context(p))return "Rack requests need the server thread.";
        Session s=current(p);if(s==null||s.nonce!=nonce)return "Rack session changed; wait for synchronization.";
        long now=now(p);if(now<s.nextRequest)return "Rack request is cooling down.";s.nextRequest=now+(operation==1?10:2);
        if(!p.isEntityAlive()||p.isDead||p.isSpectator())return "Rack is available while alive in play.";
        if(p.openContainer!=p.inventoryContainer&&!(p.openContainer instanceof RackContainer&&p.openContainer.canInteractWith(p)))return "Close the other container first.";
        RackData rack=RackData.of(p);if(rack.locked)return "Rack save needs a newer module.";
        if(revision!=rack.revision)return "Rack changed; try again.";
        if(operation==3){
            if(p.openContainer!=p.inventoryContainer||window!=p.openContainer.windowId||index<0||index>=6||(button!=0&&button!=1))return "Inventory screen changed; try again.";
            return cursor(p,rack,index,button);
        }
        if(!p.inventory.getItemStack().isEmpty())return "Put down the cursor item first.";
        if(operation==2){if(p.openContainer!=p.inventoryContainer)return "Close the storage screen first.";return rack.combat.toggle(p,rack);}
        if(rack.combat.active)return "Switch to your normal hotbar to manage storage here.";
        if(operation==1){p.openGui(RackMod.INSTANCE,0,p.world,0,0,0);return p.openContainer instanceof RackContainer?"":"Rack could not open.";}
        if(operation!=0||index<0||index>=6)return "Unknown rack slot.";
        int hand=p.inventory.currentItem;if(hand<0||hand>=9)return "Native hand selection is invalid.";
        ItemStack stored=rack.getStackInSlot(index),held=p.inventory.mainInventory.get(hand);
        if(stored.isEmpty()){
            if(held.isEmpty())return "Hold a weapon to store in this empty rack slot.";
            if(!RackPolicy.accepts(p,held))return "Only an owned weapon can enter this rack slot.";
            // Stow the physical stack, including all damage, enchantment and issued
            // ownership data. A full inventory does not need a spare cell.
            ItemStack nextRack=held.copy();
            if(p.isHandActive()&&p.getActiveHand()==net.minecraft.util.EnumHand.MAIN_HAND)p.resetActiveHand();
            p.inventory.mainInventory.set(hand,ItemStack.EMPTY);rack.raw(index,nextRack);
            rack.markDirty();p.inventory.markDirty();p.openContainer.detectAndSendChanges();return "";
        }
        if(!RackPolicy.accepts(p,stored))return "That stored weapon cannot be equipped.";
        int free=-1;
        if(!held.isEmpty()){
            if(RackPolicy.type(held)){if(!RackPolicy.accepts(p,held))return "The held weapon cannot enter this rack.";}
            else{for(int i=0;i<36;i++)if(i!=hand&&p.inventory.mainInventory.get(i).isEmpty()){free=i;break;}if(free<0)return "Free an inventory slot for the held item.";}
        }
        // Validate everything first, then publish the two/three assignments in one server task.
        ItemStack nextHand=stored.copy(),nextRack=held.isEmpty()||free>=0?ItemStack.EMPTY:held.copy();
        // Replacing a charging stack cancels that use without releasing it. It
        // must never fire a stale charged attack after leaving the active hand.
        if(p.isHandActive()&&p.getActiveHand()==net.minecraft.util.EnumHand.MAIN_HAND)p.resetActiveHand();
        if(free>=0)p.inventory.mainInventory.set(free,held.copy());
        p.inventory.mainInventory.set(hand,nextHand);rack.raw(index,nextRack);rack.markDirty();p.inventory.markDirty();p.openContainer.detectAndSendChanges();return "";
    }
    private static String cursor(EntityPlayerMP p,RackData rack,int index,int button){
        ItemStack held=p.inventory.getItemStack(),stored=rack.combat.combat(p,rack,index);
        if(!held.isEmpty()&&!RackPolicy.combatAccepts(p,held))return "This item is unavailable or belongs to another player.";
        if(held.isEmpty()&&stored.isEmpty())return "";
        ItemStack nextHeld=held.copy(),nextStored=stored.copy();
        if(held.isEmpty()){int amount=button==1?(stored.getCount()+1)/2:stored.getCount();nextHeld=nextStored.splitStack(amount);}
        else if(stored.isEmpty()){nextStored=nextHeld.splitStack(button==1?1:held.getCount());}
        else if(ItemStack.areItemsEqual(held,stored)&&ItemStack.areItemStackTagsEqual(held,stored)){
            int amount=Math.min(button==1?1:held.getCount(),Math.min(64,stored.getMaxStackSize())-stored.getCount());
            if(amount<=0)return "That combat slot is full.";nextStored.grow(amount);nextHeld.shrink(amount);
        }else if(button==0){nextStored=held.copy();nextHeld=stored.copy();}
        else return "Use left click to exchange different items.";
        rack.combat.setCombat(p,rack,index,nextStored.isEmpty()?ItemStack.EMPTY:nextStored);
        p.inventory.setItemStack(nextHeld.isEmpty()?ItemStack.EMPTY:nextHeld);rack.markDirty();p.inventory.markDirty();return "";
    }
    public static void handle(EntityPlayerMP p,RackNetwork.Request request){
        String reason=request(p,request.operation,request.index,request.nonce,request.revision,request.window,request.button);Session s=current(p);long now=now(p);
        if(s!=null&&s.nonce==request.nonce)s.acknowledged=request.sequence;
        if(!reason.isEmpty()&&s!=null&&now>=s.nextFeedback){p.sendStatusMessage(new TextComponentString(reason),true);s.nextFeedback=now+10;}
        if(reason.isEmpty()&&request.operation==2)p.sendStatusMessage(new TextComponentString(RackData.of(p).combat.active?"Combat hotbar ready - slots 1-6":"Normal hotbar restored"),true);
        if(request.operation==2||request.operation==3){
            p.sendContainerToPlayer(p.openContainer);
            p.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(p.inventory.currentItem));
        }
        if(s!=null&&(request.operation>=2||reason.isEmpty()||RackData.of(p).dirty||now>=s.nextSync))sync(p);
    }
}
