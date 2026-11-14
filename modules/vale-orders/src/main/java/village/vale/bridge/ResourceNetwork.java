package village.vale.bridge;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

/** Server-to-owner telemetry only; no client message can set or spend a resource. */
public final class ResourceNetwork {
    private static SimpleNetworkWrapper channel;
    private static volatile Receiver receiver;
    public interface Receiver {void accept(ResourceSnapshot value,Object connection);}
    public static void client(Receiver next){receiver=next;}
    public static void register(){if(channel!=null)return;channel=NetworkRegistry.INSTANCE.newSimpleChannel("naruto_resource");channel.registerMessage(Handler.class,Message.class,0,Side.CLIENT);MinecraftForge.EVENT_BUS.register(new ResourceNetwork());}
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof EntityPlayerMP)||event.player.world.isRemote)return;
        EntityPlayerMP p=(EntityPlayerMP)event.player;if(p.ticksExisted%5!=0||!MoveCost.current(p))return;
        ChakraLink.View view=MoveCost.view(p);boolean ready=view.ready();
        ResourceSnapshot value=new ResourceSnapshot(p.getUniqueID(),p.getEntityId(),p.dimension,Integer.toUnsignedLong(p.getServer().getTickCounter()),view.provider,view.state.ordinal(),ready?view.amount:0,ready?view.maximum:0,"narutomod".equals(view.provider)?MoveCost.scale():1);
        channel.sendTo(new Message(value),p);
    }
    public static final class Message implements IMessage {
        ResourceSnapshot value;
        public Message(){}public Message(ResourceSnapshot value){this.value=value;}
        @Override public void toBytes(ByteBuf b){ResourceSnapshot v=value;b.writeByte(1);b.writeLong(v.owner.getMostSignificantBits());b.writeLong(v.owner.getLeastSignificantBits());b.writeInt(v.entityId);b.writeInt(v.dimension);b.writeLong(v.serverTick);b.writeByte(v.provider.equals("narutomod")?1:0);b.writeByte(v.state);b.writeDouble(v.amount);b.writeDouble(v.maximum);b.writeDouble(v.scale);}
        @Override public void fromBytes(ByteBuf b){
            if(b.readableBytes()!=59||b.readUnsignedByte()!=1)throw new DecoderException("Invalid resource packet size/version");
            UUID owner=new UUID(b.readLong(),b.readLong());int entity=b.readInt(),dimension=b.readInt();long tick=b.readLong();int provider=b.readUnsignedByte(),state=b.readUnsignedByte();if(provider>1)throw new DecoderException("Invalid resource provider");
            try{value=new ResourceSnapshot(owner,entity,dimension,tick,provider==1?"narutomod":"resolve",state,b.readDouble(),b.readDouble(),b.readDouble());}catch(IllegalArgumentException invalid){throw new DecoderException("Invalid resource packet",invalid);}
        }
    }
    public static final class Handler implements IMessageHandler<Message,IMessage>{public IMessage onMessage(Message message,MessageContext context){Receiver next=receiver;if(context.side==Side.CLIENT&&next!=null)next.accept(message.value,context.getClientHandler());return null;}}
}
