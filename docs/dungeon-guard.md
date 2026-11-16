# Dungeon Guard integration

Dungeon Guard is the optional Minecraft 1.12.2 addon for the owner's protected
adventure world: OP building outside arenas, destructible occupied dungeons,
clean-baseline restoration after the last player leaves, world spawn beside an
existing Waystone, and transparent element/boss area banners.

The original source is maintained under `modules/dungeon-guard/`. Read that
module's `QUICK-START.md`, `README.md`, `CONFIG-REFERENCE.md`, `BUILD.md` and `CHANGELOG.md` for
commands, capture limits, data format and build instructions.

## Installation boundary

The production JAR goes in the server's `mods` directory. Install the same
version on each player's Forge client for the illustrated area banner. Clients
without the addon can connect and receive ordinary entry titles. It is not a
Bukkit plugin and does not replace a Bukkit `/spawn` command.

Installing the server addon activates its adventure-world protection policy.
It is therefore kept separate from the existing candidate's default server
roster and assembly contracts. Add it deliberately to a copied server/client
candidate; do not silently deploy it to a world where survival construction is
intended. Non-OP creative mode does not bypass the policy. OP building permission
is confined to checked direct actions and supported native editing commands.

## Dungeon and spawn setup

Open `/dungeon` for the clickable menu, or `/dungeon setup` for guidance.
Look at the existing Overworld spawn Waystone and run `/dungeon setspawn`.
Get `/dungeon wand`, left-click one corner of the complete clean dungeon and
right-click the opposite corner, then run `/dungeon create fire` (or another
element). An existing schematic must first be pasted in its
undamaged form. Dungeon Guard captures the placed world blocks; it does not
import a schematic file or infer a damaged building's original shape.

Wait until capture finishes and the dungeon reports **Ready**. Its element and
title are saved together with the verified capture. Select a chamber inside
that saved dungeon and run `/dungeon boss <boss name...>`; the parent dungeon
and internal area ID are found automatically. Existing `/dg` commands, manual
coordinates and advanced labels remain supported. Labels describe configured
regions; they do not spawn or identify a moving boss entity.

Players still inside prevent restoration. When the final player walks out,
teleports away or disconnects, the default five-second grace period begins.
Restoration includes AIR, block states and serialized tile-entity contents.
Entity respawns, NPC scripts, quests, capability data and reward cooldowns remain
owned by their existing systems. Captured chest contents return after a reset.

Keep `<world>/dungeon-guard/` with the world during backups and transfers. An
addon upgrade preserves existing snapshots and label settings; it does not
require recapturing clean dungeons.

## Existing editing tools

Direct OP mining/building and native `/setblock`, `/fill` and `/clone` are
supported. A global editing window is deliberately absent because it would
allow other players or delayed attacks to alter the world during that window.

WorldEdit is not automatically granted an OP bypass. Its container and NBT
operations can occur before the final block-write boundary, so a future bridge
must validate the actor and region before those operations. Prepare a large
schematic before enabling protection or in a separate maintenance copy.

## Evidence and next acceptance

Use the version-specific receipt under `release/evidence/` for exact production
JAR hashes, tested runtimes and assertions. Component checks, a disposable
native server, visible client gameplay and the owner's actual copied world are
separate acceptance scopes. Tests using one Naruto procedure or one runtime do
not establish that every installed addon and every ability has been exercised.

The optional client renderer is reviewed alongside the current Field HUD
source. Final visible acceptance should include boss bars, the player's chosen
HUD layout and GUI scale, long area names, dungeon exits and dimension changes.
Keep the original map and existing owner inspection session available.
