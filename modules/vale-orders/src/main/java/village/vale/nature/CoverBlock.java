package village.vale.nature;

import java.util.Random;
import net.minecraft.block.*;
import net.minecraft.block.material.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import village.vale.ValeOrders;

/** Original temporary cover, rendered with Minecraft's own cobblestone model. No inventory item. */
@Mod.EventBusSubscriber(modid=ValeOrders.ID)
public final class CoverBlock extends Block {
    public static final CoverBlock BLOCK=new CoverBlock();
    private CoverBlock(){super(Material.ROCK);setRegistryName(ValeOrders.ID,"nature_cover");setTranslationKey("valeorders.nature_cover");setHardness(1.5F);setResistance(12);setSoundType(SoundType.STONE);}
    @SubscribeEvent public static void register(RegistryEvent.Register<Block> event){event.getRegistry().register(BLOCK);}
    @Override public EnumPushReaction getPushReaction(IBlockState state){return EnumPushReaction.BLOCK;}
    @Override public Item getItemDropped(IBlockState state,Random random,int fortune){return Items.AIR;}
    @Override public void getDrops(NonNullList<ItemStack> drops,IBlockAccess world,BlockPos pos,IBlockState state,int fortune){}
    @Override public boolean canSilkHarvest(World world,BlockPos pos,IBlockState state,EntityPlayer player){return false;}
    @Override public ItemStack getPickBlock(IBlockState state,RayTraceResult target,World world,BlockPos pos,EntityPlayer player){return ItemStack.EMPTY;}
}
