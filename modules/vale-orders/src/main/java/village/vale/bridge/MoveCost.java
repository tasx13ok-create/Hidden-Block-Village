package village.vale.bridge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound;
import village.vale.HudNetwork;
import village.vale.Rules;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import village.vale.PlayerState;

/** One synchronous server-owned resource reservation; original casts are never charged twice. */
@Mod.EventBusSubscriber(modid="valeorders")
public final class MoveCost {
    private static final Map<UUID,Ticket> ACTIVE=new HashMap<>();
    private static final Map<UUID,EntityPlayerMP> READING=new HashMap<>();
    // Values never reference their weak body key. Magma may reuse that key across respawn.
    private static final Map<EntityPlayerMP,Object> SESSIONS=new WeakHashMap<>();
    private static final int CAP=256;
    private static volatile double scale=1;
    private MoveCost(){}
    public static void configure(double chakraPerResolve){if(!Double.isFinite(chakraPerResolve)||chakraPerResolve<.01||chakraPerResolve>100)throw new IllegalArgumentException("Chakra cost scale must be finite .01..100");scale=chakraPerResolve;}
    public static double scale(){return scale;}
    public static boolean external(){return ChakraLink.installed();}
    static boolean current(EntityPlayerMP p){
        return p!=null&&!(p instanceof FakePlayer)&&p.world!=null&&!p.world.isRemote&&p.isEntityAlive()&&!p.isSpectator()&&p.connection!=null&&p.getServer()!=null&&p.getServer().isCallingFromMinecraftThread()&&p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())==p;
    }
    /** Read-only current-body guard; does not probe/mutate either resource provider. */
    public static boolean busy(EntityPlayerMP p){
        if(!current(p))return false;
        Ticket held=ACTIVE.get(p.getUniqueID());
        return held!=null&&held.player==p||READING.get(p.getUniqueID())==p;
    }
    private static long now(EntityPlayerMP p){return p.getServer().getWorld(0).getTotalWorldTime();}
    private static Object session(EntityPlayerMP p){Object value=SESSIONS.get(p);if(value==null){value=new Object();SESSIONS.put(p,value);}return value;}
    private static boolean serverContext(EntityPlayer player){return player instanceof EntityPlayerMP&&!(player instanceof FakePlayer)&&player.world!=null&&!player.world.isRemote&&player.getServer()!=null&&player.getServer().isCallingFromMinecraftThread();}
    private static void invalidate(EntityPlayer player){
        if(!serverContext(player))return;
        EntityPlayerMP p=(EntityPlayerMP)player;SESSIONS.remove(p);
        // UUID is mutable during third-party callbacks; release by captured key/body identity.
        for(Ticket held:new java.util.ArrayList<>(ACTIVE.values()))if(held.player==p){held.closed=true;ACTIVE.remove(held.owner,held);}
        READING.entrySet().removeIf(entry->entry.getValue()==p);
    }
    @SubscribeEvent public static void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone e){invalidate(e.getOriginal());invalidate(e.getEntityPlayer());}
    @SubscribeEvent public static void login(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent e){invalidate(e.player);}
    @SubscribeEvent public static void logout(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent e){invalidate(e.player);}
    @SubscribeEvent public static void respawn(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent e){invalidate(e.player);}
    @SubscribeEvent public static void dimension(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent e){invalidate(e.player);}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void death(LivingDeathEvent e){
        if(!serverContext(e.getEntityLiving() instanceof EntityPlayer?(EntityPlayer)e.getEntityLiving():null))return;
        EntityPlayerMP p=(EntityPlayerMP)e.getEntityLiving();
        // Keep the final event reference; another LOWEST listener may still cancel death.
        for(Ticket held:new java.util.ArrayList<>(ACTIVE.values()))if(held.player==p)held.death=e;
    }
    private static LegacyAccount legacy(final EntityPlayerMP p){return new LegacyAccount(p,PlayerState.data(p));}
    /** Own original NBT object: detaching player state must never redirect a rollback to its replacement. */
    static final class LegacyAccount implements ResourceEscrow.CapturedLegacyAccount {
        private final EntityPlayerMP player;private final NBTTagCompound data;
        LegacyAccount(EntityPlayerMP player,NBTTagCompound data){this.player=player;this.data=data;}
        public Object identity(){return data;}
        public double amount(){return Rules.clampResolve(data.getInteger("resolve"));}
        public double maximum(){return 100;}
        private void set(int value){data.setInteger("resolve",Rules.clampResolve(value));if(player!=null)HudNetwork.dirty(player);}
        public boolean consume(double cost,boolean overcap){int before=(int)amount();if(!Double.isFinite(cost)||cost!=Math.rint(cost)||cost>before)return false;set((int)Math.max(0,Math.min(100,before-cost)));return before!=amount();}
        public void clear(){set(0);}
    }
    public static ChakraLink.View view(EntityPlayer player){
        if(!(player instanceof EntityPlayerMP)||!current((EntityPlayerMP)player))return new ChakraLink.View(external()?"narutomod":"resolve",ChakraLink.State.WRONG_CONTEXT,Double.NaN,Double.NaN,"Current server player required");
        EntityPlayerMP p=(EntityPlayerMP)player;UUID id=p.getUniqueID();
        if(READING.size()>=CAP||READING.containsKey(id))return new ChakraLink.View(external()?"narutomod":"resolve",ChakraLink.State.ERROR,Double.NaN,Double.NaN,"Resource read reentry refused");
        READING.put(id,p);
        try{
            if(external())return ChakraLink.open(p).view;
            return new ChakraLink.View("resolve",ChakraLink.State.READY,PlayerState.resolve(p),100,"Native mod absent; saved resolve");
        }finally{READING.remove(id,p);}
    }
    public static int availableUnits(EntityPlayer player){
        ChakraLink.View value=view(player);if(!value.ready())return 0;
        double units=value.amount/("narutomod".equals(value.provider)?scale:1);
        return units>=Integer.MAX_VALUE?Integer.MAX_VALUE:units<=0?0:(int)Math.floor(units);
    }
    public static Ticket reserve(EntityPlayerMP p,int units){
        if(units<0||!current(p)||READING.containsKey(p.getUniqueID())||ACTIVE.containsKey(p.getUniqueID())||ACTIVE.size()>=CAP)return null;
        Ticket ticket=new Ticket(p,units,scale);ACTIVE.put(ticket.owner,ticket);
        boolean retained=false;
        try{
            if(units==0){ticket.provider="free";ticket.ready=true;retained=true;return ticket;}
            if(external()){
                ChakraLink.Open opened=ChakraLink.open(p);if(!opened.view.ready())return null;
                ticket.account=opened.account;ticket.provider="narutomod";
            }else{ticket.account=legacy(p);ticket.provider="resolve";}
            double raw=units*("narutomod".equals(ticket.provider)?ticket.costScale:1);
            ResourceEscrow.Result result=ticket.account instanceof LegacyAccount
                ?ResourceEscrow.reserveCapturedLegacy((LegacyAccount)ticket.account,raw,ticket::context,ticket::legacyRefundContext)
                :ResourceEscrow.reserve(ticket.account,raw,ticket::context);
            if(result.debit==null)return null;
            ticket.payment=result.debit;ticket.ready=true;retained=true;return ticket;
        }catch(RuntimeException|LinkageError failure){return null;}
        finally{if(!retained){ticket.closed=true;ACTIVE.remove(ticket.owner,ticket);}}
    }
    /** Compatibility for irrevocable immediate actions only. Refundable callers need reserve/try-with-resources. */
    public static boolean pay(EntityPlayer p,int units){if(!(p instanceof EntityPlayerMP))return false;Ticket ticket=reserve((EntityPlayerMP)p,units);return ticket!=null&&ticket.commit();}
    /** Trusted server gain, capped at provider max. Returns actual credited Vale units (possibly fractional), or0. */
    public static double restore(EntityPlayerMP p,int units){
        if(units<=0||!current(p)||READING.containsKey(p.getUniqueID()))return 0;
        Ticket ticket=ACTIVE.get(p.getUniqueID());boolean own=ticket==null;
        if(own){if(ACTIVE.size()>=CAP)return 0;ticket=new Ticket(p,0,scale);ACTIVE.put(ticket.owner,ticket);}
        final Ticket chosen=ticket;
        if(!own&&(!chosen.ready||chosen.closed||chosen.gainBusy))return 0;
        chosen.gainBusy=true;
        try{
            if(own){
                if(external()){ChakraLink.Open opened=ChakraLink.open(p);if(!opened.view.ready())return 0;chosen.account=opened.account;chosen.provider="narutomod";}
                else{chosen.account=legacy(p);chosen.provider="resolve";}
                chosen.ready=true;
            }
            double factor="narutomod".equals(chosen.provider)?chosen.costScale:1;
            return ResourceEscrow.gain(chosen.account,units*factor,chosen::context)/factor;
        }catch(RuntimeException|LinkageError failure){return 0;}
        finally{chosen.gainBusy=false;if(own){chosen.closed=true;ACTIVE.remove(chosen.owner,chosen);}}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        MinecraftServer currentServer=FMLCommonHandler.instance().getMinecraftServerInstance();
        if(event.phase!=TickEvent.Phase.END||currentServer==null||!currentServer.isCallingFromMinecraftThread())return;
        // Tickets are synchronous. A leaked callback must not retain a body or
        // resource lock forever, or become refundable against a later account.
        for(Ticket ticket:new java.util.ArrayList<>(ACTIVE.values())){
            if(ticket.death!=null&&!ticket.death.isCanceled()&&(ticket.player.isDead||ticket.player.getHealth()<=0)){invalidate(ticket.player);continue;}
            ticket.death=null;
            if(!current(ticket.player)||ticket.player.world!=ticket.world||ticket.player.dimension!=ticket.dimension||now(ticket.player)<ticket.at||now(ticket.player)-ticket.at>100){ticket.closed=true;ACTIVE.remove(ticket.owner,ticket);}
        }
    }
    public static final class Ticket implements AutoCloseable {
        private final EntityPlayerMP player;private final UUID owner;private final MinecraftServer server;private final World world;private final int dimension,units;private final Object connection,session;private final long at;private final double costScale;
        private LivingDeathEvent death;
        private ResourceEscrow.Account account;private ResourceEscrow.Debit payment;private String provider="pending";private boolean ready,closed,gainBusy,checking;
        Ticket(EntityPlayerMP player,int units,double scale){this.player=player;owner=player.getUniqueID();server=player.getServer();world=player.world;dimension=player.dimension;connection=player.connection;session=session(player);this.units=units;costScale=scale;at=now(player);}
        private boolean bodyContext(){return !checking&&owner.equals(player.getUniqueID())&&SESSIONS.get(player)==session&&current(player)&&player.getServer()==server&&player.world==world&&player.dimension==dimension&&player.connection==connection&&ACTIVE.get(owner)==this;}
        private boolean legacyRefundContext(){return account instanceof LegacyAccount&&"resolve".equals(provider)&&!external()&&bodyContext();}
        private boolean context(){
            if(!bodyContext())return false;
            if(account==null)return units==0;
            checking=true;
            try{return "narutomod".equals(provider)?external()&&ChakraLink.same(player,account):!external()&&PlayerState.data(player)==account.identity();}finally{checking=false;}
        }
        public String provider(){return provider;}
        public int units(){return units;}
        public double debit(){return payment==null?0:payment.amount;}
        public double refunded(){return payment==null?0:payment.refunded();}
        public boolean pending(){return ready&&!closed;}
        public boolean valid(){return pending()&&context();}
        public boolean commit(){if(!pending())return false;closed=true;try{return payment==null?context():payment.commit();}finally{ACTIVE.remove(owner,this);}}
        public boolean refund(){if(!pending())return false;closed=true;try{return payment==null?context():payment.refund();}finally{ACTIVE.remove(owner,this);}}
        @Override public void close(){if(pending())refund();}
    }
}
