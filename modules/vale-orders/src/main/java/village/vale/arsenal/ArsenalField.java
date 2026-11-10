package village.vale.arsenal;

import java.util.List;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class ArsenalField extends ArsenalEntity {
    public static final int EMITTER=0,TRAP=1,SNARE=2,MIST=3;
    public ArsenalField(World world) {super(world);setSize(.25f,.25f);}
    ArsenalField(EntityPlayerMP p,ItemStack stack,ArsenalSpec spec,Vec3d at,int mode,float power) {
        this(p.world);initialize(p,stack,spec,mode,power);setPosition(at.x,at.y,at.z);
    }
    private boolean validMode() {
        return spec()!=null&&(mode()==EMITTER&&spec().role==ArsenalSpec.Role.EMITTER||mode()==TRAP&&spec().role==ArsenalSpec.Role.TRAP
                ||mode()==SNARE&&spec().role==ArsenalSpec.Role.EXTEND||mode()==MIST&&spec().role==ArsenalSpec.Role.MIST);
    }
    @Override public void onUpdate() {
        super.onUpdate();age++;
        if(world.isRemote)return;
        EntityPlayerMP p=owner();int lifetime=mode()==TRAP?120:mode()==MIST?70:40;
        if(!validMode()||!validOwner(p)||age<0||age>lifetime||getDistanceSq(p)>24*24||!world.isBlockLoaded(getPosition())) {setDead();return;}
        if(mode()==EMITTER) {
            if(age==1||age==13||age==25) {
                int count=Math.min(3,ArsenalMath.MAX_SHOTS-ArsenalLogic.shotCount(p));
                for(int i=0;i<count&&validOwner(p);i++)world.spawnEntity(new ArsenalShot(p,issued,spec(),ArsenalShot.NEEDLE,power(),(i-1)*9,getPositionVector()));
            }
            return;
        }
        if(age%4==0)ArsenalLogic.particles(p,getPositionVector().add(0,.5,0),mode()==MIST?EnumParticleTypes.SMOKE_LARGE:EnumParticleTypes.CRIT_MAGIC,mode()==MIST?10:3);
        if(age%10!=0)return;
        List<EntityLivingBase> targets=ArsenalLogic.near(p,getPositionVector(),mode()==TRAP?2:2.75,4);
        if(mode()==TRAP) {
            if(age>=10&&!targets.isEmpty()) {
                for(EntityLivingBase target:targets)if(validOwner(p))ArsenalLogic.hit(p,target,5+power()*3);
                ArsenalLogic.particles(p,getPositionVector(),EnumParticleTypes.EXPLOSION_NORMAL,10);setDead();
            }
        } else for(EntityLivingBase target:targets) {
            if(validOwner(p)&&ArsenalLogic.hit(p,target,mode()==MIST?1:2)&&validOwner(p)) {
                target.addPotionEffect(new PotionEffect(mode()==MIST?MobEffects.WEAKNESS:MobEffects.SLOWNESS,30,mode()==MIST?0:2));
                if(mode()==SNARE)ArsenalLogic.addEffect(p,target,issued,spec(),ArsenalLogic.Effect.TETHER,15);
            }
        }
    }
}
