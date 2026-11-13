package village.vale.bridge;

import java.lang.reflect.Method;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Optional, reflection-only link to a licensed chakra system (NarutoMod's net.narutomod.Chakra).
 * Nothing is compiled against it: when the mod is absent every call returns "unavailable" and
 * moves fall back to Vale resolve. Reads use the API already confirmed by the Field HUD
 * (isInitialized, pathway, getAmount, getMax). A spend method is discovered at runtime from
 * the Pathway class (single numeric parameter, consume/use/drain/subtract-style name) and is
 * only used if found; the discovered signature is logged so it can be confirmed.
 */
public final class ChakraLink {
    private ChakraLink() {}
    private static boolean tried, ready;
    private static Method initialized, pathway, amount, maximum, spend;
    private static Class<?> spendType;
    public static String status = "not checked";

    private static void init() {
        if (tried) return;
        tried = true;
        try {
            Class<?> api = Class.forName("net.narutomod.Chakra");
            initialized = api.getMethod("isInitialized", EntityPlayer.class);
            pathway = api.getMethod("pathway", EntityPlayer.class);
            Class<?> values = Class.forName("net.narutomod.Chakra$Pathway");
            amount = values.getMethod("getAmount");
            maximum = values.getMethod("getMax");
            for (Method m : values.getMethods()) {
                String n = m.getName().toLowerCase(java.util.Locale.ROOT);
                if (m.getParameterCount() != 1) continue;
                Class<?> t = m.getParameterTypes()[0];
                if (t != double.class && t != float.class && t != int.class) continue;
                if (n.contains("consume") || n.equals("use") || n.contains("drain") || n.contains("subtract") || n.contains("spend")) { spend = m; spendType = t; break; }
            }
            ready = true;
            status = "linked to net.narutomod.Chakra" + (spend == null ? " (read-only: no spend method found)" : " (spend via " + spend.getName() + "(" + spendType.getSimpleName() + "))");
        } catch (ReflectiveOperationException | LinkageError absent) {
            status = "chakra mod not present; using Vale resolve";
        }
        System.out.println("[valeorders bridge] " + status);
    }

    public static boolean available() { init(); return ready; }
    public static boolean canSpend() { init(); return ready && spend != null; }

    private static Object path(EntityPlayer p) throws ReflectiveOperationException {
        if (!Boolean.TRUE.equals(initialized.invoke(null, p))) return null;
        return pathway.invoke(null, p);
    }

    /** Current chakra, or NaN when unavailable. */
    public static double amount(EntityPlayer p) {
        if (!available() || p == null) return Double.NaN;
        try { Object v = path(p); return v == null ? Double.NaN : ((Number) amount.invoke(v)).doubleValue(); }
        catch (ReflectiveOperationException | RuntimeException e) { return Double.NaN; }
    }

    public static double max(EntityPlayer p) {
        if (!available() || p == null) return Double.NaN;
        try { Object v = path(p); return v == null ? Double.NaN : ((Number) maximum.invoke(v)).doubleValue(); }
        catch (ReflectiveOperationException | RuntimeException e) { return Double.NaN; }
    }

    /** Spends chakra if the link supports it and the player has enough; returns whether it was spent. */
    public static boolean spend(EntityPlayer p, double value) {
        if (!canSpend() || p == null || !(value > 0)) return false;
        double have = amount(p);
        if (!(have >= value)) return false;
        try {
            Object v = path(p);
            if (v == null) return false;
            Object arg = spendType == double.class ? (Object) value : spendType == float.class ? (Object) (float) value : (Object) (int) Math.ceil(value);
            spend.invoke(v, arg);
            double after = amount(p);
            return after < have - 1e-6;   // only trust the discovered method if chakra actually went down
        } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
}
