package village.controls;

import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.SoundCategory;
import village.controls.SettingsModel.*;

/** Native settings and side effects; the hub reflows the configured interface scale. */
public final class NativeSettings implements Port {
    public final Minecraft mc;
    public final List<Def> defs=new ArrayList<>();
    private final Map<String,GameSettings.Options> options=new LinkedHashMap<>();
    private final Map<Path,byte[]> diskSnapshot=new LinkedHashMap<>();
    private GameSettings.Options optiClouds;
    private java.lang.reflect.Field optiCloudsField;
    private Integer originalCloudMode;
    public NativeSettings(Minecraft mc) {
        this.mc=mc;
        try{optiClouds=GameSettings.Options.valueOf("CLOUDS");optiCloudsField=GameSettings.class.getField("ofClouds");}
        catch(IllegalArgumentException|NoSuchFieldException unavailable){optiClouds=null;optiCloudsField=null;}
        number("Gameplay","Field of view","The visible angle of the game camera.",GameSettings.Options.FOV,70d);
        number("Gameplay","Look sensitivity","The same mouse sensitivity used by Minecraft controls.",GameSettings.Options.SENSITIVITY,.5d);
        bool("Gameplay","Invert vertical look","Reverse vertical mouse movement.",GameSettings.Options.INVERT_MOUSE,false);
        bool("Gameplay","Auto-jump","Jump automatically at one-block obstacles.",GameSettings.Options.AUTO_JUMP,false);
        bool("Gameplay","View bobbing","Animate the camera while moving.",GameSettings.Options.VIEW_BOBBING,true);
        bool("Gameplay","Touchscreen input","Use Minecraft's touch-oriented inventory behavior.",GameSettings.Options.TOUCHSCREEN,false);
        choice("Gameplay","Main hand","Choose which hand is dominant.",GameSettings.Options.MAIN_HAND,1,"Left","Right");
        number("Video","Render distance","How far chunks are drawn. Servers may impose their own limit.",GameSettings.Options.RENDER_DISTANCE,8d);
        number("Video","Frame-rate limit","Choose a frame cap. OptiFine can reset it to Unlimited when loading options with VSync enabled.",GameSettings.Options.FRAMERATE_LIMIT,120d);
        number("Video","Brightness","Native gamma setting; shader packs can have additional exposure controls.",GameSettings.Options.GAMMA,0d);
        choice("Video","Graphics quality","Minecraft's Fast or Fancy graphics mode.",GameSettings.Options.GRAPHICS,1,"Fast","Fancy");
        choice("Video","Smooth lighting","Native ambient-occlusion quality.",GameSettings.Options.AMBIENT_OCCLUSION,2,"Off","Minimum","Maximum");
        choice("Video","Clouds","Choose the native cloud renderer.",GameSettings.Options.RENDER_CLOUDS,2,"Off","Fast","Fancy");
        choice("Video","Particles","Control the amount of particle effects.",GameSettings.Options.PARTICLES,0,"All","Decreased","Minimal");
        bool("Video","Vertical sync","Synchronize frames with the monitor.",GameSettings.Options.ENABLE_VSYNC,true);
        bool("Video","Vertex buffers","Use Minecraft's VBO rendering path.",GameSettings.Options.USE_VBO,true);
        bool("Video","Entity shadows","Draw shadows under entities.",GameSettings.Options.ENTITY_SHADOWS,true);
        number("Video","Mipmap levels","Texture filtering levels. Applying may reload textures.",GameSettings.Options.MIPMAP_LEVELS,4d);
        for(SoundCategory category:SoundCategory.values()) {
            String label=category==SoundCategory.MASTER?"Master volume":category==SoundCategory.RECORDS?"Music discs":category==SoundCategory.NEUTRAL?"Friendly creatures":category==SoundCategory.HOSTILE?"Hostile creatures":category==SoundCategory.BLOCKS?"Blocks":category==SoundCategory.PLAYERS?"Players":category==SoundCategory.VOICE?"Voice":title(category.getName());
            defs.add(new Def("sound."+category.getName(),"Audio",label,category==SoundCategory.VOICE?"Native voice category, used by mods that support it.":"Native "+label.toLowerCase(Locale.ROOT)+" channel volume.",Kind.NUMBER,0,1,.01,1d));
        }
        bool("Audio","Subtitles","Show Minecraft's sound captions.",GameSettings.Options.SHOW_SUBTITLES,false);
        choice("Interface","Interface scale","Scale all menus and HUD elements. Unicode text can use a smaller effective scale.",GameSettings.Options.GUI_SCALE,0,"Auto","Small","Normal","Large");
        bool("Interface","Unicode font","Use the native Unicode font renderer.",GameSettings.Options.FORCE_UNICODE_FONT,false);
        choice("Interface","Attack indicator","Show attack recharge at the crosshair or hotbar.",GameSettings.Options.ATTACK_INDICATOR,1,"Off","Crosshair","Hotbar");
        choice("Interface","Chat visibility","Choose which chat messages are visible.",GameSettings.Options.CHAT_VISIBILITY,0,"Shown","Commands only","Hidden");
        bool("Interface","Chat colors","Display formatting colors in chat.",GameSettings.Options.CHAT_COLOR,true);
        number("Interface","Chat text size","Scale chat text and its panel.",GameSettings.Options.CHAT_SCALE,1d);
        number("Interface","Chat opacity","The native chat background/text opacity.",GameSettings.Options.CHAT_OPACITY,1d);
        number("Interface","Chat width","The horizontal size of the chat panel.",GameSettings.Options.CHAT_WIDTH,1d);
        number("Interface","Focused chat height","The height while typing in chat.",GameSettings.Options.CHAT_HEIGHT_FOCUSED,1d);
        number("Interface","Unfocused chat height","The height of recent chat during play.",GameSettings.Options.CHAT_HEIGHT_UNFOCUSED,.44366196d);
    }
    private static String title(String s){return s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private void number(String page,String label,String help,GameSettings.Options option,Object defaults) {
        options.put(option.name(),option);
        double min=option==GameSettings.Options.FRAMERATE_LIMIT?10:option.getValueMin(),max=option.getValueMax();
        double step=option==GameSettings.Options.FOV||option==GameSettings.Options.RENDER_DISTANCE||option==GameSettings.Options.MIPMAP_LEVELS?1:option==GameSettings.Options.FRAMERATE_LIMIT?10:.01;
        defs.add(new Def(option.name(),page,label,help,Kind.NUMBER,min,max,step,defaults));
    }
    private void bool(String page,String label,String help,GameSettings.Options option,boolean defaults){options.put(option.name(),option);defs.add(new Def(option.name(),page,label,help,Kind.BOOLEAN,0,1,1,defaults));}
    private void choice(String page,String label,String help,GameSettings.Options option,int defaults,String... choices){options.put(option.name(),option);defs.add(new Def(option.name(),page,label,help,Kind.CHOICE,0,choices.length-1,1,defaults,choices));}
    @Override public Map<String,Object> read() {
        Map<String,Object> values=new LinkedHashMap<>();GameSettings gs=mc.gameSettings;
        for(Def d:defs) {
            if(d.id.startsWith("sound.")){values.put(d.id,(double)gs.getSoundLevel(SoundCategory.getByName(d.id.substring(6))));continue;}
            GameSettings.Options option=options.get(d.id);Object value;
            if(d.kind==Kind.NUMBER)value=numberValue(gs,option);
            else if(d.kind==Kind.BOOLEAN)value=gs.getOptionOrdinalValue(option);
            else switch(option) {
                case GRAPHICS:value=gs.fancyGraphics?1:0;break;
                case AMBIENT_OCCLUSION:value=gs.ambientOcclusion;break;
                case RENDER_CLOUDS:value=gs.clouds;break;
                case PARTICLES:value=gs.particleSetting;break;
                case GUI_SCALE:value=gs.guiScale;break;
                case ATTACK_INDICATOR:value=gs.attackIndicator;break;
                case CHAT_VISIBILITY:value=gs.chatVisibility.getChatVisibility();break;
                case MAIN_HAND:value=gs.mainHand==net.minecraft.util.EnumHandSide.RIGHT?1:0;break;
                default:throw new IllegalStateException("Unsupported native choice: "+d.id);
            }
            values.put(d.id,value);
        }
        return values;
    }
    private static double numberValue(GameSettings gs,GameSettings.Options option) {
        // OptiFine's slider getter can encode VSync as FPS zero. These pages
        // expose stored FPS and VSync independently, and verify the real file.
        switch(option) {
            case FOV:return gs.fovSetting;
            case SENSITIVITY:return gs.mouseSensitivity;
            case GAMMA:return gs.gammaSetting;
            case RENDER_DISTANCE:return gs.renderDistanceChunks;
            case FRAMERATE_LIMIT:return gs.limitFramerate;
            case MIPMAP_LEVELS:return gs.mipmapLevels;
            case CHAT_SCALE:return gs.chatScale;
            case CHAT_OPACITY:return gs.chatOpacity;
            case CHAT_WIDTH:return gs.chatWidth;
            case CHAT_HEIGHT_FOCUSED:return gs.chatHeightFocused;
            case CHAT_HEIGHT_UNFOCUSED:return gs.chatHeightUnfocused;
            default:throw new IllegalArgumentException("Unsupported numeric setting: "+option);
        }
    }
    @Override public void write(String id,Object value) {
        GameSettings gs=mc.gameSettings;
        if(id.startsWith("sound.")){gs.setSoundLevel(SoundCategory.getByName(id.substring(6)),((Number)value).floatValue());return;}
        GameSettings.Options option=options.get(id);
        // Native cycling can skip odd targets while a Unicode font is active.
        // Store the configured value; ScaledResolution applies the native font
        // constraint and SettingsHub reflows after persistence, including undo.
        if(option==GameSettings.Options.GUI_SCALE){gs.guiScale=((Number)value).intValue();return;}
        if(option==GameSettings.Options.RENDER_CLOUDS&&optiClouds!=null){int wanted=((Number)value).intValue();selectCloudMode(wanted==0?3:wanted);return;}
        if(option.isFloat()){
            boolean vsync=gs.enableVsync;gs.setOptionFloatValue(option,((Number)value).floatValue());
            if(option==GameSettings.Options.FRAMERATE_LIMIT&&gs.enableVsync!=vsync){gs.enableVsync=vsync;org.lwjgl.opengl.Display.setVSyncEnabled(vsync);}
            return;
        }
        for(int attempts=0;attempts<64;attempts++) {
            if(SettingsModel.equal(read().get(id),value))return;
            int storedFrameLimit=gs.limitFramerate;
            gs.setOptionValue(option,1);
            // OptiFine's VSync button also resets the FPS cap to Unlimited.
            // This shell exposes the two settings independently.
            if(option==GameSettings.Options.ENABLE_VSYNC)gs.limitFramerate=storedFrameLimit;
        }
        throw new IllegalStateException("Could not select native setting: "+id);
    }
    @Override public void begin()throws IOException {
        diskSnapshot.clear();
        originalCloudMode=optiClouds==null?null:cloudMode();
        for(String name:new String[]{"options.txt","optionsof.txt"}){Path path=mc.gameDir.toPath().resolve(name);if(Files.exists(path))diskSnapshot.put(path,Files.readAllBytes(path));}
        Path options=mc.gameDir.toPath().resolve("options.txt");
        if(Files.exists(options)) {
            Path backup=FieldControls.store.file.getParent().resolve("backups/options-settings-"+System.currentTimeMillis()+".txt");
            Files.createDirectories(backup.getParent());Files.copy(options,backup,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    @Override public void persist()throws IOException {
        Path options=mc.gameDir.toPath().resolve("options.txt");
        mc.gameSettings.saveOptions();
        if(!Files.exists(options)||Files.size(options)==0)throw new IOException("Minecraft could not save its settings file.");
        verifyDisk(options);
        if(optiClouds!=null){
            Path of=mc.gameDir.toPath().resolve("optionsof.txt");boolean found=false;
            if(Files.exists(of))for(String line:Files.readAllLines(of,StandardCharsets.UTF_8))if(line.equals("ofClouds:"+cloudMode()))found=true;
            if(!found)throw new IOException("Minecraft did not persist the OptiFine cloud preference.");
        }
        mc.fontRenderer.setUnicodeFlag(mc.getLanguageManager().isCurrentLocaleUnicode()||mc.gameSettings.forceUnicodeFont);
        mc.fontRenderer.setBidiFlag(mc.getLanguageManager().isCurrentLanguageBidirectional());
        mc.gameSettings.onGuiClosed();
    }
    @Override public void rollbackFiles()throws IOException {if(originalCloudMode!=null)selectCloudMode(originalCloudMode);for(Map.Entry<Path,byte[]> e:diskSnapshot.entrySet())Files.write(e.getKey(),e.getValue());}
    private int cloudMode(){try{return optiCloudsField.getInt(mc.gameSettings);}catch(IllegalAccessException ex){throw new IllegalStateException("Could not read the installed cloud setting",ex);}}
    private void selectCloudMode(int wanted){
        for(int attempts=0;attempts<5;attempts++){if(cloudMode()==wanted)return;mc.gameSettings.setOptionValue(optiClouds,1);}
        throw new IllegalStateException("Could not apply the installed cloud setting");
    }
    private void verifyDisk(Path file)throws IOException {
        Map<String,String> disk=new HashMap<>();
        for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)){int colon=line.indexOf(':');if(colon>0)disk.put(line.substring(0,colon),line.substring(colon+1));}
        Map<String,Object> actual=read();
        String[][] names={
            {"FOV","fov"},{"SENSITIVITY","mouseSensitivity"},{"INVERT_MOUSE","invertYMouse"},{"AUTO_JUMP","autoJump"},{"VIEW_BOBBING","bobView"},{"TOUCHSCREEN","touchscreen"},{"MAIN_HAND","mainHand"},
            {"RENDER_DISTANCE","renderDistance"},{"FRAMERATE_LIMIT","maxFps"},{"GAMMA","gamma"},{"GRAPHICS","fancyGraphics"},{"AMBIENT_OCCLUSION","ao"},{"RENDER_CLOUDS","renderClouds"},{"PARTICLES","particles"},{"ENABLE_VSYNC","enableVsync"},{"USE_VBO","useVbo"},{"ENTITY_SHADOWS","entityShadows"},{"MIPMAP_LEVELS","mipmapLevels"},
            {"SHOW_SUBTITLES","showSubtitles"},{"GUI_SCALE","guiScale"},{"FORCE_UNICODE_FONT","forceUnicodeFont"},{"ATTACK_INDICATOR","attackIndicator"},{"CHAT_VISIBILITY","chatVisibility"},{"CHAT_COLOR","chatColors"},{"CHAT_SCALE","chatScale"},{"CHAT_OPACITY","chatOpacity"},{"CHAT_WIDTH","chatWidth"},{"CHAT_HEIGHT_FOCUSED","chatHeightFocused"},{"CHAT_HEIGHT_UNFOCUSED","chatHeightUnfocused"}
        };
        Map<String,String> mapping=new HashMap<>();for(String[] n:names)mapping.put(n[0],n[1]);
        for(Def d:defs) {
            String key=d.id.startsWith("sound.")?"soundCategory_"+d.id.substring(6):mapping.get(d.id),raw=disk.get(key);Object value;
            if(raw==null)throw new IOException("Missing saved setting: "+d.label);
            try {
                if(d.id.equals("FOV"))value=70+40*Double.parseDouble(raw);
                else if(d.id.equals("GRAPHICS"))value=Boolean.parseBoolean(raw)?1:0;
                else if(d.id.equals("RENDER_CLOUDS"))value=raw.equals("true")?2:raw.equals("fast")?1:0;
                else if(d.id.equals("MAIN_HAND"))value=raw.equals("right")?1:0;
                else if(d.kind==Kind.BOOLEAN)value=Boolean.parseBoolean(raw);
                else value=Double.parseDouble(raw);
            }catch(RuntimeException ex){throw new IOException("Invalid saved value for "+d.label,ex);}
            if(!SettingsModel.equal(actual.get(d.id),value))throw new IOException("Minecraft did not persist "+d.label);
        }
    }
}
