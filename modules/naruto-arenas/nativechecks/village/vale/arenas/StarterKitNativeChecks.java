package village.vale.arenas;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.FakePlayerFactory;
import village.vale.RewardLedger;

/** Real official profile, physical intro and two-process pending starter test.
 * This claims a normal once-only starter, never fabricates a boss entitlement. */
public final class StarterKitNativeChecks {
 private static MinecraftServer server;private static ArenaDirector.Definition arena;
 private static EntityPlayerMP player;private static JsonObject result;private static JsonArray failures;
 private static int stage,wait,ticks,iteration,assertions;private static boolean done;
 private static final String CHOICE="storm";
 private StarterKitNativeChecks(){}
 private static void check(boolean ok,String message){assertions++;if(!ok)throw new IllegalStateException(message);}
 private static boolean rejected(Runnable task){try{task.run();return false;}catch(IllegalStateException expected){return true;}}
 private static WorldServer world(){return server.getWorld(0);}
 private static void guide(int[] at){NativeProbe.prewarm(world(),new BlockPos(at[0],at[1],at[2]));check(ArenaDirector.safe(world(),at),"Starter private guidance landing unsafe");player.connection.setPlayerLocation(at[0]+.5,at[1],at[2]+.5,player.rotationYaw,player.rotationPitch);player.fallDistance=0;world().getPlayerChunkMap().updateMovingPlayer(player);}
 private static void click(int index){int[] node=arena.nodes[index];boolean found=false;for(int[] off:new int[][]{{0,-3},{0,3},{-3,0},{3,0}}){int[] at={node[0]+off[0],arena.floor,node[2]+off[1]};NativeProbe.prewarm(world(),new BlockPos(at[0],at[1],at[2]));if(ArenaDirector.safe(world(),at)){guide(at);found=true;break;}}check(found,"No safe actual starter introduction node approach");ForgeHooks.onRightClickBlock(player,EnumHand.MAIN_HAND,new BlockPos(node[0],node[1],node[2]),EnumFacing.UP,new Vec3d(node[0]+.5,node[1]+.5,node[2]+.5));}
 private static double xp(){try{return ((Number)Class.forName("net.narutomod.PlayerTracker").getMethod("getBattleXp",net.minecraft.entity.player.EntityPlayer.class).invoke(null,player)).doubleValue();}catch(ReflectiveOperationException unavailable){throw new IllegalStateException("Actual native XP observer unavailable",unavailable);}}
 private static NBTTagCompound nativeState(){NBTTagCompound value=player.getEntityData().copy();value.getCompoundTag("PlayerPersisted").removeTag("ValeOrders");return value;}
 private static String coreOrder(){return player.getEntityData().getCompoundTag("PlayerPersisted").getCompoundTag("ValeOrders").getString("starterOrder");}
 private static JsonObject request(){double before=xp();NBTTagCompound nativeBefore=nativeState();String orderBefore=coreOrder();JsonObject response=ArenaStarterKits.request(player,CHOICE);check(Double.compare(before,xp())==0,"Starter changed actual native battle XP");check(nativeBefore.equals(nativeState()),"Starter changed native/nature/intro data");check(orderBefore.equals(coreOrder()),"Starter assigned/replaced Core starter order");return response;}
 public static void begin(MinecraftServer s,ArenaDirector.Definition d){
  if(!Boolean.getBoolean("hbv.arenas.nativeProbe")||!Files.isRegularFile(Paths.get(".hbv-arenas-native-probe"))||!s.getFolderName().equals("arena-preview")||!s.isCallingFromMinecraftThread())throw new IllegalStateException("Private starter guard rejected");
  server=s;arena=d;stage=wait=ticks=assertions=0;done=false;iteration=Integer.getInteger("hbv.arenas.probeIteration",1);result=new JsonObject();failures=new JsonArray();result.add("failures",failures);result.addProperty("iteration",iteration);result.addProperty("scope","ACTUAL_OPTIONAL_NORMAL_STARTER_AND_PUBLIC_CORE_LEDGER; no boss clear entitlement");
  UUID owner=UUID.nameUUIDFromBytes(("HBV:StarterNative:v1:"+world().getSeed()).getBytes(StandardCharsets.UTF_8));
  check(s.getPlayerList().getPlayerByUUID(owner)==null,"Starter private profile already online");player=new EntityPlayerMP(s,world(),new GameProfile(owner,"HbvKitProbe"),new PlayerInteractionManager(world()));player.connection=new NativeProbe.Quiet(s,player);s.getPlayerList().initializeConnectionToPlayer(player.connection.netManager,player,player.connection);check(s.getPlayerList().getPlayerByUUID(owner)==player&&world().playerEntities.contains(player),"Official starter player login failed");player.setGameType(GameType.SURVIVAL);player.setHealth(player.getMaxHealth());guide(d.entry);
 }
 public static JsonObject result(){return result;}
 private static void previewRejection(){
  ArenaDirector.Definition free=null;for(ArenaDirector.Definition candidate:ArenaDirector.DEFINITIONS){JsonObject r=ArenaDirector.run(candidate);if(r==null||ArenaDirector.text(r,"state").equals("AVAILABLE")){free=candidate;break;}}
  check(free!=null,"No empty arena for real preview starter rejection");
  NativeProbe.prewarm(world(),new BlockPos(free.entry[0],free.entry[1],free.entry[2]));NativeProbe.prewarm(world(),new BlockPos(free.exit[0],free.exit[1],free.exit[2]));NativeProbe.prewarm(world(),new BlockPos(free.boss_anchor[0],free.boss_anchor[1],free.boss_anchor[2]));
  ArenaDirector.admit(player,free,true);JsonObject own=ArenaDirector.run(free);check(own!=null&&own.getAsJsonObject("members").has(player.getUniqueID().toString()),"Actual preview membership missing");check(rejected(()->ArenaStarterKits.request(player,CHOICE)),"Preview participant claimed normal starter");ArenaDirector.exit(player,free,own);ArenaDirector.reset(free,own);guide(arena.entry);result.addProperty("actual_preview_membership_rejected",true);
 }
 private static void negatives(){
  NBTTagList before=player.inventory.writeToNBT(new NBTTagList());int pending=RewardLedger.get(player).pendingCount(player.getUniqueID());
  player.setGameType(GameType.CREATIVE);check(rejected(()->ArenaStarterKits.request(player,CHOICE)),"Creative claimed normal starter");player.setGameType(GameType.SPECTATOR);check(rejected(()->ArenaStarterKits.request(player,CHOICE)),"Spectator claimed normal starter");player.setGameType(GameType.SURVIVAL);
  EntityPlayerMP fake=FakePlayerFactory.get(world(),new GameProfile(UUID.randomUUID(),"HbvKitFake"));check(rejected(()->ArenaStarterKits.request(fake,CHOICE)),"Unregistered Forge fake claimed starter");check(before.equals(player.inventory.writeToNBT(new NBTTagList()))&&pending==RewardLedger.get(player).pendingCount(player.getUniqueID()),"Rejected starter paths mutated real inventory or queue");result.addProperty("creative_spectator_fake_rejected",true);
 }
 private static void pending(){
  RewardLedger ledger=RewardLedger.get(player);if(iteration==1){check(ledger.pendingCount(player.getUniqueID())==0,"Fresh starter profile has unrelated pending Core grants");for(int i=0;i<player.inventory.mainInventory.size();i++)player.inventory.mainInventory.set(i,new ItemStack(Blocks.COBBLESTONE,64));RewardOutbox.flush(player);}else check(ledger.pendingCount(player.getUniqueID())==5,"Actual process restart did not retain exact five queued starter items");
  NBTTagList full=player.inventory.writeToNBT(new NBTTagList());JsonObject reply=request();check(reply.get("status").getAsString().equals("CORE_PENDING")&&ledger.pendingCount(player.getUniqueID())==5,"Full inventory did not leave exact five real Core grants pending");check(full.equals(player.inventory.writeToNBT(new NBTTagList())),"Starter overwrote full native inventory");request();check(ledger.pendingCount(player.getUniqueID())==5&&full.equals(player.inventory.writeToNBT(new NBTTagList())),"Full-inventory retry duplicated or replaced starter");check(rejected(()->ArenaStarterKits.request(player,"ember")),"Permanent starter choice changed on retry");result.addProperty("full_inventory_five_real_core_pending_no_replacement",true);result.addProperty("stable_pending_replay_and_choice_lock",true);
  NBTTagCompound future=new NBTTagCompound();future.setInteger("Schema",99);future.setString("UnknownRoot","preserve");ArenaStarterKits.Store opaque=new ArenaStarterKits.Store();opaque.readFromNBT(future);check(opaque.readOnly&&future.equals(opaque.writeToNBT(new NBTTagCompound())),"Future starter schema was rewritten");result.addProperty("future_schema_preserved_read_only",true);
  if(iteration==1){result.addProperty("process_restart_case","PENDING_SAVED_FIRST_BOOT");ArenaStarterKits.store().markDirty();world().getPerWorldStorage().saveAllData();RewardOutbox.flush(player);finish();return;}
  for(int i=0;i<5;i++)player.inventory.mainInventory.set(i,ItemStack.EMPTY);request();check(ledger.pendingCount(player.getUniqueID())==0,"Queued starter did not deliver after real restart/free slots");
  Set<String> keys=new HashSet<>(),serials=new HashSet<>();for(ItemStack stack:player.inventory.mainInventory)if(!stack.isEmpty()&&stack.hasTagCompound()&&stack.getTagCompound().hasKey("valeSerial")){check(ledger.valid(player,stack),"Starter item has no genuine Core owner serial");keys.add(stack.getItem().getRegistryName().getPath());serials.add(stack.getTagCompound().getString("valeSerial"));}
  check(keys.equals(new HashSet<>(Arrays.asList("storm_helmet","storm_chestplate","storm_leggings","storm_boots","storm_saber")))&&serials.size()==5,"Actual starter items differ from four matching Ward pieces plus basic saber");
  NBTTagList delivered=player.inventory.writeToNBT(new NBTTagList());request();check(delivered.equals(player.inventory.writeToNBT(new NBTTagList()))&&ledger.pendingCount(player.getUniqueID())==0,"Delivered starter replay duplicated inventory");
  for(int i=0;i<player.inventory.mainInventory.size();i++){ItemStack stack=player.inventory.mainInventory.get(i);if(stack.hasTagCompound()&&serials.contains(stack.getTagCompound().getString("valeSerial")))player.inventory.mainInventory.set(i,ItemStack.EMPTY);}
  NBTTagList lost=player.inventory.writeToNBT(new NBTTagList());request();check(lost.equals(player.inventory.writeToNBT(new NBTTagList()))&&ledger.pendingCount(player.getUniqueID())==0,"Permanent starter replaced already delivered/lost gear");result.addProperty("process_restart_case","PASS_ACTUAL_PENDING_RELOAD_DELIVERY_REPLAY");result.addProperty("genuine_unique_owner_serials",serials.size());result.addProperty("lost_delivered_gear_not_reminted",true);finish();
 }
 private static void finish(){result.addProperty("assertions",assertions);result.addProperty("passed",failures.size()==0);if(player!=null&&server.getPlayerList().getPlayerByUUID(player.getUniqueID())==player)server.getPlayerList().playerLoggedOut(player);done=true;}
 public static boolean tick(){if(done)return true;if(--wait>0)return false;try{
  check(++ticks<100,"Starter private fixture exceeded bounded100tick work budget");check(server.getPlayerList().getPlayerByUUID(player.getUniqueID())==player&&player.isEntityAlive(),"Official starter player replaced or died");
  if(stage==0){check(ArenaStarterKits.list().length==9,"Actual registered nine Ward starter options missing");negatives();if(iteration==1){check(!ArenaIntro.complete(player,arena),"New private starter profile unexpectedly has prior intro");check(rejected(()->ArenaStarterKits.request(player,CHOICE)),"No-intro player claimed starter");ArenaIntro.start(player,arena);click(0);stage=1;wait=2;return false;}check(ArenaIntro.complete(player,arena),"Actual process reload lost completed physical intro");stage=3;return false;}
  if(stage==1){check(!ArenaIntro.complete(player,arena)&&ArenaIntro.records(player).getCompoundTag(arena.id).getBoolean("node0"),"First actual physical node did not settle without premature completion");click(1);stage=2;wait=2;return false;}
  if(stage==2){check(ArenaIntro.complete(player,arena),"Two actual physical node events did not earn completed intro");result.addProperty("actual_two_node_intro_completed",true);guide(arena.entry);stage=3;return false;}
  if(stage==3){previewRejection();negatives();pending();return done;}
 }catch(Throwable error){failures.add(error.toString());finish();return true;}return false;}
}
