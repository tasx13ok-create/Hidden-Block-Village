package village.valerack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
public final class RackGuiHandler implements IGuiHandler{
    @Override public Object getServerGuiElement(int id,EntityPlayer p,World w,int x,int y,int z){return id==0&&p.world==w&&p.isEntityAlive()&&!p.isSpectator()?new RackContainer(p,RackData.of(p)):null;}
    @Override public Object getClientGuiElement(int id,EntityPlayer p,World w,int x,int y,int z){return id==0?RackMod.PROXY.screen(p):null;}
}
