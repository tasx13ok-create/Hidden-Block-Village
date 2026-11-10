package village.valerack.client;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import village.valerack.*;

/** Optional HUD API v1. Every nonempty returned stack is a deep defensive copy. */
public final class RackClientApi{
    private static Object connection,world,player;private static UUID owner;private static long tick,received=-1000,nonce,revision,nextQueue;private static int dimension,selected;private static boolean locked,hasSnapshot;
    private static ItemStack[] stacks=new ItemStack[]{ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
    private RackClientApi(){}
    public static void clear(){owner=null;hasSnapshot=false;received=-1000;stacks=new ItemStack[]{ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};selected=0;nextQueue=0;}
    private static void context(){Minecraft mc=Minecraft.getMinecraft();if(connection!=mc.getConnection()||world!=mc.world||player!=mc.player){clear();connection=mc.getConnection();world=mc.world;player=mc.player;}}
    public static boolean ready(){context();Minecraft mc=Minecraft.getMinecraft();return hasSnapshot&&!locked&&mc.world!=null&&mc.player!=null&&mc.getConnection()!=null&&mc.player.isEntityAlive()&&!mc.player.isSpectator()&&mc.player.dimension==dimension&&mc.player.getUniqueID().equals(owner)&&tick-received<=100;}
    public static ItemStack stack(int index){return ready()&&index>=0&&index<6?stacks[index].copy():ItemStack.EMPTY;}
    public static int selectedSlot(){return ready()?selected:-1;}
    public static void select(int index){if(ready()&&index>=0&&index<6)selected=index;}
    /** Equip/swap a stored weapon; an empty selection stows the held weapon. */
    public static boolean requestEquip(int index){if(!ready()||index<0||index>=6||tick<nextQueue)return false;nextQueue=tick+2;RackNetwork.CHANNEL.sendToServer(new RackNetwork.Request(0,index,nonce,revision));return true;}
    public static boolean requestOpen(){if(!ready()||tick<nextQueue)return false;nextQueue=tick+2;RackNetwork.CHANNEL.sendToServer(new RackNetwork.Request(1,255,nonce,revision));return true;}
    static void accept(RackNetwork.Snapshot message){context();Minecraft mc=Minecraft.getMinecraft();if(!message.valid||mc.player==null||mc.world==null||!mc.player.getUniqueID().equals(message.owner)||mc.player.dimension!=message.dimension)return;
        if(!hasSnapshot||nonce!=message.nonce)selected=0;owner=message.owner;dimension=message.dimension;nonce=message.nonce;revision=message.revision;locked=message.locked;for(int i=0;i<6;i++)stacks[i]=message.stacks[i].copy();received=tick;hasSnapshot=true;
    }
    public static final class Events{
        @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END){tick++;context();}}
        @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event){clear();}
    }
}
