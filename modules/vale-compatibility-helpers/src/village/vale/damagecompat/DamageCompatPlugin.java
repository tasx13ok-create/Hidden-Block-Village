package village.vale.damagecompat;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/** Restore the standard Forge final-damage hook only in a recognized hybrid server path. */
@IFMLLoadingPlugin.Name("Vale Forge Damage Compatibility")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"village.vale.damagecompat"})
public final class DamageCompatPlugin implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass(){return new String[]{"village.vale.damagecompat.DamageCompatTransformer"};}
    @Override public String getModContainerClass(){return null;}
    @Override public String getSetupClass(){return null;}
    @Override public void injectData(Map<String,Object> values){}
    @Override public String getAccessTransformerClass(){return null;}
}
