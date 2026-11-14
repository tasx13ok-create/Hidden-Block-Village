package village.vale.arenas;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import village.vale.RewardLedger;

/** Asynchronous official-player party checks on a real admitted normal run.
 * Guidance teleports are explicit QA, never human walking/input acceptance. */
public final class PartyNativeChecks {
 private static MinecraftServer server;private static ArenaDirector.Definition arena;
 private static JsonObject run,result;private static JsonArray failures,intros,sizes;
 private static EntityPlayerMP leader,oldBody;private static final List<EntityPlayerMP> helpers=new ArrayList<>();
 private static int seat,stage,wait,ticks,objectivesBefore;private static boolean done;
 private static boolean holding,combatEnded;private static Entity combatBoss;private static int combatBossTicks;private static long combatWorldTime;
 private static final Map<UUID,NBTTagList> originalInventories=new HashMap<>();
 private static final LateCancel cancel=new LateCancel();
 private static class LateCancel {EntityPlayerMP target;boolean armed;@SubscribeEvent(priority=EventPriority.LOWEST)public void click(PlayerInteractEvent.RightClickBlock e){if(armed&&e.getEntityPlayer()==target)e.setCanceled(true);}}
 private PartyNativeChecks(){}
 private static void check(boolean ok,String text){if(!ok)throw new IllegalStateException(text);}
 private static boolean rejected(Runnable action){try{action.run();return false;}catch(IllegalStateException expected){return true;}}
 public static void begin(MinecraftServer s,ArenaDirector.Definition d,JsonObject actualRun,EntityPlayerMP p){
  if(!Boolean.getBoolean("hbv.arenas.nativeProbe")||!Files.isRegularFile(Paths.get(".hbv-arenas-native-probe"))||!s.getFolderName().equals("arena-preview")||s.getPlayerList().getPlayerByUUID(p.getUniqueID())!=p||ArenaDirector.run(d)!=actualRun||actualRun.get("preview").getAsBoolean()||!ArenaDirector.text(actualRun,"state").equals("PHASE1"))throw new IllegalStateException("Private normal-party guard rejected");
  server=s;arena=d;run=actualRun;leader=p;helpers.clear();originalInventories.clear();oldBody=null;seat=stage=wait=ticks=0;done=false;holding=combatEnded=false;
  result=new JsonObject();failures=new JsonArray();intros=new JsonArray();sizes=new JsonArray();result.add("failures",failures);result.add("physical_introductions",intros);result.add("observed_party_sizes",sizes);sizes.add(1);
  result.addProperty("scope","REAL_OFFICIAL_PLAYER_NORMAL_PARTY_ADMISSION_OBJECTIVES_AND_BOUNDED_LIVE_FOUR_PLAYER_PHASE; guided positions; no client or full cooperative clear acceptance");
  result.addProperty("support_and_accepted_damage_credit","NOT_IMPLEMENTED_SHARED_ACCEPTED_LOSS_API_REQUIRED; objectives only");
  guide(leader,d.entry);MinecraftForge.EVENT_BUS.register(cancel);
 }
 public static JsonObject result(){return result;}
 private static WorldServer world(){return server.getWorld(0);}
 private static void warm(int[] at){NativeProbe.prewarm(world(),new BlockPos(at[0],at[1],at[2]));}
 private static void guide(EntityPlayerMP p,int[] at){warm(at);check(ArenaDirector.safe(world(),at),"Party guidance landing unsafe");p.connection.setPlayerLocation(at[0]+.5,at[1],at[2]+.5,p.rotationYaw,p.rotationPitch);p.fallDistance=0;world().getPlayerChunkMap().updateMovingPlayer(p);}
 private static void near(EntityPlayerMP p,int[] node){for(int[] off:new int[][]{{0,-3},{0,3},{-3,0},{3,0}}){int[] at={node[0]+off[0],arena.floor,node[2]+off[1]};warm(at);if(ArenaDirector.safe(world(),at)){guide(p,at);return;}}throw new IllegalStateException("No safe native physical-node approach");}
 private static void click(EntityPlayerMP p,int node,boolean canceled){int[] at=arena.nodes[node];near(p,at);cancel.target=p;cancel.armed=canceled;try{ForgeHooks.onRightClickBlock(p,EnumHand.MAIN_HAND,new BlockPos(at[0],at[1],at[2]),EnumFacing.UP,new Vec3d(at[0]+.5,at[1]+.5,at[2]+.5));}finally{cancel.armed=false;cancel.target=null;}}
 private static EntityPlayerMP login(GameProfile profile){
  check(server.getPlayerList().getPlayerByUUID(profile.getId())==null,"Party fixture UUID already online");
  EntityPlayerMP p=new EntityPlayerMP(server,world(),profile,new PlayerInteractionManager(world()));p.connection=new NativeProbe.Quiet(server,p);
  server.getPlayerList().initializeConnectionToPlayer(p.connection.netManager,p,p.connection);
  check(server.getPlayerList().getPlayerByUUID(profile.getId())==p&&world().playerEntities.contains(p),"Official party login/lifecycle failed");
  p.setGameType(GameType.SURVIVAL);p.setHealth(p.getMaxHealth());guide(p,arena.entry);return p;
 }
 private static EntityPlayerMP create(int ordinal){UUID id=UUID.nameUUIDFromBytes(("HBV:Party:"+ArenaDirector.text(run,"run")+":"+ordinal).getBytes(StandardCharsets.UTF_8));return login(new GameProfile(id,"HbvParty0"+ordinal));}
 private static double contribution(EntityPlayerMP p){return run.getAsJsonObject("members").getAsJsonObject(p.getUniqueID().toString()).get("contribution").getAsDouble();}
 private static int objectives(){return run.has("objectives")?run.getAsJsonObject("objectives").size():0;}
 private static void wrongRun(EntityPlayerMP p){
  ArenaDirector.Definition other=null;for(ArenaDirector.Definition d:ArenaDirector.DEFINITIONS)if(d!=arena){JsonObject old=ArenaDirector.run(d);if(old==null||ArenaDirector.text(old,"state").equals("AVAILABLE")){other=d;break;}}
  check(other!=null,"No empty independent arena for actual wrong-run admission case");final ArenaDirector.Definition alternate=other;
  warm(other.entry);warm(other.exit);warm(other.boss_anchor);ArenaDirector.admit(p,other,true);JsonObject own=ArenaDirector.run(other);
  check(own!=null&&own.getAsJsonObject("members").has(p.getUniqueID().toString()),"Actual independent preview membership absent");
  check(rejected(()->ArenaDirector.join(p,arena)),"Already-admitted other-run player joined this normal party");
  ArenaDirector.exit(p,other,own);ArenaDirector.reset(other,own);check(ArenaDirector.text(own,"state").equals("AVAILABLE"),"Own empty wrong-run preview did not reset");guide(p,arena.entry);
  result.addProperty("actual_other_run_membership_rejected",true);
 }
 private static void success(){
  EntityPlayerMP contributor=helpers.get(0);check(contribution(contributor)>0,"Offline contributor lacks actual accepted objective");
  result.addProperty("offline_contributor_uuid",contributor.getUniqueID().toString());
  EntityPlayerMP waiting=helpers.get(3);if(server.getPlayerList().getPlayerByUUID(waiting.getUniqueID())==waiting)server.getPlayerList().playerLoggedOut(waiting);
  check(run.getAsJsonObject("members").size()==4,"Four actual members were not retained for live combat");
  result.addProperty("four_actual_members_ready_for_live_combat",true);
  cancel.armed=false;MinecraftForge.EVENT_BUS.unregister(cancel);result.addProperty("passed",true);done=true;
 }
 private static void equip(EntityPlayerMP p){
  // Genuine Core serials on newly created private QA profiles only. Their
  // original synthetic inventory is restored before logout.
  originalInventories.put(p.getUniqueID(),p.inventory.writeToNBT(new NBTTagList()));
  String[] keys=CoreRewardOutbox.bundle(arena.id);RewardLedger ledger=RewardLedger.get(p);
  EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
  for(int n=0;n<4;n++){
   ledger.issue(p,keys[n],1,"private-party-phase/"+ArenaDirector.text(run,"run")+"/"+p.getUniqueID()+"/"+keys[n]);boolean equipped=false;
   for(int i=0;i<p.inventory.mainInventory.size();i++){ItemStack stack=p.inventory.mainInventory.get(i);if(!stack.isEmpty()&&stack.getItem().getRegistryName()!=null&&stack.getItem().getRegistryName().getPath().equals(keys[n])){check(ledger.valid(p,stack),"Party armor has no genuine owner serial");p.setItemStackToSlot(slots[n],stack);p.inventory.mainInventory.set(i,ItemStack.EMPTY);equipped=true;break;}}
   check(equipped,"Genuine party ward piece was not issued/equipped");
  }
 }
 public static void holdCombat(ArenaDirector.Definition d){
  try{
   check(done&&!holding&&!combatEnded&&result.get("passed").getAsBoolean()&&d==arena&&ArenaDirector.run(arena)==run,"Party combat hold called outside successful actual party fixture");
   combatBoss=world().getEntityFromUuid(UUID.fromString(ArenaDirector.text(run,"boss_uuid")));
   check(combatBoss instanceof EntityLivingBase&&combatBoss.isEntityAlive()&&!(combatBoss instanceof EntityLiving&&((EntityLiving)combatBoss).isAIDisabled()),"Authentic active native boss absent for four-player phase");
   Set<String> used=new HashSet<>();int[][] offsets={{8,0},{-8,0},{0,8},{0,-8},{12,0},{-12,0},{0,12},{0,-12},{16,0},{-16,0},{0,16},{0,-16}};
   for(int member=0;member<3;member++){
    EntityPlayerMP p=helpers.get(member);check(ArenaDirector.eligible(p,false),"Party combat requires actual living registered survival bodies");equip(p);boolean placed=false;
    for(int k=0;k<offsets.length;k++){int[] off=offsets[(k+member)%offsets.length];int[] at={(int)Math.floor(combatBoss.posX)+off[0],arena.floor,(int)Math.floor(combatBoss.posZ)+off[1]};String key=Arrays.toString(at);warm(at);if(!used.contains(key)&&arena.contains(new BlockPos(at[0],at[1],at[2]))&&ArenaDirector.safe(world(),at)){guide(p,at);used.add(key);placed=true;break;}}
    check(placed,"No supported8..16block real party combat placement");p.setHealth(p.getMaxHealth());
   }
   combatBossTicks=combatBoss.ticksExisted;combatWorldTime=world().getTotalWorldTime();holding=true;
   result.addProperty("three_helpers_genuine_core_four_piece_armor",true);result.addProperty("four_survival_players_active_near_authentic_boss",true);
  }catch(RuntimeException error){failures.add(error.toString());result.addProperty("passed",false);for(EntityPlayerMP p:helpers)if(server.getPlayerList().getPlayerByUUID(p.getUniqueID())==p)server.getPlayerList().playerLoggedOut(p);throw error;}
 }
 public static void endCombat(){
  if(combatEnded)return;
  check(holding,"Party live phase was not begun");boolean actual=world().getTotalWorldTime()-combatWorldTime>=40&&combatBoss.ticksExisted>combatBossTicks&&run.getAsJsonObject("members").size()==4&&ArenaDirector.eligible(leader,false);
  for(int i=0;i<3;i++)actual&=ArenaDirector.eligible(helpers.get(i),false);
  result.addProperty("live_four_player_world_ticks",world().getTotalWorldTime()-combatWorldTime);result.addProperty("live_four_player_native_boss_updates",combatBoss.ticksExisted-combatBossTicks);result.addProperty("four_alive_official_survival_members_after_live_phase",actual);
  if(!actual){failures.add("Four-player live phase lacked40worldticks, boss updates, or four living registered survival participants");result.addProperty("passed",false);}
  for(int i=0;i<helpers.size();i++){
   EntityPlayerMP p=helpers.get(i);if(server.getPlayerList().getPlayerByUUID(p.getUniqueID())!=p)continue;
   if(i!=0&&run.getAsJsonObject("members").has(p.getUniqueID().toString())&&p.isEntityAlive()){warm(arena.exit);ArenaDirector.exit(p,arena,run);}
   NBTTagList original=originalInventories.get(p.getUniqueID());if(original!=null)p.inventory.readFromNBT(original);
   server.getPlayerList().playerLoggedOut(p);
  }
  EntityPlayerMP contributor=helpers.get(0);
  check(run.getAsJsonObject("members").size()==2&&run.getAsJsonObject("members").has(leader.getUniqueID().toString()),"Party cleanup lost leader or offline contributor");
  check(server.getPlayerList().getPlayerByUUID(contributor.getUniqueID())==null,"Genuine contributor is not actually offline");
  result.addProperty("actual_secondary_contributor_left_offline_for_real_clear",true);
  combatEnded=true;holding=false;check(actual,"Actual four-player live phase failed");
 }
 public static boolean tick(){
  if(done)return true;if(--wait>0)return false;
  try{
   ticks++;check(ticks<500,"Party fixture exceeded bounded 500-tick budget");
   check(ArenaDirector.run(arena)==run&&ArenaDirector.text(run,"state").equals("PHASE1")&&server.getPlayerList().getPlayerByUUID(leader.getUniqueID())==leader&&leader.isEntityAlive(),"Real leader run changed or closed during party admission");
   if(stage==0){EntityPlayerMP p=create(seat+2);helpers.add(p);ArenaIntro.start(p,arena);click(p,0,false);stage=1;wait=2;return false;}
   if(stage==1){click(helpers.get(seat),1,false);stage=2;wait=2;return false;}
   if(stage==2){
    EntityPlayerMP p=helpers.get(seat);check(ArenaIntro.complete(p,arena),"Actual two-node END-settled introduction did not complete");
    JsonObject intro=new JsonObject();intro.addProperty("uuid",p.getUniqueID().toString());intro.addProperty("official_registered_body",server.getPlayerList().getPlayerByUUID(p.getUniqueID())==p);intro.addProperty("physical_two_nodes_complete",true);intros.add(intro);
    if(seat==0){
     p.setGameType(GameType.SPECTATOR);check(rejected(()->ArenaDirector.join(p,arena)),"Spectator joined nonfull normal party");p.setGameType(GameType.SURVIVAL);result.addProperty("spectator_rejected_before_capacity",true);
     EntityPlayerMP fake=FakePlayerFactory.get(world(),new GameProfile(UUID.randomUUID(),"HbvPartyFake"));check(rejected(()->ArenaDirector.join(fake,arena)),"Unregistered Forge fake player joined party");result.addProperty("fake_unregistered_body_rejected",true);
     wrongRun(p);
    }
    if(seat<3){ArenaDirector.join(p,arena);int size=run.getAsJsonObject("members").size();check(size==seat+2,"Normal party size did not grow from1 through4");sizes.add(size);check(contribution(p)==0,"Joining/proximity granted invented contribution");guide(p,arena.entry);}
    else{check(ArenaIntro.complete(p,arena)&&rejected(()->ArenaDirector.join(p,arena)),"Fifth legitimate introduced player bypassed four-person cap");check(run.getAsJsonObject("members").size()==4,"Cap rejection changed members");result.addProperty("valid_intro_fifth_player_cap_rejected",true);}
    seat++;if(seat<4){stage=0;return false;}near(helpers.get(1),arena.nodes[1]);stage=3;wait=3;return false;
   }
   if(stage==3){check(contribution(helpers.get(1))==0&&contribution(helpers.get(2))==0,"Proximity without accepted objective was credited");result.addProperty("actual_proximity_no_credit",true);objectivesBefore=objectives();click(helpers.get(1),2,true);stage=4;wait=2;return false;}
   if(stage==4){
    check(contribution(helpers.get(1))==0&&objectives()==objectivesBefore,"Final-canceled member interaction earned contribution");result.addProperty("actual_final_canceled_member_click_no_credit",true);
    oldBody=helpers.get(0);click(oldBody,0,false);GameProfile profile=oldBody.getGameProfile();server.getPlayerList().playerLoggedOut(oldBody);EntityPlayerMP reconnected=login(profile);helpers.set(0,reconnected);stage=5;wait=2;return false;
   }
   if(stage==5){
    EntityPlayerMP p=helpers.get(0);check(!ArenaDirector.eligible(oldBody,false),"Disconnected old body retained authority");check(ArenaIntro.complete(p,arena),"Real reconnect lost saved legitimate intro");
    check(contribution(p)==0&&objectives()==objectivesBefore,"Queued old-body contact survived logout/replacement connection");
    check(rejected(()->ArenaDirector.join(p,arena))&&run.getAsJsonObject("members").size()==4,"Reconnect duplicated existing membership");result.addProperty("actual_logout_relogin_stale_contact_rejected",true);result.addProperty("actual_reconnect_saved_intro_and_member_identity",true);
    click(p,0,false);stage=6;wait=2;return false;
   }
   if(stage==6){
    EntityPlayerMP p=helpers.get(0);check(contribution(p)==1&&objectives()==objectivesBefore+1,"Actual accepted physical objective did not credit exact current registered member once");
    result.addProperty("actual_reconnected_member_objective_credit",contribution(p));check(contribution(helpers.get(1))==0&&contribution(helpers.get(2))==0,"Other nearby members inherited objective credit");
    result.addProperty("four_actual_normal_members",true);result.addProperty("normal_intro_and_phase1_party_scope",true);success();return true;
   }
  }catch(Throwable error){failures.add(error.toString());result.addProperty("failed_stage",stage);result.addProperty("failed_seat",seat);result.addProperty("passed",false);cancel.armed=false;MinecraftForge.EVENT_BUS.unregister(cancel);
   // Keep failed run/member/actor records for inspection. Only disconnect the
   // newly registered private QA bodies; never fabricate an earned completion.
   for(EntityPlayerMP p:helpers)if(server.getPlayerList().getPlayerByUUID(p.getUniqueID())==p)server.getPlayerList().playerLoggedOut(p);done=true;return true;
  }
  return false;
 }
}
