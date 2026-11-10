package village.encounters;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
public final class ClientProxy extends CommonProxy {
 public void renderers(){RenderingRegistry.registerEntityRenderingHandler(Actors.Mob.class,m->new Shape<Actors.Mob>(m));RenderingRegistry.registerEntityRenderingHandler(Actors.Boss.class,m->new Shape<Actors.Boss>(m));RenderingRegistry.registerEntityRenderingHandler(Actors.Companion.class,m->new Shape<Actors.Companion>(m));}
 static final class Shape<T extends Actors.Actor> extends RenderBiped<T>{Shape(RenderManager m){super(m,new ModelBiped(),.45F);}protected ResourceLocation getEntityTexture(T actor){return new ResourceLocation("minecraft","textures/entity/steve.png");}}
}
