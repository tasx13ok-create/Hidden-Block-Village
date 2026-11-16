# Dungeon Guard 1.2.0

Minecraft Java **1.12.2**, Forge, Java 8. Intended for the existing Naruto/Bisect/Magma server. The production JAR belongs in **mods**, not **plugins**. Install it on the server for protection and resets. Install the same JAR on each player's client for the transparent dungeon/boss HUD. Clients without it can still connect and receive plain Minecraft entry titles. It registers no new items or blocks; the selection wand uses a marked vanilla stick.

**Start here:** [Quick start](QUICK-START.md). In game, run **`/dungeon`** for the clickable menu or **`/dungeon setup`** for the next setup step. Existing `/dg` commands still work, and upgrades keep your saved dungeons and labels.

## What it does

- Locks block destruction and placement outside registered, occupied dungeons. Verified online operators can mine and build through their own direct actions; non-OP creative players, fake players and delayed ability effects receive no operator exception.
- Shows a transparent cute fox-shinobi pixel-art scroll with the dungeon element/name, or the current boss area and configured boss name. Labels follow walking, teleports and dimension changes.
- Allows ability destruction and temporary terrain inside an occupied dungeon.
- Saves a clean copy of each dungeon's complete block volume, including AIR, block-state properties, signs, and serialized tile-entity data such as chest contents.
- Watches player positions and dimensions. Walking out, teleporting to spawn, teleporting to another dungeon, and disconnecting all remove that player from the old dungeon.
- Waits until the **last player leaves**, then starts a reset after a default five-second grace period. Another player still inside keeps the fight active. Spectators and bodies overlapping the boundary count as occupants.
- Cancels a pending reset if someone returns during the grace period. Once rebuilding starts, entry is locked and players entering are sent to the bound spawn Waystone. If evacuation cannot succeed, restoration pauses while they remain inside.
- Restores in bounded batches, validates restored states and tile data, and keeps missing or damaged baselines locked.
- Records recovery before allowing combat damage. Previously used dungeons conservatively restore again after a server restart, so interrupted fights and resets recover from their original baseline.
- Binds the actual Overworld spawn to a safe landing beside an existing Waystone and sets `spawnRadius` to zero.

The clean copy is captured from your already-built dungeon. **Paste the original, undamaged schematic once before capturing.** No new `.schematic` format conversion is required. Do not capture a damaged dungeon and expect the mod to infer its original design.

## Install on BisectHosting

1. Stop the server and make a backup of the existing world.
2. Remove the old Dungeon Guard JAR if upgrading, then copy `Dungeon-Guard-1.12.2-1.2.0.jar` into the server's `mods/` directory. Keep the existing world's `dungeon-guard` folder and snapshots. Only the production JAR belongs in `mods/`.
3. Start with the existing **Java 8 / Minecraft 1.12.2 Forge or Magma** setup. Check the log for `Dungeon Guard started` and `core 11/11`.
4. For the illustrated HUD, add the same 1.2.0 JAR to each player's Minecraft 1.12.2 Forge client `mods/` folder and restart the client.
5. Join as an operator. Run `/dungeon setup`.

The world becomes protected immediately after the mod starts. Dungeon combat damage becomes available only after a valid baseline exists and players occupy that dungeon. Ordinary doors, buttons, redstone state changes, chest access, movement and combat are not globally disabled. Automatic placement/removal of other blocks outside arenas is blocked. This is an adventure-map policy; crops, fire, fluid spread, falling blocks and new terrain decoration can be affected when they would change protected blocks.

## Bind the spawn Waystone

Stand in the **Overworld**, look directly at your existing spawn Waystone and run:

```text
/dungeon setspawn
```

Or identify it explicitly:

```text
/dungeon bindspawn <x> <y> <z>
```

Either half of the Waystone works. The implementation recognizes the official 1.12 Waystones block ID `waystones:waystone` and verifies its two-block layout. Leave a solid, safe floor and two clear blocks beside it. The landing and Waystone must be outside every dungeon.

This changes the actual world-spawn location, synchronizes it periodically, and removes the random spawn offset. New players and normal bedless Overworld respawns use world spawn. Existing personal beds remain personal spawns. For one fixed server spawn, leave Waystones' own **Set Spawnpoint on Activation** option disabled; that option otherwise changes players' personal spawn to the stone they last activate.

Every player can use:

```text
/dungeon spawn
```

This command returns to the bound Waystone, including from another dimension, without creating Nether portals. An existing Bukkit `/spawn` command is not replaced. Any teleport that moves someone out of a dungeon still triggers the same occupancy/reset logic.

If the bound stone is missing or the landing becomes unsafe, the mod reports the binding unavailable. It does not silently choose a new location. Repair that stone/landing or explicitly bind another existing Waystone.

## Register each dungeon

First make sure the dungeon is in its clean condition. Get a selection wand:

```text
/dungeon wand
```

Hold that stick, **left-click the first corner block**, then **right-click the opposite corner block**. Neither click breaks the selected block. Select a box around the entire combat volume, including lower terrain, upper structures, and the air where attacks may construct temporary blocks. Both corner blocks are included. Then run:

```text
/dungeon create fire
```

That saves the original and sets the Fire Dungeon banner together. You can give it a custom title in the same command, for example `/dungeon create water Hidden Mist Dungeon`. The mod creates a safe internal ID and skips any name already used by a dungeon or retained capture file.

Keep the area empty until you receive **Ready**. `/dungeon list` shows progress in plain language. Saving runs in batches, verifies the saved copy, and activates the dungeon and its label together. If saving fails, entry stays locked and no incomplete dungeon or orphan label is activated. Entering a ready dungeon changes its display to **In use**; after everyone leaves and rebuilding finishes, it returns to **Ready**.

Supported elements are `fire`, `water`, `wind`, `earth`, `lightning`, `sand`, `ice`, and `general`. Repeat for each clean dungeon. Stand in the correct dimension when selecting, including for a Nether dungeon. Changing dimension or disconnecting clears an unfinished selection. The Overworld spawn binding provides the common exit.

For exact coordinates or a corner in empty air, `/dungeon pos1 [x y z]` and `/dungeon pos2 [x y z]` still work and share the wand's selection. Coordinates accept normal Minecraft `~` notation; without coordinates they use the block at your feet. The original `/dg capture <id>` remains available for a deliberately chosen internal ID and a separate label. Overlapping dungeons, invalid bounds and oversized captures are rejected.

There are no guessed coordinates or automatically selected builds in this package. The saved bounds are the authority for what may be destroyed and what will be restored.

## Everyday commands

| Command | Purpose |
| --- | --- |
| `/dungeon` | Open the clickable chat menu. |
| `/dungeon setup` | See the next setup step. |
| `/dungeon wand` | Get the corner-selection stick. |
| `/dungeon setspawn` | Bind the existing Overworld Waystone you are looking at. |
| `/dungeon create fire` | Save the selected clean dungeon and its Fire Dungeon label. |
| `/dungeon create water Hidden Mist Dungeon` | Save a dungeon with a custom display title. |
| `/dungeon boss Orochimaru` | Label the selected chamber inside its saved parent dungeon. |
| `/dungeon list` | Show dungeons, player counts and saving/reset progress. |
| `/dungeon spawn` | Return to the bound spawn Waystone. Available to every player. |
| `/dungeon reset <id>` | Manually restore an empty dungeon; normal exits reset automatically. |
| `/dghud [on\|off]` | Toggle the personal banner on a client with the addon installed. |

## Advanced and existing commands

Configuration commands require a verified online server operator. The server console can use applicable commands; selection/binding requires a player. `/dg spawn`, `/dg help`, and status are available to ordinary players. Vanilla editing commands still enforce their normal Minecraft permission levels.

| Command | Purpose |
| --- | --- |
| `/dg bindspawn [x y z]` | Bind an existing Overworld Waystone. |
| `/dg pos1 [x y z]` | Select the first inclusive dungeon corner. |
| `/dg pos2 [x y z]` | Select the opposite inclusive corner. |
| `/dg capture <name>` | Save a new clean baseline and register the dungeon after verification. |
| `/dg status` | Show protection, spawn binding, occupancy and capture/reset state. |
| `/dg reset <name>` | Restore an empty dungeon manually. Occupied dungeons are refused. |
| `/dg remove <name>` | Unregister an empty IDLE or FAILED dungeon. Preserve its baseline and recovery files. |
| `/dg label <dungeon> <element> <display name...>` | Set the dungeon element and display title. |
| `/dg bossarea <dungeon> <area-id> <boss name...>` | Use the current selection as a boss area inside a saved dungeon. |
| `/dg bosslabel <area-id> <area name...>` | Change that boss area's display title. |
| `/dg bossremove <area-id>` | Remove a boss label/zone without changing the dungeon baseline. |
| `/dghud [on\|off]` | Client-only personal banner toggle, available to every player with the addon. |
| `/dg reload` | Reload non-region settings; manual region-definition changes require a restart. |

### OP building and protection

Operators can mine, place normal building items/blocks, use buckets and use the native `/setblock`, `/fill` and `/clone` commands. Permission covers only the selected cells and only the duration of that checked action. Other players remain blocked even beside the operator. Changing someone to creative mode does not make them an operator. Projectile, explosion and delayed jutsu terrain damage stays contained even when the caster is an operator.

The former `/dg edit` global building window has been removed. It cannot accidentally open an area to everyone. Capture, rebuilding and FAILED regions remain locked for operators too. Native editing commands that intersect these locked regions are rejected before they can clear an inventory.

WorldEdit and arbitrary third-party construction commands do not automatically inherit this exception. Paste a large schematic before enabling protection or prepare it in a separate maintenance copy of the world. Direct OP building and the three supported vanilla editing commands can repair the map while protection stays active.

Do not use live WorldEdit edits as a protection test on a valuable save: the inspected WorldEdit versions can clear container data before a blocked terrain write, and some same-state NBT operations do not use that block-write boundary. WorldEdit needs a separate adapter. The source's `COMPATIBILITY-NOTES.md` records the inspected versions and exact remaining integration work.

To replace an existing baseline deliberately, empty and unregister that dungeon, repair the clean build, and capture it again. The easy `create` command chooses a new available ID; the advanced `capture` command requires you to choose a new name such as `fire_v2`. Old baseline files are retained and are never silently overwritten. Upgrades from 1.0.x and 1.1.x retain existing baselines; old worlds receive empty label settings where needed.

## Element and boss area banners

The supplied `art/dungeon-area-banner.png` has a real alpha channel and an invisible background. It contains a cute orange fox shinobi, a gold parchment scroll, red cord and kunai. Text is drawn in Minecraft's font by the mod so every area uses its own names. The PNG intentionally has no fixed lettering.

The easy `create` command assigns the element and title while saving the dungeon. To change a saved dungeon's label later, use its ID from `/dungeon list`:

```text
/dungeon label fire_dungeon FIRE Fire Dungeon
```

Supported elements: `FIRE`, `WATER`, `WIND`, `EARTH`, `LIGHTNING`, `SAND`, `ICE`, and `GENERAL`. The element changes the heading and text color. Without a label, the dungeon's ID becomes a readable title and its element stays GENERAL; the mod does not guess your map's element or boss.

For a boss chamber, use the wand to select its two opposite corners **inside a ready dungeon** and assign the actual boss name:

```text
/dungeon boss Orochimaru
```

The parent dungeon and a new boss-area ID are chosen automatically. To set a separate chamber title or control the ID yourself, the existing `bossarea` and `bosslabel` commands remain available.

For a standalone boss arena, capture it as its own dungeon region, then make its boss area cover that whole region. Use `GENERAL` when it has no elemental theme.

Replace angle-bracket fields with your coordinates and boss name; names can contain spaces without quotation marks. The selection must be in the same dimension and fully inside the saved dungeon. Boss areas cannot overlap. Repeating `bossarea` with the same ID updates its bounds and boss name; `bosslabel` changes its display title.

Inside that chamber, the banner shows three lines:

- `FIRE / BOSS AREA`
- `Inner Sanctuary`
- `Boss: <actual boss name>`

Outside the chamber but still in the dungeon it returns to `FIRE / DUNGEON` and `Fire Dungeon`. Outside every dungeon it fades away. The server checks positions every ten ticks and refreshes the client periodically. Long labels shrink to fit and are shortened with an ellipsis when necessary. Names can be up to 80 characters; concise names are easiest to read.

Every client with the addon can use `/dghud off`, `/dghud on`, or `/dghud` to toggle. The choice persists in `config/dungeonguard-client.cfg`. F1 hides the banner with the normal HUD. Clients without the addon receive short ordinary entry titles; they do not receive the custom texture or custom packets.

The banner reserves the boss health bars actually rendered in the current frame and the bottom combat-control area. It chooses a clear position and may reduce the artwork to a minimum readable width. On a crowded GUI it uses a compact two- or three-line text card instead. If neither fits, it hides that frame rather than covering combat controls. It also hides while menus, the debug screen, death or spectator view are active.

When Shinobi Immersion is installed, it also reserves the top 32 GUI pixels for that mod's compass strip and distance labels. This conservative reservation remains even if the compass is disabled; no private configuration fields are accessed.

An optional adapter reads public layout information from the current Field HUD source (0.20.1). It reserves the status panel, reported minimap, ability rail, armor overlay, scoreboard and contextual card where available. This is a source-level integration; live graphical coexistence remains a separate check. Older Field HUD versions with incompatible public signatures fall back to boss-bar and combat-control spacing. Transient Field HUD arrival, target and durability cards are not part of this adapter's public layout contract.

Labels are configured geographical information. They do not locate a moving boss, identify an arbitrary spawned entity, spawn or respawn a boss, or show its health. Keep boss spawning/progression in the existing Naruto/NPC/dungeon system. The addon does not invent boss assignments when the server roster is unknown.

Installing the HUD client addon does not automatically lock unrelated new singleplayer saves. Dedicated servers activate protection; an integrated server only activates it if that save already has `dungeon-guard/settings.json`.

## Default combat and loot policy

Ability terrain damage is allowed inside ACTIVE arenas. Ordinary player mining and building inside dungeons are disabled by default; they can be enabled separately in settings. Block drops from destructible arena terrain are suppressed so resetting the same walls does not become a resource farm. Mob loot and player death drops are not generally suppressed or erased. Falling-block debris originating inside an arena is configured not to drop a block item.

Tile-entity blocks such as chests and signs are protected from block destruction during fights by default. Their saved contents/data still restore with the baseline. **A chest captured with loot will have that loot again after reset.** Design dungeon rewards with this behavior in mind; this mod does not implement reward cooldowns or quest progression.

Explosion block lists are filtered by position. Entity damage and knockback remain active. Low-level World and Chunk write guards also cover ordinary direct block edits that bypass normal Forge block/explosion events.

AHZNB 0.3.1-specific compatibility guards address its manually spawned block drops and its four-block portal placement procedure. The portal procedure is refused before any placement if one of its four target blocks is protected; this avoids leaving a partially created portal or dereferencing a tile that the protection refused to create. A terrain-dependent technique may therefore be limited at a protected boundary.

Version 1.1.1 also checks the known Kamui block helper before it can create or transfer item drops. A protected block produces no drops and stays intact. Inside a writable arena with terrain loot suppressed, it removes the permitted block without creating or teleporting drops. The helper's return value counts transferred stacks, so that no-loot path correctly returns zero. This is a verified helper-path correction; it does not establish that every released ability calls that helper.

## Saved data and restart recovery

For your existing world, data is stored under:

```text
Naruto.test/dungeon-guard/settings.json
Naruto.test/dungeon-guard/snapshots/<name>.snapshot.nbt.gz
Naruto.test/dungeon-guard/snapshots/<name>.snapshot.nbt.gz.sha256
Naruto.test/dungeon-guard/snapshots/<name>.dirty
```

Other world names use their own world directory. Keep this entire folder with world backups and transfers. A missing file or checksum mismatch causes that dungeon to stay `FAILED` and locked. It does not overwrite the baseline with the current damaged world.

The `.dirty` recovery marker is intentionally retained after successful resets. Minecraft can save chunks asynchronously; keeping the marker makes the next startup reapply the clean baseline rather than assuming every restored chunk was already flushed. Do not routinely delete recovery markers. Restoring a known-good baseline/checksum pair and running `/dg reset <name>` provides explicit recovery from a file failure.

## Exact scope and compatibility

The snapshot restores blocks, their properties, and serialized tile-entity NBT. It does **not** recreate bosses, NPCs, paintings/entities, scheduled ticks, biome data, or another mod's quest/capability/world-saved state. Existing entities are not indiscriminately deleted. Ticking machines can resume changing their own inventories and timers; the snapshot is a dungeon building baseline, not a frozen copy of an entire Minecraft world.

The protection covers the instrumented normal `World`/`Chunk` mutation and drop paths. Code that directly rewrites raw chunk storage arrays or independently edits tile NBT is outside these hooks. A conflicting coremod can also require a compatibility adjustment. Mandatory hooks are checked at startup; missing or unsupported mandatory hooks stop startup instead of reporting false protection.

The Magma compatibility adapter also covers the two verified Bukkit-to-vanilla command dispatch sites, reusing the same OP checks and locked-region preflight. A present Bukkit wrapper must pass through the verified transformer chain or startup stops. The inspected wrapper grants edit scopes to connected OP players and the native local console; command blocks, proxy/function wrappers and RCON senders do not gain that exception from command permission alone. Exact-Magma bytecode checks passed, but a live Magma run could not start because required runtime dependencies were unavailable. This remains a copied-server acceptance step.

The attached verification report distinguishes actual automated Forge tests from compatibility work and identifies the exact Naruto and Waystones binaries used where applicable. The complete addon roster, the user's Magma runtime, live multiplayer clients and the real `Naruto.test` save require separate acceptance on a copy of the installed pack. Do not treat one tested helper or a generic ability fixture as evidence that every real jutsu has been exercised.

## Build and source

The original source, build tools and original test fixtures are maintained in `modules/dungeon-guard/` in [Hidden Block Village](https://github.com/tasx13ok-create/Hidden-Block-Village). Version-specific receipts under `release/evidence/` identify the exact checked production JAR. Follow `BUILD.md` in that checkout for the pinned build. Production classes target Java 8. The modern build tool uses a separate Java 25 JDK; the Minecraft server uses Java 8. Private test JARs are excluded from the production download.

The deliverable contains original addon code. It does not bundle Minecraft, Forge, Magma, Naruto mods, Waystones or any world. The transparent banner was generated for this project and is included as an original addon asset.

## Primary compatibility references

- Forge 1.12.x networking and client-thread guidance: https://docs.minecraftforge.net/en/1.12.x/networking/simpleimpl/
- Forge side separation: https://docs.minecraftforge.net/en/1.12.x/concepts/sides/
- Forge 1.12.x event and patch sources: https://github.com/MinecraftForge/MinecraftForge/tree/1.12.x
- AHZNB 0.3.1-beta source: https://github.com/AHZNB/naruto_mod/tree/0.3.1-beta
- AHZNB manual block helper: https://github.com/AHZNB/naruto_mod/blob/0.3.1-beta/src/main/java/net/narutomod/procedure/ProcedureUtils.java
- AHZNB portal procedure: https://github.com/AHZNB/naruto_mod/blob/0.3.1-beta/src/main/java/net/narutomod/procedure/ProcedureYomotsuHirasaka.java
- AHZNB Kamui block helper: https://github.com/AHZNB/naruto_mod/blob/0.3.1-beta/src/main/java/net/narutomod/procedure/ProcedureKamuiTeleportEntity.java
- Official Waystones 1.12 implementation: https://github.com/TwelveIterations/Waystones/tree/1.12
- Magma 1.12.2 source: https://github.com/magmafoundation/Magma-Forge/tree/1.12.2

These sources explain integration choices; the exact installed pack remains the final compatibility authority.
