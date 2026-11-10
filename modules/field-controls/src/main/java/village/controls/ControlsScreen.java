package village.controls;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.input.*;
import village.controls.Model.*;
import village.controls.Bindings.MouseState;

/** Compact original controls board; gameplay dispatch and binding storage remain separate. */
public final class ControlsScreen extends GuiScreen {
    final GuiScreen parent;final boolean welcome;
    Bindings bindings;Session session;MouseState mouse,mouseBaseline;MenuLayout layout;
    GuiTextField search,profileName;String page="Bindings",group="Essentials",filter="All",notice="",modal="",previewTitle="",profileTarget="";
    Action selected,capture,hoveredAction;Stroke candidate;Plan preview;
    final List<Action> visible=new ArrayList<>();List<Conflict> conflicts=new ArrayList<>();final List<Conflict> collisions=new ArrayList<>();
    int offset,profileOffset,previewOffset,pendingModifier,focus=-1,buttonId;
    int left,tableWidth,listTop,listBottom,rowHeight,capacity,sideWidth,detailWidth,footer,modalX,modalY,modalW,modalH;
    boolean draggingMouse,draggingScroll,closeAfterSave,keyboardMode;private int lastMx=-1,lastMy=-1;
    private final MenuFocus navigation=new MenuFocus();private final DraftJournal journal=new DraftJournal();private DraftJournal.State mouseGesture;
    private final MenuDispatch dispatch=new MenuDispatch();
    private final List<Surface.Button> modalButtons=new ArrayList<>();private Surface.Button undoButton;
    private final Map<Preset,Plan> presetPlans=new EnumMap<>(Preset.class);
    private MenuLayout.Box track;private String helpTitle="",helpBody="";private Runnable pendingRoute;
    ControlsScreen(GuiScreen parent){this(parent,false);}
    ControlsScreen(GuiScreen parent,boolean welcome){this.parent=parent;this.welcome=welcome;if(welcome)page="Presets";}
    @Override public void initGui(){
        Keyboard.enableRepeatEvents(true);
        if(bindings==null){bindings=new Bindings();session=new Session(bindings.catalog,bindings.snapshot());session.layout=FieldControls.store.data.layout;mouse=new MouseState(mc.gameSettings);mouseBaseline=new MouseState(mouse);notice=!FieldControls.startupWarning.isEmpty()?FieldControls.startupWarning:FieldControls.store.warning;if(session.hardCount()>0)notice="Preview a preset or repair conflicts to untangle shared inputs.";selected=bindings.catalog.byRaw("key.fieldcontrols.cast");if(selected==null)selected=bindings.catalog.byRaw("key.attack");}
        String query=search==null?"":search.getText(),name=profileName==null?"My layout":profileName.getText();
        measure();search=new GuiTextField(500,fontRenderer,left+7,layout.content.y+5,Math.max(30,tableWidth-65),12);search.setMaxStringLength(96);search.setText(query);search.setEnableBackgroundDrawing(false);search.setTextColor(Surface.INK);
        profileName=new GuiTextField(501,fontRenderer,left+7,layout.content.y+6,Math.max(30,tableWidth-102),12);profileName.setMaxStringLength(32);profileName.setText(name);profileName.setEnableBackgroundDrawing(false);profileName.setTextColor(Surface.INK);rebuild();
    }
    private void measure(){layout=MenuLayout.simple(width,height);left=layout.content.x;tableWidth=layout.content.w;sideWidth=0;detailWidth=0;footer=layout.footer.y;rowHeight=layout.compact?22:28;listTop=layout.content.y+48;listBottom=layout.content.bottom();capacity=Math.max(1,(listBottom-listTop)/rowHeight);}
    private GuiTextField field(GuiTextField old,int id,int x,int y,int w,int max){
        if(old!=null&&old.x==x&&old.y==y&&old.getWidth()==w)return old;
        String value=old==null?"":old.getText();int cursor=old==null?0:old.getCursorPosition(),selection=old==null?0:old.getSelectionEnd();
        GuiTextField next=new GuiTextField(id,fontRenderer,x,y,w,12);next.setEnableBackgroundDrawing(false);next.setTextColor(Surface.INK);next.setMaxStringLength(max);next.setText(value);next.setCursorPosition(cursor);next.setSelectionPos(selection);return next;
    }
    private void positionFields(){MenuLayout.Box s=SimpleNavigation.search(layout);search=field(search,500,s.x+7,s.y+5,Math.max(30,s.w-14),96);profileName=field(profileName,501,left+7,layout.content.y+34,Math.max(30,tableWidth-102),32);}
    private DraftJournal.State state(){return new DraftJournal.State(session.draft,session.layout,mouse.sensitivity,mouse.invert,mouse.autoJump,mouse.touchscreen);}
    private void tracked(Runnable action){DraftJournal.State before=state();action.run();if(journal.record(before,state())&&undoButton!=null)undoButton.enabled=true;}
    private Surface.Button button(int x,int y,int w,int h,String title,int style,String key,Runnable action){Surface.Button b=new Surface.Button(buttonId++,x,y,w,h,title,style,key.equals("footer/undo")?action:()->tracked(action)).key(key);buttonList.add(b);return b;}
    private void rebuild(){
        if(!dispatch.rebuildNow())return;
        measure();buttonList.clear();modalButtons.clear();buttonId=0;conflicts=session.conflicts();
        positionFields();
        if(!modal.isEmpty()){buildModal();syncFocus();return;}
        for(int i=0;i<SimpleNavigation.PAGES.length;i++){String p=SimpleNavigation.PAGES[i];MenuLayout.Box b=SimpleNavigation.tab(layout,i);button(b.x,b.y,b.w,b.h,SimpleNavigation.LABELS[i],0,"nav/"+p,()->{if(p.equals("Controls")){changePage("Bindings");}else route(()->mc.displayGuiScreen(new SettingsHub(parent,p)));}).selected=p.equals("Controls");}
        MenuLayout.Box essential=SimpleNavigation.essential(layout);Surface.Button e=button(essential.x,essential.y,essential.w,essential.h,"Essential",1,"essential",()->route(()->openEssential()));e.enabled=EssentialBridge.available();e.tooltip=EssentialBridge.status();
        if(page.equals("Bindings"))buildBindings();else if(page.equals("Presets"))buildPresets();else if(page.equals("Profiles"))buildProfiles();
        int fy=footer+3;undoButton=button(layout.footer.x,fy,42,20,"Undo",1,"footer/undo",this::undo);undoButton.enabled=journal.available();
        button(layout.footer.x+46,fy,44,20,"More...",0,"footer/more",()->route(()->mc.displayGuiScreen(new SettingsHub(parent,"More"))));
        button(layout.footer.right()-132,fy,62,20,"Apply",2,"footer/apply",()->requestSave(false)).enabled=dirty();
        button(layout.footer.right()-66,fy,66,20,welcome?"Continue":"Done",1,"footer/done",this::exit);syncFocus();
    }
    private void changePage(String next){finishGesture();page=next;offset=0;profileOffset=0;navigation.clear();search.setFocused(false);profileName.setFocused(false);rebuild();}
    private void route(Runnable next){finishGesture();if(dirty()){pendingRoute=next;modal="exit";rebuild();}else next.run();}
    private void openEssential(){try{EssentialBridge.open(this);}catch(Exception ex){notice="Essential could not open. Use its native menu.";rebuild();net.minecraftforge.fml.common.FMLLog.log.warn("Essential settings could not open",ex);}}
    private void buildBindings(){
        MenuLayout.Box preset=SimpleNavigation.layout(layout),category=SimpleNavigation.category(layout),f=SimpleNavigation.filter(layout);
        button(preset.x,preset.y,preset.w,preset.h,session.layout+" >",1,"layouts",()->changePage("Presets")).tooltip="Choose a familiar preset or open your saved layouts.";
        button(category.x,category.y,category.w,category.h,SimpleNavigation.groupLabel(group)+" v",0,"categories",()->{modal="groups";rebuild();}).tooltip="Choose a category. Searching always checks every installed action.";
        button(f.x,f.y,f.w,f.h,filter+" v",0,"filters",()->{modal="filters";rebuild();}).tooltip="Show all, conflicting, unbound or edited keybinds.";
        refreshRows();
        for(int i=0;i<capacity&&offset+i<visible.size();i++){
            Action a=visible.get(offset+i);int y=listTop+i*rowHeight,bw=Math.min(96,Math.max(65,tableWidth*36/100));
            Surface.Button key=button(left+tableWidth-bw-7,y+3,bw,rowHeight-6,Bindings.display(session.draft.get(a.id)),4,"bind/"+a.id,()->listen(a));key.tooltip=a.help;
        }
        track=new MenuLayout.Box(layout.content.right()+2,listTop,3,Math.max(1,listBottom-listTop-2));
    }
    private boolean hasConflict(Action a,boolean soft){for(Conflict c:conflicts)if((c.a==a||c.b==a)&&(soft||!c.modifierOnly))return true;return false;}
    private void refreshRows(){
        visible.clear();String[] words=search.getText().trim().toLowerCase(Locale.ROOT).split("\\s+");
        for(Action a:bindings.catalog.actions){
            if(search.getText().trim().isEmpty()&&!group.equals("All actions")&&!group.equals(a.group)&&!(group.equals("Essentials")&&Metadata.essential(a.raw)))continue;Stroke value=session.draft.get(a.id);
            if(filter.equals("Conflicts")&&!hasConflict(a,false)||filter.equals("Unbound")&&value.code!=0||filter.equals("Edited")&&value.equals(session.baseline.get(a.id)))continue;
            String hay=(a.label+" "+a.source+" "+a.raw+" "+a.group+" "+Bindings.display(value)).toLowerCase(Locale.ROOT);boolean match=true;
            for(String word:words){if(word.startsWith("key:")){if(!Bindings.display(value).toLowerCase(Locale.ROOT).replaceAll("[\\s+]","").equals(word.substring(4).replaceAll("[\\s+]","")))match=false;}else if(word.startsWith("mod:")){if(!a.source.toLowerCase(Locale.ROOT).contains(word.substring(4)))match=false;}else if(!hay.contains(word))match=false;}
            if(match)visible.add(a);
        }
        visible.sort(Comparator.comparingInt((Action a)->group.equals("Essentials")?Metadata.displayOrder(a):a.priority).thenComparing(a->a.label).thenComparing(a->a.id));offset=MenuLayout.clampOffset(offset,visible.size(),capacity);
    }
    private void buildPresets(){
        button(left,layout.content.y,86,20,"< Keybinds",0,"back/keys",()->changePage("Bindings"));
        button(left+tableWidth-94,layout.content.y,94,20,"Saved layouts",1,"saved-layouts",()->changePage("Profiles"));
        presetPlans.clear();for(Preset p:Preset.values())presetPlans.put(p,Model.plan(bindings.catalog,session.draft,p));
        MenuLayout.Box[] cards=layout.presets();
        for(int i=0;i<3;i++){Preset p=Preset.values()[i];MenuLayout.Box a=layout.presetAction(cards[i]);button(a.x,a.y,a.w,a.h,"Preview",i==0?2:1,"preset/"+p.name(),()->showPlan(presetPlans.get(p),p.title));}
        button(left+90,layout.content.y,84,20,"Mod defaults",1,"mod/defaults",()->{Map<String,Stroke> v=new LinkedHashMap<>();List<Action> changed=new ArrayList<>();for(Action a:bindings.catalog.actions){v.put(a.id,a.defaults);if(!a.defaults.equals(session.draft.get(a.id)))changed.add(a);}showPlan(new Plan(v,changed,0),"Mod defaults");});
    }
    private void buildProfiles(){
        button(left,layout.content.y,86,20,"< Layouts",0,"back/layouts",()->changePage("Presets"));
        button(left+tableWidth-90,layout.content.y+28,90,23,"Save layout",2,"profile/save",this::requestProfileSave);
        int start=layout.content.y+57,rows=Math.max(1,(layout.content.bottom()-start)/27);List<String> names=profileNames();profileOffset=MenuLayout.clampOffset(profileOffset,names.size(),rows);
        for(int i=0;i<rows&&profileOffset+i<names.size();i++){String name=names.get(profileOffset+i);int y=start+i*27;
            button(left+tableWidth-110,y+3,62,20,"Preview",1,"profile/open/"+name,()->{Map<String,Stroke> source=name.equals("Startup backup")?FieldControls.store.data.original:FieldControls.store.data.profiles.get(name);Map<String,Stroke> values=new LinkedHashMap<>(session.draft);int matched=0;for(Action a:bindings.catalog.actions)if(source.containsKey(a.id)){values.put(a.id,source.get(a.id));matched++;}List<Action> changed=new ArrayList<>();for(Action a:bindings.catalog.actions)if(!values.get(a.id).equals(session.draft.get(a.id)))changed.add(a);showPlan(new Plan(values,changed,0),name);notice=matched+" installed actions match.";});
            button(left+tableWidth-44,y+3,44,20,"Delete",3,"profile/delete/"+name,()->{profileTarget=name;modal="delete";rebuild();}).enabled=!name.equals("Startup backup");
        }
        capacity=rows;track=new MenuLayout.Box(layout.content.right()+2,start,3,Math.max(1,layout.content.bottom()-start));
    }
    private List<String> profileNames(){List<String> names=new ArrayList<>();names.add("Startup backup");names.addAll(FieldControls.store.data.profiles.keySet());return names;}
    private void requestProfileSave(){String name=profileName.getText().trim();if(!ProfileStore.validName(name)){notice="Use a profile name of 1 to 32 characters.";return;}if(FieldControls.store.data.profiles.containsKey(name)){profileTarget=name;modal="overwrite";rebuild();}else saveProfile(name);}
    private void saveProfile(String name){try{FieldControls.store.put(name,session.draft);notice="Saved profile: "+name;}catch(IOException ex){notice="Could not save profile: "+ex.getMessage();}modal="";rebuild();}
    private void buildMouse(){
        int rh=layout.mouseRowHeight();
        Surface.Button slider=button(layout.valueCell(layout.content.y,rh).x,layout.content.y+3,layout.valueCell(layout.content.y,rh).w,rh-6,"",4,"mouse/sensitivity",()->{});slider.paint=false;
        for(int i=0;i<3;i++){final int index=i;MenuLayout.Box c=layout.valueCell(layout.content.y+(i+1)*rh,rh);boolean value=i==0?mouse.invert:i==1?mouse.autoJump:mouse.touchscreen;button(c.x,c.y,c.w,c.h,value?"On":"Off",4,"mouse/"+i,()->{if(index==0)mouse.invert=!mouse.invert;else if(index==1)mouse.autoJump=!mouse.autoJump;else mouse.touchscreen=!mouse.touchscreen;rebuild();});}
        button(left+tableWidth-98,layout.content.bottom()-19,98,18,"Mouse defaults",1,"mouse/defaults",()->{mouse.sensitivity=.5f;mouse.invert=mouse.autoJump=mouse.touchscreen=false;rebuild();});
    }
    private void showPlan(Plan plan,String title){finishGesture();preview=plan;previewTitle=title;previewOffset=0;modal="preview";rebuild();}
    private Surface.Button modalButton(int x,int y,int w,String label,int style,String key,Runnable action){Surface.Button b=button(x,y,w,20,label,style,"modal/"+key,action);modalButtons.add(b);return b;}
    private void buildModal(){
        boolean groups=modal.equals("groups"),filters=modal.equals("filters");int count=groups?Metadata.GROUPS.length:4;
        MenuLayout.Box b=layout.modal(groups||filters?Math.max(196,70+count*21):modal.equals("preview")?316:208);modalX=b.x;modalY=b.y;modalW=b.w;modalH=b.h;int bottom=b.bottom()-30;
        if(groups||filters){String[] choices=groups?Metadata.GROUPS:new String[]{"All","Conflicts","Unbound","Edited"};int rh=Math.min(22,(b.h-70)/choices.length);for(int i=0;i<choices.length;i++){String value=choices[i];Surface.Button option=button(b.x+10,b.y+32+i*rh,b.w-20,rh-1,groups?SimpleNavigation.groupLabel(value):value,0,"modal/choice/"+value,()->{if(groups)group=value;else filter=value;offset=0;modal="";rebuild();});option.selected=groups?group.equals(value):filter.equals(value);}modalButton(b.right()-76,bottom,66,"Cancel",1,"cancel",()->{modal="";rebuild();});}
        else if(modal.equals("listen")){modalButton(b.x+10,bottom,94,"Mod default",1,"default",()->{session.set(capture,capture.defaults);capture=null;modal="";rebuild();}).enabled=!capture.defaults.equals(session.draft.get(capture.id));modalButton(b.right()-76,bottom,66,"Cancel",1,"cancel",()->{capture=null;modal="";rebuild();});}
        else if(modal.equals("collision")){int w=(b.w-29)/4;modalButton(b.x+10,bottom,w,"Swap",1,"swap",()->resolve("swap")).enabled=canSwap();modalButton(b.x+13+w,bottom,w,"Replace",3,"replace",()->resolve("replace"));modalButton(b.x+16+w*2,bottom,w,"Share",1,"share",()->resolve("share"));modalButton(b.x+19+w*3,bottom,w,"Cancel",1,"cancel",()->{capture=null;modal="";rebuild();});}
        else if(modal.equals("preview")){modalButton(b.x+10,bottom,100,"Use layout",2,"use",()->{session.replace(preview.values,previewTitle);if(Arrays.asList("Action","MMO").contains(previewTitle))mouse.autoJump=false;modal="";page="Bindings";group="Essentials";offset=0;notice=preview.changed.size()+" changes staged. Apply to save.";rebuild();});modalButton(b.right()-76,bottom,66,"Cancel",1,"cancel",()->{modal="";rebuild();});}
        else if(modal.equals("exit")){int w=(b.w-28)/3;modalButton(b.x+10,bottom,w,"Apply",2,"apply",()->requestSave(true));modalButton(b.x+14+w,bottom,w,"Discard",3,"discard",this::discardAndClose);modalButton(b.x+18+w*2,bottom,w,"Keep editing",1,"keep",()->{pendingRoute=null;modal="";rebuild();});}
        else if(modal.equals("saveConflicts")){modalButton(b.x+10,bottom,100,"Apply anyway",3,"apply",this::save);modalButton(b.right()-110,bottom,100,"Review keys",2,"review",()->{modal="";page="Bindings";group="All actions";filter="Conflicts";offset=0;rebuild();});}
        else if(modal.equals("delete")||modal.equals("overwrite")){boolean deleting=modal.equals("delete");modalButton(b.x+10,bottom,96,deleting?"Delete":"Overwrite",3,"confirm",()->{if(deleting){try{FieldControls.store.remove(profileTarget);notice="Deleted profile: "+profileTarget;}catch(IOException e){notice=e.getMessage();}modal="";rebuild();}else saveProfile(profileTarget);});modalButton(b.right()-76,bottom,66,"Cancel",1,"cancel",()->{modal="";rebuild();});}
        else modalButton(b.right()-76,bottom,66,"Close",1,"close",()->{modal="";rebuild();});
    }
    void listen(Action action){finishGesture();capture=selected=action;pendingModifier=0;candidate=null;search.setFocused(false);modal="listen";rebuild();}
    void offer(Stroke value){DraftJournal.State before=state();candidate=value;Map<String,Stroke> test=new LinkedHashMap<>(session.draft);test.put(capture.id,value);collisions.clear();for(Conflict c:Model.conflicts(bindings.catalog,test))if(!c.modifierOnly&&(c.a==capture||c.b==capture))collisions.add(c);if(collisions.isEmpty()){session.set(capture,value);capture=null;modal="";}else modal="collision";journal.record(before,state());rebuild();}
    private Map<String,Stroke> resolution(String mode){Map<String,Stroke> out=new LinkedHashMap<>(session.draft);Stroke old=out.get(capture.id);out.put(capture.id,candidate);if(mode.equals("replace"))for(Conflict c:collisions)out.put(c.other(capture).id,new Stroke());if(mode.equals("swap")&&collisions.size()==1)out.put(collisions.get(0).other(capture).id,old);return out;}
    private boolean canSwap(){if(capture==null||collisions.size()!=1||session.draft.get(capture.id).code==0)return false;Action other=collisions.get(0).other(capture);for(Conflict c:Model.conflicts(bindings.catalog,resolution("swap")))if(!c.modifierOnly&&(c.a==other||c.b==other||c.a==capture||c.b==capture))return false;return true;}
    void resolve(String mode){session.replace(resolution(mode),"Custom");capture=null;modal="";rebuild();}
    private boolean dirty(){return session.changes()>0||!mouse.same(mouseBaseline)||!Objects.equals(session.layout,FieldControls.store.data.layout);}
    private void requestSave(boolean close){finishGesture();closeAfterSave=close;if(session.hardCount()>0||session.missingEssentials()>0){modal="saveConflicts";rebuild();}else save();}
    private void save(){try{bindings.save(session,mouse);mouseBaseline=new MouseState(mouse);journal.clear();notice="Applied. Controls saved.";modal="";if(closeAfterSave){Runnable next=pendingRoute;pendingRoute=null;if(next==null)mc.displayGuiScreen(parent);else next.run();return;}}catch(IOException e){notice="Could not apply: "+e.getMessage();modal="";pendingRoute=null;}rebuild();}
    private void exit(){finishGesture();pendingRoute=null;if(dirty()){modal="exit";rebuild();}else discardAndClose();}
    private void discardAndClose(){finishGesture();session.draft=new LinkedHashMap<>(session.baseline);session.layout=FieldControls.store.data.layout;mouse=new MouseState(mouseBaseline);journal.clear();modal="";Runnable next=pendingRoute;pendingRoute=null;if(next==null)mc.displayGuiScreen(parent);else next.run();}
    private void undo(){finishGesture();DraftJournal.State old=journal.undo();if(old==null)return;session.draft=new LinkedHashMap<>(old.values);session.layout=old.layout;mouse.sensitivity=old.sensitivity;mouse.invert=old.invert;mouse.autoJump=old.autoJump;mouse.touchscreen=old.touchscreen;rebuild();}
    private void finishGesture(){if(mouseGesture!=null){journal.record(mouseGesture,state());mouseGesture=null;}draggingMouse=draggingScroll=false;}
    private void syncFocus(){List<String> keys=new ArrayList<>();for(GuiButton b:buttonList)if(b instanceof Surface.Button&&b.enabled&&(((Surface.Button)b).focusKey.startsWith("nav/")||((Surface.Button)b).focusKey.startsWith("group/")))keys.add(((Surface.Button)b).focusKey);if(modal.isEmpty()&&page.equals("Bindings"))keys.add("search");if(modal.isEmpty()&&page.equals("Profiles"))keys.add("profile/name");for(GuiButton b:buttonList)if(b instanceof Surface.Button&&b.enabled&&!((Surface.Button)b).focusKey.startsWith("nav/")&&!((Surface.Button)b).focusKey.startsWith("group/"))keys.add(((Surface.Button)b).focusKey);navigation.rebuild(keys,!modal.isEmpty());applyFocus();}
    private void applyFocus(){search.setFocused(navigation.current().equals("search"));profileName.setFocused(navigation.current().equals("profile/name"));focus=-1;for(int i=0;i<buttonList.size();i++)if(buttonList.get(i) instanceof Surface.Button){Surface.Button b=(Surface.Button)buttonList.get(i);b.focused=keyboardMode&&b.focusKey.equals(navigation.current());if(b.focused){focus=i;if(b.focusKey.startsWith("bind/"))selected=bindings.catalog.byId(b.focusKey.substring(5));}}}
    @Override protected void actionPerformed(GuiButton b){if(b instanceof Surface.Button){navigation.set(((Surface.Button)b).focusKey);((Surface.Button)b).click.run();}}
    @Override public void updateScreen(){search.updateCursorCounter();profileName.updateCursorCounter();}
    @Override public void onGuiClosed(){finishGesture();Keyboard.enableRepeatEvents(false);}
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        keyboardMode=false;navigation.clear();applyFocus();
        if(modal.equals("listen")){for(Surface.Button b:modalButtons)if(button==0&&inside(x,y,b.x,b.y,b.width,b.height)){b.click.run();return;}offer(new Stroke(-100+button,Bindings.activeModifier()));return;}
        if(!modal.isEmpty()){clickWidgets(x,y,button);return;}
        if(page.equals("Bindings")){search.mouseClicked(x,y,button);if(search.isFocused())navigation.set("search");if(x>=left&&x<left+tableWidth&&y>=listTop&&y<listBottom){int i=offset+(y-listTop)/rowHeight;if(i<visible.size())selected=visible.get(i);}}
        if(page.equals("Profiles")){profileName.mouseClicked(x,y,button);if(profileName.isFocused())navigation.set("profile/name");}
        if(button==0&&(page.equals("Bindings")||page.equals("Profiles"))&&track!=null&&new MenuLayout.Box(track.x-3,track.y,track.w+6,track.h).contains(x,y)){draggingScroll=true;scrollAt(y);return;}
        if(page.equals("Mouse")&&button==0){int rh=layout.mouseRowHeight();MenuLayout.Box cell=layout.valueCell(layout.content.y,rh);if(cell.contains(x,y)){navigation.set("mouse/sensitivity");mouseGesture=state();draggingMouse=true;setSensitivity(x,cell);return;}}
        clickWidgets(x,y,button);
    }
    private void clickWidgets(int x,int y,int button)throws IOException{dispatch.begin();try{super.mouseClicked(x,y,button);}finally{if(dispatch.end())rebuild();}}
    private void setSensitivity(int x,MenuLayout.Box cell){mouse.sensitivity=Math.max(0,Math.min(1,(x-cell.x-6)/(float)Math.max(1,cell.w-12)));rebuild();}
    private void scrollAt(int y){if(page.equals("Bindings"))offset=MenuLayout.offsetAt(track,visible.size(),capacity,y);else profileOffset=MenuLayout.offsetAt(track,profileNames().size(),capacity,y);rebuild();}
    @Override protected void mouseClickMove(int x,int y,int button,long time){if(draggingMouse){int rh=layout.mouseRowHeight();setSensitivity(x,layout.valueCell(layout.content.y,rh));}else if(draggingScroll)scrollAt(y);}
    @Override protected void mouseReleased(int x,int y,int button){finishGesture();if(undoButton!=null)undoButton.enabled=journal.available();super.mouseReleased(x,y,button);}
    @Override public void handleMouseInput()throws IOException{super.handleMouseInput();int wheel=Mouse.getEventDWheel();if(wheel==0)return;int delta=wheel>0?-1:1;if(modal.equals("preview")){int rows=Math.max(1,(modalH-88)/18);previewOffset=MenuLayout.clampOffset(previewOffset+delta*2,preview.changed.size(),rows);}else if(modal.isEmpty()&&page.equals("Bindings")){offset+=delta*3;rebuild();}else if(modal.isEmpty()&&page.equals("Profiles")){profileOffset+=delta*2;rebuild();}}
    @Override public void handleKeyboardInput()throws IOException{if(modal.equals("listen")&&!Keyboard.getEventKeyState()){if(pendingModifier!=0&&Keyboard.getEventKey()==pendingModifier){offer(new Stroke(pendingModifier));pendingModifier=0;}return;}super.handleKeyboardInput();}
    @Override protected void keyTyped(char c,int key)throws IOException{
        keyboardMode=true;applyFocus();
        if(modal.equals("listen")){if(key==Keyboard.KEY_ESCAPE){capture=null;modal="";rebuild();return;}if(key==Keyboard.KEY_DELETE||key==Keyboard.KEY_BACK){offer(new Stroke());return;}if(KeyModifier.isKeyCodeModifier(key)){pendingModifier=key;return;}if(key!=0||c>0)offer(new Stroke(key!=0?key:c+256,Bindings.activeModifier()));return;}
        if(key==Keyboard.KEY_ESCAPE){if(!modal.isEmpty()){modal="";capture=null;pendingRoute=null;rebuild();}else if(!page.equals("Bindings")){changePage("Bindings");}else exit();return;}
        if(key==Keyboard.KEY_TAB){navigation.move(isShiftKeyDown()?-1:1);applyFocus();return;}
        if(modal.isEmpty()&&page.equals("Bindings")&&isCtrlKeyDown()&&key==Keyboard.KEY_F){navigation.set("search");applyFocus();return;}
        if(modal.isEmpty()&&page.equals("Bindings")&&search.isFocused()){String before=search.getText();search.textboxKeyTyped(c,key);if(!before.equals(search.getText())){offset=0;rebuild();}return;}
        if(modal.isEmpty()&&page.equals("Profiles")&&profileName.isFocused()){if(key==Keyboard.KEY_RETURN)tracked(this::requestProfileSave);else profileName.textboxKeyTyped(c,key);return;}
        if(modal.isEmpty()&&isCtrlKeyDown()&&key==Keyboard.KEY_Z){undo();return;}
        if(key==Keyboard.KEY_F1&&modal.isEmpty()){chooseHelp();modal="help";rebuild();return;}
        if((key==Keyboard.KEY_RETURN||key==Keyboard.KEY_SPACE)&&focus>=0&&focus<buttonList.size()){GuiButton b=buttonList.get(focus);if(b.enabled)actionPerformed(b);return;}
        if(modal.equals("preview")&&(key==Keyboard.KEY_DOWN||key==Keyboard.KEY_UP||key==Keyboard.KEY_NEXT||key==Keyboard.KEY_PRIOR)){int rows=Math.max(1,(modalH-88)/18);previewOffset=MenuLayout.clampOffset(previewOffset+(key==Keyboard.KEY_DOWN?1:key==Keyboard.KEY_UP?-1:key==Keyboard.KEY_NEXT?rows:-rows),preview.changed.size(),rows);return;}
        if(!modal.isEmpty())return;
        if(page.equals("Mouse")&&(navigation.current().isEmpty()||navigation.current().equals("mouse/sensitivity"))&&(key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT)){tracked(()->{mouse.sensitivity=Math.max(0,Math.min(1,mouse.sensitivity+(key==Keyboard.KEY_LEFT?-.025f:.025f)));rebuild();});return;}
        if(page.equals("Bindings")&&(key==Keyboard.KEY_DOWN||key==Keyboard.KEY_UP||key==Keyboard.KEY_NEXT||key==Keyboard.KEY_PRIOR)&&!visible.isEmpty()){int step=key==Keyboard.KEY_NEXT?capacity:key==Keyboard.KEY_PRIOR?-capacity:key==Keyboard.KEY_DOWN?1:-1,index=Math.max(0,Math.min(visible.size()-1,Math.max(0,visible.indexOf(selected))+step));selected=visible.get(index);if(index<offset)offset=index;if(index>=offset+capacity)offset=index-capacity+1;rebuild();navigation.set("bind/"+selected.id);applyFocus();return;}
        if(page.equals("Profiles")&&(key==Keyboard.KEY_NEXT||key==Keyboard.KEY_PRIOR)){profileOffset+=key==Keyboard.KEY_NEXT?capacity:-capacity;rebuild();}
    }
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private void text(String s,int x,int y,int color){fontRenderer.drawString(s,x,y,color,false);}
    private void fit(String s,int x,int y,int w,int color){text(fontRenderer.trimStringToWidth(s,Math.max(1,w)),x,y,color);}
    private int wrap(String s,int x,int y,int w,int bottom,int color){for(String line:fontRenderer.listFormattedStringToWidth(s,Math.max(16,w))){if(y+9>bottom)break;text(line,x,y,color);y+=11;}return y;}
    @Override public void drawScreen(int mx,int my,float partial){
        if(mx!=lastMx||my!=lastMy){keyboardMode=false;lastMx=mx;lastMy=my;applyFocus();}
        drawDefaultBackground();drawRect(0,0,width,height,0x65080F13);int hard=session.hardCount(),missing=session.missingEssentials();String badge=missing>0?missing+" unbound essentials":hard>0?hard+" conflicts":dirty()?"Draft":"Inputs ready";
        Surface.board(layout,welcome?"Controls setup":"Settings","",Surface.MUTED);hoveredAction=null;
        if(page.equals("Bindings"))drawBindings(mx,my);else if(page.equals("Presets"))drawPresets();else if(page.equals("Profiles"))drawProfiles();else drawMouse();
        if(!modal.isEmpty())drawModal();for(GuiButton b:buttonList)b.drawButton(mc,mx,my,partial);
        if(modal.isEmpty()){
            if(page.equals("Bindings"))search.drawTextBox();if(page.equals("Profiles"))profileName.drawTextBox();chooseHelp();drawHelp();
            if(layout.footer.w>=430)fit(dirty()?"Staged / Apply saves":session.layout+" layout",layout.footer.x+98,footer+9,layout.footer.w-238,dirty()?Surface.BRASS:Surface.MUTED);
        }
    }
    private void drawBindings(int mx,int my){
        MenuLayout.Box field=SimpleNavigation.search(layout);Surface.round(field.x,field.y,field.w,field.h,2,Surface.WELL);if(search.getText().isEmpty()&&!search.isFocused())fit("Search all keybinds",field.x+7,field.y+6,field.w-14,Surface.MUTED);
        MenuLayout.Box category=SimpleNavigation.category(layout),filterBox=SimpleNavigation.filter(layout);fit(visible.size()+" binds",category.right()+7,category.y+5,filterBox.x-category.right()-14,Surface.MUTED);
        for(int i=0;i<capacity&&offset+i<visible.size();i++){Action a=visible.get(offset+i);int y=listTop+i*rowHeight;boolean hot=inside(mx,my,left,y,tableWidth,rowHeight-2),error=hasConflict(a,false),soft=hasConflict(a,true);Surface.row(left,y,tableWidth,rowHeight,selected==a||hot,!session.draft.get(a.id).equals(session.baseline.get(a.id)));if(error||soft)drawRect(left,y+4,left+2,y+rowHeight-6,error?Surface.RED:Surface.AMBER);int bw=Math.min(96,Math.max(65,tableWidth*36/100)),labelWidth=tableWidth-bw-22;
            fit(a.label,left+8,y+(rowHeight>=28?4:(rowHeight-9)/2),labelWidth,error?Surface.RED:Surface.INK);if(rowHeight>=28)fit(a.source,left+8,y+17,labelWidth,Surface.MUTED);if(hot)hoveredAction=a;
        }
        if(visible.isEmpty())wrap("No matching inputs. Clear the search or choose All actions.",left+8,listTop+7,tableWidth-16,listBottom,Surface.MUTED);if(track!=null)Surface.scrollbar(track,visible.size(),capacity,offset);
    }
    private void drawPresets(){
        boolean columns=tableWidth>=400;MenuLayout.Box[] cards=layout.presets();
        for(int i=0;i<3;i++){Preset p=Preset.values()[i];MenuLayout.Box c=cards[i];int x=c.x,y=c.y,w=c.w,h=c.h;Surface.row(x,y,w,h,session.layout.equals(p.title),false);fit(p.title,x+9,y+8,columns?w-18:78,i==0?Surface.ACCENT:Surface.INK);
            if(columns){int yy=y+29;for(String raw:new String[]{"key.sprint","key.sneak","key.inventory"}){Action a=bindings.catalog.byRaw(raw);if(a!=null&&yy+9<y+h-30){fit(raw.equals("key.sprint")?"Sprint":raw.equals("key.sneak")?"Crouch":"Inventory",x+9,yy,w-70,Surface.MUTED);String value=Bindings.display(presetPlans.get(p).values.get(a.id));fit(value,x+w-61,yy,53,Surface.BRASS);yy+=17;}}}
            else if(w>=350)fit(p==Preset.CLASSIC?"Ctrl sprint / Shift crouch":p==Preset.MMO?"Shift sprint / I inventory":"Shift sprint / E inventory",x+87,y+8,w-160,Surface.MUTED);
        }
    }
    private void drawProfiles(){
        Surface.round(left,layout.content.y+28,tableWidth-96,23,2,Surface.WELL);int start=layout.content.y+57;List<String> names=profileNames();for(int i=0;i<capacity&&profileOffset+i<names.size();i++){String name=names.get(profileOffset+i);int y=start+i*27;Surface.row(left,y,tableWidth,27,false,false);fit(name,left+8,y+9,tableWidth-120,Surface.INK);}if(track!=null)Surface.scrollbar(track,names.size(),capacity,profileOffset);
    }
    private void drawMouse(){
        int rh=layout.mouseRowHeight();String[] labels={"Look sensitivity","Invert vertical look","Auto-jump","Touchscreen input"};
        for(int i=0;i<4;i++){int y=layout.content.y+i*rh;Surface.row(left,y,tableWidth,rh,false,!mouse.same(mouseBaseline));fit(labels[i],left+8,y+(rh-9)/2,layout.labelWidth(),Surface.INK);if(i==0){MenuLayout.Box c=layout.valueCell(y,rh);Surface.round(c.x,c.y,c.w,c.h,2,Surface.WELL);fit(Math.round(mouse.sensitivity*200)+"%",c.x+6,c.y+2,c.w-12,Surface.ACCENT);int ty=c.bottom()-(c.h<18?3:5),th=c.h<18?2:3;Surface.round(c.x+6,ty,c.w-12,th,1,Surface.LINE);int fill=Math.round((c.w-12)*mouse.sensitivity);if(fill>0)Surface.round(c.x+6,ty,fill,th,1,Surface.BRASS);}}
    }
    private void chooseHelp(){
        if(page.equals("Bindings")){Action a=keyboardMode?selected:hoveredAction!=null?hoveredAction:selected;if(a!=null){helpTitle=a.label;helpBody=a.source+" / "+a.help+" Default: "+Bindings.display(a.defaults)+".";if(!notice.isEmpty()){helpTitle="Update";helpBody=notice;}return;}}
        helpTitle=page.equals("Presets")?"Layouts":page.equals("Profiles")?"Saved layouts":page;helpBody=page.equals("Presets")?"Preview every input change before staging a layout. Action uses Shift sprint; Classic keeps Ctrl sprint. All presets adapt to installed mods. Apply saves the staged layout.":page.equals("Profiles")?"A named profile stores this draft. Preview restores matching installed actions; absent mods are preserved in the saved profile. Startup backup keeps the original inputs.":page.equals("Mouse")?"Mouse preferences stay staged until Apply. Drag a value or use Left / Right for sensitivity. Undo restores a complete mouse gesture as one edit.":"Search by action, mod or key. F1 expands help.";
        if(!notice.isEmpty())helpBody=notice+" "+helpBody;
    }
    private void drawHelp(){MenuLayout.Box h=layout.help;Surface.round(h.x,h.y,h.w,h.h,2,Surface.WELL);if(h.h<30){fit(helpTitle+" / "+helpBody,h.x+7,h.y+7,h.w-14,Surface.MUTED);return;}fit(helpTitle,h.x+9,h.y+9,h.w-18,Surface.BRASS);wrap(helpBody,h.x+9,h.y+23,h.w-18,h.bottom()-8,Surface.MUTED);}
    private void drawModal(){
        drawRect(layout.board.x,layout.board.y,layout.board.right(),layout.board.bottom(),0xD00B1216);Surface.panel(modalX,modalY,modalW,modalH);int x=modalX+13,y=modalY+13,bottom=modalY+modalH-40;
        if(modal.equals("groups")||modal.equals("filters")){fit(modal.equals("groups")?"Choose category":"Filter keybinds",x,y,modalW-26,Surface.INK);}
        else if(modal.equals("listen")){fit("Rebind: "+capture.label,x,y,modalW-26,Surface.BRASS);y=wrap("Press a key, mouse button or modifier chord.",x,y+23,modalW-26,bottom,Surface.INK);Surface.round(x,y+8,modalW-26,28,2,Surface.WELL);fit(pendingModifier==0?"Listening":Bindings.display(new Stroke(pendingModifier))+" held",x+8,y+18,modalW-42,Surface.ACCENT);wrap("Esc cancels. Delete or Backspace clears. Release a modifier alone to bind it.",x,y+47,modalW-26,bottom,Surface.MUTED);}
        else if(modal.equals("collision")){text("Input already in use",x,y,Surface.RED);y=wrap(capture.label+" > "+Bindings.display(candidate),x,y+22,modalW-26,bottom-35,Surface.INK);for(Conflict c:collisions){if(y+11>bottom-30)break;y=wrap(c.other(capture).label+" / "+c.other(capture).source,x,y+5,modalW-26,bottom-30,Surface.MUTED);}wrap("Swap exchanges. Replace unbinds others. Share keeps the conflict.",x,bottom-25,modalW-26,bottom,Surface.AMBER);}
        else if(modal.equals("preview")){fit(previewTitle+" / preview",x,y,modalW-26,Surface.INK);fit(preview.changed.size()+" changes / "+preview.unresolved+" unresolved",x,y+16,modalW-26,Surface.BRASS);int rows=Math.max(1,(modalH-88)/18);previewOffset=MenuLayout.clampOffset(previewOffset,preview.changed.size(),rows);for(int i=0;i<rows&&previewOffset+i<preview.changed.size();i++){Action a=preview.changed.get(previewOffset+i);int yy=y+38+i*18;String values=Bindings.display(session.draft.get(a.id))+" > "+Bindings.display(preview.values.get(a.id));int vw=Math.min(modalW/2,fontRenderer.getStringWidth(values));fit(a.label,x,yy,modalW-vw-38,Surface.INK);fit(values,modalX+modalW-vw-13,yy,vw,Surface.ACCENT);}if(preview.changed.isEmpty())text("Already matches this layout.",x,y+43,Surface.MUTED);}
        else if(modal.equals("exit")){text("Keep pending changes?",x,y,Surface.INK);wrap("Apply saves this draft before leaving. Discard restores the saved controls. Keep editing stays here.",x,y+26,modalW-26,bottom,Surface.MUTED);}
        else if(modal.equals("saveConflicts")){text("Review shared or empty inputs",x,y,Surface.RED);wrap("This draft has "+session.hardCount()+" conflicts and "+session.missingEssentials()+" unbound essentials. Review the keys or intentionally apply this layout.",x,y+25,modalW-26,bottom,Surface.MUTED);}
        else if(modal.equals("delete")||modal.equals("overwrite")){text(modal.equals("delete")?"Delete saved profile?":"Overwrite saved profile?",x,y,Surface.RED);wrap(profileTarget,x,y+28,modalW-26,bottom,Surface.INK);}
        else{fit(helpTitle,x,y,modalW-26,Surface.BRASS);wrap(helpBody,x,y+24,modalW-26,bottom,Surface.INK);}
    }
}
