package village.vale.arenas;

import com.google.gson.*;
import net.minecraft.entity.player.EntityPlayerMP;

/** Completed entitlements survive leaving, instance reset, and later runs. */
final class RewardArchive {
 private RewardArchive(){}
 static JsonObject records(){ArenaDirector.Store s=ArenaDirector.store();if(s.readOnly)throw new IllegalStateException("Read-only future state");if(!s.data.has("reward_archive")){s.data.add("reward_archive",new JsonObject());s.dirty();}return s.data.getAsJsonObject("reward_archive");}
 static JsonObject copy(JsonObject value){return new JsonParser().parse(value.toString()).getAsJsonObject();}
 static boolean retain(JsonObject run,String owner){
  if(run.get("preview").getAsBoolean()||!ArenaDirector.text(run,"state").equals("REWARD_PENDING"))return true;
  JsonObject archive=records();String id=ArenaDirector.text(run,"run");if(!archive.has(id)&&archive.size()>=512)return false;
  JsonObject saved=archive.has(id)?archive.getAsJsonObject(id):copy(run);saved.getAsJsonObject("members").add(owner,copy(run.getAsJsonObject("members").getAsJsonObject(owner)));archive.add(id,saved);ArenaDirector.store().dirty();ArenaDirector.server.getWorld(0).getPerWorldStorage().saveAllData();return true;
 }
 static JsonObject pending(EntityPlayerMP p){String owner=p.getUniqueID().toString();for(String id:ArenaDirector.keys(records())){JsonObject run=records().getAsJsonObject(id);if(!run.getAsJsonObject("members").has(owner))continue;JsonObject member=run.getAsJsonObject("members").getAsJsonObject(owner);if(!ArenaDirector.text(member,"outbox").equals("DELIVERED")&&member.get("earned").getAsBoolean()&&member.get("contribution").getAsDouble()>0)return run;}return null;}
}
