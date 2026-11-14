package village.vale.client;
/** Pure observed-health/landing feedback. Attack input never implies a landed hit. */
public final class VfxFeedback {
 public double trauma,punch;private double health=-1,fall;private boolean ground=true;
 public void clear(){trauma=punch=fall=0;health=-1;ground=true;}
 public void sample(double hp,double absorption,double max,boolean onGround,double fallDistance,int ticks){
  if(!Double.isFinite(hp)||!Double.isFinite(absorption)||!Double.isFinite(max)||hp<0||absorption<0||max<=0||!Double.isFinite(fallDistance)){clear();return;}
  double total=hp+absorption;if(health>=0&&total<health-.01)trauma=Math.min(.8,trauma+.15+(health-total)/Math.max(1,max)*1.8);health=total;
  if(!onGround)fall=Math.max(fall,Math.max(0,fallDistance));if(onGround&&!ground&&fall>3.5)trauma=Math.min(1,trauma+Math.min(.7,(fall-3)*.08));if(onGround)fall=0;ground=onGround;
  int dt=Math.max(1,Math.min(4,ticks));trauma*=Math.pow(.86,dt);punch*=Math.pow(.6,dt);if(trauma<.002)trauma=0;if(punch<.002)punch=0;
 }
 public void acceptedSwing(boolean wide){punch=Math.min(1,punch+(wide?.6:.3));}
}
