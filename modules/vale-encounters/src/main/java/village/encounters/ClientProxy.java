package village.encounters;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
public final class ClientProxy extends CommonProxy {
 public void renderers(){RenderingRegistry.registerEntityRenderingHandler(Actors.Mob.class,m->new Shape<Actors.Mob>(m));RenderingRegistry.registerEntityRenderingHandler(Actors.Boss.class,m->new Shape<Actors.Boss>(m));RenderingRegistry.registerEntityRenderingHandler(Actors.Companion.class,m->new Shape<Actors.Companion>(m));}
 static final class Shape<T extends Actors.Actor> extends RenderBiped<T>{Shape(RenderManager m){super(m,new ModelPlayer(0,false),.45F);}protected ResourceLocation getEntityTexture(T actor){return Skins.of(actor);}}
 /** Original 256x256 shinobi skins (UV-compatible with the 64-unit biped). Chosen per profile so each actor keeps one look. */
 static final class Skins{
  static final ResourceLocation ASH=t("ash_regent_boss"),FLINT=t("garran_flint_jonin"),EMBER=t("kaito_ember_genin"),SAGE=t("lantern_sage_dawn"),DUSK=t("rook_dusk_operative"),CURRENT=t("sayo_current_kunoichi");
  static ResourceLocation t(String n){return new ResourceLocation("valeencounters","textures/entity/"+n+".png");}
  static ResourceLocation of(Actors.Actor a){
   String k;try{k=a.profileKey();}catch(RuntimeException missing){k="";}
   switch(ActorSkinRules.skin(a instanceof Actors.Boss?"boss":a instanceof Actors.Companion?"companion":"mob",k)){case "ash_regent_boss":return ASH;case "garran_flint_jonin":return FLINT;case "kaito_ember_genin":return EMBER;case "lantern_sage_dawn":return SAGE;case "sayo_current_kunoichi":return CURRENT;default:return DUSK;}
  }
 }
}
