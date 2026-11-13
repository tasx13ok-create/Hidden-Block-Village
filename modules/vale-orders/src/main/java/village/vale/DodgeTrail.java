package village.vale;
import java.util.*;
/** Six actual observed positions; teleports/nonfinite input break the visual trail. */
public final class DodgeTrail {
    public static final int CAP=6;
    public static final class Sample {public final double x,y,z;public final float yaw;Sample(double x,double y,double z,float yaw){this.x=x;this.y=y;this.z=z;this.yaw=yaw;}}
    private final ArrayDeque<Sample> samples=new ArrayDeque<>();
    public void sample(double x,double y,double z,float yaw){if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw)){samples.clear();return;}Sample last=samples.peekLast();if(last!=null){double dx=x-last.x,dy=y-last.y,dz=z-last.z,d=dx*dx+dy*dy+dz*dz;if(d>9)samples.clear();else if(d<.0064)return;}samples.addLast(new Sample(x,y,z,yaw));while(samples.size()>CAP)samples.removeFirst();}
    public List<Sample> snapshot(){return new ArrayList<>(samples);}
}
