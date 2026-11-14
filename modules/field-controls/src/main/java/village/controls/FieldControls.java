package village.controls;

import java.io.File;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.*;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.*;
import net.minecraftforge.fml.common.gameevent.*;
import org.lwjgl.input.Keyboard;

@Mod(modid=FieldControls.ID,name="Field Settings & Controls",version="1.5.0",clientSideOnly=true,acceptableRemoteVersions="*",acceptedMinecraftVersions="[1.12.2]")
public final class FieldControls {
    public static final String ID="fieldcontrols";
    public static ProfileStore store;
    public static final KeyBinding OPEN=new KeyBinding("key.fieldcontrols.open",KeyConflictContext.IN_GAME,Keyboard.KEY_F8,"key.categories.fieldcontrols");
    public static final Map<KeyBinding,String> COMMANDS=new LinkedHashMap<>();
    public static String startupWarning="";
    private static final Set<GuiScreen> NATIVE_OPTIONS=Collections.newSetFromMap(new WeakHashMap<GuiScreen,Boolean>());
    static void allowNative(GuiScreen screen){NATIVE_OPTIONS.add(screen);}
    private boolean loaded;
    private KeyBinding[] watchedBindings;
    private final Set<String> registeredIds=new HashSet<>();
    private int watchTicks;
    private long lastCommand;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        if(!event.getSide().isClient())return;
        store=new ProfileStore(new File(event.getModConfigurationDirectory(),"field-controls/profiles.json").toPath());store.load();
        ClientRegistry.registerKeyBinding(OPEN);MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override public String getName(){return "fieldcontrols";}
            @Override public List<String> getAliases(){return Arrays.asList("binds");}
            @Override public String getUsage(ICommandSender s){return "/fieldcontrols";}
            @Override public int getRequiredPermissionLevel(){return 0;}
            @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args) {
                Minecraft mc=Minecraft.getMinecraft();mc.addScheduledTask(()->open(null));
            }
        });
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override public String getName(){return "fieldsettings";}
            @Override public String getUsage(ICommandSender s){return "/fieldsettings";}
            @Override public int getRequiredPermissionLevel(){return 0;}
            @Override public void execute(MinecraftServer server,ICommandSender sender,String[] args){Minecraft.getMinecraft().addScheduledTask(()->openSettings(null));}
        });
        if(Boolean.getBoolean("fieldcontrols.capture")) {
            try { Class.forName(Boolean.getBoolean("fieldcontrols.settingscheck")?"village.controls.SettingsNativeChecks":"village.controls.NativeChecks").getMethod("start").invoke(null); }
            catch(Exception ex){throw new IllegalStateException("The local capture harness could not start",ex);}
        }
    }
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if(event.getSide().isClient()&&Loader.isModLoaded("valeorders")) {
            shortcut("status","status");
            try {
                Class.forName("village.vale.AdventureCommands",false,Loader.instance().getModClassLoader());
                shortcut("cast","art cast");shortcut("journal","journal");shortcut("recall","summon dismiss");shortcut("arts","arts");
            }catch(ClassNotFoundException ignored) { /* Older Vale builds only expose status. */ }
        }
    }
    private static void shortcut(String key,String command) {
        KeyBinding binding=new KeyBinding("key.fieldcontrols."+key,KeyConflictContext.IN_GAME,0,"key.categories.fieldcontrols");
        COMMANDS.put(binding,"/vale "+command);ClientRegistry.registerKeyBinding(binding);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void gui(GuiOpenEvent event) {
        GuiScreen next=event.getGui();
        if(EssentialBridge.returning(Minecraft.getMinecraft().currentScreen,next)){event.setGui(EssentialBridge.takeParent());return;}
        EssentialBridge.observe(Minecraft.getMinecraft().currentScreen,next);
        if(HudBridge.returning(Minecraft.getMinecraft().currentScreen,next)){event.setGui(HudBridge.legacyParent);HudBridge.legacyParent=null;return;}
        GuiScreen old=Minecraft.getMinecraft().currentScreen;
        if(HudBridge.legacyParent!=null&&old!=null&&old.getClass().getName().startsWith("village.fieldhud.")&&next!=null&&!next.getClass().getName().startsWith("village.fieldhud."))HudBridge.legacyParent=null;
        if(next instanceof GuiOptions&&!(next instanceof SettingsHub)) {
            if(NATIVE_OPTIONS.contains(next))return;
            GuiScreen hub=HudBridge.settingsScreen(Minecraft.getMinecraft().currentScreen);event.setGui(hub==null?new SettingsHub(Minecraft.getMinecraft().currentScreen):hub);return;
        }
        // Controlling 3.x subclasses GuiControls; replacement happens after its hook.
        if(next instanceof GuiControls)event.setGui(new ControlsScreen(Minecraft.getMinecraft().currentScreen));
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getMinecraft();if(mc.gameSettings==null)return;
        if(loaded){watchLateBindings(mc);return;}
        if(mc.currentScreen==null)return;
        loaded=true;
        try {
            Bindings bridge=new Bindings();bridge.repairDuplicateRegistration();
            watchedBindings=mc.gameSettings.keyBindings;registeredIds.addAll(bridge.keys.keySet());
            boolean legacy=Loader.isModLoaded("narutomod"),vale=Loader.isModLoaded("valeorders");
            store.selectContext(legacy?(vale?"hybrid":"legacy"):vale?"vale":"generic");
            boolean captured=store.data.original.isEmpty(),newOriginal=false;Map<String,Model.Stroke> startup=bridge.snapshot();
            for(Model.Action action:bridge.catalog.actions)if(!store.data.original.containsKey(action.id)){store.data.original.put(action.id,startup.get(action.id));newOriginal=true;}
            if(store.data.active.isEmpty()&&Arrays.asList("ACTION","MMO","CLASSIC").contains(store.data.initialPreset)) {
                Model.Preset requested=Model.Preset.valueOf(store.data.initialPreset);
                Model.Session setup=new Model.Session(bridge.catalog,bridge.snapshot());
                Model.Plan plan=Model.plan(bridge.catalog,setup.draft,requested);
                if(plan.unresolved>0)throw new IllegalStateException("The installed actions need additional spare keys");
                setup.replace(plan.values,requested.title);Bindings.MouseState mouse=new Bindings.MouseState(mc.gameSettings);if(requested!=Model.Preset.CLASSIC)mouse.autoJump=false;
                store.data.initialPreset="";bridge.save(setup,mouse);
                startupWarning=requested.title+" layout applied. Previous keys are in Startup backup.";openSettings(mc.currentScreen);
            } else {
                if(!store.data.active.isEmpty()) {
                    boolean added=false;for(Model.Action action:bridge.catalog.actions)added|=!store.data.active.containsKey(action.id);
                    bridge.install(store.data.active);
                    if(added){
                        Model.Session update=new Model.Session(bridge.catalog,bridge.snapshot());update.layout=store.data.layout;
                        if(Arrays.asList("Action","MMO","Classic").contains(update.layout)&&update.hardCount()>0){
                            Model.Plan repaired=Model.repair(bridge.catalog,update.draft);if(repaired.unresolved>0)throw new IllegalStateException("New mod inputs need additional spare keys");update.replace(repaired.values,update.layout);
                        } else if(update.hardCount()>0)startupWarning="New mod input added. Review sharing in Controls.";
                        bridge.save(update,new Bindings.MouseState(mc.gameSettings));
                    }else if(newOriginal)store.save();
                }else if(captured||newOriginal)store.save();
            }
        } catch(Exception ex){startupWarning="Startup restore failed; open Profiles to recover. "+ex.getClass().getSimpleName();}
    }
    private void watchLateBindings(Minecraft mc) {
        if(++watchTicks%20!=0||watchedBindings==mc.gameSettings.keyBindings||mc.currentScreen instanceof ControlsScreen)return;
        watchedBindings=mc.gameSettings.keyBindings;
        try {
            Bindings bridge=new Bindings();Map<String,Model.Stroke> current=bridge.snapshot(),restore=new LinkedHashMap<>();
            for(Model.Action action:bridge.catalog.actions)if(!registeredIds.contains(action.id)){
                if(!store.data.original.containsKey(action.id))store.data.original.put(action.id,current.get(action.id));
                if(store.data.active.containsKey(action.id))restore.put(action.id,store.data.active.get(action.id));
            }
            registeredIds.clear();registeredIds.addAll(bridge.keys.keySet());bridge.install(restore);
            if(store.data.active.isEmpty()){store.save();return;}
            Model.Session session=new Model.Session(bridge.catalog,bridge.snapshot());session.layout=store.data.layout;
            if(Arrays.asList("Action","MMO","Classic").contains(session.layout)&&session.hardCount()>0){
                Model.Plan repaired=Model.repair(bridge.catalog,session.draft);
                if(repaired.unresolved>0)throw new IllegalStateException("New mod inputs need additional spare keys");
                session.replace(repaired.values,session.layout);
            }
            bridge.save(session,new Bindings.MouseState(mc.gameSettings));
        }catch(Exception ex){startupWarning="A newly registered input needs review in Controls. "+ex.getClass().getSimpleName();}
    }
    @SubscribeEvent public void keys(InputEvent.KeyInputEvent event){input();}
    @SubscribeEvent public void mouse(InputEvent.MouseInputEvent event){input();}
    private void input() {
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.currentScreen!=null)return;
        if(OPEN.isPressed()){openSettings(null);return;}
        if(mc.player==null)return;
        for(Map.Entry<KeyBinding,String> entry:COMMANDS.entrySet())if(entry.getKey().isPressed()) {
            // Exactly one server-validated action per press, with repeat protection.
            long now=Minecraft.getSystemTime();if(now-lastCommand<180)return;lastCommand=now;
            if(entry.getValue().equals("/vale art cast")&&FocusedCast.consume(Loader.instance().getModClassLoader(),failure->net.minecraftforge.fml.common.FMLLog.log.warn("The focused jutsu request could not be routed; the old selected art was not cast",failure)))return;
            mc.player.sendChatMessage(entry.getValue());
        }
    }
    public static void open(GuiScreen parent){openSettings(parent);}
    public static void openSettings(GuiScreen parent){if(parent!=null&&parent.getClass().getName().equals("village.fieldhud.HudScreen")){Minecraft.getMinecraft().displayGuiScreen(parent);return;}GuiScreen hub=HudBridge.settingsScreen(parent);Minecraft.getMinecraft().displayGuiScreen(hub==null?new SettingsHub(parent):hub);}
    /** Complete transactional binding editor; child of the canonical Naruto hub. */
    public static void openBindings(GuiScreen parent,String section){
        ControlsScreen screen=new ControlsScreen(parent);
        if(Arrays.asList("Bindings","Presets","Profiles","Mouse").contains(section))screen.page=section;
        Minecraft.getMinecraft().displayGuiScreen(screen);
    }
    /** Complete native settings draft; never replaces bindings with a five-key subset. */
    public static void openPreferences(GuiScreen parent,String section){Minecraft.getMinecraft().displayGuiScreen(new SettingsHub(parent,section));}
}
