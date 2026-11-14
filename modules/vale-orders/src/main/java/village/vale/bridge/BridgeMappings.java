package village.vale.bridge;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.nio.charset.*;
import java.util.*;

/** Strict operator-reviewed presentation map. Proposal inference is never runtime authority. */
public final class BridgeMappings {
    public static final int MAX_BYTES=131072,MAX_ROWS=512,MAX_ID=128,MAX_STYLE=64;
    private static final Set<String> HANDS=Collections.unmodifiableSet(new HashSet<>(Arrays.asList("spiral","charge","fireedge","windedge","lava","lightmode","none","dodge")));
    public interface Registry { boolean registered(String kind,String id);Set<String> weaponKeys(); }
    public static final class Row {
        public final String kind,id,when,owner,style;
        public Row(String kind,String id,String when,String owner,String style){this.kind=kind;this.id=id;this.when=when;this.owner=owner;this.style=style;}
        public String key(){return kind+"/"+when+"/"+id;}
        public boolean noop(){return "none".equals(style)||"dodge".equals(style);}
    }
    public final Map<String,Row> rows;
    private BridgeMappings(Map<String,Row> rows){this.rows=Collections.unmodifiableMap(new TreeMap<>(rows));}
    public static BridgeMappings empty(){return new BridgeMappings(Collections.emptyMap());}
    public static boolean validId(String value){return value!=null&&value.length()<=MAX_ID&&value.matches("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,96}");}
    public static boolean validStyle(String value,Set<String> weapons){return value!=null&&value.length()<=MAX_STYLE&&(HANDS.contains(value)||value.startsWith("weapon:")&&weapons.contains(value.substring(7)));}
    public static List<String> styles(Set<String> weapons){TreeSet<String> out=new TreeSet<>(HANDS);for(String key:weapons)out.add("weapon:"+key);return Collections.unmodifiableList(new ArrayList<>(out));}
    private static void require(boolean ok,String why)throws IOException{if(!ok)throw new IOException(why);}
    private static String string(JsonReader in,int max)throws IOException{require(in.peek()==JsonToken.STRING,"String required");String value=in.nextString();require(value.length()<=max,"String exceeds bound");return value;}
    public static void validate(Row row,Registry registry)throws IOException{
        require(row!=null&&validId(row.id),"Exact namespaced registry ID required");
        require("item".equals(row.kind)||"entity".equals(row.kind),"Unknown mapping kind");
        require(validStyle(row.style,registry.weaponKeys()),"Unknown presentation style");
        boolean spawn="entity".equals(row.kind)&&"spawn".equals(row.when)&&Arrays.asList("thrower","arrow","ownable","naruto_projectile","naruto_flow").contains(row.owner);
        boolean damage="entity".equals(row.kind)&&"damage".equals(row.when)&&"damage_source".equals(row.owner);
        boolean melee="item".equals(row.kind)&&"melee".equals(row.when)&&"direct_player".equals(row.owner);
        require(spawn||damage||melee,"Unsupported evidence/owner combination");
        require(registry.registered(row.kind,row.id),"Mapping ID is not registered in its declared kind: "+row.id);
    }
    public static BridgeMappings read(byte[] bytes,Registry registry)throws IOException{
        require(bytes!=null&&bytes.length<=MAX_BYTES,"Mapping file exceeds byte bound");
        CharsetDecoder decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        try(JsonReader in=new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes),decoder))){
            in.setLenient(false);require(in.peek()==JsonToken.BEGIN_OBJECT,"Mapping root must be an object");in.beginObject();
            Set<String> fields=new HashSet<>();Map<String,Row> result=new TreeMap<>();boolean verified=false;int schema=-1;
            while(in.hasNext()){
                String field=in.nextName();require(fields.add(field),"Duplicate root field");
                if(field.equals("schema")){require(in.peek()==JsonToken.NUMBER,"Integer schema required");String number=in.nextString();require(number.equals("1"),"Unsupported mapping schema");schema=1;}
                else if(field.equals("verified")){require(in.peek()==JsonToken.BOOLEAN,"Boolean verified required");verified=in.nextBoolean();}
                else if(field.equals("moves")){
                    require(in.peek()==JsonToken.BEGIN_ARRAY,"Moves must be an array");in.beginArray();
                    while(in.hasNext()){
                        require(result.size()<MAX_ROWS,"Too many mappings");require(in.peek()==JsonToken.BEGIN_OBJECT,"Mapping row must be an object");in.beginObject();Map<String,String> values=new HashMap<>();
                        while(in.hasNext()){String key=in.nextName();require(Arrays.asList("kind","id","when","owner","style").contains(key)&&!values.containsKey(key),"Unknown/duplicate row field");values.put(key,string(in,key.equals("id")?MAX_ID:MAX_STYLE));}
                        in.endObject();require(values.size()==5,"Every mapping field is required");Row row=new Row(values.get("kind"),values.get("id"),values.get("when"),values.get("owner"),values.get("style"));validate(row,registry);require(result.put(row.key(),row)==null,"Duplicate mapping key");
                    }in.endArray();
                }else throw new IOException("Unknown mapping root field: "+field);
            }
            in.endObject();require(in.peek()==JsonToken.END_DOCUMENT,"Trailing JSON content");
            require(schema==1&&fields.size()==3&&fields.contains("moves"),"Complete schema1 root required");require(verified,"Unverified proposal cannot become an accepted mapping by renaming");return new BridgeMappings(result);
        }catch(IllegalStateException|NumberFormatException failure){throw new IOException("Malformed mapping JSON",failure);}
    }
    public BridgeMappings changed(Row row,String removeKey,Registry registry)throws IOException{
        Map<String,Row> next=new TreeMap<>(rows);if(removeKey!=null)next.remove(removeKey);if(row!=null){validate(row,registry);next.put(row.key(),row);}require(next.size()<=MAX_ROWS,"Too many mappings");return new BridgeMappings(next);
    }
    public byte[] bytes()throws IOException{
        StringWriter buffer=new StringWriter();com.google.gson.stream.JsonWriter out=new com.google.gson.stream.JsonWriter(buffer);out.setIndent("  ");out.beginObject();out.name("schema").value(1);out.name("verified").value(true);out.name("moves").beginArray();
        for(Row row:rows.values()){out.beginObject();out.name("kind").value(row.kind);out.name("id").value(row.id);out.name("when").value(row.when);out.name("owner").value(row.owner);out.name("style").value(row.style);out.endObject();}
        out.endArray();out.endObject();out.close();byte[] result=(buffer.toString()+"\n").getBytes(StandardCharsets.UTF_8);require(result.length<=MAX_BYTES,"Serialized mappings exceed byte bound");return result;
    }
}
