package village.vale.arsenal;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.Cancelable;
import net.minecraftforge.fml.common.eventhandler.Event;

/** Protection hook for a nondamaging lock, before blindness, resolve or cooldown. */
@Cancelable
public final class ClawLockEvent extends Event {
    private final EntityPlayerMP owner;
    private final EntityLivingBase target;
    private final boolean rear;
    public ClawLockEvent(EntityPlayerMP owner,EntityLivingBase target,boolean rear) {
        this.owner=owner;this.target=target;this.rear=rear;
    }
    public EntityPlayerMP getOwner() {return owner;}
    public EntityLivingBase getTarget() {return target;}
    public boolean isRear() {return rear;}
}
