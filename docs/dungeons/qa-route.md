# Dungeon QA route and adversarial findings (pre-integration)

Scope: this is a test plan plus findings from reading the current source and the authored gate content. No game was run and no v17 geometry was walked. Code findings cite the exact lines. Geometry questions are written as the checks to run with `tools/world_merge`.

## Findings from the current code that will hurt dungeon play

| # | Finding | Evidence | Player impact | Smallest fix (for Codex review) |
| --- | --- | --- | --- | --- |
| F1 | Boss rewards need at least 5% of the boss's health dealt as damage, plus a hit in the last 60 s. | `modules/vale-orders/src/main/java/village/vale/EncounterRewards.java:21` | A healer, shield or objective player in co-op gets nothing. This contradicts the fun brief ("credit contribution … support"). | Add a contribution source (heals or protection on party members, objective completions) bound to the same run, and count it toward the threshold. |
| F2 | Encounter actors are persistent and never despawn. They have no run or owner binding. | `modules/vale-encounters/.../Actors.java:23`, `:32` | If a dungeon spawns them directly, a restart or a party wipe leaves orphan mobs and bosses in the template. This is the exact stranding the atlas forbids. | Tag dungeon spawns with owner and run (the academy `ACTOR_OWNER`/`ACTOR_KIND` pattern) and remove stale ones on tick, as `AcademyEvents.actor` does. |
| F3 | There is no leash or home. Mobs use `EntityAIMoveTowardsTarget` within 24 blocks. | `Actors.java:47-48` | A boss can be dragged out of its arena into corridors or off a Cloud bridge. That breaks phase-lane designs and the camera envelope. | Set `setHomePosAndDistance` from the arena anchor and return home when the target leaves the arena. |
| F4 | Area and control attacks hit at most 6 players. | `Actors.java:62` | Fine for 1–4 players. For 5–6 it silently spares players. | Document this as the supported party size (the atlas already says 1–4 first). |
| F5 | There is a global cap of 128 loaded actors, counted only by the operator spawn command. | `Encounters.java:33` | A dungeon spawner that skips this path has no cap. | Route dungeon spawns through one bounded spawner per run. |
| F6 | Gate missions depend on 9 objective types that don't exist yet. | `python tools/validate_expeditions.py` → `adapter_objectives_needed` | The content can't be played until the adapter lands. | `content/expeditions/README.md`, "Proposed Core adapter". |
| F7 | Sakura and Cloud only fit when their minimum corner sits at Y ≤ 39 and Y ≤ 47. Their tops then land at Y ≈ 253. | `docs/dungeons/v17-integration.md` placement table | Nothing can be built above them, and the sky-island silhouette sits at the world ceiling. Upper rooms may also stop receiving rain or weather (verify natively). | Pick the lowest legal minimum corner that keeps the fractured underside above terrain, and check the arrival view from the gate. |

## Route a new player follows (per dungeon)

1. Finish the academy basics and the prerequisite contract (`academy_ambush`, `rogue_hunt` or `reed_trial`).
2. Find the gatekeeper at their day anchor. At night they keep a fallback anchor and still answer.
3. Accept the gate mission. It should take 6–10 minutes, with one choice, one encounter or puzzle, then an escort, defence or arrival.
4. Claim the reward: XP, merit, rations and the `expedition_<dungeon>_entry` unlock.
5. Enter the dungeon from the verified arrival anchor.

Measure with a stopwatch: time to the first meaningful action after accepting (target under 60 s), time spent walking with nothing happening, and how many times the player opened the journal to find the way.

## Geometry checks before anyone walks it

Run these on each real v17 file. A real failure blocks the walkthrough.

```bash
python -m tools.world_merge audit --input <file> --entrance <arrival> --target <arena centre> --target <reward chest stand cell> --strict
```

```bash
python -m tools.world_merge anchors-suggest --input <file> --entrance <arrival>
```

```bash
python -m tools.world_merge anchors-verify --input <file> --gates content/expeditions/gates.json --dungeon <id> --measured <picked.json> --entrance <arrival> --fill-output local/gates.measured.json
```

- **Rooms fit powers:** `anchors-suggest` `open_areas[].span`. Each boss arena needs a minimum span of at least 30, and the atlas targets about 44×44. A mid-boss chamber needs at least 12.
- **No stranded loot:** `audit` `routes.unreachable_containers.count == 0`.
- **No dead areas:** `routes.unreached_regions_16plus` is reviewed. Each entry is either decorative (e.g. a roof) or a defect.
- **Water and lava:** `fluids.by_classification` has no `leak_candidate`. Mist's `open_boundary` is expected and needs sea at the chosen Y. Nether lava must be `contained`.
- **Gravity:** no unsupported sand. This matters most for Sand, where blocks 12 and 179 dominate.

## Adversarial playthrough matrix (native, after the adapter)

| Case | Expected | Failure that matters |
| --- | --- | --- |
| Solo, each branch of each gate mission | Completes in 6–10 min with no command help | A branch soft-locks, or needs a party |
| Two players, one already cleared | The cleared player can assist and gets repeat rewards only | Duplicate first-clear unlock or a duplicate receipt |
| Disconnect mid-escort, then rejoin | Stage kept, escort actor recreated at the stage start | Orphan actor (F2) or a lost stage |
| Death during `defeat_group` | Respawn at checkpoint; only the current stage's actors respawn | Reinforcements stack up |
| Timer expiry (Cloud levers, Mist lanterns, Nether seal) | `on_fail` resets that stage only | The whole mission resets, or the timer persists across logout |
| Full inventory at claim | Rations go to the outbox; XP and merit once | A second claim pays again |
| Pull the boss out of its arena | Boss returns home (needs the F3 fix) | Fight spills into corridors |
| Healer-only player in a boss kill | Receives a reward (needs the F1 fix) | No reward |
| Transformation or large form in the arena | Camera and collision fit within the measured span | Clipping through roof or walls |
| Restart during an expedition | Checkpoint restored or run closed with a refund | Template edited or actors stranded |
| Reduced effects, no shaders | Tells (rings, windups) still readable | Invisible telegraph |

Record observed times and failures separately from expectations. A green audit is not a walkthrough.
