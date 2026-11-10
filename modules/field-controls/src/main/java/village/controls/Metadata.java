package village.controls;

import java.util.*;
import village.controls.Model.*;

/** Small pack-specific dictionary; every other registered action remains accessible. */
public final class Metadata {
    public static final String[] GROUPS={"All actions","Essentials","Movement","Combat & arts","Inventory","World & map","HUD & social","Advanced"};
    private static final Map<String,String> LABELS=new HashMap<>();
    static {
        String[][] names={
            {"key.attack","Attack / break"},{"key.use","Use item / interact"},{"key.pickItem","Pick block"},
            {"key.forward","Move forward"},{"key.back","Move backward"},{"key.left","Move left"},{"key.right","Move right"},
            {"key.jump","Jump / ascend"},{"key.sneak","Crouch / descend"},{"key.sprint","Sprint"},{"key.inventory","Inventory"},
            {"key.drop","Drop item"},{"key.swapHands","Swap hands"},{"key.togglePerspective","Camera perspective"},
            {"key.mcreator.specialjutsu1","Primary jutsu"},{"key.mcreator.specialjutsu2","Secondary jutsu"},
            {"key.mcreator.specialjutsu3","Third jutsu"},{"key.mcreator.specialjutsu4","Fourth jutsu"},{"key.mcreator.specialjutsu5","Fifth jutsu"},
            {"key.mcreator.powerincrease","Switch jutsu / increase"},{"key.mcreator.chakra_charge_button","Charge chakra"},
            {"key.hvbijuu.activate","Activate armor ability"},{"key.fieldcontrols.open","Open settings"},
            {"key.fieldcontrols.cast","Activate selection"},{"key.fieldhud.jutsu_focus","Switch item / jutsu bar"},{"key.fieldcontrols.journal","Mission journal"},
            {"key.fieldcontrols.recall","Recall companion"},{"key.fieldcontrols.arts","Browse learned arts"},{"key.fieldcontrols.status","Character status"},
            {"key.fieldhud.settings","HUD settings & layout"},{"key.fieldhud.mode","Cycle HUD mode"},
            {"key.travelersbackpack.inventory","Open backpack"},{"key.travelersbackpack.toggle_tank","Switch backpack tank"},
            {"key.travelersbackpack.cycle_tool","Cycle backpack tool"},{"gui.xaero_open_map","World map"},
            {"gui.xaero_minimap_settings","Minimap settings"},{"gui.xaero_new_waypoint","Create waypoint"},
            {"gui.xaero_waypoints_key","Waypoint list"},{"gui.xaero_enlarge_map","Enlarge minimap"},
            {"key.ftbquests.quests","Quest book"},{"Quest Log","NPC quest log"},{"key.portaitreposition","Move target portrait"}
        };
        for(String[] pair:names)LABELS.put(pair[0],pair[1]);
        for(int i=1;i<=5;i++)LABELS.put("key.mcreator.special_jutsu_"+i,"Spring jutsu "+i);
        for(int i=1;i<=3;i++)LABELS.put("key.specialjutsu"+i,"Boruto jutsu "+i);
    }
    private Metadata() {}
    private static final List<String> ESSENTIALS=Arrays.asList("key.attack","key.use","key.fieldcontrols.cast","key.fieldhud.jutsu_focus","key.mcreator.specialjutsu1","key.mcreator.specialjutsu2","key.mcreator.specialjutsu3","key.hvbijuu.activate","key.mcreator.chakra_charge_button","key.mcreator.powerincrease","key.fieldcontrols.journal","key.inventory","key.sprint","key.sneak","key.jump","key.travelersbackpack.inventory","gui.xaero_open_map","key.togglePerspective","key.fieldhud.settings","key.fieldcontrols.open");
    public static boolean essential(String raw) { return ESSENTIALS.contains(raw); }
    public static int displayOrder(Action action) { int index=ESSENTIALS.indexOf(action.raw);return index<0?100+action.priority:index; }
    public static boolean required(String raw) { return Arrays.asList("key.forward","key.back","key.left","key.right","key.jump","key.attack","key.use","key.inventory").contains(raw); }
    public static String label(String raw,String translated) {
        if(LABELS.containsKey(raw))return LABELS.get(raw);
        if(translated!=null&&!translated.equals(raw))return translated;
        if(raw.startsWith("key.hotbar."))return "Hotbar slot "+raw.substring(raw.lastIndexOf('.')+1);
        String text=raw.replaceFirst("^(key|keys|gui|keybind|keybinds)\\.","");
        text=text.substring(text.lastIndexOf('.')+1).replace('_',' ').replace('-',' ').trim();
        return text.isEmpty()?raw:Character.toUpperCase(text.charAt(0))+text.substring(1);
    }
    public static String group(String raw,String cat,String translated) {
        String x=(raw+" "+cat+" "+translated).toLowerCase(Locale.ROOT);
        if(Arrays.asList("key.forward","key.back","key.left","key.right","key.jump","key.sprint","key.sneak").contains(raw))return "Movement";
        if(x.contains("jutsu")||x.contains("chakra")||x.contains("hvbijuu")||x.contains("rage")||x.contains("challenge")||x.contains("fieldcontrols.cast")||x.contains("fieldcontrols.recall")||raw.equals("key.attack")||raw.equals("key.use"))return "Combat & arts";
        if(x.contains("xaero")||x.contains("waypoint")||x.contains("map")||x.contains("warp"))return "World & map";
        if(x.contains("inventory")||x.contains("hotbar")||x.contains("jei")||x.contains("backpack")||raw.equals("key.swapHands")||raw.equals("key.drop")||raw.equals("key.pickItem"))return "Inventory";
        if(x.contains("hud")||x.contains("essential")||x.contains("chat")||x.contains("friend")||x.contains("cosmetic")||x.contains("emote")||x.contains("screenshot")||raw.equals("key.togglePerspective"))return "HUD & social";
        return "Advanced";
    }
    public static int priority(String raw,String group) {
        if(raw.equals("key.fieldhud.jutsu_focus"))return 31;
        if(raw.startsWith("key.hotbar.")||group.equals("Movement")||raw.equals("key.attack")||raw.equals("key.use"))return 0;
        if(Arrays.asList("key.inventory","key.chat","key.playerlist","key.command","key.screenshot","key.fullscreen","key.fieldcontrols.open").contains(raw))return 1;
        if(raw.startsWith("key.fieldcontrols."))return 6;
        if(Arrays.asList("key.hvbijuu.activate","key.travelersbackpack.inventory","gui.xaero_open_map","key.togglePerspective").contains(raw))return 7;
        if(raw.startsWith("key.mcreator.specialjutsu")||raw.contains("chakra_charge")||raw.contains("powerincrease"))return 8;
        if(group.equals("Combat & arts"))return 12;
        if(group.equals("Inventory"))return 20;
        if(group.equals("World & map"))return 25;
        if(group.equals("HUD & social"))return 30;
        return 40;
    }
    public static String help(String raw) {
        if(raw.equals("key.use"))return "Right-click uses the held item, interacts with the world and activates equipped weapon skills. Crouch + use triggers the Vale weapon technique.";
        if(raw.equals("key.hvbijuu.activate"))return "Activates the equipped armor's ability. Passive bonuses, tail assists and wing stamina remain controlled by the armor mod.";
        if(raw.equals("key.fieldcontrols.cast"))return "Activates the highlighted jutsu-bar action while that bar has focus; otherwise casts your selected Vale art. A weapon action can equip its assigned weapon. The server checks ownership, learning, gear, resources, cooldowns and targets. Each press requests one action.";
        if(raw.equals("key.fieldhud.jutsu_focus"))return "Switches between the item hotbar and jutsu bar. While the jutsu bar has focus, the wheel highlights a slot. Activate selection or your item-use input requests its action. Menus and chat release focus. The HUD provides this optional action.";
        if(raw.equals("key.fieldcontrols.journal"))return "Opens the server's mission journal in chat. No command typing is needed.";
        if(raw.equals("key.fieldcontrols.recall"))return "Sends /vale summon dismiss once to recall your own companion.";
        if(raw.equals("key.mcreator.chakra_charge_button"))return "Uses the installed chakra-charge action. Its hold behavior and resource rules belong to the gameplay mod.";
        if(raw.contains("special_jutsu_"))return "A separate Spring Addon action. This is not merged with a similarly named core jutsu; each keeps its own binding.";
        if(raw.contains("specialjutsu"))return "Uses the installed mod's registered jutsu action. This menu changes its input without changing the technique's mechanics.";
        if(raw.equals("key.sprint"))return "Hold to sprint. Action and MMO use Left Shift; Classic uses Left Ctrl. Movement behavior remains Minecraft's native behavior.";
        return "Click the key to rebind. Keyboard, mouse buttons and Shift/Ctrl/Alt chords are supported. Esc cancels capture; Delete clears the binding. Changes stay in preview until Apply.";
    }
    public static Stroke preferred(Action a,Preset p,Stroke old) {
        String r=a.raw;boolean classic=p==Preset.CLASSIC,mmo=p==Preset.MMO;
        if(r.equals("key.attack"))return new Stroke(-100);
        if(r.equals("key.use"))return new Stroke(-99);
        String[] movement={"key.forward","key.left","key.back","key.right","key.jump"};int[] codes={17,30,31,32,57};
        for(int i=0;i<movement.length;i++)if(r.equals(movement[i]))return new Stroke(codes[i]);
        if(r.equals("key.sprint"))return new Stroke(classic?29:42);
        if(r.equals("key.sneak"))return new Stroke(classic?42:29);
        if(r.equals("key.inventory"))return new Stroke(mmo?23:18);
        if(r.startsWith("key.hotbar.")){try{return new Stroke(Integer.parseInt(r.substring(r.lastIndexOf('.')+1))+1);}catch(NumberFormatException ignored){}}
        String[] raw={"key.chat","key.playerlist","key.command","key.screenshot","key.fullscreen","key.fieldcontrols.open","key.hvbijuu.activate","key.mcreator.chakra_charge_button","key.mcreator.powerincrease","key.travelersbackpack.inventory","gui.xaero_open_map","gui.xaero_minimap_settings","gui.xaero_new_waypoint","gui.xaero_waypoints_key","key.ftbquests.quests","Quest Log","key.fieldhud.settings","key.fieldhud.mode","key.fieldcontrols.journal","key.fieldcontrols.recall","key.fieldcontrols.arts","key.fieldcontrols.status"};
        int[] code={20,15,53,60,87,66,34,46,41,48,50,27,25,22,38,38,64,65,36,49,37,46};
        for(int i=0;i<raw.length;i++)if(r.equals(raw[i]))return new Stroke(code[i]);
        if(r.equals("key.fieldcontrols.cast"))return new Stroke(19);
        if(r.equals("key.togglePerspective"))return new Stroke(classic?63:47);
        if(r.equals("key.drop"))return new Stroke(classic?16:211);
        if(r.equals("key.swapHands"))return new Stroke(classic?33:45);
        if(r.equals("key.mcreator.specialjutsu1"))return new Stroke(classic?19:16);
        if(r.equals("key.mcreator.specialjutsu2"))return new Stroke(classic?33:mmo?18:19);
        if(r.equals("key.mcreator.specialjutsu3"))return new Stroke(classic?47:mmo?19:33);
        if(r.equals("key.mcreator.specialjutsu4"))return new Stroke(36);
        if(r.equals("key.mcreator.specialjutsu5"))return new Stroke(37);
        if(r.startsWith("key.mcreator.special_jutsu_")){try{return new Stroke(new int[]{44,45,47,48,49}[Integer.parseInt(r.substring(r.length()-1))-1]);}catch(RuntimeException ignored){}}
        return old;
    }
}
