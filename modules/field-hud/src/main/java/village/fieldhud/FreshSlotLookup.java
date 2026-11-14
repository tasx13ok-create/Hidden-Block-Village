package village.fieldhud;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Resolve the native private hit-test, retaining virtual GUI input gates and slot ordering. */
public final class FreshSlotLookup {
    private final Method method;
    public FreshSlotLookup(Class<?> owner,String...names){
        Method found=null;
        if(owner!=null)for(String name:names)try{
            found=owner.getDeclaredMethod(name,Integer.TYPE,Integer.TYPE);found.setAccessible(true);break;
        }catch(NoSuchMethodException|RuntimeException unsupported){found=null;}
        method=found;
    }
    public boolean available(){return method!=null;}
    public Object at(Object screen,int eventX,int eventY){
        if(method==null||screen==null)return null;
        try{return method.invoke(screen,eventX,eventY);}
        catch(IllegalAccessException|InvocationTargetException|RuntimeException unsupported){return null;}
    }
}
