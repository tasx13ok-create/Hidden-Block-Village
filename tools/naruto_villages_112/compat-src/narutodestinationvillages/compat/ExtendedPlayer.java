package narutodestinationvillages.compat;
import java.io.File;import java.sql.*;import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;import net.minecraft.nbt.NBTTagCompound;import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraftforge.common.MinecraftForge;import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;import net.narutomod.PlayerTracker;import net.narutomod.Chakra;
/** Original NarutoMod-backed character adapter. Village/rank identity belongs to this addon. */
public final class ExtendedPlayer {
 private final EntityPlayer player;private static Connection database;private static boolean registered;
 private ExtendedPlayer(EntityPlayer p){player=p;}
 public static void initialize(File directory){
  try{
   if(database!=null&&!database.isClosed())return;
   Class.forName("org.sqlite.JDBC");database=DriverManager.getConnection("jdbc:sqlite:"+new File(directory,"native_players.sqlite").getAbsolutePath());
   try(Statement s=database.createStatement()){s.execute("CREATE TABLE IF NOT EXISTS player_data (player_uuid TEXT PRIMARY KEY, player_name TEXT NOT NULL, rank TEXT NOT NULL, affiliation INTEGER NOT NULL DEFAULT 0, level INTEGER NOT NULL DEFAULT 0, total_stats INTEGER NOT NULL DEFAULT 0)");}
   if(!registered){MinecraftForge.EVENT_BUS.register(new Events());registered=true;}
  }catch(Exception failure){throw new IllegalStateException("Village character database could not open",failure);}
 }
 public static Connection getConnection(){return database;}
 public static ExtendedPlayer get(EntityPlayer player){if(player==null)throw new IllegalArgumentException("Player required");return new ExtendedPlayer(player);}
 private NBTTagCompound data(){NBTTagCompound root=player.getEntityData();if(!root.hasKey(EntityPlayer.PERSISTED_NBT_TAG,10))root.setTag(EntityPlayer.PERSISTED_NBT_TAG,new NBTTagCompound());NBTTagCompound persistent=root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);if(!persistent.hasKey("NarutoVillages112",10))persistent.setTag("NarutoVillages112",new NBTTagCompound());return persistent.getCompoundTag("NarutoVillages112");}
 private boolean server(){return player.world!=null&&!player.world.isRemote&&database!=null;}
 private void ensure(){
  if(!server())return;
  try(PreparedStatement s=database.prepareStatement("INSERT OR IGNORE INTO player_data(player_uuid,player_name,rank,affiliation,level,total_stats) VALUES(?,?,?,?,?,?)")){
   s.setString(1,player.getUniqueID().toString());s.setString(2,player.getName());s.setString(3,data().hasKey("rank",8)?data().getString("rank"):NarutoRank.VILLAGER.getId());s.setInt(4,data().getInteger("affiliation"));s.setInt(5,getLevel());s.setInt(6,getAllSkillPoints());s.executeUpdate();
  }catch(SQLException e){throw new IllegalStateException("Village character could not be persisted",e);}
 }
 public String getRank(){if(server()){ensure();try(PreparedStatement s=database.prepareStatement("SELECT rank FROM player_data WHERE player_uuid=?")){s.setString(1,player.getUniqueID().toString());try(ResultSet r=s.executeQuery()){if(r.next())return r.getString(1);}}catch(SQLException e){throw new IllegalStateException(e);}}return data().hasKey("rank",8)?data().getString("rank"):NarutoRank.VILLAGER.getId();}
 public void setRank(String value){NarutoRank rank=NarutoRank.fromId(value);if(rank==null)throw new IllegalArgumentException("Unknown village rank");ensure();data().setString("rank",rank.getId());if(server())try(PreparedStatement s=database.prepareStatement("UPDATE player_data SET rank=? WHERE player_uuid=?")){s.setString(1,rank.getId());s.setString(2,player.getUniqueID().toString());s.executeUpdate();}catch(SQLException e){throw new IllegalStateException(e);}}
 public String getRankName(){NarutoRank rank=NarutoRank.fromId(getRank());return rank==null?getRank():rank.getDisplayName();}
 public int getAffiliation(){if(server()){ensure();try(PreparedStatement s=database.prepareStatement("SELECT affiliation FROM player_data WHERE player_uuid=?")){s.setString(1,player.getUniqueID().toString());try(ResultSet r=s.executeQuery()){if(r.next())return r.getInt(1);}}catch(SQLException e){throw new IllegalStateException(e);}}return data().getInteger("affiliation");}
 public void setAffiliation(int id){if(id<0||id>=CommandAffiliation.affiliations.length)throw new IllegalArgumentException("Unknown affiliation");ensure();data().setInteger("affiliation",id);if(server())try(PreparedStatement s=database.prepareStatement("UPDATE player_data SET affiliation=? WHERE player_uuid=?")){s.setInt(1,id);s.setString(2,player.getUniqueID().toString());s.executeUpdate();}catch(SQLException e){throw new IllegalStateException(e);}}
 public String getAffiliationName(){int id=getAffiliation();return id>=0&&id<CommandAffiliation.affiliations.length?CommandAffiliation.affiliations[id]:"None";}
 private static int bounded(double n){return Double.isFinite(n)&&n>0?(int)Math.min(Integer.MAX_VALUE,n):0;}
 public int getLevel(){return bounded(PlayerTracker.getNinjaLevel(player));}
 public int getAllSkillPoints(){return bounded(PlayerTracker.getBattleXp(player));}
 public int getAllAssignedSkillPoints(){return getAllSkillPoints();}
 public int getSkillPoints(){return data().getInteger("unspentLegacyPoints");}
 public int getCurrentChakra(){return bounded(Chakra.pathway(player).getAmount());}
 public int getMaxChakra(){return bounded(Chakra.pathway(player).getMax());}
 public int getHealth(){return bounded(player.getHealth());}public int getHealthCap(){return bounded(player.getMaxHealth());}
 public int getClan(){return data().getInteger("legacyClan");}public String getClanName(){return data().hasKey("legacyClanName",8)?data().getString("legacyClanName"):"Unassigned";}
 public int getBijuuKind(){return net.narutomod.entity.EntityBijuManager.getTails(player);}
 public int getCurrentSenChakra(){return data().getInteger("legacySenChakra");}public int getMaxSenChakra(){return data().getInteger("legacyMaxSenChakra");}
 public int getNinjutsu(){return data().getInteger("legacyNinjutsu");}public int getGenjutsu(){return data().getInteger("legacyGenjutsu");}
 public int getKenjutsu(){return data().getInteger("legacyKenjutsu");}public int getMedical(){return data().getInteger("legacyMedical");}
 public int getTaijutsu(){return player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE)==null?0:bounded(player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue());}
 public int getSpeedPoints(){return data().getInteger("legacySpeedPoints");}
 public float getTotalResistance(){return getTotalResistance(false);}public float getTotalResistance(boolean ignored){return player.getTotalArmorValue();}
 public void updateRecord(){if(!server())return;ensure();getRank();getAffiliation();try(PreparedStatement s=database.prepareStatement("UPDATE player_data SET player_name=?,level=?,total_stats=? WHERE player_uuid=?")){s.setString(1,player.getName());s.setInt(2,getLevel());s.setInt(3,getAllSkillPoints());s.setString(4,player.getUniqueID().toString());s.executeUpdate();}catch(SQLException e){throw new IllegalStateException(e);}}
 public static final class Events {
  @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){get(e.player).updateRecord();}
  @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){get(e.player).updateRecord();}
  @SubscribeEvent public void tick(net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent e){if(e.phase==net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END&&!e.player.world.isRemote&&e.player.ticksExisted%1200==0)get(e.player).updateRecord();}
  @SubscribeEvent public void clone(net.minecraftforge.event.entity.player.PlayerEvent.Clone e){NBTTagCompound old=e.getOriginal().getEntityData();if(old.hasKey(EntityPlayer.PERSISTED_NBT_TAG,10)&&old.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).hasKey("NarutoVillages112",10)){NBTTagCompound copy=get(e.getEntityPlayer()).data();copy.merge(old.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag("NarutoVillages112").copy());}}
 }
 public static void shutdown(){try{if(database!=null)database.close();database=null;}catch(SQLException e){throw new IllegalStateException("Village character database could not close",e);}}
}
