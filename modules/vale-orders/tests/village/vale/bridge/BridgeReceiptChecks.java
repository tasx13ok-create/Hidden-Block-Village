package village.vale.bridge;
import java.util.*;

/** Mutable final-event/callback and session tests for the actual production receipt engine. */
public final class BridgeReceiptChecks {
    private static int checks;private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static final class Context implements BridgeReceipts.Context {boolean valid=true;public boolean valid(){return valid;}}
    private static final class Events implements BridgeReceipts.FinalEvents {boolean attack,hurt,damage;double hurtAmount=8,damageAmount;Runnable callback;public boolean attackCanceled(){return attack;}public boolean hurtCanceled(){return hurt;}public double hurtAmount(){return hurtAmount;}public boolean damageCanceled(){return damage;}public double damageAmount(){if(callback!=null)callback.run();return damageAmount;}}
    private static BridgeReceipts.Damage damage(Context c,Events e){BridgeReceipts.Damage d=new BridgeReceipts.Damage(c,20,10);d.matched(e);return d;}
    public static void main(String[] ignored){
        Context c=new Context();check(BridgeReceipts.spawn(c,false,true,true,true),"final admitted owned effect");
        check(!BridgeReceipts.spawn(c,true,true,true,true),"late-canceled spawn rejected");check(!BridgeReceipts.spawn(c,false,false,true,true),"changed entity UUID/body rejected");check(!BridgeReceipts.spawn(c,false,true,false,true),"spawn intent without native membership rejected");check(!BridgeReceipts.spawn(c,false,true,true,false),"owner changes rejected");c.valid=false;check(!BridgeReceipts.spawn(c,false,true,true,true),"stale body/world/session rejected");
        c=new Context();Events e=new Events();BridgeReceipts.Damage d=damage(c,e);check(!d.accepted(),"hurt/damage intent before unwind is insufficient");d.observe(20,6,false);check(d.accepted(),"Magma finalDamage0 then real absorption loss accepted");
        e.damage=true;check(!d.accepted(),"mutable final cancellation still wins after observed loss");e.damage=false;e.attack=true;check(!d.accepted(),"final attack cancellation rejected");e.attack=false;e.hurt=true;check(!d.accepted(),"final hurt cancellation rejected");e.hurt=false;e.hurtAmount=0;check(!d.accepted(),"zero final hurt rejected");e.hurtAmount=8;e.damageAmount=Double.NaN;check(!d.accepted(),"nonfinite final damage rejected");e.damageAmount=-1;check(!d.accepted(),"negative final damage rejected");
        e=new Events();d=damage(c,e);d.observe(20,10,true);d.observe(20,2,false);check(!d.accepted(),"zero-loss first contact cannot inherit unrelated later absorption loss");
        d=damage(c,e);d.observe(20,8,true);d.observe(20,10,false);check(d.accepted(),"accepted first loss survives later heal/source boundary");
        c.valid=false;check(!d.accepted(),"body replacement after accepted native loss drops cosmetic receipt");
        c=new Context();final Context target=c;e=new Events();d=damage(c,e);d.observe(20,8,false);e.callback=()->target.valid=false;check(!d.accepted(),"context replaced during final-event callback is checked last");
        c=new Context();e=new Events();e.damageAmount=2;d=damage(c,e);d.observe(18,10,false);check(d.accepted(),"real health loss accepted");d=new BridgeReceipts.Damage(c,20,0);d.observe(18,0,false);check(!d.accepted(),"health mutation without matching final event refused");
        e=new Events();e.damageAmount=0;d=damage(c,e);d.observe(20,10,false);check(!d.accepted(),"uncanceled Damage0 with no actual loss refused");d.observe(20,Double.NaN,false);check(!d.accepted(),"NaN outcome refused");
        BridgeReceipts.Budget budget=new BridgeReceipts.Budget();UUID owner=UUID.randomUUID();check(budget.take(owner,"native-1",100),"first accepted effect admitted");check(!budget.take(owner,"native-2",100),"same-tick owner bound");check(!budget.take(owner,"native-2",103),"four-tick owner bound");check(budget.take(owner,"native-2",104),"next bounded effect");check(!budget.take(owner,"native-1",108),"same admitted receipt cannot replay");
        budget.clear();for(int i=0;i<8;i++)check(budget.take(UUID.randomUUID(),"r"+i,200),"global admitted effect slot");check(!budget.take(UUID.randomUUID(),"overflow",200),"global tick budget prevents flood");check(budget.take(UUID.randomUUID(),"later",201),"next tick has a fresh global budget");check(!budget.take(owner,"invalid",-1),"negative clock refused");
        System.out.println("BRIDGE_RECEIPT_CHECKS_PASS assertions="+checks+" native_endpoint=false");
    }
}
