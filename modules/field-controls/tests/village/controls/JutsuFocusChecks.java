package village.controls;

import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import village.controls.Model.*;

/** Captured native catalogs exercise the candidate's actual focus metadata. */
public final class JutsuFocusChecks {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Gson gson=new Gson();JsonArray results=new JsonArray();String rawFocus="key.fieldhud.jutsu_focus";
        String focusGroup=Metadata.group(rawFocus,"key.categories.fieldhud","Field HUD");
        check(focusGroup.equals("Combat & arts"),"Focus action must be beside attacks");
        check(Metadata.essential(rawFocus)&&!Metadata.required(rawFocus),"Optional HUD input must be easy to find without being required");
        check(Metadata.label(rawFocus,rawFocus).equals("Switch item / jutsu bar"),"Focus label must match the HUD contract");
        check(Metadata.priority(rawFocus,focusGroup)==31,"Focus must not displace normal utility keys");
        for(String profile:new String[]{"fresh-original","legacy"}) {
            JsonObject source=gson.fromJson(new String(Files.readAllBytes(Paths.get(args[0],profile+".json")),StandardCharsets.UTF_8),JsonObject.class);
            List<Action> oldActions=new ArrayList<>();List<String> contexts=new ArrayList<>();Map<String,Stroke> current=new LinkedHashMap<>();
            for(JsonElement item:source.getAsJsonArray("actions")) {
                JsonObject row=item.getAsJsonObject();String raw=row.get("raw").getAsString(),id=row.get("id").getAsString(),cat=id.substring(0,id.indexOf('|')),group=Metadata.group(raw,cat,row.get("source").getAsString()),context=row.get("context").getAsString();
                check(Arrays.asList("GUI","IN_GAME","UNIVERSAL").contains(context),"Captured context must be explicit");
                Stroke value=gson.fromJson(row.get("stroke"),Stroke.class);current.put(id,value);contexts.add(context);
                oldActions.add(new Action(oldActions.size(),id,raw,cat,Metadata.label(raw,row.get("label").getAsString()),row.get("source").getAsString(),group,Metadata.help(raw),Metadata.priority(raw,group),!context.equals("GUI"),value));
            }
            Catalog baseline=new Catalog(oldActions,overlap(contexts));
            List<Action> actions=new ArrayList<>(oldActions);List<String> extended=new ArrayList<>(contexts);extended.add("IN_GAME");
            Action focus=new Action(actions.size(),"key.categories.fieldhud|"+rawFocus+"|0",rawFocus,"key.categories.fieldhud",Metadata.label(rawFocus,rawFocus),"Field HUD",focusGroup,Metadata.help(rawFocus),Metadata.priority(rawFocus,focusGroup),true,new Stroke(33));actions.add(focus);
            Catalog catalog=new Catalog(actions,overlap(extended));Map<String,Stroke> withFocus=new LinkedHashMap<>(current);withFocus.put(focus.id,focus.defaults);
            for(Preset preset:Preset.values()) {
                Plan before=Model.plan(baseline,current,preset),after=Model.plan(catalog,withFocus,preset);
                check(after.unresolved==0,"Fresh focus allocation needs no manual spare key");check(noHard(catalog,after.values),"Fresh preset must have no hard conflicts");
                check(Model.plan(catalog,after.values,preset).values.equals(after.values),"Repeated preset must stay stable");
                Map<String,Stroke> upgrade=new LinkedHashMap<>(before.values);upgrade.put(focus.id,focus.defaults);
                Plan repaired=Model.repair(catalog,upgrade);
                check(repaired.unresolved==0&&noHard(catalog,repaired.values),"New input added to an existing preset must repair cleanly");
                check(Model.repair(catalog,repaired.values).values.equals(repaired.values),"Upgrade repair must be stable");
                List<String> changed=new ArrayList<>();
                for(Action action:oldActions) {
                    if(action.priority<=30){
                        check(before.values.get(action.id).equals(after.values.get(action.id)),"Fresh primary/utility input moved: "+profile+" / "+preset+" / "+action.label);
                        check(before.values.get(action.id).equals(repaired.values.get(action.id)),"Upgrade primary/utility input moved: "+profile+" / "+preset+" / "+action.label);
                    }
                    if(!before.values.get(action.id).equals(after.values.get(action.id)))changed.add(action.label+": "+before.values.get(action.id).code+" -> "+after.values.get(action.id).code);
                }
                if(profile.equals("fresh-original")&&(preset==Preset.ACTION||preset==Preset.MMO)){
                    check(after.values.get(focus.id).equals(new Stroke(33)),"Fresh Action/MMO focus must use F");
                    check(repaired.values.get(focus.id).equals(new Stroke(33)),"Upgraded Action/MMO focus must use F");
                    check(after.values.get(catalog.byRaw("key.fieldcontrols.cast").id).equals(new Stroke(19)),"Cast must remain R");
                }
                JsonObject result=new JsonObject();result.addProperty("profile",profile);result.addProperty("preset",preset.title);result.add("focus",gson.toJsonTree(after.values.get(focus.id)));result.add("upgrade_focus",gson.toJsonTree(repaired.values.get(focus.id)));result.add("advanced_reallocations",gson.toJsonTree(changed));results.add(result);
            }
        }
        JsonObject output=new JsonObject();output.addProperty("passed",true);output.addProperty("assertions",checks);output.addProperty("native_client_launched",false);output.addProperty("scope","Captured standard Forge key contexts; actual candidate metadata and allocator. Native upgrade/startup and combined HUD gameplay remain pending.");output.add("plans",results);
        Files.write(Paths.get(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(output).getBytes(StandardCharsets.UTF_8));System.out.println("PASS: "+checks+" captured-catalog jutsu focus and upgrade allocation checks");
    }
    static boolean noHard(Catalog c,Map<String,Stroke> v){return Model.conflicts(c,v).stream().noneMatch(x->!x.modifierOnly);}
    static boolean[][] overlap(List<String> contexts){boolean[][] value=new boolean[contexts.size()][contexts.size()];for(int a=0;a<contexts.size();a++)for(int b=0;b<contexts.size();b++)value[a][b]=contexts.get(a).equals("UNIVERSAL")||contexts.get(b).equals("UNIVERSAL")||contexts.get(a).equals(contexts.get(b));return value;}
}
