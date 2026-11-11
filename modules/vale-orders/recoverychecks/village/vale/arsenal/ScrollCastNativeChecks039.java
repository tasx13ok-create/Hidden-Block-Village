package village.vale.arsenal;

import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.potion.PotionEffect;
import net.minecraft.init.MobEffects;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.world.border.WorldBorder;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.event.world.GetCollisionBoxesEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import village.vale.ArsenalAccess;
import village.vale.Gear;
import village.vale.PlayerState;
import village.vale.RewardLedger;

/** Actual native item use and Forge callbacks, restricted to the disposable QA server. */
public final class ScrollCastNativeChecks039 {
    private static int assertions;
    private static void check(boolean condition,String why){assertions++;if(!condition)throw new AssertionError(why);}
    public static int run(EntityPlayerMP p){
        if(!Boolean.getBoolean("valeorders.recoverycheck")||!"vale-recovery-test".equals(p.getServer().getFolderName())
                ||!new File(".vale-recovery-test").isFile())throw new IllegalStateException("Scroll cast checks require the isolated recovery fixture");
        assertions=0;Rig rig=new Rig(p);MinecraftForge.EVENT_BUS.register(rig.gate);
        try{rig.setup();rig.buffAndHealing();rig.callbackIsolation();rig.serpentAndDisguise();rig.waymark();rig.carrier();return assertions;}
        finally{MinecraftForge.EVENT_BUS.unregister(rig.gate);rig.close();}
    }
    private static final class Rig {
        final EntityPlayerMP p;final WorldServer world;final Gate gate;
        final List<Entity> fixtures=new ArrayList<>();final List<Item> usedItems=new ArrayList<>();
        final NBTTagCompound savedAccount;final Vec3d savedPosition;final int savedDimension;
        final double borderX,borderZ,borderSize;final float savedHealth;int serial;
        final Vec3d origin=new Vec3d(70.5,65,32.5),mark=new Vec3d(66.5,65,32.5);
        EntityZombie enemy;Scrolls.Lesson lesson;ItemStack item;NBTTagCompound row;
        Rig(EntityPlayerMP p){
            this.p=p;world=p.getServerWorld();gate=new Gate(p,fixtures);savedAccount=PlayerState.data(p).copy();
            savedPosition=p.getPositionVector();savedDimension=p.dimension;savedHealth=p.getHealth();
            WorldBorder border=world.getWorldBorder();borderX=border.getCenterX();borderZ=border.getCenterZ();borderSize=border.getDiameter();
        }
        void setup(){
            for(int x=62;x<=83;x++)for(int z=30;z<=35;z++){
                BlockPos floor=new BlockPos(x,64,z);world.getChunk(floor);world.setBlockState(floor,Blocks.STONE.getDefaultState());
                for(int y=1;y<=3;y++)world.setBlockState(floor.up(y),Blocks.AIR.getDefaultState());
            }
            enemy=new EntityZombie(world);enemy.setNoAI(true);enemy.setPosition(75.5,65,32.5);
            check(world.spawnEntity(enemy),"Could not spawn the native scroll target");fixtures.add(enemy);
        }
        void clearInventory(){p.inventory.setItemStack(ItemStack.EMPTY);for(int i=0;i<p.inventory.getSizeInventory();i++)p.inventory.setInventorySlotContents(i,ItemStack.EMPTY);p.inventory.currentItem=0;}
        ItemStack issue(String key){
            RewardLedger.get(p).issue(p,key,1,"native039-cast/"+p.getUniqueID()+"/"+(serial++));
            for(int i=0;i<p.inventory.getSizeInventory();i++){
                ItemStack issued=p.inventory.getStackInSlot(i);
                if(issued.getItem()==Gear.ITEMS.get(key)){p.inventory.setInventorySlotContents(i,ItemStack.EMPTY);usedItems.add(issued.getItem());return issued;}
            }
            throw new AssertionError("Native cast fixture could not obtain "+key);
        }
        void reset(String key){
            gate.reset();clearInventory();p.clearActivePotions();p.dimension=savedDimension;p.setPosition(origin.x,origin.y,origin.z);
            p.rotationYaw=-90;p.rotationPitch=0;p.setSneaking(false);p.onGround=true;p.setHealth(p.getMaxHealth());
            world.setBlockState(new BlockPos(origin),Blocks.AIR.getDefaultState());world.setBlockState(new BlockPos(origin).up(),Blocks.AIR.getDefaultState());p.handleWaterMovement();
            PlayerState.data(p).setInteger("xp",1000000);PlayerState.setResolve(p,100);PlayerState.data(p).setTag("OriginalScrolls",new NBTTagCompound());
            lesson=AbilityCarriers036.lesson(key);check(lesson!=null,"Unknown native fixture lesson");
            row=Scrolls.row(p,key);row.setBoolean("learned",true);row.setInteger("practice",lesson.practice);
            if(!lesson.prerequisite.isEmpty())Scrolls.row(p,lesson.prerequisite).setBoolean("learned",true);
            item=issue("technique_"+key);p.setHeldItem(EnumHand.MAIN_HAND,item);p.getCooldownTracker().setCooldown(item.getItem(),0);
            for(Entity entity:new ArrayList<Entity>(fixtures))if(entity instanceof ScrollActor){entity.setDead();world.removeEntity(entity);fixtures.remove(entity);}
        }
        ActionResult<ItemStack> use(){return item.getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND);}
        void unchanged(String reason){check(PlayerState.resolve(p)==100&&row.getInteger("practice")==lesson.practice&&row.getLong("next")==0&&!p.getCooldownTracker().hasCooldown(item.getItem()),reason);}
        void marker(){NBTTagCompound state=Scrolls.state(p);state.setInteger("markDim",p.dimension);state.setDouble("markX",mark.x);state.setDouble("markY",mark.y);state.setDouble("markZ",mark.z);}
        void samePosition(String reason){check(p.getPositionVector().squareDistanceTo(origin)<1.0e-8,reason);}
        void buffAndHealing(){
            reset("iron_focus");gate.denyPotion=true;
            check(use().getType()==EnumActionResult.FAIL&&!p.isPotionActive(MobEffects.STRENGTH),"Denied native buff was reported as applied");
            check(gate.potions==1&&gate.resolveAtPotion==84,"Buff callbacks ran before their full resolve reservation");
            unchanged("Denied buff charged resolve, mastery or cooldown");gate.reset();
            check(use().getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==84&&p.isPotionActive(MobEffects.STRENGTH),"A failed buff left a stuck reservation or native cast guard");
            check(row.getInteger("practice")==lesson.practice+1&&row.getLong("next")==ArsenalAccess.now(p)+lesson.cooldown,"Successful buff did not commit exactly one saved deadline/mastery step");
            check(use().getType()==EnumActionResult.FAIL&&PlayerState.resolve(p)==84,"Successful buff's persisted cooldown was bypassed");

            reset("iron_focus");gate.denyPotion=true;
            gate.potionAction=()->{gate.denyPotion=false;p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH,25,0));gate.denyPotion=true;};
            check(use().getType()==EnumActionResult.FAIL&&p.isPotionActive(MobEffects.STRENGTH),"An unrelated callback buff concealed denial of the requested application");
            unchanged("An unrelated callback buff consumed the denied cast's reservation");

            reset("calm_mending");p.setHealth(10);gate.cancelHeal=true;
            check(use().getType()==EnumActionResult.FAIL&&p.getHealth()==10&&gate.heals==1&&gate.resolveAtHeal==90,"Canceled native healing was charged or not reserved before callbacks");
            unchanged("Canceled heal advanced mastery or cooldown");gate.reset();gate.zeroHeal=true;
            check(use().getType()==EnumActionResult.FAIL&&p.getHealth()==10,"Zero-effect native healing was reported as successful");unchanged("Zero-effect healing consumed resolve");gate.reset();
            check(use().getType()==EnumActionResult.SUCCESS&&p.getHealth()==16&&PlayerState.resolve(p)==90,"Successful native healing did not charge exactly once");

            reset("river_serpent");p.setSneaking(true);
            check(use().getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==98&&gate.spawns==0,"Dry-land practice spawned an effect or paid the cast surcharge");
            check(row.getInteger("practice")==lesson.practice+1&&row.getLong("next")==ArsenalAccess.now(p)+30,"Practice committed the wrong progress/deadline");

            reset("iron_focus");ItemStack unissued=new ItemStack(item.getItem());p.setHeldItem(EnumHand.MAIN_HAND,unissued);
            check(item.getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL&&gate.potions==0,"An unissued held technique reached a native effect");
            unchanged("Unissued native use spent resources");p.setHeldItem(EnumHand.MAIN_HAND,item);
            check(Gear.ITEMS.get("technique_vital_cycle").onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL&&gate.potions==0,"A different item handler forged the held lesson");
        }
        void callbackIsolation(){
            reset("iron_focus");final Item original=item.getItem();final int[] recursive={0};
            final ItemStack other=issue("technique_reed_escape");p.setHeldItem(EnumHand.OFF_HAND,other);p.getCooldownTracker().setCooldown(other.getItem(),0);
            NBTTagCompound otherRow=Scrolls.row(p,"reed_escape");otherRow.setBoolean("learned",true);otherRow.setInteger("practice",2);
            gate.potionAction=()->{
                if(use().getType()==EnumActionResult.FAIL)recursive[0]++;
                if(other.getItem().onItemRightClick(world,p,EnumHand.OFF_HAND).getType()==EnumActionResult.FAIL)recursive[0]++;
            };
            check(use().getType()==EnumActionResult.SUCCESS&&recursive[0]==2&&gate.potions==1&&PlayerState.resolve(p)==84,"Native callback reentered the same or another owned lesson");
            check(!Scrolls.state(p).getBoolean("replacement")&&otherRow.getLong("next")==0&&otherRow.getInteger("practice")==2,"Rejected cross-lesson reentry applied or committed an effect");

            failureStatusGuard();

            reset("iron_focus");gate.denyPotion=true;gate.potionAction=()->PlayerState.setResolve(p,PlayerState.resolve(p)-7);
            check(use().getType()==EnumActionResult.FAIL&&PlayerState.resolve(p)==93,"Refund overwrote an independent callback resolve debit");

            reset("iron_focus");final NBTTagCompound charged=PlayerState.data(p);gate.denyPotion=true;
            gate.potionAction=()->{NBTTagCompound replacement=PlayerState.data(p).copy();replacement.setInteger("resolve",23);replaceAccount(replacement);check(use().getType()==EnumActionResult.FAIL,"Replacing player NBT bypassed the runtime owner guard");};
            check(use().getType()==EnumActionResult.FAIL&&PlayerState.resolve(p)==23&&charged.getInteger("resolve")==100,"Failed effect refunded into a replacement account or failed to close its original reservation");

            reset("iron_focus");final long independentDeadline=ArsenalAccess.now(p)+9000;final NBTTagCompound oldRow=row;
            gate.potionAction=()->{NBTTagCompound replacement=new NBTTagCompound();replacement.setBoolean("learned",true);replacement.setInteger("practice",41);replacement.setLong("next",independentDeadline);Scrolls.state(p).setTag(lesson.key,replacement);};
            check(use().getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==84,"Applied effect lost its reserved charge when lesson state changed");
            check(Scrolls.row(p,lesson.key).getInteger("practice")==42&&Scrolls.row(p,lesson.key).getLong("next")==independentDeadline&&oldRow.getInteger("practice")==lesson.practice,"Commit restored stale lesson NBT or shortened another deadline");

            reset("iron_focus");final ItemStack held=item,swap=new ItemStack(Items.STICK);final Item heldItem=item.getItem();
            gate.potionAction=()->{p.inventory.setInventorySlotContents(1,held);p.setHeldItem(EnumHand.MAIN_HAND,swap);};
            ActionResult<ItemStack> swapped=use();
            check(swapped.getType()==EnumActionResult.SUCCESS&&swapped.getResult()==swap&&p.getHeldItemMainhand()==swap&&p.inventory.getStackInSlot(1)==held,"Native return value resurrected or duplicated the original held technique");
            check(PlayerState.resolve(p)==84&&p.getCooldownTracker().hasCooldown(heldItem)&&!p.getCooldownTracker().hasCooldown(Items.STICK),"Applied callback-swapped cast charged or cooled down the replacement item");

            reset("iron_focus");final ItemStack emptied=item;final Item beforeEmpty=item.getItem();gate.potionAction=()->emptied.setCount(0);
            check(use().getType()==EnumActionResult.SUCCESS&&p.getHeldItemMainhand().isEmpty()&&p.getCooldownTracker().hasCooldown(beforeEmpty)&&!p.getCooldownTracker().hasCooldown(Items.AIR),"Emptying a stack lost the original Item cooldown");

            reset("iron_focus");final UUID originalOwner=p.getUniqueID();final int[] ownerReentry={0};
            gate.potionAction=()->{
                p.setUniqueId(UUID.randomUUID());
                ItemStack newlyOwned=issue("technique_reed_escape");p.setHeldItem(EnumHand.OFF_HAND,newlyOwned);
                NBTTagCompound learned=Scrolls.row(p,"reed_escape");learned.setBoolean("learned",true);learned.setInteger("practice",2);
                if(newlyOwned.getItem().onItemRightClick(world,p,EnumHand.OFF_HAND).getType()==EnumActionResult.FAIL)ownerReentry[0]++;
            };
            try{check(use().getType()==EnumActionResult.SUCCESS&&ownerReentry[0]==1&&PlayerState.resolve(p)==84&&!Scrolls.state(p).getBoolean("replacement"),"Changing UUID admitted a nested cast by the same live player instance");}
            finally{p.setUniqueId(originalOwner);}
            gate.reset();p.clearActivePotions();row.setLong("next",0);p.getCooldownTracker().setCooldown(item.getItem(),0);PlayerState.setResolve(p,100);
            check(use().getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==84,"UUID mutation leaked the original owner's cast guard");

            reset("iron_focus");gate.potionAction=()->{throw new ExpectedCallbackFailure();};boolean caught=false;
            try{use();}catch(RuntimeException expected){for(Throwable cause=expected;cause!=null;cause=cause.getCause())if(cause instanceof ExpectedCallbackFailure)caught=true;}
            check(caught,"Native callback exception was not exercised");unchanged("A throwing pre-effect callback leaked its reservation");gate.reset();
            check(use().getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==84,"A throwing callback retained the owner cast guard");
        }
        void failureStatusGuard(){
            // The main recovery ProbePlayer collects status text instead of
            // invoking native sendStatusMessage. Use a normal server player here
            // so the failure really traverses the native status packet path.
            EntityPlayerMP nativePlayer=new EntityPlayerMP(p.getServer(),world,new GameProfile(UUID.randomUUID(),"ScrollCast039"),new PlayerInteractionManager(world));
            new QuietConnection(nativePlayer);Rig nativeRig=new Rig(nativePlayer);MinecraftForge.EVENT_BUS.register(nativeRig.gate);
            try{
                nativeRig.reset("iron_focus");nativeRig.gate.denyPotion=true;final int[] reentry={0};ConnectionProbe connection=new ConnectionProbe(nativePlayer);
                try{
                    connection.statusAction=()->{if(nativeRig.use().getType()==EnumActionResult.FAIL)reentry[0]++;};
                    check(nativeRig.use().getType()==EnumActionResult.FAIL&&reentry[0]==1&&nativeRig.gate.potions==1,"Failure status callback reentered a reserved cast");
                    nativeRig.unchanged("Failure-status reentry leaked the reservation");
                }finally{connection.close();}
            }finally{MinecraftForge.EVENT_BUS.unregister(nativeRig.gate);nativeRig.close();}
        }
        void serpentAndDisguise(){
            reset("river_serpent");PlayerState.setResolve(p,47);
            check(use().getType()==EnumActionResult.FAIL&&PlayerState.resolve(p)==47&&gate.spawns==0,"Dry River Serpent spawned before the full 48-resolve affordability check");
            PlayerState.setResolve(p,48);gate.cancelSpawn=true;
            check(use().getType()==EnumActionResult.FAIL&&PlayerState.resolve(p)==48&&gate.spawns==1&&gate.resolveAtSpawn==0&&!ScrollActor.exists(p,"dragon"),"Canceled dry River Serpent did not reserve/refund its entire cost");
            check(row.getLong("next")==0&&row.getInteger("practice")==lesson.practice,"Canceled serpent spawn committed progression");gate.reset();
            check(use().getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==0&&gate.resolveAtSpawn==0&&ScrollActor.exists(p,"dragon"),"Exact-cost dry River Serpent was double-charged or failed after reservation");

            reset("river_serpent");world.setBlockState(new BlockPos(origin),Blocks.WATER.getDefaultState());world.setBlockState(new BlockPos(origin).up(),Blocks.WATER.getDefaultState());p.handleWaterMovement();p.onGround=true;
            check(p.isInWater(),"Wet-serpent fixture did not enter native water state");
            check(use().getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==84&&gate.resolveAtSpawn==84,"Wet River Serpent paid the dry surcharge");

            reset("river_serpent");gate.cancelSpawn=true;gate.spawnAction=()->PlayerState.setResolve(p,PlayerState.resolve(p)-7);
            check(use().getType()==EnumActionResult.FAIL&&PlayerState.resolve(p)==93,"Failed full-cost spawn overwrote independent callback resource changes");

            reset("borrowed_silhouette");Scrolls.state(p).setString("disguiseName","prior disguise");gate.cancelSpawn=true;
            check(use().getType()==EnumActionResult.FAIL&&Scrolls.state(p).getString("disguiseName").equals("prior disguise"),"Canceled disguise spawn changed saved disguise identity");unchanged("Canceled disguise spent resolve or progress");
        }
        void waymark(){
            reset("waymark_step");
            check(use().getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==68&&Scrolls.state(p).hasKey("markDim")&&gate.teleports==0,"Native Waymark did not mark safe ground exactly once");
            reset("waymark_step");p.onGround=false;
            check(use().getType()==EnumActionResult.FAIL&&!Scrolls.state(p).hasKey("markDim"),"Airborne Waymark created a return marker");unchanged("Invalid initial marker spent resolve");

            reset("waymark_step");marker();gate.cancelTeleport=true;
            check(use().getType()==EnumActionResult.FAIL&&gate.teleports==1&&gate.resolveAtTeleport==68&&Scrolls.state(p).hasKey("markDim"),"Waymark bypassed EnderTeleportEvent cancellation or consumed a canceled marker");samePosition("Canceled Waymark moved the player");unchanged("Canceled Waymark charged progression or resolve");

            for(Vec3d invalid:new Vec3d[]{new Vec3d(Double.NaN,65,32.5),new Vec3d(1000.5,65,32.5),new Vec3d(67.5,0,32.5)}){
                reset("waymark_step");marker();gate.destination=invalid;
                check(use().getType()==EnumActionResult.FAIL&&Scrolls.state(p).hasKey("markDim"),"Waymark accepted a nonfinite, out-of-range or out-of-height retarget");samePosition("Unsafe retarget moved the player");unchanged("Unsafe retarget consumed the reservation");
            }
            reset("waymark_step");marker();BlockPos blocked=new BlockPos(68,65,32);world.setBlockState(blocked,Blocks.STONE.getDefaultState());gate.destination=new Vec3d(68.5,65,32.5);
            check(use().getType()==EnumActionResult.FAIL,"Waymark accepted a solid retarget");unchanged("Blocked retarget consumed resolve");world.setBlockState(blocked,Blocks.AIR.getDefaultState());
            reset("waymark_step");marker();world.setBlockState(blocked,Blocks.WATER.getDefaultState());gate.destination=new Vec3d(68.5,65,32.5);
            check(use().getType()==EnumActionResult.FAIL,"Waymark accepted a liquid retarget");unchanged("Liquid retarget consumed resolve");world.setBlockState(blocked,Blocks.AIR.getDefaultState());

            reset("waymark_step");marker();final ItemStack saved=item,swapped=new ItemStack(Items.STICK);
            gate.teleportAction=()->{p.inventory.setInventorySlotContents(1,saved);p.setHeldItem(EnumHand.MAIN_HAND,swapped);};
            ActionResult<ItemStack> rejected=use();
            check(rejected.getType()==EnumActionResult.FAIL&&rejected.getResult()==swapped&&p.getHeldItemMainhand()==swapped&&p.inventory.getStackInSlot(1)==saved,"Rejected teleport resurrected or lost a callback-swapped held item");
            samePosition("Waymark moved after losing its original held proof");unchanged("Lost held proof spent resolve or a deadline");check(Scrolls.state(p).hasKey("markDim"),"Lost held proof consumed the marker");

            reset("waymark_step");marker();gate.teleportAction=()->item.getTagCompound().setString("valeSerial","not-issued");
            check(use().getType()==EnumActionResult.FAIL&&Scrolls.state(p).hasKey("markDim"),"Waymark accepted changed issued-owner proof after its protection callback");samePosition("Changed ownership proof still teleported");unchanged("Invalidated ownership proof spent resolve");

            reset("waymark_step");marker();gate.teleportAction=()->Scrolls.state(p).setDouble("markX",80.5);
            check(use().getType()==EnumActionResult.FAIL&&Scrolls.state(p).getDouble("markX")==80.5,"Waymark consumed or overwrote a callback replacement marker");samePosition("Waymark ignored changed marker authority");unchanged("Changed marker spent resolve");

            reset("waymark_step");marker();gate.teleportAction=()->{PlayerState.setResolve(p,PlayerState.resolve(p)-7);p.setPosition(71.5,65,32.5);};
            check(use().getType()==EnumActionResult.FAIL&&p.posX==71.5&&PlayerState.resolve(p)==93&&Scrolls.state(p).hasKey("markDim"),"Rejected return overwrote a callback's movement or resource change");

            reset("waymark_step");marker();final NBTTagCompound charged=PlayerState.data(p);
            gate.teleportAction=()->{NBTTagCompound replacement=PlayerState.data(p).copy();replacement.setInteger("resolve",19);replaceAccount(replacement);};
            check(use().getType()==EnumActionResult.FAIL&&PlayerState.resolve(p)==19&&charged.getInteger("resolve")==100&&Scrolls.state(p).hasKey("markDim"),"Waymark refunded a replaced account or accepted displaced lesson state");samePosition("Replaced player state still teleported");

            reset("waymark_step");marker();final NBTTagCompound replacementRow=new NBTTagCompound();replacementRow.setBoolean("learned",true);replacementRow.setInteger("practice",57);replacementRow.setLong("next",ArsenalAccess.now(p)+2000);
            gate.teleportAction=()->Scrolls.state(p).setTag(lesson.key,replacementRow);
            check(use().getType()==EnumActionResult.FAIL&&Scrolls.row(p,lesson.key)==replacementRow&&replacementRow.getInteger("practice")==57&&PlayerState.resolve(p)==100,"Rejected Waymark overwrote a replacement lesson row");samePosition("Replaced lesson authority still teleported");

            reset("waymark_step");p.setPosition(1000000.5,65,1000000.5);NBTTagCompound remote=Scrolls.state(p);remote.setInteger("markDim",p.dimension);remote.setDouble("markX",1000001.5);remote.setDouble("markY",65);remote.setDouble("markZ",1000000.5);BlockPos absent=new BlockPos(1000001,65,1000000);
            check(!world.isBlockLoaded(absent),"Unloaded-destination fixture was already loaded");
            check(use().getType()==EnumActionResult.FAIL&&!world.isBlockLoaded(absent)&&gate.teleports==0,"Waymark loaded or accepted an unloaded return chunk");unchanged("Unloaded return spent resolve");

            reset("waymark_step");marker();gate.destination=new Vec3d(68.5,65,32.5);gate.teleportAction=()->check(use().getType()==EnumActionResult.FAIL,"Teleport callback recursively returned through the same marker");p.fallDistance=12;
            check(use().getType()==EnumActionResult.SUCCESS&&gate.teleports==1&&p.posX==68.5&&p.fallDistance==0&&PlayerState.resolve(p)==68,"Permitted retarget did not move/reset fall distance/charge exactly once");
            check(!Scrolls.state(p).hasKey("markDim")&&!Scrolls.state(p).hasKey("markX")&&row.getInteger("practice")==lesson.practice+1,"Successful return failed to consume its marker or commit mastery");

            reset("waymark_step");marker();ConnectionProbe rejectedConnection=new ConnectionProbe(p);
            try{
                rejectedConnection.rejectLocation=true;
                check(use().getType()==EnumActionResult.FAIL&&Scrolls.state(p).hasKey("markDim"),"Connection-rejected movement consumed a return marker");
                samePosition("Rejected connection location still moved");unchanged("No-move connection rejection was charged");
            }finally{rejectedConnection.close();}

            reset("waymark_step");marker();ConnectionProbe redirectedConnection=new ConnectionProbe(p);
            try{
                redirectedConnection.locationAction=()->{check(use().getType()==EnumActionResult.FAIL,"Connection callback reentered an in-flight return");p.setPosition(69.5,65,32.5);};
                check(use().getType()==EnumActionResult.SUCCESS&&p.posX==69.5&&PlayerState.resolve(p)==68&&!Scrolls.state(p).hasKey("markDim"),"Observed connection redirection was refunded or overwritten");
            }finally{redirectedConnection.close();}

            reset("waymark_step");marker();ConnectionProbe noMoveThrow=new ConnectionProbe(p);final ExpectedCallbackFailure beforeMove=new ExpectedCallbackFailure();
            try{
                noMoveThrow.rejectLocation=true;noMoveThrow.locationAction=()->{PlayerState.setResolve(p,PlayerState.resolve(p)-7);throw beforeMove;};
                RuntimeException observed=null;try{use();}catch(RuntimeException error){observed=error;}
                check(observed==beforeMove,"No-move callback exception was replaced");
                check(p.getPositionVector().squareDistanceTo(origin)<1.0e-8&&Scrolls.state(p).hasKey("markDim")&&PlayerState.resolve(p)==93&&row.getInteger("practice")==lesson.practice&&row.getLong("next")==0&&!p.getCooldownTracker().hasCooldown(item.getItem()),"No-move throwing callback consumed its marker, charge or deadline");
            }finally{noMoveThrow.close();}
            check(use().getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==61,"No-move exception left the owner cast guard stuck");

            reset("waymark_step");marker();ConnectionProbe movedThrow=new ConnectionProbe(p);final ExpectedCallbackFailure afterMove=new ExpectedCallbackFailure();
            try{
                movedThrow.locationAction=()->{PlayerState.setResolve(p,PlayerState.resolve(p)-7);Scrolls.state(p).setString("callback","kept");throw afterMove;};
                RuntimeException observed=null;try{use();}catch(RuntimeException error){observed=error;}
                check(observed==afterMove,"Post-move callback exception was replaced");
                check(p.posX==mark.x&&PlayerState.resolve(p)==61&&!Scrolls.state(p).hasKey("markDim")&&row.getInteger("practice")==lesson.practice+1&&row.getLong("next")==ArsenalAccess.now(p)+lesson.cooldown&&p.getCooldownTracker().hasCooldown(item.getItem())&&Scrolls.state(p).getString("callback").equals("kept"),"Post-move exception refunded an applied return, retained its marker, or skipped its completion");
            }finally{movedThrow.close();}
            row.setLong("next",0);p.getCooldownTracker().setCooldown(item.getItem(),0);PlayerState.setResolve(p,100);
            check(use().getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==68&&Scrolls.state(p).hasKey("markDim"),"Post-move exception retained the owner cast guard");

            reset("waymark_step");marker();gate.collisionAction=()->{p.setPosition(71.5,65,32.5);Scrolls.state(p).setDouble("markX",80.5);};
            check(use().getType()==EnumActionResult.FAIL&&gate.collisionCallbacks==1&&p.posX==71.5&&Scrolls.state(p).getDouble("markX")==80.5,"Final collision callback movement or marker replacement was overwritten");
            unchanged("Final collision callback authority loss spent resolve or progression");

            reset("waymark_step");marker();WorldBorder border=world.getWorldBorder();border.setCenter(70.5,32.5);border.setTransition(12);gate.destination=new Vec3d(76.4,65,32.5);
            check(use().getType()==EnumActionResult.FAIL&&gate.teleports==1,"Waymark accepted a body extending across the world border");unchanged("World-border retarget spent resolve");border.setCenter(borderX,borderZ);border.setTransition(borderSize);
        }
        void carrier(){
            reset("iron_focus");final ItemStack book=issue("stillwater_codex");p.setHeldItem(EnumHand.MAIN_HAND,book);p.getCooldownTracker().setCooldown(book.getItem(),0);
            final NBTTagCompound carrier=AbilityCarriers036.canonical(p,book);check(carrier!=null,"Native codex did not bind");carrier.getCompoundTag("mastery").setInteger("iron_focus",60);
            final ItemStack replacement=new ItemStack(Items.STICK);gate.potionAction=()->{p.inventory.setInventorySlotContents(1,book);p.setHeldItem(EnumHand.MAIN_HAND,replacement);};
            ActionResult<ItemStack> result=book.getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND);
            check(result.getType()==EnumActionResult.SUCCESS&&result.getResult()==replacement&&p.getHeldItemMainhand()==replacement&&p.inventory.getStackInSlot(1)==book,"Carrier native return resurrected a callback-replaced book");
            check(PlayerState.resolve(p)==84&&carrier.getCompoundTag("cooldowns").getLong("iron_focus")>=ArsenalAccess.now(p)+lesson.cooldown,"Carrier callback lost its full cost or saved per-codex deadline");
            check(!replacement.hasTagCompound()&&!p.getCooldownTracker().hasCooldown(book.getItem()),"Carrier commit mirrored into the replacement item or blocked codex navigation");
            p.setHeldItem(EnumHand.MAIN_HAND,book);p.inventory.setInventorySlotContents(1,ItemStack.EMPTY);
            check(book.getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.FAIL&&PlayerState.resolve(p)==84,"Replacing a carrier hand bypassed its committed lesson deadline");
        }
        void replaceAccount(NBTTagCompound account){p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).setTag("ValeOrders",account);}
        void close(){
            for(Entity entity:fixtures){entity.setDead();world.removeEntity(entity);}
            gate.reset();clearInventory();p.clearActivePotions();for(Item used:usedItems)p.getCooldownTracker().setCooldown(used,0);
            replaceAccount(savedAccount);p.dimension=savedDimension;p.setSneaking(false);p.setPosition(savedPosition.x,savedPosition.y,savedPosition.z);p.setHealth(Math.min(savedHealth,p.getMaxHealth()));
            WorldBorder border=world.getWorldBorder();border.setCenter(borderX,borderZ);border.setTransition(borderSize);
        }
    }
    private static final class QuietConnection extends NetHandlerPlayServer {
        QuietConnection(EntityPlayerMP player){super(player.getServer(),network(),player);}
        private static NetworkManager network(){NetworkManager manager=new NetworkManager(EnumPacketDirection.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(manager);return manager;}
        @Override public void sendPacket(Packet<?> packet){}
    }
    /** Delegates to the fixture's real native connection and injects bounded callbacks. */
    private static final class ConnectionProbe extends NetHandlerPlayServer {
        final EntityPlayerMP player;final NetHandlerPlayServer previous;boolean rejectLocation;
        Runnable locationAction,statusAction;
        ConnectionProbe(EntityPlayerMP p){this(p,p.connection);}
        private ConnectionProbe(EntityPlayerMP p,NetHandlerPlayServer previous){super(p.getServer(),previous.netManager,p);player=p;this.previous=previous;}
        @Override public void setPlayerLocation(double x,double y,double z,float yaw,float pitch){
            if(!rejectLocation)previous.setPlayerLocation(x,y,z,yaw,pitch);
            Runnable action=locationAction;locationAction=null;if(action!=null)action.run();
        }
        @Override public void sendPacket(Packet<?> packet){
            if(packet instanceof SPacketChat){Runnable action=statusAction;statusAction=null;if(action!=null)action.run();}
            previous.sendPacket(packet);
        }
        void close(){player.connection=previous;previous.netManager.setNetHandler(previous);}
    }
    public static final class ExpectedCallbackFailure extends RuntimeException {private static final long serialVersionUID=1L;}
    public static final class Gate {
        final EntityPlayerMP p;final List<Entity> fixtures;
        boolean denyPotion,cancelHeal,zeroHeal,cancelSpawn,cancelTeleport;
        int potions,heals,spawns,teleports,collisionCallbacks,resolveAtPotion,resolveAtHeal,resolveAtSpawn,resolveAtTeleport;
        Runnable potionAction,healAction,spawnAction,teleportAction,collisionAction;Vec3d destination;
        Gate(EntityPlayerMP p,List<Entity> fixtures){this.p=p;this.fixtures=fixtures;}
        void reset(){denyPotion=cancelHeal=zeroHeal=cancelSpawn=cancelTeleport=false;potions=heals=spawns=teleports=collisionCallbacks=0;potionAction=healAction=spawnAction=teleportAction=collisionAction=null;destination=null;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void potion(PotionEvent.PotionApplicableEvent event){
            if(event.getEntityLiving()!=p)return;potions++;resolveAtPotion=PlayerState.resolve(p);
            Runnable action=potionAction;potionAction=null;if(action!=null)action.run();if(denyPotion)event.setResult(Event.Result.DENY);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void heal(LivingHealEvent event){
            if(event.getEntityLiving()!=p)return;heals++;resolveAtHeal=PlayerState.resolve(p);
            Runnable action=healAction;healAction=null;if(action!=null)action.run();if(zeroHeal)event.setAmount(0);if(cancelHeal)event.setCanceled(true);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void spawn(EntityJoinWorldEvent event){
            if(!(event.getEntity() instanceof ScrollActor)||!((ScrollActor)event.getEntity()).owned(p))return;
            spawns++;resolveAtSpawn=PlayerState.resolve(p);fixtures.add(event.getEntity());Runnable action=spawnAction;spawnAction=null;if(action!=null)action.run();if(cancelSpawn)event.setCanceled(true);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void collision(GetCollisionBoxesEvent event){
            if(event.getEntity()!=p||teleports==0||collisionAction==null)return;
            collisionCallbacks++;Runnable action=collisionAction;collisionAction=null;action.run();
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void teleport(EnderTeleportEvent event){
            if(event.getEntityLiving()!=p)return;teleports++;resolveAtTeleport=PlayerState.resolve(p);
            Runnable action=teleportAction;teleportAction=null;if(action!=null)action.run();if(destination!=null){event.setTargetX(destination.x);event.setTargetY(destination.y);event.setTargetZ(destination.z);}if(cancelTeleport)event.setCanceled(true);
        }
    }
}
