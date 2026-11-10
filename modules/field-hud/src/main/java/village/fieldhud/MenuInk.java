package village.fieldhud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/** Original ink, paper, brass and cord primitives with the native font. */
final class MenuInk extends Gui {
    static final int INK=0xFFE1D1AF,MUTED=0xFFA8B1AD,TEAL=0xFF9DDDDC,BRASS=0xFFC0A779;
    static void panel(int x,int y,int w,int h){
        drawRect(x+2,y+3,x+w+2,y+h+3,0x65000000);
        drawRect(x,y,x+w,y+h,0xFF415054);drawRect(x+1,y+1,x+w-1,y+h-1,0xFF1B2428);
        drawRect(x+5,y+1,x+w-5,y+2,0xFF657271);
        for(int side=0;side<2;side++){
            int cx=side==0?x+7:x+w-8;
            drawRect(cx,y+5,cx+1,y+12,BRASS);drawRect(cx-2,y+7,cx+3,y+8,0xFFB26155);
            drawRect(cx,y+h-12,cx+1,y+h-5,BRASS);drawRect(cx-2,y+h-8,cx+3,y+h-7,0xFFB26155);
        }
    }
    static void header(int x,int y,int w,String title){
        Minecraft.getMinecraft().fontRenderer.drawString(title,x+12,y+11,INK,false);
        drawRect(x+10,y+26,x+w-10,y+27,0xFF415054);
        drawRect(x+16,y+26,x+40,y+27,BRASS);drawRect(x+w-40,y+26,x+w-16,y+27,BRASS);
    }
    static void row(int x,int y,int w,int h,boolean active){drawRect(x,y,x+w,y+h,active?0xFF304348:0xFF202C31);drawRect(x+3,y+h-1,x+w-3,y+h,0xFF29383D);}
    static int mix(int a,int b,double p){p=Math.max(0,Math.min(1,p));int r=0;for(int shift:new int[]{0,8,16,24})r|=((int)Math.round(((a>>>shift)&255)*(1-p)+((b>>>shift)&255)*p))<<shift;return r;}
    static final class Button extends GuiButton {
        boolean focused;private double wash;private long last;
        Button(int id,int x,int y,int w,int h,String label){super(id,x,y,w,h,label);}
        @Override public void drawButton(Minecraft mc,int mx,int my,float partial){
            if(!visible)return;hovered=mx>=x&&my>=y&&mx<x+width&&my<y+height;
            long now=Minecraft.getSystemTime();double target=enabled&&(hovered||focused)?1:0;
            if(!HudConfig.motion||HudConfig.mode==2||last==0)wash=target;else wash+=(target-wash)*Math.min(1,Math.max(0,now-last)/140.0);
            last=now;int fill=enabled?mix(0xFF131D22,0xFF40575A,wash):0xFF20292D;
            drawRect(x,y,x+width,y+height,fill);drawRect(x+2,y,x+width-2,y+1,enabled?0xFF566563:0xFF364144);
            if(focused)drawRect(x+3,y+height-2,x+width-3,y+height-1,TEAL);
            String text=mc.fontRenderer.trimStringToWidth(displayString,Math.max(1,width-8));
            mc.fontRenderer.drawString(text,x+(width-mc.fontRenderer.getStringWidth(text))/2,y+(height-8)/2,enabled?INK:0xFF77837E,false);
            GlStateManager.color(1,1,1,1);
        }
    }
}
