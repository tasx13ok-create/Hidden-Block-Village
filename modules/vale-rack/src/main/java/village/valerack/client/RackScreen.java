package village.valerack.client;
import net.minecraft.client.gui.inventory.GuiContainer;
import village.valerack.RackContainer;
public final class RackScreen extends GuiContainer{
    public RackScreen(RackContainer container){super(container);xSize=194;ySize=180;}
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mx,int my){
        int x=guiLeft,y=guiTop;drawRect(x,y,x+xSize,y+ySize,0xff1b2428);drawRect(x+2,y+2,x+xSize-2,y+ySize-2,0xffc0a779);drawRect(x+4,y+4,x+xSize-4,y+ySize-4,0xffe1d1af);
        drawRect(x+15,y+28,x+179,y+29,0xffc0a779);drawRect(x+15,y+66,x+179,y+67,0xffc0a779);
        for(net.minecraft.inventory.Slot slot:inventorySlots.inventorySlots){int sx=x+slot.xPos,sy=y+slot.yPos;drawRect(sx-1,sy-1,sx+17,sy+17,0xff1b2428);drawRect(sx,sy,sx+16,sy+16,0xffc6b58f);}
        int selected=RackClientApi.selectedSlot();if(selected>=0){int sx=x+43+selected*18;drawRect(sx-1,y+53,sx+17,y+55,0xff9ddddc);}
    }
    @Override protected void drawGuiContainerForegroundLayer(int mx,int my){fontRenderer.drawString("Combat hotbar",16,13,0x1b2428);fontRenderer.drawString("Hold combat key 3s",16,57,0x1b2428);fontRenderer.drawString("Inventory",16,74,0x1b2428);}
    @Override public void drawScreen(int mx,int my,float partial){drawDefaultBackground();super.drawScreen(mx,my,partial);renderHoveredToolTip(mx,my);}
}
