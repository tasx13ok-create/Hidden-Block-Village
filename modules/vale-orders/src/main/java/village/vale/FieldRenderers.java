package village.vale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.*;

@SideOnly(Side.CLIENT)
public final class FieldRenderers {
    private FieldRenderers(){}
    public static void register(){
        RenderingRegistry.registerEntityRenderingHandler(EchoCompanion.class,EchoRender::new);
        RenderingRegistry.registerEntityRenderingHandler(FieldRogue.class,RogueRender::new);
        RenderingRegistry.registerEntityRenderingHandler(RuneField.class,RuneRender::new);
        RenderingRegistry.registerEntityRenderingHandler(FieldProjectile.class,ProjectileRender::new);
    }
    public static final class EchoModel extends ModelBase {
        private final ModelRenderer core,cap,left,right,tail;
        EchoModel(){textureWidth=64;textureHeight=64;
            core=new ModelRenderer(this,0,0);core.addBox(-4,-4,-4,8,8,8);core.setRotationPoint(0,14,0);
            cap=new ModelRenderer(this,0,18);cap.addBox(-5,-2,-5,10,2,10);cap.setRotationPoint(0,9,0);
            left=new ModelRenderer(this,0,32);left.addBox(-2,-4,-2,4,8,4);left.setRotationPoint(-7,14,0);
            right=new ModelRenderer(this,16,32);right.addBox(-2,-4,-2,4,8,4);right.setRotationPoint(7,14,0);
            tail=new ModelRenderer(this,32,0);tail.addBox(-2,-2,-6,4,4,6);tail.setRotationPoint(0,15,6);
        }
        @Override public void render(Entity e,float limb,float amount,float age,float yaw,float pitch,float scale){
            float bob=(float)Math.sin(age*.15)*(.6F+Math.min(1,amount)*.3F);core.rotationPointY=14+bob;cap.rotationPointY=9+bob;
            left.rotateAngleZ=(float)Math.sin(age*.12)*.15F;right.rotateAngleZ=-left.rotateAngleZ;
            float attack=e instanceof EchoCompanion?((EchoCompanion)e).attackPose(age-(int)age):0;left.rotateAngleX=right.rotateAngleX=-(float)Math.sin(attack*Math.PI)*1.3F;
            tail.rotateAngleX=Math.min(.35F,amount*.15F);core.rotateAngleX=Math.min(.15F,amount*.08F);
            core.rotateAngleY=yaw*.0174533F;core.render(scale);cap.render(scale);left.render(scale);right.render(scale);tail.render(scale);
        }
    }
    public static final class EchoRender extends RenderLiving<EchoCompanion>{
        EchoRender(RenderManager m){super(m,new EchoModel(),.35F);}
        @Override protected ResourceLocation getEntityTexture(EchoCompanion e){return new ResourceLocation(ValeOrders.ID,"textures/entity/companion_"+e.kind()+".png");}
        @Override protected void preRenderCallback(EchoCompanion e,float partial){float scale=e.kind()==1?1.3F:e.kind()==2?.75F:1;GlStateManager.scale(scale,scale,scale);}
    }
    public static final class RogueRender extends RenderBiped<FieldRogue>{
        RogueRender(RenderManager m){super(m,new RogueModel(),.4F);}
        @Override protected ResourceLocation getEntityTexture(FieldRogue e){return new ResourceLocation(ValeOrders.ID,"textures/entity/rogue_"+e.kind()+".png");}
    }
    private static final class RogueModel extends ModelBiped{
        RogueModel(){super(0,0,64,64);}
        @Override public void setRotationAngles(float limb,float amount,float age,float yaw,float pitch,float scale,Entity entity){super.setRotationAngles(limb,amount,age,yaw,pitch,scale,entity);if(entity instanceof FieldRogue&&((FieldRogue)entity).isCasting()){float t=((FieldRogue)entity).castPose();bipedRightArm.rotateAngleX=bipedLeftArm.rotateAngleX=-.4F-t;bipedRightArm.rotateAngleY=-.2F;bipedLeftArm.rotateAngleY=.2F;bipedBody.rotateAngleX=t*.1F;}}
    }
    public static final class ProjectileRender extends Render<FieldProjectile>{
        ProjectileRender(RenderManager m){super(m);}
        @Override protected ResourceLocation getEntityTexture(FieldProjectile e){return net.minecraft.client.renderer.texture.TextureMap.LOCATION_BLOCKS_TEXTURE;}
        @Override public void doRender(FieldProjectile e,double x,double y,double z,float yaw,float partial){
            GlStateManager.pushMatrix();GlStateManager.translate(x,y,z);GlStateManager.enableRescaleNormal();GlStateManager.scale(.5F,.5F,.5F);GlStateManager.rotate(-renderManager.playerViewY,0,1,0);GlStateManager.rotate(renderManager.options.thirdPersonView==2?-renderManager.playerViewX:renderManager.playerViewX,1,0,0);GlStateManager.rotate(180,0,1,0);GlStateManager.rotate((e.ticksExisted+partial)*(e.kind()==1?30:e.kind()==2?7:0),0,0,1);
            bindTexture(getEntityTexture(e));if(renderOutlines){GlStateManager.enableColorMaterial();GlStateManager.enableOutlineMode(getTeamColor(e));}
            Minecraft.getMinecraft().getRenderItem().renderItem(new ItemStack(Gear.ITEMS.get(new String[]{"needle_fan","snap_disc","smoke_capsule"}[e.kind()])),net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.GROUND);
            if(renderOutlines){GlStateManager.disableOutlineMode();GlStateManager.disableColorMaterial();}GlStateManager.disableRescaleNormal();GlStateManager.popMatrix();super.doRender(e,x,y,z,yaw,partial);
        }
    }
    public static final class RuneRender extends Render<RuneField>{
        RuneRender(RenderManager m){super(m);}
        @Override protected ResourceLocation getEntityTexture(RuneField e){return new ResourceLocation(ValeOrders.ID,"textures/entity/companion_0.png");}
        @Override public void doRender(RuneField e,double x,double y,double z,float yaw,float partial){} // Native server particle ring is its visible model.
    }
}
