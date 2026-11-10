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
 static ScrollJournal get(EntityPlayerMP p){WorldServer w=p.getServer().getWorld(0);ScrollJournal j=(ScrollJournal)w.getMapStorage().getOrLoadData(ScrollJournal.class,"vale_original_scroll_journal");if(j==null){j=new ScrollJournal();w.getMapStorage().setData("vale_original_scroll_journal",j);}return j;}
 boolean spent(String serial){return serial.isEmpty()||spent.getBoolean(serial);}
 boolean claim(EntityPlayerMP p,String a,String b){if(a.isEmpty()||b.isEmpty()||a.equals(b)||spent(a)||spent(b))return false;String owner=p.getUniqueID().toString();String ca=claims.getString(a),cb=claims.getString(b);String pair=owner+"/"+(a.compareTo(b)<0?a+"/"+b:b+"/"+a);if((!ca.isEmpty()&&!ca.equals(pair))||(!cb.isEmpty()&&!cb.equals(pair)))return false;claims.setString(a,pair);claims.setString(b,pair);flush(p);return true;}
 void finish(EntityPlayerMP p,String a,String b){spent.setBoolean(a,true);spent.setBoolean(b,true);flush(p);}
 NBTTagCompound companion(String serial){if(serial.isEmpty())throw new IllegalArgumentException("Missing issuance serial");if(!companions.hasKey(serial,10)){NBTTagCompound row=new NBTTagCompound();row.setFloat("health",30);companions.setTag(serial,row);}return companions.getCompoundTag(serial);}
 /** A stale actor cannot overwrite the current deployment's saved health. */
 boolean recordCompanion(String serial,UUID actor,float health,boolean closed){
  if(serial==null||serial.isEmpty()||actor==null||!Float.isFinite(health))return false;
  NBTTagCompound row=companion(serial);
  if(row.hasUniqueId("actor")&&!actor.equals(row.getUniqueId("actor")))return false;
  row.setFloat("health",Math.max(0,health));if(closed)row.setBoolean("deployed",false);markDirty();return true;
 }
 void flush(EntityPlayerMP p){markDirty();p.getServer().getWorld(0).getMapStorage().saveAllData();}
 @Override public void readFromNBT(NBTTagCompound n){claims=n.getCompoundTag("claims");spent=n.getCompoundTag("spent");companions=n.getCompoundTag("companions");}
 @Override public NBTTagCompound writeToNBT(NBTTagCompound n){n.setTag("claims",claims);n.setTag("spent",spent);n.setTag("companions",companions);return n;}
}
