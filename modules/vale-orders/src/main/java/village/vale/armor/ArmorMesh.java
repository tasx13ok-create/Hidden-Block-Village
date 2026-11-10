package village.vale.armor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, closed-cuboid model data; all positions are in native player-bone coordinates. */
public final class ArmorMesh {
    public enum Bone { BODY, HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }
    public enum Slot { HEAD, CHEST, LEGS, FEET }
    public final ArmorProfile profile;
    public final List<Box> boxes;
    public final Map<String,Rig> rigs;
    public final Map<String,Material> materials;

    private ArmorMesh(Builder b) {
        profile=b.profile;
        boxes=Collections.unmodifiableList(new ArrayList<Box>(b.boxes));
        rigs=Collections.unmodifiableMap(new LinkedHashMap<String,Rig>(b.rigs));
        materials=Collections.unmodifiableMap(new LinkedHashMap<String,Material>(b.materials));
        for(Box box:boxes) if(!rigs.containsKey(box.rig)||!materials.containsKey(box.material))
            throw new IllegalArgumentException("Unresolved rig/material in "+box.id);
    }

    public static final class Material {
        public final int rgb;
        public final boolean emissive;
        Material(int rgb,boolean emissive) {this.rgb=rgb;this.emissive=emissive;}
    }
    public static final class Rig {
        public final String id,motion;
        public final float x,y,z;
        Rig(String id,float x,float y,float z,String motion) {
            check(x,y,z);this.id=id;this.x=x;this.y=y;this.z=z;this.motion=motion;
        }
    }
    public static final class Box {
        public final String id,rig,material;
        public final Bone bone;
        public final Slot slot;
        public final float x,y,z,w,h,d,rx,ry,rz;
        Box(String id,Bone bone,Slot slot,String rig,String material,float x,float y,float z,float w,float h,float d,float rx,float ry,float rz) {
            check(x,y,z,w,h,d,rx,ry,rz);
            if(w<=0||h<=0||d<=0)throw new IllegalArgumentException("Open or zero-volume box");
            this.id=id;this.bone=bone;this.slot=slot;this.rig=rig;this.material=material;
            this.x=x;this.y=y;this.z=z;this.w=w;this.h=h;this.d=d;this.rx=rx;this.ry=ry;this.rz=rz;
        }
    }
    static void check(float... values) {
        for(float value:values)if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite model coordinate");
    }
    public static final class Builder {
        private final ArmorProfile profile;
        private final List<Box> boxes=new ArrayList<Box>();
        private final Map<String,Rig> rigs=new LinkedHashMap<String,Rig>();
        private final Map<String,Material> materials=new LinkedHashMap<String,Material>();
        private final Set<String> ids=new HashSet<String>();
        public Builder(ArmorProfile profile) {if(profile==null)throw new IllegalArgumentException("Missing profile");this.profile=profile;}
        public void rig(String id,float x,float y,float z,String motion) {
            if(id==null||motion==null||rigs.containsKey(id))throw new IllegalArgumentException("Duplicate/missing rig");
            rigs.put(id,new Rig(id,x,y,z,motion));
        }
        public void material(String id,int rgb,boolean emissive) {
            if(id==null||materials.containsKey(id)||(rgb&0xff000000)!=0)throw new IllegalArgumentException("Invalid material");
            materials.put(id,new Material(rgb,emissive));
        }
        public void box(String id,Bone bone,Slot slot,String rig,String material,float x,float y,float z,float w,float h,float d,float rx,float ry,float rz) {
            if(id==null||!ids.add(id)||bone==null||slot==null)throw new IllegalArgumentException("Invalid/duplicate box");
            boxes.add(new Box(id,bone,slot,rig,material,x,y,z,w,h,d,rx,ry,rz));
        }
        public ArmorMesh build() {if(boxes.isEmpty())throw new IllegalArgumentException("Empty model");return new ArmorMesh(this);}
    }
}
