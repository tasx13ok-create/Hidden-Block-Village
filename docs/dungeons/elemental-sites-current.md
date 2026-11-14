# Existing five elemental dungeons

The owner wants the first five dungeons to contain elemental enemies that players can kill for loot. The separately commissioned five new coliseums are for boss fights. These are different sets: the original builds have v9 and v17 revisions; v17 is the latest recovered version of those same five builds, not the new boss arenas. The old boss proposals in the dungeon atlas do not override this direction.

## Identified builds

| Existing v17 build | Earlier owner description | Confirmed gameplay nature | Size |
| --- | --- | --- | --- |
| Sakura | Earth, in the trees | Earth | 288×217×288 |
| Cloud | Air, a floating island | Lightning | 288×209×288 |
| Sand | Sand, in the desert | Wind | 288×153×288 |
| Mist | Water, an island | Water | 200×148×200 |
| Nether | Fire, a floating island in the Nether | Fire | 256×126×256 |

On 14 November 2026 the owner confirmed this mapping and said their friend will handle the NPC drop choices. The accessible prior chats did not specify NPC types, items or drop rates. The owner's later instruction supersedes the atlas's original boss plans.

The five intact schematics are available privately under `local/v17-intake/`. A separate frozen preview at `D:/HiddenBlockVillage-Sprint-20261113/v17-world-candidate-01/world` contains all five; its placements and checks are documented in [the candidate record](v17-candidate-current.md). All preview sites, including the Nether-named template, are in the Overworld. They are not proof of final production placement or of a real Nether dungeon. Keep the frozen world and source schematics unchanged; work in a new copy. The Mist footprint remains 200×200 with real water, and Sakura, Cloud and Sand retain their fractured bases.

## First elemental build pass

Five separate copies with small colored elemental floor motifs at the entrances and encounter areas were generated under ignored `local/elemental-v17-floor-markers-encounters-20261114/`. Sakura has Earth, Cloud Lightning, Sand Wind, Mist Water and Nether Fire. These are identification details, not full dungeon conversions. The original v17 hashes matched their intake records and were unchanged. Hash-pinned plans, readback logs and copied schematics are in that directory; the original generator and its verification boundaries are in [the marker tool](../../tools/elemental_dungeons/MARKERS.md). Each modified floor cell was checked for its expected block, two air cells and absence of tile entities; readback compared all blocks, metadata, tiles and other schematic tags. The Nether marker avoids its nearby carpet and glowstone. An earlier entrance-only candidate remains preserved in `local/elemental-v17-floor-markers-20261114/`.

No copy has been installed in a world, walked or visually checked in Minecraft. Lighting, room quality, further elemental detail and encounter integration remain open. The owner previously requested Minecraft remain closed, so graphical acceptance is pending. The small markers do not fix the previously recorded inaccessible rooms.

## Enemies and loot: current boundary

The v17 schematics contain no NPC entities. Existing Vale encounter mobs neither naturally populate these sites nor drop ordinary loot. The recovered CustomNPCs clone library includes a Fire Ninja whose drop inventory is empty; no roster or working elemental drop configuration is established by that name. Some NarutoMod scroll IDs are present in the saved runtime registry, but a registry name alone does not establish a usable item, balanced drop, or existing NPC binding. The accessible chats did not supply a loot table or rates. Treat any suggested scrolls as a draft until checked with the actual mod and the owner.

The measured gate draft supplies initial encounter positions, with block-grid floor and air checks, in [the measured-sites record](measured-gates-current.md). These positions still require native collision, navigation, combat, reset, loot, restart and multiplayer tests. Do not overwrite CustomNPCs global data or invent UUIDs. Create and test enemies and drop settings in a separate copied runtime, retaining the original mod assets and any existing NPC data. Preserve the new boss-coliseum work and keep the Minecraft client closed per the prior owner instruction.

Next: inspect and extend the five separate marker candidates into complete elemental dungeon builds, coordinate NPC and drop work with the owner's friend, and exercise any later implementation through native kill/drop/reload/replay checks before describing the sites as playable. No elemental NPC population, item drop or native dungeon walkthrough is claimed by this record. Five provisional NPC clone experiments were created privately before the owner clarified the friend's role; they were not installed and are not assigned as the friend's drop choices.
