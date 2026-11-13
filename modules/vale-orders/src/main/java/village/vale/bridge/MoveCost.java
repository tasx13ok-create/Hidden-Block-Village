package village.vale.bridge;

import net.minecraft.entity.player.EntityPlayer;
import village.vale.PlayerState;

/**
 * One place to pay for a move. With a linked chakra system (and preferChakra on) the move
 * spends chakra; otherwise, or if that spend is not possible, it spends Vale resolve.
 */
public final class MoveCost {
    private MoveCost() {}

    /** Pays {@code resolveCost} (Vale units). Returns false and spends nothing if the player cannot afford it. */
    public static boolean pay(EntityPlayer p, int resolveCost) {
        if (resolveCost <= 0) return true;
        if (Bridge.preferChakra && ChakraLink.canSpend() && ChakraLink.spend(p, resolveCost * Bridge.chakraPerResolve)) return true;
        int have = PlayerState.resolve(p);
        if (have < resolveCost) return false;
        PlayerState.setResolve(p, have - resolveCost);
        return true;
    }
}
