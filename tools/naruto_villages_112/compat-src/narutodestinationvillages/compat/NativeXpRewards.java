package narutodestinationvillages.compat;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.narutomod.PlayerTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Original native XP redemption. It does not invent native counterparts for other legacy currencies. */
public final class NativeXpRewards {
    private static final Logger LOG = LogManager.getLogger("NarutoVillageRewards");
    private static final Set<UUID> REDEEMING = new HashSet<>();
    private static final Map<String, Float> MULTIPLIERS = new HashMap<>();
    private static File configFile;
    private static float general = 1;
    private static boolean registered;

    public static void initialize(File file) {
        configFile = file;
        reload();
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(new NativeXpRewards());
            registered = true;
        }
    }

    public static void reload() {
        if (configFile == null) throw new IllegalStateException("Native reward configuration has not initialized");
        Configuration config = new Configuration(configFile);
        config.load();
        general = config.getFloat("general", "xp", 1, 0, 2000, "Overall chakra-experience token multiplier. Rewards grant native Naruto battle XP.");
        MULTIPLIERS.clear();
        for (Map.Entry<String, Float> row : XpRankMultipliers.defaults().entrySet()) {
            MULTIPLIERS.put(row.getKey(), config.getFloat(row.getKey(), "rank_xp", row.getValue(), 0, 2000, "Chakra-experience token rank multiplier."));
        }
        if (config.hasChanged()) config.save();
    }

    public static int awardFor(String rank, int value, int count) {
        if (!NativeRewardItems.validValue(value) || count < 1 || count > 64) return 0;
        double multiplier = (double) MULTIPLIERS.getOrDefault(rank, 1.0f) * general;
        double amount = (double) value * multiplier * count;
        return Double.isFinite(amount) && amount > 0 ? (int) Math.min(Integer.MAX_VALUE, amount) : 0;
    }

    @SubscribeEvent
    public void click(PlayerInteractEvent.RightClickItem event) {
        if (event.getWorld().isRemote || !(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        if (NativeRewardItems.xpValue(player.getHeldItem(event.getHand())) == 0) return;
        event.setCanceled(true);
        event.setCancellationResult(redeem(player, event.getHand()) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL);
    }

    public static boolean redeem(EntityPlayerMP player, EnumHand hand) {
        if (player == null || player.world.isRemote || player.getServer() == null || !player.getServer().isCallingFromMinecraftThread()) return false;
        ItemStack stack = player.getHeldItem(hand);
        int value = NativeRewardItems.xpValue(stack);
        if (value == 0 || !REDEEMING.add(player.getUniqueID())) return false;
        try {
            int count = player.isSneaking() ? stack.getCount() : 1;
            int award = awardFor(ExtendedPlayer.get(player).getRank(), value, count);
            double before = PlayerTracker.getBattleXp(player);
            if (award <= 0 || !Double.isFinite(before) || before < 0 || before + award == before) {
                player.sendMessage(new TextComponentString("\u00a7cThis token cannot grant XP at the current limit or multiplier."));
                return false;
            }
            int originalCount = stack.getCount();
            ItemStack reserved = stack.copy();
            reserved.setCount(count);
            stack.shrink(count);
            try {
                PlayerTracker.addBattleXp(player, award);
            } catch (RuntimeException failure) {
                double after = PlayerTracker.getBattleXp(player);
                if (after == before) refund(player, hand, stack, reserved, originalCount - count);
                LOG.error("Native XP reward callback failed; observed before={}, after={}", before, after, failure);
                player.inventoryContainer.detectAndSendChanges();
                return after > before;
            }
            double after = PlayerTracker.getBattleXp(player);
            if (after <= before) {
                refund(player, hand, stack, reserved, originalCount - count);
                player.inventoryContainer.detectAndSendChanges();
                return false;
            }
            if (player.getHeldItem(hand) == stack && stack.isEmpty()) player.setHeldItem(hand, ItemStack.EMPTY);
            player.inventory.markDirty();
            player.inventoryContainer.detectAndSendChanges();
            player.sendMessage(new TextComponentString("\u00a7aGained " + (long) (after - before) + " Naruto XP from " + count + " token" + (count == 1 ? "." : "s.")));
            try { ExtendedPlayer.get(player).updateRecord(); }
            catch (RuntimeException persistence) { LOG.error("Native XP was awarded, but village statistics could not refresh", persistence); }
            return true;
        } finally {
            REDEEMING.remove(player.getUniqueID());
        }
    }

    private static void refund(EntityPlayerMP player, EnumHand hand, ItemStack original, ItemStack reserved, int remaining) {
        if (player.getHeldItem(hand) == original && original.getCount() == remaining) original.grow(reserved.getCount());
        else {
            player.inventory.addItemStackToInventory(reserved);
            if (!reserved.isEmpty()) player.dropItem(reserved, false);
        }
        player.inventory.markDirty();
    }
}
