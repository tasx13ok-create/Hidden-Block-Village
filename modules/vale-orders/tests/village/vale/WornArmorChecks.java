package village.vale;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Actual packaged resource paths, pixel masks and conditional selection, independent of client GL. */
public final class WornArmorChecks {
    private static int checks;
    private static void require(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    private static String sha(Path p)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v&255));return s.toString();}
    public static void main(String[] args)throws Exception{
        Path repo=Paths.get(args[0]);Path assets=repo.resolve("modules/vale-orders/src/main/resources/assets");
        JsonObject ledger=new JsonParser().parse(new String(Files.readAllBytes(repo.resolve("artwork/worn-armor-v1/runtime-assets.json")),StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray rows=ledger.getAsJsonArray("assets");require(rows.size()==173,"Full authored runtime roster");Set<String> paths=new HashSet<>();
        for(JsonElement element:rows){JsonObject row=element.getAsJsonObject();String id=row.get("resource").getAsString();require(paths.add(id),"Resource collision");require(id.startsWith("valeorders:textures/models/armor/")&&!id.contains(".."),"Confinement");Path file=assets.resolve(id.replace(':','/'));require(Files.isRegularFile(file),"Missing "+id);require(sha(file).equals(row.get("sha256").getAsString()),"Changed "+id);BufferedImage image=ImageIO.read(file.toFile());require(image!=null&&image.getWidth()==256&&image.getHeight()==128,"UV resolution "+id);boolean opaque=false,clear=false,binary=true,headwearClear=true;for(int y=0;y<128;y++)for(int x=0;x<256;x++){int a=image.getRGB(x,y)>>>24;opaque|=a==255;clear|=a==0;binary&=a==0||a==255;if(x>=128&&y<64)headwearClear&=a==0;}require(opaque&&clear,"Empty or filled atlas");require(binary,"Interpolated alpha");require(headwearClear,"HEAD/headwear face mask "+id);}
        for(String ward:new String[]{"ember","current","gale","flint","bloom","rime","storm","dusk","dawn"})for(boolean legs:new boolean[]{false,true})for(boolean active:new boolean[]{false,true}){
            String family=ward.equals("storm")&&active?"lightning_chakra":ward;String path=ArmorSkinPaths.texture(ward,legs,active);require(path.equals("valeorders:textures/models/armor/"+family+"_layer_"+(legs?2:1)+".png"),"Conditional ward route");require(Files.isRegularFile(assets.resolve(path.replace(':','/'))),"Conditional ward missing");}
        JsonObject roster=new JsonParser().parse(new String(Files.readAllBytes(repo.resolve("artwork/worn-armor-v1/roster-manifest.json")),StandardCharsets.UTF_8)).getAsJsonObject();int garments=0;
        for(JsonElement el:roster.getAsJsonArray("entries")){JsonObject e=el.getAsJsonObject();String category=e.get("category").getAsString();if(category.equals("ward_set"))continue;garments++;String path=ArmorSkinPaths.garment(category,e.get("key").getAsString(),e.get("slot").getAsString().equals("LEGS"));require(path.equals("valeorders:"+e.getAsJsonArray("layers").get(0).getAsString().substring("assets/valeorders/".length())),"Actual spec route");require(paths.contains(path),"Unregistered garment asset");}
        require(garments==155,"All focus/outfit slots");
        for(String bad:new String[]{null,"", "../storm", "STORM", "unknown"}){try{ArmorSkinPaths.texture(bad,false,false);throw new AssertionError("Accepted bad ward");}catch(IllegalArgumentException expected){checks++;}}
        for(String bad:new String[]{null,"focus_../x","outfit_wrong","focus_x/evil","focus_ä"}){try{ArmorSkinPaths.garment("focus",bad,false);throw new AssertionError("Accepted bad key");}catch(IllegalArgumentException expected){checks++;}}
        try{ArmorSkinPaths.garment("../", "focus_x",false);throw new AssertionError("Bad category");}catch(IllegalArgumentException expected){checks++;}
        System.out.println("Worn armor checks PASS "+checks+"; native rendering/GL remains separate.");
    }
}
