package village.vale.bridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Loader;

/** Optional public Naruto API link. Presence, readiness and failure stay distinct. */
public final class ChakraLink {
    public enum State { READY, ABSENT, UNREADY, UNSUPPORTED, WRONG_CONTEXT, ERROR }
    public static final class View {
        public final String provider;public final State state,status;public final double amount,maximum,max;public final String detail;
        View(String provider,State state,double amount,double maximum,String detail){this.provider=provider;this.state=this.status=state;this.amount=amount;this.maximum=this.max=maximum;this.detail=detail;}
        public boolean ready(){return state==State.READY;}
    }
    static final class Open {
        final ResourceEscrow.Account account;final View view;
        Open(ResourceEscrow.Account account,View view){this.account=account;this.view=view;}
    }
    private static Api api;private static boolean attempted;
    public static volatile String status="not checked";
    private ChakraLink(){}
    /** Installed includes unsupported/unready; callers must never fall back on a read failure. */
    public static boolean installed(){try{return Loader.isModLoaded("narutomod");}catch(RuntimeException|LinkageError error){status="Provider presence check failed";return true;}}
    private static synchronized Api api(){
        if(!installed())return null;
        if(!attempted){
            attempted=true;
            try{
                ClassLoader loader=ChakraLink.class.getClassLoader();
                api=new Api(Class.forName("net.narutomod.Chakra",false,loader),Class.forName("net.narutomod.Chakra$Pathway",false,loader),Class.forName("net.narutomod.PlayerTracker",false,loader),EntityPlayer.class);
                status="Base Naruto public double/clear API linked";
            }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){status="Installed Naruto public API is unsupported";}
        }
        return api;
    }
    public static boolean available(){return installed()&&api()!=null;}
    public static boolean canSpend(){return available();}
    static Open open(EntityPlayerMP player){
        if(!installed())return new Open(null,new View("none",State.ABSENT,Double.NaN,Double.NaN,"NarutoMod absent"));
        Api methods=api();
        if(methods==null)return new Open(null,new View("narutomod",State.UNSUPPORTED,Double.NaN,Double.NaN,status));
        try{
            if(!methods.ninja(player))return new Open(null,new View("narutomod",State.UNREADY,Double.NaN,Double.NaN,"Native ninja account is not ready"));
            ResourceEscrow.Account account=methods.account(player);double amount=account.amount(),max=account.maximum();
            if(!ResourceEscrow.finiteBalance(amount,max))return new Open(null,new View("narutomod",State.UNREADY,Double.NaN,Double.NaN,"Native chakra values are not ready"));
            return new Open(account,new View("narutomod",State.READY,amount,max,"Native chakra"));
        }catch(RuntimeException|LinkageError failure){return new Open(null,new View("narutomod",State.ERROR,Double.NaN,Double.NaN,"Native chakra API failed"));}
    }
    static boolean same(EntityPlayerMP player,ResourceEscrow.Account account){
        Api methods=api();if(methods==null||account==null||!installed())return false;
        try{return methods.ninja(player)&&methods.path(player)==account.identity();}catch(RuntimeException|LinkageError failure){return false;}
    }
    /** Legacy read convenience uses the server-owned view; never a client spending proof. */
    public static double amount(EntityPlayer player){View v=MoveCost.view(player);return "narutomod".equals(v.provider)&&v.ready()?v.amount:Double.NaN;}
    public static double max(EntityPlayer player){View v=MoveCost.view(player);return "narutomod".equals(v.provider)&&v.ready()?v.maximum:Double.NaN;}
    /** Exact reflection surface, package-visible for original synthetic contract checks. */
    static final class Api {
        final Method pathway,isNinja,amount,maximum,consume,clear;
        Api(Class<?> root,Class<?> values,Class<?> tracker,Class<?> player)throws ReflectiveOperationException{
            pathway=root.getMethod("pathway",player);isNinja=tracker.getMethod("isNinja",player);
            amount=values.getMethod("getAmount");maximum=values.getMethod("getMax");consume=values.getMethod("consume",double.class,boolean.class);clear=values.getMethod("clear");
            require(Modifier.isStatic(pathway.getModifiers())&&values.isAssignableFrom(pathway.getReturnType()),"pathway");
            require(Modifier.isStatic(isNinja.getModifiers())&&isNinja.getReturnType()==boolean.class,"isNinja");
            require(amount.getReturnType()==double.class&&!Modifier.isStatic(amount.getModifiers()),"getAmount");
            require(maximum.getReturnType()==double.class&&!Modifier.isStatic(maximum.getModifiers()),"getMax");
            require(consume.getReturnType()==boolean.class&&!Modifier.isStatic(consume.getModifiers()),"consume(DZ)Z");
            require(clear.getReturnType()==void.class&&!Modifier.isStatic(clear.getModifiers()),"clear");
        }
        private static void require(boolean yes,String method)throws NoSuchMethodException{if(!yes)throw new NoSuchMethodException("Unsupported public signature "+method);}
        private static Object invoke(Method method,Object receiver,Object...args){
            try{return method.invoke(receiver,args);}catch(InvocationTargetException wrapped){Throwable t=wrapped.getCause();if(t instanceof VirtualMachineError)throw (VirtualMachineError)t;if(t instanceof ThreadDeath)throw (ThreadDeath)t;throw new IllegalStateException("Native resource method failed",t);}catch(ReflectiveOperationException|RuntimeException failure){throw new IllegalStateException("Native resource access failed",failure);}
        }
        boolean ninja(Object player){return Boolean.TRUE.equals(invoke(isNinja,null,player));}
        Object path(Object player){return invoke(pathway,null,player);}
        ResourceEscrow.Account account(Object player){
            final Object value=path(player);if(value==null)throw new IllegalStateException("Null native pathway");
            return new ResourceEscrow.Account(){
                public Object identity(){return value;}
                public double amount(){return (Double)invoke(amount,value);}
                public double maximum(){return (Double)invoke(maximum,value);}
                public boolean consume(double cost,boolean overcap){return Boolean.TRUE.equals(invoke(consume,value,cost,overcap));}
                public void clear(){invoke(clear,value);}
            };
        }
    }
}
