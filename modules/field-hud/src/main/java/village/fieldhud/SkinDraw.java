package village.fieldhud;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Small immediate GUI primitives; no world/core shader replacements. */
public final class SkinDraw extends Gui {
    public static int alpha(int rgb,int alpha) { return (Math.max(0,Math.min(255,alpha))<<24)|(rgb&0xFFFFFF); }
    public static void rounded(double x,double y,double w,double h,double radius,int color) {
        if(w<=0||h<=0)return;
        double r=Math.max(0,Math.min(radius,Math.min(w,h)/2));
        GlStateManager.disableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);GlStateManager.disableAlpha();
        Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();
        int a=color>>>24,red=(color>>16)&255,g=(color>>8)&255,bl=color&255;
        b.begin(GL11.GL_TRIANGLE_FAN,DefaultVertexFormats.POSITION_COLOR);
        b.pos(x+w/2,y+h/2,0).color(red,g,bl,a).endVertex();
        for(int corner=0;corner<4;corner++)for(int i=0;i<=8;i++) {
            // GUI Y points down. This order remains front-facing with Minecraft's
            // normal GL culling enabled: top-left, bottom-left, bottom-right, top-right.
            double angle=-Math.PI/2-corner*Math.PI/2-i*Math.PI/16;
            double cx=corner<2?x+r:x+w-r,cy=corner==1||corner==2?y+h-r:y+r;
            b.pos(cx+Math.cos(angle)*r,cy+Math.sin(angle)*r,0).color(red,g,bl,a).endVertex();
        }
        b.pos(x+r,y,0).color(red,g,bl,a).endVertex();t.draw();
        if(alpha)GlStateManager.enableAlpha();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
    }
    public static void outline(double x,double y,double w,double h,double radius,double thickness,int color) {
        double r=Math.max(thickness,Math.min(radius,Math.min(w,h)/2));
        for(int corner=0;corner<4;corner++) {
            double cx=corner<2?x+w-r:x+r,cy=corner==1||corner==2?y+h-r:y+r;
            arc(cx,cy,r,thickness,-Math.PI/2+corner*Math.PI/2,corner*Math.PI/2,color);
        }
        line(x+r,y+thickness/2,x+w-r,y+thickness/2,thickness,color);
        line(x+w-thickness/2,y+r,x+w-thickness/2,y+h-r,thickness,color);
        line(x+r,y+h-thickness/2,x+w-r,y+h-thickness/2,thickness,color);
        line(x+thickness/2,y+r,x+thickness/2,y+h-r,thickness,color);
    }
    public static void ring(double x,double y,double radius,double thickness,int color) {
        arc(x,y,radius,thickness,0,Math.PI*2,color);
    }
    public static void arc(double x,double y,double radius,double thickness,double start,double end,int color) {
        arc(x,y,radius,thickness,start,end,color,false);
    }
    public static void arc(double x,double y,double radius,double thickness,double start,double end,int color,boolean additive) {
        int steps=Math.max(6,(int)Math.ceil(Math.abs(end-start)*radius/2));
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,additive?1:771,1,0);
        Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();
        b.begin(GL11.GL_QUAD_STRIP,DefaultVertexFormats.POSITION_COLOR);
        int a=color>>>24,r=(color>>16)&255,g=(color>>8)&255,bl=color&255;
        for(int i=0;i<=steps;i++) {
            double v=start+(end-start)*i/steps,c=Math.cos(v),s=Math.sin(v);
            b.pos(x+c*radius,y+s*radius,0).color(r,g,bl,a).endVertex();
            b.pos(x+c*(radius-thickness),y+s*(radius-thickness),0).color(r,g,bl,a).endVertex();
        }
        t.draw();if(additive)GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
    }
    public static void disc(double x,double y,double radius,int color) {
        ring(x,y,radius,radius,color);
    }
    public static void line(double x0,double y0,double x1,double y1,double thickness,int color) {
        double dx=x1-x0,dy=y1-y0,len=Math.hypot(dx,dy);if(len<0.001)return;
        double nx=-dy/len*thickness/2,ny=dx/len*thickness/2;
        GlStateManager.disableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();
        b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        int a=color>>>24,r=(color>>16)&255,g=(color>>8)&255,bl=color&255;
        b.pos(x0+nx,y0+ny,0).color(r,g,bl,a).endVertex();b.pos(x1+nx,y1+ny,0).color(r,g,bl,a).endVertex();
        b.pos(x1-nx,y1-ny,0).color(r,g,bl,a).endVertex();b.pos(x0-nx,y0-ny,0).color(r,g,bl,a).endVertex();
        t.draw();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
    }
}
