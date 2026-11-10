package village.fieldhud;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** GUI-only batched pixel artwork; the enclosing HUD owns blend/depth state. */
public final class SealDraw {
    private SealDraw() {}
    public static void draw(SealArt.Mark mark,int x,int y,int opacity,double sheen) {
        if(opacity<=0)return;opacity=Math.min(255,opacity);
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        GlStateManager.disableTexture2D();GlStateManager.disableAlpha();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);
        for(int row=0;row<16;row++)for(int col=0;col<16;col++)if(mark.pixel(col,row)>0) {
            int rgb=mark.color(col,row,sheen),a=mark.pixel(col,row)==1?opacity*215/255:opacity;
            int r=(rgb>>16)&255,g=(rgb>>8)&255,blue=rgb&255,xx=x+col,yy=y+row;
            b.pos(xx,yy+1,0).color(r,g,blue,a).endVertex();b.pos(xx+1,yy+1,0).color(r,g,blue,a).endVertex();
            b.pos(xx+1,yy,0).color(r,g,blue,a).endVertex();b.pos(xx,yy,0).color(r,g,blue,a).endVertex();
        }
        t.draw();if(texture)GlStateManager.enableTexture2D();if(alpha)GlStateManager.enableAlpha();GlStateManager.color(1,1,1,1);
    }
}
