package village.vale.arenas;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.eventhandler.*;

/** Private asynchronous native attack/terminal diagnostics. The authentic attack
 * API is exercised with the officially registered synthetic player. No event is
 * posted as a substitute for damage, no boss health/paid contribution is assigned,
 * and native direct XP is read separately from Forge experience-orb hooks.
 * This is not a physical-client, weapon-range or dodge gameplay acceptance test.
 */
public final class CombatNativeChecks {
 private static final String[] VARIANTS={"FINAL_CANCELLATION","WRONG_RUN_NATIVE_CONTROL","EXACT_PREVIEW","TRUSTED_NORMAL"};
 private static MinecraftServer server;private static EntityPlayerMP player;
 private static List<ArenaDirector.Definition> definitions;private static ArenaDirector.Definition d;
 private static JsonObject report,row,variant,run,attempt;private static JsonArray rows,variants,failures,attempts;
 private static int index,stage,variantIndex,age,wait,total,bodyStart;private static boolean done,trust,inAttempt,cancelAttack,cancelDamage,cancelDeath;
 private static EntityLivingBase body;private static EntityZombie control;private static String savedRun;
 private static NBTTagList savedInventory;private static GameType savedMode;private static double savedX,savedY,savedZ,beforeXp;
 private static final Observer observer=new Observer();private static final List<LivingAttackEvent> attacks=new ArrayList<>();
 private static final List<LivingHurtEvent> hurts=new ArrayList<>();private static final List<LivingDamageEvent> damages=new ArrayList<>();
 private static final List<LivingDeathEvent> deaths=new ArrayList<>();private static final List<Loot> loot=new ArrayList<>();
 private static Set<UUID> baseline;private static double beforeContribution;private static float beforeHealth,beforeAbsorption;
 private CombatNativeChecks(){}

 public static void begin(MinecraftServer s,List<ArenaDirector.Definition> all,EntityPlayerMP p){
  if(!Boolean.getBoolean("hbv.arenas.nativeProbe")||!Files.isRegularFile(Paths.get(".hbv-arenas-native-probe"))||!s.getFolderName().equals("arena-preview")||s.getPlayerList().getPlayerByUUID(p.getUniqueID())!=p)throw new IllegalStateException("Private combat guard rejected");
  if(p instanceof net.minecraftforge.common.util.FakePlayer)throw new IllegalStateException("Unregistered/fake combat body refused");
  server=s;player=p;definitions=new ArrayList<>(all);index=stage=variantIndex=age=wait=total=0;done=false;
  report=new JsonObject();rows=new JsonArray();report.add("arenas",rows);
  report.addProperty("scope","AUTHENTIC_NATIVE_DAMAGE_CALLBACKS_AND_LOOT_ON_REGISTERED_GUIDED_SYNTHETIC_BODY");
  report.addProperty("physical_client_weapon_range_or_dodge_acceptance",false);
  report.addProperty("boss_health_or_paid_contribution_assigned",false);
  report.addProperty("production_terminal_policy_promoted",false);
  savedInventory=p.inventory.writeToNBT(new NBTTagList());savedMode=p.interactionManager.getGameType();savedX=p.posX;savedY=p.posY;savedZ=p.posZ;
  MinecraftForge.EVENT_BUS.register(observer);
 }
 public static JsonObject result(){return report;}
 private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
 private static void warm(BlockPos p){WorldServer w=server.getWorld(0);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)require(w.getChunkProvider().isChunkGeneratedAt((p.getX()>>4)+x,(p.getZ()>>4)+z),"Combat prewarm refused ungenerated neighbor");NativeProbe.prewarm(w,p);}
 private static void guide(int[] at){warm(new BlockPos(at[0],at[1],at[2]));require(ArenaDirector.safe(server.getWorld(0),at),"Combat guidance landing blocked");player.connection.setPlayerLocation(at[0]+.5,at[1],at[2]+.5,player.rotationYaw,player.rotationPitch);player.fallDistance=0;server.getWorld(0).getPlayerChunkMap().updateMovingPlayer(player);}
 private static double battleXp(){try{return ((Number)Class.forName("net.narutomod.PlayerTracker").getMethod("getBattleXp",net.minecraft.entity.player.EntityPlayer.class).invoke(null,player)).doubleValue();}catch(ReflectiveOperationException unavailable){return Double.NaN;}}
 private static double contribution(){JsonObject members=run.getAsJsonObject("members");return members.has(player.getUniqueID().toString())?members.getAsJsonObject(player.getUniqueID().toString()).get("contribution").getAsDouble():0;}
 private static String state(){return ArenaDirector.text(run,"state");}
 private static void beginVariant(){
  player.setGameType(variantIndex==3?GameType.SURVIVAL:GameType.CREATIVE);player.setHealth(player.getMaxHealth());
  warm(new BlockPos(d.boss_anchor[0],d.boss_anchor[1],d.boss_anchor[2]));
  JsonObject previous=ArenaDirector.run(d);require(previous==null||ArenaDirector.text(previous,"state").equals("AVAILABLE"),"Combat requires genuinely empty reset instance");
  if(variantIndex==3)require(ArenaIntro.complete(player,d),"Real saved physical introduction is unavailable; normal terminal test not run");
  d.terminal_verified=variantIndex==3;ArenaDirector.admit(player,d,variantIndex!=3);run=ArenaDirector.run(d);
  Entity actual=server.getWorld(0).getEntityFromUuid(UUID.fromString(ArenaDirector.text(run,"boss_uuid")));
  require(actual instanceof EntityLivingBase,"Exact admitted native body missing");body=(EntityLivingBase)actual;bodyStart=body.ticksExisted;savedRun=body.getEntityData().getString("HbvArenaRun");
  boolean approach=false;for(int[] offset:new int[][]{{12,0},{-12,0},{0,12},{0,-12}}){int[] at={d.boss_anchor[0]+offset[0],d.floor,d.boss_anchor[2]+offset[1]};if(ArenaDirector.safe(server.getWorld(0),at)){guide(at);approach=true;break;}}require(approach,"No supported native activation approach");
  variant=new JsonObject();attempts=new JsonArray();variant.addProperty("kind",VARIANTS[variantIndex]);variant.add("attempts",attempts);variants.add(variant);
  variant.addProperty("run_uuid",ArenaDirector.text(run,"run"));variant.addProperty("native_uuid",body.getUniqueID().toString());variant.addProperty("native_class",body.getClass().getName());variant.addProperty("native_class_sha256",ArenaTerminals.classHash(body));
  variant.addProperty("native_maximum",body.getMaxHealth());variant.addProperty("native_ai_disabled",body instanceof EntityLiving&&((EntityLiving)body).isAIDisabled());
  baseline=new HashSet<>();for(Entity e:server.getWorld(0).loadedEntityList)if(d.contains(e.getPosition()))baseline.add(e.getUniqueID());
  loot.clear();age=0;stage=10;wait=22;
 }
 private static void attack(String name,DamageSource source,float amount,boolean ca,boolean cd,boolean ce){
  attacks.clear();hurts.clear();damages.clear();deaths.clear();cancelAttack=ca;cancelDamage=cd;cancelDeath=ce;
  beforeHealth=body.getHealth();beforeAbsorption=body.getAbsorptionAmount();beforeXp=battleXp();beforeContribution=contribution();
  attempt=new JsonObject();attempt.addProperty("name",name);attempt.addProperty("source",source.damageType);attempt.addProperty("amount",amount);attempt.addProperty("registered_true_source",source.getTrueSource()==player);attempt.addProperty("health_before",beforeHealth);attempt.addProperty("absorption_before",beforeAbsorption);
  inAttempt=true;try{attempt.addProperty("native_return",body.attackEntityFrom(source,amount));}finally{inAttempt=false;cancelAttack=cancelDamage=cancelDeath=false;}
  double inline=battleXp();if(Double.isFinite(beforeXp)&&Double.isFinite(inline))attempt.addProperty("native_battle_xp_delta_immediate_unwind",inline-beforeXp);
  attempts.add(attempt);
 }
 private static JsonObject event(String name,boolean canceled,float amount){JsonObject r=new JsonObject();r.addProperty("event",name);r.addProperty("final_cancelled",canceled);r.addProperty("amount",amount);return r;}
 private static void settleAttempt(){
  JsonArray events=new JsonArray();for(LivingAttackEvent e:attacks)events.add(event("LivingAttackEvent",e.isCanceled(),e.getAmount()));for(LivingHurtEvent e:hurts)events.add(event("LivingHurtEvent",e.isCanceled(),e.getAmount()));for(LivingDamageEvent e:damages)events.add(event("LivingDamageEvent",e.isCanceled(),e.getAmount()));for(LivingDeathEvent e:deaths)events.add(event("LivingDeathEvent",e.isCanceled(),0));attempt.add("actual_final_event_references",events);
  attempt.addProperty("health_after_native_unwind",body.getHealth());attempt.addProperty("absorption_after_native_unwind",body.getAbsorptionAmount());attempt.addProperty("actual_accepted_total_loss",Math.max(0,beforeHealth-body.getHealth())+Math.max(0,beforeAbsorption-body.getAbsorptionAmount()));
  double afterXp=battleXp();if(Double.isFinite(beforeXp)&&Double.isFinite(afterXp))attempt.addProperty("native_battle_xp_delta",afterXp-beforeXp);else attempt.addProperty("native_battle_xp","NOT_RUN_PUBLIC_READ_UNAVAILABLE");
  attempt.addProperty("arena_contribution_delta",contribution()-beforeContribution);attempt.addProperty("director_state_after_unwind",state());
  String name=attempt.get("name").getAsString();
  if(name.equals("cancel_attack")){if(attacks.isEmpty())attempt.addProperty("status","NOT_RUN_NATIVE_PROVIDER_REJECTED_BEFORE_FORGE_ATTACK");else{require(attacks.get(attacks.size()-1).isCanceled(),"Final attack cancellation not retained");require(body.getHealth()==beforeHealth&&body.getAbsorptionAmount()==beforeAbsorption,"Canceled native attack settled health/absorption loss");require(contribution()==beforeContribution,"Canceled native attack awarded contribution");attempt.addProperty("status","PASS");}}
  else if(name.equals("cancel_damage")){if(damages.isEmpty())attempt.addProperty("status","NOT_RUN_NATIVE_DAMAGE_EVENT_NOT_REACHED");else{require(damages.get(damages.size()-1).isCanceled(),"Final damage cancellation not retained");require(contribution()==beforeContribution,"Canceled native damage awarded contribution");attempt.addProperty("status","PASS");}}
  else if(name.equals("cancel_death")){if(deaths.isEmpty())attempt.addProperty("status","NOT_RUN_NATIVE_LETHAL_CONTACT_REJECTED_OR_REVIVED");else{require(deaths.get(deaths.size()-1).isCanceled(),"Final death cancellation not retained");require(!state().equals("REWARD_PENDING"),"Canceled native death issued terminal entitlement");require(contribution()==beforeContribution,"Canceled native death issued contribution");attempt.addProperty("status","PASS");}}
  else attempt.addProperty("status",events.size()>0?"OBSERVED_NATIVE_PIPELINE":"NOT_RUN_PROVIDER_REJECTED_BEFORE_FORGE_CALLBACKS");
 }
 private static void wrongUuidControl(){
  WorldServer w=server.getWorld(0);control=new EntityZombie(w);control.setPosition(d.boss_anchor[0]+18.5,d.floor,d.boss_anchor[2]+.5);control.enablePersistence();control.getEntityData().setString("HbvArenaRun",ArenaDirector.text(run,"run"));control.getEntityData().setString("HbvArenaId",d.id);
  require(w.spawnEntity(control),"Unrelated native zombie control spawn rejected");variant.addProperty("wrong_uuid_native_control",control.getUniqueID().toString());
  String before=state();control.attackEntityFrom(DamageSource.causePlayerDamage(player),1000F);
  require(!ArenaDirector.DEATHS.containsKey(ArenaDirector.text(run,"run")),"Wrong UUID living body was accepted as this run's native terminal");require(state().equals(before),"Wrong UUID body changed native director terminal state");variant.addProperty("wrong_uuid_terminal_rejected",true);
 }
 private static void endVariant(){
  variant.addProperty("actual_body_ticks_advanced",body.ticksExisted-bodyStart);variant.addProperty("native_body_present",server.getWorld(0).getEntityFromUuid(body.getUniqueID())==body);variant.addProperty("native_body_dead",body.isDead);variant.addProperty("native_body_health",body.getHealth());variant.addProperty("director_terminal_state",state());
  require(body.ticksExisted>bodyStart,"Native combat body did not actually advance entity ticks");
  boolean acceptedDeath=false,acceptedPlayerDeath=false;for(LivingDeathEvent e:deaths){acceptedDeath|=!e.isCanceled()&&e.getEntityLiving()==body;acceptedPlayerDeath|=!e.isCanceled()&&e.getEntityLiving()==body&&e.getSource().getTrueSource()==player;}
  variant.addProperty("actual_uncanceled_death_event",acceptedDeath);JsonArray actors=new JsonArray();
  for(Entity e:server.getWorld(0).loadedEntityList)if(d.contains(e.getPosition())&&!e.isDead&&e instanceof EntityLivingBase&&!(e instanceof net.minecraft.entity.player.EntityPlayer)&&!baseline.contains(e.getUniqueID())&&!ArenaTerminals.service(d,e)&&e!=control){JsonObject a=new JsonObject();a.addProperty("uuid",e.getUniqueID().toString());a.addProperty("class",e.getClass().getName());actors.add(a);}
  variant.add("new_or_replacement_living_actors",actors);
  JsonArray nativeLoot=new JsonArray();for(Loot l:loot){JsonObject r=new JsonObject();r.addProperty("entity",l.entity.getUniqueID().toString());r.addProperty("kind",l.drop!=null?"LivingDropsEvent":"LivingExperienceDropEvent");r.addProperty("before_arena_lowest",l.before);r.addProperty("after_all_callbacks",l.drop!=null?l.drop.getDrops().size():l.xp.getDroppedExperience());boolean exact=l.entity==body&&variantIndex!=1;
   if(l.entity==control||variantIndex==1){require(l.before==(l.drop!=null?l.drop.getDrops().size():l.xp.getDroppedExperience()),"Arena preview suppression changed unrelated/wrong-run native loot control");r.addProperty("control_unchanged",true);}else if(exact&&variantIndex==2){require((l.drop!=null?l.drop.getDrops().size():l.xp.getDroppedExperience())==0,"Exact run-owned preview leaked native Forge XP or drops");r.addProperty("preview_suppressed",true);}nativeLoot.add(r);}
  variant.add("actual_native_drop_xp_events",nativeLoot);if(nativeLoot.size()==0)variant.addProperty("native_drop_xp_scope","NOT_RUN_PROVIDER_EMITTED_NO_LOOT_EVENT");
  if(variantIndex==1){require(!ArenaDirector.DEATHS.containsKey(ArenaDirector.text(run,"run")),"Wrong run tag accepted terminal callback");require(!state().equals("REWARD_PENDING"),"Wrong run source issued terminal entitlement");variant.addProperty("wrong_run_terminal_rejected",true);variant.addProperty("wrong_run_native_terminal_scope",acceptedDeath?"PASS_ACTUAL_DEATH_IGNORED":"NOT_RUN_NATIVE_BODY_EMITTED_NO_ACCEPTED_DEATH");}
  if(variantIndex==2){require(!state().equals("REWARD_PENDING"),"Preview native death issued earned entitlement");variant.addProperty("preview_earned_entitlement_absent",true);for(JsonElement element:attempts){JsonObject a=element.getAsJsonObject();if(a.has("native_battle_xp_delta_immediate_unwind")&&a.get("native_battle_xp_delta_immediate_unwind").getAsDouble()>0)failures.add("Exact owned preview awarded native battleXP inside authentic attack unwind");}}
  if(variantIndex==3){boolean edo=d.id.contains("forest")||d.id.contains("fire");boolean complete=acceptedPlayerDeath&&body.isDead&&server.getWorld(0).getEntityFromUuid(body.getUniqueID())==null&&actors.size()==0&&state().equals("REWARD_PENDING");variant.addProperty("actual_uncanceled_player_death",acceptedPlayerDeath);variant.addProperty("genuine_native_terminal_flow",complete);variant.addProperty("native_terminal_scope",complete?"PASS":edo?"NOT_RUN_EDO_NATIVE_PLAYER_DAMAGE_TERMINAL_NOT_ESTABLISHED":"FAIL_LIVING_NATIVE_PLAYER_TERMINAL_NOT_ESTABLISHED");if(!edo&&!complete)failures.add("Living native boss terminal/revival flow unavailable: "+d.boss);}
  // Restore only the tag changed on this exact QA-owned body, after retaining
  // wrong-run callback evidence. Unknown/replacement actors are never deleted.
  if(variantIndex==1)body.getEntityData().setString("HbvArenaRun",savedRun);
  guide(d.exit);ArenaDirector.exit(player,d,run);ArenaDirector.reset(d,run);require(state().equals("AVAILABLE"),"Combat variant empty reset failed");
  if(control!=null&&!control.isDead)control.setDead();control=null;body=null;variantIndex++;stage=0;wait=3;
 }
 private static void finishArena(){d.terminal_verified=trust;row.addProperty("passed",failures.size()==0);rows.add(row);d=null;index++;variantIndex=stage=total=0;}
 private static void finish(){cancelAttack=cancelDamage=cancelDeath=inAttempt=false;MinecraftForge.EVENT_BUS.unregister(observer);if(d!=null)d.terminal_verified=trust;player.inventory.readFromNBT(savedInventory);player.setGameType(savedMode);player.connection.setPlayerLocation(savedX,savedY,savedZ,player.rotationYaw,player.rotationPitch);RewardOutbox.flush(player);boolean pass=rows.size()==definitions.size();for(JsonElement e:rows)pass&=e.getAsJsonObject().get("passed").getAsBoolean();report.addProperty("passed",pass);report.addProperty("physical_client_or_dodge_acceptance",false);report.addProperty("process_restart_during_native_lethal",false);done=true;}
 public static boolean tick(){
  if(done)return true;if(d!=null){total++;age++;}if(--wait>0)return false;
  if(index>=definitions.size()){finish();return true;}
  if(d==null){d=definitions.get(index);trust=d.terminal_verified;row=new JsonObject();variants=new JsonArray();failures=new JsonArray();row.addProperty("id",d.id);row.add("variants",variants);row.add("failures",failures);total=0;}
  try{
   require(total<1200,"Bounded native combat budget1200ticks exceeded");require(server.getPlayerList().getPlayerByUUID(player.getUniqueID())==player,"Registered synthetic combat identity changed");require(player.isEntityAlive(),"Synthetic participant died; no automatic fake respawn");
   if(body!=null)warm(body.getPosition());
   if(variantIndex>=VARIANTS.length){finishArena();return false;}
   if(stage==0){beginVariant();return false;}
   if(stage==10){if(variantIndex==0){attack("cancel_attack",DamageSource.causePlayerDamage(player),1F,true,false,false);stage=11;wait=2;return false;}if(variantIndex==1)body.getEntityData().setString("HbvArenaRun",UUID.randomUUID().toString());if(variantIndex==3){attack("native_high_power_phase_skip",DamageSource.causePlayerDamage(player),Math.max(1,body.getMaxHealth()*.75F),false,false,false);stage=19;wait=22;return false;}attack("native_player_lethal",DamageSource.causePlayerDamage(player),Math.min(1000000F,body.getMaxHealth()*4+100),false,false,false);stage=30;wait=180;return false;}
   if(stage==11){settleAttempt();stage=12;wait=22;return false;}
   if(stage==12){attack("cancel_damage",DamageSource.causePlayerDamage(player),1F,false,true,false);stage=13;wait=2;return false;}
   if(stage==13){settleAttempt();stage=14;wait=22;return false;}
   if(stage==14){wrongUuidControl();attack("unrelated_world_source",DamageSource.GENERIC,1F,false,false,false);stage=15;wait=25;return false;}
   if(stage==15){settleAttempt();require(contribution()==beforeContribution,"Unrelated world source credited player contribution");attack("cancel_death",DamageSource.causePlayerDamage(player),Math.min(1000000F,body.getMaxHealth()*4+100),false,false,true);stage=16;wait=2;return false;}
   if(stage==16){settleAttempt();endVariant();return false;}
   if(stage==19){settleAttempt();variant.addProperty("native_health_phase_observed",ArenaDirector.number(run,"phase"));variant.addProperty("genuine_phase_skip_to3",ArenaDirector.number(run,"phase")>=3);if(body.getHealth()<=0||body.isDead){stage=30;wait=180;return false;}attack("native_player_lethal",DamageSource.causePlayerDamage(player),Math.min(1000000F,body.getMaxHealth()*4+100),false,false,false);stage=30;wait=180;return false;}
   if(stage==30){settleAttempt();if((d.id.contains("forest")||d.id.contains("fire"))&&body.isEntityAlive()&&variantIndex==3){attack("edo_administrative_source_diagnostic_NOT_NORMAL_VICTORY",DamageSource.OUT_OF_WORLD,1000000F,false,false,false);stage=31;wait=180;return false;}endVariant();return false;}
   if(stage==31){settleAttempt();variant.addProperty("administrative_source_is_normal_player_terminal",false);endVariant();return false;}
  }catch(Throwable error){failures.add(error.toString());row.addProperty("failed_stage",stage);row.addProperty("failed_variant",variantIndex);if(run!=null)row.addProperty("retained_run_state",state());cancelAttack=cancelDamage=cancelDeath=inAttempt=false;player.setGameType(GameType.CREATIVE);if(d!=null){try{guide(d.exit);}catch(Throwable ignored){}}finishArena();wait=3;}
  return false;
 }
 private static final class Loot {Entity entity;LivingDropsEvent drop;LivingExperienceDropEvent xp;int before;}
 public static final class Observer {
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void attack(LivingAttackEvent e){if(inAttempt&&e.getEntityLiving()==body){attacks.add(e);if(cancelAttack)e.setCanceled(true);}}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void hurt(LivingHurtEvent e){if(inAttempt&&e.getEntityLiving()==body)hurts.add(e);}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void damage(LivingDamageEvent e){if(inAttempt&&e.getEntityLiving()==body){damages.add(e);if(cancelDamage)e.setCanceled(true);}}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void death(LivingDeathEvent e){if(inAttempt&&e.getEntityLiving()==body){deaths.add(e);if(cancelDeath)e.setCanceled(true);}}
  @SubscribeEvent(priority=EventPriority.HIGH,receiveCanceled=true)public void drops(LivingDropsEvent e){if(e.getEntityLiving()==body||e.getEntityLiving()==control){Loot l=new Loot();l.entity=e.getEntityLiving();l.drop=e;l.before=e.getDrops().size();loot.add(l);}}
  @SubscribeEvent(priority=EventPriority.HIGH)public void xp(LivingExperienceDropEvent e){if(e.getEntityLiving()==body||e.getEntityLiving()==control){Loot l=new Loot();l.entity=e.getEntityLiving();l.xp=e;l.before=e.getDroppedExperience();loot.add(l);}}
 }
}
