package village.dungeonguard;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import village.dungeonguard.AreaMessage;
import village.dungeonguard.AreaPresentation;
import village.dungeonguard.AreaState;

public final class AreaNetwork {
    private static SimpleNetworkWrapper channel;
    private static final Map<EntityPlayerMP, Integer> fallbackTitleUntil;

    private AreaNetwork() {
    }

    public static void init() {
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("dg_area_v1");
        channel.registerMessage(AreaMessage.Handler.class, AreaMessage.class, 0, Side.CLIENT);
    }

    public static void send(EntityPlayerMP player, AreaState area, boolean changed) {
        NetworkDispatcher dispatcher = NetworkDispatcher.get((NetworkManager)player.connection.netManager);
        if (dispatcher != null && DungeonGuard.VERSION.equals(dispatcher.getModList().get(DungeonGuard.ID))) {
            fallbackTitleUntil.remove(player);
            channel.sendTo((IMessage)new AreaMessage(area), player);
        } else if (changed && area.isEmpty()) {
            Integer until = fallbackTitleUntil.remove(player);
            if (until != null && player.ticksExisted < until) {
                player.connection.sendPacket((Packet)new SPacketTitle(SPacketTitle.Type.CLEAR, (ITextComponent)new TextComponentString("")));
            }
        } else if (changed) {
            player.connection.sendPacket((Packet)new SPacketTitle(10, 60, 15));
            player.connection.sendPacket((Packet)new SPacketTitle(SPacketTitle.Type.SUBTITLE, (ITextComponent)new TextComponentString("")));
            player.connection.sendPacket((Packet)new SPacketTitle(SPacketTitle.Type.TITLE, (ITextComponent)new TextComponentString(AreaPresentation.name(area))));
            fallbackTitleUntil.put(player, player.ticksExisted + 85);
        }
    }

    static {
        fallbackTitleUntil = new WeakHashMap<EntityPlayerMP, Integer>();
    }
}

