package village.fieldhud;

import java.util.UUID;
import java.math.BigDecimal;

/** Optional owner telemetry contract; never calls the original resource API. */
public final class SharedResourceReader {
    public static final class State {
        public final String provider;public final double amount,maximum,scale;public final boolean ready;
        State(String provider,double amount,double maximum,double scale,boolean ready){this.provider=provider;this.amount=amount;this.maximum=maximum;this.scale=scale;this.ready=ready;}
    }
    public State read(Object value,UUID owner,int entity,int dimension){
        if(value==null||owner==null)return null;
        try{
            Class<?> type=value.getClass();
            if(!owner.equals(type.getField("owner").get(value))||type.getField("entityId").getInt(value)!=entity||type.getField("dimension").getInt(value)!=dimension||type.getField("serverTick").getLong(value)<0)return null;
            String provider=(String)type.getField("provider").get(value);int state=type.getField("state").getInt(value);
            double amount=type.getField("amount").getDouble(value),maximum=type.getField("maximum").getDouble(value),scale=type.getField("scale").getDouble(value);
            if(!("narutomod".equals(provider)||"resolve".equals(provider))||state<0||state>5||!Double.isFinite(scale)||scale<.01||scale>100||("resolve".equals(provider)&&scale!=1))return null;
            boolean ready=state==0;
            if(ready?(!Double.isFinite(amount)||amount<0||!Double.isFinite(maximum)||maximum<=0):(amount!=0||maximum!=0))return null;
            return new State(provider,amount,maximum,scale,ready);
        }catch(ReflectiveOperationException|RuntimeException invalid){return null;}
    }
    public static String cost(State state,int units){
        if(state==null||!state.ready)return "Resource unavailable";
        if(units<0||units>10000)throw new IllegalArgumentException("Invalid cost");
        return BigDecimal.valueOf(state.scale).multiply(BigDecimal.valueOf(units)).stripTrailingZeros().toPlainString()+(state.provider.equals("narutomod")?" chakra":" resolve");
    }
}
