package village.vale.arenas;

import com.google.gson.*;
import net.minecraft.entity.player.EntityPlayerMP;
import village.vale.Gear;
import village.vale.RewardLedger;

/** Uses Core's real serial/ownership and durable offline outbox; never forges gear tags. */
final class CoreRewardOutbox {
 private CoreRewardOutbox(){}
 static String[] bundle(String arena){String order,weapon;if(arena.contains("forest")){order="bloom";weapon="thorn_staff";}else if(arena.contains("cloud")){order="storm";weapon="thunder_edge";}else if(arena.contains("sand")){order="flint";weapon="seal_charge";}else if(arena.contains("mist")){order="current";weapon="crescent_guard";}else{order="ember";weapon="overdrive_blade";}return new String[]{order+"_helmet",order+"_chestplate",order+"_leggings",order+"_boots",weapon};}
 static boolean enqueue(EntityPlayerMP p,JsonObject run){
  if(!ArenaDirector.eligible(p,false))throw new IllegalStateException("Living registered survival participant required");
  JsonObject member=run.getAsJsonObject("members").getAsJsonObject(p.getUniqueID().toString());if(member==null)throw new IllegalStateException("Wrong party");
  if(member.has("reward_mode")&&!member.get("reward_mode").getAsString().equals("core"))throw new IllegalStateException("Reward mode already selected");
  if(!RunRules.paid(run.get("preview").getAsBoolean(),member.get("earned").getAsBoolean(),member.get("contribution").getAsDouble(),ArenaDirector.text(run,"state").equals("REWARD_PENDING"),false))throw new IllegalStateException("No earned terminal entitlement");
  String[] keys=bundle(ArenaDirector.text(run,"arena"));for(String key:keys)if(!Gear.ITEMS.containsKey(key)||Gear.ITEMS.get(key).getRegistryName()==null)throw new IllegalStateException("Actual Core reward key absent: "+key);
  member.addProperty("reward_mode","core");member.addProperty("outbox","CORE_PENDING");ArenaDirector.store().dirty();p.getServer().getWorld(0).getPerWorldStorage().saveAllData();
  RewardLedger ledger=RewardLedger.get(p);String prefix="narutoarenas/"+ArenaDirector.text(run,"run")+"/"+p.getUniqueID()+"/";
  for(String key:keys)ledger.issue(p,key,1,prefix+key);
  boolean delivered=ledger.pendingCount(p.getUniqueID())==0;member.addProperty("outbox",delivered?"DELIVERED":"CORE_PENDING");ArenaDirector.store().dirty();return delivered;
 }
}
