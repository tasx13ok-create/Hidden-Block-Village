# Dungeon Guard settings

The mod creates `<world>/dungeon-guard/settings.json`. Use the in-game commands for spawn binding and dungeon definitions. Before manual edits, stop the server or use `/dg reload` for non-region settings; running region definitions cannot be swapped during a fight.

| Setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Protection hooks are enabled. Disabling this deliberately removes protection; reset jobs still exist. |
| `lockOutsideDungeons` | `true` | Blocks changes outside occupied, registered dungeons. |
| `allowStateChangesInProtectedWorld` | `true` | Allows properties of the same block type to change, such as a door opening. Block replacement/removal remains blocked. Capture/reset/failed areas stay fully locked. |
| `allowWorldGenerationEdits` | `false` | Optional exception for generator call stacks outside arenas. Leave false for strict outside-map preservation: a generator can reach into existing neighboring chunks. |
| `allowPlayerMiningInDungeons` | `false` | Whether ordinary players may mine inside active arenas; ability destruction is separate. |
| `allowPlayerBuildingInDungeons` | `false` | Whether ordinary players may place blocks inside active arenas. Player-built additions are removed by the next baseline restore. |
| `suppressDungeonBlockDrops` | `true` | Prevents normal terrain block drops and known AHZNB manual drops from making arena reset a resource farm. Mob/player drops are separate. |
| `protectDungeonTileEntities` | `true` | Prevents replacing tile-entity blocks in active arenas, including chests and signs. Saved NBT is restored regardless. |
| `resetDelayTicks` | `100` | Grace period after the last occupant leaves, in game ticks; 100 is five seconds at 20 TPS. Allowed 0–12000. |
| `blocksPerTick` | `4096` | Shared maximum block work across all captures/resets each server tick. The engine also yields cooperatively after about 8 ms of work. Allowed 1–65536. One Minecraft operation cannot be preempted halfway. |
| `maxSnapshotBlocks` | `2000000` | Maximum inclusive selected volume per dungeon. AIR counts. Allowed 1–16000000; raising it increases memory and disk use. |
| `regions` | `[]` | Persisted only after a new capture has been saved and verified. Each entry contains a unique name, dimension and inclusive min/max coordinates. |
| `dungeonLabels` | `{}` | Map from registered dungeon ID to `element` and `displayName`; `/dg label` manages it. |
| `bossAreas` | `[]` | Up to 256 disjoint boss zones, each contained in its parent dungeon; `/dg bossarea` and `/dg bosslabel` manage them. |
| `spawn` | unbound | Populated by `/dg bindspawn`; contains the actual Waystone base and the validated adjacent landing. |

For a selected cuboid, volume is `(maxX - minX + 1) × (maxY - minY + 1) × (maxZ - minZ + 1)`. A 100 × 100 × 150 selection contains 1,500,000 blocks and fits the default cap. Selection size affects capture/reset duration, so include the whole dungeon and necessary combat air space without selecting an entire map.

## Arena states

| State | Behavior |
| --- | --- |
| `CAPTURING` / `SAVING` | Read and persist a new baseline. Entry and edits locked. |
| `IDLE` | Clean empty arena. Terrain locked until someone enters. |
| `ACTIVE` | One or more occupants. Ability block changes allowed inside bounds. |
| `WAITING` | Empty; reset delay running. Return before it expires to continue the fight. |
| `VALIDATING` | Re-read and check the baseline before restoring. Entry locked. |
| `RESTORING_BLOCKS` / `VERIFYING` / `RESTORING_TILES` | Bounded restoration and verification. Entry locked; unexpected occupants pause work. |
| `FAILED` | Missing/corrupt data or a restore failure. Entry and terrain remain locked pending repair. |

## Area labels and client settings

Label fields are part of schema 1, so 1.0.0 settings remain readable. Existing captures are unchanged. Label strings are limited to 80 characters and may not contain control characters, Minecraft formatting codes, or outer whitespace. Elements are uppercase: GENERAL, FIRE, WATER, WIND, EARTH, LIGHTNING, SAND or ICE.

A dungeon label is stored as, for example:

```json
"dungeonLabels": {
  "fire": {"element": "FIRE", "displayName": "Fire Dungeon"}
}
```

A boss area has `id`, `dungeon`, `areaName`, `bossName`, and `bounds`. The bounds use the same inclusive dimension/min/max shape as a region but do not create another snapshot. The dungeon reset also restores its boss chambers. Removing a dungeon removes its area labels; baseline/recovery files are retained.

Only registered dungeon names may be used. Missing parents, out-of-bounds zones, duplicate IDs and overlapping boss zones are rejected before configuration is saved. `/dg reload` can reload labels while the existing region definitions stay fixed.

`config/dungeonguard-client.cfg` is per client. Its `display` category setting `showAreaBanner=true` is managed by `/dghud [on|off]`. It controls artwork only; a client cannot change server permissions or dungeon state.

Operator exceptions are deliberately actor-scoped and are not a configurable global radius. Keep `enabled=true` and `lockOutsideDungeons=true` for the requested protected map. Leave `allowWorldGenerationEdits=false` for strict preservation.
