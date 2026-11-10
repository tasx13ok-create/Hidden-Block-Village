package village.controls;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.Loader;

/** Optional route to the real installed Essential settings, through its GUI API. */
final class EssentialBridge {
    private static boolean checked;
    private static OptionalGuiRoute route;
    private static String status="Essential is not installed on this client.";
    private static GuiScreen parent;
    private static void inspect(){
        if(checked)return;checked=true;
        try{
            ClassLoader loader=Loader.instance().getModClassLoader();
            Class<?> api=Class.forName("gg.essential.api.EssentialAPI",false,loader);
            Class<?> config=Class.forName("gg.essential.config.EssentialConfig",false,loader);
            Class<?> screen=null;
            if(!GuiScreen.class.isAssignableFrom(config.getMethod("getGui").getReturnType()))
                screen=Class.forName("gg.essential.gui.vigilancev2.VigilanceV2SettingsGui",false,loader);
            route=OptionalGuiRoute.inspect(api,config,screen,GuiScreen.class);
            status="Open the real Essential mod settings, including its own social and cosmetic options.";
        }catch(ClassNotFoundException absent){status="Essential is not installed on this client.";}
        catch(ReflectiveOperationException|LinkageError unavailable){status="This Essential version does not expose the supported settings API. Use its native menu.";}
    }
    static boolean available(){inspect();return route!=null;}
    static String status(){inspect();return status;}
    static void open(GuiScreen back)throws Exception{
        if(!available())throw new IllegalStateException(status());
        parent=back;
        try{route.open();}catch(ReflectiveOperationException|RuntimeException ex){parent=null;throw ex;}
    }
    static boolean returning(GuiScreen old,GuiScreen next){return parent!=null&&old!=null&&old.getClass().getName().startsWith("gg.essential.")&&next==null;}
    static GuiScreen takeParent(){GuiScreen back=parent;parent=null;return back;}
    static void observe(GuiScreen old,GuiScreen next){if(parent!=null&&old!=null&&old.getClass().getName().startsWith("gg.essential.")&&next!=null&&!next.getClass().getName().startsWith("gg.essential."))parent=null;}
}
