package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

/** Cursor transactions use the server's real inventory cursor, never client-supplied item/NBT. */
public final class CombatInventoryOverlay extends Gui {
    private static final Minecraft MC=Minecraft.getMinecraft();
    private Object screen;
    private int pressSlot=-1,pressButton=-1;
    private int deferredSlot=-1,deferredButton,pressTicket,deferredTicket,deferredWindow;
    private void clearIntent(){pressSlot=pressButton=deferredSlot=-1;pressTicket=deferredTicket=0;}
    @SubscribeEvent public void gui(GuiOpenEvent event){if(event.getGui()!=screen){clearIntent();screen=event.getGui();}}
    private static Layout.Box box(int w,int h){int row=Math.min(24,Math.max(18,(h-24)/6));return new Layout.Box(w-row-8,Math.max(12,(h-row*6)/2),row,row*6);}
    private static int slot(Layout.Box b,int x,int y){return x>=b.x&&x<b.x+b.w&&y>=b.y&&y<b.y+b.h?(y-b.y)/b.w:-1;}
    private boolean available(Object gui){return gui instanceof GuiInventory&&MC.player!=null&&MC.player.openContainer==MC.player.inventoryContainer&&RackAccess.ready()&&RackAccess.switchSupported();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void mouse(GuiScreenEvent.MouseInputEvent.Pre event){
        if(screen!=event.getGui()){screen=event.getGui();clearIntent();}
        if(!available(screen)){clearIntent();return;}
        int button=Mouse.getEventButton();if(button<0||button>1)return;
        // A new native click supersedes any older release awaiting acknowledgement.
        if(Mouse.getEventButtonState()){deferredSlot=-1;deferredTicket=0;}
        int x=Mouse.getEventX()*event.getGui().width/MC.displayWidth,y=event.getGui().height-Mouse.getEventY()*event.getGui().height/MC.displayHeight-1;
        int target=slot(box(event.getGui().width,event.getGui().height),x,y);
        if(RackAccess.pending()){
            if(!Mouse.getEventButtonState()&&button==pressButton&&pressSlot>=0&&target<0){net.minecraft.inventory.Slot hovered=((GuiInventory)screen).getSlotUnderMouse();if(hovered!=null){deferredSlot=hovered.slotNumber;deferredButton=button;deferredTicket=pressTicket;deferredWindow=MC.player.openContainer.windowId;}}
            if(!Mouse.getEventButtonState()&&button==pressButton)pressSlot=pressButton=-1;
            event.setCanceled(true);return;
        }
        if(Mouse.getEventButtonState()){
            pressSlot=target;pressButton=button;
            if(target>=0){event.setCanceled(true);pressTicket=RackAccess.requestCursor(target,MC.player.openContainer.windowId,button)?RackAccess.cursorTicket():0;}
        }else{
            if(target>=0){
                event.setCanceled(true);
                // Native inventory pickup occurs on press. Releasing that drag over
                // the rail deposits the existing server cursor without an outside drop.
                if(pressSlot<0&&pressButton==button&&!MC.player.inventory.getItemStack().isEmpty())RackAccess.requestCursor(target,MC.player.openContainer.windowId,button);
            }else if(pressSlot>=0&&button==pressButton){
                // A rail pickup can be released over a native inventory cell.
                net.minecraft.inventory.Slot hovered=((GuiInventory)screen).getSlotUnderMouse();
                if(hovered!=null&&RackAccess.cursorAccepted(pressTicket)&&!MC.player.inventory.getItemStack().isEmpty()){
                    MC.playerController.windowClick(MC.player.openContainer.windowId,hovered.slotNumber,button,net.minecraft.inventory.ClickType.PICKUP,MC.player);event.setCanceled(true);
                }
            }
            pressSlot=pressButton=-1;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void draw(GuiScreenEvent.DrawScreenEvent.Post event){
        if(!available(event.getGui())){clearIntent();return;}Layout.Box b=box(event.getGui().width,event.getGui().height);
        if(screen==event.getGui()&&deferredSlot>=0&&!RackAccess.pending()){
            int target=deferredSlot;deferredSlot=-1;
            if(deferredWindow==MC.player.openContainer.windowId&&RackAccess.cursorAccepted(deferredTicket)&&target<MC.player.openContainer.inventorySlots.size()&&!MC.player.inventory.getItemStack().isEmpty())MC.playerController.windowClick(MC.player.openContainer.windowId,target,deferredButton,net.minecraft.inventory.ClickType.PICKUP,MC.player);
        }
        GlStateManager.pushMatrix();
        try{
            GlStateManager.disableLighting();GlStateManager.disableDepth();
            for(int i=0;i<6;i++){
                int y=b.y+i*b.w;drawRect(b.x,y,b.x+b.w,y+b.w,0xEF17242C);drawRect(b.x+1,y+1,b.x+b.w-1,y+b.w-1,0xFF796D57);
                drawRect(b.x+2,y+2,b.x+b.w-2,y+b.w-2,0xFF25343A);
                ItemStack stack=RackAccess.combatActive()?MC.player.inventory.mainInventory.get(i):RackAccess.renderStack(i);
                RenderHelper.enableGUIStandardItemLighting();GlStateManager.enableDepth();
                int x=b.x+(b.w-16)/2,iy=y+(b.w-16)/2;MC.getRenderItem().renderItemAndEffectIntoGUI(stack,x,iy);MC.getRenderItem().renderItemOverlays(MC.fontRenderer,stack,x,iy);
                RenderHelper.disableStandardItemLighting();GlStateManager.disableDepth();
            }
            MC.fontRenderer.drawStringWithShadow("Combat",Math.max(2,b.x-8),b.y-11,0xFF9DDDDC);
            int target=slot(b,event.getMouseX(),event.getMouseY());
            if(target>=0){
                ItemStack cursor=MC.player.inventory.getItemStack();
                if(!cursor.isEmpty()){
                    RenderHelper.enableGUIStandardItemLighting();GlStateManager.enableDepth();GlStateManager.translate(0,0,200);
                    MC.getRenderItem().renderItemAndEffectIntoGUI(cursor,event.getMouseX()-8,event.getMouseY()-8);MC.getRenderItem().renderItemOverlays(MC.fontRenderer,cursor,event.getMouseX()-8,event.getMouseY()-8);
                }else{
                    ItemStack stored=RackAccess.combatActive()?MC.player.inventory.mainInventory.get(target):RackAccess.renderStack(target);
                    if(!stored.isEmpty())event.getGui().drawHoveringText(stored.getTooltip(MC.player,MC.gameSettings.advancedItemTooltips?net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED:net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL),event.getMouseX(),event.getMouseY());
                }
            }
        }finally{RenderHelper.disableStandardItemLighting();GlStateManager.disableLighting();GlStateManager.enableDepth();GlStateManager.color(1,1,1,1);GlStateManager.popMatrix();}
    }
}
