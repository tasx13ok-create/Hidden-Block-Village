package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

/** The loadout editor uses the same recessed slots and 16px marks as the live bar. */
public final class JutsuSlotButton extends GuiButton {
    public final JutsuEntry entry;
    public final boolean selected;
    public boolean focused;
    private final int slot;
    public JutsuSlotButton(int id,int x,int y,int w,int h,int slot,JutsuEntry entry,boolean selected){
        super(id,x,y,w,h,entry==null?"Empty":entry.name);this.slot=slot;this.entry=entry;this.selected=selected;
    }
    @Override public void drawButton(Minecraft mc,int mouseX,int mouseY,float partialTicks){
        if(!visible)return;hovered=mouseX>=x&&mouseY>=y&&mouseX<x+width&&mouseY<y+height;
        MenuInk.row(x,y,width,height,selected||hovered||focused);
        if(selected)Gui.drawRect(x,y+3,x+2,y+height-3,MenuInk.BRASS);
        if(focused)Gui.drawRect(x+3,y+height-2,x+width-3,y+height-1,MenuInk.TEAL);
        int iconX=x+(slot>=0?17:5),iconY=y+(height-16)/2,textY=y+(height-8)/2;
        if(slot>=0)mc.fontRenderer.drawStringWithShadow(String.valueOf(slot+1),x+5,textY,MenuInk.TEAL);
        if(entry!=null)JutsuRail.icon(entry,iconX,iconY,255,0);
        else SkinDraw.outline(iconX+2,iconY+2,12,12,3,.7,0x65758D9C);
        int labelX=iconX+21;
        mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(displayString,Math.max(0,x+width-labelX-5)),labelX,textY,entry==null?MenuInk.MUTED:entry.enabled?MenuInk.INK:0xFF9AAEBB);
        GlStateManager.color(1,1,1,1);
    }
}
