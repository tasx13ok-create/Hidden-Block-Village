package village.standards.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import village.standards.EntityStandard;
import village.standards.StandardGeometry;
import village.standards.StandardVariant;

/** Original exact 5 x 5 panel mesh, lit separately at each of its 25 blocks. */
@SideOnly(Side.CLIENT)
public final class StandardRenderer extends Render<EntityStandard> {
    public StandardRenderer(RenderManager manager) { super(manager); shadowSize = 0; }
    @Override protected ResourceLocation getEntityTexture(EntityStandard entity) {
        StandardVariant variant = entity.variant();
        return variant == null ? null : variant.texture();
    }
    @Override public void doRender(EntityStandard entity, double x, double y, double z, float yaw, float partial) {
        float priorX = OpenGlHelper.lastBrightnessX, priorY = OpenGlHelper.lastBrightnessY;
        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        try {
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-entity.rotationYaw, 0, 1, 0);
            GL11.glEnable(GL11.GL_DEPTH_TEST); GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1, 1, 1, 1);
            StandardVariant variant = entity.variant();
            if (variant != null) { bindTexture(variant.texture()); GL11.glEnable(GL11.GL_TEXTURE_2D); }
            else GL11.glDisable(GL11.GL_TEXTURE_2D);
            for (int row = 0; row < 5; row++) for (int column = 0; column < 5; column++) {
                BlockPos sample = entity.anchor().up(row - 2).offset(entity.facing().rotateYCCW(), column - 2);
                int light = entity.world.getCombinedLight(sample, 0);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 65535, light >>> 16);
                double left = column - StandardGeometry.HALF, bottom = row - StandardGeometry.HALF;
                int color = variant == null ? (column == 2 || row == 2 ? 0x8a9497 : 0x333e42) : 0xffffff;
                face(left, bottom, left + 1, bottom + 1, StandardGeometry.HALF_DEPTH,
                    column / 5.0, 1 - row / 5.0, (column + 1) / 5.0, 1 - (row + 1) / 5.0, color);
            }
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            backing();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, priorX, priorY);
            GL11.glPopAttrib();
            GlStateManager.popMatrix();
        }
        super.doRender(entity, x, y, z, yaw, partial);
    }
    private static void face(double x0, double y0, double x1, double y1, double z,
            double u0, double v0, double u1, double v1, int rgb) {
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        vertex(buffer, x0,y0,z,u0,v0,rgb,0,0,1); vertex(buffer,x1,y0,z,u1,v0,rgb,0,0,1);
        vertex(buffer,x1,y1,z,u1,v1,rgb,0,0,1); vertex(buffer,x0,y1,z,u0,v1,rgb,0,0,1);
        Tessellator.getInstance().draw();
    }
    private static void backing() {
        double s = StandardGeometry.HALF, d = StandardGeometry.HALF_DEPTH;
        double[][] p = {{-s,-s,-d},{s,-s,-d},{s,s,-d},{-s,s,-d},{-s,-s,d},{s,-s,d},{s,s,d},{-s,s,d}};
        int[][] faces = {{0,3,2,1},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
        float[][] normals = {{0,0,-1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        for (int f = 0; f < faces.length; f++) for (int index : faces[f]) {
            double[] v = p[index]; float[] n = normals[f];
            vertex(buffer, v[0],v[1],v[2],0,0, f == 0 ? 0x302a23 : 0x625343,n[0],n[1],n[2]);
        }
        Tessellator.getInstance().draw();
    }
    private static void vertex(BufferBuilder b, double x, double y, double z, double u, double v,
            int rgb, float nx, float ny, float nz) {
        b.pos(x,y,z).tex(u,v).color((rgb >>> 16) & 255,(rgb >>> 8) & 255,rgb & 255,255).normal(nx,ny,nz).endVertex();
    }
}
