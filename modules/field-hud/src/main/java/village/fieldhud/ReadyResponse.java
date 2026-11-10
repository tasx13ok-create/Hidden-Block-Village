package village.fieldhud;

/** Presentation-only cue for a received cooldown-to-ready edge, never a timer. */
public final class ReadyResponse {
    private String order="";
    private boolean primed,skillReady,weaponReady;
    private double last=-1000,skillAt=-1000,weaponAt=-1000;
    public void reset(){order="";primed=false;last=skillAt=weaponAt=-1000;}
    public void silence(){skillAt=weaponAt=-1000;}
    public void accept(String nextOrder,boolean skill,boolean weapon,double time,boolean motion) {
        if(nextOrder==null||!Double.isFinite(time)){reset();return;}
        if(time<last)reset();
        if(primed) {
            if(!order.equals(nextOrder))skillAt=-1000;
            else if(skill&&!skillReady)skillAt=time;
            if(weapon&&!weaponReady)weaponAt=time;
        }
        order=nextOrder;skillReady=skill;weaponReady=weapon;last=time;primed=true;
        if(!motion)silence();
    }
    private static double cue(double time,double start) {
        double age=time-start;if(!Double.isFinite(age)||age<0||age>=10)return 0;
        double rise=Math.min(1,age/1.5);rise=rise*rise*(3-2*rise);
        return Math.min(1,rise*Math.pow(1-age/10,2)/.7);
    }
    public double skill(double time){return cue(time,skillAt);}
    public double weapon(double time){return cue(time,weaponAt);}
}
