# Actual Magma baseline startup and restart — November 13, 2026

The exact archived Magma JAR **started and cleanly restarted two separate disposable flat-world candidates**: an original-roster baseline and a Vale Core 0.3.11 enhancement. All four runs reached `Done`, saved chunks and exited `0` without forced termination. This advances the earlier static intake checkpoint; it does not establish recovered-world migration or client gameplay acceptance.

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

`tools/magma_smoke/` implements new-only staging, exact source/dependency checks, fixed local settings, Windows Java 8 checks and hidden bounded boot/stop observation. **Ten fixture tests pass**, including the original eight baseline fixtures plus enhancement receipt binding, duplicate-owner retention and legacy catalog safeguards. They cover source preservation, registry-owner retention, original world/script/config omission, changed-input failure receipts, dependency pins/overwrite protection, network/world profile rejection, Java/memory limits and endpoint allowlisting. Unit tests do not execute a server; the runs in this document are separate real runtime evidence.

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
