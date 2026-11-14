package village.vale.bridge;
import java.io.*;import java.nio.charset.StandardCharsets;import java.nio.file.*;import java.util.*;

/** Actual strict reader/store checks, including failed publication and unchanged proposal rename. */
public final class BridgeConfigChecks {
    private static int checks;private static final Set<String> WEAPONS=new HashSet<>(Arrays.asList("storm_pair","ward_fan"));
    private static final BridgeMappings.Registry REGISTRY=new BridgeMappings.Registry(){public boolean registered(String kind,String id){return BridgeMappings.validId(id)&&!id.equals("missing:gone");}public Set<String> weaponKeys(){return WEAPONS;}};
    private static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static String row(String kind,String id,String when,String owner,String style){return "{\"kind\":\""+kind+"\",\"id\":\""+id+"\",\"when\":\""+when+"\",\"owner\":\""+owner+"\",\"style\":\""+style+"\"}";}
    private static String root(String rows){return "{\"schema\":1,\"verified\":true,\"moves\":["+rows+"]}";}
    private static void reject(String source,String why)throws Exception{boolean failed=false;try{BridgeMappings.read(bytes(source),REGISTRY);}catch(IOException expected){failed=true;}check(failed,why);}
    public static void main(String[] args)throws Exception{
        String spawn=row("entity","narutomod:rasengan","spawn","naruto_projectile","spiral");String melee=row("item","kabutoaddon:blade","melee","direct_player","weapon:storm_pair");
        BridgeMappings first=BridgeMappings.read(bytes(root(spawn+","+melee)),REGISTRY);check(first.rows.size()==2,"typed complete map");check(BridgeMappings.read(first.bytes(),REGISTRY).rows.size()==2,"deterministic writer round trip");check(Arrays.equals(first.bytes(),BridgeMappings.read(first.bytes(),REGISTRY).bytes()),"stable sort round trip");
        for(String style:BridgeMappings.styles(WEAPONS))check(BridgeMappings.validStyle(style,WEAPONS),"published style accepted "+style);
        check(!BridgeMappings.validStyle("weapon:missing",WEAPONS)&&!BridgeMappings.validStyle("spiral;cast",WEAPONS),"no unknown style or invocation");
        for(String id:new String[]{"narutomod:rasengan","a:b","ns:folder/name_1.2-3"})check(BridgeMappings.validId(id),"valid exact ID");
        for(String id:new String[]{"","narutomod","A:b","a:B","a:b:c","a:b c","a:b\n","a:"})check(!BridgeMappings.validId(id),"malformed ID refused");
        reject(root(spawn).replace("\"verified\":true","\"verified\":false"),"proposal rename rejected");reject(root(spawn).replace("\"schema\":1","\"schema\":2"),"future schema preserved");reject(root(spawn).replace("\"schema\":1","\"schema\":1.0"),"schema is exact integer");reject(root(spawn).replace("\"schema\":1","\"schema\":1,\"schema\":1"),"duplicate root rejected");
        reject(root(spawn+","+spawn),"duplicate exact mapping rejected");reject(root(spawn).replace("\"kind\":\"entity\"","\"kind\":\"entity\",\"kind\":\"entity\""),"duplicate row field rejected");reject(root(spawn).replace("\"style\":\"spiral\"","\"style\":\"spiral\",\"command\":\"give\""),"unknown row field rejected");reject(root(spawn).replace("\"moves\":[","\"autoMap\":true,\"moves\":["),"runtime inference cannot be enabled");
        reject(root(row("entity","missing:gone","spawn","thrower","spiral")),"missing optional provider ID refused");reject(root(row("item","n:weapon","spawn","thrower","spiral")),"item spawn cannot masquerade as accepted item cast");reject(root(row("entity","n:effect","spawn","getOwner","spiral")),"arbitrary public method name refused");reject(root(row("entity","n:effect","damage","thrower","spiral")),"damage requires actual source proof");reject(root(row("item","n:blade","melee","damage_source","spiral")),"melee requires direct-player proof");
        reject(root(spawn)+"{}","trailing document refused");reject("{\"schema\":1,\"verified\":true,\"moves\":{}}","wrong type refused");reject("{\"schema\":1,\"verified\":true}","missing moves refused");
        boolean invalidUtf=false;try{BridgeMappings.read(new byte[]{(byte)0xc3,(byte)0x28},REGISTRY);}catch(IOException expected){invalidUtf=true;}check(invalidUtf,"invalid UTF8 refused");
        boolean oversized=false;try{BridgeMappings.read(new byte[BridgeMappings.MAX_BYTES+1],REGISTRY);}catch(IOException expected){oversized=true;}check(oversized,"bounded bytes");
        StringBuilder many=new StringBuilder();for(int i=0;i<513;i++){if(i>0)many.append(',');many.append(row("entity","n:e"+i,"damage","damage_source","charge"));}reject(root(many.toString()),"513rows refused");
        Path folder=Files.createTempDirectory("bridge-config-checks-");Path path=folder.resolve("bridge-moves.json");BridgeConfig config=new BridgeConfig(path,REGISTRY);config.initialize();check(config.snapshot().rows.isEmpty()&&config.writable(),"new exact empty config initialized");
        config.change(new BridgeMappings.Row("entity","n:bolt","damage","damage_source","charge"),null);BridgeMappings saved=config.snapshot();byte[] good=Files.readAllBytes(path);check(saved.rows.size()==1,"valid edit persisted before publication");
        Files.write(path,bytes(root(spawn).replace("\"verified\":true","\"verified\":false")));boolean failed=false;try{config.reload();}catch(IOException expected){failed=true;}check(failed&&config.snapshot()==saved&&!config.writable(),"failed reload keeps exact old snapshot and is read-only");byte[] bad=Files.readAllBytes(path);
        try{config.change(null,"entity/damage/n:bolt");}catch(IOException expected){}check(Arrays.equals(bad,Files.readAllBytes(path))&&config.snapshot()==saved,"edit cannot overwrite rejected proposal");
        Files.write(path,good);config.reload();BridgeMappings recovered=config.snapshot();check(recovered.rows.size()==1&&config.writable(),"explicit corrected reload recovers");
        Files.delete(path);Files.createDirectory(path);Files.write(path.resolve("blocker"),new byte[]{1});failed=false;try{config.change(new BridgeMappings.Row("item","n:blade","melee","direct_player","spiral"),null);}catch(IOException expected){failed=true;}check(failed&&config.snapshot()==recovered&&Files.isDirectory(path),"failed atomic replace cannot publish next map or destroy destination");
        check(Files.list(folder).filter(p->p.getFileName().toString().startsWith(".bridge-")).count()==0,"failed atomic write cleans only its new temporary file");
        if(args.length>0){byte[] actual=BridgeFiles.read(Paths.get(args[0]),BridgeMappings.MAX_BYTES);failed=false;try{BridgeMappings.read(actual,REGISTRY);}catch(IOException expected){failed=true;}check(failed,"actual separate proposal output rejected unchanged");}
        System.out.println("BRIDGE_CONFIG_CHECKS_PASS assertions="+checks+" native_endpoint=false");
    }
}
