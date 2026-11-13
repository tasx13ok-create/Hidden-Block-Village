# Current project handoff

Updated: 2026-11-13 UTC.


## Active Core 0.3.10 academy checkpoint

This section supersedes the older next-step lists below for current gameplay development. The owner asked for implementation, JEI repair, academy/first missions and ongoing GitHub saves. Use `.agents/skills/hidden-block-village/SKILL.md`.

Core 0.3.10 now has six introductory exercises (arrival, nature/ward choice, ordered walking, observed evade/guard/Reed Escape, three accepted lance contacts, real ration crafting/hand-in), a protected opt-in Minecraft campus with five resident guides, and run-bound dispatch/ambush contracts. Stable reward receipts, full-inventory delivery, nonce/distance/stage dialogue guards and owner-bound actor cleanup are implemented. The optional JEI bridge skips an empty removal batch and retains error reporting. Read [the play guide](academy-0310.md) for exact inputs and scope.

The latest complete source build passed all eleven source areas and 36 artifacts with source snapshot `0afcf1cf26b6f75caf3ee8adee5f8cb90574a061c1cfdb0ab11052df1d6b1e67`. There are 37 new progress checks and dedicated JEI bridge regressions. The tooling suite passed 170 tests with four skips. The new separate Minecraft/OptiFine/JEI client joined its fresh academy server and logged `VALE_JEI_CATALOG_PASS: 0`; the prior optional-bridge warning is absent. Exact artifact identities and scope are in `release/evidence/academy0310-verification.json`.

Local playable session: ignored `local/academy-preview-0310-c`, loopback port 25577, player `ValeAcademy`, server PID 20188, game PID 8884. It is in survival at the arrival guide. The old Core 0.3.9 inspection game PID 11804 / port 25576 remains intact. Two earlier refused setup attempts and a debug fixture are preserved under ignored local paths. Do not replace JARs in either live client. Desktop controls failed initialization with Windows path error 3, so a visual/manual-input walkthrough is not claimed.

Final exact-hash disposable Forge acceptance passed 1,261 assertions, including 103 academy assertions: enrollment/replay, full-inventory delivery, walking, actual guard/Reed Escape/arts, native recipe pickup, dispatch, ambush, partial resume and saved player reload. It found and fixed a real UUID-pair cleanup bug: Minecraft 1.12 stores UUIDs as Most/Least tags. Removing the unsuffixed key had left stale references and blocked ambush resume. Earlier failed runs and fixture corrections remain local. Final source/native hashes are in the evidence record. A CRC/hash-checked twelve-mod personal client ZIP is ready under ignored `exports/Vale-Core0310-Academy/`; it includes the fix. The currently active academy game uses the earlier candidate and was left running to preserve the owner's play. Its network/JEI evidence is labeled with the earlier source hash; final-hash graphical acceptance is not claimed. Production-world migration/restart, two-client gameplay, Magma/plugins and Bisect remain separate gates. Friend CustomNPCs/world data have not been published or imported by this change.

## Active twenty-hour Naruto build sprint

The owner explicitly allocated twenty hours to build a fully playable end-to-end Naruto game and asked for community research on mechanics, quality of life, weapons/movesets and interactions. Sprint start: **2026-11-13 17:32:41 UTC**; deadline: **2026-11-14 13:32:41 UTC** (08:32:41 America/New_York). An active goal tracks the work; do not mark it complete because planning or one slice passed.

The latest owner priorities supersede long mandatory onboarding: recognizable Naruto identity, exciting powerful equipment with real moves, fast access to action, reactive villages/world, cohesive movement/weapons/jutsu and preserved Minecraft play. `design/NARUTO-FUN-BRIEF.md` is the detailed decision reference; `design/REFERENCE-RESEARCH.md` now contains primary developer/creator sources and explicitly bounded community signals. Ads are not independent player votes; no inaccessible footage was represented as watched. The project skill and installed local copy now route to that brief. Both skills and the 49-JAR/92-package design ledger validate.

Continue actual implementation during the sprint. Next high-value delivery: **one distinctive Naruto power kit + short combat mission + causal local world reaction**, followed by a contrasting kit and coherent weapon/movement inputs. Read existing mode/armor/arsenal engines first; reuse reliable machinery without claiming shared mechanics are unique. Preserve the full roster and five-dungeon obligations. Do not spend the remaining sprint expanding prose while acquisition, feedback and game feel remain unfinished. Keep testing and publishing meaningful checkpoints; keep existing live games intact unless the user authorizes a restart.

## Owner's standing direction

Always work in and save checkpoints to this GitHub repository so work can continue across PCs. Read `AGENTS.md`. Preserve original work and licensing/provenance records. The owner is recreating mod components and adding original gameplay; maintaining ownership and accurate licensing context is part of the project objective.

## Recovered baseline

The source inspected in this continuation was commit `54ba679` (2026-11-11). The project is Hidden Block Village / Vale, targeting Minecraft 1.12.2, Forge client 14.23.5.2859, Java 8 runtime, and a Magma server intended for Bisect Hosting.

The repository records Core 0.3.9 with eleven source areas and 36 declared build artifacts. Historical source/build/native evidence is in `release/candidate039-verification.json`; the fresh reproduction below verifies the same artifacts and reruns the disposable Forge fixture. Read `docs/recovery-status.md`, `docs/client-and-bisect-039.md`, and `tools/ASSEMBLY.md` before packaging.

The last documented Windows handoff contains Core 0.3.8. A fresh reviewed 0.3.9 handoff and successful complete assembly have not been observed. The preserved world is `Naruto.test`. The approved `actual-stage-before.jsonl` inventory and private runtime inputs remain PC-side; do not fabricate replacements.

## Checks actually run in this continuation

[Fresh verification record](../release/evidence/continuation-20261113-verification.json).

- Installed official Azul JDK 25.0.5 and JDK 8u512 under the execution workspace, verifying both archive SHA-256 values against vendor metadata. Build downloads used the managed proxy and system CA trust.
- Rebuilt all eleven source areas: 36 artifacts passed integrity checks. All 36 outputs are byte-identical to the recorded Core 0.3.9 component build. All 1,512 source/build inputs match `release/candidate039-source-inputs.json`; source stayed unchanged during the build.
- Release/bootstrap tooling with Java 8: 161 tests, OK, one Windows-only skip. This supersedes the initial 145-test run with two skips before Java 8 was installed.
- PC companion coordinator: 26 tests, OK with local-loopback access. The initial sandbox attempt failed because binding localhost was denied.
- Desktop guard suite: 33 tests, OK, five platform-specific skips. This does not test real Windows input or Minecraft rendering.
- Fresh private Forge 14.23.5.2859 run: 1,158 assertions passed with nine production mods and two private QA mods. Loaded mod identities were checked against the source build, process exited successfully, and disposable runtime was removed.
- GitHub source/native job and Ubuntu/Windows release-tools jobs passed for run [43447345877](https://github.com/tasx13ok-create/Hidden-Block-Village/actions/runs/43447345877).
- Actual saved world, current Magma candidate, graphical client, full package assembly and Bisect acceptance remain unrun. These checks do not establish a deployable complete package.

## Access and recovery observations

The original PC is reportedly powered on but inaccessible to the owner. A second PC is available. Both Vale browser and native connection tools returned no connected devices during this continuation. No original-PC Codex transcripts or newer unpushed work were recovered.

An isolated official device-login flow succeeded. A subsequent authenticated `codex cloud list --json --limit 20` returned an empty task list and null cursor. This is evidence about that Cloud task query only, not evidence that local chats or other account-retained data are absent. Authentication material is private and must never be committed.

Native ChatGPT/Codex Remote is a separate possible route if the original host is already paired and reachable. Account export is another recovery avenue; inclusion of the desired Codex transcripts remains unverified. Full conversation continuity is not established by repository sync alone.

## Next steps

1. Fetch current GitHub changes on the available PC and read `AGENTS.md` plus this handoff. Preserve any existing local changes.
2. Continue original-PC context recovery through an available authorized remote connection, an existing backup, or an account export. Inspect for newer unpushed source and assembly receipts if access becomes available.
3. Source reproduction and disposable Forge verification are complete for this checkpoint. On a different PC, follow `docs/build-modules.md` and `docs/native-checks.md` to reproduce them; keep new results bound to their exact source and JAR identities.
4. Recover the approved private inventory and complete inputs; inspect the old assembly status before preparing a separate fresh 0.3.9 candidate. Preserve original world/archive/rollback and incomplete outputs.
5. Validate the complete matching server/client candidate, current Magma runtime, copied saved-world start/save/restart and migration, real graphical client/mod handshake and gameplay, intended graphics/Solas setup, and finally Bisect start/restart/client connection.

Do not represent recorded Forge fixture success, Python tooling success, or the item counterpart coverage metric as proof of complete gameplay or hosted deployment acceptance.

## Windows desktop continuation on 13 November 2026

The available Windows desktop now has a separate clone of this repository inside its older dungeon workspace. Its checkout was synchronized to `69c8c9e6fba844ca7c13feb7839c2cb47f2e0169`; all 1,512 files in `release/candidate039-source-inputs.json` were read and matched their recorded byte counts and SHA-256 values, with zero mismatches. `tools/build_modules.py --list` successfully enumerated all eleven source areas using Python 3.12.10. This verifies source checkout identity and tool discovery, not a new component build or Minecraft run.

The older workspace now has a local `AGENTS.md` entry point directing future sessions into this Git checkout and its current handoff. Existing dungeon saves and deliveries remain at v9 on this desktop; a newer chat mentions v17, but its schematic bytes were not located or verified here.

Targeted filename searches of the inspected local Downloads, Documents, OneDrive/Desktop, CurseForge instance folders and additional download/transfer locations did not locate the reviewed `actual-stage-before.jsonl`, complete Vale server stage, assembly/preparation receipts, or `Naruto.test`. The documented `D:\Codex` directory is absent on this machine. These bounded searches do not establish that every disk or backup has been searched. Both Vale browser and native connection status tools returned no devices during this chat.

Java on PATH reports 21.0.10; the inspected standard Java locations did not supply the required JDK 25/JDK 8 pair. The existing workspace Python 3.12.10 interpreter is usable. No full package assembly, saved-world load, graphical client or hosted acceptance was run during this desktop continuation. The next packaging step remains recovery of the original reviewed inventory and complete private inputs, followed by a separate fresh Core 0.3.9 assembly and its acceptance checks.

## Windows build and native execution on 13 November 2026

The Windows desktop continuation progressed beyond checkout recovery. Portable Azul JDK 25.0.5.0.3 and JDK 8u512 were downloaded from official vendor metadata and their Windows archive SHA-256 values verified. All eleven source components and 36 artifacts built successfully on Windows; every output matches the recorded Core 0.3.9 binary identity. The native Forge 14.23.5.2859 fixture then passed all 1,158 assertions with nine production mods and two private QA mods, and its disposable runtime was removed. [Windows verification](../release/evidence/windows-desktop-20261113-verification.json).

The first native attempt exposed Windows backslashes in relative component-report fields, which the strict native verifier rejects. `tools/build_modules.py` now serializes those fields with forward slashes, with a successful-build/copy regression test. All components were rebuilt from the corrected tool before the successful native run. Production gameplay sources and binaries are unchanged.

Two real Windows fixture assumptions were also corrected: virtualenv Python can retain both a redirector and an interpreter process; Microsoft Store Python can report a different executable name and drive alias from `sys.executable`. The status fixture now identifies its worker explicitly and cleans up its own process tree. The desktop smoke test independently cross-checks the loaded image with Win32 and file identity. Final local results: release/bootstrap 162 tests with four platform/privilege skips; coordinator 26 tests with one skip; desktop guard 33 tests without skips. All suites passed.

A separate personal client import ZIP was created under `exports/Vale-Core039-Local-Test/` and CRC/hash verified. It contains nine current source-built client components plus the locally available Essential and OptiFine whose bytes match the historical pins: eleven client mod JARs total. Its manifest selects Minecraft 1.12.2 / Forge 14.23.5.2859. It is a local test profile, with no saved world or supplied Solas payload, and has not been graphically launched. Its identity and roster are in the verification record; the ZIP and third-party binaries are not published to Git.

The approved original inventory and complete server/world inputs remain missing from the inspected local locations. Full production assembly, actual saved-world migration/start/save/restart, graphical client connection/gameplay/Solas, current Magma candidate and Bisect acceptance remain open. The available Windows machine can now build current mods and run the disposable native checks directly.

## Local OptiFine inspection session on 13 November 2026

At the owner's request, launched Java 8 / Forge 14.23.5.2859 with the eleven current client JARs plus local JEI 4.22.0.1034. The visible Minecraft window remains running. A separate loopback-only creative server loads the nine production server mods and a copy of the available v9 dungeon save. Both sides recorded a successful modded connection and the synthetic local inspection player joined; the client recorded OptiFine initialization and HUD scene-blur initialization. BSL 10.1.8 is available in the inspection profile but shaders start disabled. Original worlds and the older client instance were not modified.

This establishes an actual graphical client process and local network join, with no screenshot or manual gameplay acceptance claimed: the desktop-control tools failed to initialize. It does not establish the private Naruto.test world, final Magma roster, Solas or Bisect results. Local session details and logs remain under ignored local storage.

A follow-up issue was observed: the optional JEI catalog-hide bridge calls removeIngredientsAtRuntime with an empty list when no overlapping props are present. JEI starts successfully and the connection proceeds, but the optional bridge emits a warning. The running session was left available to the owner; production binaries were not replaced during play.

## Comprehensive game design checkpoint on 13 November 2026

The owner supplied mods.zip and requested a deeply planned original shinobi game, functional recreation/enhancement of the full pack, IceeRamen reference research and a reusable implementation skill/prompt. The archive contains 49 JARs; its SHA-256 is c4d54acb14e719ec4ece244aea6843fa20a345b29ff3ebfe6f5c4e719cbd6fdd. Every JAR was CRC-read and hashed. Embedded metadata/filenames are preserved separately, including version disagreements and unresolved identities. No JAR payloads or third-party assets were copied into public source.

Read design/README.md. Eight design documents cover the master product/architecture plan, detailed system contracts, all 49 reference dispositions, five dungeon campaigns, creator-source research, the ten-mission academy campaign and the reusable implementation prompt. The linked machine backlog has 89 dependency/acceptance-defined work packages, without an invented overall completion percentage. The project skill lives at .agents/skills/hidden-block-village and is installed locally for subsequent sessions. AGENTS.md points future work to these sources.

Creator material includes the original public Iceeman SMP video descriptions and current creator-published project/release pages. Direct playback fetch failed, so research explicitly records that no video intervals were visually reviewed and leaves a concrete timestamp-review queue. Do not describe this as watched footage. The 1.16.5 inspiration does not change the established 1.12.2 target.

The new standard-library audit and design validation tools were executed against the real archive and full plan. Seven new regression tests cover exact identities, malformed metadata, ambiguous paths, missing reference mappings, dependency cycles and unsupported completion claims. The design validator checks all 49 mappings, 89 work packages and local design links. The skill passed the Skill Creator frontmatter/scaffold validator. These are planning/tooling results, not new gameplay parity or a new dungeon export.

Next implementation: the known empty-list JEI catalog bridge correction in a separately versioned candidate with focused regression and real client smoke; then the normal-player academy/mentor/acquisition vertical slice and Mist encounter graybox. Keep the currently running OptiFine inspection client/server intact. Missing private production inputs, later v17 schematics and friend NPC exports remain separate recovery tasks, not blockers for independently specifiable original systems.
