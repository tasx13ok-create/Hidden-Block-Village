package village.vale.arenas;

import com.google.gson.JsonObject;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import village.vale.Gear;
import village.vale.Order;
import village.vale.RewardLedger;

/** Optional normal-practice equipment. Separate from boss rewards and native
 * awakening; only Core's public ledger creates ownership/serial data. */
public final class ArenaStarterKits {
 private static final String NAME="naruto_arena_starters_v1";
 private static final String[] PARTS={"helmet","chestplate","leggings","boots","saber"};
 private static final int MAX_CLAIMS=8192;
 private ArenaStarterKits(){}

 /** Existing registered Ward orders, never arbitrary item IDs. */
 public static String[] list(){List<String> result=new ArrayList<>();for(Order order:Order.values())if(available(order.key))result.add(order.key);return result.toArray(new String[0]);}
 private static boolean available(String order){for(String part:PARTS){net.minecraft.item.Item item=Gear.ITEMS.get(order+"_"+part);if(item==null||item.getRegistryName()==null)return false;}return true;}
 private static String order(String input){if(input==null||input.length()>16)throw new IllegalArgumentException("Choose a listed Ward kit");String key=input.trim().toLowerCase(Locale.ROOT);for(Order order:Order.values())if(order.key.equals(key)&&available(key))return key;throw new IllegalArgumentException("That Ward kit is not available");}
 private static void eligible(EntityPlayerMP p){
  if(ArenaDirector.server==null||!ArenaDirector.server.isCallingFromMinecraftThread()||p==null||!ArenaDirector.eligible(p,false))throw new IllegalStateException("A living registered survival player is required");
  if(ArenaDirector.store().readOnly)throw new IllegalStateException("Arena save data needs a compatible version");
  for(ArenaDirector.Definition d:ArenaDirector.DEFINITIONS){JsonObject run=ArenaDirector.run(d);if(run!=null&&run.getAsJsonObject("members").has(p.getUniqueID().toString()))throw new IllegalStateException("Leave your current arena run or preview before choosing a starter kit");}
  NBTTagCompound persisted=p.getEntityData().getCompoundTag("PlayerPersisted");
  if(!persisted.hasKey("HbvArenaIntro",10))throw new IllegalStateException("Complete the two physical practice seals first");
  boolean completed=false;for(ArenaDirector.Definition d:ArenaDirector.DEFINITIONS)if(ArenaIntro.complete(p,d))completed=true;
  if(!completed)throw new IllegalStateException("Complete the two physical practice seals first");
 }
 private static WorldServer world(){return ArenaDirector.server.getWorld(0);}
 static Store store(){WorldServer w=world();Store s=(Store)w.getPerWorldStorage().getOrLoadData(Store.class,NAME);if(s==null){s=new Store();w.getPerWorldStorage().setData(NAME,s);}return s;}
 private static void save(Store s){if(s.readOnly)throw new IllegalStateException("Future starter records are preserved read-only");s.markDirty();world().getPerWorldStorage().saveAllData();}

 /** One permanent UUID choice across every arena. Retry resumes the same five
  * public Core receipts, including after partial issuance or process restart. */
 public static JsonObject request(EntityPlayerMP player,String selectedOrder){
  eligible(player);String selected=order(selectedOrder);Store state=store();
  if(state.readOnly)throw new IllegalStateException("Future starter records are preserved read-only");
  String owner=player.getUniqueID().toString();NBTTagCompound row;boolean fresh=false;
  if(state.claims.hasKey(owner,10)){
   row=state.claims.getCompoundTag(owner);
   if(!row.getString("order").equals(selected))throw new IllegalStateException("Your one-time starter kit is already chosen; retry that same Ward");
  }else{
   if(state.claims.getKeySet().size()>=MAX_CLAIMS)throw new IllegalStateException("Starter receipt capacity reached; existing records were retained");
   row=new NBTTagCompound();row.setInteger("schema",1);row.setString("owner",owner);row.setString("order",selected);row.setString("status","REGISTERING");row.setLong("created",world().getTotalWorldTime());state.claims.setTag(owner,row);fresh=true;
   // A durable choice precedes any public Core grant. A retry cannot select a
   // different item after one part of the fixed kit has already been issued.
   save(state);
  }
  RewardLedger ledger=RewardLedger.get(player);
  for(String part:PARTS)ledger.issue(world(),player.getUniqueID(),selected+"_"+part,1,"narutoarenas/starter/v1/"+owner+"/"+part);
  row.setString("status","REGISTERED");save(state);
  ledger.deliver(player);
  int pending=ledger.pendingCount(player.getUniqueID());
  JsonObject result=new JsonObject();result.addProperty("order",selected);result.addProperty("new_choice",fresh);result.addProperty("fixed_kit_items",5);result.addProperty("permanent_choice",true);
  result.addProperty("status",pending==0?"CORE_ACKNOWLEDGED":"CORE_PENDING");
  result.addProperty("core_pending_units_for_owner",pending);
  result.addProperty("pending_scope","Owner's public Core queue, which may include other rewards; acknowledgement does not replace lost or consumed gear");
  result.addProperty("boss_clear_reward",false);return result;
 }

 /** Bounded independent saved data. Unknown roots/row fields retain their NBT
  * types; unknown schemas or malformed records are not silently rewritten. */
 public static final class Store extends WorldSavedData {
  NBTTagCompound opaque=new NBTTagCompound(),claims=new NBTTagCompound();boolean readOnly;
  public Store(){this(NAME);}public Store(String name){super(name);}
  @Override public void readFromNBT(NBTTagCompound n){
   opaque=n.copy();claims=new NBTTagCompound();readOnly=false;
   try{
    if(!n.hasKey("Schema",99)||n.getInteger("Schema")!=1||!n.hasKey("Claims",10)){readOnly=true;return;}
    NBTTagCompound loaded=n.getCompoundTag("Claims");if(loaded.getKeySet().size()>MAX_CLAIMS){readOnly=true;return;}
    for(String owner:loaded.getKeySet()){
     if(!UUID.fromString(owner).toString().equals(owner)||!loaded.hasKey(owner,10)){readOnly=true;return;}
     NBTTagCompound row=loaded.getCompoundTag(owner);String selected=row.getString("order"),status=row.getString("status");boolean known=false;for(Order value:Order.values())if(value.key.equals(selected))known=true;
     if(row.getInteger("schema")!=1||!owner.equals(row.getString("owner"))||!known||!(status.equals("REGISTERING")||status.equals("REGISTERED"))){readOnly=true;return;}
    }
    claims=loaded.copy();
   }catch(RuntimeException invalid){readOnly=true;}
  }
  @Override public NBTTagCompound writeToNBT(NBTTagCompound target){target.merge(opaque.copy());if(!readOnly){target.setInteger("Schema",1);target.setTag("Claims",claims.copy());}return target;}
 }
}
