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

    /** Distant village life by day near residents (villagers or any mod NPC): chatter, smithing, doors. */
    private boolean village(Minecraft mc, EntityPlayer p, java.util.Random r, boolean day) {
        int residents = 0;
        for (net.minecraft.entity.Entity en : mc.world.getEntitiesWithinAABB(net.minecraft.entity.EntityLiving.class, p.getEntityBoundingBox().grow(40, 16, 40))) {
            net.minecraft.util.ResourceLocation id = net.minecraft.entity.EntityList.getKey(en);
            String k = id == null ? "" : id.getPath();
            if (en instanceof net.minecraft.entity.passive.EntityVillager || k.contains("villager") && !k.contains("zombie") || k.contains("npc")) residents++;
        }
        int kind = ImmersionMath.village(residents, day, r.nextDouble());
        if (kind == 0 || r.nextBoolean()) return false;
        cooldown = (int) (ImmersionMath.villageGap(residents) * 20 * (0.6 + r.nextDouble() * 0.8));
        double a = r.nextDouble() * Math.PI * 2, d = 14 + r.nextDouble() * 10;
        float x = (float) (p.posX + Math.cos(a) * d), y = (float) (p.posY + 1), z = (float) (p.posZ + Math.sin(a) * d);
        float v = Config.ambienceVolume;
        switch (kind) {
            case 1:
                for (int i = 0; i < 2 + r.nextInt(2); i++) cue(mc, SoundEvents.ENTITY_VILLAGER_AMBIENT, 0.1f * v, 0.8f + r.nextFloat() * 0.35f, x + r.nextFloat() * 3, y, z + r.nextFloat() * 3, i * (8 + r.nextInt(10)));
                break;
            case 2:
                for (int i = 0; i < 3; i++) cue(mc, SoundEvents.BLOCK_ANVIL_USE, 0.05f * v, 1.35f + r.nextFloat() * 0.1f, x, y, z, i * 9);
                break;
            default:
                cue(mc, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, 0.22f * v, 0.95f, x, y, z, 0);
                cue(mc, SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, 0.22f * v, 0.95f, x, y, z, 24 + r.nextInt(20));
        }
        return true;
    }
    private static void cue(Minecraft mc, SoundEvent ev, float vol, float pitch, float x, float y, float z, int delay) {
        PositionedSoundRecord s = new PositionedSoundRecord(ev.getSoundName(), SoundCategory.AMBIENT, vol, pitch, false, 0, net.minecraft.client.audio.ISound.AttenuationType.LINEAR, x, y, z);
        SoundFx.mark(s);
        if (delay <= 0) mc.getSoundHandler().playSound(s); else mc.getSoundHandler().playDelayedSound(s, delay);
    }

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
        if (Config.villageLife && !underground && village(mc, p, r, day)) return;
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
