package village.vale.nature;

import java.util.*;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.*;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.*;
import village.vale.*;

/** Actual casts in the existing disposable native fixture. Never part of the release jar. */
public final class NatureNativeChecks {
    private final EntityPlayerMP p;private final WorldServer world;private final Gate gate=new Gate();
    private final List<Entity> actors=new ArrayList<>();private final Map<BlockPos,IBlockState> terrain=new LinkedHashMap<>();
    private int assertions,receipt;private long clock;private ItemStack codex;
    private static final int X=512,Y=90,Z=512;
    private NatureNativeChecks(EntityPlayerMP p){this.p=p;world=p.getServerWorld();clock=Adventures.now(p)+500;gate.p=p;}
    public static int run(EntityPlayerMP p){
        if(!Boolean.getBoolean("valeorders.recoverycheck")||!"vale-recovery-test".equals(p.getServer().getFolderName()))throw new IllegalStateException("Private nature fixture required");
        NatureNativeChecks c=new NatureNativeChecks(p);MinecraftForge.EVENT_BUS.register(c.gate);
        try{c.exercise();return c.assertions;}finally{MinecraftForge.EVENT_BUS.unregister(c.gate);c.cleanup();}
    }
    private void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError("Nature native: "+why);}
    private void advance(int ticks){clock+=ticks;world.getWorldInfo().setWorldTotalTime(clock);}
    private void place(){p.setPositionAndRotation(X+.5,Y,Z+.5,0,0);p.rotationYawHead=0;p.setSneaking(false);p.motionX=p.motionY=p.motionZ=0;}
    private ItemStack issue(String key){RewardLedger ledger=RewardLedger.get(p);ledger.issue(p,key,1,"nature/native/"+(receipt++));
        for(ItemStack stack:p.inventory.mainInventory)if(!stack.isEmpty()&&stack.getItem()==Gear.ITEMS.get(key)&&ledger.valid(p,stack))return stack;
        throw new AssertionError("Missing nature fixture item "+key);
    }
    private void setup(){
        for(int cx=31;cx<=33;cx++)for(int cz=31;cz<=33;cz++)world.getChunk(cx,cz);
        for(int x=X-5;x<=X+8;x++)for(int z=Z-5;z<=Z+13;z++)for(int y=Y-1;y<=Y+5;y++){
            BlockPos pos=new BlockPos(x,y,z);if(world.getTileEntity(pos)!=null)throw new AssertionError("Nature fixture terrain occupied");
            terrain.put(pos,world.getBlockState(pos));world.setBlockState(pos,y==Y-1?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
        }
        for(int i=0;i<p.inventory.mainInventory.size();i++)p.inventory.mainInventory.set(i,ItemStack.EMPTY);
        p.inventory.currentItem=0;PlayerState.data(p).setInteger("xp",2000);
        String[] parts={"helmet","chestplate","leggings","boots"};EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
        for(int i=0;i<parts.length;i++){ItemStack stack=issue("ember_"+parts[i]);remove(stack);p.setItemStackToSlot(slots[i],stack);}
        codex=issue("technique_codex");remove(codex);p.inventory.mainInventory.set(0,codex);place();
        check(Combat.fullSet(p)!=null&&PlayerState.level(p)>=2,"Issued armor/level setup failed");
    }
    private void remove(ItemStack chosen){for(int i=0;i<p.inventory.mainInventory.size();i++)if(p.inventory.mainInventory.get(i)==chosen)p.inventory.mainInventory.set(i,ItemStack.EMPTY);}
    private void ready(String order){advance(300);place();Forms.end(p,false);p.inventory.mainInventory.set(0,codex);PlayerState.setResolve(p,100);NBTTagCompound s=PlayerState.data(p);s.setString("starterOrder",order);s.setString("selectedArt",order+"_lance");s.setLong("artGlobal",0);s.setTag("artCooldowns",new NBTTagCompound());p.removePotionEffect(MobEffects.SLOWNESS);}
    private Target target(double x,double z){Target t=new Target(world);t.setNoAI(true);t.setSilent(true);t.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);t.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0);t.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(0);t.setHealth(1000);t.setPosition(X+.5+x,Y,Z+.5+z);check(world.spawnEntity(t),"Target spawn");t.motionX=t.motionY=t.motionZ=0;t.extinguish();actors.add(t);return t;}
    private void clearActors(){for(Entity e:actors){e.world=world;world.removeEntityDangerously(e);}actors.clear();gate.target=null;gate.stage=0;gate.reenter=false;gate.place=false;gate.arrow=false;gate.strip=false;gate.worldSwap=false;gate.teamSwap=false;}
    private void wall(boolean present){for(int x=X-2;x<=X+2;x++)for(int y=Y;y<=Y+3;y++)world.setBlockState(new BlockPos(x,y,Z+2),present?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);}
    private void exercise(){
        setup();
        ready("ember");Target front=target(0,4),flank=target(2.5,2.5),behind=target(0,-3);
        check(Arts.cast(p),"Fire burst failed");check(front.getHealth()<1000&&front.isBurning(),"Fire contact did not burn permitted target");
        check(flank.getHealth()==1000&&behind.getHealth()==1000,"Fire narrow directional boundary failed");
        check(PlayerState.resolve(p)==82&&!Arts.cast(p),"Fire cost/cooldown not enforced");
        for(int x=X-4;x<X+5;x++)for(int z=Z-4;z<Z+10;z++)check(world.getBlockState(new BlockPos(x,Y,z)).getBlock()!=Blocks.FIRE,"Fire placed building arson");
        clearActors();ready("current");front=target(0,3);flank=target(2.5,2.5);front.setFire(20);
        check(Arts.cast(p),"Dry-land Water wave failed");check(!front.isBurning()&&front.isPotionActive(MobEffects.SLOWNESS)&&front.motionZ>0,"Water did not extinguish/control/push");
        check(flank.getHealth()<1000&&front.getEntityData().getLong("valeNatureWetUntil")>world.getTotalWorldTime(),"Water flank/wet aftermath missing");
        clearActors();
        for(String nature:new String[]{"ember","current","gale","storm"})for(int stage=1;stage<=3;stage++){
            ready(nature);front=target(0,3);gate.target=front;gate.stage=stage;float before=front.getHealth();
            // Forge may retain vanilla contact knockback after late damage cancellation.
            // Compare the cast to that real pipeline instead of demanding a motionless target.
            ArsenalAccess.hit(p,front,nature.equals("ember")?6:nature.equals("current")?3:nature.equals("gale")?2:9);
            double nativeX=front.motionX,nativeY=front.motionY,nativeZ=front.motionZ;
            front.motionX=front.motionY=front.motionZ=0;front.hurtResistantTime=0;
            check(!Arts.cast(p)&&front.getHealth()==before&&PlayerState.resolve(p)==100,"Canceled "+nature+" spent chakra/accepted at stage "+stage);
            check(!front.isBurning()&&!front.isPotionActive(MobEffects.SLOWNESS)&&!front.isPotionActive(MobEffects.GLOWING)&&!front.getEntityData().hasKey("valeNatureWetUntil")&&Math.abs(front.motionX-nativeX)<.0001&&Math.abs(front.motionY-nativeY)<.0001&&Math.abs(front.motionZ-nativeZ)<.0001,"Canceled "+nature+" applied elemental aftermath at stage "+stage+"; motion="+front.motionZ+" native="+nativeZ);clearActors();
        }
        ready("ember");front=target(0,3);gate.target=front;gate.reenter=true;
        check(Arts.cast(p)&&gate.rejected&&gate.observed==82&&PlayerState.resolve(p)==82,"Native callback reentry or pre-reservation failed");clearActors();
        ready("ember");front=target(0,3);front.throwAfter=true;boolean threw=false;
        try{Arts.cast(p);}catch(IllegalStateException expected){threw=true;}
        check(threw&&front.getHealth()<1000&&PlayerState.resolve(p)==82,"Irreversible damage exception got a refund");
        check(PlayerState.data(p).getCompoundTag("artCooldowns").getLong("ember_lance")>clock,"Exception lost spent cooldown");clearActors();
        for(String nature:new String[]{"ember","current","gale","storm"}){
            ready(nature);front=target(0,3);gate.target=front;gate.strip=true;
            check(Arts.cast(p)&&front.getHealth()<1000&&PlayerState.resolve(p)<100,"Accepted damage lost settlement after codex removal: "+nature);
            check(!front.isBurning()&&!front.isPotionActive(MobEffects.SLOWNESS)&&!front.isPotionActive(MobEffects.GLOWING),"Changed caster authority leaked target aftermath: "+nature);clearActors();
        }
        ScorePlayerTeam oldTeam=world.getScoreboard().getPlayersTeam(p.getName());ScorePlayerTeam fixtureTeam=world.getScoreboard().getTeam("nature_guard");
        if(fixtureTeam==null)fixtureTeam=world.getScoreboard().createTeam("nature_guard");
        world.getScoreboard().addPlayerToTeam(p.getName(),fixtureTeam.getName());gate.team=fixtureTeam;
        ready("ember");front=target(0,3);gate.target=front;gate.teamSwap=true;
        check(Arts.cast(p)&&front.getHealth()<1000&&PlayerState.resolve(p)==82&&!front.isBurning(),"Accepted callback team change leaked Fire aftermath or refunded damage");
        world.getScoreboard().removePlayerFromTeams(front.getCachedUniqueIdString());world.getScoreboard().removePlayerFromTeams(p.getName());
        if(oldTeam!=null)world.getScoreboard().addPlayerToTeam(p.getName(),oldTeam.getName());clearActors();
        ready("ember");front=target(0,3);gate.target=front;gate.worldSwap=true;
        check(Arts.cast(p)&&front.getHealth()<1000&&PlayerState.resolve(p)==82&&!front.isBurning(),"Accepted callback world change leaked Fire aftermath or refunded damage");clearActors();
        for(String nature:new String[]{"ember","current","gale","storm"}){
            ready(nature);front=target(0,4);wall(true);check(!Arts.cast(p)&&front.getHealth()==1000&&PlayerState.resolve(p)==100,"Wall did not block "+nature);wall(false);clearActors();
        }
        ready("storm");front=target(0,3);float before=front.getHealth();check(Arts.cast(p),"Precise Lightning strike failed");float dry=before-front.getHealth();
        check(p.isPotionActive(MobEffects.SLOWNESS),"Lightning commitment recovery missing");clearActors();
        ready("storm");front=target(0,3);front.getEntityData().setLong("valeNatureWetUntil",world.getTotalWorldTime()+60);before=front.getHealth();check(Arts.cast(p)&&before-front.getHealth()>dry,"Defined Water/Lightning reaction missing");clearActors();
        ready("storm");front=target(0,8);check(!Arts.cast(p)&&PlayerState.resolve(p)==100,"Lightning exceeds short committed range");clearActors();
        ready("gale");front=target(0,8);check(Arts.cast(p)&&front.motionZ>0,"Longer gust contact/push missing");clearActors();
        ready("gale");Target shooter=target(5,5);EntityTippedArrow hostile=arrow(shooter,0,3);EntityCow cow=new EntityCow(world);cow.setPosition(X+6,Y,Z+5);check(world.spawnEntity(cow),"Cow spawn");actors.add(cow);EntityTippedArrow friendly=arrow(cow,1,3);
        check(Arts.cast(p)&&hostile.isDead&&!friendly.isDead,"Wind did not distinguish hostile/friendly arrows");clearActors();
        ready("gale");shooter=target(5,5);hostile=arrow(shooter,0,3);gate.arrow=true;
        check(!Arts.cast(p)&&!hostile.isDead&&PlayerState.resolve(p)==100,"Arrow protection cancellation bypassed");clearActors();
        ready("gale");shooter=target(5,5);FaultArrow fault=new FaultArrow(world);fault.shootingEntity=shooter;fault.setPosition(X+.5,Y+1.4,Z+3.5);fault.motionZ=-.6;check(world.spawnEntity(fault),"Fault arrow spawn");actors.add(fault);boolean arrowThrew=false;
        try{Arts.cast(p);}catch(IllegalStateException expected){arrowThrew=true;}
        check(arrowThrew&&fault.isDead&&PlayerState.resolve(p)==84&&PlayerState.data(p).getCompoundTag("artCooldowns").getLong("gale_lance")>clock,"Irreversible arrow removal exception got a refund");clearActors();
        ready("flint");EarthCover data=EarthCover.get(world);check(data.count()==0,"Fixture has old cover");gate.place=true;
        check(!Arts.cast(p)&&data.count()==0&&PlayerState.resolve(p)==100&&world.isAirBlock(EarthCover.center(p)),"Canceled multi-place left cover/cost");gate.place=false;
        gate.place=true;gate.rollbackFailure=true;boolean rollbackThrew=false;
        try{Arts.cast(p);}catch(RuntimeException expected){rollbackThrew=true;}
        check(rollbackThrew&&gate.rollbackAttempted&&data.count()==1&&PlayerState.resolve(p)==76,"Native rollback exception left free/orphaned cover");
        check(PlayerState.data(p).getCompoundTag("artCooldowns").getLong("flint_lance")>clock,"Native rollback exception lost cooldown");
        gate.place=false;gate.rollbackFailure=false;advance(200);data.expire(world,world.getTotalWorldTime());
        check(data.count()==0,"Recovery lease did not clean interrupted native rollback");ready("flint");
        check(Arts.cast(p)&&data.count()==6&&data.owned(p.getUniqueID())==6&&PlayerState.resolve(p)==76,"Native Earth wall failed");
        BlockPos cover=EarthCover.center(p);check(world.getBlockState(cover).getBlock()==CoverBlock.BLOCK&&!world.getCollisionBoxes(p,new net.minecraft.util.math.AxisAlignedBB(cover)).isEmpty(),"Earth cover is not physically solid");
        check(CoverBlock.BLOCK.getPushReaction(CoverBlock.BLOCK.getDefaultState())==EnumPushReaction.BLOCK&&CoverBlock.BLOCK.getDrops(world,cover,CoverBlock.BLOCK.getDefaultState(),3).isEmpty(),"Cover can be moved/farmed");
        EntityCow pick=new EntityCow(world);check(CoverBlock.BLOCK.getPickBlock(CoverBlock.BLOCK.getDefaultState(),null,world,cover,p).isEmpty(),"Cover has a cloneable inventory item");
        EarthCover loaded=new EarthCover();loaded.readFromNBT(data.writeToNBT(new NBTTagCompound()));check(loaded.count()==6,"Cover lease restart data lost");
        advance(21);PlayerState.setResolve(p,100);PlayerState.data(p).getCompoundTag("artCooldowns").setLong("flint_lance",0);
        check(!Arts.cast(p)&&PlayerState.resolve(p)==100,"Owner can exceed one wall budget");
        BlockPos edited=cover.add(1,0,0),tile=cover.up();world.setBlockState(edited,Blocks.GLASS.getDefaultState(),3);world.setBlockState(tile,Blocks.CHEST.getDefaultState(),3);
        TileEntityChest chest=(TileEntityChest)world.getTileEntity(tile);chest.setCustomName("Preserve me");chest.setInventorySlotContents(0,new ItemStack(Items.DIAMOND));
        advance(200);loaded.expire(world,world.getTotalWorldTime());data.expire(world,world.getTotalWorldTime());
        check(data.count()==0&&loaded.count()==0&&world.isAirBlock(cover),"Expired/reloaded cover did not restore");
        check(world.getBlockState(edited).getBlock()==Blocks.GLASS&&world.getTileEntity(tile)==chest&&chest.getStackInSlot(0).getItem()==Items.DIAMOND&&"Preserve me".equals(chest.getName()),"Expiry overwrote player edit/tile data");
        world.setBlockToAir(tile);world.setBlockToAir(edited);
        NBTTagCompound future=new NBTTagCompound();future.setInteger("schema",99);future.setString("opaque","keep");loaded.readFromNBT(future);check(loaded.writeToNBT(new NBTTagCompound()).equals(future),"Unknown schema was rewritten");
        ready("flint");world.setBlockState(EarthCover.center(p),Blocks.CHEST.getDefaultState(),3);
        check(!Arts.cast(p)&&PlayerState.resolve(p)==100&&world.getTileEntity(EarthCover.center(p))!=null,"Earth overwrote an existing tile entity");world.setBlockToAir(EarthCover.center(p));
        ready("flint");unloadedPosition();
        check(world.getChunkProvider().getLoadedChunk(3125,3125)==null,"Unloaded geometry fixture already loaded");
        check(!Arts.cast(p)&&PlayerState.resolve(p)==100&&world.getChunkProvider().getLoadedChunk(3125,3125)==null,"Earth forced an unloaded chunk");place();
        ready("storm");unloadedPosition();
        check(!Arts.cast(p)&&PlayerState.resolve(p)==100&&world.getChunkProvider().getLoadedChunk(3125,3125)==null,"Lightning target lookup forced an unloaded chunk");place();
    }
    /** Entity.setPositionAndRotation queries collision and itself loads the test chunk. */
    private void unloadedPosition(){p.posX=50000.5;p.posY=Y;p.posZ=50000.5;p.rotationYaw=p.rotationPitch=p.rotationYawHead=0;p.setEntityBoundingBox(new net.minecraft.util.math.AxisAlignedBB(p.posX-p.width/2,Y,p.posZ-p.width/2,p.posX+p.width/2,Y+p.height,p.posZ+p.width/2));}
    private EntityTippedArrow arrow(Entity shooter,double x,double z){EntityTippedArrow arrow=new EntityTippedArrow(world);arrow.shootingEntity=shooter;arrow.setPosition(X+.5+x,Y+1.4,Z+.5+z);arrow.motionZ=-.6;check(world.spawnEntity(arrow),"Arrow spawn");actors.add(arrow);return arrow;}
    private void cleanup(){clearActors();advance(500);EarthCover.get(world).expire(world,world.getTotalWorldTime());for(Map.Entry<BlockPos,IBlockState> row:terrain.entrySet())world.setBlockState(row.getKey(),row.getValue(),2);p.removePotionEffect(MobEffects.SLOWNESS);}
    public static final class Target extends EntityZombie {
        boolean throwAfter;public Target(net.minecraft.world.World world){super(world);}
        @Override public boolean attackEntityFrom(DamageSource source,float amount){boolean accepted=super.attackEntityFrom(source,amount);if(accepted&&throwAfter)throw new IllegalStateException("Nature fixture post-damage callback");return accepted;}
    }
    public static final class FaultArrow extends EntityTippedArrow {
        private boolean fail=true;public FaultArrow(net.minecraft.world.World world){super(world);}
        @Override public void setDead(){super.setDead();if(fail){fail=false;throw new IllegalStateException("Nature fixture post-removal arrow callback");}}
    }
    public static final class Gate {
        EntityPlayerMP p;Target target;ScorePlayerTeam team;int stage,observed;boolean place,arrow,reenter,rejected,rollbackFailure,rollbackAttempted,strip,teamSwap,worldSwap;
        @SubscribeEvent(priority=EventPriority.LOWEST)public void attack(LivingAttackEvent event){if(event.getEntityLiving()!=target)return;if(reenter){reenter=false;observed=PlayerState.resolve(p);rejected=!Arts.cast(p);}if(stage==1)event.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void hurt(LivingHurtEvent event){if(stage==2&&event.getEntityLiving()==target)event.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void damage(LivingDamageEvent event){
            if(event.getEntityLiving()!=target)return;if(stage==3)event.setCanceled(true);
            if(strip)p.inventory.mainInventory.set(0,ItemStack.EMPTY);
            if(teamSwap)p.world.getScoreboard().addPlayerToTeam(target.getCachedUniqueIdString(),team.getName());
            if(worldSwap)target.world=p.getServer().getWorld(-1);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST)public void place(BlockEvent.PlaceEvent event){if(place&&event.getPlayer()==p)event.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void rollback(BlockEvent.NeighborNotifyEvent event){
            if(!rollbackFailure||event.getState().getBlock()!=Blocks.AIR||event.getPos().getY()!=Y||event.getPos().getZ()!=Z+2)return;
            rollbackFailure=false;rollbackAttempted=true;
            // A real native neighbor callback fails after retaining a just-placed cover state.
            event.getWorld().setBlockState(event.getPos(),CoverBlock.BLOCK.getDefaultState(),2);
            throw new IllegalStateException("Nature fixture interrupted native rollback");
        }
        @SubscribeEvent(priority=EventPriority.LOWEST)public void projectile(AttackEntityEvent event){if(arrow&&event.getEntityPlayer()==p&&event.getTarget() instanceof EntityTippedArrow)event.setCanceled(true);}
    }
}
