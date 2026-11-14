package village.vale.arenas;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Private, bounded vanilla boat/world physics observation on existing Mist
 * source water. The guided registered rider is not a physical client and no
 * steering, giant-form or client-rendering acceptance is claimed. */
public final class BoatNativeChecks {
 private static MinecraftServer server;private static WorldServer world;private static EntityPlayerMP player;
 private static ArenaDirector.Definition d;private static EntityBoat boat;private static UUID boatId;private static String nonce;
 private static JsonObject report;private static JsonArray observations,failures,path;private static final List<BlockPos> water=new ArrayList<>();
 private static int stage,total,startBoatTicks,emptyTicks,mountedTicks,waterY;private static long emptyWorldTime,mountedWorldTime;
 private static boolean done;private static double oldX,oldY,oldZ,spawnY,settledY;private static GameType oldMode;private static int[] shore;
 private static AxisAlignedBB field;private static float healthBefore;
 private BoatNativeChecks(){}
 public static void begin(MinecraftServer s,List<ArenaDirector.Definition> all,EntityPlayerMP p){
  if(!Boolean.getBoolean("hbv.arenas.nativeProbe")||!Files.isRegularFile(Paths.get(".hbv-arenas-native-probe"))||!s.getFolderName().equals("arena-preview")||s.getPlayerList().getPlayerByUUID(p.getUniqueID())!=p||p instanceof net.minecraftforge.common.util.FakePlayer)throw new IllegalStateException("Private registered boat guard rejected");
  server=s;world=s.getWorld(0);player=p;d=null;for(ArenaDirector.Definition candidate:all)if(candidate.id.contains("mist"))d=candidate;
  report=new JsonObject();observations=new JsonArray();failures=new JsonArray();path=new JsonArray();report.add("observations",observations);report.add("failures",failures);report.add("human_shore_route",path);
  report.addProperty("scope","ACTUAL_VANILLA_BOAT_BUOYANCY_AND_REGISTERED_GUIDED_RIDER_ON_EXISTING_SOURCE_WATER");report.addProperty("physical_client_steering_or_giant_form_accepted",false);report.addProperty("health_assignment_or_immunity",false);report.addProperty("new_water_or_dock_blocks_written",false);
  oldX=p.posX;oldY=p.posY;oldZ=p.posZ;oldMode=p.interactionManager.getGameType();healthBefore=p.getHealth();nonce=UUID.randomUUID().toString();boat=null;boatId=null;water.clear();stage=total=emptyTicks=mountedTicks=0;done=false;
 }
 public static JsonObject result(){return report;}
 public static EntityPlayerMP currentPlayer(){return player;}
 private static void require(boolean okay,String why){if(!okay)throw new IllegalStateException(why);}
 private static JsonArray at(double x,double y,double z){JsonArray a=new JsonArray();a.add(x);a.add(y);a.add(z);return a;}
 private static void warm(BlockPos p){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){int cx=(p.getX()>>4)+x,cz=(p.getZ()>>4)+z;require(world.getChunkProvider().isChunkGeneratedAt(cx,cz),"Boat check refused ungenerated chunks");world.getChunk(cx,cz);}}
 private static AxisAlignedBB human(double x,double y,double z){return new AxisAlignedBB(x-.3,y,z-.3,x+.3,y+1.8,z+.3);}
 private static boolean safe(int[] p){AxisAlignedBB box=human(p[0]+.5,p[1],p[2]+.5);return world.getBlockState(new BlockPos(p[0],p[1]-1,p[2])).getMaterial().blocksMovement()&&world.getCollisionBoxes(player,box).isEmpty()&&!world.containsAnyLiquid(box);}
 private static void guide(int[] p){warm(new BlockPos(p[0],p[1],p[2]));require(safe(p),"Existing guided shore landing is blocked or wet");player.connection.setPlayerLocation(p[0]+.5,p[1],p[2]+.5,player.rotationYaw,player.rotationPitch);player.fallDistance=0;world.getPlayerChunkMap().updateMovingPlayer(player);}
 private static boolean source(BlockPos p){IBlockState s=world.getBlockState(p);return s.getBlock()==Blocks.WATER&&s.getBlock().getMetaFromState(s)==0;}
 private static void verifyWater(){for(BlockPos p:water){require(source(p),"Existing five-by-five source field changed after native fluid ticks");for(int y=1;y<=4;y++)require(world.isAirBlock(p.up(y)),"Boat field overhead clearance obstructed");}}
 private static void choose(){
  require(d!=null&&d.water,"Mist source-water definition unavailable");JsonObject run=ArenaDirector.run(d);require(run==null||ArenaDirector.text(run,"state").equals("AVAILABLE"),"Boat test requires empty Mist; existing failed fixture preserved");
  require(player.dimension==0&&player.world==world&&player.isEntityAlive()&&!player.isRiding(),"Boat participant must be registered, alive, on foot in Overworld");player.setGameType(GameType.SURVIVAL);
  waterY=d.min[1]+60;
  // Every candidate uses the already authored nineteen-wide dry bridge as its
  // shore. A field is accepted only from actual loaded native blocks/AABBs.
  for(int side:new int[]{1,-1})for(int z:new int[]{223,277}){
   int x=side>0?263:237;int[] landing={d.min[0]+(side>0?259:241),d.floor,d.min[2]+z};BlockPos middle=new BlockPos(d.min[0]+x,waterY,d.min[2]+z);warm(middle);if(!safe(landing))continue;
   List<BlockPos> candidate=new ArrayList<>();boolean okay=true;for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){BlockPos p=middle.add(dx,0,dz);candidate.add(p);okay&=source(p);for(int dy=1;dy<=4;dy++)okay&=world.isAirBlock(p.up(dy));}
   if(!okay)continue;shore=landing;water.addAll(candidate);field=new AxisAlignedBB(middle.getX()-2,waterY,middle.getZ()-2,middle.getX()+3,waterY+5,middle.getZ()+3);
   double bx=d.min[0]+(side>0?261.75:238.25),bz=middle.getZ()+.5;spawnY=waterY+2.25;
   require(world.getEntitiesWithinAABB(Entity.class,field).isEmpty(),"An existing actor occupies verified boat field");
   report.add("source_water_min",at(middle.getX()-2,waterY,middle.getZ()-2));report.add("source_water_max",at(middle.getX()+2,waterY,middle.getZ()+2));report.add("source_surface",at(bx,waterY+1,bz));report.add("existing_shore_standing",at(shore[0]+.5,shore[1],shore[2]+.5));report.addProperty("source_field_width",5);report.addProperty("source_field_length",5);report.addProperty("clear_height_above_source",4);
   // Independent actual human-size route from the combat anchor to the shore.
   int sx=d.boss_anchor[0],sz=d.boss_anchor[2];while(sz!=landing[2]){route(sx,d.floor,sz);sz+=Integer.signum(landing[2]-sz);}while(sx!=landing[0]){route(sx,d.floor,sz);sx+=Integer.signum(landing[0]-sx);}route(sx,d.floor,sz);
   guide(shore);for(BlockPos p:water){IBlockState state=world.getBlockState(p);state.getBlock().neighborChanged(state,world,p,Blocks.STONE,p.up());world.scheduleUpdate(p,state.getBlock(),1);}
   boat=new EntityBoat(world);boat.setPosition(bx,spawnY,bz);boat.getEntityData().setString("HbvPrivateBoatQA",nonce);boatId=boat.getUniqueID();report.addProperty("boat_uuid",boatId.toString());report.addProperty("actual_boat_width",boat.width);report.addProperty("actual_boat_height",boat.height);
   require(world.getCollisionBoxes(boat,boat.getEntityBoundingBox()).isEmpty(),"Native boat spawn hull intersects solids");require(world.spawnEntity(boat),"Actual vanilla boat spawn rejected");startBoatTicks=boat.ticksExisted;emptyWorldTime=world.getTotalWorldTime();stage=1;return;
  }
  throw new IllegalStateException("No existing clear five-by-five source-water field with a safe human shore");
 }
 private static void route(int x,int y,int z){int[] p={x,y,z};require(safe(p),"Actual human shore route has blocked headroom, liquid, or missing support");path.add(at(x,y,z));}
 private static boolean ownBoat(){return boat!=null&&boatId!=null&&boat.getUniqueID().equals(boatId)&&nonce.equals(boat.getEntityData().getString("HbvPrivateBoatQA"))&&world.getEntityFromUuid(boatId)==boat;}
 private static void live(String phase){
  require(ownBoat()&&!boat.isDead,"Owned native boat disappeared or was replaced");require(boat.posX>=field.minX+boat.width/2&&boat.posX<=field.maxX-boat.width/2&&boat.posZ>=field.minZ+boat.width/2&&boat.posZ<=field.maxZ-boat.width/2,"Native boat drifted beyond verified source field");
  require(world.getCollisionBoxes(boat,boat.getEntityBoundingBox()).isEmpty(),"Native boat hull collides with solid structure");require(boat.posY>=waterY-.1&&boat.posY<=waterY+1.6,"Native boat has sunk or escaped its water surface");
  AxisAlignedBB probe=boat.getEntityBoundingBox().grow(.001,.12,.001).offset(0,-.14,0);require(world.containsAnyLiquid(probe),"Native boat hull no longer touches real liquid");
  JsonObject r=new JsonObject();r.addProperty("phase",phase);r.addProperty("world_tick",world.getTotalWorldTime());r.addProperty("boat_ticks",boat.ticksExisted);r.add("position",at(boat.posX,boat.posY,boat.posZ));r.add("velocity",at(boat.motionX,boat.motionY,boat.motionZ));r.addProperty("hull_touches_actual_liquid",true);r.addProperty("solid_collision_free",true);observations.add(r);
 }
 private static void finish(boolean passed){
  try{if(boat!=null&&player.getRidingEntity()==boat)player.dismountRidingEntity();if(player.isEntityAlive()&&shore!=null)guide(shore);if(ownBoat()){boat.setDead();report.addProperty("own_uuid_only_boat_cleanup",true);}if(player.isEntityAlive()){player.setGameType(oldMode);int[] previous={(int)Math.floor(oldX),(int)Math.floor(oldY),(int)Math.floor(oldZ)};if(safe(previous))player.connection.setPlayerLocation(oldX,oldY,oldZ,player.rotationYaw,player.rotationPitch);}}
  catch(Throwable cleanup){failures.add("cleanup: "+cleanup);passed=false;}
  report.addProperty("health_before",healthBefore);report.addProperty("health_after",player.getHealth());report.addProperty("actual_empty_boat_ticks",emptyTicks);report.addProperty("actual_mounted_boat_ticks",mountedTicks);report.addProperty("passed",passed&&failures.size()==0);done=true;
 }
 public static boolean tick(){
  if(done)return true;
  try{require(++total<=260,"Bounded boat native budget exceeded");require(server.getPlayerList().getPlayerByUUID(player.getUniqueID())==player&&player.isEntityAlive(),"Registered boat participant changed or died");
   if(stage==0){choose();return false;}
   if(stage==1){emptyTicks=boat.ticksExisted-startBoatTicks;if(emptyTicks<40)return false;require(world.getTotalWorldTime()-emptyWorldTime>=40,"Boat ticks did not correspond to actual world ticks");verifyWater();live("EMPTY_NATIVE_BUOYANCY_AFTER_40_WORLD_TICKS");settledY=boat.posY;require(spawnY-settledY>.3,"Native empty boat did not settle under gravity into water");report.addProperty("native_gravity_settling_distance",spawnY-settledY);require(player.getDistance(boat)<=4,"Existing safe boarding shore is out of bounded reach");require(player.startRiding(boat),"Actual registered player startRiding rejected");require(player.getRidingEntity()==boat&&boat.getPassengers().contains(player),"Native passenger graph does not contain registered rider");startBoatTicks=boat.ticksExisted;mountedWorldTime=world.getTotalWorldTime();stage=2;return false;}
   if(stage==2){mountedTicks=boat.ticksExisted-startBoatTicks;require(player.getRidingEntity()==boat&&boat.getPassengers().contains(player),"Native rider detached before eighty boat ticks");if(mountedTicks%10==0)live("REGISTERED_RIDER_NATIVE_TICKS_"+mountedTicks);if(mountedTicks<80)return false;require(world.getTotalWorldTime()-mountedWorldTime>=80,"Mounted boat ticks did not correspond to actual world ticks");verifyWater();live("MOUNTED_NATIVE_BUOYANCY_AFTER_80_WORLD_TICKS");require(Math.abs(boat.posY-settledY)<.8,"Rider caused native hull to sink from stable surface");require(world.getCollisionBoxes(player,player.getEntityBoundingBox()).isEmpty(),"Registered riding human AABB intersects solids");report.addProperty("actual_registered_start_riding",true);report.addProperty("eighty_native_boat_and_world_ticks",true);player.dismountRidingEntity();report.add("native_immediate_dismount_position",at(player.posX,player.posY,player.posZ));require(player.getRidingEntity()==null&&!boat.getPassengers().contains(player),"Actual vanilla dismount retained passenger graph");report.addProperty("actual_native_dismount",true);report.addProperty("native_immediate_dismount_collision_free",world.getCollisionBoxes(player,player.getEntityBoundingBox()).isEmpty());report.addProperty("native_immediate_dismount_in_liquid",world.containsAnyLiquid(player.getEntityBoundingBox()));guide(shore);report.addProperty("guided_existing_shore_safe_return",safe(shore));report.addProperty("unassisted_client_dismount_to_dry_dock_accepted",false);stage=3;return false;}
   if(stage==3){require(!player.isRiding()&&safe(shore),"Human shore return lost native support or clearance");finish(true);return true;}
  }catch(Throwable failure){failures.add(failure.toString());finish(false);return true;}
  return false;
 }
}
