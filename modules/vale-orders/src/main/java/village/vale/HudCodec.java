package village.vale;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Bounded schema-1 wire representation. There is no client-to-server action packet here. */
public final class HudCodec {
    private HudCodec(){}
    public static void write(ByteBuf b,HudSnapshot s){
        b.writeByte(HudSnapshot.SCHEMA);b.writeLong(s.playerId.getMostSignificantBits());b.writeLong(s.playerId.getLeastSignificantBits());b.writeInt(s.dimension);b.writeLong(s.serverTick);
        b.writeDouble(s.resolve);b.writeDouble(s.resolveMax);b.writeInt(s.level);b.writeLong(s.merit);
        text(b,s.selectedArtId);text(b,s.selectedArtName);b.writeInt(s.cooldownRemainingTicks);b.writeInt(s.cooldownTotalTicks);
        text(b,s.activeFormId);text(b,s.activeFormName);text(b,s.companionId);text(b,s.companionName);text(b,s.contractId);text(b,s.contractName);
        b.writeInt(s.contractProgress);b.writeInt(s.contractGoal);b.writeLong(s.castSequence);b.writeLong(s.lastCastServerTick);text(b,s.lastCastArtId);
    }
    public static HudSnapshot read(ByteBuf b){
        if(b.readUnsignedByte()!=HudSnapshot.SCHEMA)throw new DecoderException("Unsupported Vale HUD schema");
        try{
            UUID player=new UUID(b.readLong(),b.readLong());int dimension=b.readInt();long tick=b.readLong();double resolve=b.readDouble(),max=b.readDouble();int level=b.readInt();long merit=b.readLong();
            String art=text(b),artName=text(b);int remaining=b.readInt(),total=b.readInt();String form=text(b),formName=text(b),companion=text(b),companionName=text(b),contract=text(b),contractName=text(b);
            int progress=b.readInt(),goal=b.readInt();long sequence=b.readLong(),lastCast=b.readLong();String lastArt=text(b);
            if(b.isReadable())throw new DecoderException("Trailing Vale HUD data");
            return new HudSnapshot(player,dimension,tick,resolve,max,level,merit,art,artName,remaining,total,form,formName,companion,companionName,contract,contractName,progress,goal,sequence,lastCast,lastArt);
        }catch(IllegalArgumentException e){throw new DecoderException("Invalid Vale HUD snapshot",e);}
    }
    private static void text(ByteBuf b,String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>1024)throw new IllegalArgumentException("HUD UTF-8 too long");b.writeShort(bytes.length);b.writeBytes(bytes);}
    private static String text(ByteBuf b){int length=b.readUnsignedShort();if(length>1024||length>b.readableBytes())throw new DecoderException("Invalid HUD UTF-8 length");String value=b.readCharSequence(length,StandardCharsets.UTF_8).toString();if(value.length()>256)throw new DecoderException("HUD text too long");return value;}
}
