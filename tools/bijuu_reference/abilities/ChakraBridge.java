package village.bijuu;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Loader;

/** Optional provider link. Only the server can debit an initialized ninja account. */
final class ChakraBridge {
    static boolean pay(EntityPlayer player) {
        if (!Loader.isModLoaded("narutomod")) return true;
        if (!(player instanceof EntityPlayerMP) || player.field_70170_p.field_72995_K) return false;
        try {
            return Native.pay(player);
        } catch (LinkageError unavailable) {
            return false;
        }
    }

    private static final class Native {
        static boolean pay(EntityPlayer player) {
            if (!net.narutomod.PlayerTracker.isNinja(player)) return false;
            net.narutomod.Chakra.PathwayPlayer account=net.narutomod.Chakra.pathway(player);
            if (account==null) return false;
            double max=account.getMax(), balance=account.getAmount();
            double cost=Math.max(10,max*.05);
            // The provider requires a positive remaining balance on a successful debit.
            return Double.isFinite(max) && max>0 && Double.isFinite(balance)
                && balance>cost && account.consume(cost,false);
        }
    }
}
