package village.fieldhud;
/** Original pure safety/layout clocks for cosmetic menus; never handles input or gameplay. */
public final class MenuFxRules{
 private MenuFxRules(){}
 public static boolean animated(boolean enabled,boolean motion,int mode){return enabled&&motion&&mode!=2;}
 public static boolean audible(boolean enabled,boolean motion,boolean menuSounds,int mode){return enabled&&motion&&menuSounds&&mode!=2;}
 public static final class Clock{
  private long previous=Long.MIN_VALUE,budgetTick=Long.MIN_VALUE;private int emitted;
  public int step(double ticks){if(!Double.isFinite(ticks)||ticks<0)return 0;long now=(long)Math.floor(ticks);if(previous==Long.MIN_VALUE||now<previous){previous=now;return 0;}int delta=(int)Math.min(4L,now-previous);previous=now;return delta;}
  public boolean emit(double ticks){if(!Double.isFinite(ticks)||ticks<0)return false;long now=(long)Math.floor(ticks);if(now!=budgetTick){budgetTick=now;emitted=0;}if(emitted>=16)return false;emitted++;return true;}
  public void reset(){previous=budgetTick=Long.MIN_VALUE;emitted=0;}
 }
 public static int[][] options(int width,int height){
  int w=Math.max(1,Math.min(220,width-12)),x=Math.max(0,(width-w)/2);
  if(width<180||height<140){int y=Math.max(0,Math.min(height-20,Math.max(0,(height-136)/2)+104));return new int[][]{{x,y,w,Math.max(1,Math.min(18,height-y))}};}
  int y=Math.max(30,(height-136)/2+30);return new int[][]{{x,y,w,20},{x,y+24,w,20},{x,y+48,w,20},{x,y+80,w,20}};
 }
 public static int nextFocus(int from,int direction,boolean[] enabled){if(enabled==null||enabled.length==0||enabled.length>4)return -1;int count=enabled.length,step=direction<0?-1:1,index=from<0||from>=count?(step>0?-1:0):from;for(int i=0;i<count;i++){index=(index+step+count)%count;if(enabled[index])return index;}return -1;}
 public static final class Mask{
  private final int width,height;private double left,top,right,bottom;private boolean blocked;
  public Mask(int sw,int sh,int x,int y,int w,int h){width=sw;height=sh;blocked=sw<1||sh<1||sw>16384||sh>16384||w<1||h<1||w>32768||h>32768;left=x-6.0;top=y-6.0;right=(double)x+w+6;bottom=(double)y+h+6;}
  public void include(double x,double y,double w,double h){if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(w)||!Double.isFinite(h)||w<0||h<0){blocked=true;return;}left=Math.min(left,x-4);top=Math.min(top,y-4);right=Math.max(right,x+w+4);bottom=Math.max(bottom,y+h+4);}
  public void block(){blocked=true;}
  public double leftMargin(){return blocked?0:Math.max(0,Math.min(width*.18,left-6));}
  public double rightMargin(){return blocked?0:Math.max(0,Math.min(width*.18,width-right-6));}
  public double bottomStart(){return blocked?height:Math.max(height*.8,Math.min(height,bottom+6));}
  public boolean allows(double x,double y,double radius){if(blocked||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(radius)||radius<0)return false;return x-radius>=0&&x+radius<=width&&y-radius>=0&&y+radius<=height&&(x+radius<left||x-radius>right||y+radius<top||y-radius>bottom);}
  public boolean allowsRectangle(double x,double y,double w,double h){if(blocked||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(w)||!Double.isFinite(h)||w<=0||h<=0)return false;return x>=0&&y>=0&&x+w<=width&&y+h<=height&&(x+w<left||x>right||y+h<top||y>bottom);}
 }
}
