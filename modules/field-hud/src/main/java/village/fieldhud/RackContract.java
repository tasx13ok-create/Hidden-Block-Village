package village.fieldhud;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Resolves the optional native storage API without initializing or invoking it. */
public final class RackContract {
    public final Method ready,stack,selected,select,equip,open;
    public RackContract(Class<?> api,Class<?> nativeStack)throws ReflectiveOperationException{
        ready=method(api,"ready",Boolean.TYPE);
        stack=method(api,"stack",nativeStack,Integer.TYPE);
        selected=method(api,"selectedSlot",Integer.TYPE);
        select=method(api,"select",Void.TYPE,Integer.TYPE);
        equip=method(api,"requestEquip",Boolean.TYPE,Integer.TYPE);
        open=method(api,"requestOpen",Boolean.TYPE);
    }
    private static Method method(Class<?> api,String name,Class<?> result,Class<?>...arguments)throws NoSuchMethodException{
        Method value=api.getMethod(name,arguments);
        if(!Modifier.isStatic(value.getModifiers())||value.getReturnType()!=result)throw new NoSuchMethodException("Unsupported storage API: "+name);
        return value;
    }
}
