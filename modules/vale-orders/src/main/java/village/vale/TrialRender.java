package village.vale;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class TrialRender extends RenderBiped<TrialKeeper> {
    public TrialRender(RenderManager manager) { super(manager, new TrialModel(), .5F); }
    private static final class TrialModel extends ModelBiped {
        TrialModel(){super(0,0,64,64);}
        @Override public void setRotationAngles(float swing,float amount,float age,float yaw,float pitch,float scale,net.minecraft.entity.Entity entity){
            super.setRotationAngles(swing,amount,age,yaw,pitch,scale,entity);if(!(entity instanceof TrialKeeper))return;TrialKeeper e=(TrialKeeper)entity;
            if(e.isCharging()){float t=e.chargePose();t=t*t*(3-2*t);bipedRightArm.rotateAngleX=-.3F-t*2;bipedLeftArm.rotateAngleX=-.3F-t*2;bipedRightArm.rotateAngleZ=.15F+t*.25F;bipedLeftArm.rotateAngleZ=-.15F-t*.25F;bipedBody.rotateAngleX=-t*.12F;}
            else if(e.isFollowingThrough()){float t=e.strikePose();bipedRightArm.rotateAngleX=-2.3F+t*3;bipedLeftArm.rotateAngleX=-2.3F+t*3;bipedBody.rotateAngleX=(float)Math.sin(t*Math.PI)*.22F;}
        }
    }
    @Override protected void preRenderCallback(TrialKeeper e,float partial){if(e.kind()==3)net.minecraft.client.renderer.GlStateManager.scale(2,2,2);}
    @Override protected ResourceLocation getEntityTexture(TrialKeeper entity) {
        return new ResourceLocation(ValeOrders.ID, "textures/entity/" + Bosses.KEYS[entity.kind()] + ".png");
    }
}
