package village.vale.client;

import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import village.vale.Dodge;
import village.vale.DodgeInput;
import village.vale.DodgeMath;
import village.vale.DodgeTrail;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import village.vale.ValeOrders;

/** Rebindable, physical-edge input and server-confirmed body pose; never changes the player's camera. */
@SideOnly(Side.CLIENT)
public final class DodgeClient {
    public static final KeyBinding DODGE=new KeyBinding("key.valeorders.dodge",KeyConflictContext.IN_GAME,KeyModifier.NONE,Keyboard.KEY_V,"key.categories.valeorders");
    private static final DodgeInput INPUT=new DodgeInput();
    private static final Map<UUID,Frame> ACTIVE=new LinkedHashMap<>();
    private static final Map<ModelBiped,float[]> POSES=new WeakHashMap<>();
    private static World world;private static Object connection;private static UUID player;private static long ticks,ready,notice;private static String stroke="";private static boolean initialized;
    private static final Field PRESS_TIME=pressField();
    private static final class Frame {final UUID owner;final int entity,direction;final long started,sequence;final DodgeTrail trail=new DodgeTrail();Frame(Dodge.Anim animation){owner=animation.owner();entity=animation.entity();direction=animation.direction();started=ticks;sequence=animation.sequence();}}
    private DodgeClient(){}
    public static KeyBinding binding(){return DODGE;}
    public static void initialize(){if(initialized)return;initialized=true;ClientRegistry.registerKeyBinding(DODGE);Dodge.client(DodgeClient::accept);MinecraftForge.EVENT_BUS.register(new DodgeClient());}
    private static Field pressField(){try{return ReflectionHelper.findField(KeyBinding.class,"pressTime","field_151474_i");}catch(RuntimeException absent){return null;}}
    private static boolean drain(){if(PRESS_TIME!=null)try{int count=PRESS_TIME.getInt(DODGE);PRESS_TIME.setInt(DODGE,0);return count>0;}catch(IllegalAccessException inaccessible){}boolean queued=false;for(int i=0;i<64&&DODGE.isPressed();i++)queued=true;return queued;}
    private static boolean physical(){int key=DODGE.getKeyCode();if(key==0)return false;try{if(key<0)return Mouse.isCreated()&&key>=-100&&Mouse.isButtonDown(key+100);return Keyboard.isCreated()&&key<256?Keyboard.isKeyDown(key):DODGE.isKeyDown();}catch(IndexOutOfBoundsException invalid){return false;}}
    private static void reset(Minecraft mc){for(Map.Entry<ModelBiped,float[]> entry:new ArrayList<>(POSES.entrySet())){ModelBiped model=entry.getKey();if(model!=null)restore(model,entry.getValue());}POSES.clear();ACTIVE.clear();world=mc.world;connection=mc.getConnection();player=mc.player==null?null:mc.player.getUniqueID();ticks=ready=notice=0;stroke=keyStroke();drain();INPUT.reset(physical());}
    private static String keyStroke(){return DODGE.getKeyCode()+"/"+DODGE.getKeyModifier().name();}
    private static void context(Minecraft mc){UUID id=mc.player==null?null:mc.player.getUniqueID();if(mc.world!=world||mc.getConnection()!=connection||!Objects.equals(player,id))reset(mc);}
    private static boolean usable(Entity e){return e instanceof EntityPlayer&&e.world==world&&e.isEntityAlive()&&!((EntityPlayer)e).isSpectator()&&!((EntityPlayer)e).isPlayerSleeping()&&!e.isRiding()&&!((EntityPlayer)e).isElytraFlying()&&!((EntityPlayer)e).capabilities.isFlying;}
    /** Existing legacy action wins a real active conflict; never execute both actions from one physical press. */
    public static String conflict(){Minecraft mc=Minecraft.getMinecraft();if(mc.gameSettings==null||DODGE.getKeyCode()==0)return "";for(KeyBinding other:mc.gameSettings.keyBindings)if(other!=null&&other!=DODGE&&other.getKeyCode()!=0&&other.getKeyConflictContext().isActive()&&other.getKeyModifier().isActive(other.getKeyConflictContext())&&DODGE.conflicts(other))return other.getKeyDescription();return "";}
    private static boolean server(){Minecraft mc=Minecraft.getMinecraft();if(mc.getConnection()==null)return false;try{NetworkDispatcher dispatcher=NetworkDispatcher.get(mc.getConnection().getNetworkManager());return dispatcher!=null&&dispatcher.getModList()!=null&&DodgeMath.supports(dispatcher.getModList().get(ValeOrders.ID));}catch(RuntimeException|LinkageError missing){return false;}}
    @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true)public void gui(GuiOpenEvent event){if(event.getGui()!=null){drain();INPUT.press(false,physical(),false,false);}}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();context(mc);ticks++;boolean queued=drain(),held=physical();String current=keyStroke();if(!current.equals(stroke)){stroke=current;INPUT.reset(held);queued=false;}
        boolean allowed=mc.world!=null&&mc.player!=null&&mc.currentScreen==null&&mc.inGameHasFocus&&usable(mc.player)&&DODGE.getKeyConflictContext().isActive()&&DODGE.getKeyModifier().isActive(DODGE.getKeyConflictContext());String conflict=allowed?conflict():"";
        boolean requested=INPUT.press(allowed,held,queued,!conflict.isEmpty());
        if(allowed&&queued&&!conflict.isEmpty()&&ticks>=notice){mc.player.sendStatusMessage(new TextComponentTranslation("message.valeorders.dodge_conflict",net.minecraft.client.resources.I18n.format(conflict)),true);notice=ticks+100;}
        if(requested&&ticks>=ready){if(server()){ready=ticks+4;Dodge.request(DodgeMath.direction(mc.player.movementInput.moveForward,mc.player.movementInput.moveStrafe),mc.player.dimension);}else if(ticks>=notice){mc.player.sendStatusMessage(new TextComponentTranslation("message.valeorders.dodge_server"),true);notice=ticks+100;}}
        for(Iterator<Map.Entry<UUID,Frame>> iterator=ACTIVE.entrySet().iterator();iterator.hasNext();){Frame frame=iterator.next().getValue();Entity e=mc.world==null?null:mc.world.getEntityByID(frame.entity);long age=ticks-frame.started;if(e==null||!e.getUniqueID().equals(frame.owner)||!usable(e)||age<0||age>120){iterator.remove();continue;}if(age<=DodgeMath.ANIM_TICKS)frame.trail.sample(e.posX,e.posY,e.posZ,((EntityPlayer)e).renderYawOffset);}
    }
    private static void accept(Dodge.Anim message,Object expected){Minecraft mc=Minecraft.getMinecraft();mc.addScheduledTask(()->{if(mc.getConnection()!=expected||mc.world==null||mc.world.provider.getDimension()!=message.dimension())return;context(mc);Entity entity=mc.world.getEntityByID(message.entity());if(entity==null||!message.owner().equals(entity.getUniqueID())||!usable(entity))return;Frame old=ACTIVE.get(message.owner());if(old!=null&&old.sequence>=message.sequence())return;if(old==null&&ACTIVE.size()>=256)return;Frame frame=new Frame(message);frame.trail.sample(entity.posX,entity.posY,entity.posZ,((EntityPlayer)entity).renderYawOffset);ACTIVE.put(message.owner(),frame);DodgePixels.takeoff(entity);if(entity==mc.player)ready=Math.max(ready,ticks+message.cooldown());});}
    static double age(Entity entity,float partial){if(!usable(entity))return -1;Frame frame=ACTIVE.get(entity.getUniqueID());if(frame==null||frame.entity!=entity.getEntityId())return -1;double age=ticks-frame.started+Math.max(0,Math.min(1,partial));return age>=0&&age<=DodgeMath.ANIM_TICKS?age:-1;}
    static int dir(Entity entity){Frame frame=ACTIVE.get(entity.getUniqueID());return frame==null?-1:frame.direction;}
    /** Called inside vanilla RenderLivingBase's existing body matrix, after its final cancellation gate. No push/pop pair. */
    public static void rotate(Entity entity,float bodyYaw,float partial){double age=age(entity,partial);if(age<0||!Float.isFinite(bodyYaw))return;double[] lean=DodgeMath.lean(dir(entity),age);GlStateManager.translate(0,-.22*DodgeMath.tuck(age),0);GlStateManager.rotate(180-bodyYaw,0,1,0);GlStateManager.rotate((float)lean[0],1,0,0);GlStateManager.rotate((float)lean[1],0,0,1);GlStateManager.rotate(bodyYaw-180,0,1,0);}
    @SubscribeEvent public void ghosts(RenderWorldLastEvent event){Minecraft mc=Minecraft.getMinecraft();context(mc);if(mc.world==null||mc.getRenderViewEntity()==null)return;List<DodgePixels.Image> images=new ArrayList<>();for(Frame frame:ACTIVE.values()){Entity entity=mc.world.getEntityByID(frame.entity);double age=entity==null?-1:age(entity,event.getPartialTicks());if(age<0||entity.getDistanceSq(mc.getRenderViewEntity())>1024||entity==mc.player&&mc.gameSettings.thirdPersonView==0)continue;images.add(new DodgePixels.Image(frame.trail,age,frame.direction));}DodgePixels.render(images,event.getPartialTicks());}
    /** Restore only our previous frame's limb modifications before vanilla computes the next pose. */
    public static void beginModel(ModelBiped model){float[] original=POSES.remove(model);if(original!=null)restore(model,original);}
    private static net.minecraft.client.model.ModelRenderer[] parts(ModelBiped m){return new net.minecraft.client.model.ModelRenderer[]{m.bipedHead,m.bipedHeadwear,m.bipedRightArm,m.bipedLeftArm,m.bipedRightLeg,m.bipedLeftLeg};}
    private static boolean saveModel(ModelBiped model){if(POSES.size()>=128&&!POSES.containsKey(model))return false;float[] values=new float[18];int i=0;for(net.minecraft.client.model.ModelRenderer part:parts(model)){values[i++]=part.rotateAngleX;values[i++]=part.rotateAngleY;values[i++]=part.rotateAngleZ;}POSES.put(model,values);return true;}
    private static void restore(ModelBiped model,float[] values){int i=0;for(net.minecraft.client.model.ModelRenderer part:parts(model)){part.rotateAngleX=values[i++];part.rotateAngleY=values[i++];part.rotateAngleZ=values[i++];}}
    public static void pose(ModelBiped m,Entity entity,float ageInTicks){if(!(entity instanceof EntityPlayer)||ageInTicks==0)return;double age=age(entity,Minecraft.getMinecraft().getRenderPartialTicks());if(age<0||!saveModel(m))return;int direction=dir(entity);float k=(float)DodgeMath.tuck(age);
        if(direction==DodgeMath.FRONT){m.bipedHead.rotateAngleX=lerp(m.bipedHead.rotateAngleX,-.6f,k);m.bipedRightArm.rotateAngleX=lerp(m.bipedRightArm.rotateAngleX,1.35f,k);m.bipedLeftArm.rotateAngleX=lerp(m.bipedLeftArm.rotateAngleX,1.35f,k);m.bipedRightArm.rotateAngleZ=lerp(m.bipedRightArm.rotateAngleZ,.25f,k);m.bipedLeftArm.rotateAngleZ=lerp(m.bipedLeftArm.rotateAngleZ,-.25f,k);m.bipedRightLeg.rotateAngleX=lerp(m.bipedRightLeg.rotateAngleX,-1,k);m.bipedLeftLeg.rotateAngleX=lerp(m.bipedLeftLeg.rotateAngleX,.8f,k);}
        else if(direction==DodgeMath.BACK){m.bipedHead.rotateAngleX=lerp(m.bipedHead.rotateAngleX,.35f,k);m.bipedRightArm.rotateAngleX=lerp(m.bipedRightArm.rotateAngleX,-1.45f,k);m.bipedLeftArm.rotateAngleX=lerp(m.bipedLeftArm.rotateAngleX,-1.2f,k);m.bipedRightArm.rotateAngleY=lerp(m.bipedRightArm.rotateAngleY,-.45f,k);m.bipedLeftArm.rotateAngleY=lerp(m.bipedLeftArm.rotateAngleY,.45f,k);m.bipedRightLeg.rotateAngleX=lerp(m.bipedRightLeg.rotateAngleX,-.9f,k);m.bipedLeftLeg.rotateAngleX=lerp(m.bipedLeftLeg.rotateAngleX,.55f,k);}
        else {boolean left=direction==DodgeMath.LEFT;float side=left?1:-1;net.minecraft.client.model.ModelRenderer trail=left?m.bipedRightArm:m.bipedLeftArm,guard=left?m.bipedLeftArm:m.bipedRightArm;trail.rotateAngleZ=lerp(trail.rotateAngleZ,1.25f*side,k);trail.rotateAngleX=lerp(trail.rotateAngleX,.3f,k);guard.rotateAngleX=lerp(guard.rotateAngleX,-1.35f,k);guard.rotateAngleY=lerp(guard.rotateAngleY,.6f*side,k);m.bipedRightLeg.rotateAngleZ=lerp(m.bipedRightLeg.rotateAngleZ,.6f,k);m.bipedLeftLeg.rotateAngleZ=lerp(m.bipedLeftLeg.rotateAngleZ,-.6f,k);m.bipedRightLeg.rotateAngleX=lerp(m.bipedRightLeg.rotateAngleX,0,k);m.bipedLeftLeg.rotateAngleX=lerp(m.bipedLeftLeg.rotateAngleX,0,k);m.bipedHead.rotateAngleZ=lerp(m.bipedHead.rotateAngleZ,-.35f*side,k);}
        m.bipedHeadwear.rotateAngleX=m.bipedHead.rotateAngleX;m.bipedHeadwear.rotateAngleY=m.bipedHead.rotateAngleY;m.bipedHeadwear.rotateAngleZ=m.bipedHead.rotateAngleZ;
    }
    private static float lerp(float a,float b,float weight){return a+(b-a)*weight;}
}
