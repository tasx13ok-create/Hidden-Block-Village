package village.fieldhud;

import java.util.*;

/** Exactly six local shortcuts. Duplicate shortcuts share the same real server cooldown. */
public final class JutsuLoadout {
    public static final int SIZE=6;
    public final List<JutsuSlot> slots;
    public final int selected;
    public JutsuLoadout(List<JutsuSlot> slots,int selected) {
        if(slots==null||slots.size()!=SIZE||selected<0||selected>=SIZE)throw new IllegalArgumentException("A loadout has six slots");
        for(JutsuSlot slot:slots)if(slot==null)throw new IllegalArgumentException("Missing loadout slot");
        this.slots=Collections.unmodifiableList(new ArrayList<>(slots));this.selected=selected;
    }
    public static JutsuLoadout empty(){return new JutsuLoadout(Arrays.asList(JutsuSlot.empty(),JutsuSlot.empty(),JutsuSlot.empty(),JutsuSlot.empty(),JutsuSlot.empty(),JutsuSlot.empty()),0);}
    public static JutsuLoadout defaults(List<JutsuEntry> catalogue,String serverSelected) {
        List<JutsuSlot> chosen=new ArrayList<>();Set<String> ids=new HashSet<>();
        // A varied starter deck uses only entries the server actually supplied.
        for(String kind:new String[]{"weapon","art","projectile","form","summon","technique"}) {
            JutsuEntry pick=null;
            for(JutsuEntry e:catalogue)if(e.kind.equals(kind)&&e.enabled&&!ids.contains(e.id)){
                if(pick==null||e.id.equals(serverSelected))pick=e;
            }
            if(pick!=null){chosen.add(JutsuSlot.from(pick));ids.add(pick.id);}
        }
        for(JutsuEntry e:catalogue)if(chosen.size()<SIZE&&e.enabled&&ids.add(e.id))chosen.add(JutsuSlot.from(e));
        while(chosen.size()<SIZE)chosen.add(JutsuSlot.empty());
        return new JutsuLoadout(chosen,0);
    }
}
