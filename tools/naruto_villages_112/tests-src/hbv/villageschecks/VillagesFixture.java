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
 private boolean pending;private MinecraftServer server;private EntityPlayerMP player;private int assertions;private List<String> checks=new ArrayList<>();
 private Map<String,Object> result=new LinkedHashMap<>();
 @Mod.EventHandler public void started(FMLServerStartedEvent e){pending=true;MinecraftForge.EVENT_BUS.register(this);}
 private void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);checks.add(label);}
 @SubscribeEvent public void tick(TickEvent.ServerTickEvent e){if(!pending||e.phase!=TickEvent.Phase.END)return;pending=false;
  try{run();result.put("passed",true);}catch(Throwable failure){result.put("passed",false);result.put("error",failure.toString());try{StringWriter w=new StringWriter();failure.printStackTrace(new PrintWriter(w));Files.write(Paths.get("PRIVATE-VILLAGES-NATIVE-FAILURE.txt"),w.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
  finally{try{if(player!=null)server.getPlayerList().playerLoggedOut(player);}catch(Throwable f){result.put("logout_failure",f.toString());}result.put("assertions",assertions);result.put("checks",checks);result.put("scope","PRIVATE_NATIVE_ADMITTED_PLAYER_BANK_GUI_RELATION_DATA_PAYMENT");result.put("original_world_or_real_player_used",false);try{Files.write(Paths.get("PRIVATE-VILLAGES-NATIVE-RESULT.json"),new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8));}catch(Exception failure){failure.printStackTrace();}if(server!=null)server.initiateShutdown();}
 }
 private static Method method(Class<?> c,String[] names,Class<?>... signature)throws Exception{for(String name:names)try{return c.getMethod(name,signature);}catch(NoSuchMethodException ignored){}throw new NoSuchMethodException(c+Arrays.toString(names));}
 private void run()throws Exception {
  server=FMLCommonHandler.instance().getMinecraftServerInstance();Path cwd=Paths.get("").toAbsolutePath().normalize();
  check(Boolean.getBoolean("hbv.villages.fixture")&&cwd.getFileName().toString().matches("private-native-runtime-0[1-9]"),"dedicated disposable runtime guard");
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
