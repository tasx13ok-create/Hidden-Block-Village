package village.controls;

/** Short color transitions; layout and hitboxes never animate. */
public final class MenuMotion {
    private double from,target;private long at;
    public double value(boolean active,long now,boolean motion){double next=active?1:0;if(!motion){from=target=next;at=now;return next;}double current=sample(from,target,now-at,120);if(next!=target){from=current;target=next;at=now;}return sample(from,target,now-at,120);}
    public static double sample(double from,double target,long elapsed,int duration){double t=Math.max(0,Math.min(1,elapsed/(double)Math.max(1,duration)));return from+(target-from)*t*t*(3-2*t);}
    public static int mix(int a,int b,double amount){double t=Math.max(0,Math.min(1,amount));int out=0;for(int shift:new int[]{0,8,16,24})out|=((int)Math.round((a>>>shift&255)*(1-t)+(b>>>shift&255)*t))<<shift;return out;}
}
