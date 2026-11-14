package village.vale.arsenal;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import village.vale.ArsenalAccess;
import village.vale.Diagnostics;
import village.vale.Gear;
import village.vale.Order;
import village.vale.OriginalArmorRegistry;
import village.vale.PlayerState;
import village.vale.Quartermaster;
import village.vale.RewardLedger;
import village.vale.ValeOrders;

/** Disposable native acceptance mod. Never packaged into the production Core jar. */
@Mod(modid="valerecoverychecks", name="Private Vale Recovery Checks", version="0.3.16",
     dependencies="required-after:valeorders;required-after:valerack;required-after:valestandardschecks", acceptableRemoteVersions="*", serverSideOnly=true)
public final class RecoveryNativeChecks {
    private final List<String> passed = new ArrayList<>();
    private int assertions;
    private boolean ran;
    private ProbePlayer player;
    private final ArsenalCrafting crafting = new ArsenalCrafting();

    @Mod.EventHandler public void started(FMLServerStartedEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }
    private void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private void passed(String message) {
        passed.add(message);
        System.out.println("VALE_RECOVERY039_CHECK_PASS: " + message);
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (ran || event.phase != TickEvent.Phase.END) return;
        ran = true;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        JsonObject report = new JsonObject();
        Throwable failure = null;
        try {
            if (!Boolean.getBoolean("valeorders.recoverycheck") || !"vale-recovery-test".equals(server.getFolderName())
                    || !new File(".vale-recovery-test").isFile()) {
                throw new IllegalStateException("Private fixture requires explicit flag, vale-recovery-test world and .vale-recovery-test marker");
            }
            check(server.getPlayerList().getPlayers().isEmpty(), "Private fixture found real connected players");
            report.addProperty("core_sha256", sha256(net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get(ValeOrders.ID).getSource()));
            JsonObject loaded = new JsonObject();
            for (net.minecraftforge.fml.common.ModContainer mod : net.minecraftforge.fml.common.Loader.instance().getModList()) {
                File source = mod.getSource();
                if (source != null && source.isFile() && source.getParentFile().getName().equals("mods")) {
                    JsonObject identity = new JsonObject();identity.addProperty("version", mod.getVersion());
                    identity.addProperty("file", source.getName());identity.addProperty("sha256", sha256(source));
                    loaded.add(mod.getModId(), identity);
                }
            }
            report.add("loaded_fixture_mods", loaded);
            report.addProperty("minecraft_version", server.getMinecraftVersion());
            report.addProperty("forge_version", net.minecraftforge.common.ForgeVersion.getVersion());
            Diagnostics.verify();
            check(Gear.ITEMS.size() == 556, "Current item registry is not 556");
            passed("556 live registered items and current diagnostic category checks");
            player = login(server, false);
            ItemStack first = craftedWeapon(true, false);
            check(first.getItem() == Arsenal.item(ArsenalSpec.WARD_FAN), "Native delayed craft produced wrong weapon");
            passed("Native result-slot pickup remains issued after expired preview timeout");
            clearInventory(player);
            craftedWeapon(false, true);
            passed("Stale proof is recomputed by native container before actual crafting");
            cooldownReconnect(server);
            nativePartyRejoin();
            nativeCompanionClosure();
            quartermaster();
            assertions += ScrollNativeChecks038.run(player);
            passed("Native companion orders, target protection, unloaded recall, stale leases, repair and allied support");
            assertions += ScrollCastNativeChecks039.run(player);
            passed("Native scroll cast reentry, resolve reservation, held-stack replacement and protected return teleport");
            assertions += ReedEscapeNativeChecks039.run(player);
            passed("Native Reed Escape teleport protection, recursive callbacks and rejected movement cleanup");
            assertions += ClawNativeChecks.run(player);
            passed("Native Crescent Talons lock, dodgeable lunge, cancellation, rear damage and bounded bleed");
            assertions += village.valerack.RackNativeChecks038.run(player);
            passed("Native combat hotbar: physical loadouts, cursor transactions, ownership, charge cancellation and death");
            passed("Native six mastered techniques cast from the switched physical combat hotbar");
            assertions += village.valerack.CombatHotbarLifecycleNativeChecks.run(player);
            passed("Native combat hotbar final-canceled death, keepInventory, native drops and end-clone ownership");
            passed("Native six-slot quick stow/equip, full inventory, ownership, active-use cancellation, serialization and death drops");
            try {
                assertions += (Integer)Class.forName("village.standards.StandardsNativeChecks")
                    .getMethod("run", EntityPlayerMP.class).invoke(null, player);
            } catch (java.lang.reflect.InvocationTargetException wrapped) {
                Throwable cause = wrapped.getCause();
                if (cause instanceof Exception) throw (Exception)cause;
                if (cause instanceof Error) throw (Error)cause;
                throw wrapped;
            }
            passed("Native village standards: all five villages/four walls, bounds, support, cancellation, NBT, drops and opaque recovery");
            assertions += village.vale.melee.MeleeNativeChecks.run(player);
            passed("Native six issued weapon combos, cooled contacts, final cancellation and persistent recovery");
            assertions += WeaponPulseNativeChecks.run(player);
            passed("Native 27 weapon presentation lifecycles, bounded carriers and cosmetic-failure gameplay isolation");
            assertions += HandTechniqueNativeChecks.run(player);
            passed("Native hand-technique carrier lifecycle, cosmetic spawn failure charge, observed final-cancellation/absorption contacts and cleanup");
            assertions += village.vale.nature.NatureNativeChecks.run(player);
            passed("Native five distinct nature techniques, protected temporary Earth cover and effect settlement");
            assertions += village.vale.expedition.GateNativeChecks.run(player);
            passed("Native expedition adapter: synthetic measured route, nonce/protection, owner/run actors, defend/escort/skiff, rewards and restart identity");
            assertions += village.vale.DodgeNativeChecks.run(player);
            passed("Native four-way paid dodge, collision/protection, saved cooldown/air use, grace and lifecycle");
            assertions += village.vale.AcademyNativeChecks.run(server);
            passed("Native academy enrollment, walking, guard, substitution, arts, crafting, dispatch, ambush and reconnect");
            passed("Native Lightning Armor acquisition, mode, movement, accepted counters, owned trial and mastery");
            assertions += village.vale.WornArmorNativeChecks.run(player);
            passed("Native worn armor registered getters, conditional Lightning and packaged assets");
            assertions += village.vale.bridge.SharedCostNativeChecks.run(player);
            passed("Native shared-resource accepted casts, canceled effects, mode upkeep and callback settlement");
            report.addProperty("passed", true);
        } catch (Throwable error) {
            failure = error;
            report.addProperty("passed", false);
            report.addProperty("failure", error.toString());
            error.printStackTrace();
        } finally {
            try {
                if (player != null && server.getPlayerList().getPlayerByUUID(player.getUniqueID()) == player)
                    server.getPlayerList().playerLoggedOut(player);
            } catch (Throwable cleanup) {
                if (failure == null) { failure = cleanup; report.addProperty("passed", false); report.addProperty("failure", cleanup.toString()); }
                cleanup.printStackTrace();
            }
            report.addProperty("schema", 1);
            report.addProperty("fixture", "native-recovery-0.3.9");
            report.addProperty("assertions", assertions);
            JsonArray checks = new JsonArray();for (String value : passed) checks.add(value);report.add("checks", checks);
            try {
                Files.write(new File("native-recovery039-result.json").toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
            } catch (Exception writing) { failure = writing; writing.printStackTrace(); }
            System.out.println(failure == null ? "VALE_RECOVERY039_NATIVE_PASS: " + assertions + " assertions" : "VALE_RECOVERY039_NATIVE_FAIL");
            server.initiateShutdown();
        }
    }
    private ProbePlayer login(MinecraftServer server, boolean restore) {
        ProbePlayer p = new ProbePlayer(server, server.getWorld(0));
        p.connection = new QuietConnection(server, p);
        if (restore) server.getPlayerList().readPlayerDataFromFile(p);
        p.interactionManager.setGameType(GameType.SURVIVAL);
        server.getPlayerList().playerLoggedIn(p);
        FMLCommonHandler.instance().firePlayerLoggedIn(p);
        p.openContainer = p.inventoryContainer;
        return p;
    }
    @SuppressWarnings("unchecked") private static Map<String,Object> tickets() throws Exception {
        Field field = ArsenalCrafting.class.getDeclaredField("TICKETS");field.setAccessible(true);
        return (Map<String,Object>)field.get(null);
    }
    private ItemStack craftedWeapon(boolean delayed, boolean stale) throws Exception {
        player.inventory.setItemStack(ItemStack.EMPTY);
        ContainerPlayer container = (ContainerPlayer)player.inventoryContainer;
        for (int i=0;i<container.craftMatrix.getSizeInventory();i++) container.craftMatrix.setInventorySlotContents(i, ItemStack.EMPTY);
        container.craftMatrix.setInventorySlotContents(0, new ItemStack(Items.IRON_SWORD));
        container.craftMatrix.setInventorySlotContents(1, new ItemStack(Items.DIAMOND));
        container.craftMatrix.setInventorySlotContents(2, new ItemStack(Items.FEATHER));
        ItemStack preview = container.craftResult.getStackInSlot(0);
        check(ArsenalCrafting.eligible(preview) && preview.hasTagCompound(), "Native preview lacks eligible proof");
        String nonce = preview.getTagCompound().getString(ArsenalCrafting.PROOF_TAG);
        check(!nonce.isEmpty() && tickets().containsKey(nonce), "Native recipe did not issue a preview ticket");
        check(!RewardLedger.get(player).valid(player, preview), "Preview was prematurely bound before native transfer");
        if (delayed) {
            Object ticket = tickets().get(nonce);Field expiry = ticket.getClass().getDeclaredField("expires");expiry.setAccessible(true);
            expiry.setLong(ticket, System.nanoTime() - 1);
            crafting.serverTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));
            check(tickets().containsKey(nonce), "Live result-slot proof expired while waiting");
        }
        if (stale) {
            tickets().remove(nonce);
            crafting.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            preview = container.craftResult.getStackInSlot(0);
            check(preview.hasTagCompound() && !nonce.equals(preview.getTagCompound().getString(ArsenalCrafting.PROOF_TAG)), "Stale native preview was not replaced");
            check(!RewardLedger.get(player).valid(player, preview), "Stale preview refresh issued an uncrafted reward");
        }
        container.slotClick(0, 0, ClickType.PICKUP, player);
        crafting.tick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
        ItemStack output = player.inventory.getItemStack();
        check(!output.isEmpty() && output.getCount() == 1 && RewardLedger.get(player).valid(player, output), "Native result pickup was not bound to its crafter");
        check(!output.getTagCompound().hasKey(ArsenalCrafting.PROOF_TAG), "Resolved craft retained reusable proof");
        check(container.craftMatrix.isEmpty() && container.craftResult.isEmpty(), "Native crafting failed to consume actual ingredients");
        check(RewardLedger.get(player).pendingCount(player.getUniqueID()) == 0, "Craft binding issued a second reward into the outbox");
        return output;
    }
    private ItemStack issue(String key, String receipt) {
        RewardLedger ledger=RewardLedger.get(player);ledger.issue(player,key,1,"recovery039/"+receipt);
        for (ItemStack stack : player.inventory.mainInventory)
            if (!stack.isEmpty() && stack.getItem()==Gear.ITEMS.get(key) && ledger.valid(player,stack)) return stack;
        throw new AssertionError("Missing issued fixture item " + key);
    }
    private void hold(ItemStack stack) {
        for (int i=0;i<player.inventory.mainInventory.size();i++)
            if(player.inventory.mainInventory.get(i)==stack)player.inventory.mainInventory.set(i,ItemStack.EMPTY);
        player.inventory.setItemStack(ItemStack.EMPTY);
        player.inventory.currentItem=0;player.inventory.mainInventory.set(0,stack);
    }
    private void cooldownReconnect(MinecraftServer server) {
        clearInventory(player);
        ArsenalSpec spec=ArsenalSpec.WARD_FAN;
        ItemStack weapon=issue(spec.key,"cooldown-weapon");
        for(EntityEquipmentSlot slot:new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET}) {
            String part=slot==EntityEquipmentSlot.HEAD?"helmet":slot==EntityEquipmentSlot.CHEST?"chestplate":slot==EntityEquipmentSlot.LEGS?"leggings":"boots";
            ItemStack armor=issue(spec.order.key+"_"+part,"cooldown-"+part);
            for(int i=0;i<player.inventory.mainInventory.size();i++)if(player.inventory.mainInventory.get(i)==armor)player.inventory.mainInventory.set(i,ItemStack.EMPTY);
            player.setItemStackToSlot(slot,armor);
        }
        hold(weapon);player.setSneaking(true);PlayerState.setResolve(player,100);
        check(ArsenalAccess.fullSet(player)==spec.order, "Fixture is missing the actual issued matching set");
        long castAt=ArsenalAccess.now(player);
        check(weapon.getItem().onItemRightClick(player.world,player,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Native weapon use was refused before cast");
        weapon.getItem().onPlayerStoppedUsing(weapon,player.world,player,72000-spec.charge);
        player.resetActiveHand();
        check(PlayerState.resolve(player)==100-spec.cost,"Native cast did not consume the expected resolve");
        check(ArsenalCooldowns.readyAt(ArsenalLogic.state(player),spec)==castAt+spec.cooldown,"Native cast did not persist the specialist deadline");
        server.getPlayerList().playerLoggedOut(player);
        server.getWorld(0).getWorldInfo().setWorldTotalTime(castAt+ArsenalMath.SHARED_RECOVERY);
        player=login(server,true);player.setSneaking(true);
        ItemStack restored=player.getHeldItemMainhand();
        check(restored.getItem()==weapon.getItem()&&RewardLedger.get(player).valid(player,restored),"Saved issued weapon did not survive native player reload");
        check(player.getCooldownTracker().hasCooldown(restored.getItem()),"Login did not restore the native cooldown display");
        player.getCooldownTracker().setCooldown(restored.getItem(),0);player.getCooldownTracker().tick();
        check(!ArsenalLogic.ready(player,restored,spec,false),"Reconnecting bypassed the specialist's longer persistent cooldown");
        server.getWorld(0).getWorldInfo().setWorldTotalTime(castAt+spec.cooldown);
        check(ArsenalLogic.ready(player,restored,spec,false),"Persistent cooldown failed to expire at its deadline");
        player.setSneaking(false);
        passed("Native weapon cast, saved player logout/reload and longer per-specialist cooldown");
    }
    private void nativePartyRejoin() {
        clearInventory(player);hold(issue("party_charter","party-charter"));
        ItemStack charter=player.getHeldItemMainhand();
        check(charter.getItem().onItemRightClick(player.world,player,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS,"Native charter could not create expedition");
        String team=player.getTeam().getName();
        player.world.getScoreboard().addPlayerToTeam("RecoveryGuest",team);
        player.setSneaking(true);
        check(charter.getItem().onItemRightClick(player.world,player,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS&&player.getTeam()==null,"Native charter could not leave expedition");
        player.setSneaking(false);
        check(charter.getItem().onItemRightClick(player.world,player,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS&&team.equals(player.getTeam().getName()),"Native charter failed to rejoin its existing expedition");
        check(player.world.getScoreboard().getTeam(team).getMembershipCollection().contains("RecoveryGuest"),"Native rejoin lost existing expedition member");
        passed("Native Party Charter leave/rejoin preserves the existing expedition");
    }
    private void nativeCompanionClosure() {
        clearInventory(player);PlayerState.data(player).setInteger("xp",1000000);PlayerState.setResolve(player,100);
        hold(issue("puppet_spindle","companion"));ItemStack item=player.getHeldItemMainhand();
        player.setPosition(.5,65,.5);BlockPos ground=new BlockPos(0,64,0);
        player.world.getChunk(ground);player.world.setBlockState(ground,Blocks.STONE.getDefaultState());
        check(item.getItem().onItemUse(player,player.world,ground,EnumHand.MAIN_HAND,EnumFacing.UP,.5f,1,.5f)==EnumActionResult.SUCCESS,"Native support item did not deploy companion");
        String serial=item.getTagCompound().getString("valeSerial");ScrollJournal journal=ScrollJournal.get(player);NBTTagCompound row=journal.companion(serial);
        ScrollActor original=ScrollActor.find(player,row.getUniqueId("actor"));check(original!=null,"Deployed native companion is not live");
        original.setHealth(17);original.setPosition(player.posX+65,player.posY,player.posZ);original.onLivingUpdate();
        check(original.isDead&&!row.getBoolean("deployed")&&row.getFloat("health")==17,"Out-of-range native companion left a locked deployment or lost health");
        check(item.getItem().onItemUse(player,player.world,ground,EnumHand.MAIN_HAND,EnumFacing.UP,.5f,1,.5f)==EnumActionResult.SUCCESS,"Closed native companion could not be redeployed before old expiry");
        ScrollActor replacement=ScrollActor.find(player,row.getUniqueId("actor"));check(replacement!=null&&replacement!=original&&replacement.getHealth()==17,"Native redeploy lost companion identity/health");
        original.recordCompanion(true);
        check(row.getBoolean("deployed")&&row.getUniqueId("actor").equals(replacement.getUniqueID()),"Stale actor closed its replacement deployment");
        check(item.getItem().onItemRightClick(player.world,player,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS&&!row.getBoolean("deployed"),"Native recall failed to close replacement");
        passed("Native companion distance cleanup, immediate redeploy, stale actor rejection and recall");
    }
    private void quartermaster() {
        clearInventory(player);NBTTagCompound state=PlayerState.data(player);state.setString("starterOrder",Order.GALE.key);state.setInteger("xp",0);
        player.messages.clear();Quartermaster.show(player,"spirit");
        check(provisionKeys().isEmpty(),"Spirit menu exposed level-locked armor");
        state.setInteger("xp",1000000);for(Order order:Order.values())state.setBoolean("study_"+order.key,true);
        player.messages.clear();Quartermaster.show(player,"spirit");Set<String> eligible=provisionKeys();
        check(eligible.size()==36,"Spirit menu did not expose exactly36 eligible original armor pieces");
        for(String key:eligible)check(Gear.ITEMS.get(key) instanceof OriginalArmorRegistry.Armor&&Quartermaster.price(player,key)==8,"Spirit menu contains wrong kind or price: "+key);
        String key=eligible.iterator().next();state.setInteger("merit",100);long before=state.getLong("purchaseNumber");
        check(Quartermaster.buy(player,key),"Native Spirit provision purchase failed");
        check(state.getInteger("merit")==92&&state.getLong("purchaseNumber")==before+1&&count(key)==1,"Spirit purchase charge/delivery mismatch");
        ItemStack bought=ItemStack.EMPTY;for(ItemStack stack:player.inventory.mainInventory)if(stack.getItem()==Gear.ITEMS.get(key))bought=stack;
        check(RewardLedger.get(player).valid(player,bought),"Spirit provision is not bound to its buyer");
        replayPending(key,state.getLong("purchaseNumber"));
        check(Quartermaster.recover(player)&&count(key)==1&&state.getInteger("merit")==92,"Delivered paid receipt replay duplicated or charged again");
        for(int i=0;i<player.inventory.mainInventory.size();i++)player.inventory.mainInventory.set(i,new ItemStack(Blocks.DIRT,64));
        check(Quartermaster.buy(player,key)&&state.getInteger("merit")==84,"Full inventory provision was not charged exactly once");
        RewardLedger ledger=RewardLedger.get(player);
        check(ledger.pendingCount(player.getUniqueID())==1&&count(key)==0,"Full inventory did not retain exactly one queued grant");
        replayPending(key,state.getLong("purchaseNumber"));
        check(Quartermaster.recover(player)&&ledger.pendingCount(player.getUniqueID())==1&&state.getInteger("merit")==84,"Queued receipt retry duplicated or recharged");
        player.inventory.mainInventory.set(0,ItemStack.EMPTY);ledger.deliver(player);ledger.deliver(player);
        check(ledger.pendingCount(player.getUniqueID())==0&&count(key)==1&&state.getInteger("merit")==84,"Free-slot delivery duplicated or lost queued provision");
        long number=state.getLong("purchaseNumber");
        check(!Quartermaster.buy(player,"unregistered_fixture_item")&&state.getInteger("merit")==84&&state.getLong("purchaseNumber")==number,"Unavailable provision consumed merit or receipt");
        passed("Quartermaster Spirit eligibility,36 entries, actual purchase, full-inventory outbox and paid-receipt idempotency");
    }
    private Set<String> provisionKeys() {
        Set<String> keys=new HashSet<>();for(ITextComponent message:player.messages){ClickEvent click=message.getStyle().getClickEvent();
            if(click!=null&&click.getAction()==ClickEvent.Action.RUN_COMMAND&&click.getValue().startsWith("/vale provision "))keys.add(click.getValue().substring("/vale provision ".length()));}
        return keys;
    }
    private void replayPending(String key,long number) {
        NBTTagCompound row=new NBTTagCompound();row.setString("key",key);row.setString("receipt","quartermaster/"+player.getUniqueID()+"/"+number);PlayerState.data(player).setTag("purchasePending",row);
    }
    private int count(String key) {
        int total=0;Item item=Gear.ITEMS.get(key);for(ItemStack stack:player.inventory.mainInventory)if(stack.getItem()==item)total+=stack.getCount();return total;
    }
    private static void clearInventory(ProbePlayer p) {
        p.inventory.setItemStack(ItemStack.EMPTY);for(int i=0;i<p.inventory.mainInventory.size();i++)p.inventory.mainInventory.set(i,ItemStack.EMPTY);
        p.inventory.currentItem=0;
    }
    private static String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(java.io.InputStream in=Files.newInputStream(file.toPath())){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
    }
    private static final class ProbePlayer extends EntityPlayerMP {
        final List<ITextComponent> messages=new ArrayList<>();
        ProbePlayer(MinecraftServer server,WorldServer world){super(server,world,new GameProfile(UUID.fromString("43ec7807-821a-4a56-9c03-070038000001"),"Recovery038"),new PlayerInteractionManager(world));}
        @Override public void sendMessage(ITextComponent value){if(messages!=null)messages.add(value.createCopy());}
        @Override public void sendStatusMessage(ITextComponent value,boolean actionBar){if(messages!=null)messages.add(value.createCopy());}
    }
    private static final class QuietConnection extends NetHandlerPlayServer {
        QuietConnection(MinecraftServer server,EntityPlayerMP player){super(server,network(),player);}
        private static NetworkManager network(){NetworkManager manager=new NetworkManager(EnumPacketDirection.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(manager);return manager;}
        @Override public void sendPacket(Packet<?> packet){}
    }
}
