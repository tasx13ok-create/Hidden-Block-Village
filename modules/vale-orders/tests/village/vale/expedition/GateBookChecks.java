package village.vale.expedition;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Real authored data and deliberately malformed synthetic files; no game process. */
public final class GateBookChecks {
    private static int checks;private static File source,temporary,sites;
    private static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    private static void rejected(String json,String reason)throws Exception{
        File bad=new File(temporary,"gates.json");Files.write(bad.toPath(),json.getBytes(StandardCharsets.UTF_8));
        boolean denied=false;try{GateBook.load(bad,sites);}catch(IllegalArgumentException|IOException expected){denied=true;}check(denied,reason);
    }
    public static void main(String[] args)throws Exception{
        source=new File(args[0]);temporary=Files.createTempDirectory("vale-gate-loader-checks-").toFile();sites=new File(temporary,"missing-sites.json");
        String text=new String(Files.readAllBytes(source.toPath()),StandardCharsets.UTF_8);
        File dialogue=new File(temporary,"dialogue");dialogue.mkdir();
        for(File file:Objects.requireNonNull(new File(source.getParentFile(),"dialogue").listFiles()))if(file.getName().endsWith(".json"))Files.copy(file.toPath(),new File(dialogue,file.getName()).toPath());
        GateBook book=GateBook.load(source,sites);check(book.missions.size()==5,"Authored missions were not parsed");
        for(GateBook.Mission mission:book.missions.values())check(mission.disabled.startsWith("unmeasured authored anchor"),"Null anchor did not disable "+mission.id+": "+mission.disabled);
        check(book.anchors.isEmpty()&&book.givers.isEmpty()&&book.actorSpawns.isEmpty(),"Loader guessed placements, actors or resident bindings");
        File copiedContent=new File(temporary,"gates.json");Files.write(copiedContent.toPath(),new JsonParser().parse(text).toString().getBytes(StandardCharsets.UTF_8));
        check(GateBook.load(copiedContent,sites).version.equals(book.version),"Whitespace-only formatting changed saved-run identity");
        JsonObject reordered=new JsonObject();List<Map.Entry<String,JsonElement>> entries=new ArrayList<>(new JsonParser().parse(text).getAsJsonObject().entrySet());Collections.reverse(entries);for(Map.Entry<String,JsonElement> entry:entries)reordered.add(entry.getKey(),entry.getValue());Files.write(copiedContent.toPath(),reordered.toString().getBytes(StandardCharsets.UTF_8));
        check(GateBook.load(copiedContent,sites).version.equals(book.version),"Object key ordering changed saved-run identity");
        File alternateSites=new File(temporary,"sites.json");Files.write(alternateSites.toPath(),"{\"schema\":1,\"placements\":{},\"givers\":{},\"actor_spawns\":{},\"marker_blocks\":{}}".getBytes(StandardCharsets.UTF_8));
        check(!GateBook.load(copiedContent,alternateSites).version.equals(book.version),"Changed sites definition did not change saved-run identity");
        File copiedDialogue=new File(dialogue,"keeper_hanae.json");String originalDialogue=new String(Files.readAllBytes(copiedDialogue.toPath()),StandardCharsets.UTF_8);JsonObject changedDialogue=new JsonParser().parse(originalDialogue).getAsJsonObject();changedDialogue.getAsJsonArray("pages").get(0).getAsJsonObject().getAsJsonArray("lines").set(0,new JsonPrimitive("Different reviewed greeting"));Files.write(copiedDialogue.toPath(),changedDialogue.toString().getBytes(StandardCharsets.UTF_8));
        check(!GateBook.load(copiedContent,sites).version.equals(book.version),"Changed dialogue did not change saved-run identity");Files.write(copiedDialogue.toPath(),originalDialogue.getBytes(StandardCharsets.UTF_8));
        JsonObject changedContent=new JsonParser().parse(text).getAsJsonObject();changedContent.getAsJsonArray("missions").get(0).getAsJsonObject().addProperty("title","Changed reviewed mission title");Files.write(copiedContent.toPath(),changedContent.toString().getBytes(StandardCharsets.UTF_8));
        check(!GateBook.load(copiedContent,sites).version.equals(book.version),"Changed content under same authored version retained saved-run identity");
        rejected(text.replaceFirst("\\\"schema\\\"\\s*:\\s*1","\"schema\":1,\"schema\":1"),"Duplicate JSON key accepted");
        JsonObject doc=new JsonParser().parse(text).getAsJsonObject();doc.addProperty("execute","/op player");rejected(doc.toString(),"Unknown executable field accepted");
        doc=new JsonParser().parse(text).getAsJsonObject();doc.addProperty("schema",2);rejected(doc.toString(),"Future content schema accepted");
        doc=new JsonParser().parse(text).getAsJsonObject();JsonObject first=doc.getAsJsonArray("missions").get(0).getAsJsonObject();first.getAsJsonArray("stages").get(0).getAsJsonObject().addProperty("next","arrive");rejected(doc.toString(),"Forward stage cycle accepted");
        doc=new JsonParser().parse(text).getAsJsonObject();first=doc.getAsJsonArray("missions").get(0).getAsJsonObject();first.getAsJsonObject("rewards").getAsJsonObject("first_clear").getAsJsonArray("ledger_items").get(0).getAsJsonObject().addProperty("key","operator_reward");rejected(doc.toString(),"Unreviewed reward item accepted");
        doc=new JsonParser().parse(text).getAsJsonObject();first=doc.getAsJsonArray("missions").get(0).getAsJsonObject();first.getAsJsonArray("stages").get(0).getAsJsonObject().getAsJsonObject("objective").remove("anchor");rejected(doc.toString(),"Missing required arrival anchor accepted");
        doc=new JsonParser().parse(text).getAsJsonObject();first=doc.getAsJsonArray("missions").get(0).getAsJsonObject();first.getAsJsonArray("stages").get(0).getAsJsonObject().add("branches",first.getAsJsonArray("stages").get(1).getAsJsonObject().getAsJsonArray("branches"));rejected(doc.toString(),"Ignored branches on a walking objective accepted");
        char[] tooLong=new char[65537];Arrays.fill(tooLong,' ');rejected(new String(tooLong),"Oversize JSON accepted");
        System.out.println("PASS: "+checks+" bounded gate content/disabled-site assertions. Synthetic inputs: "+temporary);
    }
}
