package village.vale.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.*;
import village.vale.*;

/** Pure client cache API for optional HUD mods. No default stats or client-side NBT inference. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid=ValeOrders.ID,value=Side.CLIENT)
public final class HudSnapshots {
    private static HudSnapshot cached;
    private static EntityPlayerSP subject;
    private static WorldClient world;
    private static long received;
    private HudSnapshots(){}
    public static HudSnapshot getLocal(){
        Minecraft mc=Minecraft.getMinecraft();HudSnapshot value=cached;
        if(value==null||mc.player==null||mc.world==null||mc.player!=subject||mc.world!=world||!mc.player.isEntityAlive()||mc.player.connection==null||!mc.player.getUniqueID().equals(value.playerId)||mc.player.dimension!=value.dimension||System.nanoTime()-received>5000000000L)return null;
        return value;
    }
    public static void accept(HudSnapshot value){
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null||mc.world==null||!mc.player.isEntityAlive()||!mc.player.getUniqueID().equals(value.playerId)||mc.player.dimension!=value.dimension)return;
        if(cached!=null&&subject==mc.player&&world==mc.world&&cached.serverTick>value.serverTick)return;
        cached=value;subject=mc.player;world=mc.world;received=System.nanoTime();
    }
    public static void clear(){cached=null;subject=null;world=null;received=0;}
    @SubscribeEvent public static void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent e){Minecraft.getMinecraft().addScheduledTask(HudSnapshots::clear);}
}
