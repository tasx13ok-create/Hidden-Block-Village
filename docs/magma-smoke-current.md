# Actual Magma baseline startup and restart — November 13, 2026

The exact archived Magma JAR **passed eight completed boot/save/stop runs across four separate candidates**, including frozen v17 world copies with Core 0.3.11 and 0.3.12. The 0.3.12 server is now available on loopback25583 through a guarded console controller, and its matching full58 client is assembled and launched by root for handshake observation. No client-join or dungeon-visit result is inferred from assembly or startup.

## Observed runs

Private candidate: `D:/HiddenBlockVillage-Sprint-20261113/magma-baseline-smoke-01/`.

| Run | Result | Elapsed |
| --- | --- | ---: |
| `boot-01-original-roster` | Reached Done; saved fresh flat world; clean exit 0 | 32.110 s |
| `boot-02-restart` | Reopened the same world; reached Done; saved; clean exit 0 | 25.781 s |

Both logs report **52 FML mod identities successfully loaded** and **CrazyCrates 1.10.2.2 enabled**. The active folder contains 46 of the 49 original mod JARs. The three explicit Forge `clientSideOnly=true` omissions are JER, Colorful Health Bar and Controlling; their exact hashes remain preserved in the intake recovery. No other mod was removed to force startup. FML's mod count includes built-in/additional declarations; it is not a claim that there are 52 independently recreated mods.

Magma SHA-256 remains `2c527f054e8db7ab7c716019c6496275c3b4f2aace04107ad6c8cb447d3883ce`, manifest version `1.12.2-b4c01d2`, despite the archived filename containing `8cc35ed`. The actual loader reports **embedded Forge 14.23.5.2860**; the separately archived 2859 JAR is not the launcher used here. Windows Zulu Java `1.8.0_512` ran with `-Xms512M -Xmx2G`, `-Djava.awt.headless=true`, the exact Magma `-jar` target and `nogui`. Both processes were hidden, observed and stopped by their own bounded runner. The selected Java executable hash is in the public receipt. The upstream [Magma Forge 1.12.2 README](https://github.com/magmafoundation/Magma-Forge/blob/1.12.2/README.md) also specifies Java 8.

The candidate binds only `127.0.0.1:25579`, uses offline local mode and one player slot, and disables query/RCON. Its generated profile selects `magma-smoke-flat` and a flat generator. No user joined. Offline mode is scoped to this loopback-only disposable profile. The test is not a publicly reachable server or Bisect deployment.

## Missing classpath targets resolved explicitly

The stage copies 111 verified original runtime/mod/plugin/library inputs and adds the eleven missing manifest targets. Every added file has an explicit origin, published SHA-1 check, SHA-256 identity and disk verification. The complete safe dependency list is in `release/evidence/magma-smoke-20261113.json`; the private input plan is `D:/HiddenBlockVillage-Sprint-20261113/magma-smoke-inputs/approved-dependencies.json`.

| Added target | Reviewed source |
| --- | --- |
| Minecraft 1.12.2 server | Existing approved Forge/native cache, verified against the cached official Minecraft version metadata: SHA-1 `886945bfb2b978778c3a0288fd7fab09d315b25f` |
| authlib 1.5.25, Gson 2.8.0, Guava 21.0, fastutil 7.1.0, Netty 4.1.9.Final | Existing CurseForge Install library cache; each checked against its exact cached official Minecraft artifact SHA-1 |
| disruptor 1.2.14, Lombok 1.16.12, jsr305 3.0.1, progressbar 0.9.3 | Exact published Maven Central coordinates, downloaded over HTTPS and checked against the repository's published SHA-1 |
| Bukkit 1.12.2-R0.1-SNAPSHOT | Official Spigot repository timestamp `20180712.012114`, build `155`; published SHA-1 `ebb39bc230e588e660c100b507f55af34ba7ea70` |

The Bukkit snapshot is an explicitly pinned official missing-dependency input, **not a recovered identity of an absent historical file**. No original snapshot hash was supplied. The Magma JAR already embeds 1,253 Bukkit classes; the new external file satisfies its declared manifest path, while the exact Magma JAR remains first in this tested launch. All added JARs remain private and are not redistributed through Git.

After bootstrap, the redundant root `minecraft_server.1.12.2.jar` is absent. Both pre-existing `libraries/minecraft_server.1.12.2.jar` and `libraries/net/minecraft/1.12.2/minecraft_server.1.12.2.jar` retain those exact Mojang bytes. The second boot succeeds with that runtime-managed layout. The original manifest-target absence therefore must not be equated with proof that the server cannot start. Post-run verification found no changed surviving staged inputs: all 111 original staged files and the ten surviving added files retain their exact hashes; only the redundant added root copy is missing. All 111 corresponding recovery inputs also remain unchanged.

## Embedded versus exploded classes

The archive also contains 2,877 loose root class files. All 2,877 paths overlap classes embedded in Magma; 2,862 payloads are byte-identical and **15 differ**. The differences are fourteen `BlockEvent` nested classes and `ForgeEventFactory` under `net/minecraftforge/event/`.

The `-jar` launch used here does not put the archive's loose root class tree on the manifest classpath. Those files were preserved in recovery and were not copied into this candidate. The historical panel command is still unavailable; do not assume its loose patches were active, or that this JAR-only acceptance verifies those fifteen class variants. Their exact safe path/hash pairs are included in the public evidence for later compatibility review. No class bytes or decompilation were published.

## Config case collisions were not guessed

Neither conflicting root `plugins/messages.yml`/`Messages.yml` nor `Locations.yml`/`locations.yml` was selected for use. All four source payloads remain preserved. The candidate uses fresh plugin defaults, with actual data written under `plugins/CrazyCrates/`.

After the runs, generated `plugins/CrazyCrates/Messages.yml` is byte-identical to the archive's **properly nested** `plugins/CrazyCrates/Messages.yml` (5,996 bytes), which is distinct from both root-collision message files. Generated `Locations.yml` is 14 bytes and matches the archived root uppercase payload. That identical small default is a useful association, not sufficient ownership evidence to relocate arbitrary root plugin configs. The lowercase/root owners and intended historical paths remain unresolved. No decision was based on file size, and no private config contents were published.

## Defects observed despite successful startup

| Finding | Observed consequence and remaining check |
| --- | --- |
| `narutomodaddononero:recipe_killer_bi_sword` references `minecraft:redstone_wire` as an item | FML rejects this recipe on **both** boots with `Unknown item`. The server continues; that crafting route is not accepted. A reviewed recipe-resource compatibility fix is separate from this exact-JAR baseline |
| Sharingan and Rinnegan addon hooks cannot find `ProcedureUtils.isWearingMangekyo` | Both attachment warnings recur. Startup does not prove the intended Mangekyo behavior works; test the behavior or author an explicit compatibility fix before accepting it |
| Cloud/sky/fog hook targets are absent | Three world-render hook warnings occur in this dedicated runtime. Their relevance to the client requires a matching client check; startup is not rendering acceptance |
| Fluidlogged API cannot resolve `customnpcs:mailbox` in its generated internal whitelist | That whitelist entry is skipped. NPC mailbox waterlogging behavior remains unverified; do not silently assume the target registry name is correct |
| Missing Shinobi Culinary mixin reference map and multiple registry/data-parameter warnings | Preserved in private logs. They did not prevent these boots, but relevant interactions remain untested |
| CrazyCrates bundled NBT API reports itself outdated | Plugin still enables on this actual Magma version despite declaring API 1.13. No crate reward, hologram integration or client interaction was tested. A warning is not justification for swapping the plugin during this baseline |

JEI's unusual extra Java 21 class resource did **not** prevent either dedicated boot. Essential and Xaero also did not require additional omission. This does not verify a graphical client, JEI recipes or every nested/bootstrap runtime. The earlier static observations remain useful; the actual startup result bounds their demonstrated effect.

No runtime failure was hidden by replacing Magma or removing registry owners. Original JARs were not patched to erase warnings. Both original boot logs and their receipts remain private; the public evidence contains reviewed metadata, issue labels and hashes rather than raw logs/configs.

## Tools, tests and next step

`tools/magma_smoke/` implements new-only staging, exact source/dependency checks, fixed local settings, Windows Java 8 checks and hidden bounded boot/stop observation. Earlier checkpoints had ten flat/enhanced and15 copied-world fixtures; the current suite has **23 passing fixtures**, including release-pin, graphical-recipe and console-command guards. Unit tests do not execute a server; the runs in this document are separate real runtime evidence.

The next gate is a **new copied-world candidate**, not modifying or relaunching the verified recovery in place. Preserve all original registry owners, select the intended config/loose-class profile explicitly, retain private NPC/quest/assets dependencies and resolve the known recipe/hook/Fluidlogged issues where their behavior matters. Then perform first copied-world boot, missing-mapping checks, clean stop/restart and matching normal-client acceptance. Vale-enhanced artifacts need their own exact assembly manifest and gameplay evidence. Neither this flat-world smoke nor its clean restart authorizes production migration or hosted deployment claims.

## Vale Core 0.3.11 enhanced Magma candidate

After preserving the stopped baseline, a separate candidate was staged at `D:/HiddenBlockVillage-Sprint-20261113/magma-enhanced-smoke-01/`, on **127.0.0.1:25580** with the same Windows Java 8/2 GB limits and a new flat world. It retains 45 original active mod JARs, adds the exact nine native-tested server components and runs the same archived CrazyCrates plugin. The total is **54 active mod JARs** and **60 loaded FML identities**.

The archived `Naruto-Dungeon-Pictures-1.12.2-1.0.0.jar`, SHA-256 `c1078a0897359bf09a58c95ff367f4d77c3eef7e9802f468789a43453cce6948`, was moved only within the new candidate to `retained-originals/mods/`. The original remains untouched in recovery and baseline. Tested `Village-Standards-0.3.1.jar`, SHA-256 `accce5bbbfff4e1bacc17ce9e4604d40b0c8eb5ba1d6cb7cf44a7e21d121a077`, becomes the sole active `hvdungeonpictures` owner. This explicit replacement avoids a duplicate mod ID; no other original registry owner was removed.

The nine additions are Vale Orders, Encounters, Rack, Residents, Residents Server Bridge, World Compat, Damage Compat, Server Bridge and Village Standards. They are selected from `run_native_checks.PRODUCTION`, not from client/bootstrap/source JARs. Their exact public hashes are in `release/evidence/magma-enhanced-smoke-20261113.json`. Core is `vale-orders-0.3.11.jar`, SHA-256 `a0f0374d160c99648fb29dbaf5b2c58da8898f3d8758cb61ba7628688da10609`. The passing source-build report is bound to the passing **1,704-assertion** native report; private exact copies are retained with this candidate. No new compilation was performed by this task.

`canonicalEquipmentCatalog=false` was written before startup and remains false after both runs, preserving legacy equipment recipes/catalog behavior during this coexistence experiment. That is an explicit compatibility profile, not a change to the product default or an assertion that all old equipment now uses Vale mechanics. The enhanced boot guard verifies this setting and all added artifact hashes.

| Enhanced run | Result | Elapsed |
| --- | --- | ---: |
| `enhanced-boot-01` | 60 FML identities loaded; CrazyCrates enabled; Done/save/exit 0 | 31.250 s |
| `enhanced-boot-02-restart` | Same-world restart; 60 identities; CrazyCrates enabled; Done/save/exit 0 | 28.829 s |

No new Core/CustomNPCs/SpringAddon/Fluidlogged classloading crash or duplicate-registry-owner failure occurred during bootstrap/restart. The baseline's rejected legacy recipe and mailbox/hook warnings persist. This is coexistence **startup/restart** acceptance; no client joined, no jutsu/mission/NPC interactions were exercised, no recovered world or original NPC scripts were copied, and no hosted deployment occurred. Both candidates remain stopped and preserved.

## Frozen v17 copied-world acceptance

A third, separate candidate at `D:/HiddenBlockVillage-Sprint-20261113/magma-v17-copied-world-01/` now passed actual saved-world startup and restart on **127.0.0.1:25581**, using the same pinned Core 0.3.11/nine-component/54-active-JAR Magma profile, Java 8 and **2 GB** heap. This candidate is stopped. No client joined it, and no hosted deployment occurred.

Copying began only after the world owner explicitly marked `D:/HiddenBlockVillage-Sprint-20261113/v17-world-candidate-01/world` **FROZEN**. Its private receipt SHA-256 is `cb89b22a12f18578a42ad620b454e5c95a2a7d813579013eebaf56f3efbe002c`; tree manifest `85298e3fb60a1b6b3e521f9ef34a0b24884b9e3597955900d854e8d7f89bec2d`. All **1,771 files / 5,687,244,986 bytes** were copied and independently read back against the pinned file hashes. The source has five exact v17 template pastes in eight new regions/1,397 chunks, with no additional terrain/doorway edits. These paste facts remain offline evidence; native visits to the five distant sites have not occurred.

Before boot, all **1,311 region files / 1,249,484 chunks** were parsed read-only to inspect saved entity/tile script fields. The pass inspected 669 saved entities and 56,275 tile entities, plus the original world/global NPC libraries. It found **zero nonempty executable script bodies and zero parse errors**. Private script/source details remain outside Git. The script absence profile fails closed for nonempty scripts; it does not claim arbitrary JavaScript has been audited or is safe to execute.

The source `level.dat` saved registry-name sets were compared with the exact already booted enhanced runtime. **All eleven registries have zero missing saved names**. In particular, all 348 saved blocks, 1,397 items and 440 entities remain registered; the enhanced runtime has 357 blocks, 1,954 items and 454 entities. This is a name-presence/migration preflight, not proof that every old item's behavior or render is equivalent. No removal, discard or `/fml confirm` command was issued.

**128 original dependency/config files** were copied through an explicit allowlist with byte/readback verification and credential-field review: global CustomNPCs libraries/assets, Armourer's Workshop library/config, required legacy mod configs, proper CrazyCrates data/configs and relevant shared content. The newer frozen world's CustomNPCs, skin database and private player data stay intact; the older server archive's world-level NPC data was not overlaid. Archived access/network/server properties and unresolved stray root plugin collision files remain excluded. The compatibility setting `canonicalEquipmentCatalog=false` remains in force.

| Copied-world run | Result | Elapsed including post-stop snapshot |
| --- | --- | ---: |
| `copied-world-boot-01` | 60 FML identities; CrazyCrates enabled; actual `Naruto.test` Done/save/exit 0 | 38.657 s |
| `copied-world-boot-02-restart` | Same saved world restarted; 60 identities; Done/save/exit 0 | 35.141 s |

No missing-mapping prompt, unknown-entity, classlinkage or out-of-memory marker occurred. The inherited invalid Killer Bi sword recipe remains an ERROR; the known addon-hook/mailbox warnings are not erased by this result. Residents reported three provisioned guides and three saved role slots on the first boot; that provisioning message did not recur on restart. This is a logged bootstrap observation, not a player interaction or dialogue acceptance claim.

After both boots, all **13 copied player-related files** remain byte-identical. All **1,771 frozen source files** and **128 recovery dependency source files** were rehashed unchanged. Runtime writes are confined to the separate candidate; complete private post-stop world snapshots are retained for both runs. Source/native receipts and JARs remain pinned to 0.3.11 even while the main source moves to later versions.

The distinct copied-world guard requires its FROZEN receipt, exact verified copy, complete script review, zero missing registry names, loopback/offline/query/RCON settings, the nine pinned artifacts and reviewed global NPC data. It verifies the initial or last clean world snapshot before each boot. Failed boots remain preserved and cannot be silently resumed through a mapping discard. Current **15 synthetic fixtures pass**, including inline scripted-tile detection without execution, pending-world rejection, source-pin/missing-registry rejection, credential redaction and changed-world protection.

Safe aggregate evidence: `release/evidence/magma-v17-copied-world-20261113.json`. The next gate is a matching full 58-JAR Core 0.3.11 client using this profile's legacy catalog setting, then actual player/NPC/jutsu/dungeon visits, relog/restart and measured native lighting/water behavior at all five sites. The stopped candidate must not be confused with the separate newer-version tryout client/server.

## Core 0.3.12 frozen world and full58 graphical assembly

The next world profile is a **new** directory, `D:/HiddenBlockVillage-Sprint-20261113/magma-v17-copied-world-0312-01/`, on **127.0.0.1:25583**. Port25582 and both existing previews were preserved. All0.3.11 profiles remain intact. Before staging, the current production source matched `58958edc9eb2a776a250ef8e0a73a97e26e8c479e7cd45433ad24a952c71ccc1`; nine exact JARs and receipts were copied into private immutable inputs so later0.3.13 work cannot silently change this profile.

Core0.3.12 is SHA-256 `7c8c91e0677a2e54cccc52459479bf57587fbd790347558c0786ee3e5d0fa8c8`. Its exact build receipt is `7bf23439d3d63fb43a05e8fe352ec9c2ab7ebbdfee0101456f4b7723b0c2f35b`; the passing **2,058-assertion** native receipt is `c95e7c2165fa1892cdd46ade6d3a490bebba5963488a32f83567be5c18cd3c26`. The published source/native proof was read from verified commit `d59ddd24f64cf404032fb9e497896bb5d3d26358`. No mutable module-build paths were used after freezing inputs.

The server still has54 active JARs:45 unchanged originals plus nine selected server components. The old copied0.3.11 components are retained outside active mods. Eight non-Core additions are byte-identical across releases; only Core differs. Because the frozen original world contains no `valeorders` saved names, the unchanged legacy registry-owner baseline can supply a bounded preflight. Its scope is recorded; it is not mislabeled as a prior0.3.12 registry dump. The actual0.3.12 world saved all original registry names after loading, with no confirmation/discard command.

The1,771-file frozen world was copied afresh. The completed1,311-region/1,249,484-chunk script-absence review was reused only after verifying identical frozen file/region manifests and rechecking NPC source hashes. This avoids pretending another full scan occurred. The same128 reviewed dependencies/config files and canonical catalogfalse were retained.

| 0.3.12 copied-world run | Observed result | Elapsed |
| --- | --- | ---: |
| `copied-world-0312-boot-01` | Done/save/clean exit0 | 36.187 s |
| `copied-world-0312-boot-02-restart` | Same-world Done/save/clean exit0 | 35.094 s |

Both runs use2GB,60 FML identities and the archived CrazyCrates plugin. The inherited invalid recipe/hook/mailbox defects remain labeled. Safe aggregate world evidence is `release/evidence/magma0312-v17-copied-world-20261113.json`.

The matching graphical profile at `D:/HiddenBlockVillage-Sprint-20261113/Vale-0312-All-Mods-Client/` was assembled separately: **49 originals − old pictures + nine client components + OptiFine =58 JARs**. There are55 statically declared mod IDs, zero duplicates and no server-only bridge JARs. Orders/Encounters/Rack/Residents/World Compat/Standards are the six byte-exact shared components; HUD, Controls and Client Lifecycle are the three graphical adapters. Essential and JEI retain their original identical pins rather than being duplicated.

Assembly verified all68 external launcher input files and **1,305 vanilla asset entries** against their pins. It reused the approved0.3.12 donor's synthetic/offline launch recipe, changed gameDir/endpoint/identity, capped memory at4GB and selected `ValeFullPack` with its proper offline UUID. Only reviewed client-relevant configs/resources were copied; no worlds, account files, player data, server scripts, old startup scripts or old filters/bookmarks were copied. Client `canonicalEquipmentCatalog=false` matches the server. Fresh JER `general { B:diyData=false }` avoids the observed legacy DIYtrue-without-JSON gap.

The client uses installed Forge2859 while Magma embeds2860. Root launched the assembled client. Its console records the modded connection at15:49:10 and joined-game chat at15:49:13 local time. That establishes an actual handshake/join; it does not establish stable play. The process subsequently hung and Windows closed it. Safe assembly and bounded runtime evidence is `release/evidence/vale0312-full58-client-assembly-20261113.json`.

The live25583 server is controlled by `tools/magma_smoke/live_console.py`, with private status in `PRIVATE-LIVE-CONSOLE-SESSION.json` and atomic JSON command requests under `fullpack-live-0312-requests/`. A `list` request was sent through the controller; clean `save-all`/`stop` uses the same mechanism and preserves a final world snapshot. Root owns the client and subsequent interaction evidence. This helper does not launch clients or disturb the existing hand preview.

The separately reviewed Killer Bi recipe has a concrete bad field at `key/3/item`: `minecraft:redstone_wire`; the registered ingredient is `minecraft:redstone`. No legacy JAR or recipe was patched during these exact-identity tests. Any fix needs a separately recorded variant and client/server recipe acceptance.

### Full58 first-client hang and isolated inventory-tab workaround

Windows recorded Application Hang1002 at15:51:28 and WER1001/AppHangB1 at15:51:26 on November13,2026, for JavaW8u512. Exit `0xCFFFFFFF` is Microsoft's documented `STATUS_APPLICATION_HANG` ([official exception reference](https://learn.microsoft.com/en-us/windows-hardware/drivers/debugger/specific-exceptions)). No matching JVM fatal-error file, Forge crash report or resource-exhaustion event was found in the inspected target locations/window. This identifies a Windows hang closure; it does not prove a JVM/native crash, heap exhaustion or a particular deadlock.

At15:50:41 the client explicitly logged an unsafe screen-open call from `pool-12-thread-1`. Static bytecode review identifies the owner as CustomNPCs SpringAddon compat JAR SHA-256 `9002f091bd06dd08bbb0c93b57a9279729b6716813094954a5d57cef101b4e0b`. `InventoryTabQuests.onTabClicked()V` schedules a Runnable through `CustomNPCsScheduler.runTack(Ljava/lang/Runnable;)V`; `InventoryTabQuests.lambda$onTabClicked$0()V` directly calls `Minecraft.func_147108_a(Lnet/minecraft/client/gui/GuiScreen;)V`. `ClientProxy` registers this inventory-tab integration only when `CustomNpcs.InventoryGuiEnabled` is true. This is a concrete thread-safety defect and a plausible hang trigger; no hung-thread dump was captured, so the exact terminal dependency remains unproven. Earlier OptiFine/RegistryDelegate signer warnings preceded successful login and are not established as the terminal cause. All third-party JARs remain unchanged.

A NEW profile, `D:/HiddenBlockVillage-Sprint-20261113/Vale-0312-All-Mods-Client-TabGuard-01`, retains all58 identical JARs, the same synthetic identity/loopback25583/max4GB recipe and every other reviewed configuration. Its sole content adjustment is client `CustomNpcs.cfg` `InventoryGuiEnabled=true`→`false`; the original bytes are retained outside the active client. The100 other dependencies/configs and three generated files were verified unchanged. The option suppresses CustomNPCs inventory/faction/quest tabs; it is a temporary compatibility workaround, not a fix for the underlying dispatch and not a claim of full UI parity. The original failed profile/logs remain intact. At this checkpoint the variant is assembled and not launched; root owns launch and actual liveness/interaction evidence.

The private variant receipt is SHA-256 `821606f3a8802cad25a6a7f686a7e9264aef95caa292cba7e3bce8cf93bdd387`; launch recipe `99b3674f61e31f377e059f41efbe6040ae8af6596ad4782e74e3f3b8ade0babd`. The matching Magma server/controller stays running. All25 Magma tooling fixtures pass, including exact-byte option editing and fail-closed handling of duplicate/unknown forms. No client UI was automated by this agent.

## Core0.3.13 copied-world baseline on25585

The NEW `D:/HiddenBlockVillage-Sprint-20261113/magma-v17-playable-gates-0313-01` profile passed two actual Done/save/clean exit0 runs and is now stopped. It uses archived Magma/Forge2860, Windows Java8,2GB,54 server JARs, CrazyCrates and `canonicalEquipmentCatalog=false`. Source `aacf8985dc408704cba269daa5a4ef488635030a5e25d938462fbed7f5c0d096` comes from the isolated0.3.13 release worktree, not the mutable original checkout. Core SHA-256 is `c2db8c4d472aec9f38a3c1d7b44b4ac31c93dfd0bbfb6373908de1af8c049352`; exact source-build receipt `33df3e878588866bec95b8e8970655d3ed681457b11975aab77e58e885ac9732`, passing **2,305-assertion** native receipt `79704422c5e655df53686822102c7461be3866573e94aee4f62a7fbece560818`. All12 runtime components and both receipts were copied/hash-frozen privately before staging; all nine server components match that proof.

Port25584 was already owned by the active combat-hotbar preview. The first preflight refused before starting Java or mutating the new world. Root approved a bounded rebind of this unstarted profile to **127.0.0.1:25585**; the existing25584 preview and25583 Magma runtime were preserved. The preflight refusal and config/receipt-only rebind are retained privately. No occupied listener was stopped.

| 0.3.13 copied-world run | Observed result | Elapsed |
| --- | --- | ---: |
| `copied-world-0313-boot-01` | Done/save/clean exit0 | 39.079 s |
| `copied-world-0313-boot-02-restart` | Same-world Done/save/clean exit0 | 35.328 s |

All1,771 frozen-world files and five original schematics were reverified unchanged before stage. Complete no-script findings were reused only for byte-identical source/region manifests; the128 original dependencies and NPC sources were reviewed again at sealing. Core and Residents changed; seven other server additions remain byte-identical to the0.3.11 baseline. No source saved names use either changed namespace. The resulting legacy-name preflight uses that bounded scope, then the actual0.3.13 saved registry dump confirms no missing original names. No mapping discard/confirmation occurred and no private QA JAR was installed.

The first boot logged three provisioned core Residents, zero adopted actors and three saved role slots. Both boots logged five authored gates with unresolved sites remaining disabled. **The seven new props, five new original gate givers and measured runtime bindings are not installed.** The candidate name does not mean those missions have native acceptance. Gate/NPC placement awaits root's console-plan review and an explicit remote-chunk loading strategy. The ignored preparation draft contains exact allowlisted commands, typed-SNBT UUID capture and six passing guard fixtures; it sends no commands. Those future actor identities must come from native output, never generated placeholder UUIDs.

A matching0.3.13 full58 client assembly/launch recipe is prepared privately, with58 verified provider JARs/55 static IDs, six server-shared matches,68 external pins and1,305 verified assets. It uses synthetic `ValeGateProbe`, loopback25585/max4GB, legacy catalogfalse/JER DIYfalse and the explicit temporary CustomNPC inventory-tab workaround. **The new client is not assembled or launched.** No0.3.13 graphical/network/dungeon visit is claimed. The original GUI-thread compatibility draft remains ignored and unpromoted.

All27 Magma tooling fixtures pass, including distinct0.3.13 pins/port and rejection of changed-owner saved IDs or unreviewed reference owners. Safe boot evidence is `release/evidence/magma0313-v17-copied-world-20261113.json`. The inherited invalid recipe and hook/mailbox warnings remain. Hosted deployment, actual gate objectives, world fluid/lighting/route fidelity and broad49-mod behavior remain separate gates.


### Controlled TabGuard client run observed

Root launched the unchanged-58-JAR TabGuard variant after independently checking all mod, six shared server, dependency, generated-config and launcher-input hashes. Its client logged a successful world join at 16:06:38 local time; the server later logged a clean `Disconnected` at 16:12:49. Minecraft logged `Stopping!` and sound shutdown at 16:15:12, and its process was subsequently absent. A numeric exit code was not retained, so these observations are not an exit-0 attestation or a manual GUI acceptance claim. The old failing client and its Windows-hang evidence remain separate. The 25583 server stays running; the client is now closed. This supersedes the earlier unlaunched-variant status.

The temporary inventory-tab option remains disabled only in this variant. A private original compatibility draft dispatches the entire original quest Runnable onto Minecraft's thread and has separate compilation/bytecode checks; it has not been promoted or accepted in-game. Full functional parity, visuals, the five authored mission routes and hosted deployment remain open.
