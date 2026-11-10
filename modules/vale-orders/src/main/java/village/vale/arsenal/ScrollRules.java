package village.vale.arsenal;

/** Original independent rules; no Minecraft or reference-mod dependency. */
public final class ScrollRules {
    private ScrollRules() {}
    public enum Failure { NONE, UNISSUED, KNOWN, LEVEL, PREREQUISITE, UNLEARNED, PRACTICE, COOLDOWN, RESOURCE }
    public static Failure learn(boolean issued,boolean known,int level,int minimum,boolean prerequisite) {
        if(!issued)return Failure.UNISSUED;if(known)return Failure.KNOWN;
        if(level<minimum)return Failure.LEVEL;if(!prerequisite)return Failure.PREREQUISITE;return Failure.NONE;
    }
    public static Failure cast(boolean issued,boolean learned,int level,int minimum,int practice,int required,long now,long next,int resolve,int cost) {
        if(!issued)return Failure.UNISSUED;if(!learned)return Failure.UNLEARNED;if(level<minimum)return Failure.LEVEL;
        if(practice<required)return Failure.PRACTICE;if(now<next)return Failure.COOLDOWN;
        if(cost<1||cost>100||resolve<cost)return Failure.RESOURCE;return Failure.NONE;
    }
    public static int advance(int current,int amount,int cap) {
        if(amount<0||cap<1)throw new IllegalArgumentException("Invalid progression.");
        return(int)Math.min(cap,Math.max(0L,(long)current)+amount);
    }
    public static String receipt(String owner,String lesson) {
        if(owner==null||owner.isEmpty()||lesson==null||!lesson.matches("[a-z][a-z0-9_]{1,48}"))throw new IllegalArgumentException("Invalid receipt key.");
        return "vale-scroll-v1/"+owner+"/"+lesson;
    }
    public static int studyAward(int uses,int roll) {
        if(uses<0||roll<0||roll>100)throw new IllegalArgumentException("Invalid study input.");
        int cap=uses<=10?100:uses<=50?50:uses<=100?40:uses<=150?30:uses<=200?20:10;
        return Math.max(1,Math.round(roll*cap/100f));
    }
    public static long next(long now,int ticks) {
        if(now<0||ticks<1||ticks>2400||now>Long.MAX_VALUE-ticks)throw new IllegalArgumentException("Invalid cooldown.");return now+ticks;
    }
}
