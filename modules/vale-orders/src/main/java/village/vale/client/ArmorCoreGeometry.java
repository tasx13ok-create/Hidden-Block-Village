package village.vale.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/** Local original voxel helpers; preserves the canonical VfxDraw frame implementation. */
final class ArmorCoreGeometry {
    private ArmorCoreGeometry(){}
    static double pixel(double n){return Double.isFinite(n)&&Math.abs(n)<=1024?Math.rint(n*16)/16:0;}
    static int shade(int rgb,double level){level=Double.isFinite(level)?Math.max(0,Math.min(1.2,level)):0;return Math.min(255,(int)(((rgb>>16)&255)*level))<<16|Math.min(255,(int)(((rgb>>8)&255)*level))<<8|Math.min(255,(int)((rgb&255)*level));}
    private static final int[][] FACES={{0,2,3,1},{4,5,7,6},{0,1,5,4},{2,6,7,3},{0,4,6,2},{1,3,7,5}};
    private static final double[] SHADE={.85,.55,.42,1.1,.7,1};
    static void cube(double x,double y,double z,double size,int rgb,float alpha){
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||Math.abs(x)>32000000||Math.abs(y)>32000000||Math.abs(z)>32000000||!Double.isFinite(size)||size<=.000001||size>2||!Float.isFinite(alpha)||alpha<=.003f)return;
        Tessellator t=Tessellator.getInstance();BufferBuilder b=t.getBuffer();b.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);double h=size/2;
        for(int face=0;face<6;face++)for(int corner:FACES[face])VfxDraw.vertex(b,x+((corner&1)==0?-h:h),y+((corner&2)==0?-h:h),z+((corner&4)==0?-h:h),shade(rgb,SHADE[face]),alpha);
        t.draw();
    }
}
