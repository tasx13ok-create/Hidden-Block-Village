package village.fieldhud;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Colorful Health Bar 0.0.4a deliberately renders canceled HEALTH events.
 * Its exact overlay owner has one GUI-only subscriber. Temporarily retire that
 * subscriber while the replacement is active, and restore the same object on
 * layout-off, debug, death, spectator, or disconnect. No mod JAR/stat changes. */
public final class LegacyHealth {
    private static Object owner;
    private static Field listeners;
    private static Method restore,render;
    private static ModContainer modOwner;
    private static boolean unavailable=false,retired=false;
    private LegacyHealth() {}
    public static void sync(boolean replace) {
        if(unavailable||replace==retired)return;
        try {
            EventBus bus=MinecraftForge.EVENT_BUS;
            if(owner==null) {
                Class.forName("locusway.colorfulhealthbar.overlay.OverlayEventHandler");
                listeners=EventBus.class.getDeclaredField("listeners");listeners.setAccessible(true);
                Map<?,?> registered=(Map<?,?>)listeners.get(bus);
                for(Object candidate:registered.keySet())
                    if(candidate.getClass().getName().equals("locusway.colorfulhealthbar.overlay.OverlayEventHandler")) { owner=candidate;break; }
                if(owner==null)return;
                int subscribers=0;
                for(Method method:owner.getClass().getMethods())if(method.isAnnotationPresent(SubscribeEvent.class))subscribers++;
                if(subscribers!=1)throw new IllegalStateException("Unsupported overlay owner");
                render=owner.getClass().getMethod("onRenderGameOverlayEventPre",RenderGameOverlayEvent.Pre.class);
                Field owners=EventBus.class.getDeclaredField("listenerOwners");owners.setAccessible(true);
                modOwner=(ModContainer)((Map<?,?>)owners.get(bus)).get(owner);
                if(modOwner==null)throw new IllegalStateException("Missing original mod owner");
                restore=EventBus.class.getDeclaredMethod("register",Class.class,Object.class,Method.class,ModContainer.class);
                restore.setAccessible(true);
            }
            // Preserve the original ModContainer; public register() outside FML
            // initialization would assign a different owner and log an error.
            if(replace)bus.unregister(owner);else restore.invoke(bus,RenderGameOverlayEvent.Pre.class,owner,render,modOwner);
            retired=replace;
            System.out.println("FIELD_HUD_LEGACY_HEALTH: "+(replace?"replaced":"restored"));
        } catch(ClassNotFoundException error) { unavailable=true; }
        catch(ReflectiveOperationException|LinkageError|RuntimeException error) {
            unavailable=true;
            System.out.println("FIELD_HUD_LEGACY_HEALTH_DISABLED: "+error.getClass().getSimpleName());
        }
    }
}
