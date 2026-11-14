package village.vale.arenas;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Actual Nether environment/geometry and sacrificial bed activation, not a
 * graphical or dimension-aware production encounter acceptance. All staging is
 * performed cold by tools.arenas.nether into a NEW private candidate.
 */
public final class NativeNetherChecks {
 private static MinecraftServer server;private static EntityPlayerMP player;private static ArenaDirector.Definition d;
 private static WorldServer nether;private static JsonObject report,marker;private static JsonArray failures,samples,fluids;
 private static int stage,index,clock,wait,sampleIndex,restoreIndex;private static boolean done,secondBoot;
 private static double oldX,oldY,oldZ;private static int oldDimension;private static GameType oldMode;
 private static final List<BlockPos> furnace=new ArrayList<>(),lava=new ArrayList<>(),cage=new ArrayList<>();
 private static final Map<Long,Integer> beforeCage=new LinkedHashMap<>(),baselineLava=new LinkedHashMap<>();
 private static final Observer observer=new Observer();private static ExplosionEvent.Start explosionStart;private static ExplosionEvent.Detonate explosionDetonate;
 private static BlockPos bed;private static Entity nativeBody;private static int bodyTicks;private static State saved;
 private NativeNetherChecks(){}
 public static final class State extends WorldSavedData {
  NBTTagCompound value=new NBTTagCompound();public State(){this("hbv_arena_nether_privateqa_v1");}public State(String n){super(n);}
  public void readFromNBT(NBTTagCompound n){value=n.copy();}
  public NBTTagCompound writeToNBT(NBTTagCompound n){n.merge(value.copy());return n;}
 }
 private static State state(){String name="hbv_arena_nether_privateqa_v1";State s=(State)nether.getPerWorldStorage().getOrLoadData(State.class,name);if(s==null){s=new State();nether.getPerWorldStorage().setData(name,s);}return s;}
 public static void begin(MinecraftServer s,List<ArenaDirector.Definition> all,EntityPlayerMP p)throws Exception{
  if(!Boolean.getBoolean("hbv.arenas.nativeProbe")||!Files.isRegularFile(Paths.get(".hbv-arenas-native-probe"))||!s.getFolderName().equals("arena-preview")||s.getPlayerList().getPlayerByUUID(p.getUniqueID())!=p)throw new IllegalStateException("Private Nether guard rejected");
  Path receipt=Paths.get("arena-preview","ARENA-NETHER-STAGE.json");if(!Files.isRegularFile(receipt))throw new IllegalStateException("Cold staged Nether marker missing; no live paste allowed");
  marker=new JsonParser().parse(new String(Files.readAllBytes(receipt),StandardCharsets.UTF_8)).getAsJsonObject();if(marker.get("schema").getAsInt()!=1||marker.get("dimension").getAsInt()!=-1||!marker.get("source_unchanged").getAsBoolean())throw new IllegalStateException("Nether staging receipt invalid");
  d=null;for(ArenaDirector.Definition candidate:all)if(candidate.id.contains("_fire_")&&candidate.id.equals(marker.get("arena_id").getAsString()))d=candidate;
  if(d==null||!d.template_sha256.equals(marker.get("template_sha256").getAsString()))throw new IllegalStateException("Exact Fire template does not match staged Nether");
  server=s;player=p;nether=s.getWorld(-1);if(nether==null)throw new IllegalStateException("Native Nether dimension disabled or absent");
  oldMode=p.interactionManager.getGameType();oldDimension=p.dimension;oldX=p.posX;oldY=p.posY;oldZ=p.posZ;
  report=new JsonObject();samples=new JsonArray();fluids=new JsonArray();failures=new JsonArray();report.add("failures",failures);report.add("native_samples",samples);report.add("native_furnaces",fluids);
  report.addProperty("scope","REAL_FIRE_NETHER_ENVIRONMENT_GEOMETRY_AND_SACRIFICIAL_BED; production controller remains Overworld-only");report.addProperty("template_sha256",d.template_sha256);report.addProperty("graphical_or_shader_fog_acceptance",false);report.addProperty("production_nether_encounter_controller_accepted",false);
  report.addProperty("native_dimension",nether.provider.getDimension());report.addProperty("provider_class",nether.provider.getClass().getName());report.addProperty("save_folder",nether.provider.getSaveFolder());report.addProperty("can_respawn_here",nether.provider.canRespawnHere());report.addProperty("water_vaporizes",nether.provider.doesWaterVaporize());report.addProperty("surface_world",nether.provider.isSurfaceWorld());
  require(nether.provider.getDimension()==-1&&!nether.provider.canRespawnHere()&&nether.provider.doesWaterVaporize()&&!nether.provider.isSurfaceWorld(),"Actual Nether provider restrictions disagree");
  bed=new BlockPos(marker.getAsJsonArray("allowed_sacrificial_bed_center").get(0).getAsInt(),32,marker.getAsJsonArray("allowed_sacrificial_bed_center").get(2).getAsInt());
  require(!d.contains(bed)&&bed.getX()<d.min[0]-12&&bed.getZ()<d.min[2]-12,"Sacrificial bed is too close to authored stadium");
  saved=state();secondBoot=saved.value.hasKey("Template");if(secondBoot){require(saved.value.getString("Template").equals(d.template_sha256),"Saved Nether QA template drift");require(saved.value.getInteger("Schema")==1,"Unknown future Nether QA state preserved");}
  report.addProperty("second_process_persistence_pass",secondBoot);
  furnace.clear();lava.clear();cage.clear();beforeCage.clear();baselineLava.clear();
  for(int[] box:new int[][]{{95,35,140,112,62,185},{385,35,140,402,62,185}})for(int y=box[1];y<=box[4];y++)for(int z=box[2];z<=box[5];z++)for(int x=box[0];x<=box[3];x++)furnace.add(new BlockPos(d.min[0]+x,d.min[1]+y,d.min[2]+z));
  for(int y=-6;y<=6;y++)for(int z=-6;z<=6;z++)for(int x=-6;x<=6;x++)cage.add(bed.add(x,y,z));
  stage=index=clock=wait=sampleIndex=restoreIndex=0;done=false;explosionStart=null;explosionDetonate=null;MinecraftForge.EVENT_BUS.register(observer);
 }
 public static JsonObject result(){return report;}
 private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
 private static void load(WorldServer w,BlockPos p,int radius){for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){int cx=(p.getX()>>4)+x,cz=(p.getZ()>>4)+z;require(w.getChunkProvider().isChunkGeneratedAt(cx,cz),"Native Nether prewarm refused an ungenerated chunk");w.getChunk(cx,cz);}}
 private static boolean safe(WorldServer w,int[] at){BlockPos b=new BlockPos(at[0],at[1],at[2]);return w.isBlockLoaded(b)&&w.getBlockState(b.down()).getMaterial().isSolid()&&w.getCollisionBoxes(null,new AxisAlignedBB(at[0]+.2,at[1],at[2]+.2,at[0]+.8,at[1]+1.8,at[2]+.8)).isEmpty()&&!w.containsAnyLiquid(new AxisAlignedBB(b,b.add(1,2,1)));}
 private static final class FixedExit extends Teleporter {
  final int[] at;FixedExit(WorldServer w,int[] target){super(w);at=target;}
  public void placeInPortal(Entity e,float yaw){e.setLocationAndAngles(at[0]+.5,at[1],at[2]+.5,yaw,e.rotationPitch);e.motionX=e.motionY=e.motionZ=0;}
  public boolean placeInExistingPortal(Entity e,float yaw){placeInPortal(e,yaw);return true;}
  public boolean makePortal(Entity e){return false;}
 }
 private static void transfer(int dimension,int[] at){WorldServer target=server.getWorld(dimension);load(target,new BlockPos(at[0],at[1],at[2]),2);require(safe(target,at),"Dimensional exit landing is unsafe");server.getPlayerList().transferPlayerToDimension(player,dimension,new FixedExit(target,at));player.connection.setPlayerLocation(at[0]+.5,at[1],at[2]+.5,player.rotationYaw,player.rotationPitch);require(player.dimension==dimension&&player.world==target&&server.getPlayerList().getPlayerByUUID(player.getUniqueID())==player,"Actual registered dimensional transfer failed");}
 private static void seed(BlockPos pos){NativeProbe.seedLights(nether,d,pos);}

 private static void sample(String name,int[] at){BlockPos pos=new BlockPos(at[0],at[1],at[2]);load(nether,pos,2);int persistedLight=nether.getLightFor(EnumSkyBlock.BLOCK,pos);if(secondBoot)require(persistedLight>=8,"Actual second-process Nether light failed BEFORE relight: "+name+" at "+pos);seed(pos);nether.checkLightFor(EnumSkyBlock.BLOCK,pos);JsonObject r=new JsonObject();r.addProperty("name",name);r.addProperty("block_light_before_relight",persistedLight);r.addProperty("second_process_persistence_checked_before_relight",secondBoot);JsonArray coordinates=new JsonArray();for(int coordinate:at)coordinates.add(coordinate);r.add("at",coordinates);r.addProperty("human_aabb_clear",safe(nether,at));r.addProperty("block_light",nether.getLightFor(EnumSkyBlock.BLOCK,pos));r.addProperty("sky_light",nether.getLightFor(EnumSkyBlock.SKY,pos));r.addProperty("biome",net.minecraft.world.biome.Biome.getIdForBiome(nether.getBiome(pos)));samples.add(r);require(safe(nether,at),"Native Nether human support/collision failed: "+name);require(nether.getLightFor(EnumSkyBlock.BLOCK,pos)>=8,"Native Nether block light below8: "+name);require(nether.getLightFor(EnumSkyBlock.SKY,pos)==0,"Nether sky light is not zero");require(net.minecraft.world.biome.Biome.getIdForBiome(nether.getBiome(pos))==8,"Staged Nether biome is not actual Hell8");}
 private static boolean fluid(IBlockState state){return state.getBlock()==Blocks.LAVA||state.getBlock()==Blocks.FLOWING_LAVA;}
 private static void inspectLava(BlockPos p,boolean after){IBlockState current=nether.getBlockState(p);if(!after){baselineLava.put(p.toLong(),Block.getStateId(current));if(fluid(current))lava.add(p);return;}if(fluid(current)){require(current.getBlock().getMetaFromState(current)==0,"Native furnace contains non-source flowing lava");for(EnumFacing f:new EnumFacing[]{EnumFacing.DOWN,EnumFacing.NORTH,EnumFacing.SOUTH,EnumFacing.EAST,EnumFacing.WEST}){IBlockState neighbor=nether.getBlockState(p.offset(f));require(fluid(neighbor)||neighbor.getMaterial().blocksMovement(),"Native lava escaped or contacts an open owned boundary");}}if(secondBoot){String key=Long.toString(p.toLong());require(saved.value.getCompoundTag("Furnaces").hasKey(key),"Saved Nether furnace cell missing");int expected=saved.value.getCompoundTag("Furnaces").getInteger(key),actual=Block.getStateId(current);if(expected!=actual){IBlockState before=Block.getStateById(expected);boolean sourcePartners=fluid(before)&&fluid(current)&&before.getBlock().getMetaFromState(before)==0&&current.getBlock().getMetaFromState(current)==0;JsonObject difference=new JsonObject();difference.addProperty("x",p.getX());difference.addProperty("y",p.getY());difference.addProperty("z",p.getZ());difference.addProperty("before_raw_state",expected);difference.addProperty("after_raw_state",actual);difference.addProperty("before_registry",before.getBlock().getRegistryName().toString());difference.addProperty("after_registry",current.getBlock().getRegistryName().toString());difference.addProperty("before_meta",before.getBlock().getMetaFromState(before));difference.addProperty("after_meta",current.getBlock().getMetaFromState(current));difference.addProperty("only_level0_lava_source_partners",sourcePartners);if(!report.has("raw_furnace_restart_differences"))report.add("raw_furnace_restart_differences",new JsonArray());report.getAsJsonArray("raw_furnace_restart_differences").add(difference);require(sourcePartners,"Nether furnace structural or level state changed across actual process restart");}}}
 private static void save(){saved.value.setInteger("Schema",1);saved.value.setString("Template",d.template_sha256);NBTTagCompound states=new NBTTagCompound();for(BlockPos p:furnace)states.setInteger(Long.toString(p.toLong()),Block.getStateId(nether.getBlockState(p)));saved.value.setTag("Furnaces",states);saved.markDirty();nether.getPerWorldStorage().saveAllData();try{nether.saveAllChunks(true,null);nether.getSaveHandler().flush();}catch(Exception failure){throw new IllegalStateException("Native Nether save failed",failure);}}
 private static void bedVerify(){
  for(int n=0;n<128&&index<cage.size();n++,index++){BlockPos p=cage.get(index);load(nether,p,0);require(nether.isAirBlock(p)&&nether.getTileEntity(p)==null,"Sacrificial pad is not verified empty native void");beforeCage.put(p.toLong(),Block.getStateId(nether.getBlockState(p)));}
  if(index==cage.size()){require(nether.getEntitiesWithinAABB(Entity.class,new AxisAlignedBB(bed.add(-9,-9,-9),bed.add(10,10,10))).isEmpty(),"An actor occupies sacrificial bed exclusion volume");NBTTagCompound journal=new NBTTagCompound();for(Map.Entry<Long,Integer> e:beforeCage.entrySet())journal.setInteger(e.getKey().toString(),e.getValue());saved.value.setTag("BedBeforeJournal",journal);saved.value.setString("BedJournalStatus","PLANNED");saved.markDirty();nether.getPerWorldStorage().saveAllData();index=0;stage=5;}
 }
 private static void bedBuild(){
  for(int n=0;n<128&&index<cage.size();n++,index++){BlockPos p=cage.get(index);int dx=Math.abs(p.getX()-bed.getX()),dy=Math.abs(p.getY()-bed.getY()),dz=Math.abs(p.getZ()-bed.getZ());if(dx==6||dy==6||dz==6||(p.getY()==bed.getY()-1&&dx<=2&&dz<=2))nether.setBlockState(p,Blocks.OBSIDIAN.getDefaultState(),2);}
  if(index==cage.size()){IBlockState foot=Blocks.BED.getDefaultState().withProperty(BlockBed.FACING,EnumFacing.NORTH).withProperty(BlockBed.PART,BlockBed.EnumPartType.FOOT);nether.setBlockState(bed,foot,2);nether.setBlockState(bed.north(),foot.withProperty(BlockBed.PART,BlockBed.EnumPartType.HEAD),2);require(nether.getBlockState(bed).getBlock()==Blocks.BED&&nether.getBlockState(bed.north()).getBlock()==Blocks.BED,"Actual native sacrificial bed placement failed");stage=6;wait=2;}
 }
 private static void activateBed(){
  // Creative protects only the private test player. The actual vanilla block
  // activation/explosion remains active inside a complete obsidian blast cage.
  player.setGameType(GameType.CREATIVE);player.connection.setPlayerLocation(bed.getX()+.5,bed.getY(),bed.getZ()+2.5,player.rotationYaw,player.rotationPitch);
  report.addProperty("direct_try_sleep_result",player.trySleep(bed).toString());
  ((BlockBed)Blocks.BED).onBlockActivated(nether,bed,nether.getBlockState(bed),player,EnumHand.MAIN_HAND,EnumFacing.UP,.5F,.5F,.5F);
  stage=7;wait=40;
 }
 private static void bedAfter(){require(explosionStart!=null,"Actual bed activation emitted no native explosion event");require(!explosionStart.isCanceled(),"Native bed explosion was canceled; not accepted from provider flags alone");require(explosionDetonate!=null,"Actual native bed detonation callback missing");require(nether.getBlockState(bed).getBlock()!=Blocks.BED&&nether.getBlockState(bed.north()).getBlock()!=Blocks.BED,"Explosive Nether bed did not remove both halves");for(BlockPos p:explosionDetonate.getAffectedBlocks())require(Math.abs(p.getX()-bed.getX())<=6&&Math.abs(p.getY()-bed.getY())<=6&&Math.abs(p.getZ()-bed.getZ())<=6,"Bed explosion affected a block outside its sacrificial journal");report.addProperty("actual_bed_block_activation_and_contained_explosion",true);stage=8;restoreIndex=0;}
 private static void restoreBed(){
  player.connection.setPlayerLocation(d.entry[0]+.5,d.entry[1],d.entry[2]+.5,player.rotationYaw,player.rotationPitch);
  for(int n=0;n<128&&restoreIndex<cage.size();n++,restoreIndex++){BlockPos p=cage.get(restoreIndex);Block block=nether.getBlockState(p).getBlock();require(block==Blocks.AIR||block==Blocks.OBSIDIAN||block==Blocks.FIRE||block==Blocks.BED,"Sacrificial journal conflict retained for whole cold-copy rollback");nether.setBlockState(p,Block.getStateById(beforeCage.get(p.toLong())),2);require(Block.getStateId(nether.getBlockState(p))==beforeCage.get(p.toLong()),"Sacrificial journal native restore failed");}
  if(restoreIndex==cage.size()){for(Entity e:nether.getEntitiesWithinAABB(Entity.class,new AxisAlignedBB(bed.add(-9,-9,-9),bed.add(10,10,10))))if(e!=player)require(false,"Unexpected sacrificial actor retained; whole-copy rollback required");report.addProperty("sacrificial_all2197_cells_restored",true);saved.value.setBoolean("BedExplosionAccepted",true);saved.value.setString("BedJournalStatus","RESTORED");saved.markDirty();stage=9;wait=2;}
 }
 public static boolean tick(){
  if(done)return true;clock++;if(--wait>0)return false;
  try{require(clock<6000,"Bounded Nether QA6000tick budget exceeded");require(server.getPlayerList().getPlayerByUUID(player.getUniqueID())==player,"Registered Nether QA player identity changed");
   if(stage==0){player.setGameType(GameType.CREATIVE);transfer(-1,d.entry);report.addProperty("actual_overworld_to_nether_safe_transfer",true);for(String name:new String[]{"entry","boss","reward","exit"})sample(name,d.point(name));stage=1;index=0;return false;}
   if(stage==1){for(int n=0;n<128&&index<furnace.size();n++,index++){BlockPos p=furnace.get(index);load(nether,p,0);inspectLava(p,false);}if(index==furnace.size()){require(lava.size()>0,"Staged Fire furnaces contain no actual native lava");report.addProperty("native_initial_lava_sources",lava.size());stage=2;index=0;}return false;}
   if(stage==2){for(int n=0;n<24&&index<lava.size();n++,index++){BlockPos p=lava.get(index);IBlockState actual=nether.getBlockState(p);actual.getBlock().neighborChanged(actual,nether,p,Blocks.OBSIDIAN,p.up());nether.scheduleUpdate(p,nether.getBlockState(p).getBlock(),1);}if(index==lava.size()){stage=3;index=0;wait=100;}return false;}
   if(stage==3){for(int n=0;n<128&&index<furnace.size();n++,index++)inspectLava(furnace.get(index),true);if(index==furnace.size()){report.addProperty("actual_post_update_lava_containment",true);stage=4;index=0;}return false;}
   if(stage==4){if(secondBoot){require(saved.value.getBoolean("BedExplosionAccepted"),"First process did not persist actual Nether bed acceptance");for(BlockPos p:cage){load(nether,p,0);require(nether.isAirBlock(p),"Restored sacrificial pad did not survive process restart");}report.addProperty("bed_and_furnace_actual_process_restart",true);stage=9;}else bedVerify();return false;}
   if(stage==5){bedBuild();return false;}if(stage==6){activateBed();return false;}if(stage==7){bedAfter();return false;}if(stage==8){restoreBed();return false;}
   if(stage==9){if(d.samples!=null&&sampleIndex<d.samples.length){for(int n=0;n<4&&sampleIndex<d.samples.length;n++,sampleIndex++)sample(d.samples[sampleIndex].name,d.samples[sampleIndex].at);return false;}stage=10;return false;}
   if(stage==10){load(nether,new BlockPos(d.boss_anchor[0],d.boss_anchor[1],d.boss_anchor[2]),2);nativeBody=ForgeRegistries.ENTITIES.getValue(new ResourceLocation(d.boss)).newInstance(nether);nativeBody.setPosition(d.boss_anchor[0]+.5,d.boss_anchor[1],d.boss_anchor[2]+.5);if(nativeBody instanceof EntityLiving){((EntityLiving)nativeBody).onInitialSpawn(nether.getDifficultyForLocation(nativeBody.getPosition()),null);((EntityLiving)nativeBody).enablePersistence();}require(nether.spawnEntity(nativeBody),"Actual Madara body rejected native Nether spawn");bodyTicks=nativeBody.ticksExisted;stage=11;wait=24;return false;}
   if(stage==11){require(nativeBody.ticksExisted>bodyTicks,"Actual Nether Madara body did not update");report.addProperty("native_madara_spawn_and_body_ticks",nativeBody.ticksExisted-bodyTicks);report.addProperty("native_madara_class_sha256",ArenaTerminals.classHash(nativeBody));report.addProperty("native_madara_ai_disabled",nativeBody instanceof EntityLiving&&((EntityLiving)nativeBody).isAIDisabled());nativeBody.setDead();nativeBody=null;transfer(0,d.exit);report.addProperty("actual_safe_nether_to_overworld_exit",true);transfer(-1,d.entry);report.addProperty("actual_repeat_safe_nether_return",true);save();finish();return true;}
  }catch(Throwable failure){failures.add(failure.toString());report.addProperty("failed_stage",stage);report.addProperty("whole_cold_copy_rollback_required",stage>=5&&stage<=8);finish();return true;}
  return false;
 }
 private static void finish(){MinecraftForge.EVENT_BUS.unregister(observer);if(nativeBody!=null){nativeBody.setDead();nativeBody=null;}try{if(player.dimension!=oldDimension)transfer(oldDimension,d.exit);player.connection.setPlayerLocation(oldX,oldY,oldZ,player.rotationYaw,player.rotationPitch);player.setGameType(oldMode);}catch(Throwable failure){failures.add("Original synthetic player return: "+failure);}report.addProperty("passed",failures.size()==0);report.addProperty("native_ticks",clock);report.addProperty("actual_portal_link_or_8to1_routing_acceptance",false);done=true;}
 public static final class Observer {
  private boolean owned(ExplosionEvent e){Vec3d p=e.getExplosion().getPosition();return e.getWorld()==nether&&p.squareDistanceTo(new Vec3d(bed.getX()+.5,bed.getY()+.5,bed.getZ()-.5))<4;}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void start(ExplosionEvent.Start e){if(owned(e))explosionStart=e;}
  @SubscribeEvent(priority=EventPriority.LOWEST)public void detonate(ExplosionEvent.Detonate e){if(owned(e))explosionDetonate=e;}
 }
}
