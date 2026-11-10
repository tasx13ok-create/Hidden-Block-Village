package village.vale.arsenal;

/** Shared pure decisions used by the actual item, field and projectile code. */
public final class ArsenalMath {
    public static final double RANGE=24, TETHER_RANGE=12;
    public static final int SHARED_RECOVERY=12, MAX_FLIGHT_TICKS=60, MAX_FIELDS=2, MAX_SHOTS=8;
    private ArsenalMath() {}
    public static float charge(int ticks) {
        float t=Math.max(0,Math.min(40,ticks))/20f;
        return Math.min(1f,(t*t+2*t)/3f);
    }
    public static boolean ready(boolean server,boolean alive,boolean spectator,boolean held,
            boolean issued,boolean set,boolean cooling,long now,long ready,int resolve,int cost) {
        return server&&alive&&!spectator&&held&&issued&&set&&!cooling&&now>=ready&&cost>0&&cost<=100&&resolve>=cost;
    }
    public static boolean released(int heldTicks,int minimum) { return minimum>=0&&heldTicks>=minimum&&heldTicks<=72000; }
    public static float finishDamage(float health,float maximum,float power) {
        if(!Float.isFinite(health)||!Float.isFinite(maximum)||maximum<=0||!Float.isFinite(power))return 0;
        float missing=1f-Math.max(0,Math.min(1,health/maximum));
        return Math.min(16,6+8*missing+2*Math.max(0,Math.min(1,power)));
    }
    public static double pull(double distance) {
        return !Double.isFinite(distance)||distance<=2?0:Math.min(.35,(distance-2)*.07);
    }
    public static boolean validFlight(int age,double distanceSquared,boolean loaded,boolean owner,boolean issued) {
        return age>=0&&age<=MAX_FLIGHT_TICKS&&Double.isFinite(distanceSquared)&&distanceSquared<=RANGE*RANGE&&loaded&&owner&&issued;
    }
    public static float restoredDamage(int damage,int maximum,float base) {
        if(maximum<=0)return base;
        return base*(.65f+.35f*(1-Math.max(0,Math.min(maximum,damage))/(float)maximum));
    }
}
