package village.vale.arsenal;

import net.minecraft.nbt.NBTTagCompound;

/** Server-world tick deadlines survive logout, player recreation and saves. */
final class ArsenalCooldowns {
    private ArsenalCooldowns() {}
    static long readyAt(NBTTagCompound state, ArsenalSpec spec) {
        return Math.max(state.getLong("ready"), state.getCompoundTag("cooldowns").getLong(spec.key));
    }
    static void record(NBTTagCompound state, ArsenalSpec spec, long now) {
        if (!state.hasKey("cooldowns", 10)) state.setTag("cooldowns", new NBTTagCompound());
        state.setLong("ready", deadline(now, ArsenalMath.SHARED_RECOVERY));
        state.getCompoundTag("cooldowns").setLong(spec.key, deadline(now, spec.cooldown));
    }
    private static long deadline(long now, int ticks) {
        return now > Long.MAX_VALUE - ticks ? Long.MAX_VALUE : now + ticks;
    }
    static int remaining(NBTTagCompound state, ArsenalSpec spec, long now) {
        long until = readyAt(state, spec);
        if (now >= until) return 0;
        long remaining = until - now;
        return remaining < 0 || remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
    }
    static void clearTransient(NBTTagCompound state) {
        for (String key : new String[]{"busy","flightMost","flightLeast","markUntil","markX","markY","markZ","markDimension",
                "guardUntil","guardKey","counter","counterUntil","sweepUntil","sweepNext","overdriveRecovery"}) state.removeTag(key);
    }
}
