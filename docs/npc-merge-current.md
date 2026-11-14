# Friend CustomNPCs changes — actual intake and merge support

The executable intake tool is implemented and tested against the recovered pack's real formats. **No friend bundle has arrived yet, and no friend NPC changes have been merged or published.** A private format fixture produced a verified backup and candidate overlay without changing any recovered files or starting Minecraft.

## What the friend should upload

Stop/save the friend's game or server before making the ZIP so its definitions and world state come from one consistent checkpoint. Keep an untouched backup on the friend's PC. Upload these **privately**, preserving their original folder structure:

| Files to include | Why they matter |
| --- | --- |
| Game/server root `customnpcs/clones/` including all relevant numbered tabs | Global/server clone exports; each new or changed placed NPC must have an exported clone if it is to be merged through the supported clone route |
| `<world>/customnpcs/clones/` | Separate world clone library; the actual pack uses both global and world libraries |
| Complete `<world>/customnpcs/dialogs/` and, if present, `quests/` | Category folders and numeric filenames preserve definition identity and graph dependencies; include referenced definitions even when the friend did not edit them |
| `<world>/customnpcs/factions.dat` | Shared faction IDs and relationships; individual factions are merged by their actual `Slot` IDs rather than replacing the whole database |
| `<world>/customnpcs/scripts/` and any global `customnpcs/scripts/` or referenced external script/library files | Script bodies, shared libraries and persistent variables can depend on NPC/dialog/quest/faction IDs; these remain private and are never executed by the intake tool |
| Root `customnpcs/assets/`, any custom skins/textures/sounds/resource packs, and referenced Armourer's Workshop library/skin files | Model and appearance dependencies do not live entirely inside an NPC clone. The actual server has a separate root `armourers_workshop/` directory; send relevant library dependencies privately if the changed NPC uses them |
| `<world>/customnpcs/global.dat`, `recipes.dat`, `spawns.dat` if the changes depend on them | These are shared databases with unsupported automatic merge semantics; they are preserved and require explicit review, not silently rewritten |
| `config/CustomNpcs.cfg`, exact mod filenames/versions and preferably SHA-256 hashes or the matching private mod pack | The actual compatibility JAR is not interchangeable with an arbitrary CustomNPCs download; scripts and addon capabilities need the same dependency identities |
| A short change list outside the normalized tool bundle | List new/changed/deleted NPCs, intended replacements, source world/version, affected dialog/quest/faction IDs if known, placement coordinates/dimension, required scripts/assets and which NPC should offer each mission |

The friend does not need to hand-edit IDs or manufacture the tool's JSON manifest. Send the intact folders and dependency information; normalize them into the layout below and derive the private manifest from the supplied identities during intake. Keep human notes and configuration references alongside the raw upload, outside the normalized executable bundle. Do not send only screenshots, an NPC's visible name or a schematic and expect dialog/quest/script state to follow automatically.

For **changes to NPCs already placed in the world**, a clone export carries an NPC definition; it does not apply that definition to an existing entity or reproduce placements. Also provide a stopped-world backup, or the exact affected Minecraft 1.12.2 region files with matching `level.dat`/world lineage plus entity UUIDs and coordinates. In this version, saved entity tags reside in the world's region chunks. Their entity-only diff and identity/placement reconciliation is a separate candidate-world operation; replacing a whole region could overwrite unrelated building work. This intake tool rejects region files rather than pretending it merges placed NPCs.

Do not include the friend's player progress or `.dat_old` rollback files in the normalized bundle. `<world>/customnpcs/playerdata/`, account/access files and raw world contents remain private and must not be committed to the public repository. A full private world upload can retain those files for preservation, but the normalized NPC overlay explicitly excludes them.

## Actual pack identities and observed storage

The approved adapter identity is:

```text
CustomNPCs_1.12.2-(05Jul20)-SpringAddon-compat-r1.jar
SHA-256 9002f091bd06dd08bbb0c93b57a9279729b6716813094954a5d57cef101b4e0b
```

The recovered pack also includes SpringAddon, NarutoMod/addons, VariedCommodities and Armourer's Workshop. Their exact identities come from `release/evidence/server-intake-20261113.json` and the private runtime inventory. The tool verifies actual baseline JAR hashes against that inventory; it does not trust a filename alone.

Read-only inspection found **eight clone exports across both libraries**, one dialog definition and a faction database containing four faction definitions. These are exported records, not a census of placed world entities. No quest-export directory was observed in this recovered NPC data. The real `.json` clone/dialog files are typed **SNBT**, not ordinary JSON: numeric `b`, `s`, `L`, `f` and `d` suffixes must survive. Shared `.dat` files are gzip binary NBT. Replacing those formats with a JSON regex rewrite would damage type identity.

## Normalized private bundle and commands

```text
npc-bundle.json
server/customnpcs/clones/<tab>/<name>.json
server/customnpcs/assets/<namespace>/...
server/customnpcs/scripts/...                 (when required)
world/customnpcs/clones/<tab>/<name>.json
world/customnpcs/dialogs/<category>/<id>.json
world/customnpcs/quests/<category>/<id>.json   (when present)
world/customnpcs/factions.dat
world/customnpcs/scripts/...                 (when required)
world/customnpcs/global.dat                  (when required; opaque)
world/customnpcs/recipes.dat                 (when required; opaque)
world/customnpcs/spawns.dat                  (when required; opaque)
```

`npc-bundle.json` has schema `1` and `required_mods`, a map of required mod IDs to exact JAR SHA-256 hashes. `customnpcs` is mandatory. Bootstrap/coremod files without a mod declaration can be pinned with `jar:mods/<exact filename>.jar` keys. Preserve the original upload before normalization; exact file bytes and canonical typed semantic hashes serve different purposes.

From the repository root, the real-world dry-run command is:

```powershell
python -m tools.npc_intake `
  --baseline D:/HiddenBlockVillage-Sprint-20261113/server-intake-verified/server `
  --world Naruto.test `
  --bundle D:/friend-npcs.zip `
  --runtime-inventory D:/HiddenBlockVillage-Sprint-20261113/server-intake-verified/PRIVATE-STATIC-INVENTORY.json `
  --output D:/HiddenBlockVillage-Sprint-20261113/friend-npc-review-01
```

No output directory may already exist. A ZIP goes through collision-preserving safe archive recovery first; traversal, aliases or conflicting paths block NPC intake. The private plan has exact conflict IDs, file hashes and supported reference trails. Exit `2` means issues remain. It creates no merged overlay unless `--candidate` is explicitly supplied and all blocking issues are resolved.

Review a private mapping, then rerun into another new directory with `--mapping D:/npc-mapping.json --candidate`. See `tools/npc_intake/README.md` for the mapping schema. Distinct ID collisions require an explicit replacement/keep choice or an explicit incoming ID remap. File collisions for clones/assets/scripts require a path decision. A remap that lands on another occupied ID still needs a decision. Duplicate IDs inside the incoming bundle, duplicate keys and path aliases fail rather than overwrite data. Baseline definitions omitted from the incoming bundle remain present; deletions are a separate unsupported operation and need review.

## What is checked and remapped

- Dialog definitions use `DialogId`; quest definitions use `Id` in this pack's serialized class metadata, with a separately supported `QuestId` variant. Numeric filenames must match the internal ID. Factions use `Slot`; clones retain their library/tab/path identity. A display name is not treated as a stable placed-entity ID.
- Incoming ID remaps update supported incoming definitions and graph edges. Existing baseline edges retain their original IDs. Supported fields include NPC dialog options, dialog `Option` edges when the actual `OptionType` is `DIALOG_OPTION=1`, enabled availability dialog/quest/faction references, `DialogQuest`, `MailQuest`, `NextQuestId`, `FactionID`, `OptionFactions1/2` and structurally supported `AttackFactions` lists/arrays. Disabled availability selectors and negative sentinel IDs are not active edges.
- Factions merge per definition, retain unrelated baseline factions and raise `lastID` for remapped IDs. Unrecognized faction root fields fail explicitly. Newly unresolved supported references block a candidate. Existing unresolved edges are reported as warnings and are not mislabeled as defects introduced by the friend.
- Namespaced item/entity `id` fields require their owning mod in the verified baseline catalog. Texture/CloakTexture/GlowTexture locations must resolve to a catalogued JAR resource or supplied asset file. This proves resource presence, not rendering, model correctness, audio playback or complete registry compatibility.
- Script bodies/libraries, command strings, `LinkedNpcName`, `CloneName`, `CompleterNpc`, remote `SkinUrl`, type-specific quest objective maps/locations and unsupported reference forms require exact file-hash acknowledgements. The tool preserves these as opaque data. An acknowledgement records a manual review decision; it does not rewrite script IDs or prove code runs. Quest objective maps can overload the same serialized field for different quest types, so the tool does not guess that every integer is a dialog ID.
- Global/recipe/spawn databases and standalone scripts are opaque file-level merges. Automatic whole-world entity merging, script execution, generic arbitrary ID discovery, migration between CustomNPCs forks, and deletion/player-progress migration are not implemented. Modified UTF-8 NUL/surrogate encodings and unsupported SNBT forms fail explicitly. The actual observed files fit the supported parser.

## Verified checkpoint and next integration

**18 synthetic tests passed** covering typed round trips, hostile/ambiguous formats and bounded parsing, conflicting/remapped IDs, supported graph edges, faction preservation, opaque references, missing dependencies/resources, path collisions, dry-run behavior, original backup/readback and source preservation. Existing-server intake tests remain a separate suite.

All **18 actual non-player export/database files** tested round-tripped semantically: 11 SNBT files and seven binary NBT files including rollback samples. All **20 original NPC-related files**, including private player files, were rehashed against the server recovery receipt and remained unchanged; no player contents were exported publicly.

The current real CLI preview is private at `D:/HiddenBlockVillage-Sprint-20261113/npc-intake-format-preview-final/`. It uses one unchanged existing clone under a new fixture filename, not friend-authored changes. It verified 15 baseline overlay files in `backup/` and wrote 16 candidate files with **zero blocking issues and one pre-existing dangling dialog edge warning**. It is an NPC data overlay, not a complete server/world backup. Its private report contains complete baseline/incoming/candidate hash manifests. Safe aggregate evidence is `release/evidence/npc-intake-20261113.json`.

Once a real friend bundle arrives, preserve it, normalize and inventory it, resolve its exact graph/path conflicts, review opaque code/assets and generate a separate candidate overlay. Apply that overlay only to a new copied-world server candidate retaining local player progress. Verify NPC display/inventory/model, conversation routes and conditions, quest start/progress/rewards, factions/hostility, trading/jobs, required scripts and resources, clean save/stop, restart and normal-client rejoin. Reconcile placed entity UUIDs separately. Only then publish reviewed portable authored content/migrations under the repository's distribution rules; raw private receipts/world/player data remain outside Git.
