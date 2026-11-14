package village.vale.arenas;

import com.google.gson.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;

/** Explicit original sealing ending. A seal is not a native death or XP award. */
final class ArenaTerminals {
 private ArenaTerminals(){}
 static final Map<Class<?>,String> HASHES=new HashMap<>();
 static String classHash(Entity entity){String old=HASHES.get(entity.getClass());if(old!=null)return old;try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=entity.getClass().getResourceAsStream("/"+entity.getClass().getName().replace('.','/')+".class")){if(input==null)return "";byte[] buffer=new byte[16384];int n;while((n=input.read(buffer))>0)digest.update(buffer,0,n);}StringBuilder result=new StringBuilder();for(byte b:digest.digest())result.append(String.format("%02x",b&255));String value=result.toString();HASHES.put(entity.getClass(),value);return value;}catch(Exception ex){return "";}}
 static void recordBaseline(ArenaDirector.Definition d,JsonObject run,WorldServer w){JsonArray ids=new JsonArray();for(Entity entity:w.loadedEntityList)if(d.contains(entity.getPosition())&&!(entity instanceof EntityPlayer)){if(ids.size()>=128)throw new IllegalStateException("Arena actor baseline budget exceeded");ids.add(entity.getUniqueID().toString());}run.add("baseline_actor_uuids",ids);}
 static boolean allSeals(JsonObject run){if(!run.has("objectives"))return false;JsonObject o=run.getAsJsonObject("objectives");for(int phase=1;phase<=3;phase++){int count=0;for(String key:ArenaDirector.keys(o))if(key.startsWith(phase+":"))count++;if(count<2)return false;}return true;}
 static boolean service(ArenaDirector.Definition d,Entity entity){String key=entity.getEntityData().getString("HbvArenaService");if(!key.startsWith(d.id+"/")||!d.template_sha256.equals(entity.getEntityData().getString("HbvArenaTemplate"))||!(entity instanceof net.minecraft.entity.passive.EntityVillager))return false;JsonObject records=ArenaServices.records();return records.has(key)&&entity.getUniqueID().toString().equals(ArenaDirector.text(records.getAsJsonObject(key),"uuid"))&&d.template_sha256.equals(ArenaDirector.text(records.getAsJsonObject(key),"template"));}
 static Entity ambiguous(ArenaDirector.Definition d,JsonObject run){Set<String> baseline=new HashSet<>();if(run.has("baseline_actor_uuids"))for(JsonElement id:run.getAsJsonArray("baseline_actor_uuids"))baseline.add(id.getAsString());for(Entity entity:ArenaDirector.server.getWorld(0).loadedEntityList){if(!d.contains(entity.getPosition())||entity instanceof EntityPlayer||entity.isDead||service(d,entity))continue;String id=entity.getUniqueID().toString();if(id.equals(ArenaDirector.text(run,"boss_uuid"))||(!baseline.contains(id)&&entity instanceof EntityLivingBase))return entity;}return null;}
 static void begin(ArenaDirector.Definition d,JsonObject run){
  if(!allSeals(run)||!ArenaDirector.text(run,"state").equals("PHASE3"))throw new IllegalStateException("Three actual seal sequences required");
  WorldServer w=ArenaDirector.server.getWorld(0);Entity body=w.getEntityFromUuid(UUID.fromString(ArenaDirector.text(run,"boss_uuid")));
  if(body==null||body.isDead||!ArenaDirector.text(run,"run").equals(body.getEntityData().getString("HbvArenaRun"))||!d.id.equals(body.getEntityData().getString("HbvArenaId")))throw new IllegalStateException("Exact active owned boss required for sealing");
  ArenaActors.observe(d,run,body);
  if(!ArenaDirector.restore(d,run)){ArenaDirector.state(run,"SAFE_CLOSED");return;}
  run.addProperty("terminal_kind","ORIGINAL_ARENA_SEAL");run.addProperty("sealed_class",body.getClass().getName());run.addProperty("sealed_class_sha256",classHash(body));run.addProperty("seal_observation_ticks",0);run.addProperty("native_death_claimed",false);ArenaDirector.state(run,"SEALING_OBSERVE");
  // Only this run's exact authenticated body. No vessel/global manager setters.
  if(!ArenaActors.cleanup(d,run)){ArenaDirector.state(run,"SAFE_CLOSED");return;}
  body.setDead();ArenaDirector.broadcast(run,"Three seals complete. The arena seal is binding its owned boss. This ending is an original arena adaptation; it does not award native death XP.");
 }
 static void tick(ArenaDirector.Definition d,JsonObject run){
  WorldServer w=ArenaDirector.server.getWorld(0);BlockPos center=new BlockPos(d.boss_anchor[0],d.boss_anchor[1],d.boss_anchor[2]);
  // Observation requires the court and its immediate neighborhood still loaded.
  for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(w.getChunkProvider().getLoadedChunk((center.getX()>>4)+x,(center.getZ()>>4)+z)==null){run.addProperty("seal_observation_ticks",0);ArenaDirector.store().dirty();return;}
  Set<String> baseline=new HashSet<>();for(JsonElement id:run.getAsJsonArray("baseline_actor_uuids"))baseline.add(id.getAsString());
  for(Entity entity:w.loadedEntityList){if(!d.contains(entity.getPosition())||entity instanceof EntityPlayer||entity.isDead||service(d,entity))continue;String id=entity.getUniqueID().toString();if(id.equals(ArenaDirector.text(run,"boss_uuid"))||(!baseline.contains(id)&&entity instanceof EntityLivingBase)){run.addProperty("ambiguous_actor_uuid",id);run.addProperty("ambiguous_actor_class",entity.getClass().getName());ArenaDirector.state(run,"SAFE_CLOSED");ArenaDirector.broadcast(run,"A surviving or replacement actor makes the seal ambiguous. No reward was issued; the actor was preserved for review.");return;}}
  int ticks=ArenaDirector.number(run,"seal_observation_ticks")+1;run.addProperty("seal_observation_ticks",ticks);if(ticks<200){if(ticks%20==0)ArenaDirector.store().dirty();return;}
  if(run.get("preview").getAsBoolean()){ArenaDirector.state(run,"PREVIEW_CLEARED");ArenaDirector.broadcast(run,"Preview sealing complete after200loaded-world ticks. No earned item, native death XP or first-clear receipt was issued. Use exit, then empty reset for replay.");}
  else if(d.terminal_verified){ArenaDirector.state(run,"REWARD_PENDING");ArenaDirector.broadcast(run,"Arena seal complete. Choose a supported reward at the reward scene, then take the short exit.");}
  else {ArenaDirector.state(run,"SAFE_CLOSED");ArenaDirector.broadcast(run,"Sealing prototype observed; version-specific earned acceptance is pending. No reward issued.");}
 }
}
