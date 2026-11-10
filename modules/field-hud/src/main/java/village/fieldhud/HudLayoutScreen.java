package village.fieldhud;

import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** Edits actual HUD coordinates while the live world and status plate render. */
public final class HudLayoutScreen extends GuiScreen {
    private final GuiScreen parent;
    public HudLayoutScreen() { this(new HudScreen()); }
    public HudLayoutScreen(GuiScreen parent) { this.parent=parent; }
    private boolean dragging;
    private int focus=-1;
    private int dx,dy;
    private ShinobiLayout layout() {
        return ShinobiHud.layout(width,height);
    }
    public void initGui() {
        buttonList.clear();int x=width/2-152,y=height-25;
        buttonList.add(new MenuInk.Button(0,x,y,72,20,"Reset"));
        buttonList.add(new MenuInk.Button(1,x+76,y,72,20,HudConfig.slim?"Width: Slim":"Width: Full"));
        buttonList.add(new MenuInk.Button(2,x+152,y,72,20,"Lower left"));
        buttonList.add(new MenuInk.Button(3,x+228,y,76,20,"Done"));
    }
    protected void actionPerformed(GuiButton button) {
        focus=button.id;
        if(button.id==0) { HudConfig.positionX=-1;HudConfig.positionY=-1;HudConfig.slim=false; }
        if(button.id==1)HudConfig.slim=!HudConfig.slim;
        if(button.id==2) { HudConfig.positionX=0;HudConfig.positionY=1000; }
        if(button.id==3) { mc.displayGuiScreen(parent);return; }
        HudConfig.save();initGui();
    }
    protected void mouseClicked(int x,int y,int button) throws IOException {
        super.mouseClicked(x,y,button);
        Layout.Box b=layout().status;
        if(button==0&&x>=b.x&&x<b.x+b.w&&y>=b.y&&y<b.y+b.h) { dragging=true;dx=x-b.x;dy=y-b.y; }
    }
    protected void mouseClickMove(int x,int y,int button,long elapsed) {
        if(!dragging||button!=0)return;
        Layout.Box b=layout().status;int m=HudConfig.margin,px=x-dx,py=y-dy;
        if(isShiftKeyDown()) { px=Math.round(px/4F)*4;py=Math.round(py/4F)*4; }
        HudConfig.positionX=HudPlacement.normalized(px,m,width-b.w-m);
        HudConfig.positionY=HudPlacement.normalized(py,m,height-74-b.h);
    }
    protected void mouseReleased(int x,int y,int button) { if(dragging) { dragging=false;HudConfig.save(); }super.mouseReleased(x,y,button); }
    public void onGuiClosed() { dragging=false;HudConfig.save(); }
    private void outline(Layout.Box b,int color) {
        drawRect(b.x-2,b.y-2,b.x+b.w+2,b.y-1,color);drawRect(b.x-2,b.y+b.h+1,b.x+b.w+2,b.y+b.h+2,color);
        drawRect(b.x-2,b.y-1,b.x-1,b.y+b.h+1,color);drawRect(b.x+b.w+1,b.y-1,b.x+b.w+2,b.y+b.h+1,color);
    }
    public void drawScreen(int x,int y,float partial) {
        int toolbarWidth=Math.min(320,width-8);MenuInk.panel((width-toolbarWidth)/2,height-72,toolbarWidth,70);
        for(GuiButton b:buttonList)if(b instanceof MenuInk.Button)((MenuInk.Button)b).focused=b.id==focus;
        ShinobiLayout layout=layout();Layout.Box status=layout.status,map=EyeMap.bounds(width,height,true);
        boolean overlap=map!=null&&status.overlaps(map);
        outline(status,overlap?0xFFE66B58:0xFF9CDED6);
        if(map!=null) { outline(map,0x889AB4BD);drawString(fontRenderer,fontRenderer.trimStringToWidth("Map position",map.w),map.x,map.y+map.h+4,0xB4C6CC); }
        for(Layout.Box reserved:ArmorHudBridge.bounds(width,height))outline(reserved,0x998CB7C3);
        drawCenteredString(fontRenderer,"Drag the status plate. Shift snaps to 4 pixels.",width/2,height-63,0xE9E1CE);
        String note=layout.statusConflict?"Space is tight. Use a smaller GUI scale.":overlap?"Status overlaps minimap. Drag it clear.":"Map movement: Xaero > Change Position";
        drawCenteredString(fontRenderer,note,width/2,height-51,overlap||layout.statusConflict?0xE68170:0xB9C8C8);
        super.drawScreen(x,y,partial);
    }
    public boolean doesGuiPauseGame() { return false; }
    protected void keyTyped(char typedChar,int keyCode) throws IOException {
        if(keyCode==1){mc.displayGuiScreen(parent);return;}
        if(keyCode==15){focus=focus<0?(isShiftKeyDown()?3:0):(focus+(isShiftKeyDown()?3:1))%4;return;}
        if(keyCode==28||keyCode==156||keyCode==57){for(GuiButton b:buttonList)if(b.id==focus){b.playPressSound(mc.getSoundHandler());actionPerformed(b);return;}}
        super.keyTyped(typedChar,keyCode);
    }
}
