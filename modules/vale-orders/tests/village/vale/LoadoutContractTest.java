package village.vale;

import io.netty.buffer.*;
import java.util.*;

/** Schema2 boundaries and explicit-equip requests, without inventing a native gameplay proof. */
public final class LoadoutContractTest {
    private static int checks;
    private static final UUID OWNER=UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    private static final String WEAPON="weapon/12345678-1234-4567-89ab-123456789abc";
    private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static void reject(Runnable action,String why){checks++;try{action.run();}catch(RuntimeException expected){return;}throw new AssertionError(why);}
    private static AbilitySnapshot.Entry art(String id){return new AbilitySnapshot.Entry(id,"Ward","art","neutral","valeorders:technique_codex",0,3,25,0,true,"",200,360,200F/360);}
    private static AbilitySnapshot.Entry weapon(){return new AbilitySnapshot.Entry(WEAPON,"Named Saber","weapon","ember","valeorders:ember_saber",0,1,0,1,true,"",0,0,0);}
    private static byte[] encode(AbilityLoadoutSnapshot value){ByteBuf b=Unpooled.buffer();try{AbilityCodec.writeLoadout(b,value);byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);return bytes;}finally{b.release();}}
    private static AbilityLoadoutSnapshot decode(byte[] bytes){ByteBuf b=Unpooled.wrappedBuffer(bytes);try{return AbilityCodec.readLoadout(b);}finally{b.release();}}
    private static byte[] legacy(AbilitySnapshot value){ByteBuf b=Unpooled.buffer();try{AbilityCodec.write(b,value);byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);return bytes;}finally{b.release();}}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    public static void main(String[] args){
        List<AbilitySnapshot.Entry> input=new ArrayList<>();input.add(art("art/ward_circle"));input.add(weapon());
        AbilityLoadoutSnapshot source=new AbilityLoadoutSnapshot(OWNER,-1,900,WEAPON,input);input.clear();
        check(source.entries.size()==2&&source.schemaVersion==2&&source.find(WEAPON).kind.equals("weapon"),"Lost exact weapon identity");
        AbilityLoadoutSnapshot copy=decode(encode(source));
        check(copy.playerId.equals(OWNER)&&copy.dimension==-1&&copy.serverTick==900&&copy.selectedEntryId.equals(WEAPON),"Lost owner/world/selection");
        check(copy.find(WEAPON).iconItemId.equals("valeorders:ember_saber")&&copy.find(WEAPON).resolveCost==0&&copy.find(WEAPON).itemCount==1,"Equip metadata changed");
        check(copy.find("art/ward_circle").cooldownRemainingTicks==200&&copy.find("art/ward_circle").cooldownFraction==200F/360,"Legacy action timer changed in schema2");
        reject(()->copy.entries.clear(),"Mutable loadout list");check(copy.find("weapon/00000000-0000-0000-0000-000000000000")==null,"Unknown instance substituted");
        List<AbilitySnapshot.Entry> maximum=new ArrayList<>();for(int i=0;i<128;i++)maximum.add(art("art/card_"+i));
        check(decode(encode(new AbilityLoadoutSnapshot(OWNER,0,0,"",maximum))).entries.size()==128,"Unsigned128 count did not round trip");
        maximum.add(art("art/card_128"));reject(()->new AbilityLoadoutSnapshot(OWNER,0,0,"",maximum),"Oversized catalog accepted");
        reject(()->new AbilityLoadoutSnapshot(OWNER,0,-1,"",Collections.emptyList()),"Negative tick");
        reject(()->new AbilityLoadoutSnapshot(OWNER,0,1,WEAPON,Collections.singletonList(art("art/a"))),"Missing selected instance accepted");
        reject(()->new AbilityLoadoutSnapshot(OWNER,0,1,"",Arrays.asList(weapon(),weapon())),"Duplicate weapon IDs accepted");
        reject(()->new AbilitySnapshot(OWNER,0,1,"",Collections.singletonList(weapon())),"Schema1 accepted weapon entry");
        reject(()->new AbilitySnapshot.Entry(WEAPON,"Wrong","art","ember","valeorders:ember_saber",0,1,0,1,true,"",0,0,0),"ID/kind mismatch accepted");
        for(String id:new String[]{"weapon/ember_saber","weapon/../../op","weapon/12345678-1234-4567-89AB-123456789abc","weapon/12345678-1234-4567-89ab-123456789abc/extra"}){
            reject(()->new AbilitySnapshot.Entry(id,"Bad","weapon","ember","valeorders:ember_saber",0,1,0,1,true,"",0,0,0),"Malformed exact weapon ID accepted");
        }
        byte[] encoded=encode(source);for(int size:new int[]{0,1,4,16,28,31,45,encoded.length-1}){final byte[] cut=Arrays.copyOf(encoded,size);reject(()->decode(cut),"Truncated loadout accepted");}
        byte[] trailing=Arrays.copyOf(encoded,encoded.length+1);reject(()->decode(trailing),"Trailing loadout bytes accepted");
        byte[] version=encoded.clone();version[0]=3;reject(()->decode(version),"Future loadout schema guessed");
        byte[] empty=encode(new AbilityLoadoutSnapshot(OWNER,0,0,"",Collections.emptyList()));empty[31]=(byte)129;reject(()->decode(empty),"Oversized wire count accepted");
        byte[] giant=encoded.clone();giant[29]=(byte)255;giant[30]=(byte)255;reject(()->decode(giant),"Oversized UTF8 field accepted");
        ByteBuf message=Unpooled.buffer();try{
            new AbilityNetwork.EquipMessage(WEAPON,-1).toBytes(message);AbilityNetwork.EquipMessage read=new AbilityNetwork.EquipMessage();read.fromBytes(message);
            check(read.id.equals(WEAPON)&&read.dimension==-1,"Exact equip request changed");
        }finally{message.release();}
        for(String id:new String[]{"art/ward_circle","item/valeorders:ember_saber","/op attacker","weapon/1234",""})reject(()->new AbilityNetwork.EquipMessage(id,0),"Non-weapon equip request accepted");
        for(int size=0;size<7;size++){final int cut=size;reject(()->{ByteBuf b=Unpooled.buffer();try{new AbilityNetwork.EquipMessage(WEAPON,0).toBytes(b);b.writerIndex(cut);new AbilityNetwork.EquipMessage().fromBytes(b);}finally{b.release();}},"Short C2S equip accepted");}
        reject(()->{ByteBuf b=Unpooled.buffer();try{new AbilityNetwork.EquipMessage(WEAPON,0).toBytes(b);b.setByte(0,2);new AbilityNetwork.EquipMessage().fromBytes(b);}finally{b.release();}},"Future equip operation accepted");
        reject(()->{ByteBuf b=Unpooled.buffer();try{new AbilityNetwork.EquipMessage(WEAPON,0).toBytes(b);b.writeByte(0);new AbilityNetwork.EquipMessage().fromBytes(b);}finally{b.release();}},"Trailing C2S equip data accepted");
        // Independently specified empty schema1 packet: version + UUID + dimension + tick + empty UTF8 + zero entries.
        check(hex(legacy(new AbilitySnapshot(OWNER,-1,900,"",Collections.emptyList()))).equals("010123456789abcdef0123456789abcdefffffffff0000000000000384000000"),"Established schema1 wire changed");
        for(String versionName:new String[]{"0.3.2","0.3.3","0.3.42-candidate1","0.3.999999+local"})check(AbilityNetwork.versionCanLoadout(versionName),"Compatible client not recognized");
        for(String versionName:new String[]{null,"","0.3.0","0.3.1","0.3.2evil","0.3.-2","0.3.1000000","0.4.0","1.3.2"})check(!AbilityNetwork.versionCanLoadout(versionName),"Unknown/legacy client sent schema2");
        System.out.println("PASS: "+checks+" loadout schema, exact-weapon request, legacy-wire and version-gate checks. No native/C2S transport claim.");
    }
}
