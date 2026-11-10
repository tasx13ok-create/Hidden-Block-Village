package village.vale.armor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import village.vale.armor.*;

/** Exact textured cuboids and a masked fullbright overlay. The caller binds the albedo at armor root. */
public final class TexturedArmorRenderer {
    private static final int[][] FACES={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
    private static final float[][] NORMALS={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
    private static final float[] SHADE={.88f,.67f,.8f,.74f,1f,.55f};
    private TexturedArmorRenderer(){}
    public static ResourceLocation albedo(ArmorProfile p){return new ResourceLocation("valearmor","textures/armor/"+p.key+".png");}
    public static ResourceLocation emission(ArmorProfile p){return new ResourceLocation("valearmor","textures/armor/"+p.key+"_e.png");}
    public static void render(ArmorMesh mesh,ModelBiped model,EntityEquipmentSlot slot,float scale,double seconds,float movement,float flight,float strike){
        ArmorMesh.Slot drawSlot=convert(slot);if(drawSlot==null||!Float.isFinite(scale)||scale<=0)return;
        float priorX=OpenGlHelper.lastBrightnessX,priorY=OpenGlHelper.lastBrightnessY;
        Minecraft mc=Minecraft.getMinecraft();ResourceLocation base=albedo(mesh.profile);
        java.util.List<MeshBatcher.Batch> batches=MeshBatcher.get(mesh.profile);
        // The armor layer has already bound this albedo. Keep its texture and state-cache relationship exact.
        mc.getTextureManager().bindTexture(base);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try{
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER,.1f);GL11.glDepthMask(true);GL11.glDepthFunc(GL11.GL_LEQUAL);
            for(int pass=0;pass<2;pass++){
                boolean emissive=pass==1;
                mc.getTextureManager().bindTexture(emissive?emission(mesh.profile):base);
                if(emissive)OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
                for(ArmorMesh.Bone name:ArmorMesh.Bone.values()){
                    ModelRenderer bone=bone(model,name);if(!bone.showModel||bone.isHidden)continue;
                    GL11.glPushMatrix();
                    try{
                        bone.postRender(scale);
                        for(MeshBatcher.Batch batch:batches){
                            if(batch.bone!=name||batch.slot!=drawSlot||batch.emission!=emissive)continue;
                            ArmorMesh.Rig rig=mesh.rigs.get(batch.rig);RigPose.Rotation pose=RigPose.at(rig.motion,seconds,movement,flight,strike);
                            GL11.glPushMatrix();
                            try{
                                GL11.glTranslatef(rig.x*scale,rig.y*scale,rig.z*scale);rotate(pose.x,pose.y,pose.z);
                                draw(batch,scale);
                            }finally{GL11.glPopMatrix();}
                        }
                    }finally{GL11.glPopMatrix();}
                }
            }
        }finally{
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,priorX,priorY);
            mc.getTextureManager().bindTexture(base);
            GL11.glPopAttrib();
        }
    }
    static void rotate(float x,float y,float z){float f=180f/(float)Math.PI;GL11.glRotatef(z*f,0,0,1);GL11.glRotatef(y*f,0,1,0);GL11.glRotatef(x*f,1,0,0);}
    static void draw(MeshBatcher.Batch batch,float scale){
        Tessellator tess=Tessellator.getInstance();BufferBuilder buffer=tess.getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        for(MeshBatcher.Vertex v:batch.vertices){int color=Math.round(v.shade*255);buffer.pos(v.x*scale,v.y*scale,v.z*scale).tex(v.u,v.v).color(color,color,color,255).normal(v.nx,v.ny,v.nz).endVertex();}
        tess.draw();
    }
    static void draw(ArmorMesh.Box b,float scale,boolean emissive){
        float x=b.x,y=b.y,z=b.z,w=b.w,h=b.h,d=b.d;
        float[][] vertices={{x,y,z},{x+w,y,z},{x+w,y+h,z},{x,y+h,z},{x,y,z+d},{x+w,y,z+d},{x+w,y+h,z+d},{x,y+h,z+d}};
        Tessellator tess=Tessellator.getInstance();BufferBuilder buffer=tess.getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        for(int face=0;face<6;face++){
            float[][] uv=AtlasUv.face(b,face);float[] n=NORMALS[face];int color=Math.round((emissive?1:SHADE[face])*255);
            for(int i=0;i<4;i++){float[] p=vertices[FACES[face][i]];
                buffer.pos(p[0]*scale,p[1]*scale,p[2]*scale).tex(uv[i][0],uv[i][1]).color(color,color,color,255).normal(n[0],n[1],n[2]).endVertex();
            }
        }
        tess.draw();
    }
    static ArmorMesh.Slot convert(EntityEquipmentSlot slot){switch(slot){case HEAD:return ArmorMesh.Slot.HEAD;case CHEST:return ArmorMesh.Slot.CHEST;case LEGS:return ArmorMesh.Slot.LEGS;case FEET:return ArmorMesh.Slot.FEET;default:return null;}}
    static ModelRenderer bone(ModelBiped m,ArmorMesh.Bone b){switch(b){case BODY:return m.bipedBody;case HEAD:return m.bipedHead;case RIGHT_ARM:return m.bipedRightArm;case LEFT_ARM:return m.bipedLeftArm;case RIGHT_LEG:return m.bipedRightLeg;case LEFT_LEG:return m.bipedLeftLeg;default:throw new IllegalArgumentException();}}
}
