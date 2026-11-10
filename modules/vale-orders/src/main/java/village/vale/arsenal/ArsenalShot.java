package village.vale.arsenal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import village.vale.ArsenalAccess;

public final class ArsenalShot extends ArsenalEntity {
    public static final int RETURN=0,MARK=1,DRAIN=2,GUIDED=3,BIND=4,VENOM=5,MIST=6,FIRE=7,GRAPPLE=8,NEEDLE=9;
    private boolean returning,anchored;
    private UUID targetId;
    private final Set<UUID> contacts=new HashSet<>();
    public ArsenalShot(World world) {super(world);}
    ArsenalShot(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec,int mode,float power,float yaw) {
        this(p,stack,spec,mode,power,yaw,p.getPositionEyes(1).add(p.getLookVec().scale(.4)));
    }
    ArsenalShot(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec,int mode,float power,float yaw,Vec3d origin) {
        super(p.world);initialize(p,stack,spec,mode,power);setPosition(origin.x,origin.y,origin.z);
        Vec3d look=p.getLookVec().rotateYaw((float)Math.toRadians(yaw));
        double speed=.9+power*.9;motionX=look.x*speed;motionY=look.y*speed;motionZ=look.z*speed;
        if(mode==GUIDED) {EntityLivingBase target=ArsenalAccess.aimedTarget(p,24);if(target!=null)targetId=target.getUniqueID();}
    }
    public void recall() {returning=true;anchored=false;}
    private boolean validMode() {
        if(spec()==null)return false;
        switch(mode()) {
            case RETURN:return spec().role==ArsenalSpec.Role.RETURN;
            case MARK:return spec().role==ArsenalSpec.Role.MARK;
            case DRAIN:return spec().role==ArsenalSpec.Role.DRAIN_BOLT;
            case GUIDED:return spec().role==ArsenalSpec.Role.GUIDED;
            case BIND:return spec().role==ArsenalSpec.Role.BIND;
            case VENOM:return spec().role==ArsenalSpec.Role.VENOM;
            case MIST:return spec().role==ArsenalSpec.Role.MIST;
            case FIRE:return spec().role==ArsenalSpec.Role.FIRE;
            case GRAPPLE:return spec().role==ArsenalSpec.Role.SONIC;
            case NEEDLE:return spec().role==ArsenalSpec.Role.EMITTER;
            default:return false;
        }
    }
    @Override public void onUpdate() {
        super.onUpdate();age++;
        if(world.isRemote) {setPosition(posX+motionX,posY+motionY,posZ+motionZ);return;}
        EntityPlayerMP p=owner();
        if(!validMode()||!ArsenalMath.validFlight(age,p==null?Double.NaN:getDistanceSq(p),world.isBlockLoaded(getPosition()),validOwner(p),validOwner(p))) {setDead();return;}
        if(anchored) {
            if(!p.isSneaking()||age>35||getDistanceSq(p)>12*12) {setDead();return;}
            Vec3d toward=getPositionVector().subtract(p.getPositionVector());double amount=ArsenalMath.pull(toward.length());
            Vec3d v=toward.normalize().scale(amount);p.addVelocity(v.x,Math.max(-.12,Math.min(.18,v.y)),v.z);p.velocityChanged=true;
            return;
        }
        if(mode()==RETURN&&(age>16||returning)) {
            returning=true;Vec3d delta=p.getPositionEyes(1).subtract(getPositionVector());
            if(delta.lengthSquared()<1) {recover(p);setDead();return;}
            Vec3d v=delta.normalize().scale(1.5);motionX=v.x;motionY=v.y;motionZ=v.z;
        } else if(mode()==GUIDED&&targetId!=null) {
            net.minecraft.entity.Entity entity=((WorldServer)world).getEntityFromUuid(targetId);
            if(entity instanceof EntityLivingBase&&ArsenalAccess.allowed(p,(EntityLivingBase)entity)&&p.canEntityBeSeen(entity)) {
                Vec3d current=new Vec3d(motionX,motionY,motionZ).normalize();
                Vec3d desired=((EntityLivingBase)entity).getPositionEyes(1).subtract(getPositionVector()).normalize();
                Vec3d next=current.scale(.75).add(desired.scale(.25)).normalize().scale(1.25+power()*.35);
                motionX=next.x;motionY=next.y;motionZ=next.z;
            } else targetId=null;
        }
        Vec3d start=getPositionVector(),end=start.add(motionX,motionY,motionZ);
        if(!Double.isFinite(end.x)||!Double.isFinite(end.y)||!Double.isFinite(end.z)||!world.isBlockLoaded(new BlockPos(end))) {setDead();return;}
        RayTraceResult block=world.rayTraceBlocks(start,end,false,true,false);
        Vec3d stop=block==null?end:block.hitVec;
        // Sweep every contact in physical order. Piercing no longer jumps over
        // a second victim (or a protected body) in the same movement segment.
        List<Contact> swept=new ArrayList<>();int checked=0;
        for(EntityLivingBase candidate:world.getEntitiesWithinAABB(EntityLivingBase.class,getEntityBoundingBox().expand(motionX,motionY,motionZ).grow(.25))) {
            if(candidate==p||contacts.contains(candidate.getUniqueID())||!candidate.isEntityAlive()||(candidate instanceof net.minecraft.entity.player.EntityPlayer&&((net.minecraft.entity.player.EntityPlayer)candidate).isSpectator())||!candidate.canBeCollidedWith())continue;
            if(checked++>=64) {setDead();return;}
            net.minecraft.util.math.AxisAlignedBB box=candidate.getEntityBoundingBox().grow(.15);
            boolean inside=start.x>=box.minX&&start.x<=box.maxX&&start.y>=box.minY&&start.y<=box.maxY&&start.z>=box.minZ&&start.z<=box.maxZ;
            RayTraceResult intersection=box.calculateIntercept(start,stop);
            if(inside||intersection!=null)swept.add(new Contact(candidate,inside?start:intersection.hitVec,start));
        }
        swept.sort(Comparator.comparingDouble((Contact hit)->hit.distance).thenComparingInt(hit->hit.target.getEntityId()));
        for(Contact hit:swept) {
            if(!validOwner(p)) {setDead();return;}
            setPosition(hit.point.x,hit.point.y,hit.point.z);
            boolean permitted=ArsenalAccess.allowed(p,hit.target)&&p.canEntityBeSeen(hit.target);
            boolean accepted=permitted&&contact(p,hit.target);
            if(!validOwner(p)) {setDead();return;}
            if(mode()==RETURN) {
                if(!returning) {recall();return;}
                if(!accepted||contacts.size()>=3) {setDead();return;}
            } else if(mode()!=BIND||!accepted||contacts.size()>=3) {
                if(mode()==MIST&&accepted)mist(p,hit.target.getPositionVector());
                setDead();return;
            }
        }
        if(block!=null) {
            setPosition(stop.x,stop.y,stop.z);
            if(mode()==MARK) {ArsenalLogic.mark(p,stop.add(0,.15,0));setDead();}
            else if(mode()==RETURN&&!returning)recall();
            else if(mode()==MIST) {mist(p,stop);setDead();}
            else if(mode()==GRAPPLE&&getDistanceSq(p)<=12*12) {anchored=true;motionX=motionY=motionZ=0;}
            else setDead();
            return;
        }
        setPosition(end.x,end.y,end.z);
        if(age%3==0)ArsenalLogic.particles(p,end,mode()==FIRE?EnumParticleTypes.FLAME:EnumParticleTypes.CRIT_MAGIC,1);
    }
    private static final class Contact {
        final EntityLivingBase target;final Vec3d point;final double distance;
        Contact(EntityLivingBase target,Vec3d point,Vec3d start) {this.target=target;this.point=point;distance=start.squareDistanceTo(point);}
    }
    private boolean contact(EntityPlayerMP p,EntityLivingBase target) {
        contacts.add(target.getUniqueID());
        float amount=mode()==GUIDED?7+power()*3:mode()==NEEDLE?2:4+power()*4;
        if(!ArsenalLogic.hit(p,target,amount))return false;
        if(!validOwner(p))return true;
        switch(mode()) {
            case MARK:ArsenalLogic.mark(p,target.getPositionVector());break;
            case DRAIN:ArsenalLogic.drain(p,target,6);break;
            case VENOM:ArsenalLogic.addEffect(p,target,issued,spec(),ArsenalLogic.Effect.VENOM,60);break;
            case FIRE:ArsenalLogic.addEffect(p,target,issued,spec(),ArsenalLogic.Effect.BURN,60);break;
            case BIND:case GRAPPLE:
                ArsenalLogic.addEffect(p,target,issued,spec(),ArsenalLogic.Effect.TETHER,35);
                target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS,35,1));break;
            case GUIDED:
                for(EntityLivingBase nearby:ArsenalLogic.near(p,target.getPositionVector(),2,3))if(validOwner(p)&&nearby!=target)ArsenalLogic.hit(p,nearby,3);
                ArsenalLogic.particles(p,target.getPositionVector(),EnumParticleTypes.EXPLOSION_NORMAL,6);break;
            default:break;
        }
        return true;
    }
    private void mist(EntityPlayerMP p,Vec3d at) {ArsenalLogic.field(p,issued,spec(),at,ArsenalField.MIST,power());}
    private void recover(EntityPlayerMP p) {
        ArsenalLogic.state(p).setLong("ready",Math.max(ArsenalLogic.state(p).getLong("ready"),ArsenalAccess.now(p)+ArsenalMath.SHARED_RECOVERY));
        if(!p.getCooldownTracker().hasCooldown(issued.getItem()))p.getCooldownTracker().setCooldown(issued.getItem(),ArsenalMath.SHARED_RECOVERY);
        ArsenalLogic.particles(p,p.getPositionEyes(1),EnumParticleTypes.CRIT,3);
    }
    @Override protected void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);nbt.setBoolean("returning",returning);nbt.setBoolean("anchored",anchored);
        if(targetId!=null)nbt.setUniqueId("target",targetId);
        net.minecraft.nbt.NBTTagList hits=new net.minecraft.nbt.NBTTagList();
        for(UUID id:contacts)hits.appendTag(new net.minecraft.nbt.NBTTagString(id.toString()));nbt.setTag("contacts",hits);
    }
    @Override protected void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);returning=nbt.getBoolean("returning");anchored=nbt.getBoolean("anchored");
        targetId=nbt.hasUniqueId("target")?nbt.getUniqueId("target"):null;
        contacts.clear();net.minecraft.nbt.NBTTagList hits=nbt.getTagList("contacts",8);
        for(int i=0;i<Math.min(3,hits.tagCount());i++)try {contacts.add(UUID.fromString(hits.getStringTagAt(i)));}catch(IllegalArgumentException ignored) {setDead();}
    }
}
