package village.controls;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.*;
import net.minecraftforge.client.settings.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import village.controls.Model.*;

public final class Bindings {
    public final Catalog catalog;
    public final Map<String,KeyBinding> keys=new LinkedHashMap<>();
    public final Minecraft mc=Minecraft.getMinecraft();
    public int duplicateDescriptions;
    public Bindings() {
        List<Action> actions=new ArrayList<>();Map<String,Integer> occurrences=new HashMap<>();Set<KeyBinding> seen=Collections.newSetFromMap(new IdentityHashMap<KeyBinding,Boolean>());
        Map<String,Integer> descriptionCounts=new HashMap<>();
        for(KeyBinding key:mc.gameSettings.keyBindings) {
            if(!seen.add(key))continue;
            String raw=key.getKeyDescription(),cat=key.getKeyCategory(),base=cat+"|"+raw;
            int ordinal=occurrences.containsKey(base)?occurrences.get(base):0;occurrences.put(base,ordinal+1);
            String id=base+"|"+ordinal,source=translate(cat),label=Metadata.label(raw,translate(raw));
            String group=Metadata.group(raw,cat,source);
            Action a=new Action(actions.size(),id,raw,cat,label,source,group,Metadata.help(raw),Metadata.priority(raw,group),
                key.getKeyConflictContext().conflicts(KeyConflictContext.IN_GAME),new Stroke(key.getKeyCodeDefault(),mod(key.getKeyModifierDefault())));
            actions.add(a);keys.put(id,key);descriptionCounts.put(raw,descriptionCounts.getOrDefault(raw,0)+1);
        }
        for(int count:descriptionCounts.values())if(count>1)duplicateDescriptions+=count-1;
        boolean[][] overlap=new boolean[actions.size()][actions.size()];
        for(Action a:actions)for(Action b:actions) {
            IKeyConflictContext x=keys.get(a.id).getKeyConflictContext(),y=keys.get(b.id).getKeyConflictContext();overlap[a.index][b.index]=x.conflicts(y)||y.conflicts(x);
        }
        catalog=new Catalog(actions,overlap);
    }
    static String translate(String value) { try{return I18n.format(value);}catch(RuntimeException ex){return value;} }
    static Mod mod(KeyModifier modifier){return Mod.valueOf(modifier.name());}
    public Map<String,Stroke> snapshot() {
        Map<String,Stroke> result=new LinkedHashMap<>();for(Map.Entry<String,KeyBinding> e:keys.entrySet())result.put(e.getKey(),new Stroke(e.getValue().getKeyCode(),mod(e.getValue().getKeyModifier())));return result;
    }
    public void install(Map<String,Stroke> values) {
        for(Map.Entry<String,KeyBinding> e:keys.entrySet()) {
            Stroke s=values.get(e.getKey());if(s!=null)e.getValue().setKeyModifierAndCode(KeyModifier.valueOf(s.mod.name()),s.code);
        }
        repairDuplicateRegistration();KeyBinding.resetKeyBindingArrayAndHash();KeyBinding.unPressAllKeys();
    }
    /** Retain the original lookup and add only objects the native description map dropped. */
    @SuppressWarnings("unchecked") public void repairDuplicateRegistration() {
        if(duplicateDescriptions==0)return;
        Map<String,KeyBinding> registered=ReflectionHelper.getPrivateValue(KeyBinding.class,null,"KEYBIND_ARRAY","field_74516_a");
        Set<KeyBinding> present=Collections.newSetFromMap(new IdentityHashMap<KeyBinding,Boolean>());present.addAll(registered.values());
        for(Map.Entry<String,KeyBinding> entry:keys.entrySet())if(present.add(entry.getValue()))registered.put("fieldcontrols.alias:"+entry.getKey(),entry.getValue());
        KeyBinding.resetKeyBindingArrayAndHash();
    }
    public void save(Session session,MouseState mouse) throws IOException {
        Map<String,Stroke> before=snapshot(),oldActive=new LinkedHashMap<>(FieldControls.store.data.active);
        String oldLayout=FieldControls.store.data.layout;MouseState mouseBefore=new MouseState(mc.gameSettings);
        Path options=mc.gameDir.toPath().resolve("options.txt"),backup=null;
        if(Files.exists(options)) {
            Path dir=FieldControls.store.file.getParent().resolve("backups");Files.createDirectories(dir);
            backup=dir.resolve("options-"+System.currentTimeMillis()+".txt");Files.copy(options,backup);
        }
        try {
            install(session.draft);mouse.install(mc.gameSettings);mc.gameSettings.saveOptions();
            verifyOptions(options,session.draft);
            FieldControls.store.data.active=new LinkedHashMap<>(session.draft);FieldControls.store.data.layout=session.layout;FieldControls.store.save();session.saved();
        } catch(Exception ex) {
            install(before);mouseBefore.install(mc.gameSettings);
            FieldControls.store.data.active=oldActive;FieldControls.store.data.layout=oldLayout;
            if(backup!=null)Files.copy(backup,options,StandardCopyOption.REPLACE_EXISTING);
            if(ex instanceof IOException)throw (IOException)ex;throw new IOException("Could not apply controls",ex);
        }
    }
    private void verifyOptions(Path path,Map<String,Stroke> expected) throws IOException {
        if(!Files.exists(path))throw new IOException("Minecraft did not save options.txt");
        List<String> lines=Files.readAllLines(path,StandardCharsets.UTF_8);Map<String,Integer> counts=new HashMap<>();
        for(Action a:catalog.actions)counts.put(a.raw,counts.getOrDefault(a.raw,0)+1);
        for(Action a:catalog.actions) {
            // Minecraft's options format cannot distinguish duplicate descriptions.
            // The identity-aware active profile restores each object after options load.
            if(counts.get(a.raw)>1)continue;
            Stroke s=expected.get(a.id);String line="key_"+a.raw+":"+s.code+(s.mod==Mod.NONE?"":":"+s.mod.name());
            if(!lines.contains(line))throw new IOException("Minecraft did not persist "+a.label);
        }
    }
    public static String display(Stroke s) {
        if(s==null||s.code==0)return "Unbound";
        String key;
        if(s.code<0)key=s.code==-100?"LMB":s.code==-99?"RMB":s.code==-98?"MMB":"Mouse "+(s.code+101);
        else {
            key=GameSettings.getKeyDisplayString(s.code);
            if(key.equals("LSHIFT"))key="L Shift";if(key.equals("RSHIFT"))key="R Shift";
            if(key.equals("LCONTROL"))key="L Ctrl";if(key.equals("RCONTROL"))key="R Ctrl";
            if(key.equals("LMENU"))key="L Alt";if(key.equals("RMENU"))key="R Alt";
            if(key.equals("PRIOR"))key="PgUp";if(key.equals("NEXT"))key="PgDn";
        }
        return (s.mod==Mod.NONE?"":s.mod==Mod.CONTROL?"Ctrl + ":s.mod==Mod.SHIFT?"Shift + ":"Alt + ")+key;
    }
    public static Mod activeModifier(){return mod(KeyModifier.getActiveModifier());}
    public static final class MouseState {
        public float sensitivity;public boolean invert,autoJump,touchscreen;
        public MouseState(GameSettings gs){sensitivity=gs.mouseSensitivity;invert=gs.invertMouse;autoJump=gs.autoJump;touchscreen=gs.touchscreen;}
        public MouseState(MouseState other){sensitivity=other.sensitivity;invert=other.invert;autoJump=other.autoJump;touchscreen=other.touchscreen;}
        public void install(GameSettings gs){gs.mouseSensitivity=sensitivity;gs.invertMouse=invert;gs.autoJump=autoJump;gs.touchscreen=touchscreen;}
        public boolean same(MouseState o){return Float.compare(sensitivity,o.sensitivity)==0&&invert==o.invert&&autoJump==o.autoJump&&touchscreen==o.touchscreen;}
    }
}
