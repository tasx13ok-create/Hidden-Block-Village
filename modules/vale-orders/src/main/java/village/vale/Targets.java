package village.vale;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;

/** Per-entity opt-in supports custom enemies without importing another mod's code. */
public final class Targets {
    public static final String KEY = "valeCombatTarget";
    private Targets() {}
    public static boolean hostile(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) return false;
        int policy = entity.getEntityData().getInteger(KEY);
        return policy > 0 || (policy == 0 && entity instanceof IMob);
    }
}
