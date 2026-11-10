package village.vale.armor.client;

import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.lwjgl.opengl.GL11;
import village.vale.armor.*;

/** The actual ItemArmor model hook, with native armor-bone posing and a scoped crouch root. */
public final class OriginalArmorModels {
    private static final EnumMap<ArmorProfile,EnumMap<EntityEquipmentSlot,WardModel>> MODELS=new EnumMap<>(ArmorProfile.class);
    private OriginalArmorModels(){}
    public static ModelBiped get(ArmorProfile profile,EntityEquipmentSlot slot,ModelBiped original){
        EnumMap<EntityEquipmentSlot,WardModel> bySlot=MODELS.get(profile);if(bySlot==null){bySlot=new EnumMap<>(EntityEquipmentSlot.class);MODELS.put(profile,bySlot);}
        WardModel model=bySlot.get(slot);if(model==null){model=new WardModel(profile,slot);bySlot.put(slot,model);}
        model.setModelAttributes(original);return model;
    }
    static final class WardModel extends ModelBiped{
        final ArmorMesh mesh;final EntityEquipmentSlot slot;
        WardModel(ArmorProfile profile,EntityEquipmentSlot slot){super(1);this.mesh=OriginalModels.get(profile);this.slot=slot;}
        @Override public void render(Entity entity,float limbSwing,float limbSwingAmount,float age,float headYaw,float headPitch,float scale){
            setRotationAngles(limbSwing,limbSwingAmount,age,headYaw,headPitch,scale,entity);
            float partial=Minecraft.getMinecraft().getRenderPartialTicks();double seconds=(entity.ticksExisted+partial)/20.0;
            float flight=OriginalClientState.fold(entity,partial,OriginalClientState.flying(entity)||entity instanceof EntityPlayer&&((EntityPlayer)entity).capabilities.isFlying);
            float strike=OriginalClientState.strike(entity,partial);
            GL11.glPushMatrix();
            try{
                if(isChild){GL11.glScalef(.5f,.5f,.5f);GL11.glTranslatef(0,24*scale,0);}
                else if(entity.isSneaking())GL11.glTranslatef(0,.2f,0);
                TexturedArmorRenderer.render(mesh,this,slot,scale,seconds,Math.min(1,Math.abs(limbSwingAmount)),flight,strike);
            }finally{GL11.glPopMatrix();}
        }
    }
}
