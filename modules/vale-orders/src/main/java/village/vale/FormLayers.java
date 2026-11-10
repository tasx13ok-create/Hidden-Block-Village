package village.vale;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.*;

/** Original form ornaments follow the posed body/arms, including held-item and run transitions. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid=ValeOrders.ID,value=Side.CLIENT)
public final class FormLayers implements LayerRenderer<AbstractClientPlayer> {
    private static final Set<RenderPlayer> INSTALLED=Collections.newSetFromMap(new WeakHashMap<RenderPlayer,Boolean>());
    private static final Map<AbstractClientPlayer,Visual> VISUALS=new WeakHashMap<>();
    private static final class Visual{final PoseMath.Spring alpha=new PoseMath.Spring();float age=Float.NaN;int kind=-1;}
    private final RenderPlayer renderer;private final ModelRenderer plate,finLeft,finRight,halo,cuff,spines;
    @SubscribeEvent public static void prepare(RenderPlayerEvent.Pre event){RenderPlayer r=event.getRenderer();if(INSTALLED.add(r))r.addLayer(new FormLayers(r));}
    private FormLayers(RenderPlayer renderer){
        this.renderer=renderer;ModelBase model=new ModelBase(){};model.textureWidth=64;model.textureHeight=64;
        plate=new ModelRenderer(model,0,0);plate.addBox(-3,-1,-3,6,3,6);
        finLeft=new ModelRenderer(model,0,16);finLeft.addBox(3,2,2,1,6,8);
        finRight=new ModelRenderer(model,20,16);finRight.addBox(-4,2,2,1,6,8);
        halo=new ModelRenderer(model,0,32);halo.addBox(-4,-4,0,8,1,1);halo.addBox(-4,3,0,8,1,1);halo.addBox(-4,-3,0,1,6,1);halo.addBox(3,-3,0,1,6,1);
        cuff=new ModelRenderer(model,32,0);cuff.addBox(-3,5,-3,6,3,6);
        spines=new ModelRenderer(model,32,32);for(int x=-3;x<=3;x+=3)spines.addBox(x,2,3,1,8,3);
    }
    @Override public void doRenderLayer(AbstractClientPlayer p,float limb,float amount,float partial,float age,float yaw,float pitch,float scale){
        if(!RunPoses.formVisuals||p.isInvisible()||p.isSpectator())return;
        Visual v=VISUALS.computeIfAbsent(p,key->new Visual());int kind=-1;for(int i=0;i<4;i++)if(Forms.EFFECTS[i]!=null&&p.isPotionActive(Forms.EFFECTS[i])){kind=i;break;}
        if(kind>=0)v.kind=kind;float clock=p.ticksExisted+partial,delta=Float.isNaN(v.age)?0:clock-v.age;
        if(delta<0||delta>40){v.alpha.value=0;v.alpha.velocity=0;delta=0;}
        v.alpha.step(kind>=0?1:0,delta,3);v.age=clock;if(v.kind<0||v.alpha.value<.01F)return;
        int color=new int[]{0xb99d72,0x83cfc4,0xe4c576,0x887baf}[v.kind];
        Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation(ValeOrders.ID,"textures/entity/form_overlay.png"));
        GlStateManager.enableBlend();GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);GlStateManager.color(((color>>16)&255)/255F,((color>>8)&255)/255F,(color&255)/255F,v.alpha.value*.8F);
        ModelPlayer body=renderer.getMainModel();
        try{
            if(v.kind==0){attach(body.bipedRightArm,plate,scale);attach(body.bipedLeftArm,plate,scale);}
            if(v.kind==1){finLeft.rotateAngleZ=.25F+(float)Math.sin(age*.2F)*.08F;finRight.rotateAngleZ=-finLeft.rotateAngleZ;attach(body.bipedBody,finLeft,scale);attach(body.bipedBody,finRight,scale);}
            if(v.kind==2){GlStateManager.pushMatrix();body.bipedBody.postRender(scale);GlStateManager.translate(0,-.6F+(float)Math.sin(age*.12F)*.025F,0);halo.rotateAngleZ=(float)Math.PI/4;halo.render(scale);GlStateManager.popMatrix();}
            if(v.kind==3){attach(body.bipedRightArm,cuff,scale);attach(body.bipedLeftArm,cuff,scale);attach(body.bipedBody,spines,scale);}
        }finally{GlStateManager.color(1,1,1,1);GlStateManager.disableBlend();}
    }
    private void attach(ModelRenderer anchor,ModelRenderer ornament,float scale){GlStateManager.pushMatrix();anchor.postRender(scale);ornament.render(scale);GlStateManager.popMatrix();}
    @Override public boolean shouldCombineTextures(){return false;}
}
