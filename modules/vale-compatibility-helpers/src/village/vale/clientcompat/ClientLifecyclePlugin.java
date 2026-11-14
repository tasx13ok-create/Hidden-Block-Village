package village.vale.clientcompat;
import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
@IFMLLoadingPlugin.Name("Vale Client Lifecycle Compatibility")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"village.vale.clientcompat"})
public final class ClientLifecyclePlugin implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass(){return new String[]{"village.vale.clientcompat.ClientLifecycleTransformer","village.vale.clientcompat.NpcGuiThreadTransformer"};}
    @Override public String getModContainerClass(){return null;}
    @Override public String getSetupClass(){return null;}
    @Override public void injectData(Map<String,Object> data){}
    @Override public String getAccessTransformerClass(){return null;}
}
