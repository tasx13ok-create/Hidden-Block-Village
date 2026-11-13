package village.vale.arsenal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import village.vale.ArsenalAccess;
import village.vale.PlayerState;

/** All specials enter through native main-hand use and the existing Core authority. */
public final class ArsenalLogic {
    private static final Map<UUID,Swing> SWINGS=new HashMap<>();
    private static final Map<UUID,List<Effect>> EFFECTS=new HashMap<>();
    private ArsenalLogic() {}
    static NBTTagCompound state(EntityPlayerMP p) {
        NBTTagCompound root=PlayerState.data(p);
        if(!root.hasKey("arsenal",10))root.setTag("arsenal",new NBTTagCompound());
        return root.getCompoundTag("arsenal");
    }
    static boolean proof(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec) {
        return p!=null&&p.isEntityAlive()&&!p.isSpectator()&&!p.world.isRemote
                &&!(p instanceof net.minecraftforge.common.util.FakePlayer)&&stack!=null&&!stack.isEmpty()
                &&stack.getCount()==1&&stack.getItem() instanceof ArsenalBlade
                &&((ArsenalBlade)stack.getItem()).spec==spec&&ArsenalAccess.issued(p,stack)&&ArsenalAccess.fullSet(p)==spec.order;
    }
    /** A detached issuance snapshot is insufficient once the real weapon is stowed. */
    static boolean heldProof(EntityPlayerMP p,ItemStack snapshot,ArsenalSpec spec) {
        if(p==null||snapshot==null||snapshot.isEmpty())return false;
        ItemStack held=p.getHeldItemMainhand();
        return !held.isEmpty()&&held.getItem()==snapshot.getItem()&&ItemStack.areItemStackTagsEqual(held,snapshot)&&proof(p,held,spec);
    }
    public static boolean ready(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec,boolean explain) {
        boolean ok=ArsenalMath.ready(!p.world.isRemote,p.isEntityAlive(),p.isSpectator(),p.getHeldItemMainhand()==stack,
                proof(p,stack,spec),ArsenalAccess.fullSet(p)==spec.order,p.getCooldownTracker().hasCooldown(stack.getItem())||state(p).getBoolean("busy"),
                ArsenalAccess.now(p),ArsenalCooldowns.readyAt(state(p),spec),PlayerState.resolve(p),spec.cost);
        if(!ok&&explain)tell(p,"Specialist technique needs your issued matching set, resolve and recovery.");
        return ok;
    }
    static void commit(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec) {
        long time=ArsenalAccess.now(p);
        ArsenalCooldowns.record(state(p),spec,time);
        PlayerState.data(p).setLong("lastCombat",time);
        for(Item item:Arsenal.items().values())if(!p.getCooldownTracker().hasCooldown(item))p.getCooldownTracker().setCooldown(item,ArsenalMath.SHARED_RECOVERY);
        // Native cooldown suppresses right-click before the item can handle a lunge.
        // Talons use the same persistent deadline for new locks; active locks need use.
        p.getCooldownTracker().setCooldown(stack.getItem(),spec.role==ArsenalSpec.Role.TALONS?0:spec.cooldown);
        stack.damageItem(1,p);
        state(p).removeTag("busy");
        ArsenalAccess.gesture(p);ArsenalAccess.save(p);
        tell(p,spec.title+" / "+description(spec.role));
    }
    public static void release(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec,int ticks) {
        if(spec.role==ArsenalSpec.Role.TALONS||!ArsenalMath.released(ticks,spec.charge)||!ready(p,stack,spec,false))return;
        // Reserve once before any native callback. Failed/cancelled casts refund it;
        // callbacks cannot recursively cast again before this transaction closes.
        state(p).setBoolean("busy",true);
        PlayerState.setResolve(p,PlayerState.resolve(p)-spec.cost);
        float power=ArsenalMath.charge(ticks);
        boolean used=false;
        try {
        EntityLivingBase target;
        switch(spec.role) {
            case RETURN: used=fire(p,stack,spec,ArsenalShot.RETURN,power,0);break;
            case MARK: used=fire(p,stack,spec,ArsenalShot.MARK,power,0);break;
            case DRAIN_BOLT: used=fire(p,stack,spec,ArsenalShot.DRAIN,power,0);break;
            case GUIDED: used=fire(p,stack,spec,ArsenalShot.GUIDED,power,0);break;
            case BIND: used=fire(p,stack,spec,ArsenalShot.BIND,power,0);break;
            case VENOM: used=fire(p,stack,spec,ArsenalShot.VENOM,power,0);break;
            case MIST: used=fire(p,stack,spec,ArsenalShot.MIST,power,0);break;
            case FIRE:
                if(shotCount(p)<=ArsenalMath.MAX_SHOTS-3) {
                    for(int i=-1;i<=1;i++)used=fire(p,stack,spec,ArsenalShot.FIRE,power,i*10)||used;
                }
                break;
            case EMITTER: used=field(p,stack,spec,p.getPositionVector().add(0,.8,0),ArsenalField.EMITTER,power);break;
            case TRAP:
                RayTraceResult floor=p.world.rayTraceBlocks(p.getPositionEyes(1),p.getPositionEyes(1).add(p.getLookVec().scale(6)),false,true,false);
                if(floor!=null&&floor.typeOfHit==RayTraceResult.Type.BLOCK&&floor.sideHit==net.minecraft.util.EnumFacing.UP)
                    used=field(p,stack,spec,floor.hitVec.add(0,.08,0),ArsenalField.TRAP,power);
                break;
            case WIND:
                if(p.isSneaking()) {guard(p,spec);used=true;}
                else {
                    float stored=Math.min(4,state(p).getFloat("counter"));
                    if(ArsenalAccess.now(p)>state(p).getLong("counterUntil"))stored=0;
                    used=cone(p,stack,spec,8,5,4+power*3+stored,true,false);
                    if(used)state(p).removeTag("counter");
                }
                break;
            case GUARD: guard(p,spec);used=true;break;
            case MEND:
                if(p.getHealth()<p.getMaxHealth()||p.isPotionActive(MobEffects.SLOWNESS)||p.isPotionActive(MobEffects.WEAKNESS)||p.isPotionActive(MobEffects.POISON)) {
                    p.heal(3);p.removePotionEffect(MobEffects.SLOWNESS);p.removePotionEffect(MobEffects.WEAKNESS);p.removePotionEffect(MobEffects.POISON);
                    PlayerState.setResolve(p,Math.min(100,PlayerState.resolve(p)+3));guard(p,spec);used=true;
                }
                break;
            case SLAM:
                if(p.onGround)used=cone(p,stack,spec,4,5,7+power*3,true,true);
                break;
            case SONIC:
                used=p.isSneaking()?fire(p,stack,spec,ArsenalShot.GRAPPLE,power,0):cone(p,stack,spec,10,5,3+power*3,false,false);
                break;
            case EXTEND:
                target=ArsenalAccess.aimedTarget(p,8);
                if(target!=null) {
                    if(p.isSneaking()) {
                        // A bounded snare field is the original prison analogue; it never places blocks.
                        used=field(p,stack,spec,target.getPositionVector(),ArsenalField.SNARE,power);
                    } else if(hit(p,target,7+4*power)) {push(p,target,.6);used=true;}
                }
                break;
            case CHAIN:
                target=ArsenalAccess.aimedTarget(p,10);
                if(target!=null&&hit(p,target,5+power*3)) {
                    used=true;int count=0;
                    for(EntityLivingBase next:near(p,target.getPositionVector(),3,4)) {
                        if(next==target||count>=2)continue;
                        if(hit(p,next,4)) {line(p,target.getPositionVector().add(0,.8,0),next.getPositionVector().add(0,.8,0),EnumParticleTypes.CRIT_MAGIC);WeaponPulse.arc(p,spec,target.getPositionVector().add(0,.8,0),next.getPositionVector().add(0,.8,0));count++;}
                    }
                }
                break;
            case OVERDRIVE:
                p.addPotionEffect(new PotionEffect(MobEffects.STRENGTH,60,0));
                p.addPotionEffect(new PotionEffect(MobEffects.SPEED,60,0));
                p.addExhaustion(3);state(p).setLong("overdriveRecovery",ArsenalAccess.now(p)+60);used=true;break;
            case SWEEP:
                state(p).setLong("sweepUntil",ArsenalAccess.now(p)+100);used=true;break;
            default:
                target=ArsenalAccess.aimedTarget(p,spec.role==ArsenalSpec.Role.FINISH?7:4.5);
                if(target!=null) {
                    float damage=spec.role==ArsenalSpec.Role.FINISH?ArsenalMath.finishDamage(target.getHealth(),target.getMaxHealth(),power):6+4*power;
                    if(hit(p,target,damage)) {
                        used=true;
                        switch(spec.role) {
                            case DRAIN: drain(p,target,8);break;
                            case RIME: target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,80,1));break;
                            case SHOCK: target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,40,2));target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,40,0));break;
                            case RESTORE: stack.setItemDamage(Math.max(0,stack.getItemDamage()-12));break;
                            case BREAK:
                                ItemStack shield=target.getActiveItemStack();
                                if(!shield.isEmpty()&&shield.getItem().isShield(shield,target)) {
                                    if(target instanceof EntityPlayerMP)((EntityPlayerMP)target).getCooldownTracker().setCooldown(shield.getItem(),60);
                                    target.resetActiveHand();
                                }
                                break;
                            case BURST: addEffect(p,target,stack,spec,Effect.BURST,20);break;
                            default:break;
                        }
                    }
                }
        }
        if(used) {commit(p,stack,spec);chargeCue(p,spec,40);WeaponPulse.cast(p,spec,power);}
        else tell(p,"No permitted effect: check aim, range, ground or your active field limit.");
        } finally {
            state(p).removeTag("busy");
            if(!used)PlayerState.setResolve(p,Math.min(100,PlayerState.resolve(p)+spec.cost));
        }
    }
    static boolean hit(EntityPlayerMP p,EntityLivingBase target,float damage) { return ArsenalAccess.hit(p,target,Math.min(16,damage)); }
    static void guard(EntityPlayerMP p,ArsenalSpec spec) {
        state(p).setLong("guardUntil",ArsenalAccess.now(p)+40);
        state(p).setString("guardKey",spec.key);
        state(p).setFloat("counter",0);state(p).setLong("counterUntil",ArsenalAccess.now(p)+160);
    }
    static float protect(EntityPlayerMP p,float amount) {
        if(!Float.isFinite(amount)||amount<=0||ArsenalAccess.now(p)>state(p).getLong("guardUntil"))return amount;
        ItemStack stack=p.getHeldItemMainhand();
        if(!(stack.getItem() instanceof ArsenalBlade))return amount;
        ArsenalSpec spec=((ArsenalBlade)stack.getItem()).spec;
        if(!spec.key.equals(state(p).getString("guardKey"))||!proof(p,stack,spec))return amount;
        float saved=Math.min(8,amount*.45f);
        state(p).setFloat("counter",Math.min(4,state(p).getFloat("counter")+saved*.25f));
        return Math.max(0,amount-saved);
    }
    static boolean cone(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec,double range,int limit,float damage,boolean push,boolean radial) {
        boolean used=false;
        for(EntityLivingBase target:near(p,p.getPositionVector(),range,limit)) {
            Vec3d delta=target.getPositionVector().subtract(p.getPositionVector()).normalize();
            if(!radial&&p.getLookVec().dotProduct(delta)<.35)continue;
            if(hit(p,target,damage)) {
                used=true;if(push)push(p,target,.5);
                if(spec.role==ArsenalSpec.Role.SONIC)target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,60,0));
            }
        }
        return used;
    }
    static List<EntityLivingBase> near(EntityPlayerMP p,Vec3d center,double radius,int limit) {
        List<EntityLivingBase> found=new ArrayList<>();
        if(radius<=0||radius>10||limit<1||limit>8||!p.world.isBlockLoaded(new BlockPos(center)))return found;
        int checked=0;
        for(EntityLivingBase e:p.world.getEntitiesWithinAABB(EntityLivingBase.class,new AxisAlignedBB(center.x-radius,center.y-radius,center.z-radius,center.x+radius,center.y+radius,center.z+radius))) {
            if(checked++>=64)break;
            if(e.getPositionVector().squareDistanceTo(center)<=radius*radius&&p.getDistanceSq(e)<=ArsenalMath.RANGE*ArsenalMath.RANGE
                    &&ArsenalAccess.allowed(p,e)&&p.canEntityBeSeen(e)
                    &&p.world.rayTraceBlocks(center.add(0,.6,0),e.getPositionEyes(1),false,true,false)==null)found.add(e);
        }
        found.sort(Comparator.comparingDouble(e->e.getPositionVector().squareDistanceTo(center)));
        if(found.size()>limit)return new ArrayList<>(found.subList(0,limit));
        return found;
    }
    static void push(EntityPlayerMP p,EntityLivingBase target,double strength) {
        Vec3d d=target.getPositionVector().subtract(p.getPositionVector()).normalize();
        target.addVelocity(d.x*strength,.1,d.z*strength);target.velocityChanged=true;
    }
    static int shotCount(EntityPlayerMP p) {
        int count=0;
        for(ArsenalShot e:p.world.getEntitiesWithinAABB(ArsenalShot.class,p.getEntityBoundingBox().grow(25)))if(e.ownedBy(p))count++;
        return count;
    }
    static int fieldCount(EntityPlayerMP p) {
        int count=0;
        for(ArsenalField e:p.world.getEntitiesWithinAABB(ArsenalField.class,p.getEntityBoundingBox().grow(25)))if(e.ownedBy(p))count++;
        return count;
    }
    static boolean fire(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec,int mode,float power,float yaw) {
        if(shotCount(p)>=ArsenalMath.MAX_SHOTS)return false;
        ArsenalShot shot=new ArsenalShot(p,stack,spec,mode,power,yaw);
        boolean spawned=p.world.spawnEntity(shot);
        if(spawned&&mode==ArsenalShot.RETURN)state(p).setUniqueId("flight",shot.getUniqueID());
        return spawned;
    }
    static boolean field(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec,Vec3d center,int mode,float power) {
        if(fieldCount(p)>=ArsenalMath.MAX_FIELDS||!p.world.isBlockLoaded(new BlockPos(center)))return false;
        return p.world.spawnEntity(new ArsenalField(p,stack,spec,center,mode,power));
    }
    public static boolean inFlight(EntityPlayerMP p,ItemStack stack) {
        if(!state(p).hasUniqueId("flight"))return false;
        Entity entity=((WorldServer)p.world).getEntityFromUuid(state(p).getUniqueId("flight"));
        if(!(entity instanceof ArsenalShot)||entity.isDead||!((ArsenalShot)entity).ownedBy(p)) {state(p).removeTag("flightMost");state(p).removeTag("flightLeast");return false;}
        return ItemStack.areItemsEqualIgnoreDurability(((ArsenalShot)entity).proofStack(),stack)
                &&ItemStack.areItemStackTagsEqual(((ArsenalShot)entity).proofStack(),stack);
    }
    public static boolean recall(EntityPlayerMP p,ItemStack stack) {
        if(!inFlight(p,stack))return false;
        ArsenalShot e=(ArsenalShot)((WorldServer)p.world).getEntityFromUuid(state(p).getUniqueId("flight"));
        if(!proof(p,stack,ArsenalSpec.RETURNING_SICKLE))return false;
        e.recall();return true;
    }
    static void mark(EntityPlayerMP p,Vec3d at) {
        state(p).setDouble("markX",at.x);state(p).setDouble("markY",at.y);state(p).setDouble("markZ",at.z);
        state(p).setInteger("markDimension",p.dimension);state(p).setLong("markUntil",ArsenalAccess.now(p)+200);
        tell(p,"Waymark set for 10 seconds. Sneak + use to travel.");
    }
    public static boolean travel(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec) {
        NBTTagCompound s=state(p);
        if(!ready(p,stack,spec,false)||!s.hasKey("markUntil",4)||ArsenalAccess.now(p)>s.getLong("markUntil")||s.getInteger("markDimension")!=p.dimension)return false;
        Vec3d at=new Vec3d(s.getDouble("markX"),s.getDouble("markY"),s.getDouble("markZ"));
        if(!safe(p,at))return tell(p,"Waymark is outside safe travel range or blocked.");
        // Reserve before Forge teleport callbacks, just as release does before damage callbacks.
        // This also prevents an event subscriber re-entering the same unconsumed marker.
        s.setBoolean("busy",true);
        PlayerState.setResolve(p,PlayerState.resolve(p)-spec.cost);
        boolean used=false;
        try {
            EnderTeleportEvent e=new EnderTeleportEvent(p,at.x,at.y,at.z,0);
            if(MinecraftForge.EVENT_BUS.post(e))return false;
            Vec3d destination=new Vec3d(e.getTargetX(),e.getTargetY(),e.getTargetZ());
            if(p.getHeldItemMainhand()!=stack||!proof(p,stack,spec)||!safe(p,destination))return false;
            p.connection.setPlayerLocation(destination.x,destination.y,destination.z,p.rotationYaw,p.rotationPitch);
            p.fallDistance=0;s.removeTag("markUntil");used=true;commit(p,stack,spec);return true;
        } finally {
            s.removeTag("busy");
            if(!used)PlayerState.setResolve(p,Math.min(100,PlayerState.resolve(p)+spec.cost));
        }
    }
    static boolean safe(EntityPlayerMP p,Vec3d at) {
        if(!Double.isFinite(at.x)||!Double.isFinite(at.y)||!Double.isFinite(at.z)||at.y<1||at.y>p.world.getActualHeight()-2
                ||at.squareDistanceTo(p.getPositionVector())>20*20||!p.world.isBlockLoaded(new BlockPos(at)))return false;
        AxisAlignedBB box=p.getEntityBoundingBox().offset(at.subtract(p.getPositionVector()));
        return p.world.getWorldBorder().contains(new BlockPos(at))&&p.world.getCollisionBoxes(p,box).isEmpty()
                &&!p.world.containsAnyLiquid(box)&&!p.world.getCollisionBoxes(p,box.offset(0,-.3,0)).isEmpty();
    }
    static void drain(EntityPlayerMP p,EntityLivingBase target,int maximum) {
        int gain=2;
        if(target instanceof EntityPlayerMP) {
            EntityPlayerMP other=(EntityPlayerMP)target;
            gain=Math.min(maximum,PlayerState.resolve(other));PlayerState.setResolve(other,PlayerState.resolve(other)-gain);
        }
        if(gain>0) {PlayerState.setResolve(p,Math.min(100,PlayerState.resolve(p)+gain));p.heal(1);}
        target.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS,60,0));
    }
    static void addEffect(EntityPlayerMP p,EntityLivingBase target,ItemStack proof,ArsenalSpec spec,int mode,int duration) {
        List<Effect> list=EFFECTS.computeIfAbsent(p.getUniqueID(),k->new ArrayList<>());
        list.removeIf(e->e.target.equals(target.getUniqueID())&&e.mode==mode);
        if(list.size()<8)list.add(new Effect(target.getUniqueID(),proof.copy(),spec,mode,duration,ArsenalAccess.now(p),p.dimension));
    }
    static void beginSwing(EntityPlayerMP p,ItemStack stack,EntityLivingBase target) {
        if(!(stack.getItem() instanceof ArsenalBlade))return;
        ArsenalSpec spec=((ArsenalBlade)stack.getItem()).spec;
        if(proof(p,stack,spec)&&ArsenalAccess.allowed(p,target))SWINGS.put(p.getUniqueID(),new Swing(target,stack,ArsenalAccess.now(p),spec));
    }
    static void meleeHit(EntityPlayerMP p,ItemStack stack,EntityLivingBase target) {
        Swing swing=SWINGS.remove(p.getUniqueID());
        if(swing==null||swing.time!=ArsenalAccess.now(p)||swing.target!=target||swing.stack!=stack||p.getHeldItemMainhand()!=stack
                ||!proof(p,stack,swing.spec)||(target.isEntityAlive()&&!ArsenalAccess.allowed(p,target))
                ||swing.attack==null||swing.hurt==null||swing.attack.isCanceled()||swing.hurt.isCanceled()
                ||(swing.damage!=null&&swing.damage.isCanceled())||!(target.getHealth()+target.getAbsorptionAmount()<swing.before))return;
        if(swing.spec.role==ArsenalSpec.Role.TALONS)ClawCombat.confirmedHit(p,stack,target);
        if(swing.spec.role==ArsenalSpec.Role.RESTORE)stack.setItemDamage(Math.max(0,stack.getItemDamage()-2));
        boolean sweeping=swing.spec.role==ArsenalSpec.Role.SWEEP&&state(p).getLong("sweepUntil")>=ArsenalAccess.now(p);
        WeaponPulse.swing(p,swing.spec,sweeping);
        if(swing.spec.role==ArsenalSpec.Role.SWEEP&&state(p).getLong("sweepUntil")>=ArsenalAccess.now(p)
                &&state(p).getLong("sweepNext")<=ArsenalAccess.now(p)) {
            state(p).setLong("sweepNext",ArsenalAccess.now(p)+16);
            int count=0;
            for(EntityLivingBase next:near(p,p.getPositionVector(),3.5,5)) {
                if(next==target||count>=2)continue;
                if(p.getLookVec().dotProduct(next.getPositionVector().subtract(p.getPositionVector()).normalize())>.1&&hit(p,next,4)) {push(p,next,.25);count++;}
            }
        }
    }
    private static Swing observed(EntityLivingBase target,DamageSource source) {
        if(!(source.getTrueSource() instanceof EntityPlayerMP)||source.getImmediateSource()!=source.getTrueSource())return null;
        EntityPlayerMP p=(EntityPlayerMP)source.getTrueSource();Swing swing=SWINGS.get(p.getUniqueID());
        return swing!=null&&swing.target==target&&swing.time==ArsenalAccess.now(p)?swing:null;
    }
    static void observe(LivingAttackEvent event) {
        Swing swing=observed(event.getEntityLiving(),event.getSource());
        if(swing!=null&&swing.attack==null) {swing.source=event.getSource();swing.attack=event;}
    }
    static void observe(LivingHurtEvent event) {
        Swing swing=observed(event.getEntityLiving(),event.getSource());
        if(swing==null||swing.source!=event.getSource())return;
        swing.hurt=event;
        EntityPlayerMP p=(EntityPlayerMP)event.getSource().getTrueSource();
        if(!event.isCanceled()&&swing.spec.role==ArsenalSpec.Role.TALONS&&p.getHeldItemMainhand()==swing.stack
                &&proof(p,swing.stack,swing.spec)&&ArsenalAccess.allowed(p,event.getEntityLiving())&&ClawCombat.rear(p,event.getEntityLiving()))
            event.setAmount(ClawRules.nativeRearDamage(event.getAmount()));
    }
    static void observe(LivingDamageEvent event) {
        Swing swing=observed(event.getEntityLiving(),event.getSource());
        if(swing!=null&&swing.source==event.getSource())swing.damage=event;
    }
    static void tick(EntityPlayerMP p) {
        long now=ArsenalAccess.now(p);
        ClawCombat.tick(p);
        Swing swing=SWINGS.get(p.getUniqueID());if(swing!=null&&swing.time<now)SWINGS.remove(p.getUniqueID());
        if(state(p).getLong("overdriveRecovery")>0&&now>=state(p).getLong("overdriveRecovery")) {
            state(p).removeTag("overdriveRecovery");p.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,30,0));
        }
        List<Effect> list=EFFECTS.get(p.getUniqueID());if(list==null)return;
        Iterator<Effect> it=list.iterator();
        while(it.hasNext()) {
            Effect effect=it.next();Entity entity=((WorldServer)p.world).getEntityFromUuid(effect.target);
            if(!(entity instanceof EntityLivingBase)||p.dimension!=effect.dimension||!heldProof(p,effect.stack,effect.spec)) {it.remove();continue;}
            EntityLivingBase target=(EntityLivingBase)entity;
            if(!ArsenalAccess.allowed(p,target)||!p.canEntityBeSeen(target)||p.getDistanceSq(target)>24*24||now>effect.until) {it.remove();continue;}
            if(effect.mode==Effect.TETHER) {
                double distance=p.getDistance(target);
                if(distance>ArsenalMath.TETHER_RANGE) {it.remove();continue;}
                Vec3d d=p.getPositionVector().subtract(target.getPositionVector()).normalize().scale(ArsenalMath.pull(distance));
                target.addVelocity(d.x,Math.max(-.1,Math.min(.1,d.y)),d.z);target.velocityChanged=true;
            } else if(now>=effect.next) {
                effect.next=now+20;
                if(effect.mode==Effect.BURST) {
                    for(EntityLivingBase e:near(p,target.getPositionVector(),2.5,4))hit(p,e,5);
                    particles(p,target.getPositionVector().add(0,.5,0),EnumParticleTypes.EXPLOSION_NORMAL,8);it.remove();
                } else if(!hit(p,target,effect.mode==Effect.VENOM?1.5f:2f))it.remove();
                else particles(p,target.getPositionVector().add(0,.7,0),effect.mode==Effect.VENOM?EnumParticleTypes.SPELL_MOB:EnumParticleTypes.FLAME,3);
            }
        }
        if(list.isEmpty())EFFECTS.remove(p.getUniqueID());
    }
    static void clear(EntityPlayerMP p) {
        SWINGS.remove(p.getUniqueID());EFFECTS.remove(p.getUniqueID());ClawCombat.clear(p);
        NBTTagCompound s=state(p);
        // Cooldown/resolve remain persistent; travel, modes and guards never survive
        // death, dimension changes, logout or login into a fresh server process.
        ArsenalCooldowns.clearTransient(s);
        // The native tracker is only a client-visible mirror; the persisted
        // per-weapon deadline remains authoritative after a new player instance.
        long now=ArsenalAccess.now(p);
        for(ArsenalSpec spec:ArsenalSpec.values()) {
            Item item=Arsenal.items().get(spec.key);
            int remaining=ArsenalCooldowns.remaining(s,spec,now);
            if(item!=null&&remaining>0)p.getCooldownTracker().setCooldown(item,remaining);
        }
        for(ArsenalShot shot:p.world.getEntitiesWithinAABB(ArsenalShot.class,p.getEntityBoundingBox().grow(25)))if(shot.ownedBy(p))shot.setDead();
        for(ArsenalField field:p.world.getEntitiesWithinAABB(ArsenalField.class,p.getEntityBoundingBox().grow(25)))if(field.ownedBy(p))field.setDead();
    }
    public static void chargeCue(EntityPlayerMP p,ArsenalSpec spec,int ticks) {
        particles(p,p.getPositionEyes(1).add(p.getLookVec().scale(.7)),EnumParticleTypes.CRIT_MAGIC,3+ticks/10);
        if(ticks==16||ticks==40)p.world.playSound(null,p.posX,p.posY,p.posZ,SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,SoundCategory.PLAYERS,.12f,.8f+ArsenalMath.charge(ticks)*.5f);
    }
    static void particles(EntityPlayerMP p,Vec3d at,EnumParticleTypes kind,int count) {
        if(p.world instanceof WorldServer)((WorldServer)p.world).spawnParticle(kind,at.x,at.y,at.z,Math.min(16,count),.18,.2,.18,.01);
    }
    static void line(EntityPlayerMP p,Vec3d a,Vec3d b,EnumParticleTypes type) {
        for(int i=0;i<8;i++)particles(p,a.add(b.subtract(a).scale(i/8d)),type,1);
    }
    private static boolean tell(EntityPlayerMP p,String text) {p.sendStatusMessage(new TextComponentString(text),true);return false;}
    public static String description(ArsenalSpec.Role role) {
        switch(role) {
            case WIND:return "wind cone / sneak: counter guard";case RETURN:return "returning throw";case MARK:return "temporary travel marker";
            case DRAIN:return "draining strike";case CHAIN:return "bounded chain arc";case SHOCK:return "shock strike";case SLAM:return "ground shock";
            case BREAK:return "breaker strike";case RESTORE:return "restore edge";case DRAIN_BOLT:return "charged draining bolt";case FIRE:return "three-bolt ember volley";
            case BURST:return "delayed cinder strike";case RIME:return "slowing pin";case BIND:return "piercing tether";case EXTEND:return "extending strike / sneak: snare";
            case MEND:return "cleanse and mend guard";case GUIDED:return "guided flux shot";case EMITTER:return "three-wave needle emitter";
            case VENOM:return "venom needle";case MIST:return "persistent mist";case FINISH:return "bounded prism finisher";case GUARD:return "timed crescent guard";
            case OVERDRIVE:return "brief overdrive with recovery";case SONIC:return "sonic cone / sneak: grapple";case SWEEP:return "temporary sweeping mode";
            case TALONS:return "five-block lock / one-second lunge / accumulating bleed";
            default:return "short-lived proximity charge";
        }
    }
    static final class Swing {
        final EntityLivingBase target;final ItemStack stack;final long time;final float before;final ArsenalSpec spec;
        DamageSource source;LivingAttackEvent attack;LivingHurtEvent hurt;LivingDamageEvent damage;
        Swing(EntityLivingBase target,ItemStack stack,long time,ArsenalSpec spec) {
            this.target=target;this.stack=stack;this.time=time;this.spec=spec;before=target.getHealth()+target.getAbsorptionAmount();
        }
    }
    static final class Effect {
        static final int BURN=0,VENOM=1,TETHER=2,BURST=3;
        final UUID target;final ItemStack stack;final ArsenalSpec spec;final int mode,dimension;final long until;long next;
        Effect(UUID target,ItemStack stack,ArsenalSpec spec,int mode,int duration,long now,int dimension) {
            this.target=target;this.stack=stack;this.spec=spec;this.mode=mode;this.dimension=dimension;until=now+Math.min(80,duration);next=now+(mode==BURST?20:20);
        }
    }
}
