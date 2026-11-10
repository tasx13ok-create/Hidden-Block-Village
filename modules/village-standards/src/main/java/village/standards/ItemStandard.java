package village.standards;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public final class ItemStandard extends Item {
    private final StandardVariant variant;
    ItemStandard(StandardVariant variant) {
        this.variant = variant;
        String key = variant == null ? "unassigned_standard" : variant.itemKey();
        setRegistryName(new ResourceLocation(StandardsMod.ID, key));
        setTranslationKey(StandardsMod.ID + "." + key);
        setMaxStackSize(16);
        if (variant != null) setCreativeTab(CreativeTabs.DECORATIONS);
    }
    @Override public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos clicked,
            EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        if (!StandardGeometry.horizontal(side) || held.isEmpty() || held.getItem() != this) return EnumActionResult.FAIL;
        BlockPos anchor = clicked.offset(side);
        for (BlockPos backing : StandardGeometry.backingCells(anchor, side)) {
            BlockPos display = backing.offset(side);
            if (!world.isBlockLoaded(backing) || !world.isBlockLoaded(display)
                    || !world.isBlockModifiable(player, backing) || !world.isBlockModifiable(player, display)
                    || !player.canPlayerEdit(backing, side, held) || !player.canPlayerEdit(display, side, held)) {
                return EnumActionResult.FAIL;
            }
        }
        EntityStandard picture = new EntityStandard(world, anchor, side,
            StandardRecord.placed(variant, held, player.getUniqueID()));
        if (!picture.onValidSurface()) return EnumActionResult.FAIL;
        if (world.isRemote) return EnumActionResult.SUCCESS;
        // EntityJoinWorld cancellation must not consume the player's item.
        if (!world.spawnEntity(picture)) return EnumActionResult.FAIL;
        picture.playPlaceSound();
        if (!player.capabilities.isCreativeMode) held.shrink(1);
        return EnumActionResult.SUCCESS;
    }
    @Override @SideOnly(Side.CLIENT) public void addInformation(ItemStack stack, @Nullable World world,
            List<String> tooltip, ITooltipFlag flag) {
        tooltip.add("Place on the center of a solid 5 x 5 wall.");
        if (variant == null) tooltip.add("Saved display data kept; an operator can assign its village.");
    }
}
