package village.vale;

import java.io.File;
import java.util.*;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.*;
import net.minecraft.util.*;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.*;

/** Cosmetic sprint pose for local and remote players. Vanilla actions take priority immediately. */
@SideOnly(Side.CLIENT)
public final class RunPoses {
    public static boolean enabled=true;
    public static boolean formVisuals=true;
    public static float easeIn=.22F,easeOut=.16F;
    private static final Map<EntityPlayer,State> STATES=new WeakHashMap<>();
    private static final class Arguments {ModelBiped model;Entity entity;float limb,age;}
    private static final ThreadLocal<Arguments> ARGUMENTS=ThreadLocal.withInitial(Arguments::new);
    private static final class State {final PoseMath.Spring spring=new PoseMath.Spring(),use=new PoseMath.Spring();float age=Float.NaN;double x,y,z;boolean suppressed;EnumHandSide foodSide=EnumHandSide.RIGHT;EnumAction foodAction=EnumAction.EAT;}
    private RunPoses(){}
    public static void configure(File folder){
        Configuration c=new Configuration(new File(folder,"valeorders-client.cfg"));c.load();
        enabled=c.getBoolean("armsBackRunning","animation",true,"Ease into an original arms-back sprint pose. Cosmetic only; item use, attacks, sneaking, water, riding and flight retain their normal poses.");
        formVisuals=c.getBoolean("auraFormVisuals","animation",true,"Original form ornaments follow the player's animated arms and body.");
        easeIn=c.getFloat("easeInSeconds","animation",.22F,.08F,.8F,"Spring response when starting to run.");easeOut=c.getFloat("easeOutSeconds","animation",.16F,.08F,.8F,"Spring response when slowing or stopping.");
        if(c.hasChanged())c.save();
    }
    public static float blend(EntityPlayer p){State s=STATES.get(p);return s==null?0:s.spring.value;}
    public static boolean suppressed(EntityPlayer p){State s=STATES.get(p);return s!=null&&s.suppressed;}
    public static void capture(ModelBiped model,Entity entity,float limb,float age){Arguments a=ARGUMENTS.get();a.model=model;a.entity=entity;a.limb=limb;a.age=age;}
    public static void finish(ModelBiped model){Arguments a=ARGUMENTS.get();if(a.model!=model)return;try{apply(model,a.entity,a.limb,a.age);village.vale.client.JutsuVfx.pose(model,a.entity,a.age);}finally{a.model=null;a.entity=null;}}
    public static void apply(ModelBiped model,Entity entity,float limbAmount,float age){
        if(!(entity instanceof EntityPlayer)||!entity.world.isRemote)return;EntityPlayer p=(EntityPlayer)entity;
        // First-person hand rendering passes age zero; keep hands, held-item use and view unchanged.
        if(Math.abs(age-p.ticksExisted)>2)return;
        State state=STATES.computeIfAbsent(p,key->new State());
        float partial=net.minecraft.client.Minecraft.getMinecraft().getRenderPartialTicks(),clock=p.ticksExisted+partial;
        ItemStack active=p.getActiveItemStack();EnumAction useAction=active.isEmpty()?EnumAction.NONE:active.getItemUseAction();boolean eating=p.isHandActive()&&(useAction==EnumAction.EAT||useAction==EnumAction.DRINK);
        if(eating){state.foodAction=useAction;state.foodSide=p.getActiveHand()==EnumHand.MAIN_HAND?p.getPrimaryHand():p.getPrimaryHand()==EnumHandSide.RIGHT?EnumHandSide.LEFT:EnumHandSide.RIGHT;}
        boolean action=p.isHandActive()||p.isSwingInProgress||p.getSwingProgress(partial)>.001F;
        boolean excluded=!enabled||!p.isEntityAlive()||p.isSpectator()||p.isSneaking()||p.isRiding()||p.isInWater()||p.isInLava()||p.isElytraFlying()||p.isPlayerSleeping()||p.capabilities.isFlying||action;
        boolean modelAction=model.swingProgress>.001F||model.isSneak||model.isRiding||model.rightArmPose==ModelBiped.ArmPose.BLOCK||model.leftArmPose==ModelBiped.ArmPose.BLOCK||model.rightArmPose==ModelBiped.ArmPose.BOW_AND_ARROW||model.leftArmPose==ModelBiped.ArmPose.BOW_AND_ARROW;
        if(Float.compare(state.age,clock)!=0){
            float delta=Float.isNaN(state.age)?0:clock-state.age;
            double displacement=(p.posX-state.x)*(p.posX-state.x)+(p.posY-state.y)*(p.posY-state.y)+(p.posZ-state.z)*(p.posZ-state.z);
            if(delta<0||delta>40||(!Float.isNaN(state.age)&&displacement>64)){state.spring.value=0;state.spring.velocity=0;delta=0;}
            double dx=p.posX-p.prevPosX,dz=p.posZ-p.prevPosZ;
            float speed=(float)Math.sqrt(Math.max(dx*dx+dz*dz,p.motionX*p.motionX+p.motionZ*p.motionZ));
            float target=!excluded&&p.isSprinting()?MathHelper.clamp((speed-.10F)/.16F,0,1):0;
            if(!p.onGround)target=p.fallDistance>2?0:target*.7F;
            state.spring.step(target,delta,(target>state.spring.value?easeIn:easeOut)*20);state.use.step(eating?1:0,delta,2.5F);state.age=clock;state.x=p.posX;state.y=p.posY;state.z=p.posZ;
        }
        state.suppressed=excluded;
        boolean foodPose=state.use.value>.001F&&!p.isSwingInProgress&&model.swingProgress<=.001F&&(eating||!p.isHandActive())&&model.rightArmPose!=ModelBiped.ArmPose.BOW_AND_ARROW&&model.leftArmPose!=ModelBiped.ArmPose.BOW_AND_ARROW&&model.rightArmPose!=ModelBiped.ArmPose.BLOCK&&model.leftArmPose!=ModelBiped.ArmPose.BLOCK;
        if(excluded||modelAction||state.spring.value<.001F){if(foodPose)food(model,state,clock);return;}
        float weight=state.spring.value*(1-state.use.value),turn=MathHelper.clamp(MathHelper.wrapDegrees(p.renderYawOffset-p.prevRenderYawOffset)/25F,-1,1);
        float ripple=(float)Math.sin(clock*.35F)*.025F,arm=1.14F+weight*.12F,lean=weight*.16F;
        model.bipedRightArm.rotateAngleX=mix(model.bipedRightArm.rotateAngleX,arm+ripple,weight);
        model.bipedLeftArm.rotateAngleX=mix(model.bipedLeftArm.rotateAngleX,arm-ripple,weight);
        model.bipedRightArm.rotateAngleY=mix(model.bipedRightArm.rotateAngleY,-turn*.18F,weight);
        model.bipedLeftArm.rotateAngleY=mix(model.bipedLeftArm.rotateAngleY,-turn*.18F,weight);
        model.bipedRightArm.rotateAngleZ=mix(model.bipedRightArm.rotateAngleZ,.14F+turn*.06F,weight);
        model.bipedLeftArm.rotateAngleZ=mix(model.bipedLeftArm.rotateAngleZ,-.14F+turn*.06F,weight);
        model.bipedBody.rotateAngleX+=lean;
        model.bipedRightLeg.rotationPointZ+=lean*10;model.bipedLeftLeg.rotationPointZ+=lean*10;
        if(foodPose)food(model,state,clock);
    }
    private static void food(ModelBiped m,State s,float clock){net.minecraft.client.model.ModelRenderer arm=s.foodSide==EnumHandSide.RIGHT?m.bipedRightArm:m.bipedLeftArm;float side=s.foodSide==EnumHandSide.RIGHT?-1:1,w=s.use.value;arm.rotateAngleX=mix(arm.rotateAngleX,-1.4F+(s.foodAction==EnumAction.EAT?(float)Math.sin(clock*1.2F)*.04F:0),w);arm.rotateAngleY=mix(arm.rotateAngleY,side*.4F,w);arm.rotateAngleZ=mix(arm.rotateAngleZ,side*.12F,w);}
    private static float mix(float a,float b,float weight){return a+(b-a)*weight;}
}
