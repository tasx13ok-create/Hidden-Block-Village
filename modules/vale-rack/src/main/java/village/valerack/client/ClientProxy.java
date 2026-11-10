package village.valerack.client;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import village.valerack.*;
public final class ClientProxy extends CommonProxy{
    @Override public void initialize(){MinecraftForge.EVENT_BUS.register(new RackClientApi.Events());}
    @Override public Object screen(EntityPlayer p){return new RackScreen(new RackContainer(p,RackData.of(p)));}
    @Override public void snapshot(RackNetwork.Snapshot message,net.minecraftforge.fml.common.network.simpleimpl.MessageContext context){Object connection=context.getClientHandler();Minecraft.getMinecraft().addScheduledTask(()->{if(Minecraft.getMinecraft().getConnection()==connection)RackClientApi.accept(message);});}
}
