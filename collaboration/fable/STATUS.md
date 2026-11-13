# Fable status

Updated 2026-11-13 by Fable (Claude Code, cloud session). This records observations only.

## Environment actually available

* Ran in an isolated Linux cloud container with a fresh clone of the repository. This was **not** the owner's Windows PC and **not** a worktree of the primary Codex checkout, so Codex's working directory and live games were untouched.
* Branch: `claude/upbeat-ride-nyxq23`. The task packet suggested `fable/dungeons-v17`, but this session may only push to its assigned branch. Base: `main` @ `b69ec02`.
* **Not reachable from this session:** `local/v17-intake`, the original v17 download folder, the parent v9 workspace (`../output_v9`, `../source`, `../tools`), the D: world recovery and the CustomNPCs data. No computer folder was shared (`list_computer_folders` returned none). No real v17 or v9 schematic or world byte was read, and no game or server was run.

## Package 1: v17 audit/import tooling — tooling done, real-data run pending

| Item | State | Evidence |
| --- | --- | --- |
| `tools/world_merge/` (NBT, schematic, audit, placement, compare, edits, anvil, CLI) | Implemented | Paths below |
| `tests/test_world_merge.py` | 33 tests passing | `python -m unittest discover -s tests -p test_world_merge.py` → OK |
| Whole repository suite | Passing | `python -m unittest discover -s tests` → 192 tests OK, 2 skipped (Linux container) |
| Scale check | Synthetic only | Full audit of a 288×217×288 volume ≈ 2.5 s; paste of 200×148×200 into a world copy ≈ 4 s |
| v17 placement table | Computed from committed metadata | `docs/dungeons/v17-integration.md`: Sakura/Cloud cannot use `//paste -o` (+34/+46 over Y 255); blind stored-origin paste puts four of five below Y 0 |
| Real v17 audit, v9 comparison, candidate schematics, world paste | **Not started** (inputs unavailable here) | Next steps in the integration doc |

Affected paths: `tools/world_merge/*`, `tests/test_world_merge.py`, `docs/dungeons/v17-integration.md`, `collaboration/fable/STATUS.md`. No shared build/version files, Core modules, root skill, plan or handoff were edited.

## Package 2: dungeon-gate missions, residents and dialogue — content done, runtime adapter pending

| Item | State | Evidence |
| --- | --- | --- |
| `content/expeditions/gates.json` | 5 original gate missions (one per v17 dungeon), 5 gatekeeper residents with day routines and reactions, 28 named anchors | Each mission has a branch, on_fail/retry, death/disconnect/abandon policy, first-clear and smaller repeat rewards, a receipt template and a cooldown |
| `content/expeditions/dialogue/*.json` | 5 resident dialogues within `DialogueBook` limits | Validated |
| `tools/validate_expeditions.py` | Cross-checks against `Missions.java` contract keys, `encounter-profiles.json` mob keys, `FieldItems` ledger items and `DialogueBook` limits; checks stage-graph reachability, cycles and completion; blocks reward-farming patterns | `python tools/validate_expeditions.py` → 0 errors, 28 "anchor not yet measured" warnings |
| `tests/test_validate_expeditions.py` | 12 tests, each breaking one real rule on a copy | Passing |
| Whole suite | `python -m unittest discover -s tests` → 204 tests OK, 2 skipped | Container run |
| Runtime | **Not implemented.** Nothing loads this content in game yet | Adapter contract in `content/expeditions/README.md` |

## Package 3 and follow-ups

* `tools/world_merge/anchors.py`: `anchors-suggest` and `anchors-verify` with `--fill-output`, so the 28 gate anchors can be measured quickly once the v17 files are reachable. Tested on fixtures.
* `tools/world_merge/npcs.py`: `npc-inventory`, a metadata-only CustomNPCs intake. It executes nothing, copies no text, script or command content, and redacts clone names. The format assumption is untested on the real recovered files.
* `docs/dungeons/qa-route.md`: the player QA route, geometry gates, the adversarial matrix, and 7 code-cited findings for Codex. Most important: F1, support players get no boss reward (`EncounterRewards.java:21`); F2, encounter actors persist with no run binding; F3, no boss leash.
* `batch` subcommand: audit, placement and anchor candidates for every schematic in a folder, plus an aggregate `summary.json`. About 3 s on a synthetic 288×217×288 volume.
* Tests: `test_world_merge.py` has 37 tests; the full suite is 208 OK, 2 skipped.

## Real v17 run (owner uploaded the five schematics; SHA256 matches the intake record)

* `batch` run on all five files. Format, tiles, gravity and fluids are clean. One unsupported ladder in Nether.
* **Defect fixed in c1 candidates:** stained glass seals every isolated chest room's doorway, in all five dungeons. Only 12/36 chests connected to the main route; after 14 cell edits (six oak doors plus two transom clears) it is 36/36. Re-audit shows no regressions. Evidence: `release/evidence/v17-audit-20261113.json`. Candidates are in ignored `local/v17-candidates/` in this container only.
* Open for review: upper storeys and towers with no stairs or ladders (see `docs/dungeons/v17-integration.md`).
* New tool `doorways` with 2 fixture tests; `test_world_merge.py` now has 39 tests.
* The candidate `.schematic` files exist only in this cloud container (private, not in Git). To get them onto the PC, re-run `doorways` and `apply` there from the committed plan coordinates, or download them from this session.

## Hooks / requests for Codex

* Package 1 needs no hook. The tools are standalone.
* Package 2 needs the small gate-mission adapter in `content/expeditions/README.md`. It is a second registry beside `Missions.ALL`, using existing `Encounters.create`, `RewardLedger.issue` receipts, academy-style owned/run-bound actors and walking checks. It does not change existing contract keys or wire formats. Codex owns that Core change; I did not edit `modules/`.

## Next

1. Owner/Codex on the PC: `python -m tools.world_merge batch --input-dir local/v17-intake --output-dir local/v17-audit`, then review `summary.json` (aggregate only) and post it to this branch.
2. Measure the 28 anchors from the real v17 files (audit `--entrance`), fill `local_pos`, and pass `validate_expeditions.py --require-anchors`.
3. On the PC: `python -m tools.world_merge npc-inventory --world <D: recovered Naruto.test copy> --output local/npc-intake.json` (read-only).
4. Codex: review findings F1–F3 in `docs/dungeons/qa-route.md` and the adapter contract; once the server archive at D: is extracted, Fable or Codex can run `copy-world`/`paste-world` on a D: candidate.

## Sync state

The first push attempt from this cloud session got HTTP 403. After GitHub access was re-attached, the branch pushed to `origin/claude/upbeat-ride-nyxq23` (package 1 `3de0d34`, package 2 `d580641`, plus this status commit). Codex has not reviewed it yet.

## Jutsu VFX engine (owner request: "Rasengan needs to look like a moving Rasengan")

- Branch `claude/upbeat-ride-nyxq23`. New files: `JutsuMath.java` (pure math, 1002 checks in `tests/village/vale/JutsuMathTest.java`), `arsenal/HandTechnique.java` (server-owned, never-saved carrier entity id 121), `client/JutsuVfx.java` (renderer + arm poses).
- Styles: spiral (rotating multi-band sphere, charge-in motes, spiral burst on hit), charge (crackling bolts), fireedge (procedural flame), windedge (rotating crescents), lava (crusted molten globs on both fists), lightmode (body arcs + aura).
- Verified in a real 1.12.2 client (Xvfb + Mesa, private probe not committed): front, back, first-person and impact shots for all six styles; `release/evidence/vfx-hand-techniques-20261113.png` is the contact sheet. Forge `check` passes.
- Hooks touched Codex-owned files (small, reviewable): `Scrolls` (entity + renderer registration), `ScrollEffects.mode` (begin), `ScrollEvents` (impact), `RunPoses.finish` (arm pose), `ClientProxy.preInit` (config). Client option `jutsuDetail` 0–2 in valeorders-client.cfg.
- Proposed for Codex: a `verifyJutsu` Gradle task running `JutsuMathTest` (build.gradle is shared, so not edited here).
- Next: real shapes for ScrollActor projectiles (all currently `RenderSnowball`), then weapon visuals through the same engine.

## Arsenal weapon VFX (all 27 weapons)
- `WeaponPulse` (id 122) + `WeaponVfx` + `VfxDraw` + `WeaponMath` (2088 checks). Every role has a cast effect, every confirmed melee hit a coloured slash, auras for guard/mend/overdrive/sweep, trails for all 10 shot modes, ground visuals for all 4 field modes.
- Verified in client: `release/evidence/vfx-arsenal-weapons-20261113.png`. Review notes: `collaboration/fable/VFX-REVIEW-FOR-CODEX.md`.
- Four-way dodge (V): rolls/flip/side rolls verified in client, see VFX-REVIEW-FOR-CODEX.md.
- Enhanced the original ScrollHotbar and JutsuRail in place (HotbarFx); see VFX-REVIEW-FOR-CODEX.md.
