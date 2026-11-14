package village.vale.client;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
/** Global voxel ceiling plus range LOD; individual effect and particle caps remain independent. */
@SideOnly(Side.CLIENT) public final class VfxBudget {
 private VfxBudget(){}public static final double MID=24,FAR=48;public static int lastUsed,lastSkipped;private static boolean installed;
 public static void install(){if(!installed){installed=true;net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new VfxBudget());}}
 @SubscribeEvent public void frame(TickEvent.RenderTickEvent e){if(e.phase==TickEvent.Phase.START){lastUsed=VfxDraw.frameUsed();lastSkipped=VfxDraw.frameSkipped();VfxDraw.frameBudget(cubeBudget());}}
 public static int cubeBudget(){return JutsuVfx.detail<=0?1200:JutsuVfx.detail==1?3000:9000;}
 /** Shared engine live count never exceeds600, including high detail. */
 public static int particleCap(){return JutsuVfx.detail<=0?200:600;}
 public static double lod(Entity e){Minecraft m=Minecraft.getMinecraft();Entity cam=m.getRenderViewEntity();if(m.world==null||m.player==null||m.getConnection()==null||e==null||e.isDead||e.world!=m.world||m.world.getEntityByID(e.getEntityId())!=e||cam==null)return 0;double d=cam.getDistanceSq(e);return !Double.isFinite(d)||d>FAR*FAR?0:d>MID*MID?.5:1;}
}
