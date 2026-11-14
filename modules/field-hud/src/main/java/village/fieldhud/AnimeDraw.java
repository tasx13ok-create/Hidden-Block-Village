package village.fieldhud;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Per-vertex-colour GUI primitives with optional additive blending, for layered anime-style HUD
 * art: gradients, radial glows, flame tongues, spirals, ribbons and brush blobs. Every call
 * restores normal alpha blending and texturing afterwards so vanilla/skin drawing is unaffected.
 */
public final class AnimeDraw {
    private AnimeDraw() {}
    private static final java.util.Deque<State> STATES=new java.util.ArrayDeque<>();
    /** Preserves actual incoming GL state. Use around every whole HUD/menu effect pass. */
    public static final class Scope implements AutoCloseable{
        private final State state=new State();private final int depth=STATES.size(),matrix=GL11.glGetInteger(GL11.GL_MATRIX_MODE);private boolean closed;
        public Scope(){GlStateManager.pushMatrix();}
        @Override public void close(){if(closed)return;closed=true;while(STATES.size()>depth)STATES.pop().restore();GlStateManager.matrixMode(matrix);GlStateManager.popMatrix();state.restore();}
    }
    private static boolean separate(){return org.lwjgl.opengl.GLContext.getCapabilities().OpenGL14||net.minecraft.client.renderer.OpenGlHelper.extBlendFuncSeparate;}
    private static final class State{
        final boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),blend=GL11.glIsEnabled(GL11.GL_BLEND),alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),cull=GL11.glIsEnabled(GL11.GL_CULL_FACE),write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),light0=GL11.glIsEnabled(GL11.GL_LIGHT0),light1=GL11.glIsEnabled(GL11.GL_LIGHT1);
        final int shade=GL11.glGetInteger(GL11.GL_SHADE_MODEL),source=GL11.glGetInteger(GL11.GL_BLEND_SRC),dest=GL11.glGetInteger(GL11.GL_BLEND_DST),sourceAlpha=separate()?GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA):source,destAlpha=separate()?GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA):dest,textureId=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D),active=GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE);
        final java.nio.FloatBuffer color=org.lwjgl.BufferUtils.createFloatBuffer(4);
        State(){GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);}
        void restore(){GlStateManager.setActiveTexture(active);GlStateManager.bindTexture(textureId);GlStateManager.shadeModel(shade);GlStateManager.tryBlendFuncSeparate(source,dest,sourceAlpha,destAlpha);GlStateManager.depthMask(write);
            if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();if(light0)GlStateManager.enableLight(0);else GlStateManager.disableLight(0);if(light1)GlStateManager.enableLight(1);else GlStateManager.disableLight(1);GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3));
        }
    }


    private static BufferBuilder begin(int mode, boolean additive) {
        if(STATES.size()>=8)throw new IllegalStateException("Nested anime draw budget exceeded");STATES.push(new State());
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, additive ? GL11.GL_ONE : GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        BufferBuilder b = Tessellator.getInstance().getBuffer();
        b.begin(mode, DefaultVertexFormats.POSITION_COLOR);
        return b;
    }

    private static void end(){try{Tessellator.getInstance().draw();}finally{if(!STATES.isEmpty())STATES.pop().restore();}}

    private static void v(BufferBuilder b, double x, double y, int argb) {
        b.pos(x, y, 0).color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24) & 255).endVertex();
    }

    public static int argb(int rgb, double alpha) { return ((int) (Math.max(0, Math.min(1, alpha)) * 255) << 24) | (rgb & 0xFFFFFF); }

    /** Linear blend of two RGB colours. */
    public static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * t));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * t));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * t));
        return (r << 16) | (g << 8) | bl;
    }

    /** Quad with independent corner colours (top-left, top-right, bottom-right, bottom-left). */
    public static void gradient(double x, double y, double w, double h, int tl, int tr, int br, int bl, boolean additive) {
        if (w <= 0 || h <= 0) return;
        BufferBuilder b = begin(GL11.GL_QUADS, additive);
        v(b, x, y + h, bl); v(b, x + w, y + h, br); v(b, x + w, y, tr); v(b, x, y, tl);
        end();
    }

    public static void vertical(double x, double y, double w, double h, int top, int bottom, boolean additive) { gradient(x, y, w, h, top, top, bottom, bottom, additive); }

    /** Elliptical radial glow: centre colour fading to transparent at the rim. */
    public static void glow(double cx, double cy, double rx, double ry, int rgb, double alpha, boolean additive) {
        if (alpha <= 0.003 || rx <= 0 || ry <= 0) return;
        BufferBuilder b = begin(GL11.GL_TRIANGLE_FAN, additive);
        v(b, cx, cy, argb(rgb, alpha));
        for (int i = 24; i >= 0; i--) { double a = i * Math.PI * 2 / 24; v(b, cx + Math.cos(a) * rx, cy + Math.sin(a) * ry, argb(rgb, 0)); }
        end();
    }

    /** Glowing ring band (inner/outer edges transparent, bright middle). */
    public static void halo(double cx, double cy, double r, double width, int rgb, double alpha, boolean additive) {
        if (alpha <= 0.003) return;
        BufferBuilder b = begin(GL11.GL_TRIANGLE_STRIP, additive);
        for (int i = 0; i <= 32; i++) {
            double a = i * Math.PI * 2 / 32, c = Math.cos(a), s = Math.sin(a);
            v(b, cx + c * (r - width), cy + s * (r - width), argb(rgb, 0));
            v(b, cx + c * r, cy + s * r, argb(rgb, alpha));
        }
        end();
        b = begin(GL11.GL_TRIANGLE_STRIP, additive);
        for (int i = 0; i <= 32; i++) {
            double a = i * Math.PI * 2 / 32, c = Math.cos(a), s = Math.sin(a);
            v(b, cx + c * r, cy + s * r, argb(rgb, alpha));
            v(b, cx + c * (r + width), cy + s * (r + width), argb(rgb, 0));
        }
        end();
    }

    /** Flame tongue rising from (x, y) to height h: core colour at the base, tip fading out. */
    public static void flame(double x, double y, double w, double h, double sway, int base, int tip, double alpha, boolean additive) {
        if (alpha <= 0.003 || h <= 0) return;
        BufferBuilder b = begin(GL11.GL_TRIANGLE_STRIP, additive);
        int steps = 8;
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps, half = w * 0.5 * Math.sin(Math.PI * (0.15 + 0.85 * (1 - f))) * (1 - f * 0.6);
            double cx = x + Math.sin(f * 3.1 + sway) * w * 0.35 * f, cy = y - h * f;
            int c = argb(mix(base, tip, f), alpha * (1 - f * f));
            v(b, cx - half, cy, c); v(b, cx + half, cy, c);
        }
        end();
    }

    /** Thick polyline with per-point colour (ARGB) and width, as a triangle strip. */
    public static void ribbon(double[] xs, double[] ys, double[] widths, int[] colors, boolean additive) {
        int n = xs.length;
        if (n < 2) return;
        BufferBuilder b = begin(GL11.GL_TRIANGLE_STRIP, additive);
        for (int i = 0; i < n; i++) {
            int p = Math.max(0, i - 1), q = Math.min(n - 1, i + 1);
            double dx = xs[q] - xs[p], dy = ys[q] - ys[p], len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-6) { dx = 1; dy = 0; len = 1; }
            double nx = -dy / len * widths[i] * 0.5, ny = dx / len * widths[i] * 0.5;
            v(b, xs[i] - nx, ys[i] - ny, colors[i]); v(b, xs[i] + nx, ys[i] + ny, colors[i]);
        }
        end();
    }

    /** Swirl seal: an Archimedean spiral with tapering width and alpha, rotated by {@code rot}. */
    public static void spiral(double cx, double cy, double r, double turns, double width, int rgb, double alpha, double rot, boolean additive) {
        int n = (int) (turns * 28);
        double[] xs = new double[n], ys = new double[n], ws = new double[n];
        int[] cs = new int[n];
        for (int i = 0; i < n; i++) {
            double f = i / (n - 1.0), a = rot + f * turns * Math.PI * 2, rr = r * (0.12 + 0.88 * f);
            xs[i] = cx + Math.cos(a) * rr; ys[i] = cy + Math.sin(a) * rr;
            ws[i] = width * (0.35 + 0.65 * f);
            cs[i] = argb(rgb, alpha * (0.25 + 0.75 * Math.sin(Math.PI * Math.min(1, f * 1.15))));
        }
        ribbon(xs, ys, ws, cs, additive);
    }

    /** Organic ink blob (wobbly disc) for brush splashes. */
    public static void blob(double cx, double cy, double r, long seed, int rgb, double alpha) {
        if (alpha <= 0.003 || r <= 0) return;
        BufferBuilder b = begin(GL11.GL_TRIANGLE_FAN, false);
        v(b, cx, cy, argb(rgb, alpha));
        for (int i = 18; i >= 0; i--) {
            double a = i * Math.PI * 2 / 18, k = 0.7 + 0.5 * FableHudMath.noise(seed, i % 18);
            v(b, cx + Math.cos(a) * r * k, cy + Math.sin(a) * r * k, argb(rgb, alpha * 0.85));
        }
        end();
    }

    /** Kunai blade pointing in direction dir (+1 right, -1 left) with a steel gradient and ring pommel. */
    public static void kunai(double tipX, double cy, int dir, double len, double alpha) {
        double baseX = tipX - dir * len;
        BufferBuilder b = begin(GL11.GL_TRIANGLES, false);
        v(b, tipX, cy, argb(0xF4F8FF, alpha)); v(b, baseX, cy - 2.6, argb(0x8C98A8, alpha)); v(b, baseX, cy + 2.6, argb(0x4A5464, alpha));
        end();
        gradient(Math.min(baseX, baseX - dir * 4), cy - 0.8, 4, 1.6, argb(0x3A2A20, alpha), argb(0x3A2A20, alpha), argb(0x1A120C, alpha), argb(0x1A120C, alpha), false);
        SkinDraw.ring(baseX - dir * 5.2, cy, 1.4, 0.6, argb(0xB8C2CE, alpha));
    }
}
