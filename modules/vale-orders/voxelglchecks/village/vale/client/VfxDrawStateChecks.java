package village.vale.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

/** Actual VfxDraw source, recording Java API contracts only: no GPU, window or Minecraft. */
public final class VfxDrawStateChecks {
    private static int checks,groups;
    private static final List<String> failures=new ArrayList<>();
    private static final Tessellator T=Tessellator.getInstance();
    private static final double[][] LINE={{0,0,0},{1,0,0}};
    private static void check(boolean ok,String label) { checks++; if (!ok) failures.add(label); }
    private static void run(String name,Runnable body) {
        groups++; try { body.run(); } catch(Throwable failure) { failures.add(name+" threw "+failure); }
    }
    private static String state() {
        return GL11.snapshot()+"/"+GlStateManager.matrixSnapshot()+"/light="+
            OpenGlHelper.lastBrightnessX+","+OpenGlHelper.lastBrightnessY;
    }
    private static void seed(int pattern,int support) {
        GLContext.CAPS.OpenGL14=support==0; OpenGlHelper.extBlendFuncSeparate=support==1;
        GL11.enabled.clear(); GL11.ints.clear(); GL11.attrs.clear();
        int[] flags={GL11.GL_TEXTURE_2D,GL11.GL_LIGHTING,GL11.GL_CULL_FACE,GL11.GL_BLEND};
        for (int i=0;i<flags.length;i++) if ((pattern&(1<<i))!=0) GL11.enabled.add(flags[i]);
        if ((pattern&1)==0) GL11.enabled.add(GL11.GL_DEPTH_TEST);
        if ((pattern&2)!=0) GL11.enabled.add(GL11.GL_ALPHA_TEST);
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA,
            support==2?GL11.GL_SRC_ALPHA:GL11.GL_ONE,support==2?GL11.GL_ONE_MINUS_SRC_ALPHA:GL11.GL_ZERO);
        GlStateManager.depthMask((pattern&4)==0); GlStateManager.resetColor();
        GlStateManager.color(.125f+pattern*.01f,.25f,.375f,.5f); GL11.lineWidth=1.25f+pattern*.125f;
        GlStateManager.matrices.clear(); GlStateManager.translation=new double[]{4,5,6};
        GlStateManager.pushMatrix(); GlStateManager.translate(.25,.5,.75);
        OpenGlHelper.lastBrightnessX=17+pattern; OpenGlHelper.lastBrightnessY=33+pattern;
        JutsuVfx.detail=0; T.reset();
    }
    private static boolean same(double[][] a,double[][] b) {
        for (int i=0;i<a.length;i++) for(int j=0;j<a[i].length;j++) if (Math.abs(a[i][j]-b[i][j])>1e-12) return false;
        return true;
    }
    private static Tessellator.Batch last() { return T.batches.get(T.batches.size()-1); }
    private static void finiteBatches(String label) {
        boolean finite=true,colors=true,bounded=true;
        for (Tessellator.Batch b:T.batches) {
            bounded &= b.vertices.size()<=49152;
            for (BufferBuilder.Vertex v:b.vertices) {
                finite &= Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);
                colors &= v.r>=0&&v.r<=255&&v.g>=0&&v.g<=255&&v.b>=0&&v.b<=255&&v.a>=0&&v.a<=255;
            }
        }
        check(finite,label+" emits only finite vertex positions");
        check(colors,label+" emits bounded color channels");
        check(bounded,label+" keeps each supported batch bounded");
    }

    private static void nestedStateAndCamera() {
        for(int support=0;support<3;support++) for(int pattern=0;pattern<16;pattern++) {
            seed(pattern,support); String initial=state(); double[][] initialCamera=VfxDraw.facing(1,2,3);
            VfxDraw.begin(5,-2,9);
            try {
                check(!GL11.enabled.contains(GL11.GL_TEXTURE_2D)&&!GL11.enabled.contains(GL11.GL_LIGHTING)
                    &&!GL11.enabled.contains(GL11.GL_CULL_FACE)&&GL11.enabled.contains(GL11.GL_BLEND)&&!GL11.depthWrite,
                    "begin applies supported effect enable/depth state");
                check(OpenGlHelper.lastBrightnessX==240&&OpenGlHelper.lastBrightnessY==240,"begin selects full-bright lightmap");
                check(Arrays.equals(GlStateManager.translation,new double[]{9.25,3.5,15.75}),"offset begin composes incoming translation");
                check(GlStateManager.matrices.size()==2&&GL11.attrs.size()==1,"begin pushes one matrix and attribute frame");
                VfxDraw.additive(); GlStateManager.color(.8f,.7f,.6f,.5f); GL11.lineWidth=3.5f;
                String outer=state(); double[][] outerCamera=VfxDraw.facing(1,2,3);
                check(!same(initialCamera,outerCamera),"offset begin changes camera-facing basis");
                VfxDraw.begin(-8,7,2);
                try {
                    check(!same(outerCamera,VfxDraw.facing(1,2,3)),"nested offset establishes independent camera");
                    check(Arrays.equals(GlStateManager.translation,new double[]{1.25,10.5,17.75}),"nested offset composes matrix");
                    VfxDraw.normal(); GlStateManager.color(.3f,.2f,.1f,.9f); GL11.lineWidth=7;
                } finally { VfxDraw.end(); }
                check(state().equals(outer),"nested end restores exact incoming enables/blend/color/line/light/matrix");
                check(same(outerCamera,VfxDraw.facing(1,2,3)),"nested end restores outer camera");
                int writes=GlStateManager.colorWrites; GlStateManager.color(.3f,.2f,.1f,.9f);
                check(GlStateManager.colorWrites==writes+1,"end invalidates color cache after native attribute restore");
            } finally { VfxDraw.end(); }
            check(state().equals(initial),"outer end restores exact initial state across capability/incoming-state variants");
            check(same(initialCamera,VfxDraw.facing(1,2,3)),"outer end restores initial camera");
            VfxDraw.end(); check(state().equals(initial),"extra end is inert and cannot pop caller matrix");
        }
    }

    private static void modeAndBudgetScope() {
        seed(15,0); String initial=state(); VfxDraw.voxel(true); VfxDraw.cube(0,0,0,1,0xFFFFFF,1);
        check(T.drawCalls==0,"outside-scope mode write and cube cannot create budget");
        VfxDraw.begin(); try {
            VfxDraw.line(LINE,0xFFFFFF,1,1,1);
            check(last().mode==GL11.GL_QUAD_STRIP&&last().vertices.size()==4,"fresh scope defaults to smooth regardless of outside voxel write");
            VfxDraw.voxel(true);
            int before=T.drawCalls; for(int i=0;i<250;i++) VfxDraw.cube(0,0,0,1,0xFFFFFF,1);
            check(T.drawCalls-before==250,"outer scope spends 250 own cube admissions");
            VfxDraw.begin(); try {
                VfxDraw.line(LINE,0xFFFFFF,1,1,1);
                check(last().mode==GL11.GL_QUAD_STRIP,"nested scope does not inherit voxel mode");
                VfxDraw.voxel(true); before=T.drawCalls;
                for(int i=0;i<260;i++) VfxDraw.cube(0,0,0,1,0xFFFFFF,1);
                check(T.drawCalls-before==256,"nested budget is independent and exhausts exactly at low-detail scope cap");
            } finally { VfxDraw.end(); }
            before=T.drawCalls; VfxDraw.line(new double[][]{{0,0,0},{10,0,0}},0xFFFFFF,1,1,1);
            check(T.drawCalls==before+1&&last().mode==GL11.GL_QUADS&&last().vertices.size()==6*24,
                  "nested end restores outer voxel mode and its six remaining cubes");
            before=T.drawCalls; VfxDraw.cube(0,0,0,1,0xFFFFFF,1);
            check(T.drawCalls==before,"depleted outer scope cannot borrow ended inner budget");
        } finally { VfxDraw.end(); }
        check(state().equals(initial),"mode/budget exercise restores GL frame");
        int[] caps={256,1024,2048};
        for(int detail=0;detail<3;detail++) {
            T.reset(); JutsuVfx.detail=detail; VfxDraw.begin(); try {
                VfxDraw.voxel(true); for(int i=0;i<caps[detail]+3;i++) VfxDraw.cube(0,0,0,1,0xFFFFFF,1);
                check(T.drawCalls==caps[detail]&&T.vertexCount()==caps[detail]*24,"detail-specific budget limits actual cube draw admissions");
                finiteBatches("detail "+detail);
            } finally { VfxDraw.end(); }
        }
        int before=T.drawCalls; VfxDraw.voxel(true); VfxDraw.cube(0,0,0,1,0xFFFFFF,1);
        check(T.drawCalls==before,"scope completion removes all cube budget");
    }

    private static void exceptionalCallerFinally() {
        for(int failure=0;failure<3;failure++) {
            seed(10,failure); String initial=state(); double[][] initialCamera=VfxDraw.facing(1,2,3);
            VfxDraw.begin(2,3,4); try {
                VfxDraw.voxel(true); VfxDraw.cube(0,0,0,1,0xFFFFFF,1);
                String outer=state(); double[][] outerCamera=VfxDraw.facing(1,2,3);
                boolean caught=false; VfxDraw.begin(-3,2,-1); try {
                    VfxDraw.voxel(true); T.getBuffer().failBegin=failure==0; T.getBuffer().failVertex=failure==1?3:-1; T.failDraw=failure==2;
                    VfxDraw.line(LINE,0xFFFFFF,1,1,1);
                } catch(IllegalStateException expected) { caught=true; }
                finally { VfxDraw.end(); T.failDraw=false; T.getBuffer().reset(); }
                check(caught,"synthetic begin/vertex/draw failure reached actual primitive");
                check(state().equals(outer)&&same(outerCamera,VfxDraw.facing(1,2,3)),"caller finally restores outer GL and camera after primitive failure");
                VfxDraw.line(LINE,0xFFFFFF,1,1,1); int lineCubes=last().vertices.size()/24;
                check(last().mode==GL11.GL_QUADS&&lineCubes>0,"outer voxel mode survives failed inner scope");
                int before=T.drawCalls; for(int i=0;i<300;i++) VfxDraw.cube(0,0,0,1,0xFFFFFF,1);
                check(T.drawCalls-before==255-lineCubes,"failed inner primitive cannot consume outer cube budget");
            } finally { VfxDraw.end(); }
            check(state().equals(initial)&&same(initialCamera,VfxDraw.facing(1,2,3)),"outer finally restores complete incoming frame after synthetic failure");
            VfxDraw.begin(); try { VfxDraw.line(LINE,0xFFFFFF,1,1,1); check(last().mode==GL11.GL_QUAD_STRIP,"fresh scope after exception has no leaked voxel mode"); }
            finally { VfxDraw.end(); }
        }
    }

    private static void invalidGeometryAndBoundedSampling() {
        for(int voxel=0;voxel<2;voxel++) {
            seed(7,0); JutsuVfx.detail=2; String initial=state(); VfxDraw.begin(); try {
                VfxDraw.voxel(voxel==1); int before=T.drawCalls;
                VfxDraw.cube(0,0,0,-1,0xFFFFFF,1); VfxDraw.cube(Double.NaN,0,0,1,0xFFFFFF,1);
                VfxDraw.cube(0,0,0,17,0xFFFFFF,1); VfxDraw.cube(0,0,0,1,0xFFFFFF,Float.NaN);
                VfxDraw.line(LINE,0xFFFFFF,1,1,-1); VfxDraw.line(LINE,0xFFFFFF,1,1,Float.POSITIVE_INFINITY);
                VfxDraw.line(new double[][]{{0,0,0},{Double.NaN,0,0}},0xFFFFFF,1,1,1);
                VfxDraw.line(new double[257][3],0xFFFFFF,1,1,1); VfxDraw.line(null,0xFFFFFF,1,1,1);
                VfxDraw.sphere(0,0,0,-1,0xFFFFFF,1,8); VfxDraw.sphere(0,0,0,129,0xFFFFFF,1,8);
                VfxDraw.sphere(Double.POSITIVE_INFINITY,0,0,1,0xFFFFFF,1,8); VfxDraw.sphere(0,0,0,1,0xFFFFFF,1,Integer.MAX_VALUE);
                VfxDraw.sphere(0,0,0,1,0xFFFFFF,1,2); VfxDraw.sphere(0,0,0,1,0xFFFFFF,Float.NaN,8);
                VfxDraw.ribbon(LINE,new double[][]{{0,0,0}},0xFFFFFF,1,1); VfxDraw.ribbon(LINE,LINE,0xFFFFFF,Float.NaN,1);
                VfxDraw.billboard(null,0,0,0,-1,0xFFFFFF,1); VfxDraw.billboard(null,0,0,0,17,0xFFFFFF,1);
                VfxDraw.billboard(null,0,Double.NaN,0,1,0xFFFFFF,1);
                VfxDraw.annulus(0,0,0,-1,2,0xFFFFFF,1,16); VfxDraw.annulus(0,0,0,2,2,0xFFFFFF,1,16);
                VfxDraw.annulus(0,0,0,1,2,0xFFFFFF,1,Integer.MAX_VALUE);
                check(T.drawCalls==before,"invalid/negative/nonfinite/oversized geometry emits no batches in either mode");
                VfxDraw.sphere(0,0,0,4,0x123456,1,64);
                check(last().vertices.size()<=(voxel==1?384*24:64*128*4),"largest supported sphere keeps actual vertex work bounded");
                VfxDraw.line(new double[][]{{-80,0,0},{-79,0,0},{80,0,0}},0x123456,.2f,1,128);
                check(last().vertices.size()<=(voxel==1?384*24:6),"valid negative-coordinate multi-segment line stays bounded");
                VfxDraw.ribbon(new double[][]{{-2,0,-1},{2,0,-1}},new double[][]{{-2,0,1},{2,0,1}},0x123456,.2f,1);
                VfxDraw.annulus(0,0,0,1,2,0x123456,1,64); VfxDraw.billboard(null,-2,-3,-4,.25,0x123456,1);
                finiteBatches("valid geometry mode "+voxel);
            } finally { VfxDraw.end(); }
            check(state().equals(initial),"geometry exercise restores full incoming frame");
        }
        seed(7,0); VfxDraw.begin(); try {
            VfxDraw.line(new double[][]{{-Double.MAX_VALUE,0,0},{Double.MAX_VALUE,1,0}},0xFFFFFF,1,1,1);
            finiteBatches("oversized finite smooth line");
        } finally { VfxDraw.end(); }
    }

    public static void main(String[] args) {
        run("nested recorded state",VfxDrawStateChecks::nestedStateAndCamera);
        run("mode and draw budget",VfxDrawStateChecks::modeAndBudgetScope);
        run("caller finally after exceptions",VfxDrawStateChecks::exceptionalCallerFinally);
        run("geometry bounds",VfxDrawStateChecks::invalidGeometryAndBoundedSampling);
        for(String failure:failures) System.err.println("FAIL: "+failure);
        System.out.println("VfxDrawStateChecks: "+checks+" assertions, "+groups+" groups, "+failures.size()+" failures");
        if(!failures.isEmpty()) throw new AssertionError("recorded VfxDraw failures: "+failures.size());
    }
}
