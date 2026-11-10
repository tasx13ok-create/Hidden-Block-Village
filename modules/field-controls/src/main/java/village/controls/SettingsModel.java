package village.controls;

import java.util.*;

/** Transactional settings drafts; independent of Minecraft for headless verification. */
public final class SettingsModel {
    public enum Kind { BOOLEAN, NUMBER, CHOICE }
    public static final class Def {
        public final String id,page,label,description;
        public final Kind kind;
        public final double min,max,step;
        public final Object defaults;
        public final String[] choices;
        public Def(String id,String page,String label,String help,Kind kind,double min,double max,double step,Object defaults,String... choices) {
            this.id=id;this.page=page;this.label=label;description=help;this.kind=kind;this.min=min;this.max=max;this.step=step;this.defaults=defaults;this.choices=choices;
        }
        public Object number(double value) {
            double v=Math.max(min,Math.min(max,value));
            if(step>0)v=Math.round((v-min)/step)*step+min;
            return Math.max(min,Math.min(max,v));
        }
        public double ratio(Object value){return max<=min?0:Math.max(0,Math.min(1,(((Number)value).doubleValue()-min)/(max-min)));}
        public String format(Object value) {
            if(kind==Kind.BOOLEAN)return Boolean.TRUE.equals(value)?"On":"Off";
            if(kind==Kind.CHOICE){int i=((Number)value).intValue();return i>=0&&i<choices.length?choices[i]:"Scale "+i;}
            double v=((Number)value).doubleValue();
            if(id.equals("FOV"))return Math.round(v)+" degrees";
            if(id.equals("SENSITIVITY"))return Math.round(v*200)+"%";
            if(id.equals("RENDER_DISTANCE"))return Math.round(v)+" chunks";
            if(id.equals("FRAMERATE_LIMIT"))return v>=max?"Unlimited":Math.round(v)+" FPS";
            if(id.equals("MIPMAP_LEVELS"))return "Level "+Math.round(v);
            return Math.round(ratio(value)*100)+"%";
        }
    }
    public interface Port {
        Map<String,Object> read();
        default void begin() throws Exception {}
        void write(String id,Object value) throws Exception;
        void persist() throws Exception;
        default void rollbackFiles() throws Exception {}
    }
    public static boolean equal(Object a,Object b) {
        if(a instanceof Number&&b instanceof Number)return Math.abs(((Number)a).doubleValue()-((Number)b).doubleValue())<.00001;
        return Objects.equals(a,b);
    }
    public static final class Session {
        public final List<Def> defs;
        public final Port port;
        public Map<String,Object> baseline,draft;
        private final Deque<Map<String,Object>> undo=new ArrayDeque<>();
        private Map<String,Object> gestureBase;private boolean gesturePushed;
        public Session(List<Def> defs,Port port){this.defs=Collections.unmodifiableList(new ArrayList<>(defs));this.port=port;baseline=new LinkedHashMap<>(port.read());draft=new LinkedHashMap<>(baseline);}
        public int changes(){int n=0;for(Def d:defs)if(!equal(baseline.get(d.id),draft.get(d.id)))n++;return n;}
        public boolean canUndo(){return !undo.isEmpty();}
        public void change(Map<String,Object> next) {
            Map<String,Object> merged=new LinkedHashMap<>(draft);boolean changed=false;
            for(Def d:defs)if(next.containsKey(d.id)&&next.get(d.id)!=null&&!equal(merged.get(d.id),next.get(d.id))){merged.put(d.id,next.get(d.id));changed=true;}
            if(!changed)return;
            if(gestureBase==null)undo.push(new LinkedHashMap<>(draft));else if(!gesturePushed){undo.push(new LinkedHashMap<>(gestureBase));gesturePushed=true;}
            if(undo.size()>40)undo.removeLast();draft=merged;
        }
        public void set(Def def,Object value){Map<String,Object> next=new HashMap<>();next.put(def.id,def.kind==Kind.NUMBER?def.number(((Number)value).doubleValue()):value);change(next);}
        public void cycle(Def def){Object old=draft.get(def.id);set(def,def.kind==Kind.BOOLEAN?!Boolean.TRUE.equals(old):(((Number)old).intValue()+1)%def.choices.length);}
        public void undo(){if(!undo.isEmpty())draft=undo.pop();}
        public void beginGesture(){gestureBase=new LinkedHashMap<>(draft);gesturePushed=false;}
        public void endGesture(){gestureBase=null;gesturePushed=false;}
        public Map<String,Object> defaults(String page){Map<String,Object> next=new LinkedHashMap<>(draft);for(Def d:defs)if(d.page.equals(page))next.put(d.id,d.defaults);return next;}
        public void refreshIfClean(){if(changes()==0){baseline=new LinkedHashMap<>(port.read());draft=new LinkedHashMap<>(baseline);undo.clear();}}
        public void discard(){baseline=new LinkedHashMap<>(port.read());draft=new LinkedHashMap<>(baseline);undo.clear();}
        public void apply() throws Exception {
            Map<String,Object> before=new LinkedHashMap<>(port.read());List<Def> applied=new ArrayList<>();
            port.begin();
            try {
                for(Def d:defs)if(!equal(before.get(d.id),draft.get(d.id))){applied.add(d);port.write(d.id,draft.get(d.id));}
                Map<String,Object> actual=port.read();
                for(Def d:applied)if(!equal(actual.get(d.id),draft.get(d.id)))throw new IllegalStateException("Setting did not apply: "+d.label);
                port.persist();baseline=new LinkedHashMap<>(actual);draft=new LinkedHashMap<>(actual);undo.clear();
            } catch(Exception failure) {
                Collections.reverse(applied);
                for(Def d:applied)try{port.write(d.id,before.get(d.id));}catch(Exception rollback){failure.addSuppressed(rollback);}
                try{port.persist();}catch(Exception rollback){failure.addSuppressed(rollback);}
                try{port.rollbackFiles();}catch(Exception rollback){failure.addSuppressed(rollback);}
                throw failure;
            }
        }
    }
}
