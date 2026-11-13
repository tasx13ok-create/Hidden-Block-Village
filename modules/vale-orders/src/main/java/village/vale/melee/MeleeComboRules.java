package village.vale.melee;

/** Pure, bounded accepted-contact cadence. Time is server ticks, never click count. */
public final class MeleeComboRules {
    public static final int WINDOW=64, RECOVERY=80, MIN_SPACING=5;
    private String weapon="", target="";
    private long last=Long.MIN_VALUE, ready;
    private int count;
    public int preview(String weapon,String target,long now) {
        if(weapon==null||weapon.isEmpty()||target==null||target.isEmpty())return 0;
        if(now<ready)return 0;
        if(last!=Long.MIN_VALUE&&now>=last&&now-last<MIN_SPACING)return 0;
        return now>=last&&now-last<=WINDOW&&this.weapon.equals(weapon)&&this.target.equals(target)?count+1:1;
    }
    public int accept(String weapon,String target,long now) {
        int step=preview(weapon,target,now);
        if(step==0)return 0;
        this.weapon=weapon;this.target=target;last=now;
        if(step>=3){count=0;ready=now+RECOVERY;return 3;}
        count=step;return step;
    }
    /** Losing a target/weapon drops the chain but cannot erase an earned recovery. */
    public void interrupt(){count=0;weapon="";target="";}
    public void restoreRecovery(long ready){this.ready=Math.max(this.ready,ready);}
    public int count(){return count;}
    public long readyAt(){return ready;}
}
