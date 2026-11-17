package village.immersion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client camera and screen feel:
 *  - Hit-stop: on a heavy or finishing blow the camera holds still for 2-3 frames, then kicks.
 *  - Guard break and stagger: a smaller kick.
 *  - Landing roll: sneaking into a long landing pushes you forward with a quick forward dip.
 *  - Low health: a heartbeat that quickens and a dark edge vignette pulsing with it.
 *  - Chakra exhaustion: heavy breathing and a faint grey edge.
 * Camera changes only touch the rendered view, never the player's real aim.
 */
@SideOnly(Side.CLIENT)
final class ImpactFx {
    private static long hitAt = -1, rollAt = -1, tiredAt = -1;
    private static float strength, side = 1;
    private static boolean captured;
    private static float heldYaw, heldPitch, heldRoll;
    private int heartbeatIn;
    private static long beatAt = -1;

    static void impact(float s, boolean hold) {
        if (!Config.hitStop) return;
        strength = Math.max(0.05f, Math.min(1f, s));
        hitAt = System.nanoTime() / 1_000_000L;
        captured = !hold;   // no hold: skip straight to the kick
        side = Minecraft.getMinecraft().world != null && Minecraft.getMinecraft().world.rand.nextBoolean() ? 1 : -1;
        if (!hold) hitAt -= ImmersionMath.holdMs(strength);
    }
    static void exhausted() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        tiredAt = System.nanoTime() / 1_000_000L;
        mc.getSoundHandler().playSound(new PositionedSoundRecord(SoundEvents.ENTITY_PLAYER_BREATH.getSoundName(), SoundCategory.PLAYERS, 0.35f, 0.85f + mc.world.rand.nextFloat() * 0.1f, false, 0, ISound.AttenuationType.NONE, 0, 0, 0));
    }

    @SubscribeEvent public void camera(EntityViewRenderEvent.CameraSetup e) {
        long now = System.nanoTime() / 1_000_000L;
        if (hitAt >= 0) {
            long since = now - hitAt, hold = ImmersionMath.holdMs(strength);
            if (since < hold) {
                if (!captured) { captured = true; heldYaw = e.getYaw(); heldPitch = e.getPitch(); heldRoll = e.getRoll(); }
                e.setYaw(heldYaw); e.setPitch(heldPitch); e.setRoll(heldRoll);
            } else {
                double k = ImmersionMath.kick(strength, since, hold);
                if (k == 0 && since > hold + 200) hitAt = -1;
                e.setPitch((float) (e.getPitch() - k));
                e.setRoll((float) (e.getRoll() + k * 0.5 * side));
            }
        }
        if (rollAt >= 0) {
            double t = (now - rollAt) / 380.0;
            if (t >= 1) rollAt = -1;
            else e.setPitch((float) (e.getPitch() + 28 * Math.sin(Math.PI * t)));
        }
    }

    @SubscribeEvent public void fall(LivingFallEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Config.landingRoll || e.getEntityLiving() != mc.player || !e.getEntityLiving().world.isRemote) return;
        EntityPlayerSP p = mc.player;
        if (!ImmersionMath.roll(p.isSneaking(), e.getDistance())) return;
        double yaw = Math.toRadians(p.rotationYaw);
        p.motionX += -Math.sin(yaw) * 0.45; p.motionZ += Math.cos(yaw) * 0.45;
        rollAt = System.nanoTime() / 1_000_000L;
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Config.lowHealth) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.isGamePaused() || mc.player.isCreative() || mc.player.isSpectator()) return;
        float d = ImmersionMath.danger(mc.player.getHealth(), mc.player.getMaxHealth());
        if (d <= 0) { heartbeatIn = 0; return; }
        if (--heartbeatIn > 0) return;
        heartbeatIn = ImmersionMath.heartbeatGap(d);
        float vol = 0.35f + 0.35f * d;
        ISound lub = new PositionedSoundRecord(SoundEvents.BLOCK_NOTE_BASEDRUM.getSoundName(), SoundCategory.PLAYERS, vol, 0.5f, false, 0, ISound.AttenuationType.NONE, 0, 0, 0);
        ISound dub = new PositionedSoundRecord(SoundEvents.BLOCK_NOTE_BASEDRUM.getSoundName(), SoundCategory.PLAYERS, vol * 0.7f, 0.45f, false, 0, ISound.AttenuationType.NONE, 0, 0, 0);
        SoundFx.mark(lub); SoundFx.mark(dub);
        mc.getSoundHandler().playSound(lub);
        mc.getSoundHandler().playDelayedSound(dub, 5);
        beatAt = System.nanoTime() / 1_000_000L;
    }

    /** Current low-health danger for the local player (0 when off or healthy). */
    static float danger() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!Config.lowHealth || mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) return 0;
        return ImmersionMath.danger(mc.player.getHealth(), mc.player.getMaxHealth());
    }

    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Post e) {
        if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.gameSettings.hideGUI) return;
        long now = System.nanoTime() / 1_000_000L;
        float d = danger();
        if (d > 0) {
            double pulse = beatAt < 0 ? 0 : Math.max(0, 1 - (now - beatAt) / 400.0);
            edge(e.getResolution(), (int) (55 * d + 35 * d * pulse), 0x2A0306, 26);
        }
        if (tiredAt >= 0 && now - tiredAt < 2200) edge(e.getResolution(), (int) (40 * (1 - (now - tiredAt) / 2200.0)), 0x3A3F46, 20);
    }

    /** Soft edge band: alpha fades from the screen edge inward over {@code band} pixels. */
    private static void edge(ScaledResolution r, int alpha, int rgb, int band) {
        if (alpha <= 0) return;
        int w = r.getScaledWidth(), h = r.getScaledHeight();
        for (int i = 0; i < band; i++) {
            int a = (int) (alpha * (1 - i / (double) band) * (1 - i / (double) band));
            if (a <= 0) continue;
            int c = (Math.min(255, a) << 24) | rgb;
            Gui.drawRect(i, i, w - i, i + 1, c); Gui.drawRect(i, h - i - 1, w - i, h - i, c);
            Gui.drawRect(i, i + 1, i + 1, h - i - 1, c); Gui.drawRect(w - i - 1, i + 1, w - i, h - i - 1, c);
        }
    }
}
