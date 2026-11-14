import com.google.gson.*;
import com.sk89q.jnbt.*;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.schematic.SchematicFormat;
import java.io.*;
import java.lang.management.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Original private compatibility probe. Calls the installed WorldEdit public
 * MCEdit reader unchanged; it never creates a world, edit session or paste. */
public final class WorldEditReadProbe {
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 private static void require(boolean okay,String message){if(!okay)throw new IllegalStateException(message);}
 private static JsonArray vector(Vector v){JsonArray a=new JsonArray();a.add(v.getBlockX());a.add(v.getBlockY());a.add(v.getBlockZ());return a;}
 private static Vector vector(JsonArray a){return new Vector(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
 private static JsonObject typed(Tag tag){
  JsonObject r=new JsonObject();r.addProperty("type",tag.getClass().getSimpleName());Object value=tag.getValue();
  if(tag instanceof CompoundTag){JsonObject fields=new JsonObject();for(Map.Entry<String,Tag> e:((CompoundTag)tag).getValue().entrySet())fields.add(e.getKey(),typed(e.getValue()));r.add("value",fields);}
  else if(tag instanceof ListTag){JsonArray items=new JsonArray();for(Tag child:((ListTag)tag).getValue())items.add(typed(child));r.addProperty("element_type",((ListTag)tag).getType().getSimpleName());r.add("value",items);}
  else r.add("value",GSON.toJsonTree(value));return r;
 }
 public static void main(String[] args)throws Exception{
  Path source=Paths.get(args[0]),expectations=Paths.get(args[1]),output=Paths.get(args[2]);
  JsonObject expected=new JsonParser().parse(new String(Files.readAllBytes(expectations),StandardCharsets.UTF_8)).getAsJsonObject();
  JsonObject result=new JsonObject();JsonArray points=new JsonArray(),tiles=new JsonArray();result.add("anchor_blocks",points);result.add("chests",tiles);
  result.addProperty("reader","SchematicFormat.MCEDIT.load(File)");result.addProperty("reader_class",SchematicFormat.MCEDIT.getClass().getName());
  result.addProperty("worldedit_code_source",SchematicFormat.class.getProtectionDomain().getCodeSource().getLocation().toString());
  result.addProperty("scope","ORIGINAL_WORLDEDIT_HEADLESS_FULL_CLIPBOARD_LOAD_ONLY; no world, server or paste");
  result.addProperty("configured_max_heap_bytes",Runtime.getRuntime().maxMemory());result.addProperty("java_version",System.getProperty("java.version"));
  long start=System.nanoTime();int exit=0;
  try{
   CuboidClipboard clipboard=SchematicFormat.MCEDIT.load(source.toFile());
   result.addProperty("full_load_elapsed_ms",(System.nanoTime()-start)/1000000L);
   result.add("dimensions",vector(clipboard.getSize()));result.add("origin",vector(clipboard.getOrigin()));result.add("offset",vector(clipboard.getOffset()));
   require(clipboard.getWidth()==500&&clipboard.getHeight()==250&&clipboard.getLength()==500,"Full-size dimensions differ after installed reader load");
   require(vector(clipboard.getOrigin()).equals(expected.get("origin")),"WorldEdit origin changed");require(vector(clipboard.getOffset()).equals(expected.get("offset")),"WorldEdit offset changed");
   for(JsonElement entry:expected.getAsJsonArray("points")){
    JsonObject e=entry.getAsJsonObject();BaseBlock block=clipboard.getBlock(vector(e.getAsJsonArray("at")));JsonObject p=new JsonObject();p.addProperty("name",e.get("name").getAsString());p.add("at",e.get("at"));p.addProperty("block",block.getId());p.addProperty("meta",block.getData());points.add(p);
    require(block.getId()==e.get("block").getAsInt()&&block.getData()==e.get("meta").getAsInt(),"WorldEdit anchor/block metadata mismatch: "+e.get("name"));
   }
   for(JsonElement entry:expected.getAsJsonArray("tiles")){
    JsonObject e=entry.getAsJsonObject();BaseBlock block=clipboard.getBlock(vector(e.getAsJsonArray("at")));JsonObject t=new JsonObject();t.add("at",e.get("at"));t.addProperty("block",block.getId());t.addProperty("meta",block.getData());t.addProperty("has_nbt",block.hasNbtData());tiles.add(t);
    require(block.getId()==54&&block.getData()==2&&block.hasNbtData(),"Actual WorldEdit chest block/metadata/NBT missing");JsonObject nbt=typed(block.getNbtData());t.add("typed_nbt",nbt);require(nbt.equals(e.get("typed_nbt")),"WorldEdit chest typed NBT changed");
   }
   result.addProperty("full_clipboard_cells",(long)clipboard.getWidth()*clipboard.getHeight()*clipboard.getLength());result.addProperty("used_heap_after_load_bytes",Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());result.addProperty("passed",true);
  }catch(Throwable failure){result.addProperty("passed",false);result.addProperty("failure",failure.toString());failure.printStackTrace(System.err);exit=1;}
  result.addProperty("total_elapsed_ms",(System.nanoTime()-start)/1000000L);JsonArray pools=new JsonArray();long peak=0;
  for(MemoryPoolMXBean bean:ManagementFactory.getMemoryPoolMXBeans())if(bean.getType()==MemoryType.HEAP){JsonObject p=new JsonObject();p.addProperty("name",bean.getName());p.addProperty("peak_used_bytes",bean.getPeakUsage().getUsed());pools.add(p);peak+=bean.getPeakUsage().getUsed();}
  result.add("heap_pool_peaks",pools);result.addProperty("sum_heap_pool_peaks_bytes",peak);Files.write(output,GSON.toJson(result).getBytes(StandardCharsets.UTF_8));System.out.println("WORLDEDIT_READER_RESULT "+result.get("passed")+" "+output);System.exit(exit);
 }
}
