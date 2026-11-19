# Current project handoff

## Bijuu armor 4K seam and tail detail pass, 19 November 2026

The owner requested another refinement round after the helmet fit correction. Front trim and markings now continue around the chest, arm and leg sides without a hard color jump. All 54 side-edge joins match their neighboring front cells. The exporter projects the color textures directly into the 4096 × 4096 atlas. It keeps the scratch detail and small marks that went missing in the previous projection. The original tail patterns and opacity are restored in the reference colors. This includes Shukaku's seals, Isobu's scales, Son Goku's lava cracks and Kurama's tail and cage details. Helmets stay centered and the geometry is byte-identical to stock.

Private outputs are the Bijuu-Armor-4K-Detail-Pass ZIP and matching 0.16.2-rc1 JAR. Both rebuilt byte for byte. Only 101 PNG entries change in the JAR. Forge with OptiFine and BSL verified 36 models and captured all nine equipped sets head on and at a 32-degree angle. See [the version-specific receipt](../release/evidence/bijuu-armor-4k-detail-pass.json). The source attachment, screenshots and binary outputs remain private.

## Bijuu armor 4K helmet fit and detail, 19 November 2026

The owner reported that the helmets looked slightly turned. In the first export, the visor openings were 8 to 24 pixels right of center on most 512-pixel front cells. The crop for some helmets also included the cheek or background. The painter now maps the front and cheek separately and centers all eight openings. Isobu keeps the continuous shell guard in the reference. Higher detail sleeve, cuff, knee and boot features come from the larger reference images. Geometry and gameplay remain stock.

Private outputs are the Bijuu-Armor-4K-Refined ZIP and matching 0.16.2-rc1 replacement JAR. Both rebuilt byte for byte. The ZIP has 104 entries and a straight-on versus angled helmet sheet. All 101 changes in the JAR are PNGs. Forge with OptiFine and BSL verified 36 armor models, 10 main atlases and 73 root draws and captured a fixed straight-on image of each equipped set. See [the version-specific verification record](../release/evidence/bijuu-armor-4k-refined.json). The attachment, previews, local shader screenshots and binary artifacts remain private.

## Bijuu armor 4K reference edition, 19 November 2026

The owner requested a texture-only match to their nine-outfit reference, with richer detail and 4K shader support. The final painter uses the actual visible outfit pixels for the front of the helmet and chest and the small outfit views for the sleeves, pants and boots. Hidden sides continue the pictured designs. See [build and shader instructions](../tools/bijuu_reference/README.md).

The private vanilla ZIP contains 18 color textures at 4096 × 2048, 36 matching LabPBR maps and 36 inventory icons at 32 × 32. The separate hvbijuu 0.16.2-rc1 replacement uses 4096 × 4096 atlases. All 101 changed entries are PNGs. Stock models, tails, gameplay code, IDs and all other entries are byte-identical to the supplied JAR. Both final packages rebuilt byte-for-byte.

Forge 14.23.5.2859 with OptiFine G5 and BSL 10.1.8 rendered a disposable gallery with nine equipped stands, with normal and specular maps active. The native probe verified 36 models, ten main atlases and 73 root draws and exited 0. The owner art, comparisons, screenshots and both binary deliverables are private. See [verification and hashes](../release/evidence/bijuu-armor-4k-reference.json). Next if requested: try the separate replacement JAR in the owner’s copied full client profile and inspect outfit details under their usual shader preset.

Updated 2026-11-16. GitHub is the shared source of truth; private PC files and unpushed work do not transfer automatically.

## Latest addon checkpoint: Dungeon Guard 1.2.0

The owner requested a final JAR and much simpler commands. Version1.2.0 adds
the `/dungeon` alias, a clickable chat menu and setup guide, a vanilla-stick
selection wand, look-at `/dungeon setspawn`, one-step
`/dungeon create <element> [display name...]`, and
`/dungeon boss <boss name...>` with automatic parent-dungeon selection.
Existing `/dg` commands, captures, labels and spawn settings remain compatible.
The short owner route is in the module's `QUICK-START.md` and `START-HERE.txt`.
The addon remains optional and is not added to the default candidate roster.

The production artifact is `Dungeon-Guard-1.12.2-1.2.0.jar`, 966,018 bytes,
SHA-256 `737f24a2dbc6745536e9403915beb6ac99fe7686b9f5bec36ec9f1dea32f5da7`.
Capture holds a copied label until the baseline is saved and verified, then
installs the region and label together. Names skip existing and retained
baseline files. The requester receives a single Ready or failure notice.
Normal status is readable, and management buttons prefill the correct IDs.
Selections clear on logout, dimension change and server lifecycle changes.
Wand clicks leave terrain and containers unchanged and grant no OP exception.

Observed current acceptance: **606 passing assertions**:220 transformer,
42 pure HUD layout,284 native (all219 prior assertions plus65 setup checks),
and60 native checks with exact Naruto0.3.1-beta/Waystones4.1.0 inputs. The final
JAR ran on standalone Forge14.23.5.2859/Java8 in fresh disposable worlds; both
server runs exited0 (19.574s regression,24.206s real mods). Thirteen relevant
core/permission class files are byte-identical to1.1.1. That version's44 external
Naruto and9 exact Magma bytecode checks are historical evidence, not added to606.
See [the version-bound receipt](../release/evidence/dungeon-guard-1.2.0.json).

The first candidate's native run retained236passes/5failures, including a
downstream creation failure and fixture abort. It exposed three real command
issues: level0 alone did not allow non-OP public dispatch, Creative inventory
pickup could discard an uninsertable wand while reporting success, and the
look-at command used a client-only ray-trace method. Production fixes preserve
the original failing assertions; all now pass, together with new positive
checks for a full hotbar/free inventory slot and ordinary-player spawn travel.
The failed candidate's real-mod60-check run also passed but is not counted as
acceptance of the final bytes. Safe failed-run evidence remains with the receipt.

Limits remain: no live Magma/plugins, full installed roster, graphical client
or real saved-world/Bisect acceptance. WorldEdit still needs its own protected
edit adapter. Snapshots restore block states and tile NBT, not boss entities or
quest state. Next acceptance is the exact JAR on a copy of the complete target
server, followed by visible HUD/menu checks with the owner's client. Preserve
all existing worlds/captures and the owner's inspection session. The separate
villages-training and broader Naruto gameplay work below remains active.

## Previous addon checkpoint: Dungeon Guard 1.1.1

The owner's separate protection/reset/banner request now has maintained original
source in `modules/dungeon-guard/`. It remains an optional addon, separate from
the existing default candidate roster and assembly contracts. Installing it on
a dedicated server activates adventure-world protection; see
[the integration guide](dungeon-guard.md) for its placement and setup boundary.

The production artifact is `Dungeon-Guard-1.12.2-1.1.1.jar`, 948,047 bytes,
SHA-256 `eb8e2a2700f3619bf3ceb7a2c936e83a3981cb15e41361feda839f2e40bcef27`.
It retains OP-scoped direct construction, last-player dungeon restoration,
existing clean baselines/labels and the Overworld spawn Waystone binding.
New corrections guard the actual Naruto public Kamui block helper before early
item effects, protect the verified Magma vanilla-command route before container
clearing, and move the transparent banner around boss bars and available public
Field HUD bounds. Crowded GUIs use compact text; Shinobi Immersion reserves its
compass strip. A present Bukkit wrapper without the verified guard stops startup.

Observed acceptance: 594 assertions passed: 220 general transformer, 42 pure
layout, 44 external Naruto bytecode, 9 exact Magma static-bytecode, 219 native
regression and 60 real-mod native checks. Both native runs used standalone
Forge14.23.5.2859 / Java8 and the exact production JAR, exited0, and used fresh
synthetic worlds. All219 prior regression assertion names/order remain intact.
The real-mod profile used exact Naruto0.3.1-beta and Waystones4.1.0 binaries;
its groups are25 Naruto helper,33 Waystones and2 identity assertions. The first
run retained48passes/3failures from a missing discovered-waystone fixture
prerequisite. Only that fixture was corrected, the original travel/reset checks
were retained, and the60-check rerun passed with unchanged production bytes.
Eighteen public Field HUD API signatures and three negative release-package
checks passed separately and are not added to594.

Version-bound [verification](../release/evidence/dungeon-guard-1.1.1.json),
original fixture sources, safe logs, input hashes and the retained failed-run
receipt are committed. External dependency JARs, private QA JARs and generated
worlds remain outside Git. Positive reference tests read pinned external JARs
at execution time; no reference implementation bodies/art are published.

Limits: live Magma startup did not run because required Bukkit/BungeeCord Chat
downloads returned403 and SnakeYAML1.19 is absent. The recovered b4c01d2 base
exactly matches the recorded PC hash despite the older8cc35ed filename. Static
wrapper checks are not live Magma acceptance. Graphical client rendering/live
HUD packet dispatch, the complete addon roster, the real saved map and Bisect
acceptance remain open. The real Naruto helper is invoked directly; no in-JAR
call site was found, so no player-hotkey exploit is claimed. WorldEdit remains
unsupported for protected edits because its container/NBT mutations need their
own adapter; details are in the module's compatibility notes.

Next for this addon: run the exact JAR in a copied complete target Magma/plugins
runtime, verify its optional command hook and OP/locked-container behavior,
then inspect banner placement and area transitions with the actual graphical
client and saved dungeon bounds. Preserve all existing world/capture data and
the owner's inspection session. The separate villages-training and broader
Naruto gameplay work recorded below remains active.

## Latest direct-work checkpoint: training and actual process restart

Latest owner preview: `C:/Users/Steal/Downloads/NarutoDestinationVillages-1.12.2-1.4.2-HBV-PREVIEW-4.jar`,
7,026,160bytes, internal1.4.2-hbv112.3, SHA256
`63bb2d8d3a46e50bf827d83fa223f28063beb87851a55e42077e221d3dc1d0e1`.
It replaces earlier villages addon versions when testing; never install
duplicate versions. This is still an unfinished port, not deployment acceptance.
No live owner profile/server/world was changed or launched. Original mod art
remains supplied by its JARs; the old flat-icon resource pack stays disabled.
No new agents were spawned or resumed.

SP/JP/reset now have separate long balances and real spending behavior. Original
adapters implement four SP disciplines (ninjutsu/taijutsu/kenjutsu/speed), real
native hurt/health/movement effects, canonical native chakra-capacity bonuses,
reset-credit spending/refund, and actual owned native technique learning. Open
`/shinobitraining` or the village hub training button. Left/right clicks spend
1/10SP; the learn button cycles on right and learns on left. OP issuance is
`/villagereward <xp|sp|jp|reset> <1|5|10|50> [count] [player]`.
Tokens require marked paper, use server inventory, and preserve installed
original providers before aliases. The supported stat formulas and JP rank
costs are documented target-base adaptations, not parity with the old base.

Native warm run12 in disposable runtime13 PASSED163assertions with the current
53server mods plus this exact .3 addon and private QA. Actual native damage
increased2->2.199999809for each supported unarmed/weapon/jutsu damage category;
movement/health modifiers, SP allocation/refusals, JP learning/duplicate refusal,
reset credit/refund and prior bank/currency/XP/PvP/respawn/drop checks passed.
The count includes54nonnull inventory-slot checks, not163distinct features.
Canonical chakra maximum remained120.5from base108after80native updates with
10ninjutsu/10taijutsu points, without stacking. It uses an original structurally
guarded two-point ASM hook, not periodic setMax calls or a shadow chakra pool.

Separate cold process on that actual saved synthetic world PASSED15assertions:
SP50/JP47/reset0, native battle XP221, actual ender-chest saved technique owner
and learned state, and another player's allocated speed5/real movement effect
survived a full shutdown/restart and native respawn. No rewards were reissued.
Both processes exited0 without forced termination; all54staged gameplay JAR
hashes stayed unchanged. No real player or original world used. This proves
training persistence, not PvP cooldown restart persistence or production-world
migration. Safe version-bound receipt:
`release/evidence/naruto-villages-112-training.json`.

Current public adapter0.1.2-draft component build, five provider-structure checks
and native-fixture source compilation passed. Its reobfuscated adapter classes
are compared to the exact native-tested .3 port in the receipt. Private original
and transformed reference bodies, input/runtime JARs, logs/worlds remain outside
Git. Original83class names, Java8 linkage and SQLite licenses/natives are audited.

Retained failures: runtime10's fixture damage target was outside loaded geometry;
one intermediate fixture compile used the wrong MCP method name. Runtime12's
real capacity check stayed108because the first hook modified only the setter
argument, after NarutoMod's cache comparison. The .3 final hook changes the
desired local before that comparison; runtime13 then passed. Failed candidates
remain private and were not relabeled successes.

Eleven of16original default loot rows now have meaningful providers. The five
DNA/KG/nature/clan/sage rows still lack complete providers. Other legacy training
disciplines, clan/senjutsu bindings, promotion/rank caps, complete teleport,
territory capture, jutsu kill-owner attribution, PvP cooldown restart persistence,
real network/client GUI/casting acceptance and copied deployment-world migration
remain open. The full game goal remains active. Next: recover/apply the five
remaining provider outcome tables, preserve their actual powers/persistence,
then finish native/client/migration acceptance before deployment packaging.

## Prior direct-work checkpoint: native XP rewards and PvP (d6a3060)

The newest local preview is now
`C:/Users/Steal/Downloads/NarutoDestinationVillages-1.12.2-1.4.2-HBV-PREVIEW-3.jar`,
7,000,342bytes, SHA256
`10e0d605d75f183983fb03c174d6f0958ba74614547b6e50fa18b82d3686e5a0`.
Internal port version is1.4.2-hbv112.2. Replace an earlier villages addon when
testing; do not install duplicate versions. No actual owner client/server was
updated or launched for this checkpoint. Original artwork/resourcepacks remain
unchanged. The live private preview server31524/25594 was preserved.

Recovered the original token provider APIs privately. XP grants chakra
experience with1/5/10/50denominations, general/rank bonuses, integer truncation
and sneak whole-stack consumption. RP is RESET points, not rank currency; SP
and JP are separate spendable resources. Nature/KG/sage randomizers have
specific roll/application semantics. Public safe metadata and unfinished
binding obligations: `tools/naruto_villages_112/reward-semantics.json`.
No reference implementation bodies/art were added to Git.

Implemented original native XP reward adapters under the existing public tool:
`NativeRewardItems`, `NativeXpRewards`, `XpRankMultipliers`,
`CommandVillageRewards`. The new reward stacks are marked vanilla paper with
name/lore, require no client item registry or resource pack, and use the actual
NarutoMod PlayerTracker.addBattleXp API. This is the deliberate native progression
adaptation, not a reproduction of the old base's XP curve. All45original rank
multiplier defaults are retained as compatibility metadata. Normal paper and
invalid denominations do not grant XP. Server inventory determines the redeemed
stack; a per-player guard prevents reentrant use. Tokens are reserved before
the native call, with a refund when an exception/refusal leaves XP unchanged.
Failure/refund branches were not injected in this native acceptance run.
Operator issuer: `/villagereward xp <1|5|10|50> [count] [player]`;
`/villagereward reload` and the general villages reload refresh NativeRewards.cfg.

Native run09 PASSED its server-side scope with the same53current server JARs,
the new addon and a separate private fixture, in a new disposable flat world.
It retains the bank/currency/data checks below, then admits a second non-OP
synthetic player. Real native player damage/death and actual native respawn were
used, not manually posted death events. Friendly native hurt was canceled with
HP21->21 even though attackEntityFrom returned true; the fixture checks the
actual hurt cancellation and HP outcome. War damage reduced HP21->19.
An actual lethal hit awarded exactly8configured XP tokens once. The selected
earned token raised native XP216->221 and left7tokens. A second lethal hit after
native respawn awarded none because anti-farm recognized the same UUID. After
an explicit private anti-farm config override, another real kill with60tokens
already stacked and a full inventory ended at64stored plus4ground tokens: no
partial-insert duplication. Ordinary and sneak-right-click XP redemption,
rank rounding, invalid-token refusal and issuer permission were also checked.
132assertions include54nonnull-slot checks; this is not132unique game features.
Exit0, all staged JAR hashes unchanged, no forced termination, no real player
or original world used. Safe version-bound receipt:
`release/evidence/naruto-villages-112-native-xp-pvp.json`.

The actual PvP kill/drop regression used an explicitly configured private
XP-only pool, loaded through the real config reload, so randomness could not
hide a failure. It is NOT proof of the entire default reward pool. Only the
four XP rows of the16-row original default pool now have providers; the other
12rows (SP, JP, reset points, DNA, KG/nature/clan/sage randomizers) remain
unresolved, preserved and explicitly unfinished. Do not turn SP/JP/reset points
into unrelated XP or currency to make a mapping count pass. Native jutsu damage
owner attribution, cooldown persistence across server restart, promotion/rank
caps, full teleport execution, territory capture, real client/mirror and copied
deployment-world acceptance also remain open. The full game goal remains active.

Intermediate native07failed because its assertion incorrectly expected a false
attack return on a canceled hurt despite observed zero HP loss. Native08earned
the proper reward but attempted to redeem a different selected item after native
starter items populated the inventory. The09fixture observes actual cancellation
and explicitly selects the earned stack; no production logic was changed to
fake either setup correction. These failed private candidates are retained.
Public adapter0.1.1-draft component build and current native-fixture source
compilation passed; the public adapter reobfuscated classes are compared with
the exact native-tested port in the receipt. Derivative addon sources, runtime
JARs, reference API recovery and synthetic worlds remain private.

Next: implement faithful SP allocation/reset and JP learning before enabling
their preserved default rewards, recover all randomizer/DNA provider outcomes,
add the outstanding native event/restart/client checks, then integrate accepted
artifacts into the one-folder release workflow. Do not call this port intact or
the full Naruto game finished from the XP/PvP subset.

## Prior checkpoint: villages port preview (5d5c078)

Owner asked to decompile the supplied NarutoDestinationVillages1.4.2 addon,
port it from1.7.10 to1.12.2, and connect it to the existing AHZNB NarutoMod.
Work remains direct: no new sub-agents were spawned or resumed. This is an
unfinished port, not full parity or a production-ready deployment.

Private workspace:
`D:/HiddenBlockVillage-Sprint-20261113/naruto-destination-villages-112-port-01`.
Original input in Downloads remains unchanged,284,189bytes, SHA256
`c4a5e4c96b597efe4b76a4d562307c4ae837fe299abe91a127d0169b8d3e8fe3`.
All83original class names are present in the rebuilt candidate; that is a
structural check, not evidence of complete behavior preservation. The candidate
has no remaining old FML or old NarutoDestination base-class linkage. Java
class majors are50/52. SQLite3.21.0.1 driver, its licenses and Windows/Linux
native libraries are bundled. Private derivative addon source/JARs are NOT in Git.

Original adapter source, its portable component build, acceptance fixture and
safe audit tool are now under `tools/naruto_villages_112/`. The adapters alone
have no mod entrypoint and must not be installed separately. The original
rank IDs/legacy numbers are compatibility metadata; adapter implementations
were authored independently. The full private port uses RFG2.0.3, Java8 and
stable39, version1.4.2-hbv112.1; FML modid is lowercase
narutodestinationvillages, with the original config directory retained.

Prior local preview delivered:
`C:/Users/Steal/Downloads/NarutoDestinationVillages-1.12.2-1.4.2-HBV-PREVIEW-2.jar`,
6,989,815bytes, SHA256
`d814b429bca938ad9896a02aefb191564799dbdbebf3ab0034e14f3d34b5c02d`.
This supersedes the earlier PREVIEW.jar (SHA039d53ab...). Neither was installed
in the owner's actual client or server. Replace an earlier villages addon when
trying the preview; do not load duplicate versions. It targets existing
narutomod and variedcommodities, not the1.7.10 NarutoDestination base.

Native run06 passed its documented SERVER SUBSET in a new flat-world Magma
process:53exact current server mod JARs plus the port, and a separate private
fixture. An officially admitted synthetic non-OP player exercised command
registration, native bank GUI/container, normal/shift deposit/withdraw, page
restrictions/navigation, item NBT and disk reload, sort locking/item count,
real SQLite identity/offline data, native battle XP/Biju reads, currency
deduction/change/full-inventory refusal, symmetric war data reload and transport
location/unlock reload.109assertions include54nonnull inventory-slot checks;
do not market that count as109distinct gameplay features. Actual source and
checked cases are in the fixture. All staged mod hashes stayed unchanged;
exit0, no forced termination, no real player/original world used.
Safe receipt: `release/evidence/naruto-villages-112-preview-server-subset.json`.
Private result/run hashes are bound there. The native carried-item check
observed inventory0/cursor0/ground6: vanilla close handling dropped all6items,
and the old explicit cursor erasure was removed. Do not claim automatic return
to inventory. Fixed original repeated sort-lock acquisition and partial-insert
loot duplication; the new currency planner changes nothing if change cannot fit.
It accepts retained base-nine VC coins and actual installed Naruto Ryo notes.

Failed intermediate fixtures are retained:01/02had insufficient test page
configuration (MAX_PAGES captures configuration at startup);03found the real
repeated sort-lock bug;04asserted an inventory return whereas native close drops
carried items;05tried unlocking an undefined test destination.06sets the two-page
config before startup, counts carried world drops, defines the destination, and
passes those server-side cases. The production83-class candidate was not changed
to fake these fixture setup fixes.

CRITICAL UNFINISHED: all16default PvP reward entries still point to old
NarutoDestination items. They are preserved in config but do not resolve in
the selected target base. Do not claim that loot works or silently replace it
with unrelated filler. Legacy-only clan/skill/senjutsu fields are not actual
native integrations. Rank caps/promotion authority, full teleport execution,
PvP damage/drop/anti-farm, territory capture, real network client/mirror and
copied deployment-world restart remain unverified. Next: recover exact original
reward semantics/public APIs, implement meaningful target-provider bindings,
and add native event/restart acceptance before a full port/release claim.
The full requested Naruto game and prior incomplete packages below remain in scope.

The previous one-folder client config step is also complete:
`Hidden Block VillageSL/config/valeorders-client.cfg` enables armorPowerCore and
jutsuDetail2. Keybinds, original mod textures, server/worlds and other config
hashes were preserved. Receipt:
`release/evidence/client-config-feature-enablement-0316.json`.
Resourcepacks stays empty; original icons/armor come from the original mod JARs.
No replacement resource pack is required on server or client. Supplied server's
resource-pack URL is empty. No new graphical acceptance is claimed.

Sprint clock record: active goal created2026-11-13T17:32:41Z; its20-hour deadline
was2026-11-14T13:32:41Z (08:32:41America/New_York). That target elapsed; the full
goal remains incomplete and active. These timestamps do not reset the sprint.

## Current owner request and live session

PRIORITY owner correction: work directly, no sub-agents, one folder at a time.
All three remaining sub-agents were interrupted and verified interrupted. Do not
restart or delegate to them without fresh owner authorization. The owner reports
the delivered setup looks about two hours behind and reverted icons are replaced.

Root found an actual client mismatch: the new CurseForge profile
`Naruto Complete Client - Friend Test` contains field-hud0.18.0 and enables
Vale-Reference-Flat-Icons-v3.zip in options.txt. It also contains the newer server
mod cohort, which does not replace old client-only HUD/control/compat JARs.
The older unpacked D:/Bisect server upload/Hidden Block Village client tree is
also stale (HUD0.20.0 / Source11Core). Do not package from that unpacked tree.
The sealed Hidden Block Village.zip passes its recorded SHA and contains
HUD0.20.1 / Source13Core. Use explicit sealed inputs, not a stale folder/glob.

ONE-FOLDER correction delivered now:
`D:/Bisect server upload/CLIENT UPDATE - Hidden Block Village/01 - mods.zip`
203,555,669bytes, SHA256
`78296d26effdc60096ced953becdf4394deb930eaa52678bc5aa1e684053d72d`.
It contains58complete client JARs at mods/, including HUD0.20.1 and a new Source14
Core0.3.16 built from current main with Claude's chest-crouch correction.
Core SHA `12eb3d63e13b817648eaabb001bf4d21abdfad0f43b89d70c2e4b2375045a93c`;
only ArmorCoreFx.class differs from accepted Source13. All nonclient Core bytes
and57other JARs are identical. All nested JARs and ZIP CRC/SHA readbacks passed.
Tool: tools/package_client_mods.py; safe receipt:
release/evidence/current-client-mods-0316.json. Server gameplay is identical, so
this renderer-only Core difference does not require another server upload.
The complete Source14Core build passed without source drift; private report:
D:/HiddenBlockVillage-Sprint-20261113/core016-build-snapshot-14/build/source-builds/20261114T173840Z-599cba7a/report.json,
SHA493df853658c54a3542cb8b6329902c047c190265adff44081d3ba6db092a437.
No new graphical/native/server-hosted acceptance is claimed for the renderer delta.

The owner copied all58matching new client JARs into the Friend Test profile,
but nine old extra roots remained, including old HUD/controls/client-compat and
three server-only JARs. Root completed ONLY the mods-folder cleanup with
`tools/clean_mixed_client_mods.ps1`: six reviewed stale/server-only JARs and three
loose metadata/image files moved to the private D retention directory
`D:/HiddenBlockVillage-Sprint-20261113/client-mixed-jar-cleanup-01/retired`.
No ordinary original mod JAR was removed. The tool refused a running profile,
checked all58new JAR hashes before any move and verified the final inventory.
Root independently rehashed all58installed JARs after cleanup: PASS, exact archive
match. Original VariedCommodities SHA remains
bb1afc496b25e2afda523ffc02e3883b4f5a8e6c852c6d5818bd2d82afae6016.
Safe receipt: release/evidence/client-mixed-jar-cleanup-0316.json.
The installed current mods now have no duplicate old HUD/control/compat JARs.
The owned JAR asset-namespace audit found no variedcommodities texture namespace.
The resourcepacks step is now complete. The owner renamed the imported profile
to `Hidden Block VillageSL`; its current directory is
`C:/Users/Steal/curseforge/minecraft/Instances/Hidden Block VillageSL`.
The obsolete Friend Test directory no longer exists. Bind future work to this
new path and verify the exact current JAR cohort rather than guessing an old name.
The previous cleanup script intentionally targets the old reviewed profile name;
do not rerun it blindly against another profile.

Computer-use initialization still fails with Windows path error3. Root detected
the new client running and requested that the owner close it; the owner confirmed
closed. Root verified no profile process, then moved only
`resourcepacks/Vale-Reference-Flat-Icons-v3.zip` to
`D:/HiddenBlockVillage-Sprint-20261113/client-retired-icon-pack-01`.
The828,165byte pack retained exact SHA256
ed45b2f8aab0840b236aef97cfcec42c452a43a84a7c6119fbb622ce617ee39e.
The active resourcepacks folder is empty; all58current mod hashes remain exact,
including the original VariedCommodities JAR. No JAR, option/config, world/player
or host file was written. The supplied server has no resource-pack URL configured.
Safe receipt: release/evidence/client-original-art-restoration-0316.json.
No new graphics test is claimed. Original native mod artwork is available on the
next client resource load; options.txt may still name the now-absent old pack until
normal Minecraft options reconciliation. The shaderpacks directory was untouched.
The client config review/update is complete as recorded in the newest checkpoint
above, preserving controls and the original-mod-art policy.
The Source13preview client exited at12:22:47local; process30120 is no longer
present. Preview server31524 remains alive. Preserve it; historical join/dodge
evidence below does not mean a client is currently connected.

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

Threshold fixture02 exited0 but failed its harness before any XP measurement:
the broad reflective capabilities name selected Forge's CapabilityDispatcher
instead of PlayerCapabilities. Fixture03 corrected that lookup and exited0,
but its final numeric report could not serialize a not-ready NaN value. Do not
claim a measured native awakening floor from either run. A new finite-safe04
fixture is being prepared; it must record exact raw native and canonical values,
including a diagnostic2000positive control if smaller floors stay unready.

Fixture03 did independently capture complete real post-Magma EntityLivingBase
and EntityPlayer input bytes, with unchanged hashes matching earlier native
damage traces. Private bytes/traces remain on D. Root inspected the real health/
absorption/heal ABI: player HP goes through CraftPlayer.setRealHealth, ordinary
mob HP through the data manager, and player absorption through a different data
parameter than mob absorption. Safe facts are in
`release/evidence/sealed-outcome-storage-abi-0317.json`. This is evidence for the
pending arena observer, not an implemented observer or enabled contribution API.
Next capture the actual data-manager/DataEntry stores and Bukkit wrapper origin
in the next necessary fixture, preserving native bytes and event ordering.

## Next work and honest limits

The final source checkpoint includes these receipts and this handoff. Fetch and verify origin/main before continuing; the session's final reply reports the verified GitHub commit. Binary ZIPs and private PC inputs do not transfer through Git automatically.

The Source13 real client has joined, advertised-capability detection passed, and 36 accepted real-session dodges were saved. Preserve the live inspection session. Do not reopen the old buggy Source11/12 client archives; superseded candidates are retained privately.

Next, repair the documented original asset references without changing the requested original artwork; finish academy/first missions, imported NPC/save restart and campaign acceptance; coordinate the sealed outcome API with the separate arena branch. Do not infer universal combat/quest parity or hosted readiness from these checks.

The full game, all49-mod behavioral parity, all campaigns, all original NPC payload migration, universal native jutsu mapping and complete graphical/hosting acceptance remain unfinished. The20-hour target elapsed. Use design/README.md and the project skill for continuation, and distinguish historical evidence from current observations.
