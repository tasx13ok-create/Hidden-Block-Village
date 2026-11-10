package village.vale;

import io.netty.buffer.*;
import java.util.UUID;

public final class HudSnapshotTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static HudSnapshot sample(double resolve,long merit){return new HudSnapshot(UUID.fromString("eeed226c-dc0a-4448-9dbf-74b1a4a8d9a7"),-7,1200,resolve,100,12,merit,"gale_lance","Airfoil",60,160,"oracle","Oracle Form","lantern","Lantern Attendant","field_patrol","Clear the Footpaths",3,5,7,1190,"gale_lance");}
    private static void rejected(Runnable r,String why){boolean failed=false;try{r.run();}catch(RuntimeException e){failed=true;}check(failed,why);}
    public static void main(String[] args){
        HudSnapshot original=sample(42,9);ByteBuf b=Unpooled.buffer();HudCodec.write(b,original);byte[] bytes=new byte[b.readableBytes()];b.getBytes(0,bytes);HudSnapshot read=HudCodec.read(b);b.release();
        check(read.playerId.equals(original.playerId)&&read.dimension==-7&&read.serverTick==1200,"owner/dimension/time lost");check(read.resolve==42&&read.resolveMax==100&&read.level==12&&read.merit==9,"resource/progression lost");check(read.selectedArtId.equals("gale_lance")&&read.selectedArtName.equals("Airfoil")&&read.cooldownRemainingTicks==60&&read.cooldownTotalTicks==160,"art/cooldown lost");check(read.activeFormId.equals("oracle")&&read.companionId.equals("lantern")&&read.contractProgress==3&&read.contractGoal==5,"optional systems lost");check(read.castSequence==7&&read.lastCastServerTick==1190&&read.lastCastArtId.equals("gale_lance"),"confirmed cast receipt lost");
        rejected(()->sample(Double.NaN,0),"NaN accepted");rejected(()->sample(Double.POSITIVE_INFINITY,0),"infinity accepted");rejected(()->sample(101,0),"overflow resolve accepted");rejected(()->sample(-1,0),"negative resolve accepted");rejected(()->sample(50,-1),"negative merit accepted");
        byte[] version=bytes.clone();version[0]=2;rejected(()->{ByteBuf bad=Unpooled.wrappedBuffer(version);try{HudCodec.read(bad);}finally{bad.release();}},"unknown schema accepted");
        for(int length:new int[]{0,1,7,29,65,bytes.length-1})rejected(()->{ByteBuf bad=Unpooled.wrappedBuffer(java.util.Arrays.copyOf(bytes,length));try{HudCodec.read(bad);}finally{bad.release();}},"truncated payload accepted at "+length);
        rejected(()->{ByteBuf bad=Unpooled.buffer();try{bad.writeBytes(bytes);bad.writeByte(1);HudCodec.read(bad);}finally{bad.release();}},"trailing payload accepted");
        System.out.println("PASS: "+checks+" HUD schema, bounded payload, owner, cooldown and confirmed-cast round-trip checks.");
    }
}
