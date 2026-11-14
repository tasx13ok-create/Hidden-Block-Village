package village.fieldhud;

import java.util.UUID;

/** Exercises actual owner DTO projection and exact displayed scaled costs. */
public final class SharedResourceChecks {
    public static final class Wire {
        public UUID owner=UUID.fromString("bd4e7e11-f623-4d63-95cd-000000000016");
        public int entityId=7,dimension=-1,state=0;public long serverTick=99;
        public String provider="narutomod";public double amount=1234567890123.25,maximum=9876543210123.75,scale=.01;
    }
    private static int assertions;
    private static void check(boolean ok,String name){assertions++;if(!ok)throw new AssertionError(name);}
    private static SharedResourceReader.State read(Wire wire){return new SharedResourceReader().read(wire,wire.owner,7,-1);}
    public static void main(String[] args){
        Wire w=new Wire();SharedResourceReader reader=new SharedResourceReader();
        SharedResourceReader.State s=read(w);check(s!=null&&s.ready&&s.amount==w.amount&&s.maximum==w.maximum,"large raw doubles changed");
        check(SharedResourceReader.cost(s,1).equals("0.01 chakra"),"fractional cost rounded to zero");
        w.scale=.1;check(SharedResourceReader.cost(read(w),29).equals("2.9 chakra"),"binary rounding leaked into cost");
        w.scale=100;check(SharedResourceReader.cost(read(w),10000).equals("1000000 chakra"),"large cost capped");
        w.amount=Double.MAX_VALUE;w.maximum=1;check(read(w)!=null,"overcap provider balance rejected");
        check(reader.read(w,UUID.randomUUID(),7,-1)==null,"other owner accepted");
        check(reader.read(w,w.owner,8,-1)==null,"other entity accepted");
        check(reader.read(w,w.owner,7,0)==null,"other dimension accepted");
        check(reader.read(null,w.owner,7,-1)==null&&reader.read(w,null,7,-1)==null,"missing identity accepted");
        for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,-1}){w.amount=bad;check(read(w)==null,"invalid amount accepted");}
        w.amount=1;for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,-1}){w.maximum=bad;check(read(w)==null,"invalid max accepted");}
        w.maximum=100;for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,.009,100.01}){w.scale=bad;check(read(w)==null,"invalid scale accepted");}
        w.scale=1;w.provider="resolve";check(SharedResourceReader.cost(read(w),35).equals("35 resolve"),"absent-provider scale changed");
        w.scale=.5;check(read(w)==null,"scaled fallback accepted");w.scale=1;
        w.provider=null;check(read(w)==null,"null provider accepted");w.provider="typo";check(read(w)==null,"unknown provider accepted");w.provider="narutomod";
        w.state=2;check(read(w)==null,"unavailable resource exposed stale amounts");w.amount=w.maximum=0;check(read(w)!=null&&!read(w).ready,"unavailable state became balance");
        check(SharedResourceReader.cost(read(w),20).equals("Resource unavailable"),"unready quoted a spend");
        w.state=6;check(read(w)==null,"unknown state accepted");w.state=-1;check(read(w)==null,"negative state accepted");w.state=2;w.serverTick=-1;check(read(w)==null,"negative tick accepted");
        check(ReadinessLine.parse("Storm Ward | Resolve 100/100 | Skybreak ready | Technique 2s")!=null,"legacy readiness lost");
        check(ReadinessLine.parse("Storm Ward | Resolve 101/100 | Skybreak ready | Technique ready")==null,"invalid legacy summary accepted");
        check(ReadinessLine.parse("Storm Ward | Chakra 123456.25/200000.5 | Skybreak ready | Technique 2s")!=null,"native readiness lost");
        check(ReadinessLine.parse("Storm Ward | Chakra 1.7976931348623157E308/1.0E308 | Skybreak ready | Technique ready")!=null,"large native summary lost");
        check(ReadinessLine.parse("Storm Ward | Chakra unavailable | Skybreak 2s | Technique ready")!=null,"unready summary lost");
        for(String bad:new String[]{"NaN/100","1/0","1e999/1","-1/100"})check(ReadinessLine.parse("Storm Ward | Chakra "+bad+" | Skybreak ready | Technique ready")==null,"invalid native summary accepted");
        check(ReadinessLine.parse("Boss warning | Chakra 1/100 | Skybreak ready | Technique ready")==null,"unrelated warning swallowed");
        System.out.println("SHARED_RESOURCE_HUD_PASS: "+assertions);
    }
}
