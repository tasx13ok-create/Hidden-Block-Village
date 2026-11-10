package village.vale;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Separate bounded schema; the established HUD packet is unchanged. */
public final class AbilityCodec {
    private AbilityCodec(){}
    public static void writeLoadout(ByteBuf b,AbilityLoadoutSnapshot s){
        b.writeByte(AbilityLoadoutSnapshot.SCHEMA);b.writeLong(s.playerId.getMostSignificantBits());b.writeLong(s.playerId.getLeastSignificantBits());b.writeInt(s.dimension);b.writeLong(s.serverTick);text(b,s.selectedEntryId);b.writeByte(s.entries.size());
        for(AbilitySnapshot.Entry e:s.entries){text(b,e.id);text(b,e.title);text(b,e.kind);text(b,e.schoolKey);text(b,e.iconItemId);b.writeInt(e.iconMeta);b.writeByte(e.requiredLevel);b.writeByte(e.resolveCost);b.writeInt(e.itemCount);b.writeBoolean(e.enabled);text(b,e.reason);b.writeInt(e.cooldownRemainingTicks);b.writeInt(e.cooldownTotalTicks);b.writeFloat(e.cooldownFraction);}
    }
    public static AbilityLoadoutSnapshot readLoadout(ByteBuf b){
        try{
            if(b.readUnsignedByte()!=AbilityLoadoutSnapshot.SCHEMA)throw new DecoderException("Unsupported loadout schema");UUID id=new UUID(b.readLong(),b.readLong());int dimension=b.readInt();long tick=b.readLong();String selected=text(b);int count=b.readUnsignedByte();
            if(count>AbilityLoadoutSnapshot.MAX_ENTRIES)throw new DecoderException("Loadout list too long");List<AbilitySnapshot.Entry> entries=new ArrayList<>();
            for(int i=0;i<count;i++)entries.add(new AbilitySnapshot.Entry(text(b),text(b),text(b),text(b),text(b),b.readInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readInt(),b.readBoolean(),text(b),b.readInt(),b.readInt(),b.readFloat()));
            if(b.isReadable())throw new DecoderException("Trailing loadout data");return new AbilityLoadoutSnapshot(id,dimension,tick,selected,entries);
        }catch(IllegalArgumentException|IndexOutOfBoundsException ex){throw new DecoderException("Invalid loadout snapshot",ex);}
    }
    public static void write(ByteBuf b,AbilitySnapshot s){
        b.writeByte(AbilitySnapshot.SCHEMA);b.writeLong(s.playerId.getMostSignificantBits());b.writeLong(s.playerId.getLeastSignificantBits());b.writeInt(s.dimension);b.writeLong(s.serverTick);text(b,s.selectedEntryId);b.writeByte(s.entries.size());
        for(AbilitySnapshot.Entry e:s.entries){text(b,e.id);text(b,e.title);text(b,e.kind);text(b,e.schoolKey);text(b,e.iconItemId);b.writeInt(e.iconMeta);b.writeByte(e.requiredLevel);b.writeByte(e.resolveCost);b.writeInt(e.itemCount);b.writeBoolean(e.enabled);text(b,e.reason);b.writeInt(e.cooldownRemainingTicks);b.writeInt(e.cooldownTotalTicks);b.writeFloat(e.cooldownFraction);}
    }
    public static AbilitySnapshot read(ByteBuf b){
        try{
            if(b.readUnsignedByte()!=AbilitySnapshot.SCHEMA)throw new DecoderException("Unsupported ability schema");
            UUID id=new UUID(b.readLong(),b.readLong());int dimension=b.readInt();long tick=b.readLong();String selected=text(b);int count=b.readUnsignedByte();
            if(count>AbilitySnapshot.MAX_ENTRIES)throw new DecoderException("Ability list too long");List<AbilitySnapshot.Entry> entries=new ArrayList<>();
            for(int i=0;i<count;i++)entries.add(new AbilitySnapshot.Entry(text(b),text(b),text(b),text(b),text(b),b.readInt(),b.readUnsignedByte(),b.readUnsignedByte(),b.readInt(),b.readBoolean(),text(b),b.readInt(),b.readInt(),b.readFloat()));
            if(b.isReadable())throw new DecoderException("Trailing ability data");return new AbilitySnapshot(id,dimension,tick,selected,entries);
        }catch(IllegalArgumentException|IndexOutOfBoundsException ex){throw new DecoderException("Invalid ability snapshot",ex);}
    }
    static void text(ByteBuf b,String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>640)throw new IllegalArgumentException("Ability UTF8 too long");b.writeShort(bytes.length);b.writeBytes(bytes);}
    static String text(ByteBuf b){int size=b.readUnsignedShort();if(size>640||size>b.readableBytes())throw new DecoderException("Invalid ability UTF8 length");String result=b.readCharSequence(size,StandardCharsets.UTF_8).toString();if(result.length()>160)throw new DecoderException("Ability text too long");return result;}
}
