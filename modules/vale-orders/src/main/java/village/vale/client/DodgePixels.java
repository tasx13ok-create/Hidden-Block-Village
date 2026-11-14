package village.vale.client;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.opengl.GL11;
import village.vale.DodgeMath;
import village.vale.DodgeTrail;

/** Original Minecraft-pixel silhouettes; bounded real-position samples, no recursive entity renderer. */
@SideOnly(Side.CLIENT)
public final class DodgePixels {
    private DodgePixels(){}
    public static final class Image {final DodgeTrail trail;final double age;final int direction;Image(DodgeTrail trail,double age,int direction){this.trail=trail;this.age=age;this.direction=direction;}}
    public static void takeoff(Entity entity){BlockPos below=entity.getPosition().down();if(!entity.world.isBlockLoaded(below))return;IBlockState state=entity.world.getBlockState(below);if(!state.getMaterial().isSolid())return;int id=Block.getStateId(state);for(int i=0;i<6;i++){double angle=i*Math.PI/3;entity.world.spawnParticle(EnumParticleTypes.BLOCK_CRACK,entity.posX+Math.cos(angle)*.2,entity.posY+.05,entity.posZ+Math.sin(angle)*.2,Math.cos(angle)*.08,.12,Math.sin(angle)*.08,id);}}
    public static void render(List<Image> images,float partial){if(images.isEmpty())return;Minecraft mc=Minecraft.getMinecraft();Entity camera=mc.getRenderViewEntity();if(camera==null)return;double cx=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial,cy=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial,cz=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
        VfxDraw.begin(-cx,-cy,-cz);try{BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);int players=0;for(Image image:images){if(players++>=12)break;List<DodgeTrail.Sample> samples=image.trail.snapshot();int ghost=0;for(int i=samples.size()-2;i>=0&&ghost<3;i--){DodgeTrail.Sample sample=samples.get(i);float alpha=(float)DodgeMath.ghostAlpha(ghost++,image.age);if(alpha<.01)continue;double[] lean=DodgeMath.lean(image.direction,image.age);body(buffer,sample,lean,alpha);}}Tessellator.getInstance().draw();}finally{VfxDraw.end();}
    }
    private static void body(BufferBuilder b,DodgeTrail.Sample s,double[] lean,float alpha){box(b,s,lean,-.25,1.25,-.25,.25,1.75,.25,0xC6EAFF,alpha);box(b,s,lean,-.25,.55,-.125,.25,1.25,.125,0x82CFFF,alpha);box(b,s,lean,-.5,.55,-.125,-.25,1.25,.125,0xA6DFFF,alpha);box(b,s,lean,.25,.55,-.125,.5,1.25,.125,0xA6DFFF,alpha);box(b,s,lean,-.25,0,-.125,0,.55,.125,0x5EB6E8,alpha);box(b,s,lean,0,0,-.125,.25,.55,.125,0x5EB6E8,alpha);}
    private static void box(BufferBuilder buffer,DodgeTrail.Sample s,double[] lean,double x0,double y0,double z0,double x1,double y1,double z1,int rgb,float alpha){double[][] v={{x0,y0,z0},{x1,y0,z0},{x1,y1,z0},{x0,y1,z0},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1},{x0,y1,z1}};int[][] faces={{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2},{3,2,6,7},{4,5,1,0}};double pitch=Math.toRadians(lean[0]),roll=Math.toRadians(lean[1]),yaw=Math.toRadians(s.yaw);for(int face=0;face<faces.length;face++)for(int index:faces[face]){double[] p=v[index];double a=p[1]*Math.cos(pitch)-p[2]*Math.sin(pitch),z=p[1]*Math.sin(pitch)+p[2]*Math.cos(pitch),x=p[0]*Math.cos(roll)-a*Math.sin(roll),y=p[0]*Math.sin(roll)+a*Math.cos(roll);double wx=x*Math.cos(yaw)-z*Math.sin(yaw),wz=x*Math.sin(yaw)+z*Math.cos(yaw);float shade=face==4?1:face==5?.55f:.82f;buffer.pos(Math.rint(s.x*16)/16+wx,Math.rint(s.y*16)/16+y,Math.rint(s.z*16)/16+wz).color((int)(((rgb>>16)&255)*shade),(int)(((rgb>>8)&255)*shade),(int)((rgb&255)*shade),(int)(alpha*255)).endVertex();}}
}
