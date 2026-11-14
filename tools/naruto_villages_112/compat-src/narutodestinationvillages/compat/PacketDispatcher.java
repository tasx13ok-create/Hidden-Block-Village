package narutodestinationvillages.compat;
import net.minecraftforge.fml.common.network.*;import net.minecraftforge.fml.common.network.simpleimpl.*;import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.relauncher.Side;import net.minecraft.entity.player.EntityPlayerMP;
public final class PacketDispatcher {
 private static SimpleNetworkWrapper channel;
 public static void initialize(){if(channel!=null)return;channel=NetworkRegistry.INSTANCE.newSimpleChannel("ndvillages112");channel.registerMessage(SyncRankMessage.Handler.class,SyncRankMessage.class,0,Side.CLIENT);}
 public static void sendTo(IMessage message,EntityPlayerMP player){if(channel==null||player==null||player.connection==null)return;NetworkDispatcher dispatcher=NetworkDispatcher.get(player.connection.netManager);if(dispatcher!=null&&dispatcher.getModList()!=null&&dispatcher.getModList().containsKey("narutodestinationvillages"))channel.sendTo(message,player);}
}
