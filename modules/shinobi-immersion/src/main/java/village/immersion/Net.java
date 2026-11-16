package village.immersion;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** One tiny server-to-client cue (perfect dodge flash). Optional: servers without the mod send nothing. */
final class Net {
    private Net() {}
    static SimpleNetworkWrapper CH;
    static void register() {
        CH = NetworkRegistry.INSTANCE.newSimpleChannel("shinobiimm");
        CH.registerMessage(Handler.class, Cue.class, 0, Side.CLIENT);
    }
    static void perfect(EntityPlayerMP p) { if (CH != null) CH.sendTo(new Cue(1), p); }

    public static final class Cue implements IMessage {
        int kind;
        public Cue() {}
        Cue(int k) { kind = k; }
        @Override public void fromBytes(ByteBuf b) { kind = b.readByte(); }
        @Override public void toBytes(ByteBuf b) { b.writeByte(kind); }
    }
    public static final class Handler implements IMessageHandler<Cue, IMessage> {
        @Override public IMessage onMessage(Cue m, MessageContext ctx) { ShinobiImmersion.proxy.cue(m.kind); return null; }
    }
}
