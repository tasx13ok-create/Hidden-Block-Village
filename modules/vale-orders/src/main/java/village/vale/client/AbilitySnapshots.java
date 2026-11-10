package village.vale.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.*;
import village.vale.*;

/** Optional GUI API. Never synthesize server progression or infer it from client NBT. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid=ValeOrders.ID,value=Side.CLIENT)
public final class AbilitySnapshots {
    private static AbilitySnapshot cached;private static EntityPlayerSP subject;private static WorldClient world;private static long received;
    private static AbilityLoadoutSnapshot loadout;private static EntityPlayerSP loadoutSubject;private static WorldClient loadoutWorld;private static long loadoutReceived;
    private AbilitySnapshots(){}
    public static AbilitySnapshot getLocal(){
        Minecraft mc=Minecraft.getMinecraft();AbilitySnapshot value=cached;
        if(value==null||mc.player==null||mc.world==null||mc.player!=subject||mc.world!=world||!mc.player.isEntityAlive()||mc.player.connection==null||!mc.player.getUniqueID().equals(value.playerId)||mc.player.dimension!=value.dimension||System.nanoTime()-received>5000000000L)return null;
        return value;
    }
    public static void accept(AbilitySnapshot value){
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null||mc.world==null||!mc.player.isEntityAlive()||!mc.player.getUniqueID().equals(value.playerId)||mc.player.dimension!=value.dimension)return;
        if(cached!=null&&subject==mc.player&&world==mc.world&&cached.serverTick>value.serverTick)return;
        cached=value;subject=mc.player;world=mc.world;received=System.nanoTime();
    }
    public static AbilityLoadoutSnapshot getLoadout(){
        Minecraft mc=Minecraft.getMinecraft();AbilityLoadoutSnapshot value=loadout;
        if(value==null||mc.player==null||mc.world==null||mc.player!=loadoutSubject||mc.world!=loadoutWorld||!mc.player.isEntityAlive()||mc.player.connection==null||!mc.player.getUniqueID().equals(value.playerId)||mc.player.dimension!=value.dimension||System.nanoTime()-loadoutReceived>5000000000L)return null;
        return value;
    }
    public static void acceptLoadout(AbilityLoadoutSnapshot value){
        Minecraft mc=Minecraft.getMinecraft();if(mc.player==null||mc.world==null||!mc.player.isEntityAlive()||!mc.player.getUniqueID().equals(value.playerId)||mc.player.dimension!=value.dimension)return;
        if(loadout!=null&&loadoutSubject==mc.player&&loadoutWorld==mc.world&&loadout.serverTick>value.serverTick)return;
        loadout=value;loadoutSubject=mc.player;loadoutWorld=mc.world;loadoutReceived=System.nanoTime();
    }
    public static void clear(){cached=null;subject=null;world=null;received=0;loadout=null;loadoutSubject=null;loadoutWorld=null;loadoutReceived=0;}
    @SubscribeEvent public static void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent e){Minecraft.getMinecraft().addScheduledTask(AbilitySnapshots::clear);}
}
