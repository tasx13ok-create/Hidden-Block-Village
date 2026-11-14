package village.vale.arsenal;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import village.vale.PlayerState;
import village.vale.bridge.MoveCost;

/** Native admission settles resource before recovery/status/cosmetic callbacks. Never replays a cast. */
final class ArsenalCharge implements AutoCloseable {
    private static final Map<UUID,ArsenalCharge> ACTIVE=new HashMap<>();
    final EntityPlayerMP player;final ItemStack stack;final ArsenalSpec spec;
    final World world;final Object account;final NBTTagCompound state;final MoveCost.Ticket ticket;
    boolean applied,completed,closed;
    private ArsenalCharge(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec,MoveCost.Ticket ticket){player=p;this.stack=stack;this.spec=spec;this.ticket=ticket;world=p.world;account=PlayerState.data(p);state=ArsenalLogic.state(p);state.setBoolean("busy",true);}
    static ArsenalCharge begin(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec){
        if(ACTIVE.containsKey(p.getUniqueID())||ACTIVE.size()>=128)return null;
        MoveCost.Ticket ticket=MoveCost.reserve(p,spec.cost);if(ticket==null)return null;
        ArsenalCharge charge=new ArsenalCharge(p,stack,spec,ticket);ACTIVE.put(p.getUniqueID(),charge);return charge;
    }
    static void accepted(EntityPlayerMP p){ArsenalCharge charge=ACTIVE.get(p.getUniqueID());if(charge!=null&&charge.player==p)charge.accept();}
    static boolean context(EntityPlayerMP p){ArsenalCharge c=ACTIVE.get(p.getUniqueID());return c==null||c.player==p&&p.world==c.world&&PlayerState.data(p)==c.account&&p.getHeldItemMainhand()==c.stack&&(c.applied||c.ticket.valid());}
    void accept(){applied=true;if(ticket.pending())ticket.commit();}
    void complete(){if(applied&&!completed){completed=true;ArsenalLogic.commit(player,stack,spec);}}
    @Override public void close(){if(closed)return;closed=true;try{complete();}finally{try{ticket.close();}finally{state.removeTag("busy");ACTIVE.remove(player.getUniqueID(),this);}}}
}
