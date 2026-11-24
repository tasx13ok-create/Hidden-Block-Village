package village.bijuu;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

/** Tick history belongs to the wearer; shadow, inventory and outline passes only sample it. */
final class WearerMotion {
    private static final Map<EntityLivingBase,WearerMotion> actors = new WeakHashMap<>();
    static final int PITCH=0, SIDE=1, FAN=2, STRIKE=3, WATER=4, AIR=5, SPEED=6, WALL=7, FLANK=8, CLEARANCE=9;
    private static final int HISTORY=16, CHANNELS=10;
    private final double[][] frames=new double[HISTORY][CHANNELS];
    private final double[] target=new double[CHANNELS];
    private int head, tick=Integer.MIN_VALUE, dimension;
    private double x,y,z, yaw,forward,lastDrop;
    private boolean grounded;

    static WearerMotion sample(EntityLivingBase actor) {
        WearerMotion state=actors.computeIfAbsent(actor, ignored -> new WearerMotion());
        state.update(actor);
        return state;
    }

    private static double limit(double v,double max) { return Math.max(-max,Math.min(max,v)); }

    private void update(EntityLivingBase e) {
        int now=e.field_70173_aa;
        if(now==tick && dimension==e.field_71093_bK)return;
        double dx=e.field_70165_t-x, dy=e.field_70163_u-y, dz=e.field_70161_v-z;
        boolean reset=now!=tick+1 || dimension!=e.field_71093_bK || dx*dx+dy*dy+dz*dz>144;
        double angle=Math.toRadians(e.field_70761_aq);
        double walk=-dx*Math.sin(angle)+dz*Math.cos(angle);
        double strafe=dx*Math.cos(angle)+dz*Math.sin(angle);
        double turn=Math.IEEEremainder(e.field_70761_aq-yaw,360);
        if(reset) {
            for(double[] frame:frames)Arrays.fill(frame,0);
            Arrays.fill(target,0); walk=0; strafe=0; dy=0; turn=0; forward=0; lastDrop=0;
            grounded=e.field_70122_E;
        }
        double speed=Math.min(1,Math.hypot(walk,strafe)/.53);
        double landing=e.field_70122_E && !grounded ? Math.min(1,lastDrop*2.8) : 0;
        lastDrop=e.field_70122_E ? 0 : Math.max(lastDrop,-dy);
        boolean wet=e.func_70090_H(), riding=e.func_184218_aH(), crouch=e.func_70093_af();
        boolean glide=e.func_184613_cA(), climb=e.func_70617_f_();
        double attack=e.func_70678_g(1);
        double swing=Math.sin(Math.PI*attack);
        double hurt=Math.min(1,e.field_70737_aN/10.0);
        double charge=DashClient.CHARGES.containsKey(e.func_145782_y()) ? 1 : 0;
        String action=e.func_184587_cr()?e.func_184607_cu().func_77975_n().name():"NONE";
        double use=e.func_184587_cr()?.45:0;
        double guard=action.equals("BLOCK")?1:0;
        double draw=action.equals("BOW")?Math.min(1,e.func_184605_cv()/20.0):0;
        double look=limit(Math.IEEEremainder(e.field_70759_as-e.field_70761_aq,360)/80,1);
        DashClient.Cue cue=DashClient.CUES.get(e.func_145782_y());
        boolean sequence=cue!=null && cue.end<0;
        double dash=sequence && cue.blink>=0 && cue.impact<0?1:0;
        double pin=sequence && cue.pin>=0 && cue.spike<0?1:0;
        target[PITCH]=limit(.58*walk+1.5*(walk-forward)-.7*dy+.7*landing+
            .34*(crouch?1:0)+.24*(riding||climb?1:0)+.25*use+.25*charge+.28*guard+.18*draw-.35*dash,.95);
        target[SIDE]=limit(1.4*strafe+turn*.026+.14*look,.95);
        target[FAN]=limit(.42*hurt+.48*guard+.26*draw+.36*pin+.34*landing+.38*charge+.24*use+.20*swing-
            .30*speed-.27*(glide?1:0)-.20*(riding?1:0),.85);
        target[STRIKE]=swing*(e.field_184622_au==net.minecraft.util.EnumHand.OFF_HAND?-1:1)
            *(e.func_184591_cq()==net.minecraft.util.EnumHandSide.LEFT?-1:1);
        target[WATER]=wet?1:0;
        target[AIR]=!e.field_70122_E && !wet && !riding && !climb?1:0;
        target[SPEED]=speed;
        // Check a small fan, including above and below its midpoint, every fourth tick.
        // These steer the pose away from surfaces without moving any attachment points.
        if(reset || (now+e.func_145782_y())%4==0) {
            Vec3d start=new Vec3d(e.field_70165_t,e.field_70163_u+.85,e.field_70161_v);
            double rearX=Math.sin(angle), rearZ=-Math.cos(angle);
            double sideX=Math.cos(angle), sideZ=Math.sin(angle);
            Vec3d mid=start.func_72441_c(rearX*1.25,.15,rearZ*1.25);
            target[WALL]=obstruction(e,start,2.9*rearX,0,2.9*rearZ);
            double left=obstruction(e,start,2*rearX+1.8*sideX,.1,2*rearZ+1.8*sideZ);
            double right=obstruction(e,start,2*rearX-1.8*sideX,.1,2*rearZ-1.8*sideZ);
            target[FLANK]=left-right;
            target[CLEARANCE]=obstruction(e,mid,0,-1.1,0)-obstruction(e,mid,0,1.7,0);
        }
        int prev=head; head=(head+1)%HISTORY;
        for(int i=0;i<CHANNELS;i++) {
            double response=i==STRIKE?.78:i==WALL?.58:.48;
            frames[head][i]=frames[prev][i]+response*(target[i]-frames[prev][i]);
        }
        x=e.field_70165_t; y=e.field_70163_u; z=e.field_70161_v; yaw=e.field_70761_aq;
        forward=walk; grounded=e.field_70122_E; tick=now; dimension=e.field_71093_bK;
    }

    private static double obstruction(EntityLivingBase e,Vec3d start,double dx,double dy,double dz) {
        Vec3d end=start.func_72441_c(dx,dy,dz);
        if(!e.field_70170_p.func_175667_e(new net.minecraft.util.math.BlockPos(end)))return 0;
        RayTraceResult hit=e.field_70170_p.func_147447_a(start,end,false,true,false);
        return hit==null?0:Math.max(0,1-start.func_72438_d(hit.field_72307_f)/start.func_72438_d(end));
    }

    double at(int channel,double delay,double partial) {
        double age=delay+1-Math.max(0,Math.min(1,partial));
        int whole=(int)age;
        int recent=(head-whole+HISTORY)%HISTORY;
        double blend=age-whole;
        return frames[recent][channel]*(1-blend)+frames[(recent+HISTORY-1)%HISTORY][channel]*blend;
    }
}
