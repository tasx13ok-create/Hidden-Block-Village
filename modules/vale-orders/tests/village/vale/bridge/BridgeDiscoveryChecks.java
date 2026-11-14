package village.vale.bridge;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
public final class BridgeDiscoveryChecks {
    private static int checks;private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static void reject(String text)throws Exception{boolean failed=false;try{BridgeDiscovery.read(text.getBytes(StandardCharsets.UTF_8));}catch(IOException expected){failed=true;}check(failed,"invalid discovery refused");}
    public static void main(String[] args)throws Exception{
        BridgeDiscovery d=new BridgeDiscovery();d.note("item","n:blade","observation:item_use_intent");d.note("item","n:blade","observation:item_use_intent");d.note("entity","n:blade","observation:spawn_admitted");check(d.size()==2,"item and entity kind stay distinct");byte[] data=d.bytes();check(Arrays.equals(data,BridgeDiscovery.read(data).bytes()),"count/detail round trip stable");check(new String(data,StandardCharsets.UTF_8).contains("item_use_intent"),"discovery intent is labeled, not accepted cast");
        d.note("item","n:blade","bad\nprivate");d.note("item","n:blade","bad\\path");d.note("item","n:blade","bad\"text");check(!new String(d.bytes(),StandardCharsets.UTF_8).contains("private"),"control/unescaped detail rejected");
        for(int i=0;i<2200;i++)d.note("entity","n:e"+i,"observation:spawn_seen");check(d.size()==BridgeDiscovery.MAX_ROWS&&d.dropped()>0,"discovery cap reported explicitly");check(d.bytes().length<BridgeDiscovery.MAX_BYTES,"bounded full inventory serializes");
        reject("{\"schema\":2,\"dropped\":0,\"seen\":[]}");reject("{\"schema\":1,\"dropped\":0,\"seen\":[],\"seen\":[]}");reject("{\"schema\":1,\"dropped\":-1,\"seen\":[]}");reject("{\"schema\":1,\"dropped\":0,\"seen\":[],\"command\":\"op\"}");
        String max="{\"schema\":1,\"dropped\":0,\"seen\":[{\"kind\":\"item\",\"id\":\"n:x\",\"count\":9223372036854775807,\"details\":[]}]}";d=BridgeDiscovery.read(max.getBytes(StandardCharsets.UTF_8));d.note("item","n:x","observation:item_use_intent");check(new String(d.bytes(),StandardCharsets.UTF_8).contains("9223372036854775807"),"counts saturate without overflow");
        System.out.println("BRIDGE_DISCOVERY_CHECKS_PASS assertions="+checks);
    }
}
