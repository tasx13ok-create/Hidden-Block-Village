package village.vale.expedition;

import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Bounded authored data. No commands, scripts, inferred dungeon origins or guessed anchors. */
public final class GateBook {
    public static final int MAX_BYTES=65536;
    public static final class Point {
        public final int dimension,x,y,z;
        Point(int d,int x,int y,int z){dimension=d;this.x=x;this.y=y;this.z=z;}
    }
    public static final class Giver {public final UUID uuid;public final Point point;Giver(UUID uuid,Point point){this.uuid=uuid;this.point=point;}}
    public static final class Stage {
        public final String id,text,type,next,retry;final JsonObject objective;final Map<String,String> branches=new LinkedHashMap<>(),labels=new LinkedHashMap<>();
        Stage(JsonObject s){id=id(s,"id");text=text(s,"text",512);objective=obj(s,"objective");type=text(objective,"type",32);
            next=s.has("next")&&!s.get("next").isJsonNull()?id(s,"next"):null;retry=s.has("on_fail")?id(obj(s,"on_fail"),"retry_from"):id;
            if(s.has("branches"))for(JsonElement element:array(s,"branches",3)){JsonObject b=element.getAsJsonObject();fields(b,"id,label,next");String key=id(b,"id");need(!branches.containsKey(key),"Duplicate branch");branches.put(key,id(b,"next"));labels.put(key,text(b,"label",64));}}
        public int number(String key,int fallback){return objective.has(key)?integer(objective,key,1,360):fallback;}
        public String string(String key){return objective.has(key)?text(objective,key,96):"";}
        public List<String> markers(){List<String> out=new ArrayList<>();if(objective.has("anchor"))out.add(id(objective,"anchor"));if(objective.has("anchors"))for(JsonElement e:array(objective,"anchors",8))out.add(identifier(e.getAsString()));return out;}
        public List<String> profiles(){List<String> out=new ArrayList<>();if(objective.has("profiles"))for(JsonElement e:array(objective,"profiles",9)){JsonObject p=e.getAsJsonObject();for(int n=0;n<integer(p,"count",1,9);n++)out.add(id(p,"key"));}need(out.size()<=9,"Too many run actors");return out;}
    }
    public static final class Mission {
        public final String id,title,giver,start;public final int level,cooldown;public final Map<String,Stage> stages=new LinkedHashMap<>();public final List<String> prerequisites=new ArrayList<>();
        final JsonObject first,repeat;public String disabled="";
        Mission(JsonObject m){id=id(m,"id");title=text(m,"title",128);giver=id(m,"giver");start=id(m,"start_stage");level=integer(m,"level",1,100);
            for(JsonElement e:array(obj(m,"prerequisites"),"contracts_completed",16))prerequisites.add(identifier(e.getAsString()));
            for(JsonElement e:array(m,"stages",32)){Stage stage=new Stage(e.getAsJsonObject());need(!stages.containsKey(stage.id),"Duplicate stage");stages.put(stage.id,stage);}
            JsonObject rewards=obj(m,"rewards");first=obj(rewards,"first_clear");repeat=obj(rewards,"repeat");cooldown=integer(rewards,"cooldown_ticks",6000,12000);
            need(text(rewards,"receipt",96).equals("gate/{player}/{mission}/{run}"),"Unsafe receipt format");
        }
    }
    public final String version,authoredVersion;public final Map<String,Mission> missions=new LinkedHashMap<>();public final Map<String,Point> anchors=new LinkedHashMap<>(),actorSpawns=new LinkedHashMap<>(),markerBlocks=new LinkedHashMap<>();
    public final Map<String,Giver> givers=new LinkedHashMap<>();final Map<String,JsonObject> dialogues=new LinkedHashMap<>();
    private GateBook(String authoredVersion){this.authoredVersion=authoredVersion;this.version="";}
    private GateBook(GateBook parsed,String identity){authoredVersion=parsed.authoredVersion;version=authoredVersion+"/sha256/"+identity;missions.putAll(parsed.missions);anchors.putAll(parsed.anchors);actorSpawns.putAll(parsed.actorSpawns);markerBlocks.putAll(parsed.markerBlocks);givers.putAll(parsed.givers);dialogues.putAll(parsed.dialogues);}
    public static GateBook load(File content,File sites) throws IOException {
        JsonObject doc=read(content);fields(doc,"schema,content_version,status,note,anchors,residents,missions");need(integer(doc,"schema",1,1)==1,"Unsupported gate schema");
        boolean sitesPresent=sites.isFile();GateBook book=new GateBook(text(doc,"content_version",64));JsonObject binding=sitesPresent?read(sites):new JsonObject();
        if(binding.entrySet().size()>0){fields(binding,"schema,placements,givers,actor_spawns,marker_blocks");integer(binding,"schema",1,1);}
        JsonObject placements=binding.has("placements")?obj(binding,"placements"):new JsonObject();Map<String,String> unresolved=new HashMap<>();
        JsonObject authored=obj(doc,"anchors");need(authored.entrySet().size()<=128,"Too many anchors");
        for(Map.Entry<String,JsonElement> row:authored.entrySet()){
            String aid=identifier(row.getKey());JsonObject a=row.getValue().getAsJsonObject();fields(a,"dungeon,kind,local_pos,measure_hint");String dungeon=id(a,"dungeon");
            if(!a.has("local_pos")||a.get("local_pos").isJsonNull()){unresolved.put(aid,"unmeasured authored anchor "+aid);continue;}
            if(!placements.has(dungeon)){unresolved.put(aid,"missing approved placement "+dungeon);continue;}
            JsonObject placement=obj(placements,dungeon);fields(placement,"dimension,origin");int[] local=position(a,"local_pos",-4096,4096),origin=position(placement,"origin",-29999000,29999000);
            long x=(long)local[0]+origin[0],y=(long)local[1]+origin[1],z=(long)local[2]+origin[2];need(Math.abs(x)<30000000&&Math.abs(z)<30000000&&y>=1&&y<=253,"Placed anchor outside world limits");
            book.anchors.put(aid,new Point(integer(placement,"dimension",-4096,4096),(int)x,(int)y,(int)z));
        }
        if(binding.has("givers"))for(Map.Entry<String,JsonElement> e:obj(binding,"givers").entrySet()){
            JsonObject b=e.getValue().getAsJsonObject();fields(b,"uuid,dimension,pos");book.givers.put(identifier(e.getKey()),new Giver(UUID.fromString(text(b,"uuid",36)),point(b)));
        }
        if(binding.has("actor_spawns"))for(Map.Entry<String,JsonElement> e:obj(binding,"actor_spawns").entrySet()){
            need(e.getKey().matches("gate_[a-z0-9_]{1,56}/[a-z0-9_]{1,64}"),"Unsafe actor spawn binding");JsonObject b=e.getValue().getAsJsonObject();fields(b,"dimension,pos");book.actorSpawns.put(e.getKey(),point(b));
        }
        if(binding.has("marker_blocks"))for(Map.Entry<String,JsonElement> e:obj(binding,"marker_blocks").entrySet()){JsonObject b=e.getValue().getAsJsonObject();fields(b,"dimension,pos");need(authored.has(identifier(e.getKey())),"Unknown marker block binding");book.markerBlocks.put(e.getKey(),point(b));}
        Set<String> residents=new HashSet<>();for(JsonElement element:array(doc,"residents",16)){
            JsonObject resident=element.getAsJsonObject();fields(resident,"id,display_name,dungeon,role,nature,personality,dialogue,routine,fallback_anchor,reactions");String rid=id(resident,"id");need(residents.add(rid),"Duplicate resident");
            if(resident.has("routine"))for(JsonElement entry:array(resident,"routine",4)){JsonObject routine=entry.getAsJsonObject();fields(routine,"phase,anchor,activity");need(Arrays.asList("dawn","day","dusk","night").contains(text(routine,"phase",8)),"Unknown routine phase");need(authored.has(id(routine,"anchor")),"Unknown routine anchor");text(routine,"activity",256);}
            if(resident.has("reactions"))for(JsonElement entry:array(resident,"reactions",8)){JsonObject reaction=entry.getAsJsonObject();fields(reaction,"event,response");id(reaction,"event");text(reaction,"response",256);}
            String dialogue=text(resident,"dialogue",96);need(dialogue.matches("dialogue/[a-z0-9_]+[.]json"),"Unsafe dialogue filename");
            JsonObject d=read(new File(content.getParentFile(),dialogue));validateDialogue(d,rid);book.dialogues.put(rid,d);
        }
        for(JsonElement element:array(doc,"missions",16)){
            JsonObject m=element.getAsJsonObject();fields(m,"id,dungeon,title,giver,level,prerequisites,target_minutes,summary,start_stage,stages,failure,rewards");
            fields(obj(m,"prerequisites"),"contracts_completed");fields(obj(m,"failure"),"death,disconnect,abandon");
            fields(obj(m,"rewards"),"first_clear,repeat,receipt,cooldown_ticks");validateReward(obj(obj(m,"rewards"),"first_clear"),true);validateReward(obj(obj(m,"rewards"),"repeat"),false);
            Mission mission=new Mission(m);need(mission.id.startsWith("gate_")&&residents.contains(mission.giver),"Unknown mission giver");need(!book.missions.containsKey(mission.id),"Duplicate mission");
            for(Stage stage:mission.stages.values()){
                JsonObject raw=null;for(JsonElement e:array(m,"stages",32))if(id(e.getAsJsonObject(),"id").equals(stage.id))raw=e.getAsJsonObject();
                fields(raw,"id,text,objective,next,branches,on_fail");if(raw.has("on_fail"))fields(obj(raw,"on_fail"),"kind,effect,retry_from");validateObjective(stage);
                List<String> required=stage.markers();if(stage.objective.has("to"))required.add(id(stage.objective,"to"));
                for(String key:required){need(authored.has(key),"Unknown anchor "+key);if(unresolved.containsKey(key)&&mission.disabled.isEmpty())mission.disabled=unresolved.get(key);}
                if(stage.type.startsWith("interact_"))for(String key:stage.markers())if(!book.markerBlocks.containsKey(key)&&mission.disabled.isEmpty())mission.disabled="missing measured clickable block "+key;
                if((stage.type.equals("escort_actor")||stage.type.equals("protect_actor"))&&!book.actorSpawns.containsKey(mission.id+"/"+stage.id)&&mission.disabled.isEmpty())mission.disabled="missing measured actor spawn "+mission.id+"/"+stage.id;
                need(mission.stages.containsKey(stage.retry),"Unknown retry stage");
            }
            Set<String> reached=new HashSet<>();walk(mission,mission.start,new HashSet<>(),reached);need(reached.size()==mission.stages.size(),"Unreachable stage");
            if(!book.givers.containsKey(mission.giver)&&mission.disabled.isEmpty())mission.disabled="missing measured resident binding "+mission.giver;
            book.missions.put(mission.id,mission);
        }
        for(Map.Entry<String,JsonObject> entry:book.dialogues.entrySet())for(JsonElement page:array(entry.getValue(),"pages",32))for(JsonElement choice:array(page.getAsJsonObject(),"choices",6)){
            JsonObject c=choice.getAsJsonObject();if(text(c,"action",32).equals("ACCEPT_CONTRACT"))need(book.missions.containsKey(id(c,"target"))&&book.missions.get(id(c,"target")).giver.equals(entry.getKey()),"Dialogue accepts another resident's contract");
        }
        JsonObject complete=new JsonObject();complete.add("content",doc);complete.add("sites",binding);complete.addProperty("sites_present",sitesPresent);JsonObject dialogueIdentity=new JsonObject();for(Map.Entry<String,JsonObject> entry:book.dialogues.entrySet())dialogueIdentity.add(entry.getKey(),entry.getValue());complete.add("dialogues",dialogueIdentity);
        return new GateBook(book,digest(canonical(complete)));
    }
    private static void validateObjective(Stage s){
        String allowed="type";switch(s.type){
            case "reach_marker":allowed+=",anchor,radius";id(s.objective,"anchor");integer(s.objective,"radius",1,6);break;
            case "interact_marker":allowed+=",anchor";need(s.markers().size()==1,"Marker missing");break;
            case "interact_sequence":allowed+=",anchors,ordered,time_limit_seconds";need(s.markers().size()>0,"Empty sequence");need(s.objective.get("ordered").isJsonPrimitive()&&s.objective.get("ordered").getAsJsonPrimitive().isBoolean(),"Ordered must be boolean");break;
            case "choose_branch":need(s.branches.size()>=2&&s.next==null,"Branch needs two choices and no implicit next");break;
            case "defeat_group":allowed+=",anchor,profiles,time_limit_seconds";id(s.objective,"anchor");need(!s.profiles().isEmpty(),"Empty encounter");break;
            case "defend_point":allowed+=",anchor,seconds,profiles";id(s.objective,"anchor");integer(s.objective,"seconds",1,180);need(!s.profiles().isEmpty(),"Empty defense");break;
            case "escort_actor":allowed+=",actor,to,max_seconds";id(s.objective,"to");integer(s.objective,"max_seconds",1,180);need(Arrays.asList("archive_courier","stranded_caravaneer").contains(s.string("actor")),"Unknown escort actor");break;
            case "protect_actor":allowed+=",actor,seconds";integer(s.objective,"seconds",1,180);need(s.string("actor").equals("pilot_skiff"),"Unknown protected actor");break;
            case "deliver_items":allowed+=",item,count,plain_only";need(s.string("item").equals("minecraft:bread")&&s.objective.get("plain_only").getAsBoolean(),"Only declared plain bread delivery supported");integer(s.objective,"count",1,64);break;
            default:throw new IllegalArgumentException("Unsupported objective "+s.type);
        }need(s.type.equals("choose_branch")||s.branches.isEmpty(),"Only branch objectives can carry branches");fields(s.objective,allowed);if(s.objective.has("profiles"))for(JsonElement e:array(s.objective,"profiles",9))fields(e.getAsJsonObject(),"key,count");
        if(s.objective.has("time_limit_seconds"))integer(s.objective,"time_limit_seconds",1,360);
    }
    private static void validateReward(JsonObject reward,boolean first){
        fields(reward,first?"xp,merit,ledger_items,unlocks,lesson_preview":"xp,merit,ledger_items");integer(reward,"xp",0,500);integer(reward,"merit",0,4);
        for(JsonElement e:array(reward,"ledger_items",4)){JsonObject item=e.getAsJsonObject();fields(item,"key,count");need(id(item,"key").equals("travel_ration"),"Unreviewed reward item");integer(item,"count",1,2);}
        if(first){for(JsonElement e:array(reward,"unlocks",4))need(e.getAsString().matches("expedition_[a-z_]+_entry"),"Unsafe unlock");identifier(text(reward,"lesson_preview",64));}
    }
    private static void validateDialogue(JsonObject d,String resident){
        fields(d,"schema,resident,start,after_clear_start,pages");integer(d,"schema",1,1);need(id(d,"resident").equals(resident),"Dialogue resident mismatch");Set<String> pages=new HashSet<>();
        for(JsonElement e:array(d,"pages",32)){JsonObject p=e.getAsJsonObject();fields(p,"id,lines,choices");need(pages.add(id(p,"id")),"Duplicate page");for(JsonElement line:array(p,"lines",12))need(line.isJsonPrimitive()&&line.getAsString().length()<=256,"Dialogue line too long");
            Set<String> choices=new HashSet<>();for(JsonElement choice:array(p,"choices",6)){JsonObject c=choice.getAsJsonObject();fields(c,"id,label,action,target");need(choices.add(id(c,"id")),"Duplicate choice");text(c,"label",64);need(Arrays.asList("PAGE","ACCEPT_CONTRACT","CLAIM_CONTRACT").contains(text(c,"action",32)),"Executable dialogue action refused");}}
        need(pages.contains(id(d,"start"))&&pages.contains(id(d,"after_clear_start")),"Dialogue start missing");
        for(JsonElement e:array(d,"pages",32))for(JsonElement c:array(e.getAsJsonObject(),"choices",6))if(text(c.getAsJsonObject(),"action",32).equals("PAGE"))need(pages.contains(id(c.getAsJsonObject(),"target")),"Dialogue page target missing");
    }
    private static void walk(Mission m,String id,Set<String> stack,Set<String> seen){need(m.stages.containsKey(id),"Unknown forward stage");need(!stack.contains(id),"Forward graph cycle");if(!seen.add(id))return;stack.add(id);Stage s=m.stages.get(id);if(s.next!=null)walk(m,s.next,stack,seen);for(String target:s.branches.values())walk(m,target,stack,seen);stack.remove(id);}
    private static Point point(JsonObject b){int[] pos=position(b,"pos",-29999999,29999999);need(pos[1]>=1&&pos[1]<=253,"Invalid measured height");return new Point(integer(b,"dimension",-4096,4096),pos[0],pos[1],pos[2]);}
    private static int[] position(JsonObject o,String key,int min,int max){JsonArray a=array(o,key,3);need(a.size()==3,"Position must have three integers");int[] p=new int[3];for(int i=0;i<3;i++){need(a.get(i).isJsonPrimitive()&&a.get(i).getAsJsonPrimitive().isNumber(),"Position requires numeric values");String v=a.get(i).getAsString();need(v.matches("-?[0-9]{1,9}"),"Position not integral");p[i]=Integer.parseInt(v);need(p[i]>=min&&p[i]<=max,"Position outside bound");}return p;}
    static void need(boolean condition,String reason){if(!condition)throw new IllegalArgumentException(reason);}
    static void fields(JsonObject o,String allowed){Set<String> keys=new HashSet<>(Arrays.asList(allowed.split(",")));for(Map.Entry<String,JsonElement> entry:o.entrySet())need(keys.contains(entry.getKey()),"Unknown field "+entry.getKey());}
    static String identifier(String value){need(value.matches("[a-z0-9_]{1,64}"),"Unsafe identifier");return value;}
    static String id(JsonObject o,String key){return identifier(text(o,key,64));}
    static String text(JsonObject o,String key,int max){need(o.has(key)&&o.get(key).isJsonPrimitive()&&o.get(key).getAsJsonPrimitive().isString(),"Missing string "+key);String value=o.get(key).getAsString();need(!value.isEmpty()&&value.length()<=max,"String bound "+key);return value;}
    static int integer(JsonObject o,String key,int min,int max){need(o.has(key)&&o.get(key).isJsonPrimitive()&&o.get(key).getAsJsonPrimitive().isNumber()&&o.get(key).getAsString().matches("-?[0-9]{1,9}"),"Missing integer "+key);int value=o.get(key).getAsInt();need(value>=min&&value<=max,"Integer bound "+key);return value;}
    static JsonObject obj(JsonObject o,String key){need(o.has(key)&&o.get(key).isJsonObject(),"Missing object "+key);return o.getAsJsonObject(key);}
    static JsonArray array(JsonObject o,String key,int max){need(o.has(key)&&o.get(key).isJsonArray(),"Missing array "+key);JsonArray a=o.getAsJsonArray(key);need(a.size()<=max,"Array too long "+key);return a;}
    private static String digest(String text){try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder(64);for(byte value:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException("SHA-256 unavailable",impossible);}}
    private static String canonical(JsonElement value){
        if(value.isJsonObject()){StringBuilder out=new StringBuilder("{");TreeMap<String,JsonElement> sorted=new TreeMap<>();for(Map.Entry<String,JsonElement> entry:value.getAsJsonObject().entrySet())sorted.put(entry.getKey(),entry.getValue());for(Map.Entry<String,JsonElement> entry:sorted.entrySet()){if(out.length()>1)out.append(',');out.append(new JsonPrimitive(entry.getKey()).toString()).append(':').append(canonical(entry.getValue()));}return out.append('}').toString();}
        if(value.isJsonArray()){StringBuilder out=new StringBuilder("[");for(JsonElement element:value.getAsJsonArray()){if(out.length()>1)out.append(',');out.append(canonical(element));}return out.append(']').toString();}
        return value.toString();
    }
    private static JsonObject read(File file)throws IOException{
        need(!Files.isSymbolicLink(file.toPath())&&file.length()<=MAX_BYTES,"File link/size refused");byte[] bytes=Files.readAllBytes(file.toPath());need(bytes.length<=MAX_BYTES,"Content grew beyond bound");
        JsonReader reader=new JsonReader(new StringReader(new String(bytes,StandardCharsets.UTF_8)));reader.setLenient(false);JsonElement value=parse(reader,0);need(reader.peek()==JsonToken.END_DOCUMENT&&value.isJsonObject(),"Expected one root object");return value.getAsJsonObject();
    }
    private static JsonElement parse(JsonReader r,int depth)throws IOException{
        need(depth<=32,"JSON nesting bound");switch(r.peek()){
            case BEGIN_OBJECT:{r.beginObject();JsonObject o=new JsonObject();int n=0;while(r.hasNext()){String key=r.nextName();need(++n<=128&&!o.has(key),"Duplicate/too many object keys");o.add(key,parse(r,depth+1));}r.endObject();return o;}
            case BEGIN_ARRAY:{r.beginArray();JsonArray a=new JsonArray();while(r.hasNext()){need(a.size()<256,"JSON array bound");a.add(parse(r,depth+1));}r.endArray();return a;}
            case STRING:{String s=r.nextString();need(s.length()<=2048,"JSON string bound");return new JsonPrimitive(s);}
            case NUMBER:{String n=r.nextString();need(n.matches("-?[0-9]{1,9}"),"Nonintegral/unbounded number refused");return new JsonPrimitive(Integer.parseInt(n));}
            case BOOLEAN:return new JsonPrimitive(r.nextBoolean());case NULL:r.nextNull();return JsonNull.INSTANCE;
            default:throw new IllegalArgumentException("Unexpected JSON token");
        }
    }
}
