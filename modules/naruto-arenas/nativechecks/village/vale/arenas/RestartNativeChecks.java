package village.vale.arenas;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.ForgeHooks;
import village.vale.RewardLedger;

/** Seven genuine save/stop/new-process boots. This guarded private diagnostic
 * progresses real physical seals; it never assigns run phase, contribution,
 * boss HP, reward entitlement or native ownership. Guidance is not client input. */
public final class RestartNativeChecks {
 private static MinecraftServer server;
 private static List<ArenaDirector.Definition> definitions;
 private static final Map<String,EntityPlayerMP> players=new LinkedHashMap<>();
 private static JsonObject previous,report;private static JsonArray rows;
 private static Path checkpoint;private static int boot,index,stage,wait,clock;
 private static boolean done;
 private static int engagementStart;
 private static final Map<String,Boolean> trust=new HashMap<>();
 private static final Set<String> startupResetSeen=new HashSet<>();
 private RestartNativeChecks(){}
 private static void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
 /** Called at real FMLServerStarted, before normal ticks or the QA warmup. */
 public static void observeStartup(MinecraftServer s,List<ArenaDirector.Definition> all){
  check(Boolean.getBoolean("hbv.arenas.nativeProbe")&&Boolean.getBoolean("hbv.arenas.restartOnly")&&s.getFolderName().equals("arena-preview"),"Restart startup guard rejected");startupResetSeen.clear();
  for(ArenaDirector.Definition d:all){JsonObject r=ArenaDirector.run(d);if(r!=null&&ArenaDirector.text(r,"state").equals("SEALING_OBSERVE")){check(ArenaDirector.number(r,"seal_observation_ticks")==0,"Actual startup did not reset loaded sealing observation");startupResetSeen.add(d.id);}}
 }
 public static void begin(MinecraftServer s,List<ArenaDirector.Definition> all)throws Exception{
  check(Boolean.getBoolean("hbv.arenas.nativeProbe")&&Boolean.getBoolean("hbv.arenas.restartOnly")&&Files.isRegularFile(Paths.get(".hbv-arenas-native-probe"))&&s.getFolderName().equals("arena-preview")&&s.getPlayerList().getCurrentPlayerCount()==0,"Private restart guard rejected");
  server=s;definitions=new ArrayList<>(all);check(definitions.size()==5,"Five definitions required");players.clear();trust.clear();
  checkpoint=Paths.get("PRIVATE-RESTART-STATE.json");previous=Files.exists(checkpoint)?new JsonParser().parse(new String(Files.readAllBytes(checkpoint),StandardCharsets.UTF_8)).getAsJsonObject():new JsonObject();
  boot=previous.has("next_boot")?previous.get("next_boot").getAsInt():0;check(boot>=0&&boot<=6,"Seven-boot sequence exhausted");
  report=new JsonObject();rows=new JsonArray();report.add("arenas",rows);report.addProperty("boot",boot);report.addProperty("scope","REAL_SEVEN_PROCESS_PHASE_AND_PENDING_REWARD_RESTART; official synthetic bodies and guided physical seals, no client or native-lethal claim");report.addProperty("all_seven_boots_complete",false);
  for(ArenaDirector.Definition d:definitions){trust.put(d.id,d.terminal_verified);d.terminal_verified=true;if(boot>0){JsonObject saved=prior(d);JsonObject current=ArenaDirector.run(d);check(current!=null,"Actual saved run missing: "+d.id);
   for(String key:new String[]{"run","boss_uuid","template","state"})check(ArenaDirector.text(saved,key).equals(ArenaDirector.text(current,key)),"Saved identity/state drift: "+d.id+"/"+key);
   check(saved.getAsJsonArray("cells").equals(current.getAsJsonArray("cells")),"Saved owned-cell journal changed at load: "+d.id);
   check(saved.getAsJsonObject("members").equals(current.getAsJsonObject("members")),"Saved party/outbox changed at load: "+d.id);
   if(ArenaDirector.text(current,"state").equals("SEALING_OBSERVE"))check(startupResetSeen.contains(d.id)&&ArenaDirector.number(current,"seal_observation_ticks")<=ArenaDirector.ticks,"Loaded observation did not restart at zero before real elapsed ticks");
  }}
  index=stage=wait=clock=0;done=false;
 }
 private static JsonObject prior(ArenaDirector.Definition d){return previous.getAsJsonObject("runs").getAsJsonObject(d.id);}
 public static JsonObject result(){return report;}
 private static UUID identity(ArenaDirector.Definition d){return UUID.nameUUIDFromBytes(("HBV:ActualArenaRestart:v1:"+d.id).getBytes(StandardCharsets.UTF_8));}
 private static EntityPlayerMP login(ArenaDirector.Definition d)throws Exception{
  EntityPlayerMP existing=players.get(d.id);if(existing!=null)return existing;
  WorldServer w=server.getWorld(0);EntityPlayerMP p=new EntityPlayerMP(server,w,new GameProfile(identity(d),"ArenaRestart"+definitions.indexOf(d)),new PlayerInteractionManager(w));p.connection=new NativeProbe.Quiet(server,p);server.getPlayerList().initializeConnectionToPlayer(p.connection.netManager,p,p.connection);
  check(server.getPlayerList().getPlayerByUUID(p.getUniqueID())==p&&w.playerEntities.contains(p),"Official restart login failed");p.setGameType(GameType.SURVIVAL);players.put(d.id,p);return p;
 }
 private static void guide(EntityPlayerMP p,int[] at){ArenaDirector.move(p,at);((WorldServer)p.world).getPlayerChunkMap().updateMovingPlayer(p);}
 private static void click(EntityPlayerMP p,ArenaDirector.Definition d,int node){int[] at=d.nodes[node];boolean placed=false;for(int[] delta:new int[][]{{0,3},{0,-3},{3,0},{-3,0}}){int[] landing={at[0]+delta[0],d.floor,at[2]+delta[1]};ArenaDirector.prepareLanding(server.getWorld(0),landing);if(ArenaDirector.safe(server.getWorld(0),landing)){guide(p,landing);placed=true;break;}}
  check(placed,"No physical restart seal approach");ForgeHooks.onRightClickBlock(p,EnumHand.MAIN_HAND,new BlockPos(at[0],at[1],at[2]),EnumFacing.UP,new Vec3d(at[0]+.5,at[1]+.5,at[2]+.5));
 }
 private static void equip(EntityPlayerMP p,ArenaDirector.Definition d){String[] keys=CoreRewardOutbox.bundle(d.id);RewardLedger ledger=RewardLedger.get(p);for(String key:keys)ledger.issue(p,key,1,"private-restart-preparation/"+p.getUniqueID()+"/"+key);
  EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};for(int n=0;n<4;n++)for(int i=0;i<p.inventory.mainInventory.size();i++){ItemStack stack=p.inventory.mainInventory.get(i);if(!stack.isEmpty()&&stack.getItem().getRegistryName()!=null&&stack.getItem().getRegistryName().getPath().equals(keys[n])){check(ledger.valid(p,stack),"Restart gear serial not owned");p.setItemStackToSlot(slots[n],stack);p.inventory.mainInventory.set(i,ItemStack.EMPTY);break;}}
  p.setItemStackToSlot(EntityEquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
 }
 private static Entity boss(ArenaDirector.Definition d){JsonObject r=ArenaDirector.run(d);Entity b=server.getWorld(0).getEntityFromUuid(UUID.fromString(ArenaDirector.text(r,"boss_uuid")));check(b instanceof EntityLivingBase&&b.isEntityAlive(),"Authentic updating restart boss absent");return b;}
 private static void engage(EntityPlayerMP p,ArenaDirector.Definition d){Entity b=boss(d);engagementStart=b.ticksExisted;for(int[] delta:new int[][]{{18,0},{-18,0},{0,18},{0,-18}}){int[] at={(int)Math.floor(b.posX)+delta[0],d.floor,(int)Math.floor(b.posZ)+delta[1]};ArenaDirector.prepareLanding(server.getWorld(0),at);if(d.contains(new BlockPos(at[0],at[1],at[2]))&&ArenaDirector.safe(server.getWorld(0),at)){guide(p,at);return;}}throw new IllegalStateException("No supported actual boss-pressure approach");}
 private static void defend(EntityPlayerMP p,ArenaDirector.Definition d){check(p.isEntityAlive(),"Restart participant died under actual boss pressure");Entity b=boss(d);if(p.getDistanceSq(b)>30*30)engage(p,d);p.rotationYaw=(float)Math.toDegrees(Math.atan2(-(b.posX-p.posX),b.posZ-p.posZ));p.rotationPitch=0;if(!p.isHandActive())p.setActiveHand(EnumHand.OFF_HAND);if(ArenaDirector.ticks%24==0)village.vale.ArenaDodgeNativeAccess.perform(p,village.vale.DodgeMath.LEFT);}
 /** At most two existing chunks loaded per driver tick. No generation/force ticket. */
 private static boolean warmSaved(ArenaDirector.Definition d){WorldServer w=server.getWorld(0);LinkedHashSet<Long> chunks=new LinkedHashSet<>();int cx=d.boss_anchor[0]>>4,cz=d.boss_anchor[2]>>4;for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)chunks.add((((long)(cx+x))<<32)^((cz+z)&0xffffffffL));
  JsonObject r=ArenaDirector.run(d);if(r!=null)for(JsonElement item:r.getAsJsonArray("cells")){JsonObject c=item.getAsJsonObject();chunks.add((((long)(ArenaDirector.number(c,"x")>>4))<<32)^((ArenaDirector.number(c,"z")>>4)&0xffffffffL));}
  int loaded=0;boolean ready=true;for(long key:chunks){int x=(int)(key>>32),z=(int)key;if(w.getChunkProvider().getLoadedChunk(x,z)!=null)continue;ready=false;if(loaded>=2)continue;check(w.getChunkProvider().isChunkGeneratedAt(x,z),"Restart chunk was not staged");w.getChunkProvider().loadChunk(x,z);loaded++;}return ready;
 }
 private static void verifyLoaded(ArenaDirector.Definition d,JsonObject out){WorldServer w=server.getWorld(0);JsonObject run=ArenaDirector.run(d);check(run!=null,"Saved run missing");
  if(!ArenaDirector.text(run,"state").equals("AVAILABLE")){JsonObject saved=prior(d);for(JsonElement item:saved.getAsJsonArray("cells")){JsonObject c=item.getAsJsonObject();BlockPos pos=new BlockPos(ArenaDirector.number(c,"x"),ArenaDirector.number(c,"y"),ArenaDirector.number(c,"z"));int actual=Block.getStateId(w.getBlockState(pos));check(actual==ArenaDirector.number(c,"after")||actual==ArenaDirector.number(c,"before")||WaterPrison.waterState(c,actual),"Persisted native cell no longer equals journal before/after");}
   UUID id=UUID.fromString(ArenaDirector.text(run,"boss_uuid"));Entity body=w.getEntityFromUuid(id);if(boot<=3){check(body instanceof EntityLivingBase&&body.isEntityAlive(),"Saved active native body absent/dead");check(ArenaDirector.text(run,"run").equals(body.getEntityData().getString("HbvArenaRun"))&&d.id.equals(body.getEntityData().getString("HbvArenaId")),"Saved actor run ownership drift");check(ArenaTerminals.classHash(body).equals(ArenaDirector.text(run,"native_class_sha256")),"Saved native boss class changed");}
   else check(body==null||body.isDead,"Sealed native body reappeared after restart");int count=0;for(Entity entity:w.loadedEntityList)if(entity.getUniqueID().equals(id)&&!entity.isDead)count++;check(count<2,"Duplicate exact actor after restart");out.addProperty("matching_live_boss_count",count);out.addProperty("saved_cell_count",saved.getAsJsonArray("cells").size());
  }out.addProperty("loaded_run_identity_party_and_journal",true);
 }
 private static void checkpoint()throws Exception{
  JsonObject next=new JsonObject();next.addProperty("schema",1);next.addProperty("next_boot",boot+1);JsonObject runs=new JsonObject(),inventories=new JsonObject(),replay=new JsonObject();
  for(ArenaDirector.Definition d:definitions){JsonObject run=ArenaDirector.run(d);check(run!=null,"No actual run at checkpoint");runs.add(d.id,new JsonParser().parse(ArenaDirector.JSON.toJson(run)));EntityPlayerMP p=players.get(d.id);if(p!=null){inventories.addProperty(d.id,p.inventory.writeToNBT(new NBTTagList()).toString());RewardOutbox.flush(p);}if(boot==5)replay.add(d.id,previous.getAsJsonObject("runs").get(d.id));}
  next.add("runs",runs);next.add("inventories",inventories);if(boot==5)next.add("earned_replay_snapshots",replay);
  byte[] bytes=ArenaDirector.JSON.toJson(next).getBytes(StandardCharsets.UTF_8);check(bytes.length<=4*1024*1024,"Private restart checkpoint budget");Path temp=checkpoint.resolveSibling("PRIVATE-RESTART-STATE.tmp");Files.write(temp,bytes,StandardOpenOption.CREATE_NEW);Files.move(temp,checkpoint,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
 }
 private static void finish(boolean success,Throwable error){
  report.addProperty("passed",success&&rows.size()==5);if(error!=null)report.addProperty("error",error.toString());report.addProperty("all_seven_boots_complete",success&&boot==6);report.addProperty("process_stop_requested_only_after_real_save_checkpoint",success);
  if(success)try{checkpoint();}catch(Throwable failure){report.addProperty("passed",false);report.addProperty("checkpoint_failure",failure.toString());}
  for(EntityPlayerMP p:new ArrayList<>(players.values()))if(server.getPlayerList().getPlayerByUUID(p.getUniqueID())==p)server.getPlayerList().playerLoggedOut(p);
  for(ArenaDirector.Definition d:definitions)d.terminal_verified=trust.get(d.id);done=true;
 }
 public static boolean tick(){if(done)return true;try{
  check(++clock<18000,"Restart boot bounded tick budget exceeded");if(--wait>0)return false;
  if(index>=definitions.size()){finish(true,null);return true;}
  ArenaDirector.Definition d=definitions.get(index);JsonObject out;if(stage==0){out=new JsonObject();out.addProperty("id",d.id);out.addProperty("template_sha256",d.template_sha256);rows.add(out);stage=1;}else out=rows.get(rows.size()-1).getAsJsonObject();
  if(!warmSaved(d))return false;JsonObject r=ArenaDirector.run(d);
  if(stage==1){if(boot>0)verifyLoaded(d,out);EntityPlayerMP p=login(d);guide(p,d.entry);out.addProperty("official_reconnected_current_body",true);
   if(boot==0){check(r==null||ArenaDirector.text(r,"state").equals("AVAILABLE"),"Restart test needs genuinely empty fresh arena");ArenaIntro.start(p,d);click(p,d,0);stage=2;wait=3;return false;}
   if(boot<=3){check(ArenaDirector.text(r,"state").equals("PHASE"+boot),"Actual phase not preserved across process");click(p,d,0);stage=4;wait=3;return false;}
   if(boot==4){check(startupResetSeen.contains(d.id),"Actual startup sealing reset not observed");String state=ArenaDirector.text(r,"state");check(state.equals("SEALING_OBSERVE")||state.equals("REWARD_PENDING"),"Sealing state not preserved");out.addProperty("actual_fml_startup_observation_reset",true);stage=6;return false;}
   if(boot==5){check(ArenaDirector.text(r,"state").equals("REWARD_PENDING"),"Pending state not preserved");check(previous.getAsJsonObject("inventories").get(d.id).getAsString().equals(p.inventory.writeToNBT(new NBTTagList()).toString()),"Full inventory did not persist");p.inventory.mainInventory.set(5,ItemStack.EMPTY);guide(p,d.reward);check(RewardOutbox.deliver(p,r,0),"Real saved earned pending reward did not deliver");NBTTagList before=p.inventory.writeToNBT(new NBTTagList());check(RewardOutbox.deliver(p,r,1)&&before.equals(p.inventory.writeToNBT(new NBTTagList())),"Saved earned reward replay duplicated");ArenaDirector.exit(p,d,r);stage=7;wait=3;return false;}
   if(boot==6){check(ArenaDirector.text(r,"state").equals("AVAILABLE"),"Reset did not persist");check(previous.getAsJsonObject("inventories").get(d.id).getAsString().equals(p.inventory.writeToNBT(new NBTTagList()).toString()),"Delivered actual inventory changed across final restart");JsonObject saved=previous.getAsJsonObject("earned_replay_snapshots").getAsJsonObject(d.id);NBTTagList before=p.inventory.writeToNBT(new NBTTagList());check(RewardOutbox.deliver(p,saved,1)&&before.equals(p.inventory.writeToNBT(new NBTTagList())),"Durable player receipt failed authentic old-entitlement replay");out.addProperty("reset_and_delivered_receipt_survived_process",true);stage=9;return false;}
  }
  EntityPlayerMP p=players.get(d.id);
  if(stage==2){click(p,d,1);stage=3;wait=3;return false;}
  if(stage==3){check(ArenaIntro.complete(p,d),"Restart real introduction not complete");equip(p,d);ArenaDirector.admit(p,d,false);guide(p,d.entry);out.addProperty("real_introduction_admission",true);stage=8;wait=8;return false;}
  if(stage==4){check(r.has("objectives")&&r.getAsJsonObject("objectives").has(boot+":0"),"First physical counter did not persist after reconnect");engage(p,d);stage=41;return false;}
  if(stage==41){defend(p,d);r=ArenaDirector.run(d);int charge=r.has("seal_pressure_ticks")?ArenaDirector.number(r.getAsJsonObject("seal_pressure_ticks"),Integer.toString(boot)):0;if(charge<40)return false;check(boss(d).ticksExisted>engagementStart,"No actual boss ticks advanced under pressure");out.addProperty("actual_sequence_pressure_ticks",charge);out.addProperty("actual_pressure_body_tick_advance",boss(d).ticksExisted-engagementStart);click(p,d,1);stage=5;wait=3;return false;}
  if(stage==5){r=ArenaDirector.run(d);String expected=boot<3?"PHASE"+(boot+1):"SEALING_OBSERVE";check(ArenaDirector.text(r,"state").equals(expected),"Accepted physical counters failed "+expected+" got "+ArenaDirector.text(r,"state"));guide(p,d.entry);out.addProperty("physical_seal_progress_after_reconnect",true);stage=8;wait=3;return false;}
  if(stage==6){r=ArenaDirector.run(d);String state=ArenaDirector.text(r,"state");check(state.equals("SEALING_OBSERVE")||state.equals("REWARD_PENDING"),"Restart seal became ambiguous: "+state);if(state.equals("SEALING_OBSERVE"))return false;check(ArenaDirector.number(r,"seal_observation_ticks")>=200,"Restart sealing skipped loaded observation");guide(p,d.reward);for(int i=0;i<p.inventory.mainInventory.size();i++)p.inventory.mainInventory.set(i,new ItemStack(Blocks.COBBLESTONE,64));check(!RewardOutbox.deliver(p,r,0),"Real earned full-inventory queue unexpectedly delivered");guide(p,d.entry);out.addProperty("real_loaded_sealing_and_earned_full_inventory_queue",true);stage=9;return false;}
  if(stage==7){r=ArenaDirector.run(d);String state=ArenaDirector.text(r,"state");check(state.equals("RESET_READY")||state.equals("AVAILABLE"),"Post-restart automatic reset failed: "+state);if(!state.equals("AVAILABLE")){wait=3;return false;}out.addProperty("real_pending_delivery_replay_nonop_automatic_reset",true);stage=9;}
  if(stage==8){r=ArenaDirector.run(d);check(!ArenaDirector.text(r,"state").equals("SAFE_CLOSED"),"Restart candidate safety closed");out.addProperty("actual_saved_state",ArenaDirector.text(r,"state"));stage=9;}
  if(stage==9){out.addProperty("passed",true);index++;stage=0;wait=2;}
 }catch(Throwable error){finish(false,error);}return done;}
}
