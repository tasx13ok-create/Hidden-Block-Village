package village.vale.nature;

import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import village.vale.*;

/** Original starter adaptations. Five different decisions, using native protection/observed effects. */
public final class NatureMoves {
    private static final Map<UUID,Attempt> ACTIVE=new HashMap<>();
    private NatureMoves(){}
    public static boolean handles(Arts.Art a){return a!=null&&a.shape==0&&(a.order==Order.EMBER||a.order==Order.CURRENT||a.order==Order.FLINT||a.order==Order.GALE||a.order==Order.STORM);}
    static final class Attempt {
        final EntityPlayerMP p;final World world;final Arts.Art art;final NBTTagCompound account;final boolean carried;
        boolean applied;village.vale.bridge.MoveCost.Ticket payment;
        void accept(){applied=true;if(payment!=null&&payment.pending())payment.commit();}
        Attempt(EntityPlayerMP p,Arts.Art art,boolean carried){this.p=p;this.world=p.world;this.art=art;account=PlayerState.data(p);this.carried=carried;}
        boolean valid(){
            if(!p.isEntityAlive()||p.isSpectator()||p.world!=world||world.isRemote||PlayerState.data(p)!=account||PlayerState.level(p)<art.level||Combat.fullSet(p)==null||(payment!=null&&(payment.pending()?!payment.valid():!applied))||p.getServer()==null||p.getServer().getPlayerList().getPlayerByUUID(p.getUniqueID())!=p)return false;
            if(!art.order.key.equals(account.getString("starterOrder"))&&!account.getBoolean("study_"+art.order.key))return false;
            if(!carried)return "valeorders:technique_codex".equals(Gear.key(p.getHeldItemMainhand()));
            for(ItemStack stack:p.inventory.mainInventory)if("valeorders:technique_codex".equals(Gear.key(stack)))return true;
            return "valeorders:technique_codex".equals(Gear.key(p.getHeldItemOffhand()));
        }
    }
    public static boolean cast(EntityPlayerMP p,Arts.Art art,boolean carried){
        if(!handles(art)||p.world.isRemote||ACTIVE.containsKey(p.getUniqueID()))return false;
        Attempt attempt=new Attempt(p,art,carried);if(!attempt.valid())return false;
        NBTTagCompound root=attempt.account,cds=root.getCompoundTag("artCooldowns");long now=Adventures.now(p),oldOwn=cds.getLong(art.key),oldGlobal=root.getLong("artGlobal");
        if(now<oldOwn||now<oldGlobal)return Adventures.tell(p,"This jutsu is recovering.");
        int cost=Forms.active(p)==2?Math.max(1,art.cost*3/4):art.cost;
        long own=now+art.cooldown*20L,global=now+20;
        ACTIVE.put(p.getUniqueID(),attempt);
        boolean success=false,reserved=false;
        try{
            attempt.payment=village.vale.bridge.MoveCost.reserve(p,cost);
            if(attempt.payment==null)return Adventures.tell(p,village.vale.bridge.ResourceText.requirement(p,cost));
            if(!attempt.valid())return false;
            cds.setLong(art.key,own);root.setTag("artCooldowns",cds);root.setLong("artGlobal",global);
            reserved=true;
            // Persist the reservation before callbacks can produce irreversible effects or save the player.
            Adventures.save(p);success=perform(attempt);
            if(!success&&!attempt.applied)Adventures.tell(p,art.order==Order.FLINT?"Need six clear blocks on solid ground, outside protected space; previous cover must fade.":"No permitted contact. Aim around cover; hostile arrows also count for Wind.");
            return success||attempt.applied;
        }finally{
            try{
                if(attempt.payment!=null){if(attempt.applied)attempt.payment.commit();else attempt.payment.refund();}
                if(reserved){
                if(attempt.applied&&PlayerState.data(p)==root&&p.world==attempt.world){
                    root.setLong("lastCombat",now);Missions.art(p);Adventures.gesture(p);HudNetwork.cast(p,art.key);
                    p.sendStatusMessage(new TextComponentString(art.title+" | "+village.vale.bridge.ResourceText.balance(p)),true);
                }else if(!attempt.applied){
                    NBTTagCompound live=root.getCompoundTag("artCooldowns");if(live.getLong(art.key)==own)live.setLong(art.key,oldOwn);root.setTag("artCooldowns",live);
                    if(root.getLong("artGlobal")==global)root.setLong("artGlobal",oldGlobal);
                }
                if(PlayerState.data(p)==root)Adventures.save(p);
                }
            }finally{ACTIVE.remove(p.getUniqueID(),attempt);}
        }
    }
    private static boolean perform(Attempt a){
        if(!a.valid())return false;EntityPlayerMP p=a.p;
        switch(a.art.order){
            case EMBER:{
                boolean ok=false;for(EntityLivingBase target:cone(p,8,.78,5)){
                    if(!a.valid())break;
                    if(impact(a,target,6)){ok=true;if(followup(a,target,8,.78)){target.getEntityData().removeTag("valeNatureWetUntil");target.setFire(3);contact(a,target);}}
                }
                if(ok&&a.valid()){spray(p,EnumParticleTypes.FLAME,8,.65,5);sound(p,SoundEvents.ENTITY_BLAZE_SHOOT,.65F,1.0F);}return ok;
            }
            case CURRENT:{
                boolean ok=false;for(EntityLivingBase target:cone(p,6,.48,5)){
                    if(!a.valid())break;
                    if(impact(a,target,3)){ok=true;if(followup(a,target,6,.48)){target.extinguish();target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,60,1));if(!followup(a,target,6,.48))continue;target.getEntityData().setLong("valeNatureWetUntil",p.world.getTotalWorldTime()+60);push(p,target,1.0);contact(a,target);}}
                }
                if(ok&&a.valid()){spray(p,EnumParticleTypes.WATER_SPLASH,6,1.8,8);sound(p,SoundEvents.ITEM_BUCKET_EMPTY,.8F,.8F);}return ok;
            }
            case FLINT:
                if(!EarthCover.raise(p,a))return false;
                a.accept();if(!a.valid())return true;
                AcademyCampaign.defensiveArt(p,a.art.key,EarthCover.center(p));
                ((WorldServer)p.world).spawnParticle(EnumParticleTypes.BLOCK_DUST,EarthCover.center(p).getX()+.5,p.posY+.6,EarthCover.center(p).getZ()+.5,32,1,.6,1,.08,net.minecraft.block.Block.getStateId(Blocks.COBBLESTONE.getDefaultState()));
                sound(p,SoundEvents.BLOCK_STONE_PLACE,1,.65F);return true;
            case GALE:{
                boolean ok=false;for(EntityLivingBase target:cone(p,9,.72,4)){
                    if(!a.valid())break;
                    if(impact(a,target,2)){ok=true;if(followup(a,target,9,.72)){push(p,target,1.35);contact(a,target);}}
                }
                int inspected=0,dispersed=0;for(EntityArrow arrow:p.world.getEntitiesWithinAABB(EntityArrow.class,p.getEntityBoundingBox().grow(9))){
                    if(!a.valid()||inspected++>=64||dispersed>=8)break;if(!eligibleArrow(p,arrow))continue;
                    AttackEntityEvent event=new AttackEntityEvent(p,arrow);if(MinecraftForge.EVENT_BUS.post(event)||!a.valid()||!eligibleArrow(p,arrow))continue;
                    // Disperse supported hostile arrows. No ownership swap or uncontrolled reflected friendly damage.
                    try{arrow.setDead();}finally{if(arrow.isDead)a.accept();}
                    if(arrow.isDead){ok=true;dispersed++;}
                }
                if(ok&&a.valid()){spray(p,EnumParticleTypes.CLOUD,9,1.25,4);sound(p,SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,1,.6F);}return ok;
            }
            case STORM:{
                Vec3d start=p.getPositionEyes(1),end=start.add(p.getLookVec().scale(6.5));
                if(!loadedRay(p.world,start,end)||!p.world.getWorldBorder().contains(new BlockPos(end)))return false;
                EntityLivingBase target=ArsenalAccess.aimedTarget(p,6.5);if(target==null||!rayClear(p,target))return false;
                boolean wet=target.isWet()||target.getEntityData().getLong("valeNatureWetUntil")>p.world.getTotalWorldTime();
                if(!impact(a,target,wet?12:9))return false;
                // A precise short strike commits the user's footing; walls and canceled native damage win.
                if(followup(a,target,6.5,.94)){
                    p.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,16,1));if(!followup(a,target,6.5,.94))return true;target.addPotionEffect(new PotionEffect(MobEffects.GLOWING,25,0));
                    if(!followup(a,target,6.5,.94))return true;line(p,target.getPositionEyes(1),EnumParticleTypes.CRIT_MAGIC);contact(a,target);sound(p,SoundEvents.ENTITY_FIREWORK_BLAST,.7F,1.7F);
                }return true;
            }
            default:return false;
        }
    }
    private static boolean impact(Attempt a,EntityLivingBase target,float damage){
        if(!a.valid()||!rayClear(a.p,target)||!ArsenalAccess.allowed(a.p,target))return false;
        float before=target.getHealth()+target.getAbsorptionAmount();
        try{return ArsenalAccess.hit(a.p,target,damage);}
        finally{if(target.getHealth()+target.getAbsorptionAmount()<before)a.accept();}
    }
    private static void contact(Attempt a,EntityLivingBase target){AcademyCampaign.art(a.p,a.art.key,target);}
    /** Damage callbacks may change world, team, equipment or position after actual health loss. */
    private static boolean followup(Attempt a,EntityLivingBase target,double range,double cosine){
        if(!a.valid()||!ArsenalAccess.allowed(a.p,target)||!rayClear(a.p,target))return false;
        Vec3d d=target.getPositionEyes(1).subtract(a.p.getPositionEyes(1)),look=a.p.getLookVec();
        return NatureRules.cone(d.x,d.y,d.z,look.x,look.y,look.z,range,cosine);
    }
    private static List<EntityLivingBase> cone(EntityPlayerMP p,double range,double cosine,int limit){
        List<EntityLivingBase> targets=new ArrayList<>();Vec3d eye=p.getPositionEyes(1),look=p.getLookVec();
        List<EntityLivingBase> all=p.world.getEntitiesWithinAABB(EntityLivingBase.class,p.getEntityBoundingBox().grow(range));all.sort(Comparator.comparingDouble(p::getDistanceSq));
        for(EntityLivingBase e:all){Vec3d d=e.getPositionEyes(1).subtract(eye);
            if(ArsenalAccess.allowed(p,e)&&NatureRules.cone(d.x,d.y,d.z,look.x,look.y,look.z,range,cosine)&&rayClear(p,e)){targets.add(e);if(targets.size()==limit)break;}
        }return targets;
    }
    private static boolean rayClear(EntityPlayerMP p,Entity target){
        Vec3d start=p.getPositionEyes(1),end=target instanceof EntityLivingBase?((EntityLivingBase)target).getPositionEyes(1):target.getPositionVector();
        if(target.world!=p.world||!loadedRay(p.world,start,end)||!p.world.getWorldBorder().contains(new BlockPos(end)))return false;
        return p.world.rayTraceBlocks(start,end,false,true,false)==null;
    }
    static boolean eligibleArrow(EntityPlayerMP p,EntityArrow arrow){
        if(arrow==null||arrow.isDead||arrow.world!=p.world||!(arrow.shootingEntity instanceof EntityLivingBase)||!ArsenalAccess.allowed(p,(EntityLivingBase)arrow.shootingEntity))return false;
        Vec3d d=arrow.getPositionVector().subtract(p.getPositionEyes(1)),look=p.getLookVec();
        if(!NatureRules.cone(d.x,d.y,d.z,look.x,look.y,look.z,9,.72)||!rayClear(p,arrow))return false;
        NBTTagCompound n=new NBTTagCompound();arrow.writeToNBT(n);return !n.getBoolean("inGround")&&Double.isFinite(arrow.motionX+arrow.motionY+arrow.motionZ)&&arrow.motionX*arrow.motionX+arrow.motionY*arrow.motionY+arrow.motionZ*arrow.motionZ>.001;
    }
    private static void push(EntityPlayerMP p,EntityLivingBase target,double force){
        double x=p.posX-target.posX,z=p.posZ-target.posZ;if(x*x+z*z<.0001){x=-p.getLookVec().x;z=-p.getLookVec().z;if(x*x+z*z<.0001)z=1;}
        target.knockBack(p,(float)force,x,z);target.velocityChanged=true;
    }
    private static void line(EntityPlayerMP p,Vec3d end,EnumParticleTypes particle){
        Vec3d start=p.getPositionEyes(1),d=end.subtract(start);int steps=Math.min(40,(int)(d.length()*5)+1);
        for(int i=0;i<=steps;i++){Vec3d at=start.add(d.scale(i/(double)steps));((WorldServer)p.world).spawnParticle(particle,at.x,at.y,at.z,2,.02,.02,.02,.01);}
    }
    private static void spray(EntityPlayerMP p,EnumParticleTypes particle,double range,double spread,int count){
        Vec3d start=p.getPositionEyes(1),look=p.getLookVec(),end=start.add(look.scale(range));
        if(!loadedRay(p.world,start,end))return;
        RayTraceResult wall=p.world.rayTraceBlocks(start,end,false,true,false);double stop=wall==null?range:start.distanceTo(wall.hitVec);
        for(double distance=.6;distance<=stop;distance+=.6){Vec3d at=start.add(look.scale(distance));double width=spread*distance/range;((WorldServer)p.world).spawnParticle(particle,at.x,at.y,at.z,count,width,width*.35,width,.03);}
    }
    private static boolean loadedRay(World world,Vec3d start,Vec3d end){
        BlockPos lo=new BlockPos(Math.min(start.x,end.x),Math.min(start.y,end.y),Math.min(start.z,end.z));
        BlockPos hi=new BlockPos(Math.max(start.x,end.x),Math.max(start.y,end.y),Math.max(start.z,end.z));
        return world.isAreaLoaded(lo,hi);
    }
    private static void sound(EntityPlayerMP p,SoundEvent sound,float volume,float pitch){p.world.playSound(null,p.posX,p.posY,p.posZ,sound,SoundCategory.PLAYERS,volume,pitch);}
}
