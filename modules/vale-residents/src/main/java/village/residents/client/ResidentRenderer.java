package village.residents.client;

import net.minecraft.client.model.ModelVillager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import village.residents.EntityResident;
import village.residents.ResidentRole;

@SideOnly(Side.CLIENT)
public final class ResidentRenderer extends RenderLiving<EntityResident> {
    private static final ResourceLocation GUIDE = new ResourceLocation("minecraft", "textures/entity/villager/librarian.png");
    private static final ResourceLocation CLERK = new ResourceLocation("minecraft", "textures/entity/villager/farmer.png");
    private static final ResourceLocation MERCHANT = new ResourceLocation("minecraft", "textures/entity/villager/smith.png");

    public ResidentRenderer(RenderManager manager) { super(manager, new ModelVillager(0.0F), 0.4F); }
    @Override protected ResourceLocation getEntityTexture(EntityResident entity) {
        ResidentRole role = entity.role();
        return role == ResidentRole.ACADEMY ? GUIDE : role == ResidentRole.QUARTERMASTER ? MERCHANT : CLERK;
    }
}
