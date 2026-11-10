package village.vale.armor.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.lwjgl.opengl.GL11;
import village.vale.armor.ArmorMesh;
import village.vale.armor.RigPose;

/**
 * Development-only adult-player mesh renderer. Caller supplies the already posed OWN armor model
 * and its armor-root matrix, including the entity/crouch transform. Child-model scaling and
 * production layer registration are deliberately outside this blockout adapter.
 */
public final class ForgeBlockoutRenderer {
    private static final int[][] FACES={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
    private static final float[][] NORMALS={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
    private static final float[] SHADE={.88f,.61f,.75f,.68f,1f,.43f};
    private ForgeBlockoutRenderer(){}

    public static void render(ArmorMesh mesh,ModelBiped armorPose,EntityEquipmentSlot equippedSlot,float scale,
                              double seconds,float movement,float flight,float strike){
        if(mesh==null||armorPose==null||!Float.isFinite(scale)||scale<=0)return;
        ArmorMesh.Slot slot=slot(equippedSlot);
        if(slot==null)return;
        float lightX=OpenGlHelper.lastBrightnessX,lightY=OpenGlHelper.lastBrightnessY;
        // Use raw GL consistently inside the attribute scope to avoid changing GlStateManager caches.
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try{
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            for(int pass=0;pass<2;pass++){
                boolean emission=pass==1;
                if(emission)OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,240,240);
                for(ArmorMesh.Bone which:ArmorMesh.Bone.values()){
                    ModelRenderer bone=bone(armorPose,which);
                    if(!bone.showModel||bone.isHidden)continue;
                    GL11.glPushMatrix();
                    try{
                        bone.postRender(scale);
                        for(ArmorMesh.Box box:mesh.boxes){
                            if(box.bone!=which||box.slot!=slot)continue;
                            ArmorMesh.Material material=mesh.materials.get(box.material);
                            if(material.emissive!=emission)continue;
                            ArmorMesh.Rig rig=mesh.rigs.get(box.rig);
                            RigPose.Rotation pose=RigPose.at(rig.motion,seconds,movement,flight,strike);
                            GL11.glPushMatrix();
                            try{
                                GL11.glTranslatef(rig.x*scale,rig.y*scale,rig.z*scale);
                                rotate(pose.x,pose.y,pose.z);
                                rotate(box.rx,box.ry,box.rz);
                                draw(box,material,scale);
                            }finally{GL11.glPopMatrix();}
                        }
                    }finally{GL11.glPopMatrix();}
                }
            }
        }finally{
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit,lightX,lightY);
            GL11.glPopAttrib();
        }
    }
    private static void rotate(float x,float y,float z){
        float deg=180f/(float)Math.PI;
        GL11.glRotatef(z*deg,0,0,1);GL11.glRotatef(y*deg,0,1,0);GL11.glRotatef(x*deg,1,0,0);
    }
    private static void draw(ArmorMesh.Box b,ArmorMesh.Material mat,float scale){
        float x=b.x,y=b.y,z=b.z,w=b.w,h=b.h,d=b.d;
        float[][] vertices={{x,y,z},{x+w,y,z},{x+w,y+h,z},{x,y+h,z},{x,y,z+d},{x+w,y,z+d},{x+w,y+h,z+d},{x,y+h,z+d}};
        Tessellator tess=Tessellator.getInstance();BufferBuilder buffer=tess.getBuffer();
        buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        int r=(mat.rgb>>16)&255,g=(mat.rgb>>8)&255,blue=mat.rgb&255;
        for(int face=0;face<FACES.length;face++){
            float shade=mat.emissive?1:SHADE[face];float[] normal=NORMALS[face];
            for(int vertex:FACES[face]){float[] p=vertices[vertex];
                buffer.pos(p[0]*scale,p[1]*scale,p[2]*scale).tex(0,0).color(Math.round(r*shade),Math.round(g*shade),Math.round(blue*shade),255)
                    .normal(normal[0],normal[1],normal[2]).endVertex();
            }
        }
        tess.draw();
    }
    private static ArmorMesh.Slot slot(EntityEquipmentSlot slot){
        if(slot==null)return null;
        switch(slot){case HEAD:return ArmorMesh.Slot.HEAD;case CHEST:return ArmorMesh.Slot.CHEST;case LEGS:return ArmorMesh.Slot.LEGS;case FEET:return ArmorMesh.Slot.FEET;default:return null;}
    }
    private static ModelRenderer bone(ModelBiped m,ArmorMesh.Bone bone){
        switch(bone){case BODY:return m.bipedBody;case HEAD:return m.bipedHead;case RIGHT_ARM:return m.bipedRightArm;case LEFT_ARM:return m.bipedLeftArm;case RIGHT_LEG:return m.bipedRightLeg;case LEFT_LEG:return m.bipedLeftLeg;default:throw new IllegalArgumentException("Unknown bone");}
    }
}
