package village.vale;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;

public final class Bosses {
    public static final String[] KEYS = {"sentinel", "duelist", "sage", "colossus"};
    public static final String[] NAMES = {"Basalt Sentinel", "Reed Duelist", "Lantern Sage", "Hollowstone Colossus"};
    public static final float[] HEALTH = {320, 280, 300, 600};
    private Bosses() {}
    public static void register(Object mod) {
        EntityRegistry.registerModEntity(new ResourceLocation(ValeOrders.ID, "trial_keeper"), TrialKeeper.class,
            "trial_keeper", 1, mod, 80, 3, true);
    }
}
