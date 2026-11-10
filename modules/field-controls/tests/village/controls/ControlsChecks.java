package village.controls;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import village.controls.Model.*;

public final class ControlsChecks {
    private static int assertions;
    private static void require(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private static final class Fixture {
        final Catalog c;final Map<String,Stroke> values;
        Fixture(Catalog c,Map<String,Stroke> values){this.c=c;this.values=values;}
    }
    private static Action a(int index,String id,String raw,boolean game,Stroke value) {
        String group=Metadata.group(raw,"","");
        return new Action(index,id,raw,"fixture",Metadata.label(raw,raw),"Fixture",group,Metadata.help(raw),Metadata.priority(raw,group),game,value);
    }
    private static Fixture pack()throws Exception {
        JsonArray array=new JsonParser().parse(new String(Files.readAllBytes(Paths.get("tests/pack-fixture.json")),StandardCharsets.UTF_8)).getAsJsonArray();
        List<Action> actions=new ArrayList<>();Map<String,Stroke> values=new LinkedHashMap<>();
        for(JsonElement e:array) {
            JsonObject o=e.getAsJsonObject();String raw=o.get("raw").getAsString();Stroke s=new Stroke(o.get("code").getAsInt(),Mod.valueOf(o.get("mod").getAsString()));String id=raw+"/"+actions.size();
            actions.add(a(actions.size(),id,raw,true,s));values.put(id,s);
        }
        boolean[][] overlap=new boolean[actions.size()][actions.size()];for(boolean[] row:overlap)Arrays.fill(row,true);
        return new Fixture(new Catalog(actions,overlap),values);
    }
    private static long hard(Catalog c,Map<String,Stroke> v){return Model.conflicts(c,v).stream().filter(x->!x.modifierOnly).count();}
    public static void main(String[] args)throws Exception {
        Fixture f=pack();require(f.c.actions.size()>100,"The fixture must represent the real loaded pack");
        require(hard(f.c,f.values)>15,"The fixture must contain the reported conflicts");
        for(Preset p:Preset.values()) {
            Plan plan=Model.plan(f.c,f.values,p);
            require(hard(f.c,plan.values)==0,p+" must remove hard conflicts");
            require(plan.unresolved==0,p+" should not run out of spare keys for this pack: "+plan.unresolved);
            for(String raw:Arrays.asList("key.forward","key.back","key.left","key.right","key.jump","key.attack","key.use")) {
                Action action=f.c.byRaw(raw);require(plan.values.get(action.id).equals(f.values.get(action.id)),p+" must protect "+raw);
            }
            require(plan.values.get(f.c.byRaw("key.sprint").id).code==(p==Preset.CLASSIC?29:42),"Sprint should match the preset");
            require(plan.values.get(f.c.byRaw("key.sneak").id).code==(p==Preset.CLASSIC?42:29),"Crouch should match the preset");
            Plan repeated=Model.plan(f.c,plan.values,p);require(repeated.changed.isEmpty(),"Applying a preset twice should be stable");
            List<Action> shuffled=new ArrayList<>(f.c.actions);Collections.shuffle(shuffled,new Random(1234));boolean[][] all=new boolean[shuffled.size()][shuffled.size()];for(boolean[] row:all)Arrays.fill(row,true);
            Plan reordered=Model.plan(new Catalog(shuffled,all),f.values,p);require(plan.values.equals(reordered.values),"Mod registration order should not move unique actions");
        }
        Plan fixed=Model.repair(f.c,f.values);require(hard(f.c,fixed.values)==0,"Repair must remove conflicts");
        for(Action action:f.c.actions)if(f.values.get(action.id).code==0)require(fixed.values.get(action.id).code==0,"Repair must preserve intentionally unbound actions");
        Session session=new Session(f.c,f.values);session.replace(fixed.values,"Repair");require(session.changes()>0,"Draft should track changes");
        require(session.baseline.equals(f.values),"Preview must not mutate the original snapshot");session.undo();require(session.draft.equals(f.values),"Undo must recover all original keys");
        require(session.layout.equals("Custom"),"Undo must recover the previous layout name as well as keys");
        session.replace(fixed.values,"Repair");session.saved();require(session.changes()==0&&!session.canUndo(),"Applied changes establish a new clean baseline");
        Map<String,Stroke> partial=new LinkedHashMap<>();Action first=f.c.actions.get(0);partial.put(first.id,new Stroke(68));partial.put("missing mod",new Stroke(1));
        session.replace(partial,"Partial");require(session.draft.size()==f.values.size(),"Partial profile must preserve newly installed actions");
        require(session.draft.get(first.id).code==68,"Matching profile action must restore");
        Action game=a(0,"game","game",true,new Stroke(19)),gui=a(1,"gui","gui",false,new Stroke(19));
        Catalog separate=new Catalog(Arrays.asList(game,gui),new boolean[][]{{true,false},{false,true}});
        require(!Model.hard(separate,game,new Stroke(19),gui,new Stroke(19)),"Disjoint input contexts may share a key");
        Catalog same=new Catalog(Arrays.asList(game,gui),new boolean[][]{{true,true},{true,true}});
        require(Model.hard(same,game,new Stroke(19),gui,new Stroke(19,Mod.ALT)),"Game input must detect plain/modified overlap");
        Action g0=a(0,"g0","g0",false,new Stroke(19)),g1=a(1,"g1","g1",false,new Stroke(19));
        Catalog guiOnly=new Catalog(Arrays.asList(g0,g1),new boolean[][]{{true,true},{true,true}});
        require(!Model.hard(guiOnly,g0,new Stroke(19),g1,new Stroke(19,Mod.CONTROL)),"GUI-only modified shortcuts can coexist");
        require(new Stroke(42,Mod.SHIFT).mod==Mod.NONE,"A standalone modifier must normalize");
        Map<String,Stroke> modifierValues=new LinkedHashMap<>();modifierValues.put(game.id,new Stroke(42));modifierValues.put(gui.id,new Stroke(19,Mod.SHIFT));
        List<Conflict> soft=Model.conflicts(same,modifierValues);require(soft.size()==1&&soft.get(0).modifierOnly,"Held modifier overlap must be distinguished from a hard conflict");
        Random random=new Random(9043);
        for(int trial=0;trial<120;trial++) {
            Map<String,Stroke> noisy=new LinkedHashMap<>();for(Action action:f.c.actions)noisy.put(action.id,new Stroke(random.nextInt(5)==0?0:2+random.nextInt(58)));
            Plan repaired=Model.repair(f.c,noisy);require(hard(f.c,repaired.values)==0,"Stress repair produced an input collision");
            for(Action action:f.c.actions)if(noisy.get(action.id).code==0)require(repaired.values.get(action.id).code==0,"Stress repair assigned an intentionally empty key");
        }
        Path directory=Paths.get(args[0]);Files.createDirectories(directory);ProfileStore store=new ProfileStore(directory.resolve("profiles.json"));
        store.data.original.putAll(f.values);store.data.active.putAll(fixed.values);store.put("Action profile",fixed.values);
        ProfileStore reload=new ProfileStore(store.file);reload.load();require(reload.data.active.equals(fixed.values),"Active layout must survive restart");
        require(reload.data.original.equals(f.values),"Startup backup must survive restart");require(reload.data.profiles.get("Action profile").equals(fixed.values),"Named profiles must round-trip");
        store.put("Second layout",f.values);require(Files.exists(store.file.resolveSibling("profiles.json.previous")),"A previous profile file must be retained");
        store.remove("Second layout");reload.load();require(!reload.data.profiles.containsKey("Second layout"),"Deleted profile must not reappear after reload");
        require(!ProfileStore.validName("Startup backup")&&!ProfileStore.validName("\n")&&!ProfileStore.validName(""),"Reserved/invalid profile names must be rejected");
        byte[] invalid="{broken json".getBytes(StandardCharsets.UTF_8);Files.write(store.file,invalid);ProfileStore broken=new ProfileStore(store.file);broken.load();
        require(!broken.warning.isEmpty(),"Corrupt profile must produce an actionable warning");require(Arrays.equals(Files.readAllBytes(store.file),invalid),"Reading a corrupt profile must not overwrite it");
        broken.put("Recovery",f.values);require(Arrays.equals(Files.readAllBytes(store.file.resolveSibling("profiles.json.previous")),invalid),"Recovery must preserve the corrupt file for inspection");
        ProfileStore scoped=new ProfileStore(directory.resolve("scoped.json"));scoped.data.layout="Action";scoped.data.original.putAll(f.values);scoped.data.active.putAll(fixed.values);scoped.put("Combat",fixed.values);
        require(!scoped.selectContext("legacy"),"A 1.0 legacy profile should be recognized without resetting it");
        require(scoped.selectContext("vale"),"First Vale load must get its own profile context");require(scoped.data.active.isEmpty()&&scoped.data.original.isEmpty(),"Legacy keys must not become the active Vale layout or backup");
        require(scoped.data.initialPreset.equals("ACTION"),"A known familiar preset should be replanned against the new profile's actual actions");
        scoped.data.active.put("key.categories.fieldcontrols|key.fieldcontrols.cast|0",new Stroke(19));scoped.data.original.put("vale-original",new Stroke(0));scoped.data.layout="Action";scoped.put("Combat",scoped.data.active);
        require(!scoped.selectContext("legacy"),"Returning to an existing context must restore it");require(scoped.data.active.equals(fixed.values)&&scoped.data.original.equals(f.values),"Original legacy layout and backup must survive a Vale switch");
        require(scoped.data.profiles.get("Combat").equals(fixed.values),"Same-named profiles in different contexts must stay separate");scoped.save();
        ProfileStore scopedReload=new ProfileStore(scoped.file);scopedReload.load();scopedReload.selectContext("vale");
        require(scopedReload.data.active.size()==1&&scopedReload.data.active.values().iterator().next().code==19,"Vale context must survive restart independently");require(scopedReload.data.original.containsKey("vale-original"),"Vale startup backup must remain independent");
        System.out.println("PASS: "+assertions+" controls assertions across actual pack conflicts, three presets, contexts, drafts, undo, stress repair and profile recovery.");
    }
}
