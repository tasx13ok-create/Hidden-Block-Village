package village.vale.melee;

public final class MeleeComboChecks {
    private static int assertions;
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        MeleeComboRules r=new MeleeComboRules();
        check(r.preview("a","x",100)==1&&r.count()==0,"Preview mutates progress");
        check(r.accept("a","x",100)==1,"First contact refused");
        check(r.accept("a","x",100)==0&&r.accept("a","x",104)==0&&r.count()==1,"Spam advanced progress");
        check(r.accept("a","x",105)==2,"Valid paced second contact refused");
        check(r.accept("a","x",169)==3&&r.count()==0&&r.readyAt()==249,"Window edge or recovery drifted");
        check(r.accept("a","x",248)==0,"Finisher recovery was skipped");
        r.interrupt();check(r.accept("b","y",248)==0,"Changing equipment erased recovery");
        check(r.accept("b","y",249)==1,"Recovery edge did not reopen");
        check(r.accept("b","z",260)==1,"Target swap retained progress");
        check(r.accept("c","z",270)==1,"Weapon swap retained progress");
        check(r.accept("c","z",335)==1,"Expired chain retained progress");
        check(r.accept("c","z",300)==1,"Rewound time credited stale progress");
        r.interrupt();check(r.count()==0&&r.accept("c","z",310)==1,"Interrupt retained chain");
        check(r.preview(null,"x",320)==0&&r.preview("","x",320)==0&&r.preview("a",null,320)==0,"Empty identity accepted");
        MeleeComboRules restored=new MeleeComboRules();restored.restoreRecovery(500);
        check(restored.accept("a","x",499)==0&&restored.accept("a","x",500)==1,"Reconnect erased recovery");
        for(int i=0;i<10000;i++){
            int value=restored.accept("a","x",501+i);
            if(value<0||value>3||restored.count()>2)throw new AssertionError("Unbounded combo");
        }
        check(restored.count()<=2,"Long sequence grew state");
        System.out.println("PASS: "+assertions+" melee cadence and recovery assertions.");
    }
}
