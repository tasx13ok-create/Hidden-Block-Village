package village.controls;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraftforge.fml.client.GuiModList;
import org.lwjgl.input.*;
import village.controls.SettingsModel.*;

/** Compact independently authored settings board over the existing native transaction port. */
public final class SettingsHub extends GuiScreen {
    static final String[] PAGES=SimpleNavigation.PAGES;
    final GuiScreen parent;NativeSettings backend;Session session;MenuLayout layout;
    String defaultsPage="Gameplay";String page="Gameplay",notice="",dialog="";GuiTextField search;
    final List<Def> visible=new ArrayList<>();Map<String,Object> resetPlan;Runnable pendingRoute;Def dragging,selected,hoveredDef;
    int offset,left,contentWidth,footer,listTop,rowHeight,capacity,focus=-1,ids,resetOffset;
    int boxX,boxY,boxW,boxH;boolean editedOnly,draggingScroll,keyboardMode;
    private final Map<Def,int[]> sliders=new LinkedHashMap<>();private final MenuFocus navigation=new MenuFocus();
    private final MenuDispatch dispatch=new MenuDispatch();
    private String contextTitle="",contextBody="";private int lastMx=-1,lastMy=-1;
    private MenuLayout.Box track;
    public SettingsHub(GuiScreen parent){this(parent,"Gameplay");}
    public SettingsHub(GuiScreen parent,String page){this.parent=parent;this.page=page.equals("More")||SimpleNavigation.isMainPage(page)?page:"Gameplay";}
    @Override public void initGui(){
        if(!dispatch.rebuildNow())return;
        super.initGui();if(backend==null){backend=new NativeSettings(mc);session=new Session(backend.defs,backend);}else session.refreshIfClean();
        Keyboard.enableRepeatEvents(true);rebuild();
    }
    private void measure(){layout=MenuLayout.simple(width,height);left=layout.content.x;contentWidth=layout.content.w;footer=layout.footer.y;rowHeight=layout.compact?26:28;listTop=layout.content.y+25+(page.equals("Interface")?26:0);capacity=Math.max(1,(layout.content.bottom()-listTop)/rowHeight);int x=left+7,y=listTop-20,w=Math.max(40,contentWidth-93);if(search!=null&&search.x==x&&search.y==y&&search.getWidth()==w)return;String query=search==null?"":search.getText();int cursor=search==null?0:search.getCursorPosition(),selection=search==null?0:search.getSelectionEnd();search=new GuiTextField(900,fontRenderer,x,y,w,12);search.setMaxStringLength(80);search.setText(query);search.setCursorPosition(cursor);search.setSelectionPos(selection);search.setEnableBackgroundDrawing(false);search.setTextColor(Surface.INK);}
    private Surface.Button button(int x,int y,int w,int h,String label,int style,String key,Runnable action){Surface.Button b=new Surface.Button(1000+ids++,x,y,w,h,label,style,action).key(key);buttonList.add(b);return b;}
    private void rebuild(){
        if(!dispatch.rebuildNow())return;
        measure();buttonList.clear();sliders.clear();ids=0;
        if(!dialog.isEmpty()){buildDialog();syncFocus();return;}
        for(int i=0;i<PAGES.length;i++){String p=PAGES[i];MenuLayout.Box box=SimpleNavigation.tab(layout,i);Surface.Button button=button(box.x,box.y,box.w,box.h,SimpleNavigation.LABELS[i],0,"page/"+p,()->{
            finishGesture();if(p.equals("Controls")){route(()->mc.displayGuiScreen(new ControlsScreen(parent)));return;}
            page=p;defaultsPage=p;offset=0;selected=null;search.setFocused(false);rebuild();
        });button.selected=page.equals(p);}
        MenuLayout.Box e=SimpleNavigation.essential(layout);Surface.Button essential=button(e.x,e.y,e.w,e.h,"Essential",1,"essential",()->route(()->{try{EssentialBridge.open(this);}catch(Exception ex){notice="Essential could not open. Use its native menu.";rebuild();}}));essential.enabled=EssentialBridge.available();essential.tooltip=EssentialBridge.status();
        if(page.equals("More"))buildTools();else buildSettings();
        int fy=layout.footer.y+3;
        button(layout.footer.x,fy,42,20,"Undo",1,"footer/undo",()->{finishGesture();session.undo();rebuild();}).enabled=session.canUndo();
        button(layout.footer.x+46,fy,44,20,"More...",0,"footer/more",()->{finishGesture();if(isSettingsPage())defaultsPage=page;page="More";offset=0;rebuild();}).selected=page.equals("More");
        button(layout.footer.right()-132,fy,62,20,"Apply",2,"footer/apply",()->save(false)).enabled=session.changes()>0;
        button(layout.footer.right()-66,fy,66,20,"Done",1,"footer/done",this::exit);
        syncFocus();
    }
    private boolean isSettingsPage(){return Arrays.asList("Gameplay","Video","Audio","Interface").contains(page);}
    private void buildOverview(){
        int cols=2,gap=6,rows=3,h=Math.max(14,(layout.content.h-(rows-1)*gap)/rows),w=(contentWidth-gap)/cols;
        String[] labels={"Gameplay","Video","Audio","Interface","Controls","More"};
        String[] tips={"Camera, sensitivity and movement preferences.","Native graphics, lighting and display options.","Native sound categories and subtitles.","GUI size, text and chat preferences.","Bindings, presets, saved profiles and mouse setup.","Actual HUD, mod, resource and advanced settings pages."};
        for(int i=0;i<labels.length;i++){String p=labels[i];Surface.Button b=button(left+i%cols*(w+gap),layout.content.y+i/cols*(h+gap),w,h,p,1,"overview/"+p,()->{
            if(p.equals("Controls"))route(()->mc.displayGuiScreen(new ControlsScreen(this)));else{page=p;defaultsPage=p;offset=0;selected=null;rebuild();}
        });b.detail=summary(p);b.tooltip=b.detail+". "+tips[i];}
    }
    private String summary(String p){if(p.equals("Gameplay"))return "FOV "+Math.round(((Number)session.draft.get("FOV")).doubleValue())+" / Look "+Math.round(((Number)session.draft.get("SENSITIVITY")).doubleValue()*200)+"%";if(p.equals("Video"))return "Chunks "+Math.round(((Number)session.draft.get("RENDER_DISTANCE")).doubleValue())+" / "+(Boolean.TRUE.equals(session.draft.get("ENABLE_VSYNC"))?"VSync":"FPS "+Math.round(((Number)session.draft.get("FRAMERATE_LIMIT")).doubleValue()));if(p.equals("Audio"))return "Master "+Math.round(((Number)session.draft.get("sound.master")).doubleValue()*100)+"%";if(p.equals("Interface"))for(Def d:backend.defs)if(d.id.equals("GUI_SCALE"))return "Scale "+d.format(session.draft.get(d.id));return p.equals("Controls")?FieldControls.store.data.layout+" layout":"Native and mod screens";}
    private static final String[] TOOLS={"HUD settings","HUD layout","Installed mods","Resource packs","Language","Skin customization","Advanced video / shaders","Advanced chat","Minecraft options"};
    private static final String[] TOOL_HELP={"Adjust the HUD or edit technique slots in HUD > Loadout.","Reposition the HUD.","Open Forge's mod list and available mod configuration pages.","Open Minecraft's resource-pack selector.","Open native language and font settings.","Open native skin-layer settings.","Open native video settings and installed OptiFine or shader extensions.","Open the complete native chat settings screen.","Open the full vanilla options screen, including the existing difficulty permissions."};
    private void buildTools(){
        button(left,layout.content.y,contentWidth,20,"Restore "+defaultsPage+" defaults...",1,"tool/defaults",()->{page=defaultsPage;resetPlan=session.defaults(page);resetOffset=0;dialog="reset";rebuild();}).tooltip="Review Game defaults before staging them. Other sections can be reset with their native settings.";

        int cols=contentWidth>=370?2:1,gap=6,h=layout.compact?23:28,rows=Math.max(1,(layout.content.h-26+gap)/(h+gap)),total=(TOOLS.length-2+cols-1)/cols,w=(contentWidth-(cols-1)*gap)/cols;
        offset=MenuLayout.clampOffset(offset,total,rows);capacity=rows;
        for(int r=0;r<rows;r++)for(int c=0;c<cols;c++){final int index=2+(r+offset)*cols+c;if(index>=TOOLS.length)continue;
            Surface.Button b=button(left+c*(w+gap),layout.content.y+26+r*(h+gap),w,h,TOOLS[index],1,"tool/"+index,()->route(()->tool(index)));b.tooltip=TOOL_HELP[index];
            if(index<2&&!HudBridge.available()){b.enabled=false;b.tooltip="Enter a world with Field HUD installed to use this tool.";}
        }
        track=new MenuLayout.Box(layout.content.right()+2,layout.content.y,3,layout.content.h);
    }
    private void tool(int index){
        try{
            if(index<2){HudBridge.open(this,index==1);return;}
            if(index==2){mc.displayGuiScreen(new GuiModList(this));return;}
            if(index==8){GuiOptions nativeOptions=new GuiOptions(this,mc.gameSettings);FieldControls.allowNative(nativeOptions);mc.displayGuiScreen(nativeOptions);return;}
            GuiScreen target=index==3?new GuiScreenResourcePacks(this):index==4?new GuiLanguage(this,mc.gameSettings,mc.getLanguageManager()):index==5?new GuiCustomizeSkin(this):index==6?new GuiVideoSettings(this,mc.gameSettings):new ScreenChatOptions(this,mc.gameSettings);mc.displayGuiScreen(target);
        }catch(Exception ex){Throwable cause=ex instanceof java.lang.reflect.InvocationTargetException&&ex.getCause()!=null?ex.getCause():ex;notice=cause.getMessage()==null?"That page is unavailable.":cause.getMessage();rebuild();}
    }
    private void route(Runnable next){finishGesture();if(session.changes()>0){pendingRoute=next;dialog="route";rebuild();}else next.run();}
    private void exit(){finishGesture();if(session.changes()>0){dialog="exit";rebuild();}else mc.displayGuiScreen(parent);}
    private void buildSettings(){
        if(page.equals("Interface")){
            int w=(contentWidth-6)/2;
            button(left,layout.content.y,w,20,"HUD settings",1,"hud/settings",()->route(()->tool(0))).enabled=HudBridge.available();
            button(left+w+6,layout.content.y,w,20,"Position HUD",1,"hud/layout",()->route(()->tool(1))).enabled=HudBridge.available();
        }
        button(left+contentWidth-80,listTop-25,80,21,editedOnly?"Edited only":"All settings",0,"filter",()->{editedOnly=!editedOnly;offset=0;rebuild();}).selected=editedOnly;
        visible.clear();String q=search.getText().trim().toLowerCase(Locale.ROOT);
        for(Def d:backend.defs)if(d.page.equals(page)&&(!editedOnly||!SettingsModel.equal(session.draft.get(d.id),session.baseline.get(d.id)))&&(d.label+" "+d.description+" "+d.id).toLowerCase(Locale.ROOT).contains(q))visible.add(d);
        capacity=Math.max(1,(layout.content.bottom()-listTop)/rowHeight);offset=MenuLayout.clampOffset(offset,visible.size(),capacity);
        if(selected==null||!selected.page.equals(page))selected=visible.isEmpty()?null:visible.get(0);
        for(int i=0;i<capacity&&offset+i<visible.size();i++){
            Def d=visible.get(offset+i);int y=listTop+i*rowHeight;MenuLayout.Box cell=layout.valueCell(y,rowHeight);
            Surface.Button b=button(cell.x,cell.y,cell.w,cell.h,d.kind==Kind.NUMBER?"":d.format(session.draft.get(d.id)),4,"option/"+d.id,()->{selected=d;if(d.kind!=Kind.NUMBER)session.cycle(d);rebuild();});b.tooltip=d.description;b.paint=d.kind!=Kind.NUMBER;
            if(d.kind==Kind.NUMBER)sliders.put(d,new int[]{cell.x+6,cell.bottom()-5,cell.w-12,3});
        }
        track=new MenuLayout.Box(layout.content.right()+2,listTop,3,Math.max(1,layout.content.bottom()-listTop-2));
    }
    private void buildDialog(){
        MenuLayout.Box box=layout.modal(dialog.equals("reset")?286:196);boxX=box.x;boxY=box.y;boxW=box.w;boxH=box.h;int y=box.bottom()-30;
        if(dialog.equals("reset")){
            button(box.x+10,y,110,20,"Stage defaults",2,"modal/stage",()->{session.change(resetPlan);dialog="";notice="Defaults staged. Apply to save.";rebuild();});
            button(box.right()-76,y,66,20,"Cancel",1,"modal/cancel",()->{dialog="";rebuild();});
        }else if(dialog.equals("error")||dialog.equals("help"))button(box.right()-76,y,66,20,"Close",1,"modal/close",()->{dialog="";rebuild();});
        else{
            int w=(box.w-28)/3;
            button(box.x+10,y,w,20,"Apply",2,"modal/apply",()->{if(save(false)){Runnable next=pendingRoute;pendingRoute=null;if(next!=null)next.run();else mc.displayGuiScreen(parent);}});
            button(box.x+14+w,y,w,20,"Discard",3,"modal/discard",()->{session.discard();dialog="";Runnable next=pendingRoute;pendingRoute=null;if(next!=null)next.run();else mc.displayGuiScreen(parent);});
            button(box.x+18+w*2,y,w,20,"Keep editing",1,"modal/keep",()->{pendingRoute=null;dialog="";rebuild();});
        }
    }
    private boolean save(boolean close){
        finishGesture();try{session.apply();notice="Applied. Settings saved.";dialog="";if(close){mc.displayGuiScreen(parent);return true;}ScaledResolution resolution=new ScaledResolution(mc);setWorldAndResolution(mc,resolution.getScaledWidth(),resolution.getScaledHeight());return true;}
        catch(Exception ex){net.minecraftforge.fml.common.FMLLog.log.error("Field Settings could not apply a settings draft",ex);notice="Could not apply settings: "+ex.getMessage();pendingRoute=null;dialog="error";rebuild();return false;}
    }
    private void syncFocus(){
        List<String> keys=new ArrayList<>();for(GuiButton b:buttonList)if(b instanceof Surface.Button&&b.enabled&&((Surface.Button)b).focusKey.startsWith("page/"))keys.add(((Surface.Button)b).focusKey);
        if(dialog.isEmpty()&&isSettingsPage())keys.add("search");
        for(GuiButton b:buttonList)if(b instanceof Surface.Button&&b.enabled&&!((Surface.Button)b).focusKey.startsWith("page/"))keys.add(((Surface.Button)b).focusKey);
        navigation.rebuild(keys,!dialog.isEmpty());applyFocus();
    }
    private void applyFocus(){search.setFocused(navigation.current().equals("search"));focus=-1;for(int i=0;i<buttonList.size();i++)if(buttonList.get(i) instanceof Surface.Button){Surface.Button b=(Surface.Button)buttonList.get(i);b.focused=keyboardMode&&b.focusKey.equals(navigation.current());if(b.focused){focus=i;if(b.focusKey.startsWith("option/"))for(Def d:visible)if(b.focusKey.equals("option/"+d.id))selected=d;}}}
    @Override protected void actionPerformed(GuiButton button){if(button instanceof Surface.Button){navigation.set(((Surface.Button)button).focusKey);((Surface.Button)button).click.run();}}
    @Override public void updateScreen(){search.updateCursorCounter();}
    @Override public void onGuiClosed(){finishGesture();Keyboard.enableRepeatEvents(false);}
    private void finishGesture(){if(dragging!=null){dragging=null;session.endGesture();}draggingScroll=false;}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        keyboardMode=false;navigation.clear();applyFocus();
        if(dialog.isEmpty()&&isSettingsPage()){
            search.mouseClicked(x,y,button);if(search.isFocused())navigation.set("search");
            if(button==0&&track!=null&&new MenuLayout.Box(track.x-3,track.y,track.w+6,track.h).contains(x,y)&&visible.size()>capacity){draggingScroll=true;offset=MenuLayout.offsetAt(track,visible.size(),capacity,y);rebuild();return;}
            for(int i=0;i<capacity&&offset+i<visible.size();i++){Def d=visible.get(offset+i);int ry=listTop+i*rowHeight;if(new MenuLayout.Box(left,ry,contentWidth,rowHeight-2).contains(x,y)){selected=d;break;}}
            if(button==0)for(Map.Entry<Def,int[]> e:sliders.entrySet()){MenuLayout.Box cell=layout.valueCell(listTop+(visible.indexOf(e.getKey())-offset)*rowHeight,rowHeight);if(cell.contains(x,y)){dragging=e.getKey();selected=dragging;navigation.set("option/"+dragging.id);session.beginGesture();setSlider(x,e.getValue());return;}}
        }
        if(dialog.isEmpty()&&page.equals("More")&&button==0&&new MenuLayout.Box(track.x-3,track.y,track.w+6,track.h).contains(x,y)){draggingScroll=true;offset=MenuLayout.offsetAt(track,toolRows(),capacity,y);rebuild();return;}
        dispatch.begin();try{super.mouseClicked(x,y,button);}finally{if(dispatch.end())rebuild();}
    }
    private void setSlider(int x,int[] rect){Def d=dragging;if(d==null)return;double ratio=Math.max(0,Math.min(1,(x-rect[0])/(double)rect[2]));session.set(d,d.min+ratio*(d.max-d.min));rebuild();}
    @Override protected void mouseClickMove(int x,int y,int button,long time){if(dragging!=null){int[] r=sliders.get(dragging);if(r!=null)setSlider(x,r);}else if(draggingScroll){offset=MenuLayout.offsetAt(track,page.equals("More")?toolRows():visible.size(),capacity,y);rebuild();}}
    @Override protected void mouseReleased(int x,int y,int state){finishGesture();super.mouseReleased(x,y,state);}
    @Override public void handleMouseInput()throws IOException{
        super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel==0)return;
        if(dialog.equals("reset")){resetOffset=Math.max(0,resetOffset+(wheel>0?-2:2));return;}
        if(dialog.isEmpty()&&(isSettingsPage()||page.equals("More"))){offset+=wheel>0?-2:2;rebuild();}
    }
    @Override protected void keyTyped(char c,int key)throws IOException{
        keyboardMode=true;applyFocus();
        if(key==Keyboard.KEY_ESCAPE){finishGesture();if(!dialog.isEmpty()){dialog="";pendingRoute=null;rebuild();}else exit();return;}
        if(key==Keyboard.KEY_TAB){navigation.move(isShiftKeyDown()?-1:1);applyFocus();return;}
        if(dialog.isEmpty()&&isSettingsPage()&&isCtrlKeyDown()&&key==Keyboard.KEY_F){navigation.set("search");applyFocus();return;}
        if(dialog.isEmpty()&&isSettingsPage()&&search.isFocused()){String before=search.getText();search.textboxKeyTyped(c,key);if(!before.equals(search.getText())){offset=0;rebuild();}return;}
        if(dialog.isEmpty()&&isCtrlKeyDown()&&key==Keyboard.KEY_Z){session.undo();rebuild();return;}
        if(key==Keyboard.KEY_F1&&dialog.isEmpty()){setContext();dialog="help";rebuild();return;}
        if((key==Keyboard.KEY_RETURN||key==Keyboard.KEY_SPACE)&&focus>=0&&focus<buttonList.size()){GuiButton b=buttonList.get(focus);if(b.enabled)actionPerformed(b);return;}
        if(dialog.equals("reset")&&(key==Keyboard.KEY_DOWN||key==Keyboard.KEY_UP)){resetOffset=Math.max(0,resetOffset+(key==Keyboard.KEY_DOWN?1:-1));return;}
        if(!dialog.isEmpty())return;
        if(isSettingsPage()&&(key==Keyboard.KEY_DOWN||key==Keyboard.KEY_UP||key==Keyboard.KEY_NEXT||key==Keyboard.KEY_PRIOR)&&!visible.isEmpty()){
            int step=key==Keyboard.KEY_NEXT?capacity:key==Keyboard.KEY_PRIOR?-capacity:key==Keyboard.KEY_DOWN?1:-1,index=Math.max(0,Math.min(visible.size()-1,Math.max(0,visible.indexOf(selected))+step));selected=visible.get(index);if(index<offset)offset=index;if(index>=offset+capacity)offset=index-capacity+1;rebuild();navigation.set("option/"+selected.id);applyFocus();return;
        }
        if(page.equals("More")&&(key==Keyboard.KEY_NEXT||key==Keyboard.KEY_PRIOR)){offset+=key==Keyboard.KEY_NEXT?capacity:-capacity;rebuild();return;}
        if(isSettingsPage()&&selected!=null&&selected.kind==Kind.NUMBER&&(navigation.current().isEmpty()||navigation.current().equals("option/"+selected.id))&&(key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT)){double step=selected.step>0?selected.step:(selected.max-selected.min)/100;session.set(selected,((Number)session.draft.get(selected.id)).doubleValue()+(key==Keyboard.KEY_LEFT?-step:step));rebuild();}
    }
    private void text(String s,int x,int y,int color){fontRenderer.drawString(s,x,y,color,false);}
    private void fit(String s,int x,int y,int w,int color){text(fontRenderer.trimStringToWidth(s,Math.max(1,w)),x,y,color);}
    private int wrap(String s,int x,int y,int w,int bottom,int color){for(String line:fontRenderer.listFormattedStringToWidth(s,Math.max(16,w))){if(y+9>bottom)break;text(line,x,y,color);y+=11;}return y;}
    @Override public void drawScreen(int mx,int my,float partial){
        if(mx!=lastMx||my!=lastMy){keyboardMode=false;lastMx=mx;lastMy=my;applyFocus();}
        drawDefaultBackground();drawRect(0,0,width,height,0x65080F13);Surface.board(layout,"Settings","",Surface.MUTED);
        hoveredDef=null;if(dialog.isEmpty()){contextTitle="";contextBody="";}
        if(isSettingsPage())drawSettings(mx,my);else if(page.equals("Overview"))drawOverview();else drawTools();
        if(!dialog.isEmpty())drawDialog();for(GuiButton b:buttonList)b.drawButton(mc,mx,my,partial);
        if(dialog.isEmpty()&&isSettingsPage())search.drawTextBox();
        if(dialog.isEmpty()){for(GuiButton b:buttonList)if(b instanceof Surface.Button&&b.isMouseOver()&&!((Surface.Button)b).tooltip.isEmpty()){contextTitle=b.displayString;contextBody=((Surface.Button)b).tooltip;}setContext();drawHelp();}
        if(layout.footer.w>=430)fit(!notice.isEmpty()?notice:session.changes()>0?"Draft only / Apply saves":"Tab focus / F1 help",layout.footer.x+104,layout.footer.y+9,layout.footer.w-246,notice.isEmpty()?Surface.MUTED:Surface.BRASS);
    }
    private void drawOverview(){
        if(contextBody.isEmpty()){contextTitle="Your game settings";contextBody="Choose a section. Values remain staged until Apply. Controls has adaptive presets and saved profiles. Edit technique slots in HUD > Loadout.";}
    }
    private int toolRows(){return (TOOLS.length-2+(contentWidth>=370?2:1)-1)/(contentWidth>=370?2:1);}
    private void drawTools(){if(track!=null)Surface.scrollbar(track,toolRows(),capacity,offset);if(contextBody.isEmpty()){contextTitle="Game and mod screens";contextBody="These buttons open actual installed settings screens. Each advanced page keeps its own save behavior. Pending changes here are applied or discarded before leaving.";}}
    private void drawSettings(int mx,int my){
        Surface.round(left,listTop-25,contentWidth-84,21,2,Surface.WELL);if(search.getText().isEmpty()&&!search.isFocused())fit("Search section",left+7,listTop-19,contentWidth-96,Surface.MUTED);
        for(int i=0;i<capacity&&offset+i<visible.size();i++){
            Def d=visible.get(offset+i);int y=listTop+i*rowHeight;boolean hot=new MenuLayout.Box(left,y,contentWidth,rowHeight-2).contains(mx,my),changed=!SettingsModel.equal(session.draft.get(d.id),session.baseline.get(d.id));
            Surface.row(left,y,contentWidth,rowHeight,selected==d||hot,changed);fit(d.label,left+8,y+(rowHeight-9)/2,layout.labelWidth(),Surface.INK);if(hot)hoveredDef=d;
            if(d.kind==Kind.NUMBER){MenuLayout.Box cell=layout.valueCell(y,rowHeight);Surface.round(cell.x,cell.y,cell.w,cell.h,2,Surface.WELL);String value=d.format(session.draft.get(d.id));fit(value,cell.x+6,cell.y+3,cell.w-12,Surface.ACCENT);int[] r=sliders.get(d);if(r==null)r=new int[]{cell.x+6,cell.bottom()-5,cell.w-12,3};Surface.round(r[0],r[1],r[2],r[3],1,Surface.LINE);int fill=(int)Math.round(r[2]*d.ratio(session.draft.get(d.id)));if(fill>0)Surface.round(r[0],r[1],fill,r[3],1,Surface.BRASS);}
        }
        if(visible.isEmpty())wrap("No matching settings.",left+8,listTop+8,contentWidth-16,layout.content.bottom(),Surface.MUTED);if(track!=null)Surface.scrollbar(track,visible.size(),capacity,offset);
    }
    private void setContext(){
        if(keyboardMode)for(GuiButton button:buttonList)if(button instanceof Surface.Button){Surface.Button b=(Surface.Button)button;if(b.focusKey.equals(navigation.current())&&!b.tooltip.isEmpty()){contextTitle=b.displayString;contextBody=b.tooltip;}}
        Def d=keyboardMode?selected:hoveredDef!=null?hoveredDef:selected;
        if(isSettingsPage()&&d!=null){contextTitle=d.label;contextBody=d.description+" Default: "+d.format(d.defaults)+"."+(d.kind==Kind.NUMBER?" Left / Right adjusts the selected value.":"");}
        if(contextBody.isEmpty()){contextTitle=page;contextBody="Changes remain staged until Apply. Undo restores the previous edit. F1 expands this help.";}
        if(!notice.isEmpty()&&!isSettingsPage())contextBody=notice+" "+contextBody;
    }
    private void drawHelp(){MenuLayout.Box h=layout.help;Surface.round(h.x,h.y,h.w,h.h,2,Surface.WELL);if(h.h<30){fit(contextTitle+" / "+contextBody,h.x+7,h.y+7,h.w-14,Surface.MUTED);return;}fit(contextTitle,h.x+9,h.y+9,h.w-18,Surface.BRASS);wrap(contextBody,h.x+9,h.y+23,h.w-18,h.bottom()-8,Surface.MUTED);}
    private void drawDialog(){
        drawRect(layout.board.x,layout.board.y,layout.board.right(),layout.board.bottom(),0xD00B1216);Surface.panel(boxX,boxY,boxW,boxH);int x=boxX+13,y=boxY+13;
        if(dialog.equals("reset")){
            fit(page+" defaults",x,y,boxW-26,Surface.INK);int rows=Math.max(1,(boxH-76)/16);List<Def> changes=new ArrayList<>();for(Def d:backend.defs)if(d.page.equals(page)&&!SettingsModel.equal(session.draft.get(d.id),resetPlan.get(d.id)))changes.add(d);resetOffset=MenuLayout.clampOffset(resetOffset,changes.size(),rows);
            for(int i=0;i<rows&&i+resetOffset<changes.size();i++){Def d=changes.get(i+resetOffset);fit(d.label+": "+d.format(session.draft.get(d.id))+" > "+d.format(resetPlan.get(d.id)),x,y+25+i*16,boxW-26,Surface.MUTED);}fit(changes.size()+" values / staged until Apply",x,boxY+boxH-47,boxW-26,Surface.BRASS);
        }else if(dialog.equals("help")){fit(contextTitle,x,y,boxW-26,Surface.BRASS);wrap(contextBody,x,y+23,boxW-26,boxY+boxH-39,Surface.INK);}
        else if(dialog.equals("error")){text("Settings need attention",x,y,Surface.RED);wrap(notice,x,y+25,boxW-26,boxY+boxH-38,Surface.MUTED);}
        else{text("Keep pending changes?",x,y,Surface.INK);wrap(dialog.equals("route")?"Apply or discard this draft before opening the other settings screen. Keep editing stays here.":"Apply saves this draft. Discard closes without changing these settings.",x,y+26,boxW-26,boxY+boxH-38,Surface.MUTED);}
    }
}
