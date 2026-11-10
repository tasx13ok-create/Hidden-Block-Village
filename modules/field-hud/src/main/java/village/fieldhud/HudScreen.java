package village.fieldhud;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

/** Compact HUD preferences. Preserves immediate saves and all public button IDs. */
public final class HudScreen extends GuiScreen {
    private final GuiScreen parent;private HudMenuLayout layout;private int focus=-1;private String storageNotice="";
    private static final int[] IDS={0,17,15,16,1,2,3,4,9,10,11,12,14,6,5,19};
    private static final String[] LABELS={"Mode","Techniques","Glass","Look focus","Location","Target","Region cues","Durability","Scroll HUD","Motion","Curve","Eye map","Resolve","Margin","Chime","Width"};
    public HudScreen(){this(null);}public HudScreen(GuiScreen parent){this.parent=parent;}
    public void initGui(){
        buttonList.clear();layout=new HudMenuLayout(width,height);
        for(int i=0;i<IDS.length;i++){Layout.Box b=layout.value(i);buttonList.add(new MenuInk.Button(IDS[i],b.x,b.y,b.w,b.h,value(IDS[i])));}
        int[] ids={13,18,7,8};String[] labels={"Layout",RackAccess.loaded()?"Storage":"Loadout","Reload zones","Done"};
        for(int i=0;i<4;i++){
            Layout.Box b=layout.footer(i);GuiButton button=new MenuInk.Button(ids[i],b.x,b.y,b.w,b.h,i==2&&b.w<80?"Reload":labels[i]);
            if(ids[i]==13)button.enabled=HudConfig.shinobi&&mc.player!=null&&mc.world!=null;
            if(ids[i]==18)button.enabled=storageReady();buttonList.add(button);
        }
    }
    private boolean storageReady(){return mc.player!=null&&mc.world!=null&&(RackAccess.loaded()?RackAccess.ready():JutsuAccess.fresh());}
    public void updateScreen(){for(GuiButton button:buttonList)if(button.id==18)button.enabled=storageReady();}
    private String on(boolean value){return value?"On":"Off";}
    private String value(int id){switch(id){
        case 0:return HudConfig.MODES[HudConfig.mode];case 17:return on(HudConfig.jutsuRail);case 15:return HudConfig.glassBlur?"Frosted":"Clear";case 16:return on(HudConfig.focusEffects);
        case 1:return on(HudConfig.location);case 2:return on(HudConfig.target);case 3:return on(HudConfig.arrivals);case 4:return on(HudConfig.warning);case 9:return on(HudConfig.shinobi);case 10:return on(HudConfig.motion);
        case 11:return on(HudConfig.curve);case 12:return on(HudConfig.eyeMap);case 14:return on(HudConfig.chakra);case 6:return HudConfig.margin+" px";case 5:return on(HudConfig.chime);case 19:return HudConfig.slim?"Slim":"Full";default:return "";
    }}
    private String help(int id){switch(id){
        case 0:return "Choose a full or quieter display. HUD preferences save immediately.";
        case 17:return RackAccess.loaded()?"Show six weapon slots beneath the minimap. Focus and use a filled slot to equip; use an empty slot to stow your held weapon.":"Show six icon-only technique shortcuts. Edit their assignments in Loadout.";
        case 15:return "Choose frosted or clear HUD surfaces. Text and item icons remain sharp.";
        case 16:return "Subtle focus effects respond to actions. Turn them off for a calmer display.";
        case 1:return "Show your current area. Reload zones refreshes the local named-area definitions.";
        case 2:return "Show the target you are looking at, including its actual health.";
        case 3:return "Show brief arrival cues when you cross into a named area.";
        case 4:return "Warn when carried equipment is close to breaking.";
        case 9:return "Use the scroll and status display. Off returns to the native item bar.";
        case 10:return "Ease selection and focus transitions. Off removes HUD and owned-menu motion.";
        case 11:return "Use a shallow curve across the nine item slots. Off keeps a straight track.";
        case 12:return "Show the eye-map frame when supported. Move Xaero's map in its own settings.";
        case 14:return "Show the Resolve resource received from the server. This grants no resources.";
        case 6:return "Set a 4-24 pixel outer margin. Layout respects reserved screen areas.";
        case 5:return "Play a short region-arrival chime. Off keeps arrivals silent.";
        case 19:return "Choose a full or slim status panel. This is the same width preference as the layout editor.";
        case 13:return "Drag the live status panel. Shift snaps to four pixels. Requires a world and Scroll HUD.";
        case 18:return RackAccess.loaded()?(storageNotice.isEmpty()?"Six real weapon slots outside your inventory. Focus + use equips a stored weapon or stows your held weapon in an empty slot.":storageNotice):"Assign six learned actions and carried items. Requires a fresh server snapshot in a world.";
        case 7:return Regions.error.isEmpty()?"Reload local named-zone definitions. Other preferences are preserved.":Regions.error;
        default:return "Changes save immediately. Tab moves focus; Enter changes a value. Escape returns.";
    }}
    protected void actionPerformed(GuiButton button)throws IOException{
        focus=button.id;switch(button.id){
            case 0:HudConfig.mode=(HudConfig.mode+1)%3;break;case 1:HudConfig.location=!HudConfig.location;break;case 2:HudConfig.target=!HudConfig.target;break;case 3:HudConfig.arrivals=!HudConfig.arrivals;break;
            case 4:HudConfig.warning=!HudConfig.warning;break;case 5:HudConfig.chime=!HudConfig.chime;break;case 6:HudConfig.margin=HudConfig.margin>=24?4:HudConfig.margin+4;break;case 7:Regions.reload();break;
            case 8:mc.displayGuiScreen(parent);return;case 9:HudConfig.shinobi=!HudConfig.shinobi;break;case 10:HudConfig.motion=!HudConfig.motion;break;case 11:HudConfig.curve=!HudConfig.curve;break;
            case 12:HudConfig.eyeMap=!HudConfig.eyeMap;break;case 13:mc.displayGuiScreen(new HudLayoutScreen(this));return;case 14:HudConfig.chakra=!HudConfig.chakra;break;
            case 15:HudConfig.glassBlur=!HudConfig.glassBlur;break;case 16:HudConfig.focusEffects=!HudConfig.focusEffects;break;case 17:HudConfig.jutsuRail=!HudConfig.jutsuRail;break;
            case 18:
                if(RackAccess.loaded()){storageNotice=RackAccess.requestOpen()?"Opening weapon storage...":"Weapon storage is syncing. Try again when connected to the world.";return;}
                mc.displayGuiScreen(new JutsuLoadoutScreen(this));return;
            case 19:HudConfig.slim=!HudConfig.slim;break;default:return;
        }HudConfig.save();initGui();
    }
    public void drawScreen(int mx,int my,float partial){
        drawDefaultBackground();Layout.Box p=layout.panel;MenuInk.panel(p.x,p.y,p.w,p.h);MenuInk.header(p.x,p.y,p.w,"HUD settings");int hovered=focus;
        for(int i=0;i<IDS.length;i++){
            Layout.Box b=layout.cell(i),v=layout.value(i);boolean over=mx>=b.x&&my>=b.y&&mx<b.x+b.w&&my<b.y+b.h;
            MenuInk.row(b.x,b.y,b.w,b.h,focus==IDS[i]||over);String text=fontRenderer.trimStringToWidth(IDS[i]==17&&RackAccess.loaded()?"Weapon slots":LABELS[i],v.x-b.x-9);
            fontRenderer.drawString(text,b.x+4,b.y+(b.h-8)/2,MenuInk.INK,false);if(over)hovered=IDS[i];
        }
        for(GuiButton b:buttonList){if(b instanceof MenuInk.Button)((MenuInk.Button)b).focused=b.id==focus;if(mx>=b.x&&my>=b.y&&mx<b.x+b.width&&my<b.y+b.height)hovered=b.id;}
        super.drawScreen(mx,my,partial);List<String> lines=fontRenderer.listFormattedStringToWidth(help(hovered),layout.help.w);
        for(int i=0;i<Math.min(2,lines.size());i++)fontRenderer.drawString(lines.get(i),layout.help.x,layout.help.y+i*10,MenuInk.MUTED,false);
    }
    public boolean doesGuiPauseGame(){return false;}
    protected void keyTyped(char character,int key)throws IOException{
        if(key==Keyboard.KEY_ESCAPE){mc.displayGuiScreen(parent);return;}List<GuiButton> available=new ArrayList<>();for(GuiButton b:buttonList)if(b.enabled&&b.visible)available.add(b);
        if(key==Keyboard.KEY_TAB){int at=-1;for(int i=0;i<available.size();i++)if(available.get(i).id==focus)at=i;int delta=isShiftKeyDown()?-1:1;int next=at<0?(delta<0?available.size()-1:0):(at+delta+available.size())%available.size();focus=available.get(next).id;return;}
        if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_NUMPADENTER||key==Keyboard.KEY_SPACE){for(GuiButton b:available)if(b.id==focus){b.playPressSound(mc.getSoundHandler());actionPerformed(b);return;}}
        super.keyTyped(character,key);
    }
}
