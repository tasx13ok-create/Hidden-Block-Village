package village.fieldhud;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** The real Forge 1.12 GUI layer. No 1.21 core shader/font-provider assumptions. */
public final class ShinobiHud extends Gui {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static final int CYAN=0xFF80D7D3;
    private static final ReactiveMeter HEALTH=new ReactiveMeter(),CHAKRA=new ReactiveMeter(),RESOLVE=new ReactiveMeter();
    private static final HudLayers LAYERS=new HudLayers();
    private static final HudLayers.Frame FRAME=new HudLayers.Frame();
    static {
        LAYERS.register(0,f->{if(MC.playerController.shouldDrawHUD())status(f.layout.status,f.time,f.chakra);});
        LAYERS.register(10,f->hotbar(f.layout,f.time,f.partial));
        LAYERS.register(15,f->ReadinessHud.draw(f.resolution,f.time));
        LAYERS.register(17,JutsuRail::draw);
        LAYERS.register(20,f->{if(HudConfig.eyeMap&&MC.currentScreen==null)EyeMap.draw(f.resolution,f.time);});
        LAYERS.register(30,f->FocusFx.draw(f.resolution,f.time));
    }
    public static void registerLayer(int depth,HudLayers.Component component) { LAYERS.register(depth,component); }
    static ShinobiLayout layout(int w,int h) {
        return new ShinobiLayout(w,h,HudConfig.margin,ValeBridge.resourceRows(),HudConfig.positionX,HudConfig.positionY,HudConfig.slim,ArmorHudBridge.bounds(w,h));
    }
    static void resetWorld() { HEALTH.reset();CHAKRA.reset();RESOLVE.reset();ScrollHotbar.reset(); }
    static boolean active() {
        return HudConfig.shinobi&&MC.player!=null&&MC.world!=null&&MC.player.isEntityAlive()
            &&!MC.player.isSpectator()&&!MC.gameSettings.hideGUI&&!MC.gameSettings.showDebugInfo;
    }
    private static boolean combatBarVisible(){return RackAccess.combatActive()&&MC.player!=null&&MC.world!=null&&!MC.gameSettings.hideGUI;}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public void replace(RenderGameOverlayEvent.Pre event) {
        if(event.getType()==RenderGameOverlayEvent.ElementType.ALL)GlassMaterial.capture(event.getResolution());
        if(!active()){
            if(combatBarVisible()&&(event.getType()==RenderGameOverlayEvent.ElementType.HOTBAR||event.getType()==RenderGameOverlayEvent.ElementType.EXPERIENCE))event.setCanceled(true);
            return;
        }
        switch(event.getType()) {
            case HOTBAR: event.setCanceled(true);break;
            case HEALTH:case ARMOR:case FOOD:case EXPERIENCE:
                if(MC.playerController.shouldDrawHUD())event.setCanceled(true);
                break;
            default:break;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void draw(RenderGameOverlayEvent.Post event) {
        if(event.getType()!=RenderGameOverlayEvent.ElementType.ALL||(!active()&&!combatBarVisible()))return;
        ScaledResolution sr=event.getResolution();
        ChakraBridge.Snapshot chakra=ChakraBridge.current();
        ShinobiLayout layout=layout(sr.getScaledWidth(),sr.getScaledHeight());
        double time=MC.player.ticksExisted+event.getPartialTicks();
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),blend=GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        int alphaFunction=GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
        float alphaReference=GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
        GlStateManager.pushMatrix();GlStateManager.disableDepth();GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.disableLighting();
        try {
            FRAME.resolution=sr;FRAME.layout=layout;FRAME.chakra=chakra;FRAME.time=time;FRAME.partial=event.getPartialTicks();
            if(active())LAYERS.render(FRAME);else ScrollHotbar.draw(layout,time,event.getPartialTicks());
        } finally {
            GlStateManager.color(1,1,1,1);RenderHelper.disableStandardItemLighting();GlStateManager.popMatrix();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
            GlStateManager.alphaFunc(alphaFunction,alphaReference);GL11.glAlphaFunc(alphaFunction,alphaReference);
            if(alpha) { GlStateManager.enableAlpha();GL11.glEnable(GL11.GL_ALPHA_TEST); }
            else { GlStateManager.disableAlpha();GL11.glDisable(GL11.GL_ALPHA_TEST); }
        }
    }
    private static void text(String s,int x,int y,int max,int color) {
        MC.fontRenderer.drawStringWithShadow(MC.fontRenderer.trimStringToWidth(s,Math.max(1,max)),x,y,color);
    }
    static String number(double value) {
        if(!Double.isFinite(value))return "?";
        if(value>=1E12||(value>0&&value<.1))return Double.toString(value);
        if(value>=1000000000)return String.format(Locale.ROOT,"%.1fB",value/1000000000);
        if(value>=1000000)return String.format(Locale.ROOT,"%.1fM",value/1000000);
        if(value>=10000)return String.format(Locale.ROOT,"%.0fk",value/1000);
        if(value>=1000)return String.format(Locale.ROOT,"%.1fk",value/1000);
        return String.format(Locale.ROOT,Math.abs(value-Math.round(value))<0.01?"%.0f":"%.1f",value);
    }
    private static void status(Layout.Box b,double time,ChakraBridge.Snapshot chakra) {
        EntityPlayer p=MC.player;
        float health=p.getHealth(),maximum=p.getMaxHealth(),absorb=p.getAbsorptionAmount();
        boolean low=SkinMath.lowHealth(health),poison=p.isPotionActive(MobEffects.POISON),wither=p.isPotionActive(MobEffects.WITHER);
        int tone=low?0xFFFF614D:wither?0xFFBD9ADD:poison?0xFFA7D56A:0xFFEC6C70;
        double light=MC.world.getLightFromNeighbors(p.getPosition())/15.0;
        boolean motion=HudConfig.motion&&HudConfig.mode!=2;
        ValeSnapshotReader.State vale=ValeBridge.current();
        HEALTH.update(health,maximum,time,motion);
        if(chakra!=null)CHAKRA.update(chakra.amount,chakra.maximum,time,motion);else CHAKRA.reset();
        if(ValeBridge.showResolve())RESOLVE.update(vale.resolve,vale.resolveMax,time,motion);else RESOLVE.reset();
        Regions.Zone zone=Regions.at(MC);
        GlassMaterial.slate(b.x,b.y,b.w,b.h,7,low||poison||wither||zone==null?tone:zone.color,255);
        SkinDraw.line(b.x+11,b.y+1.2,b.x+38,b.y+1.2,.7,0x807CA6BA);
        double impact=HEALTH.impact(time);
        if(chakra!=null)impact=Math.max(impact,CHAKRA.impact(time)*.55);
        if(motion&&HudConfig.focusEffects)impact=Math.max(impact,HudSoundPulse.value(time)*.16);
        PanelSkin.pulse(b,HEALTH.direction()<0?0xE9988C:0xA3DED6,impact);
        if(low)SkinDraw.line(b.x+12,b.y+b.h-1.2,b.x+b.w-12,b.y+b.h-1.2,.8,SkinDraw.alpha(tone,motion?(int)(65+25*Math.sin(time*.12)):65));
        // Actual player skin, rather than a fabricated portrait/character state.
        int face=b.x+8,fy=b.y+10,faceSize=b.w<170?20:28;
        SkinDraw.rounded(face-2,fy-2,faceSize+4,faceSize+4,4,0x70526676);
        SkinDraw.rounded(face-1,fy-1,faceSize+2,faceSize+2,3,0xA014202D);
        // Legacy overlays can leave alpha testing disabled. The transparent hat
        // layer must not overwrite the opaque base face with black RGB pixels.
        GlStateManager.enableAlpha();GL11.glEnable(GL11.GL_ALPHA_TEST);
        GlStateManager.alphaFunc(GL11.GL_GREATER,0.1F);GL11.glAlphaFunc(GL11.GL_GREATER,0.1F);
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        MC.getTextureManager().bindTexture(MC.player.getLocationSkin());
        GlStateManager.color(1,1,1,1);GL11.glColor4f(1,1,1,1);
        Gui.drawScaledCustomSizeModalRect(face,fy,8,8,8,8,faceSize,faceSize,64,64);
        Gui.drawScaledCustomSizeModalRect(face,fy,40,8,8,8,faceSize,faceSize,64,64);
        drawRect(face,fy+1,face+faceSize,fy+6,0xFF23333F);
        drawRect(face+4,fy+1,face+faceSize-4,fy+5,0xFFBFC7BE);
        drawRect(face+8,fy+2,face+9,fy+3,0xFF495960);drawRect(face+19,fy+2,face+20,fy+3,0xFF495960);
        int x=b.x+(b.w<170?33:47),width=b.x+b.w-x-8;
        String name=p.getDisplayName().getUnformattedText();
        if(vale!=null) {
            String level="Lv "+vale.level;int lw=MC.fontRenderer.getStringWidth(level);
            text(name,x,b.y+7,Math.max(1,width-lw-8),0xFFF0F5F7);text(level,x+width-lw,b.y+7,lw,0xFFB2CEDC);
        } else text(name,x,b.y+7,width,0xFFF0F5F7);
        String state=(poison?" Poison":"")+(wither?" Wither":"");
        text(number(health)+"/"+number(maximum)+" HP"+state,x,b.y+20,width,0xFFCEDCE3);
        EnergySlate.draw(x,b.y+33,width,6,HEALTH.shown(),HEALTH.trail(),tone,0xFFDDAD9B,time,false);
        // Gold shield is distinct from green poison and the red low-health seal.
        if(absorb>0) {
            gauge(x,b.y+42,width,2,SkinMath.ratio(absorb,Math.max(4,maximum)),0xFFE8C774,0x50322D2A);
            text("+"+number(absorb),b.x+9,b.y+41,34,0xFFE8C774);
        }
        int shift=18*((chakra!=null||ValeBridge.resourceWaiting()?1:0)+(ValeBridge.showResolve()?1:0));
        if(ValeBridge.resourceWaiting())text(SharedResourceBridge.current()==null?"Resource syncing":"Resource unavailable",x+4,b.y+47,width-8,0xFFE8C77F);
        if(chakra!=null) {
            double ratio=SkinMath.ratio(chakra.amount,chakra.maximum);
            int ct=chakra.warning?0xFFFF8059:chakra.sage?0xFFFF6A2B:chakra.byakugou?0xFFDE91C0:ratio<=0.15?0xFFE8C77F:0xFF08AFFF;
            if(JutsuSignals.casting&&!chakra.warning&&!chakra.sage&&!chakra.byakugou&&ratio>.15)ct=0xFF000000|JutsuSignals.color;
            String label=(width<145?(chakra.label.equals("Chakra")?"CH ":"RE "):chakra.label+" ")+number(chakra.amount)+"/"+number(chakra.maximum)+(chakra.warning?" !":chakra.sage?" Sage":chakra.byakugou?(width<145?" Seal":" Byakugou"):"");
            text(label,x+4,b.y+47,width-8,ct);
            EnergySlate.draw(x+4,b.y+59,width-8,5,CHAKRA.shown(),CHAKRA.trail(),ct,0xFFADC7D1,time,motion);
        }
        if(ValeBridge.showResolve()) {
            int row=chakra==null?0:18;
            text("Resolve "+number(vale.resolve)+"/"+number(vale.resolveMax),x+4,b.y+47+row,width-8,0xFF9DD5EF);
            EnergySlate.draw(x+4,b.y+59+row,width-8,5,RESOLVE.shown(),RESOLVE.trail(),0xFF08AFFF,0xFFADC7D1,time,motion);
        }
        int armor=ForgeHooks.getTotalArmorValue(p);
        drawGuard(x+8,b.y+48+shift);
        gauge(x+21,b.y+49+shift,Math.max(20,width-57),4,SkinMath.ratio(armor,20),0xFFB7CDD1,0xFF2B3642);
        text(number(armor),b.x+b.w-29,b.y+47+shift,24,0xFFC7D5CF);
        int food=p.getFoodStats().getFoodLevel();
        ramen(x+14,b.y+68+shift,food,p.isPotionActive(MobEffects.HUNGER),time);
        String meal=food+"/20";
        ItemStack held=p.getHeldItemMainhand();
        if(!held.isEmpty()&&held.getItem() instanceof ItemFood)meal+="  +"+((ItemFood)held.getItem()).getHealAmount(held);
        text(meal,x+31,b.y+64+shift,width-31,0xFFE3D4B5);
        gauge(x+31,b.y+76+shift,Math.max(24,width-31),2,SkinMath.ratio(p.getFoodStats().getSaturationLevel(),20),0xFFBBA26C,0xFF27333A);
        seal(b.x+(b.w<170?19:23),b.y+64+shift,low?11:10,low?tone:CYAN,low,time,light);
        if(chakra!=null)SkinDraw.arc(b.x+(b.w<170?19:23),b.y+64+shift,7,1.2,-Math.PI/2,-Math.PI/2+Math.PI*2*SkinMath.ratio(chakra.amount,chakra.maximum),chakra.sage?0xFFFF6A2B:0xFF08AFFF);
        else if(ValeBridge.showResolve())SkinDraw.arc(b.x+(b.w<170?19:23),b.y+64+shift,7,1.2,-Math.PI/2,-Math.PI/2+Math.PI*2*SkinMath.ratio(vale.resolve,vale.resolveMax),0xFF08AFFF);
    }
    private static void gauge(int x,int y,int width,int height,double ratio,int fill,int back) {
        meter(x,y,width,height,ratio,ratio,fill,back);
    }
    private static void meter(int x,int y,int width,int height,double ratio,double trail,int fill,int trailColor) {
        SkinDraw.rounded(x-.6,y-.6,width+1.2,height+1.2,(height+1.2)/2,0x465F7583);
        SkinDraw.rounded(x,y,width,height,height/2.0,0x90101C28);
        double end=width*ratio,last=width*trail;
        if(last>end+.1)SkinDraw.rounded(x,y,last,height,height/2.0,SkinDraw.alpha(trailColor,100));
        if(end>.1) {
            SkinDraw.rounded(x,y,end,height,Math.min(height/2.0,end/2),fill);
            if(height>=4)SkinDraw.line(x+height/2.0,y+.7,x+Math.max(height/2.0,end-height/2.0),y+.7,.7,0x65F1FBFF);
        }
    }
    private static void drawGuard(int x,int y) {
        drawRect(x,y,x+9,y+5,0xFF192734);drawRect(x+1,y,x+8,y+4,0xFFB3C6C9);
        drawRect(x+2,y+4,x+7,y+6,0xFF7C979F);drawRect(x+3,y+6,x+6,y+7,0xFF7C979F);
    }
    private static void seal(double x,double y,double radius,int tone,boolean low,double time,double light) {
        SkinDraw.ring(x,y,radius+2,1.6,0x60495E6F);
        SkinDraw.ring(x,y,radius,1.1,SkinDraw.alpha(tone,(int)(170+85*light)));
        SkinDraw.ring(x,y,radius-3,0.8,tone);
        SkinDraw.disc(x,y,2,low?0xFF5B171C:tone);
        for(int i=0;i<8;i++) {
            double angle=i*Math.PI/4;
            double gap=low?(i%2)*2:0;
            SkinDraw.line(x+Math.cos(angle)*(radius-1),y+Math.sin(angle)*(radius-1),x+Math.cos(angle)*(radius+2+gap),y+Math.sin(angle)*(radius+2+gap),1,tone);
        }
        if(low) {
            for(int i=0;i<3;i++) {
                double a=i*2.1+0.3;
                SkinDraw.line(x+Math.cos(a)*3,y+Math.sin(a)*3,x+Math.cos(a+0.3)*(radius+3),y+Math.sin(a+0.3)*(radius+3),1.4,0xFF070C13);
                if(HudConfig.motion&&HudConfig.mode!=2) {
                    double d=radius+3+((time*0.18+i*3)%5);
                    SkinDraw.disc(x+Math.cos(a)*d,y+Math.sin(a)*d,1,SkinDraw.alpha(tone,90));
                }
            }
        }
    }
    private static void ramen(int x,int y,int food,boolean hunger,double time) {
        int broth=hunger?0xFFB1BA68:0xFFD6A15F;
        SkinDraw.disc(x,y,10,0x50253542);
        drawRect(x-9,y-3,x+9,y+3,0xFFE8DDC4);
        drawRect(x-7,y+3,x+7,y+6,0xFFC4BDAB);drawRect(x-4,y+6,x+4,y+7,0xFF8FA2A2);
        drawRect(x-8,y-3,x+8,y,food>0?broth:0xFF686D68);
        int noodles=(int)Math.ceil(food/4.0);
        for(int i=0;i<noodles;i++)drawRect(x-6+i*3,y-3+(i%2),x-4+i*3,y,0xFFF0D891);
        if(food>10) { SkinDraw.disc(x+4,y-2,2,0xFFF0E8C4);SkinDraw.disc(x+4,y-2,1,0xFFE3AD53); }
        // Small engraved leaf curl on the ceramic bowl, also visible when empty.
        SkinDraw.arc(x,y+2,2,0.8,0,Math.PI*1.6,0xFF477372);
        if(HudConfig.motion&&HudConfig.mode!=2&&food>0) for(int i=0;i<2;i++) {
            double rise=(time*0.08+i*3)%6;
            SkinDraw.line(x-3+i*5,y-5-rise,x-2+i*5,y-7-rise,0.7,SkinDraw.alpha(0xD7E8DD,(int)(110-rise*12)));
        }
    }
    private static void hotbar(ShinobiLayout layout,double time,float partial) {
        ScrollHotbar.draw(layout,time,partial);
    }
}
