package village.fieldhud;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

/** Checks the actual optional R-routing contract without invoking its callback. */
public final class JutsuControlsCapability {
    private JutsuControlsCapability(){}
    public static boolean supported(boolean installed,ClassLoader loader){
        if(!installed)return true;
        try{
            Class<?> type=Class.forName("village.controls.FocusedCast",false,loader);
            Method method=type.getMethod("consume",ClassLoader.class,Consumer.class);
            return method.getReturnType()==Boolean.TYPE&&Modifier.isStatic(method.getModifiers())&&Modifier.isPublic(method.getModifiers());
        }catch(ReflectiveOperationException|LinkageError|SecurityException absent){return false;}
    }
}
