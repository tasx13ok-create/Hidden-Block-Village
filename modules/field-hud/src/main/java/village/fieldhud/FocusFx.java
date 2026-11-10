package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

/** Small open-center optics, not a full-screen filter or substitute world lighting. */
public final class FocusFx {
    private FocusFx() {}
    public static void draw(ScaledResolution screen,double time) {
        if(!HudConfig.focusEffects||HudConfig.mode==2||Minecraft.getMinecraft().currentScreen!=null)return;
        double x=screen.getScaledWidth()/2.0,y=screen.getScaledHeight()/2.0;
        boolean motion=HudConfig.motion,alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableAlpha();
        try {
            if(JutsuSignals.eye||JutsuSignals.whiteEye) {
                int tone=JutsuSignals.eye?0xE99CA6:0xD8E7FA;
                double rotation=motion?time*.008:0;
                int opacity=motion?(int)(43+10*Math.sin(time*.1)):43;
                for(int i=0;i<3;i++) {
                    double a=rotation+i*Math.PI*2/3;
                    SkinDraw.arc(x,y,24,.6,a+.18,a+1.3,SkinDraw.alpha(tone,opacity));
                    double px=x+Math.cos(a)*24,py=y+Math.sin(a)*24;
                    SkinDraw.disc(px,py,1.2,SkinDraw.alpha(tone,opacity+20));
                    SkinDraw.line(px,py,px+Math.cos(a+1.3)*2.5,py+Math.sin(a+1.3)*2.5,.7,SkinDraw.alpha(tone,opacity+10));
                }
            }
            if(JutsuSignals.casting) {
                int a=motion?70:50;
                double rotation=motion?time*.025:0;
                double radius=18+(motion?HudSoundPulse.value(time)*1.2:0);
                for(int i=0;i<4;i++)SkinDraw.arc(x,y,radius,.7,rotation+i*Math.PI/2,rotation+i*Math.PI/2+.42,SkinDraw.alpha(JutsuSignals.color,a));
            }
            boolean vale=ValeBridge.confirmedAt>JutsuSignals.confirmedAt;
            double age=time-Math.max(JutsuSignals.confirmedAt,ValeBridge.confirmedAt);
            if(motion&&age>=0&&age<12) {
                int frame=(int)(age/2);double r=8+frame*4;
                int opacity=frame<2?135:frame<4?100:frame==4?45:0;
                int tone=frame==0?0xFFFFFF:vale?0x80D6F2:JutsuSignals.color;
                // Additive is local to this pass and restored immediately.
                SkinDraw.arc(x,y,r+1.2,2.5,0,Math.PI*2,SkinDraw.alpha(tone,opacity/4),true);
                SkinDraw.arc(x,y,r,.9,0,Math.PI*2,SkinDraw.alpha(tone,opacity),true);
                for(int i=0;i<6;i++) {
                    double a=i*Math.PI/3;
                    SkinDraw.line(x+Math.cos(a)*r,y+Math.sin(a)*r,x+Math.cos(a)*(r+3),y+Math.sin(a)*(r+3),.8,SkinDraw.alpha(tone,opacity));
                }
            }
        } finally {
            GlStateManager.tryBlendFuncSeparate(770,771,1,0);if(alpha)GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);
        }
    }
}
