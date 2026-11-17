package village.immersion;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client feedback for movement: ripples while water-walking, dust and a scuff while wall-running. */
@SideOnly(Side.CLIENT)
final class MovementFx {
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.isGamePaused()) return;
        for (EntityPlayer p : mc.world.playerEntities) {
            int water = Movement.waterTicks(p), wall = Movement.wallTicks(p);
            if (Movement.ledgeAge(p) == 10) {   // the tick a ledge grab fires
                p.world.playSound(p.posX, p.posY, p.posZ, SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.5f, 1.3f, false);
                for (int i = 0; i < 6; i++) p.world.spawnParticle(EnumParticleTypes.CLOUD, p.posX + (p.world.rand.nextDouble() - 0.5) * 0.6, p.posY + 1.6, p.posZ + (p.world.rand.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            }
            java.util.Random r = p.world.rand;
            if (water > 0 && (water % 4 == 0 || p.distanceWalkedModified != p.prevDistanceWalkedModified)) {
                for (int i = 0; i < 3; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    p.world.spawnParticle(EnumParticleTypes.WATER_SPLASH, p.posX + Math.cos(a) * 0.4, Math.floor(p.posY) + 0.95, p.posZ + Math.sin(a) * 0.4, 0, 0.05, 0);
                }
                if (water % 12 == 0) p.world.playSound(p.posX, p.posY, p.posZ, SoundEvents.ENTITY_PLAYER_SWIM, SoundCategory.PLAYERS, 0.12f, 1.4f + r.nextFloat() * 0.2f, false);
            }
            if (wall > 0 && wall % 2 == 0) {
                p.world.spawnParticle(EnumParticleTypes.CLOUD, p.posX, p.posY + 0.1, p.posZ, 0, -0.02, 0);
                if (wall % 6 == 0) p.world.playSound(p.posX, p.posY, p.posZ, SoundEvents.BLOCK_STONE_STEP, SoundCategory.PLAYERS, 0.35f, 1.2f, false);
            }
        }
    }
}
