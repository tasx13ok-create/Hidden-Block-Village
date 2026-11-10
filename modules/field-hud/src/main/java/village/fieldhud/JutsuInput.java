package village.fieldhud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/** Focused wheel/use routing; normal game/menu input passes through otherwise. */
public final class JutsuInput {
    private static final Minecraft MC=Minecraft.getMinecraft();
    public static final KeyBinding FOCUS=new KeyBinding("key.fieldhud.jutsu_focus",KeyConflictContext.IN_GAME,KeyModifier.NONE,Keyboard.KEY_F,"key.categories.fieldhud");
    private static long lastRequest=-1000,lastFeedback=-1000;
    private static Boolean compatibleControls;
    private JutsuInput(){}
    public static void start(){ClientRegistry.registerKeyBinding(FOCUS);net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new JutsuInput());}
    public static String focusLabel(){return FOCUS.getKeyCode()==0?"Unbound":FOCUS.getDisplayName();}
    public static String castLabel(){
        for(KeyBinding key:MC.gameSettings.keyBindings)if(key.getKeyDescription().equals("key.fieldcontrols.cast")&&key.getKeyCode()!=0)return key.getDisplayName();
        return useLabel();
    }
    public static String useLabel(){return MC.gameSettings.keyBindUseItem.getDisplayName();}
    public static boolean controlsCompatible(){
        if(compatibleControls==null){
            net.minecraftforge.fml.common.ModContainer controls=net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get("fieldcontrols");
            compatibleControls=JutsuControlsCapability.supported(controls!=null,net.minecraftforge.fml.common.Loader.instance().getModClassLoader());
        }
        return compatibleControls;
    }
    private static boolean game(){return MC.player!=null&&MC.world!=null&&MC.player.isEntityAlive()&&!MC.player.isSpectator()&&MC.currentScreen==null&&MC.inGameHasFocus&&!MC.isGamePaused();}
    private static JutsuRailLayout layout(){ScaledResolution sr=new ScaledResolution(MC);return JutsuRail.layout(sr.getScaledWidth(),sr.getScaledHeight());}
    private static boolean focused(){return RackAccess.loaded()?RackAccess.MODEL.focused():JutsuAccess.MODEL.focused();}
    private static void focus(boolean value){if(RackAccess.loaded())RackAccess.MODEL.focus(value,JutsuAccess.time(),HudConfig.motion);else JutsuAccess.MODEL.focus(value,JutsuAccess.time(),HudConfig.motion);}
    private static boolean eligible(){return game()&&JutsuRail.visible()&&(RackAccess.loaded()?RackAccess.ready():JutsuAccess.fresh()&&JutsuAccess.MODEL.hasAssignments())&&layout().box!=null;}
    private static void clearUse(){
        KeyBinding use=MC.gameSettings.keyBindUseItem;
        while(use.isPressed()){}
        KeyBinding.setKeyBindState(use.getKeyCode(),false);
    }
    private static void feedback(String value){
        JutsuRail.hint(value);
        if(!JutsuRail.visible()||(!RackAccess.loaded()&&!JutsuAccess.MODEL.initialized())||layout().box==null||layout().footer==0){
            long now=Minecraft.getSystemTime();if(now-lastFeedback<600)return;lastFeedback=now;
            ReadinessHud.suspend(40);MC.player.sendStatusMessage(new TextComponentString(value),true);
        }
    }
    public static void tick(){
        if(RackAccess.loaded()&&JutsuAccess.MODEL.focused())JutsuAccess.MODEL.focus(false,JutsuAccess.time(),false);
        if(focused()&&!eligible()){focus(false);clearUse();}
        if(focused())clearUse();
        if(MC.player==null||MC.world==null){lastRequest=-1000;lastFeedback=-1000;}
    }
    public static boolean castFocused(){
        if(!focused())return false;
        if(!eligible()){
            if(MC.player!=null)feedback(RackAccess.loaded()?"Weapon storage is syncing":"Jutsu bar unavailable");
            focus(false);clearUse();return true;
        }
        if(RackAccess.loaded()){
            if(MC.player.isHandActive()){feedback("Finish using the held item first");return true;}
            int slot=RackAccess.MODEL.index();
            if(RackAccess.stack(slot).isEmpty()){feedback("Empty weapon slot - open Storage in HUD settings");return true;}
            long now=Minecraft.getSystemTime();if(now-lastRequest<180)return true;lastRequest=now;
            if(!RackAccess.requestEquip(slot))feedback("Weapon storage is syncing");
            return true;
        }
        JutsuEntry chosen=JutsuAccess.MODEL.selected();
        if(chosen==null){feedback(JutsuAccess.MODEL.loadout().slots.get(JutsuAccess.MODEL.index()).emptySlot()?"Empty slot - edit Loadout in HUD settings":"Not carried or unavailable");return true;}
        if(!chosen.canCast){feedback(chosen.blockReason());return true;}
        long now=Minecraft.getSystemTime();if(now-lastRequest<180)return true;lastRequest=now;
        if(!JutsuAccess.request(chosen))feedback("Jutsu bar is syncing");
        return true;
    }
    private static void binding(boolean keyboardEvent){
        boolean pressed=false;while(FOCUS.isPressed())pressed=true;
        if(!pressed||(keyboardEvent&&Keyboard.isRepeatEvent())||!game())return;
        if(focused()){focus(false);clearUse();return;}
        for(KeyBinding other:MC.gameSettings.keyBindings)if(other!=FOCUS&&other.getKeyCode()==FOCUS.getKeyCode()&&other.isActiveAndMatches(FOCUS.getKeyCode())&&FOCUS.conflicts(other)){
            feedback("Choose a free jutsu bar key in Controls");return;
        }
        if(!eligible()){
            if(RackAccess.loaded()){feedback(!HudConfig.jutsuRail?"Enable Weapon slots in HUD settings":!controlsCompatible()?"Update Controls to use weapon slots":!JutsuRail.visible()?"Show the HUD to use weapon slots":!RackAccess.ready()?"Weapon storage is syncing":"Move the minimap up or lower GUI scale");return;}
            feedback(!HudConfig.jutsuRail?"Enable Jutsu bar in HUD settings":!controlsCompatible()?"Update Controls to use the jutsu bar":!JutsuRail.visible()?"Show the HUD to use the jutsu bar":!JutsuAccess.supported()?"Jutsu bar unavailable on this server":!JutsuAccess.fresh()?"Jutsu bar is syncing":!JutsuAccess.MODEL.hasAssignments()?"Choose six slots in HUD settings":"Move the minimap up or lower GUI scale");return;
        }
        if(MC.player.isHandActive()){feedback("Finish using the held item first");return;}
        clearUse();focus(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void key(InputEvent.KeyInputEvent event){
        binding(true);
        KeyBinding use=MC.gameSettings.keyBindUseItem;
        if(focused()&&game()&&use.getKeyCode()>0&&Keyboard.getEventKeyState()&&use.isActiveAndMatches(Keyboard.getEventKey())){
            boolean repeat=Keyboard.isRepeatEvent();clearUse();if(!repeat)castFocused();
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void mouseBinding(InputEvent.MouseInputEvent event){binding(false);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void mouse(MouseEvent event){
        if(!focused()||!eligible())return;
        if(event.getDwheel()!=0){
            if(RackAccess.loaded()){
                event.setCanceled(true);int slot=RackAccess.MODEL.wheelTarget(event.getDwheel());
                if(!RackAccess.select(slot))feedback("Weapon storage is syncing");return;
            }
            if(JutsuAccess.MODEL.wheel(event.getDwheel(),layout().visible,JutsuAccess.time(),HudConfig.motion))event.setCanceled(true);
            return;
        }
        if(event.getButton()>=0&&MC.gameSettings.keyBindUseItem.getKeyCode()==event.getButton()-100){
            clearUse();event.setCanceled(true);if(event.isButtonstate())castFocused();
        }
    }
}
