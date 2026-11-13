package village.vale.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Bridge between licensed Naruto content mods and Vale's move sets. Reflection/registry-name
 * only: no licensed code or assets are referenced at compile time or copied.
 *
 * - Chakra: see {@link ChakraLink} / {@link MoveCost}.
 * - Discovery: while enabled, records every item use, entity spawn and damage source from the
 *   watched mod namespaces into config/valeorders/bridge-discovered.json, so the real IDs can
 *   be mapped without guessing.
 * - Move map: config/valeorders/bridge-moves.json ties those IDs to Vale presentation styles
 *   (hand techniques like spiral/charge/fireedge/windedge/lava/lightmode, arsenal weapon
 *   effects as "weapon:<key>", or "dodge"), so using a licensed jutsu or weapon plays the
 *   matching Vale animation, effects and HUD feedback. Gameplay of the licensed item is
 *   untouched; the bridge only adds presentation.
 */
public final class Bridge {
    private Bridge() {}
    public static boolean preferChakra = true, discovery = true;
    public static double chakraPerResolve = 1.0;
    public static int dodgeCost = 0;
    public static String[] watched = {"narutomod", "kabutoaddon", "borutomodaddononero", "ahznbcursemarkaddon", "ahznbswordsplus"};
    private static File folder;
    private static final Map<String, JsonObject> SEEN = new TreeMap<>();
    private static final Map<String, String> MOVES = new HashMap<>();
    private static boolean dirty;
    private static long lastSave;

    public static void init(File configDir) {
        folder = new File(configDir, "valeorders");
        folder.mkdirs();
        Configuration cfg = new Configuration(new File(folder, "bridge.cfg"));
        cfg.load();
        preferChakra = cfg.getBoolean("preferChakra", "chakra", true, "When a licensed chakra mod is linked and supports spending, Vale moves spend chakra instead of resolve.");
        chakraPerResolve = cfg.getFloat("chakraPerResolve", "chakra", 1.0f, 0.01f, 100f, "Chakra spent per point of Vale resolve cost.");
        dodgeCost = cfg.getInt("dodgeCost", "moves", 0, 0, 100, "Cost of one dodge in resolve units (chakra when linked). 0 keeps dodging free.");
        discovery = cfg.getBoolean("discovery", "discovery", true, "Record item, entity and damage IDs from the watched mods into bridge-discovered.json.");
        watched = cfg.getStringList("watchedNamespaces", "discovery", watched, "Mod namespaces to record and map.");
        if (cfg.hasChanged()) cfg.save();
        loadMoves();
        MinecraftForge.EVENT_BUS.register(new Bridge());
    }

    private static void loadMoves() {
        File f = new File(folder, "bridge-moves.json");
        if (!f.exists()) {
            JsonObject root = new JsonObject();
            root.addProperty("_help", "Map a licensed item or entity ID to a Vale style: spiral, charge, fireedge, windedge, lava, lightmode, weapon:<arsenal key> (for example weapon:storm_pair) or dodge. IDs come from bridge-discovered.json.");
            root.add("moves", new JsonObject());
            write(f, root);
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            JsonObject moves = new JsonParser().parse(r).getAsJsonObject().getAsJsonObject("moves");
            if (moves != null) moves.entrySet().forEach(e -> MOVES.put(e.getKey(), e.getValue().getAsString()));
            System.out.println("[valeorders bridge] loaded " + MOVES.size() + " move mappings");
        } catch (Exception bad) {
            System.out.println("[valeorders bridge] could not read bridge-moves.json: " + bad);
        }
    }

    private static boolean watchedId(String id) {
        if (id == null) return false;
        int c = id.indexOf(':');
        String ns = c < 0 ? "" : id.substring(0, c);
        for (String w : watched) if (w.equals(ns)) return true;
        return false;
    }

    private static String entityId(Entity e) {
        ResourceLocation r = e == null ? null : EntityList.getKey(e);
        return r == null ? null : r.toString();
    }

    private static void note(String kind, String id, String detail) {
        if (!discovery || !watchedId(id)) return;
        String key = kind + " " + id;
        JsonObject o = SEEN.get(key);
        if (o == null) { o = new JsonObject(); o.addProperty("kind", kind); o.addProperty("id", id); o.addProperty("count", 0); o.add("details", new JsonArray()); SEEN.put(key, o); }
        o.addProperty("count", o.get("count").getAsInt() + 1);
        JsonArray d = o.getAsJsonArray("details");
        if (detail != null && d.size() < 8) {
            boolean has = false;
            for (int i = 0; i < d.size(); i++) if (d.get(i).getAsString().equals(detail)) has = true;
            if (!has) d.add(detail);
        }
        dirty = true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void use(PlayerInteractEvent.RightClickItem e) {
        if (e.getWorld().isRemote || !(e.getEntityPlayer() instanceof EntityPlayerMP)) return;
        ItemStack s = e.getItemStack();
        ResourceLocation r = s.isEmpty() ? null : s.getItem().getRegistryName();
        if (r == null) return;
        String id = r.toString();
        note("item", id, s.getItem().getClass().getName());
        String style = MOVES.get(id);
        if (style != null) MoveVisuals.play((EntityPlayerMP) e.getEntityPlayer(), style);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void spawn(EntityJoinWorldEvent e) {
        if (e.getWorld().isRemote) return;
        String id = entityId(e.getEntity());
        if (id == null) return;
        note("entity", id, e.getEntity().getClass().getName());
        String style = MOVES.get(id);
        if (style != null && e.getEntity() instanceof net.minecraft.entity.projectile.EntityThrowable) {
            net.minecraft.entity.EntityLivingBase thrower = ((net.minecraft.entity.projectile.EntityThrowable) e.getEntity()).getThrower();
            if (thrower instanceof EntityPlayerMP) MoveVisuals.play((EntityPlayerMP) thrower, style);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void attack(LivingAttackEvent e) {
        if (e.getEntity().world.isRemote) return;
        Entity src = e.getSource().getImmediateSource(), true_ = e.getSource().getTrueSource();
        String id = entityId(src);
        note("damage", id, e.getSource().getDamageType());
        if (true_ != src) note("damage", entityId(true_), e.getSource().getDamageType());
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !dirty) return;
        long now = System.currentTimeMillis();
        if (now - lastSave < 60_000) return;
        save();
    }

    @SubscribeEvent public void unload(net.minecraftforge.event.world.WorldEvent.Unload e) { if (!e.getWorld().isRemote) save(); }

    public static void save() {
        if (folder == null || !dirty) return;
        JsonObject root = new JsonObject();
        root.addProperty("_help", "IDs seen from watched mods. Copy an id into bridge-moves.json to give it a Vale move style.");
        root.addProperty("chakra", ChakraLink.status);
        JsonArray rows = new JsonArray();
        for (JsonObject o : SEEN.values()) rows.add(o);
        root.add("seen", rows);
        write(new File(folder, "bridge-discovered.json"), root);
        dirty = false;
        lastSave = System.currentTimeMillis();
    }

    private static void write(File f, JsonObject o) {
        Gson g = new GsonBuilder().setPrettyPrinting().create();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) { g.toJson(o, w); }
        catch (Exception failed) { System.out.println("[valeorders bridge] could not write " + f + ": " + failed); }
    }
}
