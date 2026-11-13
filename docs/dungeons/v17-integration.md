# v17 dungeon integration: audit/import tooling and placement findings

Status: **tooling delivered and fixture-tested; not yet run on the real v17 bytes.** The Fable session that wrote this ran in an isolated cloud container. It could not see the owner's Windows files (`local/v17-intake`, `C:/Users/Steal/Downloads/archive-2026-11-13T173625Z`, the parent `../output_v9` / `../source` / `../tools`, or `D:/HiddenBlockVillage-Sprint-20261113/recovered-world`), and no computer folder was shared with it. So the report has no v17 route, fluid or defect counts, no v9/v17 diff and no candidate schematic yet. Everything below about the real files comes from committed metadata (`release/evidence/v17-intake-20261113.json`). Everything about behavior comes from synthetic fixtures.

## Inputs (from committed metadata, not re-hashed here)

| File | Size (W×H×L) | SHA256 | WEOrigin | WEOffset | Top occupied local Y |
| --- | --- | --- | --- | --- | --- |
| `sakura_v17.schematic` | 288×217×288 | `5f0eb66c…6a24` | (96,73,1) | (-144,-99,-49) | 214 |
| `cloud_v17.schematic` | 288×209×288 | `a59a1b4e…70fa` | (96,93,1) | (-144,-123,-49) | 206 |
| `sand_v17.schematic` | 288×153×288 | `7ea2cb4f…15ec` | (96,69,1) | (-144,-107,-49) | 150 |
| `mist_v17.schematic` | 200×148×200 | `40d6fbdb…be51` | (96,21,1) | (-96,-21,-1) | 94 |
| `nether_v17.schematic` | 256×126×256 | `55867e4f…7f72` | (80,43,1) | (-128,-51,-49) | 123 |

## Placement finding (computed from the metadata above)

WorldEdit 6 stores `WEOrigin` as the copied region's **minimum corner** and `WEOffset` as *minimum corner − copy position*. `//paste` puts the minimum corner at `paste position + WEOffset`; `//paste -o` restores it to `WEOrigin`. This interpretation agrees with the existing v9 Mist note in the design (offset Y −21, top water at local Y 16 → paste Y 68 for world water at 63). `tools/world_merge/placement.py` reproduces that value in a test.

| Dungeon | `//paste -o` world Y | Blind paste standing on stored origin | Valid minimum-corner Y | Valid `//paste` Y |
| --- | --- | --- | --- | --- |
| Sakura | 73–289 — **34 layers above 255** | −26–190 — **26 below 0** | 0–39 | 99–138 |
| Cloud | 93–301 — **46 layers above 255** | −30–178 — **30 below 0** | 0–47 | 123–170 |
| Sand | 69–221 fits | −38–114 — **38 below 0** | 0–103 | 107–210 |
| Mist | 21–168 fits | 0–147 fits | 0–108 | 21–129 |
| Nether | 43–168 fits | −8–117 — **8 below 0** | 0–130 | 51–181 |

So: Sakura and Cloud cannot go back to their authored height in a 1.12 world. Because they occupy local layers up to 214/206, their highest blocks land at world Y ≥ 214/206 even at the lowest legal placement. Their arrival ground level has to be chosen with that in mind. Nobody should paste any of them "at the stored origin". Pick an explicit anchor with `placement --anchor local:world`, which also reports chunks, region files and the equivalent `//paste` position.

## The package: `tools/world_merge/`

Standard-library Python 3 only. Run from the repository root with `python -m tools.world_merge <command>`. Inputs are never written. Every output must be a new path; JSON reports may be replaced only with `--replace-report`.

| Command | What it does |
| --- | --- |
| `audit --input F [--entrance x,y,z]… [--target x,y,z]… [--output R] [--strict]` | Checks: format and shape; 12-bit IDs including `AddBlocks` (WorldEdit nibble order); non-vanilla IDs; data > 15; occupied layers; tile-entity identity, coordinates, duplicates, block/tile mismatches and missing tiles. Container inventory counts are recorded. Command blocks are reported by **length only** and never echoed or executed. Also checks gravity supports, fluid components and walk/climb/swim routes from explicit arrivals (unreachable containers, unreachable targets, stranded regions), plus door halves/sides, iron doors, stair headroom and ladder support. Ends with the placement report. With `--strict`, exits 1 when anything needs review. |
| `placement --input F [--anchor lx,ly,lz:wx,wy,wz] [--paste-at x,y,z]` | The table above, for any file and any proposed anchor. |
| `compare --old A --new B (--delta d \| --anchor-pair a:b \| --we-origin)` | Diff under one explicit alignment: retained, replaced, added and removed cells, plus tile correspondence. It also lists tile-vote alignment *suggestions*, which are hints to check, not proof that entrances align. A centred crop is never assumed. |
| `propose --input F --output plan.json` | Writes a `PROPOSED_FOR_REVIEW` plan: supports under unsupported sand/gravel/powder (matching non-falling block) and missing upper door halves. It never edits anything. |
| `apply --input F --plan P --output NEW --log L` | Applies a reviewed plan to a **new** candidate. Each edit must state the block it expects. One mismatch, a wrong `input_sha256`, a tile-entity cell without an explicit `keep`/`remove`, a duplicate cell or a missing reason aborts the whole plan. The output is then re-read: only planned cells may differ, and every other tile entity and root tag must be byte-identical. The log records input/output/plan SHA256 and each before/after. |
| `copy-world --source W --dest NEW --manifest M` | Hash-verified copy of a world into a new candidate directory, marked with `WORLD-MERGE-CANDIDATE.json`. Refuses an existing destination, nesting and symlinks. |
| `paste-world --input F --world-copy NEW --min x,y,z --receipt R` | Pastes into the candidate's Anvil regions. Refuses any directory without the candidate marker, so the recovered source copy cannot be touched. Snapshots each region first, rewrites atomically and keeps untouched chunks' bytes and unknown chunk tags (Forge caps). It replaces tiles and ticks inside the volume, leaves entities alone and counts them, and clears `LightPopulated` so the game relights. |
| `rollback --world-copy NEW --paste N` | Restores region bytes from before paste N (hash-checked snapshots). |

Measured in the container: a full audit of a synthetic 288×217×288 volume takes ≈2.5 s, and pasting a 200×148×200 volume into a world copy takes ≈4 s.

## Fixture tests (`tests/test_world_merge.py`, 33 tests, all passing)

* **Hostile NBT:** truncation; 2 GiB and negative lengths; a 600-deep nesting bomb; a gzip decompression bomb; a non-compound root; trailing bytes; corrupt gzip. All are rejected without large allocations.
* **Format bounds:** wrong array lengths; negative or huge dimensions; Sponge and non-Alpha formats; short `AddBlocks`; the odd-cell `AddBlocks` padding length; extended IDs (300, 0x5FF) on even and odd indices round-tripping.
* **Offsets:** the real Cloud metadata reproduces the +46/−30 overshoots. A Mist anchor reproduces the design's paste Y 68 and crosses negative region boundaries.
* **Audits:** duplicate, out-of-bounds, mismatched and missing tiles; command text never appears in reports. Gravity over air or water vs solid vs bottom layer. Fluids: a contained pool, a breached basin (leak with exact coordinates), a sea reaching an edge (open boundary, not a leak) and a falling column (authored flow). Routes: a chest behind an oak door is reachable and unreachable once the doorway is bricked; an iron door blocks; a ladder climb reaches an upper ledge until the ladder is removed; a step-up under a low ceiling fails but onto a stair succeeds; a 5-block drop is not a route; invalid entrances are reported. Access: orphan halves, a blocked door side, an unsupported ladder.
* **Fixes:** the proposed plan applies to a new candidate and clears its findings while keeping chest items and an unknown root tag. A bad expectation aborts with no output. Overwrite, wrong input hash, tile-cell rules, missing reason and duplicate cells are all refused.
* **World import:** a paste across four regions at negative coordinates. Block, door and tile world coordinates are verified by reading the regions back. Forge tag and untouched chunk bytes are preserved. The source world's hashes are unchanged. Rollback restores exact bytes and removes created regions. An over-height paste writes nothing. A corrupt region header is rejected.

## Limitations, stated plainly

* The route model is a conservative block-grid approximation. It covers walking, a one-block step (stairs and slabs need no headroom), drops of up to 3, ladders, vines and swimming. It ignores sprint-jumps, gaps, fences you can jump with a trapdoor trick, slime and abilities. A "reachable" result is a candidate for a native walk, not a replacement for one. With no `--entrance`, it seeds from every boundary floor cell, which is only a smoke test.
* Fluid classes are heuristics. `open_boundary` water (Mist's sea) needs matching world water at the chosen Y. `authored_flow` needs a native physics check.
* The world paste does not compute light, does not run fluid or gravity physics, and does not fix chunk biome or heightmap beyond a simple top-block recompute. It has only run on synthetic worlds. The first real use must be on a fresh `copy-world` candidate on D:, followed by a server start, a walk, a save/restart and a `rollback` trial.
* `compare` does not diff `AddBlocks` (v17 has no extended IDs per the intake scan). It also needs the v9 files, which this session could not read.

## Exact next steps (owner's PC, for Fable or Codex with folder access)

```powershell
python -m tools.world_merge audit --input local/v17-intake/sakura_v17.schematic --output local/v17-audit/sakura.json
python -m tools.world_merge compare --old ../output_v9/earth_sacred_sakura.schematic --new local/v17-intake/sakura_v17.schematic --anchor-pair <v9 gate>:<v17 gate> --output local/v17-audit/sakura-v9-v17.json
python -m tools.world_merge propose --input local/v17-intake/sakura_v17.schematic --output local/v17-audit/sakura-plan.json
```

1. Run `audit` on all five files without entrances. Read off the arrival or gate cells from the occupied layers and boundary seeds, then rerun with `--entrance`/`--target` at the authored gate, the boss arena and the chests.
2. Get a v9↔v17 alignment from one shared authored landmark per dungeon (`--anchor-pair`), cross-checked against the tile-vote suggestions.
3. Review each `propose` plan by hand, `apply` it to `local/v17-candidates/<name>-c1.schematic` with a log, and re-audit.
4. Choose anchors (Mist: keep the top-water Y; Sakura/Cloud: minimum corner ≤ 39/47). Then run `copy-world` from the D: recovery to a new D: candidate, `paste-world`, start a disposable server, walk the routes, save, restart and roll back once. Record each result at its real scope.
