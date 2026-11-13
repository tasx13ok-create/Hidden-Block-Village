# Current project handoff

Updated: 2026-11-13 UTC.

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
