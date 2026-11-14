package village.vale;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import village.vale.bridge.MoveCost;

/** One worn-focus reservation, settled only after actual effect admission. */
final class FocusPayment implements AutoCloseable {
    final EntityPlayerMP player;final NBTTagCompound state;final MoveCost.Ticket ticket;final long ready;boolean applied;
    private FocusPayment(EntityPlayerMP p,MoveCost.Ticket ticket,int cooldown){player=p;this.ticket=ticket;state=OriginalFocusGear.state(p);ready=Adventures.now(p)+cooldown;}
    static FocusPayment reserve(EntityPlayerMP p,int cost,int cooldown){if(Adventures.now(p)<OriginalFocusGear.state(p).getLong("ready"))return null;MoveCost.Ticket t=MoveCost.reserve(p,cost);return t==null?null:new FocusPayment(p,t,cooldown);}
    void accepted(){if(applied)return;applied=true;state.setLong("ready",Math.max(state.getLong("ready"),ready));if(ticket.pending())ticket.commit();}
    @Override public void close(){ticket.close();}
}
