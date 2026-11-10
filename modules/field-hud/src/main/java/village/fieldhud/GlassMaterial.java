package village.fieldhud;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

/** One GPU-only scene copy before the HUD. Never changes the world's shader/FBO. */
public final class GlassMaterial {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static final IntBuffer VIEWPORT=BufferUtils.createIntBuffer(16);
    private static int program,texture,width,height,vx,vy,rectLoc,radiusLoc,alphaLoc,tintLoc,stepLoc,screenLoc,originLoc,cutLoc;
    private static boolean valid,unavailable,reported;
    private static int quadBuffer;
    private static float scale;
    private GlassMaterial() {}
    public static void invalidate() { valid=false; }
    private static String source(String name) throws Exception {
        try(InputStream stream=GlassMaterial.class.getResourceAsStream("/assets/fieldhud/shaders/"+name)) {
            if(stream==null)throw new IllegalStateException("Missing glass shader");
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;
            while((n=stream.read(block))!=-1)out.write(block,0,n);
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private static int shader(int type,String name) throws Exception {
        int id=GL20.glCreateShader(type);
        GL20.glShaderSource(id,source(name));GL20.glCompileShader(id);
        if(GL20.glGetShaderi(id,GL20.GL_COMPILE_STATUS)==0) {
            String reason=GL20.glGetShaderInfoLog(id,2048);GL20.glDeleteShader(id);
            throw new IllegalStateException(name+": "+reason);
        }
        return id;
    }
    private static void ensure() throws Exception {
        if(program!=0)return;
        if(!GLContext.getCapabilities().OpenGL20)throw new IllegalStateException("OpenGL 2.0 unavailable");
        int vertex=shader(GL20.GL_VERTEX_SHADER,"glass.vsh"),fragment=0,next=0;
        try {
            fragment=shader(GL20.GL_FRAGMENT_SHADER,"glass.fsh");next=GL20.glCreateProgram();
            GL20.glAttachShader(next,vertex);GL20.glAttachShader(next,fragment);GL20.glLinkProgram(next);
            if(GL20.glGetProgrami(next,GL20.GL_LINK_STATUS)==0)throw new IllegalStateException(GL20.glGetProgramInfoLog(next,2048));
            program=next;
            FloatBuffer quad=BufferUtils.createFloatBuffer(12);
            quad.put(new float[]{0,1,0,1,1,0,1,0,0,0,0,0}).flip();
            int priorBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            quadBuffer=GL15.glGenBuffers();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,quadBuffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER,quad,GL15.GL_STATIC_DRAW);GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,priorBuffer);
            rectLoc=GL20.glGetUniformLocation(program,"Rect");radiusLoc=GL20.glGetUniformLocation(program,"Radius");
            alphaLoc=GL20.glGetUniformLocation(program,"Opacity");tintLoc=GL20.glGetUniformLocation(program,"Tint");
            stepLoc=GL20.glGetUniformLocation(program,"BlurStep");screenLoc=GL20.glGetUniformLocation(program,"Screen");originLoc=GL20.glGetUniformLocation(program,"Origin");
            cutLoc=GL20.glGetUniformLocation(program,"Cut");
            int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL20.glUseProgram(program);GL20.glUniform1i(GL20.glGetUniformLocation(program,"Scene"),0);GL20.glUseProgram(previous);
        } finally {
            GL20.glDeleteShader(vertex);if(fragment!=0)GL20.glDeleteShader(fragment);
            if(program==0&&next!=0)GL20.glDeleteProgram(next);
        }
    }
    public static void capture(ScaledResolution resolution) {
        valid=false;
        if(!HudConfig.glassBlur||unavailable||MC.world==null||MC.player==null||MC.gameSettings.hideGUI||MC.gameSettings.showDebugInfo)return;
        int active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            ensure();VIEWPORT.clear();GL11.glGetInteger(GL11.GL_VIEWPORT,VIEWPORT);
            vx=VIEWPORT.get(0);vy=VIEWPORT.get(1);int w=VIEWPORT.get(2),h=VIEWPORT.get(3);
            if(w<=0||h<=0)return;
            if(texture==0)texture=GL11.glGenTextures();GlStateManager.bindTexture(texture);
            if(width!=w||height!=h) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(ByteBuffer)null);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                width=w;height=h;
            }
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,vx,vy,width,height);
            scale=resolution.getScaleFactor();valid=true;
            if(!reported) { reported=true;System.out.println("FIELD_HUD_GLASS: scene blur ready "+width+"x"+height); }
        } catch(Exception|LinkageError error) { failed(error); }
        finally { GlStateManager.bindTexture(binding);GlStateManager.setActiveTexture(active); }
    }
    private static void failed(Throwable error) {
        unavailable=true;valid=false;
        System.out.println("FIELD_HUD_GLASS_FALLBACK: "+error.getClass().getSimpleName()+": "+error.getMessage());
    }
    public static boolean frosted() { return valid&&!unavailable&&HudConfig.glassBlur; }
    public static void panel(double x,double y,double w,double h,double radius,int tint,int opacity) {
        shape(x,y,w,h,radius,tint,opacity,0);
    }
    public static void slate(double x,double y,double w,double h,double radius,int tint,int opacity) {
        shape(x,y,w,h,radius,tint,opacity,12);
    }
    private static void shape(double x,double y,double w,double h,double radius,int tint,int opacity,float cut) {
        if(opacity<8)return;
        if(!frosted()) { fallback(x,y,w,h,radius,tint,opacity);return; }
        int previous=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),active=GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);int binding=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST),depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GlStateManager.disableAlpha();GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);
        try {
            GlStateManager.bindTexture(texture);GL20.glUseProgram(program);
            GL20.glUniform4f(rectLoc,(float)x,(float)y,(float)w,(float)h);
            GL20.glUniform1f(radiusLoc,(float)Math.min(radius,Math.min(w,h)/2));GL20.glUniform1f(alphaLoc,opacity/255F);
            GL20.glUniform1f(cutLoc,cut);
            GL20.glUniform3f(tintLoc,((tint>>16)&255)/255F,((tint>>8)&255)/255F,(tint&255)/255F);
            GL20.glUniform2f(stepLoc,2.5F*scale/width,2.5F*scale/height);GL20.glUniform2f(screenLoc,width,height);GL20.glUniform2f(originLoc,vx,vy);
            int priorBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
            try {
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,quadBuffer);
                GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);GL11.glVertexPointer(3,GL11.GL_FLOAT,12,0L);
                GL11.glDrawArrays(GL11.GL_QUADS,0,4);
            } finally {
                GL11.glPopClientAttrib();GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,priorBuffer);
            }
        } catch(RuntimeException|LinkageError error) { failed(error); }
        finally {
            GL20.glUseProgram(previous);GlStateManager.bindTexture(binding);GlStateManager.setActiveTexture(active);
            if(alpha)GlStateManager.enableAlpha();else GlStateManager.disableAlpha();
            if(depth)GlStateManager.enableDepth();else GlStateManager.disableDepth();GlStateManager.color(1,1,1,1);
        }
    }
    private static void fallback(double x,double y,double w,double h,double radius,int tint,int opacity) {
        SkinDraw.rounded(x-1,y+1,w+2,h+2,radius+1,SkinDraw.alpha(0x03070D,opacity*45/255));
        SkinDraw.rounded(x,y,w,h,radius,SkinDraw.alpha(0xCFDCE7,opacity*70/255));
        SkinDraw.rounded(x+.6,y+.6,w-1.2,h-1.2,radius-.6,SkinDraw.alpha(0x172330,opacity*205/255));
        SkinDraw.line(x+radius,y+.7,x+w-radius,y+.7,.7,SkinDraw.alpha(0xF3F7FF,opacity*75/255));
    }
}
