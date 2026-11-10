package village.vale;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.attributes.*;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.player.*;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import village.vale.armor.*;

/** Server-owned passives for independent ward armor. No client message supplies targets or damage. */
@Mod.EventBusSubscriber(modid="valeorders")
public final class OriginalArmorRuntime {
    static final UUID HEALTH=UUID.fromString("689cfe40-766a-4f86-bb7d-dcc968cfca22"),ANCHOR=UUID.fromString("145fd1ce-77b5-4989-9106-4539f08bb534");
    static final Map<UUID,State> STATES=new HashMap<>();
    static final Map<UUID,Signal> SIGNALS=new HashMap<>();
    static final List<Signal> PENDING=new ArrayList<>();
    static long serverEpoch;
    public static SimpleNetworkWrapper CHANNEL;
    private OriginalArmorRuntime(){}
    public static void initialize(){
        if(CHANNEL!=null)return;CHANNEL=NetworkRegistry.INSTANCE.newSimpleChannel("vale_origarmor");
        CHANNEL.registerMessage(OriginalArmorPacket.Handler.class,OriginalArmorPacket.class,0,Side.CLIENT);
    }
    static NBTTagCompound saved(EntityPlayer p){NBTTagCompound data=PlayerState.data(p);if(!data.hasKey("originalArmor",10))data.setTag("originalArmor",new NBTTagCompound());return data.getCompoundTag("originalArmor");}
    static State state(EntityPlayer p){
        State s=STATES.get(p.getUniqueID());if(s==null){s=new State();NBTTagCompound data=saved(p);s.stamina=data.hasKey("stamina",3)?Math.max(0,Math.min(2000,data.getInteger("stamina"))):2000;s.ownsFlight=data.getBoolean("ownedFlight");s.dimension=p.dimension;s.lastTick=p.world.getTotalWorldTime();s.restUntil=s.lastTick+Math.max(0,Math.min(40,data.getLong("restUntil")-s.lastTick));STATES.put(p.getUniqueID(),s);}return s;
    }
    public static boolean allowed(EntityPlayer owner,EntityLivingBase target){
        if(owner==null||target==null||!owner.isEntityAlive()||!target.isEntityAlive()||owner==target||owner.world!=target.world||owner.isOnSameTeam(target))return false;
        if(target instanceof IEntityOwnable&&((IEntityOwnable)target).getOwnerId()!=null)return false;
        if(target instanceof AbstractHorse&&((AbstractHorse)target).isTame())return false;
        if(target instanceof EntityPlayer){EntityPlayer other=(EntityPlayer)target;if(other.isSpectator()||other.capabilities.isCreativeMode)return false;}
        return OwnedSkills.allowed(owner,target);
    }
    static boolean eligible(EntityPlayer owner,EntityLivingBase target){return allowed(owner,target)&&owner.getDistanceSq(target)<=BalanceEnvelope.RANGE*BalanceEnvelope.RANGE&&owner.canEntityBeSeen(target);}
    static boolean ownSource(DamageSource source){return source==null||"vale_original_resonance".equals(source.getDamageType())||"thorns".equals(source.getDamageType());}
    static boolean interested(EntityLivingBase target,DamageSource source){Entity attacker=source.getTrueSource();return target instanceof EntityPlayer&&OriginalArmorRegistry.wearing((EntityPlayer)target)||attacker instanceof EntityPlayer&&OriginalArmorRegistry.wearing((EntityPlayer)attacker);}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void attack(LivingAttackEvent event){
        if(event.getEntityLiving().world.isRemote||ownSource(event.getSource())||!interested(event.getEntityLiving(),event.getSource()))return;
        Signal signal=new Signal(event.getEntityLiving(),event.getSource());signal.attack=event;SIGNALS.put(event.getEntityLiving().getUniqueID(),signal);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void hurt(LivingHurtEvent event){
        if(event.getEntityLiving().world.isRemote||ownSource(event.getSource())||!interested(event.getEntityLiving(),event.getSource()))return;
        Signal signal=SIGNALS.get(event.getEntityLiving().getUniqueID());
        if(signal==null||signal.source!=event.getSource()){signal=new Signal(event.getEntityLiving(),event.getSource());SIGNALS.put(event.getEntityLiving().getUniqueID(),signal);}
        signal.hurt=event;if(!signal.queued){signal.queued=true;PENDING.add(signal);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void damage(LivingDamageEvent event){Signal signal=SIGNALS.get(event.getEntityLiving().getUniqueID());if(signal!=null&&signal.source==event.getSource())signal.damage=event;}
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        serverEpoch++;
        for(Signal signal:new ArrayList<>(PENDING)){
            EntityLivingBase victim=signal.target.get();
            PENDING.remove(signal);
            if(victim==null||signal.canceled()||signal.hurt==null||signal.hurt.getAmount()<=0||signal.before<=victim.getHealth()+victim.getAbsorptionAmount()||serverEpoch-signal.epoch>3)continue;
            Entity source=signal.source.getTrueSource();
            if(source instanceof EntityPlayerMP)engage((EntityPlayerMP)source,victim,true);
            if(victim instanceof EntityPlayerMP&&source instanceof EntityLivingBase)engage((EntityPlayerMP)victim,(EntityLivingBase)source,false);
        }
        Iterator<Signal> it=SIGNALS.values().iterator();while(it.hasNext()){Signal s=it.next();if(s.target.get()==null||serverEpoch-s.epoch>3)it.remove();}
        // Event bursts cannot retain an unbounded graph, even if an external mod stops a world clock.
        if(PENDING.size()>512)PENDING.subList(0,PENDING.size()-512).clear();if(SIGNALS.size()>1024)SIGNALS.clear();
    }
    static void engage(EntityPlayerMP p,EntityLivingBase target,boolean outgoing){
        ArmorProfile profile=OriginalArmorRegistry.full(p);if(profile==null||!eligible(p,target))return;State s=state(p);long now=p.world.getTotalWorldTime();
        if(s.profile!=profile){resetCombat(s);s.profile=profile;}
        if(outgoing||s.target==null||now-s.lastOutgoing>40){if(!target.getUniqueID().equals(s.target))s.nextAssist=Math.max(s.nextAssist,now+10);s.target=target.getUniqueID();s.targetId=target.getEntityId();s.targetUntil=now+100;}
        if(outgoing)s.lastOutgoing=now;
        if(!outgoing&&profile.role==ArmorProfile.Role.REACTIVE&&s.reactiveRemaining==0&&now>=s.reactiveReady){s.reactiveRemaining=3;s.reactiveUntil=now+61;s.reactiveReady=now+80;s.nextAssist=now+1;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void playerTick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&!event.player.world.isRemote&&event.player instanceof EntityPlayerMP)tick((EntityPlayerMP)event.player);}
    static void tick(EntityPlayerMP p){
        State s=state(p);long now=p.world.getTotalWorldTime();ArmorProfile profile=p.isEntityAlive()&&!p.isSpectator()?OriginalArmorRegistry.full(p):null;
        if(s.dimension!=p.dimension||now<s.lastTick){resetCombat(s);s.restUntil=now+40;s.dimension=p.dimension;release(p,s);}
        s.lastTick=now;if(s.profile!=profile){resetCombat(s);s.profile=profile;s.momentum=0;s.syncAt=0;attributes(p,profile);}
        if(p.ticksExisted%10==0){for(ItemStack stack:p.inventory.mainInventory)hide(stack);for(ItemStack stack:p.inventory.armorInventory)hide(stack);for(ItemStack stack:p.inventory.offHandInventory)hide(stack);}
        flight(p,s,profile,now);save(p,s);
        if(p.ticksExisted%20==0)attributes(p,profile);
        if(profile!=null&&profile.role==ArmorProfile.Role.MOMENTUM)s.momentum=p.onGround&&p.isSprinting()?Math.min(40,s.momentum+1):Math.max(0,s.momentum-1);
        if(profile!=null&&s.target!=null&&now>=s.nextAssist){
            Entity target=p.world.getEntityByID(s.targetId);
            if(!(target instanceof EntityLivingBase)||!s.target.equals(target.getUniqueID())||now>s.targetUntil)resetCombat(s);
            else if(eligible(p,(EntityLivingBase)target))assist(p,(EntityLivingBase)target,s,profile,now);
        }
        if(now>=s.syncAt){sync(p,s);s.syncAt=now+10;}
        if(profile!=null&&profile.role==ArmorProfile.Role.FLIGHT&&p.ticksExisted%20==0)p.sendStatusMessage(new TextComponentString("Auric Skimmer | Flight stamina "+(s.stamina/20)+"/100"),true);
    }
    static void flight(EntityPlayerMP p,State s,ArmorProfile profile,long now){
        if(profile==null||profile.role!=ArmorProfile.Role.FLIGHT||p.capabilities.isCreativeMode||p.isSpectator()){release(p,s);return;}
        boolean powered=s.ownsFlight&&p.capabilities.isFlying;int before=s.stamina;
        s.stamina=BalanceEnvelope.stamina(s.stamina,true,powered,p.onGround,now<s.restUntil);if(powered)s.restUntil=now+40;
        if(s.stamina==0&&before>0){release(p,s);s.restUntil=now+40;}
        if(!s.ownsFlight&&!p.capabilities.allowFlying&&BalanceEnvelope.canStart(s.stamina)){s.ownsFlight=true;p.capabilities.allowFlying=true;p.sendPlayerAbilities();}
        if(s.ownsFlight&&!p.capabilities.allowFlying)s.ownsFlight=false;
        s.flying=s.ownsFlight&&p.capabilities.isFlying;
        if(!p.onGround&&!p.capabilities.isFlying&&p.motionY<-.18){p.motionY=-.18;p.fallDistance=0;p.velocityChanged=true;}
    }
    static void release(EntityPlayerMP p,State s){if(s.ownsFlight){if(!p.capabilities.isCreativeMode&&!p.isSpectator()){p.capabilities.allowFlying=false;p.capabilities.isFlying=false;}s.ownsFlight=false;p.sendPlayerAbilities();}s.flying=false;}
    static void attributes(EntityPlayerMP p,ArmorProfile profile){
        change(p.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH),HEALTH,"Original ward vitality",profile==null?0:profile.maximumHealth-20);
        double anchor=profile==null?0:profile.role==ArmorProfile.Role.ANCHOR&&p.onGround?.25:profile.role==ArmorProfile.Role.UNDERTOW&&p.onGround?.5:profile.role==ArmorProfile.Role.VEIL&&p.isInWater()?.4:0;
        change(p.getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE),ANCHOR,"Original ward footing",anchor);
        if(profile!=null&&profile.role==ArmorProfile.Role.CLEANSE){p.removePotionEffect(MobEffects.POISON);p.removePotionEffect(MobEffects.WITHER);}
        if(p.getHealth()>p.getMaxHealth())p.setHealth(p.getMaxHealth());
    }
    static void change(IAttributeInstance attribute,UUID id,String title,double amount){AttributeModifier old=attribute.getModifier(id);if(old!=null&&Math.abs(old.getAmount()-amount)>.001){attribute.removeModifier(old);old=null;}if(old==null&&amount!=0)attribute.applyModifier(new AttributeModifier(id,title,amount,0).setSaved(false));}
    static boolean assist(EntityPlayerMP p,EntityLivingBase target,State s,ArmorProfile profile,long now){
        if(now<s.nextAssist||OriginalArmorRegistry.full(p)!=profile||!eligible(p,target)||profile.role==ArmorProfile.Role.FLIGHT)return false;
        if(profile.role==ArmorProfile.Role.REACTIVE&&(s.reactiveRemaining<=0||now>=s.reactiveUntil)){s.reactiveRemaining=0;return false;}
        if(profile.role==ArmorProfile.Role.MOMENTUM&&s.momentum<20)return false;
        if(target.hurtResistantTime>target.maxHurtResistantTime/2){s.nextAssist=now+Math.max(1,target.hurtResistantTime-target.maxHurtResistantTime/2);return false;}
        BalanceEnvelope envelope=BalanceEnvelope.forRole(profile.role);s.nextAssist=now+envelope.cooldownTicks;
        if(profile.role==ArmorProfile.Role.REACTIVE)s.reactiveRemaining--;
        if(!Combat.impact(target,new EntityDamageSource("vale_original_resonance",p),envelope.rawDamage))return false;
        switch(profile.role){
            case ANCHOR:target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,30,0));target.knockBack(p,.3f,p.posX-target.posX,p.posZ-target.posZ);break;
            case UNDERTOW:target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,35,0));target.knockBack(p,.25f,p.posX-target.posX,p.posZ-target.posZ);break;
            case HEAT:if(!target.isImmuneToFire())target.setFire(2);break;
            case MOMENTUM:s.momentum=0;target.knockBack(p,.6f,p.posX-target.posX,p.posZ-target.posZ);break;
            case CLEANSE:target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,40,0));target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,30,0));break;
            case VEIL:target.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS,25,0));target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,35,0));break;
            case REACTIVE:target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,20,0));break;
            default:break;
        }
        s.sequence++;s.assists++;sync(p,s);return true;
    }
    static void sync(EntityPlayerMP p,State s){if(CHANNEL==null||s.profile==null&&!s.published)return;OriginalArmorPacket packet=new OriginalArmorPacket(p,s.profile,s.stamina,s.flying,s.sequence);CHANNEL.sendTo(packet,p);CHANNEL.sendToAllAround(packet,new NetworkRegistry.TargetPoint(p.dimension,p.posX,p.posY,p.posZ,48));s.published=s.profile!=null;}
    static void resetCombat(State s){s.target=null;s.targetId=-1;s.targetUntil=0;s.reactiveRemaining=0;}
    static void save(EntityPlayer p,State s){NBTTagCompound data=saved(p);data.setInteger("stamina",s.stamina);data.setBoolean("ownedFlight",s.ownsFlight);data.setLong("restUntil",s.restUntil);}
    public static void hide(ItemStack stack){if(stack.isEmpty()||!(stack.getItem() instanceof OriginalArmorRegistry.Armor))return;if(!stack.hasTagCompound())stack.setTagCompound(new NBTTagCompound());stack.getTagCompound().setInteger("HideFlags",stack.getTagCompound().getInteger("HideFlags")|1);}
    @SubscribeEvent public static void dropped(EntityJoinWorldEvent e){if(!e.getWorld().isRemote&&e.getEntity() instanceof EntityItem)hide(((EntityItem)e.getEntity()).getItem());}
    @SubscribeEvent public static void logout(PlayerLoggedOutEvent e){if(e.player instanceof EntityPlayerMP){State s=STATES.remove(e.player.getUniqueID());if(s!=null){release((EntityPlayerMP)e.player,s);attributes((EntityPlayerMP)e.player,null);save(e.player,s);}}}
    @SubscribeEvent public static void dimension(PlayerChangedDimensionEvent e){if(e.player instanceof EntityPlayerMP){State s=state(e.player);release((EntityPlayerMP)e.player,s);resetCombat(s);s.dimension=e.toDim;s.restUntil=e.player.world.getTotalWorldTime()+40;s.syncAt=0;}}
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void clone(PlayerEvent.Clone e){NBTTagCompound data=saved(e.getOriginal()).copy();PlayerState.data(e.getEntityPlayer()).setTag("originalArmor",data);STATES.remove(e.getEntityPlayer().getUniqueID());}
    @SubscribeEvent public static void unload(net.minecraftforge.event.world.WorldEvent.Unload e){if(!e.getWorld().isRemote&&e.getWorld().provider.getDimension()==0){STATES.clear();SIGNALS.clear();PENDING.clear();}}
    static final class State{ArmorProfile profile;int stamina=2000,dimension=Integer.MIN_VALUE,momentum,reactiveRemaining,targetId=-1,sequence,assists;long lastTick,restUntil,nextAssist,targetUntil,lastOutgoing=-1000,reactiveUntil,reactiveReady,syncAt;boolean ownsFlight,flying,published;UUID target;}
    static final class Signal{
        final WeakReference<EntityLivingBase> target;final DamageSource source;final float before;final long epoch;LivingAttackEvent attack;LivingHurtEvent hurt;LivingDamageEvent damage;boolean queued;
        Signal(EntityLivingBase target,DamageSource source){this.target=new WeakReference<>(target);this.source=source;this.before=target.getHealth()+target.getAbsorptionAmount();this.epoch=serverEpoch;}
        boolean canceled(){return attack!=null&&attack.isCanceled()||hurt!=null&&hurt.isCanceled()||damage!=null&&damage.isCanceled();}
    }
}
