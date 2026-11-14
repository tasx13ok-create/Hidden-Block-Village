package net.minecraft.client.renderer;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

/** Stateful recording API; resetColor invalidates cache, it does not whiten native GL. */
public final class GlStateManager {
    public enum SourceFactor { SRC_ALPHA }
    public enum DestFactor { ONE,ONE_MINUS_SRC_ALPHA }
    public static double[] translation={0,0,0};
    public static final Deque<double[]> matrices=new ArrayDeque<>();
    private static float[] cachedColor={-1,-1,-1,-1};
    public static int colorWrites,colorInvalidations;
    public static void enableTexture2D() { GL11.enabled.add(GL11.GL_TEXTURE_2D); }
    public static void disableTexture2D() { GL11.enabled.remove(GL11.GL_TEXTURE_2D); }
    public static void enableLighting() { GL11.enabled.add(GL11.GL_LIGHTING); }
    public static void disableLighting() { GL11.enabled.remove(GL11.GL_LIGHTING); }
    public static void enableCull() { GL11.enabled.add(GL11.GL_CULL_FACE); }
    public static void disableCull() { GL11.enabled.remove(GL11.GL_CULL_FACE); }
    public static void enableBlend() { GL11.enabled.add(GL11.GL_BLEND); }
    public static void disableBlend() { GL11.enabled.remove(GL11.GL_BLEND); }
    public static void depthMask(boolean value) { GL11.depthWrite=value; }
    public static void blendFunc(SourceFactor source,DestFactor dest) {
        int d=dest==DestFactor.ONE?GL11.GL_ONE:GL11.GL_ONE_MINUS_SRC_ALPHA;
        setBlend(GL11.GL_SRC_ALPHA,d,GL11.GL_SRC_ALPHA,d);
    }
    public static void tryBlendFuncSeparate(int a,int b,int c,int d) {
        if (!GLContext.CAPS.OpenGL14 && !OpenGlHelper.extBlendFuncSeparate) { c=a; d=b; }
        setBlend(a,b,c,d);
    }
    private static void setBlend(int a,int b,int c,int d) {
        GL11.ints.put(GL11.GL_BLEND_SRC,a); GL11.ints.put(GL11.GL_BLEND_DST,b);
        GL11.ints.put(GL14.GL_BLEND_SRC_ALPHA,c); GL11.ints.put(GL14.GL_BLEND_DST_ALPHA,d);
    }
    public static void pushMatrix() { matrices.push(translation.clone()); }
    public static void popMatrix() {
        if (matrices.isEmpty()) throw new AssertionError("matrix stack underflow");
        translation=matrices.pop();
    }
    public static void translate(double x,double y,double z) {
        translation=new double[]{translation[0]+x,translation[1]+y,translation[2]+z};
    }
    public static void color(float r,float g,float b,float a) {
        float[] next={r,g,b,a};
        if (!Arrays.equals(cachedColor,next)) { GL11.color=next.clone(); cachedColor=next; colorWrites++; }
    }
    public static void resetColor() { cachedColor=new float[]{-1,-1,-1,-1}; colorInvalidations++; }
    public static String matrixSnapshot() { return Arrays.toString(translation)+"/depth="+matrices.size(); }
}
