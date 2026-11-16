package village.immersion;

import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Sparse, quiet environmental cues placed around the listener (never on top of them), chosen from
 * biome, time of day, enclosure, nearby water and height. Uses vanilla sounds only, in the AMBIENT
 * category so the player's ambient volume slider controls it.
 */
@SideOnly(Side.CLIENT)
final class AmbienceFx {
    private int cooldown = 100;

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Config.ambience) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.player;
        if (p == null || mc.world == null || mc.isGamePaused() || --cooldown > 0) return;
        java.util.Random r = mc.world.rand;
        BlockPos at = new BlockPos(p);
        boolean underground = !mc.world.canSeeSky(at.up()) && p.posY < mc.world.getSeaLevel() + 4 && mc.world.getLightFor(net.minecraft.world.EnumSkyBlock.SKY, at) < 4;
        boolean water = false;
        for (int dx = -6; dx <= 6 && !water; dx += 3) for (int dz = -6; dz <= 6 && !water; dz += 3)
            water = mc.world.getBlockState(at.add(dx, -1, dz)).getMaterial() == Material.WATER;
        Biome b = mc.world.getBiome(at);
        boolean forest = BiomeDictionary.hasType(b, BiomeDictionary.Type.FOREST) || BiomeDictionary.hasType(b, BiomeDictionary.Type.JUNGLE);
        boolean day = mc.world.isDaytime() && !mc.world.isRaining();
        int layer = ImmersionMath.ambience(underground, water, forest, day, p.posY);
        int gap = ImmersionMath.ambienceGap(layer);
        cooldown = gap == 0 ? 100 : (int) (gap * 20 * (0.5 + r.nextDouble()));
        if (layer == 0 || p.isInsideOfMaterial(Material.WATER)) return;
        SoundEvent sound; float vol, pitch;
        switch (layer) {
            case 1: sound = SoundEvents.ENTITY_PARROT_AMBIENT; vol = 0.25f; pitch = 1.3f + r.nextFloat() * 0.5f; break;
            case 2: sound = SoundEvents.BLOCK_GRASS_STEP; vol = 0.12f; pitch = 1.6f + r.nextFloat() * 0.3f; break;
            case 3: sound = r.nextInt(3) == 0 ? SoundEvents.ENTITY_BAT_AMBIENT : SoundEvents.BLOCK_WATER_AMBIENT; vol = 0.2f; pitch = 0.8f + r.nextFloat() * 0.4f; break;
            case 4: sound = SoundEvents.BLOCK_WATER_AMBIENT; vol = 0.3f; pitch = 0.7f + r.nextFloat() * 0.2f; break;
            default: sound = SoundEvents.ITEM_ELYTRA_FLYING; vol = 0.06f; pitch = 0.6f + r.nextFloat() * 0.2f; break;
        }
        double a = r.nextDouble() * Math.PI * 2, d = 6 + r.nextDouble() * 8;
        mc.getSoundHandler().playSound(new PositionedSoundRecord(sound.getSoundName(), SoundCategory.AMBIENT, vol * Config.ambienceVolume, pitch, false, 0,
                net.minecraft.client.audio.ISound.AttenuationType.LINEAR, (float) (p.posX + Math.cos(a) * d), (float) (p.posY + 1 + r.nextDouble() * 4), (float) (p.posZ + Math.sin(a) * d)));
    }
}
