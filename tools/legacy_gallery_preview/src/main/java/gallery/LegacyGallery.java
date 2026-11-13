package gallery;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

/** Local model-inspection aid: actual registered renderers, no spawned/ticking actors. */
@Mod(modid="legacygallery",name="Legacy Naruto Model Gallery",version="1.0",clientSideOnly=true,acceptableRemoteVersions="*")
public final class LegacyGallery {
    private World world;
    private final List<EntityLivingBase> displays=new ArrayList<>();
    @Mod.EventHandler public void init(FMLInitializationEvent e){MinecraftForge.EVENT_BUS.register(this);}
    private void load(World w){
        world=w;displays.clear();int failures=0;
        try(BufferedReader in=new BufferedReader(new InputStreamReader(LegacyGallery.class.getResourceAsStream("/gallery.tsv"),StandardCharsets.UTF_8))){
            String line;while((line=in.readLine())!=null){
                String[] p=line.split("\t");if(p.length!=6)continue;
                try{
                    Entity raw=EntityList.createEntityByIDFromName(new ResourceLocation(p[0]),w);
                    if(!(raw instanceof EntityLivingBase)){failures++;System.out.println("GALLERY_MODEL_MISSING "+p[0]);continue;}
                    EntityLivingBase e=(EntityLivingBase)raw;double x=Double.parseDouble(p[2]),y=Double.parseDouble(p[3]),z=Double.parseDouble(p[4]);float yaw=Float.parseFloat(p[5]);
                    e.setPositionAndRotation(x,y,z,yaw,0);e.prevPosX=e.lastTickPosX=x;e.prevPosY=e.lastTickPosY=y;e.prevPosZ=e.lastTickPosZ=z;
                    e.prevRotationYaw=e.rotationYaw=e.prevRenderYawOffset=e.renderYawOffset=e.prevRotationYawHead=e.rotationYawHead=yaw;
                    e.rotationPitch=e.prevRotationPitch=0;e.setCustomNameTag(p[1]);e.setAlwaysRenderNameTag(true);e.setNoGravity(true);e.setEntityInvulnerable(true);
                    if(e instanceof EntityLiving)((EntityLiving)e).setNoAI(true);
                    displays.add(e);
                }catch(Throwable ex){failures++;System.out.println("GALLERY_MODEL_FAILURE "+p[0]+" "+ex);}
            }
        }catch(IOException ex){throw new IllegalStateException(ex);}
        System.out.println("GALLERY_MODELS_READY "+displays.size()+" failures="+failures);
    }
    @SubscribeEvent public void render(RenderWorldLastEvent event){
        Minecraft mc=Minecraft.getMinecraft();if(mc.world==null||mc.player==null)return;
        if(world!=mc.world)load(mc.world);
        Entity camera=mc.getRenderViewEntity();if(camera==null)return;
        double a=event.getPartialTicks(),cx=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*a,cy=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*a,cz=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*a;
        for(EntityLivingBase e:displays){
            if(e.getDistanceSq(camera)>16384)continue;
            GlStateManager.pushMatrix();GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try{mc.getRenderManager().renderEntity(e,e.posX-cx,e.posY-cy,e.posZ-cz,e.rotationYaw,0,false);}
            catch(Throwable ex){System.out.println("GALLERY_RENDER_FAILURE "+e.getName()+" "+ex);}
            finally{GL11.glPopAttrib();GlStateManager.popMatrix();}
        }
    }
}
