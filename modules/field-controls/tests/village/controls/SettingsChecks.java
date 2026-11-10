package village.controls;

import java.util.*;
import village.controls.SettingsModel.*;

public final class SettingsChecks {
    private static int assertions;
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private static final class MemoryPort implements Port {
        final Map<String,Object> state=new LinkedHashMap<>();int writes,persists,begins,rollbacks;String reject="";boolean failPersist;
        @Override public Map<String,Object> read(){return new LinkedHashMap<>(state);}
        @Override public void begin(){begins++;}
        @Override public void write(String id,Object value){writes++;if(id.equals(reject))throw new IllegalStateException("Injected setter failure");state.put(id,value);}
        @Override public void persist(){persists++;if(failPersist){failPersist=false;throw new IllegalStateException("Injected disk failure");}}
        @Override public void rollbackFiles(){rollbacks++;}
    }
    public static void main(String[] args)throws Exception {
        Def fov=new Def("FOV","Gameplay","Field of view","camera",Kind.NUMBER,30,110,1,70d);
        Def bob=new Def("VIEW_BOBBING","Gameplay","View bobbing","motion",Kind.BOOLEAN,0,1,1,true);
        Def gui=new Def("GUI_SCALE","Interface","GUI scale","scale",Kind.CHOICE,0,3,1,0,"Auto","Small","Normal","Large");
        Def volume=new Def("sound.master","Audio","Master volume","sound",Kind.NUMBER,0,1,.01,1d);
        List<Def> defs=Arrays.asList(fov,bob,gui,volume);MemoryPort port=new MemoryPort();for(Def d:defs)port.state.put(d.id,d.defaults);
        port.state.put("foreign.mod.option","preserve");Session s=new Session(defs,port);
        s.set(fov,83d);s.cycle(bob);check(s.changes()==2,"Draft should count only changed settings");check(port.writes==0&&port.persists==0,"Draft editing must not invoke native setters or write files");
        s.undo();check(Boolean.TRUE.equals(s.draft.get(bob.id)),"Undo should restore a toggled value");s.undo();check(s.changes()==0,"Undo should restore baseline");
        s.beginGesture();for(int i=0;i<70;i++)s.set(fov,40d+i);s.endGesture();s.undo();check(s.changes()==0,"A whole slider gesture must undo in one operation");
        s.set(fov,999d);check(SettingsModel.equal(s.draft.get(fov.id),110d),"Slider must clamp high values");s.set(fov,-999d);check(SettingsModel.equal(s.draft.get(fov.id),30d),"Slider must clamp low values");
        s.set(volume,.337);check(SettingsModel.equal(s.draft.get(volume.id),.34d),"Slider must honor its step");s.discard();check(s.changes()==0,"Discard must restore the current actual values");
        s.set(fov,81d);s.set(volume,.42d);s.cycle(gui);s.apply();check(port.begins==1,"Apply must begin backup before writes");check(s.changes()==0&&!s.canUndo(),"Apply must establish a clean baseline");
        check(SettingsModel.equal(port.state.get(fov.id),81d)&&SettingsModel.equal(port.state.get(volume.id),.42d),"Apply must write reviewed values");check(port.state.get("foreign.mod.option").equals("preserve"),"Apply must preserve unrelated mod settings");
        Map<String,Object> gameplayDefaults=s.defaults("Gameplay");check(SettingsModel.equal(gameplayDefaults.get(fov.id),70d),"Section reset must select native defaults");check(SettingsModel.equal(gameplayDefaults.get(volume.id),.42d),"Section reset must preserve other pages");
        s.change(gameplayDefaults);check(SettingsModel.equal(port.state.get(fov.id),81d),"Reset preview must not change actual settings");s.undo();check(s.changes()==0,"Reset staging must undo cleanly");
        Map<String,Object> before=port.read();s.set(fov,90d);s.cycle(bob);port.reject=bob.id;boolean failed=false;
        try{s.apply();}catch(IllegalStateException expected){failed=true;}
        check(failed,"Setter failure must be reported");check(SettingsModel.equal(port.state.get(fov.id),before.get(fov.id)),"Earlier successful setters must roll back after a later failure");check(s.changes()>0,"Failed apply must retain the reviewable draft");check(port.rollbacks==1,"Failed apply must request exact file recovery");
        port.reject="";s.discard();before=port.read();s.set(volume,.1d);port.failPersist=true;failed=false;
        try{s.apply();}catch(IllegalStateException expected){failed=true;}
        check(failed&&SettingsModel.equal(port.state.get(volume.id),before.get(volume.id)),"Disk failure must roll back runtime settings");
        check(s.changes()==1,"Disk failure must not silently discard the draft");s.apply();check(s.changes()==0,"A failed apply should be retryable");
        port.state.put(gui.id,3);s.refreshIfClean();check(((Number)s.draft.get(gui.id)).intValue()==3,"Returning from a native panel must refresh clean settings");
        s.set(fov,92d);port.state.put(gui.id,2);s.refreshIfClean();check(SettingsModel.equal(s.draft.get(fov.id),92d),"A pending draft must survive a GUI resize");
        check(fov.format(81d).equals("81 degrees"),"FOV preview must state degrees");check(volume.format(.42d).equals("42%"),"Volume preview must state native channel level");
        check(gui.format(5).equals("Scale 5"),"Existing higher custom GUI scales must remain readable");
        Random random=new Random(148);
        for(int trial=0;trial<500;trial++) {
            s.discard();Map<String,Object> base=new LinkedHashMap<>(s.draft);s.beginGesture();for(int move=0;move<30;move++)s.set(fov,30+random.nextDouble()*80);s.endGesture();s.undo();check(s.draft.equals(base),"Stress gesture undo must recover exact values");
            s.set(volume,random.nextDouble());s.apply();check(SettingsModel.equal(s.draft.get(volume.id),port.state.get(volume.id)),"Stress apply must match actual values");
        }
        System.out.println("PASS: "+assertions+" settings assertions across draft isolation, numeric bounds, gesture undo, reset, file/runtime rollback, retry and native-panel refresh.");
    }
}
