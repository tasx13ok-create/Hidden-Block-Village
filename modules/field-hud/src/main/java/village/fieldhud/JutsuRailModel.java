package village.fieldhud;

import java.util.*;

/** Fixed six-slot cursor and presentation motion; no packets or inventory writes. */
public final class JutsuRailModel {
    private List<JutsuEntry> entries=Collections.emptyList();
    private Map<String,JutsuEntry> byId=Collections.emptyMap();
    private final Map<String,Double> ready=new HashMap<>();
    private JutsuLoadout loadout=JutsuLoadout.empty();
    private int selected;
    private boolean focused,initialized;
    private long serverTick=-1;
    private double receivedAt,focusFrom,focusTarget,focusAt,rowFrom,rowAt;
    public void reset() {
        entries=Collections.emptyList();byId=Collections.emptyMap();ready.clear();loadout=JutsuLoadout.empty();selected=0;
        focused=initialized=false;serverTick=-1;receivedAt=focusFrom=focusTarget=focusAt=rowFrom=rowAt=0;
    }
    public void restore(JutsuLoadout value) {
        if(value==null)throw new IllegalArgumentException("Missing loadout");
        loadout=value;selected=value.selected;initialized=true;ready.clear();focused=false;
        rowFrom=selected;focusFrom=focusTarget=0;
    }
    public boolean accept(List<JutsuEntry> incoming,String serverSelected,long tick,double now,boolean motion) {
        if(incoming==null||incoming.size()>128||tick<0||tick<serverTick||!Double.isFinite(now))return false;
        Map<String,JutsuEntry> next=new LinkedHashMap<>();
        for(JutsuEntry e:incoming)if(e==null||next.put(e.id,e)!=null)return false;
        JutsuEntry old=selected(),fresh=initialized?next.get(loadout.slots.get(selected).id):null;
        if(motion&&old!=null&&fresh!=null&&(old.remaining>0||old.cooldownFraction>0)&&fresh.canCast)ready.put(fresh.id,now);
        ready.keySet().retainAll(next.keySet());
        entries=Collections.unmodifiableList(new ArrayList<>(incoming));byId=Collections.unmodifiableMap(next);
        if(!initialized){
            JutsuLoadout starter=JutsuLoadout.defaults(entries,serverSelected);
            for(JutsuSlot slot:starter.slots)if(!slot.emptySlot()){restore(starter);break;}
        }
        List<JutsuSlot> updated=new ArrayList<>();
        for(JutsuSlot slot:loadout.slots){JutsuEntry actual=next.get(slot.id);updated.add(actual==null?slot:JutsuSlot.from(actual));}
        loadout=new JutsuLoadout(updated,selected);
        serverTick=tick;receivedAt=now;
        if(!hasAssignments())focus(false,now,motion);
        if(!motion)ready.clear();
        return true;
    }
    public List<JutsuEntry> entries(){return entries;}
    public JutsuEntry find(String id){return byId.get(id);}
    public boolean initialized(){return initialized;}
    public boolean hasAssignments(){for(JutsuSlot slot:loadout.slots)if(!slot.emptySlot())return true;return false;}
    public JutsuLoadout loadout(){return new JutsuLoadout(loadout.slots,selected);}
    public int index(){return selected;}
    public JutsuEntry selected(){return byId.get(loadout.slots.get(selected).id);}
    public JutsuEntry display(int slot){JutsuSlot saved=loadout.slots.get(slot);JutsuEntry actual=byId.get(saved.id);return actual==null?saved.unavailable():actual;}
    public boolean focused(){return focused;}
    public boolean focus(boolean value,double now,boolean motion) {
        value=value&&initialized&&hasAssignments();
        if(focused==value){if(!motion)focusFrom=focusTarget=value?1:0;return focused;}
        focusFrom=focus(now,true);focusTarget=value?1:0;focusAt=now;focused=value;
        if(!motion)focusFrom=focusTarget;
        return focused;
    }
    public boolean wheel(int delta,int visible,double now,boolean motion) {
        if(!focused||delta==0)return false;
        double before=selectedRow(visible,now,motion);
        selected=(selected+(delta>0?-1:1)+JutsuLoadout.SIZE)%JutsuLoadout.SIZE;
        rowFrom=before;rowAt=now;if(!motion)rowFrom=selected;
        return true;
    }
    public int first(int visible){return 0;}
    public double selectedRow(int visible,double now,boolean motion){return motion?mix(rowFrom,selected,now-rowAt,3):selected;}
    public double focus(double now,boolean motion){return motion?mix(focusFrom,focusTarget,now-focusAt,4):focused?1:0;}
    public double elapsed(double now){return Math.max(0,now-receivedAt);}
    public double ready(String id,double now,boolean motion) {
        if(!motion)return 0;
        Double at=ready.get(id);if(at==null)return 0;
        double age=now-at;
        if(age<0||age>=10){ready.remove(id);return 0;}
        return Math.sin(Math.PI*Math.max(0,Math.min(1,age/10)));
    }
    private static double mix(double a,double b,double age,double duration) {
        double t=Math.max(0,Math.min(1,age/duration));return a+(b-a)*(t*t*(3-2*t));
    }
}
