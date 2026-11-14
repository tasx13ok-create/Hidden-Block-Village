package village.vale.arenas;

import com.google.gson.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.world.GameType;
import village.vale.Gear;
import village.vale.RewardLedger;

/** Private real-Core outbox/serial tests; synthetic entitlement, not a boss clear. */
final class CoreRewardNativeChecks {
 private static JsonArray failures;
 private static int count;
 private static void check(boolean value,String name){count++;if(!value)failures.add(name);}
 private static JsonObject record(EntityPlayerMP p,String arena){
  JsonObject r=new JsonObject();r.addProperty("run","core-qa-"+UUID.randomUUID());r.addProperty("arena",arena);
  r.addProperty("preview",false);r.addProperty("state","REWARD_PENDING");r.add("members",new JsonObject());
  JsonObject m=new JsonObject();m.addProperty("earned",true);m.addProperty("contribution",1D);m.addProperty("outbox","PENDING");
  r.getAsJsonObject("members").add(p.getUniqueID().toString(),m);return r;
 }
 static JsonObject run(EntityPlayerMP p){
  failures=new JsonArray();count=0;JsonArray bundles=new JsonArray();JsonObject result=new JsonObject();
  NBTTagList original=p.inventory.writeToNBT(new NBTTagList());GameType mode=p.interactionManager.getGameType();
  try{
   p.setGameType(GameType.SURVIVAL);RewardLedger ledger=RewardLedger.get(p);
   check(ledger.pendingCount(p.getUniqueID())==0,"private test player starts without unrelated pending grants");
   for(String theme:new String[]{"forest","cloud","sand","mist","fire"}){
    String arena="arena_"+theme+"_qa";String[] keys=CoreRewardOutbox.bundle(arena);
    check(keys.length==5,"four armor pieces plus one weapon "+theme);
    for(String key:keys)check(Gear.ITEMS.containsKey(key),"actual Core registered reward "+key);
    for(int i=0;i<p.inventory.mainInventory.size();i++)p.inventory.mainInventory.set(i,new ItemStack(Blocks.COBBLESTONE,64));
    NBTTagList full=p.inventory.writeToNBT(new NBTTagList());JsonObject r=record(p,arena);
    check(!CoreRewardOutbox.enqueue(p,r),"full inventory defers Core bundle "+theme);
    check(ledger.pendingCount(p.getUniqueID())==5,"five actual ledger pending entries "+theme);
    check(full.equals(p.inventory.writeToNBT(new NBTTagList())),"Core never overwrites full inventory "+theme);
    check(!CoreRewardOutbox.enqueue(p,r)&&ledger.pendingCount(p.getUniqueID())==5,"pending replay does not duplicate queue "+theme);
    for(int i=0;i<5;i++)p.inventory.mainInventory.set(i,ItemStack.EMPTY);
    check(CoreRewardOutbox.enqueue(p,r),"free slots deliver all five Core pieces "+theme);
    check(ledger.pendingCount(p.getUniqueID())==0,"Core pending queue drains "+theme);
    Set<String> expected=new HashSet<>(Arrays.asList(keys)),seen=new HashSet<>(),serials=new HashSet<>();
    for(ItemStack stack:p.inventory.mainInventory){
     if(stack.isEmpty()||stack.getItem().getRegistryName()==null)continue;
     String key=stack.getItem().getRegistryName().getPath();if(!expected.contains(key))continue;
     seen.add(key);check(ledger.valid(p,stack),"genuine owner-bound Core serial "+key);
     check(stack.hasTagCompound()&&!stack.getTagCompound().getString("valeSerial").isEmpty(),"issued serial present "+key);
     if(stack.hasTagCompound())serials.add(stack.getTagCompound().getString("valeSerial"));
    }
    check(seen.equals(expected)&&serials.size()==5,"exact bundle with five unique Core serials "+theme);
    NBTTagList delivered=p.inventory.writeToNBT(new NBTTagList());
    check(CoreRewardOutbox.enqueue(p,r)&&delivered.equals(p.inventory.writeToNBT(new NBTTagList())),"delivered replay inserts no extra gear "+theme);
    boolean modeRejected=false;try{RewardOutbox.deliver(p,r,0);}catch(IllegalStateException expectedFailure){modeRejected=true;}
    check(modeRejected&&delivered.equals(p.inventory.writeToNBT(new NBTTagList())),"native choice cannot double-claim Core-selected reward "+theme);
    JsonObject detail=new JsonObject();detail.addProperty("arena",theme);detail.add("actual_keys",ArenaDirector.JSON.toJsonTree(keys));detail.addProperty("genuine_serials",serials.size());bundles.add(detail);
   }
   p.setGameType(GameType.CREATIVE);boolean denied=false;
   try{CoreRewardOutbox.enqueue(p,record(p,"arena_fire_qa"));}catch(IllegalStateException expected){denied=true;}
   check(denied,"creative transition cannot enqueue owned gear");
   p.setGameType(GameType.SURVIVAL);JsonObject preview=record(p,"arena_fire_qa");preview.addProperty("preview",true);denied=false;
   try{CoreRewardOutbox.enqueue(p,preview);}catch(IllegalStateException expected){denied=true;}
   check(denied,"unrated preview cannot enqueue owned gear");
  }catch(Throwable error){failures.add("unexpected Core native test failure: "+error.toString());}
  finally{p.inventory.readFromNBT(original);p.setGameType(mode);RewardOutbox.flush(p);}
  result.addProperty("scope","ACTUAL_CORE_PUBLIC_LEDGER_AND_NATIVE_INVENTORY_SYNTHETIC_ENTITLEMENT");
  result.addProperty("actual_earned_clear",false);result.addProperty("power_inputs_tested",false);
  result.addProperty("assertions",count);result.add("bundles",bundles);result.add("failures",failures);result.addProperty("passed",failures.size()==0);return result;
 }
}
