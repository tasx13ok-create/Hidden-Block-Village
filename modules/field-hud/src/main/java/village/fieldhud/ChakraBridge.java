package village.fieldhud;

import java.lang.reflect.*;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.*;

/** Optional read-only access to NarutoMod's synchronized client pathway.
 * Never constructs a pathway, edits resource values, or reads server-only NBT.
 * Original GUI callbacks are still invoked with their pixels clipped so all
 * warning timers run normally, including the installed SpringAddon duplicates. */
public final class ChakraBridge {
    private static final Minecraft MC=Minecraft.getMinecraft();
    public static final class Snapshot {
        public final double amount,maximum;
        public final boolean warning,sage,byakugou;public final String label;
        Snapshot(double a,double m,boolean w,boolean s,boolean b) {this(a,m,w,s,b,"Chakra");}
        Snapshot(double a,double m,boolean w,boolean s,boolean b,String label) { amount=a;maximum=m;warning=w;sage=s;byakugou=b;this.label=label; }
    }
    private static Method initialized,isNinja,pathway,amount,maximum,restore;
    private static Field overlayInstance,warning,sage;
    private static final List<Binding> bindings=new ArrayList<>();
    // LWJGL 2 requires capacity 16 for glGetInteger even for a four-value box.
    private static final IntBuffer clip=BufferUtils.createIntBuffer(16);
    private static final class Binding {
        final Object owner;final Method render;final ModContainer modOwner;
        final Field instance,warning,boost;
        Binding(Object o,ModContainer m) throws ReflectiveOperationException {
            owner=o;modOwner=m;
            int subscribers=0;
            for(Method method:o.getClass().getMethods())if(method.isAnnotationPresent(SubscribeEvent.class))subscribers++;
            if(subscribers!=1||m==null)throw new IllegalStateException("Unsupported chakra GUI owner");
            render=o.getClass().getMethod("eventHandler",RenderGameOverlayEvent.class);
            Class<?> outer=Class.forName(o.getClass().getName().replace("$GUIRenderEventClass",""));
            instance=outer.getDeclaredField("instance");instance.setAccessible(true);
            warning=outer.getDeclaredField("warningTime");warning.setAccessible(true);
            Field optional=null;
            try { optional=outer.getDeclaredField("showByakugouBar");optional.setAccessible(true); }catch(NoSuchFieldException ignored) {}
            boost=optional;
        }
    }
    private static boolean unavailable=false,ready=false,retired=false;
    private static Snapshot snapshot;
    ChakraBridge() {}
    public static Snapshot current() { return HudConfig.chakra?snapshot:null; }
    static boolean ownsResource(){return SharedResourceBridge.authoritative()||net.minecraftforge.fml.common.Loader.isModLoaded("narutomod");}
    private static void prepare() throws ReflectiveOperationException {
        Class<?> api=Class.forName("net.narutomod.Chakra");
        initialized=api.getMethod("isInitialized",EntityPlayer.class);
        pathway=api.getMethod("pathway",EntityPlayer.class);
        isNinja=Class.forName("net.narutomod.PlayerTracker").getMethod("isNinja",EntityPlayer.class);
        Class<?> values=Class.forName("net.narutomod.Chakra$Pathway");
        amount=values.getMethod("getAmount");maximum=values.getMethod("getMax");
        Class<?> gui=Class.forName("net.narutomod.gui.overlay.OverlayChakraDisplay");
        overlayInstance=gui.getDeclaredField("instance");overlayInstance.setAccessible(true);
        warning=gui.getDeclaredField("warningTime");warning.setAccessible(true);
        sage=gui.getDeclaredField("showSageBar");sage.setAccessible(true);
        ready=true;
    }
    private static void hook(boolean hide) throws ReflectiveOperationException {
        if(hide==retired)return;
        EventBus bus=MinecraftForge.EVENT_BUS;
        if(bindings.isEmpty()) {
            Field listeners=EventBus.class.getDeclaredField("listeners");listeners.setAccessible(true);
            Field owners=EventBus.class.getDeclaredField("listenerOwners");owners.setAccessible(true);
            Map<?,?> ownerMap=(Map<?,?>)owners.get(bus);List<Binding> found=new ArrayList<>();
            for(Object candidate:((Map<?,?>)listeners.get(bus)).keySet()) {
                String name=candidate.getClass().getName();
                if(name.equals("net.narutomod.gui.overlay.OverlayChakraDisplay$GUIRenderEventClass")
                    ||name.equals("net.mcreator.ahznbcursemarkaddon.gui.overlay.OverlayChakraDisplay$GUIRenderEventClass")
                    ||name.equals("net.mcreator.ahznbcursemarkaddon.gui.overlay.OverlayChakraByakugouDisplay$GUIRenderEventClass"))
                    found.add(new Binding(candidate,(ModContainer)ownerMap.get(candidate)));
            }
            if(found.isEmpty())return;
            restore=EventBus.class.getDeclaredMethod("register",Class.class,Object.class,Method.class,ModContainer.class);
            restore.setAccessible(true);
            bindings.addAll(found);
        }
        for(Binding b:bindings)if(hide)bus.unregister(b.owner);else restore.invoke(bus,RenderGameOverlayEvent.class,b.owner,b.render,b.modOwner);
        retired=hide;
        System.out.println("FIELD_HUD_CHAKRA_GUI: "+(hide?"integrated":"restored")+" overlays="+bindings.size());
    }
    public static void tick() {
        SharedResourceReader.State shared=SharedResourceBridge.current();
        try {
            if(!ready&&!unavailable)prepare();
            snapshot=null;
            if(ready&&MC.player!=null&&MC.world!=null&&MC.world.isRemote&&HudConfig.chakra
                &&(Boolean)isNinja.invoke(null,MC.player)&&(Boolean)initialized.invoke(null,MC.player)) {
                Object value=pathway.invoke(null,MC.player),gui=overlayInstance.get(null);
                double a=((Number)amount.invoke(value)).doubleValue(),m=((Number)maximum.invoke(value)).doubleValue();
                boolean warn=gui!=null&&warning.getInt(gui)>0,boost=false;
                for(Binding b:bindings) {
                    Object instance=b.instance.get(null);
                    if(instance!=null) { warn|=b.warning.getInt(instance)>0;boost|=b.boost!=null&&b.boost.getBoolean(instance); }
                }
                if(Double.isFinite(a)&&Double.isFinite(m)&&m>0&&a>=0)
                    snapshot=new Snapshot(a,m,warn,gui!=null&&sage.getBoolean(gui),boost);
            }
            if(SharedResourceBridge.authoritative())snapshot=shared!=null&&shared.ready&&HudConfig.chakra?new Snapshot(shared.amount,shared.maximum,snapshot!=null&&snapshot.warning,snapshot!=null&&snapshot.sage,snapshot!=null&&snapshot.byakugou,"narutomod".equals(shared.provider)?"Chakra":"Resolve"):null;
            hook(ShinobiHud.active()&&MC.playerController.shouldDrawHUD()&&snapshot!=null);
        } catch(ClassNotFoundException error) { unavailable=true; }
        catch(ReflectiveOperationException|LinkageError|RuntimeException error) {
            snapshot=null;unavailable=true;ready=false;
            try { hook(false); }catch(Exception ignored) {}
            System.out.println("FIELD_HUD_CHAKRA_DISABLED: "+error.getClass().getSimpleName());
        }
        if(SharedResourceBridge.authoritative()&&(!ready||unavailable))snapshot=shared!=null&&shared.ready&&HudConfig.chakra?new Snapshot(shared.amount,shared.maximum,false,false,false,"narutomod".equals(shared.provider)?"Chakra":"Resolve"):null;
    }
    @SubscribeEvent public void legacy(RenderGameOverlayEvent event) {
        if(!retired||event.isCancelable()||event.getType()!=RenderGameOverlayEvent.ElementType.HELMET)return;
        boolean scissor=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);clip.clear();GL11.glGetInteger(GL11.GL_SCISSOR_BOX,clip);
        int x=clip.get(0),y=clip.get(1),w=clip.get(2),h=clip.get(3);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(0,0,0,0);
        try {
            for(Binding b:bindings)b.render.invoke(b.owner,event);
        } catch(ReflectiveOperationException error) {
            throw new RuntimeException("Original chakra GUI callback failed",error);
        } finally { GL11.glScissor(x,y,w,h);if(!scissor)GL11.glDisable(GL11.GL_SCISSOR_TEST); }
    }
}
