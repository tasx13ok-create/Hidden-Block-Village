package village.vale.bridge;

import java.lang.reflect.Method;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Reflection-only adapter for base NarutoMod chakra (net.narutomod.Chakra), bound to the exact
 * public signatures observed for the pinned 0.3.1.beta jar:
 *   Chakra.pathway(EntityPlayer), PlayerTracker.isNinja(EntityPlayer),
 *   Pathway.getAmount()D, getMax()D, consume(DZ)Z, clear()V.
 * The float consume overloads (fraction of max) and the client-only isInitialized are never used
 * on the server. Spring/addon duplicate pools are not touched: base NarutoMod is the only provider.
 *
 * Status: ABSENT (NarutoMod not installed: callers may use the Vale resolve fallback),
 * UNSUPPORTED (installed but the API does not match: fail closed, no fallback), READY.
 */
public final class ChakraLink {
    private ChakraLink() {}
    public enum Status { UNCHECKED, ABSENT, UNSUPPORTED, READY }
    private static Status state = Status.UNCHECKED;
    private static Method pathway, isNinja, getAmount, getMax, consume, clear;
    public static String status = "not checked";

    static synchronized void init() {
        if (state != Status.UNCHECKED) return;
        Class<?> api;
        try { api = Class.forName("net.narutomod.Chakra"); }
        catch (ClassNotFoundException | LinkageError absent) { state = Status.ABSENT; status = "NarutoMod not installed: using Vale resolve"; log(); return; }
        try {
            pathway = api.getMethod("pathway", EntityPlayer.class);
            isNinja = Class.forName("net.narutomod.PlayerTracker").getMethod("isNinja", EntityPlayer.class);
            Class<?> path = Class.forName("net.narutomod.Chakra$Pathway");
            getAmount = path.getMethod("getAmount"); getMax = path.getMethod("getMax");
            consume = path.getMethod("consume", double.class, boolean.class); clear = path.getMethod("clear");
            if (getAmount.getReturnType() != double.class || getMax.getReturnType() != double.class || consume.getReturnType() != boolean.class || isNinja.getReturnType() != boolean.class)
                throw new NoSuchMethodException("unexpected return types");
            state = Status.READY;
            status = "linked to base NarutoMod chakra (consume(double,boolean), clear)";
        } catch (ReflectiveOperationException | LinkageError mismatch) {
            state = Status.UNSUPPORTED;
            status = "NarutoMod present but its chakra API does not match (" + mismatch.getClass().getSimpleName() + ": " + mismatch.getMessage() + "); chakra moves are blocked";
        }
        log();
    }
    private static void log() { System.out.println("[naruto bridge] " + status); }

    public static Status state() { init(); return state; }
    public static boolean available() { return state() == Status.READY; }
    public static boolean absent() { return state() == Status.ABSENT; }

    /** Server readiness: NarutoMod's own ninja identity. Never creates a pathway for a non-ninja. */
    public static boolean ready(EntityPlayer p) {
        if (!available() || p == null || p.world.isRemote) return false;
        try { return Boolean.TRUE.equals(isNinja.invoke(null, p)); } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }

    /** Pool for the player's current body, or null if not ready. Reacquire per action; never cache. */
    public static ChakraPool pool(EntityPlayer p) {
        if (!ready(p)) return null;
        final Object path;
        try { path = pathway.invoke(null, p); } catch (ReflectiveOperationException | RuntimeException e) { return null; }
        if (path == null) return null;
        return new ChakraPool() {
            public double amount() throws Exception { return (Double) getAmount.invoke(path); }
            public double max() throws Exception { return (Double) getMax.invoke(path); }
            public boolean consume(double d, boolean over) throws Exception { return (Boolean) consume.invoke(path, d, over); }
            public void clear() throws Exception { clear.invoke(path); }
            public Object identity() { return path; }
        };
    }

    /** {amount, max} for display, or null. */
    public static double[] view(EntityPlayer p) {
        ChakraPool pool = pool(p);
        if (pool == null) return null;
        try { return new double[] {pool.amount(), pool.max()}; } catch (Exception e) { return null; }
    }
}
