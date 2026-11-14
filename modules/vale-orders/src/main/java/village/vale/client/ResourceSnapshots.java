package village.vale.client;

import net.minecraft.client.Minecraft;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.*;
import village.vale.bridge.ResourceNetwork;
import village.vale.bridge.ResourceSnapshot;

/** Context-bound client cache for optional HUD consumers; never a spending authority. */
@SideOnly(Side.CLIENT)
public final class ResourceSnapshots {
    private static final AtomicLong DISCONNECT_EPOCH=new AtomicLong();
    private static volatile WeakReference<Object> disconnected=new WeakReference<>(null);
    private static EntityPlayerSP player;private static World world;private static Object connection;
    private static ResourceSnapshot value;private static long received,ticks;private static boolean initialized;
    private static void context(){Minecraft mc=Minecraft.getMinecraft();if(mc.player!=player||mc.world!=world||mc.getConnection()!=connection){player=mc.player;world=mc.world;connection=mc.getConnection();value=null;received=0;}}
    public static void initialize(){if(initialized)return;initialized=true;MinecraftForge.EVENT_BUS.register(new ResourceSnapshots());ResourceNetwork.client(ResourceSnapshots::receive);}
    private static void receive(ResourceSnapshot next,Object expected){
        if(next==null||expected==null||expected==disconnected.get())return;
        final long epoch=DISCONNECT_EPOCH.get();
        Minecraft mc=Minecraft.getMinecraft();final Object expectedPlayer=mc.player,expectedWorld=mc.world;
        mc.addScheduledTask(()->{if(epoch!=DISCONNECT_EPOCH.get()||expected==disconnected.get())return;context();if(connection==null||expected!=connection||player==null||world==null||player!=expectedPlayer||world!=expectedWorld||!player.getUniqueID().equals(next.owner)||player.getEntityId()!=next.entityId||player.dimension!=next.dimension)return;if(value!=null&&next.serverTick<value.serverTick)return;value=next;received=ticks;});
    }
    public static ResourceSnapshot getLocal(){context();return value!=null&&player!=null&&world!=null&&connection!=null&&connection!=disconnected.get()&&player.isEntityAlive()&&!player.isSpectator()&&player.getUniqueID().equals(value.owner)&&player.getEntityId()==value.entityId&&player.dimension==value.dimension&&ticks>=received&&ticks-received<=100?value:null;}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.START){ticks++;context();}}
    @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event){
        Object old=event==null?null:event.getHandler();if(old==null)old=connection!=null?connection:Minecraft.getMinecraft().getConnection();
        // Weak identity avoids retaining a whole disconnected world. A queued or
        // late packet keeps its old handler alive and therefore tombstoned.
        disconnected=new WeakReference<>(old);DISCONNECT_EPOCH.incrementAndGet();
        value=null;player=null;world=null;connection=null;
    }
}
