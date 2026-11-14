package village.fieldhud;
/** Pure actual visual budgets/count routing: no Minecraft or graphic context. */
public final class HotbarFxRulesChecks {
 private static int checks;private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 public static void main(String[] args){
  for(int fps:new int[]{20,30,60,144,240,1000}){
   FableHudMath.TickGate clock=new FableHudMath.TickGate();FableHudMath.Budget particles=new FableHudMath.Budget(32);int steps=0,total=0;
   for(int frame=0;frame<fps*5;frame++){int tick=(int)((long)frame*20/fps);steps+=clock.step(tick);total+=particles.take(tick,9);}
   check(steps==99,"Render FPS multiplied simulation steps");check(total<=100*32,"Render FPS multiplied particle budget");
  }
  FableHudMath.TickGate clock=new FableHudMath.TickGate();check(clock.step(40)==0&&clock.step(40)==0&&clock.step(41)==1,"First/same/new tick behavior");check(clock.step(1000000)==5,"Long gap simulation unbounded");check(clock.step(1)==0&&clock.step(2)==1,"World clock rollback invalid");clock.reset();check(clock.step(400)==0,"Context reset retained clock");
  FableHudMath.Budget cap=new FableHudMath.Budget(32);check(cap.take(1,Integer.MAX_VALUE)==32&&cap.take(1,1)==0,"Per tick cap");check(cap.take(2,-10)==0&&cap.take(2,32)==32,"Negative emission request spent budget");check(cap.take(0,99)==32,"Changed context tick did not reset cap");cap.reset();check(cap.take(0,99)==32,"Explicit context reset retained budget");
  for(int count:new int[]{6,9})for(int i=-100;i<=100;i++){double slot=FableHudMath.selector(i,count);check(slot>=0&&slot<count,"Selector escaped physical bank");}
  check(FableHudMath.selector(8,6)==5&&FableHudMath.selector(8,9)==8,"Six/nine bank identity lost");check(FableHudMath.selector(Double.NaN,6)==0,"NaN selector unsafe");
  FableHudMath.Spring spring=new FableHudMath.Spring(.4,.55,0);spring.target=Double.NaN;check(Double.isFinite(spring.step(1)),"Nonfinite target poisoned style");spring.velocity=Double.POSITIVE_INFINITY;check(Double.isFinite(spring.step(1)),"Nonfinite velocity poisoned style");spring.target=4;check(spring.step(Double.POSITIVE_INFINITY)==4,"Unbounded gap failed snap");
  System.out.println("HOTBAR_FX_RULES_PASS: "+checks);
 }
}
