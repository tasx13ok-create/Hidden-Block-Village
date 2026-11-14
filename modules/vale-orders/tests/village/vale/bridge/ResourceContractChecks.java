package village.vale.bridge;

/** Original fake public API; exercises production reflection/escrow, never external mod code. */
public final class ResourceContractChecks {
    private static int assertions;
    private static void check(boolean yes,String why){assertions++;if(!yes)throw new AssertionError(why);}
    public static class Actor { boolean ninja=true; }
    public static class Pool {
        double value=100,max=100,persisted=100;int doubles,floats,clears,syncs;boolean throwAfter,lie;Runnable callback;
        public double getAmount(){return value;}
        public double getMax(){return max;}
        protected void set(double value){this.value=value;persisted=value;syncs++;if(callback!=null)callback.run();if(throwAfter){throwAfter=false;throw new IllegalStateException("after actual mutation");}}
        public boolean consume(double cost,boolean overflow){doubles++;double next=value-cost;if(next>max&&!overflow)next=cost>0?next:value>max?value:max;if(next<=0)next=value;double old=value;set(next);return !lie&&old!=value;}
        public void consume(float fraction){floats++;throw new AssertionError("FLOAT overload selected");}
        public void clear(){clears++;set(0);}
    }
    public static final class ApiRoot {static Pool pool;static int paths;public static Pool pathway(Actor actor){paths++;return pool;}}
    public static final class Tracker {public static boolean isNinja(Actor actor){return actor.ninja;}}
    public static final class FloatOnly {public double getAmount(){return 100;}public double getMax(){return 100;}public void consume(float f){}public void clear(){}}
    public static final class FloatRoot {public static FloatOnly pathway(Actor actor){return null;}}
    public static final class WrongResult {public double getAmount(){return 100;}public double getMax(){return 100;}public void consume(double d,boolean b){}public void clear(){}}
    public static final class WrongRoot {public static WrongResult pathway(Actor actor){return null;}}
    private static ResourceEscrow.Account account(Pool pool)throws Exception{ApiRoot.pool=pool;return new ChakraLink.Api(ApiRoot.class,Pool.class,Tracker.class,Actor.class).account(new Actor());}
    private static ResourceEscrow.Debit reserve(Pool pool,double cost)throws Exception{ResourceEscrow.Result r=ResourceEscrow.reserve(account(pool),cost,()->true);check(r.debit!=null,"Reservation refused: "+r.error);return r.debit;}
    @SuppressWarnings("unchecked") private static void mutableOwnerCleanup()throws Exception{
        // Allocate an inert actual player/Ticket solely to execute settlement cleanup.
        // No constructor bypass is used by production and this body has no world, network or server authority.
        Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");java.lang.reflect.Field unsafeField=unsafeClass.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
        Object unsafe=unsafeField.get(null);java.lang.reflect.Method allocate=unsafeClass.getMethod("allocateInstance",Class.class);
        net.minecraft.entity.player.EntityPlayerMP player=(net.minecraft.entity.player.EntityPlayerMP)allocate.invoke(unsafe,net.minecraft.entity.player.EntityPlayerMP.class);
        java.util.UUID owner=java.util.UUID.randomUUID();player.setUniqueId(owner);
        java.lang.reflect.Field activeField=MoveCost.class.getDeclaredField("ACTIVE");activeField.setAccessible(true);
        java.util.Map<java.util.UUID,MoveCost.Ticket> active=(java.util.Map<java.util.UUID,MoveCost.Ticket>)activeField.get(null);
        check(active.isEmpty(),"Component cleanup fixture started with unrelated reservations");
        try{
            for(boolean refund:new boolean[]{false,true}){
                MoveCost.Ticket ticket=(MoveCost.Ticket)allocate.invoke(unsafe,MoveCost.Ticket.class);
                for(String name:new String[]{"player","owner","ready"}){
                    java.lang.reflect.Field field=MoveCost.Ticket.class.getDeclaredField(name);field.setAccessible(true);
                    field.set(ticket,name.equals("player")?player:name.equals("owner")?owner:Boolean.TRUE);
                }
                active.put(owner,ticket);player.setUniqueId(java.util.UUID.randomUUID());
                check(!(refund?ticket.refund():ticket.commit()),"Mutated UUID retained valid settlement context");
                check(!active.containsKey(owner)&&!ticket.pending(),"Mutated UUID leaked original owner reservation");
                player.setUniqueId(owner);
                check(active.putIfAbsent(owner,ticket)==null,"Restored UUID could not acquire its original reservation slot");
                active.remove(owner,ticket);
            }
        }finally{active.clear();}
    }
    public static void main(String[] args)throws Exception{
        Pool p=new Pool();ResourceEscrow.Debit d=reserve(p,6);check(p.value==94&&p.persisted==94&&p.doubles==1&&p.floats==0,"Exact absolute double route/persistence");check(d.amount==6,"Actual debit");check(d.commit()&&!d.commit()&&!d.refund(),"Commit was not final");check(p.value==94,"Committed action refunded");
        p=new Pool();d=reserve(p,100);check(p.value==0&&p.clears==1&&p.doubles==0,"Exact-zero did not use public clear");check(d.refund()&&p.value==100&&d.refunded()==100,"Exact-zero rollback");check(!d.refund()&&p.value==100,"Repeated refund minted");
        p=new Pool();d=reserve(p,0);check(p.value==100&&p.doubles==0&&p.clears==0,"Zero cost mutated provider");check(d.refund(),"Zero-cost receipt refused");
        p=new Pool();p.value=p.persisted=115;d=reserve(p,20);check(p.value==95,"Overcap debit");check(d.refund()&&p.value==115,"Legitimate overcap was lost");
        p=new Pool();p.value=p.persisted=1000;p.max=1000;d=reserve(p,30);p.consume(50,false);check(p.value==920,"External spending fixture");check(d.refund()&&p.value==950,"Refund erased independent spending");
        p=new Pool();p.value=p.persisted=50;d=reserve(p,10);p.consume(-5,false);check(d.refund()&&p.value==55,"Refund erased independent gain");
        for(double invalid:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,101}){p=new Pool();ResourceEscrow.Result r=ResourceEscrow.reserve(account(p),invalid,()->true);check(r.debit==null&&p.value==100&&p.doubles==0&&p.clears==0,"Invalid/unaffordable cost touched pool");}
        p=new Pool();ResourceEscrow.Result tiny=ResourceEscrow.reserve(account(p),Double.MIN_VALUE,()->true);check(tiny.debit==null&&p.doubles==0,"Subprecision debit granted a free paid action");
        p=new Pool();p.throwAfter=true;ResourceEscrow.Result thrown=ResourceEscrow.reserve(account(p),20,()->true);check(thrown.debit==null&&p.value==100,"Throw-after-debit was not boundedly undone");
        p=new Pool();p.throwAfter=true;thrown=ResourceEscrow.reserve(account(p),100,()->true);check(thrown.debit==null&&p.value==100,"Throw-after-clear minted/lost resource");
        p=new Pool();p.lie=true;ResourceEscrow.Result lied=ResourceEscrow.reserve(account(p),20,()->true);check(lied.debit==null&&p.value==100,"False-but-mutated provider granted action");
        final boolean[] body={true};p=new Pool();p.callback=()->body[0]=false;ResourceEscrow.Result stale=ResourceEscrow.reserve(account(p),20,()->body[0]);check(stale.debit==null&&p.value==80,"Stale body was credited or action granted");
        p=new Pool();body[0]=true;ResourceEscrow.Result live=ResourceEscrow.reserve(account(p),20,()->body[0]);body[0]=false;check(!live.debit.refund()&&p.value==80,"Refund mutated a replacement account");check(!live.debit.refund(),"Invalidated receipt replayed");
        p=new Pool();p.value=90;double gain=ResourceEscrow.gain(account(p),30,()->true);check(gain==10&&p.value==100,"Trusted gain exceeded max");
        p=new Pool();p.value=115;gain=ResourceEscrow.gain(account(p),30,()->true);check(gain==0&&p.value==115,"Gain clobbered existing overcap");
        p=new Pool();p.value=50;p.throwAfter=true;gain=ResourceEscrow.gain(account(p),10,()->true);check(gain==10&&p.value==60,"Throw-after-gain became a reusable free tonic");
        p=new Pool();d=reserve(p,10);p.throwAfter=true;check(d.refund()&&d.refunded()==10&&p.value==100&&!d.refund(),"Throw-after-refund could replay");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1}){p=new Pool();p.value=bad;ResourceEscrow.Result invalid=ResourceEscrow.reserve(account(p),1,()->true);check(invalid.debit==null&&p.doubles==0,"Bad native amount debited");}
        p=new Pool();p.max=0;check(ResourceEscrow.reserve(account(p),1,()->true).debit==null,"Unready max accepted");
        try{new ChakraLink.Api(FloatRoot.class,FloatOnly.class,Tracker.class,Actor.class);throw new AssertionError("Float-only API accepted");}catch(NoSuchMethodException expected){assertions++;}
        try{new ChakraLink.Api(WrongRoot.class,WrongResult.class,Tracker.class,Actor.class);throw new AssertionError("Wrong return type accepted");}catch(NoSuchMethodException expected){assertions++;}
        ChakraLink.Api api=new ChakraLink.Api(ApiRoot.class,Pool.class,Tracker.class,Actor.class);Actor actor=new Actor();actor.ninja=false;int paths=ApiRoot.paths;check(!api.ninja(actor)&&ApiRoot.paths==paths,"Readiness created a non-ninja pathway");
        p=new Pool();final boolean[] valid={true};d=ResourceEscrow.reserve(account(p),10,()->valid[0]).debit;valid[0]=false;check(!d.commit()&&p.value==90&&!d.refund(),"Invalid commit became refundable on a later body");
        // Captured legacy NBT rollback is a separate explicit path. It never relaxes native/API refunds.
        net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();saved.setInteger("resolve",100);
        net.minecraft.nbt.NBTTagCompound replacement=new net.minecraft.nbt.NBTTagCompound();replacement.setInteger("resolve",23);
        MoveCost.LegacyAccount legacy=new MoveCost.LegacyAccount(null,saved);
        final boolean[] attached={true},session={true};
        ResourceEscrow.Debit legacyDebit=ResourceEscrow.reserveCapturedLegacy(legacy,16,()->attached[0]&&session[0],()->session[0]).debit;
        check(legacyDebit!=null&&saved.getInteger("resolve")==84,"Captured legacy debit did not target original NBT");attached[0]=false;
        check(legacyDebit.refund()&&saved.getInteger("resolve")==100&&replacement.getInteger("resolve")==23,"Detached legacy rollback changed replacement or left original debit");
        check(!legacyDebit.refund()&&saved.getInteger("resolve")==100,"Detached legacy rollback replay minted resource");
        saved.setInteger("resolve",100);attached[0]=true;
        legacyDebit=ResourceEscrow.reserveCapturedLegacy(legacy,16,()->attached[0]&&session[0],()->session[0]).debit;
        saved.setInteger("resolve",77);replacement.setInteger("resolve",19);attached[0]=false;
        check(legacyDebit.refund()&&saved.getInteger("resolve")==93&&replacement.getInteger("resolve")==19,"Detached refund overwrote independent seven-unit debit");
        saved.setInteger("resolve",100);attached[0]=true;
        legacyDebit=ResourceEscrow.reserveCapturedLegacy(legacy,16,()->attached[0]&&session[0],()->session[0]).debit;
        saved.setInteger("resolve",89);attached[0]=false;
        check(legacyDebit.refund()&&saved.getInteger("resolve")==100&&legacyDebit.refunded()==11,"Detached gain plus refund exceeded legacy cap");
        saved.setInteger("resolve",100);attached[0]=true;
        legacyDebit=ResourceEscrow.reserveCapturedLegacy(legacy,100,()->attached[0]&&session[0],()->session[0]).debit;attached[0]=false;
        check(saved.getInteger("resolve")==0&&legacyDebit.refund()&&saved.getInteger("resolve")==100,"Detached exact-zero legacy rollback failed");
        saved.setInteger("resolve",100);attached[0]=true;
        legacyDebit=ResourceEscrow.reserveCapturedLegacy(legacy,16,()->attached[0]&&session[0],()->session[0]).debit;attached[0]=false;session[0]=false;
        check(!legacyDebit.refund()&&saved.getInteger("resolve")==84&&replacement.getInteger("resolve")==19,"Detached legacy refund crossed session epoch");session[0]=true;
        check(!legacyDebit.refund()&&saved.getInteger("resolve")==84,"Rejected stale legacy receipt revived");
        saved.setInteger("resolve",100);attached[0]=true;
        legacyDebit=ResourceEscrow.reserveCapturedLegacy(legacy,16,()->attached[0]&&session[0],()->session[0]).debit;attached[0]=false;
        check(!legacyDebit.commit()&&!legacyDebit.refund()&&saved.getInteger("resolve")==84,"Detached commit became a later free action");
        saved.setInteger("resolve",100);ResourceEscrow.Result strictLegacy=ResourceEscrow.reserve(legacy,16,()->attached[0]);
        check(strictLegacy.debit==null&&saved.getInteger("resolve")==100,"Detached account acquired new reservation authority");
        mutableOwnerCleanup();
        check(!MoveCost.busy(null),"Null body reported a resource operation");
        System.out.println("RESOURCE_CONTRACT_PASS: "+assertions);
    }
}
