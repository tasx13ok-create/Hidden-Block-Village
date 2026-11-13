package village.vale;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;

/** Server-owned paid impulse. Ordinary native travel/collision and velocity tracking move the player; never teleport. */
public final class Dodge {
    private Dodge(){}
    private static SimpleNetworkWrapper channel;
    private static volatile ClientReceiver client;
    private static int cost=DodgeMath.DEFAULT_COST,cooldown=DodgeMath.COOLDOWN_TICKS;
    private static final Map<UUID,Attempt> BUSY=new HashMap<>();
    private static final Map<UUID,Window> WINDOWS=new HashMap<>();
    private static final Map<UUID,Rate> RATES=new ConcurrentHashMap<>();
    private static final class Rate {final EntityPlayerMP player;final long at;Rate(EntityPlayerMP p,long at){player=p;this.at=at;}}
    public interface ClientReceiver {void accept(Anim animation,Object connection);}
    public static void client(ClientReceiver receiver){client=receiver;}
    public static void configure(int chakraCost,int cooldownTicks){cost=Math.max(0,Math.min(25,chakraCost));cooldown=Math.max(8,Math.min(80,cooldownTicks));}
    public static void register(){if(channel!=null)return;channel=NetworkRegistry.INSTANCE.newSimpleChannel("valeorders_dodge");channel.registerMessage(RequestHandler.class,Request.class,0,Side.SERVER);channel.registerMessage(AnimHandler.class,Anim.class,1,Side.CLIENT);MinecraftForge.EVENT_BUS.register(new Dodge());}
    public static void request(int direction,int dimension){if(channel!=null&&DodgeMath.valid(direction))channel.sendToServer(new Request(direction,dimension));}
    static NBTTagCompound state(EntityPlayerMP p){NBTTagCompound account=PlayerState.data(p);if(!account.hasKey("Dodge")){NBTTagCompound value=new NBTTagCompound();value.setInteger("schema",1);account.setTag("Dodge",value);}return account.getCompoundTag("Dodge");}
    private static boolean basic(EntityPlayerMP p){return p!=null&&!p.world.isRemote&&p.world instanceof WorldServer&&!(p instanceof FakePlayer)&&p.isEntityAlive()&&!p.isSpectator()&&!p.isRiding()&&!p.isElytraFlying()&&!p.capabilities.isFlying&&!p.isPlayerSleeping()&&!p.noClip&&Float.isFinite(p.rotationYaw)&&Float.isFinite(p.rotationPitch)&&Double.isFinite(p.posX)&&Double.isFinite(p.posY)&&Double.isFinite(p.posZ)&&Double.isFinite(p.motionX)&&Double.isFinite(p.motionY)&&Double.isFinite(p.motionZ)&&p.getServer()!=null&&p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())==p;}
    private static boolean ground(EntityPlayerMP p){return p.onGround&&!p.world.getCollisionBoxes(null,p.getEntityBoundingBox().offset(0,-.08,0)).isEmpty();}
    private static final class Window {
        final EntityPlayerMP player;final World world;final int dimension;final NBTTagCompound account,state;final long sequence,started;
        Window(Attempt a){player=a.p;world=a.world;dimension=a.dimension;account=a.account;state=a.state;sequence=a.sequence;started=a.now;}
        boolean valid(){return basic(player)&&player.world==world&&player.dimension==dimension&&PlayerState.data(player)==account&&Dodge.state(player)==state&&state.getInteger("schema")==1&&state.getLong("sequence")==sequence;}
    }
    private static final class Attempt {
        final EntityPlayerMP p;final World world;final int dimension;final NBTTagCompound account,state;final long now,oldReady,oldSequence,sequence,reservedReady;final boolean oldAir;final int paid;
        final double x,y,z,mx,my,mz;final float yaw;boolean committed;
        Attempt(EntityPlayerMP p,NBTTagCompound state,long now){this.p=p;world=p.world;dimension=p.dimension;account=PlayerState.data(p);this.state=state;this.now=now;oldReady=state.getLong("readyAt");oldSequence=state.getLong("sequence");sequence=oldSequence+1;reservedReady=now+cooldown;oldAir=state.getBoolean("airUsed");paid=cost;x=p.posX;y=p.posY;z=p.posZ;mx=p.motionX;my=p.motionY;mz=p.motionZ;yaw=p.rotationYaw;}
        boolean valid(){return basic(p)&&p.world==world&&p.dimension==dimension&&PlayerState.data(p)==account&&Dodge.state(p)==state&&state.getInteger("schema")==1&&state.getLong("readyAt")==reservedReady&&state.getLong("sequence")==sequence&&p.posX==x&&p.posY==y&&p.posZ==z&&p.motionX==mx&&p.motionY==my&&p.motionZ==mz&&p.rotationYaw==yaw;}
        void refund(){if(committed||PlayerState.data(p)!=account||Dodge.state(p)!=state)return;PlayerState.setResolve(p,Math.min(100,PlayerState.resolve(p)+paid));if(state.getLong("readyAt")==reservedReady)state.setLong("readyAt",oldReady);if(state.getLong("sequence")==sequence)state.setLong("sequence",oldSequence);state.setBoolean("airUsed",oldAir);Adventures.save(p);}
    }
    /** Read-only displacement permits protection mods to refuse this action before any impulse/grace. */
    @Cancelable public static final class Pre extends net.minecraftforge.event.entity.player.PlayerEvent {
        public final int direction;public final double motionX,motionY,motionZ;
        Pre(EntityPlayerMP p,int direction,double x,double y,double z){super(p);this.direction=direction;motionX=x;motionY=y;motionZ=z;}
    }
    static boolean perform(EntityPlayerMP p,int direction){
        if(!DodgeMath.valid(direction)||!basic(p)||BUSY.containsKey(p.getUniqueID())||BUSY.size()>=256)return false;
        NBTTagCompound value=state(p);long now=Adventures.now(p);if(value.getInteger("schema")!=1||now<0||now<value.getLong("readyAt")||value.getLong("sequence")<0||value.getLong("sequence")==Long.MAX_VALUE||PlayerState.resolve(p)<cost)return false;
        Attempt attempt=new Attempt(p,value,now);BUSY.put(p.getUniqueID(),attempt);value.setLong("readyAt",attempt.reservedReady);value.setLong("sequence",attempt.sequence);PlayerState.setResolve(p,PlayerState.resolve(p)-attempt.paid);
        try{
            Adventures.save(p);if(!attempt.valid())return false;boolean grounded=ground(p);if(!attempt.valid()||!DodgeMath.allowed(true,false,now,attempt.oldReady,grounded,attempt.oldAir))return false;
            double[] base=DodgeMath.velocity(direction,p.rotationYaw);double keep=grounded?.2:.4;
            double dx=base[0]+DodgeMath.retain(p.motionX,keep),dy=base[1]+DodgeMath.retain(Math.max(-.6,p.motionY),.3),dz=base[2]+DodgeMath.retain(p.motionZ,keep);
            AxisAlignedBB box=p.getEntityBoundingBox(),sweep=box.expand(dx,dy,dz);
            if(!p.world.getWorldBorder().contains(sweep)||!p.world.isAreaLoaded(new BlockPos(sweep.minX,sweep.minY,sweep.minZ),new BlockPos(sweep.maxX,sweep.maxY,sweep.maxZ)))return false;
            List<AxisAlignedBB> collisions=p.world.getCollisionBoxes(p,sweep);if(!attempt.valid())return false;
            for(AxisAlignedBB obstacle:collisions)dy=obstacle.calculateYOffset(box,dy);box=box.offset(0,dy,0);
            for(AxisAlignedBB obstacle:collisions)dx=obstacle.calculateXOffset(box,dx);box=box.offset(dx,0,0);
            for(AxisAlignedBB obstacle:collisions)dz=obstacle.calculateZOffset(box,dz);
            if(dx*dx+dz*dz<.0225)return false;
            Pre event=new Pre(p,direction,dx,dy,dz);MinecraftForge.EVENT_BUS.post(event);if(event.isCanceled()||!attempt.valid())return false;
            if(WINDOWS.size()>=256&&!WINDOWS.containsKey(p.getUniqueID()))return false;
            value.setBoolean("airUsed",!grounded);attempt.committed=true;p.motionX=dx;p.motionY=dy;p.motionZ=dz;p.velocityChanged=true;WINDOWS.put(p.getUniqueID(),new Window(attempt));
            // Native tracker sends velocity to the owner and observers. No direct position write, no fall reset.
            Adventures.save(p);
            try{p.world.playSound(null,p.posX,p.posY,p.posZ,SoundEvents.ENTITY_ENDERDRAGON_FLAP,SoundCategory.PLAYERS,.25f,1.85f);((WorldServer)p.world).spawnParticle(EnumParticleTypes.CLOUD,p.posX,p.posY+.1,p.posZ,6,.25,.05,.25,.02);WorldReactions.power(p,"dodge",1);}catch(RuntimeException cosmetic){warn(cosmetic);}
            if(channel!=null)try{Anim animation=new Anim(p,direction,attempt.sequence,cooldown);channel.sendToAllTracking(animation,p);channel.sendTo(animation,p);}catch(RuntimeException cosmetic){warn(cosmetic);}
            return true;
        }catch(RuntimeException failed){warn(failed);return attempt.committed;}
        finally{try{attempt.refund();}finally{BUSY.remove(p.getUniqueID(),attempt);}}
    }
    private static void warn(RuntimeException error){org.apache.logging.log4j.LogManager.getLogger("ValeDodge").warn("Dodge callback failed; accepted movement remains charged",error);}
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent event){if(event.phase!=TickEvent.Phase.END||!(event.player instanceof EntityPlayerMP)||event.player.world.isRemote)return;EntityPlayerMP p=(EntityPlayerMP)event.player;Window w=WINDOWS.get(p.getUniqueID());long now=Adventures.now(p);if(w!=null&&(!w.valid()||now<w.started||now-w.started>DodgeMath.ANIM_TICKS))WINDOWS.remove(p.getUniqueID(),w);NBTTagCompound value=state(p);if(basic(p)&&value.getInteger("schema")==1&&value.getBoolean("airUsed")&&ground(p)){value.setBoolean("airUsed",false);Adventures.save(p);}}
    @SubscribeEvent(priority=EventPriority.HIGH)public void grace(LivingAttackEvent event){if(!(event.getEntityLiving() instanceof EntityPlayerMP)||event.isCanceled())return;EntityPlayerMP p=(EntityPlayerMP)event.getEntityLiving();Window w=WINDOWS.get(p.getUniqueID());if(w==null||!w.valid())return;long age=Adventures.now(p)-w.started;if(age<0||age>=DodgeMath.GRACE_TICKS)return;DamageSource source=event.getSource();Entity immediate=source.getImmediateSource();if(source.isUnblockable()||source.isDamageAbsolute()||source.isFireDamage()||source.isMagicDamage()||immediate==null||immediate==p||immediate.world!=p.world||p.getDistanceSq(immediate)>25)return;if(immediate instanceof EntityLivingBase||source.isProjectile())event.setCanceled(true);}
    private static void forget(EntityPlayerMP p){Window w=WINDOWS.get(p.getUniqueID());if(w!=null&&w.player==p)WINDOWS.remove(p.getUniqueID(),w);Rate rate=RATES.get(p.getUniqueID());if(rate!=null&&rate.player==p)RATES.remove(p.getUniqueID(),rate);}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.player instanceof EntityPlayerMP)forget((EntityPlayerMP)event.player);}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.player instanceof EntityPlayerMP)forget((EntityPlayerMP)event.player);}
    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.getWorld().isRemote)return;for(Window w:new ArrayList<>(WINDOWS.values()))if(w.world==event.getWorld())forget(w.player);}
    public static final class Request implements IMessage {
        private int direction,dimension;public Request(){}Request(int direction,int dimension){this.direction=direction;this.dimension=dimension;}
        @Override public void fromBytes(ByteBuf b){if(b.readableBytes()!=6||b.readUnsignedByte()!=1)throw new DecoderException("Invalid dodge request size/version");direction=b.readUnsignedByte();dimension=b.readInt();if(!DodgeMath.valid(direction))throw new DecoderException("Invalid dodge direction");}
        @Override public void toBytes(ByteBuf b){if(!DodgeMath.valid(direction))throw new IllegalArgumentException("Invalid dodge direction");b.writeByte(1);b.writeByte(direction);b.writeInt(dimension);}
    }
    public static final class RequestHandler implements IMessageHandler<Request,IMessage> {
        @Override public IMessage onMessage(Request message,MessageContext context){if(context.side!=Side.SERVER)return null;EntityPlayerMP p=context.getServerHandler().player;long now=System.nanoTime();Rate previous=RATES.get(p.getUniqueID());if(previous!=null&&previous.player==p&&now-previous.at<50000000L)return null;if(RATES.size()>=256&&previous==null)return null;RATES.put(p.getUniqueID(),new Rate(p,now));p.getServerWorld().addScheduledTask(()->{if(p.dimension==message.dimension&&basic(p))perform(p,message.direction);});return null;}
    }
    public static final class Anim implements IMessage {
        private int entity,dimension,direction,cooldown;private UUID owner;private long sequence;public Anim(){}
        Anim(EntityPlayerMP p,int direction,long sequence,int cooldown){entity=p.getEntityId();dimension=p.dimension;owner=p.getUniqueID();this.direction=direction;this.sequence=sequence;this.cooldown=cooldown;}
        public int entity(){return entity;}public int dimension(){return dimension;}public int direction(){return direction;}public int cooldown(){return cooldown;}public UUID owner(){return owner;}public long sequence(){return sequence;}
        @Override public void fromBytes(ByteBuf b){if(b.readableBytes()!=35||b.readUnsignedByte()!=1)throw new DecoderException("Invalid dodge animation size/version");entity=b.readInt();dimension=b.readInt();owner=new UUID(b.readLong(),b.readLong());direction=b.readUnsignedByte();cooldown=b.readUnsignedByte();sequence=b.readLong();if(entity<0||!DodgeMath.valid(direction)||cooldown<8||cooldown>80||sequence<1)throw new DecoderException("Invalid dodge animation");}
        @Override public void toBytes(ByteBuf b){b.writeByte(1);b.writeInt(entity);b.writeInt(dimension);b.writeLong(owner.getMostSignificantBits());b.writeLong(owner.getLeastSignificantBits());b.writeByte(direction);b.writeByte(cooldown);b.writeLong(sequence);}
    }
    public static final class AnimHandler implements IMessageHandler<Anim,IMessage>{@Override public IMessage onMessage(Anim message,MessageContext context){if(context.side==Side.CLIENT&&client!=null)client.accept(message,context.netHandler);return null;}}
}
