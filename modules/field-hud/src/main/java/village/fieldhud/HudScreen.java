package village.fieldhud;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.input.Keyboard;

/** Naruto-facing preferences over real config and the existing native input objects. */
public final class HudScreen extends GuiScreen {
    private final GuiScreen parent;
    private NarutoMenuLayout layout;
    private int page,focus=-1;
    private String notice="";
    private KeyBinding listening;
    private final RebindGesture capture=new RebindGesture();
    private static final String[] TABS={"Display","Controls","Shinobi record"};
    private static final String[] PRESENT={"Paper inventory","Shinobi HUD","Reduced motion","Action focus","Glass blur","Region chime","GUI scale","Menu clicks"};
    private static final String[] CONTROL={"Dodge","Settings key","Inventory key","Use item","Attack","All controls","Video / shaders","Sound mixer"};
    private static final String[] DESCRIPTIONS={"key.valeorders.dodge","key.fieldhud.settings","key.inventory","key.use","key.attack"};
    public HudScreen(){this(null);}public HudScreen(GuiScreen parent){this.parent=parent;}
    @Override public void initGui(){
        buttonList.clear();layout=new NarutoMenuLayout(width,height);
        if(!layout.usable){int w=Math.min(160,Math.max(1,layout.panel.w-16));buttonList.add(new NarutoInk.Button(106,new Layout.Box(layout.panel.x+(layout.panel.w-w)/2,layout.panel.y+layout.panel.h-47,w,18),"GUI scale: "+value(106)));buttonList.add(new NarutoInk.Button(403,new Layout.Box(layout.panel.x+(layout.panel.w-w)/2,layout.panel.y+layout.panel.h-25,w,18),"Return"));return;}
        for(int i=0;i<3;i++){NarutoInk.Button b=new NarutoInk.Button(i,layout.tab(i),TABS[i]);b.selected=page==i;buttonList.add(b);}
        if(page<2)for(int i=0;i<8;i++){
            int id=(page==0?100:200)+i;NarutoInk.Button b=new NarutoInk.Button(id,layout.value(i),value(id));
            if(page==1&&i<5)b.enabled=NarutoBindings.find(DESCRIPTIONS[i])!=null;buttonList.add(b);
        }
        if(page==2){
            buttonList.add(new NarutoInk.Button(300,layout.value(6),"Open journal"));
            buttonList.add(new NarutoInk.Button(301,layout.value(7),"View jutsu"));
            for(GuiButton b:buttonList)if(b.id>=300)b.enabled=mc.player!=null&&mc.world!=null&&ValeBridge.hasFreshLocal();
        }
        String[] foot={"HUD layout","Advanced","Video","Done"};
        for(int i=0;i<4;i++){GuiButton b=new NarutoInk.Button(400+i,layout.footer(i),foot[i]);if(i==0)b.enabled=mc.player!=null&&mc.world!=null&&HudConfig.shinobi;buttonList.add(b);}
    }
    private static String on(boolean v){return v?"On":"Off";}
    private String value(int id){
        switch(id){
            case 100:return on(HudConfig.narutoInventory);case 101:return on(HudConfig.shinobi);case 102:return on(!HudConfig.motion);case 103:return on(HudConfig.focusEffects);
            case 104:return on(HudConfig.glassBlur);case 105:return on(HudConfig.chime);case 106:return mc.gameSettings.guiScale==0?"Auto":mc.gameSettings.guiScale+"x";case 107:return on(HudConfig.menuSounds);
            case 205:return "Open controls";case 206:return "Video settings";case 207:return "Open mixer";
            default:if(id>=200&&id<205){KeyBinding key=NarutoBindings.find(DESCRIPTIONS[id-200]);return key==null?"Unavailable":key==listening?"Press a key...":key.getKeyCode()==0?"Unbound":key.getDisplayName()+(NarutoBindings.conflict(key,key.getKeyCode(),key.getKeyModifier()).isEmpty()?"":" !");}return "";
        }
    }
    private int openingCode(){if(Keyboard.isKeyDown(Keyboard.KEY_RETURN))return Keyboard.KEY_RETURN;if(Keyboard.isKeyDown(Keyboard.KEY_NUMPADENTER))return Keyboard.KEY_NUMPADENTER;if(Keyboard.isKeyDown(Keyboard.KEY_SPACE))return Keyboard.KEY_SPACE;return org.lwjgl.input.Mouse.isButtonDown(0)?-100:0;}
    private boolean captureReady(){int code=capture.openingCode();boolean down=code>0?Keyboard.isKeyDown(code):code<0&&org.lwjgl.input.Mouse.isButtonDown(code+100);return capture.ready(down);}
    @Override public void updateScreen(){
        if(listening!=null)captureReady();
        for(GuiButton b:buttonList)if(b.id>=100&&b.id<208){b.displayString=value(b.id);if(b.id>=200&&b.id<205)b.enabled=NarutoBindings.find(DESCRIPTIONS[b.id-200])!=null;}
    }
    private String help(int id){
        if(listening!=null)return "Press a key or mouse button. Hold Ctrl/Shift/Alt for a chord. Escape cancels; Delete clears.";
        if(!notice.isEmpty())return notice;
        switch(id){
            case 100:return "Paper and ink around the native inventory. Slots, crafting, tooltips and recipe book keep their normal behavior.";
            case 101:return "Use the existing synchronized Shinobi HUD. This does not change health, chakra or the hotbar loadout.";
            case 102:return "On removes owned HUD/menu animation while keeping combat information visible.";
            case 103:return "Subtle action focus. Switch off for a quieter view; no shader is required to read attacks.";
            case 104:return "Soften the HUD surfaces, or switch off for crisp panels and reduced effects.";
            case 105:return "Optional quiet native chime when entering a configured region. Does not change combat audio.";
            case 106:return "Real Minecraft GUI scale. Auto adapts to the window; the inventory remains native-sized.";
            case 107:return "Native click audio for these Naruto settings buttons.";
            case 200:return NarutoBindings.find(DESCRIPTIONS[0])==null?"Dodge is unavailable in this session. Open All controls to review installed actions.":"Rebind your dodge. ! marks a conflicting action. A quick directional step still follows its normal cost and cooldown.";
            case 201:return "Rebind the existing settings key. Other installed actions keep their own bindings.";
            case 205:return "Open installed native/Field Controls for all actions, conflicts, profiles and mouse preferences.";
            case 206:case 402:return "Open video and shader options provided by Minecraft and OptiFine.";
            case 207:return "Open the native sound mixer. Master and category sliders affect actual game sound.";
            case 300:return "Open the server mission journal; no mission progress or rewards are granted here.";
            case 301:return "Ask the server for your available arts and techniques.";
            case 400:return "Move the existing HUD panel. No bar or inventory slot is repositioned by these settings.";
            case 401:return "All previous HUD preferences remain available in the advanced page.";
            default:return "Changes save immediately. Tab moves focus; Enter activates. Escape returns.";
        }
    }
    @Override protected void actionPerformed(GuiButton b)throws IOException{
        if(!b.enabled)return;focus=b.id;notice="";
        if(b.id<3){page=b.id;initGui();return;}
        switch(b.id){
            case 100:HudConfig.narutoInventory=!HudConfig.narutoInventory;break;
            case 101:HudConfig.shinobi=!HudConfig.shinobi;break;
            case 102:HudConfig.motion=!HudConfig.motion;break;
            case 103:HudConfig.focusEffects=!HudConfig.focusEffects;break;
            case 104:HudConfig.glassBlur=!HudConfig.glassBlur;break;
            case 105:HudConfig.chime=!HudConfig.chime;break;
            case 106:
                mc.gameSettings.guiScale=(mc.gameSettings.guiScale+1)%5;mc.gameSettings.saveOptions();
                net.minecraft.client.gui.ScaledResolution sr=new net.minecraft.client.gui.ScaledResolution(mc);setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());return;
            case 107:HudConfig.menuSounds=!HudConfig.menuSounds;break;
            case 205:mc.displayGuiScreen(new GuiControls(this,mc.gameSettings));return;
            case 206:case 402:mc.displayGuiScreen(new GuiVideoSettings(this,mc.gameSettings));return;
            case 207:mc.displayGuiScreen(new GuiScreenOptionsSounds(this,mc.gameSettings));return;
            case 300:case 301:
                if(mc.player!=null&&ValeBridge.hasFreshLocal()){mc.displayGuiScreen(null);mc.player.sendChatMessage(b.id==300?"/vale journal":"/vale arts");}return;
            case 400:mc.displayGuiScreen(new HudLayoutScreen(this));return;
            case 401:mc.displayGuiScreen(new AdvancedHudScreen(this));return;
            case 403:mc.displayGuiScreen(parent);return;
            default:if(b.id>=200&&b.id<205){listening=NarutoBindings.find(DESCRIPTIONS[b.id-200]);capture.begin(openingCode());KeyBinding.unPressAllKeys();return;}return;
        }
        HudConfig.save();initGui();
    }
    private void bind(int code,KeyModifier modifier){
        KeyBinding target=listening;if(target==null)return;
        String conflict=NarutoBindings.conflict(target,code,modifier);
        if(!conflict.isEmpty()){notice="Already used by "+I18n.format(conflict)+". Choose another key or use All controls.";listening=null;initGui();return;}
        try{NarutoBindings.save(target,code,modifier);notice="Saved "+I18n.format(target.getKeyDescription())+": "+(code==0?"Unbound":target.getDisplayName());}
        catch(Exception|LinkageError error){notice="Input could not be saved. Open All controls; the previous profile is preserved.";}
        listening=null;capture.reset();KeyBinding.unPressAllKeys();initGui();
    }
    @Override protected void mouseClicked(int x,int y,int button)throws IOException{
        if(listening!=null){if(captureReady()&&button>=0&&button<16)bind(button-100,KeyModifier.getActiveModifier());return;}super.mouseClicked(x,y,button);
    }
    @Override protected void keyTyped(char character,int key)throws IOException{
        if(listening!=null){
            if(key==Keyboard.KEY_ESCAPE){listening=null;capture.reset();notice="Binding unchanged.";initGui();return;}
            if(!captureReady())return;
            if(key==Keyboard.KEY_DELETE||key==Keyboard.KEY_BACK){bind(0,KeyModifier.NONE);return;}
            if(key==Keyboard.KEY_LCONTROL||key==Keyboard.KEY_RCONTROL||key==Keyboard.KEY_LSHIFT||key==Keyboard.KEY_RSHIFT||key==Keyboard.KEY_LMENU||key==Keyboard.KEY_RMENU)return;
            if(key>0)bind(key,KeyModifier.getActiveModifier());return;
        }
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}
        List<GuiButton> available=new ArrayList<>();for(GuiButton b:buttonList)if(b.visible&&b.enabled)available.add(b);
        if(key==Keyboard.KEY_TAB){int index=-1;for(int i=0;i<available.size();i++)if(available.get(i).id==focus)index=i;focus=available.get(NarutoMenuLayout.nextFocus(index,isShiftKeyDown()?-1:1,available.size())).id;return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){for(GuiButton b:available)if(b.id==focus){b.playPressSound(mc.getSoundHandler());actionPerformed(b);return;}}
        super.keyTyped(character,key);
    }
    @Override public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();NarutoInk.paper(layout.panel.x,layout.panel.y,layout.panel.w,layout.panel.h);
        NarutoInk.title(layout.panel,"NARUTO | Shinobi settings",page==2?"Your synchronized field record":"Paper, ink and clear combat information");
        if(!layout.usable){java.util.List<String> small=fontRenderer.listFormattedStringToWidth("Increase the window size or reduce GUI scale to show all Naruto settings.",Math.max(1,layout.panel.w-20));for(int i=0;i<Math.min(3,small.size());i++)fontRenderer.drawString(small.get(i),layout.panel.x+10,layout.panel.y+42+i*10,NarutoInk.INK,false);super.drawScreen(mx,my,partial);return;}
        int hovered=focus;
        if(page<2){String[] labels=page==0?PRESENT:CONTROL;
            for(int i=0;i<8;i++){Layout.Box r=layout.row(i),v=layout.value(i);fontRenderer.drawString(fontRenderer.trimStringToWidth(labels[i],Math.max(1,v.x-r.x-5)),r.x+2,r.y+(r.h-8)/2,NarutoInk.INK,false);}
        }else drawRecord(mx,my);
        for(GuiButton b:buttonList){if(b instanceof NarutoInk.Button)((NarutoInk.Button)b).focused=b.id==focus;if(mx>=b.x&&my>=b.y&&mx<b.x+b.width&&my<b.y+b.height)hovered=b.id;}
        super.drawScreen(mx,my,partial);
        List<String> lines=fontRenderer.listFormattedStringToWidth(help(hovered),layout.help.w);
        for(int i=0;i<Math.min(2,lines.size());i++)fontRenderer.drawString(lines.get(i),layout.help.x,layout.help.y+i*8,NarutoInk.MUTED,false);
    }
    private void drawRecord(int mx,int my){
        ValeSnapshotReader.State state=ValeBridge.hasFreshLocal()?ValeBridge.current():null;
        String[] labels={"Shinobi level","Training grade","Mission merit","Minecraft XP","Selected jutsu","Transformation","Mission journal","Learned arts"};
        String[] values={state==null?"Syncing":Integer.toString(state.level),state==null?"Awaiting record":TrainingGrade.forLevel(state.level),state==null?"Syncing":Long.toString(state.merit),mc.player==null?"No world":"Lv "+mc.player.experienceLevel+" / "+mc.player.experienceTotal+" XP",state==null||state.artName.isEmpty()?"None selected":state.artName,state==null||state.formName.isEmpty()?"None active":state.formName,"",""};
        for(int i=0;i<8;i++){
            Layout.Box r=layout.row(i);fontRenderer.drawString(fontRenderer.trimStringToWidth(labels[i],i<6?r.w:layout.value(i).x-r.x-5),r.x+2,r.y+2,NarutoInk.MUTED,false);
            if(i<6){String text=values[i];fontRenderer.drawString(fontRenderer.trimStringToWidth(text,r.w-4),r.x+2,r.y+13,NarutoInk.INK,false);if(mx>=r.x&&my>=r.y&&mx<r.x+r.w&&my<r.y+r.h&&fontRenderer.getStringWidth(text)>r.w-4)drawHoveringText(Collections.singletonList(text),mx,my);}
        }
        if(notice.isEmpty())notice="Training grade tracks your shinobi level. Mission merit records completed work.";
    }
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override public void onGuiClosed(){listening=null;capture.reset();KeyBinding.unPressAllKeys();super.onGuiClosed();}
}
