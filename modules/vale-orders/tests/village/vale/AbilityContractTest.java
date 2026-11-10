package village.vale;

import io.netty.buffer.*;
import java.util.*;

/** Wire/data boundary checks; native action/equipment effects need the separate runtime fixture. */
public final class AbilityContractTest {
    private static int checks;
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void reject(Runnable action,String why){checks++;try{action.run();}catch(RuntimeException expected){return;}throw new AssertionError(why);}
    private static AbilitySnapshot.Entry entry(String id){return new AbilitySnapshot.Entry(id,"Ward Circle","art","neutral","valeorders:technique_codex",0,3,25,0,true,"",200,360,200F/360);}
    private static byte[] encode(AbilitySnapshot value){ByteBuf b=Unpooled.buffer();try{AbilityCodec.write(b,value);byte[] data=new byte[b.readableBytes()];b.readBytes(data);return data;}finally{b.release();}}
    private static AbilitySnapshot decode(byte[] bytes){ByteBuf b=Unpooled.wrappedBuffer(bytes);try{return AbilityCodec.read(b);}finally{b.release();}}
    public static void main(String[] args){
        UUID owner=UUID.randomUUID();List<AbilitySnapshot.Entry> input=new ArrayList<>();for(int i=0;i<48;i++)input.add(entry("art/test_"+i));
        AbilitySnapshot value=new AbilitySnapshot(owner,-1,900,"art/test_7",input);input.clear();check(value.entries.size()==48,"Caller mutated snapshot");
        AbilitySnapshot copy=decode(encode(value));check(copy.playerId.equals(owner)&&copy.dimension==-1&&copy.serverTick==900,"Owner/world changed");check(copy.entries.size()==48&&copy.selectedEntryId.equals("art/test_7"),"Lost available action/selection");check(copy.find("art/test_7").cooldownRemainingTicks==200,"Lost actual timer");check(copy.find("art/test_7").cooldownFraction==200F/360,"Lost actual fraction");check(copy.find("art/unknown")==null,"Unknown action substituted");
        reject(()->copy.entries.clear(),"Mutable public list");reject(()->new AbilitySnapshot(owner,0,1,"",Arrays.asList(entry("art/a"),entry("art/a"))),"Duplicate ID accepted");
        reject(()->new AbilitySnapshot(owner,0,1,"art/missing",Collections.singletonList(entry("art/a"))),"Unknown selected ID accepted");reject(()->new AbilitySnapshot(owner,0,-1,"",Collections.emptyList()),"Negative server tick");
        List<AbilitySnapshot.Entry> oversized=new ArrayList<>();for(int i=0;i<65;i++)oversized.add(entry("art/overflow_"+i));reject(()->new AbilitySnapshot(owner,0,1,"",oversized),"Unbounded catalog");
        reject(()->new AbilitySnapshot.Entry("art/a","A","art","neutral","valeorders:technique_codex",0,1,0,0,true,"",-1,20,.5F),"Mixed unknown timers");
        reject(()->new AbilitySnapshot.Entry("art/a","A","art","neutral","valeorders:technique_codex",0,1,0,0,true,"",0,20,Float.NaN),"NaN cooling fraction");
        reject(()->new AbilitySnapshot.Entry("art/a","A","art","neutral","valeorders:technique_codex",0,1,0,0,true,"blocked",0,0,0),"Contradictory enabled state");
        AbilitySnapshot.Entry ratio=new AbilitySnapshot.Entry("item/valeorders:needle_fan","Needle Fan","projectile","neutral","valeorders:needle_fan",0,1,0,16,true,"",-1,-1,.25F);
        AbilitySnapshot nativeRatio=decode(encode(new AbilitySnapshot(owner,0,1,ratio.id,Collections.singletonList(ratio))));check(nativeRatio.entries.get(0).cooldownTotalTicks==-1&&nativeRatio.entries.get(0).cooldownFraction==.25F,"Ratio-only native timer fabricated");
        byte[] bytes=encode(value),trailing=Arrays.copyOf(bytes,bytes.length+1);reject(()->decode(trailing),"Trailing snapshot accepted");byte[] version=bytes.clone();version[0]=2;reject(()->decode(version),"Future schema guessed");
        for(int size:new int[]{0,1,12,35,bytes.length-1}){final byte[] cut=Arrays.copyOf(bytes,size);reject(()->decode(cut),"Truncated packet accepted");}
        ByteBuf action=Unpooled.buffer();try{new AbilityNetwork.ActionMessage("art/ward_circle",-1,true).toBytes(action);AbilityNetwork.ActionMessage request=new AbilityNetwork.ActionMessage();request.fromBytes(action);check(request.activate&&request.dimension==-1&&request.id.equals("art/ward_circle"),"Exact requested ID changed");}finally{action.release();}
        for(byte[] malformed:new byte[][]{{2,0,0,0,0,0,0,0},{1,2,0,0,0,0,0,0},{1,0,0,0,0,0,0,0},{1,0,0,0,0,0,(byte)255,(byte)255}}){reject(()->{ByteBuf b=Unpooled.wrappedBuffer(malformed);try{new AbilityNetwork.ActionMessage().fromBytes(b);}finally{b.release();}},"Malformed C2S request accepted");}
        reject(()->new AbilityNetwork.ActionMessage("/op attacker",0,true),"Command injection accepted");reject(()->entry("art/a\n/kill"),"Control text accepted");check(!AbilitySnapshot.validId(null)&&!AbilitySnapshot.validId(""),"Null/empty request accepted");
        System.out.println("PASS: "+checks+" immutable ability catalog and bounded S2C/C2S contract checks. No native action claim.");
    }
}
