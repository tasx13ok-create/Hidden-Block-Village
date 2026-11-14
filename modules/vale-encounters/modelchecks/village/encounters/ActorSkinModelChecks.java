package village.encounters;
import net.minecraft.client.model.ModelPlayer;
/** Real prepared API model construction, no renderer/window/GL invocation. */
public final class ActorSkinModelChecks {
 public static void main(String[] args){ModelPlayer model=new ModelPlayer(0,false);int checks=0;
  if(model.textureWidth!=64||model.textureHeight!=64)throw new AssertionError("64x64 native skin UV required");checks++;
  if(model.bipedLeftArm==null||model.bipedRightArm==null||model.bipedLeftLeg==null||model.bipedRightLeg==null||model.bipedBodyWear==null)throw new AssertionError("asymmetric skin and outerwear model required");checks++;
  model.setVisible(false);if(model.bipedBody.showModel||model.bipedHead.showModel||model.bipedBodyWear.showModel)throw new AssertionError("native visibility contract");checks++;
  model.setVisible(true);if(!model.bipedBody.showModel||!model.bipedHead.showModel||!model.bipedBodyWear.showModel)throw new AssertionError("native visibility restore");checks++;
  System.out.println("ACTOR_SKIN_REAL_MODEL_API_PASS checks="+checks);
 }
}
