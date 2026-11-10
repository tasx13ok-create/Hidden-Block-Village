package village.controls;

import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.Loader;

/** Optional integration with the HUD project's actual screens and renderer. */
final class HudBridge {
    static GuiScreen legacyParent;
    private static Method glass;
    private static boolean lookedUpGlass,glassFailed;
    static boolean available(){return Loader.isModLoaded("fieldhud")&&Minecraft.getMinecraft().world!=null;}
    static void open(GuiScreen parent,boolean layout)throws Exception {
        if(!available())throw new IllegalStateException("Enter a world with Field HUD installed to use this tool.");
        ClassLoader loader=Loader.instance().getModClassLoader();
        try {
            Class<?> api=Class.forName("village.fieldhud.ClientApi",true,loader);
            Object opened=api.getMethod(layout?"openLayout":"openSettings",GuiScreen.class).invoke(null,parent);
            if(layout&&Boolean.FALSE.equals(opened))throw new IllegalStateException("Enable the scroll HUD before editing its layout.");return;
        } catch(ClassNotFoundException|NoSuchMethodException missing) {
            Class<?> screen=Class.forName(layout?"village.fieldhud.HudLayoutScreen":"village.fieldhud.HudScreen",true,loader);
            GuiScreen target=(GuiScreen)screen.getConstructor().newInstance();legacyParent=parent;
            Minecraft.getMinecraft().displayGuiScreen(target);
        }
    }
    static boolean returning(GuiScreen old,GuiScreen next) {
        return legacyParent!=null&&old!=null&&old.getClass().getName().startsWith("village.fieldhud.")&&next==null;
    }
    static void panel(int x,int y,int w,int h) {
        if(!glassFailed&&Loader.isModLoaded("fieldhud")) {
            try {
                if(!lookedUpGlass) {
                    lookedUpGlass=true;Class<?> type=Class.forName("village.fieldhud.GlassMaterial",true,Loader.instance().getModClassLoader());
                    glass=type.getMethod("panel",double.class,double.class,double.class,double.class,double.class,int.class,int.class);
                }
                if(glass!=null) {
                    glass.invoke(null,(double)x,(double)y,(double)w,(double)h,7d,0x172630,224);return;
                }
            }catch(Exception unavailable){glassFailed=true;}
        }
        Surface.panel(x,y,w,h);
    }
}
