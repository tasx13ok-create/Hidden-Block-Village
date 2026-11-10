package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** Shared glass material, restrained field insignia, Minecraft font and items. */
public final class PanelSkin extends Gui {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private static int ink(int rgb,int alpha) { return (Math.max(0,Math.min(255,alpha))<<24)|(rgb&0xFFFFFF); }
    public static void surface(Layout.Box b,int color,int alpha) {
        GlassMaterial.panel(b.x,b.y,b.w,b.h,7,color,alpha);
        SkinDraw.line(b.x+11,b.y+1.2,b.x+Math.min(b.w-11,37),b.y+1.2,.7,ink(color,alpha*130/255));
    }
    public static void pulse(Layout.Box b,int color,double amount) {
        if(amount<=0)return;
        boolean alpha=GL11.glIsEnabled(GL11.GL_ALPHA_TEST);GlStateManager.disableAlpha();
        SkinDraw.outline(b.x+.4,b.y+.4,b.w-.8,b.h-.8,7,.9,ink(color,(int)(amount*110)));
        if(alpha)GlStateManager.enableAlpha();
    }
    private static void badge(int x,int y,int color,int alpha) {
        SkinDraw.rounded(x,y,18,14,3,ink(0x9DAEB9,alpha*150/255));
        SkinDraw.rounded(x+.6,y+.6,16.8,12.8,2.4,ink(0x253544,alpha*220/255));
        SkinDraw.line(x+3,y+1,x+15,y+1,.6,ink(0xE9F1F5,alpha*140/255));
        drawRect(x+3,y+6,x+4,y+7,ink(0x3C4747,alpha));
        drawRect(x+14,y+6,x+15,y+7,ink(0x3C4747,alpha));
        // Three engraved tide/wind strokes; an original field-kit emblem.
        for(int row=0;row<3;row++) {
            drawRect(x+6,y+4+row*2,x+10,y+5+row*2,ink(0xB1C6CE,alpha));
            drawRect(x+10,y+3+row*2,x+12,y+4+row*2,ink(0xB1C6CE,alpha));
        }
        drawRect(x+6,y+12,x+12,y+13,ink(color,alpha));
    }
    public static void location(Layout.Box b,String name,String detail,int color,int alpha) {
        surface(b,color,alpha);badge(b.x+7,b.y+7,color,alpha);
        text(name,b.x+31,b.y+5,b.w-39,0xEEF4F5,alpha,true);
        text(detail,b.x+31,b.y+16,b.w-39,0xAEBEC9,alpha,true);
    }
    public static void simple(Layout.Box b,String title,String detail,int color,int alpha) {
        surface(b,color,alpha);
        text(title,b.x+9,b.y+5,b.w-18,0xEEF4F5,alpha,true);
        text(detail,b.x+9,b.y+16,b.w-18,0xB6C6D0,alpha,true);
    }
    public static void emblem(Layout.Box b,String title,String detail,int color,int alpha,SealArt.Mark mark) {
        surface(b,color,alpha);SealDraw.draw(mark,b.x+6,b.y+7,alpha,0);
        text(title,b.x+28,b.y+5,b.w-36,0xEEF4F5,alpha,true);
        text(detail,b.x+28,b.y+16,b.w-36,0xB6C6D0,alpha,true);
    }
    public static void arrival(Layout.Box b,String title,String detail,int color,int alpha) {
        if(alpha<8)return;
        int x=b.x,y=b.y,w=b.w,h=b.h;
        surface(b,color,alpha);
        badge(x+11,y+(h-14)/2,color,alpha);
        text(title,x+36,y+10,w-46,0xEEF4F5,alpha,true);
        text(detail,x+36,y+22,w-46,0xB5C6D0,alpha,true);
    }
    public static void durability(Layout.Box b,ItemStack stack,int remaining) {
        surface(b,0xD9AE65,255);
        GlStateManager.color(1,1,1,1);
        RenderHelper.enableGUIStandardItemLighting();
        MC.getRenderItem().renderItemAndEffectIntoGUI(stack,b.x+7,b.y+3);
        RenderHelper.disableStandardItemLighting();
        text(stack.getDisplayName(),b.x+28,b.y+3,b.w-36,0xEEE6D1,255,true);
        text("Durability "+remaining+"%",b.x+28,b.y+13,b.w-36,0xE5B76C,255,true);
    }
    public static void text(String value,int x,int y,int width,int color,int alpha,boolean shadow) {
        if(alpha<8)return;
        FontRenderer font=MC.fontRenderer;
        String trimmed=font.trimStringToWidth(value,Math.max(1,width));
        font.drawString(trimmed,x,y,ink(color,alpha),shadow);
    }
}
