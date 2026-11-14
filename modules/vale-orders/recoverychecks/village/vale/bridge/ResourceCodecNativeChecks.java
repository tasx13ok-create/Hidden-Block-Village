package village.vale.bridge;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.UUID;

/** Private codec exercise of actual common production classes; no graphical/client assumptions. */
public final class ResourceCodecNativeChecks {
    private static int checks;
    private static void require(boolean yes,String why){checks++;if(!yes)throw new AssertionError("Resource codec: "+why);}
    private static byte[] encode(ResourceSnapshot value){ByteBuf out=Unpooled.buffer();try{new ResourceNetwork.Message(value).toBytes(out);require(out.readableBytes()==59,"Wire size changed");byte[] bytes=new byte[out.readableBytes()];out.readBytes(bytes);return bytes;}finally{out.release();}}
    private static ResourceSnapshot decode(byte[] bytes){ByteBuf in=Unpooled.wrappedBuffer(bytes);try{ResourceNetwork.Message decoded=new ResourceNetwork.Message();decoded.fromBytes(in);require(in.readableBytes()==0,"Decoder left trailing bytes");return decoded.value;}finally{in.release();}}
    private static void bad(byte[] bytes,String why){ByteBuf in=Unpooled.wrappedBuffer(bytes);try{new ResourceNetwork.Message().fromBytes(in);throw new AssertionError("Accepted "+why);}catch(DecoderException expected){checks++;}finally{in.release();}}
    private static byte[] byteAt(byte[] source,int offset,int value){byte[] data=source.clone();data[offset]=(byte)value;return data;}
    private static byte[] number(byte[] source,int offset,double value){byte[] data=source.clone();ByteBuf out=Unpooled.wrappedBuffer(data);try{out.setDouble(offset,value);return data;}finally{out.release();}}
    private static void equal(ResourceSnapshot a,ResourceSnapshot b){require(a.owner.equals(b.owner)&&a.entityId==b.entityId&&a.dimension==b.dimension&&a.serverTick==b.serverTick&&a.provider.equals(b.provider)&&a.state==b.state&&Double.doubleToRawLongBits(a.amount)==Double.doubleToRawLongBits(b.amount)&&Double.doubleToRawLongBits(a.maximum)==Double.doubleToRawLongBits(b.maximum)&&Double.doubleToRawLongBits(a.scale)==Double.doubleToRawLongBits(b.scale),"Exact identity/raw-double round trip");}
    public static int check(){
        checks=0;UUID owner=UUID.fromString("43ec7807-821a-4a56-9c03-031600000001");
        ResourceSnapshot base=new ResourceSnapshot(owner,123,-1,500,"narutomod",ChakraLink.State.READY.ordinal(),1234567890.125,9876543210.5,.25);byte[] wire=encode(base);equal(base,decode(wire));require(decode(wire).amount>100,"Native amount was capped to resolve");
        ResourceSnapshot huge=new ResourceSnapshot(owner,Integer.MAX_VALUE,Integer.MIN_VALUE,Long.MAX_VALUE,"narutomod",0,Double.MAX_VALUE,Double.MAX_VALUE,100);equal(huge,decode(encode(huge)));
        equal(new ResourceSnapshot(owner,0,0,0,"resolve",0,99,100,1),decode(encode(new ResourceSnapshot(owner,0,0,0,"resolve",0,99,100,1))));
        for(int state=1;state<ChakraLink.State.values().length;state++){ResourceSnapshot unavailable=new ResourceSnapshot(owner,1,0,10,"narutomod",state,0,0,1);equal(unavailable,decode(encode(unavailable)));require(!unavailable.ready()&&unavailable.units()==0,"Unavailable view exposed a spendable amount");}
        for(int size=0;size<59;size++)bad(java.util.Arrays.copyOf(wire,size),"short frame "+size);
        for(int size=60;size<=64;size++)bad(java.util.Arrays.copyOf(wire,size),"trailing bytes "+size);
        bad(byteAt(wire,0,0),"version0");bad(byteAt(wire,0,2),"future version");bad(byteAt(wire,33,2),"provider2");bad(byteAt(wire,33,255),"provider255");bad(byteAt(wire,34,255),"state255");bad(byteAt(wire,34,ChakraLink.State.values().length),"unknown state");
        for(double amount:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,-1})bad(number(wire,35,amount),"invalid amount "+amount);
        for(double max:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,-1,0})bad(number(wire,43,max),"invalid max "+max);
        for(double scale:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,0,-1,.001,100.1})bad(number(wire,51,scale),"invalid scale "+scale);
        bad(byteAt(wire,34,ChakraLink.State.UNREADY.ordinal()),"hidden amount in unavailable state");bad(byteAt(wire,33,0),"resolve provider with fractional scale");
        byte[] negativeEntity=wire.clone();ByteBuf mutable=Unpooled.wrappedBuffer(negativeEntity);try{mutable.setInt(17,-1);}finally{mutable.release();}bad(negativeEntity,"negative entity ID");
        byte[] negativeTick=wire.clone();mutable=Unpooled.wrappedBuffer(negativeTick);try{mutable.setLong(25,-1);}finally{mutable.release();}bad(negativeTick,"negative tick");
        try{new ResourceSnapshot(null,1,0,0,"resolve",0,1,100,1);throw new AssertionError("Null owner accepted");}catch(IllegalArgumentException expected){checks++;}
        for(String provider:new String[]{null,"", "spring","NARUTOMOD"})try{new ResourceSnapshot(owner,1,0,0,provider,0,1,100,1);throw new AssertionError("Unknown provider accepted");}catch(IllegalArgumentException expected){checks++;}
        try{new ResourceSnapshot(owner,1,0,0,"resolve",0,1,100,2);throw new AssertionError("Resolve scale accepted");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("RESOURCE_CODEC_PASS: "+checks);return checks;
    }
    public static void main(String[] args){check();}
}
