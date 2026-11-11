package village.vale.armor;

/** Cosmetic snapshot and pose state, independent of the world and native renderer. */
public final class ArmorPresentationState {
    private int sequence;
    private boolean receivedSnapshot, flying, posed;
    private long snapshotAt, snapshotUntil=Long.MIN_VALUE, poseUntil=Long.MIN_VALUE;
    private long strikeAt=Long.MIN_VALUE;
    private double poseTime;
    private float fold;

    public void receive(long now,int sequence,boolean flying){
        // Rendering can create this state before its first packet. That packet is a baseline,
        // as is the first snapshot after a tracking gap; neither represents a new strike.
        if(!current(now))strikeAt=Long.MIN_VALUE;
        else if(this.sequence!=sequence)strikeAt=now;
        receivedSnapshot=true;this.sequence=sequence;this.flying=flying;
        snapshotAt=now;snapshotUntil=now+40;
    }

    public boolean flying(long now){return current(now)&&flying;}

    public float strike(long now,float partial){
        if(!current(now)||strikeAt==Long.MIN_VALUE)return 0;
        double age=now-strikeAt+RigPose.unit(partial);
        return age>=0&&age<10?(float)Math.sin(Math.PI*age/10):0;
    }

    public float fold(long now,double time,boolean target){
        poseUntil=now+20;
        if(!Double.isFinite(time))return fold;
        double delta=posed?time-poseTime:0;
        // A respawn, clock reset or long absence must not resume an old partially open pose.
        if(delta<0||delta>40){fold=0;delta=0;}
        posed=true;poseTime=time;
        fold=RigPose.approachWing(fold,target?1:0,delta);
        return fold;
    }

    public boolean expired(long now){return now>Math.max(snapshotUntil,poseUntil);}

    private boolean current(long now){return receivedSnapshot&&now>=snapshotAt&&now<=snapshotUntil;}
}
