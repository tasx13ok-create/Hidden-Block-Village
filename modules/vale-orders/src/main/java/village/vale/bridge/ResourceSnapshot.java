package village.vale.bridge;

import java.util.UUID;

/** Immutable owner-only resource view. Amounts retain the provider's native precision. */
public final class ResourceSnapshot {
    public final UUID owner;
    public final int entityId,dimension,state;
    public final long serverTick;
    public final String provider;
    public final double amount,maximum,scale;
    public ResourceSnapshot(UUID owner,int entityId,int dimension,long tick,String provider,int state,double amount,double maximum,double scale){
        if(owner==null||entityId<0||tick<0||!("narutomod".equals(provider)||"resolve".equals(provider))||state<0||state>=ChakraLink.State.values().length||!Double.isFinite(scale)||scale<.01||scale>100||("resolve".equals(provider)&&scale!=1))throw new IllegalArgumentException("Invalid resource identity");
        if(state==ChakraLink.State.READY.ordinal()){
            if(!Double.isFinite(amount)||amount<0||!Double.isFinite(maximum)||maximum<=0)throw new IllegalArgumentException("Invalid resource amount");
        }else if(amount!=0||maximum!=0)throw new IllegalArgumentException("Unavailable resource contains an amount");
        this.owner=owner;this.entityId=entityId;this.dimension=dimension;serverTick=tick;this.provider=provider;this.state=state;this.amount=amount;this.maximum=maximum;this.scale=scale;
    }
    public boolean ready(){return state==ChakraLink.State.READY.ordinal();}
    public double units(){return ready()?amount/scale:0;}
}
