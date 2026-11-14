package village.vale.arenas;
public final class RunRulesChecks {
 static int n; static void ok(boolean b){n++;if(!b)throw new AssertionError(n);}
 public static void main(String[] args){
  ok(RunRules.participant(true,false,false,false,false));
  ok(!RunRules.participant(false,false,false,false,false));
  ok(!RunRules.participant(true,true,false,false,true));
  ok(!RunRules.participant(true,false,true,false,false));
  ok(RunRules.participant(true,false,true,false,true));
  ok(!RunRules.participant(true,false,false,true,true));
  ok(!RunRules.terminal(true,true,true,true,200));
  ok(!RunRules.terminal(false,false,true,true,200));
  ok(!RunRules.terminal(false,true,false,true,200));
  ok(!RunRules.terminal(false,true,true,false,200));
  ok(!RunRules.terminal(false,true,true,true,119));
  ok(RunRules.terminal(false,true,true,true,120));
  ok(RunRules.restorable(7,7));ok(!RunRules.restorable(8,7));
  ok(RunRules.paid(false,true,1,true,false));
  ok(!RunRules.paid(true,true,1,true,false));
  ok(!RunRules.paid(false,false,1,true,false));
  ok(!RunRules.paid(false,true,0,true,false));
  ok(!RunRules.paid(false,true,1,false,false));
  ok(!RunRules.paid(false,true,1,true,true));
  ok(RunRules.phase(100,100)==1);ok(RunRules.phase(66,100)==2);ok(RunRules.phase(33,100)==3);ok(RunRules.phase(Float.NaN,100)==0);
  System.out.println("ARENA_RULE_CHECKS "+n);
 }
}
