package village.residents;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Independently authored, bounded dialogue format. This is not a scripting API. */
public final class DialogueBook {
    public enum Action { PAGE, ACADEMY, CONTRACTS, QUARTERMASTER, TOOLS, SPIRIT, ACCEPT_CONTRACT, CLAIM_CONTRACT }
    public static final class Choice {
        public final String id,label,target;public final Action action;
        Choice(String id,String label,Action action,String target){this.id=id;this.label=label;this.action=action;this.target=target;}
    }
    public static final class Page {
        public final String id;public final List<String> lines;public final List<Choice> choices;
        Page(String id,List<String> lines,List<Choice> choices){this.id=id;this.lines=Collections.unmodifiableList(lines);this.choices=Collections.unmodifiableList(choices);}
    }
    public static final class Role {
        public final String start;public final Map<String,Page> pages;
        Role(String start,Map<String,Page> pages){this.start=start;this.pages=Collections.unmodifiableMap(pages);}
    }
    public static final class Book {
        public final Map<ResidentRole,Role> roles;
        Book(Map<ResidentRole,Role> roles){this.roles=Collections.unmodifiableMap(roles);}
    }
    public static final int MAX_BYTES=65536;
    private static Book current=defaults();private static long generation=1;
    private DialogueBook(){}
    public static Book current(){return current;}
    public static long generation(){return generation;}
    public static File file(){return new File(ValeResidents.authoringDirectory,"dialogue.json");}
    public static String reload() {
        try{
            File f=file();
            if(!f.isFile()){
                Files.createDirectories(f.toPath().getParent());
                Files.write(f.toPath(),defaultJson().getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
            }
            long size=Files.size(f.toPath());if(size>MAX_BYTES)throw new IllegalArgumentException("File exceeds64KiB");
            byte[] bytes=Files.readAllBytes(f.toPath());if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("File exceeds64KiB");
            Book parsed=parse(new String(bytes,StandardCharsets.UTF_8));
            current=parsed;generation++;DialogueSessions.clear();
            return "Loaded dialogue for3 resident roles from "+f.getPath()+". Existing entities and player data are unchanged.";
        }catch(Exception e){return "Dialogue reload rejected; previous valid definitions retained and file preserved: "+e.getMessage();}
    }
    private static void fields(JsonObject o,String... allowed){Set<String> keys=new HashSet<>(Arrays.asList(allowed));for(Map.Entry<String,JsonElement> e:o.entrySet())if(!keys.contains(e.getKey()))throw new IllegalArgumentException("Unknown field "+e.getKey());}
    private static String text(JsonObject o,String key,int maximum){JsonElement e=o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Expected string "+key);String value=e.getAsString();if(value.trim().isEmpty()||value.length()>maximum)throw new IllegalArgumentException("Invalid length "+key);for(char c:value.toCharArray())if(c<32||c==127)throw new IllegalArgumentException("Control character in "+key);return value;}
    private static String id(JsonObject o,String key){String value=text(o,key,48);if(!value.matches("[a-z][a-z0-9_]{0,47}"))throw new IllegalArgumentException("Invalid identifier "+key);return value;}
    private static JsonObject object(JsonElement e,String what){if(e==null||!e.isJsonObject())throw new IllegalArgumentException("Expected object "+what);return e.getAsJsonObject();}
    private static JsonArray array(JsonObject o,String key,int maximum){JsonElement e=o.get(key);if(e==null||!e.isJsonArray())throw new IllegalArgumentException("Expected array "+key);JsonArray a=e.getAsJsonArray();if(a.size()<1||a.size()>maximum)throw new IllegalArgumentException("Invalid array length "+key);return a;}
    public static Book parse(String json){
        if(json.length()>MAX_BYTES)throw new IllegalArgumentException("Definition too large");
        JsonObject root=object(new JsonParser().parse(json),"root");fields(root,"schema","roles");
        if(!root.has("schema")||!root.get("schema").isJsonPrimitive()||!root.get("schema").getAsJsonPrimitive().isNumber()||!root.get("schema").getAsString().equals("1"))throw new IllegalArgumentException("Unsupported dialogue schema");
        JsonObject roles=object(root.get("roles"),"roles");if(roles.entrySet().size()!=3)throw new IllegalArgumentException("Exactly academy/contracts/quartermaster roles required");
        Map<ResidentRole,Role> result=new EnumMap<>(ResidentRole.class);
        for(ResidentRole role:ResidentRole.values()){
            JsonObject r=object(roles.get(role.id),role.id);fields(r,"start","pages");String start=id(r,"start");Map<String,Page> pages=new LinkedHashMap<>();
            for(JsonElement pe:array(r,"pages",16)){
                JsonObject p=object(pe,"page");fields(p,"id","lines","choices");String pageId=id(p,"id");List<String> lines=new ArrayList<>();
                for(JsonElement le:array(p,"lines",4)){JsonObject wrapper=new JsonObject();wrapper.add("line",le);lines.add(text(wrapper,"line",200));}
                List<Choice> choices=new ArrayList<>();Set<String> ids=new HashSet<>();
                for(JsonElement ce:array(p,"choices",6)){
                    JsonObject c=object(ce,"choice");fields(c,"id","label","action","target");String key=id(c,"id");if(!ids.add(key))throw new IllegalArgumentException("Duplicate choice "+key);
                    Action action;try{action=Action.valueOf(text(c,"action",32));}catch(IllegalArgumentException bad){throw new IllegalArgumentException("Unsupported action");}
                    boolean target=action==Action.PAGE||action==Action.ACCEPT_CONTRACT;String argument=target?id(c,"target"):"";
                    if(!target&&c.has("target"))throw new IllegalArgumentException("Action does not accept a target");
                    choices.add(new Choice(key,text(c,"label",64),action,argument));
                }
                if(pages.put(pageId,new Page(pageId,lines,choices))!=null)throw new IllegalArgumentException("Duplicate page "+pageId);
            }
            if(!pages.containsKey(start))throw new IllegalArgumentException("Start page missing");
            for(Page p:pages.values())for(Choice c:p.choices)if(c.action==Action.PAGE&&!pages.containsKey(c.target))throw new IllegalArgumentException("Page target missing: "+c.target);
            result.put(role,new Role(start,pages));
        }return new Book(result);
    }
    private static Book defaults(){return parse(defaultJson());}
    public static String defaultJson(){return "{\n  \"schema\":1,\n  \"roles\":{\n"+
        "    \"academy\":{\"start\":\"welcome\",\"pages\":[{\"id\":\"welcome\",\"lines\":[\"Welcome to the field academy. Learn a technique, then practice it with care.\"],\"choices\":[{\"id\":\"study\",\"label\":\"Study techniques\",\"action\":\"ACADEMY\"},{\"id\":\"advice\",\"label\":\"How do I begin?\",\"action\":\"PAGE\",\"target\":\"advice\"}]},{\"id\":\"advice\",\"lines\":[\"Choose your order with /vale join, then earn experience through contracts. The academy enforces its own level and merit requirements.\"],\"choices\":[{\"id\":\"back\",\"label\":\"Back\",\"action\":\"PAGE\",\"target\":\"welcome\"}]}]},\n"+
        "    \"contracts\":{\"start\":\"welcome\",\"pages\":[{\"id\":\"welcome\",\"lines\":[\"The settlement needs reliable hands. Review a contract, deliver supplies, or turn in completed work.\"],\"choices\":[{\"id\":\"journal\",\"label\":\"Review contracts\",\"action\":\"CONTRACTS\"},{\"id\":\"supplies\",\"label\":\"Accept Granary Relief\",\"action\":\"ACCEPT_CONTRACT\",\"target\":\"supply_run\"},{\"id\":\"claim\",\"label\":\"Turn in active contract\",\"action\":\"CLAIM_CONTRACT\"}]}]},\n"+
        "    \"quartermaster\":{\"start\":\"welcome\",\"pages\":[{\"id\":\"welcome\",\"lines\":[\"Merit recognizes service. I can show equipment, field tools and spirit armor; every purchase follows the settlement's rules.\"],\"choices\":[{\"id\":\"ward\",\"label\":\"Order equipment\",\"action\":\"QUARTERMASTER\"},{\"id\":\"tools\",\"label\":\"Field tools\",\"action\":\"TOOLS\"},{\"id\":\"spirit\",\"label\":\"Spirit armor\",\"action\":\"SPIRIT\"}]}]}\n  }\n}\n";}
}
