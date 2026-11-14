package village.vale;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Original playable adaptation of Lightning Release Chakra Mode; server-owned actions. */
@Mod.EventBusSubscriber(modid=ValeOrders.ID)
public final class LightningKit {
    public static final String[] KEYS={"lightning_chakra_armor","lightning_lariat","lightning_step","lightning_counter","guillotine_drop","lightning_release"};
    public static final String[] TITLES={"Lightning Chakra Armor","Lariat","Lightning Step","Thunder Counter","Guillotine Drop","Release Lightning Armor"};
    public static final int[] COST={20,12,6,10,25,0}, COOLDOWN={40,4,3,5,12,0};
    public static Potion EFFECT;
    private static final Map<UUID,Attempt> CASTS=new HashMap<>();
    private static final ThreadLocal<PotionAdmission> ADMISSION=new ThreadLocal<>();
    private static final class PotionAdmission {
        final EntityPlayerMP player;final PotionEffect effect;PotionEvent.PotionApplicableEvent event;
        PotionAdmission(EntityPlayerMP p,PotionEffect effect){player=p;this.effect=effect;}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void applicable(PotionEvent.PotionApplicableEvent event){
        PotionAdmission a=ADMISSION.get();if(a!=null&&event.getEntityLiving()==a.player&&event.getPotionEffect()==a.effect)a.event=event;
    }
    private LightningKit(){}
    @SubscribeEvent public static void register(RegistryEvent.Register<Potion> event){
        EFFECT=new Potion(false,0x79cfff){}.setPotionName("effect.valeorders.lightning_chakra_armor");
        EFFECT.setRegistryName(ValeOrders.ID,"lightning_chakra_armor");
        EFFECT.registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED,"98c25121-f3bc-4f95-85c8-30c113000001",.35,2);
        EFFECT.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_DAMAGE,"98c25121-f3bc-4f95-85c8-30c113000002",4,0);
        EFFECT.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR,"98c25121-f3bc-4f95-85c8-30c113000003",4,0);
        event.getRegistry().register(EFFECT);
    }
    public static NBTTagCompound state(EntityPlayerMP p){NBTTagCompound root=PlayerState.data(p);if(!root.hasKey("LightningKit",10)){NBTTagCompound s=new NBTTagCompound();s.setInteger("schema",1);root.setTag("LightningKit",s);}return root.getCompoundTag("LightningKit");}
    public static boolean readable(NBTTagCompound s){return s.getInteger("schema")==1;}
    public static boolean known(EntityPlayerMP p){return readable(state(p))&&state(p).getBoolean("unlocked");}
    public static int index(String key){for(int i=0;i<KEYS.length;i++)if(KEYS[i].equals(key))return i;return -1;}
    public static boolean active(EntityPlayerMP p){return p.isEntityAlive()&&!p.isSpectator()&&!p.world.isRemote&&known(p)&&EFFECT!=null&&p.isPotionActive(EFFECT)&&state(p).getLong("modeUntil")>Adventures.now(p)&&Combat.fullSet(p)==Order.STORM&&Forms.active(p)<0;}
    public static boolean available(EntityPlayerMP p,int i){return known(p)&&(i!=4||state(p).getBoolean("mastered"))&&(i!=5||active(p));}
    public static String gate(EntityPlayerMP p,int i){
        if(i<0||i>=KEYS.length||!available(p,i))return "Learn this kit from Aki's Lightning Armor trial.";
        if(!p.isEntityAlive()||p.isSpectator())return "Use this technique while alive in play.";
        if(i==5)return "";
        if(Combat.fullSet(p)!=Order.STORM)return "Equip four matching issued Lightning/Storm armor pieces.";
        if(i==0){if(active(p))return "Lightning Armor is already active; choose Release to end it.";if(Forms.active(p)>=0)return "End your other form before using Lightning Armor.";}
        else if(!active(p))return "Activate Lightning Chakra Armor first.";
        if(i==4&&!grounded(p))return "Begin Guillotine Drop from solid ground with headroom.";
        return "";
    }
    private static boolean grounded(EntityPlayerMP p){return p.onGround&&!p.world.getCollisionBoxes(p,p.getEntityBoundingBox().offset(0,-.12,0)).isEmpty();}
    private static final class Attempt {
        final EntityPlayerMP p;final net.minecraft.world.World world;final int dimension,index;final NBTTagCompound account;boolean applied;village.vale.bridge.MoveCost.Ticket payment;
        Attempt(EntityPlayerMP p,int i){this.p=p;world=p.world;dimension=p.dimension;index=i;account=PlayerState.data(p);}
        void accept(){applied=true;if(payment!=null&&payment.pending())payment.commit();}
        boolean body(){return p.isEntityAlive()&&!p.isSpectator()&&p.world==world&&p.dimension==dimension&&PlayerState.data(p)==account&&p.getServer()!=null&&p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())==p;}
        boolean valid(){return p.isEntityAlive()&&!p.isSpectator()&&p.world==world&&p.dimension==dimension&&PlayerState.data(p)==account&&(payment==null||(payment.pending()?payment.valid():applied))&&body()&&!AbilityActions.carried(p,"valeorders:technique_codex").isEmpty()&&gate(p,index).isEmpty();}
    }
    public static boolean cast(EntityPlayerMP p,Arts.Art art){
        int i=index(art.key);String reason=gate(p,i);if(!reason.isEmpty())return Adventures.tell(p,reason);
        if(CASTS.containsKey(p.getUniqueID()))return false;
        long now=Adventures.now(p);NBTTagCompound account=PlayerState.data(p),cooldowns=account.getCompoundTag("artCooldowns");
        if(i!=5&&(now<account.getLong("artGlobal")||now<cooldowns.getLong(art.key)))return Adventures.tell(p,"This technique is cooling down.");
        long oldGlobal=account.getLong("artGlobal"),oldReady=cooldowns.getLong(art.key),reservedReady=now+COOLDOWN[i]*20L,reservedGlobal=now+10;
        Attempt attempt=new Attempt(p,i);CASTS.put(p.getUniqueID(),attempt);boolean reserved=false;
        try{
            attempt.payment=village.vale.bridge.MoveCost.reserve(p,COST[i]);
            if(attempt.payment==null)return Adventures.tell(p,village.vale.bridge.ResourceText.requirement(p,COST[i]));
            if(!attempt.valid())return false;
            if(i!=5){cooldowns.setLong(art.key,reservedReady);account.setTag("artCooldowns",cooldowns);account.setLong("artGlobal",reservedGlobal);reserved=true;}
            Adventures.save(p);
            if(!perform(attempt)&&!attempt.applied)return Adventures.tell(p,"No permitted contact or safe movement. Chakra kept.");
            return true;
        }finally{
            try{
                if(attempt.payment!=null){if(attempt.applied)attempt.payment.commit();else attempt.payment.refund();}
                if(attempt.applied&&PlayerState.data(p)==account&&p.world==attempt.world&&p.dimension==attempt.dimension){
                    NBTTagCompound live=PlayerState.data(p),cds=live.getCompoundTag("artCooldowns");cds.setLong(art.key,Math.max(cds.getLong(art.key),now+COOLDOWN[i]*20L));live.setTag("artCooldowns",cds);
                    if(i!=5){live.setLong("artGlobal",Math.max(live.getLong("artGlobal"),now+10));live.setLong("lastCombat",now);Missions.art(p);}
                    Adventures.save(p);HudNetwork.cast(p,art.key);Adventures.gesture(p);
                    p.sendStatusMessage(new TextComponentString(TITLES[i]+" | "+village.vale.bridge.ResourceText.balance(p)),true);
                }else if(!attempt.applied&&reserved){
                    if(i!=5){NBTTagCompound current=account.getCompoundTag("artCooldowns");if(current.getLong(art.key)==reservedReady)current.setLong(art.key,oldReady);if(account.getLong("artGlobal")==reservedGlobal)account.setLong("artGlobal",oldGlobal);}
                    if(PlayerState.data(p)==account)Adventures.save(p);
                }
            }finally{CASTS.remove(p.getUniqueID(),attempt);}
        }
    }
    private static boolean perform(Attempt a){
        if(!a.valid())return false;EntityPlayerMP p=a.p;NBTTagCompound s=state(p);long now=Adventures.now(p);
        switch(a.index){
            case 0:{
                String[] keys={"modeStarted","modeUntil","nextDrain"};long[] own={now,now+600,now+20},old=new long[3];boolean[] had=new boolean[3];
                for(int n=0;n<3;n++){had[n]=s.hasKey(keys[n]);old[n]=s.getLong(keys[n]);s.setLong(keys[n],own[n]);}
                PotionEffect previousEffect=p.getActivePotionEffect(EFFECT),effect=new PotionEffect(EFFECT,600,0,false,false);
                int oldDuration=previousEffect==null?0:previousEffect.getDuration(),oldAmplifier=previousEffect==null?-1:previousEffect.getAmplifier();
                PotionAdmission receipt=new PotionAdmission(p,effect),previous=ADMISSION.get();ADMISSION.set(receipt);
                try{p.addPotionEffect(effect);}
                finally{
                    if(previous==null)ADMISSION.remove();else ADMISSION.set(previous);
                    PotionEffect present=p.getActivePotionEffect(EFFECT);
                    boolean changed=present==effect||(present!=null&&present==previousEffect&&(present.getDuration()>oldDuration||present.getAmplifier()>oldAmplifier));
                    if(receipt.event!=null&&receipt.event.getResult()!=Event.Result.DENY&&changed)a.accept();
                    if(!a.applied)for(int n=0;n<3;n++)if(s.getLong(keys[n])==own[n]){if(had[n])s.setLong(keys[n],old[n]);else s.removeTag(keys[n]);}
                }
                if(!a.applied)return false;
                if(a.body()&&state(p)==s&&active(p)){burst(p,24);sound(p,1.2F);WorldReactions.power(p,"lightning_armor",2);}return true;
            }
            case 1:{
                boolean hit=false;for(EntityLivingBase target:targets(p,3.75,true,3)){
                    if(!a.valid())break;boolean applied=impact(a,target,12);hit=applied||hit;
                    if(applied&&a.valid()&&contact(p,target,3.75)){Arts.push(p,target,.85);if(!a.valid()||!contact(p,target,3.75))continue;target.addPotionEffect(new PotionEffect(net.minecraft.init.MobEffects.SLOWNESS,12,0));}
                }
                if(hit&&a.valid()){burst(p,18);sound(p,.8F);WorldReactions.power(p,"lariat",2);}return hit;
            }
            case 2:{
                Vec3d look=p.getLookVec();Vec3d horizontal=new Vec3d(look.x,0,look.z);if(horizontal.lengthSquared()<.001)return false;horizontal=horizontal.normalize();
                AxisAlignedBB next=p.getEntityBoundingBox().offset(horizontal.x*1.15,0,horizontal.z*1.15);
                if(!p.world.getWorldBorder().contains(next)||!p.world.isAreaLoaded(new BlockPos(next.minX,next.minY,next.minZ),new BlockPos(next.maxX,next.maxY,next.maxZ))||!p.world.getCollisionBoxes(p,next).isEmpty())return false;
                p.motionX=horizontal.x*1.15;p.motionZ=horizontal.z*1.15;p.motionY=Math.max(p.motionY,.10);p.velocityChanged=true;a.accept();burst(p,12);return true;
            }
            case 3:s.setLong("counterUntil",now+20);a.accept();burst(p,10);return true;
            case 4:
                if(!p.world.getCollisionBoxes(p,p.getEntityBoundingBox().offset(0,.6,0)).isEmpty())return false;
                s.setLong("slamUntil",now+60);s.setLong("slamStarted",now);s.setDouble("slamX",p.posX);s.setDouble("slamY",p.posY);s.setDouble("slamZ",p.posZ);s.setBoolean("slamAirborne",false);
                p.motionY=Math.max(.72,p.motionY);p.onGround=false;p.velocityChanged=true;p.fallDistance=0;a.accept();burst(p,16);return true;
            case 5:try{end(p);}finally{if(!s.hasKey("modeUntil"))a.accept();}return true;
            default:return false;
        }
    }
    private static boolean impact(Attempt a,EntityLivingBase target,float damage){
        float before=target.getHealth()+target.getAbsorptionAmount();
        try{return Combat.impact(target,DamageSource.causePlayerDamage(a.p),damage);}
        finally{if(target.getHealth()+target.getAbsorptionAmount()<before)a.accept();}
    }
    public static void tick(EntityPlayerMP p){
        NBTTagCompound s=state(p);if(!readable(s))return;long now=Adventures.now(p);
        if(s.hasKey("modeUntil")&&!active(p)){end(p);return;}
        if(!active(p))return;
        if(now<s.getLong("modeStarted")||now>=s.getLong("modeUntil")){end(p);return;}
        if(now>=s.getLong("nextDrain")){
            if(village.vale.bridge.MoveCost.busy(p))return; // A concurrent escrow is not chakra exhaustion.
            long due=s.getLong("nextDrain"),started=s.getLong("modeStarted"),until=s.getLong("modeUntil");
            int drains=(int)Math.min(30,1+(now-due)/20);int cost=drains*4;
            NBTTagCompound account=PlayerState.data(p);net.minecraft.world.World world=p.world;int dimension=p.dimension;
            village.vale.bridge.MoveCost.Ticket upkeep=village.vale.bridge.MoveCost.reserve(p,cost);
            if(upkeep==null){if(!village.vale.bridge.MoveCost.busy(p)&&sameMode(p,s,account,world,dimension,started,until))end(p);return;}
            try{
                if(!upkeep.valid()||!sameMode(p,s,account,world,dimension,started,until))return;
                boolean paid=upkeep.commit();
                if(!sameMode(p,s,account,world,dimension,started,until))return;
                if(!paid){end(p);return;}
                s.setLong("nextDrain",due+drains*20L);account.setLong("lastCombat",now);
            }finally{upkeep.close();}
        }
        if(p.ticksExisted%4==0)((WorldServer)p.world).spawnParticle(EnumParticleTypes.CRIT_MAGIC,p.posX,p.posY+1,p.posZ,3,.35,.7,.35,.04);
        if(s.hasKey("slamUntil")){
            double x=p.posX-s.getDouble("slamX"),z=p.posZ-s.getDouble("slamZ");
            if(now>s.getLong("slamUntil")||x*x+z*z>144||Math.abs(p.posY-s.getDouble("slamY"))>12){s.removeTag("slamUntil");return;}
            if(p.posY>s.getDouble("slamY")+.25)s.setBoolean("slamAirborne",true);
            if(s.getBoolean("slamAirborne")&&now-s.getLong("slamStarted")>=6&&grounded(p)){
                s.removeTag("slamUntil");p.fallDistance=0;
                net.minecraft.world.World arena=p.world;NBTTagCompound account=PlayerState.data(p);
                for(EntityLivingBase target:targets(p,4,false,6)){
                    if(p.world!=arena||PlayerState.data(p)!=account||state(p)!=s||!active(p))break;
                    if(Combat.impact(target,DamageSource.causePlayerDamage(p),18)&&p.world==arena&&active(p)&&contact(p,target,4))Arts.push(p,target,1.1);
                }
                if(p.world!=arena||PlayerState.data(p)!=account)return;
                for(int n=0;n<32;n++){double angle=n*Math.PI/16;((WorldServer)p.world).spawnParticle(EnumParticleTypes.CRIT_MAGIC,p.posX+Math.cos(angle)*3,p.posY+.15,p.posZ+Math.sin(angle)*3,1,0,0,0,.08);}
                sound(p,.55F);WorldReactions.power(p,"guillotine_drop",3);
            }
        }
    }
    private static boolean sameMode(EntityPlayerMP p,NBTTagCompound s,NBTTagCompound account,net.minecraft.world.World world,int dimension,long started,long until){
        return p.world==world&&p.dimension==dimension&&PlayerState.data(p)==account&&state(p)==s&&s.getLong("modeStarted")==started&&s.getLong("modeUntil")==until&&active(p);
    }
    public static void end(EntityPlayerMP p){
        NBTTagCompound s=state(p);if(!readable(s))return;
        for(String key:new String[]{"modeUntil","modeStarted","nextDrain","counterUntil","slamUntil","slamStarted","slamAirborne"})s.removeTag(key);
        if(EFFECT!=null)p.removePotionEffect(EFFECT);HudNetwork.dirty(p);
    }
    private static boolean contact(EntityPlayerMP p,EntityLivingBase target,double range){
        if(target.world!=p.world||!target.isEntityAlive()||p.getDistanceSq(target)>range*range||!OwnedSkills.allowed(p,target))return false;
        Vec3d start=p.getPositionEyes(1),end=target.getPositionEyes(1);
        BlockPos lo=new BlockPos(Math.min(start.x,end.x),Math.min(start.y,end.y),Math.min(start.z,end.z)),hi=new BlockPos(Math.max(start.x,end.x),Math.max(start.y,end.y),Math.max(start.z,end.z));
        return p.world.getWorldBorder().contains(new BlockPos(end))&&p.world.isAreaLoaded(lo,hi)&&p.canEntityBeSeen(target);
    }
    private static List<EntityLivingBase> targets(EntityPlayerMP p,double range,boolean frontal,int limit){
        List<EntityLivingBase> candidates=p.world.getEntitiesWithinAABB(EntityLivingBase.class,p.getEntityBoundingBox().grow(range));candidates.sort(Comparator.comparingDouble(p::getDistanceSq));List<EntityLivingBase> result=new ArrayList<>();
        for(EntityLivingBase target:candidates){if(target==p||!contact(p,target,range))continue;Vec3d delta=target.getPositionVector().subtract(p.getPositionVector());if(frontal&&delta.lengthSquared()>.001&&p.getLookVec().dotProduct(delta.normalize())<.25)continue;result.add(target);if(result.size()>=limit)break;}return result;
    }
    public static void burst(EntityPlayerMP p,int count){((WorldServer)p.world).spawnParticle(EnumParticleTypes.CRIT_MAGIC,p.posX,p.posY+1,p.posZ,count,.55,.85,.55,.12);}
    private static void sound(EntityPlayerMP p,float pitch){p.world.playSound(null,p.posX,p.posY,p.posZ,SoundEvents.ENTITY_LIGHTNING_THUNDER,SoundCategory.PLAYERS,.18F,pitch);}
}
