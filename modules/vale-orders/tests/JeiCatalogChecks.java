package village.vale;

import java.util.*;
import java.lang.reflect.InvocationTargetException;

public final class JeiCatalogChecks {
    public static final class Registry {
        int calls; Collection<?> received; boolean fail;
        public void removeIngredientsAtRuntime(Class<?> type, Collection<?> entries) {
            if (entries.isEmpty()) throw new IllegalArgumentException("ingredients must not be empty");
            if (fail) throw new IllegalStateException("fixture failure");
            if (type != String.class) throw new AssertionError("wrong ingredient type");
            calls++; received=entries;
        }
    }
    public static void main(String[] args) throws Exception {
        Registry r=new Registry();
        if(JeiCatalogBridge.remove(r,Registry.class,String.class,Collections.emptyList())!=0||r.calls!=0)throw new AssertionError("empty API call");
        List<String> entries=Arrays.asList("one","two");
        if(JeiCatalogBridge.remove(r,Registry.class,String.class,entries)!=2||r.calls!=1||r.received!=entries)throw new AssertionError("batch changed");
        r.fail=true; boolean failed=false;
        try {JeiCatalogBridge.remove(r,Registry.class,String.class,entries);}catch(InvocationTargetException e){failed=e.getCause() instanceof IllegalStateException;}
        if(!failed)throw new AssertionError("API failure swallowed");
        System.out.println("PASS: JEI empty/nonempty/type/identity/failure bridge checks");
    }
}
