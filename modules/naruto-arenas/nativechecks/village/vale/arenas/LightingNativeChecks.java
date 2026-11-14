package village.vale.arenas;

import com.google.gson.*;
import java.util.*;
import net.minecraft.block.material.Material;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Profile-free native light initialization for a NEW cold arena copy. Only
 * ordinary world light propagation is requested; no light arrays or blocks are
 * assigned. Second-process readings precede any explicit relight request. */
public final class LightingNativeChecks {
 private LightingNativeChecks(){}
 private static MinecraftServer server;private static WorldServer world;private static String originalRandomTicks;
 private static List<ArenaDirector.Definition> definitions;private static List<ArenaDirector.Sample> points;
 private static JsonObject report,row;private static JsonArray arenas,samples,failures;
 private static int arenaIndex,pointIndex;private static boolean done,second;private static long start;
 private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 public static void prepare(MinecraftServer s){
  check(Boolean.getBoolean("hbv.arenas.nativeProbe")&&Boolean.getBoolean("hbv.arenas.lightingOnly")&&s.getPlayerList().getCurrentPlayerCount()==0&&s.getFolderName().equals("arena-preview"),"Private profile-free lighting guard rejected");
  server=s;world=s.getWorld(0);originalRandomTicks=world.getGameRules().getString("randomTickSpeed");world.getGameRules().setOrCreateGameRule("randomTickSpeed","0");
 }
 public static void begin(MinecraftServer s,List<ArenaDirector.Definition> defs){
  check(server==s&&originalRandomTicks!=null&&s.getPlayerList().getCurrentPlayerCount()==0,"Lighting early preparation missing or player appeared");definitions=new ArrayList<>(defs);check(definitions.size()==5,"Five arena definitions required");arenaIndex=pointIndex=0;points=null;done=false;second=Integer.getInteger("hbv.arenas.probeIteration",1)>1;start=world.getTotalWorldTime();report=new JsonObject();arenas=new JsonArray();report.add("arenas",arenas);report.addProperty("scope","PROFILE_FREE_NATIVE_LIGHT_INITIALIZATION_ONLY; no login, reward or encounter acceptance");report.addProperty("second_process_before_explicit_relight",second);report.addProperty("light_arrays_assigned",false);report.addProperty("blocks_or_tiles_assigned",false);report.addProperty("randomTickSpeed_original",originalRandomTicks);report.addProperty("randomTickSpeed_during_initialization",world.getGameRules().getString("randomTickSpeed"));ArenaDirector.store().dirty();
 }
 private static void startArena(ArenaDirector.Definition d){
  points=new ArrayList<>();for(String anchor:new String[]{"entry","boss","reward","exit"}){ArenaDirector.Sample p=new ArenaDirector.Sample();p.name="anchor:"+anchor;p.traversal="walk";p.at=d.point(anchor);points.add(p);}if(d.samples!=null)points.addAll(Arrays.asList(d.samples));pointIndex=0;row=new JsonObject();samples=new JsonArray();failures=new JsonArray();row.addProperty("id",d.id);row.addProperty("template_sha256",d.template_sha256);row.add("samples",samples);row.add("failures",failures);arenas.add(row);
 }
 private static void sample(ArenaDirector.Definition d,ArenaDirector.Sample sample){
  BlockPos at=new BlockPos(sample.at[0],sample.at[1],sample.at[2]);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)check(world.getChunkProvider().isChunkGeneratedAt((at.getX()>>4)+x,(at.getZ()>>4)+z),"Lighting refused ungenerated neighboring chunk");NativeProbe.prewarm(world,at);
  int before=world.getLightFor(EnumSkyBlock.BLOCK,at);if(!second)NativeProbe.seedLights(world,d,at);int after=world.getLightFor(EnumSkyBlock.BLOCK,at);boolean swim="swim".equals(sample.traversal);AxisAlignedBB box=new AxisAlignedBB(sample.at[0]+.2,sample.at[1],sample.at[2]+.2,sample.at[0]+.8,sample.at[1]+1.8,sample.at[2]+.8);boolean clear=swim?world.getCollisionBoxes(null,box).isEmpty()&&world.getBlockState(at).getMaterial()==Material.WATER&&world.getBlockState(at.up()).getMaterial()==Material.WATER:ArenaDirector.safe(world,sample.at);
  JsonObject point=new JsonObject();point.addProperty("name",sample.name);point.add("at",ArenaDirector.JSON.toJsonTree(sample.at));point.addProperty("traversal",swim?"swim":"walk");point.addProperty("native_collision_and_traversal",clear);point.addProperty("block_light_before_explicit_relight",before);point.addProperty("block_light_after",after);point.addProperty("explicit_relight_requested",!second);samples.add(point);if(!clear||after<8||(second&&before<8))failures.add("Native initialization point "+sample.name+" at "+at+" clear="+clear+" before="+before+" after="+after);
 }
 public static boolean tick(){
  if(done)return true;try{check(server.getPlayerList().getCurrentPlayerCount()==0,"Player login during profile-free initialization");if(arenaIndex>=definitions.size()){finish();return true;}ArenaDirector.Definition d=definitions.get(arenaIndex);if(points==null)startArena(d);for(int n=0;n<16&&pointIndex<points.size();n++,pointIndex++)sample(d,points.get(pointIndex));if(pointIndex>=points.size()){row.addProperty("passed",failures.size()==0);points=null;arenaIndex++;}
   if(points!=null&&pointIndex>0){BlockPos near=new BlockPos(points.get(pointIndex-1).at[0],points.get(pointIndex-1).at[1],points.get(pointIndex-1).at[2]);for(net.minecraft.world.chunk.Chunk chunk:new ArrayList<>(world.getChunkProvider().getLoadedChunks()))if(Math.abs(chunk.x-(near.getX()>>4))>3||Math.abs(chunk.z-(near.getZ()>>4))>3)world.getChunkProvider().queueUnload(chunk);}
  }catch(Throwable failure){report.addProperty("error",failure.toString());finish();return true;}return false;
 }
 public static void restoreRule(){if(world!=null&&originalRandomTicks!=null)world.getGameRules().setOrCreateGameRule("randomTickSpeed",originalRandomTicks);}
 private static void finish(){restoreRule();report.addProperty("randomTickSpeed_restored",world.getGameRules().getString("randomTickSpeed"));report.addProperty("actual_world_ticks",world.getTotalWorldTime()-start);report.addProperty("official_registered_players",server.getPlayerList().getCurrentPlayerCount());boolean passed=!report.has("error")&&arenas.size()==5;for(JsonElement a:arenas)passed&=a.getAsJsonObject().has("passed")&&a.getAsJsonObject().get("passed").getAsBoolean();report.addProperty("passed",passed);done=true;}
 public static JsonObject result(){return report;}
}
