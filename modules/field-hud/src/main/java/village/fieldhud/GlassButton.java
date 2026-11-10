package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import java.lang.ref.WeakReference;

/** Compact settings controls that share the in-world material language. */
public final class GlassButton extends GuiButton {
    private static WeakReference<net.minecraft.client.gui.GuiScreen> clickedScreen=new WeakReference<>(null);
    private static int clickedId,clickedX,clickedY;
    private static long clickedAt;
    private long lastFrame;
    private double hover;
    public GlassButton(int id,int x,int y,int w,int h,String text) { super(id,x,y,w,h,text); }
    @Override public void drawButton(Minecraft mc,int mouseX,int mouseY,float partialTicks) {
        if(!visible)return;
        hovered=mouseX>=x&&mouseY>=y&&mouseX<x+width&&mouseY<y+height;
        long now=System.nanoTime();double elapsed=lastFrame==0?0:Math.max(0,(now-lastFrame)/1.0e9);lastFrame=now;
        double goal=enabled&&hovered?1:0;
        hover=HudConfig.motion?SkinMath.ease(hover,goal,elapsed,.075):goal;
        double press=enabled&&clickedScreen.get()==mc.currentScreen&&clickedId==id&&clickedX==x&&clickedY==y?Math.max(0,1-(now-clickedAt)/1.6e8):0;
        if(!HudConfig.motion)press=0;
        int fill=!enabled?0x40344350:blend(0x70435464,0xA06D8797,hover);
        SkinDraw.rounded(x,y,width,height,4,fill);
        SkinDraw.outline(x,y,width,height,4,.6,!enabled?0x305E7180:blend(0x457F97A7,0x9AC7DEE9,hover));
        if(enabled&&(hover>0.01||press>0)) {
            boolean alpha=org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);GlStateManager.disableAlpha();
            SkinDraw.line(x+5,y+1.1,x+width-5,y+1.1,.6,SkinDraw.alpha(0xDBF0F6,(int)(hover*60+press*70)));
            if(alpha)GlStateManager.enableAlpha();
        }
        GlStateManager.color(1,1,1,1);
        drawCenteredString(mc.fontRenderer,displayString,x+width/2,y+(height-8)/2,enabled?0xE7F0F5:0x8394A0);
    }
    @Override public boolean mousePressed(Minecraft mc,int mouseX,int mouseY) {
        boolean accepted=super.mousePressed(mc,mouseX,mouseY);
        if(accepted){clickedScreen=new WeakReference<>(mc.currentScreen);clickedId=id;clickedX=x;clickedY=y;clickedAt=System.nanoTime();}
        return accepted;
    }
    private static int blend(int a,int b,double t) {
        int result=0;for(int shift=0;shift<=24;shift+=8)result|=((int)Math.round(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;return result;
    }
}
