package village.vale.arsenal;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod.EventBusSubscriber(modid="valeorders")
public final class ArsenalEvents {
    private ArsenalEvents() {}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event) {
        if(event.phase==TickEvent.Phase.END&&event.player instanceof EntityPlayerMP&&!event.player.world.isRemote)ArsenalLogic.tick((EntityPlayerMP)event.player);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hurt(LivingHurtEvent event) {
        if(event.getEntityLiving() instanceof EntityPlayerMP&&!event.getEntityLiving().world.isRemote&&!event.getSource().isUnblockable())
            event.setAmount(ArsenalLogic.protect((EntityPlayerMP)event.getEntityLiving(),event.getAmount()));
    }
    // Keep the native event objects until hitEntity: later LOWEST subscribers can
    // still cancel, including after absorption was consumed by vanilla damage.
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void observeAttack(LivingAttackEvent event) {ArsenalLogic.observe(event);}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void observeHurt(LivingHurtEvent event) {ArsenalLogic.observe(event);}
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void observeDamage(LivingDamageEvent event) {ArsenalLogic.observe(event);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void observePotion(PotionEvent.PotionApplicableEvent event) {ClawCombat.observe(event);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {if(event.player instanceof EntityPlayerMP)ArsenalLogic.clear((EntityPlayerMP)event.player);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {if(event.player instanceof EntityPlayerMP)ArsenalLogic.clear((EntityPlayerMP)event.player);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {if(event.player instanceof EntityPlayerMP)ArsenalLogic.clear((EntityPlayerMP)event.player);}
    @SubscribeEvent public static void death(LivingDeathEvent event) {if(event.getEntityLiving() instanceof EntityPlayerMP)ArsenalLogic.clear((EntityPlayerMP)event.getEntityLiving());}
}
