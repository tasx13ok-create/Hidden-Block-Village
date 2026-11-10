package village.vale.clientcompat;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.server.MinecraftServer;
import com.google.common.util.concurrent.*;

/** Wait normally while a server can execute its task; never abandon a live saving server. */
public final class SafeShutdown {
    private SafeShutdown(){}
    public static Object await(Future<?> task,MinecraftServer server){
        // The merged development jar has getServerThread(), but the real1.12 client does not.
        // Read the one actual instance Thread field instead; names can be MCP, SRG or obfuscated.
        Thread owner=owner(server,MinecraftServer.class);
        return awaitStopped(task,()->owner.getState()==Thread.State.TERMINATED);
    }
    static Thread owner(Object server,Class<?> declaring){
        Field selected=null;
        for(Field field:declaring.getDeclaredFields())if(field.getType()==Thread.class&&!Modifier.isStatic(field.getModifiers())){
            if(selected!=null)throw new IllegalStateException("Ambiguous integrated-server thread layout");selected=field;
        }
        if(selected==null)throw new IllegalStateException("Missing integrated-server thread field");
        try{selected.setAccessible(true);Thread value=(Thread)selected.get(server);if(value==null)throw new IllegalStateException("Integrated-server owner thread not initialized");return value;}
        catch(IllegalAccessException failure){throw new IllegalStateException("Cannot read integrated-server owner thread",failure);}
    }
    public static Object awaitStopped(Future<?> task,BooleanSupplier stoppedAndThreadEnded){
        boolean interrupted=false;
        try{
            for(;;){
                try{return task.get(100,TimeUnit.MILLISECONDS);}
                catch(TimeoutException waiting){if(stoppedAndThreadEnded.getAsBoolean()&&!task.isDone()){System.out.println("VALE_CLIENT_SHUTDOWN_RECOVERED: obsolete queued task released after integrated server thread fully ended.");return null;}}
                catch(InterruptedException waiting){interrupted=true;}
                catch(ExecutionException failure){Throwable cause=failure.getCause();if(cause instanceof Error)throw new ExecutionError((Error)cause);throw new UncheckedExecutionException(cause);}
            }
        }finally{if(interrupted)Thread.currentThread().interrupt();}
    }
}
