package village.bijuu;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

/** Deterministic wearer inputs against the real client world and vanilla player class. */
final class PoseChecks {
    static final class Actor extends EntityOtherPlayerMP {
        boolean wet,climbing,riding;
        float swing;
        Actor(World world){super(world,new GameProfile(UUID.randomUUID(),"MotionActor"));}
        @Override public boolean func_70090_H(){return wet;}
        @Override public boolean func_70617_f_(){return climbing;}
        @Override public boolean func_184218_aH(){return riding;}
        @Override public float func_70678_g(float partial){return swing;}
    }
    private static WearerMotion step(Actor a){
        ++a.field_70173_aa;
        return WearerMotion.sample(a);
    }
    static void run(EntityLivingBase actual){
        Actor a=new Actor(actual.field_70170_p);
        a.func_70107_b(actual.field_70165_t,actual.field_70163_u,actual.field_70161_v);
        a.field_70122_E=true;
        for(int i=0;i<18;i++)step(a);
        a.field_70161_v+=.4;
        WearerMotion m=step(a);
        RunProbe.check(m.at(WearerMotion.SPEED,0,1)>.2 && m.at(WearerMotion.SPEED,5,1)==0,"tip lag");
        double first=m.at(WearerMotion.PITCH,0,1);
        RunProbe.check(WearerMotion.sample(a).at(WearerMotion.PITCH,0,1)==first,"duplicate render pass");
        a.field_70761_aq=35; step(a);
        RunProbe.check(m.at(WearerMotion.SIDE,0,1)>.2,"turn");
        a.field_70122_E=false; a.field_70163_u-=.7; step(a);
        RunProbe.check(m.at(WearerMotion.AIR,0,1)>.4,"air");
        a.field_70122_E=true; step(a);
        RunProbe.check(m.at(WearerMotion.FAN,0,1)>.1,"landing");
        a.field_70737_aN=10; a.swing=.5f; step(a);
        RunProbe.check(m.at(WearerMotion.FAN,0,1)>.2 && Math.abs(m.at(WearerMotion.STRIKE,0,1))>.6,"hit and attack");
        a.wet=true; step(a);
        RunProbe.check(m.at(WearerMotion.WATER,0,1)>.4,"water");
        a.wet=false; a.swing=0; a.field_70737_aN=0; a.field_70165_t+=30; step(a);
        RunProbe.check(m.at(WearerMotion.SPEED,0,1)==0 && m.at(WearerMotion.STRIKE,4,1)==0,"teleport");
        a.field_70122_E=false; a.climbing=true;
        for(int i=0;i<16;i++)step(a);
        RunProbe.check(m.at(WearerMotion.PITCH,0,1)>.2 && m.at(WearerMotion.AIR,0,1)==0,"climb");
        Actor other=new Actor(actual.field_70170_p); other.field_70122_E=true;
        RunProbe.check(step(other).at(WearerMotion.PITCH,0,1)==0,"separate wearer");
        System.out.println("POSE_INPUT_PASS");
    }
}
