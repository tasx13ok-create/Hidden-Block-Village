package village.vale.expedition;

public final class GateRulesChecks {
    private static int count;
    private static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(GateRules.walked(10,11,0,0,1,false,false,false),"Native walking rejected");
        check(!GateRules.walked(10,11,0,0,100,false,false,false),"Large teleport credited");
        check(!GateRules.walked(10,11,0,1,1,false,false,false)&&!GateRules.walked(10,11,0,0,1,false,false,true),"Dimension/native teleport credited");
        check(!GateRules.walked(10,15,0,0,1,false,false,false)&&!GateRules.walked(10,10,0,0,1,false,false,false),"Stale/duplicate movement credited");
        check(!GateRules.walked(10,11,0,0,1,true,false,false)&&!GateRules.walked(10,11,0,0,1,false,true,false),"Flying/riding counted as walking");
        check(GateRules.crossed(10,9,3,true)&&!GateRules.crossed(8,8,3,true)&&!GateRules.crossed(10,9,3,false),"Arrival did not require an observed boundary crossing");
        check(!GateRules.walked(1,2,0,0,Double.NaN,false,false,false),"NaN displacement accepted");
        check(GateRules.interact(0,1,3,true)==-1,"Wrong ordered marker accepted");
        check(GateRules.interact(0,2,3,false)==4,"Unordered marker rejected");
        check(GateRules.interact(4,2,3,false)==-2,"Marker spam credited twice");
        check(GateRules.interact(1,1,3,true)==3&&GateRules.sequenceDone(7,3),"Ordered sequence cannot finish");
        check(!GateRules.sequenceDone(15,3)&&GateRules.interact(256,0,3,false)==-2,"Foreign progress bits accepted");
        check(GateRules.observedTicks(20,10,11,true)==21,"Real observed time lost");
        check(GateRules.observedTicks(20,10,200,true)==0&&GateRules.observedTicks(20,10,11,false)==0,"Offline gap or lost point granted defense credit");
        check(GateRules.observedTicks(7199,10,14,true)==7200,"Observed timer unbounded");
        check(!GateRules.timedOut(100,1300,60)&&GateRules.timedOut(100,1301,60)&&GateRules.timedOut(100,99,60),"Deadline edge/rewind wrong");
        check(GateRules.validKill(0,0,3,false,true,true,true),"Accepted owner/run death rejected");
        check(!GateRules.validKill(0,1,3,false,true,true,true)&&!GateRules.validKill(0,0,3,true,true,true,true),"Replay/canceled kill credited");
        check(!GateRules.validKill(0,0,3,false,false,true,true)&&!GateRules.validKill(0,0,3,false,true,false,true)&&!GateRules.validKill(0,0,3,false,true,true,false),"Unconfirmed/unowned death credited");
        check(GateRules.session(10,210,1,1,"choose","choose",true)&&!GateRules.session(10,211,1,1,"choose","choose",true),"Nonce TTL wrong");
        check(!GateRules.session(10,11,1,2,"choose","choose",true)&&!GateRules.session(10,11,1,1,"choose","other",true),"Old run/stage nonce accepted");
        check(GateRules.receipt("gate_sakura_seedfall",1)&&!GateRules.receipt("../gate_x",1)&&!GateRules.receipt("gate_x",0),"Unbounded receipt identity");
        check(GateRules.clearTarget(true,true,true,false)&&!GateRules.clearTarget(false,true,true,false),"Clearing a foreign target recursively clears null again");
        check(!GateRules.clearTarget(true,true,true,true)&&GateRules.clearTarget(true,true,false,true)&&GateRules.clearTarget(true,false,false,false),"Invalid owner context retains combat target");
        Object world=new Object(),account=new Object(),state=new Object();GateRules.SpawnStamp stamp=new GateRules.SpawnStamp(world,0,account,state,3,1,"gate_native","fight");
        check(stamp.valid(world,0,account,state,3,1,"gate_native","fight",1024),"Stable spawn context rejected at range edge");
        check(!stamp.valid(new Object(),0,account,state,3,1,"gate_native","fight",1)&&!stamp.valid(world,1,account,state,3,1,"gate_native","fight",1),"World/dimension-changing callback retains spawn authority");
        check(!stamp.valid(world,0,new Object(),state,3,1,"gate_native","fight",1)&&!stamp.valid(world,0,account,new Object(),3,1,"gate_native","fight",1),"Replaced player account/state retains spawn authority");
        check(!stamp.valid(world,0,account,state,4,1,"gate_native","fight",1)&&!stamp.valid(world,0,account,state,3,2,"gate_native","fight",1),"Changed run/content generation retains spawn authority");
        check(!stamp.valid(world,0,account,state,3,1,"gate_other","fight",1)&&!stamp.valid(world,0,account,state,3,1,"gate_native","other",1),"Changed mission/stage retains spawn authority");
        check(!stamp.valid(world,0,account,state,3,1,"gate_native","fight",1024.1)&&!stamp.valid(world,0,account,state,3,1,"gate_native","fight",Double.NaN),"Moved/nonfinite spawn context accepted");
        System.out.println("PASS: "+count+" expedition authority/cadence assertions.");
    }
}
