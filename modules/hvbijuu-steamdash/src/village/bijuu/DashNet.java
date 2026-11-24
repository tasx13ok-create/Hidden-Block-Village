package village.bijuu;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Steam Dash network messages: the key press (client to server) and cinematic cues (server to clients). */
final class DashNet {
    private DashNet() {}

    static final int START = 1, BLINK = 2, IMPACT = 3, RELEASE = 4, DEBRIS = 5, SPIKE = 6, CRATER = 7, END = 8, SPRING = 9, SPLATTER = 10,
            CHARGE = 11, CHARGE_END = 12, BARRAGE = 13, PASS = 14, RELAY = 15, PIN = 16, STAB = 17, BUBBLE = 18,
            LARIAT_START = 20, LARIAT_BLINK = 21, LARIAT_IMPACT = 22, LARIAT_RELEASE = 23, LARIAT_END = 24;

    /** The dash key: PRESS when it goes down, RELEASE when it comes up (holding it overcharges). */
    public static final class Request implements IMessage {
        static final int PRESS = 0, RELEASE = 1;
        int action;
        public Request() {}
        Request(int action) { this.action = action; }
        @Override public void fromBytes(ByteBuf b) { action = b.readByte(); }
        @Override public void toBytes(ByteBuf b) { b.writeByte(action); }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {
        @Override public IMessage onMessage(Request m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            int action = m.action;
            p.getServerWorld().addScheduledTask(() -> { if (action == Request.RELEASE) DashServer.release(p); else DashServer.press(p); });
            return null;
        }
    }

    /** One cue: kind, the two entities, a position, a direction and optional packed data. */
    public static final class Fx implements IMessage {
        int kind, attacker, target;
        double x, y, z, dx, dy, dz;
        int[] data = new int[0];
        public Fx() {}
        Fx(int kind, int attacker, int target, double x, double y, double z, double dx, double dy, double dz, int[] data) {
            this.kind = kind; this.attacker = attacker; this.target = target; this.x = x; this.y = y; this.z = z;
            this.dx = dx; this.dy = dy; this.dz = dz; this.data = data == null ? new int[0] : data;
        }
        @Override public void fromBytes(ByteBuf b) {
            kind = b.readByte(); attacker = b.readInt(); target = b.readInt();
            x = b.readDouble(); y = b.readDouble(); z = b.readDouble(); dx = b.readFloat(); dy = b.readFloat(); dz = b.readFloat();
            int n = Math.min(b.readUnsignedShort(), 4096); data = new int[n];
            for (int i = 0; i < n; i++) data[i] = b.readInt();
        }
        @Override public void toBytes(ByteBuf b) {
            b.writeByte(kind); b.writeInt(attacker); b.writeInt(target);
            b.writeDouble(x); b.writeDouble(y); b.writeDouble(z); b.writeFloat((float) dx); b.writeFloat((float) dy); b.writeFloat((float) dz);
            int n = Math.min(data.length, 4096); b.writeShort(n);
            for (int i = 0; i < n; i++) b.writeInt(data[i]);
        }
    }

    public static final class FxHandler implements IMessageHandler<Fx, IMessage> {
        @Override public IMessage onMessage(Fx m, MessageContext ctx) { SteamDash.proxy.fx(m); return null; }
    }
}
