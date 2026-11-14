package org.lwjgl.opengl;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Recording subset of fixed-function GL used by the actual VfxDraw class. */
public final class GL11 {
    public static final int GL_TEXTURE_2D=3553,GL_LIGHTING=2896,GL_CULL_FACE=2884,GL_BLEND=3042,
        GL_DEPTH_TEST=2929,GL_ALPHA_TEST=3008,GL_DEPTH_WRITEMASK=2930,GL_BLEND_SRC=3041,GL_BLEND_DST=3040,
        GL_LINE_BIT=4,GL_CURRENT_BIT=1,GL_QUADS=7,GL_QUAD_STRIP=8,GL_TRIANGLE_FAN=6,
        GL_SRC_ALPHA=770,GL_ONE_MINUS_SRC_ALPHA=771,GL_ONE=1,GL_ZERO=0;
    public static final Set<Integer> enabled = new HashSet<>();
    public static final Map<Integer,Integer> ints = new HashMap<>();
    public static boolean depthWrite=true;
    public static float[] color={1,1,1,1};
    public static float lineWidth=1;
    public static final Deque<Attrib> attrs = new ArrayDeque<>();
    private static final class Attrib {
        final int mask; final float[] color; final float width;
        Attrib(int mask) { this.mask=mask; color=GL11.color.clone(); width=lineWidth; }
    }
    public static boolean glIsEnabled(int flag) { return enabled.contains(flag); }
    public static boolean glGetBoolean(int flag) {
        if (flag!=GL_DEPTH_WRITEMASK) throw new AssertionError("unsupported boolean query");
        return depthWrite;
    }
    public static int glGetInteger(int flag) {
        if ((flag==GL14.GL_BLEND_SRC_ALPHA || flag==GL14.GL_BLEND_DST_ALPHA)
            && !GLContext.CAPS.OpenGL14 && !net.minecraft.client.renderer.OpenGlHelper.extBlendFuncSeparate)
            throw new AssertionError("unsupported legacy separate-alpha query");
        if (!ints.containsKey(flag)) throw new AssertionError("unseeded integer query " + flag);
        return ints.get(flag);
    }
    public static void glPushAttrib(int mask) { attrs.push(new Attrib(mask)); }
    public static void glPopAttrib() {
        if (attrs.isEmpty()) throw new AssertionError("attribute stack underflow");
        Attrib a=attrs.pop();
        if ((a.mask&GL_CURRENT_BIT)!=0) color=a.color.clone();
        if ((a.mask&GL_LINE_BIT)!=0) lineWidth=a.width;
    }
    public static String snapshot() {
        return new java.util.TreeSet<>(enabled)+"/"+new java.util.TreeMap<>(ints)+"/"+depthWrite+
            "/"+Arrays.toString(color)+"/"+lineWidth+"/attrs="+attrs.size();
    }
}
