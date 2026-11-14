package village.vale.client;
/** Optional reduced-motion and Quiet preference shared by all added client effects. */
public final class MotionGate {private MotionGate(){}public static boolean allowed(){return VfxPreferences.motion();}}
