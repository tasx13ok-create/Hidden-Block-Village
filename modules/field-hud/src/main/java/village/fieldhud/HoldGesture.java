package village.fieldhud;

/** Monotonic real-time hold: one action per press, with release required after interruption. */
public final class HoldGesture {
    public static final long MILLIS=3000;
    private long started=-1;
    private boolean latched,blocked;
    public void reset(){started=-1;latched=blocked=false;}
    public boolean update(boolean down,boolean allowed,long now){
        if(!down){reset();return false;}
        if(!allowed){started=-1;blocked=true;return false;}
        if(blocked||latched)return false;
        if(started<0||now<started){started=now;return false;}
        if(now-started<MILLIS)return false;
        latched=true;return true;
    }
    public double progress(long now){return started<0||blocked?0:Math.max(0,Math.min(1,(now-started)/(double)MILLIS));}
}
