package village.vale.arenas;

import com.google.gson.*;
import java.io.*;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.world.GameType;

/** Private QA only. Exercises real inventory/player saves, not an earned boss clear. */
final class OutboxNativeChecks {
 private static JsonArray failures;
 private static int assertions;
 private static void check(boolean value,String description){assertions++;if(!value)failures.add(description);}
 private static JsonObject runRecord(EntityPlayerMP p,String id,boolean preview){
  JsonObject r=new JsonObject();r.addProperty("run",id);r.addProperty("arena","arena_forest_root_coliseum_v1");
  r.addProperty("preview",preview);r.addProperty("state","REWARD_PENDING");r.add("members",new JsonObject());
  JsonObject m=new JsonObject();m.addProperty("earned",true);m.addProperty("contribution",1D);m.addProperty("outbox","PENDING");
  r.getAsJsonObject("members").add(p.getUniqueID().toString(),m);return r;
 }
 private static int receiptStacks(EntityPlayerMP p,String token){
  int n=0;for(ItemStack stack:p.inventory.mainInventory)if(!stack.isEmpty()&&stack.hasTagCompound()&&token.equals(stack.getTagCompound().getString("HbvArenaRewardReceipt")))n++;
  return n;
 }
 private static boolean rejected(EntityPlayerMP p,JsonObject r,int choice){
  try{RewardOutbox.deliver(p,r,choice);return false;}catch(IllegalArgumentException|IllegalStateException expected){return true;}
 }
 private static NBTTagCompound disk(EntityPlayerMP p)throws IOException{
  File file=new File(new File(p.getServer().getWorld(0).getSaveHandler().getWorldDirectory(),"playerdata"),p.getUniqueID()+".dat");
  try(InputStream in=new FileInputStream(file)){return CompressedStreamTools.readCompressed(in);}
 }
 /** Actual two-process test. Boot1 saves a full-inventory pending case; boot2
  * must load that exact world/player record before delivery, then cleans QA. */
 static JsonObject runRestartCase(EntityPlayerMP p){
  JsonObject result=new JsonObject();JsonArray problems=new JsonArray();
  String storeKey="PRIVATE_QA_OUTBOX_RESTART_V1",tagKey="HbvArenaQaOutboxRestart";
  ArenaDirector.Store s=ArenaDirector.store();int iteration=Integer.getInteger("hbv.arenas.probeIteration",1);
  try{
   if(iteration==1){
    if(s.data.has(storeKey)||p.getEntityData().hasKey(tagKey))throw new IllegalStateException("Prior QA restart record exists in a new candidate");
    NBTTagCompound qa=new NBTTagCompound();qa.setTag("OriginalInventory",p.inventory.writeToNBT(new NBTTagList()));
    qa.setInteger("OriginalMode",p.interactionManager.getGameType().getID());
    qa.setBoolean("HadPersisted",p.getEntityData().hasKey("PlayerPersisted",10));
    qa.setTag("OriginalPersisted",p.getEntityData().getCompoundTag("PlayerPersisted").copy());
    qa.setString("Run",UUID.randomUUID().toString());p.getEntityData().setTag(tagKey,qa);
    p.setGameType(GameType.SURVIVAL);
    for(int i=0;i<p.inventory.mainInventory.size();i++)p.inventory.mainInventory.set(i,new ItemStack(Blocks.COBBLESTONE,64));
    JsonObject r=runRecord(p,qa.getString("Run"),false);s.data.add(storeKey,r);s.dirty();
    if(RewardOutbox.deliver(p,r,0))problems.add("Full inventory unexpectedly delivered first boot");
    p.getServer().getWorld(0).getPerWorldStorage().saveAllData();RewardOutbox.flush(p);
    NBTTagCompound saved=disk(p);
    if(!saved.getCompoundTag("ForgeData").hasKey(tagKey,10))problems.add("Restart marker absent from actual player file");
    if(p.inventory.getFirstEmptyStack()!=-1)problems.add("Full inventory precondition lost");
    result.addProperty("phase","PENDING_SAVED_FIRST_PROCESS");
   }else{
    if(!s.data.has(storeKey)||!p.getEntityData().hasKey(tagKey,10))throw new IllegalStateException("Actual pending world/player record did not survive process restart");
    NBTTagCompound qa=p.getEntityData().getCompoundTag(tagKey);JsonObject r=s.data.getAsJsonObject(storeKey);
    if(!qa.getString("Run").equals(ArenaDirector.text(r,"run")))problems.add("World/player pending identities differ");
    if(p.inventory.getFirstEmptyStack()!=-1)problems.add("Saved full inventory did not load on official login");
    p.setGameType(GameType.SURVIVAL);p.inventory.mainInventory.set(5,ItemStack.EMPTY);
    String token=qa.getString("Run")+":"+p.getUniqueID();
    if(!RewardOutbox.deliver(p,r,0))problems.add("Pending reward not delivered after actual restart");
    NBTTagList before=p.inventory.writeToNBT(new NBTTagList());
    if(!RewardOutbox.deliver(p,r,1)||!before.equals(p.inventory.writeToNBT(new NBTTagList()))||receiptStacks(p,token)!=1)problems.add("Post-restart replay was not exactly once");
    NBTTagCompound saved=disk(p);
    if(!saved.getCompoundTag("ForgeData").getCompoundTag("PlayerPersisted").getCompoundTag("HbvArenaReceipts").hasKey(token))problems.add("Delivered receipt absent from actual second-process player file");
    p.inventory.readFromNBT(qa.getTagList("OriginalInventory",10));
    if(qa.getBoolean("HadPersisted"))p.getEntityData().setTag("PlayerPersisted",qa.getCompoundTag("OriginalPersisted").copy());else p.getEntityData().removeTag("PlayerPersisted");
    p.setGameType(GameType.getByID(qa.getInteger("OriginalMode")));p.getEntityData().removeTag(tagKey);
    s.data.remove(storeKey);s.dirty();p.getServer().getWorld(0).getPerWorldStorage().saveAllData();RewardOutbox.flush(p);
    result.addProperty("phase","PENDING_LOADED_AND_DELIVERED_ONCE_SECOND_PROCESS");
   }
  }catch(Throwable error){problems.add(error.toString());}
  result.addProperty("iteration",iteration);result.addProperty("actual_earned_boss_clear",false);
  result.addProperty("scope","REAL_PROCESS_RESTART_PENDING_WORLD_AND_PLAYER_SAVE");
  result.add("failures",problems);result.addProperty("passed",problems.size()==0);return result;
 }
 static JsonObject run(EntityPlayerMP p){
  failures=new JsonArray();assertions=0;JsonObject result=new JsonObject();
  String key="private_outbox_qa_"+UUID.randomUUID();String token=key+":"+p.getUniqueID();
  NBTTagList originalInventory=p.inventory.writeToNBT(new NBTTagList());
  boolean hadPersisted=p.getEntityData().hasKey("PlayerPersisted",10);
  NBTTagCompound originalPersisted=p.getEntityData().getCompoundTag("PlayerPersisted").copy();
  GameType originalMode=p.interactionManager.getGameType();
  ArenaDirector.Store store=ArenaDirector.store();
  try{
   p.setGameType(GameType.SURVIVAL);
   check(p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())==p,"official registered synthetic body precondition");
   check(ArenaDirector.eligible(p,false),"normal synthetic participant precondition");
   for(int i=0;i<p.inventory.mainInventory.size();i++)p.inventory.mainInventory.set(i,new ItemStack(Blocks.COBBLESTONE,64));
   JsonObject record=runRecord(p,key,false);
   NBTTagList fullBefore=p.inventory.writeToNBT(new NBTTagList());
   check(!RewardOutbox.deliver(p,record,0),"full inventory retains undelivered entitlement");
   check(fullBefore.equals(p.inventory.writeToNBT(new NBTTagList())),"full inventory never overwrites or drops existing stacks");
   check(!RewardOutbox.receipts(p).hasKey(token),"full inventory writes no paid receipt");
   check("PENDING_FULL_INVENTORY".equals(record.getAsJsonObject("members").getAsJsonObject(p.getUniqueID().toString()).get("outbox").getAsString()),"full-inventory pending state persists");
   // A fresh saved-data object is a serialization boundary; this is not claimed
   // to be an actual process restart. The harness separately boots twice.
   ArenaDirector.Store codec=new ArenaDirector.Store();codec.runs().add(key,record);
   NBTTagCompound saved=codec.writeToNBT(new NBTTagCompound());
   ArenaDirector.Store reload=new ArenaDirector.Store();reload.readFromNBT(saved);
   check(reload.runs().getAsJsonObject(key).equals(record),"pending outbox survives WorldSavedData NBT roundtrip");
   p.inventory.mainInventory.set(5,ItemStack.EMPTY);
   check(RewardOutbox.deliver(p,record,0),"one freed slot accepts exactly one native reward");
   check(receiptStacks(p,token)==1,"one delivered item stack carries exact run-player token");
   check(RewardOutbox.receipts(p).hasKey(token),"player receipt stored with delivered inventory");
   NBTTagCompound persisted=disk(p);
   NBTTagCompound playerReceipts=persisted.getCompoundTag("ForgeData").getCompoundTag("PlayerPersisted").getCompoundTag("HbvArenaReceipts");
   check(playerReceipts.hasKey(token),"actual player NBT file contains authoritative receipt");
   int diskItems=0;NBTTagList inventory=persisted.getTagList("Inventory",10);
   for(int i=0;i<inventory.tagCount();i++)if(token.equals(inventory.getCompoundTagAt(i).getCompoundTag("tag").getString("HbvArenaRewardReceipt")))diskItems++;
   check(diskItems==1,"same actual player save contains one receipt item");
   NBTTagList beforeReplay=p.inventory.writeToNBT(new NBTTagList());
   check(RewardOutbox.deliver(p,record,1),"changed-choice replay reconciles the delivered receipt");
   check(beforeReplay.equals(p.inventory.writeToNBT(new NBTTagList())),"replay cannot issue a second or changed reward");
   check(receiptStacks(p,token)==1,"replay preserves exactly one stack");
   JsonObject wrongParty=runRecord(p,key+"wrong",false);wrongParty.getAsJsonObject("members").remove(p.getUniqueID().toString());
   check(rejected(p,wrongParty,0),"wrong-party reward rejected");
   check(rejected(p,runRecord(p,key+"preview",true),0),"unrated preview cannot award earned items");
   JsonObject unearned=runRecord(p,key+"unearned",false);unearned.getAsJsonObject("members").getAsJsonObject(p.getUniqueID().toString()).addProperty("earned",false);
   check(rejected(p,unearned,0),"unearned member cannot claim");
   JsonObject zero=runRecord(p,key+"zero",false);zero.getAsJsonObject("members").getAsJsonObject(p.getUniqueID().toString()).addProperty("contribution",0D);
   check(rejected(p,zero,0),"zero-contribution member cannot claim");
   JsonObject active=runRecord(p,key+"active",false);active.addProperty("state","PHASE3");
   check(rejected(p,active,0),"active phase cannot issue terminal reward");
   check(rejected(p,runRecord(p,key+"choice",false),2),"unsupported choice rejected without inventory mutation");
   p.setGameType(GameType.CREATIVE);
   check(rejected(p,runRecord(p,key+"creative",false),0),"creative transition cannot claim earned reward");
   p.setGameType(GameType.SPECTATOR);
   check(rejected(p,runRecord(p,key+"spectator",false),0),"spectator transition cannot claim earned reward");
   p.setGameType(GameType.SURVIVAL);
   String[] arenas={"forest","cloud","sand","mist","fire"};JsonArray choices=new JsonArray();
   for(String arena:arenas)for(int choice=0;choice<2;choice++){
    ItemStack item=RewardOutbox.choice("arena_"+arena+"_qa",choice);
    check(!item.isEmpty()&&item.getItem().getRegistryName()!=null,"actual reward registry exists "+arena+" "+choice);
    JsonObject row=new JsonObject();row.addProperty("arena",arena);row.addProperty("choice",choice);row.addProperty("registry",item.getItem().getRegistryName().toString());row.addProperty("count",item.getCount());choices.add(row);
   }
   result.add("actual_reward_items",choices);
  }catch(Throwable error){failures.add("unexpected native outbox exception: "+error.toString());}
  finally{
   p.inventory.readFromNBT(originalInventory);
   if(hadPersisted)p.getEntityData().setTag("PlayerPersisted",originalPersisted);else p.getEntityData().removeTag("PlayerPersisted");
   p.setGameType(originalMode);RewardOutbox.flush(p);
  }
  result.addProperty("scope","PRIVATE_SYNTHETIC_ENTITLEMENT_REAL_NATIVE_INVENTORY_AND_PLAYER_FILE");
  result.addProperty("actual_earned_boss_clear",false);result.addProperty("process_restart_during_pending_tested",false);
  result.addProperty("assertions",assertions);result.add("failures",failures);result.addProperty("passed",failures.size()==0);
  return result;
 }
}
