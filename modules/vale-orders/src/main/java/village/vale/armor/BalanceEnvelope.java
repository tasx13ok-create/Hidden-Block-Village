package village.vale.armor;

/** Frozen numeric limits for the next server adapter; this class applies no damage or flight permission. */
public final class BalanceEnvelope {
    public static final int ARMOR=20,TOUGHNESS=8;
    public static final double RANGE=5.5;
    public static final int STAMINA_MAX=2000,STAMINA_START=400,FLIGHT_DRAIN=5,GROUND_RECHARGE=8,REST_TICKS=40;
    public final float rawDamage;
    public final int cooldownTicks;
    private BalanceEnvelope(float damage,int cooldown){this.rawDamage=damage;this.cooldownTicks=cooldown;}
    public static BalanceEnvelope forRole(ArmorProfile.Role role){
        switch(role){
            case ANCHOR: case UNDERTOW: case CLEANSE: case VEIL:return new BalanceEnvelope(1.5f,60);
            case HEAT:return new BalanceEnvelope(2.5f,50);
            case MOMENTUM:return new BalanceEnvelope(2f,50);
            case COMPANION:return new BalanceEnvelope(3f,24);
            case REACTIVE:return new BalanceEnvelope(2f,20);
            case FLIGHT:return new BalanceEnvelope(0,0);
            default:throw new IllegalArgumentException("Missing role");
        }
    }
    public static int stamina(int value,boolean equipped,boolean powered,boolean grounded,boolean resting){
        value=Math.max(0,Math.min(STAMINA_MAX,value));
        if(!equipped)return value;
        if(powered)return Math.max(0,value-FLIGHT_DRAIN);
        return grounded&&!resting?Math.min(STAMINA_MAX,value+GROUND_RECHARGE):value;
    }
    public static boolean canStart(int value){return value>=STAMINA_START;}
}
