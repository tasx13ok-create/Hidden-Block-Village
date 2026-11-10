package village.fieldhud;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Local preferences only, separated by a hashed world/server and player identity. */
public final class JutsuLoadoutStore {
    private final File file;
    private final Map<String,JutsuLoadout> profiles=new LinkedHashMap<>();
    private String error="";
    public JutsuLoadoutStore(File file) {
        this.file=file;
        if(!file.exists())return;
        try {
            if(file.length()>524288)throw new IOException("Loadout file is too large");
            JsonObject root=new JsonParser().parse(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)throw new IOException("Unsupported loadout file version");
            JsonObject saved=root.getAsJsonObject("profiles");
            if(saved==null||saved.entrySet().size()>128)throw new IOException("Invalid loadout profiles");
            for(Map.Entry<String,JsonElement> item:saved.entrySet()){
                if(!item.getKey().matches("[a-f0-9]{64}"))throw new IOException("Invalid loadout context");
                JsonObject profile=item.getValue().getAsJsonObject();JsonArray rows=profile.getAsJsonArray("slots");
                if(rows==null||rows.size()!=JutsuLoadout.SIZE)throw new IOException("A saved loadout must have six slots");
                List<JutsuSlot> slots=new ArrayList<>();
                for(JsonElement row:rows){
                    JsonObject slot=row.getAsJsonObject();String id=slot.get("id").getAsString();
                    slots.add(id.isEmpty()?JutsuSlot.empty():new JutsuSlot(id,slot.get("title").getAsString(),slot.get("icon").getAsString(),slot.get("meta").getAsInt(),slot.get("kind").getAsString(),slot.get("school").getAsString()));
                }
                profiles.put(item.getKey(),new JutsuLoadout(slots,profile.get("selected").getAsInt()));
            }
        }catch(IOException|RuntimeException failure){profiles.clear();error="Saved loadouts could not be read; the existing file was preserved";}
    }
    public String error(){return error;}
    public JutsuLoadout get(String context){return profiles.get(context);}
    public void put(String context,JutsuLoadout loadout)throws IOException {
        if(!error.isEmpty())throw new IOException(error);
        if(context==null||!context.matches("[a-f0-9]{64}")||loadout==null)throw new IOException("Loadout context is unavailable");
        if(!profiles.containsKey(context)&&profiles.size()>=128)throw new IOException("Loadout profile limit reached");
        Map<String,JutsuLoadout> next=new LinkedHashMap<>(profiles);next.put(context,loadout);
        JsonObject root=new JsonObject(),all=new JsonObject();root.addProperty("schema",1);root.add("profiles",all);
        for(Map.Entry<String,JutsuLoadout> profile:next.entrySet()){
            JsonObject saved=new JsonObject();saved.addProperty("selected",profile.getValue().selected);JsonArray slots=new JsonArray();saved.add("slots",slots);all.add(profile.getKey(),saved);
            for(JutsuSlot slot:profile.getValue().slots){
                JsonObject row=new JsonObject();row.addProperty("id",slot.id);
                if(!slot.emptySlot()){
                    row.addProperty("title",slot.title);row.addProperty("icon",slot.iconItemId);row.addProperty("meta",slot.iconMeta);row.addProperty("kind",slot.kind);row.addProperty("school",slot.school);
                }
                slots.add(row);
            }
        }
        Path target=file.toPath().toAbsolutePath(),parent=target.getParent();Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,"fieldhud-loadout-",".tmp");
        try{
            Files.write(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
            profiles.clear();profiles.putAll(next);
        }finally{Files.deleteIfExists(temporary);}
    }
    public static String context(String worldOrServer,UUID player){
        if(worldOrServer==null||player==null)throw new IllegalArgumentException("Missing loadout context");
        try{
            byte[] hash=MessageDigest.getInstance("SHA-256").digest((worldOrServer+"\n"+player).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder(64);for(byte b:hash)hex.append(String.format(Locale.ROOT,"%02x",b&255));return hex.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
