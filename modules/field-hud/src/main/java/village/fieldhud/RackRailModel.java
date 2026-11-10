package village.fieldhud;

/** Six storage indices and presentation motion only. Never holds or invents an item. */
public final class RackRailModel {
    public static final int SIZE=6;
    private int selected;
    private boolean ready,focused;
    private double rowFrom,rowAt,focusFrom,focusTarget,focusAt;
    public void reset(){selected=0;ready=focused=false;rowFrom=rowAt=focusFrom=focusTarget=focusAt=0;}
    public boolean accept(boolean available,int actualSelected,double now,boolean motion){
        if(!available||actualSelected<0||actualSelected>=SIZE||!Double.isFinite(now)){
            focus(false,Double.isFinite(now)?now:0,false);ready=false;return false;
        }
        if(!ready){selected=actualSelected;rowFrom=selected;rowAt=now;}
        else if(selected!=actualSelected)move(actualSelected,now,motion);
        ready=true;return true;
    }
    public boolean ready(){return ready;}
    public int index(){return selected;}
    public boolean focused(){return focused;}
    public boolean focus(boolean value,double now,boolean motion){
        value=value&&ready;
        if(focused!=value){focusFrom=focus(now,true);focusTarget=value?1:0;focusAt=now;focused=value;}
        if(!motion)focusFrom=focusTarget=value?1:0;
        return focused;
    }
    public int wheelTarget(int delta){return delta==0?selected:(selected+(delta>0?-1:1)+SIZE)%SIZE;}
    public boolean select(int index,double now,boolean motion){
        if(!ready||index<0||index>=SIZE||!Double.isFinite(now))return false;
        move(index,now,motion);return true;
    }
    private void move(int index,double now,boolean motion){rowFrom=selectedRow(now,motion);rowAt=now;selected=index;if(!motion)rowFrom=selected;}
    public double selectedRow(double now,boolean motion){return motion?mix(rowFrom,selected,now-rowAt,3):selected;}
    public double focus(double now,boolean motion){return motion?mix(focusFrom,focusTarget,now-focusAt,4):focused?1:0;}
    private static double mix(double a,double b,double age,double duration){double t=Math.max(0,Math.min(1,age/duration));return a+(b-a)*t*t*(3-2*t);}
}
