package village.vale;

import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Four-way dodge. The client only asks for a direction; the server checks cooldown and the
 * one-air-dodge rule, applies the burst, grants a short hurt grace and tells every tracking
 * client (and the dodger) which animation to play.
 */
public final class Dodge {
    private Dodge() {}
    private static SimpleNetworkWrapper channel;
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, Boolean> AIR_USED = new HashMap<>();

    public static void register() {
        if (channel != null) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("valeorders_dodge");
        channel.registerMessage(RequestHandler.class, Request.class, 0, Side.SERVER);
        channel.registerMessage(AnimHandler.class, Anim.class, 1, Side.CLIENT);
        MinecraftForge.EVENT_BUS.register(new Dodge());
    }

    /** Client side: ask the server for a dodge in {@code dir}. */
    public static void request(int dir) { if (channel != null && DodgeMath.valid(dir)) channel.sendToServer(new Request(dir)); }

    static void perform(EntityPlayerMP p, int dir) {
        if (!DodgeMath.valid(dir)) return;
        long now = p.world.getTotalWorldTime();
        UUID id = p.getUniqueID();
        boolean airUsed = AIR_USED.getOrDefault(id, false);
        if (!DodgeMath.allowed(p.isEntityAlive(), p.isSpectator(), now, LAST.getOrDefault(id, Long.MIN_VALUE / 2), p.onGround, airUsed)) return;
        if (p.isRiding() || p.isElytraFlying() || p.capabilities.isFlying || p.isPlayerSleeping()) return;
        if (!village.vale.bridge.MoveCost.pay(p, village.vale.bridge.Bridge.dodgeCost)) return;
        LAST.put(id, now);
        if (!p.onGround) AIR_USED.put(id, true);
        double[] v = DodgeMath.velocity(dir, p.rotationYaw);
        double keep = p.onGround ? 0.2 : 0.4;
        p.motionX = p.motionX * keep + v[0];
        p.motionY = Math.max(p.motionY, 0) * 0.3 + v[1];
        p.motionZ = p.motionZ * keep + v[2];
        p.velocityChanged = true;
        p.fallDistance = 0;
        p.hurtResistantTime = Math.max(p.hurtResistantTime, DodgeMath.IFRAME_TICKS + p.maxHurtResistantTime / 2);
        p.addExhaustion(0.3f);
        p.world.playSound(null, p.posX, p.posY, p.posZ, SoundEvents.ENTITY_ENDERDRAGON_FLAP, SoundCategory.PLAYERS, 0.35f, 1.7f + p.getRNG().nextFloat() * 0.2f);
        Anim msg = new Anim(p.getEntityId(), dir);
        channel.sendToAllTracking(msg, p);
        channel.sendTo(msg, p);
    }

    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.player.world.isRemote && e.player.onGround) AIR_USED.remove(e.player.getUniqueID());
    }

    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST.remove(e.player.getUniqueID());
        AIR_USED.remove(e.player.getUniqueID());
    }

    public static final class Request implements IMessage {
        int dir;
        public Request() {}
        Request(int dir) { this.dir = dir; }
        @Override public void fromBytes(ByteBuf buf) { dir = buf.readByte(); }
        @Override public void toBytes(ByteBuf buf) { buf.writeByte(dir); }
    }

    public static final class RequestHandler implements IMessageHandler<Request, IMessage> {
        @Override public IMessage onMessage(Request m, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().player;
            p.getServerWorld().addScheduledTask(() -> perform(p, m.dir));
            return null;
        }
    }

    public static final class Anim implements IMessage {
        int entity, dir;
        public Anim() {}
        Anim(int entity, int dir) { this.entity = entity; this.dir = dir; }
        @Override public void fromBytes(ByteBuf buf) { entity = buf.readInt(); dir = buf.readByte(); }
        @Override public void toBytes(ByteBuf buf) { buf.writeInt(entity); buf.writeByte(dir); }
    }

    public static final class AnimHandler implements IMessageHandler<Anim, IMessage> {
        @Override public IMessage onMessage(Anim m, MessageContext ctx) {
            if (DodgeMath.valid(m.dir)) ValeOrders.proxy.dodgeAnim(m.entity, m.dir);
            return null;
        }
    }
}
