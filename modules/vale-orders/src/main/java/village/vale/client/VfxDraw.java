package village.vale.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

/**
 * Shared untextured full-bright drawing primitives for procedural effects. Always bracket calls
 * with {@link #begin()} / {@link #end()} so GL state is restored for vanilla rendering.
 */
@SideOnly(Side.CLIENT)
public final class VfxDraw {
    private VfxDraw() {}
    private static final java.util.Deque<Frame> FRAMES=new java.util.ArrayDeque<>();
    private static final class Frame {
        final float lightX=OpenGlHelper.lastBrightnessX,lightY=OpenGlHelper.lastBrightnessY;
        final double x=camX,y=camY,z=camZ;
        final boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),
            cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),blend=GL11.glIsEnabled(GL11.GL_BLEND),depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final boolean separate=GLContext.getCapabilities().OpenGL14||OpenGlHelper.extBlendFuncSeparate;
        final int src=GL11.glGetInteger(GL11.GL_BLEND_SRC),dst=GL11.glGetInteger(GL11.GL_BLEND_DST),
            srcAlpha=separate?GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA):src,dstAlpha=separate?GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA):dst;
    }
    /** Camera position in the local frame of the current effect (set by {@link #begin(double, double, double)}). */
    private static double camX, camY, camZ;
    /** World units per "pixel width" unit, so line widths look the same on every driver. */
    public static final double WIDTH_SCALE = 0.0075;

    /** Starts an effect whose local origin is the given camera-relative render offset. */
    public static void begin(double x, double y, double z) {
        begin();
        GlStateManager.translate(x, y, z);
        camX = -x; camY = -y; camZ = -z;
    }

    public static void begin() {
        FRAMES.push(new Frame());
        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_LINE_BIT | GL11.GL_CURRENT_BIT);
        camX=camY=camZ=0;
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableBlend();
        normal();
        GlStateManager.depthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
    }

    public static void end() {
        if(FRAMES.isEmpty())return;
        Frame f=FRAMES.pop();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,f.lightX,f.lightY);
        GL11.glPopAttrib();
        GlStateManager.depthMask(f.depthWrite);
        GlStateManager.tryBlendFuncSeparate(f.src,f.dst,f.srcAlpha,f.dstAlpha);
        if(f.texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
        if(f.lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
        if(f.cull)GlStateManager.enableCull();else GlStateManager.disableCull();
        if(f.blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();
        GlStateManager.resetColor();
        camX=f.x;camY=f.y;camZ=f.z;
        GlStateManager.popMatrix();
    }

    public static void additive() { GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); }
    /** Coloured translucency that keeps saturation in daylight (bodies, auras). */
    public static void normal() { GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA); }

    static void vertex(BufferBuilder b, double x, double y, double z, int rgb, float a) {
        b.pos(x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) (Math.max(0, Math.min(1, a)) * 255)).endVertex();
    }

    public static void sphere(double x, double y, double z, double r, int rgb, float alpha, int steps) {
        if (r <= 0.001 || alpha <= 0.003) return;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * i / steps - Math.PI / 2, a1 = Math.PI * (i + 1) / steps - Math.PI / 2;
            for (int j = 0; j < steps * 2; j++) {
                double o0 = Math.PI * j / steps, o1 = Math.PI * (j + 1) / steps;
                vertex(b, x + r * Math.cos(a0) * Math.cos(o0), y + r * Math.sin(a0), z + r * Math.cos(a0) * Math.sin(o0), rgb, alpha);
                vertex(b, x + r * Math.cos(a0) * Math.cos(o1), y + r * Math.sin(a0), z + r * Math.cos(a0) * Math.sin(o1), rgb, alpha);
                vertex(b, x + r * Math.cos(a1) * Math.cos(o1), y + r * Math.sin(a1), z + r * Math.cos(a1) * Math.sin(o1), rgb, alpha);
                vertex(b, x + r * Math.cos(a1) * Math.cos(o0), y + r * Math.sin(a1), z + r * Math.cos(a1) * Math.sin(o0), rgb, alpha);
            }
        }
        t.draw();
    }

    /** {right, up} unit vectors of a plane facing the camera at local point (x,y,z). */
    public static double[][] facing(double x, double y, double z) {
        double[] to = norm(new double[] {camX - x, camY - y, camZ - z}, new double[] {0, 0, 1});
        double[] right = norm(cross(new double[] {0, 1, 0}, to), new double[] {1, 0, 0});
        return new double[][] {right, cross(to, right)};
    }

    /** Soft round camera-facing glow: bright centre fading to a transparent rim. */
    public static void billboard(RenderManager rm, double x, double y, double z, double size, int rgb, float alpha) {
        if (size <= 0.0005 || alpha <= 0.003) return;
        double[][] f = facing(x, y, z);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vertex(b, x, y, z, rgb, alpha);
        for (int i = 0; i <= 12; i++) {
            double a = i * Math.PI / 6, c = Math.cos(a) * size, s = Math.sin(a) * size;
            vertex(b, x + f[0][0] * c + f[1][0] * s, y + f[0][1] * c + f[1][1] * s, z + f[0][2] * c + f[1][2] * s, rgb, 0);
        }
        t.draw();
    }

    /**
     * Polyline drawn as a camera-facing ribbon (GL line widths are capped at 1px on many drivers),
     * with alpha fading from a0 to a1 along its length. {@code width} is in "pixel" units.
     */
    public static void line(double[][] pts, int rgb, float a0, float a1, float width) {
        if (pts.length < 2 || Math.max(a0, a1) <= 0.003) return;
        double half = width * WIDTH_SCALE * 0.5;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        double[] side = {half, 0, 0};
        for (int i = 0; i < pts.length; i++) {
            double[] p = pts[i], prev = pts[Math.max(0, i - 1)], next = pts[Math.min(pts.length - 1, i + 1)];
            double[] tan = {next[0] - prev[0], next[1] - prev[1], next[2] - prev[2]};
            double[] to = {camX - p[0], camY - p[1], camZ - p[2]};
            double[] c = cross(tan, to);
            double n = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
            if (n > 1e-9) side = new double[] {c[0] / n * half, c[1] / n * half, c[2] / n * half};
            float a = a0 + (a1 - a0) * (i / (float) (pts.length - 1));
            vertex(b, p[0] - side[0], p[1] - side[1], p[2] - side[2], rgb, a);
            vertex(b, p[0] + side[0], p[1] + side[1], p[2] + side[2], rgb, a);
        }
        t.draw();
    }

    static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double[] norm(double[] v, double[] fallback) {
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return n < 1e-9 ? fallback : new double[] {v[0] / n, v[1] / n, v[2] / n};
    }

    public static void segment(double[] a, double[] b, int rgb, float alpha, float width) {
        line(new double[][] {a, b}, rgb, alpha, alpha, width);
    }

    /** Filled strip between two matching edges (slash blades, shield arcs). */
    public static void ribbon(double[][] inner, double[][] outer, int rgb, float a0, float a1) {
        int n = Math.min(inner.length, outer.length);
        if (n < 2 || Math.max(a0, a1) <= 0.003) return;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            float f = i / (float) (n - 1), a = a0 + (a1 - a0) * f;
            vertex(b, inner[i][0], inner[i][1], inner[i][2], rgb, a);
            vertex(b, outer[i][0], outer[i][1], outer[i][2], rgb, a);
        }
        t.draw();
    }

    /** Flat horizontal annulus centred at (x,y,z). */
    public static void annulus(double x, double y, double z, double r0, double r1, int rgb, float alpha, int steps) {
        if (r1 <= r0 || alpha <= 0.003) return;
        double[][] in = new double[steps + 1][], out = new double[steps + 1][];
        for (int i = 0; i <= steps; i++) {
            double a = i * Math.PI * 2 / steps;
            in[i] = new double[] {x + Math.cos(a) * r0, y, z + Math.sin(a) * r0};
            out[i] = new double[] {x + Math.cos(a) * r1, y, z + Math.sin(a) * r1};
        }
        ribbon(in, out, rgb, alpha, alpha);
    }

    public static double[][] offset(double[][] pts, double x, double y, double z) {
        double[][] o = new double[pts.length][];
        for (int i = 0; i < pts.length; i++) o[i] = new double[] {pts[i][0] + x, pts[i][1] + y, pts[i][2] + z};
        return o;
    }
}
