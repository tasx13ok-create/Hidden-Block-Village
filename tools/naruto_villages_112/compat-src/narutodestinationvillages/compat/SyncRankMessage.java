package narutodestinationvillages.compat;
import io.netty.buffer.ByteBuf;import net.minecraftforge.fml.common.network.ByteBufUtils;import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraft.entity.player.EntityPlayer;import net.minecraft.nbt.NBTTagCompound;import net.minecraftforge.fml.common.FMLCommonHandler;
/** Original optional client mirror; no client request may set server rank or membership. */
public final class SyncRankMessage implements IMessage {
 private String rank;private int affiliation;
 public SyncRankMessage(){}public SyncRankMessage(EntityPlayer player){ExtendedPlayer p=ExtendedPlayer.get(player);rank=p.getRank();affiliation=p.getAffiliation();}
 public void toBytes(ByteBuf b){ByteBufUtils.writeUTF8String(b,rank);b.writeInt(affiliation);}
 public void fromBytes(ByteBuf b){rank=ByteBufUtils.readUTF8String(b);affiliation=b.readInt();if(NarutoRank.fromId(rank)==null||affiliation<0||affiliation>=CommandAffiliation.affiliations.length)throw new IllegalArgumentException("Invalid village mirror");}
 public static final class Handler implements IMessageHandler<SyncRankMessage,IMessage>{
  public IMessage onMessage(final SyncRankMessage message,MessageContext context){
   FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(()->{
    try{
     Class<?> mc=Class.forName("net.minecraft.client.Minecraft");Object instance;
     try{instance=mc.getMethod("func_71410_x").invoke(null);}catch(NoSuchMethodException development){instance=mc.getMethod("getMinecraft").invoke(null);}
     Object value;try{value=mc.getField("field_71439_g").get(instance);}catch(NoSuchFieldException development){value=mc.getField("player").get(instance);}
     if(!(value instanceof EntityPlayer)||!((EntityPlayer)value).world.isRemote)return;
     EntityPlayer player=(EntityPlayer)value;NBTTagCompound data=player.getEntityData();if(!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG,10))data.setTag(EntityPlayer.PERSISTED_NBT_TAG,new NBTTagCompound());NBTTagCompound p=data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);if(!p.hasKey("NarutoVillages112",10))p.setTag("NarutoVillages112",new NBTTagCompound());NBTTagCompound village=p.getCompoundTag("NarutoVillages112");village.setString("rank",message.rank);village.setInteger("affiliation",message.affiliation);
    }catch(ReflectiveOperationException e){throw new IllegalStateException("Village client mirror unavailable",e);}
   });return null;
  }
 }
}
