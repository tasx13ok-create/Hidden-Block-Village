package village.immersion;

import java.lang.reflect.Method;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Pays movement costs through the Naruto Core bridge (village.vale.bridge.MoveCost.pay) by
 * reflection, so chakra is the real resource when Core and NarutoMod are present. Without Core,
 * movement costs a little food exhaustion instead. Never a hard dependency.
 */
final class ChakraCost {
    private ChakraCost() {}
    private static boolean tried;
    private static Method pay;

    static synchronized boolean pay(EntityPlayer p, int units) {
        if (units <= 0) return true;
        if (!tried) {
            tried = true;
            try { pay = Class.forName("village.vale.bridge.MoveCost").getMethod("pay", EntityPlayer.class, int.class); }
            catch (ReflectiveOperationException | LinkageError absent) { pay = null; }
        }
        if (pay != null) {
            try { return Boolean.TRUE.equals(pay.invoke(null, p, units)); }
            catch (ReflectiveOperationException | RuntimeException failed) { return false; }
        }
        if (p.getFoodStats().getFoodLevel() <= 6) return false;
        p.addExhaustion(0.4f * units);
        return true;
    }

}
