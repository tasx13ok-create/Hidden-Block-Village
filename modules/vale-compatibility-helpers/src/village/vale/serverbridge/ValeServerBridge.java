package village.vale.serverbridge;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.config.Configuration;
import village.vale.ValeCommand;

/** Original optional server adapter. No Bukkit classes or upstream code bundled. */
@Mod(modid="valeserverbridge",name="Vale Server Bridge",version="0.2.0",dependencies="required-after:valeorders",acceptedMinecraftVersions="[1.12.2]",serverSideOnly=true,acceptableRemoteVersions="*")
public final class ValeServerBridge {
    private boolean publicCrates;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event){Configuration config=new Configuration(event.getSuggestedConfigurationFile());config.load();publicCrates=config.getBoolean("publicCrateAccess","compatibility",false,"Give ordinary players access/open/preview defaults only; explicit permission-manager rules still apply. No crate administration.");if(config.hasChanged())config.save();}
    @Mod.EventHandler public void started(FMLServerStartedEvent event){
        MinecraftServer server=FMLCommonHandler.instance().getMinecraftServerInstance();
        if(!(server.getCommandManager().getCommands().get("vale") instanceof ValeCommand))return;
        try{
            Class<?> bukkit=Class.forName("org.bukkit.Bukkit");Object craft=bukkit.getMethod("getServer").invoke(null);if(craft==null)return;
            Class<?> mapType=Class.forName("org.bukkit.command.CommandMap"),commandType=Class.forName("org.bukkit.command.Command");
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());int changed=0;
            for(String getter:Arrays.asList("getCommandMap","getCraftCommandMap")){
                Method method;try{method=craft.getClass().getMethod(getter);}catch(NoSuchMethodException absent){continue;}
                Object map=method.invoke(craft);
                for(String alias:Arrays.asList("vale","valeorders:vale","minecraft:vale")){
                    Object wrapper=mapType.getMethod("getCommand",String.class).invoke(map,alias);
                    if(wrapper==null||!seen.add(wrapper)||!wrapper.getClass().getName().equals("org.bukkit.craftbukkit.v1_12_R1.command.ModCustomCommand"))continue;
                    if(!"vale".equals(commandType.getMethod("getName").invoke(wrapper)))continue;
                    // This removes only the hybrid wrapper's duplicate public-entry gate.
                    // It grants no permission nodes and leaves native admin level4 checks intact.
                    commandType.getMethod("setPermission",String.class).invoke(wrapper,new Object[]{null});changed++;
                }
            }
            org.apache.logging.log4j.LogManager.getLogger("ValeServerBridge").info("VALE_PUBLIC_BUKKIT_ENTRY: "+changed+" owned public command wrappers adapted; no permission nodes granted.");
            if(publicCrates)crateDefaults(bukkit);
        }catch(ClassNotFoundException pureForge){/* Bukkit is optional. */}
        catch(ReflectiveOperationException ex){org.apache.logging.log4j.LogManager.getLogger("ValeServerBridge").warn("Optional public-command wrapper adapter unavailable for this server; native Forge checks remain active.",ex);}
    }
    private void crateDefaults(Class<?> bukkit)throws ReflectiveOperationException{
        Object manager=bukkit.getMethod("getPluginManager").invoke(null);Class<?> pluginManager=Class.forName("org.bukkit.plugin.PluginManager"),permission=Class.forName("org.bukkit.permissions.Permission"),mode=Class.forName("org.bukkit.permissions.PermissionDefault");
        Object crates=pluginManager.getMethod("getPlugin",String.class).invoke(manager,"CrazyCrates");if(crates==null)return;
        Object enabled=mode.getField("TRUE").get(null);
        for(String name:Arrays.asList("crazycrates.access","crazycrates.open","crazycrates.preview")){
            Object node=pluginManager.getMethod("getPermission",String.class).invoke(manager,name);
            if(node==null){node=permission.getConstructor(String.class,mode).newInstance(name,enabled);pluginManager.getMethod("addPermission",permission).invoke(manager,node);}
            else permission.getMethod("setDefault",mode).invoke(node,enabled);
        }
        org.apache.logging.log4j.LogManager.getLogger("ValeServerBridge").info("VALE_PUBLIC_CRATE_DEFAULTS: access/open/preview enabled; no administration or wildcard permission.");
    }
}
