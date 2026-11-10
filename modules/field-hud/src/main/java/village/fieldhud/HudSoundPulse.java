package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Small event-based loudness cue. Does not sample OpenAL buffers or claim FFT/bass analysis. */
public final class HudSoundPulse {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static double started=-1000,peak;
    public static void reset() { started=-1000;peak=0; }
    public static double value(double time) { return time<started?0:peak*Math.exp(-(time-started)/4); }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void sound(PlaySoundSourceEvent event) {
        // The earlier PlaySoundEvent runs before PositionedSound resolves its
        // Sound accessor; getVolume() is not safe at that stage in 1.12.
        ISound sound=event.getSound();
        if(sound==null||MC.player==null||MC.world==null)return;
        String id=sound.getSoundLocation().toString();
        if(!(id.contains("explode")||id.equals("narutomod:charging_chakra")||id.equals("narutomod:windecho")||id.contains("chidori")||id.contains("rasengan")))return;
        double volume=sound.getVolume()*MC.gameSettings.getSoundLevel(sound.getCategory())*MC.gameSettings.getSoundLevel(SoundCategory.MASTER);
        double distance=MC.player.getDistance(sound.getXPosF(),sound.getYPosF(),sound.getZPosF());
        if(sound.getAttenuationType()!=ISound.AttenuationType.NONE)volume*=Math.max(0,1-distance/Math.max(16,sound.getVolume()*16));
        final double strength=Math.max(0,Math.min(1,volume));
        if(strength<=.01)return;
        Runnable apply=()->{ if(MC.player!=null) { double time=MC.player.ticksExisted;peak=Math.max(strength,value(time));started=time; } };
        if(MC.isCallingFromMinecraftThread())apply.run();else MC.addScheduledTask(apply);
    }
}
