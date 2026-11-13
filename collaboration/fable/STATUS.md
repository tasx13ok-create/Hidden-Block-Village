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

## Hooks / requests for Codex

* Package 1 needs no hook. The tools are standalone.
* Package 2 needs the small gate-mission adapter in `content/expeditions/README.md`. It is a second registry beside `Missions.ALL`, using existing `Encounters.create`, `RewardLedger.issue` receipts, academy-style owned/run-bound actors and walking checks. It does not change existing contract keys or wire formats. Codex owns that Core change; I did not edit `modules/`.

## Next

1. Owner/Codex on the PC: run the commands at the end of `docs/dungeons/v17-integration.md` against `local/v17-intake` and post the JSON summaries (aggregate only) back to this branch.
2. Measure the 28 anchors from the real v17 files (audit `--entrance`), fill `local_pos`, and pass `validate_expeditions.py --require-anchors`.
3. Codex: review the adapter contract; once the server archive at D: is extracted, Fable or Codex can run `copy-world`/`paste-world` on a D: candidate.

## Sync state

Push from this cloud session to `origin claude/upbeat-ride-nyxq23` was refused with HTTP 403 ("Claude doesn't have GitHub access to tasx13ok-create/Hidden-Block-Village for your organization"). Until GitHub access is fixed, these commits exist only in this session's container. A `git format-patch` bundle of the branch is the fallback for the owner to apply on the PC.
