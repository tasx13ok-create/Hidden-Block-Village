package village.fieldhud;

/** Presentation only: the label always reports the current synchronized value. */
public final class ReactiveMeter {
    private boolean initialized;
    private double value,maximum,shown,trail,previousTime,holdUntil,flashUntil;
    private int direction;
    public void reset() { initialized=false; }
    public void update(double amount,double capacity,double time,boolean motion) {
        double target=SkinMath.ratio(amount,capacity);
        if(!initialized||!Double.isFinite(time)||time<previousTime||capacity!=maximum||!motion) {
            initialized=true;value=shown=trail=target;maximum=capacity;
            previousTime=Double.isFinite(time)?time:0;holdUntil=flashUntil=previousTime;direction=0;return;
        }
        double elapsed=Math.min(4,time-previousTime);
        if(Math.abs(target-value)>=0.015) {
            direction=target<value?-1:1;flashUntil=time+14;
            if(direction<0) { trail=Math.max(trail,shown);holdUntil=time+7; }
        }
        shown=SkinMath.ease(shown,target,elapsed,2.2);
        trail=time<holdUntil?Math.max(trail,shown):SkinMath.ease(trail,shown,elapsed,7);
        if(trail<shown)trail=shown;
        value=target;previousTime=time;
    }
    public double shown() { return shown; }
    public double trail() { return trail; }
    public double impact(double time) { return Math.max(0,Math.min(1,(flashUntil-time)/14)); }
    public int direction() { return direction; }
}
