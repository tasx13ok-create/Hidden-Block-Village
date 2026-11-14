package village.vale.client;
import java.util.*;
/** Original finite, bounded whole-shape sampling for Claude's blocky primitive intent. */
public final class VoxelGeometry {
 private VoxelGeometry(){}public static final double PX=1/16.0;
 public static final class Cube {public final double x,y,z,size;public final float alpha;Cube(double x,double y,double z,double s,double a){this.x=x;this.y=y;this.z=z;size=s;alpha=(float)a;}}
 private static boolean point(double[] p){return p!=null&&p.length==3&&Double.isFinite(p[0])&&Double.isFinite(p[1])&&Double.isFinite(p[2])&&Math.abs(p[0])<=256&&Math.abs(p[1])<=256&&Math.abs(p[2])<=256;}
 private static boolean path(double[][] p){if(p==null||p.length<2||p.length>256)return false;for(double[] v:p)if(!point(v))return false;return true;}
 private static int cap(int budget){return Math.max(0,Math.min(384,budget));}
 private static double alpha(double a){return Double.isFinite(a)?Math.max(0,Math.min(1,a)):0;}
 private static double dist(double[] a,double[] b){double x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];return Math.sqrt(x*x+y*y+z*z);}
 private static double grid(double n,double size){double snapped=Math.rint(n/size)*size;return snapped==0?0:snapped;}
 private static double size(double n){return Math.max(PX,Math.min(16,Math.ceil(n/PX)*PX));}
 private static double[] lerp(double[] a,double[] b,double f){return new double[]{a[0]+(b[0]-a[0])*f,a[1]+(b[1]-a[1])*f,a[2]+(b[2]-a[2])*f};}
 private static void cube(List<Cube> out,Map<String,Integer> seen,double[] p,double s,double a){if(a<=.003)return;double x=grid(p[0],s),y=grid(p[1],s),z=grid(p[2],s);String key=x+":"+y+":"+z;Integer index=seen.get(key);Cube cube=new Cube(x,y,z,s,a);if(index==null){seen.put(key,out.size());out.add(cube);}else if(out.get(index).alpha<a)out.set(index,cube);}
 public static List<Cube> line(double[][] points,double a0,double a1,double width,int budget){
  int n=cap(budget);List<Cube> out=new ArrayList<>();if(n==0||!path(points)||!Double.isFinite(width)||width<=0||width>128||Math.max(alpha(a0),alpha(a1))<=.003)return out;
  double[] lengths=new double[points.length];for(int i=1;i<points.length;i++)lengths[i]=lengths[i-1]+dist(points[i-1],points[i]);double total=lengths[lengths.length-1],s=size(Math.max(width*.0075*1.15,total/Math.max(1,n-1)));Map<String,Integer> seen=new HashMap<>();int segment=1;
  for(int i=0;i<n;i++){double f=n==1?.5:i/(double)(n-1),at=total*f;while(segment<points.length-1&&lengths[segment]<at)segment++;double span=lengths[segment]-lengths[segment-1],t=span<1e-12?0:(at-lengths[segment-1])/span;cube(out,seen,lerp(points[segment-1],points[segment],t),s,alpha(a0)+(alpha(a1)-alpha(a0))*f);}return out;
 }
 public static List<Cube> sphere(double x,double y,double z,double radius,double opacity,int budget){
  int n=cap(budget);List<Cube> out=new ArrayList<>();if(n==0||!point(new double[]{x,y,z})||!Double.isFinite(radius)||radius<=.001||radius>128||alpha(opacity)<=.003)return out;
  double s=size(radius/4),cx=grid(x,s),cy=grid(y,s),cz=grid(z,s);int cells=(int)Math.ceil(radius/s),count=0;
  for(int i=-cells;i<=cells;i++)for(int j=-cells;j<=cells;j++)for(int k=-cells;k<=cells;k++){double d=Math.sqrt(i*i+j*j+k*k)*s;if(d<=radius&&(cells<=2||d>=radius-s*1.1))count++;}
  int take=Math.min(n,count),index=0,next=0,wanted=0;for(int i=-cells;i<=cells;i++)for(int j=-cells;j<=cells;j++)for(int k=-cells;k<=cells;k++){double d=Math.sqrt(i*i+j*j+k*k)*s;if(d>radius||(cells>2&&d<radius-s*1.1))continue;if(wanted<take&&index==next){out.add(new Cube(cx+i*s,cy+j*s,cz+k*s,s,alpha(opacity)));wanted++;next=take<=1?count:(int)Math.floor(wanted*(count-1.0)/(take-1));}index++;}return out;
 }
 public static List<Cube> ribbon(double[][] inner,double[][] outer,double a0,double a1,int budget){
  int limit=cap(budget);List<Cube> out=new ArrayList<>();if(limit==0||!path(inner)||!path(outer)||inner.length!=outer.length)return out;double length=0,width=PX;for(int i=0;i<inner.length;i++){width=Math.max(width,dist(inner[i],outer[i]));if(i>0)length+=Math.max(dist(inner[i-1],inner[i]),dist(outer[i-1],outer[i]));}
  int along=Math.max(1,Math.min(limit>=4?limit/2:limit,(int)Math.ceil(Math.sqrt(limit*Math.max(PX,length)/width)))),across=Math.max(1,limit/along);double s=size(Math.max(length/Math.max(1,along-1),width/Math.max(1,across-1)));Map<String,Integer> seen=new HashMap<>();
  for(int u=0;u<along;u++){double f=along==1?.5:u/(double)(along-1),v=f*(inner.length-1);int i=Math.min(inner.length-2,(int)v);double t=v-i;double[] a=lerp(inner[i],inner[i+1],t),b=lerp(outer[i],outer[i+1],t);for(int k=0;k<across;k++)cube(out,seen,lerp(a,b,across==1?.5:k/(double)(across-1)),s,alpha(a0)+(alpha(a1)-alpha(a0))*f);}return out;
 }
}
