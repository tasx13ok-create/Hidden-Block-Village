package village.vale;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;

public final class RewardLedger extends WorldSavedData {
    private static final String NAME = "vale_orders_rewards";
    private final Map<String, NBTTagCompound> grants = new LinkedHashMap<>();
    private final Map<String, String> receipts = new LinkedHashMap<>();
    // Derived index: delivery cost depends on this player's queue, not all past grants.
    private final Map<String, LinkedHashSet<String>> pendingByOwner = new LinkedHashMap<>();
    public RewardLedger() { super(NAME); }
    public RewardLedger(String name) { super(name); }

    public static RewardLedger get(EntityPlayer player) {
        WorldServer world = player.getServer().getWorld(0);
        RewardLedger ledger = (RewardLedger)world.getMapStorage().getOrLoadData(RewardLedger.class, NAME);
        if (ledger == null) {
            ledger = new RewardLedger();
            world.getMapStorage().setData(NAME, ledger);
        }
        return ledger;
    }

    public boolean valid(EntityPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTagCompound()) return false;
        NBTTagCompound row = grants.get(stack.getTagCompound().getString("valeSerial"));
        return row != null && row.getString("owner").equals(player.getUniqueID().toString())
            && row.getString("item").equals(Gear.key(stack));
    }

    /** Enroll the existing native crafted output; never issue/deliver a second stack. */
    public boolean bindCrafted(village.vale.arsenal.ArsenalCrafting.Output proof) {
        if (proof == null || !proof.claim(this)) return false;
        EntityPlayerMP player=proof.player; ItemStack stack=proof.stack;
        NBTTagCompound previous=stack.hasTagCompound()?stack.getTagCompound():new NBTTagCompound();
        boolean reserved=previous.hasKey("valeSerial");
        NBTTagCompound existing=grants.get(previous.getString("valeSerial"));
        boolean alreadyBound=valid(player,stack) && existing!=null
            && !existing.getBoolean("pending") && quantity(existing)==1;
        village.vale.arsenal.CraftedBindingRules.Decision decision=
            village.vale.arsenal.CraftedBindingRules.decide(true,
                village.vale.arsenal.ArsenalCrafting.eligible(stack),stack.getCount(),
                reserved,alreadyBound,receipts.containsKey(proof.receipt));
        if (decision==village.vale.arsenal.CraftedBindingRules.Decision.REJECT) return false;
        if (decision==village.vale.arsenal.CraftedBindingRules.Decision.ALREADY_BOUND) return true;
        String serial;
        do { serial=UUID.randomUUID().toString(); } while (grants.containsKey(serial));
        NBTTagCompound row=new NBTTagCompound();
        row.setString("owner",player.getUniqueID().toString());
        row.setString("item",Gear.key(stack));row.setInteger("quantity",stack.getCount());
        row.setBoolean("pending",false);row.setString("source","native_craft");
        row.setString("recipe",proof.recipe.toString());
        NBTTagCompound tag=previous.copy();
        tag.removeTag(village.vale.arsenal.ArsenalCrafting.PROOF_TAG);
        tag.setString("valeSerial",serial);
        // Single server-thread commit. No pending index, insertion, or outbox delivery.
        grants.put(serial,row);receipts.put(proof.receipt,serial);stack.setTagCompound(tag);
        markDirty();player.inventory.markDirty();
        // Normal world/player saves persist the completed native transaction later.
        // Do not save pre-consumption ingredients or claim cursor crash atomicity.
        return true;
    }

    /** Only the finite saved-world ticket resolver can enroll this existing stack. */
    boolean bindMigration(EntityPlayerMP player,ItemStack stack,String token) {
        if(!MigrationClaims.authorized(player,stack)||token==null||token.isEmpty()||stack.isEmpty()
                ||stack.getCount()<1||stack.getCount()>16||!Gear.ITEMS.containsValue(stack.getItem())
                ||(stack.hasTagCompound()&&stack.getTagCompound().hasKey("valeSerial")))return false;
        String serial=UUID.nameUUIDFromBytes(("vale-migration/"+token).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        String receipt="saved-world/"+token,owner=player.getUniqueID().toString(),item=Gear.key(stack);
        NBTTagCompound old=grants.get(serial);
        if(old!=null) {
            if(!serial.equals(receipts.get(receipt))||!owner.equals(old.getString("owner"))
                    ||!item.equals(old.getString("item"))||quantity(old)!=stack.getCount()
                    ||old.getBoolean("pending")||!"saved_world_migration".equals(old.getString("source")))return false;
        } else {
            if(receipts.containsKey(receipt)||receipts.containsValue(serial))return false;
            NBTTagCompound row=new NBTTagCompound();row.setString("owner",owner);row.setString("item",item);
            row.setInteger("quantity",stack.getCount());row.setBoolean("pending",false);row.setString("source","saved_world_migration");
            grants.put(serial,row);receipts.put(receipt,serial);
        }
        NBTTagCompound tag=stack.hasTagCompound()?stack.getTagCompound():new NBTTagCompound();
        tag.setString("valeSerial",serial);stack.setTagCompound(tag);markDirty();player.inventory.markDirty();return true;
    }

    /** Returns false for an already recorded identical receipt; rejects receipt reuse for a different grant. */
    public boolean issue(EntityPlayerMP player, String key, int count, String receipt) {
        boolean fresh = issue(player.getServer().getWorld(0), player.getUniqueID(), key, count, receipt);
        deliver(player);
        return fresh;
    }

    /** Durable outbox also supports offline boss participants. */
    public boolean issue(WorldServer world, UUID owner, String key, int count, String receipt) {
        if (!Gear.ITEMS.containsKey(key) || count < 1 || count > 16) throw new IllegalArgumentException("Invalid reward.");
        return issueItem(world,owner,ValeOrders.ID+":"+key,key,count,receipt,false);
    }
    public boolean issueOwned(EntityPlayerMP player,String key,String receipt) {
        if(!OwnedArmor.rewardKey(key)||!net.minecraft.item.Item.REGISTRY.containsKey(new net.minecraft.util.ResourceLocation("hvbijuu",key)))
            throw new IllegalArgumentException("Owned armor is unavailable or the item is unknown.");
        boolean fresh=issueItem(player.getServer().getWorld(0),player.getUniqueID(),"hvbijuu:"+key,"hvbijuu:"+key,1,receipt,true);
        deliver(player);return fresh;
    }
    public boolean issueStandard(EntityPlayerMP player,String key,String receipt) {
        String item="hvdungeonpictures:"+key+"_picture";
        if(!java.util.Arrays.asList("stone","sand","mist","leaf","cloud").contains(key)||!Item.REGISTRY.containsKey(new net.minecraft.util.ResourceLocation(item)))
            throw new IllegalArgumentException("Village standards are unavailable or the item is unknown.");
        boolean fresh=issueItem(player.getServer().getWorld(0),player.getUniqueID(),item,item,1,receipt,false);deliver(player);return fresh;
    }
    private boolean issueItem(WorldServer world,UUID owner,String item,String requestKey,int count,String receipt,boolean owned) {
        String request = owner.toString() + "/" + requestKey + "/" + count;
        if (!Rules.recordReceipt(receipts, receipt, request)) return false;
        Item nativeItem=Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(item));
        int limit=owned?1:Math.max(1,Math.min(16,new ItemStack(nativeItem).getMaxStackSize()));
        for (int i = 0; i < count;) {
            int quantity=Math.min(limit,count-i);i+=quantity;
            NBTTagCompound row = new NBTTagCompound();
            row.setString("owner", owner.toString());
            row.setString("item", item);
            row.setBoolean("pending", true);
            row.setInteger("quantity", quantity);
            String serial = UUID.randomUUID().toString();
            if(owned)OwnedArmor.registerReward(world,item.substring("hvbijuu:".length()),serial);
            grants.put(serial, row);
            pendingByOwner.computeIfAbsent(row.getString("owner"), ignored -> new LinkedHashSet<>()).add(serial);
        }
        markDirty();
        // Persist the pending outbox before attempting an inventory delivery.
        world.getMinecraftServer().getWorld(0).getMapStorage().saveAllData();
        return true;
    }

    public void deliver(EntityPlayerMP player) {
        if (player.isDead || player.getHealth() <= 0) return;
        String owner = player.getUniqueID().toString();
        LinkedHashSet<String> pending = pendingByOwner.get(owner);
        if (pending == null || pending.isEmpty()) return;
        NBTTagCompound state = PlayerState.data(player);
        NBTTagCompound delivered = state.getCompoundTag("delivered");
        if (!state.hasKey("delivered", 10)) state.setTag("delivered", delivered);
        java.util.List<String> acknowledged = new ArrayList<>();
        for (String serial : pending) {
            NBTTagCompound row = grants.get(serial);
            if (!row.getBoolean("pending") || !row.getString("owner").equals(owner)) continue;
            if (!delivered.getBoolean(serial) && !hasSerial(player, serial)) {
                String itemKey = row.getString("item");
                Item item = itemKey.startsWith(ValeOrders.ID + ":") ?
                    Gear.ITEMS.get(itemKey.substring(ValeOrders.ID.length() + 1)) :
                    itemKey.startsWith("hvbijuu:")&&OwnedArmor.rewardKey(itemKey.substring(8))?
                        Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(itemKey)):
                    itemKey.startsWith("hvdungeonpictures:")&&Item.REGISTRY.containsKey(new net.minecraft.util.ResourceLocation(itemKey))?
                        Item.REGISTRY.getObject(new net.minecraft.util.ResourceLocation(itemKey)):null;
                if (item == null) continue;
                int quantity=quantity(row);
                // One row fits one real native slot. Equipment remains individually
                // serialized; consumables use their actual stack limit (at most16).
                // A changed external item limit leaves the batch queued for review.
                if(quantity>new ItemStack(item).getMaxStackSize())continue;
                if (player.inventory.getFirstEmptyStack() < 0) break;
                ItemStack stack = new ItemStack(item,quantity);
                NBTTagCompound tag = new NBTTagCompound(); tag.setString("valeSerial", serial); stack.setTagCompound(tag);
                if(itemKey.startsWith("hvbijuu:"))tag.setString("hvReward",serial);
                boolean inserted=player.inventory.addItemStackToInventory(stack);
                if(!inserted||!stack.isEmpty()||serialQuantity(player,serial)!=quantity){
                    // Do not acknowledge a partial or falsely reported insertion.
                    // This serial was absent before this attempt, so rollback touches
                    // only this new delivery, not another owned item or older batch.
                    for(int i=0;i<player.inventory.mainInventory.size();i++){
                        ItemStack added=player.inventory.mainInventory.get(i);
                        if(added.hasTagCompound()&&serial.equals(added.getTagCompound().getString("valeSerial")))player.inventory.mainInventory.set(i,ItemStack.EMPTY);
                    }
                    player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();break;
                }
            }
            delivered.setBoolean(serial, true);
            acknowledged.add(serial);
        }
        if (!acknowledged.isEmpty()) {
            // One player save per batch; acknowledgement precedes clearing the outbox.
            player.getServer().getWorld(0).getSaveHandler().getPlayerNBTManager().writePlayerData(player);
            for (String serial : acknowledged) {
                grants.get(serial).setBoolean("pending", false);
                pending.remove(serial);
            }
            if (pending.isEmpty()) pendingByOwner.remove(owner);
            player.inventoryContainer.detectAndSendChanges();
            markDirty();
            player.getServer().getWorld(0).getMapStorage().saveAllData();
        }
    }

    public int pendingCount(UUID owner) {
        java.util.Set<String> queue = pendingByOwner.get(owner.toString());
        if(queue==null)return 0;long units=0;
        for(String serial:queue)units+=quantity(grants.get(serial));
        return (int)Math.min(Integer.MAX_VALUE,units);
    }

    private static int quantity(NBTTagCompound row){
        int count=row.hasKey("quantity",99)?row.getInteger("quantity"):1;
        if(count<1||count>16)throw new IllegalStateException("Invalid saved reward quantity");
        return count;
    }

    private int serialQuantity(EntityPlayer player,String serial){
        int count=0;
        for(ItemStack stack:player.inventory.mainInventory)if(stack.hasTagCompound()&&serial.equals(stack.getTagCompound().getString("valeSerial")))count+=stack.getCount();
        return count;
    }

    private boolean hasSerial(EntityPlayer player, String serial) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.hasTagCompound() && serial.equals(stack.getTagCompound().getString("valeSerial"))) return true;
        }
        return false;
    }

    @Override public void readFromNBT(NBTTagCompound nbt) {
        grants.clear(); receipts.clear(); pendingByOwner.clear();
        NBTTagList list = nbt.getTagList("grants", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound row = list.getCompoundTagAt(i);
            String serial = row.getString("serial");
            if (!serial.isEmpty()) {
                grants.put(serial, row);
            }
        }
        for (Map.Entry<String, NBTTagCompound> entry : grants.entrySet()) {
            NBTTagCompound row = entry.getValue();
            if (row.getBoolean("pending"))
                pendingByOwner.computeIfAbsent(row.getString("owner"), ignored -> new LinkedHashSet<>()).add(entry.getKey());
        }
        NBTTagCompound saved = nbt.getCompoundTag("receipts");
        for (String key : saved.getKeySet()) receipts.put(key, saved.getString(key));
    }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("rewardSchema",2);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, NBTTagCompound> entry : grants.entrySet()) {
            NBTTagCompound row = entry.getValue().copy(); row.setString("serial", entry.getKey()); list.appendTag(row);
        }
        nbt.setTag("grants", list);
        NBTTagCompound saved = new NBTTagCompound();
        for (Map.Entry<String, String> entry : receipts.entrySet()) saved.setString(entry.getKey(), entry.getValue());
        nbt.setTag("receipts", saved);
        return nbt;
    }
}
