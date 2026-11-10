package village.vale.arsenal;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Optional survival middle-click. Native use remains the configurable fallback. */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid="valeorders",value=Side.CLIENT)
public final class ClawClient {
    private static boolean apiChecked;
    private static Method hudOwnsInput;
    private ClawClient() {}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void mouse(MouseEvent event) {
        if(event.isCanceled()||event.getButton()!=2||!event.isButtonstate())return;
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.player==null||mc.world==null||mc.currentScreen!=null||!mc.inGameHasFocus||mc.isGamePaused()
                ||!mc.player.isEntityAlive()||mc.player.isSpectator()||mc.player.capabilities.isCreativeMode
                ||mc.player.isHandActive()||hudOwnsInput())return;
        ItemStack held=mc.player.getHeldItemMainhand();
        if(!(held.getItem() instanceof ArsenalBlade)||((ArsenalBlade)held.getItem()).spec!=ArsenalSpec.CRESCENT_TALONS)return;
        // Other active bindings get their normal chance to consume this button.
        for(KeyBinding key:mc.gameSettings.keyBindings)if(key!=mc.gameSettings.keyBindPickBlock&&key.getKeyCode()==-98
                &&key.getKeyConflictContext().isActive()&&key.getKeyModifier().isActive(key.getKeyConflictContext()))return;
        if(!aimedLiving(mc))return;
        if(ClawNetwork.request(mc.player.dimension))event.setCanceled(true);
    }
    private static boolean hudOwnsInput() {
        if(!Loader.isModLoaded("fieldhud"))return false;
        if(!apiChecked) {
            apiChecked=true;
            try {hudOwnsInput=Class.forName("village.fieldhud.ClientApi").getMethod("ownsGameplayInput");}
            catch(ReflectiveOperationException|LinkageError unavailable) {return true;}
        }
        if(hudOwnsInput==null)return true;
        try {return Boolean.TRUE.equals(hudOwnsInput.invoke(null));}
        catch(ReflectiveOperationException|LinkageError unavailable) {return true;}
    }
    private static boolean aimedLiving(Minecraft mc) {
        Vec3d from=mc.player.getPositionEyes(1),to=from.add(mc.player.getLookVec().scale(ClawRules.RANGE));
        RayTraceResult block=mc.world.rayTraceBlocks(from,to,false,true,false);
        Vec3d stop=block==null?to:block.hitVec;
        double limit=from.squareDistanceTo(stop);int checked=0;
        for(EntityLivingBase target:mc.world.getEntitiesWithinAABB(EntityLivingBase.class,mc.player.getEntityBoundingBox()
                .expand(to.x-from.x,to.y-from.y,to.z-from.z).grow(.4))) {
            if(checked++>=64)break;
            if(target==mc.player||!target.isEntityAlive()||(target instanceof net.minecraft.entity.player.EntityPlayer&&((net.minecraft.entity.player.EntityPlayer)target).isSpectator())||!target.canBeCollidedWith())continue;
            RayTraceResult hit=target.getEntityBoundingBox().grow(.2).calculateIntercept(from,stop);
            if(hit!=null&&from.squareDistanceTo(hit.hitVec)<limit&&ClawRules.inRange(mc.player.getDistanceSq(target)))return true;
        }
        return false;
    }
}
