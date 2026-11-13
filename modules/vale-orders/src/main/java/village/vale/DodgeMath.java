package village.vale;

/** Four real native-physics directions and bounded presentation curves. */
public final class DodgeMath {
    private DodgeMath(){}
    public static final int FRONT=0,BACK=1,LEFT=2,RIGHT=3;
    public static final int ANIM_TICKS=9,COOLDOWN_TICKS=16,GRACE_TICKS=6,DEFAULT_COST=6;
    public static final double SPEED=1.55,LIFT=.08,BACK_LIFT=.14;
    public static int direction(float forward,float strafe){
        if(!Float.isFinite(forward)||!Float.isFinite(strafe))return BACK;
        if(Math.abs(strafe)>.1f&&Math.abs(strafe)>=Math.abs(forward))return strafe>0?LEFT:RIGHT;
        return forward>.1f?FRONT:BACK;
    }
    public static boolean valid(int direction){return direction>=FRONT&&direction<=RIGHT;}
    public static double[] velocity(int direction,float yaw){
        if(!valid(direction)||!Float.isFinite(yaw))throw new IllegalArgumentException("Invalid dodge direction/yaw");
        double angle=Math.toRadians(yaw),fx=-Math.sin(angle),fz=Math.cos(angle),rx=-Math.cos(angle),rz=-Math.sin(angle);
        double x=direction==FRONT?fx:direction==BACK?-fx:direction==LEFT?-rx:rx;
        double z=direction==FRONT?fz:direction==BACK?-fz:direction==LEFT?-rz:rz;
        double speed=direction==BACK?SPEED*.9:SPEED;
        return new double[]{x*speed,direction==BACK?BACK_LIFT:LIFT,z*speed};
    }
    public static boolean allowed(boolean alive,boolean spectator,long now,long ready,boolean ground,boolean airUsed){
        return alive&&!spectator&&now>=0&&ready>=0&&now>=ready&&(ground||!airUsed);
    }
    public static boolean supports(String version){
        if(version==null)return false;java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("^0\\.3\\.(\\d{1,6})(?:[-+][A-Za-z0-9_.-]+)?$").matcher(version);
        return matcher.matches()&&Integer.parseInt(matcher.group(1))>=14;
    }
    public static double progress(double age){
        if(!Double.isFinite(age))return 0;double u=Math.max(0,Math.min(1,age/ANIM_TICKS)),ease=1-Math.pow(1-u,3);return .35*u+.65*ease;
    }
    public static double spin(int direction,double age){return (direction==FRONT||direction==RIGHT?360:-360)*progress(age);}
    public static double tuck(double age){if(!Double.isFinite(age))return 0;double u=Math.max(0,Math.min(1,age/ANIM_TICKS)),in=Math.min(1,u*4),out=Math.min(1,(1-u)*2.5);return Math.sin(Math.PI/2*Math.min(in,out));}
    public static double[] lean(int direction,double age){double k=tuck(age);switch(direction){case FRONT:return new double[]{38*k,0};case BACK:return new double[]{-22*k,0};case LEFT:return new double[]{8*k,-28*k};default:return new double[]{8*k,28*k};}}
    public static double ghostAlpha(int index,double age){if(index<0||index>=3||!Double.isFinite(age))return 0;return .34*(1-index/3.0)*Math.max(0,Math.min(1,1-age/ANIM_TICKS));}
    public static double retain(double motion,double fraction){return Math.max(-1.2,Math.min(1.2,motion))*fraction;}
}
