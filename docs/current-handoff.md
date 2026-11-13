# Current project handoff

Updated: 2026-11-13 UTC.

## Owner's standing direction

Always work in and save checkpoints to this GitHub repository so work can continue across PCs. Read `AGENTS.md`. Preserve original work and licensing/provenance records. The owner is recreating mod components and adding original gameplay; maintaining ownership and accurate licensing context is part of the project objective.

## Recovered baseline

The source inspected in this continuation was commit `54ba679` (2026-11-11). The project is Hidden Block Village / Vale, targeting Minecraft 1.12.2, Forge client 14.23.5.2859, Java 8 runtime, and a Magma server intended for Bisect Hosting.

The repository records Core 0.3.9 with eleven source areas and 36 declared build artifacts. Historical source/build/native evidence is in `release/candidate039-verification.json`; these historical Minecraft checks were not rerun in this continuation. Read `docs/recovery-status.md`, `docs/client-and-bisect-039.md`, and `tools/ASSEMBLY.md` before packaging.

The last documented Windows handoff contains Core 0.3.8. A fresh reviewed 0.3.9 handoff and successful complete assembly have not been observed. The preserved world is `Naruto.test`. The approved `actual-stage-before.jsonl` inventory and private runtime inputs remain PC-side; do not fabricate replacements.

## Checks actually run in this continuation

- Release/bootstrap tooling: 145 tests, OK, 2 skips in this Linux environment.
- PC companion coordinator: 26 tests, OK after running with local-loopback access. The initial sandbox attempt failed because binding localhost was denied.
- Desktop guard suite: 33 tests, OK, 5 platform-specific skips. This does not test real Windows input or Minecraft rendering.
- No fresh Java component build or native Minecraft check was run. This executor initially has Java 21; the documented build needs Java 25 for Gradle and a Java 8 toolchain.

## Access and recovery observations

The original PC is reportedly powered on but inaccessible to the owner. A second PC is available. Both Vale browser and native connection tools returned no connected devices during this continuation. No original-PC Codex transcripts or newer unpushed work were recovered.

An isolated official device-login flow succeeded. A subsequent authenticated `codex cloud list --json --limit 20` returned an empty task list and null cursor. This is evidence about that Cloud task query only, not evidence that local chats or other account-retained data are absent. Authentication material is private and must never be committed.

Native ChatGPT/Codex Remote is a separate possible route if the original host is already paired and reachable. Account export is another recovery avenue; inclusion of the desired Codex transcripts remains unverified. Full conversation continuity is not established by repository sync alone.

## Next steps

1. Fetch current GitHub changes on the available PC and read `AGENTS.md` plus this handoff. Preserve any existing local changes.
2. Continue original-PC context recovery through an available authorized remote connection, an existing backup, or an account export. Inspect for newer unpushed source and assembly receipts if access becomes available.
3. Prepare the documented Java toolchains and reproduce source component builds, retaining exact source/output identities and actual results.
4. Recover the approved private inventory and complete inputs; inspect the old assembly status before preparing a separate fresh 0.3.9 candidate. Preserve original world/archive/rollback and incomplete outputs.
5. Validate the complete matching server/client candidate, current Magma runtime, copied saved-world start/save/restart and migration, real graphical client/mod handshake and gameplay, intended graphics/Solas setup, and finally Bisect start/restart/client connection.

Do not represent recorded Forge fixture success, Python tooling success, or the item counterpart coverage metric as proof of complete gameplay or hosted deployment acceptance.
