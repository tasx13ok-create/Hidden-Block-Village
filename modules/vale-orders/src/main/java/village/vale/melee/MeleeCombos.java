package village.vale.melee;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import village.vale.ArsenalAccess;
import village.vale.Gear;
import village.vale.PlayerState;
import village.vale.ValeOrders;

/** Native left-click combos. Only a successful, cooled, issued melee contact can advance. */
@Mod.EventBusSubscriber(modid=ValeOrders.ID)
public final class MeleeCombos {
    private static final int LIMIT=256;
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final ThreadLocal<Boolean> EFFECT=ThreadLocal.withInitial(()->false);
    private MeleeCombos(){}
    private static final class Session {
        final EntityPlayerMP owner;final MeleeComboRules rules=new MeleeComboRules();
        Intent intent;Hit hit;ItemStack equipped;long touched;
        Session(EntityPlayerMP owner){this.owner=owner;rules.restoreRecovery(PlayerState.data(owner).getLong("meleeReady"));}
    }
    private static final class Intent {
        final AttackEntityEvent event;final EntityLivingBase target;final ItemStack stack;
        final String serial,kind;final long at;final int step;
        Intent(AttackEntityEvent event,EntityLivingBase target,ItemStack stack,long at,int step){
            this.event=event;this.target=target;this.stack=stack;this.at=at;this.step=step;
            serial=stack.getTagCompound().getString("valeSerial");kind=((Gear.WardBlade)stack.getItem()).kind;
        }
    }
    private static final class Hit {
        final Intent intent;final LivingAttackEvent attack;final float before;
        LivingHurtEvent hurt;LivingDamageEvent damage;
        Hit(Intent intent,LivingAttackEvent attack){this.intent=intent;this.attack=attack;
            before=intent.target.getHealth()+intent.target.getAbsorptionAmount();}
    }
    private static boolean proof(EntityPlayerMP p,ItemStack stack){
        return p.isEntityAlive()&&!p.isSpectator()&&!p.capabilities.isCreativeMode&&!stack.isEmpty()
            &&stack.getItem() instanceof Gear.WardBlade&&ArsenalAccess.issued(p,stack)
            &&ArsenalAccess.fullSet(p)==((Gear.WardBlade)stack.getItem()).order();
    }
    private static boolean close(EntityPlayerMP p,EntityLivingBase target){
        if(!ArsenalAccess.allowed(p,target)||!p.canEntityBeSeen(target))return false;
        Vec3d eye=p.getPositionEyes(1);AxisAlignedBB b=target.getEntityBoundingBox();
        double x=Math.max(b.minX,Math.min(eye.x,b.maxX)),y=Math.max(b.minY,Math.min(eye.y,b.maxY)),z=Math.max(b.minZ,Math.min(eye.z,b.maxZ));
        return eye.squareDistanceTo(x,y,z)<=9.61;
    }
    private static Session session(EntityPlayerMP p){
        Session s=SESSIONS.get(p.getUniqueID());
        if(s!=null&&s.owner!=p){SESSIONS.remove(p.getUniqueID());s=null;}
        if(s==null&&SESSIONS.size()<LIMIT){s=new Session(p);SESSIONS.put(p.getUniqueID(),s);}
        return s;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void intent(AttackEntityEvent event){
        if(EFFECT.get()||!(event.getEntityPlayer() instanceof EntityPlayerMP))return;
        EntityPlayerMP p=(EntityPlayerMP)event.getEntityPlayer();Session s=session(p);if(s==null)return;
        long now=ArsenalAccess.now(p);s.touched=now;
        // A second packet cannot replace an unresolved native damage transaction.
        if(s.hit!=null)return;
        s.intent=null;ItemStack stack=p.getHeldItemMainhand();
        if(event.isCanceled()||!(event.getTarget() instanceof EntityLivingBase)||!proof(p,stack)
            ||p.getCooledAttackStrength(0)<.9f||!close(p,(EntityLivingBase)event.getTarget())){s.rules.interrupt();return;}
        EntityLivingBase target=(EntityLivingBase)event.getTarget();
        if(s.equipped!=stack)s.rules.interrupt();s.equipped=stack;
        int step=s.rules.preview(stack.getTagCompound().getString("valeSerial"),target.getUniqueID().toString(),now);
        if(step>0)s.intent=new Intent(event,target,stack,now,step);
    }
    private static Session source(net.minecraft.util.DamageSource source,EntityLivingBase target){
        if(EFFECT.get()||!(source.getTrueSource() instanceof EntityPlayerMP)||source.getImmediateSource()!=source.getTrueSource()
            ||!"player".equals(source.damageType))return null;
        Session s=SESSIONS.get(source.getTrueSource().getUniqueID());
        return s!=null&&s.owner==source.getTrueSource()?s:null;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void attack(LivingAttackEvent event){
        Session s=source(event.getSource(),event.getEntityLiving());if(s==null||s.hit!=null||s.intent==null)return;
        Intent i=s.intent;
        if(i.target!=event.getEntityLiving())return; // Vanilla sweep victims never consume/advance the chain.
        s.intent=null;long now=ArsenalAccess.now(s.owner);
        // Vanilla resets its swing clock between AttackEntityEvent and damage. An
        // ability invoked recursively inside an attack-intent listener has not.
        if(!event.isCanceled()&&!i.event.isCanceled()&&s.owner.getCooledAttackStrength(0)<=.1f
            &&now>=i.at&&now-i.at<=1&&s.owner.getHeldItemMainhand()==i.stack
            &&proof(s.owner,i.stack)&&close(s.owner,i.target))s.hit=new Hit(i,event);
    }
    @SubscribeEvent(priority=EventPriority.LOW,receiveCanceled=true)
    public static void hurt(LivingHurtEvent event){
        Session s=source(event.getSource(),event.getEntityLiving());if(s==null||s.hit==null||s.hit.intent.target!=event.getEntityLiving())return;
        Hit hit=s.hit;if(event.getSource()!=hit.attack.getSource())return;hit.hurt=event;
        if(event.isCanceled()||hit.attack.isCanceled()||hit.intent.event.isCanceled()||hit.intent.step!=3||event.getAmount()<=0
            ||s.owner.getHeldItemMainhand()!=hit.intent.stack||!proof(s.owner,hit.intent.stack)
            ||!ArsenalAccess.allowed(s.owner,hit.intent.target))return;
        float scale=1.1f;String kind=hit.intent.kind;
        if("saber".equals(kind))scale=1.4f;
        else if("axe".equals(kind))scale=1.65f;
        else if("dagger".equals(kind))scale=rear(s.owner,hit.intent.target)?1.8f:1.2f;
        else if("glaive".equals(kind))scale=1.2f;
        float boosted=event.getAmount()*scale;
        if(Float.isFinite(boosted))event.setAmount(boosted);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void damage(LivingDamageEvent event){
        Session s=source(event.getSource(),event.getEntityLiving());
        if(s!=null&&s.hit!=null&&s.hit.intent.target==event.getEntityLiving()&&event.getSource()==s.hit.attack.getSource())s.hit.damage=event;
    }
    private static boolean rear(EntityPlayerMP p,EntityLivingBase target){
        Vec3d to=p.getPositionVector().subtract(target.getPositionVector());
        return to.lengthSquared()>.001&&target.getLookVec().dotProduct(to.normalize())<-.35;
    }
    /** Flush after native damage unwinds. Public for the private native acceptance harness. */
    public static void tickPlayer(EntityPlayerMP p){
        Session s=SESSIONS.get(p.getUniqueID());if(s==null||s.owner!=p)return;
        long now=ArsenalAccess.now(p);
        if(now<s.touched||now-s.touched>200){clear(p);return;}
        if(!proof(p,p.getHeldItemMainhand())||s.equipped!=p.getHeldItemMainhand()){
            s.rules.interrupt();s.intent=null;s.hit=null;s.equipped=p.getHeldItemMainhand();return;
        }
        if(s.intent!=null&&(s.intent.event.isCanceled()||now<s.intent.at||now-s.intent.at>1)){
            s.intent=null;s.rules.interrupt();
        }
        Hit hit=s.hit;if(hit==null)return;s.hit=null;Intent i=hit.intent;
        boolean accepted=now>=i.at&&now-i.at<=2&&!i.event.isCanceled()&&!hit.attack.isCanceled()
            &&hit.hurt!=null&&!hit.hurt.isCanceled()&&hit.hurt.getAmount()>0
            &&(hit.damage==null||(!hit.damage.isCanceled()&&hit.damage.getAmount()>=0))
            &&i.stack==p.getHeldItemMainhand()&&p.world==i.target.world
            &&!p.isOnSameTeam(i.target)&&(!i.target.isEntityAlive()||ArsenalAccess.allowed(p,i.target))
            &&hit.before>i.target.getHealth()+i.target.getAbsorptionAmount()+.0001f;
        if(!accepted){s.rules.interrupt();return;}
        // Dead targets may finish a chain but can never receive secondary control effects.
        int step=s.rules.accept(i.serial,i.target.getUniqueID().toString(),i.at);
        if(step==0)return;
        // Recovery is gameplay state: persist it before gain/status/render callbacks.
        if(step==3){PlayerState.data(p).setLong("meleeReady",s.rules.readyAt());EFFECT.set(true);try{finish(p,i.target,i.kind,i.stack);}finally{EFFECT.set(false);}}
        feedback(p,i.target,i.kind,step);
    }
    private static boolean finisherContext(EntityPlayerMP p,ItemStack stack,net.minecraft.world.World world,net.minecraft.nbt.NBTTagCompound account){
        return p.world==world&&PlayerState.data(p)==account&&p.getHeldItemMainhand()==stack&&proof(p,stack)
            &&p.getServer()!=null&&p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())==p;
    }
    private static void finish(EntityPlayerMP p,EntityLivingBase target,String kind,ItemStack stack){
        net.minecraft.world.World world=p.world;net.minecraft.nbt.NBTTagCompound account=PlayerState.data(p);
        if(!finisherContext(p,stack,world,account))return;
        if("saber".equals(kind))p.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE,24,0));
        else if("dagger".equals(kind))p.addPotionEffect(new PotionEffect(MobEffects.SPEED,30,1));
        else if("glaive".equals(kind)){
            int struck=0;for(EntityLivingBase other:ArsenalAccess.targets(p,3,true,8)){
                if(!finisherContext(p,stack,world,account))break;
                if(other==target||other.world!=world||!ArsenalAccess.allowed(p,other)||!p.canEntityBeSeen(other))continue;
                if(ArsenalAccess.hit(p,other,4)){struck++;if(struck==2)break;}
            }
        }else if("staff".equals(kind)){
            village.vale.bridge.MoveCost.restore(p,4);
            if(finisherContext(p,stack,world,account)&&target.world==world&&ArsenalAccess.allowed(p,target))target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,40,0));
        }else if("spear".equals(kind)&&ArsenalAccess.allowed(p,target)){
            double dx=target.posX-p.posX,dz=target.posZ-p.posZ;
            if(dx*dx+dz*dz>.001)target.knockBack(p,.85f,-dx,-dz);
        }else if("axe".equals(kind)&&ArsenalAccess.allowed(p,target))target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,20,0));
    }
    private static void feedback(EntityPlayerMP p,EntityLivingBase target,String kind,int step){
        String name="saber".equals(kind)?"Pressure Cut":"spear".equals(kind)?"Line Break":"dagger".equals(kind)?"Shadow Flank"
            :"glaive".equals(kind)?"Crescent Sweep":"staff".equals(kind)?"Chakra Check":"Splitting Impact";
        p.sendStatusMessage(new TextComponentString(step==3?"§6"+name+"! §7Recovering 4s":"§f"+kind+" §b"+step+"/3 §7• clean hit"),true);
        p.world.playSound(null,target.posX,target.posY,target.posZ,step==3?SoundEvents.ENTITY_PLAYER_ATTACK_CRIT:SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,SoundCategory.PLAYERS,.65f,step==3?.8f:1.1f+step*.1f);
        if(p.world instanceof WorldServer)((WorldServer)p.world).spawnParticle(step==3?EnumParticleTypes.CRIT:EnumParticleTypes.SWEEP_ATTACK,
            target.posX,target.posY+target.height*.55,target.posZ,step==3?12:1,.3,.3,.3,.05);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        for(Session s:new ArrayList<>(SESSIONS.values()))tickPlayer(s.owner);
    }
    public static void clear(EntityPlayerMP p){if(p!=null)SESSIONS.remove(p.getUniqueID());}
    public static int chain(EntityPlayerMP p){Session s=SESSIONS.get(p.getUniqueID());return s==null?0:s.rules.count();}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.player instanceof EntityPlayerMP)clear((EntityPlayerMP)e.player);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.player instanceof EntityPlayerMP)clear((EntityPlayerMP)e.player);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntityLiving() instanceof EntityPlayerMP)clear((EntityPlayerMP)e.getEntityLiving());}
}
