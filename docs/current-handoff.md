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

Current filenames use Naruto branding while stable technical mod, registry and save IDs remain unchanged.
Current Core is Naruto-Core-0.3.16.jar from Source13; current HUD is Naruto-HUD-0.20.1.jar from Source12.
The remaining production components use the complete Source11 cohort.
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

## Next work and honest limits

The final source checkpoint includes these receipts and this handoff. Fetch and verify origin/main before continuing; the session's final reply reports the verified GitHub commit. Binary ZIPs and private PC inputs do not transfer through Git automatically.

The Source13 real client has joined, advertised-capability detection passed, and 36 accepted real-session dodges were saved. Preserve the live inspection session. Do not reopen the old buggy Source11/12 client archives; superseded candidates are retained privately.

Next, repair the documented original asset references without changing the requested original artwork; finish academy/first missions, imported NPC/save restart and campaign acceptance; coordinate the sealed outcome API with the separate arena branch. Do not infer universal combat/quest parity or hosted readiness from these checks.

The full game, all49-mod behavioral parity, all campaigns, all original NPC payload migration, universal native jutsu mapping and complete graphical/hosting acceptance remain unfinished. The20-hour target elapsed. Use design/README.md and the project skill for continuation, and distinguish historical evidence from current observations.
