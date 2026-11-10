package village.vale.arsenal;

/** Bounded rules for the original Crescent Talons. All deadlines use server ticks. */
public final class ClawRules {
    public static final double RANGE=5, DODGE_DISTANCE=1.2;
    public static final int LOCK_TICKS=100, WINDUP_TICKS=20, BLIND_TICKS=100;
    public static final int BLEED_TICKS=340, HITS_PER_STACK=5, MAX_STACKS=3, MAX_TARGETS=8;
    private ClawRules() {}
    public static boolean inRange(double squared) {
        return Double.isFinite(squared)&&squared>=0&&squared<=RANGE*RANGE;
    }
    public static boolean rear(double lookX,double lookZ,double fromX,double fromZ) {
        double look=lookX*lookX+lookZ*lookZ,from=fromX*fromX+fromZ*fromZ;
        return Double.isFinite(look)&&Double.isFinite(from)&&look>1.0e-8&&from>1.0e-8
                &&(lookX*fromX+lookZ*fromZ)/Math.sqrt(look*from)<-.5;
    }
    public static boolean lockOpen(long now,long started) {
        return now>=started&&now-started<LOCK_TICKS;
    }
    public static boolean windupDue(long now,long started) {
        return now>=started&&now-started>=WINDUP_TICKS&&now-started<=WINDUP_TICKS+2;
    }
    public static boolean dodged(double squared) {
        return !Double.isFinite(squared)||squared<0||squared>DODGE_DISTANCE*DODGE_DISTANCE;
    }
    /** Vanilla melee may include enchantments; do not replace its damage with a spell cap. */
    public static float nativeRearDamage(float base) {
        if(!Float.isFinite(base)||base<=0)return 0;
        float result=base*1.1f;return Float.isFinite(result)?result:base;
    }
    public static float rearDamage(float base,boolean rear) {
        return Float.isFinite(base)&&base>0?Math.min(16,base*(rear?1.1f:1)):0;
    }
}
