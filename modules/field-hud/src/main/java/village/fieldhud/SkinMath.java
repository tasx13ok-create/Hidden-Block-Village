package village.fieldhud;

/** Display math only. Combat/resource decisions remain on their original server. */
public final class SkinMath {
    private SkinMath() {}
    public static double ease(double from,double to,double ticks,double response) {
        if(!Double.isFinite(from)||!Double.isFinite(to))return Double.isFinite(to)?to:0;
        if(!Double.isFinite(ticks)||ticks<=0)return from;
        return from+(to-from)*(1-Math.exp(-ticks/Math.max(0.01,response)));
    }
    public static double ratio(double value,double maximum) {
        if(!Double.isFinite(value)||!Double.isFinite(maximum)||maximum<=0)return 0;
        return Math.max(0,Math.min(1,value/maximum));
    }
    public static boolean lowHealth(float health) { return Float.isFinite(health)&&health>0&&health<=6; }
    public static double selectorAngle(double ticks) {
        double t=((ticks%120)+120)%120;
        if(t<30||t>=48)return 0;
        t-=30;
        int[] times={3,1,1,2,5,2,1,1,2};
        double[] angles={0,60,150,240,300,360,300,240,150,0};
        for(int i=0;i<times.length;i++) {
            if(t<times[i]) { double v=t/times[i];v=v*v*(3-2*v);return angles[i]+(angles[i+1]-angles[i])*v; }
            t-=times[i];
        }
        return 0;
    }
    public static int slotCurve(int slot,boolean enabled) {
        return enabled?(int)Math.round(Math.pow((slot-4)/4.0,2)*2):0;
    }
}
