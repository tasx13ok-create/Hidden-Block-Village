package village.bijuu;

import com.google.gson.GsonBuilder;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import net.minecraft.block.BlockStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.sound.PlaySoundSourceEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Client acceptance for the Steam Dash 2.0, run in a real client through the real key binding:
 *   A  tap dash: afterimage barrage, hit-stop, time bubble (a bystander mob and an arrow), launch
 *      through a mountain and a ridge, cliff splatter, molten scars that cool
 *   B  full overcharge: tier 3 pierces a line of three
 *   C  juggle: two relays, then the tail pin and spike crater
 *   D  wall springboard
 * Each run is recorded in slow motion (the game runs at 1/SCALE speed) with a sync flash, and every
 * sound the client plays is logged with its game time, so the footage can be assembled at a smooth
 * 60 fps with the real sound mixed back in. QA only; never shipped.
 */
@Mod(modid = "hvb_dash_qa", name = "Steam dash QA", version = "2", clientSideOnly = true, acceptableRemoteVersions = "*", dependencies = "required-after:hvbijuu_steamdash")
public final class DashQa {
    static final int SCALE = Integer.getInteger("hvb.dashqa.scale", 5);
    private boolean launched;
    private int tick, phase, t, waited;
    private final Map<String, Object> out = new LinkedHashMap<>();
    private final List<String> passed = new ArrayList<>(), failed = new ArrayList<>();
    private final List<Map<String, Object>> windows = new ArrayList<>(), sounds = Collections.synchronizedList(new ArrayList<>());
    private final List<String> shots = new ArrayList<>(), chat = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> shotNames = new LinkedHashSet<>();
    private volatile EntityVillager target;
    private final List<EntityVillager> line = new ArrayList<>();
    private volatile net.minecraft.entity.passive.EntityPig bystander;
    private volatile int bystanderUpdates, bystanderBubbleTicks, maxMolten, moltenAt = -1;
    private final List<String> launchTrace = Collections.synchronizedList(new ArrayList<>());
    private volatile EntityTippedArrow arrow;
    private final List<double[]> arrowSteps = Collections.synchronizedList(new ArrayList<>());
    private volatile int bystanderAge0 = -1, bystanderAge1 = -1, maxRipples, maxRubble;
    private volatile float hp0;
    private volatile boolean serverSlow, sawBubble, sawFreeze;
    private long serverTickStart, syncUntil = -1;
    private boolean syncLogged;
    private Map<String, Object> window;
    private int keyHold;   // ticks left to hold the dash key down
    private final Map<Integer, Integer> maxTier = new HashMap<>();

    @Mod.EventHandler public void init(FMLInitializationEvent e) { if (Boolean.getBoolean("hvb.dashqa")) MinecraftForge.EVENT_BUS.register(this); }

    private void check(boolean ok, String label) { (ok ? passed : failed).add(label); }
    private Minecraft mc() { return Minecraft.getMinecraft(); }
    private EntityPlayerMP sp() { return mc().getIntegratedServer().getPlayerList().getPlayers().get(0); }
    private WorldServer sw() { return mc().getIntegratedServer().getWorld(0); }
    private void server(Runnable r) { mc().getIntegratedServer().addScheduledTask(r); }
    private static Item item(String n) { return Item.REGISTRY.getObject(new ResourceLocation("hvbijuu", n)); }

    // ---- key, slow motion, sync ----

    /** Holds the dash key for `ticks` client ticks (1 = a tap). */
    private void key(int ticks) { keyHold = ticks; KeyBinding.setKeyBindState(DashClient.KEY.getKeyCode(), true); }

    private static Field timerF, lengthF;
    private void slowmo(boolean on) {
        try {
            if (timerF == null) {
                timerF = field(Minecraft.class, "field_71428_T", "timer");
                lengthF = field(net.minecraft.util.Timer.class, "field_194149_e", "tickLength");
            }
            lengthF.setFloat(timerF.get(mc()), on ? 50f * SCALE : 50f);
        } catch (ReflectiveOperationException e) { failed.add("slow motion unavailable: " + e); }
        serverSlow = on;
        mc().gameSettings.limitFramerate = on ? 60 : 30;
    }

    private static Field field(Class<?> c, String srg, String mcp) throws NoSuchFieldException {
        Field f;
        try { f = c.getDeclaredField(srg); } catch (NoSuchFieldException d) { f = c.getDeclaredField(mcp); }
        f.setAccessible(true);
        return f;
    }

    /** Starts a recorded window: slow motion on, then a magenta sync frame marks it in the footage. */
    private void record(String name) {
        slowmo(true);
        window = new LinkedHashMap<>();
        window.put("name", name);
        window.put("scale", SCALE);
        windows.add(window);
        syncUntil = System.currentTimeMillis() + 120;
        syncLogged = false;
    }

    private void stopRecord() {
        if (window != null) { window.put("end_real_ms", System.currentTimeMillis()); window.put("end_virtual_ms", DashClient.now()); }
        window = null;
        slowmo(false);
    }

    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.START) { serverTickStart = System.nanoTime(); return; }
        if (mc().getIntegratedServer() == null) return;
        serverSample();
        if (serverSlow) {
            long left = 50L * SCALE - (System.nanoTime() - serverTickStart) / 1_000_000;
            if (left > 0) try { Thread.sleep(left); } catch (InterruptedException ignored) {}
        }
    }

    // ---- logs ----

    /** Counts the bystander's real (not cancelled) updates while the time bubble is open. */
    @SubscribeEvent(priority = EventPriority.LOWEST) public void living(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent e) {
        if (bystander != null && e.getEntityLiving() == bystander && TimeBubble.active(e.getEntityLiving().world)) bystanderUpdates++;
    }

    @SubscribeEvent public void sound(PlaySoundSourceEvent e) {
        ISound s = e.getSound();
        Entity l = mc().getRenderViewEntity();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", DashClient.now());
        try { m.put("file", s.getSound().getSoundAsOggLocation().toString()); } catch (RuntimeException x) { return; }
        m.put("vol", s.getVolume()); m.put("pitch", s.getPitch());
        m.put("x", s.getXPosF()); m.put("y", s.getYPosF()); m.put("z", s.getZPosF());
        m.put("att", s.getAttenuationType() == ISound.AttenuationType.LINEAR ? 1 : 0);
        if (l != null) { m.put("lx", l.posX); m.put("ly", l.posY + l.getEyeHeight()); m.put("lz", l.posZ); m.put("lyaw", l.rotationYaw); }
        sounds.add(m);
    }

    @SubscribeEvent public void chat(net.minecraftforge.client.event.ClientChatReceivedEvent e) { chat.add("phase" + phase + " t" + t + ": " + e.getMessage().getUnformattedText()); }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void frame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || mc().world == null) return;
        long now = System.currentTimeMillis();
        if (syncUntil > 0 && now < syncUntil) {
            ScaledResolution sr = new ScaledResolution(mc());
            Gui.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), 0xFFFF00FF);
            if (!syncLogged && window != null) { window.put("sync_real_ms", now); window.put("sync_virtual_ms", DashClient.now()); syncLogged = true; }
        }
        String s = DashCamera.shot;
        if (!s.isEmpty() && (shots.isEmpty() || !shots.get(shots.size() - 1).startsWith(s + "@"))) { shots.add(s + "@" + DashClient.now()); shotNames.add(s); }
        maxRipples = Math.max(maxRipples, DashClient.Rubble.ripples);
        maxRubble = Math.max(maxRubble, DashClient.Rubble.alive);
        for (Map.Entry<Integer, long[]> en : DashClient.CHARGES.entrySet()) maxTier.merge(en.getKey(), (int) en.getValue()[1], Math::max);
    }

    // ---- world ----

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = mc();
        if (!launched) {
            if (mc.currentScreen instanceof GuiMainMenu) {
                launched = true;
                mc.gameSettings.thirdPersonView = 0;
                mc.launchIntegratedServer("dashqa-" + System.currentTimeMillis(), "dashqa", new WorldSettings(777L, GameType.SURVIVAL, false, false, WorldType.FLAT).enableCommands());
            }
            return;
        }
        if (mc.player == null || mc.world == null || mc.getIntegratedServer() == null) return;
        mc.gameSettings.pauseOnLostFocus = false;
        mc.inGameHasFocus = true;
        if (mc.currentScreen != null) mc.displayGuiScreen(null);
        if (keyHold > 0 && --keyHold == 0) KeyBinding.setKeyBindState(DashClient.KEY.getKeyCode(), false);
        tick++;
        if (tick == 60) build();
        if (tick == 80) mc.gameSettings.thirdPersonView = 1;   // warm the 8K armor textures and the clone pass
        if (tick >= 200 && tick < 290 && tick % 10 == 0) DashClient.ghost(mc.player.getEntityId(), DashClient.pose(mc.player, 1f, mc.player.posX + 2, mc.player.posY, mc.player.posZ + 2));
        if (tick == 300) mc.gameSettings.thirdPersonView = 0;
        if (tick < 320) return;
        try { course(); } catch (Throwable x) { failed.add("course crashed: " + x); x.printStackTrace(); finish(); }
    }

    private void build() {
        server(() -> {
            EntityPlayerMP p = sp();
            WorldServer w = sw();
            w.setWorldTime(6000); w.getGameRules().setOrCreateGameRule("doDaylightCycle", "false"); w.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
            BijuuArmor.requireIssued = false;
            fill(w, -5, 4, 20, 5, 13, 26, Blocks.STONE.getDefaultState());     // A: mountain
            fill(w, -8, 4, 40, 8, 24, 44, Blocks.STONE.getDefaultState());     // A: second ridge
            fill(w, -8, 4, 60, 8, 30, 64, Blocks.STONE.getDefaultState());     // A: cliff where the boil runs out
            fill(w, -88, 4, 30, -72, 16, 33, Blocks.STONE.getDefaultState());  // B: wall behind the line
            fill(w, 30, 4, -6, 30, 16, 6, Blocks.STONE.getDefaultState());     // D: springboard wall
            p.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(item("kurama_helmet")));
            p.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(item("kurama_chestplate")));
            p.setItemStackToSlot(EntityEquipmentSlot.LEGS, new ItemStack(item("kurama_leggings")));
            p.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(item("kurama_boots")));
            p.inventory.setInventorySlotContents(8, new ItemStack(item("kokuo_helmet")));
            out.put("channeling", DashServer.channeling(p));
        });
    }

    private static void fill(WorldServer w, int x0, int y0, int z0, int x1, int y1, int z1, IBlockState s) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) w.setBlockState(new BlockPos(x, y, z), s, 2);
    }

    private static int count(WorldServer w, int x0, int y0, int z0, int x1, int y1, int z1, java.util.function.Predicate<IBlockState> p) {
        int n = 0;
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) if (p.test(w.getBlockState(new BlockPos(x, y, z)))) n++;
        return n;
    }

    private EntityVillager dummy(double x, double y, double z) {
        EntityVillager v = new EntityVillager(sw());
        v.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0);   // a real mob that stands still
        v.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(400);
        v.setHealth(400);
        v.setLocationAndAngles(x, y, z, 180, 0);
        sw().spawnEntity(v);
        return v;
    }

    private void ready(double x, double y, double z, float yaw, float pitch) {
        server(() -> {
            EntityPlayerMP p = sp();
            p.connection.setPlayerLocation(x, y, z, yaw, pitch);
            p.motionX = p.motionY = p.motionZ = 0;
            p.setHealth(p.getMaxHealth());
            BijuuArmor.state(p).setInteger("energy", 100);
            BijuuArmor.state(p).setLong("dashReady", 0);
            BijuuArmor.state(p).setLong("springReady", 0);
        });
    }

    private DashClient.Cue cue() { return mc().player == null ? null : DashClient.CUES.get(mc().player.getEntityId()); }
    private boolean settled() { return serverTickMs() < 50 && Minecraft.getDebugFPS() >= 8; }
    private double serverTickMs() {
        long[] a = mc().getIntegratedServer().tickTimeArray;
        long sum = 0; for (int i = 0; i < 20; i++) sum += a[(mc().getIntegratedServer().getTickCounter() - 1 - i + a.length * 4) % a.length];
        return sum / 20.0 / 1e6;
    }

    /** Server-side samples every server tick, so a stalled client frame can't hide anything. */
    private void serverSample() {
        WorldServer w = sw();
        int molten = MoltenScars.active();
        if (phase == 4) lariatSample();
        if (molten > maxMolten) { maxMolten = molten; if (moltenAt < 0) moltenAt = phase * 100000 + t; }
        if (TimeBubble.active(w)) {
            sawBubble = true;
            if (bystander != null) bystanderBubbleTicks++;
            if (arrow == null && phase == 0) {
                EntityTippedArrow a = new EntityTippedArrow(w, -7, 8.5, 13);
                a.motionX = 1.2; a.motionY = 0.05; a.motionZ = 0; a.setNoGravity(true);
                w.spawnEntity(a);
                arrow = a;
            } else if (arrow != null && !arrow.isDead && arrowSteps.size() < 40) arrowSteps.add(new double[] {arrow.posX, 1});
        } else if (arrow != null && !arrow.isDead && arrowSteps.size() < 60) arrowSteps.add(new double[] {arrow.posX, 0});
        for (DashServer.Seq q : DashServer.RUNNING.values()) {
            if (q.phase == DashServer.Phase.FREEZE) sawFreeze = true;
            if (q.phase == DashServer.Phase.LAUNCH && phase == 0 && launchTrace.size() < 40)
                launchTrace.add(String.format(Locale.ROOT, "age=%d boil=%d pos=%.2f,%.2f,%.2f m=%.2f,%.2f,%.2f hc=%b look=%.2f,%.2f,%.2f", q.phaseAge, q.boil, q.target.posX, q.target.posY, q.target.posZ, q.target.motionX, q.target.motionY, q.target.motionZ, q.target.collidedHorizontally, q.look[0], q.look[1], q.look[2]));
        }
    }

    // ---- the course ----

    private void course() {
        Minecraft mc = mc();
        if (phase == 0 && t == 0) phase = Integer.getInteger("hvb.dashqa.from", 0);   // QA iteration: start at a later scene
        t++;
        int pressAt = 40;
        if (t == pressAt - 10 && !settled() && waited < 900) { t--; waited++; return; }   // before slow motion starts
        if (t == pressAt - 10) { out.put("phase" + phase + "_settle_ticks", waited); out.put("phase" + phase + "_fps", Minecraft.getDebugFPS()); waited = 0; }
        DashClient.Cue c = cue();
        if (window != null && phase <= 2 && c != null && c.end >= 0 && DashClient.now() - c.end > 1400) stopRecord();
        switch (phase) {
            case 0: {   // A: tap dash, barrage, bubble, mountain, ridge, cliff, molten scars
                if (t == 1) { ready(0.5, 4, 0.5, 0, -2); }
                if (t == 6) server(() -> { target = dummy(0.5, 4, 14.5); hp0 = 400;
                    bystander = new net.minecraft.entity.passive.EntityPig(sw()); bystander.setLocationAndAngles(5.5, 4, 10.5, 90, 0); bystander.enablePersistence(); sw().spawnEntity(bystander); });
                if (t == pressAt - 8) record("A-tap-dash");
                if (t == pressAt) key(1);
                if (t == pressAt + 150) server(() -> {
                    WorldServer w = sw();
                    out.put("A_target", String.format(Locale.ROOT, "z=%.1f hp=%.0f", target.posZ, target.getHealth()));
                    out.put("A_mountain_air", count(w, -5, 4, 20, 5, 13, 26, s -> s.getBlock() == Blocks.AIR));
                    out.put("A_ridge_air", count(w, -8, 4, 40, 8, 24, 44, s -> s.getBlock() == Blocks.AIR));
                    int cobble = count(w, -8, 4, 56, 8, 30, 64, s -> s.getBlock() == Blocks.COBBLESTONE);
                    int cracked = count(w, -8, 4, 56, 8, 30, 64, s -> s.getBlock() == Blocks.STONEBRICK);
                    out.put("A_splatter", cobble + "+" + cracked);
                    int magma = count(w, -10, 0, 10, 10, 30, 66, s -> s.getBlock() == Blocks.MAGMA);
                    out.put("A_molten_blocks", magma);
                    double dmg = hp0 - target.getHealth();
                    check(dmg >= 16 + 4 * 2 - 0.1, "barrage + punch land: 4 clone jabs (2) and the punch (16) (" + dmg + ")");
                    check(count(w, -5, 4, 20, 5, 13, 26, s -> s.getBlock() == Blocks.AIR) >= 60, "the launch tunnels through the mountain");
                    check(cobble + cracked >= 6, "the boil ends in a cliff splatter (" + cobble + "+" + cracked + ")");
                    out.put("A_molten_max_seen", maxMolten + " (first at phase*100000+t = " + moltenAt + ")");
                    check(maxMolten >= 10, "the tunnel lining glows as molten scars (" + maxMolten + " molten blocks, " + magma + " still molten at this check)");
                    check(DashServer.RUNNING.isEmpty() && DashServer.FROZEN.isEmpty(), "sequence A finished cleanly");
                });
                if (t == pressAt + 152) {
                    if (window != null) stopRecord();
                    check(sawFreeze, "hit-stop ran");
                    check(sawBubble, "the time bubble opened around the hit");
                    out.put("A_bystander_updates", bystanderUpdates + " of " + bystanderBubbleTicks + " bubble ticks");
                    check(bystanderBubbleTicks >= 16 && bystanderUpdates >= bystanderBubbleTicks / 4 - 2 && bystanderUpdates <= bystanderBubbleTicks / 4 + 2, "a bystander mob in the bubble updates at a quarter rate (" + bystanderUpdates + " of " + bystanderBubbleTicks + ")");
                    out.put("A_launch_trace", new ArrayList<>(launchTrace));
                    double inside = stepAvg(1), outside = stepAvg(0);
                    out.put("A_arrow_step_in_out", String.format(Locale.ROOT, "%.3f / %.3f", inside, outside));
                    check(inside > 0 && outside > 0 && inside < outside * 0.4, "an arrow in the bubble moves at about a quarter speed");
                    check(shotNames.contains("dash-dolly") && shotNames.contains("hit-orbit") && shotNames.contains("chase"), "cut-scene shots: dolly, hit orbit, chase (" + shotNames + ")");
                    check(maxRipples > 20, "the ground ripple rolls out (" + maxRipples + " blocks)");
                    check(DashClient.whiteFrames > 0 && DashClient.inkFrames > 0, "impact frames drawn (white " + DashClient.whiteFrames + ", ink " + DashClient.inkFrames + ")");
                }
                if (t == pressAt + 152 + 220) server(() -> {   // after the scars cool
                    WorldServer w = sw();
                    int magma = count(w, -10, 0, 10, 10, 30, 66, s -> s.getBlock() == Blocks.MAGMA);
                    int andesite = count(w, -10, 0, 10, 10, 30, 66, s -> s.getBlock() == Blocks.STONE && s.getValue(BlockStone.VARIANT) == BlockStone.EnumType.ANDESITE);
                    out.put("A_after_cooling", "magma=" + magma + " andesite=" + andesite);
                    check(magma == 0 && andesite > 0, "molten scars cool to cobblestone and andesite (" + magma + " left, " + andesite + " andesite)");
                });
                if (t == pressAt + 152 + 225) next();
                break;
            }
            case 1: {   // B: full overcharge pierces a line of three
                if (t == 1) { ready(-79.5, 4, 0.5, 0, -2); DashClient.whiteFrames = 0; }
                if (t == 6) server(() -> { line.clear(); line.add(dummy(-79.5, 4, 8.5)); line.add(dummy(-78.9, 4, 14.5)); line.add(dummy(-80.1, 4, 20.5)); });
                if (t == pressAt - 8) record("B-overcharge-pierce");
                if (t == pressAt) key(26);
                if (t == pressAt + 130) server(() -> {
                    StringBuilder sb = new StringBuilder();
                    int hit = 0, aside = 0;
                    for (int i = 0; i < line.size(); i++) {
                        EntityVillager v = line.get(i);
                        sb.append(String.format(Locale.ROOT, "[x=%.1f z=%.1f hp=%.0f] ", v.posX, v.posZ, v.getHealth()));
                        if (v.getHealth() < 400 - 11) hit++;
                        if (i < line.size() - 1 && Math.abs(v.posX + 79.5) > 1.5) aside++;
                    }
                    out.put("B_line", sb.toString().trim());
                    out.put("B_wall_air", count(sw(), -88, 4, 30, -72, 16, 33, s -> s.getBlock() == Blocks.AIR));
                    check(hit == 3, "a tier 3 dash hits every body in the line (" + hit + "/3)");
                    check(aside == 2, "the bodies it passes through are knocked aside (" + aside + "/2)");
                    check(line.get(2).posZ > 30, String.format(Locale.ROOT, "the last one takes the finish and is launched (z=%.1f)", line.get(2).posZ));
                    check(DashServer.RUNNING.isEmpty(), "sequence B finished cleanly");
                });
                if (t == pressAt + 132) {
                    if (window != null) stopRecord();
                    int me = mc.player.getEntityId();
                    check(maxTier.getOrDefault(me, 0) == 3, "holding the key charges through tiers 1, 2, 3 (max " + maxTier.get(me) + ")");
                    check(shotNames.contains("charge"), "the charge has its own camera shot");
                    next();
                }
                break;
            }
            case 2: {   // C: juggle — relay, relay, then pin and spike
                if (t == 1) ready(80.5, 4, 0.5, 0, -2);
                if (t == 6) server(() -> { target = dummy(80.5, 4, 14.5); hp0 = 400; });
                if (t == pressAt - 8) record("C-juggle-pin-spike");
                if (t == pressAt) key(1);
                if (c != null && c.launch >= 0 && keyHold == 0) {
                    long since = DashClient.now() - c.launch;
                    int presses = c.relays + (c.pin >= 0 ? 1 : 0);
                    if (since > 250 * 1 && presses < 3 && !juggled.contains(presses + "@" + c.launch)) { juggled.add(presses + "@" + c.launch); key(1); }
                }
                if (t == pressAt + 160) server(() -> {
                    WorldServer w = sw();
                    int bx = (int) Math.floor(target.posX), bz = (int) Math.floor(target.posZ);
                    int crater = count(w, bx - 10, 1, bz - 10, bx + 10, 3, bz + 10, s -> s.getBlock() == Blocks.AIR || s.getBlock() == Blocks.MAGMA);
                    double dmg = hp0 - target.getHealth();
                    out.put("C_target", String.format(Locale.ROOT, "x=%.1f y=%.1f z=%.1f dmg=%.0f crater=%d", target.posX, target.posY, target.posZ, dmg, crater));
                    check(dmg >= 16 + 8 + 6 * 2 + 10 + 8 - 0.5, "punch, barrage, two relays, spike and crater all land (" + dmg + ")");
                    check(crater >= 100, "the spike leaves a crater (" + crater + ")");
                    check(DashServer.RUNNING.isEmpty() && DashServer.FROZEN.isEmpty(), "sequence C finished cleanly");
                });
                if (t == pressAt + 162) {
                    if (window != null) stopRecord();
                    out.put("C_juggle_presses", new ArrayList<>(juggled));
                    check(juggled.size() == 3, "three presses while they flew: relay, relay, pin");
                    check(shotNames.contains("relay-orbit") && shotNames.contains("pin-low") && shotNames.contains("overhead"), "relay, pin and crater shots (" + shotNames + ")");
                    next();
                }
                break;
            }
            case 3: {   // D: springboard
                if (t == 1) server(() -> { if (target != null) target.setDead(); });
                if (t == pressAt - 8) record("D-springboard");
                if (t == pressAt - 6) ready(29.65, 9, 0.5, -90, 0);
                if (t == pressAt - 3) server(() -> { sp().motionY = 0.2; sp().velocityChanged = true; });
                if (t == pressAt) { springX0 = mc.player.posX; key(1); }
                if (t == pressAt + 6) out.put("D_x_after", mc.player.posX);
                if (t == pressAt + 24) {
                    stopRecord();
                    check(mc.player.posX < springX0 - 2.5, String.format(Locale.ROOT, "the springboard launches off the wall (x %.2f -> %.2f)", springX0, mc.player.posX));
                    server(() -> check(count(sw(), 30, 4, -6, 30, 16, 6, s -> s.getBlock() == Blocks.COBBLESTONE || s.getBlock() == Blocks.STONEBRICK) >= 3, "the springboard cracks the wall"));
                }
                if (t == pressAt + 30) {
                    check(!DashCamera.active() && mc.getRenderViewEntity() == mc.player && !mc.gameSettings.hideGUI, "the camera and HUD are back to normal afterwards");
                    next();
                }
                break;
            }

            case 4: {   // E: Gyuki lariat — crouch, blink through glass and leaves, hit-stop, launch through a fence, glass and snow, trench, skid, regrowth
                if (t == 1) server(() -> {
                    EntityPlayerMP p = sp(); WorldServer w = sw();
                    p.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(item("gyuki_helmet")));
                    p.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(item("gyuki_chestplate")));
                    p.setItemStackToSlot(EntityEquipmentSlot.LEGS, new ItemStack(item("gyuki_leggings")));
                    p.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(item("gyuki_boots")));
                    p.inventory.setInventorySlotContents(8, ItemStack.EMPTY);
                    // On the blink path: a glass wall and a leaf hedge. Behind the target: a fence, a glass wall, snow on the ground.
                    fill(w, LX - 2, 4, LZ + 5, LX + 2, 6, LZ + 5, Blocks.GLASS.getDefaultState());
                    fill(w, LX - 1, 4, LZ + 8, LX + 1, 5, LZ + 8, Blocks.LEAVES.getDefaultState().withProperty(net.minecraft.block.BlockLeaves.DECAYABLE, false));
                    fill(w, LX - 2, 4, LZ + 15, LX + 2, 4, LZ + 15, Blocks.OAK_FENCE.getDefaultState());
                    fill(w, LX - 2, 4, LZ + 19, LX + 2, 7, LZ + 19, Blocks.GLASS.getDefaultState());
                    fill(w, LX - 2, 4, LZ + 22, LX + 2, 4, LZ + 30, Blocks.SNOW_LAYER.getDefaultState());
                    lariatBefore = snapshot(w);
                });
                if (t == 4) { out.put("E_wearing_gyuki", LariatServer.wearing(mc.player)); mc.gameSettings.thirdPersonView = 1; }   // warm the Gyuki 8K textures
                if (t > 4 && t < pressAt - 12 && t % 6 == 0) DashClient.ghost(mc.player.getEntityId(), DashClient.pose(mc.player, 1f, mc.player.posX + 2, mc.player.posY, mc.player.posZ + 2));
                if (t == pressAt - 12) mc.gameSettings.thirdPersonView = 0;
                if (t == 8) server(() -> { target = dummy(LX + 0.5, 4, LZ + 12.5); });
                if (t == pressAt - 8) record("E-lariat");
                if (t == pressAt - 6) ready(LX + 0.5, 4, LZ + 0.5, 0, 2);
                if (t == pressAt - 6) server(() -> BijuuArmor.state(sp()).setLong("lariatReady", 0));
                if (t == pressAt) key(1);
                if (t == pressAt + 45) {
                    stopRecord();
                    server(() -> {
                        WorldServer w = sw();
                        float dmg = 400 - target.getHealth();
                        out.put("E_damage", dmg);
                        out.put("E_trace", new ArrayList<>(lariatTrace));
                        out.put("E_path_blocks", w.getBlockState(new BlockPos(LX, 4, LZ + 5)) + " / " + w.getBlockState(new BlockPos(LX, 5, LZ + 5)) + " / " + w.getBlockState(new BlockPos(LX, 4, LZ + 8)));
                        check(dmg >= SteamDash.lariatDamage - 0.1, "the lariat lands its blow (" + dmg + ")");
                        check(lariatStages.contains(3) && lariatHeld >= LariatMath.HITSTOP - 1, "hit-stop: both fighters held for the beat (" + lariatHeld + " ticks, spread " + String.format(Locale.ROOT, "%.3f", lariatSpread) + ")");
                        check(lariatSpread < 0.05, "nothing drifts during the hit-stop");
                        double fly = target.posZ - (LZ + 12.5);
                        out.put("E_target", String.format(Locale.ROOT, "x=%.1f y=%.1f z=%.1f", target.posX, target.posY, target.posZ));
                        check(fly > 12, String.format(Locale.ROOT, "the victim is launched along the blow (%.1f blocks)", fly));
                        int glass1 = count(w, LX - 2, 4, LZ + 5, LX + 2, 6, LZ + 5, st -> st.getBlock() == Blocks.AIR);
                        int leaves = count(w, LX - 1, 4, LZ + 8, LX + 1, 5, LZ + 8, st -> st.getBlock() == Blocks.AIR);
                        int fence = count(w, LX - 2, 4, LZ + 15, LX + 2, 4, LZ + 15, st -> st.getBlock() == Blocks.AIR);
                        int glass2 = count(w, LX - 2, 4, LZ + 19, LX + 2, 7, LZ + 19, st -> st.getBlock() == Blocks.AIR);
                        out.put("E_broken", "glass " + glass1 + ", leaves " + leaves + ", fence " + fence + ", far glass " + glass2);
                        check(glass1 >= 2 && leaves >= 2, "the blink crashes through the glass wall and the hedge (" + glass1 + ", " + leaves + ")");
                        check(fence >= 1 && glass2 >= 2, "the flying body smashes the fence and the far glass (" + fence + ", " + glass2 + ")");
                        int trench = count(w, LX - 2, 3, LZ + 10, LX + 2, 3, LZ + 40, st -> st.getBlock() == Blocks.AIR);
                        int rough = count(w, LX - 2, 3, LZ + 10, LX + 2, 3, LZ + 40, st -> st.getBlock() == Blocks.DIRT);
                        out.put("E_trench", trench + " gouged + " + rough + " torn up");
                        check(trench >= 2, "a trench is gouged where it skims the ground (" + trench + " + " + rough + ")");
                        double skid = lariatSkidEnd - lariatSkidStart;
                        out.put("E_skid", String.format(Locale.ROOT, "%.2f", skid));
                        check(skid > 0.9 && skid < 2.4, String.format(Locale.ROOT, "the caster skids about 1.5 blocks after the blow (%.2f)", skid));
                        out.put("E_regen_pending", TerrainRegen.pending());
                        check(TerrainRegen.pending() >= 10, "every broken block is queued to grow back (" + TerrainRegen.pending() + ")");
                        check(LariatServer.RUNNING.isEmpty() && !DashServer.FROZEN.contains(target.getEntityId()), "the lariat finished cleanly");
                    });
                    check(LariatClient.afterimageFrames > 0 && LariatClient.ribbonFrames > 0, "afterimages and ribbons drawn (" + LariatClient.afterimageFrames + ", " + LariatClient.ribbonFrames + ")");
                    check(DashClient.invertFrames > 0 && LariatClient.webFrames > 0, "inverted frames and the lightning web drawn (" + DashClient.invertFrames + ", " + LariatClient.webFrames + ")");
                    check(shotNames.contains("lariat-crouch") && shotNames.contains("lariat-blink") && shotNames.contains("lariat-hit") && shotNames.contains("lariat-trail"), "lariat shots: crouch, blink, hit, trail (" + shotNames + ")");
                }
                if (t == pressAt + 50) server(() -> { if (target != null) target.setDead(); sp().connection.setPlayerLocation(LX + 4.5, 4, LZ - 2.5, 0, 0); });   // nothing left standing on a block that should grow back
                // The terrain grows back after regenSeconds (20 s) of game time.
                if (t == pressAt + 60 + SteamDash.regenSeconds * 20) server(() -> {
                    WorldServer w = sw();
                    int diff = 0; List<String> diffs = new ArrayList<>();
                    for (Map.Entry<BlockPos, IBlockState> en : lariatBefore.entrySet()) if (w.getBlockState(en.getKey()) != en.getValue()) { diff++; if (diffs.size() < 6) diffs.add(en.getKey() + " " + en.getValue() + " -> " + w.getBlockState(en.getKey())); }
                    out.put("E_regen_left", TerrainRegen.pending() + " queued, " + diff + " differ " + diffs);
                    check(TerrainRegen.pending() == 0 && diff == 0, "after " + SteamDash.regenSeconds + " s every block is back exactly (" + diff + " differ)");
                    // Restore on unload/stop: break one block through the queue and restore everything at once.
                    BlockPos g = new BlockPos(LX + 4, 3, LZ);
                    IBlockState was = w.getBlockState(g);
                    boolean took = TerrainRegen.breakTemporarily(w, g, sp());
                    TerrainRegen.restoreAll();
                    check(took && w.getBlockState(g) == was && TerrainRegen.pending() == 0, "restoreAll (server stop / unload) puts the ground back");
                });
                if (t == pressAt + 70 + SteamDash.regenSeconds * 20) finish();
                break;
            }
            default: break;
        }
    }
    private static final int LX = -40, LZ = -70;
    private Map<BlockPos, IBlockState> lariatBefore = new HashMap<>();
    private final Set<Integer> lariatStages = new HashSet<>();
    private final List<String> lariatTrace = Collections.synchronizedList(new ArrayList<>());
    private int lariatHeld; private double lariatSpread, lariatSkidStart = Double.NaN, lariatSkidEnd;
    private double[] lariatHoldAt;

    private Map<BlockPos, IBlockState> snapshot(WorldServer w) {
        Map<BlockPos, IBlockState> m = new HashMap<>();
        for (int x = LX - 4; x <= LX + 4; x++) for (int y = 2; y <= 8; y++) for (int z = LZ - 3; z <= LZ + 45; z++) { BlockPos b = new BlockPos(x, y, z); m.put(b, w.getBlockState(b)); }
        return m;
    }

    /** Server side, every tick of the lariat: stages seen, the hit-stop hold, the skid. */
    private void lariatSample() {
        LariatServer.L l = LariatServer.RUNNING.get(sp().getUniqueID());
        if (l == null) return;
        if (lariatStages.add(l.stage) || l.stage == 1 && lariatTrace.size() < 3)
            lariatTrace.add(String.format(Locale.ROOT, "stage=%d age=%d pos=%.2f,%.2f,%.2f dir=%.2f,%.2f,%.2f start=%s end=%s broken=%d victim=%s", l.stage, l.age, sp().posX, sp().posY, sp().posZ, l.dir[0], l.dir[1], l.dir[2], l.start, l.end, l.broken, l.victim));
        if (l.stage == 3 && target != null) {
            double[] now = {target.posX, target.posY, target.posZ, sp().posX, sp().posY, sp().posZ};
            if (lariatHoldAt == null) lariatHoldAt = now;
            else for (int i = 0; i < 6; i++) lariatSpread = Math.max(lariatSpread, Math.abs(now[i] - lariatHoldAt[i]));
            lariatHeld++;
        }
        if (l.stage == 4) {
            double along = sp().posZ;
            if (Double.isNaN(lariatSkidStart)) lariatSkidStart = along;
            lariatSkidEnd = along;
        }
    }
    private double springX0;
    private final Set<String> juggled = new LinkedHashSet<>();

    private double stepAvg(int inside) {
        synchronized (arrowSteps) {
            double sum = 0; int n = 0;
            for (int i = 1; i < arrowSteps.size(); i++) if ((int) arrowSteps.get(i)[1] == inside && (int) arrowSteps.get(i - 1)[1] == inside) { sum += Math.abs(arrowSteps.get(i)[0] - arrowSteps.get(i - 1)[0]); n++; }
            return n == 0 ? -1 : sum / n;
        }
    }

    private void next() { phase++; t = 0; }

    private void finish() {
        slowmo(false);
        out.put("passed", failed.isEmpty());
        out.put("pass_count", passed.size());
        out.put("fail_count", failed.size());
        out.put("failed", failed);
        out.put("passed_checks", passed);
        out.put("shots", shots);
        out.put("chat", new ArrayList<>(chat));
        out.put("windows", windows);
        out.put("white_frames_drawn", DashClient.whiteFrames);
        out.put("ink_frames_drawn", DashClient.inkFrames);
        out.put("max_rubble", maxRubble);
        try {
            Files.write(Paths.get("DASH-QA-RESULT.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out).getBytes(StandardCharsets.UTF_8));
            synchronized (sounds) { Files.write(Paths.get("DASH-QA-SOUNDS.json"), new GsonBuilder().create().toJson(sounds).getBytes(StandardCharsets.UTF_8)); }
        } catch (Exception x) { x.printStackTrace(); }
        mc().shutdown();
    }
}
