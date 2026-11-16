package village.dungeonguard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Settings belong to the current save, never shared across unrelated worlds. */
public final class GuardConfig {
    public int schema = 1;
    public boolean enabled = true;
    public boolean lockOutsideDungeons = true;
    public boolean allowStateChangesInProtectedWorld = true;
    public boolean allowWorldGenerationEdits = false;
    public boolean allowPlayerMiningInDungeons = false;
    public boolean allowPlayerBuildingInDungeons = false;
    public boolean suppressDungeonBlockDrops = true;
    public boolean protectDungeonTileEntities = true;
    public int resetDelayTicks = 100;
    public int blocksPerTick = 4096;
    public int maxSnapshotBlocks = 2000000;
    public List<GuardRegion> regions = new ArrayList<GuardRegion>();
    public Map<String, DungeonLabel> dungeonLabels = new LinkedHashMap<String, DungeonLabel>();
    public List<BossZone> bossAreas = new ArrayList<BossZone>();
    public Spawn spawn = new Spawn();

    public static final class DungeonLabel {
        public String element = "GENERAL";
        public String displayName = "Dungeon";
    }
    public static final class BossZone {
        public String id;
        public String dungeon;
        public String areaName = "Boss Area";
        public String bossName;
        public GuardRegion bounds;
    }

    public static final class Spawn {
        public boolean bound;
        public int dimension, x, y, z;
        public String blockId = "waystones:waystone";
        public float yaw;
        public double landingX, landingY, landingZ;
    }
    public void validate() {
        if (schema != 1) throw new IllegalArgumentException("Unsupported Dungeon Guard configuration schema.");
        if (resetDelayTicks < 0 || resetDelayTicks > 12000 || blocksPerTick < 1 || blocksPerTick > 65536
                || maxSnapshotBlocks < 1 || maxSnapshotBlocks > 16000000)
            throw new IllegalArgumentException("Invalid reset delay, tick budget, or maximum snapshot size.");
        if (regions == null || spawn == null || dungeonLabels == null || bossAreas == null)
            throw new IllegalArgumentException("Missing dungeon, spawn, or area-label settings.");
        Set<String> names = new HashSet<String>();
        Map<String, GuardRegion> parents = new LinkedHashMap<String, GuardRegion>();
        for (int i = 0; i < regions.size(); i++) {
            GuardRegion a = regions.get(i);
            if (a == null) throw new IllegalArgumentException("Null dungeon region.");
            a.validate();
            if (!names.add(a.name) || a.volume() > maxSnapshotBlocks) throw new IllegalArgumentException("Duplicate or oversized dungeon: " + a.name);
            parents.put(a.name, a);
            for (int j = 0; j < i; j++) if (a.overlaps(regions.get(j))) throw new IllegalArgumentException("Dungeon bounds overlap: " + a.name);
        }
        for (Map.Entry<String, DungeonLabel> entry : dungeonLabels.entrySet()) {
            DungeonLabel label = entry.getValue();
            if (!names.contains(entry.getKey()) || label == null) throw new IllegalArgumentException("Label has no registered dungeon: " + entry.getKey());
            AreaState.requireElement(label.element);
            AreaState.requireText(label.displayName, 80, false, "dungeon display name");
        }
        if (bossAreas.size() > 256) throw new IllegalArgumentException("At most 256 boss areas are supported per world.");
        Set<String> zoneIds = new HashSet<String>();
        for (int i = 0; i < bossAreas.size(); i++) {
            BossZone zone = bossAreas.get(i);
            if (zone == null || zone.id == null || !zone.id.matches("[a-z0-9][a-z0-9_-]{0,47}") || !zoneIds.add(zone.id))
                throw new IllegalArgumentException("Boss area IDs must be unique lowercase names of 1-48 letters, digits, underscores or hyphens.");
            GuardRegion parent = parents.get(zone.dungeon);
            if (parent == null || zone.bounds == null) throw new IllegalArgumentException("Boss area needs a registered dungeon and bounds: " + zone.id);
            zone.bounds.validate();
            if (zone.bounds.dimension != parent.dimension
                    || !parent.contains(zone.bounds.minX, zone.bounds.minY, zone.bounds.minZ)
                    || !parent.contains(zone.bounds.maxX, zone.bounds.maxY, zone.bounds.maxZ))
                throw new IllegalArgumentException("Boss area must be entirely inside its dungeon: " + zone.id);
            AreaState.requireText(zone.areaName, 80, false, "boss area name");
            AreaState.requireText(zone.bossName, 80, false, "boss name");
            for (int j = 0; j < i; j++) if (zone.bounds.overlaps(bossAreas.get(j).bounds))
                throw new IllegalArgumentException("Boss areas overlap: " + zone.id);
        }
        if (spawn.bound && (spawn.dimension != 0 || !"waystones:waystone".equals(spawn.blockId)
                || !Double.isFinite(spawn.landingX) || !Double.isFinite(spawn.landingY) || !Double.isFinite(spawn.landingZ)
                || spawn.y < 0 || spawn.y > 255)) throw new IllegalArgumentException("Invalid spawn Waystone binding.");
    }
}
