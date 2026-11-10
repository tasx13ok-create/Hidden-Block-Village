package village.vale.culinary;
import net.minecraft.entity.player.EntityPlayer;import net.minecraft.init.MobEffects;import net.minecraftforge.event.entity.living.PotionEvent;import net.minecraftforge.fml.common.Mod;import net.minecraftforge.fml.common.eventhandler.*;import village.vale.PlayerState;
public final class KitchenEvents {
 private KitchenEvents(){}
 @SubscribeEvent public static void poison(PotionEvent.PotionApplicableEvent e){if(e.getEntityLiving() instanceof EntityPlayer&&!e.getEntityLiving().world.isRemote&&e.getPotionEffect().getPotion()==MobEffects.POISON){EntityPlayer p=(EntityPlayer)e.getEntityLiving();if(p.world.getTotalWorldTime()<PlayerState.data(p).getLong("kitchenPoisonUntil"))e.setResult(Event.Result.DENY);}}
}
