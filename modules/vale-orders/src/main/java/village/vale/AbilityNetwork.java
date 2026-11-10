package village.vale;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.*;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

/** Additive owner-only S2C channel and strictly bounded exact-ID C2S requests. */
public final class AbilityNetwork {
    private static SimpleNetworkWrapper channel,loadoutChannel;
    private static final Set<UUID> DIRTY=new HashSet<>();
    private static final Map<UUID,Long> LAST_ACTION=new HashMap<>();
    private static final Map<UUID,Long> LAST_ENQUEUE=new ConcurrentHashMap<>();
    private AbilityNetwork(){}
    public static void register(){
        if(channel!=null)return;channel=NetworkRegistry.INSTANCE.newSimpleChannel("valeorders_bar");channel.registerMessage(SnapshotHandler.class,SnapshotMessage.class,0,Side.CLIENT);channel.registerMessage(ActionHandler.class,ActionMessage.class,1,Side.SERVER);
        loadoutChannel=NetworkRegistry.INSTANCE.newSimpleChannel("valeorders_bar2");loadoutChannel.registerMessage(LoadoutHandler.class,LoadoutMessage.class,0,Side.CLIENT);loadoutChannel.registerMessage(EquipHandler.class,EquipMessage.class,1,Side.SERVER);MinecraftForge.EVENT_BUS.register(new AbilityNetwork());
    }
    public static void dirty(EntityPlayer p){if(p instanceof EntityPlayerMP&&!(p instanceof FakePlayer)&&!p.world.isRemote)DIRTY.add(p.getUniqueID());}
    public static boolean request(String id,int dimension,boolean activate){if(channel==null||!AbilitySnapshot.validId(id))return false;channel.sendToServer(new ActionMessage(id,dimension,activate));return true;}
    public static boolean requestEquip(String id,int dimension){if(loadoutChannel==null||!weaponId(id))return false;loadoutChannel.sendToServer(new EquipMessage(id,dimension));return true;}
    private static boolean weaponId(String id){return id!=null&&id.matches("weapon/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");}
    static boolean versionCanLoadout(String version){
        if(version==null)return false;java.util.regex.Matcher m=java.util.regex.Pattern.compile("^0\\.3\\.(\\d{1,6})(?:[-+][A-Za-z0-9_.-]+)?$").matcher(version);return m.matches()&&Integer.parseInt(m.group(1))>=2;
    }
    private static boolean supportsLoadout(EntityPlayerMP player){
        try{NetworkDispatcher dispatcher=NetworkDispatcher.get(player.connection.netManager);return dispatcher!=null&&dispatcher.getModList()!=null&&versionCanLoadout(dispatcher.getModList().get(ValeOrders.ID));}
        catch(RuntimeException|LinkageError unavailable){return false;}
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.player.world.isRemote||!(event.player instanceof EntityPlayerMP)||event.player instanceof FakePlayer)return;
        EntityPlayerMP p=(EntityPlayerMP)event.player;boolean changed=DIRTY.remove(p.getUniqueID());
        if(p.isEntityAlive()&&p.connection!=null&&p.connection.netManager.isChannelOpen()&&(changed||p.ticksExisted%20==0)){
            AbilitySnapshot legacy=AbilityActions.snapshot(p);channel.sendTo(new SnapshotMessage(legacy),p);
            if(supportsLoadout(p))loadoutChannel.sendTo(new LoadoutMessage(AbilityActions.loadout(p,legacy)),p);
        }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){dirty(e.player);}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){dirty(e.player);LAST_ACTION.remove(e.player.getUniqueID());}
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){dirty(e.player);LAST_ACTION.remove(e.player.getUniqueID());}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){UUID id=e.player.getUniqueID();DIRTY.remove(id);LAST_ACTION.remove(id);LAST_ENQUEUE.remove(id);}
    public static final class SnapshotMessage implements IMessage {
        private AbilitySnapshot value;public SnapshotMessage(){}SnapshotMessage(AbilitySnapshot value){this.value=value;}
        @Override public void toBytes(ByteBuf b){AbilityCodec.write(b,value);}@Override public void fromBytes(ByteBuf b){value=AbilityCodec.read(b);}
    }
    public static final class SnapshotHandler implements IMessageHandler<SnapshotMessage,IMessage> {
        @Override public IMessage onMessage(SnapshotMessage message,MessageContext context){if(context.side==Side.CLIENT)ValeOrders.proxy.abilitySnapshot(message.value);return null;}
    }
    public static final class LoadoutMessage implements IMessage {
        private AbilityLoadoutSnapshot value;public LoadoutMessage(){}LoadoutMessage(AbilityLoadoutSnapshot value){this.value=value;}
        @Override public void toBytes(ByteBuf b){AbilityCodec.writeLoadout(b,value);}@Override public void fromBytes(ByteBuf b){value=AbilityCodec.readLoadout(b);}
    }
    public static final class LoadoutHandler implements IMessageHandler<LoadoutMessage,IMessage> {
        @Override public IMessage onMessage(LoadoutMessage message,MessageContext context){if(context.side==Side.CLIENT)ValeOrders.proxy.abilityLoadout(message.value);return null;}
    }
    public static final class EquipMessage implements IMessage {
        String id;int dimension;public EquipMessage(){}public EquipMessage(String id,int dimension){if(!weaponId(id))throw new IllegalArgumentException("Invalid equip ID");this.id=id;this.dimension=dimension;}
        @Override public void toBytes(ByteBuf b){b.writeByte(1);b.writeInt(dimension);AbilityCodec.text(b,id);}
        @Override public void fromBytes(ByteBuf b){if(b.readUnsignedByte()!=1)throw new DecoderException("Unsupported equip request");dimension=b.readInt();id=AbilityCodec.text(b);if(!weaponId(id)||b.isReadable())throw new DecoderException("Invalid equip request");}
    }
    public static final class EquipHandler implements IMessageHandler<EquipMessage,IMessage> {
        @Override public IMessage onMessage(EquipMessage message,MessageContext context){enqueue(context,message.id,message.dimension,2);return null;}
    }
    public static final class ActionMessage implements IMessage {
        String id;int dimension;boolean activate;
        public ActionMessage(){}public ActionMessage(String id,int dimension,boolean activate){if(!AbilitySnapshot.validId(id))throw new IllegalArgumentException("Invalid action ID");this.id=id;this.dimension=dimension;this.activate=activate;}
        @Override public void toBytes(ByteBuf b){b.writeByte(1);b.writeByte(activate?1:0);b.writeInt(dimension);AbilityCodec.text(b,id);}
        @Override public void fromBytes(ByteBuf b){if(b.readUnsignedByte()!=1)throw new DecoderException("Unsupported ability action");int op=b.readUnsignedByte();if(op>1)throw new DecoderException("Invalid ability action");activate=op==1;dimension=b.readInt();id=AbilityCodec.text(b);if(!AbilitySnapshot.validId(id)||b.isReadable())throw new DecoderException("Invalid ability request");}
    }
    public static final class ActionHandler implements IMessageHandler<ActionMessage,IMessage> {
        @Override public IMessage onMessage(ActionMessage message,MessageContext context){
            enqueue(context,message.id,message.dimension,message.activate?1:0);return null;
        }
    }
    private static void enqueue(MessageContext context,String id,int dimension,int operation){
        if(context.side!=Side.SERVER)return;EntityPlayerMP p=context.getServerHandler().player;long nanos=System.nanoTime();Long previous=LAST_ENQUEUE.put(p.getUniqueID(),nanos);
        if(previous!=null&&nanos-previous<50000000L)return;
        p.getServerWorld().addScheduledTask(()->{
            if(p instanceof FakePlayer||p.connection!=context.getServerHandler()||!p.connection.netManager.isChannelOpen()||!p.isEntityAlive()||p.isSpectator()||p.dimension!=dimension||p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())!=p)return;
            long now=Adventures.now(p);Long last=LAST_ACTION.get(p.getUniqueID());if(last!=null&&now>=last&&now-last<3)return;LAST_ACTION.put(p.getUniqueID(),now);
            if(operation==2)AbilityActions.equip(p,id);else if(operation==1)AbilityActions.activate(p,id);else AbilityActions.select(p,id);
        });
    }
}
