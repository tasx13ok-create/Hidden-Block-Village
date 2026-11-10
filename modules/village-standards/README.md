# Village Standards 0.3.0

This module now builds a complete, newly authored Minecraft 1.12.2 Forge mod.
It provides five placeable, persistent village pictures with their own hanging
entity and client renderer. Install `Village-Standards-0.3.0.jar` on the client
and server. The `-dev` and `-sources` JARs are development artifacts.

The original village art is reused without changing its bytes. The six excluded
reference Java files listed in `SOURCE-PROVENANCE.json` were not used as source
for this implementation. This release is a new implementation, not a rebuild
or behavioral reproduction of the earlier binary.

## Using the pictures

Each picture covers exactly five blocks across and five blocks high, with a
one-sixteenth-block depth. Right-click the center block of a solid 5 by 5 wall
while holding the picture. It faces the clicked side. Floor and ceiling
placement are rejected. All 25 supporting faces must be solid, the display
space must be clear, and the picture cannot overlap another hanging entity.
The entire backing and display area must be loaded and editable by the player.

| Item identifier | Display name | Picture texture |
| --- | --- | --- |
| `hvdungeonpictures:stone_picture` | Ridgecrest Standard | `pictures/stone.png` |
| `hvdungeonpictures:sand_picture` | Dunehaven Standard | `pictures/sand.png` |
| `hvdungeonpictures:mist_picture` | Tidewatch Standard | `pictures/mist.png` |
| `hvdungeonpictures:leaf_picture` | Canopy Hold Standard | `pictures/leaf.png` |
| `hvdungeonpictures:cloud_picture` | Skylark Rise Standard | `pictures/cloud.png` |

The five known item identifiers and their resources are preserved. They are
available in the Decorations creative tab and remain compatible with Core's
standard reward item references. Survival placement consumes one item only
after the server accepts its entity spawn. Creative placement does not consume
an item. Breaking a picture in survival returns one matching item; a creative
break does not create a drop. The `doEntityDrops` game rule is respected.
Removing any required supporting block causes the native hanging-entity check
to break the picture within 101 ticks. Custom item tags, names, and `valeSerial`
reward metadata survive normal placement, save/reload, and breaking.

The renderer is isolated behind the client proxy; dedicated servers do not
load rendering classes. It maps the complete original 256 by 256 picture onto
an original 5 by 5 panel mesh. The panel has a solid back and edges, correct
horizontal-facing transforms, and light samples for each of its 25 blocks.

## Saved data and earlier worlds

The new entity is registered as `hvdungeonpictures:dungeon_picture`, matching
the entity language key in the recovered resources. The old binary's actual
entity registration and custom variant NBT schema have not been verified.
The language key alone does not prove that an old world uses this entity ID.
This release therefore does **not** claim automatic compatibility with every
previously placed picture or permission to overwrite an existing server save.

New pictures store an explicit schema version and a string village key. They
use the native hanging-entity `TileX`, `TileY`, `TileZ`, and `Facing` fields for
wall position. Unknown fields are copied without interpretation. Current
native position fields take precedence over historical coordinates when an
item is placed at a new location. Saved item history is flattened so repeated
placement and breaking do not create nested, ever-growing copies.

When a record reaches this new entity but has no recognized new village key,
it becomes an **unassigned** picture. It is visible as a neutral panel with a
cross, can be targeted and broken, and never silently becomes another village.
Its drop is `hvdungeonpictures:unassigned_standard`, with the opaque saved
record attached. That recovery item can be placed on a valid wall again. An
operator can then explicitly select the intended village.

Before an unassigned picture is broken, its first-seen saved record is also
stored in the overworld's `data/vale_standards_recovery.dat`. This archive is
written even when creative mode or `doEntityDrops=false` suppresses the item
drop. It keeps one deep copy per stable origin UUID, retains the first copy,
and does not add entries for repeated place/break cycles of the same origin.
Distinct original records are retained without silently discarding older data.
An entry is not deleted when an operator creates a recovery copy.

Operator commands (permission level 2):

- `/standards inspect` lists up to 20 loaded unassigned entity UUIDs and counts
  assigned/unassigned loaded pictures. It does not scan unloaded chunks.
- `/standards resolve <entity-uuid> <stone|sand|mist|leaf|cloud>` assigns a loaded
  unassigned picture while preserving its unknown metadata.
- `/standards archive [page]` lists 20 saved recovery origins per page.
- `/standards recover <archive-uuid>` gives the operator one recovery copy into
  an empty inventory slot. This is an explicit administrative copy, like an
  item grant; it does not search for or remove other copies.

Use a copy of an actual old world to establish its entity ID, old custom NBT,
orientation, and inventory behavior before replacing the historical mod.
An old entity with a different ID cannot enter this recovery path merely by
installing the new JAR. No guessed entity aliases or guessed numeric variant
mappings are included.

## Building and checks

The module uses the repository's pinned Gradle/RFG setup, Java 8, Minecraft
1.12.2, and stable MCP mapping 39. Build through the repository orchestrator,
or run the Gradle launcher with this module as its project directory:

```
./gradlew -p modules/village-standards check reobfJar sourcesJar
```

The shared `prebuiltMinecraft` property can point to an API JAR prepared by the
Core module. `check` verifies the exact recovered-resource inventory and SHA-256
digests, plus geometric support coverage, string variant identity, compressed
NBT persistence, opaque field preservation, bounded item history, and archive
deduplication. These component checks do not prove GPU rendering or a full
historical-world migration.

The separate `worldchecks` source set produces a private helper via
`reobfStandardsNativeChecksJar`. Its entry point is
`village.standards.StandardsNativeChecks.run(EntityPlayerMP)`, returning the
number of passing assertions. It runs only with
`-Dvalestandards.nativecheck=true`, server save `vale-recovery-test`, a
`.vale-recovery-test` marker in the working directory, and no other connected
players. The fixture places all five variants in all four wall directions,
uses real entity/item APIs, checks cancellation/permissions/support loss,
survival/creative drops, native NBT and spawn-data round trips, and unassigned
recovery cycles. It restores its blocks and removes its fixture entities.
It is excluded from production artifacts and release installation instructions.

A native server pass establishes server-side placement/persistence behavior;
the renderer still requires an actual Minecraft client visual check. The
private fixture deliberately does not run against a player's existing world.

## Provenance

`SOURCE-PROVENANCE.json`, `README.txt`, `LICENSE.txt`, and `resources/` preserve
the recovered historical record. In particular, the old resource-only build
scope and binary hash in `README.txt` describe 0.2, not this new runtime.
`RUNTIME-CONTRACT.json` describes the current implementation and compatibility
boundary. Newly authored source and checks are under `src/main/java`, `tests`,
and `worldchecks`. No Minecraft, Forge, excluded Java, or other third-party
binary is embedded in this module's runtime JAR.
