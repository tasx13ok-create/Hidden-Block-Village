package village.fieldhud;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;

/** Native ContainerPlayer/recipe book/tooltips/input with an original paper background only. */
public final class NarutoInventoryScreen extends GuiInventory {
    private static final int SETTINGS=19631;
    private GuiButton settings;
    private InventoryThemeLayout theme(){return new InventoryThemeLayout(width,guiLeft,guiTop,xSize);}
    public NarutoInventoryScreen(EntityPlayer player){super(player);}
    @Override public void initGui(){
        super.initGui();settings=new NarutoInk.Button(SETTINGS,new Layout.Box(guiLeft+xSize-56,guiTop-19,56,16),"Settings");buttonList.add(settings);
    }
    @Override public void drawScreen(int mx,int my,float partial){
        if(settings!=null){InventoryThemeLayout t=theme();settings.visible=t.settings!=null;if(t.settings!=null){settings.x=t.settings.x;settings.y=t.settings.y;}settings.enabled=mc.player.inventory.getItemStack().isEmpty()&&(!RackAccess.loaded()||!RackAccess.pending());}
        super.drawScreen(mx,my,partial);
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partial,int mx,int my){
        if(!HudConfig.narutoInventory){super.drawGuiContainerBackgroundLayer(partial,mx,my);return;}
        NarutoInk.paper(guiLeft,guiTop,xSize,ySize);
        drawRect(guiLeft+26,guiTop+7,guiLeft+78,guiTop+76,0xFFB7AD94);drawRect(guiLeft+28,guiTop+9,guiLeft+76,guiTop+74,0xFFD0C3A4);
        NarutoInk.seal(guiLeft+43,guiTop+24,0xFFB0A184);
        for(Slot slot:inventorySlots.inventorySlots)NarutoInk.slot(guiLeft+slot.xPos,guiTop+slot.yPos);
        drawRect(guiLeft+7,guiTop+79,guiLeft+xSize-7,guiTop+80,0xFFB19B76);
        drawRect(guiLeft+7,guiTop+139,guiLeft+xSize-7,guiTop+140,0xFFB19B76);
        InventoryThemeLayout t=theme();if(t.header!=null){int hy=t.header.y;drawRect(guiLeft,hy,guiLeft+xSize-60,hy+16,0xED27333B);fontRenderer.drawString("SHINOBI LOADOUT",guiLeft+5,hy+4,0xFFF1DDB6,false);}
        drawEntityOnScreen(guiLeft+51,guiTop+75,30,guiLeft+51-mx,guiTop+25-my,mc.player);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mx,int my){
        if(!HudConfig.narutoInventory){super.drawGuiContainerForegroundLayer(mx,my);return;}
        fontRenderer.drawString("Crafting",97,8,NarutoInk.INK,false);
        fontRenderer.drawString("Field pack",8,73,NarutoInk.MUTED,false);
    }
    @Override protected void actionPerformed(GuiButton button)throws IOException{
        if(button==settings){if(mc.player.inventory.getItemStack().isEmpty()&&(!RackAccess.loaded()||!RackAccess.pending()))mc.displayGuiScreen(new HudScreen(this));return;}
        super.actionPerformed(button);
    }
}
