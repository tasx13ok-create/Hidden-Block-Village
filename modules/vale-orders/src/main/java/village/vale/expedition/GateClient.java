package village.vale.expedition;

import net.minecraft.client.renderer.entity.RenderBoat;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Root calls only from its client proxy; common subscribers never reference it. */
@SideOnly(Side.CLIENT)
public final class GateClient {
    private GateClient(){}
    public static void register(){RenderingRegistry.registerEntityRenderingHandler(GateSkiff.class,manager->new RenderBoat(manager));}
}
