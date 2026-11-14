package village.vale.client;

import java.util.Set;

/** Bounded identity/lifecycle/native-tick rules exercised without opening a client. */
public final class ArmorCoreRulesChecks {
    private static int checks;
    private static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    private static final class SameId { @Override public boolean equals(Object other){return other instanceof SameId;} @Override public int hashCode(){return 7;} }
    public static void main(String[] ignored){
        Object world=new Object(),body=new Object(),connection=new Object();ArmorCoreRules.Tracker<Object> cache=new ArmorCoreRules.Tracker<>();
        check(cache.begin(world,body,connection,0),"first live context");check(cache.context(world,body,connection),"actual identity context");
        SameId a=new SameId(),b=new SameId();ArmorCoreRules.Power first=cache.get(a),second=cache.get(b);check(first!=second&&cache.size()==2,"entity-id reuse cannot share state");
        check(!cache.begin(world,body,connection,0),"render repeats do not advance tick");
        for(int i=0;i<16;i++)check(cache.emit(),"tick particle budget");for(int i=0;i<100;i++)check(!cache.emit(),"tick particle cap persists across render passes");
        check(cache.begin(world,body,connection,1)&&cache.emit(),"next native tick resets emission cap");
        for(int i=0;i<1000;i++)cache.get(new Object());check(cache.size()==32&&cache.get(new Object())==null,"hard state bound");
        Set<Object> live=cache.identities();live.add(a);cache.retain(live);check(cache.size()==1&&cache.find(a)==first&&cache.find(b)==null,"departed body state removed by identity");
        check(cache.get(new Object())!=null,"cleanup frees bounded capacity");
        check(cache.begin(new Object(),body,connection,2)&&cache.size()==0,"direct non-null world swap");
        cache.get(a);world=new Object();check(cache.begin(world,body,connection,3)&&cache.size()==0,"new world identity");
        cache.get(a);body=new Object();check(cache.begin(world,body,connection,4)&&cache.size()==0,"new player body");
        cache.get(a);connection=new Object();check(cache.begin(world,body,connection,5)&&cache.size()==0,"new connection");
        cache.get(a);check(cache.begin(world,body,connection,1)&&cache.size()==0,"native tick rewind resets");
        check(cache.begin(world,body,connection,10000)&&cache.delta()==4,"long pause catchup bounded");
        cache.get(a);check(!cache.begin(null,body,connection,10001)&&cache.size()==0&&!cache.context(world,body,connection),"disconnect clears everything");
        ArmorCoreRules.Power power=new ArmorCoreRules.Power();
        for(int tick=0;tick<200;tick++)check(power.step(tick,false,false,0,20,20),"rest native tick admitted");
        check(power.sample(1)>.14&&power.sample(1)<.16,"full health at rest stays dim");
        double stable=power.sample(1);for(int i=0;i<1000;i++){check(!power.step(199,true,true,10,1,20),"same tick cannot inject newer frame input");check(power.sample(1)==stable,"frame count cannot brighten power");}
        for(int tick=200;tick<220;tick++)power.step(tick,true,false,0,20,20);check(power.sample(1)>.5,"sprint raises visual power");
        int sparked=0;for(int i=0;i<1000;i++)if(power.spark(219))sparked++;check(sparked==1,"one spark admission per wearer native tick");
        power.step(220,false,true,10,1,20);check(!power.spark(220),"spark cadence excludes other ticks");
        for(int tick=221;tick<230;tick++)power.step(tick,false,false,Math.max(0,230-tick),1,20);check(power.sample(1)>.8,"damage and critical health raise power");
        for(double partial:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,-2,0,.25,.5,1,2,Double.POSITIVE_INFINITY})check(Double.isFinite(power.sample(partial))&&power.sample(partial)>=0&&power.sample(partial)<=1,"partial ticks stay finite and bounded");
        for(int tick=230;tick<500;tick++)power.step(tick,false,false,0,20,20);check(power.sample(1)<.16,"power decays after action");
        check(power.step(0,false,false,0,Float.NaN,Float.POSITIVE_INFINITY)&&power.sample(1)<.15,"same body tick reset cannot retain old action");
        check(!power.step(-1,true,true,20,0,0),"invalid native tick rejected");
        for(int tick=1;tick<1000;tick++){power.step(tick,tick%5==0,tick%7==0,tick%11==0?10:0,tick%3==0?Float.NaN:Float.MAX_VALUE,tick%4==0?0:Float.MIN_VALUE);check(Double.isFinite(power.sample(.5))&&power.sample(.5)>=0&&power.sample(.5)<=1,"adversarial native readings bounded");}
        check(ArmorCoreRules.unit(Double.NaN)==0&&ArmorCoreRules.unit(-1)==0&&ArmorCoreRules.unit(2)==1,"unit sanitation");
        ArmorCoreRules.Power charge=new ArmorCoreRules.Power();int overloads=0;
        for(int tick=0;tick<171;tick++){
            charge.step(tick,false,false,0,20,20,true,false,false);
            if(charge.burst(tick)){overloads++;check(tick==60||tick==160,"three-second hold and two-second cooldown");for(int frame=0;frame<1000;frame++)check(!charge.burst(tick),"overload receipt cannot replay in render passes");}
            if(tick==59)check(charge.charging(0)>.98&&charge.flash(0)==0,"hold reaches charge before overload");
            if(tick==60)check(charge.charging(0)==0&&charge.flash(0)==1,"overload resets gathering ring and starts flash");
            if(tick>60&&tick<100)check(charge.charging(0)==0,"continuous sneak respects cooldown");
        }
        check(overloads==2,"held sneak produces bounded recurring overloads");
        ArmorCoreRules.Power interrupted=new ArmorCoreRules.Power();for(int tick=0;tick<31;tick++)interrupted.step(tick,false,false,0,20,20,true,false,false);
        check(interrupted.charging(0)>.49,"half hold builds cosmetic charge");interrupted.step(31,false,false,0,20,20,false,false,false);check(interrupted.charging(0)==0,"releasing sneak cancels charge");
        for(int tick=32;tick<92;tick++){interrupted.step(tick,false,false,0,20,20,true,false,false);check(!interrupted.burst(tick),"fresh hold must earn full interval");}interrupted.step(92,false,false,0,20,20,true,false,false);check(interrupted.burst(92),"fresh complete hold overloads");
        ArmorCoreRules.Power skipped=new ArmorCoreRules.Power();skipped.step(0,false,false,0,20,20,true,false,false);skipped.step(600,false,false,0,20,20,true,false,false);check(skipped.charging(0)<.1&&!skipped.burst(600),"missed observations cannot instantly charge");
        ArmorCoreRules.Power environment=new ArmorCoreRules.Power();environment.step(10,false,false,10,20,20,false,true,true);check(environment.wet()&&environment.night(),"wet and dark environment captured on native tick");check(environment.hit(10)&&!environment.hit(10),"hit spray receipt once per native hurt rise");
        check(!environment.step(10,true,true,20,1,20,true,false,false)&&environment.wet()&&environment.night(),"render-like repeat cannot change environment or rearm hit");environment.step(11,false,false,9,20,20,false,false,false);check(!environment.hit(11)&&!environment.wet()&&!environment.night(),"hurt countdown is not another hit");
        ArmorCoreRules.Power critical=new ArmorCoreRules.Power();critical.step(0,false,false,0,0,20,false,false,false);double firstBeat=critical.pulse(.5,true);critical.step(2,false,false,0,0,20,false,false,false);double secondBeat=critical.pulse(.2,true);critical.step(5,false,false,0,0,20,false,false,false);double valley=critical.pulse(0,true);check(firstBeat>.95&&secondBeat>.65&&valley<.05,"low-health pulse has two distinct beats");
        for(double fraction:new double[]{Double.NaN,-2,0,.1,.5,1,2,Double.POSITIVE_INFINITY}){check(critical.pulse(fraction,false)==.5,"reduced motion has static heartbeat");check(Double.isFinite(critical.pulse(fraction,true))&&critical.pulse(fraction,true)>=0&&critical.pulse(fraction,true)<=1,"heartbeat finite and bounded");}
        ArmorCoreRules.Tracker<Object> audio=new ArmorCoreRules.Tracker<>();audio.begin(world,body,connection,1);check(audio.sound()&&audio.sound(),"two nearby overload sounds admitted per native tick");for(int frame=0;frame<1000;frame++)check(!audio.sound(),"crowded overload audio is bounded");check(!audio.begin(world,body,connection,1)&&!audio.sound(),"repeated pass cannot reset audio budget");check(audio.begin(world,body,connection,2)&&audio.sound(),"next native tick resets audio budget");
        System.out.println("ARMOR_CORE_RULES_PASS checks="+checks);
    }
}
