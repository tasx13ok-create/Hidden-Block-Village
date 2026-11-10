package village.encounters;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class Profiles {
 public static final class Profile {public String key,title,kind,primary,secondary,phase2,phase3,companionRole;public float health,damage,speed,range,radius;public int cooldown,windup,duration;}
 public static final Map<String,Profile> ALL=new LinkedHashMap<>();
 public static void load(){if(!ALL.isEmpty())return;try(Reader r=new InputStreamReader(Profiles.class.getResourceAsStream("/encounter-profiles.json"),StandardCharsets.UTF_8)){
  Profile[] values=new Gson().fromJson(r,Profile[].class);for(Profile p:values){
   if(p.key==null||!p.key.matches("[a-z][a-z0-9_]{1,63}")||ALL.put(p.key,p)!=null||!Arrays.asList("mob","boss","companion").contains(p.kind)||p.health<20||p.health>800||p.damage<1||p.damage>10||p.cooldown<60||p.windup<20||p.range>24||p.radius>4)throw new IllegalStateException("Invalid original encounter profile");
  }if(ALL.size()!=136)throw new IllegalStateException("Expected136 original profiles");
 }catch(IOException e){throw new IllegalStateException(e);}}
 public static Profile first(String kind){load();for(Profile p:ALL.values())if(kind.equals(p.kind))return p;throw new IllegalArgumentException(kind);}
 public static Profile get(String key){load();return ALL.get(key);}
 private Profiles(){}
}
