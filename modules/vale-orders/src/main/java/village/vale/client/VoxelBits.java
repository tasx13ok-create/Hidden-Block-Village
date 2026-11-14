package village.vale.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import org.lwjgl.opengl.GL11;
import village.vale.WeaponMath;
import village.vale.arsenal.*;

/** One bounded client-only voxel store for weapons and opt-in armor. No gameplay mutations. */
@SideOnly(Side.CLIENT)
public final class VoxelBits {
 public static final double PX=1/16.0;static final Random RNG=new Random();
 private static final Minecraft MC=Minecraft.getMinecraft();
 private static final List<Bit> BITS=new ArrayList<>();
 private static final VoxelRules.Epoch EPOCH=new VoxelRules.Epoch();
 private static final VoxelRules.Receipts<Entity> RECEIPTS=new VoxelRules.Receipts<>();
 private static final IdentityHashMap<Entity,Integer> PENDING=new IdentityHashMap<>();
 private static final Set<ArsenalShot> FIRE=Collections.newSetFromMap(new IdentityHashMap<ArsenalShot,Boolean>());
 private static boolean installed;private static long version=-1;private static int simulated=Integer.MIN_VALUE,armorCount,weaponCount;
 private static final class Bit{double x,y,z,px,py,pz,vx,vy,vz,size,gravity,drag;int rgb,age,life;float alpha;boolean bounce,armor;}
 private VoxelBits(){}
 public static void install(){if(!installed){installed=true;net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new VoxelBits());}}
 private static boolean context(){if(!MC.isCallingFromMinecraftThread())return false;boolean ready=EPOCH.prepare(MC.world,MC.player,MC.getConnection(),MC.player==null?-1:MC.player.ticksExisted);if(version!=EPOCH.version()){version=EPOCH.version();BITS.clear();PENDING.clear();RECEIPTS.clear();FIRE.clear();armorCount=weaponCount=0;simulated=Integer.MIN_VALUE;}return ready;}
 private static boolean alive(Entity e){return e!=null&&!e.isDead&&e.world==MC.world&&MC.world.getEntityByID(e.getEntityId())==e;}
 public static void track(Entity e){if(!context()||e==null||e.isDead||e.world!=MC.world||!(e instanceof WeaponPulse||e instanceof ArsenalShot)||RECEIPTS.contains(e)||RECEIPTS.size()>=512||PENDING.size()>=512)return;if(!PENDING.containsKey(e))PENDING.put(e,EPOCH.tick());}
 @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void added(EntityJoinWorldEvent e){if(!e.isCanceled()&&e.getWorld().isRemote)track(e.getEntity());}
 private static boolean visible(double x,double y,double z){Entity camera=MC.getRenderViewEntity();return camera!=null&&camera.getDistanceSq(x,y,z)<=64*64;}
 private static boolean finite(double... values){for(double value:values)if(!Double.isFinite(value))return false;return true;}
 private static boolean add(boolean armor,double x,double y,double z,double vx,double vy,double vz,double size,int rgb,float alpha,int life,double gravity,double drag,boolean bounce){
  if(!context()||!VfxPreferences.motion()||!finite(x,y,z,vx,vy,vz,size,gravity,drag)||Math.abs(x)>32000000||Math.abs(y)>32000000||Math.abs(z)>32000000||Math.abs(vx)>4||Math.abs(vy)>4||Math.abs(vz)>4||size<=0||size>1||!Float.isFinite(alpha)||alpha<=0||alpha>1||life<1||life>120||Math.abs(gravity)>1||drag<0||drag>1||!visible(x,y,z)||!EPOCH.admit(armor))return false;
  int count=armor?armorCount:weaponCount,cap=armor?128:472;if(count>=cap){for(Iterator<Bit> it=BITS.iterator();it.hasNext();){Bit old=it.next();if(old.armor==armor){it.remove();if(armor)armorCount--;else weaponCount--;break;}}}
  Bit b=new Bit();b.armor=armor;b.x=b.px=x;b.y=b.py=y;b.z=b.pz=z;b.vx=vx;b.vy=vy;b.vz=vz;b.size=size;b.rgb=rgb;b.alpha=alpha;b.life=life;b.gravity=gravity;b.drag=drag;b.bounce=bounce;BITS.add(b);if(armor)armorCount++;else weaponCount++;return true;
 }
 public static void spawn(double x,double y,double z,double vx,double vy,double vz,double pixels,int rgb,float alpha,int life,double gravity,double drag,boolean bounce){add(false,x,y,z,vx,vy,vz,pixels*PX,rgb,alpha,life,gravity,drag,bounce);}
 static boolean spawnArmor(double x,double y,double z,double vx,double vy,double vz,double size,int rgb,double alpha,int life,double signedGravity,double drag){return add(true,x,y,z,vx,vy,vz,size,rgb,(float)alpha,life,-signedGravity,drag,false);}
 static void clearArmor(){if(armorCount==0)return;BITS.removeIf(b->b.armor);armorCount=0;}
 public static int groundColor(Entity e){if(e==null||e.world!=MC.world)return -1;BlockPos pos=new BlockPos(e.posX,e.posY-.2,e.posZ);if(!e.world.isBlockLoaded(pos))return -1;try{net.minecraft.block.state.IBlockState state=e.world.getBlockState(pos);if(state.getMaterial()==net.minecraft.block.material.Material.AIR)return -1;int colour=state.getMapColor(e.world,pos).colorValue;return state.getBlock()==net.minecraft.init.Blocks.GRASS?0x6A9E3A:colour==0?0x7F7F7F:colour;}catch(RuntimeException|LinkageError unavailable){return -1;}}
 public static int vary(int rgb){return VfxDraw.shade(rgb,Math.min(1,.78+RNG.nextDouble()*.3));}
 @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){
  if(event.phase!=TickEvent.Phase.END||!context())return;if(!VfxPreferences.motion()){BITS.clear();armorCount=weaponCount=0;}
  if(simulated!=EPOCH.tick()){simulated=EPOCH.tick();for(int step=0;step<EPOCH.delta();step++)advance();admissions();if(VfxPreferences.motion())for(Iterator<ArsenalShot> it=FIRE.iterator();it.hasNext();){ArsenalShot shot=it.next();if(!alive(shot)||shot.mode()!=ArsenalShot.FIRE){it.remove();continue;}if(shot.ticksExisted%2==0)spawn(shot.posX,shot.posY+.12,shot.posZ,(RNG.nextDouble()-.5)*.03,.03,(RNG.nextDouble()-.5)*.03,1,RNG.nextBoolean()?0xFFB03A:0xFF6A1A,1f,10,-.002,.9,false);}}
 }
 private static void admissions(){
  RECEIPTS.retain(VoxelBits::alive);
  for(Entity e:new ArrayList<>(PENDING.keySet())){Integer queued=PENDING.get(e);if(queued==null)continue;if(!alive(e)||EPOCH.tick()-queued>20){PENDING.remove(e);continue;}
   if(e instanceof WeaponPulse){WeaponPulse pulse=(WeaponPulse)e;if(pulse.spec()==null)continue;PENDING.remove(e);if(RECEIPTS.take(e)){if(pulse.kind()==WeaponPulse.SWING)CombatFeel.acceptedSwing(pulse);if(pulse.kind()==WeaponPulse.CAST&&VfxPreferences.motion())WeaponVfx.debris(pulse,pulse.spec(),new double[]{pulse.posX,pulse.posY,pulse.posZ},WeaponMath.palette(pulse.spec().role.name()));}}
   else{ArsenalShot shot=(ArsenalShot)e;if(shot.spec()==null)continue;PENDING.remove(e);if(RECEIPTS.take(e)&&shot.mode()==ArsenalShot.FIRE&&FIRE.size()<128)FIRE.add(shot);}
  }
 }
 private static void advance(){for(Iterator<Bit> it=BITS.iterator();it.hasNext();){Bit b=it.next();b.px=b.x;b.py=b.y;b.pz=b.z;if(b.armor){b.x+=b.vx;b.y+=b.vy;b.z+=b.vz;b.vx*=b.drag;b.vy=(b.vy-b.gravity)*b.drag;b.vz*=b.drag;if(++b.age>=b.life||!visible(b.x,b.y,b.z)){it.remove();armorCount--;}continue;}b.vy-=b.gravity;b.vx*=b.drag;b.vy*=b.drag;b.vz*=b.drag;double ny=b.y+b.vy;
   if(b.bounce&&b.vy<0){BlockPos pos=new BlockPos(b.x,ny-b.size/2,b.z);if(MC.world.isBlockLoaded(pos))try{net.minecraft.block.state.IBlockState state=MC.world.getBlockState(pos);AxisAlignedBB box=state.getCollisionBoundingBox(MC.world,pos);if(box!=null){double top=pos.getY()+box.maxY;if(b.x>=pos.getX()+box.minX&&b.x<=pos.getX()+box.maxX&&b.z>=pos.getZ()+box.minZ&&b.z<=pos.getZ()+box.maxZ&&b.y-b.size/2>=top-.05&&ny-b.size/2<=top){ny=top+b.size/2;b.vy=-b.vy*.3;b.vx*=.6;b.vz*=.6;}}}catch(RuntimeException|LinkageError ignored){}}
   b.x+=b.vx;b.y=ny;b.z+=b.vz;if(++b.age>=b.life||!visible(b.x,b.y,b.z)){it.remove();if(b.armor)armorCount--;else weaponCount--;}}
 }
 @SubscribeEvent public void render(RenderWorldLastEvent event){
  if(!context()||!VfxPreferences.motion()||BITS.isEmpty())return;Entity camera=MC.getRenderViewEntity();if(camera==null)return;double partial=Double.isFinite(event.getPartialTicks())?Math.max(0,Math.min(1,event.getPartialTicks())):0;
  double cx=camera.lastTickPosX+(camera.posX-camera.lastTickPosX)*partial,cy=camera.lastTickPosY+(camera.posY-camera.lastTickPosY)*partial,cz=camera.lastTickPosZ+(camera.posZ-camera.lastTickPosZ)*partial;
  VfxDraw.begin(-cx,-cy,-cz);try{for(int lane=0;lane<2;lane++){boolean armor=lane==0;if((armor?armorCount:weaponCount)==0)continue;if(armor)VfxDraw.additive();else VfxDraw.normal();Tessellator t=Tessellator.getInstance();BufferBuilder buffer=t.getBuffer();buffer.begin(GL11.GL_QUADS,DefaultVertexFormats.POSITION_COLOR);for(Bit b:BITS){if(b.armor!=armor)continue;double progress=(b.age+partial)/b.life,shrink=progress<.6?1:Math.max(0,(1-progress)/.4),size=b.armor?b.size:Math.max(PX,Math.round(b.size*shrink/PX)*PX);float alpha=b.armor?(float)(b.alpha*(1-progress)):b.alpha;VfxDraw.cubeInto(buffer,b.px+(b.x-b.px)*partial,b.py+(b.y-b.py)*partial,b.pz+(b.z-b.pz)*partial,size,b.rgb,alpha);}t.draw();}}finally{VfxDraw.end();}
 }
}
