package village.vale.arsenal;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;

/** World-owned medical input claims prevent traded/cloned donor eyes being spent twice. */
public final class ScrollJournal extends WorldSavedData {
 private NBTTagCompound claims=new NBTTagCompound(),spent=new NBTTagCompound(),companions=new NBTTagCompound();
 public ScrollJournal(){super("vale_original_scroll_journal");}
 public ScrollJournal(String name){super(name);}
 static ScrollJournal get(EntityPlayerMP p){return get(p.getServerWorld());}
 static ScrollJournal get(WorldServer world){WorldServer w=world.getMinecraftServer().getWorld(0);ScrollJournal j=(ScrollJournal)w.getMapStorage().getOrLoadData(ScrollJournal.class,"vale_original_scroll_journal");if(j==null){j=new ScrollJournal();w.getMapStorage().setData("vale_original_scroll_journal",j);}return j;}
 boolean spent(String serial){return serial.isEmpty()||spent.getBoolean(serial);}
 boolean claim(EntityPlayerMP p,String a,String b){if(a.isEmpty()||b.isEmpty()||a.equals(b)||spent(a)||spent(b))return false;String owner=p.getUniqueID().toString();String ca=claims.getString(a),cb=claims.getString(b);String pair=owner+"/"+(a.compareTo(b)<0?a+"/"+b:b+"/"+a);if((!ca.isEmpty()&&!ca.equals(pair))||(!cb.isEmpty()&&!cb.equals(pair)))return false;claims.setString(a,pair);claims.setString(b,pair);flush(p);return true;}
 void finish(EntityPlayerMP p,String a,String b){spent.setBoolean(a,true);spent.setBoolean(b,true);flush(p);}
 NBTTagCompound companion(String serial){if(serial.isEmpty())throw new IllegalArgumentException("Missing issuance serial");if(!companions.hasKey(serial,10)){NBTTagCompound row=new NBTTagCompound();row.setFloat("health",30);row.setString("order",ScrollCompanionOrders.Order.ASSIST.key);companions.setTag(serial,row);}return companions.getCompoundTag(serial);}
 /** A stale actor cannot overwrite the current deployment's saved health. */
 boolean recordCompanion(String serial,UUID actor,float health,boolean closed){
  if(serial==null||serial.isEmpty()||actor==null||!Float.isFinite(health))return false;
  if(!companions.hasKey(serial,10))return false;
  NBTTagCompound row=companions.getCompoundTag(serial);
  if(!row.getBoolean("deployed")||!row.hasUniqueId("actor")||!actor.equals(row.getUniqueId("actor")))return false;
  row.setFloat("health",Math.max(0,health));if(closed)row.setBoolean("deployed",false);markDirty();return true;
 }
 /** Journal ownership invalidates unloaded or duplicated actors before they can act. */
 boolean activeCompanion(String serial,UUID actor,UUID owner,int dimension){
  if(serial==null||serial.isEmpty()||actor==null||owner==null||!companions.hasKey(serial,10))return false;
  NBTTagCompound row=companions.getCompoundTag(serial);
  if(!row.getBoolean("deployed")||!row.hasUniqueId("actor")||!actor.equals(row.getUniqueId("actor")))return false;
  if(row.hasUniqueId("owner")&&!owner.equals(row.getUniqueId("owner")))return false;
  if(row.hasKey("actorDimension",3)&&row.getInteger("actorDimension")!=dimension)return false;
  // A matching older deployment is migrated in place; no new actor is trusted.
  if(!row.hasUniqueId("owner")||!row.hasKey("actorDimension",3)){
   row.setUniqueId("owner",owner);row.setInteger("actorDimension",dimension);markDirty();
  }
  return true;
 }
 void deployCompanion(String serial,UUID owner,UUID actor,int dimension,long expires){
  if(owner==null||actor==null||expires<0)throw new IllegalArgumentException("Invalid companion deployment");
  NBTTagCompound row=companion(serial);
  if(row.getBoolean("deployed"))throw new IllegalStateException("Companion is already deployed");
  row.setUniqueId("owner",owner);row.setUniqueId("actor",actor);row.setInteger("actorDimension",dimension);
  row.setLong("actorExpires",expires);row.setBoolean("deployed",true);
  row.setString("order",ScrollCompanionOrders.read(row.getString("order")).key);markDirty();
 }
 boolean sealCompanion(String serial,UUID owner){
  if(serial==null||owner==null||!companions.hasKey(serial,10))return false;
  NBTTagCompound row=companions.getCompoundTag(serial);
  if(!row.getBoolean("deployed")||(row.hasUniqueId("owner")&&!owner.equals(row.getUniqueId("owner"))))return false;
  row.setUniqueId("owner",owner);row.setBoolean("deployed",false);markDirty();return true;
 }
 int sealOwnerCompanions(UUID owner){
  int count=0;
  for(String serial:companions.getKeySet()){
   NBTTagCompound row=companions.getCompoundTag(serial);
   if(row.hasUniqueId("owner")&&owner.equals(row.getUniqueId("owner"))&&sealCompanion(serial,owner))count++;
  }
  return count;
 }
 void flush(EntityPlayerMP p){flush(p.getServerWorld());}
 void flush(WorldServer world){markDirty();world.getMinecraftServer().getWorld(0).getMapStorage().saveAllData();}
 @Override public void readFromNBT(NBTTagCompound n){claims=n.getCompoundTag("claims");spent=n.getCompoundTag("spent");companions=n.getCompoundTag("companions");}
 @Override public NBTTagCompound writeToNBT(NBTTagCompound n){n.setTag("claims",claims);n.setTag("spent",spent);n.setTag("companions",companions);return n;}
}
