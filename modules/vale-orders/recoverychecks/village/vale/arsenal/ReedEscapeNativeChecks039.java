package village.vale.arsenal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import village.vale.ArsenalAccess;
import village.vale.Gear;
import village.vale.PlayerState;
import village.vale.RewardLedger;

/** Actual damage, protection events and connection outcomes in a disposable world only. */
public final class ReedEscapeNativeChecks039 {
    private static final DamageSource SOURCE=new DamageSource("vale_reed039");
    private static int assertions;
    private static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    public static int run(EntityPlayerMP p){
        if(!Boolean.getBoolean("valeorders.recoverycheck")||!"vale-recovery-test".equals(p.getServer().getFolderName())
                ||!new File(".vale-recovery-test").isFile())throw new IllegalStateException("Reed Escape checks require the isolated recovery fixture");
        assertions=0;Rig rig=new Rig(p);MinecraftForge.EVENT_BUS.register(rig.gate);
        try{rig.setup();rig.normalAndCanceled();rig.retarget();rig.changedAuthority();rig.connections();return assertions;}
        finally{MinecraftForge.EVENT_BUS.unregister(rig.gate);rig.close();}
    }
    private static final class Rig {
        final EntityPlayerMP p;final WorldServer world;final Gate gate;final List<ScrollActor> fixtures=new ArrayList<>();
        final NBTTagCompound savedAccount;final Vec3d savedPosition;final int savedDimension,savedRespawn;final float savedHealth;
        final Vec3d origin=new Vec3d(110.5,65,32.5);NBTTagCompound state;long until;
        Rig(EntityPlayerMP p){this.p=p;world=p.getServerWorld();gate=new Gate(p,fixtures);savedAccount=PlayerState.data(p).copy();savedPosition=p.getPositionVector();savedDimension=p.dimension;savedHealth=p.getHealth();savedRespawn=(Integer)ReflectionHelper.getPrivateValue(EntityPlayerMP.class,p,"respawnInvulnerabilityTicks","field_147101_bU");}
        void setup(){for(int x=103;x<=116;x++)for(int z=29;z<=37;z++){
            BlockPos floor=new BlockPos(x,64,z);world.getChunk(floor);world.setBlockState(floor,Blocks.STONE.getDefaultState());
            for(int y=1;y<=3;y++)world.setBlockState(floor.up(y),Blocks.AIR.getDefaultState());
        }}
        void clear(){p.inventory.setItemStack(ItemStack.EMPTY);for(int i=0;i<p.inventory.getSizeInventory();i++)p.inventory.setInventorySlotContents(i,ItemStack.EMPTY);p.inventory.currentItem=0;}
        void reset(){
            gate.reset();for(ScrollActor actor:fixtures){actor.setDead();actor.world.removeEntity(actor);}fixtures.clear();clear();
            // Exercise actual incoming damage immediately without waiting for the
            // newly logged-in disposable player's native respawn grace period.
            ReflectionHelper.setPrivateValue(EntityPlayerMP.class,p,0,"respawnInvulnerabilityTicks","field_147101_bU");
            p.world=world;p.dimension=savedDimension;p.clearActivePotions();p.setAbsorptionAmount(0);p.setHealth(p.getMaxHealth());
            p.setPosition(origin.x,origin.y,origin.z);p.rotationYaw=-90;p.rotationPitch=0;p.onGround=true;p.setSneaking(false);p.fallDistance=0;p.hurtResistantTime=0;p.hurtTime=0;
            PlayerState.data(p).setInteger("xp",1000000);PlayerState.setResolve(p,100);PlayerState.data(p).setTag("OriginalScrolls",new NBTTagCompound());
            state=Scrolls.state(p);until=ArsenalAccess.now(p)+240;state.setBoolean("replacement",true);state.setLong("replacementUntil",until);
        }
        LivingHurtEvent hurt(){
            gate.outer=null;gate.active=true;p.hurtResistantTime=0;p.hurtTime=0;
            try{p.attackEntityFrom(SOURCE,6);}finally{gate.active=false;}
            check(gate.outer!=null,"Actual incoming damage did not reach LivingHurtEvent");return gate.outer;
        }
        void preserved(String why){check(state.getBoolean("replacement")&&state.getLong("replacementUntil")==until&&PlayerState.resolve(p)==100,why);}
        void noMove(String why){check(p.getPositionVector().squareDistanceTo(origin)<1.0e-8,why);}
        void noClone(String why){check(gate.clone==null||gate.clone.isDead||!world.loadedEntityList.contains(gate.clone),why);}
        void tookHit(String why){check(Float.isFinite(p.getHealth())&&p.getHealth()<p.getMaxHealth(),why+" (health="+p.getHealth()+", max="+p.getMaxHealth()+", native hurt="+(gate.outer==null?"missing":gate.outer.getAmount())+")");}
        void normalAndCanceled(){
            reset();state.setBoolean("replacement",false);Scrolls.Lesson lesson=AbilityCarriers036.lesson("reed_escape");NBTTagCompound learned=Scrolls.row(p,lesson.key);learned.setBoolean("learned",true);learned.setInteger("practice",lesson.practice);
            RewardLedger.get(p).issue(p,"technique_reed_escape",1,"native039-reed/"+p.getUniqueID()+"/arm");ItemStack item=ItemStack.EMPTY;
            for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==Gear.ITEMS.get("technique_reed_escape")){item=stack;break;}
            check(!item.isEmpty(),"Could not obtain an issued Reed Escape technique");p.setHeldItem(EnumHand.MAIN_HAND,item);p.getCooldownTracker().setCooldown(item.getItem(),0);
            check(item.getItem().onItemRightClick(world,p,EnumHand.MAIN_HAND).getType()==EnumActionResult.SUCCESS&&PlayerState.resolve(p)==94&&state.getBoolean("replacement"),"Native Reed Escape arming failed or charged incorrectly");
            LivingHurtEvent hit=hurt();
            check(hit.isCanceled()&&p.getHealth()==p.getMaxHealth()&&p.posX==108.5&&gate.teleports==1&&gate.spawns==1,"Armed Reed Escape did not protect the actual incoming hit exactly once");
            check(!state.getBoolean("replacement")&&!state.hasKey("replacementUntil")&&PlayerState.resolve(p)==94&&gate.clone!=null&&!gate.clone.isDead,"Successful escape failed to consume its activation or keep its clone");
            p.getCooldownTracker().setCooldown(item.getItem(),0);gate.reset();p.setHealth(p.getMaxHealth());
            check(!hurt().isCanceled()&&gate.teleports==0&&gate.spawns==0,"Consumed Reed Escape triggered on a second hit");tookHit("Second hit was still protected by the consumed activation");

            reset();gate.cancelTeleport=true;
            check(!hurt().isCanceled()&&gate.teleports==1&&gate.spawns==0,"Canceled protection event still spawned a clone or canceled damage");noMove("Canceled escape moved the player");preserved("Canceled escape consumed its active state or resolve");tookHit("Canceled escape prevented real damage");
            gate.reset();p.setHealth(p.getMaxHealth());
            check(hurt().isCanceled()&&gate.teleports==1&&gate.spawns==1,"Canceled escape retained a stale owner guard");

            reset();gate.cancelSpawn=true;
            check(!hurt().isCanceled()&&gate.teleports==1&&gate.spawns==1,"Canceled clone still canceled the incoming hit");noMove("Canceled clone still moved the player");preserved("Canceled clone consumed the escape");noClone("Canceled clone leaked into the world");tookHit("Canceled clone prevented damage");

            reset();state.setLong("replacementUntil",ArsenalAccess.now(p)-1);
            check(!hurt().isCanceled()&&gate.teleports==0&&gate.spawns==0,"Expired escape still ran");tookHit("Expired escape prevented damage");

            reset();gate.cancelIncoming=true;
            check(hurt().isCanceled()&&gate.teleports==0&&gate.spawns==0,"An already-canceled hit spent an escape");preserved("Already-canceled damage consumed its escape");
        }
        void retarget(){
            for(Vec3d target:new Vec3d[]{origin,new Vec3d(Double.NaN,65,32.5),new Vec3d(116.5,65,32.5),new Vec3d(108.5,0,32.5)}){
                reset();gate.destination=target;
                check(!hurt().isCanceled()&&gate.spawns==0,"Escape accepted a no-op, nonfinite, out-of-range or invalid-height retarget");noMove("Unsafe escape retarget moved the player");preserved("Unsafe retarget consumed escape state");tookHit("Unsafe retarget canceled damage");
            }
            reset();BlockPos blocked=new BlockPos(111,65,34);world.setBlockState(blocked,Blocks.STONE.getDefaultState());gate.destination=new Vec3d(111.5,65,34.5);
            check(!hurt().isCanceled()&&gate.spawns==0,"Escape accepted a blocked retarget");preserved("Blocked retarget consumed escape state");world.setBlockState(blocked,Blocks.AIR.getDefaultState());
            reset();world.setBlockState(blocked,Blocks.WATER.getDefaultState());gate.destination=new Vec3d(111.5,65,34.5);
            check(!hurt().isCanceled()&&gate.spawns==0,"Escape accepted a liquid retarget");preserved("Liquid retarget consumed escape state");world.setBlockState(blocked,Blocks.AIR.getDefaultState());

            reset();gate.destination=new Vec3d(111.5,65,34.5);p.fallDistance=12;
            check(hurt().isCanceled()&&p.posX==111.5&&p.posZ==34.5&&p.fallDistance==0&&!state.getBoolean("replacement")&&gate.spawns==1,"Safe protection retarget was not honored exactly once");

            reset();gate.spawnAction=()->world.setBlockState(new BlockPos(108,65,32),Blocks.STONE.getDefaultState());
            check(!hurt().isCanceled(),"Escape ignored destination geometry changed during clone spawning");noMove("Spawn callback blocked the destination but escape still moved");preserved("Post-spawn geometry rejection consumed its activation");noClone("Post-spawn geometry rejection leaked its attempted clone");world.setBlockState(new BlockPos(108,65,32),Blocks.AIR.getDefaultState());
        }
        void changedAuthority(){
            reset();gate.teleportAction=()->state.setBoolean("replacement",false);
            check(!hurt().isCanceled()&&gate.spawns==0&&!state.getBoolean("replacement"),"Escape restored or ignored a callback-disabled activation");noMove("Disabled escape still moved");tookHit("Disabled escape canceled damage");

            reset();final NBTTagCompound newState=state.copy();newState.setLong("replacementUntil",until+900);newState.setString("callback","kept");gate.teleportAction=()->PlayerState.data(p).setTag("OriginalScrolls",newState);
            check(!hurt().isCanceled()&&gate.spawns==0&&Scrolls.state(p)==newState&&newState.getBoolean("replacement")&&newState.getLong("replacementUntil")==until+900,"Escape overwrote a replacement scroll compound");noMove("Replaced scroll authority still moved");

            reset();gate.teleportAction=()->p.setPosition(111.5,65,32.5);
            check(!hurt().isCanceled()&&p.posX==111.5&&gate.spawns==0,"Escape overrode callback movement before its own relocation");preserved("Callback movement consumed an unapplied escape");tookHit("Callback movement incorrectly canceled damage");

            reset();gate.teleportAction=()->p.dimension=savedDimension+1;
            try{check(!hurt().isCanceled()&&gate.spawns==0&&p.dimension==savedDimension+1,"Escape ignored or restored a callback dimension change");preserved("Changed dimension consumed the escape");}
            finally{p.dimension=savedDimension;}

            reset();final UUID originalOwner=p.getUniqueID();gate.teleportAction=()->{p.setUniqueId(UUID.randomUUID());recursiveHurt("changed owner");};
            try{check(!hurt().isCanceled()&&gate.spawns==0,"Escape accepted a callback change to player owner identity");noMove("Changed owner identity still moved");preserved("Changed owner identity consumed the activation");}
            finally{p.setUniqueId(originalOwner);}

            reset();gate.spawnAction=()->state.setLong("replacementUntil",until+50);
            check(!hurt().isCanceled()&&state.getBoolean("replacement")&&state.getLong("replacementUntil")==until+50,"Clone callback activation replacement was overwritten");noMove("Changed activation still moved");noClone("Changed activation leaked its attempted clone");

            reset();gate.spawnAction=()->p.setPosition(111.5,65,32.5);
            check(!hurt().isCanceled()&&p.posX==111.5,"Clone callback movement was overwritten");preserved("Clone callback movement consumed escape state");noClone("Clone callback movement leaked the attempted clone");

            reset();state.setString("mode","lava");state.setLong("modeUntil",until);final NBTTagCompound replacementAccount=PlayerState.data(p).copy();replacementAccount.setInteger("resolve",37);replacementAccount.getCompoundTag("OriginalScrolls").removeTag("mode");replacementAccount.getCompoundTag("OriginalScrolls").removeTag("modeUntil");gate.spawnAction=()->replaceAccount(replacementAccount);
            check(!hurt().isCanceled()&&PlayerState.data(p)==replacementAccount&&PlayerState.resolve(p)==37&&Scrolls.state(p).getBoolean("replacement"),"Escape changed a callback replacement account");noMove("Replaced account still escaped");noClone("Replaced account leaked the attempted clone");tookHit("Failed escape prevented incoming damage");check(gate.outer.getAmount()==6,"Failed escape applied a defensive mode from stale NBT");

            reset();gate.teleportAction=()->recursiveHurt("teleport");gate.spawnAction=()->recursiveHurt("spawn");
            check(hurt().isCanceled()&&gate.teleports==1&&gate.spawns==1&&!state.getBoolean("replacement"),"Recursive hurt caused a second teleport/clone or consumed twice");

            reset();gate.spawnAction=()->gate.outer.setCanceled(true);
            check(hurt().isCanceled()&&state.getBoolean("replacement"),"A callback-canceled incoming hit consumed the escape");noMove("Already-canceled incoming hit still moved");noClone("Callback-canceled damage leaked a clone");
        }
        void recursiveHurt(String phase){
            LivingHurtEvent nested=new LivingHurtEvent(p,new DamageSource("vale_reed039_recursive"),2);
            MinecraftForge.EVENT_BUS.post(nested);check(!nested.isCanceled(),"Recursive "+phase+" hurt reentered an active escape");
        }
        void connections(){
            reset();ScrollActor other=ScrollActor.spawn(p,"clone",origin.add(1,0,2),null,160,0);check(other!=null,"Could not create unrelated clone fixture");fixtures.add(other);
            ConnectionProbe rejected=new ConnectionProbe(p);
            try{
                rejected.reject=true;check(!hurt().isCanceled()&&gate.spawns==1,"No-move connection rejection canceled damage");noMove("Rejected connection still moved");preserved("Rejected connection consumed escape state");noClone("Rejected connection retained this attempt's clone");
                check(!other.isDead&&world.loadedEntityList.contains(other),"Failed escape cleaned up an unrelated existing clone");tookHit("No-move connection rejection prevented actual damage");
            }finally{rejected.close();}

            reset();ConnectionProbe redirected=new ConnectionProbe(p);
            try{redirected.after=()->{recursiveHurt("connection");p.setPosition(111.5,65,33.5);};
                check(hurt().isCanceled()&&p.posX==111.5&&p.posZ==33.5&&!state.getBoolean("replacement")&&gate.clone!=null&&!gate.clone.isDead,"Observed connection redirection was overwritten, unconsumed or left damage uncanceled");
            }finally{redirected.close();}

            reset();ConnectionProbe throwing=new ConnectionProbe(p);final RuntimeException expected=new IllegalStateException("Expected Reed post-move callback failure");
            try{throwing.after=()->{throw expected;};RuntimeException observed=null;try{hurt();}catch(RuntimeException error){observed=error;}
                check(observed==expected&&gate.outer!=null&&gate.outer.isCanceled()&&p.posX==108.5&&!state.getBoolean("replacement")&&!state.hasKey("replacementUntil")&&gate.clone!=null&&!gate.clone.isDead,"Post-move Reed exception lost its cancellation/consumption or replaced the original exception");
            }finally{throwing.close();}
            p.setPosition(origin.x,origin.y,origin.z);state.setBoolean("replacement",true);state.setLong("replacementUntil",until);p.setHealth(p.getMaxHealth());gate.reset();
            check(hurt().isCanceled()&&!state.getBoolean("replacement"),"Post-move Reed exception retained its owner guard");

            reset();final NBTTagCompound old=state,newAccount=PlayerState.data(p).copy();newAccount.setInteger("resolve",29);newAccount.getCompoundTag("OriginalScrolls").setLong("replacementUntil",until+700);ConnectionProbe replacement=new ConnectionProbe(p);
            try{replacement.after=()->replaceAccount(newAccount);
                check(hurt().isCanceled()&&!old.getBoolean("replacement")&&PlayerState.data(p)==newAccount&&PlayerState.resolve(p)==29&&Scrolls.state(p).getBoolean("replacement")&&Scrolls.state(p).getLong("replacementUntil")==until+700,"Applied relocation consumed new callback state or failed to close its original activation");
            }finally{replacement.close();}
        }
        void replaceAccount(NBTTagCompound account){p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).setTag("ValeOrders",account);}
        void close(){gate.reset();for(ScrollActor actor:fixtures){actor.setDead();actor.world.removeEntity(actor);}clear();p.world=world;p.dimension=savedDimension;p.clearActivePotions();replaceAccount(savedAccount);p.setPosition(savedPosition.x,savedPosition.y,savedPosition.z);p.setHealth(Math.min(savedHealth,p.getMaxHealth()));p.hurtResistantTime=0;p.hurtTime=0;p.setSneaking(false);ReflectionHelper.setPrivateValue(EntityPlayerMP.class,p,savedRespawn,"respawnInvulnerabilityTicks","field_147101_bU");}
    }
    public static final class Gate {
        final EntityPlayerMP p;final List<ScrollActor> fixtures;boolean active,cancelTeleport,cancelSpawn,cancelIncoming;int teleports,spawns;
        LivingHurtEvent outer;ScrollActor clone;Vec3d destination;Runnable teleportAction,spawnAction;
        Gate(EntityPlayerMP p,List<ScrollActor> fixtures){this.p=p;this.fixtures=fixtures;}
        void reset(){active=cancelTeleport=cancelSpawn=cancelIncoming=false;teleports=spawns=0;outer=null;clone=null;destination=null;teleportAction=spawnAction=null;}
        @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public void incoming(LivingHurtEvent event){if(active&&event.getEntityLiving()==p&&event.getSource()==SOURCE){outer=event;if(cancelIncoming)event.setCanceled(true);}}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void teleport(EnderTeleportEvent event){
            if(!active||event.getEntityLiving()!=p)return;teleports++;Runnable action=teleportAction;teleportAction=null;if(action!=null)action.run();
            if(destination!=null){event.setTargetX(destination.x);event.setTargetY(destination.y);event.setTargetZ(destination.z);}if(cancelTeleport)event.setCanceled(true);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void spawn(EntityJoinWorldEvent event){
            if(!active||!(event.getEntity() instanceof ScrollActor))return;ScrollActor actor=(ScrollActor)event.getEntity();if(!actor.owned(p)||!actor.kind.equals("clone"))return;
            spawns++;if(clone==null)clone=actor;fixtures.add(actor);Runnable action=spawnAction;spawnAction=null;if(action!=null)action.run();if(cancelSpawn)event.setCanceled(true);
        }
    }
    private static final class ConnectionProbe extends NetHandlerPlayServer {
        final EntityPlayerMP p;final NetHandlerPlayServer previous;boolean reject;Runnable after;
        ConnectionProbe(EntityPlayerMP p){this(p,p.connection);}
        private ConnectionProbe(EntityPlayerMP p,NetHandlerPlayServer previous){super(p.getServer(),previous.netManager,p);this.p=p;this.previous=previous;}
        @Override public void setPlayerLocation(double x,double y,double z,float yaw,float pitch){if(reject)return;previous.setPlayerLocation(x,y,z,yaw,pitch);Runnable callback=after;after=null;if(callback!=null)callback.run();}
        @Override public void sendPacket(Packet<?> packet){previous.sendPacket(packet);}
        void close(){p.connection=previous;previous.netManager.setNetHandler(previous);}
    }
}
