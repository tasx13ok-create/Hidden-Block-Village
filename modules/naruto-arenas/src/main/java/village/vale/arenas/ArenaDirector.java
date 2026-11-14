package village.vale.arenas;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.*;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Server-only original arena glue. No native cast replay, chakra cost, registry or profile mutation. */
@Mod(modid=ArenaDirector.ID,name="Naruto Coliseums",version="0.1.0",serverSideOnly=true,acceptableRemoteVersions="*",acceptedMinecraftVersions="[1.12.2]",dependencies="required-after:valeorders@[0.3.16,)")
public final class ArenaDirector {
 public static final String ID="narutoarenas";
 static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
 static final List<Definition> DEFINITIONS=new ArrayList<>();
 static MinecraftServer server;
 static long ticks;
 static File config;
 static final Map<String,LivingDeathEvent> DEATHS=new HashMap<>();
 static final class Definition {
  String id,title,boss,template_sha256; int[] min,entry,boss_anchor,reward,exit; int[][] nodes,services,lights; Sample[] samples; Storage[] containers; Prison water_prison; int floor; boolean water,terminal_verified; Map<String,Palette> palette;
  int[] point(String key){return key.equals("entry")?entry:key.equals("reward")?reward:key.equals("exit")?exit:boss_anchor;}
  boolean contains(BlockPos p){return p.getX()>=min[0]&&p.getX()<min[0]+500&&p.getZ()>=min[2]&&p.getZ()<min[2]+500&&p.getY()>=3&&p.getY()<=252;}
 }
 static final class Palette {String name;int[] metadata;}
 static final class Sample {String name;int[] at;}
 static final class Storage {String name;int[] at,front;}
 static final class Prison {int[] min,max;int[][] exits;}
 @Mod.EventHandler public void pre(FMLPreInitializationEvent e)throws IOException {
  config=new File(e.getModConfigurationDirectory(),"naruto-arenas.json");DEFINITIONS.clear();
  if(!config.isFile())return;
  JsonObject root=new JsonParser().parse(new String(Files.readAllBytes(config.toPath()),StandardCharsets.UTF_8)).getAsJsonObject();
  if(root.get("schema").getAsInt()!=1)throw new IOException("Unknown arena config schema");
  Set<String> ids=new HashSet<>();
  for(JsonElement row:root.getAsJsonArray("arenas")){
   Definition d=JSON.fromJson(row,Definition.class);
   if(!ids.add(d.id)||d.min.length!=3||d.min[1]!=3||d.template_sha256==null||!d.template_sha256.matches("[0-9a-f]{64}"))throw new IOException("Invalid arena identity");
   for(String key:new String[]{"entry","boss","reward","exit"}){int[] p=d.point(key);if(p.length!=3||!d.contains(new BlockPos(p[0],p[1],p[2])))throw new IOException("Anchor outside owned plot");}
   if(d.terminal_verified)throw new IOException("Earned terminal authority requires reviewed integration; config cannot self-certify it");
   DEFINITIONS.add(d);
  }
  if(DEFINITIONS.size()>5)throw new IOException("Arena cap exceeded");
  MinecraftForge.EVENT_BUS.register(this);
 }
 @Mod.EventHandler public void start(FMLServerStartingEvent e){server=e.getServer();e.registerServerCommand(new ArenaCommand());}
 @Mod.EventHandler public void started(FMLServerStartedEvent e){ticks=0;for(Definition d:DEFINITIONS)d.terminal_verified=NativeBossPolicies.accepted(d);if(!store().readOnly)for(Definition d:DEFINITIONS){JsonObject r=run(d);if(r!=null&&text(r,"state").equals("SEALING_OBSERVE")){r.addProperty("seal_observation_ticks",0);store().dirty();}}}
 @Mod.EventHandler public void stopped(FMLServerStoppedEvent e){DEATHS.clear();SEALS.clear();JOINING.clear();server=null;ticks=0;}
 static Definition definition(String id){for(Definition d:DEFINITIONS)if(d.id.equals(id)||d.title.equalsIgnoreCase(id))return d;throw new IllegalArgumentException("Unknown arena");}
 static Store store(){WorldServer w=server.getWorld(0);Store s=(Store)w.getPerWorldStorage().getOrLoadData(Store.class,"naruto_arenas_v1");if(s==null){s=new Store();w.getPerWorldStorage().setData("naruto_arenas_v1",s);}return s;}
 public static final class Store extends WorldSavedData {
  NBTTagCompound opaque=new NBTTagCompound();JsonObject data=new JsonObject();boolean readOnly;
  public Store(){this("naruto_arenas_v1");}public Store(String n){super(n);data.addProperty("schema",1);data.add("runs",new JsonObject());data.add("admissions",new JsonObject());}
  public void readFromNBT(NBTTagCompound n){opaque=n.copy();try{if(n.hasKey("Envelope",99)&&n.getInteger("Envelope")!=1){readOnly=true;return;}String payload;if(n.hasKey("PayloadUtf8",7)){byte[] bytes=n.getByteArray("PayloadUtf8");if(bytes.length>4*1024*1024){readOnly=true;return;}payload=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}else payload=n.getString("Payload");if(payload.length()>4*1024*1024){readOnly=true;return;}data=new JsonParser().parse(payload).getAsJsonObject();readOnly=!data.has("schema")||data.get("schema").getAsInt()!=1||!data.has("runs")||!data.get("runs").isJsonObject()||!data.has("admissions")||!data.get("admissions").isJsonObject();if(!readOnly&&(data.getAsJsonObject("runs").size()>5||data.getAsJsonObject("admissions").size()>8192))readOnly=true;}catch(Exception ex){readOnly=true;}}
  public NBTTagCompound writeToNBT(NBTTagCompound n){n.merge(opaque.copy());if(!readOnly){byte[] bytes=JSON.toJson(data).getBytes(StandardCharsets.UTF_8);if(bytes.length>4*1024*1024)throw new IllegalStateException("Arena state exceeds bounded4MiB envelope");n.removeTag("Payload");n.setInteger("Envelope",1);n.setByteArray("PayloadUtf8",bytes);}return n;}
  JsonObject runs(){if(readOnly)throw new IllegalStateException("Future arena state preserved read-only");return data.getAsJsonObject("runs");}
  void dirty(){if(readOnly)throw new IllegalStateException("Read-only");markDirty();}
 }
 /** Integration surface: caller must first validate its own earned, once-only receipt. Not a player command. */
 public static boolean acceptEarnedAdmission(EntityPlayerMP player,String arena,String validatedSerial){
  if(server==null||!server.isCallingFromMinecraftThread()||!eligible(player,false)||validatedSerial==null||!validatedSerial.matches("[A-Za-z0-9:_-]{16,160}"))return false;
  Definition d=definition(arena);Store s=store();if(s.readOnly)return false;
  JsonObject admissions=s.data.getAsJsonObject("admissions");if(admissions.has(validatedSerial))return false;
  if(admissions.size()>=8192)return false;
  JsonObject a=new JsonObject();a.addProperty("owner",player.getUniqueID().toString());a.addProperty("arena",d.id);a.addProperty("consumed",false);admissions.add(validatedSerial,a);s.dirty();return true;
 }
 static boolean eligible(EntityPlayerMP p,boolean preview){return RunRules.participant(server.getPlayerList().getPlayerByUUID(p.getUniqueID())==p,p.isSpectator(),p.isCreative(),p instanceof FakePlayer,preview)&&p.isEntityAlive()&&p.dimension==0;}
 static void tell(EntityPlayer p,String text){p.sendMessage(new TextComponentString(text));}
 static JsonObject run(Definition d){Store s=store();if(s.readOnly)return null;return s.runs().has(d.id)?s.runs().getAsJsonObject(d.id):null;}
 static String text(JsonObject o,String k){return o.has(k)?o.get(k).getAsString():"";}
 static int number(JsonObject o,String k){return o.has(k)?o.get(k).getAsInt():0;}
 static int sealPhase(JsonObject r){if(r.has("seal_phase"))return Math.max(1,Math.min(3,number(r,"seal_phase")));if(r.has("objectives"))for(int phase=1;phase<=3;phase++){int count=0;for(String key:keys(r.getAsJsonObject("objectives")))if(key.startsWith(phase+":"))count++;if(count<2)return phase;}return 1;}
 static Set<String> keys(JsonObject o){Set<String> out=new HashSet<>();for(Map.Entry<String,JsonElement> e:o.entrySet())out.add(e.getKey());return out;}
 static void state(JsonObject r,String state){r.addProperty("state",state);store().dirty();}
 static void broadcast(JsonObject r,String text){for(Map.Entry<String,JsonElement> m:r.getAsJsonObject("members").entrySet()){EntityPlayerMP p=server.getPlayerList().getPlayerByUUID(UUID.fromString(m.getKey()));if(p!=null)tell(p,text);}}
 static boolean safe(WorldServer w,int[] p){BlockPos b=new BlockPos(p[0],p[1],p[2]);return w.isBlockLoaded(b)&&w.getBlockState(b.down()).getMaterial().isSolid()&&w.getCollisionBoxes(null,new AxisAlignedBB(p[0]+.2,p[1],p[2]+.2,p[0]+.8,p[1]+1.8,p[2]+.8)).isEmpty()&&!w.containsAnyLiquid(new AxisAlignedBB(b,b.add(1,2,1)));}
 static void prepareLanding(WorldServer w,int[] target){for(int x=(target[0]-1)>>4;x<=(target[0]+1)>>4;x++)for(int z=(target[2]-1)>>4;z<=(target[2]+1)>>4;z++){if(w.getChunkProvider().getLoadedChunk(x,z)!=null)continue;if(!w.getChunkProvider().isChunkGeneratedAt(x,z))throw new IllegalStateException("Travel landing chunk was not staged");w.getChunkProvider().loadChunk(x,z);}}
 static void move(EntityPlayerMP p,int[] target){WorldServer w=(WorldServer)p.world;prepareLanding(w,target);double width=Math.max(.6,p.width)/2,height=Math.max(1.8,p.height);AxisAlignedBB body=new AxisAlignedBB(target[0]+.5-width,target[1],target[2]+.5-width,target[0]+.5+width,target[1]+height,target[2]+.5+width);if(!safe(w,target)||!w.getCollisionBoxes(p,body).isEmpty()||w.containsAnyLiquid(body)){BlockPos pos=new BlockPos(target[0],target[1],target[2]);org.apache.logging.log4j.LogManager.getLogger(ID).warn("Arena landing rejected: position={}, support={}, feet={}, head={}, playerWidth={}, playerHeight={}",pos,w.getBlockState(pos.down()),w.getBlockState(pos),w.getBlockState(pos.up()),p.width,p.height);throw new IllegalStateException("Return landing is obstructed. Your run and reward were retained.");}if(p.isRiding())p.dismountRidingEntity();p.connection.setPlayerLocation(target[0]+.5,target[1],target[2]+.5,p.rotationYaw,p.rotationPitch);p.fallDistance=0;}
 static void admit(EntityPlayerMP p,Definition d,boolean preview){
  if(!eligible(p,preview))throw new IllegalStateException("A living logged-in player is required; preview excludes spectators and fake players");
  for(Definition existing:DEFINITIONS){JsonObject active=run(existing);if(active!=null&&active.getAsJsonObject("members").has(p.getUniqueID().toString()))throw new IllegalStateException("Exit your current run before another admission");}
  if(!preview&&(!d.terminal_verified||!ArenaIntro.complete(p,d)))throw new IllegalStateException("Finish physical practice and use a native-accepted arena before earned admission");
  JsonObject r=run(d);if(r!=null&&!text(r,"state").equals("AVAILABLE"))throw new IllegalStateException("Arena is occupied or requires empty reset");
  WorldServer w=server.getWorld(0);for(int[] anchor:new int[][]{d.entry,d.boss_anchor,d.exit}){w.getChunk(new BlockPos(anchor[0],anchor[1],anchor[2]));if(!safe(w,anchor))throw new IllegalStateException("Unsafe entry, boss or exit anchor");}
  EntityEntry entry=ForgeRegistries.ENTITIES.getValue(new ResourceLocation(d.boss));if(entry==null)throw new IllegalStateException("Exact native boss provider absent");
  Entity body=entry.newInstance(w);if(!(body instanceof EntityLivingBase))throw new IllegalStateException("Registry is not a living boss");
  r=new JsonObject();r.addProperty("run",UUID.randomUUID().toString());r.addProperty("arena",d.id);r.addProperty("template",d.template_sha256);r.addProperty("seed",w.getSeed());r.addProperty("preview",preview);r.addProperty("state","PREPARING");r.addProperty("phase",1);r.addProperty("seal_phase",1);r.addProperty("clock",0);r.addProperty("quiet",0);r.addProperty("death_cancelled",true);r.addProperty("boss_dead",false);r.addProperty("grace",0);r.add("cells",new JsonArray());r.add("receipts",new JsonObject());r.add("members",new JsonObject());JsonObject m=new JsonObject();m.addProperty("contribution",0);m.addProperty("earned",!preview);m.addProperty("outbox","NONE");r.getAsJsonObject("members").add(p.getUniqueID().toString(),m);
  body.setPosition(d.boss_anchor[0]+.5,d.boss_anchor[1],d.boss_anchor[2]+.5);if(body instanceof EntityLiving){((EntityLiving)body).onInitialSpawn(w.getDifficultyForLocation(body.getPosition()),null);((EntityLiving)body).enablePersistence();}body.getEntityData().setString("HbvArenaRun",text(r,"run"));body.getEntityData().setString("HbvArenaId",d.id);r.addProperty("boss_uuid",body.getUniqueID().toString());r.addProperty("native_class_sha256",ArenaTerminals.classHash(body));ArenaTerminals.recordBaseline(d,r,w);
  store().runs().add(d.id,r);store().dirty();
  if(!w.spawnEntity(body)){state(r,"SAFE_CLOSED");throw new IllegalStateException("Native boss spawn canceled; candidate retained");}
  try{if(!eligible(p,preview))throw new IllegalStateException("Native spawn callback changed participant context");move(p,d.entry);}catch(RuntimeException changed){state(r,"SAFE_CLOSED");throw changed;}state(r,"PHASE1");broadcast(r,preview?"Preview: native boss abilities remain active. Practice carries no earned rewards. Gold seal nodes interrupt arena hazards; dry side paths remain safe.":"Arena started. Gold seals interrupt environmental hazards. Earn each phase through two distinct seals, or defeat the authentic native boss through its own accepted rules.");
 }
 static boolean ownedPreview(Entity body){String id=body.getEntityData().getString("HbvArenaId");if(id.isEmpty())return false;for(Definition d:DEFINITIONS)if(d.id.equals(id)){JsonObject r=run(d);return r!=null&&r.get("preview").getAsBoolean()&&text(r,"boss_uuid").equals(body.getUniqueID().toString())&&text(r,"run").equals(body.getEntityData().getString("HbvArenaRun"));}return false;}
 @SubscribeEvent(priority=EventPriority.LOWEST) public void previewDrops(LivingDropsEvent e){if(server!=null&&ownedPreview(e.getEntityLiving()))e.getDrops().clear();}
 @SubscribeEvent(priority=EventPriority.LOWEST) public void previewXp(LivingExperienceDropEvent e){if(server!=null&&ownedPreview(e.getEntityLiving()))e.setDroppedExperience(0);}
 @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){if(server==null)return;for(Definition d:DEFINITIONS){JsonObject r=run(d);if(r!=null&&r.getAsJsonObject("members").has(e.player.getUniqueID().toString())){restore(d,r);state(r,"RECOVERY_CHECKPOINT");}}}
 @SubscribeEvent public void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e){if(server==null)return;for(Definition d:DEFINITIONS){JsonObject r=run(d);if(r!=null&&r.getAsJsonObject("members").has(e.player.getUniqueID().toString())){restore(d,r);state(r,"RECOVERY_CHECKPOINT");}}}
 @SubscribeEvent public void clonePlayer(net.minecraftforge.event.entity.player.PlayerEvent.Clone e){NBTTagCompound source=e.getOriginal().getEntityData().getCompoundTag("PlayerPersisted").copy();NBTTagCompound destination=e.getEntityPlayer().getEntityData();if(!destination.hasKey("PlayerPersisted",10))destination.setTag("PlayerPersisted",new NBTTagCompound());for(String key:new String[]{"HbvArenaIntro","HbvArenaReceipts"})if(source.hasKey(key))destination.getCompoundTag("PlayerPersisted").setTag(key,source.getTag(key).copy());}
 static final Set<UUID> JOINING=new HashSet<>();
 static void join(EntityPlayerMP p,Definition d){JsonObject r=run(d);if(r==null||!text(r,"state").startsWith("PHASE"))throw new IllegalStateException("No live party run");boolean preview=r.get("preview").getAsBoolean();if(!eligible(p,preview)||(!preview&&!ArenaIntro.complete(p,d)))throw new IllegalStateException("Party admission prerequisites are missing");JsonObject members=r.getAsJsonObject("members");if(members.has(p.getUniqueID().toString()))throw new IllegalStateException("Already admitted");if(members.size()>=RunRules.MAX_PARTY)throw new IllegalStateException("Four-player party cap");for(Definition other:DEFINITIONS){JsonObject active=run(other);if(active!=null&&active.getAsJsonObject("members").has(p.getUniqueID().toString()))throw new IllegalStateException("Already in another run");}JsonObject m=new JsonObject();m.addProperty("earned",!preview);m.addProperty("contribution",0);m.addProperty("outbox","NONE");UUID owner=p.getUniqueID();Object connection=p.connection;prepareLanding(server.getWorld(0),d.entry);if(!safe(server.getWorld(0),d.entry))throw new IllegalStateException("Party entry is obstructed or unloaded");if(!JOINING.add(owner))throw new IllegalStateException("Party admission reentry refused");try{move(p,d.entry);if(!owner.equals(p.getUniqueID())||p.connection!=connection||server.getPlayerList().getPlayerByUUID(owner)!=p||!eligible(p,preview)||run(d)!=r||!text(r,"state").startsWith("PHASE")||members.has(owner.toString()))throw new IllegalStateException("Party callback changed admission context");members.add(owner.toString(),m);store().dirty();broadcast(r,"A party member joined. Each participant needs actual objective or accepted-contact contribution for an earned reward.");}finally{JOINING.remove(owner);}}
 @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void death(LivingDeathEvent e){
  if(server==null||e.getEntityLiving().world.isRemote)return;
  for(Definition d:DEFINITIONS){JsonObject r=run(d);if(r==null||!text(r,"boss_uuid").equals(e.getEntityLiving().getUniqueID().toString())||!text(r,"run").equals(e.getEntityLiving().getEntityData().getString("HbvArenaRun")))continue;r.addProperty("death_confirmed",false);r.addProperty("death_cancelled",true);r.addProperty("boss_dead",false);r.addProperty("quiet",0);DEATHS.put(text(r,"run"),e);state(r,"VICTORY_PENDING");}
 }
 static final class SealContact {final PlayerInteractEvent.RightClickBlock event;final EntityPlayerMP player;final Object connection;final World world;final UUID owner;final BlockPos pos;final String arena,run;final int phase;SealContact(PlayerInteractEvent.RightClickBlock e,Definition d){event=e;player=(EntityPlayerMP)e.getEntityPlayer();connection=player.connection;world=player.world;owner=player.getUniqueID();pos=e.getPos().toImmutable();arena=d.id;JsonObject r=ArenaDirector.run(d);run=r!=null&&r.getAsJsonObject("members").has(owner.toString())?text(r,"run"):"INTRO";phase=r==null?0:sealPhase(r);}}
 static final List<SealContact> SEALS=new ArrayList<>();
 @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void interact(PlayerInteractEvent.RightClickBlock e){
  if(server==null||e.getWorld().isRemote||e.getHand()!=EnumHand.MAIN_HAND||!(e.getEntityPlayer() instanceof EntityPlayerMP)||SEALS.size()>=128)return;
  for(Definition d:DEFINITIONS)if(d.contains(e.getPos())&&d.nodes!=null)for(int[] node:d.nodes)if(e.getPos().equals(new BlockPos(node[0],node[1],node[2]))){SEALS.add(new SealContact(e,d));return;}
 }
 static boolean introContact(EntityPlayerMP p,Definition d,BlockPos pos){try{return ArenaIntro.contact(p,d,pos);}catch(RuntimeException|LinkageError failure){org.apache.logging.log4j.LogManager.getLogger(ID).warn("Arena introduction settlement failed",failure);tell(p,"Native practice could not settle. Its pending receipt was retained.");return false;}}
 static void settleSeals(){List<SealContact> batch=new ArrayList<>(SEALS);SEALS.clear();for(SealContact contact:batch){
  EntityPlayerMP p=contact.player;PlayerInteractEvent.RightClickBlock event=contact.event;
  if(event.isCanceled()||event.getUseBlock()==Event.Result.DENY||p.world!=contact.world||p.world!=server.getWorld(0)||p.dimension!=0||p.connection!=contact.connection||!p.getUniqueID().equals(contact.owner)||server.getPlayerList().getPlayerByUUID(contact.owner)!=p||!p.isEntityAlive()||p.isSpectator()||p.getDistanceSq(contact.pos)>36||!p.world.isBlockLoaded(contact.pos)||p.world.getBlockState(contact.pos).getBlock()!=Blocks.GOLD_BLOCK)continue;
  for(Definition d:DEFINITIONS){if(!d.id.equals(contact.arena)||!d.contains(contact.pos)||!d.contains(p.getPosition()))continue;
   JsonObject r=run(d);if(contact.run.equals("INTRO")){if(r==null||!r.getAsJsonObject("members").has(contact.owner.toString())){if(introContact(p,d,contact.pos))break;}continue;}if(r==null||!text(r,"run").equals(contact.run)||sealPhase(r)!=contact.phase||!r.getAsJsonObject("members").has(contact.owner.toString()))continue;
   if(!text(r,"state").startsWith("PHASE")||!eligible(p,r.get("preview").getAsBoolean()))continue;
   int node=-1;for(int i=0;i<d.nodes.length;i++)if(contact.pos.equals(new BlockPos(d.nodes[i][0],d.nodes[i][1],d.nodes[i][2])))node=i;if(node<0)continue;
   Entity body=server.getWorld(0).getEntityFromUuid(UUID.fromString(text(r,"boss_uuid")));if(!(body instanceof EntityLivingBase)||!body.isEntityAlive()||!text(r,"run").equals(body.getEntityData().getString("HbvArenaRun")))continue;
   if(!restore(d,r)){state(r,"SAFE_CLOSED");continue;}
   String key=sealPhase(r)+":"+node;if(!r.has("objectives"))r.add("objectives",new JsonObject());JsonObject objectives=r.getAsJsonObject("objectives");
   if(!objectives.has(key)){objectives.addProperty(key,true);JsonObject m=r.getAsJsonObject("members").getAsJsonObject(contact.owner.toString());m.addProperty("contribution",m.get("contribution").getAsDouble()+1);}
   r.addProperty("clock",70);store().dirty();tell(p,"Seal interrupted: recovery window. Native boss attacks retain their own tells.");int count=0;for(String completed:keys(objectives))if(completed.startsWith(sealPhase(r)+":"))count++;
   if(count>=2){if(sealPhase(r)<3){int next=sealPhase(r)+1;r.addProperty("seal_phase",next);r.addProperty("phase",Math.max(number(r,"phase"),next));r.addProperty("clock",0);state(r,"PHASE"+number(r,"phase"));broadcast(r,"Two physical seals earned the next sequence. Native combat remains in its actual health phase.");}else if(ArenaTerminals.allSeals(r)){try{ArenaTerminals.begin(d,r);}catch(RuntimeException|LinkageError failure){r.addProperty("last_failure",failure.toString());state(r,"SAFE_CLOSED");tell(p,"The owned terminal callback changed state; run preserved for review.");}}else state(r,"SAFE_CLOSED");}break;
  }
 }}
 static void journal(WorldServer w,JsonObject r,BlockPos p,IBlockState after){
  JsonArray cells=r.getAsJsonArray("cells");if(cells.size()>=RunRules.MAX_CELLS)throw new IllegalStateException("Owned cell budget exceeded");
  if(!w.isBlockLoaded(p)||w.getTileEntity(p)!=null)throw new IllegalStateException("Unloaded or tile-bearing owned cell");
  int old=Block.getStateId(w.getBlockState(p)),next=Block.getStateId(after);if(old==next)return;
  for(JsonElement existing:cells){JsonObject c=existing.getAsJsonObject();if(number(c,"x")==p.getX()&&number(c,"y")==p.getY()&&number(c,"z")==p.getZ()){if(number(c,"after")==next)return;throw new IllegalStateException("Conflicting owned cell plan");}}
  JsonObject c=new JsonObject();c.addProperty("x",p.getX());c.addProperty("y",p.getY());c.addProperty("z",p.getZ());c.addProperty("before",old);c.addProperty("after",next);c.addProperty("status","PLANNED");if(after.getBlock()==Blocks.WATER)c.addProperty("owned_water",true);cells.add(c);store().dirty();
 }
 static void applyJournal(JsonObject r){WorldServer w=server.getWorld(0);store().dirty();w.getPerWorldStorage().saveAllData();for(JsonElement element:r.getAsJsonArray("cells")){JsonObject c=element.getAsJsonObject();if(!text(c,"status").equals("PLANNED"))continue;BlockPos p=new BlockPos(number(c,"x"),number(c,"y"),number(c,"z"));if(!w.isBlockLoaded(p)||w.getTileEntity(p)!=null||Block.getStateId(w.getBlockState(p))!=number(c,"before"))throw new IllegalStateException("Owned write before-state changed");w.setBlockState(p,Block.getStateById(number(c,"after")),2);if(Block.getStateId(w.getBlockState(p))!=number(c,"after"))throw new IllegalStateException("Owned write native readback failed");c.addProperty("status","APPLIED");store().dirty();}}
 static boolean restore(Definition d,JsonObject r){
  WorldServer w=server.getWorld(0);JsonArray cells=r.getAsJsonArray("cells"),pending=new JsonArray();
  if(cells.size()>0){for(JsonElement element:cells)element.getAsJsonObject().addProperty("status","RESTORING");store().dirty();w.getPerWorldStorage().saveAllData();}
  for(JsonElement element:cells){JsonObject c=element.getAsJsonObject();BlockPos p=new BlockPos(number(c,"x"),number(c,"y"),number(c,"z"));if(!d.contains(p)||!w.isBlockLoaded(p)||w.getTileEntity(p)!=null){pending.add(c);continue;}int current=Block.getStateId(w.getBlockState(p));if(current==number(c,"before"))continue;if(RunRules.restorable(current,number(c,"after"))||WaterPrison.waterState(c,current)){w.setBlockState(p,Block.getStateById(number(c,"before")),2);if(Block.getStateId(w.getBlockState(p))!=number(c,"before"))pending.add(c);}else pending.add(c);}
  r.add("cells",pending);store().dirty();return pending.size()==0;
 }
 static boolean courtReady(Definition d,JsonObject r){WorldServer w=server.getWorld(0);int lastX=r.has("last_boss_x")?number(r,"last_boss_x"):d.boss_anchor[0],lastZ=r.has("last_boss_z")?number(r,"last_boss_z"):d.boss_anchor[2];int loaded=0;for(int center=0;center<2;center++)for(int x=(center==0?-3:-2);x<=(center==0?3:2);x++)for(int z=(center==0?-3:-2);z<=(center==0?3:2);z++){int cx=((center==0?d.boss_anchor[0]:lastX)>>4)+x,cz=((center==0?d.boss_anchor[2]:lastZ)>>4)+z;if(w.getChunkProvider().getLoadedChunk(cx,cz)!=null)continue;if(!w.getChunkProvider().isChunkGeneratedAt(cx,cz)){state(r,"SAFE_CLOSED");broadcast(r,"Owned arena chunk is absent. Generation was refused; use the staged world package.");return false;}if(loaded>=2)return false;w.getChunkProvider().loadChunk(cx,cz);loaded++;}return true;}
 @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END||server==null)return;ticks++;if(store().readOnly)return;settleSeals();
  for(Definition d:DEFINITIONS){JsonObject r=run(d);if(r==null)continue;String state=text(r,"state");if(state.equals("AVAILABLE")||state.equals("SAFE_CLOSED"))continue;
   if(!text(r,"template").equals(d.template_sha256)){state(r,"SAFE_CLOSED");continue;}
   if((state.startsWith("PHASE")||state.equals("VICTORY_PENDING")||state.equals("SEALING_OBSERVE")||state.equals("PREPARING_RETRY"))&&!courtReady(d,r))continue;
   if(state.equals("SEALING_OBSERVE")){ArenaTerminals.tick(d,r);continue;}
   // Forge's LivingDamageEvent is before native unwind. It cannot establish
   // accepted HP/absorption loss or safely credit a later unrelated hit. Only
   // physical seal completion is credited until the owner supplies that API.
   Entity body=server.getWorld(0).getEntityFromUuid(UUID.fromString(text(r,"boss_uuid")));
   if(body!=null&&r.has("native_class_sha256")&&!text(r,"native_class_sha256").equals(ArenaTerminals.classHash(body))){state(r,"SAFE_CLOSED");continue;}
   if(body!=null){r.addProperty("last_boss_x",body.getPosition().getX());r.addProperty("last_boss_z",body.getPosition().getZ());}
   if(state.equals("PREPARING_RETRY")){EntityPlayerMP retry=server.getPlayerList().getPlayerByUUID(UUID.fromString(text(r,"retry_owner")));if(!(body instanceof EntityLivingBase)||!body.isEntityAlive()||!text(r,"run").equals(body.getEntityData().getString("HbvArenaRun"))){state(r,"SAFE_CLOSED");continue;}if(retry==null||!eligible(retry,r.get("preview").getAsBoolean()))continue;if(!restore(d,r)){state(r,"SAFE_CLOSED");continue;}try{move(retry,d.entry);r.addProperty("clock",0);state(r,"PHASE"+Math.max(1,number(r,"phase")));}catch(IllegalStateException blocked){tell(retry,"Recovery landing obstructed. Clear the entry before retry.");}continue;}
   if(state.equals("VICTORY_PENDING")){
    LivingDeathEvent death=DEATHS.remove(text(r,"run"));if(death!=null){boolean accepted=!death.isCanceled()&&death.getEntityLiving().getHealth()<=0&&text(r,"boss_uuid").equals(death.getEntityLiving().getUniqueID().toString());r.addProperty("death_confirmed",accepted);r.addProperty("death_cancelled",!accepted);r.addProperty("boss_dead",accepted);Entity killer=death.getSource().getTrueSource();if(accepted&&killer instanceof EntityPlayerMP&&server.getPlayerList().getPlayerByUUID(killer.getUniqueID())==killer&&r.getAsJsonObject("members").has(killer.getUniqueID().toString()))r.addProperty("finisher_uid",killer.getUniqueID().toString());store().dirty();if(!accepted){state(r,"PHASE3");continue;}}
    if(!r.has("death_confirmed")||!r.get("death_confirmed").getAsBoolean()){state(r,"SAFE_CLOSED");continue;}
    boolean absent=body==null;int quiet=absent?number(r,"quiet")+1:0;r.addProperty("quiet",quiet);
    if(RunRules.terminal(r.get("death_cancelled").getAsBoolean(),r.get("boss_dead").getAsBoolean(),absent,d.terminal_verified,quiet)){Entity ambiguous=ArenaTerminals.ambiguous(d,r);if(ambiguous!=null){r.addProperty("ambiguous_actor_uuid",ambiguous.getUniqueID().toString());state(r,"SAFE_CLOSED");continue;}String finisher=text(r,"finisher_uid");if(r.getAsJsonObject("members").has(finisher)){JsonObject m=r.getAsJsonObject("members").getAsJsonObject(finisher);m.addProperty("contribution",m.get("contribution").getAsDouble()+1);}state(r,"REWARD_PENDING");broadcast(r,"Victory confirmed. Claim your supported reward at the reward scene.");}
    else if(absent&&quiet>=120){state(r,"SAFE_CLOSED");broadcast(r,"Native terminal/revival acceptance is pending. Preview ends with no earned reward; use exit.");}continue;
   }
   if(!state.startsWith("PHASE"))continue;
   boolean online=false;for(String uid:keys(r.getAsJsonObject("members"))){EntityPlayerMP p=server.getPlayerList().getPlayerByUUID(UUID.fromString(uid));if(p!=null&&eligible(p,true)&&d.contains(p.getPosition()))online=true;}
   if(!online){int grace=number(r,"grace")+1;r.addProperty("grace",grace);if(grace>=RunRules.GRACE_TICKS){restore(d,r);state(r,"RECOVERY_CHECKPOINT");}continue;}r.addProperty("grace",0);
   if(!(body instanceof EntityLivingBase)||body.isDead){restore(d,r);state(r,"SAFE_CLOSED");continue;}
   if(!text(r,"run").equals(body.getEntityData().getString("HbvArenaRun"))){state(r,"SAFE_CLOSED");continue;}
   int healthPhase=RunRules.phase(((EntityLivingBase)body).getHealth(),((EntityLivingBase)body).getMaxHealth());int phase=Math.max(number(r,"phase"),healthPhase);if(healthPhase==0){state(r,"SAFE_CLOSED");continue;}
   r.addProperty("phase",phase);r.addProperty("state","PHASE"+phase);int clock=number(r,"clock");try{EnvironmentalBeats.tick(d,r,phase,clock);}catch(RuntimeException ex){state(r,"SAFE_CLOSED");broadcast(r,"Arena safety boundary closed this run. Exit remains available.");}clock=(clock+1)%120;r.addProperty("clock",clock);if(clock==0)r.addProperty("cycles",number(r,"cycles")+1);if(ticks%20==0)store().dirty();
  }
 }
 static void exit(EntityPlayerMP p,Definition d,JsonObject r){boolean retained=RewardArchive.retain(r,p.getUniqueID().toString());move(p,d.exit);if(!retained){tell(p,"Reward archive capacity reached. Your entitlement remains in this arena; claim before replay.");return;}r.getAsJsonObject("members").remove(p.getUniqueID().toString());store().dirty();if(r.getAsJsonObject("members").size()==0)state(r,"RESET_READY");}
 static void reset(Definition d,JsonObject r){
  WorldServer w=server.getWorld(0);for(EntityPlayerMP p:w.playerEntities.toArray(new EntityPlayerMP[0])){if(Math.abs(p.posX-d.boss_anchor[0])<80&&Math.abs(p.posZ-d.boss_anchor[2])<80)throw new IllegalStateException("Owned combat cell must be empty before reset");for(JsonElement cell:r.getAsJsonArray("cells")){JsonObject c=cell.getAsJsonObject();if(p.getEntityBoundingBox().intersects(new AxisAlignedBB(new BlockPos(number(c,"x"),number(c,"y"),number(c,"z")))))throw new IllegalStateException("Player intersects owned reset cell");}}
  for(String owner:keys(r.getAsJsonObject("members")))if(!RewardArchive.retain(r,owner))throw new IllegalStateException("Pending offline entitlement archive is full; retain this run before reset");
  if(!restore(d,r))throw new IllegalStateException("Journal conflict retained; whole-copy rollback required");
  Entity body=w.getEntityFromUuid(UUID.fromString(text(r,"boss_uuid")));if(body!=null){if(!text(r,"run").equals(body.getEntityData().getString("HbvArenaRun")))throw new IllegalStateException("Actor ownership mismatch");body.setDead();}
  state(r,"AVAILABLE");r.add("members",new JsonObject());store().dirty();
 }
 static final class ArenaCommand extends CommandBase {
 public String getName(){return "narutoarena";}public String getUsage(ICommandSender s){return "/narutoarena list|preview <id>|enter <id>|status|claim <0or1>|exit|reset <id>";}public int getRequiredPermissionLevel(){return 0;}
  public void execute(MinecraftServer srv,ICommandSender sender,String[] args)throws CommandException{
   try{if(args.length==0||args[0].equals("list")){for(Definition d:DEFINITIONS)sender.sendMessage(new TextComponentString(d.id+" — "+d.title+" [preview; earned acceptance pending]"));return;}
    if(args[0].equals("reset")){if(!sender.canUseCommand(2,"narutoarena"))throw new IllegalStateException("Operator required");Definition d=definition(args[1]);JsonObject r=run(d);if(r!=null)reset(d,r);return;}
    EntityPlayerMP p=getCommandSenderAsPlayer(sender);if(args[0].equals("intro")){ArenaIntro.start(p,definition(args[1]));return;}if(args[0].equals("join")){ArenaDirector.join(p,definition(args[1]));return;}if(args[0].equals("preview")||args[0].equals("enter")){if(args.length!=2)throw new IllegalArgumentException("Choose an arena ID");admit(p,definition(args[1]),args[0].equals("preview"));return;}
    for(Definition d:DEFINITIONS){JsonObject r=run(d);if(r!=null&&r.getAsJsonObject("members").has(p.getUniqueID().toString())){if(args[0].equals("exit")){exit(p,d,r);return;}if(args[0].equals("retry")){if(!text(r,"state").equals("RECOVERY_CHECKPOINT"))throw new IllegalStateException("No recovery checkpoint pending");r.addProperty("retry_owner",p.getUniqueID().toString());state(r,"PREPARING_RETRY");tell(p,"Reopening the saved checkpoint in bounded chunk batches.");return;}if(args[0].equals("claim")){if(p.getDistanceSq(new BlockPos(d.reward[0],d.reward[1],d.reward[2]))>64)throw new IllegalStateException("Reach the reward balcony first");if(args[1].equals("core"))tell(p,CoreRewardOutbox.enqueue(p,r)?"Owned matching set and moveset weapon received.":"Owned matching set and moveset weapon queued in Core outbox; clear inventory space.");else tell(p,RewardOutbox.deliver(p,r,Integer.parseInt(args[1]))?"Reward received once.":"Reward waiting: clear one inventory slot.");return;}tell(p,d.title+": "+text(r,"state")+" phase "+number(r,"phase")+"; preview="+r.get("preview"));return;}}if(args[0].equals("claim")){JsonObject archived=RewardArchive.pending(p);if(archived!=null){Definition d=definition(text(archived,"arena"));if(p.getDistanceSq(new BlockPos(d.reward[0],d.reward[1],d.reward[2]))>64)throw new IllegalStateException("Reach the reward balcony for your saved entitlement");if(args[1].equals("core"))tell(p,CoreRewardOutbox.enqueue(p,archived)?"Saved owned reward received.":"Saved owned reward queued; clear inventory space.");else tell(p,RewardOutbox.deliver(p,archived,Integer.parseInt(args[1]))?"Saved reward received once.":"Saved reward waiting for inventory space.");return;}}tell(p,"No active arena membership. Use /narutoarena list.");
   }catch(RuntimeException ex){throw new CommandException(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage());}
  }
 }
}
