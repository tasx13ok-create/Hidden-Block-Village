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
    private static Object connection,world,player;private static UUID owner;private static long tick,received=-1000,nonce,revision,nextQueue,generation,pendingUntil;private static int dimension,selected;private static boolean locked,hasSnapshot,combat;
    private static ItemStack[] stacks=new ItemStack[]{ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};
    private static int sequence,pendingSequence;private static long pendingNonce;private static int contextDimension;
    private static final CursorReceipt CURSOR=new CursorReceipt();
    private RackClientApi(){}
    public static void clear(){owner=null;pendingSequence=0;pendingNonce=0;CURSOR.clear();hasSnapshot=combat=false;received=-1000;stacks=new ItemStack[]{ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY};selected=0;nextQueue=pendingUntil=0;generation++;}
    private static void context(){Minecraft mc=Minecraft.getMinecraft();if(connection!=mc.getConnection()||world!=mc.world||player!=mc.player||(mc.player!=null&&contextDimension!=mc.player.dimension)){clear();connection=mc.getConnection();world=mc.world;player=mc.player;contextDimension=mc.player==null?0:mc.player.dimension;}}
    public static boolean ready(){context();Minecraft mc=Minecraft.getMinecraft();return hasSnapshot&&!locked&&mc.world!=null&&mc.player!=null&&mc.getConnection()!=null&&mc.player.isEntityAlive()&&!mc.player.isSpectator()&&mc.player.dimension==dimension&&mc.player.getUniqueID().equals(owner)&&tick-received<=100;}
    public static ItemStack stack(int index){return ready()&&index>=0&&index<6?stacks[index].copy():ItemStack.EMPTY;}
    public static int selectedSlot(){return ready()?selected:-1;}
    public static boolean combatActive(){return ready()&&combat;}
    public static long snapshotGeneration(){return generation;}
    private static void expireTransactions(){CURSOR.expire(tick);if(pendingUntil>0&&tick>=pendingUntil){pendingUntil=0;pendingSequence=0;pendingNonce=0;CURSOR.clear();}}
    public static boolean pending(){context();expireTransactions();return tick<pendingUntil;}
    public static int cursorTicket(){return ready()?pendingSequence:0;}
    public static boolean cursorAccepted(int ticket){Minecraft mc=Minecraft.getMinecraft();return ready()&&mc.player.openContainer==mc.player.inventoryContainer&&CURSOR.accepted(ticket,nonce,mc.player.openContainer.windowId,tick);}
    private static void sendTracked(RackNetwork.Request request){CURSOR.clear();sequence=sequence==Integer.MAX_VALUE?1:sequence+1;request.sequence=pendingSequence=sequence;pendingNonce=nonce;nextQueue=tick+2;pendingUntil=tick+CursorReceipt.TIMEOUT;if(request.operation==3)CURSOR.begin(sequence,nonce,request.window,tick);RackNetwork.CHANNEL.sendToServer(request);}
    public static boolean requestToggle(){if(!ready()||pending()||tick<nextQueue)return false;sendTracked(new RackNetwork.Request(2,255,nonce,revision));return true;}
    public static boolean requestCursor(int index,int window,int button){
        if(!ready()||pending()||tick<nextQueue||index<0||index>=6||window<0||button<0||button>1)return false;
        RackNetwork.Request request=new RackNetwork.Request(3,index,nonce,revision);request.window=window;request.button=button;
        sendTracked(request);return true;
    }
    public static void select(int index){if(ready()&&index>=0&&index<6)selected=index;}
    /** Equip/swap a stored weapon; an empty selection stows the held weapon. */
    public static boolean requestEquip(int index){if(!ready()||pending()||index<0||index>=6||tick<nextQueue)return false;CURSOR.clear();pendingSequence=0;nextQueue=tick+2;RackNetwork.CHANNEL.sendToServer(new RackNetwork.Request(0,index,nonce,revision));return true;}
    public static boolean requestOpen(){if(!ready()||pending()||tick<nextQueue)return false;CURSOR.clear();pendingSequence=0;nextQueue=tick+2;RackNetwork.CHANNEL.sendToServer(new RackNetwork.Request(1,255,nonce,revision));return true;}
    static void accept(RackNetwork.Snapshot message){context();Minecraft mc=Minecraft.getMinecraft();if(!message.valid||mc.player==null||mc.world==null||!mc.player.getUniqueID().equals(message.owner)||mc.player.dimension!=message.dimension)return;
        expireTransactions();
        if(!hasSnapshot||nonce!=message.nonce){selected=0;pendingUntil=0;pendingSequence=0;pendingNonce=0;CURSOR.clear();}
        // A periodic/stale snapshot is not acknowledgement of an inventory transaction.
        if(pendingUntil>tick&&message.nonce==pendingNonce&&message.acknowledged==pendingSequence&&pendingSequence>0){CURSOR.acknowledge(message.nonce,pendingSequence,message.accepted,tick);pendingUntil=0;}
        owner=message.owner;dimension=message.dimension;nonce=message.nonce;revision=message.revision;locked=message.locked;combat=message.combatActive;for(int i=0;i<6;i++)stacks[i]=message.stacks[i].copy();received=tick;hasSnapshot=true;generation++;
    }
    public static final class Events{
        @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END){tick++;context();expireTransactions();}}
        @SubscribeEvent public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event){clear();}
    }
}
