package village.immersion;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Wind: in storms, up high, or in gusts over dry biomes, a few dust motes from the ground under them
 * drift with a wind direction that turns slowly through the day. Client particles only, capped at a
 * handful per tick and skipped when particles are set to Minimal.
 */
@SideOnly(Side.CLIENT)
final class WeatherFx {
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Config.weather) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.player;
        if (p == null || mc.world == null || mc.isGamePaused() || mc.gameSettings.particleSetting == 2) return;
        BlockPos at = new BlockPos(p);
        Biome b = mc.world.getBiome(at);
        boolean dry = BiomeDictionary.hasType(b, BiomeDictionary.Type.SANDY) || BiomeDictionary.hasType(b, BiomeDictionary.Type.SAVANNA) || BiomeDictionary.hasType(b, BiomeDictionary.Type.MESA);
        long t = mc.world.getTotalWorldTime();
        boolean gust = dry && Math.sin(t / 900.0) > 0.55;
        boolean windy = mc.world.isThundering() || p.posY > 100 && mc.world.canSeeSky(at) || gust;
        if (!windy || p.isInsideOfMaterial(net.minecraft.block.material.Material.WATER)) return;
        double a = ImmersionMath.windAngle(mc.world.getWorldTime()), speed = mc.world.isThundering() ? 0.35 : 0.22;
        java.util.Random r = mc.world.rand;
        int n = mc.gameSettings.particleSetting == 1 ? 1 : 3;
        for (int i = 0; i < n; i++) {
            double x = p.posX + (r.nextDouble() - 0.5) * 24, z = p.posZ + (r.nextDouble() - 0.5) * 24;
            BlockPos top = mc.world.getHeight(new BlockPos(x, 0, z));
            if (Math.abs(top.getY() - p.posY) > 12) continue;
            IBlockState ground = mc.world.getBlockState(top.down());
            if (ground.getMaterial().isLiquid() || ground.getMaterial() == net.minecraft.block.material.Material.AIR) continue;
            mc.world.spawnParticle(EnumParticleTypes.BLOCK_DUST, x, top.getY() + 0.2 + r.nextDouble() * 1.6, z,
                    Math.cos(a) * speed, 0.01, Math.sin(a) * speed, net.minecraft.block.Block.getStateId(ground));
        }
    }
}
