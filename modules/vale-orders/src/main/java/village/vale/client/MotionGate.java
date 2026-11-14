package village.vale.client;

/** Respects the shared "reduce motion" choice: the Field HUD's animation toggle when present. */
public final class MotionGate {
    private MotionGate() {}
    private static java.lang.reflect.Field motion;
    private static boolean tried;
    public static boolean allowed() {
        if (!tried) {
            tried = true;
            try { motion = Class.forName("village.fieldhud.HudConfig").getField("motion"); } catch (ReflectiveOperationException | LinkageError absent) { motion = null; }
        }
        try { return motion == null || motion.getBoolean(null); } catch (IllegalAccessException e) { return true; }
    }
}
