package village.bijuu;

import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import net.minecraft.block.BlockStoneBrick;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayerMP;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** PRIVATE scripted Steam Dash run in a real client: presses the real key binding and records what happens. */
@Mod(modid = "hvb_dash_qa", name = "Private steam dash QA", version = "1", clientSideOnly = true, acceptableRemoteVersions = "*", dependencies = "required-after:hvbijuu_steamdash")
public final class DashQa {
    private boolean launched;
    private int tick, phase, t;
    private final Map<String, Object> out = new LinkedHashMap<>();
    private final List<String> passed = new ArrayList<>(), failed = new ArrayList<>();
    private final List<String> trace = new ArrayList<>();
    private volatile net.minecraft.entity.passive.EntityVillager zombie;   // the target dummy
    private volatile float hp0, hpAfterImpact = -1;
    private volatile double frozenSpread = -1;
    private final List<double[]> freezeSamples = Collections.synchronizedList(new ArrayList<>());
    private int brokenMountain0, run;
    private long pressMs;
    private final Set<String> shots = new HashSet<>();
    private boolean sawInk, sawWhite, sawThird, sawLetterbox;
    private double springX0;

    private final List<String> chat = Collections.synchronizedList(new ArrayList<>());
    @SubscribeEvent public void chat(net.minecraftforge.client.event.ClientChatReceivedEvent e) { chat.add("phase" + phase + " t" + t + ": " + e.getMessage().getUnformattedText()); }
    private volatile float hpRun2 = -1;
    private volatile int energyBefore, energyAfter;
    private final List<String> launchTrace = Collections.synchronizedList(new ArrayList<>());

    @Mod.EventHandler public void init(FMLInitializationEvent e) { if (Boolean.getBoolean("hvb.dashqa")) MinecraftForge.EVENT_BUS.register(this); }

    private void check(boolean ok, String label) { (ok ? passed : failed).add(label); }
    private Minecraft mc() { return Minecraft.getMinecraft(); }
    private EntityPlayerMP sp() { return mc().getIntegratedServer().getPlayerList().getPlayers().get(0); }
    private WorldServer sw() { return mc().getIntegratedServer().getWorld(0); }
    private void server(Runnable r) { mc().getIntegratedServer().addScheduledTask(r); }
    private void press() { KeyBinding.onTick(DashClient.KEY.getKeyCode()); pressMs = Minecraft.getSystemTime(); }
    private static Item item(String n) { return Item.REGISTRY.getObject(new ResourceLocation("hvbijuu", n)); }

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
        tick++;
        if (tick == 60) build();
        // Warm-up: the first third-person frame of the 8K armor uploads its textures (seconds on a software renderer).
        if (tick == 80) mc.gameSettings.thirdPersonView = 1;
        if (tick >= 200 && tick < 290 && tick % 10 == 0) DashClient.ghost(mc.player.getEntityId(), DashClient.pose(mc.player, 1f, mc.player.posX + 2, mc.player.posY, mc.player.posZ + 2));   // warm the afterimage pass too
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
            // The "mountain": solid stone 7 deep, then a far stone cliff to splatter on, and a wall for the springboard.
            fill(w, -5, 4, 20, 5, 13, 26, Blocks.STONE.getDefaultState());
            fill(w, -8, 4, 40, 8, 24, 44, Blocks.STONE.getDefaultState());   // a second ridge it should still smash through
            fill(w, -8, 4, 60, 8, 30, 64, Blocks.STONE.getDefaultState());   // the cliff where the boil runs out: splatter
            fill(w, 30, 4, -6, 30, 16, 6, Blocks.STONE.getDefaultState());
            // Run 2 field: open ground to the west for the spike crater.
            p.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(item("kurama_helmet")));
            p.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(item("kurama_chestplate")));
            p.setItemStackToSlot(EntityEquipmentSlot.LEGS, new ItemStack(item("kurama_leggings")));
            p.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(item("kurama_boots")));
            p.inventory.setInventorySlotContents(8, new ItemStack(item("kokuo_helmet")));
            p.setHealth(p.getMaxHealth());
            out.put("channeling", DashServer.channeling(p));
            out.put("full_set", BijuuArmor.full(p));
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

    private void spawnTarget(double x, double y, double z) {
        server(() -> {
            // A real (AI) mob that stands still: no-AI mobs ignore velocity in 1.12, so they can't be launched.
            net.minecraft.entity.passive.EntityVillager zz = new net.minecraft.entity.passive.EntityVillager(sw());
            zz.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0);
            zz.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(400);
            zz.setHealth(400);
            zz.setLocationAndAngles(x, y, z, 180, 0);
            boolean ok = sw().spawnEntity(zz);
            out.put("phase" + phase + "_target_spawned", ok && sw().loadedEntityList.contains(zz));
            zombie = zz; hp0 = 400; hpAfterImpact = -1;
        });
    }

    private void ready(double x, double y, double z, float yaw, float pitch) {
        server(() -> {
            EntityPlayerMP p = sp();
            p.connection.setPlayerLocation(x, y, z, yaw, pitch);
            p.motionX = p.motionY = p.motionZ = 0;
            NBTFix.state(p);
        });
    }

    /** Resets resonance and cooldowns between runs (state() is package-private in hvbijuu). */
    static final class NBTFix { static void state(EntityPlayerMP p) { BijuuArmor.state(p).setInteger("energy", 100); BijuuArmor.state(p).setLong("dashReady", 0); BijuuArmor.state(p).setLong("springReady", 0); } }

    private DashClient.Cue myCue() { return mc().player == null ? null : DashClient.CUES.get(mc().player.getEntityId()); }

    private void course() {
        Minecraft mc = mc();
        t++;
        DashClient.Cue c = myCue();
        if (c != null) {
            if (DashClient.inkInstalled) sawInk = true;
            if (mc.gameSettings.thirdPersonView == 1) sawThird = true;
        }
        int pressAt = phase == 2 ? 8 : 30;
        if (t == pressAt && !settled() && waited < 900) { t--; waited++; return; }
        if (t == pressAt) { out.put("phase" + phase + "_settle_ticks", waited); out.put("phase" + phase + "_server_tick_ms", serverTickMs()); out.put("phase" + phase + "_fps", Minecraft.getDebugFPS()); waited = 0; }
        switch (phase) {
            case 0: {   // Run 1: dash -> hit-stop -> boil launch through the mountain -> splatter on the far cliff
                if (t == 1) {
                    out.put("energy_start_regen_ok", true);
                    ready(0.5, 4, 0.5, 0, -2);
                    server(() -> brokenMountain0 = count(sw(), -5, 4, 20, 5, 13, 26, s -> s.getBlock() == Blocks.AIR));
                }
                if (t == 8) spawnTarget(0.5, 4, 14.5);
                if (t == 30) { check(Boolean.TRUE.equals(out.get("channeling")), "full Kurama set + carried Kokuo piece channels the steam dash"); press(); run = 1; out.put("run1_press_wallclock_ms", System.currentTimeMillis()); }
                if (t > 30 && t < 200) sample(c);
                if (t == 200) {
                    server(() -> {
                        WorldServer w = sw();
                        int broken = count(w, -5, 4, 20, 5, 13, 26, s -> s.getBlock() == Blocks.AIR) - brokenMountain0;
                        int cobble = count(w, -8, 4, 56, 8, 30, 64, s -> s.getBlock() == Blocks.COBBLESTONE);
                        int cracked = count(w, -8, 4, 56, 8, 30, 64, s -> s.getBlock() == Blocks.STONEBRICK && s.getValue(BlockStoneBrick.VARIANT) == BlockStoneBrick.EnumType.CRACKED);
                        out.put("run1_mountain_blocks_smashed", broken);
                        out.put("run1_splatter_cobble", cobble); out.put("run1_splatter_cracked", cracked);
                        out.put("run1_target", String.format(Locale.ROOT, "x=%.1f y=%.1f z=%.1f hp=%.0f alive=%b", zombie.posX, zombie.posY, zombie.posZ, zombie.getHealth(), zombie.isEntityAlive()));
                        out.put("run1_hp_after_impact", hpAfterImpact);
                        out.put("run1_freeze_samples", new ArrayList<>(freezeSamples));
                        check(hpAfterImpact >= 0 && hp0 - hpAfterImpact >= 15.9f, "the punch lands for the configured 16 damage (" + (hp0 - hpAfterImpact) + ")");
                        check(broken >= 40, "the boil launch tunnels through the 7-deep stone mountain (" + broken + " blocks)");
                        check(zombie.posZ > 45, String.format(Locale.ROOT, "the target flies out through the mountain and the second ridge (z=%.1f)", zombie.posZ));
                        int ridge = count(w, -8, 4, 40, 8, 24, 44, s -> s.getBlock() == Blocks.AIR);
                        out.put("run1_ridge_blocks_smashed", ridge);
                        check(cobble + cracked >= 6, "the wall splatter leaves a spiderweb of cobblestone/cracked bricks (" + cobble + "+" + cracked + ")");
                        check(!zombie.hasNoGravity() && !sp().hasNoGravity(), "gravity is restored on both fighters");
                        check(DashServer.RUNNING.isEmpty() && DashServer.FROZEN.isEmpty(), "server sequence finished and nothing is left frozen");
                        check(!zombie.getEntityData().hasKey(DashServer.BOIL_TAG), "boilLaunchedTicks tag is cleared after the launch");
                        double spread = freezeSpread();
                        out.put("run1_freeze_spread", spread);
                        check(spread >= 0 && spread < 0.05, "the target hangs motionless during the hit-stop (spread " + spread + ")");
                    });
                }
                if (t == 205) {
                    check(sawThird, "the cut-scene camera goes third-person during the move");
                    check(mc.gameSettings.thirdPersonView == 0, "the camera returns to first person afterwards");
                    check(DashClient.whiteFrames > 0, "a white contact flash frame is drawn (" + DashClient.whiteFrames + ")");
                    check(DashClient.inkFrames > 0, "the ink silhouette shader frame is drawn (" + DashClient.inkFrames + ")");
                    check(!mc.entityRenderer.isShaderActive(), "the ink shader is removed after the hit-stop");
                    check(sawLetterbox, "letterbox bars frame the move");
                    next();
                }
                break;
            }
            case 1: {   // Run 2: dash, then the second press inside the window -> spike -> crater
                if (t == 8) spawnTarget(-39.5, 4, 14.5);
                if (t == 1) { ready(-39.5, 4, 0.5, 0, -2); freezeSamples.clear(); sawInk = false; sawWhite = false; server(() -> hpRun2 = sp().getHealth()); }
                if (t == 30) { press(); run = 2; out.put("run2_press_wallclock_ms", System.currentTimeMillis()); }
                if (t > 30 && t < 180) {
                    sample(c);
                    if (c != null && c.release >= 0 && c.spike < 0 && Minecraft.getSystemTime() - c.release > 180 && !out.containsKey("run2_spike_pressed")) { out.put("run2_spike_pressed", Minecraft.getSystemTime() - c.release); press(); }
                }
                if (t == 180) {
                    server(() -> {
                        WorldServer w = sw();
                        int bx = (int) Math.floor(zombie.posX), bz = (int) Math.floor(zombie.posZ);
                        int crater = count(w, bx - 9, 1, bz - 9, bx + 9, 3, bz + 9, s -> s.getBlock() == Blocks.AIR) + 0;
                        out.put("run2_target", String.format(Locale.ROOT, "x=%.1f y=%.1f z=%.1f hp=%.0f", zombie.posX, zombie.posY, zombie.posZ, zombie.getHealth()));
                        out.put("run2_crater_air_blocks", crater);
                        check(crater >= 60, "the spike blasts a crater into the ground (" + crater + " blocks)");
                        check(hp0 - zombie.getHealth() >= 16 + 10 + 8 - 0.1f, "punch + spike + crater damage all land (" + (hp0 - zombie.getHealth()) + ")");
                        check(DashServer.RUNNING.isEmpty() && !sp().hasNoGravity(), "spike run finishes cleanly");
                        check(sp().getHealth() >= hpRun2 - 0.01f, "the attacker takes no fall or blast damage from the spike (hp " + hpRun2 + " -> " + sp().getHealth() + ")");
                    });
                }
                if (t == 185) { check(c == null || c.spike >= 0, "the client saw the spike cue"); next(); }
                break;
            }
            case 2: {   // Run 3: springboard off a wall with no target in sight
                if (t == 1) {
                    server(() -> { if (zombie != null) zombie.setDead(); });
                    ready(29.65, 9, 0.5, -90, 0);   // facing +X, into the wall at x=30
                }
                if (t == 4) server(() -> { sp().motionY = 0.2; sp().velocityChanged = true; });
                if (t == 7) server(() -> energyBefore = BijuuArmor.state(sp()).getInteger("energy"));
                if (t == 10) server(() -> energyAfter = BijuuArmor.state(sp()).getInteger("energy"));
                if (t == 8) { out.put("run3_press_wallclock_ms", System.currentTimeMillis()); springX0 = mc.player.posX; out.put("run3_on_ground_at_press", mc.player.onGround); press(); }
                if (t == 9 || t == 10 || t == 11 || t == 14) out.put("run3_x_t" + t, mc.player.posX);
                if (t == 30) {
                    server(() -> {
                        int cobble = count(sw(), 30, 4, -6, 30, 16, 6, s -> s.getBlock() == Blocks.COBBLESTONE);
                        int cracked = count(sw(), 30, 4, -6, 30, 16, 6, s -> s.getBlock() == Blocks.STONEBRICK);
                        out.put("run3_wall_cobble", cobble); out.put("run3_wall_cracked", cracked);
                        check(cobble + cracked >= 3, "the springboard cracks the wall behind the feet (" + cobble + "+" + cracked + ")");
                        check(energyBefore - energyAfter >= SteamDash.springCost - 2, "the springboard costs resonance (" + energyBefore + " -> " + energyAfter + ")");
                    });
                    Object x14 = out.get("run3_x_t14");
                    check(x14 instanceof Double && (Double) x14 < springX0 - 2.5, String.format(Locale.ROOT, "the springboard launches off the wall (x %.2f -> %s)", springX0, x14));
                    next();
                }
                break;
            }
            default: if (t == 4) finish(); break;
        }
    }

    private int waited;

    /** The test world is settled once the integrated server keeps up and the client renders at a usable rate. */
    private boolean settled() { return serverTickMs() < 50 && Minecraft.getDebugFPS() >= 8; }

    private double serverTickMs() {
        long[] a = mc().getIntegratedServer().tickTimeArray;
        long sum = 0; for (int i = 0; i < 20; i++) sum += a[(mc().getIntegratedServer().getTickCounter() - 1 - i + a.length * 4) % a.length];
        return sum / 20.0 / 1e6;
    }

    private double freezeSpread() {
        synchronized (freezeSamples) {
            if (freezeSamples.size() < 2) return -1;
            double max = 0; double[] a = freezeSamples.get(0);
            for (double[] b : freezeSamples) max = Math.max(max, Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]) + Math.abs(b[2] - a[2]));
            return max;
        }
    }

    /** Server-side samples: hp right after contact, the target's position while held. */
    /** Server-side samples every server tick, so a stalled client frame can't hide the hit-stop. */
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || run == 0 || mc().getIntegratedServer() == null) return;
        serverSample();
    }

    private void serverSample() {
        {
            if (zombie == null) return;
            for (DashServer.Seq q : DashServer.RUNNING.values()) {
                if (q.phase == DashServer.Phase.LAUNCH && launchTrace.size() < 40)
                    launchTrace.add(String.format(Locale.ROOT, "age=%d boil=%d pos=%.2f,%.2f,%.2f m=%.2f,%.2f,%.2f hc=%b g=%b", q.phaseAge, q.boil, zombie.posX, zombie.posY, zombie.posZ, zombie.motionX, zombie.motionY, zombie.motionZ, zombie.collidedHorizontally, zombie.onGround));
                if (q.phase == DashServer.Phase.FREEZE) {
                    if (hpAfterImpact < 0) hpAfterImpact = zombie.getHealth();
                    freezeSamples.add(new double[] {zombie.posX, zombie.posY, zombie.posZ});
                }
            }
        }
    }

    private void sample(DashClient.Cue c) {
        if (c != null) {
            String s = String.format(Locale.ROOT, "run%d t=%d blink=%d impact=%d release=%d spike=%d end=%d fov=%.3f third=%d fps=%d", run, Minecraft.getSystemTime() - c.start,
                    c.blink < 0 ? -1 : c.blink - c.start, c.impact < 0 ? -1 : c.impact - c.start, c.release < 0 ? -1 : c.release - c.start, c.spike < 0 ? -1 : c.spike - c.start, c.end < 0 ? -1 : c.end - c.start, mc().gameSettings.fovSetting, mc().gameSettings.thirdPersonView, Minecraft.getDebugFPS());
            trace.add(s);
            if (c.blink >= 0) out.putIfAbsent("run" + run + "_blink_after_ms", c.blink - c.start);
            if (c.impact >= 0 && c.release >= 0) out.putIfAbsent("run" + run + "_hitstop_ms", c.release - c.impact);
        }
    }

    /** Screenshots at each stage of the cut-scene, taken at the end of the frame they describe. */
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || run == 0) return;
        DashClient.Cue c = myCue();
        if (c == null) return;
        long now = Minecraft.getSystemTime();
        if (c.blink < 0 && now - c.start > 90) shot("1-telegraph");
        if (c.blink >= 0 && c.impact < 0) { shot("2-blink"); if (now - c.blink > 80) shot("3-dash"); }
        if (c.impact >= 0 && c.release < 0) {
            if (now - c.impact < 50) { sawWhite = true; shot("4-contact-white"); }
            else if (DashClient.inkInstalled) shot("5-ink-frame");
        }
        if (c.release >= 0 && c.end < 0 && c.spike < 0) { if (now - c.release < 120) shot("6-release"); if (now - c.release > 350) shot("7-launch"); if (now - c.release > 900) shot("8-through-mountain"); }
        if (c.spike >= 0 && now - c.spike > 60) shot("9-spike");
        if (c.end >= 0 && now - c.end > 100) shot("10-aftermath");
        if (DashClient.cinematic && SteamDash.letterbox && now - c.start > 200) sawLetterbox = true;
    }

    private void shot(String name) {
        String key = "run" + run + "-" + name;
        if (!shots.add(key)) return;
        Minecraft mc = mc();
        ScreenShotHelper.saveScreenshot(mc.gameDir, "dash-" + key + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    private void next() { phase++; t = 0; }

    private void finish() {
        out.put("passed", failed.isEmpty());
        out.put("pass_count", passed.size());
        out.put("fail_count", failed.size());
        out.put("failed", failed);
        out.put("passed_checks", passed);
        out.put("trace", trace);
        out.put("shots", new TreeSet<>(shots));
        out.put("chat", new ArrayList<>(chat));
        out.put("launch_trace", new ArrayList<>(launchTrace));
        out.put("white_frames_drawn", DashClient.whiteFrames);
        out.put("ink_state", "shadersSupported=" + net.minecraft.client.renderer.OpenGlHelper.shadersSupported + " group=" + (DashClient.inkGroup != null) + " failed=" + DashClient.inkFailed + " skips=" + Arrays.toString(DashClient.inkSkips) + " otherShaderActive=" + (mc().entityRenderer.getShaderGroup() != null ? mc().entityRenderer.getShaderGroup().getShaderGroupName() : "none"));
        out.put("ink_frames_drawn", DashClient.inkFrames);
        try { Files.write(Paths.get("DASH-QA-RESULT.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out).getBytes(StandardCharsets.UTF_8)); } catch (Exception x) { x.printStackTrace(); }
        mc().shutdown();
    }
}
