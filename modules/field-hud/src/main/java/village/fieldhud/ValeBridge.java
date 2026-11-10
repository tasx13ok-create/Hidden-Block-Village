package village.fieldhud;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;

/** Optional owner-only client cache adapter, agreed with Vale Orders schema 1. */
public final class ValeBridge {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static final ValeSnapshotReader READER=new ValeSnapshotReader();
    private static Method getter;
    private static boolean attempted,disabled,primed;
    private static Object previous,player,world;
    private static long lastSequence;
    private static ValeSnapshotReader.State state;
    public static double confirmedAt=-1000;
    private ValeBridge() {}
    public static ValeSnapshotReader.State current() { return state; }
    public static boolean hasFreshLocal() {
        if(state==null||getter==null||disabled||MC.player==null||MC.world==null)return false;
        try { return READER.read(getter.invoke(null),MC.player.getUniqueID(),MC.player.dimension)!=null; }
        catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable) { return false; }
    }
    public static int resourceRows() { return (ChakraBridge.current()==null?0:1)+(state==null?0:1); }
    private static void reset() { state=null;previous=null;primed=false;lastSequence=0;confirmedAt=-1000; }
    public static void tick() {
        if(player!=MC.player||world!=MC.world) { player=MC.player;world=MC.world;reset(); }
        if(MC.player==null||MC.world==null||!MC.player.isEntityAlive()) { reset();return; }
        if(!attempted) {
            attempted=true;
            try { getter=Class.forName("village.vale.client.HudSnapshots").getMethod("getLocal"); }
            catch(ReflectiveOperationException|LinkageError absent) { disabled=true; }
        }
        if(disabled)return;
        try {
            Object raw=getter.invoke(null);
            if(raw==null) { reset();return; }
            if(raw==previous)return;
            previous=raw;state=READER.read(raw,MC.player.getUniqueID(),MC.player.dimension);
            if(state==null) { primed=false;return; }
            if(primed&&state.castSequence>lastSequence&&state.serverTick>=state.lastCastServerTick&&state.serverTick-state.lastCastServerTick<=40) {
                confirmedAt=MC.player.ticksExisted;
                System.out.println("FIELD_HUD_VALE_CAST: sequence="+state.castSequence+" art="+state.lastCastArtId);
            }
            lastSequence=state.castSequence;primed=true;
        } catch(ReflectiveOperationException|RuntimeException|LinkageError error) {
            disabled=true;reset();System.out.println("FIELD_HUD_VALE_DISABLED: "+error.getClass().getSimpleName());
        }
    }
}
