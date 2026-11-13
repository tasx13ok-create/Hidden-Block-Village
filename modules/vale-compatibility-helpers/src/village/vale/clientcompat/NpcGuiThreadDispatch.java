package village.vale.clientcompat;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Original client-only dispatch; never waits on the render thread from the worker. */
@SideOnly(Side.CLIENT)
public final class NpcGuiThreadDispatch {
    private NpcGuiThreadDispatch() {}
    public static void run(final Runnable task) {
        if (task == null) throw new NullPointerException("quest task");
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.isCallingFromMinecraftThread()) task.run();
        else minecraft.addScheduledTask(task);
    }
}
