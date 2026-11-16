package village.dungeonguard;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server-to-client display only, with bounded lengths and a versioned wire format. */
public final class AreaMessage implements IMessage {
    public AreaState area = AreaState.EMPTY;
    public AreaMessage() { }
    public AreaMessage(AreaState area) {
        if (area == null) throw new IllegalArgumentException("Area is required.");
        this.area = area;
    }
    @Override public void toBytes(ByteBuf out) {
        out.writeByte(1); out.writeBoolean(!area.isEmpty());
        if (area.isEmpty()) return;
        write(out, area.key); write(out, area.element); write(out, area.title); write(out, area.boss);
    }
    @Override public void fromBytes(ByteBuf in) {
        try {
            if (in.readUnsignedByte() != 1) throw new IllegalArgumentException("Unsupported area protocol.");
            int present = in.readUnsignedByte();
            if (present == 0) area = AreaState.EMPTY;
            else if (present == 1) area = new AreaState(read(in, 128), read(in, 16), read(in, 80), read(in, 80));
            else throw new IllegalArgumentException("Invalid area flag.");
            if (in.isReadable()) throw new IllegalArgumentException("Unexpected area packet data.");
        } catch (RuntimeException e) { throw new DecoderException("Invalid Dungeon Guard area packet", e); }
    }
    private static void write(ByteBuf out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length); out.writeBytes(bytes);
    }
    private static String read(ByteBuf in, int limit) {
        int size = in.readUnsignedShort();
        if (size > limit * 4 || size > in.readableBytes()) throw new IllegalArgumentException("Area text is too long or truncated.");
        byte[] bytes = new byte[size]; in.readBytes(bytes);
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.length() > limit) throw new IllegalArgumentException("Area text exceeds character limit.");
            return text;
        } catch (CharacterCodingException e) { throw new IllegalArgumentException("Invalid area UTF-8.", e); }
    }
    public static final class Handler implements IMessageHandler<AreaMessage, IMessage> {
        @Override public IMessage onMessage(AreaMessage message, MessageContext context) {
            DungeonGuard.proxy.receiveArea(message.area); return null;
        }
    }
}
