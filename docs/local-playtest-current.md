# Reusable private local playtest assembly

`tools/playtest/assemble_local.py` prepares independent candidates under **D:**. It never launches Java/Minecraft, downloads a file, installs Forge, reads launcher accounts or edits source inputs. A successful assembly is a copy/integrity receipt, not a gameplay or deployment pass. Keep its generated receipt, original mod JARs, configs and world copies private and outside Git.

Two deliberately separate variants are supported:

| Variant | Client | Dedicated server | World |
| --- | --- | --- | --- |
| `vale` | Nine current Vale client components plus pinned JEI, OptiFine and Essential | The nine exact runtime components bound by the successful disposable Forge receipt | Independent `server/playtest-world` copy; client recipe connects only to `127.0.0.1` |
| `baseline` | All 49 exact inputs from the explicitly selected reference or server-archive inventory, plus Vale enhancements and pinned support, with duplicate owners resolved | None: this client is a singleplayer compatibility experiment | Independent `client/saves/playtest-world` copy |

The full original client cannot truthfully be paired with the smaller Vale-only server: its extra registry owners require a separate compatibility/native assessment. The baseline variant therefore has no server launch recipe or automatic connection. Magma/plugins, hosted acceptance, copied-world migration, the complete 49-mod stack and visible gameplay remain separate checks.

The user's mods.zip 49 loose JARs on this machine were read-only hash-checked against `design/reference-mod-inventory.json`: all **49 match**, with **46 statically observed `@Mod` IDs** and **no duplicate annotated IDs**. That inventory includes OptiFine. The separate archived server also has 49 JARs, but replaces the reference CustomNPCs/Fluidlogged builds and OptiFine with compatibility CustomNPCs/Fluidlogged builds and marketblocks. `--baseline-source reference` (default) and `--baseline-source server-archive` choose their respective existing public hash receipts explicitly. They cannot be interchanged just because both counts are 49. Bootstrap/tweaker JARs can load additional code at runtime, so a static scan is not a substitute for full Forge startup. Original `mcmod.info` example entries are not treated as authoritative owners when actual class annotations identify a different mod.

## Evidence gate

Run this first from the current source checkout:

```powershell
& '..\.venv\Scripts\python.exe' tools/playtest/assemble_local.py check-source
```

It requires all source components to pass; validates the recorded source inventory digest and current source inputs; checks every original and copied build artifact identity; inspects production runtime archives; requires a successful invoked disposable native run bound to that **exact build-report hash**; and verifies native private-source hashes plus the nine loaded production identities/versions. A failed or stale native attempt refuses assembly. The check neither compiles nor starts Minecraft.

Source freshness uses the existing build source inventory (`modules`, Gradle inputs and build tooling) and native fixture inventory. Documentation, this assembler and unrelated handoff generation do not invalidate a compiled JAR. New or changed runtime source/build inputs and private native fixture changes do invalidate it. This is not a bypass for stale source.

The Core 0.3.11 native pass was **not established when this assembler was authored**. The first observed source check correctly refused because the source-build report had advanced while native evidence still bound the earlier build. Use the live receipt gate; do not infer readiness from this document.

## Input selection

The assembler accepts an already installed Minecraft 1.12.2 / Forge 14.23.5.2859 client and an existing Forge server template. Defaults follow the existing inspection tooling:

- Java 8: `local/toolchains/zulu8.98.0.111-ca-jdk8.0.512-win_x64`.
- Client installation: `C:/Users/Steal/curseforge/minecraft/Install`.
- Pinned local support: `C:/Users/Steal/curseforge/minecraft/Instances/Hidden Block Village/mods`.

The support selection is **JEI 4.22.0.1034**, **OptiFine 1.12.2 HD U G5** and **Essential 1.5.1** at their repository-recorded SHA256 pins. JEI itself is preserved byte for byte; the empty-hide-list integration repair is delivered by the current source-built Field HUD bridge. No upstream JAR is patched or uploaded.

Choose a **closed, independent source world**, not either preserved live inspection session. Copying files with stable before/after hashes detects observed changes but is not an atomic backup of a running Minecraft world. `Naruto.test` is a private compatibility input containing original registry owners; do not point the smaller Vale roster at it and call that a verified migration.

World `level.dat` must parse within a bounded NBT reader and cannot have a DataVersion newer than 1.12.2's 1343. Recorded FML mod owners are compared with the selected roster. Missing owners refuse by default. `--allow-unverified-world` exists only for an explicit migration experiment: it records every missing owner, leaves copied-world acceptance false, and never changes the source. This checks level.dat identity, not all region registries or supported mod transitions.

The config tree is copied byte for byte except explicit omissions: authentication/account/RCON paths, nonempty credential values and binary files requiring review. Only omitted paths/reasons/hashes enter the local manifest; secret values are not printed. Source `session.lock` is omitted. No source server properties, operators, account profiles, logs or auth caches are inherited. The server template selection is limited to the pinned Forge universal JAR, matching vanilla server JAR, existing libraries and existing EULA choice. Forge/client library and vanilla version pins are checked when present; installed native-library/assets-index identities are recorded as external references. Missing local dependencies are refused, never downloaded.

## Plan and assemble a Vale copy

Set the two reviewed closed-input paths first; these example paths are placeholders, not a claim those inputs already exist:

```powershell
$closedWorld = 'D:/HiddenBlockVillage-Sprint-20261113/closed-vale-world'
$closedConfig = 'D:/HiddenBlockVillage-Sprint-20261113/closed-vale-config'
$template = Join-Path (Get-Location) 'local/inspection-session/server'
$support = 'C:/Users/Steal/curseforge/minecraft/Instances/Hidden Block Village/mods'
$candidate = 'D:/HiddenBlockVillage-Sprint-20261113/Vale-0311-Playtest-A'
$arguments = @(
  '--variant', 'vale',
  '--destination', $candidate,
  '--world', $closedWorld,
  '--config', $closedConfig,
  '--support-mods', $support,
  '--forge-server', $template,
  '--server-port', '25578'
)
& '..\.venv\Scripts\python.exe' tools/playtest/assemble_local.py plan @arguments
& '..\.venv\Scripts\python.exe' tools/playtest/assemble_local.py assemble @arguments
```

`plan` is read-only and prints counts/bytes/evidence scope. `assemble` requires a destination that does not exist and whose parent already exists. On Windows it must be on D:. Input/destination overlap, links/reparse points, path traversal, output name collisions, changed identities, insufficient free space and occupied destinations are refused. No recursive deletion, replacement or in-place update is implemented. A failed copy remains with `ASSEMBLY-FAILED.json`; select a new destination after correcting the cause.

For the Vale variant, generated server properties bind `127.0.0.1`, use the selected unprivileged port (default **25578**), disable query/RCON and remote command access, and use a synthetic offline local test identity. Existing sessions on 25576/25577 are not touched. Choose a free port before later launch; assembly does not reserve it or claim startup succeeded. EULA choice is copied from the already installed template, or generated as false if absent.

## Preserve the full 49-mod copy

Use a different new destination and the recovered, closed original world/config inputs:

```powershell
$serverCopy = 'D:/HiddenBlockVillage-Sprint-20261113/server-intake-verified/server'
$support = 'C:/Users/Steal/curseforge/minecraft/Instances/Hidden Block Village/mods'
$arguments = @(
  '--variant', 'baseline',
  '--baseline-source', 'server-archive',
  '--destination', 'D:/HiddenBlockVillage-Sprint-20261113/Naruto-49-Plus-0311-B',
  '--world', (Join-Path $serverCopy 'Naruto.test'),
  '--config', (Join-Path $serverCopy 'config'),
  '--support-mods', $support,
  '--baseline-mods', (Join-Path $serverCopy 'mods')
)
& '..\.venv\Scripts\python.exe' tools/playtest/assemble_local.py plan @arguments
```

The 49 baseline filenames, sizes and SHA256 values must match the selected receipt exactly; missing, altered or extra active JARs fail. Server-archive selection binds `release/evidence/server-intake-20261113.json`; reference selection binds `design/reference-mod-inventory.json`. The private manifest records which receipt and its identity were used. Matching support bytes are reused with an omission record rather than duplicated.

**Original `hvdungeonpictures` stays by default.** The tested Vale `village-standards` component is explicitly omitted from this baseline profile so the original saved registry owner remains active. To test the replacement in the copy, add **`--replace-standard-pictures`**; the manifest records the exact removed original, selected tested replacement and shared IDs. This never deletes or edits the original JAR. It does not establish production-world picture migration acceptance.

Any other duplicate original/enhancement ID fails closed until an exact `--prefer-mod MOD_ID=baseline` or `--prefer-mod MOD_ID=vale` is supplied. An explicit replacement cannot remove unrelated IDs bundled in the original JAR. Mixed preferences within one JAR, duplicate baseline owners and ambiguous filenames are refused. Specifying `--prefer-mod hvdungeonpictures=vale` alone is refused: use the dedicated replacement switch so this transition stays deliberate and visible.

With the currently observed IDs and no extra conflicts, the server-archive baseline retains all 49 originals, adds eight unique Vale client JARs and adds pinned OptiFine, for **58 client JARs**. The user's reference baseline already contains OptiFine and resolves to **57 client JARs**. The actual resolver was checked against the reference inputs and selected 57 while explicitly recording all three support JARs as identical-byte reuse. Both 57/58 paths have synthetic end-to-end planning tests. Counts come from the resolved roster; neither is a compatibility or gameplay pass.

## Review and later launch

`PRIVATE-PLAYTEST-RECEIPT.json` contains exact per-file source/destination identities, generated config identities, input-directory membership, copied world/version identity, artifact and native evidence, selected mod owners, omissions and explicit replacements. It also provides `client_launch_recipe` and, for Vale, `server_launch_recipe` arrays and working directories. The client recipe uses only a synthetic offline name/UUID and dummy token `0`; it never reads a real account token. Launch recipes are recorded, **not executed**.

Root can review those arrays and execute them later through the existing local launcher/session workflow. Use the indicated working directory, preserve the independent candidate, start the loopback server first for variant A, and keep B as a separate singleplayer world. This assembler does not add a paid dependency or establish a connection with Claude/Fable.

## Checks run

The synthetic suite is `python -m unittest discover -s tests -p test_playtest.py`. It exercises exact source/native binding, unchanged-document tolerance, new runtime/private source refusal, artifact tampering, duplicate registry choices, complete 49-input preservation/replacement, real independent copying, source immutability, auth/session exclusions, world-version/owner refusal, Java 8 and port gates, support pin failures, changed directory membership and failed-copy retention. On this Windows machine, symlink creation was unavailable; that test reports a skip rather than a native-link pass. No candidate, game, installer, download or published archive was produced by these tests.
