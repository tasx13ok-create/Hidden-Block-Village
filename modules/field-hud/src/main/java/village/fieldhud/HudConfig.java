package village.fieldhud;

import java.io.File;
import net.minecraftforge.common.config.Configuration;

public final class HudConfig {
    public static Configuration file;
    public static int mode=0,margin=8;
    public static boolean location=true,target=true,arrivals=true,warning=true,chime=false;
    public static boolean shinobi=true,motion=true,curve=true,eyeMap=true;
    public static boolean chakra=true,slim=false;
    public static boolean glassBlur=true;
    public static boolean focusEffects=true;
    public static boolean jutsuRail=true;
    public static int positionX=-1,positionY=-1;
    public static final String[] MODES={"Explore","Combat","Quiet"};
    public static void load(File directory) {
        file=new Configuration(new File(directory,"fieldhud.cfg"));
        file.load();
        mode=file.getInt("mode","hud",0,0,2,"0 Explore, 1 Combat, 2 Quiet. Changes only Field HUD.");
        margin=file.getInt("margin","hud",8,4,24,"Screen inset in scaled GUI pixels.");
        location=file.getBoolean("location","hud",true,"Show biome or configured build name in Explore mode.");
        target=file.getBoolean("target","hud",true,"Show the living entity directly under the crosshair. No through-wall tracking.");
        arrivals=file.getBoolean("arrivals","hud",true,"Short entry card for configured builds. Hidden during boss fights.");
        warning=file.getBoolean("durability","hud",true,"Show equipped gear below 15 percent durability.");
        chime=file.getBoolean("arrivalChime","hud",false,"Optional quiet vanilla chime on entering a configured build.");
        shinobi=file.getBoolean("shinobiLayout","hud",true,"Native 1.12 status plate and scroll hotbar. Uses actual synchronized health/armor/food.");
        motion=file.getBoolean("animation","hud",true,"Smooth meters, brief selection feedback and low-health seal. Off snaps to actual values.");
        glassBlur=file.getBoolean("glassBlur","hud",true,"GPU scene blur behind glass panels. Off uses translucent glass without blur.");
        focusEffects=file.getBoolean("focusEffects","hud",true,"Subtle native dojutsu/casting focus and confirmed-cast flash. Quiet hides these; Motion off removes animation.");
        jutsuRail=file.getBoolean("jutsuBar","hud",true,"Six local shortcuts to server-owned attacks and equipment below the minimap. Focus routes scrolling; menus retain normal input.");
        curve=file.getBoolean("curvedHotbar","hud",true,"Subtle two-pixel scroll curve; items follow their slots.");
        eyeMap=file.getBoolean("eyeBezel","hud",true,"Optional Xaero 26.6 circular-map eye bezel using live module bounds.");
        chakra=file.getBoolean("chakra","hud",true,"Integrate NarutoMod's real synchronized chakra and warning/Sage flags when available.");
        slim=file.getBoolean("slimPlate","hud",false,"Narrower status plate. Can be changed in the layout editor.");
        positionX=file.getInt("statusX","hud",-1,-1,1000,"Normalized status position; -1 uses the default margin.");
        positionY=file.getInt("statusY","hud",-1,-1,1000,"Normalized status position above the hotbar; -1 uses the default margin.");
        save();
    }
    public static void save() {
        file.get("hud","mode",0).set(mode);
        file.get("hud","margin",8).set(margin);
        file.get("hud","location",true).set(location);
        file.get("hud","target",true).set(target);
        file.get("hud","arrivals",true).set(arrivals);
        file.get("hud","durability",true).set(warning);
        file.get("hud","arrivalChime",false).set(chime);
        file.get("hud","shinobiLayout",true).set(shinobi);
        file.get("hud","animation",true).set(motion);
        file.get("hud","glassBlur",true).set(glassBlur);
        file.get("hud","focusEffects",true).set(focusEffects);
        file.get("hud","jutsuBar",true).set(jutsuRail);
        file.get("hud","curvedHotbar",true).set(curve);
        file.get("hud","eyeBezel",true).set(eyeMap);
        file.get("hud","chakra",true).set(chakra);
        file.get("hud","slimPlate",false).set(slim);
        file.get("hud","statusX",-1).set(positionX);
        file.get("hud","statusY",-1).set(positionY);
        if(file.hasChanged()) file.save();
    }
}
