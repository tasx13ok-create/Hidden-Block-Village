package hbv.villageschecks;
import com.google.gson.GsonBuilder;import com.mojang.authlib.GameProfile;
import java.io.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.lang.reflect.*;
import net.minecraft.entity.player.*;import net.minecraft.init.Items;import net.minecraft.inventory.*;import net.minecraft.item.*;import net.minecraft.network.*;
import net.minecraft.server.MinecraftServer;import net.minecraft.server.management.PlayerInteractionManager;import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;import net.minecraftforge.fml.common.*;import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;import net.minecraftforge.fml.common.gameevent.TickEvent;
import narutodestinationvillages.compat.*;import narutodestinationvillages.bank.*;import narutodestinationvillages.config.*;
import narutodestinationvillages.data.*;import narutodestinationvillages.gui.*;import narutodestinationvillages.util.*;
/** Original private acceptance fixture, never part of the delivered addon. */
@Mod(modid="hbv_villages_checks",name="Private villages native checks",version="1",serverSideOnly=true,acceptableRemoteVersions="*",dependencies="required-after:narutodestinationvillages")
public final class VillagesFixture {
 private int state,ticks;private MinecraftServer server;private EntityPlayerMP player,victim;private int assertions;private List<String> checks=new ArrayList<>();
 private Map<String,Object> result=new LinkedHashMap<>();
 private net.minecraftforge.event.entity.living.LivingHurtEvent lastHurt;
 private NativeLearning.Choice learnedChoice;
 @SubscribeEvent(priority=net.minecraftforge.fml.common.eventhandler.EventPriority.LOWEST,receiveCanceled=true) public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e){if(e.getEntityLiving()==victim&&e.getSource().getTrueSource()==player)lastHurt=e;}
 @Mod.EventHandler public void started(FMLServerStartedEvent e){if(Boolean.getBoolean("hbv.villages.coldTraining"))return;state=1;MinecraftForge.EVENT_BUS.register(this);}
 private void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);checks.add(label);}
 @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){if(state==0||e.phase!=TickEvent.Phase.END)return;
  try{if(state==1){run();state=2;ticks=0;return;}player.connection.update();victim.connection.update();if(++ticks<80)return;
   if(state==2){firstKill();state=3;ticks=0;return;}if(state==3){repeatKill();state=4;ticks=0;return;}partialInventoryKill();result.put("passed",true);finish();
  }catch(Throwable failure){result.put("passed",false);result.put("error",failure.toString());try{StringWriter w=new StringWriter();failure.printStackTrace(new PrintWriter(w));Files.write(Paths.get("PRIVATE-VILLAGES-NATIVE-FAILURE.txt"),w.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}finish();}
 }
 private void finish(){state=0;try{if(victim!=null)server.getPlayerList().playerLoggedOut(victim);if(player!=null)server.getPlayerList().playerLoggedOut(player);}catch(Throwable f){result.put("logout_failure",f.toString());}result.put("assertions",assertions);result.put("checks",checks);result.put("scope","PRIVATE_NATIVE_ADMITTED_PLAYER_BANK_XP_PVP");result.put("original_world_or_real_player_used",false);try{Files.write(Paths.get("PRIVATE-VILLAGES-NATIVE-RESULT.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));}catch(Exception failure){failure.printStackTrace();}if(server!=null)server.initiateShutdown();}
 private static Method method(Class<?> c,String[] names,Class<?>... signature)throws Exception{for(String name:names)try{return c.getMethod(name,signature);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(c+Arrays.toString(names));}
 private void run()throws Exception {
  server=FMLCommonHandler.instance().getMinecraftServerInstance();Path cwd=Paths.get("").toAbsolutePath().normalize();
  check(Boolean.getBoolean("hbv.villages.fixture")&&cwd.getFileName().toString().matches("private-native-runtime-(0[1-9]|[1-9][0-9])"),"dedicated disposable runtime guard");
  check("villages-port-flat-01".equals(server.getFolderName())&&server.getServerPort()==25596,"exact private flat world and port");
  check(server.getPlayerList().getCurrentPlayerCount()==0,"no real players in fixture");
  check(server.isCallingFromMinecraftThread(),"native server thread");
  Map<String,net.minecraft.command.ICommand> commands=server.getCommandManager().getCommands();
  for(String name:new String[]{"villages","villageranks","villagesreload","narutopromote","villagerelation","narutotransport","villagebank","villagebankpages","villagepvp","territory","villageaffiliation"})check(commands.containsKey(name),"command registered: "+name);
  WorldServer world=server.getWorld(0);player=new EntityPlayerMP(server,world,new GameProfile(UUID.nameUUIDFromBytes("HiddenBlockVillage:VillagesPortNative01".getBytes(StandardCharsets.UTF_8)),"VillagesProbe"),new PlayerInteractionManager(world));
  QuietConnection connection=new QuietConnection(server,player);player.connection=connection;
  method(server.getPlayerList().getClass(),new String[]{"initializeConnectionToPlayer","func_72355_a"},NetworkManager.class,EntityPlayerMP.class,NetHandlerPlayServer.class).invoke(server.getPlayerList(),connection.manager,player,connection);
  check(server.getPlayerList().getPlayerByUUID(player.getUniqueID())==player,"official synthetic player admitted");
  check(!server.getPlayerList().canSendCommands(player.getGameProfile()),"ordinary non-OP player");
  ExtendedPlayer character=ExtendedPlayer.get(player);character.setAffiliation(9);character.setRank("Genin");character.updateRecord();
  check(character.getAffiliation()==9&&"Genin".equals(character.getRank()),"village/rank persisted through real adapter");
  check(ReflectionCache.getPlayerAffiliation(player)==9&&ReflectionCache.getPlayerRank(player)==1,"original reflection API bound to adapter");
  check(ExtendedPlayer.getConnection().isValid(2),"real SQLite connection");
  net.narutomod.PlayerTracker.addBattleXp(player,200);character.updateRecord();
  check(character.getAllSkillPoints()==200,"combat strength reads real Naruto battle XP");
  check(character.getBijuuKind()==net.narutomod.entity.EntityBijuManager.getTails(player),"Jinchuriki tails read native Biju manager");
  try(java.sql.PreparedStatement s=ExtendedPlayer.getConnection().prepareStatement("SELECT affiliation, rank, total_stats FROM player_data WHERE player_uuid=?")){s.setString(1,player.getUniqueID().toString());try(java.sql.ResultSet row=s.executeQuery()){check(row.next()&&row.getInt(1)==9&&row.getString(2).equals("Genin")&&row.getInt(3)==200,"offline database stores real combat strength and identity");}}
  check(NarutoRank.values().length==45&&NarutoRank.fromLegacyInt(23)==NarutoRank.KAGE&&NarutoRank.fromId("Hokage")!=null,"exact original public rank identities");
  BankManager manager=BankManager.getInstance();check(manager!=null&&manager.canAccessBank(player,9)&&!manager.canAccessBank(player,16),"bank affiliation access boundaries");
  manager.openBank(player,9);check(player.openContainer instanceof VillageBankContainer,"bank GUI opens native container");
  VillageBankContainer bank=(VillageBankContainer)player.openContainer;VillageBankInventory inv=bank.getBankInventory();
  check(inv.isOpen()&&bank.inventorySlots.size()==90,"54-slot bank with 36 player slots");
  for(int i=0;i<54;i++)check(inv.getStackInSlot(i)!=null,"native nonnull inventory slot "+i);
  player.inventory.setInventorySlotContents(9,new ItemStack(Items.DIAMOND,17));bank.slotClick(54,0,ClickType.QUICK_MOVE,player);
  check(player.inventory.getStackInSlot(9).isEmpty()&&inv.getStackInSlot(0).getCount()==17,"shift deposit preserves exact item count");
  bank.slotClick(0,0,ClickType.QUICK_MOVE,player);check(inv.getStackInSlot(0).isEmpty()&&inventoryCount(Items.DIAMOND)==17,"shift withdraw preserves exact item count");
  player.inventory.clear();player.inventory.setItemStack(new ItemStack(Items.EMERALD,9).setStackDisplayName("Native bank NBT test"));bank.slotClick(0,0,ClickType.PICKUP,player);
  check(player.inventory.getItemStack().isEmpty()&&inv.getStackInSlot(0).getCount()==9,"normal click deposit");
  bank.slotClick(0,0,ClickType.PICKUP,player);check(player.inventory.getItemStack().getCount()==9&&inv.getStackInSlot(0).isEmpty(),"normal click withdrawal");
  bank.slotClick(0,0,ClickType.PICKUP,player);manager.getStorage().setPageMode(9,0,PageMode.DEPOSIT_ONLY);
  bank.slotClick(0,0,ClickType.PICKUP,player);check(player.inventory.getItemStack().isEmpty()&&inv.getStackInSlot(0).getCount()==9,"deposit-only blocks click withdrawal");
  bank.slotClick(0,0,ClickType.QUICK_MOVE,player);check(inv.getStackInSlot(0).getCount()==9&&inventoryCount(Items.EMERALD)==0,"deposit-only blocks shift withdrawal");
  manager.getStorage().setPageMode(9,0,PageMode.WITHDRAW_ONLY);player.inventory.setItemStack(new ItemStack(Items.DIAMOND,3));bank.slotClick(1,0,ClickType.PICKUP,player);
  check(player.inventory.getItemStack().getCount()==3&&inv.getStackInSlot(1).isEmpty(),"withdraw-only blocks click deposit");player.inventory.setItemStack(ItemStack.EMPTY);
  manager.getStorage().setPageMode(9,0,PageMode.NORMAL);check(BankStorage.MAX_PAGES==2,"explicit two-page fixture config applied before storage initialization");manager.getStorage().setUnlockedPages(9,2);bank.slotClick(53,0,ClickType.PICKUP,player);
  check(inv.getCurrentPage()==1&&inv.getStackInSlot(0).isEmpty(),"page next opens independent storage");bank.slotClick(45,0,ClickType.PICKUP,player);
  check(inv.getCurrentPage()==0&&inv.getStackInSlot(0).getCount()==9,"page previous restores saved stack");
  player.closeContainer();manager.forceSave();BankStorage loaded=new BankData(narutodestinationvillages.NarutoDestinationVillages.instance.getDataDir()).load();
  check(loaded.getPage(9,0)[0].getCount()==9&&loaded.getUnlockedPages(9)==2,"bank disk reload preserves stacks and unlocked pages");
  check(loaded.getPage(9,0)[0].getDisplayName().equals("Native bank NBT test"),"bank disk reload preserves custom item NBT");
  check(loaded.acquireSortLock(9)&&!loaded.acquireSortLock(9),"bank sort lock excludes concurrent sort");loaded.releaseSortLock(9);
  BankSorter.sortBank(loaded,9);check(loaded.getTotalItemCount(9)==9,"bank sorting conserves total item count");
  player.inventory.clear();player.inventory.setItemStack(new ItemStack(Items.DIAMOND,6));VillagesGUIManager.getInstance().openGui(player);
  check(player.openContainer instanceof VillagesContainer,"village hub opens native GUI");
  int held=player.inventory.getItemStack().isEmpty()?0:player.inventory.getItemStack().getCount();int carriedConserved=inventoryCount(Items.DIAMOND)+held+groundCount(Items.DIAMOND);result.put("carried_item_after_hub_open",Arrays.asList(inventoryCount(Items.DIAMOND),held,groundCount(Items.DIAMOND)));
  check(carriedConserved==6,"opening village GUI conserves carried item through native inventory/cursor/drop handling");player.closeContainer();
  check(RyoPaymentHelper.isAvailable(),"actual installed currency items resolve");player.inventory.clear();Item gold=AddedItems.RyoGold;player.inventory.setInventorySlotContents(0,new ItemStack(gold,1));
  check(RyoPaymentHelper.getPlayerRyoBalance(player)==81,"original currency denomination retained");check(RyoPaymentHelper.takePayment(player,10)&&RyoPaymentHelper.getPlayerRyoBalance(player)==71,"currency exact payment makes change without loss");
  long balance=RyoPaymentHelper.getPlayerRyoBalance(player);check(!RyoPaymentHelper.takePayment(player,9999)&&RyoPaymentHelper.getPlayerRyoBalance(player)==balance,"insufficient payment leaves inventory unchanged");
  player.inventory.clear();Item note=Legacy112.findItem("narutomod","ryo_100");player.inventory.setInventorySlotContents(0,new ItemStack(note));
  check(RyoPaymentHelper.getPlayerRyoBalance(player)==100,"actual Naruto Ryo note accepted");check(RyoPaymentHelper.takePayment(player,10)&&RyoPaymentHelper.getPlayerRyoBalance(player)==90,"native Naruto note makes exact conserved change");
  player.inventory.clear();for(int i=0;i<36;i++)player.inventory.setInventorySlotContents(i,new ItemStack(Items.DIAMOND,64));player.inventory.setInventorySlotContents(0,new ItemStack(gold,64));
  balance=RyoPaymentHelper.getPlayerRyoBalance(player);check(!RyoPaymentHelper.takePayment(player,10)&&RyoPaymentHelper.getPlayerRyoBalance(player)==balance&&inventoryCount(Items.DIAMOND)==35*64,"full inventory change refusal is atomic with no missing items");
  RelationshipData.setRelationship("Konoha","Suna",RelationshipData.RelationType.WAR,"fixture");RelationshipData.reload();
  check(RelationshipData.getRelationship("Suna","Konoha")==RelationshipData.RelationType.WAR,"symmetric village war survives disk reload");
  TransportData.setLocation("Konoha",10,70,10,0,0,"0","fixture");check(TransportData.hasLocation("Konoha"),"village transport location saved");
  check(!TransportData.unlockForPlayer(player.getUniqueID().toString(),"Missing"),"unknown transport destination cannot be unlocked");
  TransportData.setLocation("Suna",20,70,20,0,0,"0","fixture");TransportData.setAlwaysUnlocked("Suna",false);
  check(TransportData.unlockForPlayer(player.getUniqueID().toString(),"Suna"),"existing transport destination can be unlocked");TransportData.reload();check(TransportData.isUnlockedForPlayer(player.getUniqueID().toString(),"Suna")&&TransportData.getPlayerUnlocks(player.getUniqueID().toString()).contains("Suna"),"transport unlock survives reload");
  List<String> unresolved=new ArrayList<>();for(PvPConfig.LootEntry entry:PvPConfig.getLootPool()){String[] parts=entry.itemId.split(":");if(Legacy112.findItem(parts[0],parts[1])==null)unresolved.add(entry.itemId);}
  result.put("unresolved_original_loot_ids",unresolved);result.put("default_loot_mapping_complete",unresolved.isEmpty());
  xpRewards();trainingRewards();prepareCombat();
 }
 private void xpRewards()throws Exception {
  check(server.getCommandManager().getCommands().containsKey("villagereward"),"XP reward issuer registered");
  check(!server.getCommandManager().getCommands().get("villagereward").checkPermission(server,player),"ordinary player cannot issue reward tokens");
  check(XpRankMultipliers.defaults().size()==45&&NativeXpRewards.awardFor("Kage",50,2)==200,"original rank bonus metadata and whole-stack rounding retained");
  for(int value:new int[]{1,5,10,50})check(NativeRewardItems.xpValue(NativeRewardItems.create("NarutoDestination:xptoken",value,1))==value,"legacy XP denomination creates marked native reward "+value);
  player.inventory.clear();player.inventory.currentItem=0;player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,NativeRewardItems.create("NarutoDestination:xptoken",5,3));
  double xp=net.narutomod.PlayerTracker.getBattleXp(player);
  net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem click=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(player,net.minecraft.util.EnumHand.MAIN_HAND);MinecraftForge.EVENT_BUS.post(click);
  check(click.isCanceled()&&player.getHeldItemMainhand().getCount()==2&&net.narutomod.PlayerTracker.getBattleXp(player)==xp+5,"ordinary right-click consumes one token and awards actual Naruto battle XP");
  xp=net.narutomod.PlayerTracker.getBattleXp(player);player.setSneaking(true);click=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(player,net.minecraft.util.EnumHand.MAIN_HAND);MinecraftForge.EVENT_BUS.post(click);player.setSneaking(false);
  check(click.isCanceled()&&player.getHeldItemMainhand().isEmpty()&&net.narutomod.PlayerTracker.getBattleXp(player)==xp+11,"sneak-right-click consumes remaining stack with original rank rounding");
  xp=net.narutomod.PlayerTracker.getBattleXp(player);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(Items.PAPER,3));click=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(player,net.minecraft.util.EnumHand.MAIN_HAND);MinecraftForge.EVENT_BUS.post(click);
  check(!click.isCanceled()&&player.getHeldItemMainhand().getCount()==3&&net.narutomod.PlayerTracker.getBattleXp(player)==xp,"ordinary paper cannot grant native XP");
  ItemStack invalid=NativeRewardItems.create("NarutoDestination:xptoken",5,2);invalid.getTagCompound().getCompoundTag(NativeRewardItems.TAG).setInteger("value",2);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,invalid);
  check(!NativeXpRewards.redeem(player,net.minecraft.util.EnumHand.MAIN_HAND)&&player.getHeldItemMainhand().getCount()==2&&net.narutomod.PlayerTracker.getBattleXp(player)==xp,"invalid XP token value is refused without consumption");
  result.put("native_xp_redemption_verified",true);player.inventory.clear();
 }
 private void prepareCombat()throws Exception {
  net.minecraftforge.common.config.Configuration cfg=new net.minecraftforge.common.config.Configuration(new File(narutodestinationvillages.NarutoDestinationVillages.instance.getConfigDir(),"PvP.cfg"));cfg.load();
  cfg.get("loot_pool","lootPool",new String[0]).set(new String[]{"NarutoDestination:xptoken:5:8:1"});cfg.get("loot_drops","maxDropsPerKill",1).set(1);
  cfg.get("multipliers_relation","useRelationMultipliers",false).set(false);cfg.get("multipliers_strength","useStrengthMultipliers",false).set(false);cfg.save();PvPConfig.reload();
  check(PvPConfig.getLootPool().size()==1&&!PvPConfig.useRelationMultipliers&&!PvPConfig.useStrengthMultipliers,"explicit private XP-only pool loaded through actual config reload");
  WorldServer world=server.getWorld(0);victim=new EntityPlayerMP(server,world,new GameProfile(UUID.nameUUIDFromBytes("HiddenBlockVillage:VillagesPortVictim01".getBytes(StandardCharsets.UTF_8)),"VillagesVictim"),new PlayerInteractionManager(world));QuietConnection connection=new QuietConnection(server,victim);victim.connection=connection;
  method(server.getPlayerList().getClass(),new String[]{"initializeConnectionToPlayer","func_72355_a"},NetworkManager.class,EntityPlayerMP.class,NetHandlerPlayServer.class).invoke(server.getPlayerList(),connection.manager,victim,connection);
  check(server.getPlayerList().getCurrentPlayerCount()==2&&!server.getPlayerList().canSendCommands(victim.getGameProfile()),"second official non-OP player admitted for real native damage");
  ExtendedPlayer.get(victim).setAffiliation(9);ExtendedPlayer.get(victim).setRank("Genin");net.narutomod.PlayerTracker.addBattleXp(victim,200);
  check(NativeTraining.credit(victim,"skill",5)&&NativeTraining.spend(victim,NativeTraining.Stat.SPEED,5),"private respawn subject has real saved speed allocation");
 }
 private void firstKill(){
  verifyCapacityAndReset();
  ItemStack learned=player.getHeldItemMainhand();check(learnedChoice!=null&&NativeLearning.learned(learned,learnedChoice),"learned native technique remains unlocked after eighty real native updates");player.getInventoryEnderChest().setInventorySlotContents(0,learned);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);
  player.setPositionAndUpdate(0.5,4,0.5);victim.setPositionAndUpdate(2.5,4,0.5);float before=victim.getHealth();boolean hit=victim.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player),2);
  result.put("same_village_native_damage",Arrays.asList(before,victim.getHealth(),hit));check(victim.getHealth()==before&&lastHurt!=null&&lastHurt.isCanceled(),"same-village protection cancels native hurt with no HP loss even when attack returns true");
  ExtendedPlayer.get(victim).setAffiliation(16);hit=victim.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player),4);
  result.put("war_native_damage",Arrays.asList(before,victim.getHealth(),hit));check(hit&&victim.getHealth()<before,"war relation permits actual native player damage");
  victim.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player),1000);check(!victim.isEntityAlive(),"actual native player death occurred");
  check(tokenCount(false)==8,"real native death callback delivers exact configured XP reward once");
  int rewardSlot=-1;for(int i=0;i<36;i++)if(NativeRewardItems.xpValue(player.inventory.getStackInSlot(i))==5){rewardSlot=i;break;}check(rewardSlot>=0,"earned token remains an identifiable inventory stack");
  if(rewardSlot<9)player.inventory.currentItem=rewardSlot;else{ItemStack previous=player.inventory.getStackInSlot(0);player.inventory.setInventorySlotContents(0,player.inventory.getStackInSlot(rewardSlot));player.inventory.setInventorySlotContents(rewardSlot,previous);player.inventory.currentItem=0;}
  double xp=net.narutomod.PlayerTracker.getBattleXp(player);boolean redeemed=NativeXpRewards.redeem(player,net.minecraft.util.EnumHand.MAIN_HAND);result.put("earned_xp_token_redemption",Arrays.asList(xp,net.narutomod.PlayerTracker.getBattleXp(player),tokenCount(false),redeemed));check(redeemed&&net.narutomod.PlayerTracker.getBattleXp(player)==xp+5&&tokenCount(false)==7,"selected PvP-earned reward redeems into actual Naruto XP");
  victim=server.getPlayerList().recreatePlayerEntity(victim,0,false);check(server.getPlayerList().getPlayerByUUID(victim.getUniqueID())==victim&&ExtendedPlayer.get(victim).getAffiliation()==16&&NativeTraining.points(victim,NativeTraining.Stat.SPEED)==5,"native respawn preserves village identity and allocated training for same profile");
 }
 private void repeatKill(){
  check(victim.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue()>victim.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED).getBaseValue(),"respawned player's saved training restores actual movement attribute");
  victim.setPositionAndUpdate(2.5,4,0.5);int before=tokenCount(false);victim.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player),1000);
  check(!victim.isEntityAlive()&&tokenCount(false)==before,"anti-farm prevents another reward across real respawn and repeated native kill");
  victim=server.getPlayerList().recreatePlayerEntity(victim,0,false);
  net.minecraftforge.common.config.Configuration cfg=new net.minecraftforge.common.config.Configuration(new File(narutodestinationvillages.NarutoDestinationVillages.instance.getConfigDir(),"PvP.cfg"));cfg.load();cfg.get("anti_farm","antiFarmEnabled",false).set(false);cfg.save();PvPConfig.reload();
  check(!PvPConfig.antiFarmEnabled,"explicit private anti-farm override loaded for partial-insert regression");
  player.inventory.clear();for(int i=0;i<36;i++)player.inventory.setInventorySlotContents(i,new ItemStack(Items.DIAMOND,64));player.inventory.setInventorySlotContents(0,NativeRewardItems.create("NarutoDestination:xptoken",5,60));
 }
 private void partialInventoryKill(){
  victim.setPositionAndUpdate(2.5,4,0.5);int ground=tokenCount(true);victim.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(player),1000);
  check(!victim.isEntityAlive()&&tokenCount(false)==64&&tokenCount(true)-ground==4,"partial inventory insert keeps four and drops only four of eight earned tokens");
  result.put("native_pvp_damage_death_respawn_antifarm_verified",true);result.put("partial_pvp_reward_inventory_count",tokenCount(false));result.put("partial_pvp_reward_ground_delta",tokenCount(true)-ground);
  Map<String,Object> expected=new LinkedHashMap<>();expected.put("skill_points",NativeTraining.balance(player,"skill"));expected.put("jutsu_points",NativeTraining.balance(player,"jutsu"));expected.put("reset_points",NativeTraining.balance(player,"reset"));expected.put("battle_xp",net.narutomod.PlayerTracker.getBattleXp(player));expected.put("technique",learnedChoice.technique.unlocalizedName);expected.put("technique_index",learnedChoice.technique.index);expected.put("victim_speed_points",NativeTraining.points(victim,NativeTraining.Stat.SPEED));try{Files.write(Paths.get("PRIVATE-TRAINING-RESTART-EXPECTED.json"),new GsonBuilder().setPrettyPrinting().create().toJson(expected).getBytes(StandardCharsets.UTF_8));}catch(Exception failure){throw new IllegalStateException(failure);}
 }
 private int tokenCount(boolean ground){int count=0;if(ground){for(net.minecraft.entity.Entity entity:player.world.loadedEntityList)if(entity instanceof net.minecraft.entity.item.EntityItem){ItemStack stack=((net.minecraft.entity.item.EntityItem)entity).getItem();if(NativeRewardItems.xpValue(stack)==5)count+=stack.getCount();}}else for(int i=0;i<player.inventory.getSizeInventory();i++){ItemStack stack=player.inventory.getStackInSlot(i);if(NativeRewardItems.xpValue(stack)==5)count+=stack.getCount();}return count;}
 private double baselineFist,baselineWeapon,baselineJutsu;
 private double cowDamage(net.minecraft.util.DamageSource source){net.minecraft.entity.passive.EntityCow cow=new net.minecraft.entity.passive.EntityCow(player.world);cow.setPosition(player.posX+4,player.posY,player.posZ);player.world.getChunk(cow.getPosition());check(player.world.spawnEntity(cow),"disposable native damage subject spawned in explicitly loaded owned fixture geometry");float before=cow.getHealth();cow.attackEntityFrom(source,2);double lost=before-cow.getHealth();cow.setDead();return lost;}
 private void trainingRewards(){
  net.narutomod.Chakra.pathway(player);check("2".equals(System.getProperty("narutovillages.chakraHook")),"two canonical Naruto chakra maximum calculation hooks are active");
  double xp=net.narutomod.PlayerTracker.getBattleXp(player);player.inventory.clear();player.inventory.currentItem=0;player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,NativeRewardItems.create("NarutoDestination:sptoken",10,5));
  net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem click=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(player,net.minecraft.util.EnumHand.MAIN_HAND);MinecraftForge.EVENT_BUS.post(click);
  check(click.isCanceled()&&NativeTraining.balance(player,"skill")==10&&player.getHeldItemMainhand().getCount()==4,"SP token banks actual spendable skill points");
  player.setSneaking(true);click=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(player,net.minecraft.util.EnumHand.MAIN_HAND);MinecraftForge.EVENT_BUS.post(click);player.setSneaking(false);
  check(NativeTraining.balance(player,"skill")==50&&player.getHeldItemMainhand().isEmpty()&&net.narutomod.PlayerTracker.getBattleXp(player)==xp,"whole-stack SP consumption adds points without substituting XP");
  baselineFist=cowDamage(net.minecraft.util.DamageSource.causePlayerDamage(player));player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));baselineWeapon=cowDamage(net.minecraft.util.DamageSource.causePlayerDamage(player));player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);baselineJutsu=cowDamage(net.narutomod.item.ItemJutsu.causeJutsuDamage(player,player));
  double speed=player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();NativeTrainingMenu.open(player);check(player.openContainer instanceof NativeTrainingMenu,"training opens actual native chest container");
  for(int slot:new int[]{10,12,14,16})player.openContainer.slotClick(slot,1,ClickType.PICKUP,player);
  check(NativeTraining.balance(player,"skill")==10&&NativeTraining.assigned(player)==40,"four server menu actions allocate ten points each with exact SP costs");
  check(player.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue()>speed,"speed allocation changes actual native movement attribute");
  check(!NativeTraining.spend(player,NativeTraining.Stat.SPEED,-1)&&!NativeTraining.spend(player,NativeTraining.Stat.SPEED,500)&&NativeTraining.balance(player,"skill")==10,"negative and unaffordable training cannot change balances");
  check(!NativeTraining.reset(player)&&NativeTraining.assigned(player)==40,"allocated training cannot reset without a reset credit");
  player.openContainer.slotClick(10,1,ClickType.THROW,player);check(NativeTraining.balance(player,"skill")==10&&inventoryCount(Items.BLAZE_POWDER)==0,"training GUI throw action cannot mint its displayed items");player.closeContainer();
  double fist=cowDamage(net.minecraft.util.DamageSource.causePlayerDamage(player));player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));double weapon=cowDamage(net.minecraft.util.DamageSource.causePlayerDamage(player));player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,ItemStack.EMPTY);double jutsu=cowDamage(net.narutomod.item.ItemJutsu.causeJutsuDamage(player,player));
  result.put("training_native_damage",Arrays.asList(baselineFist,fist,baselineWeapon,weapon,baselineJutsu,jutsu));check(fist>baselineFist&&weapon>baselineWeapon&&jutsu>baselineJutsu,"allocated disciplines increase actual unarmed weapon and native jutsu damage");
  player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,NativeRewardItems.create("NarutoDestination:rpToken",1,1));check(NativeTraining.redeem(player,net.minecraft.util.EnumHand.MAIN_HAND)&&NativeTraining.balance(player,"reset")==1&&NativeTraining.assigned(player)==40,"reset token banks a credit without immediately erasing training");
  player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,NativeRewardItems.create("NarutoDestination:jptoken",50,1));check(NativeTraining.redeem(player,net.minecraft.util.EnumHand.MAIN_HAND)&&NativeTraining.balance(player,"jutsu")==50&&net.narutomod.PlayerTracker.getBattleXp(player)==xp,"JP token banks learning points without substituting XP");
  Item item=Legacy112.findItem("narutomod","katon");check(item instanceof net.narutomod.item.ItemJutsu.Base,"actual Naruto Fire Release item is available for learning");ItemStack scroll=new ItemStack(item);net.narutomod.item.ItemJutsu.Base base=(net.narutomod.item.ItemJutsu.Base)item;base.setOwner(scroll,player);base.enableAllJutsus(scroll,false);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,scroll);
  List<NativeLearning.Choice> choices=NativeLearning.choices(player);check(!choices.isEmpty(),"held owned scroll discovers supported techniques through native public API");NativeLearning.Choice choice=choices.get(0);learnedChoice=choice;long before=NativeTraining.balance(player,"jutsu");NativeTrainingMenu.open(player);player.openContainer.slotClick(32,0,ClickType.PICKUP,player);
  check(NativeLearning.learned(scroll,choice)&&NativeTraining.balance(player,"jutsu")==before-choice.cost,"JP menu action unlocks actual native technique with its required knowledge state");
  check(!NativeLearning.learn(player,choice)&&NativeTraining.balance(player,"jutsu")==before-choice.cost,"already learned technique cannot charge JP again");player.closeContainer();
  ItemStack restored=new ItemStack(scroll.writeToNBT(new net.minecraft.nbt.NBTTagCompound()));check(NativeLearning.learned(restored,choice),"native learned technique survives actual item NBT serialization roundtrip");player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,restored);
  VillagesGUIManager.getInstance().openGui(player);player.openContainer.slotClick(48,0,ClickType.PICKUP,player);
  result.put("native_training_point_spend_and_learning_verified",true);
 }
 private void verifyCapacityAndReset(){
  check(player.openContainer instanceof NativeTrainingMenu,"village hub training entry opens after real delayed command tick");player.closeContainer();
  double maximum=net.narutomod.Chakra.pathway(player).getMax(),base=net.narutomod.PlayerTracker.getBattleXp(player)*0.5;result.put("canonical_training_chakra_maximum",Arrays.asList(base,maximum));result.put("canonical_training_chakra_points",Arrays.asList(NativeTraining.points(player,NativeTraining.Stat.NINJUTSU),NativeTraining.points(player,NativeTraining.Stat.TAIJUTSU),NativeTraining.chakraMaximum(base,player)));
  check(Math.abs(maximum-(base+12.5))<0.0001,"SP bonus remains exactly once in canonical chakra maximum after eighty native updates");
  long total=NativeTraining.totalSkillPoints(player);double xp=net.narutomod.PlayerTracker.getBattleXp(player);check(NativeTraining.reset(player)&&NativeTraining.balance(player,"skill")==50&&NativeTraining.balance(player,"reset")==0&&NativeTraining.assigned(player)==0,"one reset credit reclaims exactly allocated SP");
  check(NativeTraining.totalSkillPoints(player)==total&&net.narutomod.PlayerTracker.getBattleXp(player)==xp&&net.narutomod.Chakra.pathway(player).getAmount()==0,"reset preserves earned SP and native XP while clearing native chakra");
  check(!NativeTraining.reset(player)&&NativeTraining.balance(player,"skill")==50,"repeated reset cannot create points");result.put("native_training_canonical_capacity_and_reset_verified",true);
 }
 private int inventoryCount(Item item){int count=0;for(int i=0;i<player.inventory.getSizeInventory();i++){ItemStack s=player.inventory.getStackInSlot(i);if(!s.isEmpty()&&s.getItem()==item)count+=s.getCount();}return count;}
 private int groundCount(Item item){int count=0;for(net.minecraft.entity.Entity entity:player.world.loadedEntityList)if(entity instanceof net.minecraft.entity.item.EntityItem){ItemStack s=((net.minecraft.entity.item.EntityItem)entity).getItem();if(!s.isEmpty()&&s.getItem()==item)count+=s.getCount();}return count;}
 public static final class QuietConnection extends NetHandlerPlayServer {
  final NetworkManager manager;QuietConnection(MinecraftServer s,EntityPlayerMP p)throws Exception{this(s,p,network());}
  private QuietConnection(MinecraftServer s,EntityPlayerMP p,NetworkManager n){super(s,n,p);manager=n;}
  private static NetworkManager network()throws Exception{NetworkManager n=new NetworkManager(EnumPacketDirection.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(n){@Override protected java.net.SocketAddress remoteAddress0(){return new java.net.InetSocketAddress("127.0.0.1",0);}};method(n.getClass(),new String[]{"setConnectionState","func_150723_a"},EnumConnectionState.class).invoke(n,EnumConnectionState.PLAY);return n;}
  @Override public void sendPacket(Packet<?> packet){}
 }
}
