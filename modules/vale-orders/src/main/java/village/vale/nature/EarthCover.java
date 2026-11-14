package village.vale.nature;

import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import village.vale.ValeOrders;

/** Six temporary solid blocks. Journaled per dimension; never overwrites terrain or tile data. */
@Mod.EventBusSubscriber(modid=ValeOrders.ID)
public final class EarthCover extends WorldSavedData {
    private static final String NAME="vale_nature_cover";
    private final Map<BlockPos,Lease> leases=new LinkedHashMap<>();
    private boolean readOnly,placing;
    private NBTTagCompound unknown;
    private static final class Lease {
        final BlockPos pos;final UUID owner;final long until;final int original,expected;
        Lease(BlockPos p,UUID owner,long until,int original,int expected){this.pos=p.toImmutable();this.owner=owner;this.until=until;this.original=original;this.expected=expected;}
    }
    public EarthCover(){super(NAME);} public EarthCover(String name){super(name);}
    public static EarthCover get(WorldServer world){
        EarthCover data=(EarthCover)world.getPerWorldStorage().getOrLoadData(EarthCover.class,NAME);
        if(data==null){data=new EarthCover();world.getPerWorldStorage().setData(NAME,data);}return data;
    }
    public int count(){return leases.size();}
    public int owned(UUID owner){int count=0;for(Lease l:leases.values())if(l.owner.equals(owner))count++;return count;}
    public static BlockPos center(EntityPlayerMP p){return p.getPosition().offset(p.getHorizontalFacing(),2);}
    /** Reservation records exist before writes/protection callbacks; callbacks cannot recursively place. */
    static boolean raise(EntityPlayerMP p,NatureMoves.Attempt attempt){
        WorldServer world=p.getServerWorld();EarthCover data=get(world);data.expire(world,world.getTotalWorldTime());
        if(data.readOnly||data.placing||!NatureRules.roomFor(data.count(),data.owned(p.getUniqueID()),6))return false;
        EnumFacing facing=p.getHorizontalFacing();List<Lease> made=new ArrayList<>();List<BlockSnapshot> snapshots=new ArrayList<>();
        int expected=Block.getStateId(CoverBlock.BLOCK.getDefaultState());BlockPos base=p.getPosition();
        for(int[] point:NatureRules.cover(base.getX(),base.getY(),base.getZ(),facing.getXOffset(),facing.getZOffset())){
            BlockPos pos=new BlockPos(point[0],point[1],point[2]);
            if(pos.getY()<1||pos.getY()>254||!world.isBlockLoaded(pos)||!world.getWorldBorder().contains(pos)||data.leases.containsKey(pos)
                ||!world.isAirBlock(pos)||world.getTileEntity(pos)!=null||!world.isBlockModifiable(p,pos)||!p.canPlayerEdit(pos,EnumFacing.UP,ItemStack.EMPTY)
                ||!world.getEntitiesWithinAABB(Entity.class,new AxisAlignedBB(pos)).isEmpty())return false;
            if(pos.getY()==base.getY()&&(!world.isBlockLoaded(pos.down())||!world.getBlockState(pos.down()).getMaterial().isSolid()))return false;
            snapshots.add(BlockSnapshot.getBlockSnapshot(world,pos));
            made.add(new Lease(pos,p.getUniqueID(),world.getTotalWorldTime()+NatureRules.COVER_LIFE,Block.getStateId(world.getBlockState(pos)),expected));
        }
        data.placing=true;boolean accepted=false;Set<BlockPos> touched=new HashSet<>();
        try{
            for(Lease l:made)data.leases.put(l.pos,l);data.markDirty();world.getPerWorldStorage().saveAllData();
            for(Lease l:made){
                if(!attempt.valid()||!world.isAirBlock(l.pos)||world.getTileEntity(l.pos)!=null)return false;
                touched.add(l.pos); // Native notifications can throw after the state has already changed.
                if(!world.setBlockState(l.pos,Block.getStateById(l.expected),3))return false;
            }
            // Exactly the native multi-place protection hook used for player-owned multi-block placement.
            BlockEvent.MultiPlaceEvent event=ForgeEventFactory.onPlayerMultiBlockPlace(p,snapshots,EnumFacing.UP,EnumHand.MAIN_HAND);
            if(event.isCanceled()||!attempt.valid())return false;
            for(Lease l:made)if(!world.isBlockLoaded(l.pos)||world.getTileEntity(l.pos)!=null||Block.getStateId(world.getBlockState(l.pos))!=l.expected)return false;
            accepted=true;attempt.applied=true;return true;
        }finally{
            try{
                RuntimeException failure=null;
                if(!accepted)for(Lease l:made){
                    if(!touched.contains(l.pos)){data.leases.remove(l.pos);continue;}
                    try{data.restore(world,l);}catch(RuntimeException ex){if(failure==null)failure=ex;}
                    // Continue every rollback after an exception. An unobservable/unloaded native write
                    // remains journaled and charged; it must not become a free orphaned wall.
                    boolean remains=true;
                    try{remains=!world.isBlockLoaded(l.pos)||(world.getTileEntity(l.pos)==null&&Block.getStateId(world.getBlockState(l.pos))==l.expected);}
                    catch(RuntimeException ex){if(failure==null)failure=ex;}
                    if(remains)attempt.applied=true;else data.leases.remove(l.pos);
                }
                data.markDirty();world.getPerWorldStorage().saveAllData();
                if(failure!=null)throw failure;
            }
            finally{data.placing=false;}
        }
    }
    /** Restore only still-owned states; preserve every intervening player/mod edit and all tile entities. */
    private void restore(WorldServer world,Lease l){
        if(!world.isBlockLoaded(l.pos))return;
        if(NatureRules.mayRestore(true,world.getTileEntity(l.pos)!=null,Block.getStateId(world.getBlockState(l.pos)),l.expected))world.setBlockState(l.pos,Block.getStateById(l.original),3);
    }
    public void expire(WorldServer world,long now){
        if(readOnly||placing)return;
        Iterator<Lease> it=leases.values().iterator();int work=0;
        while(it.hasNext()&&work++<NatureRules.MAX_WORLD_BLOCKS){Lease l=it.next();
            if(!world.isBlockLoaded(l.pos))continue;
            boolean matches=world.getTileEntity(l.pos)==null&&Block.getStateId(world.getBlockState(l.pos))==l.expected;
            if(!matches||now>=l.until||now<l.until-NatureRules.COVER_LIFE){if(matches)restore(world,l);it.remove();markDirty();}
        }
    }
    @SubscribeEvent public static void tick(TickEvent.WorldTickEvent event){if(event.phase==TickEvent.Phase.END&&!event.world.isRemote&&event.world instanceof WorldServer){WorldServer w=(WorldServer)event.world;if(w.getTotalWorldTime()%5==0)get(w).expire(w,w.getTotalWorldTime());}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void explosion(ExplosionEvent.Detonate event){
        if(event.getWorld().isRemote||!(event.getWorld() instanceof WorldServer))return;EarthCover d=get((WorldServer)event.getWorld());
        // The short-lived cover remains solid during explosions; it cannot be farmed for blocks.
        event.getAffectedBlocks().removeIf(pos->{Lease l=d.leases.get(pos);return l!=null&&Block.getStateId(event.getWorld().getBlockState(pos))==l.expected;});
    }
    @Override public void readFromNBT(NBTTagCompound n){
        leases.clear();readOnly=n.getInteger("schema")!=1;
        if(readOnly){unknown=n.copy();return;}
        NBTTagList list=n.getTagList("leases",10);if(list.tagCount()>NatureRules.MAX_WORLD_BLOCKS){readOnly=true;unknown=n.copy();return;}
        for(int i=0;i<list.tagCount();i++){NBTTagCompound r=list.getCompoundTagAt(i);BlockPos pos=BlockPos.fromLong(r.getLong("pos"));
            if(!r.hasUniqueId("owner")||pos.getY()<1||pos.getY()>254||!"minecraft:air".equals(r.getString("original"))||!"valeorders:nature_cover".equals(r.getString("expected"))||leases.containsKey(pos)){readOnly=true;unknown=n.copy();leases.clear();return;}
            leases.put(pos,new Lease(pos,r.getUniqueId("owner"),r.getLong("until"),Block.getStateId(Blocks.AIR.getDefaultState()),Block.getStateId(CoverBlock.BLOCK.getDefaultState())));
        }
    }
    @Override public NBTTagCompound writeToNBT(NBTTagCompound n){
        if(readOnly&&unknown!=null)return unknown.copy();n.setInteger("schema",1);NBTTagList list=new NBTTagList();
        for(Lease l:leases.values()){NBTTagCompound r=new NBTTagCompound();r.setLong("pos",l.pos.toLong());r.setUniqueId("owner",l.owner);r.setLong("until",l.until);r.setString("original","minecraft:air");r.setString("expected","valeorders:nature_cover");list.appendTag(r);}n.setTag("leases",list);return n;
    }
}
