package village.vale;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import village.vale.armor.ArmorProfile;

/** Clientbound visual/stamina state only; this channel registers no serverbound messages. */
public final class OriginalArmorPacket implements IMessage {
    public int entityId,stamina,sequence;public UUID owner;public ArmorProfile profile;public boolean flying;
    public OriginalArmorPacket(){}
    public OriginalArmorPacket(EntityPlayerMP player,ArmorProfile profile,int stamina,boolean flying,int sequence){this.entityId=player.getEntityId();this.owner=player.getUniqueID();this.profile=profile;this.stamina=stamina;this.flying=flying;this.sequence=sequence;}
    @Override public void toBytes(ByteBuf out){out.writeInt(entityId);out.writeLong(owner.getMostSignificantBits());out.writeLong(owner.getLeastSignificantBits());ByteBufUtils.writeUTF8String(out,profile==null?"":profile.key);out.writeShort(Math.max(0,Math.min(2000,stamina)));out.writeBoolean(flying);out.writeInt(sequence);}
    @Override public void fromBytes(ByteBuf in){entityId=in.readInt();owner=new UUID(in.readLong(),in.readLong());String key=ByteBufUtils.readUTF8String(in);if(key.length()>32)throw new IllegalArgumentException("Invalid armor identity");profile=ArmorProfile.byKey(key);stamina=Math.min(2000,in.readUnsignedShort());flying=in.readBoolean();sequence=in.readInt();}
    public static final class Handler implements IMessageHandler<OriginalArmorPacket,IMessage>{
        @Override public IMessage onMessage(OriginalArmorPacket packet,MessageContext ctx){net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(()->village.vale.armor.client.OriginalClientState.accept(packet));return null;}
    }
}
