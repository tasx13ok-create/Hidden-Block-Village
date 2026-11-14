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
    /**
     * Voxel mode: lines, spheres, ribbons and glows are rebuilt from face-shaded pixel cubes on a
     * 1/16-block grid so effects look like Minecraft blocks and particles instead of smooth GL.
     */
    public static boolean voxel;
    private static final double PX = 1 / 16.0;

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
        if (voxel) { voxelSphere(x, y, z, r, rgb, alpha); return; }
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
        if (voxel) { cube(pixel(x), pixel(y), pixel(z), Math.max(PX, Math.round(size * 1.2 / PX) * PX), rgb, alpha); return; }
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
        if (voxel) { voxelLine(pts, rgb, a0, a1, width); return; }
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
        if (voxel) { voxelRibbon(inner, outer, n, rgb, a0, a1); return; }
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

    /**
     * Axis-aligned voxel cube with Minecraft-style face shading (top 100%, north/south 80%,
     * east/west 60%, bottom 50%), so pixel effects read like blocks and particles.
     */
    public static void cube(double x, double y, double z, double size, int rgb, float alpha) {
        if (size <= 0.0005 || alpha <= 0.003) return;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        cubeInto(b, x, y, z, size, rgb, alpha);
        t.draw();
    }

    private static final java.util.HashSet<Long> SEEN = new java.util.HashSet<>();

    private static long key(double x, double y, double z) {
        return ((Math.round(x / PX) & 0x1FFFFFL) << 42) | ((Math.round(y / PX) & 0x1FFFFFL) << 21) | (Math.round(z / PX) & 0x1FFFFFL);
    }

    /** Polyline as a chain of grid-snapped cubes (thicker width -> bigger cubes). */
    private static void voxelLine(double[][] pts, int rgb, float a0, float a1, float width) {
        double size = Math.max(PX, Math.round(width * WIDTH_SCALE * 1.15 / PX) * PX), step = Math.max(PX, size * 0.9);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        SEEN.clear();
        double total = 0;
        for (int i = 1; i < pts.length; i++) total += dist(pts[i - 1], pts[i]);
        double run = 0;
        for (int i = 1; i < pts.length; i++) {
            double[] p = pts[i - 1], q = pts[i];
            double len = dist(p, q);
            int n = Math.max(1, (int) Math.ceil(len / step));
            for (int k = 0; k < n; k++) {
                double f = k / (double) n, s = total < 1e-9 ? 0 : (run + len * f) / total;
                double x = p[0] + (q[0] - p[0]) * f, y = p[1] + (q[1] - p[1]) * f, z = p[2] + (q[2] - p[2]) * f;
                // Snap to a grid matching the cube size so neighbouring cubes tile like blocks.
                double gx = Math.round(x / size) * size, gy = Math.round(y / size) * size, gz = Math.round(z / size) * size;
                if (!SEEN.add(key(gx, gy, gz))) continue;
                cubeInto(b, gx, gy, gz, size, rgb, a0 + (a1 - a0) * (float) s);
            }
            run += len;
        }
        t.draw();
    }

    /** Hollow voxel shell (plus sparse interior for small orbs), cube size scaled to radius. */
    private static void voxelSphere(double x, double y, double z, double r, int rgb, float alpha) {
        double size = Math.max(PX, Math.round(Math.max(r / 5, PX) / PX) * PX);
        double cx = Math.round(x / size) * size, cy = Math.round(y / size) * size, cz = Math.round(z / size) * size;
        int n = (int) Math.ceil(r / size);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (int i = -n; i <= n; i++) for (int j = -n; j <= n; j++) for (int k = -n; k <= n; k++) {
            double d = Math.sqrt(i * i + j * j + k * k) * size;
            if (d > r || (n > 2 && d < r - size * 1.1)) continue;
            cubeInto(b, cx + i * size, cy + j * size, cz + k * size, size, rgb, alpha);
        }
        t.draw();
    }

    /** Filled strip as a grid of cubes sampled between the two edges. */
    private static void voxelRibbon(double[][] inner, double[][] outer, int n, int rgb, float a0, float a1) {
        double width = 0;
        for (int i = 0; i < n; i++) width = Math.max(width, dist(inner[i], outer[i]));
        double size = Math.max(PX, Math.round(Math.max(PX, Math.min(0.12, width / 3)) / PX) * PX);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        SEEN.clear();
        for (int i = 0; i < n - 1; i++) {
            float a = a0 + (a1 - a0) * (i / (float) (n - 1));
            if (a <= 0.01) continue;
            int along = Math.max(1, (int) Math.ceil(dist(outer[i], outer[i + 1]) / size));
            for (int u = 0; u < along; u++) {
                double f = u / (double) along;
                double[] in = lerp(inner[i], inner[i + 1], f), out = lerp(outer[i], outer[i + 1], f);
                int across = Math.max(1, (int) Math.ceil(dist(in, out) / size));
                for (int v = 0; v <= across; v++) {
                    double[] p = lerp(in, out, v / (double) across);
                    double gx = Math.round(p[0] / size) * size, gy = Math.round(p[1] / size) * size, gz = Math.round(p[2] / size) * size;
                    if (!SEEN.add(key(gx, gy, gz))) continue;
                    cubeInto(b, gx, gy, gz, size, rgb, a);
                }
            }
        }
        t.draw();
    }

    private static double dist(double[] a, double[] b) { double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2]; return Math.sqrt(x * x + y * y + z * z); }
    private static double[] lerp(double[] a, double[] b, double f) { return new double[] {a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f}; }

    static void cubeInto(BufferBuilder b, double x, double y, double z, double size, int rgb, float alpha) {
        if (alpha <= 0.003) return;
        double h = size / 2, x0 = x - h, x1 = x + h, y0 = y - h, y1 = y + h, z0 = z - h, z1 = z + h;
        int top = rgb, ns = shade(rgb, 0.8), ew = shade(rgb, 0.6), bot = shade(rgb, 0.5);
        vertex(b, x0, y1, z0, top, alpha); vertex(b, x0, y1, z1, top, alpha); vertex(b, x1, y1, z1, top, alpha); vertex(b, x1, y1, z0, top, alpha);
        vertex(b, x0, y0, z0, bot, alpha); vertex(b, x1, y0, z0, bot, alpha); vertex(b, x1, y0, z1, bot, alpha); vertex(b, x0, y0, z1, bot, alpha);
        vertex(b, x0, y0, z0, ns, alpha); vertex(b, x0, y1, z0, ns, alpha); vertex(b, x1, y1, z0, ns, alpha); vertex(b, x1, y0, z0, ns, alpha);
        vertex(b, x0, y0, z1, ns, alpha); vertex(b, x1, y0, z1, ns, alpha); vertex(b, x1, y1, z1, ns, alpha); vertex(b, x0, y1, z1, ns, alpha);
        vertex(b, x0, y0, z0, ew, alpha); vertex(b, x0, y0, z1, ew, alpha); vertex(b, x0, y1, z1, ew, alpha); vertex(b, x0, y1, z0, ew, alpha);
        vertex(b, x1, y0, z0, ew, alpha); vertex(b, x1, y1, z0, ew, alpha); vertex(b, x1, y1, z1, ew, alpha); vertex(b, x1, y0, z1, ew, alpha);
    }

    public static int shade(int rgb, double f) {
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), bl = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    /** Snaps a coordinate to a 1/16-block pixel grid. */
    public static double pixel(double v) { return Math.round(v * 16) / 16.0; }

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
