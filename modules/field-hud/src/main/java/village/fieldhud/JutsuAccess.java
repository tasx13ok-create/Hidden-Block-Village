package village.fieldhud;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/** Optional server-owned ability/equipment API, with local six-slot preferences. */
public final class JutsuAccess {
    private static final Minecraft MC=Minecraft.getMinecraft();
    public static final JutsuRailModel MODEL=new JutsuRailModel();
    private static final JutsuSnapshotReader READER=new JutsuSnapshotReader();
    private static Method getter,legacyGetter,activate,equip;
    private static boolean attempted,available,fresh,reported,saveAttempted;
    private static Object player,world,previous;
    private static String context;
    private static JutsuLoadoutStore store;
    private JutsuAccess(){}
    public static void configure(File directory){store=new JutsuLoadoutStore(new File(directory,"fieldhud-loadouts.json"));}
    public static double time(){return MC.player==null?0:MC.player.ticksExisted+MC.getRenderPartialTicks();}
    public static boolean fresh(){return fresh;}
    public static boolean supported(){return available;}
    public static String context(){return context;}
    private static String identity(){
        if(MC.player==null||MC.world==null)return null;
        try{
            String location;
            if(MC.isIntegratedServerRunning()&&MC.getIntegratedServer()!=null)
                location="world:"+new File(new File(MC.gameDir,"saves"),MC.getIntegratedServer().getFolderName()).getCanonicalPath();
            else if(MC.getCurrentServerData()!=null)location="server:"+MC.getCurrentServerData().serverIP.trim().toLowerCase(Locale.ROOT);
            else return null;
            return JutsuLoadoutStore.context(location,MC.player.getUniqueID());
        }catch(IOException unavailable){return null;}
    }
    public static String applyLoadout(String expectedContext,JutsuLoadout value){
        if(context==null||!context.equals(expectedContext)||MC.player==null||MC.world==null)return "The world changed; reopen this loadout";
        if(store==null)return "Loadout storage is unavailable";
        try{store.put(context,value);MODEL.restore(value);return "";}
        catch(IOException failure){return failure.getMessage()==null?"Could not save the loadout":failure.getMessage();}
    }
    public static void tick(){
        if(player!=MC.player||world!=MC.world){
            player=MC.player;world=MC.world;previous=null;fresh=false;MODEL.reset();context=identity();saveAttempted=false;
            JutsuLoadout saved=store==null||context==null?null:store.get(context);if(saved!=null){MODEL.restore(saved);saveAttempted=true;}
        }
        if(MC.player==null||MC.world==null||!MC.player.isEntityAlive()||MC.player.isSpectator()){fresh=false;MODEL.reset();previous=null;return;}
        if(!MODEL.initialized()&&store!=null&&context!=null){JutsuLoadout saved=store.get(context);if(saved!=null)MODEL.restore(saved);}
        if(!attempted){
            attempted=true;
            try{
                Class<?> snapshots=Class.forName("village.vale.client.AbilitySnapshots"),controls=Class.forName("village.vale.client.AbilityControls");
                getter=legacyGetter=snapshots.getMethod("getLocal");activate=controls.getMethod("requestActivate",String.class);available=activate.getReturnType()==Boolean.TYPE;
                try{
                    Method loadout=snapshots.getMethod("getLoadout"),weapon=controls.getMethod("requestEquip",String.class);
                    if(weapon.getReturnType()==Boolean.TYPE){getter=loadout;equip=weapon;}
                }catch(NoSuchMethodException legacy){/* 0.3.1 ability-only compatibility */}
            }catch(ReflectiveOperationException|LinkageError absent){available=false;}
        }
        if(!available)return;
        try{
            Object raw=getter.invoke(null);
            // A 0.3.2 client can connect to a legacy server with only schema1.
            // Saved weapon shortcuts stay unavailable until schema2 supplies them.
            if(raw==null&&getter!=legacyGetter)raw=legacyGetter.invoke(null);
            if(raw==null){fresh=false;return;}
            if(raw==previous){fresh=true;return;}
            JutsuSnapshotReader.State value=READER.read(raw,MC.player.getUniqueID(),MC.player.dimension);
            fresh=value!=null&&MODEL.accept(value.entries,value.selected,value.serverTick,MC.player.ticksExisted,HudConfig.motion);
            if(fresh){
                previous=raw;
                if(MODEL.initialized()&&!saveAttempted&&context!=null&&store!=null){
                    saveAttempted=true;
                    try{store.put(context,MODEL.loadout());}catch(IOException failure){System.out.println("FIELD_HUD_LOADOUT_SAVE: "+failure.getClass().getSimpleName());}
                }
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){
            fresh=false;if(!reported){reported=true;System.out.println("FIELD_HUD_JUTSU_UNAVAILABLE: "+failure.getClass().getSimpleName());}
        }
    }
    public static boolean request(JutsuEntry entry){
        if(!fresh||entry==null||MODEL.find(entry.id)!=entry)return false;
        Method action=entry.kind.equals("weapon")?equip:activate;if(action==null)return false;
        try{return Boolean.TRUE.equals(action.invoke(null,entry.id));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure){fresh=false;return false;}
    }
}
