package village.dungeonguard;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

/** Metadata and wire-contract checks executed by the gated disposable Forge fixture. */
public final class AreaNativeChecks {
    private static final Gson JSON = new Gson();
    private AreaNativeChecks() { }

    public static void run(MinecraftServer server, BiConsumer<String, Boolean> check) {
        GuardConfig old = JSON.fromJson("{\"schema\":1,\"regions\":[],\"enabled\":true}", GuardConfig.class);
        old.validate();
        check.accept("old 1.0 settings gain empty area metadata without losing protection", old.dungeonLabels != null
                && old.dungeonLabels.isEmpty() && old.bossAreas != null && old.bossAreas.isEmpty() && old.lockOutsideDungeons);

        GuardConfig config = fixture();
        config.validate();
        GuardConfig restored = JSON.fromJson(JSON.toJson(config), GuardConfig.class);
        restored.validate();
        check.accept("area metadata survives settings JSON roundtrip", "FIRE".equals(restored.dungeonLabels.get("fire_shrine").element)
                && "Ember Guardian".equals(restored.bossAreas.get(0).bossName)
                && restored.bossAreas.get(0).bounds.minX == 104);

        AreaState dungeon = AreaResolver.at(config, 0, new BlockPos(102, 66, 102));
        check.accept("dungeon resolves configured element and display title", dungeon.key.equals("fire_shrine")
                && dungeon.element.equals("FIRE") && dungeon.title.equals("The Ember Shrine") && !dungeon.isBoss());
        check.accept("dungeon minimum and maximum corners are included", !AreaResolver.at(config, 0, new BlockPos(100, 64, 100)).isEmpty()
                && !AreaResolver.at(config, 0, new BlockPos(120, 80, 120)).isEmpty());
        check.accept("one block outside each dungeon axis clears the label", AreaResolver.at(config, 0, new BlockPos(99, 64, 100)).isEmpty()
                && AreaResolver.at(config, 0, new BlockPos(100, 81, 100)).isEmpty()
                && AreaResolver.at(config, 0, new BlockPos(100, 64, 121)).isEmpty());
        check.accept("same coordinates in another dimension do not leak area labels", AreaResolver.at(config, -1, new BlockPos(105, 66, 105)).isEmpty());

        AreaState boss = AreaResolver.at(config, 0, new BlockPos(105, 66, 105));
        check.accept("boss area overrides parent title while retaining dungeon element", boss.key.equals("fire_shrine/inner_sanctum")
                && boss.element.equals("FIRE") && boss.title.equals("Inner Sanctum") && boss.boss.equals("Ember Guardian") && boss.isBoss());
        check.accept("boss zone inclusive corners resolve the same encounter", AreaResolver.at(config, 0, new BlockPos(104, 65, 104)).equals(boss)
                && AreaResolver.at(config, 0, new BlockPos(108, 72, 108)).equals(boss));
        check.accept("leaving boss zone restores parent dungeon label", AreaResolver.at(config, 0, new BlockPos(109, 66, 108)).equals(dungeon));
        AreaState second = AreaResolver.at(config, 0, new BlockPos(115, 66, 115));
        check.accept("separate boss areas show their own boss names", second.key.equals("fire_shrine/upper_gate")
                && second.title.equals("Upper Gate") && second.boss.equals("Gate Sentinel") && !second.equals(boss));
        check.accept("unconfigured element stays general instead of guessing from dungeon ID", AreaResolver.at(config, 1, new BlockPos(100, 65, 100)).element.equals("GENERAL")
                && AreaResolver.at(config, 1, new BlockPos(100, 65, 100)).title.equals("Water Annex"));
        check.accept("dungeon and boss headings identify area kind", dungeon.heading().equals("FIRE / DUNGEON")
                && boss.heading().equals("FIRE / BOSS AREA"));

        config.dungeonLabels.get("fire_shrine").displayName = "Renamed Ember Shrine";
        AreaState renamed = AreaResolver.at(config, 0, new BlockPos(102, 66, 102));
        check.accept("renaming an occupied area changes display value without changing identity", renamed.key.equals(dungeon.key)
                && !renamed.equals(dungeon) && renamed.title.equals("Renamed Ember Shrine"));
        check.accept("leaving every region returns the explicit empty state", AreaResolver.at(config, 0, new BlockPos(0, 70, 0)).equals(AreaState.EMPTY));
        check.accept("missing location input produces no stale label", AreaResolver.at(config, 0, null).isEmpty()
                && AreaResolver.at(null, 0, BlockPos.ORIGIN).isEmpty());

        invalidConfig(check, "orphan dungeon label is rejected", c -> c.dungeonLabels.put("missing", new GuardConfig.DungeonLabel()));
        invalidConfig(check, "boss area with missing parent is rejected", c -> c.bossAreas.get(0).dungeon = "missing");
        invalidConfig(check, "boss zone extending outside parent is rejected", c -> c.bossAreas.get(0).bounds.minX = 99);
        invalidConfig(check, "boss zone in wrong dimension is rejected", c -> c.bossAreas.get(0).bounds.dimension = -1);
        invalidConfig(check, "overlapping boss zones cannot create ambiguous display priority", c -> c.bossAreas.get(1).bounds =
                new GuardRegion("overlapping", 0, 107, 66, 107, 110, 70, 110));
        invalidConfig(check, "duplicate boss identities are rejected", c -> c.bossAreas.get(1).id = c.bossAreas.get(0).id);
        invalidConfig(check, "unknown element metadata is rejected", c -> c.dungeonLabels.get("fire_shrine").element = "LAVA");
        invalidConfig(check, "blank boss name is rejected", c -> c.bossAreas.get(0).bossName = "");
        invalidConfig(check, "overlong area title is rejected", c -> c.dungeonLabels.get("fire_shrine").displayName = repeat("A", 81));
        invalidConfig(check, "formatted or multiline labels are rejected", c -> c.bossAreas.get(0).bossName = "Boss\u00a7k\nHidden");
        invalidConfig(check, "explicitly null metadata cannot disable validation", c -> c.bossAreas = null);
        invalidConfig(check, "unpaired UTF16 surrogate in a label is rejected", c -> c.bossAreas.get(0).bossName = "Broken\uD800");

        check.accept("dungeon display packet roundtrips all fields", roundTrip(dungeon).equals(dungeon));
        check.accept("boss display packet roundtrips all fields", roundTrip(boss).equals(boss));
        ByteBuf empty = encode(AreaState.EMPTY);
        try {
            check.accept("clear-area packet is compact and explicitly empty", empty.readableBytes() == 2 && decode(empty).isEmpty());
        } finally { empty.release(); }
        AreaState maximum = new AreaState(repeat("k", 128), "LIGHTNING", repeat("\u754C", 80), repeat("\uD83D\uDC09", 40));
        check.accept("maximum allowed Unicode labels survive UTF8 without truncation", roundTrip(maximum).equals(maximum));

        malformed(check, "unsupported area protocol is rejected", Unpooled.buffer().writeByte(2).writeByte(0));
        malformed(check, "invalid area-presence flag is rejected", Unpooled.buffer().writeByte(1).writeByte(2));
        malformed(check, "truncated area header is rejected", Unpooled.buffer().writeByte(1));
        malformed(check, "truncated string length is rejected", Unpooled.buffer().writeByte(1).writeByte(1).writeByte(0));
        malformed(check, "declared string beyond packet bytes is rejected", Unpooled.buffer().writeByte(1).writeByte(1).writeShort(8).writeByte('a'));
        malformed(check, "huge unsigned string length is rejected before allocation", Unpooled.buffer().writeByte(1).writeByte(1).writeShort(65535));
        malformed(check, "ASCII key exceeding character limit is rejected", raw(repeat("k", 129), "FIRE", "Shrine", ""));
        malformed(check, "unknown wire element is rejected", raw("arena", "LAVA", "Shrine", ""));
        malformed(check, "active wire area cannot omit its title", raw("arena", "FIRE", "", ""));
        malformed(check, "wire control characters cannot reach HUD labels", raw("arena", "FIRE", "Shrine\nOverlay", ""));
        ByteBuf badUtf8 = Unpooled.buffer().writeByte(1).writeByte(1).writeShort(2).writeByte(0xC3).writeByte(0x28);
        write(badUtf8, "FIRE"); write(badUtf8, "Shrine"); write(badUtf8, "");
        malformed(check, "invalid UTF8 is rejected rather than silently replaced", badUtf8);
        ByteBuf trailing = encode(boss); trailing.writeByte(0);
        malformed(check, "trailing packet data is rejected", trailing);
        ByteBuf trailingEmpty = encode(AreaState.EMPTY); trailingEmpty.writeByte(1);
        malformed(check, "clear packet cannot smuggle a trailing payload", trailingEmpty);
    }

    private static GuardConfig fixture() {
        GuardConfig c = new GuardConfig();
        c.regions.add(new GuardRegion("fire_shrine", 0, 100, 64, 100, 120, 80, 120));
        c.regions.add(new GuardRegion("water_annex", 1, 100, 64, 100, 120, 80, 120));
        GuardConfig.DungeonLabel label = new GuardConfig.DungeonLabel();
        label.element = "FIRE"; label.displayName = "The Ember Shrine";
        c.dungeonLabels.put("fire_shrine", label);
        c.bossAreas.add(zone("inner_sanctum", "Inner Sanctum", "Ember Guardian", 104, 65, 104, 108, 72, 108));
        c.bossAreas.add(zone("upper_gate", "Upper Gate", "Gate Sentinel", 114, 65, 114, 118, 72, 118));
        return c;
    }
    private static GuardConfig.BossZone zone(String id, String areaName, String bossName, int x1, int y1, int z1, int x2, int y2, int z2) {
        GuardConfig.BossZone z = new GuardConfig.BossZone();
        z.id = id; z.dungeon = "fire_shrine"; z.areaName = areaName; z.bossName = bossName;
        z.bounds = new GuardRegion(id, 0, x1, y1, z1, x2, y2, z2); return z;
    }
    private static void invalidConfig(BiConsumer<String, Boolean> check, String name, java.util.function.Consumer<GuardConfig> mutation) {
        GuardConfig c = fixture(); mutation.accept(c);
        try { c.validate(); check.accept(name, false); }
        catch (IllegalArgumentException expected) { check.accept(name, true); }
    }
    private static String repeat(String s, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(s);
        return out.toString();
    }
    private static ByteBuf encode(AreaState state) {
        ByteBuf out = Unpooled.buffer(); new AreaMessage(state).toBytes(out); return out;
    }
    private static AreaState decode(ByteBuf in) {
        AreaMessage message = new AreaMessage(); message.fromBytes(in); return message.area;
    }
    private static AreaState roundTrip(AreaState state) {
        ByteBuf bytes = encode(state);
        try { return decode(bytes); } finally { bytes.release(); }
    }
    private static ByteBuf raw(String key, String element, String title, String boss) {
        ByteBuf out = Unpooled.buffer().writeByte(1).writeByte(1);
        write(out, key); write(out, element); write(out, title); write(out, boss); return out;
    }
    private static void write(ByteBuf out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeShort(bytes.length); out.writeBytes(bytes);
    }
    private static void malformed(BiConsumer<String, Boolean> check, String name, ByteBuf bytes) {
        try {
            try { decode(bytes); check.accept(name, false); }
            catch (DecoderException expected) { check.accept(name, true); }
        } finally { bytes.release(); }
    }
}
