package village.vale.arenas;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.material.Material;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Isolated official survival body, real camp aid and real submerged ticks.
 * Movement is explicit server-guided MoverType.SELF, not client swimming input.
 * No health/air assignment, immunity, blocks or liquid are used to pass this case. */
public final class WetNativeChecks {
 private static MinecraftServer server;private static WorldServer world;
 private static EntityPlayerMP player,previous,oldBody;private static GameProfile profile;
 private static ArenaDirector.Definition arena;private static ArenaDirector.Sample wet;
 private static EntityVillager aid;private static JsonObject report;private static JsonArray failures,observations,swimPath,exitPath;
 private static final List<Vec3d> swim=new ArrayList<>(),egress=new ArrayList<>();
 private static int stage,total,index,assertions,fmlTicks,submergedFml,submergedBody,submergedWorld,preReconnectTicks,postReconnectTicks;
 private static int lastBodyTicks,lastFml,minimumAir,initialDuration,reconnectDuration,reconnectAir;
 private static long lastWorld,swimStartedWorld,reloginAfter;private static float initialHealth,reconnectHealth;
 private static double reconnectX,reconnectY,reconnectZ;private static boolean done,swimming,reconnected,ownHaste;
 private static int[] recovery;private static NBTTagCompound reconnectPotion;
 private static final Observer observer=new Observer();
 private WetNativeChecks(){}
 private static final class Observer {
  @SubscribeEvent public void playerTick(TickEvent.PlayerTickEvent event){
   if(event.phase!=TickEvent.Phase.END||event.player!=player||server==null||server.getPlayerList().getPlayerByUUID(player.getUniqueID())!=player)return;
   fmlTicks++;
   if(swimming&&player.isEntityAlive()&&player.isInsideOfMaterial(Material.WATER))submergedFml++;
  }
 }
 private static void check(boolean accepted,String why){assertions++;if(!accepted)throw new IllegalStateException(why);}
 private static JsonArray point(double x,double y,double z){JsonArray a=new JsonArray();a.add(x);a.add(y);a.add(z);return a;}
 private static Vec3d center(int[] p){return new Vec3d(p[0]+.5,p[1],p[2]+.5);}
 private static BlockPos feet(Vec3d p){return new BlockPos(p.x,p.y+.01,p.z);}
 private static AxisAlignedBB body(Vec3d p){return new AxisAlignedBB(p.x-.3,p.y,p.z-.3,p.x+.3,p.y+1.8,p.z+.3);}
 private static NBTTagCompound effect(PotionEffect effect){return effect==null?null:effect.writeCustomPotionEffectToNBT(new NBTTagCompound());}
 private static NBTTagCompound afterMeasuredTicks(NBTTagCompound before,NBTTagCompound after,int nativeFmlUpdates,String boundary){
  check(before!=null&&after!=null&&before.hasKey("Duration",3)&&nativeFmlUpdates>=0&&nativeFmlUpdates<=4,"Invalid measured native potion boundary: "+boundary);
  NBTTagCompound expected=before.copy();check(expected.getInteger("Duration")>nativeFmlUpdates,"Genuine breathing expired during "+boundary);expected.setInteger("Duration",expected.getInteger("Duration")-nativeFmlUpdates);
  check(expected.equals(after),"Typed genuine breathing changed beyond exact measured FML duration ticks during "+boundary);return expected;
 }
 private static void warm(BlockPos p){
  for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
   int cx=(p.getX()>>4)+x,cz=(p.getZ()>>4)+z;
   check(world.getChunkProvider().isChunkGeneratedAt(cx,cz),"Wet fixture refused ungenerated chunk");
   if(world.getChunkProvider().getLoadedChunk(cx,cz)==null)world.getChunkProvider().loadChunk(cx,cz);
  }
 }
 private static boolean clear(Vec3d p){return world.getCollisionBoxes(player,body(p)).isEmpty();}
 private static boolean water(Vec3d p){
  AxisAlignedBB box=body(p);if(!clear(p))return false;
  for(BlockPos q:BlockPos.getAllInBox(new BlockPos(box.minX+.001,box.minY+.001,box.minZ+.001),new BlockPos(box.maxX-.001,box.maxY-.001,box.maxZ-.001)))if(world.getBlockState(q).getMaterial()!=Material.WATER)return false;
  return true;
 }
 private static int light(Vec3d p){
  BlockPos at=feet(p);NativeProbe.seedLights(world,arena,at);
  return world.getLightFor(EnumSkyBlock.BLOCK,at);
 }
 private static void dryGuide(int[] at){
  warm(new BlockPos(at[0],at[1],at[2]));check(ArenaDirector.safe(world,at),"Wet private dry guidance landing is unsafe");
  player.connection.setPlayerLocation(at[0]+.5,at[1],at[2]+.5,player.rotationYaw,player.rotationPitch);world.getPlayerChunkMap().updateMovingPlayer(player);
 }
 private static EntityPlayerMP login(boolean first){
  check(server.getPlayerList().getPlayerByUUID(profile.getId())==null,"Wet UUID already online");
  EntityPlayerMP p=new EntityPlayerMP(server,world,profile,new PlayerInteractionManager(world));p.connection=new NativeProbe.Quiet(server,p);player=p;
  server.getPlayerList().initializeConnectionToPlayer(p.connection.netManager,p,p.connection);
  check(server.getPlayerList().getPlayerByUUID(profile.getId())==p&&world.playerEntities.contains(p),"Official wet player login failed");
  if(first)p.setGameType(GameType.SURVIVAL);
  check(p.interactionManager.getGameType()==GameType.SURVIVAL&&!p.capabilities.disableDamage&&!p.capabilities.isFlying&&!p.isSpectator(),"Wet player must be ordinary vulnerable survival");
  return p;
 }
 public static void begin(MinecraftServer s,List<ArenaDirector.Definition> definitions,EntityPlayerMP shared){
  if(!Boolean.getBoolean("hbv.arenas.nativeProbe")||!Files.isRegularFile(Paths.get(".hbv-arenas-native-probe"))||!s.getFolderName().equals("arena-preview")||!s.isCallingFromMinecraftThread()||s.getPlayerList().getPlayerByUUID(shared.getUniqueID())!=shared)throw new IllegalStateException("Private wet guard rejected");
  server=s;world=s.getWorld(0);previous=shared;arena=null;wet=null;player=oldBody=null;done=swimming=reconnected=ownHaste=false;
  stage=total=index=assertions=fmlTicks=submergedFml=submergedBody=submergedWorld=preReconnectTicks=postReconnectTicks=0;minimumAir=Integer.MAX_VALUE;swim.clear();egress.clear();
  report=new JsonObject();failures=new JsonArray();observations=new JsonArray();swimPath=new JsonArray();exitPath=new JsonArray();report.add("failures",failures);report.add("observations",observations);report.add("actual_native_swim_centerline",swimPath);report.add("guided_clear_egress",exitPath);
  report.addProperty("scope","ACTUAL_OFFICIAL_SURVIVAL_AID_BREATHING_UNDERWATER_TICKS_AND_SAME_PROCESS_OFFICIAL_RECONNECT; server-guided MoverType.SELF, not client input or process restart");
  report.addProperty("health_or_air_setters",false);report.addProperty("immunity_or_creative",false);report.addProperty("blocks_or_liquids_written",false);report.addProperty("unassisted_client_swimming_accepted",false);
  for(ArenaDirector.Definition d:definitions)if(d.water&&d.samples!=null)for(ArenaDirector.Sample sample:d.samples)if("swim".equals(sample.traversal)&&sample.wet_bounds!=null&&sample.wet_bounds.length==6&&sample.wet_path!=null&&sample.wet_path.length>1){arena=d;wet=sample;break;}
  check(arena!=null&&wet!=null,"Typed authored wet route with complete path is required");
  JsonObject run=ArenaDirector.run(arena);check(run==null||ArenaDirector.text(run,"state").equals("AVAILABLE"),"Wet fixture requires empty Mist; failed active run preserved");
  String token="HBV:WetNative:v1:"+world.getSeed()+":"+arena.template_sha256+":"+System.getProperty("hbv.arenas.probeIteration","1");profile=new GameProfile(UUID.nameUUIDFromBytes(token.getBytes(StandardCharsets.UTF_8)),"HbvWetProbe");
  player=login(true);dryGuide(arena.entry);report.addProperty("profile_uuid",profile.getId().toString());report.addProperty("template_sha256",arena.template_sha256);report.addProperty("authored_wet_points",wet.wet_path.length);
  MinecraftForge.EVENT_BUS.register(observer);
 }
 public static EntityPlayerMP currentPlayer(){return previous;}
 public static JsonObject result(){return report;}
 private static void treatment(){
  check(arena.services!=null&&arena.services.length==3,"Mist breathing aid role absent");for(int[] at:arena.services)warm(new BlockPos(at[0],at[1],at[2]));
  check(ArenaServices.ensure(arena),"Actual three service identities unavailable");JsonObject record=ArenaServices.records().getAsJsonObject(arena.id+"/aid");
  check(record!=null,"Saved aid identity missing");Entity entity=world.getEntityFromUuid(UUID.fromString(ArenaDirector.text(record,"uuid")));check(entity instanceof EntityVillager,"Exact saved native aid actor absent");aid=(EntityVillager)entity;
  boolean placed=false;for(int[] off:new int[][]{{2,0},{-2,0},{0,2},{0,-2}}){int[] at={(int)Math.floor(aid.posX)+off[0],(int)Math.floor(aid.posY),(int)Math.floor(aid.posZ)+off[1]};if(ArenaDirector.safe(world,at)){dryGuide(at);placed=true;break;}}
  check(placed&&player.getDistanceSq(aid)<=36,"No actual safe breathing aid interaction approach");check(player.getActivePotionEffect(MobEffects.WATER_BREATHING)==null,"Fresh dedicated profile already has breathing; existing effect preserved");
  // Explicit QA potion is solely an existing-effect preservation control. It
  // is removed before the genuine service-granted underwater case begins.
  player.addPotionEffect(new PotionEffect(MobEffects.WATER_BREATHING,3600,2,true,false));
  check(player.getActivePotionEffect(MobEffects.HASTE)==null,"Fresh wet profile has unrelated fixture effect");player.addPotionEffect(new PotionEffect(MobEffects.HASTE,12000,1,true,false));ownHaste=true;
  NBTTagCompound stronger=effect(player.getActivePotionEffect(MobEffects.WATER_BREATHING)),unrelated=effect(player.getActivePotionEffect(MobEffects.HASTE));
  check(ForgeHooks.onInteractEntity(player,aid,EnumHand.MAIN_HAND)!=null,"Actual Forge aid preservation interaction not handled");
  check(stronger.equals(effect(player.getActivePotionEffect(MobEffects.WATER_BREATHING))),"Aid replaced or shortened stronger existing breathing");check(unrelated.equals(effect(player.getActivePotionEffect(MobEffects.HASTE))),"Aid altered unrelated existing status");
  report.addProperty("LegitAppliedFixturePotion_existing_effect_control_only",true);report.addProperty("actual_aid_preserves_existing_breathing_exactly",true);report.addProperty("actual_aid_preserves_unrelated_status_exactly",true);
  player.removePotionEffect(MobEffects.WATER_BREATHING);stage=1;
 }
 private static void obtainBreathing(){
  long now=world.getTotalWorldTime(),ready=RewardOutbox.receipts(player).getLong("campAid/"+arena.id);if(now<ready)return;
  check(player.getActivePotionEffect(MobEffects.WATER_BREATHING)==null,"Preservation fixture effect leaked into swim case");NBTTagCompound unrelated=effect(player.getActivePotionEffect(MobEffects.HASTE));
  float before=player.getHealth();check(ForgeHooks.onInteractEntity(player,aid,EnumHand.MAIN_HAND)!=null,"Actual Forge breathing aid interaction not handled");
  PotionEffect breathing=player.getActivePotionEffect(MobEffects.WATER_BREATHING);check(breathing!=null&&breathing.getAmplifier()==0&&breathing.getDuration()==2400,"Real out-of-fight aid did not grant exact2400tick breathing");
  check(unrelated.equals(effect(player.getActivePotionEffect(MobEffects.HASTE))),"Actual aid grant changed unrelated status");check(RewardOutbox.receipts(player).getLong("campAid/"+arena.id)==now+300,"Breathing treatment did not persist real300tick cooldown");
  initialHealth=player.getHealth();initialDuration=breathing.getDuration();report.addProperty("health_before_genuine_aid",before);report.addProperty("health_after_genuine_aid",initialHealth);report.addProperty("genuine_service_breathing_duration",initialDuration);report.addProperty("no_cooldown_receipt_override",true);
  planSwim();Vec3d start=swim.get(0);warm(feet(start));check(water(start)&&light(start)>=8,"Authored swim entry lacks real water, clearance or light");player.connection.setPlayerLocation(start.x,start.y,start.z,player.rotationYaw,player.rotationPitch);world.getPlayerChunkMap().updateMovingPlayer(player);
  report.addProperty("initial_underwater_position_is_explicit_QA_guidance",true);index=1;swimming=true;swimStartedWorld=lastWorld=world.getTotalWorldTime();lastBodyTicks=player.ticksExisted;lastFml=fmlTicks;stage=2;observe("GENUINE_AID_UNDERWATER_BEGIN");
 }
 private static void planSwim(){
  int[] first=wet.wet_path[0];check(first!=null&&first.length==3,"Invalid complete wet path start");swim.add(center(first));
  for(int[] target:wet.wet_path){check(target!=null&&target.length==3,"Invalid authored wet coordinate");check(target[0]>=wet.wet_bounds[0]&&target[0]<=wet.wet_bounds[3]&&target[2]>=wet.wet_bounds[2]&&target[2]<=wet.wet_bounds[5],"Authored wet path escapes declared room");warm(new BlockPos(target[0],target[1],target[2]));check(water(center(target))&&light(center(target))>=8,"Authored wet point has native collision, nonwater or darkness");appendWetConnection(target);}
  for(Vec3d p:swim)swimPath.add(point(p.x,p.y,p.z));check(swim.size()>=100,"Full authored submerged passage is unexpectedly short");report.addProperty("native_connected_swim_points",swim.size());
 }
 private static void appendWetConnection(int[] target){
  Vec3d last=swim.get(swim.size()-1);BlockPos start=feet(last),goal=new BlockPos(target[0],target[1],target[2]);check(start.getY()==goal.getY(),"Wet centerline changed vertical plane");if(start.equals(goal))return;
  int minX=Math.min(start.getX(),goal.getX())-4,maxX=Math.max(start.getX(),goal.getX())+4,minZ=Math.min(start.getZ(),goal.getZ())-4,maxZ=Math.max(start.getZ(),goal.getZ())+4;
  ArrayDeque<BlockPos> queue=new ArrayDeque<>();Map<BlockPos,BlockPos> parent=new HashMap<>();queue.add(start);parent.put(start,null);
  while(!queue.isEmpty()&&!parent.containsKey(goal)){
   BlockPos p=queue.remove();for(int[] off:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){BlockPos q=p.add(off[0],0,off[1]);if(q.getX()<minX||q.getX()>maxX||q.getZ()<minZ||q.getZ()>maxZ||parent.containsKey(q))continue;Vec3d c=new Vec3d(q.getX()+.5,q.getY(),q.getZ()+.5);if(water(c)&&light(c)>=8){parent.put(q,p);queue.add(q);}}
  }
  check(parent.containsKey(goal),"Actual water/AABB path cannot connect consecutive authored swim points");List<BlockPos> reverse=new ArrayList<>();for(BlockPos p=goal;!p.equals(start);p=parent.get(p))reverse.add(p);Collections.reverse(reverse);for(BlockPos p:reverse)swim.add(new Vec3d(p.getX()+.5,p.getY(),p.getZ()+.5));
 }
 private static boolean advance(List<Vec3d> path){
  if(index>=path.size())return true;Vec3d goal=path.get(index),here=new Vec3d(player.posX,player.posY,player.posZ),delta=goal.subtract(here);double distance=delta.length();
  if(distance<.035){index++;return index>=path.size();}double scale=Math.min(.25,distance)/distance;player.move(MoverType.SELF,delta.x*scale,delta.y*scale,delta.z*scale);world.getPlayerChunkMap().updateMovingPlayer(player);return false;
 }
 private static void alive(){
  check(server.getPlayerList().getPlayerByUUID(profile.getId())==player&&player.world==world&&world.playerEntities.contains(player)&&player.isEntityAlive(),"Official wet body disappeared, changed world or died");check(player.interactionManager.getGameType()==GameType.SURVIVAL&&!player.capabilities.disableDamage&&!player.capabilities.isFlying,"Wet survival body gained immunity or flight");check(player.getHealth()>=initialHealth-.001F,"Actual submerged survival body lost health");
  PotionEffect breathing=player.getActivePotionEffect(MobEffects.WATER_BREATHING);check(breathing!=null&&breathing.getAmplifier()==0&&breathing.getDuration()>0,"Genuine service breathing expired or changed");
 }
 private static void submerged(){
  alive();Vec3d here=new Vec3d(player.posX,player.posY,player.posZ);warm(feet(here));check(water(here)&&player.isInsideOfMaterial(Material.WATER),"Native survival body escaped actual full-body water");check(light(here)>=8,"Native swim route block light below8");minimumAir=Math.min(minimumAir,player.getAir());check(player.getAir()>=0,"Native air fell below zero despite genuine service breathing");
  int body=player.ticksExisted-lastBodyTicks,fml=fmlTicks-lastFml;long elapsed=world.getTotalWorldTime()-lastWorld;
  if(body>0&&fml>0&&elapsed>0){submergedBody+=body;submergedWorld+=(int)elapsed;if(reconnected)postReconnectTicks+=body;else preReconnectTicks+=body;}
  lastBodyTicks=player.ticksExisted;lastFml=fmlTicks;lastWorld=world.getTotalWorldTime();if(total%20==0)observe("ACTUAL_SURVIVAL_SUBMERGED");
 }
 private static void observe(String phase){
  JsonObject r=new JsonObject();r.addProperty("phase",phase);r.addProperty("world_tick",world.getTotalWorldTime());r.addProperty("body_ticks",player.ticksExisted);r.addProperty("real_FML_player_END_ticks",fmlTicks);r.addProperty("submerged_FML_ticks",submergedFml);r.addProperty("air",player.getAir());r.addProperty("health",player.getHealth());PotionEffect breathing=player.getActivePotionEffect(MobEffects.WATER_BREATHING);r.addProperty("breathing_duration",breathing==null?-1:breathing.getDuration());r.add("position",point(player.posX,player.posY,player.posZ));r.addProperty("actual_eye_in_water",player.isInsideOfMaterial(Material.WATER));r.addProperty("native_AABB_clear",clear(new Vec3d(player.posX,player.posY,player.posZ)));r.addProperty("native_foot_block_light",world.getLightFor(EnumSkyBlock.BLOCK,new BlockPos(player.posX,player.posY+.01,player.posZ)));r.addProperty("native_eye_block_light_observed_only",world.getLightFor(EnumSkyBlock.BLOCK,new BlockPos(player.posX,player.posY+1.62,player.posZ)));observations.add(r);
 }
 private static void logoutUnderwater()throws IOException{
  submerged();reconnectX=player.posX;reconnectY=player.posY;reconnectZ=player.posZ;reconnectHealth=player.getHealth();reconnectAir=player.getAir();reconnectPotion=effect(player.getActivePotionEffect(MobEffects.WATER_BREATHING));reconnectDuration=player.getActivePotionEffect(MobEffects.WATER_BREATHING).getDuration();observe("ACTUAL_OFFICIAL_LOGOUT_UNDERWATER");
  int beforeBody=player.ticksExisted,beforeFml=fmlTicks;report.addProperty("breathing_expected_before_native_logout_snbt",reconnectPotion.toString());
  oldBody=player;server.getPlayerList().playerLoggedOut(oldBody);check(server.getPlayerList().getPlayerByUUID(profile.getId())==null&&!world.playerEntities.contains(oldBody),"Actual wet logout retained official body");
  int logoutFmlUpdates=fmlTicks-beforeFml;NBTTagCompound actualBodyAfterLogout=effect(oldBody.getActivePotionEffect(MobEffects.WATER_BREATHING));report.addProperty("native_logout_body_updates",oldBody.ticksExisted-beforeBody);report.addProperty("native_logout_FML_player_END_updates",logoutFmlUpdates);report.addProperty("breathing_actual_body_after_native_logout_snbt",String.valueOf(actualBodyAfterLogout));
  NBTTagCompound expectedSaved=afterMeasuredTicks(reconnectPotion,actualBodyAfterLogout,logoutFmlUpdates,"official logout");
  File file=server.getFile(server.getFolderName()+"/playerdata/"+profile.getId()+".dat");NBTTagCompound saved;try(InputStream in=new FileInputStream(file)){saved=CompressedStreamTools.readCompressed(in);}
  check(saved.getShort("Air")==reconnectAir&&Math.abs(saved.getFloat("Health")-reconnectHealth)<.001F,"Native underwater player save lost air or health");NBTTagList pos=saved.getTagList("Pos",6);check(Math.abs(pos.getDoubleAt(0)-reconnectX)<.001&&Math.abs(pos.getDoubleAt(1)-reconnectY)<.001&&Math.abs(pos.getDoubleAt(2)-reconnectZ)<.001,"Native underwater player save changed position");
  boolean persisted=false;NBTTagList effects=saved.getTagList("ActiveEffects",10);JsonArray candidates=new JsonArray();report.add("breathing_saved_candidates_diagnostic_only",candidates);
  for(int i=0;i<effects.tagCount();i++){
   NBTTagCompound actual=effects.getCompoundTagAt(i);if(expectedSaved.equals(actual))persisted=true;
   if(actual.getByte("Id")==reconnectPotion.getByte("Id")){
    JsonObject candidate=new JsonObject();candidate.addProperty("actual_snbt",actual.toString());JsonArray differences=new JsonArray();candidate.add("typed_field_differences",differences);Set<String> keys=new TreeSet<>(reconnectPotion.getKeySet());keys.addAll(actual.getKeySet());
    for(String key:keys){NBTBase before=reconnectPotion.getTag(key),after=actual.getTag(key);if(Objects.equals(before,after))continue;JsonObject diff=new JsonObject();diff.addProperty("field",key);diff.addProperty("expected_type",before==null?-1:before.getId());diff.addProperty("actual_type",after==null?-1:after.getId());diff.addProperty("expected_snbt",String.valueOf(before));diff.addProperty("actual_snbt",String.valueOf(after));differences.add(diff);}candidates.add(candidate);
   }
  }
  check(persisted,"Native underwater player save lacks exact genuine breathing effect after measured logout ticks");reconnectPotion=expectedSaved;reconnectDuration=expectedSaved.getInteger("Duration");report.addProperty("duration_boundary_rule","Subtract exactly independently observed real FML player END updates; every other typed/unknown Potion NBT tag remains byte-equal; no tolerance");report.addProperty("actual_underwater_native_player_save",true);swimming=false;reloginAfter=world.getTotalWorldTime()+2;stage=3;
 }
 private static void relogin(){
  if(world.getTotalWorldTime()<reloginAfter)return;int beforeFml=fmlTicks;player=login(false);check(player!=oldBody,"Official reconnect reused old wet body");check(Math.abs(player.posX-reconnectX)<.001&&Math.abs(player.posY-reconnectY)<.001&&Math.abs(player.posZ-reconnectZ)<.001,"Official reconnect did not reload underwater position");
  NBTTagCompound actual=effect(player.getActivePotionEffect(MobEffects.WATER_BREATHING));report.addProperty("native_relogin_FML_player_END_updates",fmlTicks-beforeFml);report.addProperty("breathing_expected_before_native_relogin_snbt",reconnectPotion.toString());report.addProperty("breathing_actual_after_native_relogin_snbt",String.valueOf(actual));
  check(player.getAir()==reconnectAir&&Math.abs(player.getHealth()-reconnectHealth)<.001F,"Official underwater reconnect changed air/health");afterMeasuredTicks(reconnectPotion,actual,fmlTicks-beforeFml,"official relogin");
  check(water(new Vec3d(player.posX,player.posY,player.posZ)),"Official reconnect spawned outside full-body water");report.addProperty("actual_official_new_body_reconnect_underwater",true);report.addProperty("same_process_logout_relogin_not_process_restart",true);report.addProperty("reloaded_genuine_breathing_duration",reconnectDuration);reconnected=true;swimming=true;lastBodyTicks=player.ticksExisted;lastFml=fmlTicks;lastWorld=world.getTotalWorldTime();stage=2;observe("ACTUAL_NEW_BODY_UNDERWATER_RELOGIN");
 }
 private static boolean segment(List<Vec3d> path,Vec3d from,Vec3d to){
  Vec3d delta=to.subtract(from);int count=(int)Math.ceil(delta.length()*4);for(int i=1;i<=count;i++){Vec3d p=from.add(delta.scale((double)i/count));if(!clear(p))return false;}path.add(to);return true;
 }
 private static void planEgress(){
  Vec3d from=new Vec3d(player.posX,player.posY,player.posZ);List<ArenaDirector.Sample> targets=new ArrayList<>();
  for(ArenaDirector.Sample candidate:arena.samples)if(candidate.name!=null&&candidate.name.contains("underwater_dry_recovery")&&!"swim".equals(candidate.traversal)&&ArenaDirector.safe(world,candidate.at))targets.add(candidate);
  Collections.sort(targets,(a,b)->Double.compare(center(a.at).squareDistanceTo(from),center(b.at).squareDistanceTo(from)));check(!targets.isEmpty(),"No safe actual authored dry recovery sample");
  List<Vec3d> starts=new ArrayList<>(swim);Collections.reverse(starts);
  for(ArenaDirector.Sample target:targets){Vec3d dry=center(target.at);if(dry.squareDistanceTo(from)>4096)continue;warm(feet(dry));
   for(Vec3d shaft:starts){if(shaft.squareDistanceTo(from)>256)continue;List<Vec3d> approach=new ArrayList<>();approach.add(from);if(!segment(approach,from,shaft))continue;Vec3d top=new Vec3d(shaft.x,dry.y,shaft.z);if(!segment(approach,shaft,top))continue;
    for(boolean xFirst:new boolean[]{true,false}){List<Vec3d> trial=new ArrayList<>(approach);Vec3d bend=xFirst?new Vec3d(dry.x,dry.y,top.z):new Vec3d(top.x,dry.y,dry.z);if(segment(trial,top,bend)&&segment(trial,bend,dry)){egress.addAll(trial);recovery=target.at.clone();for(Vec3d p:egress)exitPath.add(point(p.x,p.y,p.z));report.addProperty("dry_recovery_sample",target.name);report.addProperty("egress_scope","GUIDED_NATIVE_MOVER_CLEAR_SHAFT_AND_AIR_PATH_TO_AUTHORED_DRY_RECOVERY; not unassisted client swim, jump or walking");index=1;return;}}
   }
  }
  throw new IllegalStateException("No actual clear authored wet shaft to dry recovery; teleport fallback refused");
 }
 private static void finish(boolean accepted){
  swimming=false;MinecraftForge.EVENT_BUS.unregister(observer);report.addProperty("actual_submerged_body_ticks",submergedBody);report.addProperty("actual_submerged_FML_player_ticks",submergedFml);report.addProperty("actual_submerged_world_ticks",submergedWorld);report.addProperty("before_reconnect_submerged_body_ticks",preReconnectTicks);report.addProperty("after_reconnect_submerged_body_ticks",postReconnectTicks);report.addProperty("minimum_observed_native_air",minimumAir==Integer.MAX_VALUE?-1:minimumAir);report.addProperty("assertions",assertions);
  try{if(player!=null&&server.getPlayerList().getPlayerByUUID(player.getUniqueID())==player){observe(accepted?"SAFE_DRY_FINISH":"FAILED_PRIVATE_BODY_PRESERVED");if(ownHaste)player.removePotionEffect(MobEffects.HASTE);server.getPlayerList().playerLoggedOut(player);}}catch(Throwable cleanup){failures.add("Own official wet profile logout: "+cleanup);accepted=false;}
  report.addProperty("passed",accepted&&failures.size()==0);done=true;
 }
 public static boolean tick(){
  if(done)return true;try{check(++total<=1800,"Bounded wet native1800tick budget exceeded");
   if(stage==0){treatment();return false;}if(stage==1){obtainBreathing();return false;}if(stage==3){relogin();return false;}
   if(stage==2){submerged();if(!reconnected&&preReconnectTicks>=180){logoutUnderwater();return false;}boolean complete=advance(swim);if(complete&&submergedBody>320&&submergedFml>320&&submergedWorld>320&&reconnected&&postReconnectTicks>=120){report.addProperty("full_authored_wet_route_traversed",true);check(player.getActivePotionEffect(MobEffects.WATER_BREATHING).getDuration()<initialDuration-320,"Breathing did not decay across actual native ticks");swimming=false;planEgress();stage=4;observe("REAL_SUBMERGED_TICKS_COMPLETE_BEGIN_GUIDED_EGRESS");}return false;}
   if(stage==4){alive();warm(player.getPosition());check(clear(new Vec3d(player.posX,player.posY,player.posZ)),"Native guided egress body hit structure");if(advance(egress)){stage=5;lastBodyTicks=player.ticksExisted;lastWorld=world.getTotalWorldTime();}return false;}
   if(stage==5){alive();if(player.ticksExisted-lastBodyTicks<2||world.getTotalWorldTime()-lastWorld<2)return false;check(recovery!=null&&ArenaDirector.safe(world,recovery)&&clear(new Vec3d(player.posX,player.posY,player.posZ))&&!world.containsAnyLiquid(player.getEntityBoundingBox())&&!player.isInsideOfMaterial(Material.WATER),"Actual dry recovery lost support/clearance or remained wet");check(player.getDistanceSq(recovery[0]+.5,recovery[1],recovery[2]+.5)<.02,"Guided native body did not reach authored dry landing");report.addProperty("actual_guided_dry_recovery_after_native_player_ticks",true);report.addProperty("health_at_safe_exit",player.getHealth());report.addProperty("air_at_safe_exit",player.getAir());finish(true);return true;}
  }catch(Throwable error){failures.add(error.toString());finish(false);return true;}return false;
 }
}
