package narutodestinationvillages.compat.transform;
import java.util.Map;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
@IFMLLoadingPlugin.Name("Naruto Villages Training")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1002)
@IFMLLoadingPlugin.TransformerExclusions({"narutodestinationvillages.compat.transform."})
public final class TrainingPlugin implements IFMLLoadingPlugin {
 public String[] getASMTransformerClass(){return new String[]{"narutodestinationvillages.compat.transform.ChakraTrainingTransformer"};}
 public String getModContainerClass(){return null;}public String getSetupClass(){return null;}
 public void injectData(Map<String,Object> data){}public String getAccessTransformerClass(){return null;}
}
