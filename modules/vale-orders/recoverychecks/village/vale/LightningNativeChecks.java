package village.vale;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Runs after the original Academy route on its real logged-in survival learner. */
public final class LightningNativeChecks {
    private final EntityPlayerMP p;private final AcademyCourse course;
    private final List<EntityLivingBase> actors=new ArrayList<>();
    private final Gate gate=new Gate();private int assertions;
    private LightningNativeChecks(EntityPlayerMP p){this.p=p;course=AcademyCourse.get(p.getServer().getWorld(0));}
    public static int run(EntityPlayerMP p){LightningNativeChecks c=new LightningNativeChecks(p);MinecraftForge.EVENT_BUS.register(c.gate);
        try{c.exercise();return c.assertions;}finally{MinecraftForge.EVENT_BUS.unregister(c.gate);c.cleanup();}}
    private void check(boolean ok,String message){assertions++;if(!ok)throw new AssertionError("Lightning native: "+message);}
    private long now(){return Adventures.now(p);}
    private void step(int ticks){p.getServer().getWorld(0).getWorldInfo().setWorldTotalTime(now()+ticks);}
    private void place(){BlockPos at=course.at(16,34);p.setPositionAndRotation(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        p.rotationYawHead=0;p.onGround=true;p.motionX=p.motionY=p.motionZ=0;p.fallDistance=0;p.setHealth(p.getMaxHealth());p.setAbsorptionAmount(0);p.hurtResistantTime=0;}
    private ItemStack find(String key){for(ItemStack stack:p.inventory.mainInventory)if(!stack.isEmpty()&&stack.getItem()==Gear.ITEMS.get(key)&&RewardLedger.get(p).valid(p,stack))return stack;
        for(ItemStack stack:p.inventory.armorInventory)if(!stack.isEmpty()&&stack.getItem()==Gear.ITEMS.get(key)&&RewardLedger.get(p).valid(p,stack))return stack;
        throw new AssertionError("Lightning issued fixture missing "+key);}
    private int quantity(String key){int count=0;for(ItemStack stack:p.inventory.mainInventory)if(stack.getItem()==Gear.ITEMS.get(key))count+=stack.getCount();
        for(ItemStack stack:p.inventory.armorInventory)if(stack.getItem()==Gear.ITEMS.get(key))count+=stack.getCount();return count;}
    private void take(ItemStack stack){for(int i=0;i<p.inventory.mainInventory.size();i++)if(p.inventory.mainInventory.get(i)==stack)p.inventory.mainInventory.set(i,ItemStack.EMPTY);}
    private void armor(){String[] parts={"helmet","chestplate","leggings","boots"};EntityEquipmentSlot[] slots={EntityEquipmentSlot.HEAD,EntityEquipmentSlot.CHEST,EntityEquipmentSlot.LEGS,EntityEquipmentSlot.FEET};
        for(int i=0;i<parts.length;i++){ItemStack incoming=find("storm_"+parts[i]);if(p.getItemStackFromSlot(slots[i])==incoming)continue;
            take(incoming);ItemStack old=p.getItemStackFromSlot(slots[i]);p.setItemStackToSlot(slots[i],incoming);if(!old.isEmpty())check(p.inventory.addItemStackToInventory(old),"No room to preserve original learner armor");}
        check(Combat.fullSet(p)==Order.STORM,"Native matching Storm armor not recognized");}
    private void ready(){LightningKit.end(p);PlayerState.data(p).setLong("artGlobal",0);NBTTagCompound cds=PlayerState.data(p).getCompoundTag("artCooldowns");
        for(String key:LightningKit.KEYS)cds.removeTag(key);PlayerState.data(p).setTag("artCooldowns",cds);PlayerState.setResolve(p,100);place();gate.reset();}
    private boolean cast(int index){return Arts.castFromBar(p,LightningKit.KEYS[index]);}
    private void mode(){ready();check(cast(0)&&LightningKit.active(p)&&PlayerState.resolve(p)==80,"Armor activation or cost failed");step(10);}
    private EntityZombie zombie(double x,double z){EntityZombie t=new EntityZombie(p.world);t.setNoAI(true);t.setSilent(true);t.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);
        t.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0);t.setHealth(1000);t.setPosition(p.posX+x,p.posY,p.posZ+z);
        check(p.world.spawnEntity(t),"Native target spawn denied");actors.add(t);return t;}
    private void erase(){for(EntityLivingBase actor:actors)p.world.removeEntityDangerously(actor);actors.clear();}
    private void cleanup(){gate.reset();LightningKitEvents.flush();LightningKit.end(p);LightningTrial.clear(p);erase();}
    private void exercise(){
        check(AcademyCampaign.basicsComplete(p)&&LightningTrial.canAccept(p),"Run this helper after the Academy learner route");
        String starter=PlayerState.data(p).getString("starterOrder"),nature=AcademyCampaign.state(p).getString("nature");
        check(LightningTrial.begin(p)&&LightningKit.known(p),"Aki optional kit acquisition failed");
        String[] items={"storm_helmet","storm_chestplate","storm_leggings","storm_boots","storm_saber","technique_codex"};
        int[] quantities=new int[items.length];String[] serials=new String[items.length];
        for(int i=0;i<items.length;i++){quantities[i]=quantity(items[i]);serials[i]=find(items[i]).getTagCompound().getString("valeSerial");check(quantities[i]>=1,"Kit item not delivered "+items[i]);}
        LightningTrial.acquire(p);LightningTrial.acquire(p);
        for(int i=0;i<items.length;i++)check(quantity(items[i])==quantities[i]&&find(items[i]).getTagCompound().getString("valeSerial").equals(serials[i]),"Acquisition replay changed receipt or quantity "+items[i]);
        check(PlayerState.data(p).getString("starterOrder").equals(starter)&&AcademyCampaign.state(p).getString("nature").equals(nature),"Optional kit changed original nature");
        armor();mode();long began=now()-10;
        check(LightningKit.state(p).getLong("modeUntil")==began+600,"Mode duration is not thirty seconds");
        check(p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue()>.1,"Mode has no native movement attribute");
        int before=PlayerState.resolve(p);check(!cast(0)&&PlayerState.resolve(p)==before,"Duplicate mode activation charged or restarted");
        step(10);LightningKit.tick(p);check(PlayerState.resolve(p)==76,"One-second upkeep is not four chakra");
        step(580);LightningKit.tick(p);check(!LightningKit.active(p)&&!p.isPotionActive(LightningKit.EFFECT),"Expired mode retained native potion");
        mode();PlayerState.setResolve(p,3);step(10);LightningKit.tick(p);check(!LightningKit.active(p)&&PlayerState.resolve(p)==3,"Insufficient upkeep went negative or retained mode");
        ready();gate.denyMode=true;long oldGlobal=PlayerState.data(p).getLong("artGlobal"),oldReady=PlayerState.data(p).getCompoundTag("artCooldowns").getLong(LightningKit.KEYS[0]);
        check(!cast(0)&&PlayerState.resolve(p)==100&&!p.isPotionActive(LightningKit.EFFECT),"Denied native mode consumed chakra or retained effect");
        check(PlayerState.data(p).getLong("artGlobal")==oldGlobal&&PlayerState.data(p).getCompoundTag("artCooldowns").getLong(LightningKit.KEYS[0])==oldReady,"Denied mode did not roll back its escrow deadlines");gate.reset();
        // An old native effect is not proof that the requested application was admitted.
        ready();p.addPotionEffect(new net.minecraft.potion.PotionEffect(LightningKit.EFFECT,1200,0,false,false));gate.denyMode=true;
        check(!cast(0)&&PlayerState.resolve(p)==100&&p.getActivePotionEffect(LightningKit.EFFECT).getDuration()==1200,"Denied application with an old effect consumed chakra");
        check(!LightningKit.state(p).hasKey("modeUntil")&&!LightningKit.state(p).hasKey("nextDrain"),"Denied old-effect activation retained reserved mode stamps");
        gate.reset();check(!cast(0)&&PlayerState.resolve(p)==100&&!LightningKit.state(p).hasKey("modeUntil"),"Unchanged stronger/longer effect counted as a fresh activation");
        ready();gate.throwMode=true;boolean threw=false;try{cast(0);}catch(IllegalStateException expected){threw=true;}
        check(threw&&PlayerState.resolve(p)==100&&!p.isPotionActive(LightningKit.EFFECT)&&!LightningKit.state(p).hasKey("modeUntil"),"Throwing native pre-admission callback retained charge or mode");
        gate.reset();check(cast(0)&&PlayerState.resolve(p)==80&&LightningKit.active(p),"Failed potion callback leaked admission/escrow context");
        mode();step(10);long due=LightningKit.state(p).getLong("nextDrain");
        village.vale.bridge.MoveCost.Ticket pending=village.vale.bridge.MoveCost.reserve(p,2);check(pending!=null,"Busy-upkeep fixture could not reserve a real native ticket");
        try{step(40);LightningKit.tick(p);check(LightningKit.active(p)&&LightningKit.state(p).getLong("nextDrain")==due&&PlayerState.resolve(p)==78,"Pending cast incorrectly ended or advanced Lightning upkeep");}
        finally{pending.close();}
        LightningKit.tick(p);check(LightningKit.active(p)&&PlayerState.resolve(p)==68&&LightningKit.state(p).getLong("nextDrain")==due+60,"Deferred Lightning upkeep lost elapsed periods or charged twice");
        mode();p.interactionManager.setGameType(GameType.SPECTATOR);
        try{LightningKit.tick(p);check(!LightningKit.active(p)&&!p.isPotionActive(LightningKit.EFFECT),"Spectator switch retained mode potion");}
        finally{p.interactionManager.setGameType(GameType.SURVIVAL);}
        mode();ItemStack helmet=p.getItemStackFromSlot(EntityEquipmentSlot.HEAD);p.setItemStackToSlot(EntityEquipmentSlot.HEAD,ItemStack.EMPTY);LightningKit.tick(p);
        check(!p.isPotionActive(LightningKit.EFFECT),"Unequipping matching armor retained mode");p.setItemStackToSlot(EntityEquipmentSlot.HEAD,helmet);
        for(int stage=1;stage<=3;stage++){
            erase();mode();EntityZombie t=zombie(0,2);gate.target=t;gate.stage=stage;float hp=t.getHealth();int chakra=PlayerState.resolve(p);
            oldGlobal=PlayerState.data(p).getLong("artGlobal");oldReady=PlayerState.data(p).getCompoundTag("artCooldowns").getLong(LightningKit.KEYS[1]);
            check(!cast(1)&&t.getHealth()==hp&&PlayerState.resolve(p)==chakra&&!t.isPotionActive(net.minecraft.init.MobEffects.SLOWNESS),"Canceled Lariat applied damage, control or cost at stage "+stage);
            check(PlayerState.data(p).getLong("artGlobal")==oldGlobal&&PlayerState.data(p).getCompoundTag("artCooldowns").getLong(LightningKit.KEYS[1])==oldReady,"Canceled Lariat did not restore escrow deadlines "+stage);
            gate.reset();
        }
        erase();mode();EntityZombie t=zombie(0,2);float hp=t.getHealth();check(cast(1)&&t.getHealth()<hp&&PlayerState.resolve(p)==68&&t.isPotionActive(net.minecraft.init.MobEffects.SLOWNESS),"Accepted Lariat did not settle native hit/control/cost");
        erase();mode();BlockPos wall=new BlockPos(p.posX,p.posY,p.posZ+1);IBlockState old=p.world.getBlockState(wall),oldUpper=p.world.getBlockState(wall.up());
        try{p.world.setBlockState(wall,Blocks.STONE.getDefaultState(),2);p.world.setBlockState(wall.up(),Blocks.STONE.getDefaultState(),2);int chakra=PlayerState.resolve(p);
            check(!cast(2)&&PlayerState.resolve(p)==chakra&&p.motionX==0&&p.motionZ==0,"Step accepted solid wall or charged failed movement");
        }finally{p.world.setBlockState(wall,old,2);p.world.setBlockState(wall.up(),oldUpper,2);}
        check(cast(2)&&PlayerState.resolve(p)==74&&p.motionZ>1&&p.velocityChanged,"Clear native Step did not move/settle cost");
        for(int canceled=0;canceled<4;canceled++){
            erase();mode();t=zombie(0,2);check(cast(3)&&PlayerState.resolve(p)==70,"Counter arming/cost failed");long counter=LightningKit.state(p).getLong("counterUntil");hp=t.getHealth();
            gate.target=p;gate.stage=canceled==0?3:0;if(canceled==3)p.setAbsorptionAmount(100);float health=p.getHealth();p.hurtResistantTime=0;
            p.attackEntityFrom(DamageSource.causeMobDamage(t),8);if(canceled==1)p.setHealth(health);LightningKitEvents.flush();
            if(canceled<2)check(t.getHealth()==hp&&LightningKit.state(p).getLong("counterUntil")==counter,"Canceled/restored incoming hit triggered counter");
            else check(t.getHealth()<hp&&!LightningKit.state(p).hasKey("counterUntil"),"Accepted incoming native hit did not trigger one counter");
            if(canceled==3)check(p.getHealth()==health&&p.getAbsorptionAmount()<100,"Absorption-only counter fixture damaged HP or missed absorption");
            gate.reset();
        }
        erase();LightningKit.end(p);place();check(LightningTrial.prepare(p,course),"Trial actors could not spawn in native courtyard");
        List<EntityLivingBase> challengers=new ArrayList<>();for(int i=0;i<3;i++){
            NBTTagCompound s=LightningKit.state(p);Entity actor=p.getServerWorld().getEntityFromUuid(s.getUniqueId("trialActor"+i));
            check(actor instanceof FieldRogue&&actor.isEntityAlive()&&LightningTrial.matches(p,(EntityLivingBase)actor),"Trial challenger lacks run/owner binding "+i);challengers.add((EntityLivingBase)actor);
        }
        check(!Missions.claim(p)&&!LightningKit.available(p,4),"Trial mastery was claimable before three kills");
        EntityLivingBase first=challengers.get(0);UUID owner=first.getEntityData().getUniqueId(LightningTrial.OWNER);long run=first.getEntityData().getLong(LightningTrial.RUN);
        first.getEntityData().setUniqueId(LightningTrial.OWNER,UUID.randomUUID());hp=first.getHealth();
        check(!first.attackEntityFrom(DamageSource.causePlayerDamage(p),1000)&&first.getHealth()==hp,"Another owner's challenger accepted damage");first.getEntityData().setUniqueId(LightningTrial.OWNER,owner);
        first.getEntityData().setLong(LightningTrial.RUN,run-1);check(!LightningTrial.matches(p,first),"Stale run challenger counted");first.getEntityData().setLong(LightningTrial.RUN,run);
        int progress=PlayerState.data(p).getInteger("missionProgress");
        for(EntityLivingBase actor:challengers){actor.hurtResistantTime=0;check(actor.attackEntityFrom(DamageSource.causePlayerDamage(p),1000),"Owner could not defeat native challenger");actor.deathTime=1;Missions.flushDeaths(p.getServer());
            check(PlayerState.data(p).getInteger("missionProgress")==++progress,"Trial death did not credit exact run once");Missions.flushDeaths(p.getServer());check(PlayerState.data(p).getInteger("missionProgress")==progress,"Trial death replay duplicated credit");}
        check(Missions.claim(p)&&LightningKit.state(p).getBoolean("mastered")&&LightningKit.available(p,4),"Three real trial kills did not unlock Guillotine Drop");
        int xp=PlayerState.data(p).getInteger("xp");check(!Missions.claim(p)&&PlayerState.data(p).getInteger("xp")==xp,"Trial claim replay awarded XP");
        check(PlayerState.data(p).getString("starterOrder").equals(starter)&&AcademyCampaign.state(p).getString("nature").equals(nature),"Mastery changed the original nature");
        LightningTrial.clear(p);for(EntityLivingBase actor:challengers)check(!actor.isEntityAlive()||actor.isDead,"Claim cleanup retained live trial actor");
        guillotine();witnesses();
    }
    private void guillotine(){
        erase();mode();p.onGround=false;int chakra=PlayerState.resolve(p);
        check(!cast(4)&&PlayerState.resolve(p)==chakra&&!LightningKit.state(p).hasKey("slamUntil"),"Guillotine launched without grounded native support");
        p.onGround=true;EntityZombie target=zombie(0,2);float hp=target.getHealth();double floor=p.posY;
        check(cast(4)&&p.motionY>=.72&&!p.onGround&&PlayerState.resolve(p)==55&&LightningKit.state(p).hasKey("slamUntil"),"Guillotine did not launch and charge exactly once");
        check(target.getHealth()==hp,"Guillotine damaged target before airborne/landing phase");
        p.setPosition(p.posX,floor+.8,p.posZ);p.onGround=false;step(3);LightningKit.tick(p);
        check(LightningKit.state(p).getBoolean("slamAirborne")&&target.getHealth()==hp,"Native airborne observation did not arm landing independently");
        p.setPosition(p.posX,floor,p.posZ);p.onGround=true;p.motionY=0;p.fallDistance=6;step(3);LightningKit.tick(p);
        check(target.getHealth()<hp&&!LightningKit.state(p).hasKey("slamUntil")&&p.fallDistance==0,"Grounded two-phase Guillotine landing did not damage and consume pending impact");
        check(PlayerState.resolve(p)==55,"Guillotine charged again on landing before upkeep boundary");
        hp=target.getHealth();LightningKit.tick(p);check(target.getHealth()==hp&&PlayerState.resolve(p)==55,"Repeated landing tick replayed damage or cost");
        erase();mode();target=zombie(0,2);hp=target.getHealth();check(cast(4),"Range-expiry fixture could not launch");
        p.setPosition(p.posX+13,p.posY+.8,p.posZ);p.onGround=false;step(3);LightningKit.tick(p);
        check(!LightningKit.state(p).hasKey("slamUntil")&&target.getHealth()==hp&&PlayerState.resolve(p)==55,"Out-of-range Guillotine retained or executed pending impact");
        erase();mode();target=zombie(0,2);hp=target.getHealth();check(cast(4),"Time-expiry fixture could not launch");
        p.setPosition(p.posX,p.posY+.8,p.posZ);p.onGround=false;step(3);LightningKit.tick(p);step(58);LightningKit.tick(p);
        check(!LightningKit.state(p).hasKey("slamUntil")&&target.getHealth()==hp,"Expired airborne Guillotine retained or executed pending impact");
        check(PlayerState.resolve(p)==43,"Expired Guillotine charged anything beyond original launch and three elapsed upkeep ticks");
        LightningKit.end(p);erase();place();
    }
    private EntityVillager villager(double x,double z){EntityVillager v=new EntityVillager(p.world);v.setNoAI(true);v.setPosition(p.posX+x,p.posY,p.posZ+z);
        check(p.world.spawnEntity(v),"Witness fixture spawn rejected");actors.add(v);return v;}
    private void witnesses(){
        LightningKit.end(p);place();EntityVillager visible=villager(-3,0),hidden=villager(0,5),far=villager(17,0),future=villager(4,0);
        NBTTagCompound opaque=new NBTTagCompound();opaque.setInteger("schema",99);opaque.setString("futureRecord","preserve-exactly");opaque.setLong("ready",0);future.getEntityData().setTag("ValePowerReaction",opaque.copy());
        List<BlockPos> wall=new ArrayList<>();List<IBlockState> previous=new ArrayList<>();BlockPos center=new BlockPos(p.posX,p.posY,p.posZ+2);
        try{
            for(int x=-1;x<=1;x++)for(int y=0;y<3;y++){BlockPos at=center.add(x,y,0);wall.add(at);previous.add(p.world.getBlockState(at));p.world.setBlockState(at,Blocks.STONE.getDefaultState(),2);}
            check(!hidden.canEntityBeSeen(p)&&visible.canEntityBeSeen(p),"Witness wall did not provide distinct native LOS");long observed=now();
            check(WorldReactions.power(p,"lariat",2)>=1,"Visible local witness did not react");
            NBTTagCompound memory=visible.getEntityData().getCompoundTag("ValePowerReaction");
            check(memory.getInteger("schema")==1&&memory.getString("kind").equals("lariat")&&memory.getInteger("intensity")==2
                &&memory.getLong("ready")==observed+200&&memory.getLong("expires")==observed+1200&&memory.hasUniqueId("witnessedPlayer")&&memory.getUniqueId("witnessedPlayer").equals(p.getUniqueID()),"Local witness memory lacks bounded owner/kind/timing");
            check(!hidden.getEntityData().hasKey("ValePowerReaction")&&!far.getEntityData().hasKey("ValePowerReaction"),"Occluded or distant villager gained power knowledge");
            check(future.getEntityData().getCompoundTag("ValePowerReaction").equals(opaque),"Future witness schema was overwritten");
            NBTTagCompound settled=memory.copy();WorldReactions.power(p,"guillotine_drop",3);
            check(visible.getEntityData().getCompoundTag("ValePowerReaction").equals(settled),"Immediate reaction spam bypassed local cooldown");
            step(199);WorldReactions.power(p,"guillotine_drop",3);check(visible.getEntityData().getCompoundTag("ValePowerReaction").equals(settled),"Witness cooldown reopened early");
            step(1);WorldReactions.power(p,"lightning_armor",1);memory=visible.getEntityData().getCompoundTag("ValePowerReaction");
            check(memory.getString("kind").equals("lightning_armor")&&memory.getLong("ready")==now()+200,"Witness cooldown did not reopen on exact native tick");
            check(future.getEntityData().getCompoundTag("ValePowerReaction").equals(opaque)&&!hidden.getEntityData().hasKey("ValePowerReaction")&&!far.getEntityData().hasKey("ValePowerReaction"),"Repeated reactions mutated future/hidden/distant witness state");
        }finally{for(int i=0;i<wall.size();i++)p.world.setBlockState(wall.get(i),previous.get(i),2);}
        erase();
    }
    public static final class Gate {
        EntityLivingBase target;int stage;boolean denyMode,throwMode;
        void reset(){target=null;stage=0;denyMode=false;throwMode=false;}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void attack(LivingAttackEvent e){if(e.getEntityLiving()==target&&stage==1)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void hurt(LivingHurtEvent e){if(e.getEntityLiving()==target&&stage==2)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void damage(LivingDamageEvent e){if(e.getEntityLiving()==target&&stage==3)e.setCanceled(true);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void potion(PotionEvent.PotionApplicableEvent e){if(denyMode&&e.getPotionEffect().getPotion()==LightningKit.EFFECT)e.setResult(Event.Result.DENY);}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void adding(PotionEvent.PotionAddedEvent e){if(throwMode&&e.getPotionEffect().getPotion()==LightningKit.EFFECT)throw new IllegalStateException("Intentional native potion pre-admission failure");}
    }
}
