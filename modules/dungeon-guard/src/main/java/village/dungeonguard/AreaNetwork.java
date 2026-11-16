package village.dungeonguard;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class AreaNetwork {
    private static SimpleNetworkWrapper channel;
    private AreaNetwork() { }
    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("dg_area_v1");
        channel.registerMessage(AreaMessage.Handler.class, AreaMessage.class, 0, Side.CLIENT);
    }
    public static void send(EntityPlayerMP player, AreaState area, boolean changed) {
        NetworkDispatcher dispatcher = NetworkDispatcher.get(player.connection.netManager);
        if (dispatcher != null && DungeonGuard.VERSION.equals(dispatcher.getModList().get(DungeonGuard.ID))) {
            channel.sendTo(new AreaMessage(area), player);
        } else if (changed && !area.isEmpty()) {
            // Unmodified clients receive ordinary Minecraft text. Never send them an unknown custom packet.
            player.connection.sendPacket(new SPacketTitle(10, 60, 15));
            player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE,
                    new TextComponentString(area.heading() + (area.isBoss() ? " — " + area.boss : ""))));
            player.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, new TextComponentString(area.title)));
        }
    }
}
