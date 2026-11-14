package village.fieldhud;
import java.util.*;import org.lwjgl.opengl.*;import net.minecraft.client.renderer.*;
/** Actual AnimeDraw source against original synthetic stateful GL contracts, no native context. */
public final class AnimeDrawStateChecks{
 private static int checks;private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static String snapshot(){return new TreeSet<>(GL11.enabled)+"/"+new TreeMap<>(GL11.ints)+"/"+Arrays.toString(GL11.color)+"/"+GL11.write+"/"+GL11.matrices;}
 private static void seed(int mode){GL11.enabled.clear();for(int flag:new int[]{GL11.GL_TEXTURE_2D,GL11.GL_BLEND,GL11.GL_ALPHA_TEST,GL11.GL_DEPTH_TEST,GL11.GL_LIGHTING,GL11.GL_CULL_FACE,GL11.GL_LIGHT0,GL11.GL_LIGHT1})if((flag+mode)%2==0)GL11.enabled.add(flag);
  GlStateManager.tryBlendFuncSeparate(7+mode,11+mode,13+mode,17+mode);GlStateManager.shadeModel(23+mode);GlStateManager.setActiveTexture(101+mode);GlStateManager.bindTexture(201+mode);GlStateManager.matrixMode(301+mode);GlStateManager.depthMask(mode%2==0);GlStateManager.color(.1f*mode,.2f,.3f,.4f);GL11.matrices=0;}
 public static void main(String[] args){
  boolean refused=false;try{GL11.glGetFloat(GL11.GL_CURRENT_COLOR,org.lwjgl.BufferUtils.createFloatBuffer(4));}catch(IllegalArgumentException expected){refused=true;}check(refused,"Strict stub must reject original4-float crash input");
  java.nio.FloatBuffer positioned=org.lwjgl.BufferUtils.createFloatBuffer(20);positioned.position(5);refused=false;try{GL11.glGetFloat(GL11.GL_CURRENT_COLOR,positioned);}catch(IllegalArgumentException expected){refused=true;}check(refused,"Capacity20with15remaining must fail");positioned.position(4);GL11.glGetFloat(GL11.GL_CURRENT_COLOR,positioned);check(positioned.position()==4,"GL query does not advance caller position");
  for(int mode=0;mode<8;mode++){
   seed(mode);String initial=snapshot();AnimeDraw.gradient(1,2,40,20,1,2,3,4,true);check(snapshot().equals(initial),"Primitive failed exact incoming GL restore");
   try(AnimeDraw.Scope scope=new AnimeDraw.Scope()){GlStateManager.disableDepth();GlStateManager.enableBlend();GlStateManager.color(1,1,1,1);AnimeDraw.glow(2,3,10,10,0x334455,.8,true);try(AnimeDraw.Scope nested=new AnimeDraw.Scope()){GlStateManager.bindTexture(500);AnimeDraw.vertical(1,1,20,30,1,2,false);}}
   check(snapshot().equals(initial),"Nested full pass lost incoming state/matrix");
   Tessellator.getInstance().fail=true;try(AnimeDraw.Scope scope=new AnimeDraw.Scope()){AnimeDraw.gradient(0,0,10,10,1,2,3,4,false);throw new AssertionError("missing throw");}catch(IllegalStateException expected){}finally{Tessellator.getInstance().fail=false;}
   check(snapshot().equals(initial),"Tessellation exception leaked GL/matrix state");
   Tessellator.getInstance().getBuffer().fail=true;try(AnimeDraw.Scope scope=new AnimeDraw.Scope()){AnimeDraw.gradient(0,0,10,10,1,2,3,4,false);throw new AssertionError("missing begin throw");}catch(IllegalStateException expected){}finally{Tessellator.getInstance().getBuffer().fail=false;}
   check(snapshot().equals(initial),"Begin exception leaked GL/matrix state");
   AnimeDraw.Scope repeated=new AnimeDraw.Scope();repeated.close();repeated.close();check(snapshot().equals(initial),"Scope replay popped caller matrix");
  }
  GLContext.CAPS.OpenGL14=false;OpenGlHelper.extBlendFuncSeparate=false;seed(1);GlStateManager.tryBlendFuncSeparate(8,12,8,12);String legacy=snapshot();
  try(AnimeDraw.Scope scope=new AnimeDraw.Scope()){AnimeDraw.glow(1,2,4,5,0x44FF88,.5,true);}check(snapshot().equals(legacy),"Separate-blend fallback lost supported state");
  System.out.println("ANIME_DRAW_STATE_PASS: "+checks);
 }
}