package village.dungeonguard;

import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;

/** A marked vanilla item: usable on a server without a custom client item registry. */
public final class GuardWand {
    private static final String MARKER = "dungeonguard:selection_wand";
    private GuardWand() { }

    public static boolean isWand(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == Items.STICK
                && stack.hasTagCompound() && stack.getTagCompound().getBoolean(MARKER);
    }
    public static void give(EntityPlayerMP player) throws CommandException {
        if (!OperatorAccess.isOperator(player)) throw new CommandException("Only connected server operators can use the dungeon wand.");
        for (int slot = 0; slot < player.inventory.mainInventory.size(); slot++) {
            if (isWand(player.inventory.getStackInSlot(slot))) {
                if (slot < 9) equip(player, slot);
                player.inventoryContainer.detectAndSendChanges();
                GuardMenus.say(player, "Your Dungeon Wand is in " + (slot < 9 ? "hotbar slot " + (slot + 1) : "your inventory") + ".");
                instructions(player); return;
            }
        }
        if (isWand(player.getHeldItemOffhand())) {
            GuardMenus.say(player, "Your Dungeon Wand is in your off hand. Move it to your main hand to select corners."); return;
        }
        ItemStack wand = new ItemStack(Items.STICK);
        NBTTagCompound tag = new NBTTagCompound(); tag.setBoolean(MARKER, true); wand.setTagCompound(tag);
        wand.setStackDisplayName("Dungeon Wand");
        NBTTagCompound display = wand.getTagCompound().getCompoundTag("display");
        NBTTagList lore = new NBTTagList();
        lore.appendTag(new NBTTagString("Left-click: first corner"));
        lore.appendTag(new NBTTagString("Right-click: opposite corner"));
        lore.appendTag(new NBTTagString("Selection only - blocks stay unchanged"));
        display.setTag("Lore", lore); wand.getTagCompound().setTag("display", display);
        for (int slot = 0; slot < 9; slot++) if (player.inventory.getStackInSlot(slot).isEmpty()) {
            player.inventory.setInventorySlotContents(slot, wand); equip(player, slot);
            player.inventory.markDirty(); player.inventoryContainer.detectAndSendChanges(); instructions(player); return;
        }
        for (int slot = 9; slot < player.inventory.mainInventory.size(); slot++) if (player.inventory.getStackInSlot(slot).isEmpty()) {
            player.inventory.setInventorySlotContents(slot, wand); player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            GuardMenus.say(player, "Dungeon Wand added to your inventory. Put it in your main hand."); instructions(player); return;
        }
        throw new CommandException("Free one inventory slot, then use /dungeon wand again.");
    }
    private static void equip(EntityPlayerMP player, int slot) {
        player.inventory.currentItem = slot;
        if (player.connection != null) player.connection.sendPacket(new net.minecraft.network.play.server.SPacketHeldItemChange(slot));
    }
    private static void instructions(EntityPlayerMP player) {
        GuardMenus.say(player, "Hold the wand: left-click the first corner, then right-click the opposite corner. Include the floor, walls and ceiling.");
        GuardMenus.buttonLine(player, "Next: ", "Open setup", "/dungeon setup", "Choose an element and save the selected clean dungeon.", false);
    }
    private static boolean holding(EntityPlayer player) {
        return player != null && isWand(player.getHeldItemMainhand());
    }
    public static boolean leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getWorld().isRemote || !GuardRuntime.ready() || !holding(event.getEntityPlayer())) return false;
        event.setUseBlock(Event.Result.DENY); event.setUseItem(Event.Result.DENY); event.setCanceled(true);
        select(event.getEntityPlayer(), event.getPos(), true); return true;
    }
    public static boolean rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote || !GuardRuntime.ready() || !holding(event.getEntityPlayer())) return false;
        event.setUseBlock(Event.Result.DENY); event.setUseItem(Event.Result.DENY);
        event.setCancellationResult(EnumActionResult.SUCCESS); event.setCanceled(true);
        if (event.getHand() == EnumHand.MAIN_HAND) select(event.getEntityPlayer(), event.getPos(), false);
        return true;
    }
    /** Covers native harvest paths that did not send an ordinary interaction event. */
    public static boolean breaking(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote || !GuardRuntime.ready() || !holding(event.getPlayer())) return false;
        event.setCanceled(true); event.setExpToDrop(0); return true;
    }
    private static void select(EntityPlayer player, BlockPos pos, boolean first) {
        if (!(player instanceof EntityPlayerMP) || !OperatorAccess.isOperator(player)) {
            GuardMenus.say(player, "This wand is for server operators. Your blocks were left unchanged."); return;
        }
        EntityPlayerMP operator = (EntityPlayerMP) player;
        try {
            GuardSelections.Selection selected = GuardSelections.select(operator, pos, first);
            GuardMenus.say(player, "Corner " + (first ? "1" : "2") + ": " + GuardSelections.coordinates(pos) + ".");
            if (selected.complete()) {
                GuardMenus.say(player, GuardSelections.describe(operator));
                long size = selected.region("selection").volume();
                if (size > GuardRuntime.settings().maxSnapshotBlocks)
                    GuardMenus.say(player, "That selection is too large. Choose closer corners; the limit is " + GuardRuntime.settings().maxSnapshotBlocks + " blocks.");
                else GuardMenus.buttonLine(player, "Ready to save: ", "Open setup", "/dungeon setup", "Select an element for this clean dungeon.", false);
            } else GuardMenus.say(player, first ? "Now right-click the opposite corner." : "Now left-click the first corner.");
        } catch (CommandException failure) { GuardMenus.say(player, failure.getMessage()); }
    }
}
