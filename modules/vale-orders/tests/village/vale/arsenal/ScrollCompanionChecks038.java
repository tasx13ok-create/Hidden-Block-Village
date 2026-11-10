package village.vale.arsenal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** Saved deployments are authoritative even while their entity chunk is absent. */
public final class ScrollCompanionChecks038 {
    private static int checks;
    private static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
    private static NBTTagCompound reload(NBTTagCompound value)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();CompressedStreamTools.writeCompressed(value,bytes);
        return CompressedStreamTools.readCompressed(new ByteArrayInputStream(bytes.toByteArray()));
    }
    public static void main(String[] args)throws Exception{
        UUID owner=UUID.fromString("38000000-0000-0000-0000-000000000001");
        UUID other=UUID.fromString("38000000-0000-0000-0000-000000000002");
        UUID oldActor=UUID.fromString("38000000-0000-0000-0000-000000000003");
        UUID newActor=UUID.fromString("38000000-0000-0000-0000-000000000004");
        ScrollJournal journal=new ScrollJournal();
        check(!journal.activeCompanion("absent",oldActor,owner,0),"Absent deployment became active");
        check(!journal.recordCompanion("absent",oldActor,12,true),"Unknown actor created a journal row");
        journal.companion("contract").setString("order","guard");
        journal.deployCompanion("contract",owner,oldActor,-1,4000);
        check(journal.activeCompanion("contract",oldActor,owner,-1),"Correct owner/dimension deployment was rejected");
        check(!journal.activeCompanion("contract",oldActor,other,-1),"Different owner acquired deployment");
        check(!journal.activeCompanion("contract",oldActor,owner,0),"Different dimension acquired deployment");
        check(!journal.activeCompanion("contract",newActor,owner,-1),"Different actor acquired deployment");
        check(journal.recordCompanion("contract",oldActor,17,false),"Live companion health was not recorded");
        boolean duplicate=false;try{journal.deployCompanion("contract",owner,newActor,0,5000);}catch(IllegalStateException expected){duplicate=true;}
        check(duplicate,"A second actor replaced a live deployment without recall");
        ScrollJournal restored=new ScrollJournal();restored.readFromNBT(reload(journal.writeToNBT(new NBTTagCompound())));
        check(restored.activeCompanion("contract",oldActor,owner,-1),"Saved deployment lost authority on reload");
        check(restored.companion("contract").getFloat("health")==17&&restored.companion("contract").getString("order").equals("guard"),"Saved health or Guard order was lost");
        check(!restored.sealCompanion("contract",other),"Other player recalled the deployment");
        check(restored.sealCompanion("contract",owner),"Unloaded owned deployment could not be recalled");
        check(!restored.activeCompanion("contract",oldActor,owner,-1),"Recalled unloaded actor retained authority");
        check(!restored.recordCompanion("contract",oldActor,0,true)&&restored.companion("contract").getFloat("health")==17,"Stale unloaded actor overwrote saved recall health");
        restored.deployCompanion("contract",owner,newActor,0,8000);
        check(restored.activeCompanion("contract",newActor,owner,0),"Recalled companion could not deploy in new dimension");
        check(!restored.recordCompanion("contract",oldActor,0,true)&&restored.companion("contract").getBoolean("deployed"),"Old actor closed its replacement");
        check(!restored.recordCompanion("contract",newActor,Float.NaN,true),"Invalid health poisoned a live deployment");

        restored.deployCompanion("second",owner,oldActor,1,9000);
        restored.recordCompanion("second",oldActor,9,false);
        restored.deployCompanion("foreign",other,UUID.randomUUID(),0,9000);
        check(restored.sealOwnerCompanions(owner)==2,"Owner cleanup did not seal every saved deployment");
        check(!restored.companion("contract").getBoolean("deployed")&&!restored.companion("second").getBoolean("deployed"),"Owner cleanup left an unloaded deployment active");
        check(restored.companion("foreign").getBoolean("deployed"),"Owner cleanup sealed another player's companion");
        check(restored.companion("contract").getFloat("health")==17&&restored.companion("second").getFloat("health")==9,"Owner cleanup lost recorded health");
        check(restored.sealOwnerCompanions(owner)==0,"Repeated cleanup changed closed deployments");

        ScrollJournal legacy=new ScrollJournal();NBTTagCompound row=legacy.companion("legacy");
        row.setBoolean("deployed",true);row.setUniqueId("actor",oldActor);row.setFloat("health",23);
        check(!legacy.activeCompanion("legacy",newActor,owner,0)&&!row.hasUniqueId("owner"),"Wrong actor migrated an older deployment");
        check(legacy.activeCompanion("legacy",oldActor,owner,-1)&&row.getUniqueId("owner").equals(owner)&&row.getInteger("actorDimension")==-1,"Matching legacy deployment was not migrated in place");
        check(row.getFloat("health")==23,"Legacy migration healed or injured the companion");

        check(ScrollCompanionOrders.read(null)==ScrollCompanionOrders.Order.ASSIST&&ScrollCompanionOrders.read("unknown")==ScrollCompanionOrders.Order.ASSIST,"Missing/unknown old orders have no safe default");
        String key="assist";String[] expected={"follow","guard","hold","assist"};
        for(String value:expected){key=ScrollCompanionOrders.next(key).key;check(key.equals(value),"Order cycle changed its saved keys");}
        check(!ScrollCompanionOrders.Order.ASSIST.anchored()&&!ScrollCompanionOrders.Order.FOLLOW.anchored()&&ScrollCompanionOrders.Order.GUARD.anchored()&&ScrollCompanionOrders.Order.HOLD.anchored(),"Movement order contract changed");
        check(ScrollCompanionOrders.recentCombat(0,0),"Actual combat on the first player tick was ignored");
        check(ScrollCompanionOrders.recentCombat(220,20)&&!ScrollCompanionOrders.recentCombat(221,20),"Assist target survived beyond ten seconds");
        check(!ScrollCompanionOrders.recentCombat(20,21)&&!ScrollCompanionOrders.recentCombat(20,-1),"Future or invalid combat event triggered assistance");
        check(ScrollCompanionOrders.commandDeadline(Long.MAX_VALUE-5)==Long.MAX_VALUE,"Command deadline overflowed");
        check(ScrollCompanionOrders.commandActive(100,300)&&!ScrollCompanionOrders.commandActive(100,301)&&!ScrollCompanionOrders.commandActive(301,300),"Command lifetime was not bounded after a clock change");
        System.out.println("PASS: "+checks+" companion order and saved-deployment checks. Native movement, support and item interactions are checked separately.");
    }
}
