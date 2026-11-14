package village.vale.clientcompat;
public final class PhysicalChargeKeyChecks {
 private static int checks;
 private static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
 private static boolean event(int binding,int key,boolean down,boolean repeat,boolean playing){return PhysicalChargeKey.allow(binding,key,down,repeat,playing);}
 public static void main(String[] ignored){
  int calls=0;if(event(46,46,true,false,true))calls++;
  check(calls==1,"first matching physical C press reaches native toggle once");
  check(!event(46,17,true,false,true)&&!event(46,30,true,false,true)&&!event(46,47,true,false,true),"W/A/V while C is held cannot replay C toggle");
  for(int n=0;n<100;n++)if(event(46,46,true,true,true))calls++;
  check(calls==1,"OS repeat cannot replay toggle or reset its timer");
  check(!event(46,46,false,false,true),"key release preserves original no-toggle behavior");
  if(event(46,46,true,false,true))calls++;check(calls==2,"next intentional press reaches original stop toggle");
  check(!event(47,46,true,false,true)&&event(47,47,true,false,true),"current rebound key accepted; old binding rejected");
  check(!event(0,0,true,false,true)&&!event(-100,-100,true,false,true),"unbound and non-keyboard values never manufacture keyboard input");
  check(!event(46,46,true,false,false),"GUI/loading/disconnected context cannot trigger");
  check(event(46,46,true,false,true),"new valid context admits a new physical press without stale latch");
  check(!event(46,0,true,false,true),"character-only event cannot trigger a held key");
  System.out.println("PHYSICAL_CHARGE_KEY_CHECKS_PASS assertions="+checks);
 }
}
