package village.vale.worldcompat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.*;
import net.minecraft.entity.player.*;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.nbt.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.*;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import village.vale.Quartermaster;

/** Original save bridge. Legacy technical identifiers are data compatibility only. */
@Mod(modid=WorldCompat.ID,name="Vale World Furnishings",version="1.0.0",dependencies="required-after:valeorders",acceptedMinecraftVersions="[1.12.2]")
@Mod.EventBusSubscriber(modid=WorldCompat.ID)
public final class WorldCompat {
 public static final String ID="valeworldcompat";
 public static final Furnishing MARKER=new Furnishing("waystones:waystone",0,Material.ROCK);
 public static final Furnishing TABLE=new Furnishing("variedcommodities:table",1,Material.WOOD);
 public static final Furnishing FIRE=new Furnishing("variedcommodities:campfire",2,Material.ROCK);
 public static final Furnishing BOARD=new Furnishing("variedcommodities:trading_block",3,Material.WOOD);
 public static final Furnishing[] BLOCKS={MARKER,TABLE,FIRE,BOARD};
 static final List<Destination> DESTINATIONS=new ArrayList<>();
 static final Map<UUID,String> SELECTED=new HashMap<>();
 static final Map<UUID,Long> COOLDOWNS=new HashMap<>();
 @SubscribeEvent public static void blocks(RegistryEvent.Register<Block> e){for(Furnishing b:BLOCKS)e.getRegistry().register(b);}
 @SubscribeEvent public static void items(RegistryEvent.Register<Item> e){for(Furnishing b:BLOCKS)e.getRegistry().register(new ItemBlock(b).setRegistryName(b.getRegistryName()));}
 @Mod.EventHandler public void preInit(FMLPreInitializationEvent e)throws IOException {
  GameRegistry.registerTileEntity(MarkerTile.class,new ResourceLocation("waystones:waystone"));
  GameRegistry.registerTileEntity(TableTile.class,new ResourceLocation("variedcommodities:table"));
  GameRegistry.registerTileEntity(FireTile.class,new ResourceLocation("variedcommodities:campfire"));
  GameRegistry.registerTileEntity(BoardTile.class,new ResourceLocation("variedcommodities:trading_block"));
  File file=new File(e.getModConfigurationDirectory(),"valeworldcompat-destinations.tsv");
  DESTINATIONS.clear();
  if(file.isFile())for(String line:Files.readAllLines(file.toPath(),StandardCharsets.UTF_8)) {
   if(line.trim().isEmpty()||line.startsWith("#"))continue;
   String[] c=line.split("\\t",5); if(c.length!=5)throw new IOException("Invalid travel marker row: "+line);
   try {Destination d=new Destination(Integer.parseInt(c[0]),new BlockPos(Integer.parseInt(c[1]),Integer.parseInt(c[2]),Integer.parseInt(c[3])),c[4]);if(d.name.trim().isEmpty())throw new IllegalArgumentException();DESTINATIONS.add(d);}catch(RuntimeException ex){throw new IOException("Invalid travel marker coordinates",ex);}
  }
 }
 @Mod.EventHandler public void stop(FMLServerStoppedEvent e){SELECTED.clear();COOLDOWNS.clear();}
 static void tell(EntityPlayer p,String text){p.sendMessage(new TextComponentString(text));}
 static final class Destination {final int dimension;final BlockPos pos;final String name;Destination(int d,BlockPos p,String n){dimension=d;pos=p;name=n;}String key(){return dimension+":"+pos.toLong();}}
 public static final class Furnishing extends Block {
  static final PropertyInteger LEGACY=PropertyInteger.create("legacy",0,15);final int kind;
  Furnishing(String id,int k,Material m){super(m);kind=k;setRegistryName(new ResourceLocation(id));setTranslationKey(ID+"."+new ResourceLocation(id).getPath());setHardness(2);setResistance(8);setDefaultState(blockState.getBaseState().withProperty(LEGACY,0));if(k==2)setLightLevel(0.875F);}
  protected BlockStateContainer createBlockState(){return new BlockStateContainer(this,LEGACY);}
  public IBlockState getStateFromMeta(int m){return getDefaultState().withProperty(LEGACY,m&15);}public int getMetaFromState(IBlockState s){return s.getValue(LEGACY);}
  public boolean hasTileEntity(IBlockState s){return true;}
  public TileEntity createTileEntity(World w,IBlockState s){return kind==0?new MarkerTile():kind==1?new TableTile():kind==2?new FireTile():new BoardTile();}
  public boolean isOpaqueCube(IBlockState s){return false;}public boolean isFullCube(IBlockState s){return false;}
  public AxisAlignedBB getBoundingBox(IBlockState s,IBlockAccess w,BlockPos p){return kind==0?new AxisAlignedBB(.25,0,.25,.75,1,.75):kind==2?new AxisAlignedBB(.1,0,.1,.9,.35,.9):new AxisAlignedBB(0,0,0,1,1,1);}
  public boolean onBlockActivated(World w,BlockPos pos,IBlockState s,EntityPlayer p,EnumHand h,EnumFacing f,float x,float y,float z){
   if(w.isRemote)return true;if(!(p instanceof EntityPlayerMP)||!p.isEntityAlive()||p.isSpectator()||p.getDistanceSq(pos)>64||h!=EnumHand.MAIN_HAND)return true;
   if(kind==0)travel((EntityPlayerMP)p,pos);else if(kind==1)p.displayGui(new Crafting(w,pos));else if(kind==3)Quartermaster.show((EntityPlayerMP)p,p.isSneaking()?"tools":"ward");else {TileEntity te=w.getTileEntity(pos);if(te instanceof FireTile)((FireTile)te).cook(p,h);}return true;
  }
  public void breakBlock(World w,BlockPos p,IBlockState s){TileEntity te=w.getTileEntity(p);if(te instanceof FireTile)((FireTile)te).release();super.breakBlock(w,p,s);}
 }
 /** Unknown saved fields survive unchanged; runtime-owned fields use a private prefix. */
 public static class SavedTile extends TileEntity {
  NBTTagCompound opaque=new NBTTagCompound();
  public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);opaque=n.copy();}
  public NBTTagCompound writeToNBT(NBTTagCompound n){n.merge(opaque.copy());return super.writeToNBT(n);}
 }
 public static final class MarkerTile extends SavedTile {} public static final class TableTile extends SavedTile {} public static final class BoardTile extends SavedTile {}
 public static final class FireTile extends SavedTile implements ITickable {
  ItemStack pending=ItemStack.EMPTY;int remaining;
  public void readFromNBT(NBTTagCompound n){super.readFromNBT(n);pending=new ItemStack(n.getCompoundTag("ValeCookInput"));remaining=Math.max(1,Math.min(100,n.getInteger("ValeCookTicks")));}
  public NBTTagCompound writeToNBT(NBTTagCompound n){super.writeToNBT(n);n.setTag("ValeCookInput",pending.writeToNBT(new NBTTagCompound()));n.setInteger("ValeCookTicks",remaining);return n;}
  void cook(EntityPlayer p,EnumHand h){if(!pending.isEmpty()){tell(p,"The hearth is cooking. Food will appear above the coals.");return;}ItemStack held=p.getHeldItem(h);ItemStack result=FurnaceRecipes.instance().getSmeltingResult(held);if(held.isEmpty()||result.isEmpty()||!(result.getItem() instanceof ItemFood)){tell(p,"Place one raw food on the hearth to cook it.");return;}pending=held.copy();pending.setCount(1);held.shrink(1);remaining=100;markDirty();}
  public void update(){if(world==null||world.isRemote||pending.isEmpty())return;if(--remaining>0){if(remaining%20==0)markDirty();return;}ItemStack cooked=FurnaceRecipes.instance().getSmeltingResult(pending);if(cooked.isEmpty()||!(cooked.getItem() instanceof ItemFood))cooked=pending;ItemStack drop=cooked.copy();pending=ItemStack.EMPTY;remaining=0;markDirty();Block.spawnAsEntity(world,pos.up(),drop);}
  void release(){if(world!=null&&!world.isRemote&&!pending.isEmpty()){ItemStack drop=pending;pending=ItemStack.EMPTY;markDirty();Block.spawnAsEntity(world,pos.up(),drop);}}
 }
 static final class Crafting implements IInteractionObject {
  final World world;final BlockPos pos;Crafting(World w,BlockPos p){world=w;pos=p;}
  public String getName(){return "container.crafting";}public boolean hasCustomName(){return false;}public net.minecraft.util.text.ITextComponent getDisplayName(){return new net.minecraft.util.text.TextComponentTranslation(getName());}public String getGuiID(){return "minecraft:crafting_table";}
  public Container createContainer(InventoryPlayer inv,EntityPlayer p){return new ContainerWorkbench(inv,world,pos){public boolean canInteractWith(EntityPlayer user){return user.isEntityAlive()&&!user.isSpectator()&&world.getBlockState(pos).getBlock()==TABLE&&user.getDistanceSq(pos)<=64;}};}
 }
 static void travel(EntityPlayerMP p,BlockPos source){
  List<Destination> choices=new ArrayList<>();for(Destination d:DESTINATIONS)if(d.dimension==p.dimension&&!d.pos.equals(source))choices.add(d);
  if(choices.isEmpty()){tell(p,"No other indexed travel markers are available in this dimension.");return;}
  int index=-1;String key=SELECTED.get(p.getUniqueID());for(int i=0;i<choices.size();i++)if(choices.get(i).key().equals(key))index=i;
  if(index<0||p.isSneaking()){index=(index+1)%choices.size();SELECTED.put(p.getUniqueID(),choices.get(index).key());tell(p,"Travel marker: "+choices.get(index).name+". Sneak-use to cycle; use to travel.");return;}
  if(p.isRiding()||p.isBeingRidden()){tell(p,"Dismount before travelling.");return;}long now=System.nanoTime();Long until=COOLDOWNS.get(p.getUniqueID());if(until!=null&&now<until){tell(p,"The marker is recharging.");return;}
  Destination d=choices.get(index);WorldServer w=p.getServerWorld();w.getChunk(d.pos);if(!w.getWorldBorder().contains(d.pos)||w.getBlockState(d.pos).getBlock()!=MARKER||!(w.getTileEntity(d.pos) instanceof MarkerTile)){tell(p,"That destination no longer resolves to a travel marker.");return;}
  BlockPos safe=safeLanding(w,d.pos,p);if(safe==null){tell(p,"No safe landing is available at that marker.");return;}
  if(p.world!=w||!p.isEntityAlive()||p.getDistanceSq(source)>64)return;
  COOLDOWNS.put(p.getUniqueID(),now+10_000_000_000L);p.connection.setPlayerLocation(safe.getX()+.5,safe.getY(),safe.getZ()+.5,p.rotationYaw,p.rotationPitch);p.fallDistance=0;tell(p,"Arrived at "+d.name+".");
 }
 static BlockPos safeLanding(WorldServer w,BlockPos marker,EntityPlayerMP p){for(int r=1;r<=4;r++)for(int dy=0;dy<=3;dy++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
  if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;BlockPos q=marker.add(dx,dy,dz);if(q.getY()<1||q.getY()>253||!w.getWorldBorder().contains(q)||!w.isBlockLoaded(q)||!w.getBlockState(q.down()).isSideSolid(w,q.down(),EnumFacing.UP))continue;
  Block floor=w.getBlockState(q.down()).getBlock();if(floor==Blocks.MAGMA||floor==Blocks.CACTUS||floor==FIRE)continue;
  AxisAlignedBB box=new AxisAlignedBB(q.getX()+.2,q.getY(),q.getZ()+.2,q.getX()+.8,q.getY()+1.8,q.getZ()+.8);
  if(w.getCollisionBoxes(p,box).isEmpty()&&!w.containsAnyLiquid(box)&&w.getBlockState(q).getBlock()!=Blocks.FIRE&&w.getBlockState(q.up()).getBlock()!=Blocks.FIRE)return q;
 }return null;}
}

