package village.bijuu;

import com.google.gson.GsonBuilder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockSandStone;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Builds the "Steam Dash Arena" test world for the owner (-Dhvb.buildarena=true), or reloads the
 * finished world and checks it (-Dhvb.testarena=true). Not shipped in the mod.
 *
 * Deep-stone superflat (stand at y=57). Hub at the origin with signs, the issued armor and reset buttons.
 *   south (+z)  mountain run: target dummy, mountain, ridge, cliff, a pig pen for bullet time
 *   east  (+x)  overcharge line: three dummies in a row and a wall
 *   west  (-x)  juggle field: one dummy in open ground
 *   north (-z)  springboard wall
 * Buttons run the world's own functions (arena:reset_all, arena:dummies, per-station resets).
 */
@Mod(modid = "hvb_arena_builder", name = "Steam Dash arena builder", version = "1", clientSideOnly = true, acceptableRemoteVersions = "*", dependencies = "required-after:hvbijuu_steamdash")
public final class ArenaBuilder {
    static final String FOLDER = "SteamDashArena", NAME = "Steam Dash Arena";
    static final int G = 57;   // standing height
    private static final String PRESET = "3;minecraft:bedrock,52*minecraft:stone,3*minecraft:dirt,minecraft:grass;1;";
    private boolean build, finish, test, launched;
    private int tick;
    private final Map<String, Object> out = new LinkedHashMap<>();
    private final List<String> passed = new ArrayList<>(), failed = new ArrayList<>();

    @Mod.EventHandler public void init(FMLInitializationEvent e) {
        build = Boolean.getBoolean("hvb.buildarena");
        finish = Boolean.getBoolean("hvb.finisharena");
        test = Boolean.getBoolean("hvb.testarena");
        if (build || finish || test) MinecraftForge.EVENT_BUS.register(this);
    }

    private Minecraft mc() { return Minecraft.getMinecraft(); }
    private MinecraftServer srv() { return mc().getIntegratedServer(); }
    private WorldServer sw() { return srv().getWorld(0); }
    private EntityPlayerMP sp() { return srv().getPlayerList().getPlayers().get(0); }
    private void server(Runnable r) { srv().addScheduledTask(r); }
    private void cmd(String c) { srv().getCommandManager().executeCommand(srv(), c); }
    private void check(boolean ok, String label) { (ok ? passed : failed).add(label); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = mc();
        if (!launched) {
            if (mc.currentScreen instanceof GuiMainMenu) {
                launched = true;
                if (build) {
                    WorldSettings s = new WorldSettings(20261122L, GameType.SURVIVAL, false, false, WorldType.FLAT).enableCommands();
                    s.setGeneratorOptions(PRESET);
                    mc.launchIntegratedServer(FOLDER, NAME, s);
                } else mc.launchIntegratedServer(finish ? FOLDER : FOLDER + "Test", NAME, null);
            }
            return;
        }
        if (mc.player == null || mc.world == null || srv() == null) return;
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.currentScreen != null) mc.displayGuiScreen(null);
        tick++;
        if (build) buildSteps(); else if (finish) finishSteps(); else testSteps();
    }

    // ---- build ----

    private void buildSteps() {
        if (tick == 40) server(this::buildWorld);
        if (tick == 120) server(this::issueArmor);
        if (tick == 200) server(() -> sp().connection.setPlayerLocation(0.5, G, -2.5, 0, 0));
        if (tick == 240) {
            try { Files.write(Paths.get("ARENA-BUILD.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out).getBytes(StandardCharsets.UTF_8)); } catch (Exception x) { x.printStackTrace(); }
            mc().world.sendQuittingDisconnectingPacket();
            mc().loadWorld(null);
            mc().shutdown();
        }
    }

    private void buildWorld() {
        WorldServer w = sw();
        File dir = w.getSaveHandler().getWorldDirectory();
        writeFunctions(new File(dir, "data/functions/arena"));
        srv().getFunctionManager().reload();
        w.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        w.getGameRules().setOrCreateGameRule("doWeatherCycle", "false");
        w.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        w.getGameRules().setOrCreateGameRule("keepInventory", "true");
        w.getGameRules().setOrCreateGameRule("commandBlockOutput", "false");
        w.setWorldTime(6000);
        w.setSpawnPoint(new BlockPos(0, G, -2));
        cmd("function arena:reset_all");
        // Hub: a gold floor, the armor chest, reset buttons and the guide signs.
        for (int x = -3; x <= 3; x++) for (int z = -5; z <= 1; z++) w.setBlockState(new BlockPos(x, G - 1, z), Blocks.SANDSTONE.getDefaultState().withProperty(BlockSandStone.TYPE, BlockSandStone.EnumType.SMOOTH), 2);
        button(w, new BlockPos(-3, G, -5), "function arena:reset_all", "RESET ARENA", "terrain + dummies", "", "");
        button(w, new BlockPos(-1, G, -5), "function arena:dummies", "RESPAWN", "DUMMIES", "", "");
        sign(w, new BlockPos(1, G, -5), 0, "STEAM DASH", "ARENA", "Kurama set on,", "Kokuo piece in bag");
        sign(w, new BlockPos(3, G, -5), 0, "Chest: Gyuki set", "+ spare Kurama", "and Kokuo (try", "reverse combo)");
        chest(w, new BlockPos(3, G, -4));
        sign(w, new BlockPos(-2, G, 1), 8, "TAP B: dash", "HOLD B: charge", "B as they fly:", "relay  SNEAK+B: pin");
        sign(w, new BlockPos(0, G, 1), 8, "S: mountain run", "E: overcharge", "W: juggle", "N: springboard");
        sign(w, new BlockPos(2, G, 1), 8, "Settings:", "config/hvbijuu_", "steamdash.cfg", "(key in Controls)");
        // Station pads, each with a guide sign and a reset button.
        pad(w, 0, 8, "MOUNTAIN RUN", "Stand on gold,", "look at dummy,", "tap B", "function arena:reset_mountain");
        pad(w, 8, 0, "OVERCHARGE", "Look down the", "line, HOLD B", "for 1 second", "function arena:reset_line");
        pad(w, -8, 0, "JUGGLE", "Tap B, then B", "B again while they", "fly; SNEAK+B: pin", "function arena:reset_juggle");
        pad(w, -30, 18, "GYUKI LARIAT", "Wear the Gyuki", "set (chest), look", "at dummy, tap B", "function arena:reset_lariat");
        sign(w, new BlockPos(-3, G, -2), 12, "GYUKI LARIAT:", "west, then south", "(x -30, z 18)", "set in chest");
        pad(w, 0, -8, "SPRINGBOARD", "Jump into the", "wall, press B", "while touching it", "function arena:reset_springboard");
    }

    /** A gold standing pad facing outward from the hub, with a guide sign and a reset button beside it. */
    private void pad(WorldServer w, int x, int z, String a, String b, String c, String d, String reset) {
        w.setBlockState(new BlockPos(x, G - 1, z), Blocks.GOLD_BLOCK.getDefaultState(), 2);
        int sx = z != 0 ? x + 2 : x, sz = z != 0 ? z : z + 2;
        int bx = z != 0 ? x - 2 : x, bz = z != 0 ? z : z - 2;
        int rot = z > 0 ? 8 : z < 0 ? 0 : x > 0 ? 4 : 12;   // face back toward the hub
        sign(w, new BlockPos(sx, G, sz), rot, a, b, c, d);
        button(w, new BlockPos(bx, G, bz), reset, "RESET", "this station", "", "");
    }

    /** A standing sign; rot is the sign's 16-step rotation (0 faces south, 4 west, 8 north, 12 east). */
    private void sign(WorldServer w, BlockPos p, int rot, String... lines) {
        w.setBlockState(p, Blocks.STANDING_SIGN.getDefaultState().withProperty(net.minecraft.block.BlockStandingSign.ROTATION, rot), 2);
        TileEntity te = w.getTileEntity(p);
        if (te instanceof TileEntitySign) for (int i = 0; i < 4 && i < lines.length; i++) ((TileEntitySign) te).signText[i] = new TextComponentString(lines[i]);
    }

    /** A command block with a stone button on top and a label sign on its side. */
    private void button(WorldServer w, BlockPos p, String command, String l1, String l2, String l3, String l4) {
        w.setBlockState(p, Blocks.COMMAND_BLOCK.getDefaultState(), 2);
        TileEntity te = w.getTileEntity(p);
        if (te instanceof TileEntityCommandBlock) ((TileEntityCommandBlock) te).getCommandBlockLogic().setCommand(command);
        w.setBlockState(p.up(), Blocks.STONE_BUTTON.getDefaultState().withProperty(BlockButton.FACING, EnumFacing.UP), 2);
        BlockPos label = p.south();
        w.setBlockState(label, Blocks.WALL_SIGN.getDefaultState().withProperty(net.minecraft.block.BlockWallSign.FACING, EnumFacing.SOUTH), 2);
        TileEntity st = w.getTileEntity(label);
        if (st instanceof TileEntitySign) { String[] ls = {l1, l2, l3, l4}; for (int i = 0; i < 4; i++) ((TileEntitySign) st).signText[i] = new TextComponentString(ls[i]); }
    }

    private void chest(WorldServer w, BlockPos p) { w.setBlockState(p, Blocks.CHEST.getDefaultState(), 2); }

    /** Issues real ledger-backed armor through the mod's own console command, then equips and stores it. */
    private void issueArmor() {
        EntityPlayerMP p = sp();
        String n = p.getName();
        p.inventory.clear();
        cmd("bijuureward " + n + " kurama set");
        cmd("bijuureward " + n + " kokuo helmet");
        cmd("bijuureward " + n + " kokuo set");
        cmd("bijuureward " + n + " kurama helmet");
        cmd("bijuureward " + n + " gyuki set");
        List<ItemStack> kurama = new ArrayList<>(), kokuo = new ArrayList<>(), gyuki = new ArrayList<>();
        for (int i = 0; i < p.inventory.getSizeInventory(); i++) {
            ItemStack s = p.inventory.getStackInSlot(i);
            if (s.getItem() instanceof BijuuArmor.Armor) { int b = ((BijuuArmor.Armor) s.getItem()).beast; (b == 8 ? kurama : b == 7 ? gyuki : kokuo).add(s.copy()); }
        }
        p.inventory.clear();
        out.put("issued_kurama", kurama.size()); out.put("issued_kokuo", kokuo.size()); out.put("issued_gyuki", gyuki.size());
        // Wear one Kurama set, carry one Kokuo piece; the rest goes in the hub chest.
        TileEntityChest chest = (TileEntityChest) sw().getTileEntity(new BlockPos(3, G, -4));
        int slot = 0;
        boolean[] worn = new boolean[4];
        boolean carried = false;
        for (ItemStack s : kurama) {
            BijuuArmor.Armor a = (BijuuArmor.Armor) s.getItem();
            if (!worn[a.part]) { worn[a.part] = true; p.setItemStackToSlot(EntityEquipmentSlot.values()[5 - a.part], s); }
            else chest.setInventorySlotContents(slot++, s);
        }
        for (ItemStack s : kokuo) {
            if (!carried) { p.inventory.setInventorySlotContents(8, s); carried = true; }
            else chest.setInventorySlotContents(slot++, s);
        }
        for (ItemStack s : gyuki) chest.setInventorySlotContents(slot++, s);
        out.put("valid_worn", BijuuArmor.full(p));
        out.put("channeling", DashServer.channeling(p));
    }

    private static final String DUMMY = "{Profession:0,Attributes:[{Name:\"generic.movementSpeed\",Base:0.0d},{Name:\"generic.maxHealth\",Base:400.0d}],Health:400.0f,CustomName:\"Target Dummy\",CustomNameVisible:1b,PersistenceRequired:1b,Tags:[\"dummy\"],Rotation:[%sf,0f]}";

    private static String dummy(double x, double z, int yaw) { return String.format(Locale.ROOT, "summon villager %.1f %d %.1f " + DUMMY, x, G, z, String.valueOf(yaw)); }

    /** Terrain resets: rock below, dirt and grass on top, clear air above, then the station's structures. */
    private static List<String> ground(int x0, int z0, int x1, int z1) {
        List<String> c = new ArrayList<>();
        for (int y = 30; y <= 52; y += 8) c.add(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", x0, y, z0, x1, Math.min(52, y + 7), z1));
        c.add(String.format(Locale.ROOT, "fill %d 53 %d %d 55 %d minecraft:dirt", x0, z0, x1, z1));
        c.add(String.format(Locale.ROOT, "fill %d 56 %d %d 56 %d minecraft:grass", x0, z0, x1, z1));
        for (int y = 57; y <= 95; y += 6) c.add(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", x0, y, z0, x1, Math.min(95, y + 5), z1));
        return c;
    }

    private static void writeFunctions(File dir) {
        dir.mkdirs();
        Map<String, List<String>> f = new LinkedHashMap<>();
        List<String> mountain = ground(-14, 10, 14, 80);
        mountain.add("fill -6 57 28 6 67 34 minecraft:stone");
        mountain.add("fill -9 57 48 9 77 52 minecraft:stone");
        mountain.add("fill -10 57 68 10 86 72 minecraft:stone");
        mountain.add("fill 8 57 18 12 57 18 minecraft:oak_fence");
        mountain.add("fill 8 57 22 12 57 22 minecraft:oak_fence");
        mountain.add("fill 8 57 18 8 57 22 minecraft:oak_fence");
        mountain.add("fill 12 57 18 12 57 22 minecraft:oak_fence");
        f.put("reset_mountain", mountain);
        List<String> line = ground(10, -14, 62, 14);
        line.add("fill 38 57 -8 41 69 8 minecraft:stone");
        f.put("reset_line", line);
        f.put("reset_juggle", ground(-85, -14, -10, 14));
        List<String> spring = ground(-14, -40, 14, -10);
        spring.add("fill -6 57 -14 6 71 -13 minecraft:stone");
        f.put("reset_springboard", spring);
        // Gyuki lariat lane: a glass wall and a hedge on the blink path, then a fence, a far glass wall and snow behind the dummy.
        List<String> lariat = ground(-40, 16, -20, 90);
        lariat.add("fill -32 57 24 -28 59 24 minecraft:glass");
        lariat.add("fill -31 57 27 -29 58 27 minecraft:leaves 4");
        lariat.add("fill -32 57 34 -28 57 34 minecraft:oak_fence");
        lariat.add("fill -32 57 38 -28 60 38 minecraft:glass");
        lariat.add("fill -32 57 41 -28 57 49 minecraft:snow_layer");
        f.put("reset_lariat", lariat);
        f.put("dummies", Arrays.asList(
                "kill @e[tag=dummy]", "kill @e[tag=bystander]",
                dummy(0.5, 22.5, 180), dummy(16.5, 0.5, 90), dummy(22.5, 1.1, 90), dummy(28.5, -0.1, 90), dummy(-22.5, 0.5, -90), dummy(-29.5, 31.5, 180),
                "summon pig 9.5 57 20.5 {PersistenceRequired:1b,Tags:[\"bystander\"]}", "summon pig 11.5 57 19.5 {PersistenceRequired:1b,Tags:[\"bystander\"]}",
                "tellraw @a {\"text\":\"Dummies ready.\",\"color\":\"gold\"}"));
        f.put("reset_all", Arrays.asList("function arena:reset_mountain", "function arena:reset_line", "function arena:reset_juggle", "function arena:reset_springboard", "function arena:reset_lariat", "function arena:dummies",
                "tellraw @a {\"text\":\"Arena reset.\",\"color\":\"gold\"}"));
        for (Map.Entry<String, List<String>> en : f.entrySet()) {
            try { Files.write(new File(dir, en.getKey() + ".mcfunction").toPath(), String.join("\n", en.getValue()).concat("\n").getBytes(StandardCharsets.UTF_8)); }
            catch (Exception x) { throw new RuntimeException(x); }
        }
    }

    // ---- finish: the world's functions load on reopening; run the full reset once and save ----

    private void finishSteps() {
        if (tick == 40) server(() -> cmd("function arena:reset_all"));
        if (tick == 100) server(() -> {
            int dummies = sw().getEntities(Entity.class, x -> x.getTags().contains("dummy")).size();
            boolean built = sw().getBlockState(new BlockPos(0, G + 3, 31)).getBlock() == Blocks.STONE && sw().getBlockState(new BlockPos(0, G + 10, 70)).getBlock() == Blocks.STONE;
            out.put("finish_dummies", dummies); out.put("finish_built", built);
            sp().connection.setPlayerLocation(0.5, G, -2.5, 0, 0);
            sp().setHealth(sp().getMaxHealth());
            BijuuArmor.state(sp()).setInteger("energy", 100);
        });
        if (tick == 140) {
            try { Files.write(Paths.get("ARENA-FINISH.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out).getBytes(StandardCharsets.UTF_8)); } catch (Exception x) { x.printStackTrace(); }
            mc().world.sendQuittingDisconnectingPacket();
            mc().loadWorld(null);
            mc().shutdown();
        }
    }

    // ---- test the finished world ----

    private double hp0;

    private void testSteps() {
        if (tick == 60) server(() -> {
            EntityPlayerMP p = sp();
            out.put("worn_full_set", BijuuArmor.full(p));
            check(BijuuArmor.full(p) == 8, "the loaded world's player wears a valid issued Kurama set");
            check(DashServer.channeling(p), "the carried Kokuo piece is valid, so the steam dash can be channeled");
            TileEntity te = sw().getTileEntity(new BlockPos(3, G, -4));
            int valid = 0;
            if (te instanceof TileEntityChest) for (int i = 0; i < ((TileEntityChest) te).getSizeInventory(); i++) if (BijuuArmor.valid(sw(), ((TileEntityChest) te).getStackInSlot(i))) valid++;
            check(valid >= 9, "the hub chest holds valid spare armor, the Gyuki set included (" + valid + " pieces)");
            check(sw().getEntities(Entity.class, x -> x.getTags().contains("dummy")).size() == 6, "all six target dummies are in place");
            check(srv().getFunctionManager().getFunctions().containsKey(new net.minecraft.util.ResourceLocation("arena", "reset_all")), "the arena reset functions load with the world");
            check(sw().getBlockState(new BlockPos(0, G + 3, 31)).getBlock() == Blocks.STONE && sw().getBlockState(new BlockPos(0, G + 10, 70)).getBlock() == Blocks.STONE, "mountain and cliff are built");
            p.connection.setPlayerLocation(0.5, G, 8.5, 0, 2);
            BijuuArmor.state(p).setInteger("energy", 100);
        });
        if (tick == 100) server(() -> {
            Entity d = sw().getEntities(Entity.class, x -> x.getTags().contains("dummy") && x.posZ > 20).get(0);
            hp0 = ((net.minecraft.entity.EntityLivingBase) d).getHealth();
        });
        if (tick == 110) KeyBinding.setKeyBindState(DashClient.KEY.getKeyCode(), true);
        if (tick == 111) KeyBinding.setKeyBindState(DashClient.KEY.getKeyCode(), false);
        if (tick == 260) server(() -> {
            List<Entity> ds = sw().getEntities(Entity.class, x -> x.getTags().contains("dummy") && x.posX > -3 && x.posX < 3 && x.posZ > 15);
            double hp = ds.isEmpty() ? hp0 : ((net.minecraft.entity.EntityLivingBase) ds.get(0)).getHealth();
            out.put("mountain_dummy", ds.isEmpty() ? "gone" : String.format(Locale.ROOT, "z=%.1f hp=%.0f", ds.get(0).posZ, hp));
            check(hp < hp0 - 20, "a tap at the mountain station lands the full combo (" + (hp0 - hp) + " damage)");
            int air = 0;
            for (int x = -6; x <= 6; x++) for (int y = G; y <= G + 10; y++) for (int z = 28; z <= 34; z++) if (sw().isAirBlock(new BlockPos(x, y, z))) air++;
            out.put("mountain_air_after_dash", air);
            check(air > 40, "the dummy is launched through the mountain (" + air + " blocks)");
            cmd("function arena:reset_all");
        });
        if (tick == 300) server(() -> {
            int air = 0;
            for (int x = -6; x <= 6; x++) for (int y = G; y <= G + 10; y++) for (int z = 28; z <= 34; z++) if (sw().isAirBlock(new BlockPos(x, y, z))) air++;
            check(air == 0, "RESET ARENA rebuilds the mountain (" + air + " air left)");
            check(sw().getEntities(Entity.class, x -> x.getTags().contains("dummy")).size() == 6, "RESET ARENA respawns the dummies");
        });
        // The Gyuki station: put on the Gyuki set from the chest and throw the lariat down the lane.
        if (tick == 330) server(() -> {
            EntityPlayerMP p = sp();
            TileEntityChest chest = (TileEntityChest) sw().getTileEntity(new BlockPos(3, G, -4));
            for (int i = 0; i < chest.getSizeInventory(); i++) {
                ItemStack s = chest.getStackInSlot(i);
                if (s.getItem() instanceof BijuuArmor.Armor && ((BijuuArmor.Armor) s.getItem()).beast == 7) {
                    BijuuArmor.Armor a = (BijuuArmor.Armor) s.getItem();
                    EntityEquipmentSlot slot = EntityEquipmentSlot.values()[5 - a.part];
                    ItemStack old = p.getItemStackFromSlot(slot);
                    p.setItemStackToSlot(slot, s);
                    chest.setInventorySlotContents(i, old);
                }
            }
            check(LariatServer.wearing(p), "the chest's Gyuki set is valid and worn");
            p.connection.setPlayerLocation(-29.5, G, 18.5, 0, 2);
            BijuuArmor.state(p).setInteger("energy", 100);
            Entity d = sw().getEntities(Entity.class, x -> x.getTags().contains("dummy") && x.posX < -25).get(0);
            hp0 = ((net.minecraft.entity.EntityLivingBase) d).getHealth();
        });
        if (tick == 360) KeyBinding.setKeyBindState(DashClient.KEY.getKeyCode(), true);
        if (tick == 361) KeyBinding.setKeyBindState(DashClient.KEY.getKeyCode(), false);
        if (tick == 420) server(() -> {
            List<Entity> ds = sw().getEntities(Entity.class, x -> x.getTags().contains("dummy") && x.posX < -25);
            double hp = ds.isEmpty() ? hp0 : ((net.minecraft.entity.EntityLivingBase) ds.get(0)).getHealth();
            out.put("lariat_dummy", ds.isEmpty() ? "gone" : String.format(Locale.ROOT, "z=%.1f hp=%.0f", ds.get(0).posZ, hp));
            check(hp <= hp0 - SteamDash.lariatDamage + 0.1, "the lariat at the Gyuki station lands (" + (hp0 - hp) + " damage)");
            check(!ds.isEmpty() && ds.get(0).posZ > 40, "the dummy is launched down the lane");
            check(sw().isAirBlock(new BlockPos(-30, G, 24)) && sw().isAirBlock(new BlockPos(-30, G + 1, 38)), "it smashes through both glass walls");
            cmd("function arena:reset_all");
        });
        if (tick == 470) server(() -> {
            check(sw().getBlockState(new BlockPos(-30, G, 24)).getBlock() == Blocks.GLASS, "RESET ARENA rebuilds the lariat lane");
            // Leave the player as the world ships: Kurama on, by the hub.
            EntityPlayerMP p = sp();
            TileEntityChest chest = (TileEntityChest) sw().getTileEntity(new BlockPos(3, G, -4));
            for (int i = 0; i < chest.getSizeInventory(); i++) {
                ItemStack s = chest.getStackInSlot(i);
                if (s.getItem() instanceof BijuuArmor.Armor && ((BijuuArmor.Armor) s.getItem()).beast == 8) {
                    EntityEquipmentSlot slot = EntityEquipmentSlot.values()[5 - ((BijuuArmor.Armor) s.getItem()).part];
                    ItemStack old = p.getItemStackFromSlot(slot);
                    p.setItemStackToSlot(slot, s); chest.setInventorySlotContents(i, old);
                }
            }
        });
        if (tick == 500) {
            out.put("passed", failed.isEmpty()); out.put("pass_count", passed.size()); out.put("failed", failed); out.put("passed_checks", passed);
            try { Files.write(Paths.get("ARENA-TEST.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out).getBytes(StandardCharsets.UTF_8)); } catch (Exception x) { x.printStackTrace(); }
            mc().shutdown();
        }
    }
}
