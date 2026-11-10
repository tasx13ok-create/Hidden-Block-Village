package village.controls;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import village.controls.Model.*;

public final class ProfileStore {
    public static class Context {
        public String layout="Custom";
        public Map<String,Stroke> original=new LinkedHashMap<>(),active=new LinkedHashMap<>();
        public Map<String,Map<String,Stroke>> profiles=new TreeMap<>();
    }
    public static final class Data extends Context {
        public int version=1;
        public String initialPreset="",profileKind="";
        public Map<String,Context> contexts=new LinkedHashMap<>();
    }
    public final Path file;
    public Data data=new Data();
    public String warning="";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public ProfileStore(Path file) { this.file=file; }
    public void load() {
        if(!Files.exists(file))return;
        try(Reader reader=Files.newBufferedReader(file,StandardCharsets.UTF_8)) {
            if(Files.size(file)>16*1024*1024)throw new IOException("Profile file is too large");
            Data parsed=JSON.fromJson(reader,Data.class);
            if(parsed==null||parsed.version!=1)throw new IOException("Unsupported profile version");
            cleanContext(parsed);
            Map<String,Context> validContexts=new LinkedHashMap<>();
            if(parsed.contexts!=null)for(Map.Entry<String,Context> e:parsed.contexts.entrySet())if(validKind(e.getKey())&&e.getValue()!=null){cleanContext(e.getValue());validContexts.put(e.getKey(),e.getValue());}
            parsed.contexts=validContexts;if(!validKind(parsed.profileKind))parsed.profileKind="";
            data=parsed;
        } catch(Exception ex) {
            warning="Could not read saved profiles. Your file is preserved; a backup is made before saving.";
        }
    }
    private static void cleanContext(Context c) {
        c.original=clean(c.original);c.active=clean(c.active);Map<String,Map<String,Stroke>> profiles=new TreeMap<>();
        if(c.profiles!=null)for(Map.Entry<String,Map<String,Stroke>> e:c.profiles.entrySet()){if(profiles.size()>=100)break;if(validName(e.getKey()))profiles.put(e.getKey(),clean(e.getValue()));}
        c.profiles=profiles;if(c.layout==null||c.layout.length()>64)c.layout="Custom";
    }
    private static boolean validKind(String kind){return Arrays.asList("legacy","vale","hybrid","generic").contains(kind);}
    private static Context snapshot(Context source){Context c=new Context();c.layout=source.layout;c.original=new LinkedHashMap<>(source.original);c.active=new LinkedHashMap<>(source.active);for(Map.Entry<String,Map<String,Stroke>> e:source.profiles.entrySet())c.profiles.put(e.getKey(),new LinkedHashMap<>(e.getValue()));return c;}
    /** Separate original Naruto and Vale bindings even when one client folder is reused. */
    public boolean selectContext(String kind) {
        if(!validKind(kind))throw new IllegalArgumentException("Unsupported profile context");
        if(data.profileKind.isEmpty()) {
            boolean legacy=false,vale=false;
            for(String id:data.active.keySet()){legacy|=id.contains("key.mcreator.")||id.contains("narutomod");vale|=id.contains("key.fieldcontrols.cast");}
            data.profileKind=legacy?(vale?"hybrid":"legacy"):vale?"vale":kind;
        }
        if(data.profileKind.equals(kind))return false;
        String previousLayout=data.layout;boolean hadActive=!data.active.isEmpty();data.contexts.put(data.profileKind,snapshot(data));
        Context target=data.contexts.get(kind);boolean fresh=target==null;if(target==null)target=new Context();
        Context copy=snapshot(target);data.layout=copy.layout;data.original=copy.original;data.active=copy.active;data.profiles=copy.profiles;data.profileKind=kind;
        data.initialPreset="";
        if(fresh&&hadActive)for(Preset p:Preset.values())if(p.title.equals(previousLayout))data.initialPreset=p.name();
        return fresh;
    }
    private static Map<String,Stroke> clean(Map<String,Stroke> map) {
        Map<String,Stroke> out=new LinkedHashMap<>();if(map==null)return out;
        for(Map.Entry<String,Stroke> e:map.entrySet()) {
            if(out.size()>=4096)break;Stroke s=e.getValue();
            if(e.getKey()!=null&&e.getKey().length()<=512&&s!=null&&s.mod!=null&&s.code>=-100&&s.code<=511)out.put(e.getKey(),new Stroke(s.code,s.mod));
        }
        return out;
    }
    public static boolean validName(String name) { return name!=null&&!name.trim().isEmpty()&&!name.trim().equalsIgnoreCase("Startup backup")&&name.length()<=32&&!name.matches(".*[\\p{Cntrl}].*"); }
    public void save() throws IOException {
        if(validKind(data.profileKind))data.contexts.put(data.profileKind,snapshot(data));
        Files.createDirectories(file.toAbsolutePath().getParent());
        if(Files.exists(file))Files.copy(file,file.resolveSibling(file.getFileName()+".previous"),StandardCopyOption.REPLACE_EXISTING);
        Path temp=file.resolveSibling(file.getFileName()+".tmp");
        try(Writer writer=Files.newBufferedWriter(temp,StandardCharsets.UTF_8)) { JSON.toJson(data,writer); }
        try { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException ex) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
        warning="";
    }
    public void put(String name,Map<String,Stroke> values) throws IOException {
        name=name.trim();if(!validName(name))throw new IOException("Use a profile name of 1 to 32 characters.");
        if(data.profiles.size()>=100&&!data.profiles.containsKey(name))throw new IOException("100 profiles are already saved.");
        Map<String,Map<String,Stroke>> before=new TreeMap<>(data.profiles);
        data.profiles.put(name,new LinkedHashMap<>(values));
        try { save(); }catch(IOException ex){data.profiles=before;throw ex;}
    }
    public void remove(String name) throws IOException {
        Map<String,Stroke> before=data.profiles.remove(name);
        try{save();}catch(IOException ex){if(before!=null)data.profiles.put(name,before);throw ex;}
    }
}
