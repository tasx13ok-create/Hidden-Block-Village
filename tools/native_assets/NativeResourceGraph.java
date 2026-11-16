package village.assetproof;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.util.ResourceLocation;

/** Thin metadata graph from actual Gson/ResourceLocation/variant parsers.
 * Geometry, image/audio bytes and class bodies are never emitted. */
public final class NativeResourceGraph {
    private static final int MAX=8*1024*1024;
    private static String hash(byte[] bytes)throws Exception{MessageDigest m=MessageDigest.getInstance("SHA-256");m.update(bytes);return hex(m.digest());}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static String fileHash(Path p)throws Exception{MessageDigest m=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)m.update(b,0,n);}return hex(m.digest());}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static byte[] read(ZipFile z,ZipEntry e)throws Exception{require(e.getSize()>=0&&e.getSize()<=MAX,"bounded model JSON");try(InputStream in=z.getInputStream(e);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n,total=0;while((n=in.read(b))!=-1){total+=n;require(total<=MAX,"stream JSON bound");out.write(b,0,n);}return out.toByteArray();}}
    private static String id(String raw){return new ResourceLocation(raw).toString();}
    private static void strings(JsonElement node,String pointer,JsonArray result){
        if(node.isJsonObject())for(Map.Entry<String,JsonElement> pair:node.getAsJsonObject().entrySet())strings(pair.getValue(),pointer+"/"+pair.getKey(),result);
        else if(node.isJsonArray()){int n=0;for(JsonElement child:node.getAsJsonArray())strings(child,pointer+"/"+(n++),result);}
        else if(node.isJsonPrimitive()&&node.getAsJsonPrimitive().isString()){
            String value=node.getAsString();if(value.length()>240||value.startsWith("#")||value.indexOf('\\')>=0||value.indexOf('\n')>=0)return;
            try{JsonObject row=new JsonObject();row.addProperty("pointer",pointer);row.addProperty("value",id(value));result.add(row);}catch(RuntimeException ignored){}
        }
    }
    private static JsonArray variants(ModelBlockDefinition definition,JsonObject raw){
        Set<String> models=new TreeSet<>();
        if(raw.has("variants")&&raw.get("variants").isJsonObject())for(Map.Entry<String,JsonElement> row:raw.getAsJsonObject("variants").entrySet()){
            if(definition.hasVariant(row.getKey()))for(Variant v:definition.getVariant(row.getKey()).getVariantList())models.add(v.getModelLocation().toString());
        }
        for(VariantList list:definition.getMultipartVariants())for(Variant v:list.getVariantList())models.add(v.getModelLocation().toString());
        JsonArray out=new JsonArray();for(String model:models)out.add(new JsonPrimitive(model));return out;
    }
    public static void main(String[] args)throws Exception{
        require(args.length==3,"manifest new-report expected-real-api-sha");Path output=Paths.get(args[1]);require(!Files.exists(output),"new graph output only");
        Path api=Paths.get(ModelBlock.class.getProtectionDomain().getCodeSource().getLocation().toURI());require(fileHash(api).equals(args[2]),"real prepared API origin pin");
        JsonObject manifest;try(Reader in=Files.newBufferedReader(Paths.get(args[0]),StandardCharsets.UTF_8)){manifest=new JsonParser().parse(in).getAsJsonObject();}
        JsonArray definitions=new JsonArray(),errors=new JsonArray(),pins=new JsonArray();int count=0;
        for(JsonElement element:manifest.getAsJsonArray("archives")){
            JsonObject archive=element.getAsJsonObject();String label=archive.get("label").getAsString(),pin=archive.get("sha256").getAsString();Path path=Paths.get(archive.get("path").getAsString());require(fileHash(path).equals(pin),"exact source archive before graph");
            try(ZipFile zip=new ZipFile(path.toFile())){
                Enumeration<? extends ZipEntry> members=zip.entries();
                while(members.hasMoreElements()){
                    ZipEntry member=members.nextElement();String name=member.getName();if(member.isDirectory()||!name.startsWith("assets/")||!name.endsWith(".json")||!(name.contains("/models/")||name.contains("/blockstates/")))continue;
                    require(++count<=250000,"model graph resource bound");JsonObject row=new JsonObject();row.addProperty("provider",label);row.addProperty("provider_sha256",pin);row.addProperty("entry",name);
                    try{
                        byte[] raw=read(zip,member);row.addProperty("original_json_sha256",hash(raw));JsonElement parsed=new JsonParser().parse(new String(raw,StandardCharsets.UTF_8));require(parsed.isJsonObject(),"resource JSON root object");JsonObject obj=parsed.getAsJsonObject();
                        String[] parts=name.split("/",3);String ns=parts[1];JsonArray refs=new JsonArray();strings(obj,"",refs);row.add("string_references",refs);
                        if(name.contains("/models/")){
                            row.addProperty("kind","model");String modelPath=name.substring(name.indexOf("/models/")+8,name.length()-5);row.addProperty("id",id(ns+":"+modelPath));
                            row.addProperty("custom_loader",obj.has("loader"));
                            if(obj.has("parent")){require(obj.get("parent").isJsonPrimitive()&&obj.getAsJsonPrimitive("parent").isString(),"string parent");row.addProperty("parent",id(obj.get("parent").getAsString()));}
                            JsonObject textures=new JsonObject();if(obj.has("textures")){require(obj.get("textures").isJsonObject(),"texture object");for(Map.Entry<String,JsonElement> t:obj.getAsJsonObject("textures").entrySet()){require(t.getValue().isJsonPrimitive()&&t.getValue().getAsJsonPrimitive().isString(),"string texture binding");textures.addProperty(t.getKey(),t.getValue().getAsString());}}
                            row.add("textures",textures);row.addProperty("elements",obj.has("elements")&&obj.get("elements").isJsonArray()?obj.getAsJsonArray("elements").size():0);
                            JsonArray overrides=new JsonArray();if(obj.has("overrides")){require(obj.get("overrides").isJsonArray(),"override array");for(JsonElement v:obj.getAsJsonArray("overrides")){require(v.isJsonObject()&&v.getAsJsonObject().has("model")&&v.getAsJsonObject().get("model").isJsonPrimitive(),"known override model");overrides.add(new JsonPrimitive(id(v.getAsJsonObject().get("model").getAsString())));}}
                            row.add("overrides",overrides);
                        }else{
                            row.addProperty("kind","blockstate");String block=name.substring(name.indexOf("/blockstates/")+13,name.length()-5);row.addProperty("id",id(ns+":"+block));
                            ModelBlockDefinition nativeDefinition=ModelBlockDefinition.parseFromReader(new StringReader(new String(raw,StandardCharsets.UTF_8)),new ResourceLocation(ns,block));row.add("models",variants(nativeDefinition,obj));
                        }
                        definitions.add(row);
                    }catch(Exception|LinkageError failure){row.addProperty("error_type",failure.getClass().getName());row.addProperty("error",String.valueOf(failure.getMessage()));errors.add(row);}
                }
            }
            require(fileHash(path).equals(pin),"source archive unchanged after graph");JsonObject record=new JsonObject();record.addProperty("label",label);record.addProperty("sha256",pin);pins.add(record);
        }
        Path gson=Paths.get(Gson.class.getProtectionDomain().getCodeSource().getLocation().toURI());JsonObject result=new JsonObject();result.addProperty("schema",1);result.addProperty("scope","ACTUAL_GSON_RESOURCELOCATION_AND_NATIVE_BLOCKSTATE_METADATA_GRAPH");result.addProperty("resources_examined",count);result.addProperty("complete",errors.size()==0);result.addProperty("actual_api_sha256",args[2]);result.addProperty("actual_gson_sha256",fileHash(gson));result.addProperty("native_geometry_emitted",false);result.addProperty("minecraft_client_launched",false);result.addProperty("source_archives_unchanged",true);result.add("archives",pins);result.add("definitions",definitions);result.add("errors",errors);
        Files.write(output,(new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);System.out.println("NATIVE_RESOURCE_GRAPH resources="+count+" errors="+errors.size()+" client=false");
    }
}
