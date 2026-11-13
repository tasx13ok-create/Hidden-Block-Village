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

## Hooks / requests for Codex

* None needed for package 1. The tools are standalone.
* For the mission content (package 2), see `content/missions/README.md` on this branch once it is pushed: it describes a proposed data adapter contract for Core review.

## Next

1. Owner/Codex on the PC: run the commands at the end of `docs/dungeons/v17-integration.md` against `local/v17-intake` and post the JSON summaries (aggregate only) back to this branch.
2. Fable continues with independent package 2 (original dungeon-entrance missions/NPC dialogue as data) on this branch.
