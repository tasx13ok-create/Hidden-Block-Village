package village.valerack;
import net.minecraft.entity.player.EntityPlayer;
public class CommonProxy{
    public void initialize(){}
    public Object screen(EntityPlayer p){return null;}
    public void snapshot(RackNetwork.Snapshot message,net.minecraftforge.fml.common.network.simpleimpl.MessageContext context){}
}
