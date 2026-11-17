package village.dungeonguard;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import village.dungeonguard.GuardMenus;
import village.dungeonguard.GuardRuntime;
import village.dungeonguard.GuardSelections;
import village.dungeonguard.OperatorAccess;

public final class GuardWand {
    private static final String MARKER = "dungeonguard:selection_wand";

    private GuardWand() {
    }

    public static boolean isWand(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == Items.STICK && stack.hasTagCompound() && stack.getTagCompound().getBoolean(MARKER);
    }

    public static void give(EntityPlayerMP player) throws CommandException {
        int slot;
        if (!OperatorAccess.isOperator((EntityPlayer)player)) {
            throw new CommandException("Only connected server operators can use the dungeon wand.", new Object[0]);
        }
        for (int slot2 = 0; slot2 < player.inventory.mainInventory.size(); ++slot2) {
            if (!GuardWand.isWand(player.inventory.getStackInSlot(slot2))) continue;
            if (slot2 < 9) {
                GuardWand.equip(player, slot2);
            }
            player.inventoryContainer.detectAndSendChanges();
            GuardMenus.feedback((ICommandSender)player, "Your Dungeon Wand is in " + (slot2 < 9 ? "hotbar slot " + (slot2 + 1) : "your inventory") + ".");
            GuardWand.instructions(player);
            return;
        }
        if (GuardWand.isWand(player.getHeldItemOffhand())) {
            GuardMenus.feedback((ICommandSender)player, "Your Dungeon Wand is in your off hand. Move it to your main hand to select corners.");
            return;
        }
        ItemStack wand = new ItemStack(Items.STICK);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean(MARKER, true);
        wand.setTagCompound(tag);
        wand.setStackDisplayName("Dungeon Wand");
        NBTTagCompound display = wand.getTagCompound().getCompoundTag("display");
        NBTTagList lore = new NBTTagList();
        lore.appendTag((NBTBase)new NBTTagString("Left-click: first corner"));
        lore.appendTag((NBTBase)new NBTTagString("Right-click: opposite corner"));
        lore.appendTag((NBTBase)new NBTTagString("Selection only - blocks stay unchanged"));
        display.setTag("Lore", (NBTBase)lore);
        wand.getTagCompound().setTag("display", (NBTBase)display);
        for (slot = 0; slot < 9; ++slot) {
            if (!player.inventory.getStackInSlot(slot).isEmpty()) continue;
            player.inventory.setInventorySlotContents(slot, wand);
            GuardWand.equip(player, slot);
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            GuardWand.instructions(player);
            return;
        }
        for (slot = 9; slot < player.inventory.mainInventory.size(); ++slot) {
            if (!player.inventory.getStackInSlot(slot).isEmpty()) continue;
            player.inventory.setInventorySlotContents(slot, wand);
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            GuardMenus.feedback((ICommandSender)player, "Dungeon Wand added to your inventory. Put it in your main hand.");
            GuardWand.instructions(player);
            return;
        }
        throw new CommandException("Free one inventory slot, then use /dungeon wand again.", new Object[0]);
    }

    private static void equip(EntityPlayerMP player, int slot) {
        player.inventory.currentItem = slot;
        if (player.connection != null) {
            player.connection.sendPacket((Packet)new SPacketHeldItemChange(slot));
        }
    }

    private static void instructions(EntityPlayerMP player) {
        GuardMenus.feedback((ICommandSender)player, "Wand: left-click one corner; right-click the opposite corner.");
    }

    private static boolean holding(EntityPlayer player) {
        return player != null && GuardWand.isWand(player.getHeldItemMainhand());
    }

    public static boolean leftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getWorld().isRemote || !GuardRuntime.ready() || !GuardWand.holding(event.getEntityPlayer())) {
            return false;
        }
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);
        GuardWand.select(event.getEntityPlayer(), event.getPos(), true);
        return true;
    }

    public static boolean rightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote || !GuardRuntime.ready() || !GuardWand.holding(event.getEntityPlayer())) {
            return false;
        }
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
        if (event.getHand() == EnumHand.MAIN_HAND) {
            GuardWand.select(event.getEntityPlayer(), event.getPos(), false);
        }
        return true;
    }

    public static boolean breaking(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote || !GuardRuntime.ready() || !GuardWand.holding(event.getPlayer())) {
            return false;
        }
        event.setCanceled(true);
        event.setExpToDrop(0);
        return true;
    }

    private static void select(EntityPlayer player, BlockPos pos, boolean first) {
        if (!(player instanceof EntityPlayerMP) || !OperatorAccess.isOperator(player)) {
            GuardMenus.feedback((ICommandSender)player, "This wand is for server operators. Your blocks were left unchanged.");
            return;
        }
        EntityPlayerMP operator = (EntityPlayerMP)player;
        try {
            GuardSelections.Selection selected = GuardSelections.select(operator, pos, first);
            if (selected.complete()) {
                long size = selected.region("selection").volume();
                GuardMenus.feedback((ICommandSender)player, size > (long)GuardRuntime.settings().maxSnapshotBlocks ? "Selection exceeds the snapshot limit. Choose closer corners." : GuardSelections.describe(operator));
            } else {
                GuardMenus.feedback((ICommandSender)player, first ? "Corner 1 set. Right-click the opposite corner." : "Corner 2 set. Left-click the first corner.");
            }
        }
        catch (CommandException failure) {
            GuardMenus.feedback((ICommandSender)player, failure.getMessage());
        }
    }
}

