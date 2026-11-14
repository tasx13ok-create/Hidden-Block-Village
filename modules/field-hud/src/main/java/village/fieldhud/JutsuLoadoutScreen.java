package village.fieldhud;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;

/** Six-slot assignment edits local preferences; no equip/cast packets are sent here. */
public final class JutsuLoadoutScreen extends GuiScreen {
    private final GuiScreen parent;
    private final String context;
    private final List<JutsuSlot> draft;
    private List<JutsuEntry> catalogue,filtered=Collections.emptyList();
    private GuiTextField search;
    private int selected,page,x,y,panelWidth,panelHeight,leftWidth,slotHeight,rowHeight,rows;
    private String query="",message="";
    private int focus=-1;
    private boolean keyboardFocus;
    public JutsuLoadoutScreen(GuiScreen parent){
        this.parent=parent;context=JutsuAccess.context();JutsuLoadout current=JutsuAccess.MODEL.loadout();
        draft=new ArrayList<>(current.slots);selected=current.selected;catalogue=JutsuAccess.MODEL.entries();
    }
    @Override public void initGui(){
        Keyboard.enableRepeatEvents(true);panelWidth=Math.min(500,width-12);panelHeight=Math.min(280,height-12);
        x=(width-panelWidth)/2;y=(height-panelHeight)/2;leftWidth=Math.min(182,panelWidth*39/100);
        slotHeight=Math.max(16,Math.min(28,(panelHeight-80)/6));rowHeight=slotHeight<22?18:24;
        rows=Math.max(1,(panelHeight-96)/rowHeight);
        search=new GuiTextField(1,fontRenderer,x+leftWidth+8,y+31,panelWidth-leftWidth-18,16);
        search.setMaxStringLength(64);search.setText(query);search.setCanLoseFocus(true);search.setFocused(focus<0);
        filter();
    }
    private JutsuEntry display(JutsuSlot slot){JutsuEntry actual=JutsuAccess.MODEL.find(slot.id);return actual==null?slot.unavailable():actual;}
    private void filter(){
        String needle=query.trim().toLowerCase(Locale.ROOT);List<JutsuEntry> next=new ArrayList<>();
        for(JutsuEntry entry:catalogue)if((entry.name+" "+entry.kind+" "+entry.school).toLowerCase(Locale.ROOT).contains(needle))next.add(entry);
        filtered=next;page=Math.max(0,Math.min(page,Math.max(0,(filtered.size()-1)/rows)));buttons();
    }
    private void buttons(){
        buttonList.clear();
        for(int i=0;i<JutsuLoadout.SIZE;i++)buttonList.add(new JutsuSlotButton(100+i,x+8,y+53+i*slotHeight,leftWidth-13,slotHeight-1,i,display(draft.get(i)),i==selected));
        for(int i=0;i<rows&&page*rows+i<filtered.size();i++)buttonList.add(new JutsuSlotButton(200+i,x+leftWidth+8,y+53+i*rowHeight,panelWidth-leftWidth-18,rowHeight-1,-1,filtered.get(page*rows+i),false));
        int footer=y+panelHeight-23;
        buttonList.add(new MenuInk.Button(400,x+8,footer,Math.max(64,leftWidth-13),17,"Clear slot"));
        int rightX=x+leftWidth+8,rightW=panelWidth-leftWidth-18;
        buttonList.add(new MenuInk.Button(401,rightX,footer,(rightW-4)/2,17,"Cancel"));
        GuiButton save=new MenuInk.Button(402,rightX+(rightW-4)/2+4,footer,rightW-(rightW-4)/2-4,17,"Save loadout");
        save.enabled=context!=null&&context.equals(JutsuAccess.context());buttonList.add(save);
        int pageY=y+53+rows*rowHeight;
        GuiButton previous=new MenuInk.Button(403,rightX,pageY,26,14,"<");previous.enabled=page>0;buttonList.add(previous);
        GuiButton next=new MenuInk.Button(404,rightX+rightW-26,pageY,26,14,">");next.enabled=(page+1)*rows<filtered.size();buttonList.add(next);
    }
    @Override protected void actionPerformed(GuiButton button)throws IOException{
        focus=button.id;search.setFocused(false);
        if(button.id>=100&&button.id<106){selected=button.id-100;message="Choose an action on the right";buttons();}
        else if(button.id>=200&&button.id<200+rows){draft.set(selected,JutsuSlot.from(filtered.get(page*rows+button.id-200)));message="Slot "+(selected+1)+" assigned";buttons();}
        else if(button.id==400){draft.set(selected,JutsuSlot.empty());message="Slot "+(selected+1)+" cleared";buttons();}
        else if(button.id==401)mc.displayGuiScreen(parent);
        else if(button.id==402){message=JutsuAccess.applyLoadout(context,new JutsuLoadout(draft,selected));if(message.isEmpty())mc.displayGuiScreen(parent);}
        else if(button.id==403){page--;buttons();}
        else if(button.id==404){page++;buttons();}
    }
    @Override public void updateScreen(){
        search.updateCursorCounter();
        if(catalogue!=JutsuAccess.MODEL.entries()){catalogue=JutsuAccess.MODEL.entries();filter();}
    }
    @Override protected void mouseClicked(int mouseX,int mouseY,int button)throws IOException{keyboardFocus=false;search.mouseClicked(mouseX,mouseY,button);if(search.isFocused())focus=-1;super.mouseClicked(mouseX,mouseY,button);}
    @Override protected void keyTyped(char character,int key)throws IOException{
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}
        if(key==Keyboard.KEY_F&&isCtrlKeyDown()){focus=-1;search.setFocused(true);return;}
        if(key==Keyboard.KEY_TAB){
            keyboardFocus=true;
            List<GuiButton> available=new ArrayList<>();for(GuiButton b:buttonList)if(b.enabled&&b.visible)available.add(b);
            int at=-1;for(int i=0;i<available.size();i++)if(available.get(i).id==focus)at=i;
            int next=at<0?(isShiftKeyDown()?available.size()-1:0):at+(isShiftKeyDown()?-1:1);if(next<0||next>=available.size()){focus=-1;search.setFocused(true);}else{focus=available.get(next).id;search.setFocused(false);}return;
        }
        if(!search.isFocused()&&(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE)){
            for(GuiButton b:buttonList)if(b.id==focus&&b.enabled&&b.visible){b.playPressSound(mc.getSoundHandler());actionPerformed(b);return;}
        }
        if(search.textboxKeyTyped(character,key)){query=search.getText();page=0;filter();}else super.keyTyped(character,key);
    }
    @Override public void drawScreen(int mouseX,int mouseY,float partialTicks){
        drawDefaultBackground();MenuInk.panel(x,y,panelWidth,panelHeight);MenuInk.header(x,y,panelWidth,"Technique loadout");
        drawString(fontRenderer,"Your slots",x+9,y+36,MenuInk.BRASS);search.drawTextBox();
        if(query.isEmpty()&&!search.isFocused())drawString(fontRenderer,fontRenderer.trimStringToWidth("Search attacks, weapons, schools",search.width-8),search.x+4,search.y+4,0x7E95A4);
        int pageY=y+53+rows*rowHeight;
        drawCenteredString(fontRenderer,(page+1)+" / "+Math.max(1,(filtered.size()+rows-1)/rows),x+leftWidth+8+(panelWidth-leftWidth-18)/2,pageY+3,MenuInk.MUTED);
        if(filtered.isEmpty())drawString(fontRenderer,JutsuAccess.fresh()?"No matching actions":"Waiting for the server",x+leftWidth+12,y+58,MenuInk.MUTED);
        for(GuiButton b:buttonList){if(b instanceof MenuInk.Button)((MenuInk.Button)b).focused=b.id==focus;if(b instanceof JutsuSlotButton)((JutsuSlotButton)b).focused=b.id==focus;}
        super.drawScreen(mouseX,mouseY,partialTicks);
        for(GuiButton button:buttonList)if(button instanceof JutsuSlotButton&&(button.isMouseOver()||keyboardFocus&&button.id==focus)){
            JutsuEntry entry=((JutsuSlotButton)button).entry;List<String> tip=new ArrayList<>();
            if(entry==null)tip.add("Empty shortcut");
            else{
                tip.add(entry.name);tip.add(entry.kind.equals("weapon")?"Activate to equip this carried weapon":entry.kind.equals("technique")?"Uses the matching held weapon":"Activate to use this attack");
                if(entry.cost>0)tip.add(SharedResourceBridge.cost(entry));
                if(entry.requiredLevel>0)tip.add("Level "+entry.requiredLevel);
                if(entry.itemCount>0)tip.add("Carried: "+entry.itemCount);
                if(!entry.enabled)tip.add(entry.blockReason());
            }
            boolean anchored=keyboardFocus&&button.id==focus;tip.add("Assignments do not move or consume items");drawHoveringText(tip,anchored?Math.min(width-14,button.x+button.width):mouseX,anchored?button.y:mouseY);break;
        }
        if(!message.isEmpty())drawCenteredString(fontRenderer,fontRenderer.trimStringToWidth(message,panelWidth-18),width/2,Math.min(height-9,y+panelHeight+2),0xC8DDD9);
    }
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void onGuiClosed(){Keyboard.enableRepeatEvents(false);}
}
