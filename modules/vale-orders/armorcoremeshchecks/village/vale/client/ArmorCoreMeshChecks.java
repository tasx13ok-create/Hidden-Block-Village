package village.vale.client;
import java.util.List;
import net.minecraft.client.renderer.Tessellator;
/** Executes actual original cube source with a synthetic vertex recorder, not a GPU. */
public final class ArmorCoreMeshChecks {
 private static int checks;private static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
 private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
 public static void main(String[] args){
  Tessellator t=Tessellator.INSTANCE;
  for(double size:new double[]{.00001,.025,.25,1,2}){
   int before=t.draws;ArmorCoreGeometry.cube(1.25,2.5,-3.75,size,0x806040,.7f);check(t.draws==before+1&&!t.buffer.building,"one completed draw");List<double[]> v=t.buffer.vertices;check(v.size()==24,"six bounded four-vertex faces");
   for(double[] p:v){check(Math.abs(Math.abs(p[0]-1.25)-size/2)<1e-9&&Math.abs(Math.abs(p[1]-2.5)-size/2)<1e-9&&Math.abs(Math.abs(p[2]+3.75)-size/2)<1e-9,"all vertices lie on actual cube corners");}
   for(int face=0;face<6;face++){double[] a=v.get(face*4),b=v.get(face*4+1),c=v.get(face*4+2),d=v.get(face*4+3),u=sub(b,a),w=sub(c,a);double[] n={u[1]*w[2]-u[2]*w[1],u[2]*w[0]-u[0]*w[2],u[0]*w[1]-u[1]*w[0]};double mx=(a[0]+b[0]+c[0]+d[0])/4-1.25,my=(a[1]+b[1]+c[1]+d[1])/4-2.5,mz=(a[2]+b[2]+c[2]+d[2])/4+3.75;check(n[0]*mx+n[1]*my+n[2]*mz>0,"cube winding faces outwards");check(a[3]==b[3]&&a[3]==c[3]&&a[3]==d[3],"uniform shading within each face");}
  }
  int before=t.draws;
  for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.MAX_VALUE}){ArmorCoreGeometry.cube(bad,0,0,.1,0xffffff,1);ArmorCoreGeometry.cube(0,bad,0,.1,0xffffff,1);ArmorCoreGeometry.cube(0,0,bad,.1,0xffffff,1);ArmorCoreGeometry.cube(0,0,0,bad,0xffffff,1);}
  for(double bad:new double[]{-1,0,Double.MIN_VALUE,.000001,2.01})ArmorCoreGeometry.cube(0,0,0,bad,0xffffff,1);
  for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-1,0,.003f})ArmorCoreGeometry.cube(0,0,0,.1,0xffffff,bad);
  check(t.draws==before&&!t.buffer.building,"invalid geometry never opens a draw");
  check(ArmorCoreGeometry.pixel(Double.MAX_VALUE)==0&&ArmorCoreGeometry.pixel(Double.NaN)==0,"pixel snap cannot overflow");
  check(ArmorCoreGeometry.pixel(.064)==.0625&&ArmorCoreGeometry.pixel(-.064)==-.0625,"voxel grid spacing and sign");
  check(ArmorCoreGeometry.shade(0xffffff,1.2)==0xffffff&&ArmorCoreGeometry.shade(0xffffff,Double.NaN)==0&&ArmorCoreGeometry.shade(0xffffff,-1)==0,"shade channels bounded");
  System.out.println("ARMOR_CORE_MESH_SYNTHETIC_PASS checks="+checks);
 }
}
