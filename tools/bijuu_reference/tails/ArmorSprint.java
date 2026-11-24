package village.bijuu;

import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid="hvbijuu_motion",name="Bijuu Movement",version="1.0",dependencies="required-after:hvbijuu")
@Mod.EventBusSubscriber
public final class ArmorSprint {
    private static final UUID ID=UUID.fromString("93ab39b2-5d9f-48ec-88bd-8ef2b18a3a2d");
    private static final WeakHashMap<EntityPlayerMP,Run> runs=new WeakHashMap<>();
    private static final class Run {
        final SprintCurve curve=new SprintCurve();
        final int tails, dimension;
        double x,y,z,dirX,dirZ;
        int quietTicks;
        Run(EntityPlayerMP p,int tails) {
            this.tails=tails; dimension=p.field_71093_bK;
            x=p.field_70165_t; y=p.field_70163_u; z=p.field_70161_v;
        }
    }
    private static void bonus(IAttributeInstance attr,double value) {
        AttributeModifier old=attr.func_111127_a(ID);
        if(old!=null && old.func_111164_d()==value)return;
        if(old!=null)attr.func_111124_b(old);
        if(value>0)attr.func_111121_a(new AttributeModifier(ID,"Bijuu running momentum",value,2).func_111168_a(false));
    }

    @SubscribeEvent public static void move(TickEvent.PlayerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP))return;
        EntityPlayerMP p=(EntityPlayerMP)event.player;
        IAttributeInstance speed=p.func_110148_a(SharedMonsterAttributes.field_111263_d);
        int tails=BijuuArmor.full(p)+1;
        if(tails<1 || !p.func_70089_S() || p.func_175149_v() || p.field_71075_bZ.field_75100_b ||
            p.func_184218_aH() || p.func_70090_H() || p.func_180799_ab() || p.func_70617_f_() || p.func_184613_cA()) {
            runs.remove(p); bonus(speed,0); return;
        }
        Run r=runs.get(p);
        if(r==null || r.tails!=tails || r.dimension!=p.field_71093_bK){
            runs.put(p,new Run(p,tails)); bonus(speed,0); return;
        }
        double dx=p.field_70165_t-r.x, dy=p.field_70163_u-r.y, dz=p.field_70161_v-r.z;
        r.x=p.field_70165_t; r.y=p.field_70163_u; r.z=p.field_70161_v;
        double distance=Math.hypot(dx,dz);
        if(distance>2.5+.9*r.quietTicks || Math.abs(dy)>2.5 || p.field_70123_F || p.field_70737_aN>0) {
            r.curve.charge=0; r.quietTicks=0; r.dirX=r.dirZ=0; bonus(speed,0); return;
        }
        double yaw=Math.toRadians(p.field_70177_z);
        boolean forward=distance>.035 && (dz*Math.cos(yaw)-dx*Math.sin(yaw))>.40*distance;
        double alignment=(r.dirX==0 && r.dirZ==0) || !forward ? 1 : (dx*r.dirX+dz*r.dirZ)/distance;
        if(forward){r.dirX=dx/distance; r.dirZ=dz/distance;}
        boolean sprint=p.func_70051_ag() && !p.func_70093_af() && !p.func_184587_cr();
        // The server can receive several client positions in one tick and none in the next.
        // Hold through a short gap, then credit elapsed moving ticks only when travel resumes.
        if(sprint && distance<.001 && r.quietTicks<3){
            r.quietTicks++;
            return;
        }
        int elapsed=forward && sprint?1+r.quietTicks:1;
        r.quietTicks=0;
        double value=r.curve.advance(tails,sprint,forward,p.field_70122_E,alignment);
        if(alignment>=.35)for(int i=1;i<elapsed;i++)
            value=r.curve.advance(tails,sprint,forward,p.field_70122_E,1);
        bonus(speed,value);
    }
}
