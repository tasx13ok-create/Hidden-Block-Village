package village.vale.arenas;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;

/** Real registered-player, world-actor and Forge interaction checks. Private QA only. */
public final class ServicesNativeChecks {
 private static final String[] ROLES={"keeper","aid","stores"};
 private ServicesNativeChecks(){}
 private static void check(JsonArray failures,boolean accepted,String message){if(!accepted)failures.add(message);}
 private static JsonObject copy(JsonObject value){return new JsonParser().parse(value.toString()).getAsJsonObject();}
 private static List<EntityVillager> actors(WorldServer world,ArenaDirector.Definition d){
  List<EntityVillager> found=new ArrayList<>();
  for(Entity entity:new ArrayList<Entity>(world.loadedEntityList))if(entity instanceof EntityVillager&&entity.getEntityData().getString("HbvArenaService").startsWith(d.id+"/"))found.add((EntityVillager)entity);
  return found;
 }
 private static void load(WorldServer world,int[] at){for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)world.getChunk((at[0]>>4)+x,(at[2]>>4)+z);}
 private static void move(EntityPlayerMP p,Entity target){p.connection.setPlayerLocation(target.posX+2,target.posY,target.posZ,p.rotationYaw,p.rotationPitch);((WorldServer)p.world).getPlayerChunkMap().updateMovingPlayer(p);}
 private static NBTTagCompound saved(Entity entity){NBTTagCompound n=new NBTTagCompound();entity.writeToNBTOptional(n);return n;}

 public static JsonObject run(EntityPlayerMP player,ArenaDirector.Definition d){
  JsonObject result=new JsonObject();JsonArray failures=new JsonArray();result.add("failures",failures);result.addProperty("arena",d.id);
  result.addProperty("scope","REAL_NATIVE_SERVICE_ACTORS_AND_REGISTERED_PLAYER_INTERACTION; not encounter or imported-world acceptance");
  EntityVillager untagged=null;WorldServer world=(WorldServer)player.world;
  double x=player.posX,y=player.posY,z=player.posZ;float yaw=player.rotationYaw,pitch=player.rotationPitch,health=player.getHealth();
  JsonObject originalRun=null;boolean hadRun=false,runSnapshotTaken=false,timerSnapshotTaken=false,effectTouched=false;NBTBase oldTimer=null;String timer="campAid/"+d.id;
  PotionEffect oldBreathing=player.getActivePotionEffect(MobEffects.WATER_BREATHING);if(oldBreathing!=null)oldBreathing=new PotionEffect(oldBreathing);
  try{
   Path cwd=Paths.get("").toAbsolutePath().normalize();
   if(!Boolean.getBoolean("hbv.arenas.nativeProbe")||!Files.isRegularFile(cwd.resolve(".hbv-arenas-native-probe"))||!ArenaDirector.server.getFolderName().equals("arena-preview")||ArenaDirector.server.getPlayerList().getPlayerByUUID(player.getUniqueID())!=player||!ArenaDirector.server.isCallingFromMinecraftThread())throw new IllegalStateException("Service QA requires isolated official native player");
   if(d.services==null||d.services.length!=3)throw new IllegalStateException("Missing three service standing positions");
   for(int[] at:d.services){load(world,at);if(!ArenaDirector.safe(world,at))throw new IllegalStateException("Unsafe service actor landing");}
   if(!d.id.matches("[a-z0-9_]{1,100}"))throw new IllegalStateException("Unsafe private receipt ID");
   Path identity=cwd.resolve("PRIVATE-ARENA-SERVICES-"+d.id+".json");
   JsonObject earlier=Files.isRegularFile(identity)?new JsonParser().parse(new String(Files.readAllBytes(identity),StandardCharsets.UTF_8)).getAsJsonObject():null;
   if(earlier!=null&&!d.template_sha256.equals(earlier.get("template_sha256").getAsString()))throw new IllegalStateException("Native saved-service receipt belongs to another template");

   // A real same-class unrelated actor is deliberately present before ensure.
   // It receives no arena role tag and must never be adopted or rewritten.
   for(int dx:new int[]{4,-4,8,-8,12,-12}){
    int[] at={d.services[0][0]+dx,d.services[0][1],d.services[0][2]};
    if(d.contains(new BlockPos(at[0],at[1],at[2]))&&ArenaDirector.safe(world,at)){
     untagged=new EntityVillager(world);untagged.setNoAI(true);untagged.enablePersistence();untagged.setPosition(at[0]+.5,at[1],at[2]+.5);untagged.setCustomNameTag("Private unrelated-actor witness");
     if(!world.spawnEntity(untagged))throw new IllegalStateException("Unrelated native witness spawn rejected");break;
    }
   }
   if(untagged==null)throw new IllegalStateException("No safe unrelated-actor witness landing");
   UUID unrelatedUUID=untagged.getUniqueID();
   check(failures,ArenaServices.ensure(d),"Initial native service ensure refused");
   List<EntityVillager> actual=actors(world,d);
   check(failures,actual.size()==3,"Expected exactly three actual tagged service villagers");
   if(actual.size()!=3)throw new IllegalStateException("Cannot continue service ownership checks without exact three actors");
   JsonObject records=ArenaServices.records(),stable=new JsonObject();Map<String,EntityVillager> byRole=new HashMap<>();
   JsonArray snapshots=new JsonArray();
   for(String role:ROLES){
    String key=d.id+"/"+role;JsonObject row=records.getAsJsonObject(key);if(row==null)throw new IllegalStateException("Missing saved service role "+role);
    UUID uuid=UUID.fromString(ArenaDirector.text(row,"uuid"));Entity body=world.getEntityFromUuid(uuid);
    check(failures,body instanceof EntityVillager,"Saved role UUID is not an actual native villager");
    if(!(body instanceof EntityVillager))continue;EntityVillager actor=(EntityVillager)body;byRole.put(role,actor);stable.addProperty(role,uuid.toString());
    check(failures,!uuid.equals(unrelatedUUID),"Unrelated villager was adopted");
    check(failures,key.equals(actor.getEntityData().getString("HbvArenaService"))&&d.template_sha256.equals(actor.getEntityData().getString("HbvArenaTemplate")),"Actor role/template does not match saved authority");
    NBTTagCompound n=saved(actor);Entity loaded=EntityList.createEntityFromNBT(n,world);
    boolean persistent=n.getBoolean("NoAI")&&n.getBoolean("PersistenceRequired")&&n.getCompoundTag("ForgeData").getString("HbvArenaService").equals(key)&&n.getCompoundTag("ForgeData").getString("HbvArenaTemplate").equals(d.template_sha256);
    check(failures,persistent,"Native actor NBT lost NoAI/persistence/role/template");
    check(failures,loaded instanceof EntityVillager&&loaded.getUniqueID().equals(uuid)&&((EntityVillager)loaded).isAIDisabled()&&((EntityVillager)loaded).isNoDespawnRequired()&&key.equals(loaded.getEntityData().getString("HbvArenaService")),"Actual native entity NBT reconstruction failed");
    if(earlier!=null)check(failures,earlier.getAsJsonObject("role_uuids").get(role).getAsString().equals(uuid.toString()),"Role UUID changed across actual process save/restart");
    JsonObject snapshot=new JsonObject();snapshot.addProperty("role",role);snapshot.addProperty("uuid",uuid.toString());snapshot.addProperty("native_persistent_nbt",persistent);snapshot.addProperty("native_roundtrip",loaded instanceof EntityVillager&&loaded.getUniqueID().equals(uuid));snapshots.add(snapshot);
   }
   result.add("actual_actors",snapshots);result.addProperty("process_restart_identity",earlier==null?"BASELINE_WRITTEN_FIRST_BOOT":"PASS_OBSERVED_SAME_UUID_AFTER_PROCESS_RESTART");
   check(failures,ArenaServices.ensure(d),"Repeated ensure refused stable actors");
   check(failures,actors(world,d).size()==3,"Repeated ensure duplicated actual actors");
   for(String role:ROLES)check(failures,stable.get(role).getAsString().equals(ArenaServices.records().getAsJsonObject(d.id+"/"+role).get("uuid").getAsString()),"Repeated ensure replaced stable role UUID");
   result.addProperty("repeated_ensure_exact_three_stable",actors(world,d).size()==3);
   String keeperKey=d.id+"/keeper";JsonObject keeperRecord=copy(records.getAsJsonObject(keeperKey));
   try{
    JsonObject mismatch=copy(keeperRecord);mismatch.addProperty("template",d.template_sha256+"-mismatch");records.add(keeperKey,mismatch);
    boolean refused=!ArenaServices.ensure(d)&&actors(world,d).size()==3;
    check(failures,refused,"Template mismatch did not close without replacement");result.addProperty("template_mismatch_refused_no_duplicate",refused);
    JsonObject missing=copy(keeperRecord);missing.addProperty("uuid",UUID.randomUUID().toString());records.add(keeperKey,missing);
    boolean quarantined=!ArenaServices.ensure(d)&&actors(world,d).size()==3&&world.getEntityFromUuid(UUID.fromString(keeperRecord.get("uuid").getAsString()))==byRole.get("keeper");
    check(failures,quarantined,"Missing UUID spawned replacement or removed existing actor");result.addProperty("missing_uuid_refused_without_respawn",quarantined);
   }finally{records.add(keeperKey,keeperRecord);ArenaDirector.store().dirty();}
   check(failures,untagged.getEntityData().getString("HbvArenaService").isEmpty()&&untagged.getUniqueID().equals(unrelatedUUID),"Unrelated native witness was tagged or reidentified");
   result.addProperty("actual_untagged_villager_not_adopted",untagged.getEntityData().getString("HbvArenaService").isEmpty());

   // Dispatch through Forge's real interaction hook on the real registered body.
   hadRun=ArenaDirector.store().runs().has(d.id);if(hadRun)originalRun=copy(ArenaDirector.store().runs().getAsJsonObject(d.id));runSnapshotTaken=true;ArenaDirector.store().runs().remove(d.id);
   NBTTagCompound receipts=RewardOutbox.receipts(player);oldTimer=receipts.hasKey(timer)?receipts.getTag(timer).copy():null;timerSnapshotTaken=true;receipts.removeTag(timer);
   EntityVillager aid=byRole.get("aid");move(player,aid);player.setHealth(Math.max(1F,player.getMaxHealth()-10F));
   float before=player.getHealth();long now=world.getTotalWorldTime();
   effectTouched=true;player.addPotionEffect(new PotionEffect(MobEffects.WATER_BREATHING,1200,2));PotionEffect effectBeforeAid=new PotionEffect(player.getActivePotionEffect(MobEffects.WATER_BREATHING));
   boolean handled=ForgeHooks.onInteractEntity(player,aid,EnumHand.MAIN_HAND)!=null;
   float expected=Math.min(player.getMaxHealth(),before+6F);boolean healed=handled&&Math.abs(player.getHealth()-expected)<.001F;
   check(failures,healed,"Real native aid interaction did not heal exactly six within max health");result.addProperty("actual_forge_aid_heal",healed);
   check(failures,receipts.getLong(timer)==now+300,"Aid cooldown was not recorded as300ticks");
   PotionEffect breathing=player.getActivePotionEffect(MobEffects.WATER_BREATHING);check(failures,breathing!=null&&breathing.getAmplifier()>=effectBeforeAid.getAmplifier()&&breathing.getDuration()>=effectBeforeAid.getDuration(),"Aid altered stronger existing potion");
   File playerFile=ArenaDirector.server.getFile(ArenaDirector.server.getFolderName()+"/playerdata/"+player.getUniqueID()+".dat");
   NBTTagCompound onDisk;try(InputStream input=new FileInputStream(playerFile)){onDisk=CompressedStreamTools.readCompressed(input);}
   long savedCooldown=onDisk.getCompoundTag("ForgeData").getCompoundTag("PlayerPersisted").getCompoundTag("HbvArenaReceipts").getLong(timer);
   check(failures,savedCooldown==now+300,"Actual player NBT save lacks durable aid cooldown");result.addProperty("aid_cooldown_native_player_save",savedCooldown==now+300);
   player.setHealth(before);ForgeHooks.onInteractEntity(player,aid,EnumHand.MAIN_HAND);
   check(failures,Math.abs(player.getHealth()-before)<.001F,"Aid cooldown allowed duplicate immediate heal");result.addProperty("aid_cooldown_blocks_immediate_repeat",Math.abs(player.getHealth()-before)<.001F);
   receipts.removeTag(timer);JsonObject activeRun=new JsonObject(),members=new JsonObject();members.add(player.getUniqueID().toString(),new JsonObject());activeRun.add("members",members);activeRun.addProperty("state","PHASE1");ArenaDirector.store().runs().add(d.id,activeRun);
   ForgeHooks.onInteractEntity(player,aid,EnumHand.MAIN_HAND);boolean blocked=Math.abs(player.getHealth()-before)<.001F&&!receipts.hasKey(timer);check(failures,blocked,"Aid healed active saved-run member");result.addProperty("aid_active_saved_run_guard",blocked);ArenaDirector.store().runs().remove(d.id);
   move(player,byRole.get("keeper"));boolean keeper=ForgeHooks.onInteractEntity(player,byRole.get("keeper"),EnumHand.MAIN_HAND)!=null;
   move(player,byRole.get("stores"));boolean stores=ForgeHooks.onInteractEntity(player,byRole.get("stores"),EnumHand.MAIN_HAND)!=null;
   check(failures,keeper&&stores,"Keeper/stores interaction did not route through actual Forge hook");result.addProperty("keeper_stores_actual_interaction_routed",keeper&&stores);
   move(player,untagged);boolean untouched=ForgeHooks.onInteractEntity(player,untagged,EnumHand.MAIN_HAND)==null;
   check(failures,untouched,"Arena services intercepted unrelated native villager");result.addProperty("untagged_actual_interaction_not_intercepted",untouched);
   if(earlier==null&&failures.size()==0){JsonObject baseline=new JsonObject();baseline.addProperty("schema",1);baseline.addProperty("template_sha256",d.template_sha256);baseline.add("role_uuids",stable);Files.write(identity,(ArenaDirector.JSON.toJson(baseline)+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);}
  }catch(Throwable error){failures.add(error.toString());}
  finally{
   if(untagged!=null&&untagged.getEntityData().getString("HbvArenaService").isEmpty())world.removeEntityDangerously(untagged);
   try{
    if(runSnapshotTaken){if(hadRun)ArenaDirector.store().runs().add(d.id,originalRun);else ArenaDirector.store().runs().remove(d.id);ArenaDirector.store().dirty();}
    if(timerSnapshotTaken){NBTTagCompound receipts=RewardOutbox.receipts(player);if(oldTimer==null)receipts.removeTag(timer);else receipts.setTag(timer,oldTimer);}
    if(effectTouched){player.removePotionEffect(MobEffects.WATER_BREATHING);if(oldBreathing!=null)player.addPotionEffect(oldBreathing);}
    player.setHealth(health);player.connection.setPlayerLocation(x,y,z,yaw,pitch);world.getPlayerChunkMap().updateMovingPlayer(player);RewardOutbox.flush(player);
   }catch(Throwable cleanup){failures.add("Service fixture cleanup: "+cleanup);}
  }
  result.addProperty("passed",failures.size()==0);return result;
 }
}
