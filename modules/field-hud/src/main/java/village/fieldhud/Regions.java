package village.fieldhud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

public final class Regions {
    public static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    public static final Map<String,Template> TEMPLATES=new LinkedHashMap<>();
    public static class Template {
        public String id,name,subtitle; public int color; public int[] dimensions,anchor;
    }
    public static class Zone {
        public String id,name,subtitle,world; public int color,dimension; public int[] min,max;
        public boolean contains(String scope,int dim,BlockPos p) {
            return world.equals(scope)&&dimension==dim&&p.getX()>=min[0]&&p.getX()<=max[0]
                &&p.getY()>=min[1]&&p.getY()<=max[1]&&p.getZ()>=min[2]&&p.getZ()<=max[2];
        }
    }
    public static class Data { public int format=1; public List<Zone> zones=new ArrayList<>(); }
    public static Data data=new Data();
    private static File file;
    public static String error="";
    public static void load(File directory) {
        file=new File(directory,"fieldhud-regions.json");
        try(InputStream in=Regions.class.getResourceAsStream("/assets/fieldhud/regions.json")) {
            Template[] templates=GSON.fromJson(new InputStreamReader(in,StandardCharsets.UTF_8),Template[].class);
            for(Template t:templates) TEMPLATES.put(t.id,t);
        } catch(Exception e) { error="Build templates could not be loaded."; }
        reload();
    }
    public static void reload() {
        if(!file.exists()) { data=new Data(); return; }
        try(Reader r=Files.newBufferedReader(file.toPath(),StandardCharsets.UTF_8)) {
            Data next=GSON.fromJson(r,Data.class);
            if(next==null||next.format!=1||next.zones==null||next.zones.size()>128) throw new IOException("Invalid region format or count");
            for(Zone z:next.zones) {
                if(z==null||z.id==null||z.name==null||z.name.length()>80||z.subtitle==null||z.subtitle.length()>120
                    ||z.world==null||z.min==null||z.max==null||z.min.length!=3||z.max.length!=3) throw new IOException("Invalid zone");
                for(int a=0;a<3;a++) if(z.min[a]>z.max[a]) throw new IOException("Reversed bounds");
            }
            data=next; error="";
        } catch(Exception e) { error="Region file invalid; keeping last valid zones."; }
    }
    public static void save() throws IOException {
        File temp=new File(file.getParentFile(),file.getName()+".tmp");
        try(Writer w=Files.newBufferedWriter(temp.toPath(),StandardCharsets.UTF_8)) { GSON.toJson(data,w); }
        Files.move(temp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    public static String scope(Minecraft mc) {
        if(mc.getCurrentServerData()!=null) return "server:"+mc.getCurrentServerData().serverIP.toLowerCase(Locale.ROOT);
        return mc.getIntegratedServer()==null?"none":"local:"+mc.getIntegratedServer().getFolderName();
    }
    public static Zone at(Minecraft mc) {
        if(mc.player==null) return null;
        String scope=scope(mc);
        for(Zone z:data.zones) if(z.contains(scope,mc.player.dimension,mc.player.getPosition())) return z;
        return null;
    }
    public static Zone mark(Minecraft mc,String id,BlockPos paste) throws IOException {
        return mark(id,paste,scope(mc),mc.player.dimension);
    }
    static Zone mark(String id,BlockPos paste,String scope,int dimension) throws IOException {
        Template t=TEMPLATES.get(id);
        if(t==null) throw new IllegalArgumentException("Unknown build");
        Zone z=new Zone(); z.id=id; z.name=t.name; z.subtitle=t.subtitle; z.color=t.color;
        z.world=scope; z.dimension=dimension; z.min=new int[3]; z.max=new int[3];
        int[] p={paste.getX(),paste.getY(),paste.getZ()};
        for(int a=0;a<3;a++) { z.min[a]=p[a]-t.anchor[a]; z.max[a]=z.min[a]+t.dimensions[a]-1; }
        List<Zone> old=new ArrayList<>(data.zones);
        data.zones.removeIf(v->v.id.equals(id)&&v.world.equals(z.world)&&v.dimension==z.dimension);
        if(data.zones.size()>=128) { data.zones=old; throw new IOException("128 zone limit"); }
        data.zones.add(z);
        try { save(); } catch(IOException e) { data.zones=old; throw e; }
        return z;
    }
}
