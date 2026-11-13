package village.vale.arsenal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** PRIVATE visual probe: activates each hand technique through the real server path and saves screenshots. Never shipped. */
@Mod(modid = "valevfxshots", name = "Private VFX screenshot probe", version = "1", clientSideOnly = true, acceptableRemoteVersions = "*")
public final class VfxShots {
    private int tick, phase = -1, styleIndex;
    private EntityArmorStand cam;
    private boolean launched;
    private EntityArmorStand target;
    private final List<String> log = new ArrayList<>();

    @Mod.EventHandler public void init(FMLInitializationEvent e) {
        if (Boolean.getBoolean("valeorders.vfxshots")) MinecraftForge.EVENT_BUS.register(this);
    }

    private static final String[] STYLES = {"spiral", "charge", "fireedge", "windedge", "lava", "lightmode"};

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!launched) {
            if (mc.currentScreen instanceof GuiMainMenu) {
                launched = true;
                WorldSettings s = new WorldSettings(1234L, GameType.CREATIVE, false, false, WorldType.FLAT);
                mc.launchIntegratedServer("vfxshots-" + System.currentTimeMillis(), "vfxshots", s);
            }
            return;
        }
        if (mc.player == null || mc.world == null || mc.getIntegratedServer() == null) return;
        tick++;
        mc.gameSettings.hideGUI = true;
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.currentScreen != null) mc.displayGuiScreen(null);
        mc.gameSettings.renderDistanceChunks = 4;
        mc.gameSettings.gammaSetting = 1.0f;
        IntegratedServer server = mc.getIntegratedServer();
        if (tick == 80) server.addScheduledTask(() -> {
            EntityPlayerMP p = server.getPlayerList().getPlayers().get(0);
            p.world.setWorldTime(6000);
            p.world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
            p.capabilities.isFlying = false;
            p.world.getChunk(0, 0);
            p.connection.setPlayerLocation(0.5, 4, 0.5, 0, 0);
            target = new EntityArmorStand(p.world, 1.7, 4, 2.0);
            target.setNoGravity(true);
            p.world.spawnEntity(target);
        });
        if (tick < 300) return;
        if (Boolean.getBoolean("valeorders.vfxshots.weapons")) { weapons(mc, server, tick - 300); return; }
        int local = tick - 300, per = 120;
        int s = local / per, t = local % per;
        if (s >= STYLES.length) {
            if (t == 0) { log.add("done"); mc.shutdown(); }
            return;
        }
        String style = STYLES[s];
        if (t == 0) server.addScheduledTask(() -> {
            EntityPlayerMP p = server.getPlayerList().getPlayers().get(0);
            Scrolls.state(p).removeTag("mode");
            ScrollEffects.mode(p, style, 2400);
        });
        if (t == 12) {
            long client = mc.world.loadedEntityList.stream().filter(x -> x instanceof HandTechnique).count();
            server.addScheduledTask(() -> {
                EntityPlayerMP p = server.getPlayerList().getPlayers().get(0);
                long srv = p.world.loadedEntityList.stream().filter(x -> x instanceof HandTechnique).count();
                System.out.println("VFXPROBE " + style + " server=" + srv + " client=" + client + " map=" + HandTechnique.CLIENT_BY_OWNER.keySet()
                    + " player=" + p.getEntityId() + " mode=" + Scrolls.state(p).getString("mode") + " until=" + Scrolls.state(p).getLong("modeUntil") + " now=" + village.vale.ArsenalAccess.now(p));
            });
        }
        int[] shots = {3, 9, 30, 31, 32};
        for (int i = 0; i < shots.length; i++) {
            if (t == shots[i]) { mc.gameSettings.thirdPersonView = 2; shot(mc, style + "_front_t" + shots[i]); }
        }
        if (t == 40) { mc.gameSettings.thirdPersonView = 1; }
        if (t == 44) shot(mc, style + "_back");
        if (t == 50) { mc.gameSettings.thirdPersonView = 0; }
        if (t == 54) shot(mc, style + "_firstperson");
        if (t == 60) { mc.gameSettings.thirdPersonView = 2; }
        if (t == 66) server.addScheduledTask(() -> {
            EntityPlayerMP p = server.getPlayerList().getPlayers().get(0);
            EntityArmorStand stand = p.world.getEntitiesWithinAABB(EntityArmorStand.class, p.getEntityBoundingBox().grow(6)).stream().findFirst().orElse(null);
            if (stand != null) HandTechnique.impact(p, stand);
        });
        if (t == 69 || t == 72 || t == 76) shot(mc, style + "_impact_t" + (t - 66));
        if (t == 90) server.addScheduledTask(() -> Scrolls.state(server.getPlayerList().getPlayers().get(0)).removeTag("mode"));
    }

    private void weapons(Minecraft mc, IntegratedServer server, int local) {
        int per = 34, s = local / per, t = local % per;
        ArsenalSpec[] specs = ArsenalSpec.values();
        if (s >= specs.length) { if (t == 0) mc.shutdown(); return; }
        ArsenalSpec spec = specs[s];
        String name = String.format("w%02d_%s", s, spec.key);
        if (local == 0) server.addScheduledTask(() -> {
            EntityPlayerMP p = server.getPlayerList().getPlayers().get(0);
            for (EntityArmorStand st : p.world.getEntities(EntityArmorStand.class, x -> true)) st.setDead();
            net.minecraft.entity.monster.EntityHusk husk = new net.minecraft.entity.monster.EntityHusk(p.world);
            husk.setPosition(0.5, 4, 3.6); husk.rotationYaw = husk.rotationYawHead = husk.renderYawOffset = 180;
            husk.setNoAI(true); husk.setSilent(true); husk.setEntityInvulnerable(true);
            p.world.spawnEntity(husk);
        });
        if (cam == null) {
            cam = new EntityArmorStand(mc.world, -4.6, 4.2, 2.0);
            cam.rotationYaw = cam.prevRotationYaw = -90; cam.rotationPitch = cam.prevRotationPitch = 6;
            cam.setNoGravity(true);
            mc.world.spawnEntity(cam);
        }
        cam.setPositionAndRotation(-4.6, 4.2, 2.0, -90, 6); cam.prevRotationYaw = -90; cam.prevRotationPitch = 6;
        cam.lastTickPosX = cam.prevPosX = cam.posX; cam.lastTickPosY = cam.prevPosY = cam.posY; cam.lastTickPosZ = cam.prevPosZ = cam.posZ;
        if (t == 0) {
            mc.setRenderViewEntity(cam); mc.gameSettings.thirdPersonView = 0;
            server.addScheduledTask(() -> {
                EntityPlayerMP p = server.getPlayerList().getPlayers().get(0);
                for (WeaponPulse w : p.world.getEntities(WeaponPulse.class, x -> true)) w.setDead();
                p.connection.setPlayerLocation(0.5, 4, 0.5, 0, 0);
                WeaponPulse.cast(p, spec, 1f);
            });
            for (net.minecraft.entity.Entity x : new ArrayList<>(mc.world.loadedEntityList)) if (x instanceof ArsenalEntity) x.setDead();
            EntityPlayerMP sp = server.getPlayerList().getPlayers().get(0);
            int shotMode = shotMode(spec.role), fieldMode = fieldMode(spec.role);
            if (shotMode >= 0) {
                ArsenalShot a = new ArsenalShot(mc.world);
                a.initialize(sp, new net.minecraft.item.ItemStack(Arsenal.item(spec)), spec, shotMode, 1f);
                a.setPosition(0.3, 5.3, 1.6);
                a.motionX = 0.0; a.motionY = 0.0; a.motionZ = 0.06;
                mc.world.spawnEntity(a);
            }
            if (fieldMode >= 0) {
                ArsenalField fl = new ArsenalField(mc.world);
                fl.initialize(sp, new net.minecraft.item.ItemStack(Arsenal.item(spec)), spec, fieldMode, 1f);
                fl.setPosition(0.5, fieldMode == ArsenalField.EMITTER ? 4.8 : 4.0, 4.0);
                mc.world.spawnEntity(fl);
            }
        }
        if (t == 4) shot(mc, name + "_a_side");
        if (t == 9) shot(mc, name + "_b_side");
        if (t == 10) { mc.setRenderViewEntity(mc.player); mc.gameSettings.thirdPersonView = 1; }
        if (t == 14) shot(mc, name + "_c_back");
        if (t == 18) { mc.setRenderViewEntity(cam); mc.gameSettings.thirdPersonView = 0; }
        if (t == 20) { server.addScheduledTask(() -> WeaponPulse.swing(server.getPlayerList().getPlayers().get(0), spec, spec.role == ArsenalSpec.Role.SWEEP)); }
        if (t == 24) shot(mc, name + "_d_swing");
    }

    private static int shotMode(ArsenalSpec.Role r) {
        switch (r) {
            case RETURN: return ArsenalShot.RETURN; case MARK: return ArsenalShot.MARK; case DRAIN_BOLT: return ArsenalShot.DRAIN;
            case GUIDED: return ArsenalShot.GUIDED; case BIND: return ArsenalShot.BIND; case VENOM: return ArsenalShot.VENOM;
            case MIST: return ArsenalShot.MIST; case FIRE: return ArsenalShot.FIRE; case SONIC: return ArsenalShot.GRAPPLE;
            case EMITTER: return ArsenalShot.NEEDLE; default: return -1;
        }
    }

    private static int fieldMode(ArsenalSpec.Role r) {
        switch (r) {
            case EMITTER: return ArsenalField.EMITTER; case TRAP: return ArsenalField.TRAP; case EXTEND: return ArsenalField.SNARE; case MIST: return ArsenalField.MIST;
            default: return -1;
        }
    }

    private void shot(Minecraft mc, String name) {
        ScreenShotHelper.saveScreenshot(mc.gameDir, "vfx_" + name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }
}
