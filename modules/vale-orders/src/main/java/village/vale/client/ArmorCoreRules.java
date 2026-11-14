package village.vale.client;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Original bounded native-tick presentation state. Never changes equipment, health or chakra. */
public final class ArmorCoreRules {
    private ArmorCoreRules(){}
    public static double unit(double n){return Double.isFinite(n)?Math.max(0,Math.min(1,n)):0;}
    public static final class Power {
        private int tick=Integer.MIN_VALUE,hurt,swingAt=Integer.MIN_VALUE,hurtAt=Integer.MIN_VALUE,sparkAt=Integer.MIN_VALUE;
        private boolean swinging,wet,night;private double previous,value,danger;
        private int chargeAt=Integer.MIN_VALUE,chargeTicks,overloadAt=Integer.MIN_VALUE,claimedBurst=Integer.MIN_VALUE,claimedHit=Integer.MIN_VALUE;
        public boolean step(int now,boolean sprint,boolean swing,int damage,float hp,float max){
            return step(now,sprint,swing,damage,hp,max,false,false,false);
        }
        public boolean step(int now,boolean sprint,boolean swing,int damage,float hp,float max,boolean sneak,boolean wet,boolean night){
            if(now<0||now==tick)return false;
            if(tick!=Integer.MIN_VALUE&&now<tick){tick=Integer.MIN_VALUE;hurt=chargeTicks=0;swinging=false;swingAt=hurtAt=sparkAt=chargeAt=overloadAt=claimedBurst=claimedHit=Integer.MIN_VALUE;previous=value=0;}
            int dt=tick==Integer.MIN_VALUE?1:(int)Math.min(4L,(long)now-tick);tick=now;previous=value;
            this.wet=wet;this.night=night;danger=Float.isFinite(hp)&&Float.isFinite(max)&&max>0?unit((.35-hp/max)/.35):0;
            if(damage>hurt)hurtAt=now;hurt=Math.max(0,damage);
            if(swing&&!swinging)swingAt=now;swinging=swing;
            double target=sprint?.55:.15;
            if(swingAt!=Integer.MIN_VALUE)target=Math.max(target,.9*Math.max(0,1-((long)now-swingAt)/20.0));
            if(hurtAt!=Integer.MIN_VALUE)target=Math.max(target,Math.max(0,1-((long)now-hurtAt)/30.0));
            if(Float.isFinite(hp)&&Float.isFinite(max)&&max>0&&hp/max<.35)target=Math.max(target,.4+.6*unit((.35-hp/max)/.35));
            if(sneak&&(overloadAt==Integer.MIN_VALUE||(long)now-overloadAt>=40)){if(chargeAt==Integer.MIN_VALUE){chargeAt=now;chargeTicks=0;}else chargeTicks=Math.min(60,chargeTicks+dt);target=Math.max(target,unit(.3+chargeTicks/60.0));}
            else{chargeAt=Integer.MIN_VALUE;chargeTicks=0;}
            if(chargeAt!=Integer.MIN_VALUE&&chargeTicks>=60){overloadAt=now;chargeAt=Integer.MIN_VALUE;chargeTicks=0;}
            if(overloadAt!=Integer.MIN_VALUE&&(long)now-overloadAt<12)target=1;
            double rate=target>value?.35:.04;value=unit(value+(target-value)*(1-Math.pow(1-rate,dt)));return true;
        }
        public double sample(double partial){return unit(previous+(value-previous)*unit(partial));}
        public boolean spark(int now){if(now!=tick||now<0||now%3!=0||sparkAt==now||value<=.45)return false;sparkAt=now;return true;}
        public boolean burst(int now){if(now!=tick||overloadAt!=now||claimedBurst==now)return false;claimedBurst=now;return true;}
        public boolean hit(int now){if(now!=tick||hurtAt!=now||claimedHit==now)return false;claimedHit=now;return true;}
        public double charging(double partial){return chargeAt==Integer.MIN_VALUE?0:unit((chargeTicks+unit(partial))/60.0);}
        public double flash(double partial){return overloadAt==Integer.MIN_VALUE?0:unit(1-((long)tick-overloadAt+unit(partial))/10.0);}
        public boolean wet(){return wet;}public boolean night(){return night;}
        public double pulse(double partial,boolean motion){if(!motion)return .5;double time=tick+unit(partial),smooth=.5+.5*Math.sin(time*(.12+.25*sample(partial)));if(danger<=0)return smooth;double phase=(time*(.05+.05*danger))%1,beat=Math.max(Math.exp(-Math.pow((phase-.05)/.04,2)),.7*Math.exp(-Math.pow((phase-.22)/.04,2)));return unit(smooth*(1-danger)+beat*danger);}
    }
    public static final class Tracker<K> {
        private final Map<K,Power> states=new IdentityHashMap<>();
        private Object world,body,connection;private int tick=Integer.MIN_VALUE,emitted,sounded,delta;
        public boolean context(Object w,Object b,Object c){return w!=null&&b!=null&&c!=null&&world==w&&body==b&&connection==c;}
        public boolean begin(Object w,Object b,Object c,int now){
            if(w==null||b==null||c==null||now<0){clear();return false;}
            if(!context(w,b,c)||now<tick){clear();world=w;body=b;connection=c;}
            if(now==tick)return false;delta=tick==Integer.MIN_VALUE?1:(int)Math.min(4L,(long)now-tick);tick=now;emitted=sounded=0;return true;
        }
        public int delta(){return delta;}
        public Power get(K key){if(key==null)return null;Power found=states.get(key);if(found!=null)return found;if(states.size()>=32)return null;Power added=new Power();states.put(key,added);return added;}
        public Power find(K key){return states.get(key);}
        public void retain(Set<K> live){states.keySet().removeIf(k->!live.contains(k));}
        public boolean emit(){if(emitted>=16)return false;emitted++;return true;}
        public boolean sound(){if(sounded>=2)return false;sounded++;return true;}
        public int size(){return states.size();}
        public void clear(){states.clear();world=body=connection=null;tick=Integer.MIN_VALUE;emitted=sounded=delta=0;}
        public Set<K> identities(){Set<K> result=Collections.newSetFromMap(new IdentityHashMap<K,Boolean>());return result;}
    }
}
