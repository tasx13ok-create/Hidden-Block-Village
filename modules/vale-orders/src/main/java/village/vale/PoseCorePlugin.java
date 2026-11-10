package village.vale;

import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

/** One client-model hook; no movement, input, combat or server entity changes. */
@IFMLLoadingPlugin.Name("Vale Run Pose")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions({"village.vale.PoseCorePlugin","village.vale.PoseTransformer"})
public final class PoseCorePlugin implements IFMLLoadingPlugin {
    @Override public String[] getASMTransformerClass(){return new String[]{"village.vale.PoseTransformer"};}
    @Override public String getModContainerClass(){return null;}
    @Override public String getSetupClass(){return null;}
    @Override public void injectData(Map<String,Object> data){}
    @Override public String getAccessTransformerClass(){return null;}
}
