package village.vale.bridge;
import com.google.gson.stream.*;import java.io.*;import java.nio.charset.*;import java.util.*;

/** Persistent bounded observations. Counts and class names are never native cast authority. */
public final class BridgeDiscovery {
    public static final int MAX_ROWS=2048,MAX_BYTES=3145728;
    private static final Set<String> KINDS=new HashSet<>(Arrays.asList("item","entity","damage"));
    private static final class Seen{final String kind,id;long count;final Set<String> details=new LinkedHashSet<>();Seen(String kind,String id){this.kind=kind;this.id=id;}}
    private final Map<String,Seen> rows=new TreeMap<>();private long dropped;
    public int size(){return rows.size();}public long dropped(){return dropped;}
    private static boolean detail(String value){if(value==null||value.length()>192)return false;for(int i=0;i<value.length();i++)if(value.charAt(i)<32||value.charAt(i)>126||value.charAt(i)=='"'||value.charAt(i)=='\\')return false;return true;}
    private static long increment(long n){return n==Long.MAX_VALUE?n:n+1;}
    public void note(String kind,String id,String value){
        if(!KINDS.contains(kind)||!BridgeMappings.validId(id))return;String key=kind+" "+id;Seen seen=rows.get(key);
        if(seen==null){if(rows.size()>=MAX_ROWS){dropped=increment(dropped);return;}seen=new Seen(kind,id);rows.put(key,seen);}
        seen.count=increment(seen.count);if(seen.details.size()<4&&detail(value))seen.details.add(value);
    }
    private static void need(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why);}
    private static String text(JsonReader r,int max)throws IOException{need(r.peek()==JsonToken.STRING,"Discovery string required");String s=r.nextString();need(s.length()<=max,"Discovery string too long");return s;}
    private static long number(JsonReader r)throws IOException{need(r.peek()==JsonToken.NUMBER,"Nonnegative integer required");String s=r.nextString();need(s.matches("0|[1-9][0-9]{0,18}"),"Invalid discovery count");try{return Long.parseLong(s);}catch(NumberFormatException bad){throw new IOException("Discovery count overflow",bad);}}
    public static BridgeDiscovery read(byte[] bytes)throws IOException{
        need(bytes!=null&&bytes.length<=MAX_BYTES,"Discovery bytes exceed bound");BridgeDiscovery result=new BridgeDiscovery();
        CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try(JsonReader r=new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes),decoder))){
            r.setLenient(false);r.beginObject();Set<String> root=new HashSet<>();
            while(r.hasNext()){
                String key=r.nextName();need(root.add(key),"Duplicate discovery root key");
                if(key.equals("schema"))need(number(r)==1,"Unsupported discovery schema");
                else if(key.equals("dropped"))result.dropped=number(r);
                else if(key.equals("seen")){
                    r.beginArray();while(r.hasNext()){
                        need(result.rows.size()<MAX_ROWS,"Too many discovery rows");r.beginObject();Set<String> fields=new HashSet<>();String kind=null,id=null;long count=-1;Set<String> details=new LinkedHashSet<>();
                        while(r.hasNext()){
                            String field=r.nextName();need(fields.add(field),"Duplicate discovery row key");
                            if(field.equals("kind"))kind=text(r,16);else if(field.equals("id"))id=text(r,128);else if(field.equals("count"))count=number(r);
                            else if(field.equals("details")){r.beginArray();int n=0;while(r.hasNext()){need(++n<=4,"Too many discovery details");String s=text(r,192);need(detail(s)&&details.add(s),"Invalid/duplicate discovery detail");}r.endArray();}
                            else throw new IOException("Unknown discovery row field");
                        }r.endObject();need(fields.size()==4&&KINDS.contains(kind)&&BridgeMappings.validId(id)&&count>=0,"Malformed discovery row");Seen seen=new Seen(kind,id);seen.count=count;seen.details.addAll(details);need(result.rows.put(kind+" "+id,seen)==null,"Duplicate discovery ID/kind");
                    }r.endArray();
                }else throw new IOException("Unknown discovery root field");
            }r.endObject();need(root.size()==3&&root.contains("schema")&&root.contains("seen")&&root.contains("dropped")&&r.peek()==JsonToken.END_DOCUMENT,"Incomplete/trailing discovery JSON");return result;
        }catch(IllegalStateException bad){throw new IOException("Malformed discovery JSON",bad);}
    }
    public byte[] bytes()throws IOException{
        StringWriter buffer=new StringWriter();JsonWriter out=new JsonWriter(buffer);out.setIndent("  ");out.beginObject();out.name("schema").value(1);out.name("dropped").value(dropped);out.name("seen").beginArray();
        for(Seen seen:rows.values()){out.beginObject();out.name("kind").value(seen.kind);out.name("id").value(seen.id);out.name("count").value(seen.count);out.name("details").beginArray();for(String s:seen.details)out.value(s);out.endArray();out.endObject();}
        out.endArray();out.endObject();out.close();byte[] result=(buffer.toString()+"\n").getBytes(StandardCharsets.UTF_8);need(result.length<=MAX_BYTES,"Discovery output exceeds byte bound");return result;
    }
}
