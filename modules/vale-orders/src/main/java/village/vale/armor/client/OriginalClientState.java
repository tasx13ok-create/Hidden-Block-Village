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
import village.vale.armor.ArmorPresentationState;
import village.vale.armor.RigPose;

/** UUID-checked cosmetic state; no target acquisition or damage is performed on the client. */
@Mod.EventBusSubscriber(modid="valeorders",value=Side.CLIENT)
public final class OriginalClientState {
    private static final Map<UUID,ArmorPresentationState> FRAMES=new HashMap<>();private static World world;
    private OriginalClientState(){}
    public static void accept(OriginalArmorPacket packet){
        Minecraft mc=Minecraft.getMinecraft();if(mc.world==null||packet.owner==null)return;
        if(world!=mc.world){FRAMES.clear();world=mc.world;}
        Entity entity=mc.world.getEntityByID(packet.entityId);if(entity==null||!packet.owner.equals(entity.getUniqueID()))return;
        long now=mc.world.getTotalWorldTime();ArmorPresentationState frame=FRAMES.computeIfAbsent(packet.owner,key->new ArmorPresentationState());
        frame.receive(now,packet.sequence,packet.flying&&packet.profile!=null);
        entity.getEntityData().setInteger("valeArmorStamina",packet.stamina);entity.getEntityData().setBoolean("valeArmorFlying",frame.flying(now));
    }
    public static float strike(Entity entity,float partial){ArmorPresentationState f=FRAMES.get(entity.getUniqueID());return f==null||entity.world!=world?0:f.strike(entity.world.getTotalWorldTime(),partial);}
    public static boolean flying(Entity entity){ArmorPresentationState f=FRAMES.get(entity.getUniqueID());return f!=null&&entity.world==world&&f.flying(entity.world.getTotalWorldTime());}
    public static float fold(Entity entity,float partial,boolean target){
        if(world!=entity.world){FRAMES.clear();world=entity.world;}
        ArmorPresentationState f=FRAMES.computeIfAbsent(entity.getUniqueID(),key->new ArmorPresentationState());
        return f.fold(entity.world.getTotalWorldTime(),entity.ticksExisted+(double)RigPose.unit(partial),target);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;World current=Minecraft.getMinecraft().world;if(world!=current){FRAMES.clear();world=current;}if(current!=null)FRAMES.values().removeIf(f->f.expired(current.getTotalWorldTime()));}
}
