package net.minecraft.client;
import net.minecraft.client.gui.GuiScreen;
import java.util.concurrent.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Futures;
/** Original test double, never packaged in a production artifact. */
public class Minecraft {
    private static Minecraft instance;
    public Minecraft(){instance=this;}
    public static Minecraft getMinecraft(){return instance;}
    public final Thread owner=Thread.currentThread();public final ConcurrentLinkedQueue<Runnable> queue=new ConcurrentLinkedQueue<>();
    public GuiScreen current;public int calls;
    public boolean isCallingFromMinecraftThread(){return Thread.currentThread()==owner;}
    public ListenableFuture<Object> addScheduledTask(Runnable task){queue.add(task);return Futures.immediateFuture(null);}
    public void displayGuiScreen(GuiScreen screen){if(!isCallingFromMinecraftThread())throw new AssertionError("off-thread display");calls++;current=screen;}
}
