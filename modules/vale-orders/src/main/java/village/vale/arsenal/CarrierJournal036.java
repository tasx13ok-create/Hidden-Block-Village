package village.vale.arsenal;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;

/** Canonical per-issued-stack state; inventory NBT is a display mirror, never authority. */
public final class CarrierJournal036 extends WorldSavedData {
 private NBTTagCompound carriers=new NBTTagCompound(),affinities=new NBTTagCompound();
 public CarrierJournal036(){super("vale_carriers036");}
 public CarrierJournal036(String name){super(name);}
 static CarrierJournal036 get(EntityPlayerMP p){WorldServer w=p.getServer().getWorld(0);CarrierJournal036 j=(CarrierJournal036)w.getMapStorage().getOrLoadData(CarrierJournal036.class,"vale_carriers036");if(j==null){j=new CarrierJournal036();w.getMapStorage().setData("vale_carriers036",j);}return j;}
 NBTTagCompound bind(String serial,String owner,String profile){
  if(serial==null||!serial.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")||owner==null||!owner.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")||profile==null||!profile.matches("[a-z][a-z0-9_]{1,48}"))return null;
  if(!carriers.hasKey(serial,10)){NBTTagCompound r=new NBTTagCompound();r.setString("serial",serial);r.setString("owner",owner);r.setString("profile",profile);r.setInteger("selected",0);r.setTag("mastery",new NBTTagCompound());r.setTag("cooldowns",new NBTTagCompound());carriers.setTag(serial,r);markDirty();}
  NBTTagCompound r=carriers.getCompoundTag(serial);return CarrierRules036.provenance(owner,r.getString("owner"),profile,r.getString("profile"),serial,r.getString("serial"))?r:null;
 }
 NBTTagCompound affinity(String owner){if(!affinities.hasKey(owner,10)){NBTTagCompound a=new NBTTagCompound();a.setInteger("choice",0);affinities.setTag(owner,a);markDirty();}return affinities.getCompoundTag(owner);}
 void flush(EntityPlayerMP p){markDirty();p.getServer().getWorld(0).getMapStorage().saveAllData();}
 @Override public void readFromNBT(NBTTagCompound n){carriers=n.getCompoundTag("carriers");affinities=n.getCompoundTag("affinities");}
 @Override public NBTTagCompound writeToNBT(NBTTagCompound n){n.setTag("carriers",carriers);n.setTag("affinities",affinities);return n;}
}
