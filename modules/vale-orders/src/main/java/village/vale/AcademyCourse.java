package village.vale;

import java.util.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;

/** Explicit operator-authored graybox. Never automatically edits an existing world. */
public final class AcademyCourse extends WorldSavedData {
    public static final String NAME="vale_academy_course";
    public static final String[] ROLES={"arrival","mentor","supplies","contracts","courier"};
    private static final int[][] POINTS={{-24,0},{-16,0},{16,0},{24,0},{24,32}};
    private static final String[] NAMES={"Dispatch Keeper - Welcome","Aki - Academy Mentor","Jun - Provisions","Mission Officer","Ren - Courier"};
    public boolean active,readOnly; public BlockPos origin=BlockPos.ORIGIN; public String problem="";
    private NBTTagCompound opaque;
    public final Map<String,UUID> guides=new LinkedHashMap<>();
    public AcademyCourse(){super(NAME);} public AcademyCourse(String name){super(name);}
    public static AcademyCourse get(WorldServer world){
        AcademyCourse d=(AcademyCourse)world.getMapStorage().getOrLoadData(AcademyCourse.class,NAME);
        if(d==null){d=new AcademyCourse();world.getMapStorage().setData(NAME,d);}return d;
    }
    public BlockPos at(int x,int z){return origin.add(x,1,z);}
    public boolean near(net.minecraft.entity.Entity entity,int x,int z,double range){return active&&entity.dimension==0&&entity.getDistanceSq(at(x,z))<=range*range;}
    public String role(Entity e){for(Map.Entry<String,UUID> row:guides.entrySet())if(row.getValue().equals(e.getUniqueID()))return row.getKey();return null;}
    public boolean build(WorldServer world,BlockPos anchor){
        problem="";
        if(world.provider.getDimension()!=0||active||readOnly||!guides.isEmpty()){problem="Course already exists or world/schema is unsupported";return false;}
        if(anchor.getY()<2||anchor.getY()>230||Math.abs((long)anchor.getX())>29999800||Math.abs((long)anchor.getZ())>29999800){problem="Position outside supported bounds";return false;}
        BlockPos lo=anchor.add(-32,0,-16),hi=anchor.add(32,9,48);
        if(!world.isAreaLoaded(lo,hi)||!world.getWorldBorder().contains(lo)||!world.getWorldBorder().contains(hi)){problem="Footprint is unloaded or crosses the world border";return false;}
        for(BlockPos p:BlockPos.getAllInBox(lo,hi))if(!world.isAirBlock(p)||world.getTileEntity(p)!=null){problem="Existing block at "+p;return false;}
        AxisAlignedBB box=new AxisAlignedBB(lo,hi.add(1,1,1));
        if(!world.getEntitiesWithinAABB(Entity.class,box).isEmpty()){problem="An entity occupies the footprint";return false;}
        origin=anchor;Map<BlockPos,IBlockState> placed=new LinkedHashMap<>();List<Entity> spawned=new ArrayList<>();
        try{
            for(int x=-32;x<=32;x++)for(int z=-16;z<=48;z++)put(world,placed,anchor.add(x,0,z),Blocks.STONEBRICK.getDefaultState());
            for(int x=-29;x<=29;x++)for(int z=-13;z<=45;z++)if(x%8==0||z%8==0)put(world,placed,anchor.add(x,0,z),Blocks.PLANKS.getDefaultState());
            for(int x=-6;x<=6;x++)for(int z=2;z<=14;z++)put(world,placed,anchor.add(x,0,z),(Math.abs(x)==6||z==2||z==14?Blocks.WOOL:Blocks.SANDSTONE).getDefaultState());
            for(int[] mark:new int[][]{{-20,12},{-12,24},{0,24}}){put(world,placed,anchor.add(mark[0],0,mark[1]),Blocks.LAPIS_BLOCK.getDefaultState());put(world,placed,anchor.add(mark[0],-0,mark[1]+1),Blocks.GLOWSTONE.getDefaultState());}
            // Open-sided classroom and kitchen: real supports, usable tables and roofs.
            for(int[] pavilion:new int[][]{{-16,-8},{16,8}}){
                for(int dx:new int[]{-4,4})for(int dz:new int[]{-3,3})for(int y=1;y<=4;y++)put(world,placed,anchor.add(pavilion[0]+dx,y,pavilion[1]+dz),Blocks.LOG.getDefaultState());
                for(int dx=-5;dx<=5;dx++)for(int dz=-4;dz<=4;dz++)put(world,placed,anchor.add(pavilion[0]+dx,5,pavilion[1]+dz),Blocks.WOODEN_SLAB.getDefaultState());
                put(world,placed,anchor.add(pavilion[0],4,pavilion[1]),Blocks.GLOWSTONE.getDefaultState());
            }
            put(world,placed,at(14,8),Blocks.CRAFTING_TABLE.getDefaultState());put(world,placed,at(18,8),Blocks.FURNACE.getDefaultState());
            for(int x=12;x<=20;x++)for(int z=16;z<=22;z++){
                if(x==16&&z==19){put(world,placed,anchor.add(x,0,z),Blocks.WATER.getDefaultState());continue;}
                put(world,placed,anchor.add(x,0,z),Blocks.FARMLAND.getDefaultState().withProperty(net.minecraft.block.BlockFarmland.MOISTURE,7));
                put(world,placed,at(x,z),(x<16?Blocks.WHEAT:Blocks.CARROTS).getStateFromMeta(7));
            }
            for(int x=-28;x<=28;x+=8)for(int z:new int[]{-12,44})put(world,placed,anchor.add(x,1,z),Blocks.GLOWSTONE.getDefaultState());
            for(int i=0;i<ROLES.length;i++){
                EntityVillager guide=new EntityVillager(world);guide.setPosition(at(POINTS[i][0],POINTS[i][1]).getX()+.5,at(POINTS[i][0],POINTS[i][1]).getY(),at(POINTS[i][0],POINTS[i][1]).getZ()+.5);
                guide.setNoAI(true);guide.setEntityInvulnerable(true);guide.enablePersistence();guide.setCustomNameTag(NAMES[i]);guide.setAlwaysRenderNameTag(true);
                if(!world.spawnEntity(guide))throw new IllegalStateException("Guide spawn rejected");spawned.add(guide);guides.put(ROLES[i],guide.getUniqueID());
            }
            active=true;markDirty();world.getMapStorage().saveAllData();return true;
        }catch(RuntimeException e){
            for(Entity actor:spawned){actor.setDead();world.removeEntity(actor);}guides.clear();active=false;problem=e.toString();
            for(Map.Entry<BlockPos,IBlockState> row:placed.entrySet())if(world.getBlockState(row.getKey()).equals(row.getValue()))world.setBlockToAir(row.getKey());
            return false;
        }
    }
    private static void put(WorldServer world,Map<BlockPos,IBlockState> journal,BlockPos p,IBlockState state){
        if(!world.setBlockState(p,state,2))throw new IllegalStateException("Block placement rejected at "+p+" for "+state);journal.put(p.toImmutable(),state);
    }
    @Override public void readFromNBT(NBTTagCompound n){
        guides.clear();active=false;readOnly=n.getInteger("schema")!=1;opaque=readOnly?n.copy():null;if(readOnly){problem="Unsupported course schema";return;}
        origin=BlockPos.fromLong(n.getLong("origin"));
        for(String role:ROLES)if(n.hasUniqueId(role))guides.put(role,n.getUniqueId(role));
        active=n.getBoolean("active")&&guides.size()==ROLES.length;
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){if(readOnly){n.merge(opaque);return n;}n.setInteger("schema",1);n.setLong("origin",origin.toLong());n.setBoolean("active",active);for(Map.Entry<String,UUID> e:guides.entrySet())n.setUniqueId(e.getKey(),e.getValue());return n;}
}
