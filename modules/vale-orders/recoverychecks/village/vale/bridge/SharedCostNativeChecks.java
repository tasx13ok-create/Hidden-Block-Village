package village.vale.bridge;

import com.mojang.authlib.GameProfile;
import java.io.File;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.*;
import net.minecraft.util.text.*;
import net.minecraft.world.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import village.vale.*;

/** Private actual native absent-provider regression. Full54 provider/cache acceptance is separate. */
public final class SharedCostNativeChecks {
    private static int checks,serial;private static Learner p;private static WorldServer world;private static EntityZombie victim;
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Shared cost native: "+why);}
    public static int run(EntityPlayerMP anchor)throws Exception{
        MinecraftServer server=anchor.getServer();
        if(!Boolean.getBoolean("valeorders.recoverycheck")||!"vale-recovery-test".equals(server.getFolderName())||!new File(".vale-recovery-test").isFile())throw new IllegalStateException("Disposable native isolation required");
        checks=0;require(!MoveCost.external(),"This is the absent-provider regression, not a full54 compatibility claim");checks+=ResourceCodecNativeChecks.check();serial=0;world=server.getWorld(0);
        for(int x=58;x<=60;x++)for(int z=58;z<=60;z++)world.getChunk(x,z);
        for(int x=944;x<=954;x++)for(int z=944;z<=956;z++){world.setBlockState(new net.minecraft.util.math.BlockPos(x,90,z),Blocks.STONE.getDefaultState(),2);for(int y=91;y<=94;y++)world.setBlockToAir(new net.minecraft.util.math.BlockPos(x,y,z));}
        p=new Learner(server,world);p.connection=new Quiet(server,p);p.interactionManager.setGameType(GameType.SURVIVAL);server.getPlayerList().playerLoggedIn(p);FMLCommonHandler.instance().firePlayerLoggedIn(p);ReflectionHelper.setPrivateValue(EntityPlayerMP.class,p,0,"respawnInvulnerabilityTicks","field_147101_bU");
        Gate gate=new Gate();MinecraftForge.EVENT_BUS.register(gate);
        try{
            require(server.getPlayerList().getPlayerByUUID(p.getUniqueID())==p,"Actual server player registration");
            prepare(Order.STORM,"saber");spawnVictim();gate.cancelDamage=true;
            require(!Combat.activate(p,p.getHeldItemMainhand(),Order.STORM),"Late canceled order damage accepted");require(balance()==100&&state().getLong("nextSkill")==0,"Canceled order spent chakra/recovery");
            gate.cancelDamage=false;gate.cancelHurt=true;require(!Combat.activate(p,p.getHeldItemMainhand(),Order.STORM),"Canceled Hurt order accepted");require(balance()==100,"Canceled Hurt order charged");
            gate.cancelHurt=false;gate.cancelDamage=true;victim.setAbsorptionAmount(20);victim.hurtResistantTime=0;
            require(!Combat.activate(p,p.getHeldItemMainhand(),Order.STORM),"Final Damage cancellation after absorption accepted");require(victim.getAbsorptionAmount()<20&&balance()==100,"Absorption-phase cancellation gave paid/credited contact");
            gate.cancelDamage=false;victim.setAbsorptionAmount(0);victim.hurtResistantTime=0;
            require(Combat.activate(p,p.getHeldItemMainhand(),Order.STORM),"Accepted order refused");require(balance()==100-ValeOrders.skillCost,"Order did not debit once");
            prepare(Order.RIME,"spear");gate.cancelDamage=true;victim.hurtResistantTime=0;
            require(!Techniques.activate(p,(Gear.WardBlade)p.getHeldItemMainhand().getItem()),"Canceled spear special accepted");require(balance()==100&&state().getLong("nextTechnique")==0,"Canceled spear retained debit/recovery");
            gate.cancelDamage=false;victim.hurtResistantTime=0;require(Techniques.activate(p,(Gear.WardBlade)p.getHeldItemMainhand().getItem()),"Native spear accepted hit refused");require(balance()==80,"Spear debited more than once");
            prepare(Order.RIME,"staff");p.setHealth(p.getMaxHealth()-4);gate.cancelHeal=true;
            require(!Techniques.activate(p,(Gear.WardBlade)p.getHeldItemMainhand().getItem()),"Canceled heal claimed a staff effect");require(balance()==100&&state().getLong("nextTechnique")==0,"Canceled staff charged");
            gate.cancelHeal=false;gate.throwAcceptedHeal=true;boolean threw=false;try{Techniques.activate(p,(Gear.WardBlade)p.getHeldItemMainhand().getItem());}catch(IllegalStateException expected){threw=true;}
            require(threw&&balance()==80&&state().getLong("nextTechnique")>Adventures.now(p),"Actual heal before callback throw was refunded");
            prepare(Order.RIME,"saber");hold(issue("technique_codex"));require(Arts.select(p,"damping_script"),"Damping lesson unavailable to prepared synthetic account");victim.hurtResistantTime=0;gate.cancelDamage=true;gate.otherSpend=7;gate.reenter=true;
            require(!Arts.cast(p),"Canceled utility damage accepted");require(balance()==93&&gate.nestedRefused,"Refund erased unrelated debit or allowed reserve reentry");require(state().getLong("artGlobal")==0&&state().getCompoundTag("artCooldowns").getLong("damping_script")==0,"Canceled art recovery survived");
            gate.cancelDamage=false;gate.reenter=false;prepare(Order.RIME,"saber");hold(issue("technique_codex"));Arts.select(p,"phase_step");gate.cancelTeleport=true;double beforeZ=p.posZ;
            require(!Arts.cast(p)&&p.posZ==beforeZ&&balance()==100,"Canceled teleport moved/charged");
            gate.cancelTeleport=false;gate.swapCodex=true;require(!Arts.cast(p)&&p.posZ==beforeZ&&balance()==100,"Teleport callback detached codex bypassed authority");
            gate.swapCodex=false;prepare(Order.RIME,"saber");hold(issue("technique_codex"));Arts.select(p,"phase_step");require(Arts.cast(p)&&p.posZ>beforeZ+3.9&&balance()==82,"Accepted physical phase step not debited once");
            prepare(Order.RIME,"saber");gate.cancelSpawn=true;require(!Summons.activate(p,0),"Canceled native companion spawn accepted");require(balance()==100&&state().getLong("summonReady")==0&&state().getLong("summonEnd")==0,"Rejected spawn retained fee/recovery/lease");
            gate.cancelSpawn=false;require(Summons.activate(p,0)&&balance()==65,"Accepted companion fee");Entity actor=world.getEntityFromUuid(state().getUniqueId("summonId"));require(actor instanceof EchoCompanion&&!actor.isDead,"No actual admitted owned companion");MoveCost.Ticket held=MoveCost.reserve(p,0);require(held!=null&&MoveCost.busy(p),"Current body pending guard not visible");long summonDue=state().getLong("summonDrain");Summons.tick(p);require(!actor.isDead&&balance()==65&&state().getLong("summonDrain")==summonDue,"Busy companion ended or advanced unpaid deadline");step(40);Summons.tick(p);require(!actor.isDead&&balance()==65,"Busy companion catch-up charged while locked");require(held.commit()&&!MoveCost.busy(p),"Settled guard remained busy");Summons.tick(p);require(balance()==62&&state().getLong("summonDrain")==summonDue+60,"Companion missed three-period catch-up");
            PlayerState.setResolve(p,1);step(20);Summons.tick(p);require(balance()==0&&!actor.isDead,"Exact-zero upkeep refused");step(20);Summons.tick(p);require(actor.isDead&&state().getLong("summonEnd")==0,"Exhausted companion continued without resource");
            prepare(Order.RIME,"saber");gate.cancelForm=true;require(!Forms.activate(p,0),"Canceled form potion accepted");require(balance()==100&&Forms.active(p)<0&&state().getLong("formReady")==0,"Canceled form retained fee/state/recovery");
            gate.cancelForm=false;require(Forms.activate(p,0)&&balance()==40&&p.isPotionActive(Forms.EFFECTS[0]),"Actual form admission fee");final boolean[] readBusy={false};long formDue=state().getLong("formDrain");p.onData=()->{readBusy[0]=MoveCost.busy(p);Forms.tick(p);};MoveCost.view(p);require(readBusy[0]&&Forms.active(p)==0&&balance()==40&&state().getLong("formDrain")==formDue,"Read guard shut down form or spent upkeep");
            held=MoveCost.reserve(p,0);require(held!=null&&MoveCost.busy(p),"Form transaction guard absent");Forms.tick(p);step(40);Forms.tick(p);require(Forms.active(p)==0&&balance()==40&&state().getLong("formDrain")==formDue,"Busy form changed resource/deadline");
            Learner staleBody=new Learner(server,world);staleBody.connection=new Quiet(server,staleBody);require(!MoveCost.busy(staleBody),"Unregistered same-UUID body inherited busy authority");((Quiet)staleBody.connection).stop();
            final boolean[] offThread={true};Thread observer=new Thread(()->offThread[0]=MoveCost.busy(p),"shared-busy-probe");observer.start();observer.join(2000);require(!observer.isAlive()&&!offThread[0],"Off-thread busy read exposed mutable server state");
            require(held.commit()&&!MoveCost.busy(p),"Form busy did not clear");Forms.tick(p);require(balance()==31&&state().getLong("formDrain")==formDue+60,"Form missed three-period catch-up");PlayerState.setResolve(p,2);step(20);Forms.tick(p);require(balance()==2&&Forms.active(p)<0,"Insufficient form upkeep did not stop cleanly");
            prepare(Order.RIME,"saber");gate.throwAcceptedForm=true;threw=false;try{Forms.activate(p,0);}catch(IllegalStateException expected){threw=true;}
            require(threw&&p.isPotionActive(Forms.EFFECTS[0])&&balance()==40&&state().getLong("formReady")>Adventures.now(p),"Accepted potion before callback throw became free");require(Forms.activate(p,0)&&Forms.active(p)<0&&balance()==40,"Form startup guard leaked after callback failure");
            prepare(Order.RIME,"saber");PlayerState.setResolve(p,50);p.ticksExisted=20;state().setLong("lastCombat",Adventures.now(p));new Combat().tick(new net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent(net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END,p));require(balance()==(Adventures.now(p)>0?52:55),"Absent-mod legacy regeneration changed");
            lifecycle(gate);
            return checks;
        }finally{
            MinecraftForge.EVENT_BUS.unregister(gate);if(victim!=null){victim.setDead();world.removeEntity(victim);}if(p!=null){Forms.end(p,false);Summons.dismiss(p);if(server.getPlayerList().getPlayerByUUID(p.getUniqueID())==p)server.getPlayerList().playerLoggedOut(p);if(p.connection instanceof Quiet)((Quiet)p.connection).stop();}p=null;victim=null;
        }
    }
    private static void lifecycle(Gate gate){
        // These are real Forge event dispatches on an actually registered body with a retained pool.
        // Full Magma recreatePlayerEntity same-object behavior is a separate host acceptance case.
        Forms.end(p,false);Summons.dismiss(p);p.clearActivePotions();p.setHealth(p.getMaxHealth());
        Runnable[] transitions={
            ()->MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.Clone(p,p,true)),
            ()->MinecraftForge.EVENT_BUS.post(new net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent(p,false)),
            ()->MinecraftForge.EVENT_BUS.post(new net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent(p)),
            ()->MinecraftForge.EVENT_BUS.post(new net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent(p)),
            ()->MinecraftForge.EVENT_BUS.post(new net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent(p,0,0))
        };
        int transition=0;
        for(Runnable event:transitions){
            PlayerState.setResolve(p,100);MoveCost.Ticket old=MoveCost.reserve(p,10);require(old!=null&&balance()==90,"Lifecycle fixture initial reservation "+transition);
            event.run();PlayerState.setResolve(p,100);
            require(!old.pending()&&!old.valid()&&!old.refund()&&!old.commit()&&balance()==100,"Old reservation survived same-body lifecycle "+transition);
            MoveCost.Ticket fresh=MoveCost.reserve(p,1);require(fresh!=null&&fresh.valid()&&MoveCost.busy(p),"New session did not acquire authority "+transition);
            require(!old.refund()&&MoveCost.busy(p)&&balance()==99,"Old session disturbed new guard or credited pool "+transition);
            require(fresh.refund()&&balance()==100&&!MoveCost.busy(p),"New session refund failed "+transition);transition++;
        }
        PlayerState.setResolve(p,100);MoveCost.Ticket canceled=MoveCost.reserve(p,10);require(canceled!=null,"Canceled death fixture reservation");
        gate.cancelDeath=true;LivingDeathEvent refused=new LivingDeathEvent(p,DamageSource.GENERIC);MinecraftForge.EVENT_BUS.post(refused);gate.cancelDeath=false;
        MoveCost.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent(net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END));
        require(refused.isCanceled()&&canceled.valid()&&canceled.refund()&&balance()==100,"Final-canceled death invalidated live session");
        MoveCost.Ticket dead=MoveCost.reserve(p,10);require(dead!=null,"Accepted death fixture reservation");
        LivingDeathEvent accepted=new LivingDeathEvent(p,DamageSource.GENERIC);MinecraftForge.EVENT_BUS.post(accepted);require(!accepted.isCanceled(),"Accepted death fixture unexpectedly canceled");p.setHealth(0);
        MoveCost.tick(new net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent(net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END));p.setHealth(p.getMaxHealth());PlayerState.setResolve(p,100);
        require(!dead.pending()&&!dead.refund()&&!dead.commit()&&balance()==100,"Accepted dead body resurrection revived old reservation");
    }
    private static void step(int ticks){world.getWorldInfo().setWorldTotalTime(world.getTotalWorldTime()+ticks);for(int i=0;i<ticks;i++)p.getCooldownTracker().tick();}
    private static NBTTagCompound state(){return PlayerState.data(p);}
    private static int balance(){return MoveCost.availableUnits(p);}
    private static void prepare(Order order,String weapon){
        Forms.end(p,false);Summons.dismiss(p);p.clearActivePotions();p.resetActiveHand();p.inventory.clear();p.inventory.currentItem=0;p.setPositionAndRotation(948.5,91,948.5,0,0);p.motionX=p.motionY=p.motionZ=0;p.onGround=true;p.setHealth(p.getMaxHealth());
        NBTTagCompound s=state();s.setString("starterOrder",order.key);s.setInteger("xp",10000);s.setLong("nextSkill",0);s.setLong("nextTechnique",0);s.setLong("artGlobal",0);s.setTag("artCooldowns",new NBTTagCompound());s.setLong("formReady",0);s.setLong("summonReady",0);s.setLong("guardUntil",0);PlayerState.setResolve(p,100);
        String[] parts={"helmet","chestplate","leggings","boots"};EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
        for(int i=0;i<parts.length;i++){ItemStack gear=issue(order.key+"_"+parts[i]);remove(gear);p.setItemStackToSlot(slots[i],gear);}hold(issue(order.key+"_"+weapon));
        require(Combat.fullSet(p)==order,"Real issued full-set proof");
    }
    private static ItemStack issue(String key){RewardLedger.get(p).issue(p,key,1,"shared016/"+(serial++));for(ItemStack stack:p.inventory.mainInventory)if(!stack.isEmpty()&&stack.getItem()==Gear.ITEMS.get(key)&&RewardLedger.get(p).valid(p,stack))return stack;throw new AssertionError("Missing issued "+key);}
    private static void remove(ItemStack stack){for(int i=0;i<p.inventory.mainInventory.size();i++)if(p.inventory.mainInventory.get(i)==stack)p.inventory.mainInventory.set(i,ItemStack.EMPTY);}
    private static void hold(ItemStack stack){remove(stack);ItemStack old=p.getHeldItemMainhand();p.inventory.mainInventory.set(0,stack);p.inventory.currentItem=0;if(!old.isEmpty())p.inventory.addItemStackToInventory(old);}
    private static void spawnVictim(){victim=new EntityZombie(world);victim.setNoAI(true);victim.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(100);victim.setHealth(100);victim.setPosition(948.5,91,951);require(world.spawnEntity(victim),"Native hostile fixture spawn");}
    public static final class Gate {
        boolean cancelDeath,cancelDamage,cancelHurt,cancelHeal,cancelTeleport,swapCodex,cancelSpawn,cancelForm,throwAcceptedHeal,throwAcceptedForm,reenter,nestedRefused;int otherSpend;
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void death(LivingDeathEvent e){if(e.getEntityLiving()==p&&cancelDeath)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void hurt(LivingHurtEvent e){if(e.getEntityLiving()==victim&&cancelHurt)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void damage(LivingDamageEvent e){if(e.getEntityLiving()!=victim)return;if(reenter)nestedRefused=MoveCost.reserve(p,1)==null;if(otherSpend>0){PlayerState.setResolve(p,PlayerState.resolve(p)-otherSpend);otherSpend=0;}if(cancelDamage)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void heal(LivingHealEvent e){if(e.getEntityLiving()!=p)return;if(cancelHeal)e.setCanceled(true);if(throwAcceptedHeal){throwAcceptedHeal=false;p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+2));throw new IllegalStateException("Synthetic after actual health change");}}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void potion(PotionEvent.PotionApplicableEvent e){if(e.getEntityLiving()!=p||e.getPotionEffect().getPotion()!=Forms.EFFECTS[0])return;if(cancelForm)e.setResult(Event.Result.DENY);if(throwAcceptedForm){throwAcceptedForm=false;p.addPotionEffect(new PotionEffect(Forms.EFFECTS[0],400,0,false,true));throw new IllegalStateException("Synthetic after actual native potion admission");}}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void teleport(EnderTeleportEvent e){if(e.getEntityLiving()!=p)return;if(swapCodex)p.inventory.mainInventory.set(0,ItemStack.EMPTY);if(cancelTeleport)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void spawn(EntityJoinWorldEvent e){if(cancelSpawn&&e.getEntity() instanceof EchoCompanion)e.setCanceled(true);}
    }
    private static final class Learner extends EntityPlayerMP {
        Runnable onData;
        @Override public NBTTagCompound getEntityData(){Runnable action=onData;onData=null;if(action!=null)action.run();return super.getEntityData();}
        Learner(MinecraftServer server,WorldServer world){super(server,world,new GameProfile(UUID.fromString("43ec7807-821a-4a56-9c03-031600000001"),"SharedCost0316"),new PlayerInteractionManager(world));}
        @Override public void sendMessage(ITextComponent value){}
        @Override public void sendStatusMessage(ITextComponent value,boolean bar){}
    }
    private static final class Quiet extends NetHandlerPlayServer {
        final NetworkManager manager;
        Quiet(MinecraftServer server,EntityPlayerMP player){this(server,player,network());}
        Quiet(MinecraftServer server,EntityPlayerMP player,NetworkManager manager){super(server,manager,player);this.manager=manager;}
        private static NetworkManager network(){NetworkManager n=new NetworkManager(EnumPacketDirection.SERVERBOUND);new io.netty.channel.embedded.EmbeddedChannel(n);return n;}
        @Override public void sendPacket(Packet<?> packet){}
        void stop(){manager.closeChannel(new TextComponentString("Shared fixture complete"));}
    }
}
