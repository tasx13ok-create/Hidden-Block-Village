package village.controls;

import java.lang.reflect.*;
import java.util.function.Consumer;

/** Optional HUD contract: only an explicit false allows the old cast path. */
public final class FocusedCast {
    private FocusedCast() {}
    public static boolean consume(ClassLoader loader,Consumer<Throwable> failure) {
        Method action;
        try {action=Class.forName("village.fieldhud.ClientApi",false,loader).getMethod("castFocusedJutsu");}
        catch(ClassNotFoundException|NoSuchMethodException absent){return false;}
        catch(LinkageError|SecurityException broken){failure.accept(broken);return true;}
        if(!Modifier.isStatic(action.getModifiers())||action.getReturnType()!=boolean.class){failure.accept(new IllegalStateException("Unsupported HUD cast contract"));return true;}
        try{return !Boolean.FALSE.equals(action.invoke(null));}
        catch(InvocationTargetException failed){
            Throwable cause=failed.getCause();
            if(cause instanceof VirtualMachineError)throw (VirtualMachineError)cause;
            if(cause instanceof ThreadDeath)throw (ThreadDeath)cause;
            failure.accept(cause==null?failed:cause);return true;
        }catch(IllegalAccessException|IllegalArgumentException|LinkageError failed){failure.accept(failed);return true;}
    }
}
