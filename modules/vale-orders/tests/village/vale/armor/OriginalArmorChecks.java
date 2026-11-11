package village.vale.armor;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Headless contract checks; this deliberately does not claim native GL/server behavior. */
public final class OriginalArmorChecks {
    private static int assertions;
    private static void ok(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void near(double a,double b,double tolerance,String message){ok(Math.abs(a-b)<=tolerance,message+": "+a+" / "+b);}
    public static void main(String[] args)throws Exception{
        File output=new File(args[0]);output.mkdirs();
        Set<String> wards=new HashSet<String>(),keys=new HashSet<String>();
        int cuboids=0;
        try(PrintWriter data=new PrintWriter(new File(output,"compiled-models.tsv"),"UTF-8");PrintWriter uvData=new PrintWriter(new File(output,"compiled-uvs.tsv"),"UTF-8")){
            for(ArmorProfile profile:ArmorProfile.values()){
                ok(keys.add(profile.key),"Unique stable key");ok(wards.add(profile.ward),"Unique ward");
                ok(ArmorProfile.byKey(profile.key)==profile,"Key resolution");
                ok(profile.maximumHealth>=32&&profile.maximumHealth<=48,"Health stays within approved envelope");
                ArmorMesh mesh=OriginalModels.get(profile);cuboids+=mesh.boxes.size();
                ok(mesh.profile==profile,"Explicit profile binding");
                ok(OriginalModels.get(profile)==mesh,"Immutable meshes are cached across renders");
                ok(mesh.boxes.size()>=75&&mesh.boxes.size()<=130,"Bounded blockout complexity");
                Set<String> ids=new HashSet<String>();Set<ArmorMesh.Slot> slots=new HashSet<ArmorMesh.Slot>();
                for(ArmorMesh.Box b:mesh.boxes){
                    ok(ids.add(b.id),"Unique part ID");slots.add(b.slot);
                    ok(b.w>0&&b.h>0&&b.d>0,"Closed positive-volume cuboid");
                    ok(mesh.rigs.containsKey(b.rig)&&mesh.materials.containsKey(b.material),"Resolved dependencies");
                    ok(b.slot!=ArmorMesh.Slot.HEAD||b.bone==ArmorMesh.Bone.HEAD,"Helmet follows head");
                    ok(b.slot!=ArmorMesh.Slot.FEET||b.bone==ArmorMesh.Bone.LEFT_LEG||b.bone==ArmorMesh.Bone.RIGHT_LEG,"Boot follows leg");
                    for(float f:new float[]{b.x,b.y,b.z,b.w,b.h,b.d,b.rx,b.ry,b.rz})ok(Float.isFinite(f)&&Math.abs(f)<50,"Finite bounded geometry");
                    data.println(profile.key+"\t"+b.id+"\t"+b.bone.name().toLowerCase()+"\t"+b.slot.name().toLowerCase()+"\t"+b.rig+"\t"+b.material+
                        "\t"+b.x+"\t"+b.y+"\t"+b.z+"\t"+b.w+"\t"+b.h+"\t"+b.d+"\t"+b.rx+"\t"+b.ry+"\t"+b.rz);
                    if(b.id.endsWith("_boot")){near(b.w,4.4,1e-5,"Vanilla-like boot width");near(b.d,4.4,1e-5,"Vanilla-like boot depth");}
                    if(b.id.contains("_halo_glow_front"))near(b.w,4.4,1e-5,"Small wrist halo span");
                    if(b.id.endsWith("_sleeve"))ok(b.y+b.h>=9.53,"Halo is embedded in the wrist sleeve");
                    for(int face=0;face<6;face++){
                        float[][] uv=AtlasUv.face(b,face);int tile=AtlasUv.tile(b.material);float tx=tile%4*64,ty=tile/4*64;
                        StringBuilder line=new StringBuilder(profile.key+"\t"+b.id+"\t"+face);
                        for(float[] point:uv){ok(Float.isFinite(point[0])&&Float.isFinite(point[1])&&point[0]*256>=tx+.5&&point[0]*256<=tx+63.5&&point[1]*128>=ty+.5&&point[1]*128<=ty+63.5,"Half-texel UV guard stays in its own material tile");line.append('\t').append(point[0]).append('\t').append(point[1]);}
                        uvData.println(line.toString());
                    }
                }
                ok(slots.size()==4,"All armor slots modeled");
                List<MeshBatcher.Batch> batches=MeshBatcher.get(profile);int baseVertices=0,emissionVertices=0,expectedEmission=0;
                for(ArmorMesh.Box b:mesh.boxes)if(mesh.materials.get(b.material).emissive)expectedEmission+=24;
                for(MeshBatcher.Batch batch:batches){
                    if(batch.emission)emissionVertices+=batch.vertices.size();else baseVertices+=batch.vertices.size();
                    for(MeshBatcher.Vertex v:batch.vertices){
                        ok(Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z)&&Float.isFinite(v.u)&&Float.isFinite(v.v),"Baked vertices remain finite");
                        near(v.nx*v.nx+v.ny*v.ny+v.nz*v.nz,1,1e-5,"Baked physical face normals stay normalized");
                    }
                    verifyFaceWinding(batch);
                }
                ok(baseVertices==mesh.boxes.size()*24&&emissionVertices==expectedEmission,"Batching preserves every closed face and emission face");
                ok(batches.size()<=28&&MeshBatcher.get(profile)==batches,"Bounded cached draw groups replace per-cuboid draw calls");
                for(Map.Entry<String,ArmorMesh.Material> e:mesh.materials.entrySet()){
                    ok(e.getValue().rgb!=0,"No pure black blockout material");
                    ok(e.getValue().emissive==("glow".equals(e.getKey())||"core".equals(e.getKey())),"Only authored inset materials emit");
                }
                boolean immutable=false;try{mesh.boxes.clear();}catch(UnsupportedOperationException expected){immutable=true;}ok(immutable,"Mesh cannot be mutated by consumer");
                verifyOwnership(profile);
                for(ArmorMesh.Rig rig:mesh.rigs.values()){
                    for(int i=0;i<1200;i++){
                        RigPose.Rotation r=RigPose.at(rig.motion,i/120.0f,i%2,i%3*.5f,i%5*.25f);
                        for(float f:new float[]{r.x,r.y,r.z})ok(Float.isFinite(f)&&Math.abs(f)<=1.1,"Bounded cosmetic hinge");
                    }
                    for(double seconds:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.MAX_VALUE,-Double.MAX_VALUE}){
                        RigPose.Rotation r=RigPose.at(rig.motion,seconds,Float.NaN,Float.POSITIVE_INFINITY,-100);
                        ok(Float.isFinite(r.x)&&Float.isFinite(r.y)&&Float.isFinite(r.z),"Invalid timing cannot poison model matrices");
                    }
                    RigPose.Rotation wrapBefore=RigPose.at(rig.motion,Math.PI*10-1e-6,1,1,1),wrapAfter=RigPose.at(rig.motion,Math.PI*10+1e-6,1,1,1);
                    ok(Math.abs(wrapBefore.x-wrapAfter.x)<1e-4&&Math.abs(wrapBefore.y-wrapAfter.y)<1e-4&&Math.abs(wrapBefore.z-wrapAfter.z)<1e-4,"Periodic timing wrap has no hinge jump");
                    RigPose.Rotation last=RigPose.at(rig.motion,0,.8f,1,1);
                    for(int i=1;i<2400;i++){
                        RigPose.Rotation next=RigPose.at(rig.motion,i/240.0,.8f,1,1);
                        ok(Math.abs(last.x-next.x)<.01&&Math.abs(last.y-next.y)<.01&&Math.abs(last.z-next.z)<.01,"Continuous bounded hinge motion");last=next;
                    }
                }
                BalanceEnvelope e=BalanceEnvelope.forRole(profile.role);
                ok(e.rawDamage<=3&&e.rawDamage>=0,"Raw damage cap");
                if(profile.role==ArmorProfile.Role.COMPANION){near(e.rawDamage,3,0,"Companion damage");ok(e.cooldownTicks==24,"Companion rate");}
                else if(profile.role!=ArmorProfile.Role.FLIGHT)ok(e.cooldownTicks>=20,"No unbounded passive spam");
            }
        }
        ok(wards.size()==9&&keys.size()==9,"Complete independently keyed roster");
        ok(ArmorProfile.byKey("unissued")==null,"Unknown identity rejected");
        verifyStamina();verifyBuilderRejection();verifyFold();verifyPresentationState();verifyWingHinges();
        try(PrintWriter report=new PrintWriter(new File(output,"java-checks.json"),"UTF-8")){
            report.println("{\"status\":\"PASS\",\"assertions\":"+assertions+",\"profiles\":9,\"cuboids\":"+cuboids+",\"native_tested\":false,\"server_integrated\":false}");
        }
        System.out.println("ORIGINAL_ARMOR_HEADLESS_PASS assertions="+assertions+" cuboids="+cuboids);
    }
    private static void verifyOwnership(ArmorProfile profile){
        final UUID owner=UUID.fromString("c4892916-6a41-4afe-9081-4bac54da4183");
        final Map<String,String> issued=new HashMap<String,String>();
        EquipmentGate.Piece[] valid=new EquipmentGate.Piece[4];
        ArmorMesh.Slot[] slots=ArmorMesh.Slot.values();
        for(int i=0;i<4;i++){
            String serial=profile.key+"-issued-"+i;
            valid[i]=new EquipmentGate.Piece(profile,slots[i],profile.item(slots[i]),serial);
            issued.put(serial,owner+"/"+valid[i].registeredItem+"/"+slots[i]);
        }
        EquipmentGate.LedgerAuthority ledger=(wearer,item,serial,slot)->(wearer+"/"+item+"/"+slot).equals(issued.get(serial));
        ok(EquipmentGate.resolve(owner,valid,ledger)==profile,"Valid live issued set accepted");
        ok(EquipmentGate.resolve(UUID.randomUUID(),valid,ledger)==null,"Wrong wearer rejected");
        ok(EquipmentGate.resolve(owner,valid,null)==null,"Missing ledger rejected");
        ok(EquipmentGate.resolve(owner,valid,(a,b,c,d)->{throw new IllegalStateException("offline ledger");})==null,"Failed ledger is closed");
        ok(EquipmentGate.resolve(owner,new EquipmentGate.Piece[0],ledger)==null,"Missing pieces rejected");
        for(int i=0;i<4;i++){
            EquipmentGate.Piece[] changed=valid.clone();EquipmentGate.Piece old=valid[i];
            changed[i]=new EquipmentGate.Piece(profile,old.slot,old.registeredItem,"forged");
            ok(EquipmentGate.resolve(owner,changed,ledger)==null,"Forged serial rejected at every slot");
            changed[i]=new EquipmentGate.Piece(profile,old.slot,old.registeredItem,"   ");
            ok(EquipmentGate.resolve(owner,changed,ledger)==null,"Blank serial rejected at every slot");
            changed[i]=new EquipmentGate.Piece(profile,old.slot,"other:"+profile.key,old.serial);
            ok(EquipmentGate.resolve(owner,changed,(a,b,c,d)->true)==null,"Item identity checked before ledger");
            changed[i]=null;ok(EquipmentGate.resolve(owner,changed,ledger)==null,"Empty equipped slot rejected");
        }
        EquipmentGate.Piece[] changed=valid.clone();
        changed[1]=new EquipmentGate.Piece(profile,slots[1],profile.item(slots[1]),valid[0].serial);
        ok(EquipmentGate.resolve(owner,changed,(a,b,c,d)->true)==null,"Duplicated serial cannot authorize a set");
        changed=valid.clone();changed[1]=valid[0];
        ok(EquipmentGate.resolve(owner,changed,(a,b,c,d)->true)==null,"Duplicated slot rejected");
        changed=valid.clone();ArmorProfile other=profile==ArmorProfile.CAIRNROOT?ArmorProfile.GLASSCHIME:ArmorProfile.CAIRNROOT;
        changed[1]=new EquipmentGate.Piece(other,slots[1],other.item(slots[1]),"other-serial");
        ok(EquipmentGate.resolve(owner,changed,(a,b,c,d)->true)==null,"Mixed profiles cannot activate");
    }
    private static void verifyStamina(){
        int stamina=BalanceEnvelope.STAMINA_MAX;
        for(int i=0;i<400;i++)stamina=BalanceEnvelope.stamina(stamina,true,true,false,false);
        ok(stamina==0,"Full pool gives exactly 400 powered ticks");
        for(int i=0;i<40;i++)stamina=BalanceEnvelope.stamina(stamina,true,false,true,true);
        ok(stamina==0,"Rest window prevents immediate recharge");
        for(int i=0;i<250;i++)stamina=BalanceEnvelope.stamina(stamina,true,false,true,false);
        ok(stamina==2000,"Ground recovery rate");
        ok(!BalanceEnvelope.canStart(399)&&BalanceEnvelope.canStart(400),"Restart threshold");
        for(int i=0;i<400;i++){
            ok(BalanceEnvelope.stamina(777,false,false,true,false)==777,"Gear swap cannot create stamina");
            ok(BalanceEnvelope.stamina(777,true,false,false,false)==777,"No airborne free recharge");
        }
        ok(BalanceEnvelope.stamina(Integer.MAX_VALUE,true,false,true,false)==2000,"Oversized persisted stamina clamped");
        ok(BalanceEnvelope.stamina(Integer.MIN_VALUE,true,true,false,false)==0,"Negative persisted stamina clamped");
    }
    private static void verifyBuilderRejection(){
        boolean rejected=false;
        try{ArmorMesh.Builder b=new ArmorMesh.Builder(ArmorProfile.CAIRNROOT);b.box("bad",ArmorMesh.Bone.BODY,ArmorMesh.Slot.CHEST,"fixed","base",0,0,0,0,1,1,0,0,0);}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"Zero-volume mesh rejected");rejected=false;
        try{ArmorMesh.Builder b=new ArmorMesh.Builder(ArmorProfile.CAIRNROOT);b.rig("bad",Float.NaN,0,0,"none");}catch(IllegalArgumentException expected){rejected=true;}
        ok(rejected,"Non-finite rig rejected");
    }
    private static void verifyFold(){
        for(boolean opening:new boolean[]{true,false}){
            float initial=opening?0:1,target=opening?1:0,reference=initial;
            for(int i=0;i<30;i++)reference=RigPose.approachWing(reference,target,20.0/30);
            for(int fps:new int[]{30,60,120,240}){
                float value=initial;
                for(int i=0;i<fps;i++){
                    float prior=value;value=RigPose.approachWing(value,target,20.0/fps);
                    ok(value>=0&&value<=1&&(opening?value>=prior:value<=prior),"Wing transition is monotone and bounded");
                    near(RigPose.approachWing(value,target,0),value,0,"Repeated armor passes cannot accelerate wings");
                }
                near(value,reference,2e-6,"Actual wing transition is frame-rate independent at "+fps+" FPS");
            }
        }
        near(RigPose.approachWing(.5f,1,Double.NaN),.5,0,"Invalid frame delta cannot poison the wing rig");
        near(RigPose.approachWing(.5f,1,-1),.5,0,"A backwards frame cannot advance wings");
        ok(1-RigPose.approachWing(1,0,1)<RigPose.approachWing(0,1,1),"Landing folds more slowly than takeoff deploys");
    }
    private static void verifyPresentationState(){
        ArmorPresentationState beforePacket=new ArmorPresentationState();
        beforePacket.fold(100,100,false);
        ok(!beforePacket.flying(100),"A rendered model cannot invent flight authority");
        beforePacket.receive(100,37,true);
        near(beforePacket.strike(105,0),0,0,"First snapshot after rendering is a baseline, not a phantom strike");
        ok(beforePacket.flying(140)&&!beforePacket.flying(141),"Network flight expires after its snapshot window");
        beforePacket.receive(110,38,false);
        near(beforePacket.strike(115,0),1,1e-6,"A new sequence drives a complete contact strike");
        beforePacket.receive(116,38,false);
        near(beforePacket.strike(119,0),Math.sin(Math.PI*.9),1e-6,"Repeated snapshots cannot restart the same strike");
        near(beforePacket.strike(120,0),0,0,"Contact strike finishes after ten ticks");
        beforePacket.fold(157,157,false);
        beforePacket.receive(157,90,true);
        near(beforePacket.strike(162,0),0,0,"Rendering through a tracking gap does not replay missed strikes");
        beforePacket.receive(158,91,true);
        near(beforePacket.strike(163,Float.NaN),1,1e-6,"Invalid partial time cannot poison the strike pose");
        ok(!beforePacket.flying(157),"A snapshot from a later clock cannot grant current flight");
        beforePacket.receive(10,1,false);
        near(beforePacket.strike(15,0),0,0,"A backwards world clock establishes a fresh baseline");

        ArmorPresentationState packetFirst=new ArmorPresentationState();
        packetFirst.receive(100,37,true);
        near(packetFirst.strike(105,0),0,0,"A packet received before rendering is also a baseline");
        ok(!packetFirst.expired(140)&&packetFirst.expired(141),"An unrendered stale snapshot is collectible");
        packetFirst.fold(150,150,true);
        ok(!packetFirst.expired(170)&&packetFirst.expired(171),"Rendering keeps only a bounded local pose lifetime");

        ArmorPresentationState pose=new ArmorPresentationState();
        near(pose.fold(100,100,true),0,0,"Wing animation starts folded");
        float opened=pose.fold(101,101,true);
        ok(opened>0&&opened<1,"Successive frames ease the wings open");
        for(int pass=0;pass<8;pass++)near(pose.fold(101,101,true),opened,0,"Multiple equipped pieces share one frame transition");
        near(pose.fold(101,Double.NaN,true),opened,0,"Invalid render time preserves the last valid pose");
        ok(pose.fold(102,102,false)<opened,"Landing begins a closing transition");
        near(pose.fold(103,1,true),0,0,"Respawn tick reset discards a stale open pose");
        pose.fold(104,2,true);
        near(pose.fold(200,100,true),0,0,"A long render absence discards a stale open pose");
    }
    private static void verifyWingHinges(){
        ArmorMesh mesh=OriginalModels.get(ArmorProfile.AURIC_SKIMMER);
        for(int i=0;i<=20;i++){
            float spread=i/20f;
            RigPose.Rotation left=RigPose.at("wing_left",.37,.8f,spread,0),right=RigPose.at("wing_right",.37,.8f,spread,0);
            near(left.x,right.x,1e-6,"Wing pitch is shared");
            near(left.y,-right.y,1e-6,"Wing hinges deploy with mirrored yaw");
            near(left.z,-right.z,1e-6,"Wing strokes stay mirrored");
            for(String name:new String[]{"wing_left","wing_right"}){
                ArmorMesh.Rig rig=mesh.rigs.get(name);RigPose.Rotation pose=RigPose.at(name,0,0,spread,0);
                double tipX=name.endsWith("left")?14.8:-14.8;
                double tipZ=rig.z-tipX*Math.sin(pose.y);
                ok(tipZ>rig.z,"Wing tips remain behind their body hinges while deploying");
            }
        }
    }
    private static void verifyFaceWinding(MeshBatcher.Batch batch){
        ok(batch.vertices.size()%4==0,"Each draw batch contains complete quads");
        for(int i=0;i<batch.vertices.size();i+=4){
            MeshBatcher.Vertex a=batch.vertices.get(i),b=batch.vertices.get(i+1),c=batch.vertices.get(i+2);
            double ux=b.x-a.x,uy=b.y-a.y,uz=b.z-a.z,vx=c.x-a.x,vy=c.y-a.y,vz=c.z-a.z;
            double outward=(uy*vz-uz*vy)*a.nx+(uz*vx-ux*vz)*a.ny+(ux*vy-uy*vx)*a.nz;
            ok(outward>0,"Every baked front, side and back face winds toward its outward normal under culling");
        }
    }
}
