package village.immersion;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Server-to-client cues and squad updates. Clients without the mod simply never receive them. */
final class Net {
    private Net() {}
    static final int PERFECT = 1, IMPACT = 2, GUARD_BREAK = 3, EXHAUSTED = 4, STAGGER = 5;
    static SimpleNetworkWrapper CH;
    static void register() {
        CH = NetworkRegistry.INSTANCE.newSimpleChannel("shinobiimm");
        CH.registerMessage(CueHandler.class, Cue.class, 0, Side.CLIENT);
        CH.registerMessage(SquadHandler.class, SquadMsg.class, 1, Side.CLIENT);
    }
    static void cue(EntityPlayerMP p, int kind, float value) { if (CH != null && p.connection != null) CH.sendTo(new Cue(kind, value), p); }
    static void perfect(EntityPlayerMP p) { cue(p, PERFECT, 1); }

    public static final class Cue implements IMessage {
        int kind; float value;
        public Cue() {}
        Cue(int k, float v) { kind = k; value = v; }
        @Override public void fromBytes(ByteBuf b) { kind = b.readByte(); value = b.readFloat(); }
        @Override public void toBytes(ByteBuf b) { b.writeByte(kind); b.writeFloat(value); }
    }
    public static final class CueHandler implements IMessageHandler<Cue, IMessage> {
        @Override public IMessage onMessage(Cue m, MessageContext ctx) { ShinobiImmersion.proxy.cue(m.kind, m.value); return null; }
    }

    /** One squad member as seen by a teammate. */
    static final class Member {
        String name; int dim; double x, y, z; float health, max;
    }
    public static final class SquadMsg implements IMessage {
        final List<Member> members = new ArrayList<>();
        public SquadMsg() {}
        @Override public void fromBytes(ByteBuf b) {
            int n = Math.min(b.readByte(), ImmersionMath.SQUAD_MAX);
            for (int i = 0; i < n; i++) {
                Member m = new Member();
                byte[] raw = new byte[Math.min(b.readByte() & 0xFF, 32)]; b.readBytes(raw); m.name = new String(raw, StandardCharsets.UTF_8);
                m.dim = b.readInt(); m.x = b.readDouble(); m.y = b.readDouble(); m.z = b.readDouble(); m.health = b.readFloat(); m.max = b.readFloat();
                members.add(m);
            }
        }
        @Override public void toBytes(ByteBuf b) {
            int n = Math.min(members.size(), ImmersionMath.SQUAD_MAX);
            b.writeByte(n);
            for (int i = 0; i < n; i++) {
                Member m = members.get(i);
                byte[] raw = m.name.getBytes(StandardCharsets.UTF_8); int len = Math.min(raw.length, 32);
                b.writeByte(len); b.writeBytes(raw, 0, len);
                b.writeInt(m.dim); b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z); b.writeFloat(m.health); b.writeFloat(m.max);
            }
        }
    }
    public static final class SquadHandler implements IMessageHandler<SquadMsg, IMessage> {
        @Override public IMessage onMessage(SquadMsg m, MessageContext ctx) { ShinobiImmersion.proxy.squad(m.members); return null; }
    }
}
