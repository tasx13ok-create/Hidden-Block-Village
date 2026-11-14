package village.fieldhud;

import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;

/** Data-source integration only; the server owns every resource mutation. */
final class SharedResourceBridge {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static final SharedResourceReader READER=new SharedResourceReader();
    private static Method getter;private static boolean attempted,received;
    private static Object player,world,connection,previous;
    private static SharedResourceReader.State state;
    private SharedResourceBridge(){}
    static SharedResourceReader.State current(){return state;}
    static boolean authoritative(){return received;}
    static void tick(){
        if(player!=MC.player||world!=MC.world||connection!=MC.getConnection()){player=MC.player;world=MC.world;connection=MC.getConnection();previous=null;state=null;received=false;}
        if(MC.player==null||MC.world==null||!MC.player.isEntityAlive()||MC.player.isSpectator()){previous=null;state=null;return;}
        if(!attempted){attempted=true;try{getter=Class.forName("village.vale.client.ResourceSnapshots").getMethod("getLocal");}catch(ReflectiveOperationException|LinkageError absent){getter=null;}}
        if(getter==null)return;
        try{Object raw=getter.invoke(null);if(raw==null){previous=null;state=null;return;}if(raw==previous)return;previous=raw;state=READER.read(raw,MC.player.getUniqueID(),MC.player.getEntityId(),MC.player.dimension);if(state!=null)received=true;}
        catch(ReflectiveOperationException|RuntimeException|LinkageError unavailable){previous=null;state=null;}
    }
    static String cost(JutsuEntry entry){
        if(entry.cost<=0)return "";
        if(!entry.resource.equalsIgnoreCase("resolve"))return entry.cost+" "+entry.resource;
        if(state!=null)return SharedResourceReader.cost(state,entry.cost);
        return authoritative()?"Resource syncing":entry.cost+" resolve";
    }
}
