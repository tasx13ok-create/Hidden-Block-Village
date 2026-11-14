package village.vale.bridge;
import java.lang.reflect.Proxy;
import net.minecraft.command.*;

/** The real command class refuses direct low-permission invocation before any bridge/world work. */
public final class BridgeCommandChecks {
    private static int checks,messages;private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static ICommandSender sender(final boolean permission){return (ICommandSender)Proxy.newProxyInstance(ICommandSender.class.getClassLoader(),new Class<?>[]{ICommandSender.class},(p,m,a)->{if(m.getName().equals("canUseCommand"))return permission;if(m.getName().equals("sendMessage")){messages++;return null;}return null;});}
    public static void main(String[] args)throws Exception{
        BridgeCommand command=new BridgeCommand();ICommandSender denied=sender(false);check(command.getRequiredPermissionLevel()==2,"permission2 command");
        for(String action:new String[]{"status","reload","discover","map","mapentity","mapdamage","unmap","test"}){
            boolean failed=false;try{command.execute(null,denied,new String[]{action});}catch(CommandException expected){failed=true;}check(failed,"direct denied "+action);
        }
        check(messages==0,"denied command sends no preview/discovery feedback");check(command.getTabCompletions(null,denied,new String[]{""},null).isEmpty(),"no low-permission completion disclosure");
        boolean failed=false;try{command.execute(null,sender(true),new String[]{"status"});}catch(CommandException expected){failed=true;}check(failed,"permission alone cannot bypass missing server context");
        System.out.println("BRIDGE_COMMAND_CHECKS_PASS assertions="+checks+" native_endpoint=false");
    }
}
