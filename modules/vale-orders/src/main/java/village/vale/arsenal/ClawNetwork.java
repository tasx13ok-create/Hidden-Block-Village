package village.vale.arsenal;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import village.vale.ArsenalAccess;

/** A bounded input request only: aim, item, target, power and timing stay on the server. */
public final class ClawNetwork {
    private static SimpleNetworkWrapper channel;
    private static final Map<UUID,Long> ENQUEUED=new ConcurrentHashMap<>();
    private static final Map<UUID,Long> ACTIONS=new HashMap<>();
    private ClawNetwork() {}
    static void register() {
        if(channel!=null)return;
        channel=NetworkRegistry.INSTANCE.newSimpleChannel("valeorders_claw");
        channel.registerMessage(Handler.class,Request.class,0,Side.SERVER);
    }
    public static boolean request(int dimension) {
        if(channel==null)return false;
        channel.sendToServer(new Request(dimension));return true;
    }
    static void clear(UUID owner) {ENQUEUED.remove(owner);ACTIONS.remove(owner);}
    static boolean useFromPacket(EntityPlayerMP p) {
        if(p.isHandActive()||p.openContainer!=p.inventoryContainer||p.capabilities.isCreativeMode)return false;
        ItemStack stack=p.getHeldItemMainhand();
        if(!(stack.getItem() instanceof ArsenalBlade)||((ArsenalBlade)stack.getItem()).spec!=ArsenalSpec.CRESCENT_TALONS)return false;
        // Reuse the native path, including ForgeHooks.onItemRightClick, active item
        // cooldown and the item's offhand-shield routing. A packet is only input.
        return p.interactionManager.processRightClick(p,p.world,stack,EnumHand.MAIN_HAND)==EnumActionResult.SUCCESS;
    }
    public static final class Request implements IMessage {
        int dimension;
        public Request() {}
        public Request(int dimension) {this.dimension=dimension;}
        @Override public void toBytes(ByteBuf buffer) {buffer.writeByte(1);buffer.writeInt(dimension);}
        @Override public void fromBytes(ByteBuf buffer) {
            if(buffer.readableBytes()!=5||buffer.readUnsignedByte()!=1)throw new DecoderException("Invalid Crescent Talons request");
            dimension=buffer.readInt();
        }
    }
    public static final class Handler implements IMessageHandler<Request,IMessage> {
        @Override public IMessage onMessage(Request request,MessageContext context) {
            if(context.side!=Side.SERVER)return null;
            EntityPlayerMP p=context.getServerHandler().player;
            long nanos=System.nanoTime();Long last=ENQUEUED.put(p.getUniqueID(),nanos);
            if(last!=null&&nanos-last<50000000L)return null;
            p.getServerWorld().addScheduledTask(()->{
                if(p instanceof FakePlayer||p.connection!=context.getServerHandler()||!p.connection.netManager.isChannelOpen()
                        ||!p.isEntityAlive()||p.isSpectator()||p.dimension!=request.dimension||p.getServer()==null
                        ||p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())!=p)return;
                long now=ArsenalAccess.now(p);Long previous=ACTIONS.get(p.getUniqueID());
                if(previous!=null&&now>=previous&&now-previous<3)return;
                ACTIONS.put(p.getUniqueID(),now);useFromPacket(p);
            });
            return null;
        }
    }
}
