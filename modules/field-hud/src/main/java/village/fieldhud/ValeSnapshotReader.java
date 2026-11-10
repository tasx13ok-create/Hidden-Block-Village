package village.fieldhud;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Schema-1 projection of a received server DTO. Never reads/creates player NBT. */
public final class ValeSnapshotReader {
    private Class<?> type;
    private final Map<String,Field> fields=new HashMap<>();
    public static final class State {
        public final UUID owner;
        public final int dimension,level,cooldownRemaining,cooldownTotal,contractProgress,contractGoal;
        public final long serverTick,merit,castSequence,lastCastServerTick;
        public final double resolve,resolveMax;
        public final String artId,artName,formId,formName,companionId,companionName,contractId,contractName,lastCastArtId;
        private State(ValeSnapshotReader r,Object raw) throws ReflectiveOperationException {
            owner=(UUID)r.required(raw,"playerId");dimension=r.number(raw,"dimension").intValue();serverTick=r.number(raw,"serverTick").longValue();
            resolve=r.number(raw,"resolve").doubleValue();resolveMax=r.number(raw,"resolveMax").doubleValue();
            level=r.number(raw,"level").intValue();merit=r.number(raw,"merit").longValue();
            artId=r.text(raw,"selectedArtId");artName=r.text(raw,"selectedArtName");
            cooldownRemaining=r.optionalInt(raw,"cooldownRemainingTicks");cooldownTotal=r.optionalInt(raw,"cooldownTotalTicks");
            formId=r.text(raw,"activeFormId");formName=r.text(raw,"activeFormName");companionId=r.text(raw,"companionId");companionName=r.text(raw,"companionName");
            contractId=r.text(raw,"contractId");contractName=r.text(raw,"contractName");contractProgress=r.optionalInt(raw,"contractProgress");contractGoal=r.optionalInt(raw,"contractGoal");
            castSequence=r.optionalLong(raw,"castSequence");lastCastServerTick=r.optionalLong(raw,"lastCastServerTick");lastCastArtId=r.text(raw,"lastCastArtId");
        }
    }
    private Object required(Object raw,String name) throws ReflectiveOperationException {
        Field f=fields.get(name);if(f==null)throw new NoSuchFieldException(name);return f.get(raw);
    }
    private Number number(Object raw,String name) throws ReflectiveOperationException { return (Number)required(raw,name); }
    private int optionalInt(Object raw,String name) throws ReflectiveOperationException { return fields.containsKey(name)?Math.max(0,number(raw,name).intValue()):0; }
    private long optionalLong(Object raw,String name) throws ReflectiveOperationException { return fields.containsKey(name)?Math.max(0,number(raw,name).longValue()):0; }
    private String text(Object raw,String name) throws ReflectiveOperationException {
        if(!fields.containsKey(name))return "";Object value=required(raw,name);return value instanceof String?(String)value:"";
    }
    public State read(Object raw,UUID owner,int dimension) {
        if(raw==null||owner==null)return null;
        try {
            if(type!=raw.getClass()) { type=raw.getClass();fields.clear();for(Field f:type.getFields())fields.put(f.getName(),f); }
            if(number(raw,"schemaVersion").intValue()!=1)return null;
            State s=new State(this,raw);
            if(!owner.equals(s.owner)||dimension!=s.dimension||s.serverTick<0||s.level<0||s.merit<0
                ||!Double.isFinite(s.resolve)||!Double.isFinite(s.resolveMax)||s.resolveMax<=0||s.resolve<0)return null;
            return s;
        } catch(ReflectiveOperationException|RuntimeException error) { return null; }
    }
}
