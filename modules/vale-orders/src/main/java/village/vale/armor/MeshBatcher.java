package village.vale.armor;

import java.util.*;

/** Immutable pre-baked vertices, grouped by native bone, slot, rigid pivot and render pass. */
public final class MeshBatcher {
    private static final Map<ArmorProfile,List<Batch>> CACHE=new EnumMap<>(ArmorProfile.class);
    private static final int[][] FACES={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
    private static final float[][] NORMALS={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
    private static final float[] SHADE={.88f,.67f,.8f,.74f,1f,.55f};
    private MeshBatcher(){}
    public static synchronized List<Batch> get(ArmorProfile profile){
        List<Batch> batches=CACHE.get(profile);if(batches==null){batches=bake(OriginalModels.get(profile));CACHE.put(profile,batches);}return batches;
    }
    public static List<Batch> bake(ArmorMesh mesh){
        Map<String,Mutable> groups=new LinkedHashMap<>();
        for(ArmorMesh.Box box:mesh.boxes)for(int pass=0;pass<2;pass++){
            boolean emission=pass==1;if(emission&&!mesh.materials.get(box.material).emissive)continue;
            String key=box.bone+"/"+box.slot+"/"+box.rig+"/"+pass;Mutable group=groups.get(key);
            if(group==null){group=new Mutable(box.bone,box.slot,box.rig,emission);groups.put(key,group);}
            float x=box.x,y=box.y,z=box.z,w=box.w,h=box.h,d=box.d;
            float[][] vertices={{x,y,z},{x+w,y,z},{x+w,y+h,z},{x,y+h,z},{x,y,z+d},{x+w,y,z+d},{x+w,y+h,z+d},{x,y+h,z+d}};
            for(int face=0;face<6;face++){
                float[][] uv=AtlasUv.face(box,face);float[] normal=rotate(NORMALS[face],box);
                for(int i=0;i<4;i++){float[] p=rotate(vertices[FACES[face][i]],box);group.vertices.add(new Vertex(p[0],p[1],p[2],uv[i][0],uv[i][1],normal[0],normal[1],normal[2],emission?1:SHADE[face]));}
            }
        }
        List<Batch> result=new ArrayList<>();for(Mutable group:groups.values())result.add(new Batch(group));return Collections.unmodifiableList(result);
    }
    private static float[] rotate(float[] p,ArmorMesh.Box b){
        double x=p[0],y=p[1],z=p[2];double yy=y*Math.cos(b.rx)-z*Math.sin(b.rx),zz=y*Math.sin(b.rx)+z*Math.cos(b.rx);y=yy;z=zz;
        double xx=x*Math.cos(b.ry)+z*Math.sin(b.ry);zz=-x*Math.sin(b.ry)+z*Math.cos(b.ry);x=xx;z=zz;
        return new float[]{(float)(x*Math.cos(b.rz)-y*Math.sin(b.rz)),(float)(x*Math.sin(b.rz)+y*Math.cos(b.rz)),(float)z};
    }
    public static final class Vertex{
        public final float x,y,z,u,v,nx,ny,nz,shade;
        Vertex(float x,float y,float z,float u,float v,float nx,float ny,float nz,float shade){this.x=x;this.y=y;this.z=z;this.u=u;this.v=v;this.nx=nx;this.ny=ny;this.nz=nz;this.shade=shade;}
    }
    public static final class Batch{
        public final ArmorMesh.Bone bone;public final ArmorMesh.Slot slot;public final String rig;public final boolean emission;public final List<Vertex> vertices;
        Batch(Mutable group){bone=group.bone;slot=group.slot;rig=group.rig;emission=group.emission;vertices=Collections.unmodifiableList(new ArrayList<>(group.vertices));}
    }
    private static final class Mutable{
        final ArmorMesh.Bone bone;final ArmorMesh.Slot slot;final String rig;final boolean emission;final List<Vertex> vertices=new ArrayList<>();
        Mutable(ArmorMesh.Bone bone,ArmorMesh.Slot slot,String rig,boolean emission){this.bone=bone;this.slot=slot;this.rig=rig;this.emission=emission;}
    }
}
