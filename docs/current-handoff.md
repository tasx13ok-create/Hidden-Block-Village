# Current project handoff

Updated 2026-11-14. GitHub is the shared source of truth; private PC files and unpushed work do not transfer automatically.

## Current owner request and live session

The owner wants Claude's reviewed work merged, the requested Bisect archive updated, named client/full ZIPs, and a playable creative inspection session.
This explicitly revokes the earlier request to keep Minecraft closed.
Keep the active inspection session available. Do not send input, close the client, or stop the server without coordination.
The latest client process was launched at about 11:11 local with Core Source13; server process 31524 is ready on private port 25594.
Client process 30120 emitted NARUTO_DODGE_SERVER_READY at 11:12:13 local and completed the actual Source13 join at 11:12:14.
The modded connection established at 11:12:10; the current client is stable in multiplayer.
Actual saved-session dodge acceptance is now confirmed: the accepted sequence increased from 0 to 36 after save-all at 11:13:38.
The transaction restores that sequence on refusal/refund, so this shows 36 accepted session dodges, without claiming all four physical directions were tested.
Root owns final logs/process observations, Git sync, archives and the final status update. Do not infer a join from process existence.
The owner requested no new backup for the target update because backups already exist. Do not create another one automatically.

## Exact current component cohort

The owner's latest deployment preference is one server folder at a time in chat.
The first delivered replacement is
`D:/Bisect server upload/Hidden Block Village - Folder Replacements/01 - mods.zip`:
200,992,059bytes, SHA256
`62daa2a27644d07f5a192afd78c85c7761a5e93db2e653d2f5daa82278dea60d`.
All127files, including53JARs, match the accepted unpacked server manifest;
every ZIP member passed CRC/SHA256 readback. It contains a top-level `mods/`
folder, so stop the host, remove the old mods folder, upload/unarchive at the
server root, and keep stopped until config/plugins replacements follow.
The owner briefly requested a combined ZIP, then explicitly returned to this
one-folder-at-a-time approach. `02 - config.zip` was delivered,900,249bytes with66files,
SHA256 `ac98fb18479929c1bc1a156d4dc7dbddc9b5c3b30fb0f9b1c95da874ef0436c7`.
`03 - plugins.zip` is the next delivered folder,3,272,247bytes with1,602files/3JARs;
SHA256 `6e18573aa590721a16ef549cce3d486566729ab02f96220ecf1809452995b4e6`.
It includes the final native-verified stamina plugin enabled. Both passed
manifest/CRC/SHA256 readback; receipts are
`release/evidence/server-folder-config-0316.json` and
`release/evidence/server-folder-plugins-0316.json`. No hosted changes were performed.
The plugin ZIP was rechecked against its whole-archive SHA/CRC and exact stamina
JAR SHA/config before delivery. Keep the server stopped between replacements.
`04 - server settings.zip` is prepared in the same directory:1,657bytes, containing
only spigot.yml; SHA256 `b79c58c33957f233d1ee73406f868e4363701854455f04bff51de8aed80737cc`.
It passed manifest/CRC/SHA readback, with safe receipt
`release/evidence/server-root-stamina-settings-0316.json`. No connection, operator,
whitelist or player files are included. Deliver this final root-settings ZIP next.
The client/world need not be reuploaded for these. The plugin enforces stamina
independently of Magma's partly inert legacy multipliers. See
`docs/bisect-stepwise-replacement.md` for the concise operator sequence.
Tool: `tools/package_server_folder.py`; safe receipt:
`release/evidence/server-folder-mods-0316.json`. Do not start another full6GB ZIP.

Latest owner steering: near-infinite stamina and finish this release now. The final
server-only overlay is `Hidden Block Village - Final Stamina Settings.zip` in
D:/Bisect server upload:5,212bytes, SHA256
`48cac3a8134d25986f073a98e1c5cf3dc218716486eaf251c3a546d96f3b8e21`.
Extract this LAST after the base server ZIP/eight upload parts, then start/restart
the server. No world reupload or client reinstall is required. The unpacked server
folder, original selected archive folder and preview's future startup files are
already updated; the currently running owner session was not restarted.
Dodge cost is0. Original Naruto-Stamina1.0.0 maintains20food/20saturation/0exhaustion
for current Survival/Adventure players every20ticks and cancels only hunger decreases.
The exact plugin passed22real CraftPlayer/native checks in a new flat Magma fixture,
including scheduling, mode/session exclusions and disable cleanup. No health/chakra/XP
calls are added. Native jutsu chakra remains separate. Some Magma movement hunger
config values are inert, so the plugin, not YAML alone, provides the guarantee.
Safe receipts: `release/evidence/final-stamina-profile-0316.json` and
`release/evidence/stamina-native-0316.json`. Other unfinished candidate work stays
private/uninstalled; this change does not claim the full campaign is complete.

The owner's later request for a separate ready-to-upload server is also fulfilled.
`D:/Bisect server upload/Hidden Block Village Server` is a new standalone server folder;
`Hidden Block Village Server - Bisect.zip` is its checked root-layout server archive,
6,037,663,281bytes, SHA256 `80c1e35a1de16c61f9afb7036d67773c9b29a9c3c2bfbea4700870091097ec1a`.
It contains53mods and3,695server inputs, without a nested server/client wrapper.
Bisect documents a500MB browser limit, so `Hidden Block Village - Browser Upload`
contains8independent standard ZIPs, each below450MB; upload and unarchive all eight
into the same server root. They need no multipart joining. Replace old mods/plugins
rather than mixing old JAR versions. Keep/use Magma1.12.2 and64-bit Java8.
The single large ZIP/folder is suitable for SFTP drag-and-drop. Give players only
the separate `Hidden Block Village.zip` CurseForge client. Private world/player and
host settings are preserved; the live inspection session was not changed.
Safe receipts: `release/evidence/bisect-root-server-0316.json` and
`release/evidence/bisect-browser-upload-0316.json`. Actual hosted acceptance remains pending.
Official upload guide: https://help.bisecthosting.com/hc/en-us/articles/40124893034011-How-to-Move-Over-Existing-Minecraft-Server-Files

Current filenames use Naruto branding while stable technical mod, registry and save IDs remain unchanged.
Current Core is Naruto-Core-0.3.16.jar from Source13; current HUD is Naruto-HUD-0.20.1.jar from Source12.
The remaining production components use the complete Source11 cohort.
External main merge c970ce0 later adds Claude's small chest-crouch visual tweak.
That source update is preserved, but was not rebuilt into the Source13 binary
archives during this final config/plugin update. Select the recorded release
artifact hashes rather than assuming every later source edit is in those ZIPs.
Read [the composite proof](../release/evidence/core0316-hud0201-dodge-client.json) before claiming what this mixed correction cohort proves.
Core SHA256: `387dfa432647515193a4d2167aeb1ccba59ab99319cc33a1b3fd8b982d7c1766`.
HUD SHA256: `a0e2a25c74bbd26cd5e5ba3a5bdca45439fd26cf01cd17ab43a83c728fb1b474`.
The final client ZIP is complete: Hidden Block Village.zip, 204,639,753 bytes, 58 mods and 192 ZIP entries; CRC readback passed.
Client ZIP SHA256: `2172ab6f94105ff93f332ad89e96cae665f95d6f2b54c3c240e9531b86a28abe`.
It retains stock original art and the BSL shader pack. Hidden Block Village Full Setup.zip is complete at6,242,378,177bytes with all file CRC/SHA256 readbacks passed; SHA256 `31db1bd77f1985556288b2a582a3a92a568ecae5f33d00ac36dde140d30e9d89`.
Both ZIPs are in D:/Bisect server upload. The full setup contains private world/player data and must stay private.
Read release/evidence/hidden-block-village-client-0316.json for the completed client archive receipt.
Do not distribute an older same-version Core from a stale build/libs glob. Select the exact declared artifact role and hash.

## Observed build and native results

Source11: `83a0fe1cd40dd88aa320216df66a64b4809da708568285b80c608196f0660372`.
Its complete build passed all 11 source areas and 36 artifacts with no source drift.
Build report: `4c9afe3a916f29468e699fb08c92866fc02ef53922e27a740f89b59f97bd356f`.
The disposable Forge native suite passed 3,665 assertions.
Forge report: `2993c4de01bbda8170b060a044a8e3286b9b2e34cd21d9e52a9e721428751b0b`.
A separate full 55-JAR Magma fixture passed 202 assertions: 55 providers, 1 official synthetic admission, 22 charge, 45 Naruto-backed dodge and 79 Bridge.
That fixture used a new flat world and no real player/owner world, exited 0 without force and preserved all 45 original native JAR hashes.
Its safe evidence is [source11-native-provider-bridge.json](../release/evidence/source11-native-provider-bridge.json).
No private economic transformer was installed. The diagnostic port was released after exit.
These are server/native proofs; they do not establish actual keyboard, graphical, shader or full owner-world migration acceptance.

Source12 HUD source: `71418f57b6d05ce624e249a73b4b650ab32c1ce92f94e64ba71f4b03e30f8e26`.
Its HUD-only build passed and corrects the actual client HUD crash.
Source13 Core source: `f8533d0b6cd498a93bd7998effe4ff6e737b0aba6cffdfeb3b50aefeee4d9330`.
Its Core-only build passed all 36 declared Core check tasks, including 18 new server-capability assertions.
A direct ZIP comparison against native-tested Source11 changes only four client Dodge class entries.
Every nonclient class byte and every other runtime archive entry is identical; no fresh Source13 backend-native rerun is claimed.

## Actual V-key defect and correction

The owner reported the real joined Source12 client falsely requiring Core 0.3.14 although client and server both had Core 0.3.16.
The numeric version predicate already accepts 0.3.16. The defect was reading an empty client-side Forge dispatcher mod map.
Source13 uses the current NetworkManager's advertised valeorders_dodge channel, honoring unregister and disconnect.
It preserves registration received before the later ClientConnected event and refuses known unsupported versions.
The existing request wire, server payment, native travel, collision, dodge window and cooldown are unchanged.
Actual Source13 session dodge acceptance is confirmed by the saved sequence increase 0 to 36.
This is separate from the prior four-direction synthetic native proof; complete physical direction coverage remains unclaimed.

## Original mods, artwork and known asset errors

Original supplied mods and their art remain authoritative; our gameplay/presentation glue is additive.
The old flat inventory reskin overlay is disabled. No corrective native asset overlay was activated.
The actual Source11 client log records 39 distinct missing model references (40 occurrences), 2 textures, 5 sound files, 1 sound event and 5 failing variants (6 occurrences).
Most missing parents occur in original full-geometry JSONs; aliasing a missing parent back to its own geometry creates a cycle.
Exact crystal_senbon art exists under the original namespace. Exact kkg_paper art is unresolved; do not guess a substitute.
Some original crop ages and sound mappings remain undefined. These errors may persist after our HUD and dodge fixes.
Read-only diagnostic evidence SHA: `2ebd88a6976b095ebea614ffa16e0df3c02eb22c269acc68d9c8e5033f22c0fa`.
Computer-use kernel assets failed to load, so no automated screen or physical-input pass can be claimed.
Source12 did complete a real join and shader/GL loading, but that does not establish every model, UI, move or gameplay loop works.

## Target update and creative setup

The requested private Bisect target has 53 server mods, including 9 Naruto-named project JARs.
Core Source13 is installed in both the copied preview and the requested target.
The target update wrote no world files and preserved host settings. Existing backups were reused per owner instruction.
Original worlds, schematics, save archives, rollback candidates, player data and credentials stay private and preserved.
The copied inspection player's creative/admin setup used actual server commands and positive native battle XP/current-chakra setup.
Some execute wrappers printed misleading “All invocations failed” text even though changes applied.
Verify actual state before any retry; never repeat progression or resource commands blindly.
NoBreak1.4's original Bukkit bytes fail this legacy Magma interaction API. Use only the separately verified compatible private handling recorded by root.
Do not treat a local copied-world launch as hosted or public deployment acceptance.

## Adopted work and remaining boundaries

Reviewed menu/HUD, hand/weapon cosmetics, original armor opt-ins, generic VFX helpers, Encounter art, registry discovery and inactive proposal tools are integrated.
Runtime mappings require exact registered IDs, reviewed verified:true configuration and accepted outcomes; no RightClick-intent charges or flashes.
The Bridge does not recast native jutsu, debit twice, award extra XP or claim universal native-cast/boss-credit coverage.
The offline name proposal remains verified:false. Renaming it does not activate it.
Hardened ChakraLink, MoveCost, ResourceNetwork and JutsuSnapshotReader remain authoritative; deleted ledger/pool paths are not resurrected.
Recovered upstream tail bodies were removed from the public working tree without rewriting external history.
The proposed hVB tail enhancements remain UNADOPTED; the existing original native tail mod is the baseline.
The separate five-coliseum chat owns all arena paths and orchestration files. Do not edit them from this chat.
The requested sealed Core outcome observer for arena damage/support credit remains pending; generic deferred damage is not accepted reward authority.

## Original-model recovery and next playable candidate

The reviewed original recovery tool is now in `tools/native_asset_repair.py` with
its original synthetic tests and real-parser helpers under `tools/native_assets/`.
Only original tool source/tests/docs and safe hash metadata were promoted;
native geometry, private manifests/launch recipes, JARs and generated packs remain
private on D. The current release ZIPs, owner session and host were not changed.

Root independently ran the promoted21synthetic tests: PASS. Root also compiled
and ran the promoted Java helper against the exact real prepared Minecraft API:
38candidate models /41incoming contexts /1,576assertions PASS. Its report SHA256
`8f11b2426e0696a152537bcf65bf80bdf88e48570ee362392f6dcbdb17c77b15`
matches the worker's independent run. These are headless parser/geometry/UV/
texture-binding checks, not an atlas bake or graphical client test.
Safe records: `release/evidence/original-model-parent-repair-candidate.json` and
`release/evidence/original-model-parent-repair-root-review.json`.

The private parent-only candidate covers37of39logged missing references while
preserving original geometry and child texture maps. Medical's unresolved key1
and missing jiton stay held. Exact native callback selector/field-to-registered-
item identity and a new disposable real-client bake remain activation gates.
No corrective overlay is active or included in the delivered client ZIP.

Academy continuation is a separate D-only0.3.17candidate, not a change to the
accepted0.3.16upload. The worker is measuring fresh actual native-player BXP
thresholds before implementing an earned, once-only native chakra awakening,
short optional basics/first-field-mission progression and restart-safe course
provisioning. No new-player end-to-end route is accepted yet. Preserve the
current four manual upload packages while that candidate is developed.

## Next work and honest limits

The final source checkpoint includes these receipts and this handoff. Fetch and verify origin/main before continuing; the session's final reply reports the verified GitHub commit. Binary ZIPs and private PC inputs do not transfer through Git automatically.

The Source13 real client has joined, advertised-capability detection passed, and 36 accepted real-session dodges were saved. Preserve the live inspection session. Do not reopen the old buggy Source11/12 client archives; superseded candidates are retained privately.

Next, repair the documented original asset references without changing the requested original artwork; finish academy/first missions, imported NPC/save restart and campaign acceptance; coordinate the sealed outcome API with the separate arena branch. Do not infer universal combat/quest parity or hosted readiness from these checks.

The full game, all49-mod behavioral parity, all campaigns, all original NPC payload migration, universal native jutsu mapping and complete graphical/hosting acceptance remain unfinished. The20-hour target elapsed. Use design/README.md and the project skill for continuation, and distinguish historical evidence from current observations.
