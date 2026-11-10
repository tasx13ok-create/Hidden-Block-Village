package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

/** Optional server-owned inventory. Cached native copies are rendering data, never local storage. */
public final class RackAccess {
    private static final Minecraft MC=Minecraft.getMinecraft();
    public static final RackRailModel MODEL=new RackRailModel();
    private static Boolean loaded;
    private static boolean attempted,ready,reported;
    private static RackContract api;
    private static Object player,world;
    private static int dimension;
    private static ItemStack[] stacks=empty();
    private RackAccess(){}
    private static ItemStack[] empty(){ItemStack[] value=new ItemStack[RackRailModel.SIZE];java.util.Arrays.fill(value,ItemStack.EMPTY);return value;}
    public static boolean loaded(){if(loaded==null)loaded=Loader.isModLoaded("valerack");return loaded;}
    private static void ensure(){
        if(attempted||!loaded())return;attempted=true;
        try{api=new RackContract(Class.forName("village.valerack.client.RackClientApi",false,Loader.instance().getModClassLoader()),ItemStack.class);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure){unavailable(failure);}
    }
    private static boolean context(){return MC.player!=null&&MC.world!=null&&MC.player==player&&MC.world==world&&MC.player.dimension==dimension&&MC.player.isEntityAlive()&&!MC.player.isSpectator();}
    private static void unavailable(Throwable failure){
        ready=false;stacks=empty();MODEL.accept(false,0,JutsuAccess.time(),false);
        if(!reported){reported=true;System.out.println("FIELD_HUD_RACK_UNAVAILABLE: "+failure.getClass().getSimpleName());}
    }
    public static void tick(){
        if(MC.player!=player||MC.world!=world||(MC.player!=null&&MC.player.dimension!=dimension)){
            player=MC.player;world=MC.world;dimension=MC.player==null?0:MC.player.dimension;ready=false;stacks=empty();MODEL.reset();
        }
        if(!loaded())return;ensure();
        if(api==null||!context()){ready=false;stacks=empty();MODEL.accept(false,0,JutsuAccess.time(),false);return;}
        try{
            if(!Boolean.TRUE.equals(api.ready.invoke(null))){ready=false;stacks=empty();MODEL.accept(false,0,JutsuAccess.time(),false);return;}
            int selected=((Integer)api.selected.invoke(null)).intValue();ItemStack[] incoming=empty();
            for(int slot=0;slot<RackRailModel.SIZE;slot++){
                Object value=api.stack.invoke(null,slot);if(!(value instanceof ItemStack))throw new IllegalStateException("Missing native storage stack");
                incoming[slot]=((ItemStack)value).copy();
            }
            boolean verified=context()&&Boolean.TRUE.equals(api.ready.invoke(null));
            ready=MODEL.accept(verified,selected,JutsuAccess.time(),HudConfig.motion);
            stacks=ready?incoming:empty();
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){unavailable(failure);}
    }
    public static boolean ready(){return ready&&context();}
    public static ItemStack stack(int slot){return ready()&&slot>=0&&slot<RackRailModel.SIZE?stacks[slot].copy():ItemStack.EMPTY;}
    private static boolean requestReady(){
        if(!ready()||api==null)return false;
        try{if(Boolean.TRUE.equals(api.ready.invoke(null)))return true;}
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure){unavailable(failure);return false;}
        ready=false;stacks=empty();MODEL.accept(false,0,JutsuAccess.time(),false);return false;
    }
    public static boolean select(int slot){
        if(slot<0||slot>=RackRailModel.SIZE||!requestReady())return false;
        try{api.select.invoke(null,slot);return MODEL.select(slot,JutsuAccess.time(),HudConfig.motion);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure){unavailable(failure);return false;}
    }
    public static boolean requestEquip(int slot){
        if(slot<0||slot>=RackRailModel.SIZE||!requestReady())return false;
        try{return Boolean.TRUE.equals(api.equip.invoke(null,slot));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure){unavailable(failure);return false;}
    }
    public static boolean requestOpen(){
        if(!requestReady())return false;
        try{return Boolean.TRUE.equals(api.open.invoke(null));}
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure){unavailable(failure);return false;}
    }
}
