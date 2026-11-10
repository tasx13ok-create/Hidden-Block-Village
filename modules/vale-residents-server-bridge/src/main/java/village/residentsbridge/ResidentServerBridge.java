package village.residentsbridge;

import village.residents.ResidentChooseCommand;
import village.residents.ValeResidents;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.server.MinecraftServer;

/** Optional hybrid entry metadata for our level0 token command; no Bukkit binary dependency. */
@Mod(modid="residentserverbridge",name="Vale Residents Server Bridge",version="0.1.0",serverSideOnly=true,acceptableRemoteVersions="*",dependencies="required-after:valeresidents@[0.2.0,0.3.0)",acceptedMinecraftVersions="[1.12.2]")
public final class ResidentServerBridge {
    @Mod.EventHandler public void started(FMLServerStartedEvent e){install(FMLCommonHandler.instance().getMinecraftServerInstance());}
    public static void install(MinecraftServer server){
        if(!(server.getCommandManager().getCommands().get("residentchoose") instanceof ResidentChooseCommand))return;
        try{
            Object craft=Class.forName("org.bukkit.Bukkit").getMethod("getServer").invoke(null);if(craft==null)return;
            Class<?> mapType=Class.forName("org.bukkit.command.CommandMap"),commandType=Class.forName("org.bukkit.command.Command");
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());int changed=0;
            for(String getter:new String[]{"getCommandMap","getCraftCommandMap"}){
                Method method;try{method=craft.getClass().getMethod(getter);}catch(NoSuchMethodException absent){continue;}
                Object map=method.invoke(craft);
                for(String name:new String[]{"residentchoose","valeresidents:residentchoose","minecraft:residentchoose"}){
                    Object wrapper=mapType.getMethod("getCommand",String.class).invoke(map,name);
                    if(wrapper==null||!seen.add(wrapper)||!wrapper.getClass().getName().equals("org.bukkit.craftbukkit.v1_12_R1.command.ModCustomCommand"))continue;
                    if(!"residentchoose".equals(commandType.getMethod("getName").invoke(wrapper)))continue;
                    commandType.getMethod("setPermission",String.class).invoke(wrapper,new Object[]{null});changed++;
                }
            }
            ValeResidents.LOG.info("RESIDENT_PUBLIC_CHOICE_ENTRY: {} owned token-command wrapper(s); authoring permissions unchanged.",changed);
        }catch(ClassNotFoundException pureForge){/* Native Forge needs no hybrid entry adjustment. */}
        catch(ReflectiveOperationException unsupported){ValeResidents.LOG.warn("Hybrid token-command entry metadata could not be adapted on this kernel.",unsupported);}
    }
}
