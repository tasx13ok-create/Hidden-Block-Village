package village.fieldhud;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.common.Loader;

/** Mutates the one existing native binding; optional Controls uses its transactional profile save. */
final class NarutoBindings {
    private NarutoBindings(){}
    static KeyBinding find(String description){for(KeyBinding k:Minecraft.getMinecraft().gameSettings.keyBindings)if(k.getKeyDescription().equals(description))return k;return null;}
    static String conflict(KeyBinding target,int code,KeyModifier modifier){
        if(code==0)return "";
        for(KeyBinding k:Minecraft.getMinecraft().gameSettings.keyBindings)if(k!=target&&k.getKeyCode()==code&&(k.getKeyModifier()==modifier||k.getKeyModifier()==KeyModifier.NONE||modifier==KeyModifier.NONE)&&(k.getKeyConflictContext().conflicts(target.getKeyConflictContext())||target.getKeyConflictContext().conflicts(k.getKeyConflictContext())))return k.getKeyDescription();
        return "";
    }
    @SuppressWarnings({"unchecked","rawtypes"}) static void save(KeyBinding target,int code,KeyModifier modifier)throws Exception{
        Minecraft mc=Minecraft.getMinecraft();
        if(Loader.isModLoaded("fieldcontrols")){
            ClassLoader loader=Loader.instance().getModClassLoader();Class<?> bindings=Class.forName("village.controls.Bindings",true,loader);
            Object api=bindings.getConstructor().newInstance();Map<String,KeyBinding> keys=(Map<String,KeyBinding>)bindings.getField("keys").get(api);String id=null;
            for(Map.Entry<String,KeyBinding> entry:keys.entrySet())if(entry.getValue()==target){id=entry.getKey();break;}
            if(id==null)throw new IllegalStateException("Existing binding not in Controls catalog");
            Map draft=new LinkedHashMap((Map)bindings.getMethod("snapshot").invoke(api));
            Class<?> mod=Class.forName("village.controls.Model$Mod",true,loader),stroke=Class.forName("village.controls.Model$Stroke",true,loader);
            draft.put(id,stroke.getConstructor(int.class,mod).newInstance(code,Enum.valueOf((Class)mod,modifier.name())));
            Object catalog=bindings.getField("catalog").get(api);Class<?> session=Class.forName("village.controls.Model$Session",true,loader);
            Object next=session.getConstructor(catalog.getClass(),Map.class).newInstance(catalog,bindings.getMethod("snapshot").invoke(api));session.getField("draft").set(next,draft);session.getField("layout").set(next,"Custom");
            Class<?> mouse=Class.forName("village.controls.Bindings$MouseState",true,loader);Object mouseState=mouse.getConstructor(net.minecraft.client.settings.GameSettings.class).newInstance(mc.gameSettings);
            bindings.getMethod("save",session,mouse).invoke(api,next,mouseState);
        }else{target.setKeyModifierAndCode(modifier,code);KeyBinding.resetKeyBindingArrayAndHash();KeyBinding.unPressAllKeys();mc.gameSettings.saveOptions();}
    }
}
