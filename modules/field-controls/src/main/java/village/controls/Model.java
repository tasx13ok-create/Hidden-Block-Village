package village.controls;

import java.util.*;

/** Pure planning model. No game state changes until the user applies a session. */
public final class Model {
    private Model() {}
    public enum Mod { NONE, SHIFT, CONTROL, ALT }
    public static final class Stroke {
        public int code;
        public Mod mod;
        public Stroke() { this(0, Mod.NONE); }
        public Stroke(int code) { this(code, Mod.NONE); }
        public Stroke(int code, Mod mod) { this.code=code;this.mod=code==0||modifierMatches(mod,code)?Mod.NONE:Objects.requireNonNull(mod); }
        @Override public boolean equals(Object x) { return x instanceof Stroke && ((Stroke)x).code==code && ((Stroke)x).mod==mod; }
        @Override public int hashCode() { return code*31+mod.ordinal(); }
    }
    public static final class Action {
        public final String id,raw,category,label,source,group,help;
        public final int index,priority;
        public final boolean game;
        public final Stroke defaults;
        public Action(int index,String id,String raw,String category,String label,String source,String group,String help,int priority,boolean game,Stroke defaults) {
            this.index=index;this.id=id;this.raw=raw;this.category=category;this.label=label;this.source=source;this.group=group;this.help=help;this.priority=priority;this.game=game;this.defaults=defaults;
        }
    }
    public static final class Catalog {
        public final List<Action> actions;
        private final boolean[][] overlap;
        public Catalog(List<Action> actions,boolean[][] overlap) { this.actions=Collections.unmodifiableList(new ArrayList<>(actions));this.overlap=overlap; }
        public boolean overlaps(Action a,Action b) { return overlap[a.index][b.index]; }
        public Action byRaw(String raw) { for(Action a:actions)if(a.raw.equals(raw))return a;return null; }
        public Action byId(String id) { for(Action a:actions)if(a.id.equals(id))return a;return null; }
    }
    public static final class Conflict {
        public final Action a,b;public final boolean modifierOnly;
        Conflict(Action a,Action b,boolean soft) { this.a=a;this.b=b;this.modifierOnly=soft; }
        public Action other(Action x) { return a==x?b:a; }
    }
    public static boolean hard(Catalog c,Action a,Stroke x,Action b,Stroke y) {
        return x.code!=0 && y.code!=0 && c.overlaps(a,b) && x.code==y.code &&
            (x.mod==y.mod || (a.game||b.game)&&(x.mod==Mod.NONE||y.mod==Mod.NONE));
    }
    private static boolean modifierMatches(Mod m,int code) {
        return m==Mod.SHIFT&&(code==42||code==54) || m==Mod.CONTROL&&(code==29||code==157) || m==Mod.ALT&&(code==56||code==184);
    }
    public static List<Conflict> conflicts(Catalog c,Map<String,Stroke> values) {
        List<Conflict> out=new ArrayList<>();
        for(int i=0;i<c.actions.size();i++)for(int j=i+1;j<c.actions.size();j++) {
            Action a=c.actions.get(i),b=c.actions.get(j);Stroke x=values.get(a.id),y=values.get(b.id);
            if(x==null||y==null||x.code==0||y.code==0||!c.overlaps(a,b))continue;
            if(hard(c,a,x,b,y))out.add(new Conflict(a,b,false));
            else if(modifierMatches(x.mod,y.code)||modifierMatches(y.mod,x.code))out.add(new Conflict(a,b,true));
        }
        return out;
    }
    public enum Preset {
        ACTION("Action", "Shift to sprint. Ctrl to crouch.", "WASD, E inventory, Q/R/F arts, G armor, C charge."),
        MMO("MMO", "A familiar skill row around WASD.", "Shift sprint, Ctrl crouch, Q/E/R arts, I inventory."),
        CLASSIC("Classic", "Keep Minecraft movement muscle memory.", "Ctrl sprint, Shift crouch, E inventory, R/F/V arts.");
        public final String title,subtitle,description;
        Preset(String a,String b,String c) { title=a;subtitle=b;description=c; }
    }
    public static final class Plan {
        public final Map<String,Stroke> values;
        public final List<Action> changed;
        public final int unresolved;
        Plan(Map<String,Stroke> values,List<Action> changed,int unresolved) { this.values=values;this.changed=changed;this.unresolved=unresolved; }
    }
    public static Plan plan(Catalog c,Map<String,Stroke> current,Preset preset) { return allocate(c,current,preset,false); }
    public static Plan repair(Catalog c,Map<String,Stroke> current) { return allocate(c,current,null,true); }
    private static Plan allocate(Catalog c,Map<String,Stroke> current,Preset preset,boolean repair) {
        List<Action> sorted=new ArrayList<>(c.actions);
        sorted.sort(Comparator.comparingInt((Action a)->a.priority).thenComparing(a->a.id));
        LinkedHashMap<String,Stroke> out=new LinkedHashMap<>();
        List<Action> assigned=new ArrayList<>();int unresolved=0;
        for(Action a:sorted) {
            Stroke old=current.get(a.id);if(old==null)old=a.defaults;
            Stroke wanted=repair?old:Metadata.preferred(a,preset,old);
            Stroke chosen=wanted;
            if(wanted.code!=0&&!free(c,a,wanted,assigned,out)) {
                chosen=null;
                if(!repair&&old.code!=0&&free(c,a,old,assigned,out))chosen=old;
                if(chosen==null)for(Stroke candidate:candidates())if(free(c,a,candidate,assigned,out)){chosen=candidate;break;}
                if(chosen==null) { chosen=new Stroke();unresolved++; }
            }
            out.put(a.id,chosen);assigned.add(a);
        }
        List<Action> changed=new ArrayList<>();for(Action a:c.actions)if(!Objects.equals(out.get(a.id),current.get(a.id)))changed.add(a);
        return new Plan(out,changed,unresolved);
    }
    private static boolean free(Catalog c,Action a,Stroke x,List<Action> assigned,Map<String,Stroke> out) {
        // Modified shortcuts must never steal a held movement/modifier key.
        for(Action b:assigned) {
            Stroke y=out.get(b.id);
            if(hard(c,a,x,b,y) || c.overlaps(a,b)&&(modifierMatches(x.mod,y.code)||modifierMatches(y.mod,x.code)))return false;
        }
        return true;
    }
    private static List<Stroke> candidates() {
        List<Stroke> out=new ArrayList<>();
        // Easy single keys first, then function/navigation keys, then Alt chords.
        int[] pool={47,44,45,48,49,36,37,38,22,21,35,24,25,23,16,18,19,20,33,34,46,41,26,27,43,39,40,51,52,12,13,11,14,65,66,67,68,64,63,62,88,199,207,201,209,210,200,203,205,208,71,72,73,75,76,77,79,80,81,82,83,78,74,55,181,156,28,-97,-96};
        for(int k:pool)out.add(new Stroke(k));
        int[] chord={16,18,19,33,34,46,47,44,45,48,49,36,37,38,22,21,35,24,25,26,27,39,40,51,52};
        for(int k:chord)out.add(new Stroke(k,Mod.ALT));
        // Do not offer Alt+F4, Alt+Tab or OS shortcuts.
        return out;
    }
    public static final class Session {
        public final Catalog catalog;
        public Map<String,Stroke> baseline,draft;
        private static final class History {final Map<String,Stroke> values;final String layout;History(Map<String,Stroke> v,String l){values=new LinkedHashMap<>(v);layout=l;}}
        private final Deque<History> undo=new ArrayDeque<>();
        public String layout="Custom";
        public Session(Catalog c,Map<String,Stroke> initial) { catalog=c;baseline=new LinkedHashMap<>(initial);draft=new LinkedHashMap<>(initial); }
        public void replace(Map<String,Stroke> values,String name) {
            Map<String,Stroke> next=new LinkedHashMap<>(draft);
            for(Action a:catalog.actions)if(values.containsKey(a.id)&&values.get(a.id)!=null)next.put(a.id,values.get(a.id));
            if(next.equals(draft)&&Objects.equals(layout,name))return;
            undo.push(new History(draft,layout));if(undo.size()>40)undo.removeLast();draft=next;layout=name;
        }
        public void set(Action a,Stroke value) { Map<String,Stroke> next=new LinkedHashMap<>(draft);next.put(a.id,value);replace(next,"Custom"); }
        public void undo() { if(!undo.isEmpty()){History h=undo.pop();draft=h.values;layout=h.layout;} }
        public boolean canUndo() { return !undo.isEmpty(); }
        public int changes() { int n=0;for(Action a:catalog.actions)if(!Objects.equals(draft.get(a.id),baseline.get(a.id)))n++;return n; }
        public List<Conflict> conflicts() { return Model.conflicts(catalog,draft); }
        public int hardCount() { int n=0;for(Conflict x:conflicts())if(!x.modifierOnly)n++;return n; }
        public int missingEssentials() { int n=0;for(Action a:catalog.actions)if(Metadata.required(a.raw)&&draft.get(a.id).code==0)n++;return n; }
        public void saved() { baseline=new LinkedHashMap<>(draft);undo.clear(); }
    }
}
