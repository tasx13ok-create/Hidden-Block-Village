package village.fieldhud;

import java.lang.reflect.Field;
import java.util.*;

/** Additive schema1 abilities / schema2 loadout. Never reads gameplay state from NBT. */
public final class JutsuSnapshotReader {
    private final Map<Class<?>,Map<String,Field>> fields=new HashMap<>();
    public static final class State {
        public final UUID owner;
        public final int dimension;
        public final long serverTick;
        public final String selected;
        public final List<JutsuEntry> entries;
        State(UUID owner,int dimension,long tick,String selected,List<JutsuEntry> entries){this.owner=owner;this.dimension=dimension;serverTick=tick;this.selected=selected;this.entries=Collections.unmodifiableList(entries);}
    }
    private Object field(Object raw,String key)throws ReflectiveOperationException {
        Map<String,Field> type=fields.get(raw.getClass());
        if(type==null){
            if(fields.size()>8)fields.clear();type=new HashMap<>();for(Field f:raw.getClass().getFields())type.put(f.getName(),f);fields.put(raw.getClass(),type);
        }
        Field f=type.get(key);if(f==null)throw new NoSuchFieldException(key);return f.get(raw);
    }
    private int integer(Object raw,String key)throws ReflectiveOperationException{return ((Number)field(raw,key)).intValue();}
    private String text(Object raw,String key)throws ReflectiveOperationException{return (String)field(raw,key);}
    public State read(Object raw,UUID owner,int dimension) {
        if(raw==null||owner==null)return null;
        try {
            int schema=integer(raw,"schemaVersion");if(schema!=1&&schema!=2)return null;
            UUID id=(UUID)field(raw,"playerId");int dim=integer(raw,"dimension");long tick=((Number)field(raw,"serverTick")).longValue();
            if(!owner.equals(id)||dim!=dimension||tick<0)return null;
            String selected=text(raw,"selectedEntryId");if(selected==null||selected.length()>96)return null;
            Object source=field(raw,"entries");if(!(source instanceof Iterable))return null;
            List<JutsuEntry> entries=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(Object e:(Iterable<?>)source){
                if(e==null||entries.size()>=(schema==2?128:64))return null;
                JutsuEntry entry=new JutsuEntry(text(e,"id"),text(e,"title"),text(e,"iconItemId"),integer(e,"iconMeta"),text(e,"kind"),text(e,"schoolKey"),integer(e,"requiredLevel"),"Resolve",integer(e,"resolveCost"),integer(e,"itemCount"),integer(e,"cooldownRemainingTicks"),integer(e,"cooldownTotalTicks"),0,((Number)field(e,"cooldownFraction")).doubleValue(),(Boolean)field(e,"enabled"),text(e,"reason"));
                if(entry.kind.equals("weapon")){
                    if(schema!=2||!entry.id.matches("weapon/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))return null;
                }else if(!entry.id.matches("(?:art|order|technique|item)/[a-z0-9_:.\\-/]+")||!Arrays.asList("art","order","technique","projectile","form","summon").contains(entry.kind))return null;
                if(!seen.add(entry.id))return null;entries.add(entry);
            }
            if(!selected.isEmpty()&&!seen.contains(selected))return null;
            return new State(id,dim,tick,selected,entries);
        }catch(ReflectiveOperationException|RuntimeException invalid){return null;}
    }
}
