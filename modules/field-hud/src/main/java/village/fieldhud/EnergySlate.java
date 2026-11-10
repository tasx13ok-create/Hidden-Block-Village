package village.fieldhud;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Pixel-clipped 45-degree slates. One batched draw, no shared stencil/FBO writes. */
public final class EnergySlate {
    private EnergySlate() {}
    private static void quad(BufferBuilder b,double x,double y,double w,double h,int color) {
        int a=color>>>24,r=(color>>16)&255,g=(color>>8)&255,bl=color&255;
        b.pos(x,y+h,0).color(r,g,bl,a).endVertex();b.pos(x+w,y+h,0).color(r,g,bl,a).endVertex();
        b.pos(x+w,y,0).color(r,g,bl,a).endVertex();b.pos(x,y,0).color(r,g,bl,a).endVertex();
    }
    public static void draw(int x,int y,int width,int height,double ratio,double trail,int color,int trailColor,double ticks,boolean flow) {
        GlStateManager.disableTexture2D();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);GlStateManager.disableAlpha();
        Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        int filled=(int)Math.round(width*Math.max(0,Math.min(1,ratio))),last=(int)Math.round(width*Math.max(ratio,Math.min(1,trail)));
        int frame=flow?EnergyFrames.frame(ticks):0;
        for(int row=0;row<height;row++) {
            int left=EnergyFrames.left(height,row),right=EnergyFrames.right(width,row);
            if(right<=left)continue;
            quad(b,x+left-1,y+row,right-left+2,1,0x504D626E);
            quad(b,x+left,y+row,right-left,1,0xC0182029);
            int end=Math.min(right,filled),ghost=Math.min(right,last);
            if(ghost>left)quad(b,x+left,y+row,ghost-left,1,SkinDraw.alpha(trailColor,100));
            if(end>left)quad(b,x+left,y+row,end-left,1,color);
            if(flow)for(int column=left;column<end;column++) {
                if(row==height-1-EnergyFrames.crest(frame,column))quad(b,x+column,y+row,1,1,0x65C3EDFF);
                if(EnergyFrames.fragment(frame,column,row))quad(b,x+column,y+row,1,1,0xB5E5F7FF);
            }
            else if(row==1&&end-left>2)quad(b,x+left+1,y+row,end-left-2,1,0x30FFF0EA);
        }
        t.draw();if(alpha)GlStateManager.enableAlpha();GlStateManager.enableTexture2D();GlStateManager.color(1,1,1,1);
    }
}
