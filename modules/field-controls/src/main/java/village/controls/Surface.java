package village.controls;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.opengl.GL11;

/** Original shinobi board material and native-font widgets; no external UI assets. */
final class Surface {
    static final int BACK=0xFFE8DCC0,PANEL=0xFFE0D2B2,WELL=0xFFCABC9A,LINE=0xFF817257;
    static final int INK=0xFF302B24,MUTED=0xFF595044,ACCENT=0xFF245B58,BRASS=0xFF897044,CORD=0xFF8F493C,RED=0xFF883C33,AMBER=0xFF73551F;
    private static java.lang.reflect.Field hudMotion;private static boolean motionChecked;
    static boolean motion(){
        if(Boolean.getBoolean("fieldcontrols.reducedMotion"))return false;
        if(!motionChecked){motionChecked=true;if(Loader.isModLoaded("fieldhud"))try{hudMotion=Class.forName("village.fieldhud.HudConfig",false,Loader.instance().getModClassLoader()).getField("motion");}catch(ReflectiveOperationException|LinkageError ignored){}}
        try{return hudMotion==null||hudMotion.getBoolean(null);}catch(ReflectiveOperationException|LinkageError ignored){return false;}
    }
    static void round(int x,int y,int w,int h,int radius,int color){
        if(w<=0||h<=0)return;int r=Math.max(0,Math.min(radius,Math.min(w,h)/2));if(r==0){Gui.drawRect(x,y,x+w,y+h,color);return;}
        Gui.drawRect(x+r,y,x+w-r,y+h,color);Gui.drawRect(x,y+r,x+w,y+h-r,color);
        float a=(color>>>24)/255f,red=(color>>16&255)/255f,g=(color>>8&255)/255f,b=(color&255)/255f;
        GlStateManager.enableBlend();GlStateManager.disableTexture2D();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        for(int c=0;c<4;c++){double cx=x+(c==0||c==3?r:w-r),cy=y+(c<2?r:h-r);int start=180+c*90;BufferBuilder vb=Tessellator.getInstance().getBuffer();vb.begin(GL11.GL_TRIANGLE_FAN,DefaultVertexFormats.POSITION_COLOR);vb.pos(cx,cy,0).color(red,g,b,a).endVertex();for(int i=0;i<=6;i++){double angle=Math.toRadians(start+90-i*15);vb.pos(cx+Math.cos(angle)*r,cy+Math.sin(angle)*r,0).color(red,g,b,a).endVertex();}Tessellator.getInstance().draw();}
        GlStateManager.enableTexture2D();GlStateManager.disableBlend();GlStateManager.color(1,1,1,1);
    }
    static void panel(int x,int y,int w,int h){round(x,y+3,w,h,4,0x65000000);round(x,y,w,h,4,LINE);round(x+1,y+1,w-2,h-2,3,BACK);Gui.drawRect(x+7,y+1,x+w-7,y+2,0xFF9B8B6A);for(int sx:new int[]{x+5,x+w-6}){Gui.drawRect(sx,y+h-10,sx+1,y+h-5,BRASS);int a=sx==x+5?sx:sx-6;Gui.drawRect(a,y+h-5,a+7,y+h-4,CORD);}}
    static void seal(int x,int y,int color){for(int dx:new int[]{-1,1})for(int dy:new int[]{-1,1}){int a=x+dx*4,b=y+dy*4;Gui.drawRect(Math.min(a,a+dx*3),b,Math.max(a,a+dx*3)+1,b+1,color);Gui.drawRect(a,Math.min(b,b+dy*3),a+1,Math.max(b,b+dy*3)+1,color);}Gui.drawRect(x-1,y-1,x+2,y+2,color);}
    static void board(MenuLayout l,String title,String status,int color){
        panel(l.board.x,l.board.y,l.board.w,l.board.h);seal(l.header.x+10,l.header.y+l.header.h/2,BRASS);Minecraft mc=Minecraft.getMinecraft();mc.fontRenderer.drawString(title,l.header.x+26,l.header.y+(l.header.h-8)/2,INK,false);
        String s=mc.fontRenderer.trimStringToWidth(status,l.header.w/2);mc.fontRenderer.drawString(s,l.header.right()-mc.fontRenderer.getStringWidth(s),l.header.y+(l.header.h-8)/2,color,false);
        int line=l.board.y+(l.compact?29:35);Gui.drawRect(l.board.x+10,line,l.board.right()-10,line+1,LINE);Gui.drawRect(l.board.x+16,line,l.board.x+48,line+1,CORD);Gui.drawRect(l.footer.x,l.footer.y-4,l.footer.right(),l.footer.y-3,LINE);
    }
    static void row(int x,int y,int w,int h,boolean selected,boolean edited){round(x,y,w,h-2,2,selected?0xFFD0C6A7:PANEL);if(edited)Gui.drawRect(x,y+4,x+2,y+h-6,BRASS);Gui.drawRect(x+4,y+h-3,x+w-4,y+h-2,0xFFB7A889);}
    static void scrollbar(MenuLayout.Box track,int total,int capacity,int offset){if(total<=capacity)return;round(track.x,track.y,track.w,track.h,1,WELL);MenuLayout.Box t=MenuLayout.thumb(track,total,capacity,offset);round(t.x,t.y,t.w,t.h,1,BRASS);}
    static final class Button extends GuiButton {
        final Runnable click;final int style;boolean selected,focused,paint=true;String tooltip="",detail="",focusKey="";private final MenuMotion wash=new MenuMotion();private long pressedUntil;
        Button(int id,int x,int y,int w,int h,String label,int style,Runnable click){super(id,x,y,w,h,label);this.style=style;this.click=click;focusKey="button/"+id;}
        Button key(String value){focusKey=value;return this;}
        @Override public boolean mousePressed(Minecraft mc,int mx,int my){boolean hit=super.mousePressed(mc,mx,my);if(hit)pressedUntil=Minecraft.getSystemTime()+90;return hit;}
        @Override public void drawButton(Minecraft mc,int mx,int my,float partial){
            if(!visible)return;hovered=mx>=x&&my>=y&&mx<x+width&&my<y+height;long now=Minecraft.getSystemTime();boolean moving=motion();double e=wash.value(enabled&&(hovered||focused),now,moving);
            if(!paint){if(focused)Gui.drawRect(x+4,y+height-1,x+width-4,y+height,ACCENT);return;}
            int fill=style==2?0xFFC0D0B9:style==3?0xFFDDB9A0:style==4?WELL:0xFFD8CAAA;if(selected)fill=0xFFC8CCAE;fill=MenuMotion.mix(fill,style==3?0xFFD8A78E:0xFFCFD5BA,e);if(style==0&&!selected)fill=MenuMotion.mix(BACK,0xFFDDD1B3,e);if(!enabled)fill=0xFFD1C6AA;round(x,y,width,height,2,fill);
            if(style==4||style==2||style==3)Gui.drawRect(x+2,y,x+width-2,y+1,enabled?0xFF8F8063:0xFFB0A48A);if(selected)Gui.drawRect(x+2,y+3,x+3,y+height-3,BRASS);if(focused)Gui.drawRect(x+4,y+height-2,x+width-4,y+height-1,ACCENT);if(enabled&&moving&&pressedUntil>now)Gui.drawRect(x+3,y+height-3,x+width-3,y+height-2,BRASS);
            int color=!enabled?0xFF756B59:style==2||selected?ACCENT:style==3?RED:INK;String label=mc.fontRenderer.trimStringToWidth(displayString,Math.max(1,width-8));boolean two=!detail.isEmpty()&&height>=32;mc.fontRenderer.drawString(label,x+(width-mc.fontRenderer.getStringWidth(label))/2,y+(height-8)/2-(two?7:0),color,false);if(two){String sub=mc.fontRenderer.trimStringToWidth(detail,Math.max(1,width-12));mc.fontRenderer.drawString(sub,x+(width-mc.fontRenderer.getStringWidth(sub))/2,y+height/2+5,enabled?MUTED:0xFF756B59,false);}GlStateManager.color(1,1,1,1);
        }
    }
}
