package net.minecraft.client.renderer;
public final class OpenGlHelper {
    public static boolean extBlendFuncSeparate=true;
    public static int lightmapTexUnit=1,lightmapWrites;
    public static float lastBrightnessX,lastBrightnessY;
    public static void setLightmapTextureCoords(int unit,float x,float y) {
        if (unit!=lightmapTexUnit) throw new AssertionError("wrong lightmap unit");
        lastBrightnessX=x; lastBrightnessY=y; lightmapWrites++;
    }
}
