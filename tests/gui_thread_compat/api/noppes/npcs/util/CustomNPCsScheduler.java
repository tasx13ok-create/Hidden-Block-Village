package noppes.npcs.util;
/** Original isolated fixture; never included in a game artifact. */
public final class CustomNPCsScheduler {
    public static int calls;
    public static void runTack(Runnable task){calls++;}
}
