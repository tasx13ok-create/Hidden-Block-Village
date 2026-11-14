package village.vale.bridge;
import java.util.*;

/** Pure accepted-outcome rules used by the native event adapter; intent never settles a cue. */
public final class BridgeReceipts {
    private BridgeReceipts(){}
    public interface Context {boolean valid();}
    public interface FinalEvents {boolean attackCanceled();boolean hurtCanceled();double hurtAmount();boolean damageCanceled();double damageAmount();}
    public static boolean spawn(Context context,boolean finalCanceled,boolean sameEntity,boolean admitted,boolean sameOwner){return context!=null&&!finalCanceled&&sameEntity&&admitted&&sameOwner&&context.valid();}
    public static final class Damage {
        private final Context context;private final double healthBefore,absorptionBefore;private FinalEvents events;private double absorptionLoss,acceptedLoss;private boolean sealed;
        public Damage(Context context,double health,double absorption){this.context=context;healthBefore=health;absorptionBefore=absorption;}
        public void matched(FinalEvents events){if(this.events==null)this.events=events;}
        private boolean admitted(){return events!=null&&!events.attackCanceled()&&!events.hurtCanceled()&&!events.damageCanceled()&&Double.isFinite(events.hurtAmount())&&events.hurtAmount()>0&&Double.isFinite(events.damageAmount())&&events.damageAmount()>=0&&(events.damageAmount()>0||absorptionLoss>0);}
        public void observe(double health,double absorption,boolean seal){
            if(sealed)return;
            if(events!=null&&Double.isFinite(absorptionBefore)&&Double.isFinite(absorption))absorptionLoss=Math.max(absorptionLoss,Math.max(0,absorptionBefore-absorption));
            if(admitted()&&Double.isFinite(healthBefore)&&Double.isFinite(absorptionBefore)&&Double.isFinite(health)&&Double.isFinite(absorption))acceptedLoss=Math.max(acceptedLoss,healthBefore+absorptionBefore-health-absorption);
            if(seal)sealed=true;
        }
        public boolean accepted(){return context!=null&&admitted()&&acceptedLoss>0&&context.valid();}
    }
    public static final class Budget {
        private final Map<UUID,Long> ownerTicks=new LinkedHashMap<>();private final Set<String> used=new LinkedHashSet<>();private long tick=Long.MIN_VALUE;private int thisTick;
        public boolean take(UUID owner,String receipt,long now){
            if(owner==null||receipt==null||receipt.length()>192||now<0)return false;
            if(now<tick){clear();}if(now!=tick){tick=now;thisTick=0;}
            Long last=ownerTicks.get(owner);if(thisTick>=8||last!=null&&now-last<4||used.contains(receipt))return false;
            if(ownerTicks.size()>=256&&!ownerTicks.containsKey(owner)){Iterator<UUID> i=ownerTicks.keySet().iterator();i.next();i.remove();}
            if(used.size()>=2048){Iterator<String> i=used.iterator();i.next();i.remove();}
            ownerTicks.put(owner,now);used.add(receipt);thisTick++;return true;
        }
        public void clear(){ownerTicks.clear();used.clear();tick=Long.MIN_VALUE;thisTick=0;}
    }
}
