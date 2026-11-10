package village.vale.arsenal;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

/** Pure boundaries and wire format; native combat is exercised by ClawNativeChecks. */
public final class ClawCombatChecks {
    private static int assertions;
    private static void check(boolean condition,String message) {assertions++;if(!condition)throw new AssertionError(message);}
    private static void rejected(ByteBuf wire,String message) {
        boolean rejected=false;
        try {new ClawNetwork.Request().fromBytes(wire);}catch(DecoderException expected) {rejected=true;}
        finally {wire.release();}
        check(rejected,message);
    }
    public static void main(String[] args) {
        check(ClawRules.inRange(25),"Five-block edge is rejected");
        check(!ClawRules.inRange(25.0001),"Range can exceed five blocks");
        check(!ClawRules.inRange(-1)&&!ClawRules.inRange(Double.NaN)&&!ClawRules.inRange(Double.POSITIVE_INFINITY),"Invalid range is accepted");
        check(ClawRules.rear(0,1,0,-4),"Rear approach is missed");
        check(!ClawRules.rear(0,1,0,4),"Front approach receives rear bonus");
        check(!ClawRules.rear(0,1,4,0),"Side approach receives rear bonus");
        check(!ClawRules.rear(0,0,0,-4)&&!ClawRules.rear(0,1,0,0)&&!ClawRules.rear(Double.NaN,1,0,-4),"Degenerate rear vectors pass");
        check(ClawRules.lockOpen(1000,1000)&&ClawRules.lockOpen(1099,1000),"Live lock is unavailable");
        check(!ClawRules.lockOpen(1100,1000)&&!ClawRules.lockOpen(999,1000),"Expired or rewound lock is available");
        check(!ClawRules.windupDue(1019,1000)&&ClawRules.windupDue(1020,1000),"One-second dodge window is shortened");
        check(!ClawRules.windupDue(100000,1000),"Stale windup executes after a time jump");
        check(!ClawRules.dodged(1.44)&&ClawRules.dodged(1.440001)&&ClawRules.dodged(Double.NaN),"Dodge distance is not bounded");
        check(Math.abs(ClawRules.rearDamage(8,true)-8.8f)<.0001f&&ClawRules.rearDamage(8,false)==8,"Rear damage is not exactly ten percent");
        check(ClawRules.rearDamage(Float.NaN,true)==0&&ClawRules.rearDamage(100,true)==16,"Damage becomes nonfinite or unbounded");
        check(Math.abs(ClawRules.nativeRearDamage(30)-33)<.001f&&Float.isFinite(ClawRules.nativeRearDamage(Float.MAX_VALUE)),"Native rear melee is capped or overflows");
        check(ClawRules.BLEED_TICKS==340&&ClawRules.HITS_PER_STACK==5&&ClawRules.MAX_STACKS==3,"Bleed contract drifted");
        check(ArsenalSpec.byKey("valeorders:crescent_talons")==ArsenalSpec.CRESCENT_TALONS&&ArsenalSpec.CRESCENT_TALONS.cost==18,"Registry identity or lock cost drifted");
        ByteBuf wire=Unpooled.buffer();new ClawNetwork.Request(-7).toBytes(wire);
        check(wire.readableBytes()==5,"Lock request carries unnecessary authority");
        ClawNetwork.Request decoded=new ClawNetwork.Request();decoded.fromBytes(wire);check(decoded.dimension==-7&&!wire.isReadable(),"Dimension does not round-trip");wire.release();
        rejected(Unpooled.buffer(),"Empty packet accepted");
        rejected(Unpooled.buffer().writeByte(1).writeByte(0),"Truncated packet accepted");
        rejected(Unpooled.buffer().writeByte(2).writeInt(0),"Unknown protocol accepted");
        rejected(Unpooled.buffer().writeByte(1).writeInt(0).writeByte(0),"Trailing authority accepted");
        System.out.println("PASS: "+assertions+" Crescent Talons boundary and protocol assertions. Native combat is a separate fixture.");
    }
}
