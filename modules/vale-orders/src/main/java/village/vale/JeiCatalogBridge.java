package village.vale;

import java.util.Collection;

/** Isolate the optional API call: JEI rejects an empty runtime removal batch. */
public final class JeiCatalogBridge {
    private JeiCatalogBridge() {}
    public static int remove(Object registry, Class<?> api, Class<?> ingredientType, Collection<?> hidden) throws ReflectiveOperationException {
        if (hidden.isEmpty()) return 0;
        api.getMethod("removeIngredientsAtRuntime", Class.class, Collection.class).invoke(registry, ingredientType, hidden);
        return hidden.size();
    }
}
