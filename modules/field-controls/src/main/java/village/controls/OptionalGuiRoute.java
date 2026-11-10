package village.controls;

import java.lang.reflect.*;

/** Metadata-only discovery; optional mod state is accessed only after a click. */
public final class OptionalGuiRoute {
    private final Field singleton;
    private final Method settings,getUtility,open;
    private final Constructor<?> screen;
    private final Class<?> guiBase;
    private OptionalGuiRoute(Field singleton,Method settings,Constructor<?> screen,Method utility,Method open,Class<?> guiBase){this.singleton=singleton;this.settings=settings;this.screen=screen;getUtility=utility;this.open=open;this.guiBase=guiBase;}
    public static OptionalGuiRoute inspect(Class<?> api,Class<?> config,Class<?> settingsScreen,Class<?> guiBase)throws ReflectiveOperationException{
        Method utility=api.getMethod("getGuiUtil"),getter=config.getMethod("getGui");Field instance=config.getField("INSTANCE");
        if(!Modifier.isStatic(utility.getModifiers())||!Modifier.isStatic(instance.getModifiers()))throw new NoSuchMethodException("Unsupported optional GUI API");
        Method open=utility.getReturnType().getMethod("openScreen",guiBase);
        Constructor<?> constructor=null;
        if(!guiBase.isAssignableFrom(getter.getReturnType())){
            if(settingsScreen==null||!guiBase.isAssignableFrom(settingsScreen))throw new NoSuchMethodException("Optional settings screen is unavailable");
            constructor=settingsScreen.getConstructor(getter.getReturnType());
        }
        return new OptionalGuiRoute(instance,getter,constructor,utility,open,guiBase);
    }
    public void open()throws ReflectiveOperationException{
        Object state=settings.invoke(singleton.get(null));
        Object target=screen==null?state:screen.newInstance(state);
        if(!guiBase.isInstance(target))throw new IllegalStateException("The optional mod did not provide a settings screen");
        open.invoke(getUtility.invoke(null),target);
    }
}
