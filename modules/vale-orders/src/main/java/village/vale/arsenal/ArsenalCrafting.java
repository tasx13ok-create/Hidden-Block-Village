package village.vale.arsenal;

import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.item.crafting.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.registries.IForgeRegistryEntry;
import village.vale.Gear;
import village.vale.RewardLedger;

/** Native recipe -> native craft event -> post-transfer live-output certification.
 * No packet handler, item-use callback, or arbitrary-stack binding entry exists.
 */
public final class ArsenalCrafting {
    public static final String PROOF_TAG = "valeCraftProof";
    private static final Map<InventoryCrafting, World> WORLDS = new WeakHashMap<>();
    private static final Map<String, Ticket> TICKETS = new LinkedHashMap<>();
    public ArsenalCrafting() {}
    private static boolean server(World w) {
        return w != null && !w.isRemote && w.getMinecraftServer() != null
            && w.getMinecraftServer().isCallingFromMinecraftThread();
    }
    public static boolean eligible(ItemStack s) {
        if (s == null || s.isEmpty() || s.getCount() != 1) return false;
        Item i=s.getItem(); ResourceLocation id=i.getRegistryName();
        return i instanceof ItemSword && i instanceof CraftedArsenalWeapon && id != null
            && "valeorders".equals(id.getNamespace()) && Gear.ITEMS.get(id.getPath()) == i
            && Item.REGISTRY.containsKey(id) && Item.REGISTRY.getObject(id) == i
            && s.getMaxStackSize() == 1;
    }
    private static String token(ItemStack s) {
        return s != null && s.hasTagCompound() ? s.getTagCompound().getString(PROOF_TAG) : "";
    }
    private static InventoryCrafting matrix(Container c) {
        return c instanceof ContainerWorkbench ? ((ContainerWorkbench)c).craftMatrix
            : c instanceof ContainerPlayer ? ((ContainerPlayer)c).craftMatrix : null;
    }
    private static IInventory result(Container c) {
        return c instanceof ContainerWorkbench ? ((ContainerWorkbench)c).craftResult
            : c instanceof ContainerPlayer ? ((ContainerPlayer)c).craftResult : null;
    }
    private static final class Ticket {
        final String nonce; final Recipe recipe; final InventoryCrafting matrix;
        final World world; final long expires; final ItemStack snapshot;
        EntityPlayerMP owner; Container container; boolean certified;
        Ticket(String n,Recipe r,InventoryCrafting m,World w,ItemStack s) {
            nonce=n;recipe=r;matrix=m;world=w;snapshot=s.copy();expires=System.nanoTime()+10000000000L;
        }
    }
    /** Register this wrapper instead of its delegate under the chosen recipe ID.
     * Only fresh, count-one base recipes are supported; repair/upgrade is separate.
     */
    public static final class Recipe extends IForgeRegistryEntry.Impl<IRecipe> implements IRecipe {
        private final IRecipe delegate;
        public Recipe(ResourceLocation id,IRecipe delegate) {
            if (delegate == null) throw new IllegalArgumentException("Missing recipe");
            this.delegate=delegate;setRegistryName(id);
        }
        public boolean matches(InventoryCrafting m,World w) {
            boolean ok=delegate.matches(m,w);
            if (ok && server(w)) WORLDS.put(m,w);
            return ok;
        }
        public ItemStack getCraftingResult(InventoryCrafting m) {
            ItemStack s=delegate.getCraftingResult(m).copy();
            if (net.minecraftforge.fml.common.FMLCommonHandler.instance().getEffectiveSide().isClient()) return s;
            World w=WORLDS.get(m);
            if (!server(w) || CraftingManager.REGISTRY.getObject(getRegistryName()) != this
                    || !delegate.matches(m,w) || !eligible(s)) return s;
            if (s.hasTagCompound() && (s.getTagCompound().hasKey("valeSerial")
                    || s.getTagCompound().hasKey(PROOF_TAG))) return ItemStack.EMPTY;
            if (TICKETS.size() >= 4096) return ItemStack.EMPTY;
            String n=UUID.randomUUID().toString();
            NBTTagCompound tag=s.hasTagCompound()?s.getTagCompound().copy():new NBTTagCompound();
            tag.setString(PROOF_TAG,n);s.setTagCompound(tag);
            TICKETS.put(n,new Ticket(n,this,m,w,s));return s;
        }
        public boolean canFit(int w,int h) { return delegate.canFit(w,h); }
        public ItemStack getRecipeOutput() { return delegate.getRecipeOutput().copy(); }
        public NonNullList<ItemStack> getRemainingItems(InventoryCrafting m) { return delegate.getRemainingItems(m); }
        public NonNullList<Ingredient> getIngredients() { return delegate.getIngredients(); }
        public boolean isDynamic() { return delegate.isDynamic(); }
        public String getGroup() { return delegate.getGroup(); }
    }
    @SubscribeEvent public void crafted(PlayerEvent.ItemCraftedEvent e) {
        if (!(e.player instanceof EntityPlayerMP) || !server(e.player.world)) return;
        EntityPlayerMP p=(EntityPlayerMP)e.player; Ticket t=TICKETS.get(token(e.crafting));
        if (t == null || t.world != p.world || !p.isEntityAlive() || p.isSpectator()
                || matrix(p.openContainer) != t.matrix || e.craftMatrix != t.matrix
                || System.nanoTime() > t.expires || !eligible(e.crafting)
                || CraftingManager.REGISTRY.getObject(t.recipe.getRegistryName()) != t.recipe
                || !ItemStack.areItemStacksEqual(t.snapshot,e.crafting)) return;
        if (t.certified && (t.owner != p || t.container != p.openContainer)) return;
        t.owner=p;t.container=p.openContainer;t.certified=true;
    }
    private static void add(Set<ItemStack> found,ItemStack s,Ticket t) {
        if (t.nonce.equals(token(s)) && ItemStack.areItemStacksEqual(t.snapshot,s)) found.add(s);
    }
    @SubscribeEvent public void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof EntityPlayerMP)
                || !server(e.player.world)) return;
        EntityPlayerMP p=(EntityPlayerMP)e.player;
        Iterator<Ticket> it=TICKETS.values().iterator();
        while (it.hasNext()) {
            Ticket t=it.next();
            if (!t.certified) { if (System.nanoTime()>t.expires) it.remove(); continue; }
            if (t.owner != p) continue;
            // Consume certification once, even on a failed/ambiguous resolution.
            it.remove();
            if (!p.isEntityAlive() || p.isSpectator() || p.world != t.world) continue;
            IInventory preview=result(t.container);
            if (preview == null || t.nonce.equals(token(preview.getStackInSlot(0)))) continue;
            Set<ItemStack> found=Collections.newSetFromMap(new IdentityHashMap<ItemStack,Boolean>());
            for (int i=0;i<p.inventory.getSizeInventory();i++) add(found,p.inventory.getStackInSlot(i),t);
            add(found,p.inventory.getItemStack(),t);
            for (Slot slot:p.openContainer.inventorySlots)
                if (!(slot instanceof SlotCrafting) && slot.inventory != preview) add(found,slot.getStack(),t);
            for (Entity entity:p.world.loadedEntityList)
                if (entity instanceof EntityItem && !entity.isDead && entity.getDistanceSq(p)<=64)
                    add(found,((EntityItem)entity).getItem(),t);
            if (found.size()!=1) continue;
            ItemStack live=found.iterator().next();
            RewardLedger.get(p).bindCrafted(new Output(p,live,t.nonce,t.recipe.getRegistryName()));
        }
    }
    /** Private constructor: only the post-native-event resolver can certify a stack. */
    public static final class Output {
        public final EntityPlayerMP player; public final ItemStack stack;
        public final String receipt; public final ResourceLocation recipe;
        private final NBTTagCompound snapshot;
        private Output(EntityPlayerMP p,ItemStack s,String n,ResourceLocation r) {
            player=p;stack=s;receipt="craft/"+n;recipe=r;snapshot=fingerprint(s);
        }
        private static NBTTagCompound fingerprint(ItemStack stack) {
            NBTTagCompound n=stack.writeToNBT(new NBTTagCompound());
            NBTTagCompound tag=n.getCompoundTag("tag");
            tag.removeTag(PROOF_TAG);tag.removeTag("valeSerial");
            if (tag.getKeySet().isEmpty()) n.removeTag("tag");
            return n;
        }
        public boolean claim(RewardLedger ledger) {
            return server(player.world) && player.isEntityAlive() && !player.isSpectator()
                && RewardLedger.get(player)==ledger && eligible(stack)
                && snapshot.equals(fingerprint(stack));
        }
    }
}
