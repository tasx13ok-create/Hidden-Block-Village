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

/** Original, headless verifier of the ACTUAL prepared Minecraft ModelBlock API.
 * No mod bodies, artwork, graphics context, stub ModelBlock, or live world used. */
public final class NativeModelProof {
    private static final int MAX=8*1024*1024;
    private static int checks;
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static String sha(byte[] raw)throws Exception{byte[] value=MessageDigest.getInstance("SHA-256").digest(raw);StringBuilder out=new StringBuilder();for(byte b:value)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static String fileSha(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(path)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static byte[] member(ZipFile zip,String name)throws Exception{
        check(name.startsWith("assets/")&&name.contains("/models/")&&name.endsWith(".json")&&!name.contains("..")&&!name.contains("\\"),"safe model member");
        ZipEntry entry=zip.getEntry(name);check(entry!=null&&entry.getSize()>=0&&entry.getSize()<=MAX,"bounded actual model member");
        try(InputStream in=zip.getInputStream(entry);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n,total=0;while((n=in.read(buffer))!=-1){total+=n;if(total>MAX)throw new AssertionError("stream model bound");out.write(buffer,0,n);}return out.toByteArray();
        }
    }
    private static String typedDigest(Object value)throws Exception{return sha(new Gson().toJson(value).getBytes(StandardCharsets.UTF_8));}
    private static Set<String> faceTextures(ModelBlock model){
        Set<String> result=new TreeSet<>(),references=new TreeSet<>();
        for(BlockPart part:model.getElements())for(BlockPartFace face:part.mapFaces.values())references.add(face.texture);
        for(String reference:references){
            String resolved=model.resolveTextureName(reference);
            check(resolved!=null&&!resolved.equals("missingno")&&!resolved.startsWith("#"),"actual face texture resolves without inherited parent");
            result.add(new ResourceLocation(resolved).toString());
        }
        return result;
    }
    private static JsonObject contextProof(JsonObject context,JsonObject candidate,ZipFile pack,Map<Path,String> originals)throws Exception{
        Map<ResourceLocation,ModelBlock> beforeMap=new HashMap<>(),afterMap=new HashMap<>();
        for(JsonElement element:context.getAsJsonArray("chain")){
            JsonObject node=element.getAsJsonObject();Path archive=Paths.get(node.get("original_archive").getAsString());String pin=node.get("provider_sha256").getAsString();
            if(!originals.containsKey(archive)){check(fileSha(archive).equals(pin),"exact context provider archive");originals.put(archive,pin);}else check(originals.get(archive).equals(pin),"consistent context provider identity");
            byte[] before;try(ZipFile zip=new ZipFile(archive.toFile())){before=member(zip,node.get("entry").getAsString());}
            check(sha(before).equals(node.get("original_json_sha256").getAsString()),"exact original context JSON");
            byte[] after=pack.getEntry(node.get("entry").getAsString())!=null?member(pack,node.get("entry").getAsString()):before;
            ModelBlock first=ModelBlock.deserialize(new String(before,StandardCharsets.UTF_8)),second=ModelBlock.deserialize(new String(after,StandardCharsets.UTF_8));
            check(first.textures.equals(second.textures),"each real child/parent texture map unchanged; no merged map injected");
            ResourceLocation id=new ResourceLocation(node.get("id").getAsString());beforeMap.put(id,first);afterMap.put(id,second);
        }
        for(Map.Entry<ResourceLocation,ModelBlock> node:beforeMap.entrySet())if(node.getValue().getParentLocation()!=null)node.getValue().getParentFromMap(beforeMap);
        for(Map.Entry<ResourceLocation,ModelBlock> node:afterMap.entrySet())if(node.getValue().getParentLocation()!=null)node.getValue().getParentFromMap(afterMap);
        ModelBlock root=beforeMap.get(new ResourceLocation(context.get("root").getAsString())),after=afterMap.get(new ResourceLocation(context.get("root").getAsString()));
        check(root!=null&&after!=null&&after.isResolved(),"actual repaired incoming hierarchy resolves");ModelBlock.checkModelHierarchy(afterMap);
        check(typedDigest(root.getElements()).equals(typedDigest(after.getElements())),"actual inherited incoming geometry/UV/rotation/shading unchanged");
        check(typedDigest(root.getAllTransforms()).equals(typedDigest(after.getAllTransforms())),"actual inherited incoming camera transforms unchanged");
        check(root.isAmbientOcclusion()==after.isAmbientOcclusion()&&root.isGui3d()==after.isGui3d(),"actual incoming render flags unchanged");
        check(typedDigest(root.getOverrides()).equals(typedDigest(after.getOverrides())),"actual incoming overrides unchanged");
        Set<String> beforeTextures=faceTextures(root),afterTextures=faceTextures(after);check(beforeTextures.equals(afterTextures),"actual child-bound texture names unchanged");
        Set<String> declared=new HashSet<>();for(JsonElement item:context.getAsJsonArray("texture_closure"))declared.add(item.getAsJsonObject().get("id").getAsString());
        check(declared.containsAll(afterTextures),"actual child-bound textures are proven existing original resources");
        JsonObject result=new JsonObject();result.addProperty("root",context.get("root").getAsString());result.addProperty("chain_models",beforeMap.size());result.addProperty("actual_typed_geometry_sha256",typedDigest(after.getElements()));result.addProperty("actual_texture_ids",String.join(",",afterTextures));result.addProperty("all_original_child_bindings_and_transforms_unchanged",true);return result;
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("PRIVATE-NATIVE-PROOF-INPUT.json NEW-REPORT.json expected-prepared-api-sha256");
        Path input=Paths.get(args[0]),report=Paths.get(args[1]);check(!Files.exists(report),"new proof report only");check(Files.isRegularFile(input)&&Files.size(input)<=2*1024*1024,"bounded own proof manifest");
        Path actualApi=Paths.get(ModelBlock.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        check(Files.isRegularFile(actualApi)&&fileSha(actualApi).equals(args[2]),"actual ModelBlock comes from the pinned real prepared API");
        check(!ModelBlock.class.getName().startsWith("village."),"no project stub ModelBlock");
        JsonObject manifest;try(Reader reader=Files.newBufferedReader(input,StandardCharsets.UTF_8)){manifest=new JsonParser().parse(reader).getAsJsonObject();}
        check(manifest.get("schema").getAsInt()==1,"proof input schema");
        JsonArray rows=manifest.getAsJsonArray("models");check(rows.size()<=512,"proof model bound");
        check(rows.size()>0,"nonempty proof model set");
        check(fileSha(Paths.get(manifest.get("pack").getAsString())).equals(manifest.get("pack_sha256").getAsString()),"exact candidate pack identity");
        Map<Path,String> originals=new LinkedHashMap<>();JsonArray results=new JsonArray();Map<ResourceLocation,ModelBlock> hierarchy=new HashMap<>();
        try(ZipFile patchedPack=new ZipFile(manifest.get("pack").getAsString())){
            Set<String> expectedMembers=new HashSet<>();expectedMembers.add("pack.mcmeta");for(JsonElement e:rows)check(expectedMembers.add(e.getAsJsonObject().get("entry").getAsString()),"unique candidate member");
            Set<String> actualMembers=new HashSet<>();Enumeration<? extends ZipEntry> members=patchedPack.entries();while(members.hasMoreElements()){ZipEntry entry=members.nextElement();check(!entry.isDirectory()&&actualMembers.add(entry.getName()),"unique pack file member");}
            check(actualMembers.equals(expectedMembers),"pack has exactly approved model entries and own metadata; no hidden child/pixel/code override");
            for(JsonElement element:rows){
                JsonObject row=element.getAsJsonObject();Path original=Paths.get(row.get("original_archive").getAsString());String pin=row.get("provider_sha256").getAsString();
                check(fileSha(original).equals(pin),"exact pinned original provider before proof");originals.put(original,pin);
                byte[] beforeBytes;try(ZipFile originalZip=new ZipFile(original.toFile())){beforeBytes=member(originalZip,row.get("entry").getAsString());}
                byte[] afterBytes=member(patchedPack,row.get("entry").getAsString());
                check(sha(beforeBytes).equals(row.get("original_json_sha256").getAsString()),"exact original JSON input");
                check(sha(afterBytes).equals(row.get("patched_json_sha256").getAsString()),"exact private patched JSON input");
                ModelBlock before=ModelBlock.deserialize(new String(beforeBytes,StandardCharsets.UTF_8));
                ModelBlock after=ModelBlock.deserialize(new String(afterBytes,StandardCharsets.UTF_8));
                check(before!=null&&after!=null,"real ModelBlock.deserialize succeeded");
                check(before.getParentLocation()!=null&&before.getParentLocation().toString().equals(row.get("removed_parent").getAsString()),"actual parsed missing parent identity");
                check(after.getParentLocation()==null&&after.isResolved(),"actual repaired model has no unresolved parent");
                check(!before.getElements().isEmpty()&&before.getElements().size()==row.get("elements_count").getAsInt(),"actual original own geometry count");
                String geometry=typedDigest(before.getElements());check(geometry.equals(typedDigest(after.getElements())),"all actual typed positions/faces/UVs/rotations/shading unchanged");
                check(before.textures.equals(after.textures),"all actual texture bindings unchanged");
                check(typedDigest(before.getAllTransforms()).equals(typedDigest(after.getAllTransforms())),"all actual typed camera transforms unchanged");
                check(before.ambientOcclusion==after.ambientOcclusion&&before.isGui3d()==after.isGui3d(),"actual render flags unchanged");
                check(typedDigest(before.getOverrides()).equals(typedDigest(after.getOverrides())),"actual overrides unchanged");
                JsonArray contextResults=new JsonArray();Set<String> afterTextures=new TreeSet<>();
                if(row.has("incoming_contexts")&&row.getAsJsonArray("incoming_contexts").size()>0){
                    for(JsonElement context:row.getAsJsonArray("incoming_contexts"))contextResults.add(contextProof(context.getAsJsonObject(),row,patchedPack,originals));
                }else{
                    Set<String> beforeTextures=faceTextures(before);afterTextures=faceTextures(after);check(beforeTextures.equals(afterTextures),"actual face-texture resolution unchanged");
                    Set<String> declared=new HashSet<>();for(JsonElement t:row.getAsJsonArray("texture_closure"))declared.add(t.getAsJsonObject().get("id").getAsString());
                    check(declared.containsAll(afterTextures),"actual parser resolves only proven existing original texture IDs");
                }
                hierarchy.put(new ResourceLocation(row.get("id").getAsString()),after);
                JsonObject result=new JsonObject();result.addProperty("id",row.get("id").getAsString());result.addProperty("actual_elements",after.getElements().size());result.addProperty("typed_geometry_sha256",geometry);result.addProperty("original_and_patched_geometry_identical",true);result.addProperty("actual_missing_parent_removed",true);result.addProperty("actual_face_texture_ids",String.join(",",afterTextures));result.add("incoming_contexts",contextResults);results.add(result);
            }
        }
        ModelBlock.checkModelHierarchy(hierarchy);check(true,"actual repaired model hierarchy has no cycles");
        for(Map.Entry<Path,String> original:originals.entrySet())check(fileSha(original.getKey()).equals(original.getValue()),"original provider archive unchanged after native proof");
        JsonObject result=new JsonObject();result.addProperty("schema",1);result.addProperty("scope","HEADLESS_REAL_MODEL_BLOCK_PARSE_AND_TEXTURE_CLOSURE_ONLY");result.addProperty("passed",true);result.addProperty("assertions",checks);result.addProperty("models",rows.size());result.addProperty("actual_api_sha256",args[2]);result.addProperty("minecraft_client_launched",false);result.addProperty("open_gl_context_used",false);result.addProperty("native_model_bake_tested",false);result.addProperty("runtime_registry_tested",false);result.addProperty("original_provider_archives_unchanged",true);result.add("results",results);
        Files.write(report,(new GsonBuilder().setPrettyPrinting().create().toJson(result)+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
        System.out.println("NATIVE_MODEL_BLOCK_PARSE_PASS models="+rows.size()+" assertions="+checks+" graphical_bake=false");
    }
}
