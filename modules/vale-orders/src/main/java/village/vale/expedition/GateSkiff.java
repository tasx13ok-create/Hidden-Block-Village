package village.vale.expedition;

import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/** A real native boat with owner/run damage authority; requires explicit registration. */
public final class GateSkiff extends EntityBoat {
    public GateSkiff(World world){super(world);}
    @Override public boolean attackEntityFrom(DamageSource source,float amount){return GateRuntime.skiffDamage(this,source)&&super.attackEntityFrom(source,amount);}
    @Override public void onUpdate(){super.onUpdate();if(!world.isRemote&&ticksExisted%20==0&&!GateRuntime.skiffValid(this)){setDead();world.removeEntity(this);}}
}
