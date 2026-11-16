package village.immersion;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.Sound;
import net.minecraft.entity.Entity;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Environmental sound. Underwater, every world sound is muffled (quieter, lower). In enclosed spaces
 * (most of the sky blocked above and around the listener) a quiet delayed echo plays after each
 * world sound. Music, UI and ambient-weather sounds are left alone; echoes are never re-echoed.
 */
@SideOnly(Side.CLIENT)
final class SoundFx {
    private final Set<ISound> ours = Collections.newSetFromMap(new WeakHashMap<>());
    private double enclosure;
    private long enclosureAt;

    @SubscribeEvent(priority = EventPriority.LOW)
    public void play(PlaySoundEvent e) {
        ISound s = e.getResultSound();
        Minecraft mc = Minecraft.getMinecraft();
        if (s == null || mc.player == null || mc.world == null || ours.contains(s)) return;
        SoundCategory cat = s.getCategory();
        if (cat == SoundCategory.MUSIC || cat == SoundCategory.RECORDS || cat == SoundCategory.MASTER || cat == SoundCategory.WEATHER) return;
        if (s.getAttenuationType() == ISound.AttenuationType.NONE && cat == SoundCategory.AMBIENT) return;
        s.createAccessor(mc.getSoundHandler());
        Sound resolved = s.getSound();
        if (resolved == null || resolved == net.minecraft.client.audio.SoundHandler.MISSING_SOUND) return;
        float vol = s.getVolume(), pitch = s.getPitch();
        String ns = s.getSoundLocation().getNamespace();
        if (Config.combatLayers && (ns.equals("narutomod") || ns.equals("valeorders") || ns.equals("hvbijuu") || ns.equals("kabutoaddon") || ns.equals("ahznbswordsplus")) && s.getAttenuationType() != ISound.AttenuationType.NONE) {
            pitch = ImmersionMath.vary(pitch, mc.world.rand.nextDouble());
            ISound varied = new PositionedSoundRecord(s.getSoundLocation(), cat, vol, pitch, false, 0, s.getAttenuationType(), s.getXPosF(), s.getYPosF(), s.getZPosF());
            ours.add(varied);
            e.setResultSound(varied);
            s = varied;
            if (ImmersionMath.heavy(vol)) {   // low weight tail just after the hit
                ISound tail = new PositionedSoundRecord(net.minecraft.init.SoundEvents.ENTITY_GENERIC_EXPLODE.getSoundName(), cat, vol * 0.18f, 0.55f, false, 0, s.getAttenuationType(), s.getXPosF(), s.getYPosF(), s.getZPosF());
                ours.add(tail);
                mc.getSoundHandler().playDelayedSound(tail, 2);
            }
        }
        Entity cam = mc.getRenderViewEntity() == null ? mc.player : mc.getRenderViewEntity();
        if (Config.underwaterSound && cam.isInsideOfMaterial(Material.WATER)) {
            float[] m = ImmersionMath.underwater(vol, pitch);
            ISound muffled = new PositionedSoundRecord(s.getSoundLocation(), cat, m[0], m[1], false, 0, s.getAttenuationType(), s.getXPosF(), s.getYPosF(), s.getZPosF());
            ours.add(muffled);
            e.setResultSound(muffled);
            return;
        }
        if (Config.caveEcho && s.getAttenuationType() != ISound.AttenuationType.NONE) {
            double enc = enclosure(mc, cam);
            if (enc > 0.55) {
                ISound echo = new PositionedSoundRecord(s.getSoundLocation(), cat, ImmersionMath.echoVolume(vol, enc), pitch * 0.97f, false, ImmersionMath.echoDelay(enc), s.getAttenuationType(), s.getXPosF(), s.getYPosF(), s.getZPosF());
                ours.add(echo);
                mc.getSoundHandler().playDelayedSound(echo, ImmersionMath.echoDelay(enc));
            }
        }
    }

    /** Share of probe columns around the listener that have a solid roof within 12 blocks (cached 1 s). */
    private double enclosure(Minecraft mc, Entity cam) {
        long now = mc.world.getTotalWorldTime();
        if (now - enclosureAt < 20) return enclosure;
        enclosureAt = now;
        int covered = 0, total = 0;
        BlockPos base = new BlockPos(cam.posX, cam.posY + cam.getEyeHeight(), cam.posZ);
        for (int dx = -4; dx <= 4; dx += 4) for (int dz = -4; dz <= 4; dz += 4) {
            total++;
            for (int dy = 1; dy <= 12; dy++) {
                BlockPos p = base.add(dx, dy, dz);
                if (mc.world.getBlockState(p).isOpaqueCube()) { covered++; break; }
            }
        }
        boolean sky = mc.world.canSeeSky(base);
        enclosure = sky ? 0 : covered / (double) total;
        return enclosure;
    }
}
