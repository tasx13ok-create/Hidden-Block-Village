package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/** Original code-drawn paper, ink, headband metal and red spiral seals. No asset copies or FBO. */
final class NarutoInk extends Gui {
    static final int PAPER=0xFFE6D9B9,INK=0xFF292B2E,MUTED=0xFF62625B,ORANGE=0xFFD97836,RED=0xFFA94736,BLUE=0xFF477F9B;
    static void paper(int x,int y,int w,int h){
        drawRect(x+3,y+4,x+w+3,y+h+4,0x65000000);drawRect(x,y,x+w,y+h,INK);
        drawRect(x+1,y+1,x+w-1,y+h-1,0xFF9E8C6E);drawRect(x+3,y+3,x+w-3,y+h-3,PAPER);
        drawRect(x+5,y+5,x+w-5,y+6,0xFFB8A786);drawRect(x+5,y+h-6,x+w-5,y+h-5,0xFFB8A786);
        for(int i=0;i<3;i++){drawRect(x+6+i*2,y+9,x+7+i*2,y+14,0xFF9A6A4F);drawRect(x+w-7-i*2,y+h-14,x+w-6-i*2,y+h-9,0xFF9A6A4F);}
    }
    static void seal(int x,int y,int color){
        int[][] strokes={{0,0,12,2},{10,2,12,12},{0,10,10,12},{0,2,2,10},{3,3,9,5},{7,5,9,9},{3,7,7,9},{3,5,5,7},{11,9,16,11},{14,6,16,9}};
        for(int[] s:strokes)drawRect(x+s[0],y+s[1],x+s[2],y+s[3],color);
    }
    static void title(Layout.Box p,String title,String sub){
        drawRect(p.x+3,p.y+3,p.x+p.w-3,p.y+30,0xFF27333B);drawRect(p.x+3,p.y+29,p.x+p.w-3,p.y+31,ORANGE);
        seal(p.x+11,p.y+10,0xFFDCA665);Minecraft mc=Minecraft.getMinecraft();
        mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(title,Math.max(1,p.w-46)),p.x+34,p.y+7,0xFFF3E5CA,false);
        mc.fontRenderer.drawString(mc.fontRenderer.trimStringToWidth(sub,p.w-46),p.x+34,p.y+19,0xFFB7C8C8,false);
    }
    static void slot(int x,int y){drawRect(x-1,y-1,x+17,y+17,0xFF746B58);drawRect(x,y,x+16,y+16,0xFFC7BA9B);drawRect(x+1,y+1,x+16,y+16,0xFFD7CBAE);}
    static final class Button extends GuiButton {
        boolean focused,selected;
        Button(int id,Layout.Box b,String text){super(id,b.x,b.y,b.w,b.h,text);}
        @Override public void drawButton(Minecraft mc,int mx,int my,float partial){
            if(!visible)return;hovered=mx>=x&&my>=y&&mx<x+width&&my<y+height;
            int fill=!enabled?0xFFC4BAA2:selected?0xFF315369:hovered||focused?0xFFB76B3D:0xFFD4C7A9;
            drawRect(x,y,x+width,y+height,0xFF79705E);drawRect(x+1,y+1,x+width-1,y+height-1,fill);
            if(focused)drawRect(x+2,y+height-3,x+width-2,y+height-1,RED);
            String label=mc.fontRenderer.trimStringToWidth(displayString,Math.max(1,width-6));
            mc.fontRenderer.drawString(label,x+(width-mc.fontRenderer.getStringWidth(label))/2,y+(height-8)/2,!enabled?MUTED:selected||hovered||focused?0xFFF9F0DC:INK,false);
            GlStateManager.color(1,1,1,1);
        }
        @Override public void playPressSound(SoundHandler sound){if(HudConfig.menuSounds)super.playPressSound(sound);}
    }
}
