package village.vale.expedition;

/** Pure server-observation rules; no client counters or offline elapsed credit. */
public final class GateRules {
    public static final int SCHEMA=1,MAX_ACTORS=9,MAX_OBSERVED_GAP=4;
    private GateRules(){}
    public static boolean walked(long previous,long now,int previousDimension,int dimension,double distanceSquared,boolean flying,boolean riding,boolean teleported){
        return previous>0&&now>previous&&now-previous<=MAX_OBSERVED_GAP&&previousDimension==dimension
            &&Double.isFinite(distanceSquared)&&distanceSquared>.0001&&distanceSquared<=9&&!flying&&!riding&&!teleported;
    }
    public static boolean crossed(double beforeSquared,double afterSquared,double radius,boolean walked){
        return walked&&Double.isFinite(beforeSquared)&&Double.isFinite(afterSquared)&&radius>=1&&radius<=6
            &&beforeSquared>radius*radius&&afterSquared<=radius*radius;
    }
    /** Return new mask; -1 means wrong order, -2 means irrelevant/repeated input. */
    public static int interact(int mask,int index,int total,boolean ordered){
        if(total<1||total>8||index<0||index>=total||mask<0||(mask&~((1<<total)-1))!=0)return -2;
        int bit=1<<index;if((mask&bit)!=0)return -2;
        if(ordered&&index!=Integer.bitCount(mask))return -1;
        return mask|bit;
    }
    public static boolean sequenceDone(int mask,int total){return total>=1&&total<=8&&mask==(1<<total)-1;}
    public static int observedTicks(int accumulated,long previous,long now,boolean eligible){
        if(!eligible||accumulated<0||previous<=0||now<=previous||now-previous>MAX_OBSERVED_GAP)return 0;
        return (int)Math.min(7200L,(long)accumulated+now-previous);
    }
    public static boolean timedOut(long started,long now,int seconds){return started<=0||now<started||seconds<1||seconds>360||now-started>seconds*20L;}
    public static boolean validKill(int slot,int killedMask,int slots,boolean finalCanceled,boolean healthZero,boolean deathObserved,boolean ownerRunStageMember){
        return slots>0&&slots<=MAX_ACTORS&&slot>=0&&slot<slots&&(killedMask&(1<<slot))==0&&!finalCanceled&&healthZero&&deathObserved&&ownerRunStageMember;
    }
    public static boolean session(long created,long now,long run,long liveRun,String stage,String liveStage,boolean sameContext){
        return sameContext&&run>0&&run==liveRun&&stage!=null&&stage.equals(liveStage)&&now>=created&&now-created<=200;
    }
    public static boolean receipt(String mission,long run){return mission!=null&&mission.matches("gate_[a-z0-9_]{1,56}")&&run>0;}
    public static boolean clearTarget(boolean hasTarget,boolean ownerPresent,boolean ownerContext,boolean isOwner){return hasTarget&&(!ownerPresent||!ownerContext||!isOwner);}
    public static final class SpawnStamp {
        private final Object world,account,state;private final int dimension;private final long run,generation;private final String mission,stage;
        public SpawnStamp(Object world,int dimension,Object account,Object state,long run,long generation,String mission,String stage){this.world=world;this.dimension=dimension;this.account=account;this.state=state;this.run=run;this.generation=generation;this.mission=mission;this.stage=stage;}
        public boolean valid(Object world,int dimension,Object account,Object state,long run,long generation,String mission,String stage,double siteDistanceSquared){
            return this.world!=null&&this.account!=null&&this.state!=null&&this.world==world&&this.dimension==dimension&&this.account==account&&this.state==state
                &&this.run>0&&this.run==run&&this.generation==generation&&this.mission.equals(mission)&&this.stage.equals(stage)
                &&Double.isFinite(siteDistanceSquared)&&siteDistanceSquared>=0&&siteDistanceSquared<=1024;
        }
    }
}
