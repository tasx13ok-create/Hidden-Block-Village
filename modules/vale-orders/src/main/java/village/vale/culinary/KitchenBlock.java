package village.vale.culinary;
import net.minecraft.block.*;import net.minecraft.block.material.Material;import net.minecraft.block.state.IBlockState;import net.minecraft.entity.player.EntityPlayer;import net.minecraft.inventory.InventoryHelper;import net.minecraft.tileentity.TileEntity;import net.minecraft.util.*;import net.minecraft.util.math.BlockPos;import net.minecraft.world.World;
public final class KitchenBlock extends BlockContainer {
 public final int machine;KitchenBlock(int m){super(Material.ROCK);machine=m;setHardness(2);setResistance(5);setHarvestLevel("pickaxe",0);setCreativeTab(net.minecraft.creativetab.CreativeTabs.DECORATIONS);}
 @Override public TileEntity createNewTileEntity(World w,int meta){return new KitchenTile(machine);}
 @Override public EnumBlockRenderType getRenderType(IBlockState s){return EnumBlockRenderType.MODEL;}
 @Override public boolean onBlockActivated(World w,BlockPos pos,IBlockState state,EntityPlayer p,EnumHand hand,EnumFacing side,float x,float y,float z){if(!w.isRemote&&w.getTileEntity(pos) instanceof KitchenTile){p.displayGUIChest((KitchenTile)w.getTileEntity(pos));p.sendStatusMessage(new net.minecraft.util.text.TextComponentString("Slots: inputs 1-3 | fuel 4 | meal 5 | returned containers 6-9"),true);}return true;}
 @Override public void breakBlock(World w,BlockPos pos,IBlockState state){TileEntity tile=w.getTileEntity(pos);if(!w.isRemote&&tile instanceof KitchenTile)InventoryHelper.dropInventoryItems(w,pos,(KitchenTile)tile);super.breakBlock(w,pos,state);}
}
