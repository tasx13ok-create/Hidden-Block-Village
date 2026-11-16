package village.dungeonguard.client;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;

/** Optional read-only public API adapter; no Field HUD dependency or private state access. */
@SideOnly(Side.CLIENT)
final class FieldHudReservations {
    private static final AreaBannerLayout.Box[] EMPTY = new AreaBannerLayout.Box[0];
    private boolean attempted, available;
    private Class<?> boxType;
    private Constructor<?> boxConstructor, statusConstructor;
    private Method armorBounds, mapBounds, railBounds, resourceRows, sidebar, contextualBounds;
    private Field statusField, shinobi, margin, positionX, positionY, slim, x, y, width, height;

    AreaBannerLayout.Box[] bounds(Minecraft minecraft, int screenWidth, int screenHeight) {
        if (!attempted) discover();
        if (!available) return EMPTY;
        try {
            Object armor = armorBounds.invoke(null, screenWidth, screenHeight);
            List<Object> reserved = new ArrayList<Object>();
            for (int i = 0; i < Math.min(24, Array.getLength(armor)); i++) reserved.add(Array.get(armor, i));
            Object rail = railBounds.invoke(null, screenWidth, screenHeight);
            if (rail != null) reserved.add(rail);
            Object status = null;
            if (shinobi.getBoolean(null)) {
                Object layout = statusConstructor.newInstance(screenWidth, screenHeight, margin.getInt(null),
                        resourceRows.invoke(null), positionX.getInt(null), positionY.getInt(null), slim.getBoolean(null), armor);
                status = statusField.get(layout);
            }
            Object map = mapBounds.invoke(null, screenWidth, screenHeight, true);
            Object scores = (Boolean) sidebar.invoke(null)
                    ? boxConstructor.newInstance(screenWidth - 184, 40, 184, Math.max(1, screenHeight - 114)) : null;
            // Field HUD itself uses this fallback only when choosing the contextual card's position.
            // It is not added as a guessed minimap reservation.
            Object cardMap = map != null ? map : boxConstructor.newInstance(screenWidth - margin.getInt(null) - 148, 44, 148, 160);
            Object array = Array.newInstance(boxType, reserved.size());
            for (int i = 0; i < reserved.size(); i++) Array.set(array, i, reserved.get(i));
            Object contextual = contextualBounds.invoke(null, screenWidth, screenHeight, status, cardMap, scores, array);
            reserved.add(minecraft.playerController.shouldDrawHUD() ? status : null);
            reserved.add(map); reserved.add(scores); reserved.add(contextual);
            List<AreaBannerLayout.Box> result = new ArrayList<AreaBannerLayout.Box>();
            for (Object box : reserved) {
                if (box == null || result.size() >= 32) continue;
                long left = Math.max(0, x.getInt(box)), top = Math.max(0, y.getInt(box));
                long right = Math.min((long) screenWidth, (long) x.getInt(box) + width.getInt(box));
                long bottom = Math.min((long) screenHeight, (long) y.getInt(box) + height.getInt(box));
                if (right > left && bottom > top) result.add(new AreaBannerLayout.Box((int) left, (int) top, (int) (right - left), (int) (bottom - top)));
            }
            return result.toArray(new AreaBannerLayout.Box[result.size()]);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            disable(unsupported); return EMPTY;
        }
    }
    private void discover() {
        attempted = true;
        if (!Loader.isModLoaded("fieldhud")) return;
        try {
            Class<?> config = Class.forName("village.fieldhud.HudConfig");
            boxType = Class.forName("village.fieldhud.Layout$Box");
            Class<?> boxes = Array.newInstance(boxType, 0).getClass();
            boxConstructor = boxType.getConstructor(int.class, int.class, int.class, int.class);
            x = boxType.getField("x"); y = boxType.getField("y"); width = boxType.getField("w"); height = boxType.getField("h");
            shinobi = config.getField("shinobi"); margin = config.getField("margin");
            positionX = config.getField("positionX"); positionY = config.getField("positionY"); slim = config.getField("slim");
            Class<?> status = Class.forName("village.fieldhud.ShinobiLayout");
            statusConstructor = status.getConstructor(int.class, int.class, int.class, int.class, int.class, int.class, boolean.class, boxes);
            statusField = status.getField("status");
            armorBounds = Class.forName("village.fieldhud.ArmorHudBridge").getMethod("bounds", int.class, int.class);
            mapBounds = Class.forName("village.fieldhud.EyeMap").getMethod("bounds", int.class, int.class, boolean.class);
            railBounds = Class.forName("village.fieldhud.JutsuRail").getMethod("bounds", int.class, int.class);
            resourceRows = Class.forName("village.fieldhud.ValeBridge").getMethod("resourceRows");
            sidebar = Class.forName("village.fieldhud.ClientHud").getMethod("sidebar");
            contextualBounds = Class.forName("village.fieldhud.ValeHud").getMethod("bounds", int.class, int.class, boxType, boxType, boxType, boxes);
            available = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) { disable(unsupported); }
    }
    private void disable(Throwable cause) {
        available = false;
        LogManager.getLogger("Dungeon Guard").warn("Optional Field HUD public layout API is unavailable; boss-bar and hotbar spacing remain active. {}", cause.getClass().getSimpleName());
    }
}
