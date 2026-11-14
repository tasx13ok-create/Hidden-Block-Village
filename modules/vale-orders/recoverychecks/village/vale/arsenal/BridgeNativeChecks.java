package village.vale.arsenal;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.*;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import village.vale.PlayerState;
import village.vale.bridge.*;

/** Actual Forge callback/identity probe. Invoke LAST in the guarded, disposable native harness.
 * It is never a production source set or an actual Naruto cast-coverage claim. */
public final class BridgeNativeChecks {
    private final EntityPlayerMP player;private final WorldServer world;private final Gate gate=new Gate();
    private final List<Entity> actors=new ArrayList<>();private final NBTTagCompound state;private final NBTTagList inventory;
    private final double x,y,z;private final float health,absorption;private final NetHandlerPlayServer connection;
    private final UUID uuid;private final int entityId;private long clock;private int assertions;private EntityZombie target;private File config;
    private BridgeNativeChecks(EntityPlayerMP player){this.player=player;world=player.getServerWorld();state=PlayerState.data(player).copy();inventory=player.inventory.writeToNBT(new NBTTagList());x=player.posX;y=player.posY;z=player.posZ;health=player.getHealth();absorption=player.getAbsorptionAmount();connection=player.connection;uuid=player.getUniqueID();entityId=player.getEntityId();clock=world.getTotalWorldTime();gate.rig=this;}
    public static int run(EntityPlayerMP player)throws Exception{
        if(!Boolean.getBoolean("valeorders.recoverycheck")||!"vale-recovery-test".equals(player.getServer().getFolderName())||!new File(".vale-recovery-test").isFile())throw new IllegalStateException("Explicit disposable bridge fixture required");
        if(player.getServer().getPlayerList().getPlayers().size()!=1||player.getServer().getPlayerList().getPlayers().get(0)!=player)throw new IllegalStateException("No other online bodies allowed");
        BridgeNativeChecks checks=new BridgeNativeChecks(player);MinecraftForge.EVENT_BUS.register(checks.gate);
        try{checks.config=Files.createTempDirectory(new File(".").toPath(),"bridge-native-checks-").toFile();Bridge.start(player.getServer(),checks.config);checks.damage();checks.spawns();checks.cosmetics();return checks.assertions;}
        finally{MinecraftForge.EVENT_BUS.unregister(checks.gate);checks.close();}
    }
    private void check(boolean yes,String message){assertions++;if(!yes)throw new AssertionError("Native bridge: "+message);}
    private void flush()throws Exception{Field events=Bridge.class.getDeclaredField("EVENTS");events.setAccessible(true);((Bridge)events.get(null)).tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END));}
    private void next(){clock+=10;world.getWorldInfo().setWorldTotalTime(clock);}
    private void erase(){for(Entity e:new ArrayList<>(actors)){e.setDead();world.removeEntityDangerously(e);}actors.clear();for(HandTechnique h:new ArrayList<>(world.getEntities(HandTechnique.class,e->e.ownerId()==entityId)))world.removeEntityDangerously(h);for(WeaponPulse p:new ArrayList<>(world.getEntities(WeaponPulse.class,e->e.ownerId()==entityId)))world.removeEntityDangerously(p);}
    private int pulses(){return world.getEntities(WeaponPulse.class,e->!e.isDead&&e.ownerId()==entityId).size();}
    private WeaponPulse pulse(){List<WeaponPulse> list=world.getEntities(WeaponPulse.class,e->!e.isDead&&e.ownerId()==entityId);return list.isEmpty()?null:list.get(0);}
    private void reset(){erase();gate.clear();next();player.world=world;player.connection=connection;player.setUniqueId(uuid);player.setEntityId(entityId);PlayerState.data(player).removeTag("form");PlayerState.data(player).setTag("OriginalScrolls",new NBTTagCompound());player.setHealth(player.getMaxHealth());player.setAbsorptionAmount(0);player.setPosition(x,y,z);player.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND,new ItemStack(Items.STICK));
        target=new EntityZombie(world);target.setNoAI(true);target.setSilent(true);target.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(100);target.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0);target.setHealth(100);target.setPosition(x,y,z+2);check(world.spawnEntity(target),"target admission");actors.add(target);}
    private void mapItem(String style)throws Exception{Bridge.mapping(new BridgeMappings.Row("item","minecraft:stick","melee","direct_player",style),null);}
    private void hit(DamageSource source,float amount){target.hurtResistantTime=0;target.attackEntityFrom(source,amount);}
    private void damage()throws Exception{
        check(Bridge.mappingCount()==0,"new configuration has no inferred active mappings");reset();hit(DamageSource.causePlayerDamage(player),3);flush();check(pulses()==0,"unmapped actual damage has no cue");
        mapItem("weapon:"+ArsenalSpec.WARD_FAN.key);reset();hit(DamageSource.causePlayerDamage(player),3);check(pulses()==0,"no intent-stage cosmetic");NBTTagCompound afterGameplay=PlayerState.data(player).copy();double amount=MoveCost.view(player).amount;flush();check(pulses()==1,"actual accepted health loss presents once");check(afterGameplay.equals(PlayerState.data(player))&&Double.compare(amount,MoveCost.view(player).amount)==0,"bridge settlement changes no progression or resource state");flush();check(pulses()==1,"receipt does not replay");
        for(int stage=1;stage<=3;stage++){reset();gate.cancelStage=stage;hit(DamageSource.causePlayerDamage(player),3);flush();check(target.getHealth()==100&&pulses()==0,"final canceled contact stage "+stage);}
        reset();target.setAbsorptionAmount(40);hit(DamageSource.causePlayerDamage(player),3);check(target.getHealth()==100&&target.getAbsorptionAmount()<40,"real absorption-only outcome");flush();check(pulses()==1,"absorption-only final outcome presents");
        reset();gate.zeroDamage=true;hit(DamageSource.causePlayerDamage(player),3);gate.zeroDamage=false;hit(DamageSource.GENERIC,3);flush();check(pulses()==0,"zero mapped contact cannot inherit later unrelated loss");
        reset();hit(DamageSource.causePlayerDamage(player),3);target.heal(10);flush();check(pulses()==1,"accepted loss survives subsequent native heal");
        reset();DamageSource source=DamageSource.causePlayerDamage(player);MinecraftForge.EVENT_BUS.post(new LivingAttackEvent(target,source,3));MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(target,source,3));MinecraftForge.EVENT_BUS.post(new LivingDamageEvent(target,source,3));flush();check(pulses()==0,"posted events without native outcome do not present");
    }
    private EntityTippedArrow arrow(boolean owner){EntityTippedArrow arrow=new EntityTippedArrow(world);if(owner)arrow.shootingEntity=player;arrow.setPosition(x,y+2,z);actors.add(arrow);return arrow;}
    private void spawns()throws Exception{
        Bridge.mapping(new BridgeMappings.Row("entity","minecraft:arrow","spawn","arrow","weapon:"+ArsenalSpec.WARD_FAN.key),null);
        reset();EntityTippedArrow arrow=arrow(true);check(world.spawnEntity(arrow),"owned native arrow admitted");check(pulses()==0,"spawn callback itself has no cue");flush();check(pulses()==1,"admitted owned native arrow presents after unwind");
        reset();gate.cancelArrow=true;arrow=arrow(true);world.spawnEntity(arrow);flush();check(pulses()==0,"later final-canceled native spawn refused");
        reset();arrow=arrow(false);world.spawnEntity(arrow);flush();check(pulses()==0,"unattributed native entity refused");
        reset();arrow=arrow(true);world.spawnEntity(arrow);arrow.setUniqueId(UUID.randomUUID());flush();check(pulses()==0,"changed entity identity refused");
        reset();arrow=arrow(true);world.spawnEntity(arrow);world.removeEntityDangerously(arrow);flush();check(pulses()==0,"removed native entity refused");
        reset();arrow=arrow(true);world.spawnEntity(arrow);arrow.shootingEntity=null;flush();check(pulses()==0,"changed final owner refused");
        reset();arrow=arrow(true);world.spawnEntity(arrow);clock+=3;world.getWorldInfo().setWorldTotalTime(clock);flush();check(pulses()==0,"stale spawn receipt refused");
        reset();arrow=arrow(true);world.spawnEntity(arrow);MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player,false));flush();check(pulses()==0,"same-body respawn invalidates pending receipt epoch");
        reset();arrow=arrow(true);world.spawnEntity(arrow);Bridge.reload();flush();check(pulses()==0,"mapping reload invalidates prior snapshot receipt");
    }
    private void accepted()throws Exception{hit(DamageSource.causePlayerDamage(player),3);flush();}
    private void cosmetics()throws Exception{
        mapItem("weapon:"+ArsenalSpec.WARD_FAN.key);reset();accepted();WeaponPulse pulse=pulse();check(pulse!=null,"cosmetic pulse admitted");int id=player.getEntityId();MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player,false));check(player.getEntityId()==id&&pulse.isDead,"same-body respawn closes tagged pulse by epoch");
        reset();accepted();pulse=pulse();LivingDeathEvent death=new LivingDeathEvent(player,DamageSource.GENERIC);death.setCanceled(true);MinecraftForge.EVENT_BUS.post(death);pulse.onUpdate();check(!pulse.isDead,"final-canceled death alone does not invalidate a living cosmetic");check(!pulse.writeToNBTOptional(new NBTTagCompound()),"cosmetic pulse never persists");WeaponPulse loaded=new WeaponPulse(world);loaded.readEntityFromNBT(new NBTTagCompound());check(loaded.isDead,"uncaptured deserialized pulse dies without UUID attachment");
        reset();gate.cancelPulse=true;accepted();check(target.getHealth()<100&&pulses()==0,"late cosmetic spawn cancellation retains native outcome without a cue");
        reset();gate.cancelPulse=false;gate.mutation=5;accepted();check(pulses()==0,"same-body respawn callback rejects cosmetic admission");
        reset();gate.mutation=1;accepted();check(pulses()==0,"connection replacement during cosmetic admission fails closed");player.connection=connection;
        reset();gate.mutation=2;accepted();check(pulses()==0,"world replacement during cosmetic admission fails closed");player.world=world;
        reset();gate.mutation=3;accepted();check(pulses()==0,"entity ID replacement during cosmetic admission fails closed");player.setEntityId(entityId);
        reset();gate.mutation=4;accepted();check(pulses()==0,"owner UUID replacement during cosmetic admission fails closed");player.setUniqueId(uuid);
        reset();accepted();pulse=pulse();WorldServer other=player.getServer().getWorld(-1);check(other!=null&&other!=world,"isolated alternate world fixture");pulse.world=other;pulse.onUpdate();check(pulse.isDead,"cosmetic pulse moved across worlds expires");pulse.world=world;
        reset();accepted();pulse=pulse();world.removeEntityDangerously(pulse);pulse.onUpdate();check(pulse.isDead,"removed cosmetic pulse cannot remain a live UUID follower");
        reset();accepted();pulse=pulse();Bridge.reload();pulse.onUpdate();check(pulse.isDead,"snapshot-invalid cosmetic expires before UUID following");
        reset();HandTechnique.begin(player,"spiral");HandTechnique earned=HandTechnique.find(player);check(earned!=null&&!earned.visualOnly(),"earned hand carrier fixture");mapItem("charge");accepted();check(HandTechnique.find(player)==earned&&earned.state()==HandTechnique.HELD,"native hand cue never replaces earned carrier");Bridge.stop();check(!earned.isDead&&earned.state()==HandTechnique.HELD,"bridge stop closes no earned hand carrier");Bridge.start(player.getServer(),config);
        reset();mapItem("charge");accepted();List<HandTechnique> hands=world.getEntities(HandTechnique.class,h->!h.isDead&&h.visualOnly());check(hands.size()==1&&HandTechnique.find(player)==null,"cosmetic hand is not a gameplay carrier");HandTechnique visual=hands.get(0);MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(player,false));check(visual.isDead,"same-body respawn closes only tagged cosmetic hand");
        reset();mapItem("weapon:"+ArsenalSpec.WARD_FAN.key);WeaponPulse.cast(player,ArsenalSpec.WARD_FAN,1);WeaponPulse earnedPulse=pulse();accepted();check(pulses()==2,"earned and cosmetic pulses can coexist");Bridge.stop();check(earnedPulse!=null&&!earnedPulse.isDead&&pulses()==1,"bridge stop closes only tagged cosmetic weapon pulse");
    }
    private void close(){try{player.world=world;player.connection=connection;player.setUniqueId(uuid);player.setEntityId(entityId);Bridge.stop();erase();player.inventory.readFromNBT(inventory);player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).setTag("ValeOrders",state);player.setPosition(x,y,z);player.setHealth(Math.min(health,player.getMaxHealth()));player.setAbsorptionAmount(absorption);}finally{gate.clear();}}
    public static final class Gate {
        BridgeNativeChecks rig;int cancelStage,mutation;boolean cancelArrow,cancelPulse,zeroDamage;
        void clear(){cancelStage=mutation=0;cancelArrow=cancelPulse=zeroDamage=false;}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void spawn(EntityJoinWorldEvent event){if(cancelArrow&&event.getEntity() instanceof EntityTippedArrow)event.setCanceled(true);if(!(event.getEntity() instanceof WeaponPulse))return;if(cancelPulse)event.setCanceled(true);if(mutation==0)return;int selected=mutation;mutation=0;if(selected==1)rig.player.connection=null;if(selected==2)rig.player.world=null;if(selected==3)rig.player.setEntityId(rig.entityId+100000);if(selected==4)rig.player.setUniqueId(UUID.randomUUID());if(selected==5)MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(rig.player,false));}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void attack(LivingAttackEvent event){if(event.getEntityLiving()==rig.target&&cancelStage==1)event.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void hurt(LivingHurtEvent event){if(event.getEntityLiving()==rig.target&&cancelStage==2)event.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)public void damage(LivingDamageEvent event){if(event.getEntityLiving()!=rig.target)return;if(cancelStage==3)event.setCanceled(true);if(zeroDamage)event.setAmount(0);}
    }
}
