# Dungeon gate missions and residents (content package, not yet runtime)

`gates.json` defines one short, original gate mission per v17 dungeon. Each mission has a resident who gives it, a routine for that resident, a branch choice, failure and retry rules, and real rewards. The `dialogue/*.json` files hold each resident's conversation, written to the existing Residents `DialogueBook` limits. Nothing here is loaded by the game yet. This is reviewed content plus a proposed adapter for Codex. Validate it with:

```bash
python tools/validate_expeditions.py
```

The validator checks the content against the repository's real data: prerequisite contracts in `Missions.java`, mob profiles in `encounter-profiles.json`, reward items in `FieldItems.java`, and `DialogueBook` limits. It also checks stage-graph reachability, termination and retries, and blocks reward-farming patterns. Tests live in `tests/test_validate_expeditions.py`.

| Mission | Dungeon | Giver | Level / prerequisite | Branch | Verb it teaches |
| --- | --- | --- | --- | --- | --- |
| `gate_sakura_seedfall` | Sakura | Hanae, Root Archive Keeper | 4 / `academy_ambush` | read seed stones **or** flank braced scouts | observation vs. flanking, escort |
| `gate_cloud_first_interval` | Cloud | Tsumugi, Bellwarden | 5 / `academy_ambush` | exposed bridge **or** timed covered levers | firing-lane reading, hold a point |
| `gate_sand_dry_wind` | Sand | Rihane, Cistern Keeper | 6 / `rogue_hunt` | wind vanes **or** shielded ambush | side attacks, escort, food relief |
| `gate_mist_low_tide` | Mist | Shiori, Harbor Pilot | 5 / `academy_ambush` | boat-house lane **or** protect the skiff | timed lanterns, sound cues |
| `gate_nether_ash_seal` | Nether | Kagero, Forge Sentinel | 8 / `reed_trial` | cooling valves **or** gate assault | interrupt a channel under a timer |

Each run is planned at 6–10 minutes. Rewards stay inside the current `Missions.java` scale: first clear gives 300–500 XP, 3–4 merit, two queued `travel_ration`, a preview flag and an `expedition_<dungeon>_entry` unlock. Repeats give less and unlock nothing, and a 6000-tick cooldown applies. No item, armor or power is invented that the game doesn't already register.

## Anchors still to measure

All 28 anchors have `local_pos: null`, each with a hint for what to find in the schematic. Once the v17 files are reachable, run `python -m tools.world_merge audit --input <v17> --entrance <x,y,z>` to confirm each candidate cell is a standable route node. Then fill in `local_pos`, and `python tools/validate_expeditions.py --require-anchors` must pass. World positions come later, from the chosen paste anchor (`placement --anchor`).

## Proposed Core adapter (smallest contract; Codex owns Core)

Today `Missions.Contract` covers one flat objective per contract. The adapter would add a second registry beside `Missions.ALL`, without changing existing contract keys, wire formats or saved fields:

1. **Load.** Read `gates.json` and the dialogue files at server start, with the same bounded-JSON rules as `DialogueBook` (64 KiB, unknown fields rejected). If a file is invalid, keep the previous definitions and log why.
2. **State.** Store per-player data under `PlayerState.data(p).getCompoundTag("Gates")`: `{mission, run, stage, branch, stageProgress, stageDeadline}`. `run` increments on accept, like `missionRun`. Clear any run-bound actor UUIDs with both `Most`/`Least` tags (`AcademyProgress.forgetActor` pattern).
3. **Events → objectives** (server-observed only):
   - `reach_marker`: the player's feet lie within `radius` of the anchor's world cell, in the same dimension, and they walked there (the academy's walking sample rule, no teleport credit).
   - `interact_marker` / `interact_sequence`: right-clicking the anchor block. `ordered` enforces order. A wrong order runs `on_fail`.
   - `choose_branch`: a clickable token choice, like `AcademyCampaign.choose`. The token is single-use.
   - `defeat_group`: actors spawned through `village.encounters.Encounters.create(world, profileKey)` and tagged with an owner and run ID, credited through `Missions.credit` → the same owner/run check as `AcademyCampaign.ambushMatches`.
   - `escort_actor`, `protect_actor`, `defend_point`: one owned actor or point with a bounded timer. If the actor is downed or the point is lost, `on_fail.retry_from` runs.
   - `deliver_items`: consume plain stacks only at stage completion, as in `Missions.claim` for supplies.
4. **Rewards.** Use `RewardLedger.issue(p, key, count, receipt)` with receipt `gate/<uuid>/<mission>/<run>` so a full inventory goes to the outbox and a replay is a no-op. First clear versus repeat is decided by a saved `gatesCleared` flag set in the same save as the XP and merit.
5. **Dialogue.** Map `ACCEPT_CONTRACT target=gate_*` to the gate registry, and `CLAIM_CONTRACT` to gate claim when a gate mission is active. Show `after_clear_start` once the player has cleared it.
6. **Lifecycle.** On logout, dimension change or death, remove the player's run-bound actors and keep `stage`. On resume, respawn only what the current stage still needs, as the academy ambush does.

Proposed native checks: accept → each branch → completion → claim; claim replay; full-inventory outbox; logout mid-escort, then resume; a wrong interact order; timer expiry and retry; a death during `defeat_group`; repeat-reward cooldown.

## Provenance

All names, characters, dialogue and mission premises are original to this repository. They use public Naruto vocabulary (nature releases, village roles) without canon characters, and no CustomNPCs payload or text was used. The recovered Kakashi clone and dialogs are separate private inputs and were not read.
