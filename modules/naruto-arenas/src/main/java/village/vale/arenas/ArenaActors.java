package village.vale.arenas;

import com.google.gson.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.world.WorldServer;

/** Hash-bound native Ay allies. A nearby class alone never establishes ownership. */
final class ArenaActors {
 private ArenaActors(){}
 private static final Map<Class<?>,Boolean> PROVIDERS=new HashMap<>();
 private static boolean provider(ArenaDirector.Definition d,Entity boss){Boolean old=PROVIDERS.get(boss.getClass());if(old!=null)return old;boolean accepted=NativeBossPolicies.identity(d);PROVIDERS.put(boss.getClass(),accepted);return accepted;}
 private static final String[][] TYPES={
  {"net.mcreator.kabutoaddon.entity.EntityDarui$EntityCustom","43c67142f2485fb111026976a0b2d11642a40bbdade8c5ecee2187d56802bca8"},
  {"net.mcreator.kabutoaddon.entity.EntityC$EntityCustom","9a6b46937e9b9e42bd3f3e53de51572ad897136c9a45a31e56e3d3250c71be7e"}
 };
 private static boolean nativeType(Entity e){for(String[] row:TYPES)if(row[0].equals(e.getClass().getName()))return row[1].equals(ArenaTerminals.classHash(e));return false;}
 private static boolean baseline(JsonObject run,Entity e){if(run.has("baseline_actor_uuids"))for(JsonElement id:run.getAsJsonArray("baseline_actor_uuids"))if(id.getAsString().equals(e.getUniqueID().toString()))return true;return false;}
 private static JsonObject records(JsonObject r){if(!r.has("owned_native_allies"))r.add("owned_native_allies",new JsonObject());return r.getAsJsonObject("owned_native_allies");}
 static void observe(ArenaDirector.Definition d,JsonObject run,Entity boss){
  if(!d.boss.equals("kabutoaddon:ay")||boss==null||!boss.isEntityAlive()||!ArenaDirector.text(run,"boss_uuid").equals(boss.getUniqueID().toString())||!ArenaDirector.text(run,"run").equals(boss.getEntityData().getString("HbvArenaRun"))||!provider(d,boss))return;
  JsonObject owned=records(run);WorldServer world=ArenaDirector.server.getWorld(0);
  for(Entity actor:new ArrayList<>(world.loadedEntityList)){
   if(actor.isDead||!d.contains(actor.getPosition())||!nativeType(actor)||baseline(run,actor))continue;
   try{if(actor.getClass().getMethod("getLeader").invoke(actor)!=boss)continue;}catch(ReflectiveOperationException invalid){continue;}
   // The inspected provider sets public leader=this before spawnTeam joins the world.
   // Its adoption of a pre-existing baseline ally is deliberately excluded above.
   try{if(!java.nio.file.Files.isSameFile(NativeBossPolicies.providerFile(actor.getClass()),NativeBossPolicies.providerFile(boss.getClass())))continue;}catch(Exception invalid){continue;}
   String id=actor.getUniqueID().toString();if(!owned.has(id)&&owned.size()>=16)throw new IllegalStateException("Native ally budget exceeded");
   JsonObject row=owned.has(id)?owned.getAsJsonObject(id):new JsonObject();row.addProperty("class",actor.getClass().getName());row.addProperty("class_sha256",ArenaTerminals.classHash(actor));row.addProperty("leader_uuid",boss.getUniqueID().toString());row.addProperty("run",ArenaDirector.text(run,"run"));row.addProperty("x",actor.getPosition().getX());row.addProperty("y",actor.getPosition().getY());row.addProperty("z",actor.getPosition().getZ());row.addProperty("status","OBSERVED_PUBLIC_LEADER");owned.add(id,row);
   actor.getEntityData().setString("HbvArenaAllyRun",ArenaDirector.text(run,"run"));actor.getEntityData().setString("HbvArenaAllyTemplate",d.template_sha256);actor.getEntityData().setString("HbvArenaAllyLeader",boss.getUniqueID().toString());ArenaDirector.store().dirty();
  }
 }
 static boolean cleanup(ArenaDirector.Definition d,JsonObject run){
  if(!run.has("owned_native_allies"))return true;WorldServer world=ArenaDirector.server.getWorld(0);JsonObject rows=records(run);
  // Persist ownership and removal intent before removing any exact actor.
  for(String id:ArenaDirector.keys(rows))rows.getAsJsonObject(id).addProperty("status","REMOVAL_PLANNED");ArenaDirector.store().dirty();world.getPerWorldStorage().saveAllData();
  for(String id:ArenaDirector.keys(rows)){
   JsonObject row=rows.getAsJsonObject(id);Entity actor=world.getEntityFromUuid(UUID.fromString(id));
   if(actor==null){if(!world.isBlockLoaded(new net.minecraft.util.math.BlockPos(ArenaDirector.number(row,"x"),ArenaDirector.number(row,"y"),ArenaDirector.number(row,"z"))))return false;continue;}
   if(baseline(run,actor)||!nativeType(actor)||!ArenaDirector.text(row,"class_sha256").equals(ArenaTerminals.classHash(actor))||!ArenaDirector.text(run,"run").equals(actor.getEntityData().getString("HbvArenaAllyRun"))||!d.template_sha256.equals(actor.getEntityData().getString("HbvArenaAllyTemplate"))||!ArenaDirector.text(run,"boss_uuid").equals(actor.getEntityData().getString("HbvArenaAllyLeader")))return false;
   try{Object leader=actor.getClass().getMethod("getLeader").invoke(actor);if(leader instanceof Entity&&!ArenaDirector.text(run,"boss_uuid").equals(((Entity)leader).getUniqueID().toString()))return false;}catch(ReflectiveOperationException invalid){return false;}
   actor.setDead();row.addProperty("status","EXACT_OWNED_ACTOR_REMOVED");ArenaDirector.store().dirty();
  }return true;
 }
}
