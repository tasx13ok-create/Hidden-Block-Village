package hbv.villageschecks;
import com.google.gson.*;import com.mojang.authlib.GameProfile;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;
import net.minecraft.entity.player.*;import net.minecraft.network.*;import net.minecraft.server.MinecraftServer;import net.minecraft.server.management.PlayerInteractionManager;import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;import net.minecraftforge.fml.common.*;import net.minecraftforge.fml.common.event.*;import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;import net.minecraftforge.fml.common.gameevent.TickEvent;
import narutodestinationvillages.compat.*;

/** Separate-process readback of actual saved player/ender-chest state, without reissuing rewards. */
@Mod(modid="hbv_training_restart_checks",name="Private training restart checks",version="1",serverSideOnly=true,acceptableRemoteVersions="*",dependencies="required-after:narutodestinationvillages")
public final class TrainingRestartFixture {
 private int state,ticks,assertions;private MinecraftServer server;private EntityPlayerMP player,victim;private JsonObject expected;private final Map<String,Object> result=new LinkedHashMap<>();private final List<String> checks=new ArrayList<>();
 @Mod.EventHandler public void started(FMLServerStartedEvent e){if(!Boolean.getBoolean("hbv.villages.coldTraining"))return;state=1;MinecraftForge.EVENT_BUS.register(this);}
 private void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);checks.add(label);}
 @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){if(state==0||e.phase!=TickEvent.Phase.END)return;
  try{if(state==1){start();state=2;return;}player.connection.update();victim.connection.update();if(++ticks<40)return;
   check(NativeTraining.points(victim,NativeTraining.Stat.SPEED)==expected.get("victim_speed_points").getAsInt(),"allocated training survives full process restart and native respawn");
   check(victim.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue()>victim.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED).getBaseValue(),"cold restored training produces real native movement modifier");
   check(NativeTraining.balance(player,"skill")==expected.get("skill_points").getAsLong()&&NativeTraining.balance(player,"jutsu")==expected.get("jutsu_points").getAsLong()&&NativeTraining.balance(player,"reset")==expected.get("reset_points").getAsLong(),"native ticks after restart do not reissue or duplicate spent resources");result.put("passed",true);finish();
  }catch(Throwable failure){result.put("passed",false);result.put("error",failure.toString());try{StringWriter w=new StringWriter();failure.printStackTrace(new PrintWriter(w));Files.write(Paths.get("PRIVATE-TRAINING-COLD-FAILURE.txt"),w.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}finish();}
 }
 private EntityPlayerMP admit(String seed,String name)throws Exception{
  WorldServer world=server.getWorld(0);EntityPlayerMP p=new EntityPlayerMP(server,world,new GameProfile(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)),name),new PlayerInteractionManager(world));VillagesFixture.QuietConnection connection=new VillagesFixture.QuietConnection(server,p);p.connection=connection;
  java.lang.reflect.Method method;try{method=server.getPlayerList().getClass().getMethod("initializeConnectionToPlayer",NetworkManager.class,EntityPlayerMP.class,NetHandlerPlayServer.class);}catch(NoSuchMethodException runtime){method=server.getPlayerList().getClass().getMethod("func_72355_a",NetworkManager.class,EntityPlayerMP.class,NetHandlerPlayServer.class);}method.invoke(server.getPlayerList(),connection.manager,p,connection);check(server.getPlayerList().getPlayerByUUID(p.getUniqueID())==p&&!server.getPlayerList().canSendCommands(p.getGameProfile()),"saved synthetic non-OP profile admitted through native player list");return p;
 }
 private void start()throws Exception{
  server=FMLCommonHandler.instance().getMinecraftServerInstance();Path cwd=Paths.get("").toAbsolutePath().normalize();check(cwd.getFileName().toString().matches("private-native-runtime-(0[1-9]|[1-9][0-9])")&&"villages-port-flat-01".equals(server.getFolderName())&&server.getServerPort()==25596,"exact disposable saved world and loopback server guard");check(server.getPlayerList().getCurrentPlayerCount()==0,"no real players before cold readback");
  expected=new JsonParser().parse(new String(Files.readAllBytes(Paths.get("PRIVATE-TRAINING-RESTART-EXPECTED.json")),StandardCharsets.UTF_8)).getAsJsonObject();
  player=admit("HiddenBlockVillage:VillagesPortNative01","VillagesProbe");
  check(NativeTraining.balance(player,"skill")==expected.get("skill_points").getAsLong(),"unspent SP restored from actual player save");check(NativeTraining.balance(player,"jutsu")==expected.get("jutsu_points").getAsLong(),"spent JP balance restored from actual player save");check(NativeTraining.balance(player,"reset")==expected.get("reset_points").getAsLong(),"spent reset credit remains spent after process restart");check(net.narutomod.PlayerTracker.getBattleXp(player)==expected.get("battle_xp").getAsDouble(),"native battle XP preserved independently from training resources");
  net.minecraft.item.ItemStack learned=player.getInventoryEnderChest().getStackInSlot(0);check(!learned.isEmpty(),"actual learned item restored from vanilla ender-chest save");
  player.getInventoryEnderChest().setInventorySlotContents(1,player.inventory.getStackInSlot(0));player.inventory.setInventorySlotContents(0,learned);player.getInventoryEnderChest().setInventorySlotContents(0,net.minecraft.item.ItemStack.EMPTY);player.inventory.currentItem=0;
  NativeLearning.Choice saved=null;for(NativeLearning.Choice c:NativeLearning.choices(player))if(c.technique.unlocalizedName.equals(expected.get("technique").getAsString())&&c.technique.index==expected.get("technique_index").getAsInt())saved=c;
  check(saved!=null&&NativeLearning.learned(learned,saved),"actual native technique knowledge and owner survive full process restart");
  long jp=NativeTraining.balance(player,"jutsu");check(!NativeLearning.learn(player,saved)&&NativeTraining.balance(player,"jutsu")==jp,"cold re-learning cannot charge JP again");
  victim=admit("HiddenBlockVillage:VillagesPortVictim01","VillagesVictim");check(NativeTraining.points(victim,NativeTraining.Stat.SPEED)==expected.get("victim_speed_points").getAsInt(),"allocated speed points restored from actual second player save");if(!victim.isEntityAlive())victim=server.getPlayerList().recreatePlayerEntity(victim,0,false);
  result.put("loaded_balances",Arrays.asList(NativeTraining.balance(player,"skill"),NativeTraining.balance(player,"jutsu"),NativeTraining.balance(player,"reset")));result.put("native_battle_xp",net.narutomod.PlayerTracker.getBattleXp(player));result.put("reward_reissued_by_fixture",false);
 }
 private void finish(){state=0;try{if(victim!=null)server.getPlayerList().playerLoggedOut(victim);if(player!=null)server.getPlayerList().playerLoggedOut(player);}catch(Throwable failure){result.put("logout_error",failure.toString());}result.put("scope","PRIVATE_TRAINING_ACTUAL_PROCESS_RESTART");result.put("assertions",assertions);result.put("checks",checks);result.put("original_world_or_real_player_used",false);try{Files.write(Paths.get("PRIVATE-TRAINING-COLD-RESULT.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));}catch(Exception failure){failure.printStackTrace();}if(server!=null)server.initiateShutdown();}
}
