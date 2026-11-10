package village.fieldhud;

import java.lang.reflect.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import net.minecraft.potion.Potion;

/** Optional Xaero 26.6 adapter: delegate its renderer, observe its live pushed
 * module bounds, then decorate the real map. No replacement map/radar data. */
public final class EyeMap {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static Object module,original,proxy,world;
    private static Field rendererField;
    private static int lastTick=-10,x,y,w,h,screenW,screenH;
    private static boolean unavailable=false,reported=false;
    private static long lastAttempt=-100;
    private EyeMap() {}
    public static void resetWorld() { world=null;lastTick=-10;lastAttempt=-100; }
    private static Object call(Object target,String name) throws ReflectiveOperationException {
        return target.getClass().getMethod(name).invoke(target);
    }
    private static void ensure() {
        if(unavailable||MC.player==null)return;
        if(proxy!=null&&world==MC.world)return;
        if(MC.player.ticksExisted-lastAttempt<20&&world==MC.world)return;
        lastAttempt=MC.player.ticksExisted;world=MC.world;
        try {
            Object root=Class.forName("xaero.common.HudMod").getField("INSTANCE").get(null);
            if(root==null)return;
            Object manager=call(call(root,"getHud"),"getModuleManager");
            Object next=manager.getClass().getMethod("get",ResourceLocation.class).invoke(manager,new ResourceLocation("xaerominimap","minimap"));
            if(next==null)return;
            if(next==module&&proxy!=null)return;
            Object base=call(next,"getRenderer");if(base==null)return;
            Class<?> api=Class.forName("xaero.hud.render.module.IModuleRenderer");
            final Object delegate=base;
            Object observer=Proxy.newProxyInstance(api.getClassLoader(),new Class<?>[]{api},(self,method,args)->{
                Object result;
                try { result=method.invoke(delegate,args); }
                catch(InvocationTargetException error) { throw error.getCause(); }
                if(!unavailable&&method.getName().equals("render")&&args!=null&&args.length==3&&MC.player!=null) {
                  try {
                    Object session=args[0],context=args[1];
                    boolean hidden=false;
                    try { hidden=(Boolean)call(call(session,"getProcessor"),"getNoMinimapMessageReceived"); }
                    catch(ReflectiveOperationException ignored) { hidden=true; }
                    for(String effect:new String[]{"NO_MINIMAP","NO_MINIMAP_HARMFUL"}) {
                        Potion p=(Potion)Class.forName("xaero.common.effect.Effects").getField(effect).get(null);
                        if(p!=null&&MC.player.isPotionActive(p))hidden=true;
                    }
                    if(!hidden) {
                        Class<?> c=context.getClass();
                        x=c.getField("x").getInt(context);y=c.getField("y").getInt(context);w=c.getField("w").getInt(context);h=c.getField("h").getInt(context);
                        screenW=c.getField("screenWidth").getInt(context);screenH=c.getField("screenHeight").getInt(context);
                        lastTick=MC.player.ticksExisted;
                        if(!reported) { reported=true;System.out.println("FIELD_HUD_EYE_BOUNDS: "+x+","+y+" width="+w+" screen="+screenW+"x"+screenH); }
                    }
                  } catch(ReflectiveOperationException|RuntimeException error) {
                      unavailable=true;lastTick=-10;
                      System.out.println("FIELD_HUD_EYE_DISABLED: could not observe native bounds: "+error.getClass().getSimpleName());
                  }
                }
                return result;
            });
            rendererField=next.getClass().getDeclaredField("renderer");rendererField.setAccessible(true);
            rendererField.set(next,observer);module=next;original=base;proxy=observer;
        } catch(ClassNotFoundException e) { unavailable=true; }
        catch(ReflectiveOperationException|LinkageError|RuntimeException e) {
            unavailable=true;System.out.println("FIELD_HUD_EYE_DISABLED: Xaero adapter unavailable: "+e.getClass().getSimpleName());
        }
    }
    public static void draw(ScaledResolution sr,double time) {
        ensure();
        if(lastTick!=MC.player.ticksExisted||screenW!=sr.getScaledWidth()||screenH!=sr.getScaledHeight()||w<40||w>220)return;
        double cx=x+w/2.0,cy=y+w/2.0,r=(w-12)/2.0;
        // Inner shadow stays at the rim; the actual terrain/waypoints remain visible.
        for(int i=0;i<4;i++)SkinDraw.ring(cx,cy,r-i,1,SkinDraw.alpha(0x0C1727,62-i*13));
        SkinDraw.ring(cx,cy,r+3.5,2.5,0x502E4157);
        SkinDraw.ring(cx,cy,r+2,.8,0xC5DCE9F3);
        SkinDraw.ring(cx,cy,r-.5,.7,0x709BAEC4);
        SkinDraw.arc(cx,cy,r+2.2,.8,Math.PI*1.05,Math.PI*1.7,0x80F1F7FF);
        double glare=HudConfig.motion&&HudConfig.mode!=2?(time%120)/120*Math.PI*2:0;
        Regions.Zone zone=Regions.at(MC);
        SkinDraw.arc(cx,cy,r+2,1,glare,glare+0.4,zone==null?0x80E3F7FA:SkinDraw.alpha(zone.color,135));
        for(int i=0;i<12;i++) {
            double a=i*Math.PI/6+0.12;
            SkinDraw.line(cx+Math.cos(a)*(r-1),cy+Math.sin(a)*(r-1),cx+Math.cos(a+0.02)*(r-4),cy+Math.sin(a+0.02)*(r-4),0.6,0x429BA8D1);
            if(i%3==1)SkinDraw.line(cx+Math.cos(a+0.02)*(r-4),cy+Math.sin(a+0.02)*(r-4),cx+Math.cos(a+0.06)*(r-6),cy+Math.sin(a+0.06)*(r-6),0.6,0x309BA8D1);
        }
        // Decorative kunai-shaped glass notches; native compass letters retain
        // their actual directional calculation rather than guessing a bearing.
        for(int i=0;i<4;i++) {
            double a=i*Math.PI/2;
            double px=cx+Math.cos(a)*(r+2),py=cy+Math.sin(a)*(r+2);
            SkinDraw.line(px-Math.cos(a)*2,py-Math.sin(a)*2,px+Math.cos(a)*2,py+Math.sin(a)*2,1,0xBFDDE8EF);
            SkinDraw.disc(px,py,.7,0xC0ADD9EC);
        }
    }
    public static Layout.Box bounds(int width,int height,boolean stale) {
        if(unavailable||lastTick<0||width!=screenW||height!=screenH||w<40||w>220)return null;
        if(!stale&&(MC.player==null||lastTick!=MC.player.ticksExisted))return null;
        return new Layout.Box(x-5,y-5,w+10,Math.max(w,h)+24);
    }
}
