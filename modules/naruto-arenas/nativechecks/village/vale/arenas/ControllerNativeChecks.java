package village.vale.arenas;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import village.vale.RewardLedger;
import village.vale.bridge.MoveCost;

/** Asynchronous real-world controller exercise. Only the private guarded QA JAR
 * invokes this. Teleports are test guidance, not accepted walking/client input. */
public final class ControllerNativeChecks {
 private static MinecraftServer server;private static List<ArenaDirector.Definition> definitions;
 private static EntityPlayerMP player;private static int index,stage,wait,clock,phase,node,repeat;
 private static boolean done,trust;private static int bodyTicks;private static ArenaDirector.Definition d;private static JsonObject row,report;
 private static JsonArray rows,failures;private static NBTTagList inventory;private static GameType mode;
 private static double startX,startY,startZ;private static final LateCancel cancel=new LateCancel();
 private static final LoadSamples load=new LoadSamples();
 private static final DamageLog damageLog=new DamageLog();
 public static final class DamageLog {
  final List<DamageObservation> pending=new ArrayList<>();
  void add(net.minecraftforge.fml.common.eventhandler.Event event,EntityLivingBase target,DamageSource source,String kind,float amount){if(target==player&&row!=null&&pending.size()<128)pending.add(new DamageObservation(event,target,source,kind,amount,row));}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void hurt(LivingHurtEvent e){add(e,e.getEntityLiving(),e.getSource(),"HURT",e.getAmount());}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void damage(LivingDamageEvent e){add(e,e.getEntityLiving(),e.getSource(),"DAMAGE",e.getAmount());}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void death(LivingDeathEvent e){add(e,e.getEntityLiving(),e.getSource(),"DEATH",0);}
  void flush(){for(DamageObservation p:pending){JsonObject r=new JsonObject();r.addProperty("event",p.kind);r.addProperty("damage_type",p.source.damageType);r.addProperty("amount",Float.isFinite(p.amount)?p.amount:0);r.addProperty("final_cancelled",p.event.isCanceled());r.addProperty("health_after_unwind",Float.isFinite(p.target.getHealth())?p.target.getHealth():0);r.addProperty("unblockable",p.source.isUnblockable());r.addProperty("magic",p.source.isMagicDamage());r.addProperty("projectile",p.source.isProjectile());r.addProperty("fire",p.source.isFireDamage());Entity trueSource=p.source.getTrueSource(),immediate=p.source.getImmediateSource();r.addProperty("true_source_class",trueSource==null?"":trueSource.getClass().getName());r.addProperty("immediate_source_class",immediate==null?"":immediate.getClass().getName());if(!p.row.has("native_damage_observations"))p.row.add("native_damage_observations",new JsonArray());JsonArray list=p.row.getAsJsonArray("native_damage_observations");if(list.size()<240)list.add(r);}pending.clear();}
 }
 private static final class DamageObservation {final net.minecraftforge.fml.common.eventhandler.Event event;final EntityLivingBase target;final DamageSource source;final String kind;final float amount;final JsonObject row;DamageObservation(net.minecraftforge.fml.common.eventhandler.Event e,EntityLivingBase t,DamageSource s,String k,float a,JsonObject r){event=e;target=t;source=s;kind=k;amount=a;row=r;}}
 public static final class LoadSamples {
  final long[] times=new long[36000];int size,maxChunks,maxEntities,maxPlayers;long start,first,last,peakHeap;boolean enabled;
  final Map<Integer,Integer> activePartyTicks=new TreeMap<>();final Map<String,Integer> bodyTicksByRun=new HashMap<>();
  void begin(){size=maxChunks=maxEntities=maxPlayers=0;start=first=last=peakHeap=0;activePartyTicks.clear();bodyTicksByRun.clear();enabled=true;MinecraftForge.EVENT_BUS.register(this);}
  @SubscribeEvent(priority=EventPriority.HIGHEST)public void start(TickEvent.ServerTickEvent e){if(enabled&&e.phase==TickEvent.Phase.START){start=System.nanoTime();if(first==0)first=start;}}
  @SubscribeEvent(priority=EventPriority.LOWEST)public void end(TickEvent.ServerTickEvent e){if(!enabled||e.phase!=TickEvent.Phase.END||start==0)return;last=System.nanoTime();if(size<times.length)times[size++]=last-start;start=0;
   WorldServer w=server.getWorld(0);maxChunks=Math.max(maxChunks,w.getChunkProvider().getLoadedChunks().size());maxEntities=Math.max(maxEntities,w.loadedEntityList.size());maxPlayers=Math.max(maxPlayers,server.getPlayerList().getCurrentPlayerCount());Runtime vm=Runtime.getRuntime();peakHeap=Math.max(peakHeap,vm.totalMemory()-vm.freeMemory());
   for(ArenaDirector.Definition arena:definitions){JsonObject r=ArenaDirector.run(arena);if(r==null||!ArenaDirector.text(r,"state").startsWith("PHASE"))continue;Entity body=w.getEntityFromUuid(UUID.fromString(ArenaDirector.text(r,"boss_uuid")));if(body==null||body.isDead)continue;String id=ArenaDirector.text(r,"run");Integer prior=bodyTicksByRun.put(id,body.ticksExisted);if(prior==null||body.ticksExisted<=prior)continue;int online=0;for(String owner:ArenaDirector.keys(r.getAsJsonObject("members"))){EntityPlayerMP p=server.getPlayerList().getPlayerByUUID(UUID.fromString(owner));if(p!=null&&ArenaDirector.eligible(p,r.get("preview").getAsBoolean()))online++;}activePartyTicks.put(online,activePartyTicks.getOrDefault(online,0)+1);}
  }
  JsonObject finish(){enabled=false;MinecraftForge.EVENT_BUS.unregister(this);long[] sorted=Arrays.copyOf(times,size);Arrays.sort(sorted);JsonObject r=new JsonObject();r.addProperty("scope","Actual server START/END ticks during guided native controller; includes prewarm, native AI, hazards and party cases; no real network client");r.addProperty("samples",size);r.addProperty("sample_capacity",times.length);double seconds=last>first?(last-first)/1e9:0;r.addProperty("duration_seconds",seconds);r.addProperty("observed_tps",seconds>0?size/seconds:0);r.addProperty("p50_tick_ms",percentile(sorted,.50));r.addProperty("p95_tick_ms",percentile(sorted,.95));r.addProperty("p99_tick_ms",percentile(sorted,.99));r.addProperty("max_tick_ms",size==0?0:sorted[size-1]/1e6);r.addProperty("max_loaded_chunks",maxChunks);r.addProperty("max_loaded_entities",maxEntities);r.addProperty("max_registered_player_bodies",maxPlayers);r.addProperty("peak_used_java_heap_bytes",peakHeap);r.add("actual_boss_updating_ticks_by_online_party_size",ArenaDirector.JSON.toJsonTree(activePartyTicks));r.addProperty("os",System.getProperty("os.name"));r.addProperty("arch",System.getProperty("os.arch"));r.addProperty("cpu",System.getenv("PROCESSOR_IDENTIFIER"));r.addProperty("available_processors",Runtime.getRuntime().availableProcessors());r.addProperty("tick_targets_met",size>0&&percentile(sorted,.95)<=50&&percentile(sorted,.99)<=100);return r;}
  double percentile(long[] sorted,double fraction){return sorted.length==0?0:sorted[Math.min(sorted.length-1,(int)Math.ceil(sorted.length*fraction)-1)]/1e6;}
 }
 public static class LateCancel {boolean armed;@SubscribeEvent(priority=EventPriority.LOWEST)public void click(PlayerInteractEvent.RightClickBlock e){if(armed&&e.getEntityPlayer()==player)e.setCanceled(true);}}
 private ControllerNativeChecks(){}
 public static void begin(MinecraftServer s,List<ArenaDirector.Definition> all,EntityPlayerMP p){
  if(!Boolean.getBoolean("hbv.arenas.nativeProbe")||!Files.isRegularFile(Paths.get(".hbv-arenas-native-probe"))||!s.getFolderName().equals("arena-preview")||s.getPlayerList().getPlayerByUUID(p.getUniqueID())!=p)throw new IllegalStateException("Private controller guard rejected");
  server=s;definitions=new ArrayList<>(all);player=p;index=stage=wait=clock=repeat=0;done=false;
  report=new JsonObject();rows=new JsonArray();report.add("arenas",rows);
  report.addProperty("scope","REAL_NATIVE_CONTROLLER_GUIDED_SOLO_FLOW_WITH_PRIVATE_TRUST_GATE; not ordinary client walking or production policy promotion");
  report.addProperty("official_registered_player",true);report.addProperty("actual_client",false);report.addProperty("production_terminal_policy_promoted",false);
  inventory=p.inventory.writeToNBT(new NBTTagList());mode=p.interactionManager.getGameType();startX=p.posX;startY=p.posY;startZ=p.posZ;
  MinecraftForge.EVENT_BUS.register(cancel);MinecraftForge.EVENT_BUS.register(damageLog);load.begin();
 }
 public static JsonObject result(){return report;}
 public static EntityPlayerMP currentPlayer(){return player;}
 private static void check(boolean ok,String description){if(!ok)throw new IllegalStateException(description);}
 private static void warm(int[] at){NativeProbe.prewarm(server.getWorld(0),new BlockPos(at[0],at[1],at[2]));}
 private static void guide(int[] at){warm(at);check(ArenaDirector.safe(server.getWorld(0),at),"Guided landing unsafe "+Arrays.toString(at));player.connection.setPlayerLocation(at[0]+.5,at[1],at[2]+.5,player.rotationYaw,player.rotationPitch);player.fallDistance=0;server.getWorld(0).getPlayerChunkMap().updateMovingPlayer(player);}
 private static void near(int[] at){for(int[] off:new int[][]{{0,-3},{0,3},{-3,0},{3,0}}){int[] landing={at[0]+off[0],d.floor,at[2]+off[1]};warm(landing);if(ArenaDirector.safe(server.getWorld(0),landing)){guide(landing);return;}}throw new IllegalStateException("No supported physical seal approach");}
 private static void click(int which,boolean cancelled){int[] at=d.nodes[which];near(at);cancel.armed=cancelled;try{ForgeHooks.onRightClickBlock(player,EnumHand.MAIN_HAND,new BlockPos(at[0],at[1],at[2]),EnumFacing.UP,new Vec3d(at[0]+.5,at[1]+.5,at[2]+.5));}finally{cancel.armed=false;}}
 private static JsonObject current(){JsonObject r=ArenaDirector.run(d);check(r!=null,"Run record missing");return r;}
 private static String state(){return ArenaDirector.text(current(),"state");}
 private static void engage(){
  Entity body=server.getWorld(0).getEntityFromUuid(UUID.fromString(ArenaDirector.text(current(),"boss_uuid")));
  check(body instanceof EntityLivingBase&&body.isEntityAlive(),"Authentic live boss missing before phase engagement");
  bodyTicks=body.ticksExisted;
  for(int[] offset:new int[][]{{12,0},{-12,0},{0,12},{0,-12}}){int[] at={(int)Math.floor(body.posX)+offset[0],d.floor,(int)Math.floor(body.posZ)+offset[1]};warm(at);if(d.contains(new BlockPos(at[0],at[1],at[2]))&&ArenaDirector.safe(server.getWorld(0),at)){guide(at);return;}}
  throw new IllegalStateException("No actual supported approach within native boss activation range");
 }
 private static void equip(){
  // These are real Core-issued serials for a private synthetic QA body. Its
  // original inventory is saved/restored, and no real player profile is read.
  player.inventory.clear();String[] keys=CoreRewardOutbox.bundle(d.id);RewardLedger ledger=RewardLedger.get(player);
  for(String key:keys)ledger.issue(player,key,1,"private-arena-flow/"+UUID.randomUUID()+"/"+key);
  EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
  for(int n=0;n<4;n++)for(int i=0;i<player.inventory.mainInventory.size();i++){ItemStack stack=player.inventory.mainInventory.get(i);if(!stack.isEmpty()&&stack.getItem().getRegistryName()!=null&&stack.getItem().getRegistryName().getPath().equals(keys[n])){check(ledger.valid(player,stack),"Equipment serial not owned");player.setItemStackToSlot(slots[n],stack);player.inventory.mainInventory.set(i,ItemStack.EMPTY);break;}}
  for(int i=0;i<player.inventory.mainInventory.size();i++){ItemStack stack=player.inventory.mainInventory.get(i);if(!stack.isEmpty()&&stack.getItem().getRegistryName()!=null&&stack.getItem().getRegistryName().getPath().equals(keys[4])){check(ledger.valid(player,stack),"Weapon serial not owned");player.inventory.currentItem=i;break;}}
  row.addProperty("actual_owned_armor_and_weapon_equipped",true);
  player.setItemStackToSlot(EntityEquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
 }
 private static boolean camp(){
  check(d.services!=null&&d.services.length==3,"Actual camp services are not configured");int[] at=d.services[1];warm(at);ArenaServices.ensure(d);
  String key=d.id+"/aid";JsonObject records=ArenaServices.records();if(!records.has(key))return false;
  Entity aid=server.getWorld(0).getEntityFromUuid(UUID.fromString(ArenaDirector.text(records.getAsJsonObject(key),"uuid")));if(aid==null)return false;
  boolean placed=false;for(int[] offset:new int[][]{{2,0},{-2,0},{0,2},{0,-2}}){int[] landing={(int)Math.floor(aid.posX)+offset[0],(int)Math.floor(aid.posY),(int)Math.floor(aid.posZ)+offset[1]};if(ArenaDirector.safe(server.getWorld(0),landing)){guide(landing);placed=true;break;}}
  check(placed,"Native camp aid has no usable approach");double before=MoveCost.view(player).amount;boolean handled=ForgeHooks.onInteractEntity(player,aid,EnumHand.MAIN_HAND)!=null;double after=MoveCost.view(player).amount;
  check(handled,"Actual native camp aid interaction was not handled");check(Double.isFinite(before)&&Double.isFinite(after)&&after>=before,"Camp lost native chakra or changed to an unready account");
  if(before<MoveCost.view(player).maximum-1&&before<40)check(after>before,"Native camp recovery did not replenish depleted chakra");
  row.addProperty("run"+repeat+"_actual_camp_chakra_recovery",after-before);return true;
 }
 private static void defend(){
  if(stage!=3||d==null||!player.isEntityAlive()||player.isCreative()||!state().startsWith("PHASE"))return;
  Entity body=server.getWorld(0).getEntityFromUuid(UUID.fromString(ArenaDirector.text(current(),"boss_uuid")));if(body==null)return;
  double rx=player.posX-body.posX,rz=player.posZ-body.posZ,distance=Math.sqrt(rx*rx+rz*rz);if(distance<.01)return;
  player.rotationYaw=(float)Math.toDegrees(Math.atan2(-(body.posX-player.posX),body.posZ-player.posZ));player.rotationPitch=0;
  if(!player.isHandActive())player.setActiveHand(EnumHand.OFF_HAND);
  if(ArenaDirector.ticks%24==0){double before=MoveCost.view(player).amount;boolean accepted=village.vale.ArenaDodgeNativeAccess.perform(player,village.vale.DodgeMath.LEFT);if(accepted){row.addProperty("actual_paid_core_dodges",row.has("actual_paid_core_dodges")?row.get("actual_paid_core_dodges").getAsInt()+1:1);row.addProperty("last_dodge_native_cost",before-MoveCost.view(player).amount);}}
  double dx=-rz/distance*.10+player.motionX,dz=rx/distance*.10+player.motionZ,dy=player.motionY-.08;
  int[] at={(int)Math.floor(player.posX+dx),d.floor,(int)Math.floor(player.posZ+dz)};
  if(d.contains(new BlockPos(at[0],at[1],at[2]))&&ArenaDirector.safe(server.getWorld(0),at)){player.move(MoverType.SELF,dx,Math.max(-.6,dy),dz);player.motionX*=.6;player.motionZ*=.6;player.motionY=player.onGround?0:dy;server.getWorld(0).getPlayerChunkMap().updateMovingPlayer(player);}
 }
 private static void finishArena(){d.terminal_verified=trust;row.addProperty("passed",failures.size()==0);rows.add(row);d=null;index++;stage=clock=repeat=0;}
 private static void finish(){
  cancel.armed=false;MinecraftForge.EVENT_BUS.unregister(cancel);damageLog.flush();MinecraftForge.EVENT_BUS.unregister(damageLog);
  if(d!=null)d.terminal_verified=trust;
  player.inventory.readFromNBT(inventory);player.setGameType(mode);player.connection.setPlayerLocation(startX,startY,startZ,player.rotationYaw,player.rotationPitch);RewardOutbox.flush(player);
  boolean passed=rows.size()==5,cooperative=rows.size()==5;for(JsonElement r:rows){JsonObject arena=r.getAsJsonObject();passed&=arena.get("passed").getAsBoolean();cooperative&=arena.has("native_party_cases")&&arena.getAsJsonObject("native_party_cases").get("passed").getAsBoolean()&&arena.has("party_live_phase_closed");}report.add("measured_load",load.finish());report.addProperty("passed",passed);report.addProperty("process_restart_each_phase",false);report.addProperty("cooperative_four_player",cooperative);done=true;
 }
 public static boolean tick(){
  if(done)return true;
  damageLog.flush();defend();
  if(--wait>0){clock++;return false;}
  if(index>=definitions.size()){finish();return true;}
  if(d==null){d=definitions.get(index);trust=d.terminal_verified;row=new JsonObject();failures=new JsonArray();row.addProperty("id",d.id);row.add("failures",failures);row.addProperty("template_sha256",d.template_sha256);}
  try{
   check(server.getPlayerList().getPlayerByUUID(player.getUniqueID())==player,"Official body was replaced or logged out");
   check(player.isEntityAlive(),"Participant died during actual controller test");
   // Only already generated neighbors may be prewarmed by NativeProbe.prewarm.
   warm(d.boss_anchor);clock++;check(clock<1800,"Native flow exceeded bounded1800tick budget");
   if(stage==0){
    player.setGameType(GameType.SURVIVAL);player.setHealth(player.getMaxHealth());warm(d.entry);warm(d.exit);warm(d.reward);
    JsonObject previous=ArenaDirector.run(d);check(previous==null||ArenaDirector.text(previous,"state").equals("AVAILABLE"),"Previous arena requires genuine empty reset");
    ArenaIntro.start(player,d);click(0,false);stage=1;wait=3;return false;
   }
   if(stage==1){click(1,false);stage=2;wait=3;return false;}
   if(stage==2){
    check(ArenaIntro.complete(player,d),"Physical introduction did not persist complete");row.addProperty("real_two_physical_seal_introduction",true);
    row.add("native_chakra_after_intro",ArenaDirector.JSON.toJsonTree(MoveCost.view(player)));
    equip();stage=21;wait=20;return false;
   }
   if(stage==21){
    player.setHealth(player.getMaxHealth());row.addProperty("prepared_actual_equipment_max_health",player.getMaxHealth());
    if(!camp()){wait=2;return false;}
    d.terminal_verified=true;ArenaDirector.admit(player,d,false);check(state().equals("PHASE1"),"Normal admission did not start phase1");
    row.addProperty("actual_native_boss_normal_admission",true);phase=1;
    if(repeat==0){PartyNativeChecks.begin(server,d,current(),player);stage=20;wait=1;return false;}
    engage();stage=3;wait=48;return false;
   }
   if(stage==20){
    if(!PartyNativeChecks.tick()){wait=1;return false;}
    JsonObject party=PartyNativeChecks.result();row.add("native_party_cases",party);check(party.get("passed").getAsBoolean(),"Actual registered party cases failed: "+party);
    PartyNativeChecks.holdCombat(d);engage();stage=3;wait=48;return false;
   }
   if(stage==3){
    check(state().startsWith("PHASE"),"Run closed before physical phase counter: "+state());
    if(row.has("native_party_cases")&&!row.has("party_live_phase_closed")){PartyNativeChecks.endCombat();check(PartyNativeChecks.result().get("passed").getAsBoolean(),"Actual four-player live combat failed");row.addProperty("party_live_phase_closed",true);}
    Entity active=server.getWorld(0).getEntityFromUuid(UUID.fromString(ArenaDirector.text(current(),"boss_uuid")));
    check(active!=null&&active.ticksExisted>bodyTicks,"Native boss did not actually update during phase");
    row.addProperty("run"+repeat+"_phase"+phase+"_actual_body_ticks",active.ticksExisted-bodyTicks);
    check(!(active instanceof EntityLiving)||!((EntityLiving)active).isAIDisabled(),"Native boss AI was disabled during encounter");
    phase=ArenaDirector.number(current(),"phase");
    int before=current().has("objectives")?current().getAsJsonObject("objectives").size():0;row.addProperty("cancel_objectives_before",before);click(2,true);stage=4;wait=3;return false;
   }
   if(stage==4){
    int now=current().has("objectives")?current().getAsJsonObject("objectives").size():0;
    check(now==row.get("cancel_objectives_before").getAsInt(),"Later canceled physical interaction advanced objective");row.addProperty("final_cancelled_seal_rejected",true);
    click(0,false);stage=5;wait=3;return false;
   }
   if(stage==5){click(1,false);stage=6;wait=3;return false;}
   if(stage==6){
    String status=state();check(!status.equals("SAFE_CLOSED"),"Seal/actor restoration closed the run");
    if(phase<3){check(status.equals("PHASE"+(phase+1)),"Two distinct physical nodes did not advance phase");phase++;engage();stage=3;wait=48;return false;}
    check(status.equals("SEALING_OBSERVE")||status.equals("REWARD_PENDING"),"Final phase did not begin authenticated sealing");stage=7;wait=5;return false;
   }
   if(stage==7){
    String status=state();check(!status.equals("SAFE_CLOSED"),"Ambiguous native actor blocked terminal");
    if(!status.equals("REWARD_PENDING")){check(status.equals("SEALING_OBSERVE"),"Unexpected terminal state "+status);wait=5;return false;}
    check(ArenaDirector.number(current(),"seal_observation_ticks")>=200,"Terminal lacked200loaded-world ticks");
    row.addProperty("three_phases_and_actual_owned_body_seal",true);row.addProperty("loaded_world_terminal_observation",ArenaDirector.number(current(),"seal_observation_ticks"));
    check(current().getAsJsonArray("cells").size()==0,"Environmental journal not fully restored");guide(d.reward);
    for(int i=0;i<player.inventory.mainInventory.size();i++)player.inventory.mainInventory.set(i,new ItemStack(Blocks.COBBLESTONE,64));
    check(!RewardOutbox.deliver(player,current(),0),"Actual victory full inventory unexpectedly delivered");
    String token=ArenaDirector.text(current(),"run")+":"+player.getUniqueID();row.addProperty("reward_token",token);
    ArenaDirector.exit(player,d,current());check(RewardArchive.pending(player)!=null,"Exit lost actual pending earned reward");
    ArenaDirector.reset(d,current());check(state().equals("AVAILABLE"),"Actual empty combat-cell reset failed");
    if(row.has("native_party_cases")&&row.getAsJsonObject("native_party_cases").has("offline_contributor_uuid")){
     String owner=row.getAsJsonObject("native_party_cases").get("offline_contributor_uuid").getAsString();boolean retained=false;
     for(String id:ArenaDirector.keys(RewardArchive.records())){JsonObject archivedParty=RewardArchive.records().getAsJsonObject(id);JsonObject members=archivedParty.getAsJsonObject("members");
      if(members.has(owner)){JsonObject contribution=members.getAsJsonObject(owner);if(contribution.get("earned").getAsBoolean()&&contribution.get("contribution").getAsDouble()>0&&!ArenaDirector.text(contribution,"outbox").equals("DELIVERED"))retained=true;}}
     check(retained,"Offline actual contributor lost earned entitlement through clear/exit/reset");row.addProperty("offline_actual_contributor_reward_retained",true);
    }
    JsonObject archived=RewardArchive.pending(player);player.inventory.mainInventory.set(5,ItemStack.EMPTY);
    guide(d.reward);check(RewardOutbox.deliver(player,archived,0),"Archived actual earned reward did not deliver");
    NBTTagList delivered=player.inventory.writeToNBT(new NBTTagList());check(RewardOutbox.deliver(player,archived,1)&&delivered.equals(player.inventory.writeToNBT(new NBTTagList())),"Actual earned reward replay duplicated inventory");
    row.addProperty("actual_clear_full_inventory_exit_archive_reset_claim_replay",true);
    if(repeat==0){repeat=1;row.addProperty("first_run_complete",true);player.resetActiveHand();equip();stage=22;wait=20;return false;}
    row.addProperty("clean_second_run_complete",true);finishArena();wait=3;return false;
   }
   if(stage==22){player.setHealth(player.getMaxHealth());if(!camp()){wait=2;return false;}ArenaDirector.admit(player,d,false);phase=1;engage();stage=3;wait=48;return false;}
  }catch(Throwable error){failures.add(error.toString());row.addProperty("failed_stage",stage);row.addProperty("last_state",d==null?"":ArenaDirector.run(d)==null?"NO_RUN":state());
   // Preserve failed arena state and actor/cell evidence. Do not blind-reset it.
   damageLog.flush();try{if(row.has("native_party_cases")&&!row.has("party_live_phase_closed"))PartyNativeChecks.endCombat();}catch(Throwable cleanup){row.addProperty("party_cleanup_observation",cleanup.toString());}
   try{if(!player.isEntityAlive()){player=server.getPlayerList().recreatePlayerEntity(player,0,false);check(server.getPlayerList().getPlayerByUUID(player.getUniqueID())==player&&player.isEntityAlive(),"Official death recreation failed");row.addProperty("actual_official_death_respawn",true);}
    player.setGameType(GameType.CREATIVE);if(d!=null){warm(d.exit);if(ArenaDirector.safe(server.getWorld(0),d.exit)){guide(d.exit);JsonObject failed=ArenaDirector.run(d);if(failed!=null&&failed.getAsJsonObject("members").has(player.getUniqueID().toString()))ArenaDirector.exit(player,d,failed);}}}catch(Throwable cleanup){row.addProperty("failure_recovery_observation",cleanup.toString());}
   finishArena();wait=3;
  }
  return false;
 }
}
