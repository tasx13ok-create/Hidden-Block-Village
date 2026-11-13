package village.valerack;

import java.util.UUID;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

public final class RackNetwork{
    public static SimpleNetworkWrapper CHANNEL;
    private RackNetwork(){}
    public static void initialize(){CHANNEL=NetworkRegistry.INSTANCE.newSimpleChannel("valerack");CHANNEL.registerMessage(RequestHandler.class,Request.class,0,Side.SERVER);CHANNEL.registerMessage(SnapshotHandler.class,Snapshot.class,1,Side.CLIENT);}
    public static final class Request implements IMessage{
        public int operation,index,window=-1,button,sequence;public long nonce,revision;public boolean valid;
        public Request(){}
        public Request(int operation,int index,long nonce,long revision){this.operation=operation;this.index=index;this.nonce=nonce;this.revision=revision;valid=true;}
        @Override public void fromBytes(ByteBuf b){valid=b.readableBytes()==27;if(!valid)return;operation=b.readUnsignedByte();index=b.readUnsignedByte();nonce=b.readLong();revision=b.readLong();window=b.readInt();button=b.readUnsignedByte();sequence=b.readInt();valid=sequence>=0&&(((operation==1||operation==2)&&index==255)||operation==0&&index<6||operation==3&&index<6&&window>=0&&button<=1);}
        @Override public void toBytes(ByteBuf b){b.writeByte(operation);b.writeByte(index);b.writeLong(nonce);b.writeLong(revision);b.writeInt(window);b.writeByte(button);b.writeInt(sequence);}
    }
    public static final class RequestHandler implements IMessageHandler<Request,IMessage>{
        @Override public IMessage onMessage(Request request,MessageContext context){
            EntityPlayerMP p=context.getServerHandler().player;RackSessions.Session session=RackSessions.SESSIONS.get(p.getUniqueID());
            if(!request.valid||session==null||session.owner!=p||!session.pending.compareAndSet(false,true))return null;
            p.getServerWorld().addScheduledTask(()->{try{RackSessions.handle(p,request);}finally{session.pending.set(false);}});return null;
        }
    }
    public static final class Snapshot implements IMessage{
        public UUID owner;public int dimension,acknowledged;public long nonce,revision;public boolean locked,valid,combatActive,accepted;public ItemStack[] stacks=new ItemStack[6];
        public Snapshot(){}
        Snapshot(EntityPlayerMP p,RackSessions.Session s,RackData data){owner=p.getUniqueID();dimension=p.dimension;nonce=s.nonce;revision=data.revision;locked=data.locked;combatActive=data.combat.active;acknowledged=s.acknowledged;accepted=s.accepted;for(int i=0;i<6;i++)stacks[i]=data.combat.combat(p,data,i).copy();valid=true;}
        @Override public void toBytes(ByteBuf b){b.writeLong(owner.getMostSignificantBits());b.writeLong(owner.getLeastSignificantBits());b.writeInt(dimension);b.writeLong(nonce);b.writeLong(revision);b.writeBoolean(locked);b.writeBoolean(combatActive);b.writeInt(acknowledged);b.writeBoolean(accepted);for(ItemStack stack:stacks)ByteBufUtils.writeItemStack(b,stack);}
        @Override public void fromBytes(ByteBuf b){owner=new UUID(b.readLong(),b.readLong());dimension=b.readInt();nonce=b.readLong();revision=b.readLong();locked=b.readBoolean();combatActive=b.readBoolean();acknowledged=b.readInt();accepted=b.readBoolean();for(int i=0;i<6;i++)stacks[i]=ByteBufUtils.readItemStack(b);valid=b.readableBytes()==0&&acknowledged>=0;}
    }
    public static final class SnapshotHandler implements IMessageHandler<Snapshot,IMessage>{@Override public IMessage onMessage(Snapshot message,MessageContext context){RackMod.PROXY.snapshot(message,context);return null;}}
}
