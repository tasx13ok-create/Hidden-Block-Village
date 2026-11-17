package village.dungeonguard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import village.dungeonguard.AreaState;
import village.dungeonguard.GuardRegion;
import village.dungeonguard.WaystoneBounds;

public final class GuardConfig {
    public int schema = 3;
    public boolean enabled = true;
    public boolean lockOutsideDungeons = true;
    public boolean allowStateChangesInProtectedWorld = true;
    public boolean allowWorldGenerationEdits = false;
    public boolean allowPlayerMiningInDungeons = false;
    public boolean allowPlayerBuildingInDungeons = false;
    public boolean allowAbilityTerrainDamageInDungeons = true;
    public boolean preserveNinjaXpOnDeath = true;
    public boolean preventSusanooSelfDamage = true;
    public boolean enforceKgXpRequirement = true;
    public double ninjaXpGainMultiplier = -1.0;
    public boolean suppressDungeonBlockDrops = true;
    public boolean protectDungeonTileEntities = true;
    public int resetDelayTicks = 100;
    public int blocksPerTick = 16384;
    public int maxSnapshotBlocks = 30000000;
    public List<GuardRegion> regions = new ArrayList<GuardRegion>();
    public Map<String, DungeonLabel> dungeonLabels = new LinkedHashMap<String, DungeonLabel>();
    public Map<String, WaystoneZone> dungeonWaystones = new LinkedHashMap<String, WaystoneZone>();
    public List<BossZone> bossAreas = new ArrayList<BossZone>();
    public Spawn spawn = new Spawn();

    public void validate() {
        if (this.schema != 3) {
            throw new IllegalArgumentException("Unsupported Dungeon Guard configuration schema.");
        }
        if (this.resetDelayTicks < 0 || this.resetDelayTicks > 12000 || this.blocksPerTick < 1 || this.blocksPerTick > 65536 || this.maxSnapshotBlocks < 1 || this.maxSnapshotBlocks > 64000000) {
            throw new IllegalArgumentException("Invalid reset delay, tick budget, or maximum snapshot size.");
        }
        if (this.regions == null || this.spawn == null || this.dungeonLabels == null || this.bossAreas == null || this.dungeonWaystones == null) {
            throw new IllegalArgumentException("Missing dungeon, spawn, or area-label settings.");
        }
        if (!Double.isFinite(this.ninjaXpGainMultiplier) || this.ninjaXpGainMultiplier != -1.0 && (this.ninjaXpGainMultiplier < 0.0 || this.ninjaXpGainMultiplier > 2000.0)) {
            throw new IllegalArgumentException("Ninja XP multiplier must be -1 (native config) or 0 to 2000.");
        }
        HashSet<String> names = new HashSet<String>();
        LinkedHashMap<String, GuardRegion> parents = new LinkedHashMap<String, GuardRegion>();
        for (int i = 0; i < this.regions.size(); ++i) {
            GuardRegion guardRegion = this.regions.get(i);
            if (guardRegion == null) {
                throw new IllegalArgumentException("Null dungeon region.");
            }
            guardRegion.validate();
            if (!names.add(guardRegion.name) || guardRegion.volume() > (long)this.maxSnapshotBlocks) {
                throw new IllegalArgumentException("Duplicate or oversized dungeon: " + guardRegion.name);
            }
            parents.put(guardRegion.name, guardRegion);
            for (int j = 0; j < i; ++j) {
                if (!guardRegion.overlaps(this.regions.get(j))) continue;
                throw new IllegalArgumentException("Dungeon bounds overlap: " + guardRegion.name);
            }
        }
        for (Map.Entry<String, DungeonLabel> entry : this.dungeonLabels.entrySet()) {
            DungeonLabel label = entry.getValue();
            if (!names.contains(entry.getKey()) || label == null) {
                throw new IllegalArgumentException("Label has no registered dungeon: " + entry.getKey());
            }
            AreaState.requireElement(label.element);
            AreaState.requireText(label.displayName, 80, false, "dungeon display name");
        }
        for (Map.Entry<String, WaystoneZone> entry : this.dungeonWaystones.entrySet()) {
            GuardRegion region = (GuardRegion)parents.get(entry.getKey());
            WaystoneZone stone = (WaystoneZone)entry.getValue();
            if (region == null || stone == null || stone.y < 1 || stone.y > 254 || stone.dimension != region.dimension || !region.contains(stone.x, stone.y, stone.z) || !region.contains(stone.x, stone.y + 1, stone.z)) {
                throw new IllegalArgumentException("Dungeon Waystone needs valid bounds: " + entry.getKey());
            }
            GuardRegion expected = WaystoneBounds.around(region.name, stone.dimension, stone.x, stone.y, stone.z);
            if (region.minX != expected.minX || region.maxX != expected.maxX || region.minY != expected.minY || region.maxY != expected.maxY || region.minZ != expected.minZ || region.maxZ != expected.maxZ) {
                throw new IllegalArgumentException("Waystone zones must be exactly 300 x 250 x 300 around their stone.");
            }
            if (!this.spawn.bound || this.spawn.dimension != region.dimension || !region.contains(this.spawn.x, this.spawn.y, this.spawn.z) && !region.contains((int)Math.floor(this.spawn.landingX), (int)Math.floor(this.spawn.landingY), (int)Math.floor(this.spawn.landingZ))) continue;
            throw new IllegalArgumentException("Keep the spawn Waystone outside dungeon zones.");
        }
        if (this.bossAreas.size() > 256) {
            throw new IllegalArgumentException("At most 256 boss areas are supported per world.");
        }
        HashSet<String> zoneIds = new HashSet<String>();
        for (int n = 0; n < this.bossAreas.size(); ++n) {
            BossZone zone = this.bossAreas.get(n);
            if (zone == null || zone.id == null || !zone.id.matches("[a-z0-9][a-z0-9_-]{0,47}") || !zoneIds.add(zone.id)) {
                throw new IllegalArgumentException("Boss area IDs must be unique lowercase names of 1-48 letters, digits, underscores or hyphens.");
            }
            GuardRegion parent = (GuardRegion)parents.get(zone.dungeon);
            if (parent == null || zone.bounds == null) {
                throw new IllegalArgumentException("Boss area needs a registered dungeon and bounds: " + zone.id);
            }
            zone.bounds.validate();
            if (zone.bounds.dimension != parent.dimension || !parent.contains(zone.bounds.minX, zone.bounds.minY, zone.bounds.minZ) || !parent.contains(zone.bounds.maxX, zone.bounds.maxY, zone.bounds.maxZ)) {
                throw new IllegalArgumentException("Boss area must be entirely inside its dungeon: " + zone.id);
            }
            AreaState.requireText(zone.areaName, 80, false, "boss area name");
            AreaState.requireText(zone.bossName, 80, false, "boss name");
            for (int j = 0; j < n; ++j) {
                if (!zone.bounds.overlaps(this.bossAreas.get((int)j).bounds)) continue;
                throw new IllegalArgumentException("Boss areas overlap: " + zone.id);
            }
        }
        if (!(!this.spawn.bound || this.spawn.dimension == 0 && "waystones:waystone".equals(this.spawn.blockId) && Double.isFinite(this.spawn.landingX) && Double.isFinite(this.spawn.landingY) && Double.isFinite(this.spawn.landingZ) && this.spawn.y >= 0 && this.spawn.y <= 255)) {
            throw new IllegalArgumentException("Invalid spawn Waystone binding.");
        }
    }

    public static final class Spawn {
        public boolean bound;
        public int dimension;
        public int x;
        public int y;
        public int z;
        public String blockId = "waystones:waystone";
        public float yaw;
        public double landingX;
        public double landingY;
        public double landingZ;
    }

    public static final class WaystoneZone {
        public int dimension;
        public int x;
        public int y;
        public int z;
    }

    public static final class BossZone {
        public String id;
        public String dungeon;
        public String areaName = "Boss Area";
        public String bossName;
        public GuardRegion bounds;
    }

    public static final class DungeonLabel {
        public String element = "GENERAL";
        public String displayName = "Dungeon";
    }
}

