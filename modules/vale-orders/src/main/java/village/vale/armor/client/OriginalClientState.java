package village.vale.armor.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import village.vale.OriginalArmorPacket;
import village.vale.armor.RigPose;

/** UUID-checked cosmetic state; no target acquisition or damage is performed on the client. */
@Mod.EventBusSubscriber(modid="valeorders",value=Side.CLIENT)
public final class OriginalClientState {
    private static final Map<UUID,Frame> FRAMES=new HashMap<>();private static World world;
    private OriginalClientState(){}
    public static void accept(OriginalArmorPacket packet){
        Minecraft mc=Minecraft.getMinecraft();if(mc.world==null||packet.owner==null)return;
        if(world!=mc.world){FRAMES.clear();world=mc.world;}
        Entity entity=mc.world.getEntityByID(packet.entityId);if(entity==null||!packet.owner.equals(entity.getUniqueID()))return;
        long now=mc.world.getTotalWorldTime();Frame frame=FRAMES.get(packet.owner);
        if(frame==null){frame=new Frame();frame.sequence=packet.sequence;frame.strikeAt=Long.MIN_VALUE;FRAMES.put(packet.owner,frame);}
        else if(frame.sequence!=packet.sequence){frame.sequence=packet.sequence;frame.strikeAt=now;}
        frame.until=now+40;frame.flying=packet.flying&&packet.profile!=null;
        entity.getEntityData().setInteger("valeArmorStamina",packet.stamina);entity.getEntityData().setBoolean("valeArmorFlying",frame.flying);
    }
    public static float strike(Entity entity,float partial){Frame f=FRAMES.get(entity.getUniqueID());if(f==null||entity.world!=world||entity.world.getTotalWorldTime()>f.until||f.strikeAt==Long.MIN_VALUE)return 0;double age=entity.world.getTotalWorldTime()-f.strikeAt+partial;return age>=0&&age<10?(float)Math.sin(Math.PI*age/10):0;}
    public static boolean flying(Entity entity){Frame f=FRAMES.get(entity.getUniqueID());return f!=null&&entity.world==world&&entity.world.getTotalWorldTime()<=f.until&&f.flying;}
    public static float fold(Entity entity,float partial,boolean target){
        if(world!=entity.world){FRAMES.clear();world=entity.world;}Frame f=FRAMES.get(entity.getUniqueID());
        if(f==null){f=new Frame();f.strikeAt=Long.MIN_VALUE;FRAMES.put(entity.getUniqueID(),f);}f.poseUntil=entity.world.getTotalWorldTime()+20;
        double time=entity.ticksExisted+partial,delta=f.poseInitialized?time-f.poseTime:0;f.poseInitialized=true;f.poseTime=time;
        f.fold=RigPose.approachWing(f.fold,target?1:0,delta);return f.fold;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;World current=Minecraft.getMinecraft().world;if(world!=current){FRAMES.clear();world=current;}if(current!=null)FRAMES.values().removeIf(f->current.getTotalWorldTime()>Math.max(f.until,f.poseUntil));}
    static final class Frame{int sequence;long strikeAt,until,poseUntil;boolean flying,poseInitialized;double poseTime;float fold;}
}
