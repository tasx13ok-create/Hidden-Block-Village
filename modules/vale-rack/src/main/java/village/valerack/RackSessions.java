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
        Session(EntityPlayerMP p){owner=p;world=p.world;dimension=p.dimension;}
    }
    public static Session reset(EntityPlayerMP p){Session s=new Session(p);SESSIONS.put(p.getUniqueID(),s);return s;}
    public static boolean context(EntityPlayerMP p){return p!=null&&p.getServer()!=null&&p.getServer().isCallingFromMinecraftThread()&&!p.world.isRemote;}
    public static Session current(EntityPlayerMP p){Session s=SESSIONS.get(p.getUniqueID());return s!=null&&s.owner==p&&s.world==p.world&&s.dimension==p.dimension?s:null;}
    public static long now(EntityPlayerMP p){return p.getServer().getWorld(0).getTotalWorldTime();}
    public static void sync(EntityPlayerMP p){Session s=current(p);if(s==null)return;RackData r=RackData.of(p);RackNetwork.CHANNEL.sendTo(new RackNetwork.Snapshot(p,s,r),p);r.dirty=false;s.nextSync=now(p)+20;}
    public static String request(EntityPlayerMP p,int operation,int index,long nonce,long revision){
        if(!context(p))return "Rack requests need the server thread.";
        Session s=current(p);if(s==null||s.nonce!=nonce)return "Rack session changed; wait for synchronization.";
        long now=now(p);if(now<s.nextRequest)return "Rack request is cooling down.";s.nextRequest=now+(operation==1?10:2);
        if(!p.isEntityAlive()||p.isDead||p.isSpectator())return "Rack is available while alive in play.";
        if(p.openContainer!=p.inventoryContainer&&!(p.openContainer instanceof RackContainer&&p.openContainer.canInteractWith(p)))return "Close the other container first.";
        if(!p.inventory.getItemStack().isEmpty())return "Put down the cursor item first.";
        if(operation==1){p.openGui(RackMod.INSTANCE,0,p.world,0,0,0);return p.openContainer instanceof RackContainer?"":"Rack could not open.";}
        if(operation!=0||index<0||index>=6)return "Unknown rack slot.";
        RackData rack=RackData.of(p);if(rack.locked)return "Rack save needs a newer module.";
        if(revision!=rack.revision)return "Rack changed; try again.";
        ItemStack stored=rack.getStackInSlot(index);if(stored.isEmpty())return "That rack slot is empty.";
        if(!RackPolicy.accepts(p,stored))return "That stored weapon cannot be equipped.";
        int hand=p.inventory.currentItem;if(hand<0||hand>=9)return "Native hand selection is invalid.";
        ItemStack held=p.inventory.mainInventory.get(hand);int free=-1;
        if(!held.isEmpty()){
            if(RackPolicy.type(held)){if(!RackPolicy.accepts(p,held))return "The held weapon cannot enter this rack.";}
            else{for(int i=0;i<36;i++)if(i!=hand&&p.inventory.mainInventory.get(i).isEmpty()){free=i;break;}if(free<0)return "Free an inventory slot for the held item.";}
        }
        // Validate everything first, then publish the two/three assignments in one server task.
        ItemStack nextHand=stored.copy(),nextRack=held.isEmpty()||free>=0?ItemStack.EMPTY:held.copy();
        if(free>=0)p.inventory.mainInventory.set(free,held.copy());
        p.inventory.mainInventory.set(hand,nextHand);rack.raw(index,nextRack);rack.markDirty();p.inventory.markDirty();p.openContainer.detectAndSendChanges();return "";
    }
    public static void handle(EntityPlayerMP p,RackNetwork.Request request){String reason=request(p,request.operation,request.index,request.nonce,request.revision);Session s=current(p);long now=now(p);if(!reason.isEmpty()&&s!=null&&now>=s.nextFeedback){p.sendStatusMessage(new TextComponentString(reason),true);s.nextFeedback=now+10;}if(s!=null&&(reason.isEmpty()||RackData.of(p).dirty||now>=s.nextSync))sync(p);}
}
