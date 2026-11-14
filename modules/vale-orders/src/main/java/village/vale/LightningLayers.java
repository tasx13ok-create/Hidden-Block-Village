package village.vale;

import java.util.*;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.opengl.GL11;

/** Original bounded electric ribbons follow the player's actual posed limbs. No shader dependency. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid=ValeOrders.ID,value=Side.CLIENT)
public final class LightningLayers implements LayerRenderer<AbstractClientPlayer> {
    private static final Set<RenderPlayer> INSTALLED=Collections.newSetFromMap(new WeakHashMap<RenderPlayer,Boolean>());
    private final RenderPlayer renderer;
    private LightningLayers(RenderPlayer renderer){this.renderer=renderer;}
    @SubscribeEvent public static void prepare(RenderPlayerEvent.Pre event){RenderPlayer renderer=event.getRenderer();if(INSTALLED.add(renderer))renderer.addLayer(new LightningLayers(renderer));}
    @Override public void doRenderLayer(AbstractClientPlayer player,float limb,float amount,float partial,float age,float yaw,float pitch,float scale){
        if(!RunPoses.formVisuals||player.isInvisible()||player.isSpectator()||LightningKit.EFFECT==null||!player.isPotionActive(LightningKit.EFFECT))return;
        // Preserve prior GL state, including third-party render-layer settings.
        boolean texture=GL11.glIsEnabled(GL11.GL_TEXTURE_2D),lighting=GL11.glIsEnabled(GL11.GL_LIGHTING),blend=GL11.glIsEnabled(GL11.GL_BLEND),depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_RGB),dst=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_RGB),srcAlpha=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_SRC_ALPHA),dstAlpha=GL11.glGetInteger(org.lwjgl.opengl.GL14.GL_BLEND_DST_ALPHA);
        GL11.glPushAttrib(GL11.GL_LINE_BIT|GL11.GL_CURRENT_BIT);
        GlStateManager.disableTexture2D();GlStateManager.disableLighting();GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);GL11.glLineWidth(2);
        try{
            net.minecraft.client.model.ModelPlayer body=renderer.getMainModel();
            ribbon(body.bipedBody,age,0,12,3.3,scale);
            ribbon(body.bipedRightArm,age,1,11,2.3,scale);ribbon(body.bipedLeftArm,age,2,11,2.3,scale);
            ribbon(body.bipedRightLeg,age,3,11,2.3,scale);ribbon(body.bipedLeftLeg,age,4,11,2.3,scale);
        }finally{
            // Restore through GlStateManager as well as GL to keep its state cache consistent.
            GlStateManager.depthMask(depthWrite);GlStateManager.color(1,1,1,1);
            if(texture)GlStateManager.enableTexture2D();else GlStateManager.disableTexture2D();
            if(lighting)GlStateManager.enableLighting();else GlStateManager.disableLighting();
            GlStateManager.tryBlendFuncSeparate(src,dst,srcAlpha,dstAlpha);
            if(blend)GlStateManager.enableBlend();else GlStateManager.disableBlend();GL11.glPopAttrib();
        }
    }
    private static void ribbon(ModelRenderer anchor,float age,int seed,int length,double radius,float scale){
        GlStateManager.pushMatrix();
        try{
            anchor.postRender(scale);GlStateManager.scale(scale,scale,scale);
            // Fixed two ribbons per limb, nine vertices each: independent of FPS/entity age.
            for(int side=0;side<2;side++){
                BufferBuilder buffer=Tessellator.getInstance().getBuffer();buffer.begin(GL11.GL_LINE_STRIP,DefaultVertexFormats.POSITION_COLOR);
                for(int node=0;node<=8;node++){
                    double t=node/8.0,phase=Math.floor(age*.65)+seed*7+node*2.7+side*Math.PI;
                    double theta=t*5+side*Math.PI+age*.12+seed;
                    double jag=Math.sin(phase)*.65,r=radius+jag;
                    buffer.pos(Math.cos(theta)*r,t*length,Math.sin(theta)*r).color(.48F,.83F,1F,.62F+(float)Math.sin(age*.8+seed)*.17F).endVertex();
                }
                Tessellator.getInstance().draw();
            }
        }finally{GlStateManager.popMatrix();}
    }
    @Override public boolean shouldCombineTextures(){return false;}
}
